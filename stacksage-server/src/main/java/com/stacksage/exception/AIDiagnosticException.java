package com.stacksage.exception;

public class AIDiagnosticException extends RuntimeException {

    public AIDiagnosticException(String message) {
        super(message);
    }

    public AIDiagnosticException(String message, Throwable cause) {
        super(message, cause);
    }
}
