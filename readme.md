create a readme md that have everything we did tests + modules and architecture
Market Tool - Stock Market Data Collection & Analysis Platform
https://img.shields.io/badge/Java-17-blue.svg
https://img.shields.io/badge/Spring%2520Boot-3.5.13-brightgreen.svg
https://img.shields.io/badge/Spring%2520Cloud-2025.0.0-brightgreen.svg
https://img.shields.io/badge/License-MIT-yellow.svg

📋 Overview
Market Tool is a comprehensive microservices-based platform for collecting, processing, and analyzing Tunisian stock market data. It features automated web scraping, Graham value calculations, REST APIs with advanced filtering, and service discovery.

Key Features
🚀 Automated Data Collection - Scheduled scraping from StockAnalysis.com

📊 Graham Value Analysis - Calculate intrinsic value and margin of safety

🔍 Advanced Filtering - Filter stocks by 30+ criteria using JPA Specifications

📈 REST API - Full-featured API with pagination and Swagger documentation

🛡️ Service Discovery - Netflix Eureka for dynamic service registration

⚖️ Load Balancing - Client-side (Spring Cloud) + Server-side (Nginx)

🧪 Comprehensive Testing - Unit tests, integration tests, repository tests

🏗️ Architecture
Microservices Architecture
text
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL CLIENTS                                │
│                          (Browser, Mobile App, API)                          │
└─────────────────────────────────────┬───────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              NGINX (Port 80)                                 │
│                         SERVER-SIDE LOAD BALANCER                            │
└─────────────────────────────────────┬────────────────────────────────────
what the hell , put it in text box
Market Tool - Stock Market Data Collection & Analysis Platform
Overview
Market Tool is a comprehensive microservices-based platform for collecting, processing, and analyzing Tunisian stock market data. It features automated web scraping, Graham value calculations, REST APIs with advanced filtering, service discovery, and load balancing.

Key Features
Automated Data Collection - Scheduled scraping from StockAnalysis.com

Graham Value Analysis - Calculate intrinsic value and margin of safety

Advanced Filtering - Filter stocks by 30+ criteria using JPA Specifications

REST API - Full-featured API with pagination and Swagger documentation

Service Discovery - Netflix Eureka for dynamic service registration

Load Balancing - Client-side (Spring Cloud) + Server-side (Nginx)

Comprehensive Testing - Unit tests, integration tests, repository tests

