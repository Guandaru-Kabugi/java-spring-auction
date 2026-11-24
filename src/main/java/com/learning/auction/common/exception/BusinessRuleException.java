package com.learning.auction.common.exception;

import lombok.Getter;

/**
 * A request that is well-formed but breaks a domain rule (bid too low, auction not active, ...).
 * The {@code code} is a stable, machine-readable reason clients can switch on.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
