package com.p2plending.auth.mapper;

import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.dto.response.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserResponse toResponse(User user);
}