Technology Stack
Technology	Version	Purpose
Java	17	Core language
Spring Boot	3.5.13	Application framework
Spring Cloud	2025.0.0	Microservices (Eureka, LoadBalancer)
PostgreSQL	18.3	Database
H2	Latest	Testing database
JPA/Hibernate	6.6.45	ORM
Maven	3.x	Build tool
JUnit 5	5.12.2	Unit testing
Mockito	5.17.0	Mocking framework
AssertJ	3.27.7	Fluent assertions
JSoup	1.17.2	HTML parsing
Selenium	4.31.0	Web scraping
Swagger/OpenAPI	2.1.0	API documentation
Nginx	Latest	Server-side load balancing
Architecture
Microservices Diagram
text
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL CLIENTS                                │
│                          (Browser, Mobile App, API)                          │
└─────────────────────────────────────┬───────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              NGINX (Port 80)                                 │
│                         SERVER-SIDE LOAD BALANCER                            │
│                      (Round Robin / Least Connections)                       │
└─────────────────────────────────────┬───────────────────────────────────────┘
│
┌───────────────────────┼───────────────────────┐
│                       │                       │
▼                       ▼                       ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│   API INSTANCE 1    │   │   API INSTANCE 2    │   │   API INSTANCE 3    │
│     Port 8080       │   │     Port 8084       │   │     Port 8085       │
│                     │   │                     │   │                     │
│  - REST Endpoints   │   │  - REST Endpoints   │   │  - REST Endpoints   │
│  - Swagger UI       │   │  - Swagger UI       │   │  - Swagger UI       │
│  - StockService     │   │  - StockService     │   │  - StockService     │
└─────────┬───────────┘   └─────────┬───────────┘   └─────────┬───────────┘
│                         │                         │
│              ┌──────────┴──────────┐              │
│              │                     │              │
└──────────────┼─────────────────────┼──────────────┘
│                     │
▼                     ▼
┌─────────────────────────────────────────────────────┐
│                   EUREKA SERVER                      │
│                     Port 8761                        │
│                                                      │
│  Service Registry:                                   │
│  - API-SERVICE (3 instances)                         │
│  - COLLECTOR-SERVICE (3 instances)                   │
└─────────────────────────────────────────────────────┘
│                     │
┌──────────────┼─────────────────────┼──────────────┐
│              │                     │              │
▼              ▼                     ▼              ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  COLLECTOR-8081 │ │  COLLECTOR-8082 │ │  COLLECTOR-8083 │
│                 │ │                 │ │                 │
│ - Fetcher       │ │ - Fetcher       │ │ - Fetcher       │
│ - Parser        │ │ - Parser        │ │ - Parser        │
│ - Enricher      │ │ - Enricher      │ │ - Enricher      │
│ - Scheduler     │ │ - Scheduler     │ │ - Scheduler     │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
│                    │                    │
└────────────────────┼────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│                   POSTGRESQL                         │
│                    Port 5432                         │
│                                                      │
│  Tables: stocks (with embedded objects)              │
└─────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│                STOCKANALYSIS.COM                     │
│              (External Data Source)                  │
└─────────────────────────────────────────────────────┘
Project Structure
text
market-tool/
├── pom.xml (Parent)
├── common/                         # Shared library
│   ├── entities/                   # JPA entities
│   │   ├── Stock.java
│   │   ├── embedded/              # PriceData, VolumeData, etc.
│   │   └── enums/                 # SectorType, OwnershipType
│   ├── dto/                       # Data Transfer Objects
│   │   └── providers/investingcom/
│   │       ├── StockDto.java
│   │       ├── EnrichedStockData.java
│   │       └── NormalizedStockData.java
│   ├── repository/jpa/            # Repository interfaces
│   │   └── StockRepository.java
│   ├── exception/                 # Custom exceptions
│   ├── specification/             # JPA Specifications
│   │   └── StockSpecification.java
│   └── utils/                     # Utilities
│       ├── constants/
│       └── pagination/
├── api/                           # API Service (Port 8080)
│   ├── ApiApplication.java
│   ├── controllers/stock/
│   │   ├── StockApi.java
│   │   └── StockController.java
│   ├── services/
│   │   └── StockServiceImpl.java
│   ├── config/
│   │   ├── swagger/SwaggerConfig.java
│   │   └── WebClientConfig.java
│   └── handlers/
│       └── RestExceptionHandler.java
├── collector/                     # Collector Service (Port 8081-8083)
│   ├── CollectorApplication.java
│   ├── orchestrator/
│   │   └── DataOrchestratorImpl.java
│   ├── providers/stockanalysis/
│   │   └── StockAnalysisProvider.java
│   ├── services/
│   │   ├── fetcher/
│   │   ├── parser/
│   │   ├── enricher/
│   │   ├── normalizer/
│   │   ├── calculator/
│   │   └── async/
│   ├── config/webclient/
│   │   └── WebClientConfig.java
│   └── webscraping/              # Stealth scraping utilities
└── discovery-server/              # Eureka Server (Port 8761)
├── DiscoveryServerApplication.java
└── application.properties
Module Details
Common Module
Shared library used by both API and Collector services.

Contents:

JPA Entities (Stock with embedded objects)

DTOs (StockDto, EnrichedStockData, NormalizedStockData)

Repository interfaces (StockRepository)

Custom exceptions (EntityNotFoundException, InvalidEntityException)

JPA Specifications for dynamic filtering

Pagination utilities

Constants and configuration

Why separate? Avoids code duplication across services.

API Service
REST API service that serves stock data to clients.

Port: 8080 (multiple instances: 8080, 8084, 8085)

Endpoints:

Method	Endpoint	Description
GET	/api/v1/stocks/symbol/{symbol}	Find by symbol
GET	/api/v1/stocks/symbol/{symbol}/exchange/{exchange}	Find by symbol and exchange
POST	/api/v1/stocks/filter	Advanced filtering with pagination
GET	/api/v1/stocks/statistics/by-sector	Sector distribution
GET	/api/v1/stocks/statistics/by-ownership	Ownership distribution
PUT	/api/v1/stocks/refresh/{symbol}	Trigger data refresh
Filtering capabilities:

Basic: symbol, name, exchange, sector, ownershipType

Price: minPrice, maxPrice

52-week range: near52WeekLow, near52WeekHigh

