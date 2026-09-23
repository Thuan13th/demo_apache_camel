package com.demo_apache_camel.hub.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * [HUB CANONICAL DATA MODEL]
 * Kết quả xử lý chuẩn của Hub trả về cho Client (sử dụng Lombok để tinh giản mã nguồn).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse implements Serializable {
    private String orderId;
    private String status;         // SUCCESS, FAILED, FALLBACK, REJECTED
    private String serviceType;
    private Double finalAmount;
    private String message;
    private String processedBy;    // Đối tác hoặc Route thực thi

    @Builder.Default
    private String timestamp = LocalDateTime.now().toString();

    // Constructor tiện ích 6 tham số đảm bảo tương thích 100% với các hàm gọi sẵn có
    public OrderResponse(String orderId, String status, String serviceType, Double finalAmount, String message, String processedBy) {
        this.orderId = orderId;
        this.status = status;
        this.serviceType = serviceType;
        this.finalAmount = finalAmount;
        this.message = message;
        this.processedBy = processedBy;
        this.timestamp = LocalDateTime.now().toString();
    }
}
