package com.p2plending.auth.mapper;

import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KycDocumentMapper {

    KycDocumentResponse toResponse(KycDocument document);
}
