package com.thiago.escenasFX.exception;

public class AfipIntegracionException extends RuntimeException {
    public AfipIntegracionException(String message) {
        super(message);
    }

    public AfipIntegracionException(String message, Throwable cause) {
        super(message, cause);
    }
}
