package com.demo_apache_camel.hub.exception;

import lombok.Getter;

@Getter
public class PartnerException extends HubException {

    private final String partnerCode;
    private final int httpStatusCode;

    public PartnerException(String partnerCode, int httpStatusCode, String message) {
        super("PARTNER_ERROR", "FAILED", message);
        this.partnerCode = partnerCode;
        this.httpStatusCode = httpStatusCode;
    }
}
