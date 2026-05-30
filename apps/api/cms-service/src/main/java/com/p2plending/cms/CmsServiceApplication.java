package com.p2plending.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CmsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsServiceApplication.class, args);
    }
}
