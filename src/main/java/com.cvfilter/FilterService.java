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


    public static class AnalysisResult {
        private final boolean matches;
        private final String reason;
        private final int score;

        public AnalysisResult(boolean matches, String reason, int score) {
            this.matches = matches;
            this.reason = reason;
            this.score = score;
        }

        public boolean isMatch() {
            return matches;
        }

        public String getReason() {
            return reason;
        }

        public int getScore() {
            return score;
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
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria:\n\n" +
                                    "1. Education Background: Associate degree or above in Environmental Science, Safety Engineering, Industrial Engineering, or related fields.\n\n" +
                                    "2. Work Experience: More than 3 years of EHS (Environment, Health, and Safety) work experience. Priority will be given to candidates with manufacturing industry experience. Fresh graduates with relevant internship experience or EHS-related certificates will also be considered.\n\n" +
                                    "3. Professional Skills: Knowledge of Malaysian EHS laws, regulations and standards; familiarity with safety inspection, hazard identification, accident investigation, and other EHS work processes and methods. Basic knowledge of environmental management and occupational health, ability to identify common safety hazards and environmental issues.\n\n" +
                                    "4. Other Abilities: Conscientious and responsible with strong accountability and execution; good team collaboration and communication skills; proficient in office software such as Word, Excel, PPT, etc.\n\n" +
                                    "Resume text:\n" + text + "\n\n" +
                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 50 words.")
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