Profitability: minProfitMargin, maxProfitMargin, profitable

Margin of Safety: minMarginOfSafety, maxMarginOfSafety, undervalued, overvalued

Graham Value: minGrahamFairValue, maxGrahamFairValue, priceBelowGrahamValue

Debt: minDebtToEquity, maxDebtToEquity, lowDebt, highDebt

EPS/BVPS: minEps, maxEps, minBvps, maxBvps

PE Ratio: minPeRatio, maxPeRatio, lowPeRatio

Dividend: minDividendYield, maxDividendYield, highDividend

Presets: valueInvestorFavorites, growthInvestorFavorites, grahamCriteria

Collector Service
Background worker that fetches, parses, and enriches stock data.

Port: 8081 (multiple instances: 8081, 8082, 8083)

Components:

Fetcher - Retrieves HTML from StockAnalysis.com using WebClient with stealth headers

Parser - Extracts metrics from HTML/DOM using JSoup

Normalizer - Cleans numeric values (converts "1.5B" to 1500000000)

Enricher - Calculates Graham fair value and margin of safety

Scheduler - Runs every 30 minutes during market hours

WebScraping - Anti-detection utilities (UserAgent rotation, fingerprints)

Graham Formula:

text
Graham Fair Value = EPS × (8.5 + 2 × Growth Rate)
Margin of Safety = (Fair Value - Current Price) / Fair Value × 100%
Discovery Server (Eureka)
Service registry for all microservices.

Port: 8761

Dashboard: http://localhost:8761

Registered services:

API-SERVICE (3 instances)

COLLECTOR-SERVICE (3 instances)

Nginx (Optional Server-Side Load Balancer)
Port: 80

Configuration:

nginx
upstream market_tool_api {
server localhost:8080;
server localhost:8084;
server localhost:8085;
}

server {
listen 80;
location /api/ {
proxy_pass http://market_tool_api;
}
}
Testing
Test Structure
text
common/src/test/
├── java/com/tunindex/market_tool/common/
│   ├── repository/jpa/
│   │   └── StockRepositoryTest.java
│   └── services/
│       └── StockServiceImplTest.java
└── resources/
└── application-test.properties
Repository Tests (DataJpaTest)
Location: StockRepositoryTest.java

Tests:

findBySymbol (exists, not exists, case sensitivity)

findBySymbolAndExchange (both match, exchange mismatch)

existsBySymbol / existsBySymbolAndExchange

countStocksBySector / countStocksByOwnership

updateLastUpdateTime

CRUD operations (save, delete, findAll)

Embedded object validation (PriceData, FundamentalData, CalculatedValues)

How to run:

bash
cd common
mvn test -Dtest=StockRepositoryTest
Service Unit Tests (Mockito)
Location: StockServiceImplTest.java

Tests:

Find by symbol (success, null, empty, not found)

Find by symbol and exchange

Filter stocks with pagination

Pagination parameter validation

Count by sector / ownership

Refresh stock data (success, non-existent)

Filter applications (sector, price, margin of safety)

Preset filters (undervalued, grahamCriteria)

DTO conversion

How to run:

bash
cd common
mvn test -Dtest=StockServiceImplTest
Test Configuration
application-test.properties:

properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
Running All Tests
bash
cd common
mvn clean test
Installation & Setup
Prerequisites
Java 17

Maven 3.8+

PostgreSQL 15+ (or Docker)

Git

Step 1: Clone Repository
bash
git clone https://github.com/yourusername/market-tool.git
cd market-tool
Step 2: Build Project
bash
mvn clean install
Step 3: Configure Database
Create PostgreSQL database:

sql
CREATE DATABASE tunindex;
CREATE USER postgres WITH PASSWORD 'root';
GRANT ALL PRIVILEGES ON DATABASE tunindex TO postgres;
Or use Docker:

bash
docker run -d \
--name postgres \
-e POSTGRES_DB=tunindex \
-e POSTGRES_USER=postgres \
-e POSTGRES_PASSWORD=root \
-p 5432:5432 \
postgres:15
Step 4: Start Services
Order matters - start in this sequence:

bash
# Terminal 1 - Eureka Server
cd discovery-server
mvn spring-boot:run

# Terminal 2 - PostgreSQL (if not using Docker)
# Already running

# Terminal 3 - API Service
cd api
mvn spring-boot:run

# Terminal 4 - Collector Instance 1 (default port)
cd collector
mvn spring-boot:run

