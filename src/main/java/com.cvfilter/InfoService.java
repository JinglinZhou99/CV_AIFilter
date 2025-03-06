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
public class InfoService {

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.key}")
    private String apiKey;

    @Autowired
    private final RestTemplate restTemplate = new RestTemplate();

//    public static class CandidateInfo {
//        private final String name;
//        private final String gender;
//        private final String age;
//        private final String nationality;
//        private final String location;
//        private final String race;
//        private final String rawResponse;
//
//        public CandidateInfo(String name, String gender, String age, String nationality,
//                             String location, String race, String rawResponse) {
//            this.name = name;
//            this.gender = gender;
//            this.age = age;
//            this.nationality = nationality;
//            this.location = location;
//            this.race = race;
//            this.rawResponse = rawResponse;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public String getGender() {
//            return gender;
//        }
//
//        public String getAge() {
//            return age;
//        }
//
//        public String getNationality() {
//            return nationality;
//        }
//
//        public String getLocation() {
//            return location;
//        }
//
//        public String getRace() {
//            return race;
//        }
//
//        public String getRawResponse() {
//            return rawResponse;
//        }
//    }
// In InfoService.java, update the CandidateInfo class:
public static class CandidateInfo {
    private final String name;
    private final String gender;
    private final String certificate; // Changed from age
    private final String yearsOfEHSExperience; // Changed from nationality
    private final String location;
    private final String race;
    private final String rawResponse;

    public CandidateInfo(String name, String gender, String certificate, String yearsOfEHSExperience,
                         String location, String race, String rawResponse) {
        this.name = name;
        this.gender = gender;
        this.certificate = certificate;
        this.yearsOfEHSExperience = yearsOfEHSExperience;
        this.location = location;
        this.race = race;
        this.rawResponse = rawResponse;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public String getCertificate() { // Updated getter
        return certificate;
    }

    public String getYearsOfEHSExperience() { // Updated getter
        return yearsOfEHSExperience;
    }

    public String getLocation() {
        return location;
    }

    public String getRace() {
        return race;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}

    public CandidateInfo extractCandidateInfo(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document);
            return extractInfoWithAI(text);
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            return new CandidateInfo("Error", "Error", "Error", "Error", "Error", "Error",
                    "Error processing file: " + e.getMessage());
        }
    }

    private CandidateInfo extractInfoWithAI(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that extracts specific information from resume texts."),
                            Map.of("role", "user", "content", "Extract the following information from the resume text. If any information is not found, write 'Not specified'.\n\n" +
                                    "1. Name\n" +
                                    "2. Gender\n" +
                                    "3. Certificate (certification in Malaysian EHS standards, particularly the Greenbook or HQ/21/SHO/ series documentation system)\n" +
                                    "4. Years of EHS experience\n" +
                                    "5. Location (City)\n" +
                                    "6. Race\n\n" +
                                    "Resume text:\n" + text + "\n\n" +
                                    "Format your response as follows (JSON format):\n" +
                                    "```json\n" +
                                    "{\n" +
                                    "  \"name\": \"...\",\n" +
                                    "  \"gender\": \"...\",\n" +
                                    "  \"certificate\": \"...\",\n" +
                                    "  \"yearsOfEHSExperience\": \"...\",\n" +
                                    "  \"location\": \"...\",\n" +
                                    "  \"race\": \"...\"\n" +
                                    "}\n" +
                                    "```")
                    ),
                    "temperature", 0.3
            );
//            Map<String, Object> request = Map.of(
//                    "model", "deepseek-chat",
//                    "messages", List.of(
//                            Map.of("role", "system", "content", "You are a helpful assistant that extracts specific information from resume texts."),
//                            Map.of("role", "user", "content", "Extract the following information from the resume text. If any information is not found, write 'Not specified'.\n\n" +
//                                    "1. Name\n" +
//                                    "2. Gender\n" +
//                                    "3. Age\n" +
//                                    "4. Nationality\n" +
//                                    "5. Location (City)\n" +
//                                    "6. Race\n\n" +
//                                    "Resume text:\n" + text + "\n\n" +
//                                    "Format your response as follows (JSON format):\n" +
//                                    "```json\n" +
//                                    "{\n" +
//                                    "  \"name\": \"...\",\n" +
//                                    "  \"gender\": \"...\",\n" +
//                                    "  \"age\": \"...\",\n" +
//                                    "  \"nationality\": \"...\",\n" +
//                                    "  \"location\": \"...\",\n" +
//                                    "  \"race\": \"...\"\n" +
//                                    "}\n" +
//                                    "```")
//                    ),
//                    "temperature", 0.3
//            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            String response = restTemplate.postForObject(apiUrl, entity, String.class);

            // Parse the response
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(response, Map.class);

            // Extract the assistant's message
            Map<String, Object> choices = ((List<Map<String, Object>>) responseMap.get("choices")).get(0);
            Map<String, Object> message = (Map<String, Object>) choices.get("message");
            String content = (String) message.get("content");

            // Extract JSON from the content
            String jsonStr = extractJsonFromContent(content);

            if (jsonStr != null) {
                Map<String, String> candidateData = mapper.readValue(jsonStr, Map.class);

                return new CandidateInfo(
                        candidateData.getOrDefault("name", "Not specified"),
                        candidateData.getOrDefault("gender", "Not specified"),
                        candidateData.getOrDefault("certificate", "Not specified"),
                        candidateData.getOrDefault("yearsOfEHSExperience", "Not specified"),
                        candidateData.getOrDefault("location", "Not specified"),
                        candidateData.getOrDefault("race", "Not specified"),
                        content
                );
//                return new CandidateInfo(
//                        candidateData.getOrDefault("name", "Not specified"),
//                        candidateData.getOrDefault("gender", "Not specified"),
//                        candidateData.getOrDefault("age", "Not specified"),
//                        candidateData.getOrDefault("nationality", "Not specified"),
//                        candidateData.getOrDefault("location", "Not specified"),
//                        candidateData.getOrDefault("race", "Not specified"),
//                        content
//                );
            } else {
                System.err.println("Failed to extract JSON data from AI response");
                return new CandidateInfo("Parsing Error", "Parsing Error", "Parsing Error",
                        "Parsing Error", "Parsing Error", "Parsing Error", content);
            }
        } catch (Exception e) {
            System.err.println("Error calling AI service: " + e.getMessage());
            return new CandidateInfo("Error", "Error", "Error", "Error", "Error", "Error",
                    "Error calling AI service: " + e.getMessage());
        }
    }

    private String extractJsonFromContent(String content) {
        // Look for JSON content between ```json and ``` markers
        int jsonStart = content.indexOf("```json");
        if (jsonStart == -1) {
            // Try alternative format
            jsonStart = content.indexOf("```");
        }

        if (jsonStart != -1) {
            int contentStart = content.indexOf("{", jsonStart);
            int contentEnd = content.lastIndexOf("}") + 1;

            if (contentStart != -1 && contentEnd != -1 && contentEnd > contentStart) {
                return content.substring(contentStart, contentEnd);
            }
        }

        // If we couldn't find the JSON with markers, try to extract any JSON object
        int firstBrace = content.indexOf("{");
        int lastBrace = content.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return content.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }
}