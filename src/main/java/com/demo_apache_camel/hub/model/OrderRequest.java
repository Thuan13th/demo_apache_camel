package com.demo_apache_camel.hub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * [HUB CANONICAL DATA MODEL - CHUẨN DOANH NGHIỆP]
 * Mô hình dữ liệu chuẩn nội bộ của Hub (sử dụng Lombok để tinh giản mã nguồn).
 * Toàn bộ thông tin từ Client (Postman/Mobile App) hoặc từ File đối tác đều được chuẩn hóa vào DTO này.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest implements Serializable {
    private String orderId;
    private String customerName;
    private String phoneNumber; // Số điện thoại nhận dịch vụ (Topup / Nhận mã vé)
    private String serviceType; // TOPUP, FLIGHT, FLIGHT_BOOKING, BILL
    private String provider;    // VIETTEL, VINA, MOBI, VIETJET, VN, VINWONDERS
    private Double amount;
    private String destination; // Nơi đến cho vé máy bay (SGN, HAN, DAD)
    private Map<String, Object> extraData; // Dữ liệu mở rộng linh hoạt tùy nghiệp vụ

    // Constructor tiện ích 6 tham số đảm bảo tương thích 100% với các hàm gọi sẵn có
    public OrderRequest(String orderId, String customerName, String serviceType, String provider, Double amount, String destination) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.serviceType = serviceType;
        this.provider = provider;
        this.amount = amount;
        this.destination = destination;
    }
}
