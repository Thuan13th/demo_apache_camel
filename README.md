# DU AN MAU THUC CHIEN: APACHE CAMEL SERVICE HUB (TINH GON)
### Ban kien truc truc quan giup ban nam tron ven ban chat va luong luan chuyen du lieu cua Apache Camel

> **TAI LIEU KIEN TRUC & HUONG DAN CHUYEN SAU:**
> - [KIEN_TRUC_CHUAN_VA_HUONG_DAN_TRIEN_KHAI_HUB.md](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/docs/KIEN_TRUC_CHUAN_VA_HUONG_DAN_TRIEN_KHAI_HUB.md): Cam nang toan dien ve **Cau truc chuan Doanh nghiep**, **Quan tri Cookie/Token/Session (Inbound/Outbound)**, **Bat Exception chuyen sau (`handled` vs `continued`, Retry, DLC)** va **Bo cau hinh Production hoan chinh (`application.yaml`, Pools, Stream Caching, PII Masking)**.

---

## 1. Gioi Thieu Du An

Du an nay la phien ban kien truc chuan tinh gon cua **He sinh thai Hub Tich Hop Dich Vu (Service Hub Aggregator)** duoc xay dung tren nen tang **Spring Boot 3.4 & Apache Camel 4.22.0 (Java 17)**.

Du an tap trung vao **5 Tru Cot Kien Truc Trong Tam** cua Apache Camel:
1. **Single Entry Ingress & Security:** Tiep nhan REST, xac thuc Client, Fail-fast 401, quan ly Distributed Tracing.
2. **Central Orchestration & WireTap:** Phan luong dich vu thong minh (`choice - when`) va ghi log kiem toan ngam bat dong bo qua SEDA.
3. **Scatter-Gather EIP:** Ban yeu cau khao gia ve song song sang Vietjet Air va Vietnam Airlines tren 2 luong rieng biet, sau do tu dong so khop va chon ra ve co gia re nhat qua `LowestPriceAggregator`.
4. **Idempotency & Resilience:** Chong nap the trung lap qua `hubIdempotentRepository`, tu dong Retry khi nha mang loi va Fallback sang Napas.
5. **Partner Token Lifecycle:** Quan ly vong doi OAuth2 Token cua doi tac ngoai (Cache & Auto-Refresh khi 401) va tuoc bo header nhay cam truoc khi goi doi tac.

---

## 2. Cau Truc Ma Nguon Tinh Gon (Slim Architecture)

Du an phan dinh ranh gioi kien truc ro rang giua **Lop Loi cua Hub** va **Lop Ket Noi Doi Tac**:

```
c:\WorkSpace\ZenoAI\APACHE_CAMEL\demo_apache_camel
│
├── src/main/java/com/demo_apache_camel/
│   ├── DemoApacheCamelApplication.java      # Main khoi chay Spring Boot
│   │
│   ├── hub/                                 # [PHAN HE LOI SERVICE HUB NOI BO]
│   │   ├── controller/
│   │   │   └── OrderController.java         # REST API Ingress tiep nhan, tra cuu don hang & Health check
│   │   ├── security/
│   │   │   └── InboundSecurityFilter.java   # [TRAM GAC CONG 1] Bat & xac thuc Token, Cookie, Trace ID
│   │   ├── config/                          # [CAU HINH TAP TRUNG] Cau hinh he thong & Beans
│   │   │   ├── CamelGlobalConfig.java       # Bat Stream Caching, Log Masking, ThreadPool Profile
│   │   │   ├── HubProperties.java           # Quan tri thong so moi truong tu YAML
│   │   │   ├── IdempotencyConfig.java       # Cung cap Bean IdempotentRepository chong trung lap
│   │   │   └── SecurityHeaderFilterStrategy.java # Loc bo Authorization, Cookie chong ro ri Outbound
│   │   ├── model/
│   │   │   ├── OrderRequest.java            # Canonical Data Model (CDM) chuan dau vao
│   │   │   └── OrderResponse.java           # DTO ket qua chuan phan hoi Client
│   │   ├── exception/                       # Quan ly ngoai le tap trung chuan REST
│   │   │   ├── HubException.java            # Base Exception mang ma loi he thong
│   │   │   ├── PartnerException.java        # Ngoai le chuan hoa khi doi tac ngoai loi
│   │   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice chuan hoa JSON phan hoi
│   │   ├── routes/
│   │   │   ├── MainOrderRoute.java          # Bo nao dieu phoi trung tam (Router + WireTap)
│   │   │   ├── AuditRoute.java              # Kenh ghi log kiem toan bat dong bo qua SEDA
│   │   │   └── DeadLetterRoute.java         # Tuyen cuu nan Dead Letter Channel (DLC)
│   │   └── aggregator/
│   │       └── LowestPriceAggregator.java   # Chien luoc chon loc ve uu dai nhat cua Hub
│   │
│   └── partner/                             # [PHAN HE TICH HOP DOI TAC NGOAI]
│       ├── common/
│       │   └── PartnerTokenManager.java     # Quan ly vong doi OAuth2 Token doi tac (Cache & Refresh)
│       ├── telco/
│       │   └── TopupPartnerRoute.java       # [IDEMPOTENCY + RETRY/FALLBACK] Nap the Viettel/Mobi/Vina
│       └── airline/
│           ├── AirlinePartnerRoute.java     # [SCATTER-GATHER EIP] Tim ve song song Vietjet & Vietnam Airlines
│           └── FlightQuote.java             # Model bao gia chuyen bay noi bo
│
├── src/test/java/com/demo_apache_camel/
│   └── CamelRouteTest.java                  # 13 Unit Tests kiem thu tron ven 5 tru cot kien truc
│
└── pom.xml                                  # Cau hinh Maven & dependencies
```

---

## 3. Huong Dan Kiem Thu Nhanh (Quick Start)

### Chay Toan Bo Test Suite Tu Dong
```powershell
.\mvnw.cmd test
```

### Goi API Tao Don Hang Mau (cURL)
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: PARTNER_MOBILE" \
  -H "Authorization: Bearer my-secure-token" \
  -d '{
    "orderId": "ORD-LIVE-001",
    "customerName": "Nguyen Van A",
    "serviceType": "TOPUP",
    "provider": "VIETTEL",
    "amount": 100000.0
  }'
```

Phan hoi mau thanh cong tu Hub:
```json
{
  "orderId": "ORD-LIVE-001",
  "status": "SUCCESS",
  "serviceType": "TOPUP",
  "finalAmount": 95000.0,
  "message": "Nap the Viettel thanh cong (Chiet khau 5%)",
  "processedBy": "VIETTEL_TELCO_DIRECT"
}
```
