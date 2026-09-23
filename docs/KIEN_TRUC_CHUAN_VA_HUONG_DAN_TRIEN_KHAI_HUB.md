# CẨM NANG KIẾN TRÚC CHUẨN & HƯỚNG DẪN TRIỂN KHAI SERVICE HUB
### Hệ Sinh Thái Tích Hợp Đa Dịch Vụ Với Apache Camel 4 & Spring Boot 3
> **Tài liệu chuẩn kiến trúc doanh nghiệp, quản trị phiên/bảo mật, xử lý ngoại lệ và cấu hình Production**  
> **Dự án tham chiếu:** `demo_apache_camel` | **Ngôn ngữ:** Java 17+ | **Framework:** Camel Spring Boot 4.22.0

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

### 2.2. Inbound: Xác thực Client, Phân quyền & Distributed Tracing

Khi một request từ Client chạm vào Hub, nó phải đi qua **2 trạm kiểm soát nghiêm ngặt** trước khi vào Camel Route:

1. **Trạm Gác Cổng Số 1 — [`InboundSecurityFilter.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/InboundSecurityFilter.java) (Tầng Servlet Ingress):**
   * **Bắt chặn trước Controller & Camel:** Kế thừa `OncePerRequestFilter` với độ ưu tiên cao nhất (`@Order(Ordered.HIGHEST_PRECEDENCE)`).
   * **Quản lý Token (Bearer JWT / API Key):** Trích xuất header `Authorization: Bearer <jwt>`, kiểm tra chữ ký số, thời hạn (`exp`), kiểm tra danh sách đen (Blacklist trong Redis).
   * **Quản lý Cookie & Session:** Đọc Cookie định danh phiên (nếu có từ Web/SPA), xác minh tính hợp lệ.
   * **Fail-Fast (Chặn đứng trong 1ms):** Nếu Token giả mạo hoặc hết hạn, lập tức trả về `HTTP 401 Unauthorized` dạng JSON chuẩn ngay tại cổng Servlet. Tuyệt đối không để request rác lọt vào Controller hay tiêu tốn tài nguyên Camel Context.
   * **Distributed Tracing:** Khởi tạo `X-Correlation-Id` và gắn vào `MDC.put("traceId", correlationId)` để toàn bộ log của request đều có vết đồng nhất.

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

### 2.3. Outbound: Quản lý vòng đời OAuth2 Token của đối tác (`PartnerTokenManager`)

Lớp [`PartnerTokenManager.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/common/token/PartnerTokenManager.java) hiện thực hóa chuẩn Thread-safe Token Lifecycle:

```java
@Slf4j
@Service
public class PartnerTokenManager {

    private final Map<String, CachedToken> tokenStore = new ConcurrentHashMap<>();

    public String getOrRefreshToken(String partnerCode) {
        CachedToken cached = tokenStore.get(partnerCode);

        if (cached == null || cached.isExpired()) {
            synchronized (this) {
                cached = tokenStore.get(partnerCode);
                if (cached == null || cached.isExpired()) {
                    log.info("[PARTNER-TOKEN] Token của đối tác '{}' đã hết hạn. Đang cấp mới...", partnerCode);
                    cached = requestNewTokenFromPartner(partnerCode);
                    tokenStore.put(partnerCode, cached);
                }
            }
        }
        return cached.getToken();
    }

    public void evictToken(String partnerCode) {
        log.warn("[PARTNER-TOKEN] Thu hồi token đối tác '{}' do nhận lỗi 401", partnerCode);
        tokenStore.remove(partnerCode);
    }
}
```