# Terminal 5 - Collector Instance 2 (optional - for load balancing)
cd collector
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082

# Terminal 6 - Collector Instance 3 (optional - for load balancing)
cd collector
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8083
Step 5: Verify Services
Open browser:

Eureka Dashboard: http://localhost:8761

API Swagger UI: http://localhost:8080/swagger-ui.html

API Health: http://localhost:8080/actuator/health

API Usage Examples
Get Stock by Symbol
bash
curl http://localhost:8080/api/v1/stocks/symbol/BH
Filter Stocks
bash
curl -X POST http://localhost:8080/api/v1/stocks/filter \
-H "Content-Type: application/json" \
-d '{
"page": 1,
"size": 10,
"filters": {
"sector": "FINANCIALS",
"minMarginOfSafety": "20",
"undervalued": "true"
}
}'
Trigger Data Refresh
bash
curl -X PUT http://localhost:8080/api/v1/stocks/refresh/BH
Get Statistics
bash
# Count by sector
curl http://localhost:8080/api/v1/stocks/statistics/by-sector

# Count by ownership
curl http://localhost:8080/api/v1/stocks/statistics/by-ownership
Load Balancing
Client-Side Load Balancing
API service uses Spring Cloud LoadBalancer to call Collector service:

java
@Bean
@LoadBalanced
public WebClient.Builder loadBalancedWebClientBuilder() {
return WebClient.builder();
}

// Usage - uses service name instead of hardcoded URL
webClientBuilder.build()
.post()
.uri("http://COLLECTOR-SERVICE/internal/collector/stock/BH")
Server-Side Load Balancing (Nginx)
For external clients, Nginx distributes requests across multiple API instances:

nginx
upstream market_tool_api {
server localhost:8080;
server localhost:8084;
server localhost:8085;
}
Testing Load Balancing
bash
# Run multiple requests - should see different ports
for i in 1 2 3 4 5 6; do
curl http://localhost:8080/api/test/load-balance-test
echo ""
sleep 1
done
Deployment
Docker Compose
Create docker-compose.yml:

yaml
version: '3.8'
services:
postgres:
image: postgres:15
environment:
POSTGRES_DB: tunindex
POSTGRES_USER: postgres
POSTGRES_PASSWORD: root
ports:
- "5432:5432"

discovery-server:
build: ./discovery-server
ports:
- "8761:8761"

api:
build: ./api
ports:
- "8080:8080"
depends_on:
- postgres
- discovery-server

collector:
build: ./collector
ports:
- "8081:8081"
depends_on:
- postgres
- discovery-server

nginx:
image: nginx:alpine
ports:
- "80:80"
volumes:
- ./nginx.conf:/etc/nginx/nginx.conf
depends_on:
- api
Production Considerations
Use environment variables for sensitive data

Enable HTTPS with SSL certificates

Set up monitoring (Prometheus + Grafana)

Configure log aggregation (ELK stack)

Implement circuit breakers (Resilience4j)

Add distributed tracing (Zipkin)

Troubleshooting
Common Issues
Issue	Solution
Eureka connection refused	Start discovery-server first
StockRepository null	Add @EnableJpaRepositories with correct package
Port already in use	Change server.port in application.properties
WebClient load balancing not working	Add @LoadBalanced to WebClient.Builder bean
NULL values in filter results	Add isNotNull checks in StockSpecification
Debugging
Enable SQL logging:

properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
Future Enhancements
Add API Gateway (Spring Cloud Gateway)

Add Circuit Breakers (Resilience4j)

Add Distributed Tracing (Zipkin)

Add Metrics Monitoring (Prometheus + Grafana)

Add More Data Providers (Investing.com, Yahoo Finance)

Add Historical Data Storage

Add Email Notifications

Add User Authentication (Spring Security + JWT)

Add React Frontend Dashboard

License
MIT License

Contributors
Your Name

Support
For issues or questions, please open a GitHub issue.

Okay now create the readme.md file so i download it
README.md
markdown
# Market Tool - Stock Market Data Collection & Analysis Platform

