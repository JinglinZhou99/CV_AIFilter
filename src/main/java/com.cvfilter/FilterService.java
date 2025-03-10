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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FilterService {

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.key}")
    private String apiKey;

    @Autowired
    private final RestTemplate restTemplate = new RestTemplate();

    // Default job requirements
    private String jobRequirements = "1. Associate degree or higher, majoring in Business Administration, Marketing, or related fields is preferred.\n\n" +
            "2. Fluent in Chinese or English.\n\n" +
            "3. Experience in managing large-scale chain operations, with over 5 years of operational management experience in the Thai region, and overseas work experience; experience as a brand manager related to IP(Intellectual Property)is preferred, as well as experience in the Thai market.\n\n" +
            "4. Honest and determined, with a strong sense of purpose; excellent ability to integrate resources, analyze, and solve problems; strong communication skills and stress resistance.";

    // Default AI instruction (customizable part of the prompt)
    private String aiInstruction = "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 40 words in Chinese.";


    public static class AnalysisResult {
        private final boolean matches;
        private final String reason;
        private final int score;

        public AnalysisResult(boolean matches, String reason, int score) {
            this.matches = matches;
            this.reason = reason;
            this.score = score;
        }


        public String getReason() {
            return reason;
        }

        public int getScore() {
            return score;
        }
    }

    // Allow custom job requirements to be set
    public void setCustomJobRequirements(String jobRequirements) {
        if (jobRequirements != null && !jobRequirements.trim().isEmpty()) {
            this.jobRequirements = jobRequirements;
        }
    }

    // Allow custom AI instructions to be set
    public void setCustomAiInstruction(String aiInstruction) {
        if (aiInstruction != null && !aiInstruction.trim().isEmpty()) {
            this.aiInstruction = aiInstruction;
        }
    }

    public AnalysisResult analyzeCriteria(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document);
            return aiBasedFiltering(text);
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            return new AnalysisResult(false, "Error processing file: " + e.getMessage(), 0);
        }
    }

    private AnalysisResult aiBasedFiltering(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();//Operation
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create the prompt using the customizable job requirements and AI instruction
            String promptContent = "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
                    jobRequirements + "\n\n" +
                    "Resume text:\n" + text + "\n\n" +
                    aiInstruction;

            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
                            Map.of("role", "user", "content", promptContent)
                    ),
                    "temperature", 0.3
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

             // Extract score from the content (the format like "Match score: 85/100")
            int score = 0;
            Pattern scorePattern = Pattern.compile("[Ss]core:?\\s*\\**\\s*(\\d+)\\s*/\\s*100\\**");
            Matcher matcher = scorePattern.matcher(content);
            if (matcher.find()) {
                score = Integer.parseInt(matcher.group(1));
            } else {
                // Fallback pattern that just looks for a number followed by /100
                Pattern fallbackPattern = Pattern.compile("(\\d+)\\s*/\\s*100");
                matcher = fallbackPattern.matcher(content);
                if (matcher.find()) {
                    score = Integer.parseInt(matcher.group(1));
                }
            }

            // Determine match status
            String contentLower = content.toLowerCase();
            boolean matches = contentLower.contains("matches all criteria") ||
                    (contentLower.contains("match") && !contentLower.contains("not match") &&
                            !contentLower.contains("doesn't match") && !contentLower.contains("does not match"));

            // Return analysis result with score
            return new AnalysisResult(matches, content, score);
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return new AnalysisResult(false, "Error calling AI service: " + e.getMessage(), 0);
        }
    }
}