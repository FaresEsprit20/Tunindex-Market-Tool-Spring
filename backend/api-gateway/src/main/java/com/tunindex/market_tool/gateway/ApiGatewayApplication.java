package com.tunindex.market_tool.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * The single front door to the platform.
 *
 * <p>Everything the browser talks to arrives here and is routed on by service
 * name rather than host and port, so a service can move or scale without the
 * client knowing. It also gives the cross-cutting concerns one home instead of
 * a copy in every service: CORS, rate limiting, correlation ids, and a first
 * check on credentials.
 *
 * <p>That first check is deliberately shallow. Each service still verifies
 * independently, so reaching one directly - which is possible while they all
 * listen on their own ports - grants nothing. The gateway's job is to turn
 * away obvious rubbish cheaply, not to be the only thing standing between a
 * request and the data.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