[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%
my name is Fares Ben Slama
README.md
markdown
# Market Tool - Stock Market Data Collection & Analysis Platform

[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen.svg)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 📋 Overview

Market Tool is a comprehensive microservices-based platform for collecting, processing, and analyzing Tunisian stock market data. It features automated web scraping, Graham value calculations, REST APIs with advanced filtering, service discovery, and load balancing.

**Author:** Fares Ben Slama

### Key Features

- 🚀 **Automated Data Collection** - Scheduled scraping from StockAnalysis.com
- 📊 **Graham Value Analysis** - Calculate intrinsic value and margin of safety
- 🔍 **Advanced Filtering** - Filter stocks by 30+ criteria using JPA Specifications
- 📈 **REST API** - Full-featured API with pagination and Swagger documentation
- 🛡️ **Service Discovery** - Netflix Eureka for dynamic service registration
- ⚖️ **Load Balancing** - Client-side (Spring Cloud) + Server-side (Nginx)
- 🧪 **Comprehensive Testing** - Unit tests, integration tests, repository tests

## 🏗️ Architecture

### Microservices Diagram
┌─────────────────────────────────────────────────────────────────────────────┐
│ EXTERNAL CLIENTS │
│ (Browser, Mobile App, API) │
└─────────────────────────────────────┬───────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ NGINX (Port 80) │
│ SERVER-SIDE LOAD BALANCER │
└─────────────────────────────────────┬───────────────────────────────────────┘
│
┌───────────────────────┼───────────────────────┐
│ │ │
▼ ▼ ▼
┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
│ API INSTANCE 1 │ │ API INSTANCE 2 │ │ API INSTANCE 3 │
│ Port 8080 │ │ Port 8084 │ │ Port 8085 │
└─────────┬───────────┘ └─────────┬───────────┘ └─────────┬───────────┘
│ │ │
└─────────────────────────┼─────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│ EUREKA SERVER │
│ Port 8761 │
│ │
│ Service Registry: │
│ - API-SERVICE (3 instances) │
│ - COLLECTOR-SERVICE (3 instances) │
└─────────────────────────────────────────────────────┘
│
┌─────────────────────────┼─────────────────────────┐
│ │ │
▼ ▼ ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ COLLECTOR-8081 │ │ COLLECTOR-8082 │ │ COLLECTOR-8083 │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
│ │ │
└──────────────────────┼──────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│ POSTGRESQL │
│ Port 5432 │
└─────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│ STOCKANALYSIS.COM │
│ (External Data Source) │
└─────────────────────────────────────────────────────┘

text

### Project Structure
market-tool/
├── pom.xml (Parent)
├── common/ # Shared library
│ ├── entities/ # JPA entities
│ │ ├── Stock.java
│ │ ├── embedded/ # PriceData, VolumeData, etc.
│ │ └── enums/ # SectorType, OwnershipType
│ ├── dto/ # Data Transfer Objects
│ │ └── providers/investingcom/
│ │ ├── StockDto.java
│ │ ├── EnrichedStockData.java
│ │ └── NormalizedStockData.java
│ ├── repository/jpa/ # Repository interfaces
│ │ └── StockRepository.java
│ ├── exception/ # Custom exceptions
│ ├── specification/ # JPA Specifications
│ │ └── StockSpecification.java
│ └── utils/ # Utilities
│ ├── constants/
│ └── pagination/
├── api/ # API Service (Port 8080)
│ ├── ApiApplication.java
│ ├── controllers/stock/
│ │ ├── StockApi.java
│ │ └── StockController.java
│ ├── services/
│ │ └── StockServiceImpl.java
│ ├── config/
│ │ ├── swagger/SwaggerConfig.java
│ │ └── WebClientConfig.java
│ └── handlers/
│ └── RestExceptionHandler.java
├── collector/ # Collector Service (Port 8081-8083)
│ ├── CollectorApplication.java
│ ├── orchestrator/
│ │ └── DataOrchestratorImpl.java
│ ├── providers/stockanalysis/
│ │ └── StockAnalysisProvider.java
│ ├── services/
│ │ ├── fetcher/
│ │ ├── parser/
│ │ ├── enricher/
│ │ ├── normalizer/
│ │ ├── calculator/
│ │ └── async/
│ ├── config/webclient/
│ │ └── WebClientConfig.java
│ └── webscraping/ # Stealth scraping utilities
└── discovery-server/ # Eureka Server (Port 8761)
├── DiscoveryServerApplication.java
└── application.properties

text

## 📦 Module Details

### Common Module

Shared library used by both API and Collector services.

**Contents:**
- JPA Entities (Stock with embedded objects)
- DTOs (StockDto, EnrichedStockData, NormalizedStockData)
- Repository interfaces (StockRepository)
- Custom exceptions (EntityNotFoundException, InvalidEntityException)
- JPA Specifications for dynamic filtering
- Pagination utilities
- Constants and configuration

### API Service

REST API service that serves stock data to clients.

**Port:** 8080 (multiple instances: 8080, 8084, 8085)

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/stocks/symbol/{symbol} | Find by symbol |
| GET | /api/v1/stocks/symbol/{symbol}/exchange/{exchange} | Find by symbol and exchange |
| POST | /api/v1/stocks/filter | Advanced filtering with pagination |
| GET | /api/v1/stocks/statistics/by-sector | Sector distribution |
| GET | /api/v1/stocks/statistics/by-ownership | Ownership distribution |
| PUT | /api/v1/stocks/refresh/{symbol} | Trigger data refresh |

**Filtering capabilities:**
- Basic: symbol, name, exchange, sector, ownershipType
- Price: minPrice, maxPrice
- 52-week range: near52WeekLow, near52WeekHigh
- Profitability: minProfitMargin, maxProfitMargin, profitable
- Margin of Safety: minMarginOfSafety, maxMarginOfSafety, undervalued, overvalued
- Graham Value: minGrahamFairValue, maxGrahamFairValue, priceBelowGrahamValue
- Debt: minDebtToEquity, maxDebtToEquity, lowDebt, highDebt
- EPS/BVPS: minEps, maxEps, minBvps, maxBvps
- PE Ratio: minPeRatio, maxPeRatio, lowPeRatio
- Dividend: minDividendYield, maxDividendYield, highDividend
- Presets: valueInvestorFavorites, growthInvestorFavorites, grahamCriteria

### Collector Service

Background worker that fetches, parses, and enriches stock data.

**Port:** 8081 (multiple instances: 8081, 8082, 8083)

**Components:**
- **Fetcher** - Retrieves HTML from StockAnalysis.com using WebClient with stealth headers
- **Parser** - Extracts metrics from HTML/DOM using JSoup
- **Normalizer** - Cleans numeric values (converts "1.5B" to 1500000000)
- **Enricher** - Calculates Graham fair value and margin of safety
- **Scheduler** - Runs every 30 minutes during market hours
- **WebScraping** - Anti-detection utilities (UserAgent rotation, fingerprints)

**Graham Formula:**
Graham Fair Value = EPS × (8.5 + 2 × Growth Rate)
Margin of Safety = (Fair Value - Current Price) / Fair Value × 100%

text

### Discovery Server (Eureka)

Service registry for all microservices.

**Port:** 8761
**Dashboard:** http://localhost:8761

**Registered services:**
- API-SERVICE (3 instances)
- COLLECTOR-SERVICE (3 instances)

## 🧪 Testing

### Test Structure
common/src/test/
├── java/com/tunindex/market_tool/common/
│ ├── repository/jpa/
│ │ └── StockRepositoryTest.java
│ └── services/
│ └── StockServiceImplTest.java
└── resources/
└── application-test.properties

text

### Repository Tests (DataJpaTest)

**Location:** `StockRepositoryTest.java`

**Tests Covered:**
- findBySymbol (exists, not exists, case sensitivity)
- findBySymbolAndExchange (both match, exchange mismatch)
- existsBySymbol / existsBySymbolAndExchange
- countStocksBySector / countStocksByOwnership
- updateLastUpdateTime
- CRUD operations (save, delete, findAll)
- Embedded object validation (PriceData, FundamentalData, CalculatedValues)

**Run tests:**
```bash
cd common
mvn test -Dtest=StockRepositoryTest
Service Unit Tests (Mockito)
Location: StockServiceImplTest.java

Tests Covered:

Find by symbol (success, null, empty, not found)

Find by symbol and exchange

Filter stocks with pagination

Pagination parameter validation

Count by sector / ownership

Refresh stock data (success, non-existent)

Filter applications (sector, price, margin of safety)

Preset filters (undervalued, grahamCriteria)

DTO conversion

Run tests:

bash
cd common
mvn test -Dtest=StockServiceImplTest
Test Configuration
properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
Run All Tests
bash
cd common
mvn clean test
🚀 Installation & Setup
Prerequisites
Java 17

Maven 3.8+

PostgreSQL 15+ (or Docker)

Git

Step 1: Clone Repository
bash
git clone https://github.com/faresbenslama/market-tool.git
cd market-tool
Step 2: Build Project
bash
mvn clean install
Step 3: Configure Database
Option A: PostgreSQL locally

sql
CREATE DATABASE tunindex;
CREATE USER postgres WITH PASSWORD 'root';
GRANT ALL PRIVILEGES ON DATABASE tunindex TO postgres;
Option B: Docker

bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=tunindex \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=root \
  -p 5432:5432 \
  postgres:15
Step 4: Start Services
Start in this order:

bash
# Terminal 1 - Eureka Server
cd discovery-server
mvn spring-boot:run

# Terminal 2 - API Service
cd api
mvn spring-boot:run

# Terminal 3 - Collector Service (Instance 1)
cd collector
mvn spring-boot:run

# Terminal 4 - Collector Service (Instance 2 - optional)
cd collector
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082

# Terminal 5 - Collector Service (Instance 3 - optional)
cd collector
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8083
Step 5: Verify Services
Open browser:

Eureka Dashboard: http://localhost:8761

API Swagger UI: http://localhost:8080/swagger-ui.html

API Health: http://localhost:8080/actuator/health

📖 API Usage Examples
Get Stock by Symbol
bash
curl http://localhost:8080/api/v1/stocks/symbol/BH
Filter Stocks
bash
curl -X POST http://localhost:8080/api/v1/stocks/filter \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "size": 10,
    "filters": {
      "sector": "FINANCIALS",
      "minMarginOfSafety": "20",
      "undervalued": "true"
    }
  }'
Trigger Data Refresh
bash
curl -X PUT http://localhost:8080/api/v1/stocks/refresh/BH
Get Statistics
bash
# Count by sector
curl http://localhost:8080/api/v1/stocks/statistics/by-sector

# Count by ownership
curl http://localhost:8080/api/v1/stocks/statistics/by-ownership
⚖️ Load Balancing
Client-Side Load Balancing
API service uses Spring Cloud LoadBalancer to call Collector service:

java
@Bean
@LoadBalanced
public WebClient.Builder loadBalancedWebClientBuilder() {
    return WebClient.builder();
}

// Usage - uses service name instead of hardcoded URL
webClientBuilder.build()
    .post()
    .uri("http://COLLECTOR-SERVICE/internal/collector/stock/BH")
Server-Side Load Balancing (Nginx)
nginx
upstream market_tool_api {
    server localhost:8080;
    server localhost:8084;
    server localhost:8085;
}

server {
    listen 80;
    location /api/ {
        proxy_pass http://market_tool_api;
    }
}
Test Load Balancing
bash
for i in 1 2 3 4 5 6; do
  curl http://localhost:8080/api/test/load-balance-test
  echo ""
  sleep 1
done
🐳 Docker Deployment
Docker Compose
yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: tunindex
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: root
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  discovery-server:
    build: ./discovery-server
    ports:
      - "8761:8761"
    depends_on:
      - postgres

  api:
    build: ./api
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - discovery-server
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/tunindex

  collector:
    build: ./collector
    ports:
      - "8081:8081"
    depends_on:
      - postgres
      - discovery-server
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/tunindex

volumes:
  postgres_data:
Build and Run
bash
docker-compose up -d
🔧 Troubleshooting
Issue	Solution
Eureka connection refused	Start discovery-server first
StockRepository null	Add @EnableJpaRepositories("com.tunindex.market_tool.common.repository")
Port already in use	Change server.port in application.properties
WebClient load balancing not working	Add @LoadBalanced to WebClient.Builder bean
NULL values in filter results	Add isNotNull checks in StockSpecification
Enable SQL Logging for Debugging
properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
📋 Future Enhancements
Add API Gateway (Spring Cloud Gateway)

Add Circuit Breakers (Resilience4j)

Add Distributed Tracing (Zipkin)

Add Metrics Monitoring (Prometheus + Grafana)

Add More Data Providers (Investing.com, Yahoo Finance)

Add Historical Data Storage

Add Email Notifications

Add User Authentication (Spring Security + JWT)

Add React Frontend Dashboard

📄 License
MIT License

Copyright (c) 2025 Fares Ben Slama

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

🙏 Acknowledgments
Spring Cloud team for microservices tools

StockAnalysis.com for data source

Graham for value investing principles

📧 Contact
Fares Ben Slama

GitHub: @faresesprit20