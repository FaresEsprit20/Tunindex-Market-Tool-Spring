# Payment Service - Market Tool

[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen.svg)](https://spring.io/projects/spring-cloud)

## 📋 Overview

The Payment Service is a pure payment processing microservice for the Market Tool platform. It handles payment creation, status tracking, webhook processing, refunds, and integrates with the Konnect payment gateway for Tunisian market support.

**This service ONLY handles payments - no subscriptions, no invoices, no user management.**

**Port:** 8087  
**Database:** PostgreSQL (tunindex-payment)

## 🏗️ Architecture
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ PURE PAYMENT SERVICE ARCHITECTURE │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ EXTERNAL API (Port 8087) │
│ │
│ ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│ │ PaymentGatewayController │ │
│ │ │ │
│ │ - POST /api/payments/create Create payment │ │
│ │ - POST /api/payments/status Get payment status │ │
│ │ - GET /api/payments/payment-methods Get available payment methods │ │
│ │ - GET /api/payments/export/pdf Export transactions to PDF │ │
│ │ - GET /api/payments/export/csv Export transactions to CSV │ │
│ └─────────────────────────────────────────────────────────────────────────────────┘ │
│ │
│ ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│ │ Internal Controllers (Internal only) │ │
│ │ │ │
│ │ - POST /internal/payments/webhook/konnect Konnect webhook handler │ │
│ │ - POST /internal/refund/process Process refund (internal) │ │
│ └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ SERVICE LAYER │
│ │
│ ┌─────────────────────────────┐ ┌─────────────────────────────────────────────────┐ │
│ │ PaymentGatewayService │ │ RefundService │ │
│ │ │ │ │ │
│ │ - createPayment() │ │ - requestRefund() │ │
│ │ - getPaymentStatus() │ │ - updateRefundStatus() │ │
│ │ - processWebhook() │ │ - markAsCompleted/Failed() │ │
│ │ - refundPayment() │ │ - getTotalRefundedAmount() │ │
│ └─────────────────────────────┘ └─────────────────────────────────────────────────┘ │
│ │
│ ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│ │ PaymentTransactionService │ │
│ │ │ │
│ │ - initiatePayment() │ │
│ │ - findByTransactionId() │ │
│ │ - updateTransactionStatus() │ │
│ │ - markAsCompleted/Failed/Refunded() │ │
│ │ - exportTransactionsToPdf/Csv() │ │
│ └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ REPOSITORY LAYER │
│ │
│ ┌─────────────────────────────┐ ┌─────────────────────────────────────────────────┐ │
│ │ PaymentTransactionRepository │ │ RefundRepository │ │
│ │ │ │ │ │
│ │ - findByTransactionId() │ │ - findByTransactionId() │ │
│ │ - findAllByUserId() │ │ - findAllByStatus() │ │
│ │ - updateTransactionStatus() │ │ - updateRefundStatus() │ │
│ └─────────────────────────────┘ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ POSTGRESQL DATABASE │
│ │
│ ┌─────────────────────────────┐ ┌─────────────────────────────────────────────────┐ │
│ │ payment_transactions │ │ refunds │ │
│ │ │ │ │ │
│ │ - id │ │ - id │ │
│ │ - transaction_id (unique) │ │ - transaction_id │ │
│ │ - user_id │ │ - amount │ │
│ │ - amount │ │ - reason │ │
│ │ - currency │ │ - status │ │
│ │ - status │ │ - provider_refund_id │ │
│ │ - payment_method │ └─────────────────────────────────────────────────┘ │
│ │ - provider_payment_id │ │
│ │ - provider_name │ │
│ │ - description │ │
│ │ - failure_reason │ │
│ │ - payment_date │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ EXTERNAL INTEGRATIONS │
│ │
│ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│ │ API Service │────▶│ Konnect │────▶│ Mailing │ │
│ │ (User lookup) │ │ Gateway │ │ Service │ │
│ └─────────────────┘ └─────────────────┘ └─────────────────┘ │
│ │
└─────────────────────────────────────────────────────────────────────────────────────────┘

text

## 📁 Module Structure
payment-service/
├── pom.xml
├── src/main/java/com/tunindex/market_tool/payment/
│ ├── PaymentServiceApplication.java
│ ├── client/
│ │ ├── ApiServiceClient.java # Calls API service for user data
│ │ └── EmailServiceClient.java # Calls mailing service for emails
│ ├── config/
│ │ ├── KonnectConfig.java # Konnect API configuration
│ │ ├── PaymentProperties.java # Payment settings
│ │ ├── SecurityConfig.java # Security configuration
│ │ └── WebClientConfig.java # WebClient with LoadBalanced
│ ├── controller/
│ │ ├── api/
│ │ │ └── PaymentGatewayApi.java # Payment API interface
│ │ ├── gateway/
│ │ │ └── PaymentGatewayController.java
│ │ ├── internal/
│ │ │ ├── InternalPaymentWebhookController.java
│ │ │ └── InternalRefundController.java
│ │ └── payment_transaction/
│ │ └── PaymentTransactionController.java
│ ├── dto/
│ │ ├── CreatePaymentRequestDto.java
│ │ ├── CreatePaymentResponseDto.java
│ │ ├── PaymentMethodType.java
│ │ ├── PaymentRequestDto.java
│ │ ├── PaymentResponseDto.java
│ │ ├── PaymentStatusRequestDto.java
│ │ ├── PaymentStatusResponseDto.java
│ │ ├── RefundPaymentRequestDto.java
│ │ ├── RefundPaymentResponseDto.java
│ │ ├── RefundResponseDto.java
│ │ ├── gateway/
│ │ │ ├── PaymentGatewayRequest.java
│ │ │ ├── PaymentGatewayResponse.java
│ │ │ ├── PaymentGatewayStatusRequest.java
│ │ │ ├── PaymentGatewayStatusResponse.java
│ │ │ └── PaymentGatewayWebhookPayload.java
│ │ └── export/
│ │ └── ExportRequestDto.java
│ ├── entities/
│ │ ├── PaymentTransaction.java
│ │ ├── Refund.java
│ │ └── enums/
│ │ ├── PaymentMethod.java
│ │ ├── PaymentStatus.java
│ │ └── RefundStatus.java
│ ├── repository/
│ │ ├── PaymentTransactionRepository.java
│ │ └── RefundRepository.java
│ ├── service/
│ │ ├── gateway/
│ │ │ ├── PaymentGatewayService.java
│ │ │ └── konnect/
│ │ │ └── KonnectPaymentGateway.java
│ │ ├── payment_transaction/
│ │ │ ├── PaymentTransactionService.java
│ │ │ └── PaymentTransactionServiceImpl.java
│ │ ├── refund/
│ │ │ ├── RefundService.java
│ │ │ └── RefundServiceImpl.java
│ │ └── internal/
│ │ └── RefundInternalService.java
│ ├── specifications/
│ │ ├── PaymentTransactionSpecification.java
│ │ └── RefundSpecification.java
│ └── validators/
│ ├── gateway/
│ │ ├── CreatePaymentRequestValidator.java
│ │ ├── PaymentGatewayRequestValidator.java
│ │ ├── PaymentGatewayStatusRequestValidator.java
│ │ ├── PaymentStatusRequestValidator.java
│ │ └── RefundPaymentRequestValidator.java
│ └── RefundValidator.java
└── src/main/resources/
├── application.properties
├── application-dev.properties
└── application-prod.properties

text

## 📊 Database Schema

### Payment Transactions Table

```sql
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'TND',
    payment_method VARCHAR(30),
    status VARCHAR(20) DEFAULT 'PENDING',
    provider_payment_id VARCHAR(200),
    provider_name VARCHAR(50),
    description TEXT,
    subscription_id BIGINT,
    receipt_url TEXT,
    failure_reason TEXT,
    payment_date TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
Refunds Table
sql
CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    provider_refund_id VARCHAR(200),
    failure_reason TEXT,
    refund_date TIMESTAMP,
    created_at TIMESTAMP
);
🔄 Payment Flow
text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              COMPLETE PAYMENT FLOW                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. User initiates payment (from Subscription service or direct)
         │
         ▼
2. Frontend calls POST /api/payments/create
         │
         ▼
3. PaymentGatewayController validates request
         │
         ▼
4. KonnectPaymentGateway.createPayment()
   - Saves transaction metadata
   - Calls Konnect API
   - Returns payment_url
         │
         ▼
5. Frontend redirects user to Konnect payment page
         │
         ▼
6. User completes payment on Konnect
         │
         ▼
7. Konnect sends webhook to /internal/payments/webhook/konnect
         │
         ▼
8. InternalPaymentWebhookController processes webhook
   - Validates signature
   - Extracts payment status
         │
         ▼
9. On SUCCESS:
   - PaymentTransactionService.markAsCompleted()
   - EmailServiceClient.sendPaymentConfirmationEmail()
   - (Subscription service is notified separately)
         │
         ▼
10. On FAILURE:
    - PaymentTransactionService.markAsFailed()
         │
         ▼
11. Payment record is updated with final status
🔧 Configuration
application.properties
properties
# Service Configuration
spring.application.name=payment-service
server.port=8087

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/tunindex-payment
spring.datasource.username=postgres
spring.datasource.password=root

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# Konnect Payment Gateway
konnect.api.url=https://api.konnect.network/v1
konnect.api.key=${KONNECT_API_KEY}
konnect.webhook.secret=${KONNECT_WEBHOOK_SECRET}
konnect.allowed-payment-methods=BANK_CARD,E_DINAR,FLOUCI,WALLET

# Payment Settings
payment.currency=TND
payment.success-redirect-url=http://localhost:4200/payment/success
payment.cancel-redirect-url=http://localhost:4200/payment/cancel
payment.webhook-url=http://localhost:8087/internal/payment/webhook/konnect

# Internal API Key
internal.api.key=market-tool-internal-secret-key-2026

# Mailing Service
mailing.service.url=http://mailing-service

# API Service
api.service.url=http://api-service

# Logging
logging.level.com.tunindex.market_tool.payment=DEBUG
📡 API Endpoints
Public Payment Endpoints (/api/payments)
Method	Endpoint	Description
POST	/create	Create a new payment
POST	/status	Get payment status
POST	/refund	Request a refund
GET	/payment-methods	Get available payment methods
GET	/export/pdf	Export all transactions to PDF
GET	/export/csv	Export all transactions to CSV
GET	/user/{userId}/export/pdf	Export user transactions to PDF
GET	/user/{userId}/export/csv	Export user transactions to CSV
GET	/{transactionId}/export/pdf	Export single transaction to PDF
GET	/{transactionId}/export/csv	Export single transaction to CSV
Internal Endpoints (Service-to-Service)
Method	Endpoint	Description
POST	/internal/payments/webhook/konnect	Konnect payment webhook
POST	/internal/refund/process	Process refund (internal)
GET	/internal/refund/status/{transactionId}	Get refund status
🧪 Testing
Run Tests
bash
cd payment-service

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PaymentTransactionServiceTest
mvn test -Dtest=PaymentTransactionRepositoryTest
mvn test -Dtest=RefundRepositoryTest
Test Coverage
Component	Coverage Target
Services	80%
Repositories	85%
Validators	90%
Controllers	70%
🚀 Deployment
Build and Run
bash
# Build
mvn clean package -DskipTests

# Run with dev profile
java -jar target/payment-service-1.0.0.jar --spring.profiles.active=dev

# Run with prod profile
java -jar target/payment-service-1.0.0.jar --spring.profiles.active=prod
Docker
dockerfile
FROM openjdk:17-jdk-slim
COPY target/payment-service-1.0.0.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app.jar"]
Start Order
bash
# 1. Discovery Server
cd discovery-server && mvn spring-boot:run

# 2. API Service
cd api && mvn spring-boot:run

# 3. Mailing Service
cd mailing-service && mvn spring-boot:run

# 4. Payment Service
cd payment-service && mvn spring-boot:run
🔐 Security
Security Feature	Description
Internal API Key	All internal endpoints require X-API-Key header
Webhook Signature	Konnect webhooks verified using HMAC-SHA256
Input Validation	All DTOs validated before processing
HTTPS	Production profile enforces HTTPS
📦 Dependencies
Dependency	Version	Purpose
Spring Boot Starter Web	3.5.13	REST API
Spring Boot Starter Data JPA	3.5.13	Database access
Spring Cloud Netflix Eureka	4.3.0	Service discovery
Spring Boot Starter WebFlux	3.5.13	WebClient
PostgreSQL	42.7.10	Database
Lombok	1.18.44	Code generation
SpringDoc OpenAPI	2.1.0	API documentation
iTextPDF	5.5.13.3	PDF export
OpenCSV	5.7.1	CSV export
❌ What This Service Does NOT Do
Feature	Where it belongs
Subscription plans	Subscription Service
User subscriptions	Subscription Service
Invoices	Invoice Service
Promo codes	Subscription Service
User management	API Service
📋 What This Service Does
Feature	Description
✅ Create payment	Initiate payment with Konnect
✅ Get payment status	Check payment status
✅ Process webhooks	Handle Konnect callbacks
✅ Process refunds	Refund payments (with conditions)
✅ Export transactions	PDF/CSV export with pagination
✅ Payment history	View user payment history
🎯 Future Enhancements
Multiple payment gateway support (Stripe, etc.)

Payment analytics and reporting

Saved payment methods for users

Recurring payment support

Payment retry logic

👨‍💻 Author
Fares Ben Slama

📄 License
MIT License