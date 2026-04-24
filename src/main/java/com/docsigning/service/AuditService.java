package com.docsigning.service;

import com.docsigning.model.SigningAuditRecord;
import com.docsigning.repository.SigningAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SigningAuditRepository repository;

    public SigningAuditRecord saveAuditRecord(SigningAuditRecord record) {
        log.info("Saving audit record for documentId={}", record.getDocumentId());
        return repository.save(record);
    }

    public List<SigningAuditRecord> findByDocumentId(String documentId) {
        return repository.findByDocumentId(documentId);
    }

    public List<SigningAuditRecord> findAll() {
        return repository.findAll();
    }
}
