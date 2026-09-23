# DỰ ÁN MẪU THỰC CHIẾN: APACHE CAMEL SERVICE HUB (ENTERPRISE GRADE)
### Hệ Sinh Thái Tích Hợp Đa Dịch Vụ: Hàng Không, Viễn Thông, SIM/eSIM, Bảo Hiểm, Vé Vui Chơi & Đối Soát File Lô

> **TÀI LIỆU KIẾN TRÚC & HƯỚNG DẪN CHUYÊN SÂU:**
> - [KIEN_TRUC_CHUAN_VA_HUONG_DAN_TRIEN_KHAI_HUB.md](file:///c:/WorkSpace/ZenoAI/APACHE_CAMEL/demo_apache_camel/docs/KIEN_TRUC_CHUAN_VA_HUONG_DAN_TRIEN_KHAI_HUB.md): Cẩm nang toàn diện về **Cấu trúc chuẩn Doanh nghiệp**, **Quản trị Cookie/Token/Session (Inbound/Outbound)**, **Bắt Exception chuyên sâu (`handled` vs `continued`, Retry, DLC)** và **Bộ cấu hình Production hoàn chỉnh (`application.yaml`, Pools, Stream Caching, PII Masking)**.

---

## 1. Giới Thiệu Dự Án

Dự án này là phiên bản kiến trúc chuẩn doanh nghiệp (Clean Architecture & Domain-Driven Design) của **Hệ sinh thái Hub Tích Hợp Dịch Vụ (Service Hub Aggregator)** được xây dựng trên nền tảng **Spring Boot 3.4 & Apache Camel 4.22.0 (Java 17)**.

Dự án tích hợp đầy đủ **6 Nhóm Domain Dịch Vụ Thực Chiến**:
1. **Vé máy bay (Airline):** Scatter-Gather EIP khảo giá song song Vietjet & Vietnam Airlines (Sabre GDS), so khớp vé rẻ nhất và cấp mã PNR.
2. **Viễn thông / Topup (Telco):** Chống nạp thẻ trùng lặp (Idempotent Consumer), phân luồng Viettel/Mobi/Vina, Retry tự động và Fallback sang cổng Napas.
3. **SIM & eSIM (Sim):** Cấp phát mã QR kích hoạt eSIM Wintel và đấu nối SIM Data 4G Viettel.
4. **Bảo hiểm (Insurance):** Tính phí và phát hành hợp đồng bảo hiểm trực tuyến Bảo Việt, PVI.
5. **Vé vui chơi & Dịch vụ (Attraction / Bill):** Xuất vé điện tử QR Code VinWonders, Sun World và thanh toán hóa đơn tiện ích (EVN).
6. **Đối soát & Tác vụ Lô (Reconciliation):** Phân tích và bóc tách định dạng file đối soát đa dạng: CSV (Viettel), XML (Sabre GDS), JSON (VinWonders), Fixed-Length TXT (Napas).

---

## 2. Cấu Trúc Mã Nguồn Chuẩn Doanh Nghiệp (Domain-Driven Architecture)

Dự án phân định ranh giới kiến trúc rõ ràng giữa **Lớp Lõi của Hub (`hub/`)** và **Lớp Kết Nối Đối Tác Phân Theo Từng Domain Riêng Biệt (`partner/<domain>/`)**:

```
c:\WorkSpace\ZenoAI\APACHE_CAMEL\demo_apache_camel
│
├── src/main/java/com/demo_apache_camel/
│   ├── DemoApacheCamelApplication.java             # Main khởi chạy Spring Boot
│   │
│   ├── hub/                                        # [PHÂN HỆ LÕI SERVICE HUB NỘI BỘ]
│   │   ├── controller/
│   │   │   └── OrderController.java                # REST API Ingress tiếp nhận, tra cứu đơn hàng & Health check
│   │   ├── security/
│   │   │   └── InboundSecurityFilter.java          # [TRẠM GÁC CỔNG 1] Bắt & xác thực Token, Cookie, Trace ID
│   │   ├── config/                                 # [CẤU HÌNH TẬP TRUNG] Cấu hình hệ thống & Beans
│   │   │   ├── CamelGlobalConfig.java              # Bật Stream Caching, Log Masking, ThreadPool Profile
│   │   │   ├── HubProperties.java                  # Quản trị thông số môi trường từ YAML
│   │   │   ├── IdempotencyConfig.java              # Cung cấp Bean IdempotentRepository chống trùng lặp
│   │   │   └── SecurityHeaderFilterStrategy.java   # Lọc bỏ Authorization, Cookie chống rò rỉ Outbound
│   │   ├── model/
│   │   │   ├── OrderRequest.java                   # Canonical Data Model (CDM) chuẩn đầu vào
│   │   │   └── OrderResponse.java                  # DTO kết quả chuẩn phản hồi Client
│   │   ├── exception/                              # Quản lý ngoại lệ tập trung chuẩn REST
│   │   │   ├── HubException.java                   # Base Exception mang mã lỗi hệ thống
│   │   │   ├── PartnerException.java               # Ngoại lệ chuẩn hóa khi đối tác ngoài lỗi
│   │   │   └── GlobalExceptionHandler.java         # @RestControllerAdvice chuẩn hóa JSON phản hồi
│   │   ├── routes/
│   │   │   ├── MainOrderRoute.java                 # Bộ não điều phối trung tâm (Router + WireTap)
│   │   │   ├── AuditRoute.java                     # Kênh ghi log kiểm toán bất đồng bộ qua SEDA
│   │   │   └── DeadLetterRoute.java                # Tuyến cứu nạn Dead Letter Channel (DLC)
│   │   └── aggregator/
│   │       └── LowestPriceAggregator.java          # Chiến lược chọn lọc vé ưu đãi nhất của Hub
│   │
│   └── partner/                                    # [PHÂN HỆ TÍCH HỢP ĐỐI TÁC NGOÀI]
│       ├── common/
│       │   └── PartnerTokenManager.java            # Quản lý vòng đời OAuth2 Token đối tác (Cache & Refresh)
│       │
│       ├── airline/                                # [DOMAIN 1: HÀNG KHÔNG]
│       │   ├── AirlinePartnerRoute.java            # Điều phối Scatter-Gather tìm vé đa hãng & Booking
│       │   ├── FlightQuote.java                    # Model báo giá chuyến bay nội bộ
│       │   └── adapter/
│       │       ├── VietjetAdapterRoute.java        # Adapter Vietjet Air
│       │       └── VietnamAirlinesAdapterRoute.java# Adapter Vietnam Airlines (Sabre GDS)
│       │
│       ├── telco/                                  # [DOMAIN 2: VIỄN THÔNG / TOPUP]
│       │   ├── TopupPartnerRoute.java              # Điều phối Idempotency & Retry/Fallback
│       │   └── adapter/
│       │       ├── ViettelTelcoRoute.java          # Adapter nạp thẻ Viettel (Chiết khấu 5%)
│       │       ├── MobifoneTelcoRoute.java         # Adapter nạp thẻ Mobifone (Chiết khấu 3%)
│       │       ├── VinaphoneTelcoRoute.java        # Adapter nạp thẻ Vinaphone (Chiết khấu 4% + Retry)
│       │       └── NapasFallbackRoute.java         # Adapter chuyển tuyến dự phòng Napas
│       │
│       ├── sim/                                    # [DOMAIN 3: SIM & ESIM]
│       │   ├── SimPartnerRoute.java                # Điều phối dịch vụ SIM
│       │   └── adapter/
│       │       ├── WintelEsimAdapterRoute.java     # Cấp profile eSIM QR Code Wintel
│       │       └── ViettelSimAdapterRoute.java     # Đấu nối SIM Data 4G Viettel
│       │
│       ├── insurance/                              # [DOMAIN 4: BẢO HIỂM]
│       │   ├── InsurancePartnerRoute.java          # Điều phối dịch vụ bảo hiểm trực tuyến
│       │   └── adapter/
│       │       ├── BaoVietInsuranceRoute.java      # Adapter Bảo hiểm du lịch Bảo Việt
│       │       └── PviInsuranceRoute.java          # Adapter Bảo hiểm tai nạn PVI
│       │
│       ├── attraction/                             # [DOMAIN 5: VÉ VUI CHƠI & DỊCH VỤ]
│       │   ├── AttractionPartnerRoute.java         # Điều phối vé vui chơi & thanh toán dịch vụ
│       │   └── adapter/
│       │       ├── VinwondersAdapterRoute.java     # Adapter xuất vé QR VinWonders
│       │       └── SunworldAdapterRoute.java       # Adapter xuất vé QR Sun World
│       │
│       └── reconciliation/                         # [DOMAIN 6: ĐỐI SOÁT & TÁC VỤ LÔ]
│           ├── PartnerFileInboundRoute.java        # Quét và điều phối file đối soát Inbound
│           └── parsers/
│               ├── ViettelCsvParserRoute.java      # Parser CSV phân cách '|' Viettel
│               ├── SabreXmlParserRoute.java        # Parser XML đặt vé Sabre GDS
│               ├── VinwondersJsonParserRoute.java  # Parser JSON mảng vé VinWonders
│               └── NapasFixedLengthParserRoute.java# Parser TXT Fixed-Length Napas
│
├── src/test/java/com/demo_apache_camel/
│   ├── CamelRouteTest.java                         # 20 Unit Tests kiểm thử toàn diện mọi domain
│   └── DemoApacheCamelApplicationTests.java        # Test kiểm tra Spring Boot Application Context
│
├── data/samples/                                   # File dữ liệu mẫu đối soát (CSV, XML, JSON, TXT)
└── pom.xml                                         # Cấu hình Maven & dependencies
```

---

## 3. Hướng Dẫn Kiểm Thử Nhanh (Quick Start)

### Chạy Toàn Bộ Test Suite Tự Động (21/21 Tests Pass)
```powershell
.\mvnw.cmd test
```

### Gọi API Tạo Đơn Hàng Mẫu (cURL)

#### 1. Mua vé vui chơi VinWonders:
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: PARTNER_MOBILE" \
  -H "Authorization: Bearer my-secure-token" \
  -d '{
    "orderId": "ORD-LIVE-VW-01",
    "customerName": "Nguyen Van A",
    "serviceType": "ATTRACTION",
    "provider": "VINWONDERS",
    "amount": 850000.0
  }'
```

#### 2. Kích hoạt eSIM Wintel:
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: PARTNER_MOBILE" \
  -H "Authorization: Bearer my-secure-token" \
  -d '{
    "orderId": "ORD-LIVE-SIM-01",
    "customerName": "Tran Thi B",
    "serviceType": "SIM",
    "provider": "WINTEL",
    "amount": 120000.0
  }'
```

#### 3. Mua bảo hiểm du lịch Bảo Việt:
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: PARTNER_MOBILE" \
  -H "Authorization: Bearer my-secure-token" \
  -d '{
    "orderId": "ORD-LIVE-INS-01",
    "customerName": "Dang Thuy Trang",
    "serviceType": "INSURANCE",
    "provider": "BAOVIET",
    "amount": 250000.0
  }'
```
