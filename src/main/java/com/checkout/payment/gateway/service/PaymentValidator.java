package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.ValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Set;

/**
 * Service responsible for validating payment request data before processing.
 * Validates payment card details including card number format, expiry date,
 * currency, amount, and CVV to ensure they meet the required criteria.
 */
@Service
public class PaymentValidator {

  /**
   * Set of allowed currency codes for payment processing.
   * Currently, supports USD, EUR, and GBP.
   */
  // Could use java.util.Currency to fetch all of them, but we will just allow these three for now
  private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  /**
   * Validates a payment request against business rules.
   * <p>
   * Performs the following validations:
   * </p>
   * <ul>
   *   <li>Card number: must be 14-19 digits and numeric</li>
   *   <li>Expiry month: must be between 1 and 12</li>
   *   <li>Expiry year: must be non-negative</li>
   *   <li>Expiry date: must be in the future</li>
   *   <li>Currency: must be a 3-letter code from the allowed set</li>
   *   <li>Amount: must be positive</li>
   *   <li>CVV: must be 3-4 digits and numeric</li>
   * </ul>
   *
   * @param request the payment request to validate
   * @throws ValidationException if any validation rule fails
   */
  public void validate(PostPaymentRequest request) {

    // Card number: length + numeric
    if (request.getCardNumber() == null ||
        request.getCardNumber().length() < 14 ||
        request.getCardNumber().length() > 19 ||
        !request.getCardNumber().matches("\\d+")) {
      throw new ValidationException("Invalid Card Number");
    }

    // Expiry month
    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      throw new ValidationException("Invalid expiry month");
    }

    if (request.getExpiryYear() < 0) {
      throw new ValidationException("Invalid expiry year");
    }

    // Expiry date must be in the future
    LocalDate today = LocalDate.now();

    // Get the last valid date for the card
    // for example if it's valid until 2025-12 then we'll check if 2025-12-31 is in the future
    LocalDate expiry = LocalDate.of(request.getExpiryYear(), request.getExpiryMonth(), 1)
        .plusMonths(1)
        .minusDays(1);

    if (expiry.isBefore(today)) {
      throw new ValidationException("Expiry is in the past");
    }

    // Currency is present and valid format, plus it's in our allowed set
    if (request.getCurrency() == null ||
        request.getCurrency().length() != 3 ||
        !ALLOWED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
      throw new ValidationException("Currency is invalid");
    }

    // Amount must be positive
    if (request.getAmount() <= 0) {
      throw new ValidationException("Amount is invalid");
    }

    // CVV: numeric, 3–4 chars
    if (request.getCvv() == null || request.getCvv().length() < 3 || request.getCvv().length() > 4 ||
        !request.getCvv().matches("\\d+")) {
      throw new ValidationException("CVV is invalid");
    }
  }
}
