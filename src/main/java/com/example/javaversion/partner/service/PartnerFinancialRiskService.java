package com.example.javaversion.partner.service;

import com.example.javaversion.database.entity.FinancialStatementData;
import com.example.javaversion.database.repository.FinancialStatementDataRepository;
import com.example.javaversion.partner.dto.FinancialRiskAssessmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerFinancialRiskService {

    private final FinancialStatementDataRepository financialStatementDataRepository;

    // DART API 응답의 계정 과목명 또는 XBRL 표준 계정 ID를 기반으로 정의해야 합니다.
    // 실제 응답을 보고 정확히 맞춰야 하며, 여기서는 일반적인 명칭을 사용합니다.
    private static final String ACC_REVENUE = "매출액"; // ifrs-full_Revenue, 수익(매출액)
    private static final String ACC_OPERATING_INCOME = "영업이익"; // ifrs-full_ProfitLossFromOperatingActivities, 영업이익(손실)
    private static final String ACC_TRADE_RECEIVABLES = "매출채권"; // ifrs-full_TradeAndOtherCurrentReceivables
    // private static final String ACC_COST_OF_SALES = "매출원가"; // ifrs-full_CostOfSales (매입채무회전율에 사용)
    private static final String ACC_TRADE_PAYABLES = "매입채무"; // ifrs-full_TradeAndOtherCurrentPayables
    private static final String ACC_CASHFLOW_OPERATING = "영업활동으로인한현금흐름"; // ifrs-full_CashFlowsFromUsedInOperatingActivities (실제 계정명 확인 필요)
    private static final String ACC_TOTAL_ASSETS = "자산총계"; // ifrs-full_Assets
    private static final String ACC_TOTAL_LIABILITIES = "부채총계"; // ifrs-full_Liabilities
    private static final String ACC_TOTAL_EQUITY = "자본총계"; // ifrs-full_Equity
    private static final String ACC_PAID_IN_CAPITAL = "자본금";    // ifrs-full_IssuedCapital
    private static final String ACC_SHORT_TERM_BORROWINGS = "단기차입금"; // ifrs-full_ShorttermBorrowings
    // 총차입금은 단기차입금 + 유동성장기부채 + 장기차입금 + 사채 등으로 구성되거나, 직접 제공될 수 있음
    // 여기서는 단기차입금과 장기차입금(별도 항목 가정)을 사용하거나, 자산총계 대비로만 판단
    private static final String ACC_LONG_TERM_BORROWINGS = "장기차입금"; // ifrs-full_LongtermBorrowings

    public FinancialRiskAssessmentDto assessFinancialRisk(String partnerCorpCode, String partnerName, String bsnsYear, String reprtCode) {
        log.info("파트너사 재무 위험 분석 요청 (DB 조회): 회사코드={}, 회사명={}, 사업연도={}, 보고서코드={}",
                partnerCorpCode, partnerName, bsnsYear, reprtCode);

        // DB에서 재무제표 데이터 조회
        List<FinancialStatementData> financialStatementItems = 
            financialStatementDataRepository.findByCorpCodeAndBsnsYearAndReprtCode(partnerCorpCode, bsnsYear, reprtCode);

        if (financialStatementItems == null || financialStatementItems.isEmpty()) {
            log.warn("DB에서 회사코드 {} ({}) 에 대한 {}년도 {} 재무 데이터를 찾을 수 없습니다. Kafka 컨슈머가 아직 데이터를 저장하지 않았거나 해당 데이터가 없을 수 있습니다.",
                    partnerCorpCode, partnerName, bsnsYear, reprtCode);
            // 데이터 준비 중 또는 없음을 나타내는 메시지 포함하여 반환
            Map<String, FinancialRiskAssessmentDto.RiskItemResult> emptyRiskItems = new HashMap<>();
            emptyRiskItems.put("정보 조회", FinancialRiskAssessmentDto.RiskItemResult.builder()
                                        .description("재무 정보 조회")
                                        .isAtRisk(true) // 정보 없음을 위험으로 간주할 수도 있음
                                        .actualValue("데이터 없음")
                                        .threshold("-")
                                        .notes("요청된 조건의 재무제표 데이터가 내부 DB에 없습니다. 데이터 동기화 중이거나 아직 제공되지 않은 정보일 수 있습니다. 잠시 후 다시 시도해주세요.")
                                        .build());
            return FinancialRiskAssessmentDto.builder()
                    .partnerCompanyId(partnerCorpCode)
                    .partnerCompanyName(partnerName)
                    .assessmentYear(bsnsYear)
                    .reportCode(reprtCode)
                    .riskItems(emptyRiskItems)
                    .build();
        }

        Map<String, FinancialRiskAssessmentDto.RiskItemResult> riskItemsResult = new HashMap<>();
        
        // 체크리스트 항목별 분석
        riskItemsResult.put("매출액 30% 이상 감소", checkRevenueDecrease(financialStatementItems));
        riskItemsResult.put("영업이익 30% 이상 감소", checkOperatingIncomeDecrease(financialStatementItems));
        riskItemsResult.put("매출채권회전율 3회 이하", checkReceivablesTurnover(financialStatementItems));
        riskItemsResult.put("매출채권 잔액이 매출액의 50% 이상", checkReceivablesToSalesRatio(financialStatementItems));
        riskItemsResult.put("매입채무회전율 2회 이하", checkPayablesTurnover(financialStatementItems));
        riskItemsResult.put("영업손실(적자) 발생", checkOperatingLoss(financialStatementItems));
        riskItemsResult.put("영업활동 현금흐름 적자", checkOperatingCashflowDeficit(financialStatementItems));
        riskItemsResult.put("차입금 30% 이상 증가", checkBorrowingsIncrease(financialStatementItems));
        riskItemsResult.put("차입금이 자산의 50% 이상", checkBorrowingsToAssetsRatio(financialStatementItems));
        riskItemsResult.put("단기차입금이 전체차입금의 90% 이상", checkShortTermBorrowingsRatio(financialStatementItems));
        riskItemsResult.put("부채비율 200% 이상", checkDebtToEquityRatio(financialStatementItems));
        riskItemsResult.put("납입자본금 잠식", checkCapitalImpairment(financialStatementItems));

        log.info("파트너사 재무 위험 분석 완료 (DB 기반): 회사명={}", partnerName);
        return FinancialRiskAssessmentDto.builder()
                .partnerCompanyId(partnerCorpCode)
                .partnerCompanyName(partnerName)
                .assessmentYear(bsnsYear)
                .reportCode(reprtCode)
                .riskItems(riskItemsResult)
                .build();
    }

    private Optional<BigDecimal> findFinancialValue(List<FinancialStatementData> items, String accountName, String termField) {
        return items.stream()
                .filter(item -> accountName.equals(item.getAccountNm()))
                .map(item -> {
                    try {
                        String amountStr;
                        switch (termField) {
                            case "thstrm_amount": amountStr = item.getThstrmAmount(); break;
                            case "frmtrm_amount": amountStr = item.getFrmtrmAmount(); break;
                            case "thstrm_add_amount": amountStr = item.getThstrmAddAmount(); break;
                            case "frmtrm_add_amount": amountStr = item.getFrmtrmAddAmount(); break;
                            default: return Optional.<BigDecimal>empty();
                        }
                        if (amountStr == null || amountStr.trim().isEmpty() || "-".equals(amountStr.trim())) {
                            return Optional.<BigDecimal>empty();
                        }
                        return Optional.of(new BigDecimal(amountStr.replaceAll(",", "")));
                    } catch (NumberFormatException e) {
                        log.warn("금액 변환 오류: 계정명={}, 값(termField)={}, 원본값='{}', 회사코드={}, 사업연도={}, 보고서코드={}", 
                                 accountName, termField, item.getThstrmAmount(), item.getCorpCode(), item.getBsnsYear(), item.getReprtCode(), e);
                        return Optional.<BigDecimal>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    // 각 체크리스트 항목별 계산 메소드들
    private FinancialRiskAssessmentDto.RiskItemResult buildResult(String desc, String threshold, boolean isRisk, String actualFormatted, String notes) {
        return FinancialRiskAssessmentDto.RiskItemResult.builder()
                .description(desc)
                .threshold(threshold)
                .isAtRisk(isRisk)
                .actualValue(actualFormatted)
                .notes(notes)
                .build();
    }
    
    // 1. 매출액 30% 이상 감소
    private FinancialRiskAssessmentDto.RiskItemResult checkRevenueDecrease(List<FinancialStatementData> items) {
        String desc = "매출액 30% 이상 감소";
        String threshold = "<= -30%";
        Optional<BigDecimal> currentRevenueOpt = findFinancialValue(items, ACC_REVENUE, "thstrm_amount");
        Optional<BigDecimal> previousRevenueOpt = findFinancialValue(items, ACC_REVENUE, "frmtrm_amount");

        if (currentRevenueOpt.isPresent() && previousRevenueOpt.isPresent()) {
            BigDecimal currentRevenue = currentRevenueOpt.get();
            BigDecimal previousRevenue = previousRevenueOpt.get();
            if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, false, "전기 매출액 0", "전기 매출액이 0이므로 증감률 계산 불가");
            }
            BigDecimal changePercent = currentRevenue.subtract(previousRevenue)
                                        .divide(previousRevenue.abs(), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));
            boolean isRisk = changePercent.compareTo(new BigDecimal("-30")) <= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", changePercent), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "매출액(당기 또는 전기) 정보 없음");
        }
    }

    // 2. 영업이익 30% 이상 감소
    private FinancialRiskAssessmentDto.RiskItemResult checkOperatingIncomeDecrease(List<FinancialStatementData> items) {
        String desc = "영업이익 30% 이상 감소";
        String threshold = "<= -30% (단, 전기 영업이익 > 0)";
        Optional<BigDecimal> currentOpt = findFinancialValue(items, ACC_OPERATING_INCOME, "thstrm_amount");
        Optional<BigDecimal> previousOpt = findFinancialValue(items, ACC_OPERATING_INCOME, "frmtrm_amount");

        if (currentOpt.isPresent() && previousOpt.isPresent()) {
            BigDecimal current = currentOpt.get();
            BigDecimal previous = previousOpt.get();
            if (previous.compareTo(BigDecimal.ZERO) <= 0) {
                 return buildResult(desc, threshold, false, String.format("전기 영업이익: %,.0f", previous), "전기 영업이익이 0 이하이므로 증감률 비교 무의미");
            }
            BigDecimal changePercent = current.subtract(previous)
                                        .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));
            boolean isRisk = changePercent.compareTo(new BigDecimal("-30")) <= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", changePercent), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "영업이익(당기 또는 전기) 정보 없음");
        }
    }
    
    // 3. 매출채권회전율 3회 이하
    private FinancialRiskAssessmentDto.RiskItemResult checkReceivablesTurnover(List<FinancialStatementData> items) {
        String desc = "매출채권회전율 3회 이하";
        String threshold = "<= 3회";
        Optional<BigDecimal> revenueOpt = findFinancialValue(items, ACC_REVENUE, "thstrm_amount");
        Optional<BigDecimal> receivablesOpt = findFinancialValue(items, ACC_TRADE_RECEIVABLES, "thstrm_amount"); 

        if (revenueOpt.isPresent() && receivablesOpt.isPresent()) {
            BigDecimal revenue = revenueOpt.get();
            BigDecimal receivables = receivablesOpt.get();
            if (receivables.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, false, "매출채권 0", "매출채권이 0이므로 회전율 계산 불가 (또는 무한대)");
            }
            BigDecimal turnover = revenue.divide(receivables, 2, RoundingMode.HALF_UP);
            boolean isRisk = turnover.compareTo(new BigDecimal("3")) <= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f회", turnover), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "매출액 또는 매출채권 정보 없음");
        }
    }

    // 4. 매출채권 잔액이 매출액의 50% 이상
    private FinancialRiskAssessmentDto.RiskItemResult checkReceivablesToSalesRatio(List<FinancialStatementData> items) {
        String desc = "매출채권 잔액이 매출액의 50% 이상";
        String threshold = ">= 50%";
        Optional<BigDecimal> revenueOpt = findFinancialValue(items, ACC_REVENUE, "thstrm_amount");
        Optional<BigDecimal> receivablesOpt = findFinancialValue(items, ACC_TRADE_RECEIVABLES, "thstrm_amount");

        if (revenueOpt.isPresent() && receivablesOpt.isPresent()) {
            BigDecimal revenue = revenueOpt.get();
            BigDecimal receivables = receivablesOpt.get();
            if (revenue.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, receivables.compareTo(BigDecimal.ZERO) > 0, "매출액 0", "매출액이 0, 매출채권 존재시 100% 이상으로 간주");
            }
            BigDecimal ratio = receivables.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            boolean isRisk = ratio.compareTo(new BigDecimal("50")) >= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", ratio), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "매출액 또는 매출채권 정보 없음");
        }
    }

    // 5. 매입채무회전율 2회 이하
    private FinancialRiskAssessmentDto.RiskItemResult checkPayablesTurnover(List<FinancialStatementData> items) {
        String desc = "매입채무회전율 2회 이하";
        String threshold = "<= 2회";
        Optional<BigDecimal> costOfSalesOpt = findFinancialValue(items, ACC_REVENUE, "thstrm_amount"); 
        Optional<BigDecimal> payablesOpt = findFinancialValue(items, ACC_TRADE_PAYABLES, "thstrm_amount");

        if (costOfSalesOpt.isPresent() && payablesOpt.isPresent()) {
            BigDecimal costOfSales = costOfSalesOpt.get();
            BigDecimal payables = payablesOpt.get();
             if (payables.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, false, "매입채무 0", "매입채무가 0이므로 회전율 계산 불가 (또는 무한대)");
            }
            BigDecimal turnover = costOfSales.divide(payables, 2, RoundingMode.HALF_UP);
            boolean isRisk = turnover.compareTo(new BigDecimal("2")) <= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f회 (매출액 기준)", turnover), "매출원가 대신 매출액 사용으로 정확도 낮음");
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "매출액(또는 매출원가) 또는 매입채무 정보 없음");
        }
    }

    // 6. 영업손실(적자) 발생
    private FinancialRiskAssessmentDto.RiskItemResult checkOperatingLoss(List<FinancialStatementData> items) {
        String desc = "영업손실(적자) 발생";
        String threshold = "< 0";
        Optional<BigDecimal> operatingIncomeOpt = findFinancialValue(items, ACC_OPERATING_INCOME, "thstrm_amount");
        if (operatingIncomeOpt.isPresent()) {
            BigDecimal operatingIncome = operatingIncomeOpt.get();
            boolean isRisk = operatingIncome.compareTo(BigDecimal.ZERO) < 0;
            return buildResult(desc, threshold, isRisk, String.format("%,.0f", operatingIncome), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "영업이익 정보 없음");
        }
    }

    // 7. 영업활동 현금흐름 적자
    private FinancialRiskAssessmentDto.RiskItemResult checkOperatingCashflowDeficit(List<FinancialStatementData> items) {
        String desc = "영업활동 현금흐름 적자";
        String threshold = "< 0";
        Optional<BigDecimal> cashflowOpt = findFinancialValue(items, ACC_CASHFLOW_OPERATING, "thstrm_amount");
        if (cashflowOpt.isPresent()) {
            BigDecimal cashflow = cashflowOpt.get();
            boolean isRisk = cashflow.compareTo(BigDecimal.ZERO) < 0;
            return buildResult(desc, threshold, isRisk, String.format("%,.0f", cashflow), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "영업활동 현금흐름 정보 없음");
        }
    }

    // 8. 차입금 30% 이상 증가
    private FinancialRiskAssessmentDto.RiskItemResult checkBorrowingsIncrease(List<FinancialStatementData> items) {
        String desc = "총차입금 30% 이상 증가";
        String threshold = ">= 30%";
        Optional<BigDecimal> currentShortTermOpt = findFinancialValue(items, ACC_SHORT_TERM_BORROWINGS, "thstrm_amount");
        Optional<BigDecimal> currentLongTermOpt = findFinancialValue(items, ACC_LONG_TERM_BORROWINGS, "thstrm_amount");
        Optional<BigDecimal> previousShortTermOpt = findFinancialValue(items, ACC_SHORT_TERM_BORROWINGS, "frmtrm_amount");
        Optional<BigDecimal> previousLongTermOpt = findFinancialValue(items, ACC_LONG_TERM_BORROWINGS, "frmtrm_amount");

        BigDecimal currentTotalBorrowings = sum(currentShortTermOpt, currentLongTermOpt);
        BigDecimal previousTotalBorrowings = sum(previousShortTermOpt, previousLongTermOpt);

        if (currentTotalBorrowings.compareTo(BigDecimal.ZERO) >= 0 && previousTotalBorrowings.compareTo(BigDecimal.ZERO) >=0) {
             if (previousTotalBorrowings.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, currentTotalBorrowings.compareTo(BigDecimal.ZERO) > 0, 
                                   String.format("당기: %,.0f", currentTotalBorrowings), "전기 총차입금 0");
            }
            BigDecimal changePercent = currentTotalBorrowings.subtract(previousTotalBorrowings)
                                        .divide(previousTotalBorrowings.abs(), 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));
            boolean isRisk = changePercent.compareTo(new BigDecimal("30")) >= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", changePercent), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "차입금(당기 또는 전기) 정보 부족");
        }
    }

    // 9. 차입금이 자산의 50% 이상
    private FinancialRiskAssessmentDto.RiskItemResult checkBorrowingsToAssetsRatio(List<FinancialStatementData> items) {
        String desc = "총차입금이 자산총계의 50% 이상";
        String threshold = ">= 50%";
        Optional<BigDecimal> currentShortTermOpt = findFinancialValue(items, ACC_SHORT_TERM_BORROWINGS, "thstrm_amount");
        Optional<BigDecimal> currentLongTermOpt = findFinancialValue(items, ACC_LONG_TERM_BORROWINGS, "thstrm_amount");
        Optional<BigDecimal> totalAssetsOpt = findFinancialValue(items, ACC_TOTAL_ASSETS, "thstrm_amount");
        
        BigDecimal currentTotalBorrowings = sum(currentShortTermOpt, currentLongTermOpt);

        if (currentTotalBorrowings.compareTo(BigDecimal.ZERO) >=0 && totalAssetsOpt.isPresent()) {
            BigDecimal totalAssets = totalAssetsOpt.get();
             if (totalAssets.compareTo(BigDecimal.ZERO) == 0) {
                return buildResult(desc, threshold, currentTotalBorrowings.compareTo(BigDecimal.ZERO) > 0, "자산총계 0", "자산총계가 0, 차입금 존재시 100% 이상으로 간주");
            }
            BigDecimal ratio = currentTotalBorrowings.divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            boolean isRisk = ratio.compareTo(new BigDecimal("50")) >= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", ratio), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "총차입금 또는 자산총계 정보 없음");
        }
    }

    // 10. 단기차입금이 전체차입금의 90% 이상
    private FinancialRiskAssessmentDto.RiskItemResult checkShortTermBorrowingsRatio(List<FinancialStatementData> items) {
        String desc = "단기차입금이 전체차입금의 90% 이상";
        String threshold = ">= 90%";
        Optional<BigDecimal> shortTermOpt = findFinancialValue(items, ACC_SHORT_TERM_BORROWINGS, "thstrm_amount");
        Optional<BigDecimal> longTermOpt = findFinancialValue(items, ACC_LONG_TERM_BORROWINGS, "thstrm_amount");

        BigDecimal totalBorrowings = sum(shortTermOpt, longTermOpt);

        if (shortTermOpt.isPresent() && totalBorrowings.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shortTerm = shortTermOpt.get();
            BigDecimal ratio = shortTerm.divide(totalBorrowings, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            boolean isRisk = ratio.compareTo(new BigDecimal("90")) >= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", ratio), null);
        } else if (shortTermOpt.isPresent() && totalBorrowings.compareTo(BigDecimal.ZERO) == 0) {
            return buildResult(desc, threshold, false, "총차입금 0", "단기차입금 존재하나 총차입금 0");
        }else {
            return buildResult(desc, threshold, false, "데이터 부족", "단기차입금 또는 총차입금 정보 없음");
        }
    }

    // 11. 부채비율 200% 이상
    private FinancialRiskAssessmentDto.RiskItemResult checkDebtToEquityRatio(List<FinancialStatementData> items) {
        String desc = "부채비율 200% 이상";
        String threshold = ">= 200%";
        Optional<BigDecimal> totalLiabilitiesOpt = findFinancialValue(items, ACC_TOTAL_LIABILITIES, "thstrm_amount");
        Optional<BigDecimal> totalEquityOpt = findFinancialValue(items, ACC_TOTAL_EQUITY, "thstrm_amount");

        if (totalLiabilitiesOpt.isPresent() && totalEquityOpt.isPresent()) {
            BigDecimal totalLiabilities = totalLiabilitiesOpt.get();
            BigDecimal totalEquity = totalEquityOpt.get();
            if (totalEquity.compareTo(BigDecimal.ZERO) == 0) {
                 return buildResult(desc, threshold, totalLiabilities.compareTo(BigDecimal.ZERO) > 0, "자본총계 0", "자본총계 0, 부채 존재 시 무한대로 간주");
            } else if (totalEquity.compareTo(BigDecimal.ZERO) < 0) {
                 return buildResult(desc, threshold, true, String.format("자본잠식 %,.0f", totalEquity) , "자본총계가 음수(자본잠식)");
            }
            BigDecimal ratio = totalLiabilities.divide(totalEquity, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            boolean isRisk = ratio.compareTo(new BigDecimal("200")) >= 0;
            return buildResult(desc, threshold, isRisk, String.format("%.2f%%", ratio), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "부채총계 또는 자본총계 정보 없음");
        }
    }

    // 12. 납입자본금 잠식
    private FinancialRiskAssessmentDto.RiskItemResult checkCapitalImpairment(List<FinancialStatementData> items) {
        String desc = "납입자본금 잠식";
        String threshold = "자본총계 < 자본금";
        Optional<BigDecimal> totalEquityOpt = findFinancialValue(items, ACC_TOTAL_EQUITY, "thstrm_amount");
        Optional<BigDecimal> paidInCapitalOpt = findFinancialValue(items, ACC_PAID_IN_CAPITAL, "thstrm_amount");

        if (totalEquityOpt.isPresent() && paidInCapitalOpt.isPresent()) {
            BigDecimal totalEquity = totalEquityOpt.get();
            BigDecimal paidInCapital = paidInCapitalOpt.get();
            boolean isRisk = totalEquity.compareTo(paidInCapital) < 0;
            return buildResult(desc, threshold, isRisk, 
                               String.format("자본총계: %,.0f, 자본금: %,.0f", totalEquity, paidInCapital), null);
        } else {
            return buildResult(desc, threshold, false, "데이터 부족", "자본총계 또는 자본금 정보 없음");
        }
    }

    @SafeVarargs
    private BigDecimal sum(Optional<BigDecimal>... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (Optional<BigDecimal> value : values) {
            total = total.add(value.orElse(BigDecimal.ZERO));
        }
        return total;
    }
} 