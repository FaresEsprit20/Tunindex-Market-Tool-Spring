package com.tunindex.market_tool.user_subscription.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${mailing.service.url:http://mailing-service}")
    private String mailingServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public void sendPaymentConfirmationEmail(String to, String customerName, String amount, String currency, String transactionId) {
        log.info("Sending payment confirmation email to: {}", to);

        String subject = "Payment Confirmation - Market Tool";
        String htmlContent = buildPaymentConfirmationHtml(customerName, amount, currency, transactionId);

        Map<String, String> request = Map.of(
                "to", to,
                "subject", subject,
                "content", htmlContent,
                "label", "Market Tool Payments"
        );

        try {
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(mailingServiceUrl + "/internal/email/send-html")
                    .header("X-API-Key", internalApiKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Payment confirmation email sent successfully to: {}", to);
            } else {
                log.warn("Failed to send payment confirmation email to: {}", to);
            }
        } catch (Exception e) {
            log.error("Error sending payment confirmation email: {}", e.getMessage());
        }
    }

    public void sendPaymentReceiptEmail(String to, String customerName, String amount, String currency, String transactionId, String planName) {
        log.info("Sending payment receipt email to: {}", to);

        String subject = "Your Payment Receipt - Market Tool";
        String htmlContent = buildPaymentReceiptHtml(customerName, amount, currency, transactionId, planName);

        Map<String, String> request = Map.of(
                "to", to,
                "subject", subject,
                "content", htmlContent,
                "label", "Market Tool Billing"
        );

        try {
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(mailingServiceUrl + "/internal/email/send-html")
                    .header("X-API-Key", internalApiKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Payment receipt email sent successfully to: {}", to);
            } else {
                log.warn("Failed to send payment receipt email to: {}", to);
            }
        } catch (Exception e) {
            log.error("Error sending payment receipt email: {}", e.getMessage());
        }
    }

    private String buildPaymentConfirmationHtml(String customerName, String amount, String currency, String transactionId) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { padding: 20px; background-color: #f4f4f4; }
                        .header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; }
                        .content { background-color: white; padding: 20px; margin-top: 10px; }
                        .amount { font-size: 24px; font-weight: bold; color: #27ae60; }
                        .footer { margin-top: 20px; font-size: 12px; color: #7f8c8d; text-align: center; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>Payment Confirmation</h2>
                        </div>
                        <div class="content">
                            <p>Dear %s,</p>
                            <p>Your payment has been successfully processed.</p>
                            <p>Amount: <span class="amount">%s %s</span></p>
                            <p>Transaction ID: <strong>%s</strong></p>
                            <p>Thank you for using Market Tool!</p>
                        </div>
                        <div class="footer">
                            <p>Market Tool - Stock Market Data Collection & Analysis Platform</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(customerName, amount, currency, transactionId);
    }

    private String buildPaymentReceiptHtml(String customerName, String amount, String currency, String transactionId, String planName) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { padding: 20px; background-color: #f4f4f4; }
                        .header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; }
                        .content { background-color: white; padding: 20px; margin-top: 10px; }
                        .amount { font-size: 24px; font-weight: bold; color: #27ae60; }
                        .receipt-details { border-collapse: collapse; width: 100%%; margin-top: 15px; }
                        .receipt-details td { padding: 8px; border-bottom: 1px solid #ddd; }
                        .footer { margin-top: 20px; font-size: 12px; color: #7f8c8d; text-align: center; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>Payment Receipt</h2>
                        </div>
                        <div class="content">
                            <p>Dear %s,</p>
                            <p>Thank you for your purchase. Here are your receipt details:</p>
                            <table class="receipt-details">
                                <tr><td><strong>Plan:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Amount:</strong></td><td>%s %s</td></tr>
                                <tr><td><strong>Transaction ID:</strong></td><td>%s</td></tr>
                                <tr><td><strong>Date:</strong></td><td>%s</td></tr>
                            </table>
                            <p>Your subscription is now active. You can access all premium features immediately.</p>
                        </div>
                        <div class="footer">
                            <p>Market Tool - Stock Market Data Collection & Analysis Platform</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(customerName, planName, amount, currency, transactionId, java.time.LocalDateTime.now());
    }
}