package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.enums.DocType;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository extends JpaRepository<KycDocument, String> {

    List<KycDocument> findByUserId(String userId);

    Optional<KycDocument> findByUserIdAndDocType(String userId, DocType docType);

    List<KycDocument> findByStatus(KycStatus status);
}
