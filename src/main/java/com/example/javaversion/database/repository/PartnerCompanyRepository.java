/**
 * @file PartnerCompanyRepository.java
 * @description 파트너 회사 정보에 대한 데이터베이스 액세스를 제공하는 저장소 인터페이스입니다.
 */
package com.example.javaversion.database.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.javaversion.database.entity.PartnerCompany;
import com.example.javaversion.partner.model.PartnerCompanyStatus;

@Repository
public interface PartnerCompanyRepository extends JpaRepository<PartnerCompany, String> {
    
    /**
     * 회사명으로 파트너 회사를 검색합니다. (대소문자 구분 없음)
     *
     * @param companyName 회사명 (부분 일치)
     * @param status 파트너 회사 상태
     * @param pageable 페이지네이션 정보
     * @return 검색된 파트너 회사 목록 (페이지네이션)
     */
    Page<PartnerCompany> findByCompanyNameContainingIgnoreCaseAndStatus(String companyName, PartnerCompanyStatus status, Pageable pageable);
    
    /**
     * 특정 회원의 파트너 회사 목록을 회사명으로 검색합니다. (대소문자 구분 없음)
     *
     * @param memberId 회원 ID
     * @param companyName 회사명 (부분 일치)
     * @param status 파트너 회사 상태
     * @param pageable 페이지네이션 정보
     * @return 검색된 파트너 회사 목록 (페이지네이션)
     */
    Page<PartnerCompany> findByMemberIdAndCompanyNameContainingIgnoreCaseAndStatus(String memberId, String companyName, PartnerCompanyStatus status, Pageable pageable);

    /**
     * 특정 회원의 파트너 회사 목록을 상태별로 검색합니다.
     *
     * @param memberId 회원 ID
     * @param status 파트너 회사 상태
     * @param pageable 페이지네이션 정보
     * @return 검색된 파트너 회사 목록 (페이지네이션)
     */
    Page<PartnerCompany> findByMemberIdAndStatus(String memberId, PartnerCompanyStatus status, Pageable pageable);

    /**
     * 상태별로 파트너 회사를 검색합니다.
     *
     * @param status 파트너 회사 상태
     * @param pageable 페이지네이션 정보
     * @return 검색된 파트너 회사 목록 (페이지네이션)
     */
    Page<PartnerCompany> findByStatus(PartnerCompanyStatus status, Pageable pageable);

    /**
     * 상태별로 모든 파트너 회사를 검색합니다.
     *
     * @param status 파트너 회사 상태
     * @return 검색된 파트너 회사 목록
     */
    java.util.List<PartnerCompany> findByStatus(PartnerCompanyStatus status);
    
    /**
     * DART 기업 코드로 파트너 회사를 검색합니다.
     *
     * @param corpCode DART 기업 코드
     * @return 검색된 파트너 회사 (Optional)
     */
    Optional<PartnerCompany> findByCorpCode(String corpCode);
    
    /**
     * 주식 코드로 파트너 회사를 검색합니다.
     *
     * @param stockCode 주식 코드
     * @return 검색된 파트너 회사 (Optional)
     */
    Optional<PartnerCompany> findByStockCode(String stockCode);

    /**
     * ID와 상태로 파트너 회사를 검색합니다.
     *
     * @param id     파트너 회사 ID
     * @param status 파트너 회사 상태
     * @return 검색된 파트너 회사 (Optional)
     */
    Optional<PartnerCompany> findByIdAndStatus(String id, PartnerCompanyStatus status);
}