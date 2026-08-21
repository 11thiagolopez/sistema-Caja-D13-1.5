package com.thiago.escenasFX.exception;

public class EmailEnvioException extends RuntimeException {
    public EmailEnvioException(String message) {
        super(message);
    }

    public EmailEnvioException(String message, Throwable cause) {
        super(message, cause);
    }
}
