package com.ecm.enterprisecontentmanagementbackend.repository;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentRepository extends
        JpaRepository<DocumentEntity, Long>,
        JpaSpecificationExecutor<DocumentEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DocumentEntity d WHERE d.id = :id")
    Optional<DocumentEntity> findByIdForUpdate(@Param("id") Long id);
}
