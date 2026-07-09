package com.p2plending.credit.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Thin wrapper quanh Gemini REST API.
 * Dùng chung cho GeminiAiRiskAssessor và GeminiAiDocumentAnalyzer.
 *
 * Free tier: 15 req/phút, 1.500 req/ngày — đủ cho test/UAT.
 * Lấy API key tại: https://aistudio.google.com/apikey
 */
@Slf4j
class GeminiClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final Object REQUEST_LOCK = new Object();
    private static final long MIN_REQUEST_INTERVAL_MS = 1200L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private static long lastRequestAtMs = 0L;

    GeminiClient(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model) {
        this.restTemplate  = restTemplate;
        this.objectMapper  = objectMapper;
        this.apiKey        = apiKey;
        this.model         = model;
    }

    /**
     * Gọi Gemini với danh sách parts (text + optional inline_data).
     * @return text phản hồi từ model, hoặc null nếu lỗi
     */
    String generateContent(List<Part> parts) {
        try {
            ObjectNode body    = objectMapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode turn    = contents.addObject();
            ArrayNode partsArr = turn.putArray("parts");

            for (Part p : parts) {
                ObjectNode partNode = partsArr.addObject();
                if (p.text() != null) {
                    partNode.put("text", p.text());
                } else {
                    ObjectNode inlineData = partNode.putObject("inline_data");
                    inlineData.put("mime_type", p.mimeType());
                    inlineData.put("data", p.base64Data());
                }
            }

            // Giảm randomness; KHÔNG đặt response_mime_type — JSON mode + inline PDF
            // khiến Gemini trả candidates rỗng (finishReason SAFETY/OTHER) với sao kê ngân hàng.
            // JSON schema đã được mô tả trong prompt, Gemini vẫn trả JSON mà không cần ràng buộc này.
            ObjectNode genConfig = body.putObject("generationConfig");
            genConfig.put("temperature", 0.1);
            genConfig.put("maxOutputTokens", 8192);

            ArrayNode safetySettings = body.putArray("safetySettings");
            for (String category : List.of(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
            )) {
                ObjectNode setting = safetySettings.addObject();
                setting.put("category", category);
                setting.put("threshold", "BLOCK_NONE");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = BASE_URL.formatted(model, apiKey);
            ResponseEntity<JsonNode> response = postWithRetry(url, body, headers);

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Gemini API returned empty response body");
                return null;
            }

            // Log blockReason nếu request bị từ chối bởi safety filter
            JsonNode blockReason = responseBody.path("promptFeedback").path("blockReason");
            if (!blockReason.isMissingNode() && !blockReason.isNull()) {
                log.warn("Gemini API blocked request: blockReason={}", blockReason.asText());
            }

            JsonNode candidate = responseBody.path("candidates").path(0);
            if (candidate.isMissingNode() || candidate.isNull()) {
                log.warn("Gemini API response has no candidates: {}", responseBody);
                return null;
            }

            StringBuilder textBuilder = new StringBuilder();
            JsonNode textParts = candidate.path("content").path("parts");
            if (textParts.isArray()) {
                for (JsonNode part : textParts) {
                    String value = part.path("text").asText(null);
                    if (value != null && !value.isBlank()) {
                        textBuilder.append(value);
                    }
                }
            }
            String text = textBuilder.isEmpty() ? null : textBuilder.toString();
            if (text == null || text.isBlank()) {
                log.warn("Gemini API response has no text part. finishReason={} response={}",
                        candidate.path("finishReason").asText(null), responseBody);
                return null;
            }
            return text;

        } catch (ResourceAccessException e) {
            log.error("Gemini API network/timeout error: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private ResponseEntity<JsonNode> postWithRetry(String url, ObjectNode body, HttpHeaders headers) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                throttleGeminiRequests();
                return restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 429 || attempt == maxAttempts) {
                    log.error("Gemini API HTTP error status={} attempt={}/{} body={}",
                            e.getStatusCode(), attempt, maxAttempts, truncate(e.getResponseBodyAsString()), e);
                    throw e;
                }

                long retryDelayMs = retryDelayMs(e.getResponseBodyAsString(), attempt);
                log.warn("Gemini API rate limited status={} attempt={}/{} retryAfterMs={} body={}",
                        e.getStatusCode(), attempt, maxAttempts, retryDelayMs,
                        truncate(e.getResponseBodyAsString()));
                sleepQuietly(retryDelayMs);
            }
        }
        throw new IllegalStateException("Gemini API retry loop ended unexpectedly");
    }

    private long retryDelayMs(String responseBody, int attempt) {
        long fallback = 5000L * attempt;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            for (JsonNode detail : root.path("error").path("details")) {
                String retryDelay = detail.path("retryDelay").asText(null);
                if (retryDelay != null && retryDelay.endsWith("s")) {
                    long seconds = Long.parseLong(retryDelay.substring(0, retryDelay.length() - 1));
                    return Math.max(fallback, (seconds + 1L) * 1000L);
                }
            }
        } catch (Exception ignored) {
            // Không parse được RetryInfo thì dùng fallback.
        }
        return fallback;
    }

    private void throttleGeminiRequests() {
        synchronized (REQUEST_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAtMs);
            if (waitMs > 0) {
                sleepQuietly(waitMs);
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request interrupted while waiting for retry/throttle", e);
        }
    }

    private String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        int max = 4000;
        return body.length() <= max ? body : body.substring(0, max) + "...[truncated]";
    }

    record Part(String text, String mimeType, String base64Data) {
        static Part text(String text)                              { return new Part(text, null, null); }
        static Part file(String mimeType, String base64Data)      { return new Part(null, mimeType, base64Data); }
    }
}
