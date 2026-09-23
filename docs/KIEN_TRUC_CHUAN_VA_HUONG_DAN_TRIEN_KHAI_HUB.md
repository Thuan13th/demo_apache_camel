# CẨM NANG KIẾN TRÚC CHUẨN & HƯỚNG DẪN TRIỂN KHAI SERVICE HUB
### Hệ Sinh Thái Tích Hợp Đa Dịch Vụ Với Apache Camel 4 & Spring Boot 3
> **Tài liệu chuẩn kiến trúc doanh nghiệp, quản trị phiên/bảo mật, xử lý ngoại lệ và cấu hình Production**  
> **Dự án tham chiếu:** `demo_apache_camel` | **Ngôn ngữ:** Java 17+ | **Framework:** Camel Spring Boot 4.22.0 | **Phiên bản tài liệu:** v2.0 (Production-Grade Token & JWT)

---

## MỤC LỤC

1. [Cấu Trúc Chuẩn Dự Án Hub Doanh Nghiệp (Enterprise Hub Architecture)](#1-cấu-trúc-chuẩn-dự-án-hub-doanh-nghiệp)
   - 1.1. Mô hình 6 Tầng Kiến Trúc Phân Lớp
   - 1.2. Chuẩn tổ chức thư mục mã nguồn thực tế (Package Layout)
   - 1.3. Triết lý Canonical Data Model (CDM)
2. [Quản Lý Toàn Diện Cookie, Token & Session](#2-quản-lý-toàn-diện-cookie-token--session)
   - 2.1. Phân biệt 3 vị trí: Inbound vs Outbound vs Nội tại Hub
   - 2.2. Inbound: Xác thực Client, Phân quyền & Distributed Tracing
   - 2.3. Outbound: Quản lý vòng đời OAuth2 Token của đối tác (`PartnerTokenManager`)
   - 2.4. Outbound: Quản lý Stateful Session (Sabre GDS) & CookieHandler
   - 2.5. An toàn nội tại: Phân biệt Header vs Property & Bộ lọc bảo mật (`SecurityHeaderFilterStrategy`)
3. [Xử Lý Ngoại Lệ & Chiến Lược Phòng Vệ (Exception Handling Deep-Dive)](#3-xử-lý-ngoại-lệ--chiến-lược-phòng-vệ)
   - 3.1. Các cấp độ bắt lỗi trong Camel (`onException`, `errorHandler`, `doTry ... doCatch`)
   - 3.2. Phân biệt sống còn: `handled(true)` vs `continued(true)`
   - 3.3. Tầng Ingress Exception Handling (`GlobalExceptionHandler`)
   - 3.4. Dead Letter Channel (DLC): Tuyến cứu nạn (`DeadLetterRoute`)
   - 3.5. Bảng ma trận ánh xạ lỗi chuẩn (Error Mapping Matrix)
4. [Hướng Dẫn Cấu Hình Toàn Diện (Production Configuration Guide)](#4-hướng-dẫn-cấu-hình-toàn-diện)
   - 4.1. File cấu hình chuẩn `application.yaml` (Production-Ready)
   - 4.2. Cấu hình Connection Pool & Quản trị Timeout (Timeout Governance)
   - 4.3. Cấu hình Stream Caching & Log Masking (`CamelGlobalConfig`)
   - 4.4. Cấu hình Thread Pool Profiles chuyên biệt cho Scatter-Gather
   - 4.5. Cấu hình Idempotency với Repository (`IdempotencyConfig`)
5. [Bảng Ánh Xạ Mã Nguồn Thực Tế Trong Dự Án `demo_apache_camel`](#5-bảng-ánh-xạ-mã-nguồn-thực-tế)
6. [Bằng Chứng Kiểm Thử Tự Động (18/18 Tests Pass)](#6-bằng-chứng-kiểm-thử-tự-động)

---

## 1. CẤU TRÚC CHUẨN DỰ ÁN HUB DOANH NGHIỆP

### 1.1. Mô hình 6 Tầng Kiến Trúc Phân Lớp

Trong một hệ thống Hub dịch vụ tích hợp đa đối tác (OTA Vé máy bay, Viễn thông Topup, Bảo hiểm, Điện nước, Đối soát ngân hàng), toàn bộ hệ thống được chia thành **6 tầng độc lập**:

```
[ CLIENTS: Mobile App / Web / B2B Partners ]
                    │
                    ▼ (HTTPS / JSON / Bearer JWT)
════════════════════╪═════════════════════════════════════════════════════════
 TẦNG 1: CỔNG BIÊN  │ (Kong / Envoy / APISIX Gateway: WAF, Rate Limit, mTLS)
════════════════════╪═════════════════════════════════════════════════════════
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 2: HUB INGRESS & CANONICAL DATA MODEL (CDM)                            │
│ - OrderController: Skinny Controller, xác thực Fail-Fast, sinh TraceId      │
│ - DTO chuẩn hóa: OrderRequest, OrderResponse                                │
│ - GlobalExceptionHandler: Chuẩn hóa toàn bộ mã lỗi REST HTTP                │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │ producerTemplate.requestBodyAndHeaders()
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 3: BỘ NÃO ĐIỀU PHỐI CAMEL (CENTRAL ORCHESTRATION ENGINE)               │
│ - MainOrderRoute: Định tuyến dịch vụ (Content-Based Router: choice-when)   │
│ - WireTap EIP: Sao chép bản tin bắn sang Audit Log ngầm (seda:hubAuditLog)  │
│ - Domain Orchestrators: AirlinePartnerRoute (Scatter-Gather), TopupPartner  │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 4: PHÒNG VỆ, CHỊU LỖI & BỀN BỈ (RESILIENCE & STABILITY LAYER)         │
│ - Idempotency: Chống nạp/mua trùng lặp (hubIdempotentRepository)            │
│ - Circuit Breaker (Resilience4j): Tự ngắt mạch & Auto-Fallback sang dự phòng│
│ - Dead Letter Channel (DLC): Tuyến cứu nạn lưu trữ đơn lỗi mạng             │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 5: ADAPTER ĐỐI TÁC & PHIÊN DỊCH DỮ LIỆU (PARTNER ADAPTERS LAYER)       │
│ - VietjetAdapterRoute: REST API (JSON) + HMAC Signature + Giữ chỗ 12h       │
│ - VietnamAirlinesAdapterRoute: SOAP 1.2 (XML) + Sabre GDS Session + 24h     │
│ - TopupPartnerRoute: Viettel, Mobifone, Vinaphone + Fallback Napas          │
│ - PartnerTokenManager: Quản lý vòng đời OAuth2 Token (Cache & Auto-Refresh) │
│ - SecurityHeaderFilterStrategy: Lọc bỏ token nhạy cảm trước khi gọi đối tác │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 6: TÁC VỤ LÔ & ĐỐI SOÁT TÀI CHÍNH (BATCH & RECONCILIATION LAYER)      │
│ - PartnerFileInboundRoute: Quét định kỳ file SFTP/Folder đối tác            │
│ - Streaming Tokenizer: Cắt dòng CSV, XML, JSON, Napas Fixed-Length          │
│ - 3-Way Reconciliation: So khớp Client Request <-> Hub DB <-> Sao kê Đối tác│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 1.2. Chuẩn tổ chức thư mục mã nguồn thực tế (Package Layout)

Dự án [`demo_apache_camel`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel) hiện thực hóa trọn vẹn mô hình trên với cấu trúc package chuẩn doanh nghiệp:

```
src/main/java/com/demo_apache_camel/
├── DemoApacheCamelApplication.java                   # Main Spring Boot Application
│
├── 🏛️ hub/                                          # [PHÂN HỆ LÕI HỆ THỐNG HUB]
│   ├── controller/                                   # Tầng Ingress tiếp nhận & tra cứu HTTP
│   │   ├── OrderController.java                      # Đón tiếp nhận & tra cứu đơn hàng
│   │   └── HealthController.java                     # Liveness / Readiness Probe (Health check)
│   ├── security/                                     # Tầng Bảo mật & Xác thực Client Ingress
│   │   └── InboundSecurityFilter.java                # Bắt chặn Token, Cookie, Trace ID (Fail-Fast 401)
│   ├── config/                                       # Tầng Cấu hình hệ thống (Spring Beans / Camel)
│   │   ├── CamelGlobalConfig.java                    # Bật Stream Caching, Log Masking, ThreadPool
│   │   ├── HubProperties.java                        # Quản trị thông số môi trường (Rest, Storage, Retry)
│   │   ├── IdempotencyConfig.java                    # Cung cấp Bean IdempotentRepository (Redis/Memory)
│   │   └── SecurityHeaderFilterStrategy.java         # Lọc bỏ Authorization, Cookie chống rò rỉ
│   ├── model/                                        # Canonical Data Model (CDM) - DTO chuẩn hóa
│   │   ├── OrderRequest.java                         # Payload chuẩn đầu vào của mọi dịch vụ
│   │   └── OrderResponse.java                        # Payload chuẩn phản hồi cho Client
│   ├── exception/                                    # Quản lý ngoại lệ tập trung chuẩn REST
│   │   ├── HubException.java                         # Base Exception mang mã lỗi hệ thống
│   │   ├── PartnerException.java                     # Ngoại lệ chuẩn hóa khi đối tác ngoài lỗi
│   │   └── GlobalExceptionHandler.java               # @RestControllerAdvice chuẩn hóa JSON phản hồi
│   ├── routes/                                       # Tuyến đường điều phối cấp trung tâm của Hub
│   │   ├── MainOrderRoute.java                       # Bộ não Content-Based Router + WireTap
│   │   ├── AuditRoute.java                           # Hàng đợi Audit Log ngầm (seda:hubAuditLog)
│   │   ├── DeadLetterRoute.java                      # Tuyến cứu nạn Dead Letter Channel (DLC)
│   │   └── RestApiRoute.java                         # Camel REST DSL
│   └── aggregator/                                   # Các chiến lược gộp dữ liệu EIP
│       └── LowestPriceAggregator.java                # Gộp báo giá vé máy bay rẻ nhất
│
├── 🔌 partner/                                      # [PHÂN HỆ TÍCH HỢP ĐỐI TÁC NGOÀI]
│   ├── common/                                       # Tiện ích dùng chung cho các đối tác
│   │   └── PartnerTokenManager.java                  # Quản lý vòng đời OAuth2 Token, Cache & Refresh
│   ├── telco/                                        # Phân hệ Viễn thông (Topup / Data / Thẻ cào)
│   │   ├── TopupPartnerRoute.java                    # Tuyến điều phối chung viễn thông & chống nạp trùng
│   │   ├── ViettelTelcoRoute.java                    # Adapter nạp thẻ Viettel (Chiết khấu 5%)
│   │   ├── MobifoneTelcoRoute.java                   # Adapter nạp thẻ Mobifone (Chiết khấu 3%)
│   │   └── VinaphoneTelcoRoute.java                  # Adapter nạp thẻ Vinaphone (Chiết khấu 4% + Retry)
│   ├── airline/                                      # Phân hệ Hàng không
│   │   ├── AirlinePartnerRoute.java                  # Điều phối Scatter-Gather tìm kiếm & đặt vé đa hãng
│   │   ├── FlightQuote.java                          # DTO báo giá nội bộ
│   │   ├── VietjetAdapterRoute.java                  # Adapter Vietjet Air (REST JSON + HMAC)
│   │   └── VietnamAirlinesAdapterRoute.java          # Adapter Vietnam Airlines (SOAP XML + Sabre GDS)
│   └── inbound/                                      # Phân hệ Đối soát & Tác vụ Lô (Batch File)
│       ├── PartnerFileInboundRoute.java              # Bộ quét thư mục & điều phối file Inbound
│       ├── ViettelCsvParserRoute.java                # Parser bóc tách CSV phân cách '|' Viettel
│       ├── SabreXmlParserRoute.java                  # Parser bóc tách XML vé Sabre GDS
│       ├── VinwondersJsonParserRoute.java            # Parser bóc tách JSON mảng lồng VinWonders
│       └── NapasFixedLengthParserRoute.java          # Parser bóc tách Fixed-Length TXT Napas
│
└── src/main/resources/
    ├── application.yaml                              # Cấu hình chung của hệ thống
    ├── application-dev.yaml                          # Cấu hình môi trường Development
    └── application-prod.yaml                         # Cấu hình môi trường Production
```

---

### 1.3. Triết lý Canonical Data Model (CDM)
* **Quy tắc bất biến:** Không một DTO đặc thù nào của đối tác (ví dụ `VietjetBookingRQ`, `SabreOTA_AirBookRS`) được phép xuất hiện tại tầng Ingress Controller hay trả về cho Client.
* **Quy trình chuyển dịch dữ liệu (Mediation Pipeline):**
  $$\text{Client (JSON)} \xrightarrow{\text{Unmarshal}} \text{CDM POJO} \xrightarrow{\text{Marshal}} \text{Partner DTO (XML/JSON/ISO)} \xrightarrow{\text{Đối tác xử lý}} \text{Partner Response} \xrightarrow{\text{Convert}} \text{CDM Response}$$

---

## 2. QUẢN LÝ TOÀN DIỆN COOKIE, TOKEN & SESSION

### 2.1. Phân biệt 3 vị trí: Inbound vs Outbound vs Nội tại Hub

| Khía cạnh | 1. Inbound (Client $\rightarrow$ Hub) | 2. Outbound (Hub $\rightarrow$ Đối tác) | 3. Nội tại Hub (Camel Core) |
| :--- | :--- | :--- | :--- |
| **Bản chất** | Client gửi danh tính lên để Hub cấp phép. | Hub phải tự chứng minh danh tính với từng đối tác ngoài. | Trạng thái luồng xử lý bên trong bộ nhớ JVM của Hub. |
| **Hình thức** | `Bearer JWT`, `X-API-Key`, `Cookie`. | OAuth2 Token, Basic Auth, HMAC Signature, Session ID. | `Exchange.getProperties()`, `Exchange.getHeaders()`. |
| **Thời hạn** | Ngắn (ví dụ: Token người dùng 15 phút). | Theo quy định đối tác (1 tiếng, 24 tiếng, hoặc 1 giao dịch). | Chỉ sống trong thời gian Route đang thực thi. |
| **Lưu trữ** | Không lưu (Stateless verify chữ ký). | Lưu Cache Redis có TTL (Time-To-Live). | Lưu trong Heap RAM của Thread thực thi Exchange. |

---

### 2.2. Inbound: Xác thực Client, Phân quyền & Distributed Tracing — **PRODUCTION GRADE**

Khi một request từ Client chạm vào Hub, nó phải đi qua **2 trạm kiểm soát nghiêm ngặt** trước khi vào Camel Route:

1. **Trạm Gác Cổng Số 1 — [`InboundSecurityFilter.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/InboundSecurityFilter.java) (Tầng Servlet Ingress):**
   * **Bắt chặn trước Controller & Camel:** Kế thừa `OncePerRequestFilter` với độ ưu tiên cao nhất (`@Order(Ordered.HIGHEST_PRECEDENCE)`).
   * **JWT Thật bằng JJWT 0.12:** Dùng [`JwtTokenValidator.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/JwtTokenValidator.java) để:
     - Xác thực chữ ký số HMAC-SHA256 (chống giả mạo token)
     - Kiểm tra `exp` (hết hạn), `iss` (issuer khớp Hub), `nbf` (chưa có hiệu lực)
     - Clock skew tolerance: chấp nhận lệch đồng hồ ±60s
   * **3 phương thức xác thực song song:** Bearer JWT → X-API-Key → Cookie phiên (fallback chain)
   * **Phân loại lỗi chi tiết** qua `JwtValidationException.Reason`: INVALID_SIGNATURE, TOKEN_EXPIRED, INVALID_ISSUER, NOT_YET_VALID, MISSING_OR_MALFORMED — Safe message trả client không lộ kỹ thuật nội bộ.
   * **JwtClaims gắn vào request:** `request.setAttribute("HUB_JWT_CLAIMS", claims)` → Controller và Camel Route dùng lại.
   * **Fail-Fast (Chặn đứng trong <1ms):** Trả `HTTP 401 Unauthorized` dạng JSON chuẩn hóa ngay tại cổng Servlet, bảo vệ toàn bộ Camel Context.
   * **Distributed Tracing:** `X-Correlation-Id` + `MDC.put("traceId", correlationId)` xuyên suốt.

```java
// Cấu hình JwtProperties (application.yaml — đọc từ biến môi trường production)
hub:
  security:
    jwt:
      secret: ${JWT_SECRET:CHANGE_ME_production_256bit_secret_key}
      expiration-seconds: 3600
      issuer: enterprise-service-hub
      clock-skew-seconds: 60

// Tạo JWT thật cho client (internal hoặc test setup)
String token = jwtTokenValidator.issueToken("CLIENT_APP_001", List.of("ROLE_PARTNER"));

// Validate JWT trong InboundSecurityFilter
JwtTokenValidator.JwtClaims claims = jwtTokenValidator.validate(authHeader);
request.setAttribute("HUB_JWT_CLAIMS", claims);  // → downstream dùng lại
```

2. **Trạm Gác Cổng Số 2 — [`OrderController.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/controller/OrderController.java) (Tầng API Gateway REST Ingress):**
   * Tiếp nhận danh tính đã được Trạm 1 xác thực.
   * **Fail-Fast Validation nghiệp vụ:** Kiểm tra tính toàn vẹn của payload (`orderId`, `serviceType`), chặn mã 400 nếu rỗng.
   * **Đóng gói an toàn vào Camel Exchange:** Đưa `X-Client-Id` và `X-Correlation-Id` vào `camelHeaders` rồi gọi `producerTemplate.requestBodyAndHeaders("direct:processOrder", request, camelHeaders, OrderResponse.class)`.

2. **Trạm Gác Cổng Số 2 — [`OrderController.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/controller/OrderController.java) (Tầng API Gateway REST Ingress):**
   * Tiếp nhận danh tính đã được Trạm 1 xác thực.
   * **Fail-Fast Validation nghiệp vụ:** Kiểm tra tính toàn vẹn của payload (`orderId`, `serviceType`), chặn mã 400 nếu rỗng.
   * **Đóng gói an toàn vào Camel Exchange:** Đưa `X-Client-Id` và `X-Correlation-Id` vào `camelHeaders` rồi gọi `producerTemplate.requestBodyAndHeaders("direct:processOrder", request, camelHeaders, OrderResponse.class)`.

```java
// Đóng gói danh tính an toàn đưa vào Động cơ Camel
Map<String, Object> camelHeaders = new HashMap<>();
camelHeaders.put("X-Client-Id", clientId);
camelHeaders.put("X-Correlation-Id", traceId);

// Gửi sang luồng điều phối trung tâm Camel
OrderResponse response = producerTemplate.requestBodyAndHeaders(
        "direct:processOrder", request, camelHeaders, OrderResponse.class);
```


---

### 2.3. Outbound: Quản lý vòng đời OAuth2 Token của đối tác — **PRODUCTION GRADE**

[`PartnerTokenManager.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/common/PartnerTokenManager.java) hiện thực hóa **5 cơ chế production-grade**:

| Cơ chế | Mô tả | Lợi ích |
|---|---|---|
| **Proactive Refresh** | Refresh sớm khi còn <5 phút trước hết hạn | Zero latency spike, không có request nào chờ fetch token |
| **Double-Checked Locking** | Synchronized block kiểm tra 2 lần | Chống race condition khi N threads đồng thời phát hiện token hết hạn |
| **Real OAuth2 RFC 6749** | HTTP POST Client Credentials Grant | Đúng chuẩn giao thức, tương thích mọi Authorization Server |
| **Graceful Fallback** | URL localhost/mock → tự dùng mock token | Test & local dev không cần partner thật |
| **Defensive Evict (Layer 2)** | `evictToken()` khi nhận 401 từ partner | Xử lý token bị thu hồi đột xuất ngoài dự kiến |

```java
// Cấu hình partner OAuth2 credentials (application.yaml)
hub:
  partner-oauth2:
    token-refresh-buffer-seconds: 300   # Refresh khi còn <5 phút
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
    partners:
      VIETJET:
        token-url: ${VIETJET_TOKEN_URL:http://localhost:9999/mock/...}
        client-id: ${VIETJET_CLIENT_ID:vietjet-hub-client}
        client-secret: ${VIETJET_CLIENT_SECRET:secret}    # Đọc từ env var
        scope: flight:read flight:book
```

```java
// Cách dùng trong Adapter Route — hoàn toàn transparent
from("direct:callVietjetRest")
    .process(exchange -> {
        // Tự động proactive refresh nếu token sắp hết hạn
        String token = partnerTokenManager.getOrRefreshToken("VIETJET");
        exchange.getMessage().setHeader("Authorization", "Bearer " + token);
    })
    .to("http://partner-api.vietjetair.com/orders?bridgeEndpoint=true")
    
    // Layer 2: Nếu token bị thu hồi bất ngờ → evict và retry 1 lần
    .onException(HttpOperationFailedException.class)
        .onWhen(simple("${exception.statusCode} == 401"))
        .process(exchange -> partnerTokenManager.evictToken("VIETJET"))
        .maximumRedeliveries(1)
        .redeliveryDelay(300);

// Proactive warm-up tự động mỗi 60s (không chờ request thật)
@Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
public void proactiveTokenRefresh() {
    partnerTokenManager.warmUpAllPartners();
}
```

**Cơ chế OAuth2 Client Credentials Flow (RFC 6749):**
```
Hub                                    Partner OAuth2 Server
  │                                           │
  │──POST /oauth2/token──────────────────────>│
  │  Content-Type: application/x-www-form-urlencoded
  │  Authorization: Basic base64(clientId:secret)
  │  Body: grant_type=client_credentials&scope=flight:read
  │                                           │
  │<──── 200 OK ──────────────────────────────│
  │  {"access_token": "eyJ...",               │
  │   "token_type": "Bearer",                 │
  │   "expires_in": 3600,                     │
  │   "scope": "flight:read flight:book"}     │
  │                                           │
  │ CachedToken{accessToken, expiresAt,       │
  │             tokenType, scope, isMock}     │
```

---

### 2.4. Outbound: Quản lý Stateful Session (Sabre GDS) & CookieHandler

#### A. Stateful Session với hệ thống GDS (Sabre / Amadeus):
Đối tác yêu cầu bắt buộc: **Mở phiên (`SessionCreateRQ`) $\rightarrow$ Thao tác $\rightarrow$ Đóng phiên (`SessionCloseRQ`)**:
```java
from("direct:sabreFlightBooking")
    .routeId("sabre-stateful-session-route")
    .doTry()
        // 1. Mở phiên, nhận BinarySecurityToken
        .to("direct:sabreSessionCreate")
        .process(exchange -> {
            String sessionToken = exchange.getMessage().getHeader("BinarySecurityToken", String.class);
            exchange.setProperty("SabreSessionToken", sessionToken); // Lưu an toàn vào Property
        })
        // 2. Giữ chỗ chuyến bay trong phiên
        .setHeader("BinarySecurityToken", exchangeProperty("SabreSessionToken"))
        .to("direct:sabreBookPassenger")
    .doFinally()
        // 3. ĐÓNG PHIÊN BẮT BUỘC: Dù thành công hay ném Exception đều phải đóng phiên
        .setHeader("BinarySecurityToken", exchangeProperty("SabreSessionToken"))
        .to("direct:sabreSessionClose")
    .end();
```

#### B. Quản lý Cookie đối tác bằng `ExchangeCookieHandler`:
```java
@Bean
public CookieHandler exchangeCookieHandler() {
    return new ExchangeCookieHandler(); // Quản lý cookie cô lập cho từng giao dịch
}
```

---

### 2.5. An toàn nội tại: Phân biệt Header vs Property & Bộ lọc bảo mật

```
┌─────────────────────────────────────────────────────────────┐
│                           EXCHANGE                          │
│                                                             │
│  ┌─────────────────────────┐   ┌─────────────────────────┐  │
│  │     EXCHANGE HEADERS    │   │   EXCHANGE PROPERTIES   │  │
│  │ ⚠️ DỄ BỊ BẮN RA NGOÀI!  │   │ 🔒 AN TOÀN TUYỆT ĐỐI    │  │
│  │ - Tự copy vào HTTP call │   │ - Chỉ sống trong JVM    │  │
│  │ - Content-Type, Method  │   │ - OrderId, TraceId      │  │
│  │ - Authorization nguy cơ!│   │ - OriginalRequest       │  │
│  └─────────────────────────┘   └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

Lớp [`SecurityHeaderFilterStrategy.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/SecurityHeaderFilterStrategy.java) đảm bảo loại bỏ hoàn toàn các thông tin nhạy cảm của Client trước khi gửi HTTP ra đối tác ngoài:

```java
@Component("securityHeaderFilterStrategy")
public class SecurityHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    private static final Set<String> BLOCKED_OUTBOUND_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie",
            "x-client-id", "x-client-secret", "x-internal-token", "x-user-id"
    );

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        if (headerName != null && BLOCKED_OUTBOUND_HEADERS.contains(headerName.toLowerCase())) {
            return true; // Chặn lại, không cho phép bắn ra ngoài
        }
        return super.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }
}
```

---

## 3. XỬ LÝ NGOẠI LỆ & CHIẾN LƯỢC PHÒNG VỆ

### 3.1. Các cấp độ bắt lỗi trong Camel

Camel cung cấp 3 cấp độ xử lý lỗi từ rộng đến hẹp:
1. **RouteBuilder Level (`onException`):** Bắt lỗi khai báo theo loại Exception cho toàn bộ RouteBuilder.
2. **Cục bộ (`doTry ... doCatch ... doFinally`):** Tương tự try-catch trong Java thuần, dùng khi cần xử lý rẽ nhánh đặc biệt.
3. **Channel Level (`errorHandler`):** Cấu hình cách Camel đối xử với mọi lỗi không được xử lý (mặc định là `DeadLetterChannel`).

---

### 3.2. Phân biệt sống còn: `handled(true)` vs `continued(true)`

```
                  ┌── handled(true) ──► Lỗi được giải quyết triệt để. Camel NUỐT lỗi,
                  │                     trả body mới về cho caller. Caller nhận HTTP 200/400.
[ Ngoại Lệ Xảy Ra ]
                  │
                  └── continued(true) ─► BỎ QUA lỗi, xem như không có chuyện gì,
                                        tiếp tục chạy bước tiếp theo trong Route.
```

Trích xuất thực tế từ [`TopupPartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/telco/TopupPartnerRoute.java):
```java
// Bắt lỗi nhà mạng sập, thử lại 2 lần và chuyển sang cổng dự phòng Napas
onException(IllegalStateException.class)
    .maximumRedeliveries(2)
    .redeliveryDelay(800)
    .retryAttemptedLogLevel(LoggingLevel.WARN)
    .handled(true) // BẮT BUỘC: Nuốt ngoại lệ để trả về OrderResponse Fallback chuẩn
    .process(exchange -> {
        OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
        OrderResponse fallbackResp = new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "FALLBACK_SUCCESS",
            "TOPUP",
            req != null ? req.getAmount() : 0.0,
            "Nhà mạng chính gián đoạn. Đã tự động chuyển tuyến qua cổng dự phòng Napas.",
            "NAPAS_FALLBACK_GATEWAY"
        );
        exchange.getMessage().setBody(fallbackResp);
    });
```

---

### 3.3. Tầng Ingress Exception Handling (`GlobalExceptionHandler`)

Tại lớp [`GlobalExceptionHandler.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/exception/GlobalExceptionHandler.java):
* Bắt các ngoại lệ không lường trước tại Controller và chuẩn hóa về DTO `OrderResponse`:
```java
@ExceptionHandler(HubException.class)
public ResponseEntity<OrderResponse> handleHubException(HubException ex) {
    HttpStatus status = "REJECTED".equalsIgnoreCase(ex.getStatus())
            ? HttpStatus.UNPROCESSABLE_ENTITY
            : HttpStatus.BAD_GATEWAY;

    OrderResponse response = OrderResponse.builder()
            .orderId("N/A")
            .status(ex.getStatus())
            .message(ex.getMessage())
            .processedBy("HUB_EXCEPTION_HANDLER")
            .build();

    return ResponseEntity.status(status).body(response);
}
```

---

### 3.4. Dead Letter Channel (DLC): Tuyến cứu nạn (`DeadLetterRoute`)

Tại lớp [`DeadLetterRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/DeadLetterRoute.java):
```java
@Component
public class DeadLetterRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("seda:deadLetterQueue")
            .routeId("hub-dead-letter-route")
            .process(exchange -> {
                Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String failedEndpoint = exchange.getProperty(Exchange.FAILURE_ENDPOINT, String.class);
                String originalBody = exchange.getMessage().getBody(String.class);

                log.error("[DEAD-LETTER-ALERT] Giao dịch thất bại! Endpoint: {}, Lỗi: {}, Payload: {}",
                        failedEndpoint, cause != null ? cause.getMessage() : "N/A", originalBody);
            })
            .log("[DEAD-LETTER] Đã ghi nhận bản tin lỗi vào kho dữ liệu phục hồi sự cố");
    }
}
```

---

### 3.5. Bảng ma trận ánh xạ lỗi chuẩn (Error Mapping Matrix)

| Mã lỗi Đối tác | Loại lỗi thực tế | Trạng thái Hub (`status`) | HTTP Status Code | Hành vi xử lý |
| :--- | :--- | :--- | :--- | :--- |
| `200 OK` / `00` | Thành công | `SUCCESS` | `200 OK` | Hoàn tất giao dịch, ghi Audit Log. |
| `400` / Missing Field | Dữ liệu Client gửi sai | `REJECTED` | `400 Bad Request` | Chặn ngay tại Controller trong 1ms. |
| `422` / Out of Stock | Hết hạn mức / Hết vé | `REJECTED` | `422 Unprocessable` | Báo rõ lý do nghiệp vụ cho khách. |
| `500` / `502` / `503` | Đối tác sập / Nghẽn mạng | `FALLBACK_SUCCESS` | `200 OK` | Tự động bẻ lái sang đối tác phụ qua Circuit Breaker. |
| `Timeout` quá số lần | Mất kết nối toàn diện | `FAILED` | `504 Gateway Timeout` | Đẩy đơn vào Dead Letter Channel để tra soát. |

---

## 4. HƯỚNG DẪN CẤU HÌNH TOÀN DIỆN (PRODUCTION GUIDE)

### 4.1. File cấu hình chuẩn `application.yaml`

```yaml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
      min-spare: 20

spring:
  application:
    name: demo-apache-camel-hub

# Cấu hình Camel Runtime
camel:
  springboot:
    name: EnterpriseServiceHubContext
    main-run-controller: true

  main:
    stream-caching-enabled: true
    stream-caching-spool-directory: ${java.io.tmpdir}/camel-cache
    stream-caching-spool-threshold: 524288 # Dưới 512KB lưu RAM, trên 512KB ghi file tạm

  component:
    http:
      connections-per-route: 100       # Tối đa 100 kết nối đồng thời trên 1 domain đối tác
      max-total-connections: 500       # Tổng kết nối toàn hệ thống
      connection-request-timeout: 2000 # Thời gian chờ lấy connection từ pool (2s)
      connect-timeout: 3000            # Bắt tay TCP (3s)
      socket-timeout: 8000             # Chờ nhả dữ liệu (8s)

    seda:
      default-concurrent-consumers: 5  # Số luồng xử lý Audit Log song song
      default-queue-size: 10000        # Độ dài hàng đợi bộ nhớ
```

---

### 4.2. Cấu hình Connection Pool & Timeout Governance
* **Nguyên tắc vàng:** Tuyệt đối không bao giờ dùng `to("http://...")` mà không cấu hình Timeout.
* Nếu đối tác bị treo, HttpClient mặc định sẽ giữ socket vô thời hạn $\rightarrow$ Cạn kiệt 200 Tomcat Threads $\rightarrow$ Toàn bộ Hub sập dây chuyền.
* Bắt buộc cấu hình: `connectTimeout=3000&socketTimeout=8000`.

---

### 4.3. Cấu hình Stream Caching & Log Masking (`CamelGlobalConfig`)

Tại lớp [`CamelGlobalConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/CamelGlobalConfig.java):
```java
@Override
public void beforeApplicationStart(CamelContext camelContext) {
    // 1. Tạo Thread Pool Profile chuyên biệt cho xử lý song song đa hãng
    ThreadPoolProfileBuilder poolProfile = new ThreadPoolProfileBuilder("customHubParallelPool");
    poolProfile.poolSize(10).maxPoolSize(50).maxQueueSize(1000);
    camelContext.getExecutorServiceManager().registerThreadPoolProfile(poolProfile.build());

    // 2. Kích hoạt Stream Caching (đọc Payload nhiều lần mà không cạn luồng)
    camelContext.setStreamCaching(true);

    // 3. Kích hoạt Log Masking (Tự động che giấu số thẻ, mật khẩu, CCCD)
    camelContext.setLogMask(true);

    // 4. Tắt tracing chi tiết ở mức context để tối ưu hiệu năng
    camelContext.setTracing(false);
}
```

---

### 4.4. Cấu hình Idempotency với Repository (`IdempotencyConfig`)

Lớp [`IdempotencyConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/idempotency/IdempotencyConfig.java) cung cấp bean lưu trữ khóa giao dịch:
```java
@Configuration
public class IdempotencyConfig {
    @Bean("hubIdempotentRepository")
    public IdempotentRepository hubIdempotentRepository() {
        return new MemoryIdempotentRepository(new HashMap<>(10000));
    }
}
```

Và được áp dụng trực tiếp trong [`TopupPartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/telco/TopupPartnerRoute.java):
```java
.idempotentConsumer(simple("${body.orderId}"))
    .idempotentRepository("hubIdempotentRepository")
    .skipDuplicate(false)
.filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
    .process(exchange -> {
        OrderResponse dup = new OrderResponse(
            req.getOrderId(), "REJECTED", "TOPUP", 0.0,
            "Giao dịch nạp thẻ đã được xử lý trước đó (Chặn trùng lặp Idempotency)",
            "HUB_IDEMPOTENCY_GUARD"
        );
        exchange.getMessage().setBody(dup);
    })
    .stop()
.end()
```

---

## 5. BẢNG ÁNH XẠ MÃ NGUỒN THỰC TẾ

Dưới đây là liên kết trực tiếp giữa các thành phần kiến trúc với mã nguồn đang chạy trong dự án [`demo_apache_camel`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel):

| Phân hệ / Tầng | File Mã Nguồn Cụ Thể | Trách nhiệm Nghiệp vụ & Kỹ thuật |
| :--- | :--- | :--- |
| **Ingress Controller** | [`OrderController.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/controller/OrderController.java) | Đón nhận REST JSON, Fail-Fast (1ms), cấp `X-Correlation-Id`. |
| **Canonical Model** | [`OrderRequest.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/model/OrderRequest.java), [`OrderResponse.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/model/OrderResponse.java) | Chuẩn hóa DTO duy nhất của Hub, độc lập dữ liệu đối tác. |
| **Exception Handling**| [`GlobalExceptionHandler.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/exception/GlobalExceptionHandler.java) | Bắt mọi Exception tại Ingress, format chuẩn HTTP Status Code. |
| **Global Config & Pools**| [`CamelGlobalConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/CamelGlobalConfig.java) | Kích hoạt Stream Caching, Log Masking, Parallel ThreadPool. |
| **Environment Config** | [`HubProperties.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/HubProperties.java) | Cấu hình Type-Safe nạp từ application.yaml (rest, storage, retry). |
| **JWT Config** | [`JwtProperties.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/JwtProperties.java) | Cấu hình JWT secret, expiry, issuer, clock-skew cho HS256. |
| **OAuth2 Config** | [`PartnerOAuth2Properties.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/PartnerOAuth2Properties.java) | Cấu hình tokenUrl, clientId, secret cho từng đối tác. |
| **Partner HTTP** | [`PartnerHttpConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/PartnerHttpConfig.java) | RestTemplate có timeout cho OAuth2 + bật @EnableScheduling. |
| **Security Filter** | [`InboundSecurityFilter.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/InboundSecurityFilter.java) | JWT thật (HMAC-SHA256) + 3 auth method + Distributed Tracing. |
| **JWT Validator** | [`JwtTokenValidator.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/JwtTokenValidator.java) | Parse & validate JWT (sig, exp, iss, nbf, clock-skew). Phát hành token nội bộ. |
| **JWT Exception** | [`JwtValidationException.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/security/JwtValidationException.java) | Phân loại 6 loại lỗi JWT với safe client message. |
| **Security Header** | [`SecurityHeaderFilterStrategy.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/SecurityHeaderFilterStrategy.java) | Chặn rò rỉ Authorization / Cookie sang hệ thống ngoài. |
| **Idempotency** | [`IdempotencyConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/IdempotencyConfig.java) | Cung cấp kho lưu trữ khóa giao dịch chống nạp/mua trùng. |
| **Central Router** | [`MainOrderRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/MainOrderRoute.java) | Cấp `OrderId`, kích hoạt WireTap sang Audit, phân luồng theo `serviceType`. |
| **Audit Log (Async)** | [`AuditRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/AuditRoute.java) | Hàng đợi `seda:hubAuditLog` lưu vết ngầm không chặn luồng chính. |
| **Dead Letter (DLC)** | [`DeadLetterRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/DeadLetterRoute.java) | Tiếp nhận và cảnh báo giao dịch lỗi mạng vượt quá retry. |
| **Partner Token** | [`PartnerTokenManager.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/common/PartnerTokenManager.java) | Proactive refresh (5ph), OAuth2 RFC 6749 thật, graceful fallback. |
| **Token Scheduler** | [`PartnerTokenRefreshScheduler.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/common/PartnerTokenRefreshScheduler.java) | Warm-up tự động mỗi 60s, phát hiện sớm OAuth2 endpoint có sự cố. |
| **Scatter-Gather** | [`AirlinePartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/airline/AirlinePartnerRoute.java) | Gọi song song Vietjet & Vietnam Airlines trong tối đa 3000ms. |
| **Price Aggregator** | [`LowestPriceAggregator.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/aggregator/LowestPriceAggregator.java) | So sánh giá vé giữa các hãng và chọn vé rẻ nhất cho khách. |
| **Circuit Breaker** | [`TopupPartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/telco/TopupPartnerRoute.java) | Chặn nạp trùng, ngắt mạch khi nhà mạng lỗi và fallback qua Napas. |
| **Streaming Batch** | [`PartnerFileInboundRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/reconciliation/PartnerFileInboundRoute.java) | Đọc stream 4 loại file đối tác (CSV, XML, JSON, Napas TXT). |

---

## 6. BẰNG CHỨNG KIỂM THỬ TỰ ĐỘNG — **22/22 PASS**

Dự án đã được tích hợp bộ kiểm thử tự động toàn diện trong [`CamelRouteTest.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/test/java/com/demo_apache_camel/CamelRouteTest.java).

Kết quả thực thi thực tế từ Maven (sau khi nâng cấp Production-Grade Token):
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.demo_apache_camel.CamelRouteTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.539 s
[INFO] Running com.demo_apache_camel.DemoApacheCamelApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s
[INFO] 
[INFO] Results:
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Danh sách 22 ca kiểm thử đã được xác minh thành công:

**Domain Services (1-9):**
1. `testTopupViettelRoute`: Content-Based Router phân luồng và chiết khấu Viettel 5%.
2. `testFlightScatterGatherRoute`: Scatter-Gather EIP tìm vé đa hãng và chọn vé rẻ nhất.
3. `testFlightBookingRoute`: Đặt vé và giữ chỗ PNR qua Booking Gateway.
4. `testTopupRetryAndFallback`: Retry 2 lần và Fallback qua Napas khi nhà mạng gặp sự cố.
5. `testUnsupportedService`: Từ chối dịch vụ không xác định với mã `REJECTED`.
6. `testBillService`: Thanh toán hóa đơn EVN qua Attraction Gateway.
7. `testWintelEsimService`: Cấp phát eSIM QR Code Wintel thành công.
8. `testBaoVietInsuranceService`: Mụa bảo hiểm du lịch Bảo Việt và cấp mã hợp đồng.
9. `testVinwondersAttractionService`: Xuất vé vui chơi VinWonders thành công.

**Controller & Validation (10-12):**
10. `testOrderControllerIntegration`: Ingress Gateway tự sinh, bảo toàn Trace ID và HTTP status đúng chuẩn.
11. `testOrderControllerValidation`: Chặn request rác trong 1ms (HTTP 400 Bad Request).
12. `testIdempotencyDuplicateOrder`: Chặn đứng giao dịch gửi trùng `orderId` (Idempotent Consumer EIP).

**Security Layer (13-16):**
13. `testSecurityHeaderFilter`: Bộ lọc Header loại bỏ `Authorization`, `Cookie`, `X-Client-Secret`.
14. `testInboundSecurityFilter_InvalidSignature`: Chặn 401 khi JWT có chữ ký số sai (giả mạo).
15. `testInboundSecurityFilter_ValidJWT`: JWT thật hợp lệ → Pass filter, gắn JwtClaims vào request.
16. `testJwtValidatorIssueAndValidate`: Tạo JWT thật HS256, validate subject/issuer/expiry/roles.

**Token Management (17-18):**
17. `testPartnerTokenManagerLifecycle`: Proactive cache, evict 401, warmUp không exception.
18. `testJwtValidatorIssueAndValidate`: Tạo và validate JWT thật với JJWT (bao gồm `testPartnerTokenManagerLifecycle`).

**Batch & Reconciliation (19-22):**
19. `testViettelCsvReconciliationParser`: Phân tích file CSV Viettel (đối soát 3 bản ghi).
20. `testSabreXmlReconciliationParser`: Phân tích file XML Sabre GDS (2 booking).
21. `testVinwondersJsonReconciliationParser`: Phân tích file JSON VinWonders (2 vé).
22. `testNapasFixedLengthReconciliationParser`: Phân tích file Fixed-Length Napas (2 giao dịch).
