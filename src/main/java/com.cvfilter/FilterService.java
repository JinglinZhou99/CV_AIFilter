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
            HttpHeaders headers = new HttpHeaders();//Operation
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> request = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
                                    "1. Associate degree or higher, majoring in Business Administration, Marketing, or related fields is preferred.\n\n" +
                                    "2. Fluent in Chinese or English.\n\n" +
                                    "3. Experience in managing large-scale chain operations, with over 5 years of operational management experience in the Thai region, and overseas work experience; experience as a brand manager related to IP(Intellectual Property)is preferred, as well as experience in the Thai market.\n\n" +
                                    "4. Honest and determined, with a strong sense of purpose; excellent ability to integrate resources, analyze, and solve problems; strong communication skills and stress resistance.\n\n" +
                                    "Resume text:\n" + text + "\n\n" +
                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 40 words in Chinese.")
                    ),
                    "temperature", 0.3
            );

//            HttpHeaders headers = new HttpHeaders();//Marketing Thai
//            headers.set("Authorization", "Bearer " + apiKey);
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            Map<String, Object> request = Map.of(
//                    "model", "deepseek-chat",
//                    "messages", List.of(
//                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
//                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
//                                    "1. Bachelor's degree or higher, familiar with retail markets and retail concepts. Overseas study experience is preferred.\n\n" +
//                                    "2. Fluent in Chinese or English.\n\n" +
//                                    "3. Minimum 5 years of experience in retail, marketing promotion, and management. Overseas work experience is required. Preference will be given to candidates with experience in IP-related brands and the Thai market.\n\n" +
//                                    "4. Strong data analysis and planning skills, adept at promotion planning and offline event execution. Ability to effectively coordinate and leverage resources to achieve goals. Strong insight into offline retail scenarios and customer behavior. Self-driven, with a strong sense of responsibility and career ambition.\n\n" +
//                                    "5. Established relationships with shopping mall stakeholders. Strong negotiation, communication, and public relations skills. Ability to handle work pressure.\n\n" +
//                                    "Resume text:\n" + text + "\n\n" +
//                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 40 words in Chinese.")
//                    ),
//                    "temperature", 0.3
//            );

//            HttpHeaders headers = new HttpHeaders(); //EHS
//            headers.set("Authorization", "Bearer " + apiKey);
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            Map<String, Object> request = Map.of(
//                    "model", "deepseek-chat",
//                    "messages", List.of(
//                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
//                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
//                                    "1. Cultural Fit (MOST IMPORTANT): Candidates of Indian nationality/race or with strong religious beliefs are NOT ACCEPTED. Preference given to local Malaysians (who speak Chinese or English) or ethnically Chinese individuals.\n\n" +
//                                    "2. Work Experience: More than 3 years of EHS (Environment, Health, and Safety) work experience. Fresh graduates with relevant internship experience or EHS-related certificates will also be considered.\n\n" +
//                                    "2a. Certifications: Candidates should possess knowledge of or certification in Malaysian EHS standards, particularly the Greenbook (Malaysian environmental management guidelines) or HQ/21/SHO/ series documentation system. These qualifications demonstrate familiarity with national environmental regulations and implementation requirements.\n\n" +
//                                    "3. Education Background: Associate degree or above in Environmental Science, Safety Engineering, Industrial Engineering, or related fields.\n\n" +
//                                    "4. Industry Experience: Priority given to candidates from the hygiene products industry. Candidates from petrochemical or coating industries will also be considered.\n\n" +
//                                    "5. Company Background: Preference for experience working in large Western multinational factory settings (e.g., Henkel, Flint Group, Unica, BASF, etc.).\n\n" +
//                                    "6. Regional Preference: Priority given to local Malaysians (who speak Chinese or English) or ethnically Chinese candidates.\n\n" +
//                                    "7. Quality Management: Understanding of the entire quality management process is preferred.\n\n" +
//                                    "8. Other Abilities: Proficient in office software such as Word, Excel, PPT, etc.\n\n" +
//                                    "Resume text:\n" + text + "\n\n" +
//                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 50 words in Chinese and 50 words in English.")
//                    ),
//                    "temperature", 0.3
//            );

//            HttpHeaders headers = new HttpHeaders();
//            headers.set("Authorization", "Bearer " + apiKey);
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            Map<String, Object> request = Map.of(
//                    "model", "deepseek-chat",
//                    "messages", List.of(
//                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
//                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
//                                    "1. Age: Under 40 years old\n\n" +
//                                    "2. Experience: Minimum 5 years of procurement/product development experience in the Indonesian market\n\n" +
//                                    "3. Resources: Possess quality factory resources and skilled at maintaining and developing factory relationships (B2B)\n\n" +
//                                    "4. Skills: Strong responsiveness, learning ability, communication skills, and stress resistance\n\n" +
//                                    "5. Industry Requirement: Must have experience in new retail industry\n\n" +
//                                    "6. Language Requirements: Indonesian and Chinese\n\n" +
//                                    "Resume text:\n" + text + "\n\n" +
//                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 50 words in Chinese and 50 words in English.")
//                    ),
//                    "temperature", 0.3
//            );


//            HttpHeaders headers = new HttpHeaders();
//            headers.set("Authorization", "Bearer " + apiKey);
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            Map<String, Object> request = Map.of(
//                    "model", "deepseek-chat",
//                    "messages", List.of(
//                            Map.of("role", "system", "content", "You are a helpful assistant that analyzes resumes and determines if they match specific criteria."),
//                            Map.of("role", "user", "content", "Analyze the following resume text and determine if it matches the following criteria in order of importance:\n\n" +
//                                    "1. B2B Experience: Over 8 years of experience in professional services, fintech, payment, or e-commerce industries with a proven track record of success.\n\n" +
//                                    "2. Language Skills: Fluent in English and Indonesian.\n\n" +
//                                    "3. Network Requirements: High-level connections with telecommunications operators, payment companies, and e-commerce companies, such as heads of technology departments, data departments, or C-level executives.\n\n" +
//                                    "4. Preferred Background: Priority given to candidates with startup experience who have contributed to successful businesses.\n\n" +
//                                    "5. Additional Skills: Experience in data source partnerships is highly preferred. Ability to learn quickly and grow rapidly in new industries and markets. Capable of conducting in-depth research, identifying root problems, and proposing effective solutions. Deep business understanding and data-driven mindset preferred.\n\n" +
//                                    "Resume text:\n" + text + "\n\n" +
//                                    "Give a match score from 0-100 on how well this resume matches our requirements. Then, briefly summary this candidate in 50 words in Chinese and 50 words in English.")
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