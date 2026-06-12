package com.p2plending.credit.service;

import com.p2plending.credit.domain.entity.ScoringCriteria;
import com.p2plending.credit.domain.repository.ScoringCriteriaRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Engine chấm điểm rule-based theo scorecard trong bảng scoring_criteria.
 *
 * Nguyên tắc:
 *  - Tiêu chí thiếu dữ liệu → nhận ĐIỂM SÀN (band điểm thấp nhất của tiêu chí),
 *    KHÔNG phải 0 cứng. Lý do: hồ sơ chưa có dữ liệu (vd khách mới chưa có lịch sử,
 *    chưa khai DTI) không nên bị phạt về đáy như tín hiệu xấu thật. Vẫn flag vào
 *    missingData để nhắc thẩm định viên thu thập (bổ sung có thể nâng lên tới band cao).
 *  - maxPoints của mỗi tiêu chí = band có điểm cao nhất; mẫu số luôn tính đủ maxPoints
 *    nên không vống điểm khi hồ sơ mỏng.
 *  - Tổng maxPoints tính từ DB → đổi scorecard không cần sửa code
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringEngine {

    private final ScoringCriteriaRepository criteriaRepository;

    @Transactional(readOnly = true)
    public EngineResult evaluate(Map<String, Object> features) {
        List<ScoringCriteria> all = criteriaRepository.findByActiveTrueAndIsDeletedFalseOrderByCriteriaCodeAscPointsDesc();

        // Gom band theo criteria_code, giữ thứ tự
        Map<String, List<ScoringCriteria>> grouped = new LinkedHashMap<>();
        for (ScoringCriteria c : all) {
            grouped.computeIfAbsent(c.getCriteriaCode(), k -> new ArrayList<>()).add(c);
        }

        List<CriteriaResult> results = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int total = 0;
        int maxTotal = 0;

        for (Map.Entry<String, List<ScoringCriteria>> entry : grouped.entrySet()) {
            String code = entry.getKey();
            List<ScoringCriteria> bands = entry.getValue();
            int maxPoints = bands.stream().mapToInt(ScoringCriteria::getPoints).max().orElse(0);
            int minPoints = bands.stream().mapToInt(ScoringCriteria::getPoints).min().orElse(0);
            maxTotal += maxPoints;

            ScoringCriteria first = bands.get(0);
            Object value = features.get(code);

            int points;
            String rawValue;

            if (value == null) {
                // Thiếu dữ liệu → điểm sàn của tiêu chí (không phạt về 0 như tín hiệu xấu thật)
                points = minPoints;
                rawValue = "(thiếu dữ liệu)";
                missing.add(code);
            } else {
                rawValue = String.valueOf(value);
                ScoringCriteria matched = matchBand(bands, value);
                points = matched != null ? matched.getPoints() : 0;
            }

            total += points;
            results.add(CriteriaResult.builder()
                    .criteriaCode(code)
                    .criteriaName(first.getCriteriaName())
                    .component(first.getComponent())
                    .rawValue(rawValue)
                    .points(points)
                    .maxPoints(maxPoints)
                    .build());
        }

        return EngineResult.builder()
                .totalPoints(total)
                .maxPoints(maxTotal)
                .details(results)
                .missingData(missing)
                .build();
    }

    private ScoringCriteria matchBand(List<ScoringCriteria> bands, Object value) {
        for (ScoringCriteria band : bands) {
            if (band.getMatchValue() != null) {
                // Categorical
                if (band.getMatchValue().equalsIgnoreCase(String.valueOf(value))) {
                    return band;
                }
            } else {
                // Numeric: min <= v < max (null = không chặn)
                BigDecimal v = toBigDecimal(value);
                if (v == null) continue;
                boolean geMin = band.getMinValue() == null || v.compareTo(band.getMinValue()) >= 0;
                boolean ltMax = band.getMaxValue() == null || v.compareTo(band.getMaxValue()) < 0;
                if (geMin && ltMax) {
                    return band;
                }
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Data
    @Builder
    public static class EngineResult {
        private int totalPoints;
        private int maxPoints;
        private List<CriteriaResult> details;
        private List<String> missingData;
    }

    @Data
    @Builder
    public static class CriteriaResult {
        private String criteriaCode;
        private String criteriaName;
        private String component;
        private String rawValue;
        private int points;
        private int maxPoints;
    }
}
