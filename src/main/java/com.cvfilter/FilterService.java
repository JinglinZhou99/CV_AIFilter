package com.cvfilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class FilterService {

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.key}")
    private String apiKey;

    @Autowired
    private final RestTemplate restTemplate = new RestTemplate();

    public static class AnalysisResult {
        private final boolean matches;
        private final String reason;

        public AnalysisResult(boolean matches, String reason) {
            this.matches = matches;
            this.reason = reason;
        }

        public boolean isMatch() {
            return matches;
        }

        public String getReason() {
            return reason;
        }
    }

    public AnalysisResult analyzeCriteria(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document);
            return aiBasedFiltering(text);
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            return new AnalysisResult(false, "Error processing file: " + e.getMessage());
        }
    }

    private AnalysisResult aiBasedFiltering(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria:\n\n" +
                                    "Skills required: Java, Spring, SQL.\n" +
                                    "Education required: At least one of Bachelor, Master, PhD.\n\n" +
                                    "Resume text:\n" + text + "\n\n" +
                                    "First, state whether the resume matches ALL criteria or not. Then explain which specific criteria are met and which are not met. Be specific about which skills or education requirements are missing if applicable.")
                    ),
                    "temperature", 0.3,
                    "max_tokens", 300
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);

            // Parse the response
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(response, Map.class);

            // Extract the assistant's message
            Map<String, Object> choices = ((List<Map<String, Object>>) responseMap.get("choices")).get(0);
            Map<String, Object> message = (Map<String, Object>) choices.get("message");
            String content = (String) message.get("content");

            // More robust check for matches - look for positive phrases, avoid negatives
            String contentLower = content.toLowerCase();
            boolean matches = contentLower.contains("matches all the criteria") ||
                    (contentLower.contains("match") && !contentLower.contains("not match") &&
                            !contentLower.contains("doesn't match") && !contentLower.contains("does not match"));


            return new AnalysisResult(matches, content);
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return new AnalysisResult(false, "Error calling AI service: " + e.getMessage());
        }
    }
}