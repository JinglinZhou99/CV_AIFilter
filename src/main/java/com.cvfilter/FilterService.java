package com.cvfilter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FilterService {

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.key}")
    private String apiKey;

    private static final List<String> REQUIRED_SKILLS = Arrays.asList("Java", "Spring", "SQL");
    private static final List<String> REQUIRED_DEGREES = Arrays.asList("Bachelor", "Master", "PhD");

    @Autowired
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean matchesCriteria(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document);
            return aiBasedFiltering(text);
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean aiBasedFiltering(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "api_key", apiKey,
                    "text", text,
                    "instruction", "Analyze the resume and determine if it matches the following criteria: " +
                            "Skills required: " + String.join(", ", REQUIRED_SKILLS) + ". " +
                            "Education required: At least one of " + String.join(", ", REQUIRED_DEGREES) + "."
            );

            Map response = restTemplate.postForObject(apiUrl, request, Map.class);
            return response != null && Boolean.TRUE.equals(response.get("matches"));
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return false;
        }
    }
}

