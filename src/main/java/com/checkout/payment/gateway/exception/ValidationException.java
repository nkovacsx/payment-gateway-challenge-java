package com.checkout.payment.gateway.exception;

/**
 * Exception thrown when payment request validation fails.
 */
public class ValidationException extends RuntimeException {
  public ValidationException(String message) {
    super(message);
  }
}
