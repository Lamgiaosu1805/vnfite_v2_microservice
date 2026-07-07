package com.p2plending.loan.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@Builder
public class JobApplicationCvResource {
    private Resource resource;
    private String fileName;
}
