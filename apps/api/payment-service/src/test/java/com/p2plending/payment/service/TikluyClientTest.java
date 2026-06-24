package com.p2plending.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TikluyClientTest {

    @Test
    void fundTransferBodyCarriesVnfiteSourceForYfchRouting() {
        Map<String, Object> body = TikluyClient.buildFundTransferBody(
                "MB", "0123456789", "100000.00", "VNFITE");

        assertEquals("VNFITE", body.get("source"));
        assertEquals("MB", body.get("bankCode"));
        assertEquals("0123456789", body.get("creditResourceNumber"));
        assertEquals("100000", body.get("transferAmount"));
    }

    @Test
    void fundTransferBodyRejectsFractionalVndAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> TikluyClient.buildFundTransferBody(
                        "MB", "0123456789", "100000.50", "VNFITE"));
    }

    @Test
    void fundTransferBodyRejectsMissingSourceInsteadOfFallingBackTo6rch() {
        assertThrows(IllegalStateException.class,
                () -> TikluyClient.buildFundTransferBody("MB", "0123456789", "100000", " "));
        assertThrows(IllegalStateException.class,
                () -> TikluyClient.buildFundTransferBody("MB", "0123456789", "100000", "UNKNOWN"));
    }

    @Test
    void transferStatusParsesTerminalMbResult() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"errorCode":"000","data":{"transStatus":"SUCCESS","ft":"FT123"}}
                """);

        TikluyClient.TransferQueryResult result = TikluyClient.parseTransferStatus(json);

        assertEquals(TikluyClient.TransferState.SUCCESS, result.state());
        assertEquals("FT123", result.ftNumber());
    }

    @Test
    void transferInitiationReadsRealYfchReferenceFromTikluyResponse() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"errorCode":"000","data":{"transactionId":"YFCHAbc12345", "status":"PROCESSING", "ftNumber":""}}
                """);

        TikluyClient.TransferInitiation result = TikluyClient.parseTransferInitiation(json);

        assertEquals("YFCHAbc12345", result.providerReference());
        assertEquals(TikluyClient.TransferState.PROCESSING, result.state());
    }

    @Test
    void unknownTransferStatusRemainsProcessingInsteadOfRetrying() throws Exception {
        var json = new ObjectMapper().readTree("""
                {"errorCode":"000","data":{"transStatus":"WAITING","ft":""}}
                """);

        TikluyClient.TransferQueryResult result = TikluyClient.parseTransferStatus(json);

        assertEquals(TikluyClient.TransferState.PROCESSING, result.state());
    }
}
