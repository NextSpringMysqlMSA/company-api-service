/**
 * @file DartCorpCodeRepository.java
 * @description DartCorpCode 엔티티에 대한 데이터베이스 액세스를 제공하는 저장소 인터페이스입니다.
 */
package com.example.javaversion.database.repository;

import com.example.javaversion.database.entity.DartCorpCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, String>, JpaSpecificationExecutor<DartCorpCode> {

    Optional<DartCorpCode> findByStockCode(String stockCode);

    List<DartCorpCode> findByCorpNameContainingIgnoreCase(String corpName);

    Page<DartCorpCode> findByCorpNameContainingIgnoreCase(String corpName, Pageable pageable);
    
    Page<DartCorpCode> findByStockCodeIsNotNull(Pageable pageable);
    
    Page<DartCorpCode> findByCorpNameContainingIgnoreCaseAndStockCodeIsNotNull(String corpName, Pageable pageable);

} 