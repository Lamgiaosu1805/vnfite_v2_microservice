package com.p2plending.loan.mapper;

import com.p2plending.loan.domain.entity.LoanOffer;
import com.p2plending.loan.dto.response.LoanOfferResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LoanOfferMapper {

    LoanOfferResponse toResponse(LoanOffer offer);
}
