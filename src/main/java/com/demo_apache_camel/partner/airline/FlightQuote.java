package com.demo_apache_camel.partner.airline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * [PARTNER MODEL - AIRLINE]
 * Định dạng báo giá nội bộ giữa các đối tác hàng không (sử dụng Lombok).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightQuote implements Serializable {
    private String airline;
    private String flightNumber;
    private Double price;
    private String departure;
    private String destination;
}
