# syntax=docker/dockerfile:1

# ============================================================================
# Builder - one reactor build for every module.
#
# `common` is a plain library jar (no spring-boot-maven-plugin, see its pom)
# that the other eight modules depend on through the Maven reactor - it is
# never published to a repository anywhere. That means it cannot be built
# once and cached independently per service the way a published dependency
# would be: `mvn package` has to run from backend/, where the parent pom's
# <modules> list lets Maven build all nine in dependency order in one
# session. Building any service module alone, without this stage, fails to
# resolve common at all.
#
# One slower shared layer beats nine faster-but-wrong ones.
# ============================================================================
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

# ============================================================================
# Runtime stages - one per service, each copying only its own jar off the
# shared builder. Docker/BuildKit builds `builder` once and reuses it for
# every stage below; `docker compose build` with several `target:` values
# against this same Dockerfile does not repeat the Maven build per service.
#
# `target/*.jar` is unambiguous: spring-boot-maven-plugin's repackage goal
# keeps the original, un-repackaged jar as `*.jar.original` (verified against
# a real build - discovery-server/target held exactly discovery-server-1.0.0
# .jar and discovery-server-1.0.0.jar.original, nothing else), so the glob
# never matches more than the one executable jar meant to run.
#
# jre-alpine rather than the jdk image: nothing here compiles at runtime.
# ============================================================================

FROM eclipse-temurin:17-jre-alpine AS discovery-server
WORKDIR /app
COPY --from=builder /build/discovery-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS api-gateway
WORKDIR /app
COPY --from=builder /build/api-gateway/target/*.jar app.jar
EXPOSE 8070
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS collector
WORKDIR /app
COPY --from=builder /build/collector/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS api
WORKDIR /app
COPY --from=builder /build/api/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS ad-module
WORKDIR /app
COPY --from=builder /build/ad-module/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS mailing-service
WORKDIR /app
COPY --from=builder /build/mailing-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS sms-service
WORKDIR /app
COPY --from=builder /build/sms-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jre-alpine AS recaptcha-service
WORKDIR /app
COPY --from=builder /build/recaptcha-service/target/*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
