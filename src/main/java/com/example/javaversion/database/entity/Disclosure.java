/**
 * @file Disclosure.java
 * @description DART에 공시된 개별 공시 정보를 저장하는 엔티티 클래스입니다.
 *              DART API의 '공시 검색' 결과를 기반으로 하며, 각 레코드는 하나의 공시 문서를 나타냅니다.
 *              - receiptNo: 공시 접수번호 (기본 키)
 *              - companyProfile: 공시 주체인 회사 정보 (CompanyProfile 엔티티와 연관)
 *              - corpName: 회사명
 *              - stockCode: 종목 코드 (상장된 경우)
 *              - corpClass: 시장 구분 (Y: 유가증권, K: 코스닥, N: 코넥스, E: 기타)
 *              - reportName: 보고서명 (예: 사업보고서, 주요사항보고서 등)
 *              - submitterName: 제출인명
 *              - receiptDate: 공시 접수일자 (YYYY-MM-DD 형식)
 *              - remark: 비고 (공시의 종류나 특이사항 등)
 *              - createdAt: 레코드 생성 시간
 *              - updatedAt: 레코드 마지막 업데이트 시간
 */
package com.example.javaversion.database.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "disclosures")
@Getter
@Setter
@ToString(exclude = {"companyProfile"})
@EqualsAndHashCode(exclude = {"companyProfile"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Disclosure {
    
    @Id
    @Column(name = "receipt_no")
    private String receiptNo; // 공시 접수번호
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corp_code", nullable = false)
    private CompanyProfile companyProfile; // 공시 회사 정보
    
    @Column(name = "corp_name", nullable = false)
    private String corpName; // 회사명
    
    @Column(name = "stock_code", length = 6)
    private String stockCode; // 종목 코드
    
    @Column(name = "corp_class", length = 10)
    private String corpClass; // 시장 구분
    
    @Column(name = "report_name", nullable = false)
    private String reportName; // 보고서명
    
    @Column(name = "submitter_name")
    private String submitterName; // 제출인명
    
    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate; // 공시 접수일자
    
    @Column(name = "remark", length = 1000)
    private String remark; // 비고
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
} 