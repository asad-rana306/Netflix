# User Service (`user-service`)

An enterprise-grade core microservice responsible for authentication, JWT lifecycle management, multi-device session tracking, profile customization, and event-driven subscription synchronizations for the Netflix platform.

---

## 1. System Overview & Architecture

The `user-service` acts as the primary identity provider (IdP) and profile gateway in the microservice ecosystem. Built with Spring Boot 3.3.3 and Java 21, it features a hardened stateless JWT architecture, Redis-backed multi-device session tracking with token rotation, and asynchronous Kafka messaging for downstream event propagation.

---

## 2. Tech Stack & Infrastructure Dependencies

| Technology | Version / Specification | Justification & Role in Architecture |
| :--- | :--- | :--- |
| **Java** | OpenJDK 21 | Long-Term Support (LTS) release utilizing virtual threads and high-concurrency memory efficiency. |
| **Spring Boot** | 3.3.3 | Enterprise microservice framework managing DI, REST APIs, and transactional boundaries. |
| **Spring Security** | Stateless + BCrypt (12) | Enforces role-based route authorizations, JWT header validation, and BCrypt credential hashing. |
| **PostgreSQL** | 16 (HikariCP) | Relational storage for core identity entities (`User`, `Profile`, `Subscription`). |
| **Redis** | 7.x (Data Redis) | Low-latency in-memory hash mapping for active device sessions (`user:sessions:{userId}`) and refresh tokens. |
| **Apache Kafka** | 3.x (Spring Kafka) | Asynchronous messaging bus for emitting sign-up events (`user-events`) and consuming payment updates (`payment-events`). |
| **Jackson / JJWT** | 0.12.x | Secure JSON serialization with `PolymorphicTypeValidator` and HMAC-SHA512 token signing. |

---

## 3. Key Security & Resilience Hardening

### 🔒 Identity & Access Management (IAM)
* **JWT Access & Refresh Token Rotation:** Issues short-lived Access Tokens (15-min TTL) and single-use Refresh Tokens (30-day TTL). Exchanging a refresh token automatically revokes the old token and issues a new token pair.
* **Anti-IDOR Protections:** Endpoint paths like `/public/logout-all`, `/public/sessions`, and `/api/v1/profiles/{profileId}/verify-pin` extract the caller's identity strictly from the verified JWT context or valid refresh token payload, preventing unauthorized cross-user data tampering.
* **Clickjacking & Headers Defense:** Security filters enforce `X-Frame-Options: DENY` and explicit Content-Type headers while removing raw HTML error leaks on unauthorized (401) or forbidden (403) route accesses.

### 🛡️ Infrastructure & Payload Security
* **Kafka Deserialization Safeguards:** Restricted `spring.json.trusted.packages` to explicit DTO package boundaries (`com.Netfilx.User.DTO.Request,com.Netfilx.User.Event`), eliminating Remote Code Execution (RCE) gadget vulnerabilities.
* **Redis Polymorphic Type Validation:** Enabled Jackson `PolymorphicTypeValidator` on `RedisTemplate` to prevent arbitrary class injection during cache reads/writes.
* **PII & Audit Logging Compliance:** Server logs mask sensitive user emails and credentials during event dispatches, recording non-sensitive `userId` identifiers instead to comply with privacy regulations (GDPR/SOC2).

---

## 4. Cache & Session Lifecycle Architecture


To prevent Redis RAM memory leaks, device session tracking utilizes a synchronized TTL strategy:
* **Key Format:** `user:sessions:{userId}` (Redis Hash Map)
* **Hash Field:** `{deviceId}` $\rightarrow$ Serialized `UserSession` payload
* **Automatic TTL Synchronization:** Every session update programmatically refreshes `redisTemplate.expire(key, 30_DAYS)` to guarantee abandoned session hashes are garbage collected.

---

## 5. Event-Driven Architecture (Kafka Specifications)

### Producer (`user-events`)
* **Trigger:** Dispatched when a new user completes `/public/signup`.
* **Topic:** `user-events` (3 Partitions, 1 Replica)
* **Payload:** `UserRegisteredEvent` (`userId`, `email`, `verificationToken`, `createdAt`)
* **Reliability:** Non-blocking async dispatch utilizing `CompletableFuture` callbacks to catch broker timeouts and log delivery partition/offset metadata.

### Consumer (`payment-events`)
* **Trigger:** Listens for subscription billing events from `payment-service`.
* **Topic:** `payment-events` (Group: `user-service-group`)
* **Consumer Idempotency:** Implements a Redis `SETNX` guard (`processed:payment-event:{userId}:{eventType}:{timestamp}`) with a 7-day TTL. Duplicate Kafka messages are rejected in `<0.5ms` without executing database writes.
* **State Sync:**
    * `PAYMENT_SUCCEEDED` / `INVOICE_PAYMENT_SUCCEEDED` $\rightarrow$ Sets `Subscription.status = ACTIVE` and updates `planTier`.
    * `PAYMENT_FAILED` / `CANCELED` / `DELETED` $\rightarrow$ Sets `Subscription.status = INACTIVE`.

---

## 6. Database Schema & Entities

---

## 7. REST API Reference

### Public Authentication & Health Routes (`/public`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/public/health` | Public | Health probe returning service readiness status. |
| `POST` | `/public/signup` | Public | Registers a new account, hashes password, and emits Kafka event. |
| `POST` | `/public/login` | Public | Authenticates credentials, stores Redis session, and issues JWT pair. |
| `POST` | `/public/refresh` | Public | Validates refresh token and returns a rotated token pair. |
| `POST` | `/public/logout` | Public | Revokes current device refresh token and deletes session hash. |
| `POST` | `/public/logout-all` | Authenticated | Revokes all active device sessions for the token holder. |
| `POST` | `/public/sessions` | Authenticated | Returns all active logged-in device sessions for the user. |

### User Profile Management Routes (`/api/v1/profiles`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/profiles` | JWT Required | Creates a new profile (max 5 profiles per account). |
| `GET` | `/api/v1/profiles` | JWT Required | Fetches all profiles associated with the account. |
| `POST` | `/api/v1/profiles/{id}/verify-pin` | JWT Required | Validates 4-digit numeric PIN with ownership verification. |
| `DELETE` | `/api/v1/profiles/{id}` | JWT Required | Deletes a profile (requires account to retain $\ge1$ profile). |

---

## 8. Environment & Configuration Guide

All infrastructure endpoints and credentials use Spring property placeholders with local development defaults:

```yaml
# Injected Configuration Keys (application.yaml)
server:
  port: ${PORT:8081}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/netflix_user_db}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgre123}
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

jwt:
  secret: ${JWT_SECRET:TaK+HaV^uvCHEFsEVfypW#7g9^k*Z8$VTaK+HaV^uvCHEFsEVfypW#7g9^k*Z8$V}
  access-token-expiration-ms: 900000        # 15 Minutes
  refresh-token-expiration-ms: 2592000000    # 30 Days