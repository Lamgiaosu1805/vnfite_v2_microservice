package com.p2plending.matching.controller;

import com.p2plending.matching.dto.request.InvestorPreferenceRequest;
import com.p2plending.matching.dto.response.MatchRecordResponse;
import com.p2plending.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matchingService;

    /**
     * GET /api/matches/{loanId}
     * Returns all match records for a given loan, sorted by score descending.
     */
    @GetMapping("/{loanId}")
    public ResponseEntity<List<MatchRecordResponse>> getMatchesForLoan(
            @PathVariable String loanId) {
        return ResponseEntity.ok(matchingService.getMatchesForLoan(loanId));
    }

    /**
     * POST /api/matches/preferences
     * Investor registers or updates their lending preferences.
     * Requires X-Investor-Id header (set by API gateway after JWT validation).
     */
    @PostMapping("/preferences")
    public ResponseEntity<Void> upsertPreference(
            @RequestHeader("X-Investor-Id") String investorId,
            @Valid @RequestBody InvestorPreferenceRequest request) {
        matchingService.upsertPreference(investorId, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
