package com.p2plending.auth.domain.repository;

import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.enums.DocType;
import com.p2plending.auth.domain.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByUserId(Long userId);

    Optional<KycDocument> findByUserIdAndDocType(Long userId, DocType docType);

    List<KycDocument> findByStatus(KycStatus status);
}
