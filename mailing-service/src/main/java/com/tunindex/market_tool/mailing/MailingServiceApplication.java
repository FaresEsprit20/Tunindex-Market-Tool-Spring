package com.tunindex.market_tool.mailing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@Slf4j
public class MailingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailingServiceApplication.class, args);
        log.info("📧 MAILING SERVICE STARTED on port 8085");
    }
}
