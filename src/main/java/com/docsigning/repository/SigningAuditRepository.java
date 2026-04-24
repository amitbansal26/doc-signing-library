package com.docsigning.repository;

import com.docsigning.model.SigningAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SigningAuditRepository extends JpaRepository<SigningAuditRecord, UUID> {

    List<SigningAuditRecord> findByDocumentId(String documentId);
}
