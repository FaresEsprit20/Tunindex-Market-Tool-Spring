# Payment Service - Market Tool

[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen.svg)](https://spring.io/projects/spring-cloud)

## 📋 Overview

The Payment Service is a comprehensive microservice for handling all payment-related operations in the Market Tool platform. It manages subscription plans, user subscriptions, payment transactions, invoices, refunds, and integrates with Konnect payment gateway for Tunisian market support.

**Port:** 8087  
**Database:** PostgreSQL (tunindex-payment)

## 🏗️ Architecture
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ PAYMENT SERVICE ARCHITECTURE │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ EXTERNAL API (Port 8087) │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ │
│ │ Subscription │ │ Payment │ │ Invoice │ │ Refund │ │
│ │ Plans │ │ Gateway │ │ Controller │ │ Controller │ │
│ └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ │
└──────────┼──────────────────┼──────────────────┼──────────────────┼────────────────────┘
│ │ │ │
▼ ▼ ▼ ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ SERVICE LAYER │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ │
│ │ Subscription │ │ Payment │ │ Invoice │ │ Refund │ │
│ │ Service │ │ Transaction │ │ Service │ │ Service │ │
│ │ │ │ Service │ │ │ │ │ │
│ └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ └───────┬───────┘ │
└──────────┼──────────────────┼──────────────────┼──────────────────┼────────────────────┘
│ │ │ │
▼ ▼ ▼ ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ REPOSITORY LAYER │
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ │
│ │ Subscription │ │ Payment │ │ Invoice │ │ Refund │ │
│ │ Plan Repo │ │ Transaction │ │ Repository │ │ Repository │ │
│ │ │ │ Repository │ │ │ │ │ │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ POSTGRESQL DATABASE │
│ ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│ │ subscription_plans | user_subscriptions | payment_transactions | invoices | refunds │ │
│ └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ INTERNAL INTEGRATIONS │
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
│ │ │ ├── InvoiceApi.java # Invoice API interface
│ │ │ ├── PaymentGatewayApi.java # Payment API interface
│ │ │ └── SubscriptionPlanApi.java # Subscription plan API interface
│ │ ├── gateway/
│ │ │ └── PaymentGatewayController.java
│ │ ├── internal/
│ │ │ └── InternalPaymentWebhookController.java
│ │ └── subscription_plan/
│ │ └── SubscriptionPlanController.java
│ ├── dto/
│ │ ├── CreatePaymentRequestDto.java
│ │ ├── CreatePaymentResponseDto.java
│ │ ├── InvoiceDto.java
│ │ ├── PaymentMethodType.java
│ │ ├── PaymentRequestDto.java
│ │ ├── PaymentResponseDto.java
│ │ ├── PaymentStatusRequestDto.java
│ │ ├── PaymentStatusResponseDto.java
│ │ ├── RefundPaymentRequestDto.java
│ │ ├── RefundPaymentResponseDto.java
│ │ ├── RefundRequestDto.java
│ │ ├── RefundResponseDto.java
│ │ ├── SubscriptionPlanDto.java
│ │ ├── UserPaymentInfoDto.java
│ │ ├── UserSubscriptionDto.java
│ │ └── gateway/
│ │ ├── PaymentGatewayRequest.java
│ │ ├── PaymentGatewayResponse.java
│ │ ├── PaymentGatewayStatusRequest.java
│ │ ├── PaymentGatewayStatusResponse.java
│ │ └── PaymentGatewayWebhookPayload.java
│ ├── entities/
│ │ ├── Invoice.java
│ │ ├── PaymentTransaction.java
│ │ ├── Refund.java
│ │ ├── SubscriptionPlan.java
│ │ ├── UserSubscription.java
│ │ └── enums/
│ │ ├── BillingPeriod.java
│ │ ├── InvoiceStatus.java
│ │ ├── PaymentMethod.java
│ │ ├── PaymentStatus.java
│ │ ├── RefundStatus.java
│ │ └── SubscriptionStatus.java
│ ├── repository/
│ │ ├── InvoiceRepository.java
│ │ ├── PaymentTransactionRepository.java
│ │ ├── RefundRepository.java
│ │ ├── SubscriptionPlanRepository.java
│ │ └── UserSubscriptionRepository.java
│ ├── service/
│ │ ├── gateway/
│ │ │ ├── PaymentGatewayService.java
│ │ │ └── konnect/
│ │ │ └── KonnectPaymentGateway.java
│ │ ├── invoices/
│ │ │ ├── InvoiceService.java
│ │ │ └── InvoiceServiceImpl.java
│ │ ├── payment_transaction/
│ │ │ ├── PaymentTransactionService.java
│ │ │ └── PaymentTransactionServiceImpl.java
│ │ ├── refund/
│ │ │ ├── RefundService.java
│ │ │ └── RefundServiceImpl.java
│ │ ├── subscription_plan/
│ │ │ ├── SubscriptionPlanService.java
│ │ │ └── SubscriptionPlanServiceImpl.java
│ │ └── user_subscription/
│ │ ├── UserSubscriptionService.java
│ │ └── UserSubscriptionServiceImpl.java
│ ├── specifications/
│ │ ├── InvoiceSpecification.java
│ │ ├── PaymentTransactionSpecification.java
│ │ ├── RefundSpecification.java
│ │ ├── SubscriptionPlanSpecification.java
│ │ └── UserSubscriptionSpecification.java
│ └── validators/
│ ├── CreatePaymentRequestValidator.java
│ ├── InvoiceValidator.java
│ ├── PaymentGatewayRequestValidator.java
│ ├── PaymentGatewayStatusRequestValidator.java
│ ├── PaymentStatusRequestValidator.java
│ ├── RefundPaymentRequestValidator.java
│ ├── RefundValidator.java
│ ├── SubscriptionPlanValidator.java
│ ├── UserSubscriptionValidator.java
│ └── WebhookValidator.java
└── src/main/resources/
├── application.properties
├── application-dev.properties
└── application-prod.properties

text

## 📊 Database Schema

### Subscription Plans Table
```sql
CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    price_monthly DECIMAL(10,2) NOT NULL,
    price_yearly DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'TND',
    duration_days INTEGER DEFAULT 30,
    features TEXT,
    api_calls_limit INTEGER DEFAULT 1000,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
User Subscriptions Table
sql
CREATE TABLE user_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT REFERENCES subscription_plans(id),
    status VARCHAR(20) DEFAULT 'PENDING',
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    billing_period VARCHAR(10),
    auto_renew BOOLEAN DEFAULT TRUE,
    cancellation_reason TEXT,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
Payment Transactions Table
sql
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
Invoices Table
sql
CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    transaction_id BIGINT REFERENCES payment_transactions(id),
    amount DECIMAL(10,2) NOT NULL,
    tax_amount DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'TND',
    status VARCHAR(20) DEFAULT 'ISSUED',
    pdf_url TEXT,
    issue_date TIMESTAMP,
    due_date TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP
);
Refunds Table
sql
CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT REFERENCES payment_transactions(id),
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

1. User selects subscription plan
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
   - UserSubscriptionService.createSubscription()
   - InvoiceService.generateInvoice()
   - EmailServiceClient.sendPaymentReceiptEmail()
         │
         ▼
10. User subscription is ACTIVE
         │
         ▼
11. Frontend polls GET /api/payments/{id}/status
         │
         ▼
12. User accesses premium features
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

# Logging
logging.level.com.tunindex.market_tool.payment=DEBUG
📡 API Endpoints
Subscription Plans (/api/subscription-plans)
Method	Endpoint	Description
GET	/{id}	Get plan by ID
GET	/active	Get all active plans
GET	/price-range?maxPrice={price}	Get plans by max price
Payments (/api/payments)
Method	Endpoint	Description
POST	/create	Create new payment
GET	/{transactionId}/status	Get payment status
POST	/refund	Request refund
GET	/payment-methods	Get available payment methods
Invoices (/api/invoices)
Method	Endpoint	Description
GET	/{id}	Get invoice by ID
GET	/number/{invoiceNumber}	Get invoice by number
GET	/user/{userId}	Get user invoices
GET	/export/pdf	Export all invoices to PDF
GET	/export/csv	Export all invoices to CSV
GET	/user/{userId}/export/pdf	Export user invoices to PDF
GET	/user/{userId}/export/csv	Export user invoices to CSV
Internal Webhook (/internal/payments/webhook)
Method	Endpoint	Description
POST	/konnect	Konnect payment webhook
🧪 Testing
Run Tests
bash
cd payment-service
mvn test

# Run specific test class
mvn test -Dtest=PaymentTransactionServiceTest
mvn test -Dtest=InvoiceServiceTest
Test Coverage
Component	Coverage Target
Services	80%
Controllers	70%
Validators	90%
Specifications	60%
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
Internal API Key: All internal endpoints require X-API-Key header

Webhook Signature: Konnect webhooks are verified using HMAC-SHA256

Input Validation: All DTOs are validated before processing

HTTPS: Production profile enforces HTTPS

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
🎯 Future Enhancements
Wallet/Balance system for prepaid users

Subscription auto-renewal scheduler

Bank transfer manual verification

Admin dashboard for payment management

Multiple payment gateway support (Stripe, etc.)

Payment analytics and reporting

Installment payment support

👨‍💻 Author
Fares Ben Slama

📄 License
MIT License

text

This README provides a complete overview of the Payment Service including architecture, database schema, API endpoints, configuration, deployment, and future enhancements. ✅
