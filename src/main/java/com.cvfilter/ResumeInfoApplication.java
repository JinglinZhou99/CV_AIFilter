//package com.cvfilter;
//
//import org.apache.poi.ss.usermodel.Cell;
//import org.apache.poi.ss.usermodel.Row;
//import org.apache.poi.ss.usermodel.Sheet;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.web.client.RestTemplate;
//
//import java.io.*;
//import java.util.*;
//import java.util.concurrent.*;
//
//@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
//public class ResumeInfoApplication {
//    public static void main(String[] args) {
//        SpringApplication.run(ResumeInfoApplication.class, args);
//    }
//
//    @Bean
//    public RestTemplate restTemplate() {
//        return new RestTemplate();
//    }
//
//    @Bean
//    public CommandLineRunner run(InfoService infoService) {
//        return args -> {
//            String cvFolderPath = "EHSCVs";
//            if (args.length > 0) {
//                cvFolderPath = args[0];
//            }
//
//            // Define Excel file path - in the same directory as the application
//            String excelFilePath = "EHS_Resume_Candidate_Certificate.xlsx";
//
//            File folder = new File(cvFolderPath);
//            if (!folder.exists() || !folder.isDirectory()) {
//                System.err.println("Error: The specified path is not a valid directory: " + cvFolderPath);
//                return;
//            }
//
//            File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
//            if (files == null || files.length == 0) {
//                System.out.println("No PDF files found in the directory: " + cvFolderPath);
//                return;
//            }
//
//            System.out.println("Starting to extract information from " + files.length + " resumes...");
//
//            // Create results directory for caching if needed
//            String cachePath = cvFolderPath + File.separator + "extraction_cache";
//            File cacheDir = new File(cachePath);
//            if (!cacheDir.exists()) {
//                cacheDir.mkdirs();
//            }
//
//            // Create the thread pool based on CPU cores
//            int processors = Runtime.getRuntime().availableProcessors();
//            ExecutorService executorService = Executors.newFixedThreadPool(processors);
//
//            // Process results in parallel
//            ConcurrentHashMap<File, InfoService.CandidateInfo> resultMap = new ConcurrentHashMap<>();
//            CountDownLatch latch = new CountDownLatch(files.length);
//
//            for (File file : files) {
//                executorService.submit(() -> {
//                    try {
//                        System.out.println("Processing: " + file.getName());
//                        InfoService.CandidateInfo info = infoService.extractCandidateInfo(file);
//                        resultMap.put(file, info);
//                    } catch (Exception e) {
//                        System.err.println("Error processing " + file.getName() + ": " + e.getMessage());
//                    } finally {
//                        latch.countDown();
//                    }
//                });
//            }
//
//            // Wait for all tasks to complete
//            try {
//                latch.await();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                System.err.println("Processing was interrupted");
//            }
//
//            // Shutdown the thread pool
//            executorService.shutdown();
//
//            // Convert results to list
//            List<Map.Entry<File, InfoService.CandidateInfo>> results =
//                    new ArrayList<>(resultMap.entrySet());
//
//            // Display extracted information
//            System.out.println("\n===== EXTRACTED CANDIDATE INFORMATION =====\n");
//
//            for (Map.Entry<File, InfoService.CandidateInfo> entry : results) {
//                File file = entry.getKey();
//                InfoService.CandidateInfo info = entry.getValue();
//
//                System.out.println("File: " + file.getName());
//                System.out.println("  Name: " + info.getName());
//                System.out.println("  Gender: " + info.getGender());
//                System.out.println("  Certificate: " + info.getCertificate());
//                System.out.println("  Years of EHS Experience: " + info.getYearsOfEHSExperience());
//                System.out.println("  Location: " + info.getLocation());
//                System.out.println("  Race: " + info.getRace());
////                System.out.println("  Name: " + info.getName());
////                System.out.println("  Gender: " + info.getGender());
////                System.out.println("  Age: " + info.getAge());
////                System.out.println("  Nationality: " + info.getNationality());
////                System.out.println("  Location: " + info.getLocation());
////                System.out.println("  Race: " + info.getRace());
//                System.out.println();
//            }
//
//            // Write results to Excel file
//            writeResultsToExcel(results, excelFilePath);
//            System.out.println("Results have been saved to: " + excelFilePath);
//        };
//    }
//
//    /**
//     * Write candidate information to an Excel file
//     * @param results List of file and candidate info entries
//     * @param excelFilePath Path where the Excel file should be saved
//     */
//    private static void writeResultsToExcel(List<Map.Entry<File, InfoService.CandidateInfo>> results, String excelFilePath) {
//        try (Workbook workbook = new XSSFWorkbook()) {
//            Sheet sheet = workbook.createSheet("Candidate Information");
//
//            // Create header row
//            Row headerRow = sheet.createRow(0);
//            //String[] headers = {"Filename", "Name", "Gender", "Age", "Nationality", "Location (City)", "Race"};
//            String[] headers = {"Filename", "Name", "Gender", "Certificate", "Years of EHS Experience", "Location (City)", "Race"};
//
//            for (int i = 0; i < headers.length; i++) {
//                Cell cell = headerRow.createCell(i);
//                cell.setCellValue(headers[i]);
//            }
//
//            // Add data rows
//            int rowNum = 1;
//            for (Map.Entry<File, InfoService.CandidateInfo> entry : results) {
//                Row row = sheet.createRow(rowNum++);
//                InfoService.CandidateInfo info = entry.getValue();
//
//                // Filename
//                row.createCell(0).setCellValue(entry.getKey().getName());
//                row.createCell(1).setCellValue(info.getName());
//                row.createCell(2).setCellValue(info.getGender());
//                row.createCell(3).setCellValue(info.getCertificate());
//                row.createCell(4).setCellValue(info.getYearsOfEHSExperience());
//                row.createCell(5).setCellValue(info.getLocation());
//                row.createCell(6).setCellValue(info.getRace());
//                // Candidate information
////                row.createCell(1).setCellValue(info.getName());
////                row.createCell(2).setCellValue(info.getGender());
////                row.createCell(3).setCellValue(info.getAge());
////                row.createCell(4).setCellValue(info.getNationality());
////                row.createCell(5).setCellValue(info.getLocation());
////                row.createCell(6).setCellValue(info.getRace());
//            }
//
//            // Auto-size columns for better readability
//            for (int i = 0; i < headers.length; i++) {
//                sheet.autoSizeColumn(i);
//            }
//
//            // Write to file
//            try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
//                workbook.write(fileOut);
//            }
//        } catch (IOException e) {
//            System.err.println("Error writing to Excel file: " + e.getMessage());
//        }
//    }
//}