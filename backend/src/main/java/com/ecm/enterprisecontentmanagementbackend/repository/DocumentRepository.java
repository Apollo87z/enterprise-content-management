package com.ecm.enterprisecontentmanagementbackend.repository;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
}