* **Xử lý tự động Refresh Token khi gặp lỗi 401:**
```java
from("direct:callVietjetRest")
    .process(exchange -> {
        String token = partnerTokenManager.getOrRefreshToken("VIETJET");
        exchange.getMessage().setHeader("Authorization", "Bearer " + token);
    })
    .to("http://partner-api.vietjetair.com/orders?bridgeEndpoint=true")
    
    // Nếu token bị đối tác thu hồi sớm -> Tự động xóa cache và thử lại 1 lần
    .onException(HttpOperationFailedException.class)
        .onWhen(simple("${exception.statusCode} == 401"))
        .process(exchange -> partnerTokenManager.evictToken("VIETJET"))
        .maximumRedeliveries(1)
        .redeliveryDelay(300);
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
| **Security Header** | [`SecurityHeaderFilterStrategy.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/SecurityHeaderFilterStrategy.java) | Chặn rò rỉ Authorization / Cookie sang hệ thống ngoài. |
| **Idempotency** | [`IdempotencyConfig.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/config/IdempotencyConfig.java) | Cung cấp kho lưu trữ khóa giao dịch chống nạp/mua trùng. |
| **Central Router** | [`MainOrderRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/MainOrderRoute.java) | Cấp `OrderId`, kích hoạt WireTap sang Audit, phân luồng theo `serviceType`. |
| **Audit Log (Async)** | [`AuditRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/AuditRoute.java) | Hàng đợi `seda:hubAuditLog` lưu vết ngầm không chặn luồng chính. |
| **Dead Letter (DLC)** | [`DeadLetterRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/routes/DeadLetterRoute.java) | Tiếp nhận và cảnh báo giao dịch lỗi mạng vượt quá retry. |
| **Partner Token** | [`PartnerTokenManager.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/common/PartnerTokenManager.java) | Thread-safe Token Cache (TTL 1h) và Auto-refresh khi gặp 401. |
| **Scatter-Gather** | [`AirlinePartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/airline/AirlinePartnerRoute.java) | Gọi song song Vietjet & Vietnam Airlines trong tối đa 3000ms. |
| **Price Aggregator** | [`LowestPriceAggregator.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/hub/aggregator/LowestPriceAggregator.java) | So sánh giá vé giữa các hãng và chọn vé rẻ nhất cho khách. |
| **Circuit Breaker** | [`TopupPartnerRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/telco/TopupPartnerRoute.java) | Chặn nạp trùng, ngắt mạch khi nhà mạng lỗi và fallback qua Napas. |
| **Streaming Batch** | [`PartnerFileInboundRoute.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/main/java/com/demo_apache_camel/partner/inbound/PartnerFileInboundRoute.java) | Đọc stream 4 loại file đối tác (CSV, XML, JSON, Napas TXT). |

---

## 6. BẰNG CHỨNG KIỂM THỬ TỰ ĐỘNG

Dự án đã được tích hợp bộ kiểm thử tự động toàn diện trong [`CamelRouteTest.java`](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/src/test/java/com/demo_apache_camel/CamelRouteTest.java).

Kết quả thực thi thực tế từ Maven:
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.demo_apache_camel.CamelRouteTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.307 s
[INFO] Running com.demo_apache_camel.DemoApacheCamelApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s
[INFO] 
[INFO] Results:
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Danh sách 19 ca kiểm thử đã được xác minh thành công:
1. `testTopupViettelRoute`: Content-Based Router phân luồng và chiết khấu Viettel 5%.
2. `testFlightScatterGatherRoute`: Scatter-Gather EIP tìm vé đa hãng và chọn vé rẻ nhất.
3. `testFlightBookingRoute`: Đặt vé Vietjet LCC (giữ chỗ 12h, PNR VJ-).
4. `testFlightBookingVietnamAirlinesRoute`: Đặt vé VNA Sabre GDS (giữ chỗ 24h, 23kg hành lý, dặm Lotusmiles).
5. `testTopupRetryAndFallback`: Tự phục hồi Retry 2 lần và Fallback qua Napas khi nhà mạng gặp sự cố.
6. `testUnsupportedService`: Từ chối dịch vụ không xác định với mã `REJECTED`.
7. `testViettelCsvParsing`: Phân tích Streaming file CSV Viettel (phân cách `|`).
8. `testSabreXmlParsing`: Phân tích Streaming file XML Hàng không Sabre GDS.
9. `testVinwondersJsonParsing`: Phân tích Streaming file JSON Batch VinWonders.
10. `testNapasTxtParsing`: Phân tích Streaming file Fixed-Length Napas.
11. `testOrderControllerSuccessWithCorrelationId`: Ingress Gateway tự sinh và bảo toàn Trace ID.
12. `testOrderControllerFailFastValidation`: Chặn request rác trong 1ms (HTTP 400 Bad Request).
13. `testOrderControllerRejectedServiceReturns422`: Dịch vụ bị từ chối trả về HTTP 422 Unprocessable Entity.
14. `testOrderControllerInquiryAndHealth`: Tra cứu trạng thái đơn hàng và kiểm tra Probe K8s.
15. `testRestDslOrderBridge`: Cầu nối Camel REST DSL tiếp nhận đơn thành công.
16. `testPartnerTokenLifecycle`: Quản lý vòng đời OAuth2 Token đối tác (cấp mới, lưu cache, thu hồi khi 401).
17. `testIdempotentConsumerDuplicateBlocking`: Chặn đứng giao dịch gửi trùng `orderId` (Idempotent Consumer EIP).
18. `testSecurityHeaderFilter`: Bộ lọc Header loại bỏ `Authorization`, `Cookie`, `X-Client-Secret`.
19. `contextLoads`: Khởi tạo thành công toàn bộ Spring Boot Application Context và CamelContext.
