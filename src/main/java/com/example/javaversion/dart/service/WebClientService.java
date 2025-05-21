/**
 * @file WebClientService.java
 * @description WebClient를 사용한 API 호출을 담당하는 서비스 클래스입니다.
 *              DART API와의 통신을 처리합니다.
 */
package com.example.javaversion.dart.service;

import com.example.javaversion.dart.dto.CompanyProfileResponse;
import com.example.javaversion.dart.dto.DisclosureSearchResponse;
import com.example.javaversion.dart.dto.FinancialStatementResponseDto;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebClientService {

    @Value("${dart.api.key}")
    private String apiKey;

    @Value("${dart.api.base-url}")
    private String baseUrl;

    @Value("${dart.api.timeout:30}")
    private int timeout; // 기본 타임아웃 30초

    private final WebClient.Builder webClientBuilder;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        log.info("WebClientService 초기화 - 타임아웃: {}초", timeout);
        this.webClient = webClientBuilder.baseUrl(baseUrl)
                .build();
    }

    /**
     * DART API에서 기업 코드 ZIP 파일을 다운로드합니다.
     *
     * @return ZIP 파일 데이터 버퍼 Flux
     */
    @RateLimiter(name = "dartApi")
    public Flux<DataBuffer> downloadCorpCodeZip() {
        log.info("DART API에서 기업 코드 ZIP 파일 다운로드 시작");

        String uri = "/api/corpCode.xml?crtfc_key=" + apiKey;
        log.debug("기업 코드 다운로드 API 요청 URI: {}", uri);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/corpCode.xml")
                        .queryParam("crtfc_key", apiKey)
                        .build())
                .exchangeToFlux(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class)
                            .doOnNext(dataBuffer -> {
                                if (log.isDebugEnabled()) {
                                    try {
                                        // 데이터 버퍼를 복제하여 로깅에 사용 (원본 버퍼는 변경하지 않음)
                                        byte[] bytes = new byte[Math.min(dataBuffer.readableByteCount(), 100)];
                                        // 데이터 버퍼의 내용을 복사
                                        dataBuffer.asByteBuffer().get(bytes);
                                        log.debug("ZIP 데이터 샘플 (첫 100바이트): {}", new String(bytes, StandardCharsets.UTF_8));
                                    } catch (Exception e) {
                                        log.warn("ZIP 데이터 샘플 로깅 실패", e);
                                    }
                                }
                            });
                    } else if (response.statusCode().is4xxClientError()) {
                        log.error("기업 코드 다운로드 API 클라이언트 오류: {}", response.statusCode());
                        return response.bodyToMono(String.class)
                            .flatMapMany(errorBody -> {
                                log.error("기업 코드 다운로드 API 오류 응답 본문: {}", errorBody);
                                return Flux.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "DART API 요청 오류: " + errorBody));
                            });
                    } else {
                        log.error("기업 코드 다운로드 API 서버 오류: {}", response.statusCode());
                        return Flux.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "DART API 서버 오류가 발생했습니다."));
                    }
                })
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("기업 코드 다운로드 중 오류 발생: {}", error.getMessage(), error));
    }

    /**
     * DART API에서 회사 정보를 조회합니다.
     *
     * @param corpCode 회사 코드
     * @return 회사 정보 응답 Mono
     */
    @RateLimiter(name = "dartApi")
    public Mono<CompanyProfileResponse> getCompanyProfile(String corpCode) {
        log.info("[WebClientService] getCompanyProfile 메소드 시작: corpCode={}", corpCode);

        if (webClient == null) {
            log.error("[WebClientService] webClient가 null입니다! 초기화 실패 가능성. corpCode={}", corpCode);
            return Mono.error(new IllegalStateException("WebClientService가 제대로 초기화되지 않았습니다."));
        }
        if (!StringUtils.hasText(apiKey)) {
            log.error("[WebClientService] DART API 키가 설정되지 않았습니다. corpCode={}", corpCode);
            return Mono.error(new IllegalStateException("DART API 키가 설정되지 않았습니다."));
        }
        if (!StringUtils.hasText(corpCode)) {
            log.error("[WebClientService] corpCode가 비어있습니다.");
            return Mono.error(new IllegalArgumentException("corpCode는 비어있을 수 없습니다."));
        }

        log.info("회사 정보 조회 API 호출 시작: corpCode={}", corpCode);

        String uri = "/api/company.json?crtfc_key=" + apiKey + "&corp_code=" + corpCode;
        log.debug("회사 정보 조회 API 요청 URI: {}", uri);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/company.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .build())
                .exchangeToMono(response -> {
                    HttpStatus responseStatus = (HttpStatus) response.statusCode();
                    log.info("DART API 응답 수신: corpCode={}, status={}", corpCode, responseStatus);

                    if (responseStatus.is2xxSuccessful()) {
                        return response.bodyToMono(CompanyProfileResponse.class)
                            .doOnSuccess(profile -> {
                                if (profile == null) {
                                    log.warn("DART API 성공 응답이지만, CompanyProfileResponse가 null입니다. corpCode={}", corpCode);
                                } else {
                                    log.info("DART API 성공 응답, CompanyProfileResponse 파싱 완료: corpCode={}", corpCode);
                                }
                            })
                            .doOnError(e -> log.error("DART API 응답 본문 파싱 중 오류 발생: corpCode={}, error={}", corpCode, e.getMessage()));
                    } else {
                        return response.bodyToMono(String.class)
                            .defaultIfEmpty("[오류 응답 본문 없음]") // 오류 응답 본문이 비어있을 경우를 대비
                            .flatMap(errorBody -> {
                                log.error("DART API 오류 응답: corpCode={}, status={}, body={}", corpCode, responseStatus, errorBody);
                                // 클라이언트 오류(4xx)는 null 대신 에러 Mono 반환하여 실패 전파
                                // DART API에서 데이터가 없는 경우에도 특정 오류 코드를 줄 수 있으므로, 이를 어떻게 처리할지 결정 필요
                                // 예를 들어, 404 Not Found 와 유사한 DART API 응답 코드가 있다면, 여기에서 Mono.empty()를 반환하여 처리할 수 있음
                                // 현재는 모든 비2xx 응답을 에러로 간주
                                String errorMessage = String.format("DART API 오류: status=%s, body=%s", responseStatus, errorBody);
                                // return Mono.error(new ResponseStatusException(responseStatus, errorMessage)); // 원본 상태 코드 사용
                                 return Mono.empty(); // KafkaConsumerService에서 null로 받고 처리하도록, 여기서는 empty() 반환
                            });
                    }
                })
                .timeout(Duration.ofSeconds(timeout))
                .doOnSubscribe(subscription -> log.info("DART API [company.json] 구독 시작: corpCode={}", corpCode))
                .doOnCancel(() -> log.warn("DART API [company.json] 요청 취소됨: corpCode={}", corpCode))
                .doOnError(error -> {
                    // 네트워크 오류, 타임아웃 등의 경우 여기에 해당
                    log.error("DART API [company.json] 호출 중 WebClient 오류 발생: corpCode={}, errorClass={}, errorMessage={}", 
                            corpCode, error.getClass().getSimpleName(), error.getMessage());
                });
    }

    /**
     * DART API에서 공시 정보를 검색합니다.
     *
     * @param corpCode 회사 코드
     * @param startDate 검색 시작일(YYYYMMDD)
     * @param endDate 검색 종료일(YYYYMMDD)
     * @return 공시 검색 결과 응답 Mono
     */
    @RateLimiter(name = "dartApi")
    public Mono<DisclosureSearchResponse> searchDisclosures(String corpCode, String startDate, String endDate) {
        log.info("공시 검색 API 호출: {}, {} ~ {}", corpCode, startDate, endDate);

        String uri = String.format("/api/list.json?crtfc_key=%s&corp_code=%s&bgn_de=%s&end_de=%s&page_count=100", 
                apiKey, corpCode, startDate, endDate);
        log.debug("공시 검색 API 요청 URI: {}", uri);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", startDate)
                        .queryParam("end_de", endDate)
                        .queryParam("page_count", 100)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(DisclosureSearchResponse.class);
                    } else if (response.statusCode().is4xxClientError()) {
                        log.error("공시 검색 API 클라이언트 오류: {}, 회사 코드: {}, 기간: {} ~ {}", 
                                response.statusCode(), corpCode, startDate, endDate);
                        return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("공시 검색 API 오류 응답 본문: {}", errorBody);
                                return Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "DART API 요청 오류: " + errorBody));
                            });
                    } else {
                        log.error("공시 검색 API 서버 오류: {}", response.statusCode());
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "DART API 서버 오류가 발생했습니다."));
                    }
                })
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("공시 검색 API 호출 중 오류 발생: {}", error.getMessage(), error));
    }

    /**
     * DART API에서 단일 회사 전체 재무제표를 조회합니다.
     *
     * @param corpCode    고유번호 (8자리)
     * @param bsnsYear    사업연도 (4자리)
     * @param reprtCode   보고서 코드 (1분기: 11013, 반기: 11012, 3분기: 11014, 사업보고서: 11011)
     * @param fsDiv       개별/연결 구분 (OFS: 재무제표, CFS: 연결재무제표)
     * @return 재무제표 정보 응답 Mono
     */
    @RateLimiter(name = "dartApi")
    public Mono<FinancialStatementResponseDto> getFinancialStatementApi(String corpCode, String bsnsYear, String reprtCode, String fsDiv) {
        log.info("단일 회사 전체 재무제표 조회 API 호출: corpCode={}, bsnsYear={}, reprtCode={}, fsDiv={}",
                corpCode, bsnsYear, reprtCode, fsDiv);

        String uri = String.format("/api/fnlttSinglAcntAll.json?crtfc_key=%s&corp_code=%s&bsns_year=%s&reprt_code=%s&fs_div=%s",
                apiKey, corpCode, bsnsYear, reprtCode, fsDiv);
        log.debug("재무제표 조회 API 요청 URI: {}", uri);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/fnlttSinglAcntAll.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", bsnsYear)
                        .queryParam("reprt_code", reprtCode)
                        .queryParam("fs_div", fsDiv)
                        .build())
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(FinancialStatementResponseDto.class);
                    } else if (response.statusCode().is4xxClientError()) {
                        log.error("재무제표 조회 API 클라이언트 오류: {}, corpCode={}, bsnsYear={}, reprtCode={}, fsDiv={}",
                                response.statusCode(), corpCode, bsnsYear, reprtCode, fsDiv);
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("재무제표 조회 API 오류 응답 본문: {}", errorBody);
                                    return Mono.error(new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST, "DART API 요청 오류: " + errorBody));
                                });
                    } else {
                        log.error("재무제표 조회 API 서버 오류: {}", response.statusCode());
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "DART API 서버 오류가 발생했습니다."));
                    }
                })
                .timeout(Duration.ofSeconds(timeout))
                .doOnError(error -> log.error("재무제표 조회 API 호출 중 오류 발생: {}", error.getMessage(), error));
    }
}
