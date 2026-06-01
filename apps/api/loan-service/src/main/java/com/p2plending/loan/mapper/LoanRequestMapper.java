package com.p2plending.loan.mapper;

import com.p2plending.loan.domain.entity.LoanRequest;
import com.p2plending.loan.dto.request.LoanCreateRequest;
import com.p2plending.loan.dto.response.LoanResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LoanRequestMapper {

    @Mapping(target = "remainingAmount", expression = "java(loan.getRemainingAmount())")
    @Mapping(target = "offers", ignore = true)
    LoanResponse toResponse(LoanRequest loan);

    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "status",         ignore = true)
    @Mapping(target = "interestRate",   ignore = true)
    @Mapping(target = "fundedAmount",   ignore = true)
    @Mapping(target = "rejectionReason",ignore = true)
    @Mapping(target = "reviewedAt",     ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    @Mapping(target = "updatedAt",      ignore = true)
    @Mapping(target = "borrowerId",     ignore = true)
    @Mapping(target = "isDeleted",      ignore = true)
    LoanRequest toEntity(LoanCreateRequest request);
}
