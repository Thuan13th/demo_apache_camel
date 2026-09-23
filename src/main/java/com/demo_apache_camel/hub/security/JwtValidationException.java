package com.demo_apache_camel.hub.security;

/**
 * [JWT VALIDATION EXCEPTION]
 * Exception đặc thù cho các lỗi xác thực JWT, phân biệt rõ từng loại lỗi
 * để trả về thông báo chính xác cho client (không tiết lộ thông tin nhạy cảm).
 */
public class JwtValidationException extends RuntimeException {

    public enum Reason {
        /** Token không có hoặc sai định dạng Bearer */
        MISSING_OR_MALFORMED,
        /** Chữ ký số sai (token bị giả mạo hoặc ký bởi key khác) */
        INVALID_SIGNATURE,
        /** Token đã hết hạn (exp claim đã qua) */
        TOKEN_EXPIRED,
        /** Issuer (iss claim) không khớp với Hub */
        INVALID_ISSUER,
        /** Token chưa đến thời điểm có hiệu lực (nbf claim) */
        NOT_YET_VALID,
        /** Lỗi parse không xác định */
        PARSE_ERROR
    }

    private final Reason reason;

    public JwtValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public JwtValidationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * Trả về message an toàn để trả về client (không tiết lộ chi tiết nội bộ).
     */
    public String getSafeClientMessage() {
        return switch (reason) {
            case MISSING_OR_MALFORMED -> "Token xác thực không hợp lệ hoặc thiếu";
            case INVALID_SIGNATURE    -> "Chữ ký số của token không hợp lệ";
            case TOKEN_EXPIRED        -> "Token đã hết hạn, vui lòng đăng nhập lại";
            case INVALID_ISSUER       -> "Token không được phát hành bởi hệ thống Hub";
            case NOT_YET_VALID        -> "Token chưa có hiệu lực";
            case PARSE_ERROR          -> "Không thể xử lý token xác thực";
        };
    }
}
