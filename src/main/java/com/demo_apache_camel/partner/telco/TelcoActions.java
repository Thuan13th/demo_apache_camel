package com.demo_apache_camel.partner.telco;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TELCO ACTIONS BEAN]
 * Các action xử lý chiết khấu và giả lập lỗi kênh truyền viễn thông
 * được Workflow YAML (02-telco-topup-workflow.yaml) gọi tới theo chuẩn GitHub Actions.
 */
@Slf4j
@Component("telcoActions")
public class TelcoActions {

    private final AtomicInteger vinaFailCounter = new AtomicInteger(0);

    public OrderResponse viettel(OrderRequest req) {
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "SUCCESS",
            "TOPUP",
            req != null && req.getAmount() != null ? req.getAmount() * 0.95 : 0.0,
            "Nạp thẻ Viettel thành công (Chiết khấu 5%)",
            "VIETTEL_TELCO_DIRECT"
        );
    }

    public OrderResponse mobi(OrderRequest req) {
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "SUCCESS",
            "TOPUP",
            req != null && req.getAmount() != null ? req.getAmount() * 0.955 : 0.0,
            "Nạp thẻ Mobifone thành công (Chiết khấu 4.5%)",
            "MOBIFONE_TELCO_DIRECT"
        );
    }

    public OrderResponse vina(OrderRequest req, @Header("X-Simulate-Error") String simulateError) {
        boolean isError = "true".equalsIgnoreCase(simulateError)
                || (req != null && req.getAmount() != null && req.getAmount() == 999999.0);

        if (isError) {
            int count = vinaFailCounter.incrementAndGet();
            log.warn("[ACTION-VINA] Kênh truyền Vinaphone gián đoạn (Lần thử {})! Đang kích hoạt Camel Retry...", count);
            throw new IllegalStateException("Kênh truyền Vinaphone 503 Service Unavailable");
        }

        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "SUCCESS",
            "TOPUP",
            req != null && req.getAmount() != null ? req.getAmount() * 0.96 : 0.0,
            "Nạp thẻ Vinaphone thành công (Chiết khấu 4%)",
            "VINAPHONE_TELCO_DIRECT"
        );
    }

    public OrderResponse napasFallback(OrderRequest req) {
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "FALLBACK_SUCCESS",
            "TOPUP",
            req != null ? req.getAmount() : 0.0,
            "Nhà mạng chính gián đoạn. Đã tự động chuyển tuyến qua cổng dự phòng Napas.",
            "NAPAS_FALLBACK_GATEWAY"
        );
    }

    public OrderResponse duplicateRejected(OrderRequest req) {
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "REJECTED",
            "TOPUP",
            0.0,
            "Giao dịch nạp thẻ đã được xử lý trước đó (Chặn trùng lặp Idempotency)",
            "HUB_IDEMPOTENCY_GUARD"
        );
    }
}
