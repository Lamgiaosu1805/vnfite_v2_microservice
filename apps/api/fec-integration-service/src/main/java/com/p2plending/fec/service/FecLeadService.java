package com.p2plending.fec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.fec.config.FecProperties;
import com.p2plending.fec.dto.FecLeadStatusCallbackRequest;
import com.p2plending.fec.dto.FecLeadSubmitRequest;
import com.p2plending.fec.dto.FecLeadSubmitResponse;
import com.p2plending.fec.dto.FecReceiveLeadRequest;
import com.p2plending.fec.dto.FecReceiveLeadResponse;
import com.p2plending.fec.util.RsaCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FecLeadService {

    private final FecProperties properties;
    private final RestTemplate fecRestTemplate;
    private final ObjectMapper objectMapper;

    public FecLeadSubmitResponse submitLead(FecLeadSubmitRequest request) {
        ensureEnabled();
        ensureOutboundConfig();

        String transId = request.getTransId() == null || request.getTransId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getTransId().trim();

        FecReceiveLeadRequest fecRequest = FecReceiveLeadRequest.builder()
                .transId(transId)
                .fullName(encrypt(request.getFullName()))
                .phoneNumber(encrypt(request.getPhoneNumber()))
                .nid(encrypt(request.getNid()))
                .dob(encrypt(nullToBlank(request.getDob())))
                .email(encrypt(nullToBlank(request.getEmail())))
                .loanAmount(request.getLoanAmount())
                .tenor(request.getTenor())
                .leadSource(defaultText(request.getLeadSource(), properties.getLeadSource()))
                .agentCode(defaultText(request.getAgentCode(), properties.getPartnerCode()))
                .consentType(request.getConsentType())
                .consentTickbox(request.getConsentTickbox())
                .consentContent(request.getConsentContent())
                .build();

        try {
            String body = objectMapper.writeValueAsString(fecRequest);
            String signature = RsaCrypto.signSha256(body, properties.getPartnerPrivateKey());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getBearerToken());
            headers.set("signature", signature);
            headers.set("partnerCode", properties.getPartnerCode());

            ResponseEntity<String> response = fecRestTemplate.exchange(
                    properties.getReceiveLeadUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            String responseBody = response.getBody() == null ? "" : response.getBody();
            boolean validResponseSignature = verifyResponseSignature(body, responseBody, response.getHeaders().getFirst("Signature"));
            FecReceiveLeadResponse fecResponse = objectMapper.readValue(responseBody, FecReceiveLeadResponse.class);
            FecReceiveLeadResponse.Data data = fecResponse.getData();

            log.info("FEC lead submitted: transId={}, code={}, leadStatus={}, leadGenId={}",
                    transId, fecResponse.getCode(),
                    data != null ? data.getLeadStatus() : null,
                    data != null ? data.getLeadGenId() : null);

            return FecLeadSubmitResponse.builder()
                    .transId(fecResponse.getTransId())
                    .code(fecResponse.getCode())
                    .description(fecResponse.getDescription())
                    .leadStatus(data != null ? data.getLeadStatus() : null)
                    .leadGenId(data != null ? data.getLeadGenId() : null)
                    .onboardingLink(data != null ? data.getLink() : null)
                    .responseSignatureValid(validResponseSignature)
                    .build();
        } catch (RestClientResponseException ex) {
            log.warn("FEC UAT receive-leads error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(ex.getStatusCode(), sourceMessage(ex));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cannot submit FEC lead: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể gửi lead sang FEC UAT");
        }
    }

    public void handleStatusCallback(String apiKey, String signatureHeader, String rawBody, FecLeadStatusCallbackRequest body) {
        ensureCallbackConfig();
        if (!properties.getCallbackApiKey().equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid x-api-key");
        }
        String signature = RsaCrypto.extractSignatureValue(signatureHeader);
        if (!RsaCrypto.verifySha256(rawBody, signature, properties.getFecPublicKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid signature");
        }
        log.info("FEC callback accepted: leadGenId={}, status={}, appId={}, appType={}, offerAmt={}, cashAmt={}",
                body.getLeadgenId(), body.getStatus(), body.getAppId(), body.getAppType(),
                body.getOfferAmt(), body.getCashAmt());
    }

    private String encrypt(String value) {
        return RsaCrypto.encryptPkcs1(value, properties.getFecPublicKey());
    }

    private boolean verifyResponseSignature(String requestBody, String responseBody, String signatureHeader) {
        if (properties.getFecPublicKey() == null || properties.getFecPublicKey().isBlank()) return false;
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        String signature = RsaCrypto.extractSignatureValue(signatureHeader);
        return RsaCrypto.verifySha256(requestBody + "|" + responseBody, signature, properties.getFecPublicKey());
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FEC integration is disabled");
        }
    }

    private void ensureOutboundConfig() {
        if (isBlank(properties.getReceiveLeadUrl())
                || isBlank(properties.getPartnerCode())
                || isBlank(properties.getBearerToken())
                || isBlank(properties.getPartnerPrivateKey())
                || isBlank(properties.getFecPublicKey())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Thiếu cấu hình FEC UAT");
        }
    }

    private void ensureCallbackConfig() {
        if (isBlank(properties.getCallbackApiKey()) || isBlank(properties.getFecPublicKey())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Thiếu cấu hình callback FEC");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String sourceMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body == null || body.isBlank() ? "FEC UAT trả lỗi " + ex.getStatusCode().value() : body;
    }
}
