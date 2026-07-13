package com.p2plending.auth.feign;

import com.p2plending.auth.common.Constant;
import com.p2plending.auth.config.FeignConfig;
import com.p2plending.auth.dto.request.vwork.CustomerSyncRequest;
import com.p2plending.auth.dto.request.vwork.UpsertCustomerRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "VWork", url = "${spring.vwork.url}", configuration = {FeignConfig.class})
public interface VWorkFeignService {
    @PostMapping(value = "${spring.vwork.user-upsert}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    void upsertCustomer(@RequestHeader(Constant.X_API_KEY) String apiKey,
                        @RequestBody UpsertCustomerRequest upsertCustomerRequest);

    @PostMapping(value = "/customer/bulk-upsert",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    void bulkUpsertCustomers(@RequestHeader(Constant.X_API_KEY) String apiKey,
                             @RequestBody CustomerSyncRequest customerSyncRequest);
}
