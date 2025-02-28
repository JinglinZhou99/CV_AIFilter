package com.cvfilter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.File;

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
            int matchCount = 0;

            for (File file : files) {
                System.out.println("Processing: " + file.getName());
                boolean matches = filterService.matchesCriteria(file);
                if (matches) {
                    matchCount++;
                    System.out.println("✅ MATCH: " + file.getName());
                } else {
                    System.out.println("❌ NO MATCH: " + file.getName());
                }
            }

            System.out.println("\nSummary:");
            System.out.println("Total resumes: " + files.length);
            System.out.println("Matching resumes: " + matchCount);
        };
    }
}
