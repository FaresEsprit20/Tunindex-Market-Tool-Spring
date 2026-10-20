package com.tunindex.market_tool.mailing;

import com.tunindex.market_tool.common.config.DotenvInitializer;
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
        SpringApplication app = new SpringApplication(MailingServiceApplication.class);
        // Reads backend/.env, which is where the SMTP credentials now live.
        // They used to sit in application.properties, which is committed.
        app.addInitializers(new DotenvInitializer());
        app.run(args);
        log.info("📧 MAILING SERVICE STARTED on port 8085");
    }
}
