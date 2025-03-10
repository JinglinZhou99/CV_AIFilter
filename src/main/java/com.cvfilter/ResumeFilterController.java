package com.cvfilter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Controller
public class ResumeFilterController {

    private final Path uploadDir = Paths.get("uploads");
    private final Path resultsDir = Paths.get("results");

    @Autowired
    private FilterService filterService;

    public ResumeFilterController() throws IOException {
        Files.createDirectories(uploadDir);
        Files.createDirectories(resultsDir);
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("jobRequirements", "");
        model.addAttribute("aiInstruction", "");
        return "index";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("files") MultipartFile[] files,
                                   @RequestParam("jobRequirements") String jobRequirements,
                                   @RequestParam("aiInstruction") String aiInstruction,
                                   RedirectAttributes redirectAttributes) {

        if (files.length == 0 || files[0].isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select files to upload");
            return "redirect:/";
        }

        // Generate unique session ID for this batch
        String sessionId = UUID.randomUUID().toString();
        Path sessionDir = uploadDir.resolve(sessionId);

        try {
            Files.createDirectories(sessionDir);

            // Save uploaded files
            for (MultipartFile file : files) {
                if (file.getOriginalFilename() != null && !file.isEmpty()) {
                    Files.copy(file.getInputStream(), sessionDir.resolve(file.getOriginalFilename()));
                }
            }

            // Process the files and create Excel
            String excelFilename = processFiles(sessionDir, jobRequirements, aiInstruction);

            redirectAttributes.addFlashAttribute("message",
                    "Successfully processed " + files.length + " files");
            redirectAttributes.addFlashAttribute("excelFile", excelFilename);
            redirectAttributes.addFlashAttribute("sessionId", sessionId);

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("message",
                    "Failed to process uploaded files: " + e.getMessage());
        }

        return "redirect:/";
    }

    @GetMapping("/download/{sessionId}/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String sessionId,
                                                 @PathVariable String filename) {
        try {
            Path file = resultsDir.resolve(sessionId).resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(resource);
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    private String processFiles(Path sessionDir, String jobRequirements, String aiInstruction) throws IOException {
        // Create results directory for this session
        Path sessionResultsDir = resultsDir.resolve(sessionDir.getFileName());
        Files.createDirectories(sessionResultsDir);

        // Get all PDF files
        List<File> files = Files.list(sessionDir)
                .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                .map(Path::toFile)
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            throw new IOException("No PDF files found in uploaded files");
        }

        // Update the filter service to use custom job requirements and AI instruction
        filterService.setCustomJobRequirements(jobRequirements);
        filterService.setCustomAiInstruction(aiInstruction);

        // Process files in parallel
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(processors);

        ConcurrentHashMap<File, FilterService.AnalysisResult> resultMap = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(files.size());

        for (File file : files) {
            executorService.submit(() -> {
                try {
                    FilterService.AnalysisResult result = filterService.analyzeCriteria(file);
                    resultMap.put(file, result);
                } catch (Exception e) {
                    System.err.println("Error processing " + file.getName() + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Processing was interrupted");
        }

        // Shutdown executor
        executorService.shutdown();

        // Sort results
        List<Map.Entry<File, FilterService.AnalysisResult>> results =
                new ArrayList<>(resultMap.entrySet());
        results.sort((a, b) -> Integer.compare(b.getValue().getScore(), a.getValue().getScore()));

        // Create Excel file
        String excelFilename = "resume_analysis_" + sessionDir.getFileName() + ".xlsx";
        Path excelPath = sessionResultsDir.resolve(excelFilename);

        writeResultsToExcel(results, excelPath.toString());

        return excelFilename;
    }


    private static void writeResultsToExcel(List<Map.Entry<File, FilterService.AnalysisResult>> results, String excelFilePath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Resume Analysis Results");

            Row headerRow = sheet.createRow(0);
            Cell fileNameCell = headerRow.createCell(0);
            fileNameCell.setCellValue("Filename");

            Cell scoreCell = headerRow.createCell(1);
            scoreCell.setCellValue("Score");

            Cell reasonCell = headerRow.createCell(2);
            reasonCell.setCellValue("Reason");

            int rowNum = 1;
            for (Map.Entry<File, FilterService.AnalysisResult> entry : results) {
                Row row = sheet.createRow(rowNum++);

                // filename
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(entry.getKey().getName());

                // score
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(entry.getValue().getScore());

                // reason
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(entry.getValue().getReason());
            }

            // auto size column
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            // write to file
            try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
                workbook.write(fileOut);
            }
        } catch (IOException e) {
            System.err.println("Error writing to Excel file: " + e.getMessage());
        }
    }
}
