package com.cvfilter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})

public class ResumeFilterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResumeFilterApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CommandLineRunner run(FilterService filterService) {
        return args -> {
            String cvFolderPath = "EcoCVs";
            if (args.length > 0) {
                cvFolderPath = args[0];
            }

            // Define Excel file path - in the same directory as the application
            String excelFilePath = "Eco_resume_analysis_results.xlsx";

            File folder = new File(cvFolderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                System.err.println("Error: The specified path is not a valid directory: " + cvFolderPath);
                return;
            }

            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files == null || files.length == 0) {
                System.out.println("No PDF files found in the directory: " + cvFolderPath);
                return;
            }

            System.out.println("Starting to scan " + files.length + " resumes...");

            // create the pool - according to cpu cores
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = Executors.newFixedThreadPool(processors);

            // process the result parallel
            ConcurrentHashMap<File, FilterService.AnalysisResult> resultMap = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(files.length);

            for (File file : files) {
                executorService.submit(() -> {
                    try {
                        System.out.println("Processing: " + file.getName());
                        FilterService.AnalysisResult result = filterService.analyzeCriteria(file);
                        resultMap.put(file, result);
                    } catch (Exception e) {
                        System.err.println("Error processing " + file.getName() + ": " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // wait for all works done
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Processing was interrupted");
            }

            // shut down
            executorService.shutdown();

            // sort the results
            List<Map.Entry<File, FilterService.AnalysisResult>> results =
                    new ArrayList<>(resultMap.entrySet());
            results.sort((a, b) -> Integer.compare(b.getValue().getScore(), a.getValue().getScore()));

            // display the sorted results
            System.out.println("\n===== RESULTS (SORTED BY MATCH SCORE) =====\n");

            for (Map.Entry<File, FilterService.AnalysisResult> entry : results) {
                File file = entry.getKey();
                FilterService.AnalysisResult result = entry.getValue();

                System.out.println(file.getName() + " (Score: " + result.getScore() + "/100)");
                System.out.println("  Reason: " + result.getReason());
                System.out.println();
            }

            // Write results to file
            writeResultsToExcel(results, excelFilePath);
            System.out.println("Results have been saved to: " + excelFilePath);
        };
    }

    /**
     * Write analysis results to a file
     * @param results List of file and analysis result entries
     * @param excelFilePath Path where the CSV file should be saved
     */
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