package com.tunindex.market_tool.core.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CurlHttpClient {

    public String fetch(String url) {
        log.debug("Fetching URL with system curl: {}", url);

        try {
            // Build curl command with realistic browser headers
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "-s", "-L",
                    "--max-time", "60",
                    "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
                    "--header", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                    "--header", "Accept-Language: en-US,en;q=0.9",
                    "--header", "Accept-Encoding: gzip, deflate, br",
                    "--header", "Connection: keep-alive",
                    "--header", "Upgrade-Insecure-Requests: 1",
                    "--header", "Sec-Fetch-Dest: document",
                    "--header", "Sec-Fetch-Mode: navigate",
                    "--header", "Sec-Fetch-Site: none",
                    "--header", "Sec-Fetch-User: ?1",
                    "--header", "Sec-Ch-Ua: \"Chromium\";v=\"146\", \"Google Chrome\";v=\"146\", \"Not?A_Brand\";v=\"99\"",
                    "--header", "Sec-Ch-Ua-Mobile: ?0",
                    "--header", "Sec-Ch-Ua-Platform: \"Windows\"",
                    "--compressed",
                    url
            );

            // Use system curl - make sure curl is in PATH
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            String html;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                html = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Curl exit code {} for {}", exitCode, url);
                return null;
            }

            if (html != null && html.contains("__NEXT_DATA__")) {
                log.info("✅ Curl successfully fetched full page for: {}", url);
            } else if (html != null) {
                log.warn("⚠️ Curl fetched page without __NEXT_DATA__ for: {}", url);
            }

            return html;

        } catch (Exception e) {
            log.error("❌ Curl fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
}