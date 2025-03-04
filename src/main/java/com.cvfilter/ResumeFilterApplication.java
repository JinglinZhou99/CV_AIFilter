package com.cvfilter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.File;
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
            String cvFolderPath = "CVs";
            if (args.length > 0) {
                cvFolderPath = args[0];
            }

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

        };
    }
}
