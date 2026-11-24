package com.learning.auction.common.exception;

/** The caller is authenticated but is not allowed to touch this particular resource. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
