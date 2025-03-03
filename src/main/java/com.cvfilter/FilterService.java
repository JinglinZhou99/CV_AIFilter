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
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Prepare the request body
            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria:\n\nSkills required: Java, Spring, SQL.\nEducation required: At least one of Bachelor, Master, PhD.\n\nResume text:\n" + text)
                    ),
                    "temperature", 0.7,
                    "max_tokens", 150
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

            // Check if the response indicates a match
            return content.toLowerCase().contains("matches the specified criteria");
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return false;
        }
    }

}

