package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.configuration.ApplicationConfiguration;
import com.checkout.payment.gateway.exception.ValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.checkout.payment.gateway.util.PaymentGatewayTestUtil.createValidPaymentRequest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaymentValidatorTest {
  private PaymentValidator validator;

  @BeforeEach
  void setUp() {
    ApplicationConfiguration config = new ApplicationConfiguration();
    config.setSupportedCurrencies(List.of("USD", "GBP", "EUR"));
    validator = new PaymentValidator(config);
  }

  @Nested
  @DisplayName("Card Number Validation")
  class CardNumberValidation {

    @Test
    @DisplayName("should accept valid 16-digit card number")
    void shouldAcceptValid16DigitCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept minimum valid card length (14 digits)")
    void shouldAcceptMinimumCardLength() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("41111111111111");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept maximum valid card length (19 digits)")
    void shouldAcceptMaximumCardLength() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("4111111111111111111");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject null card number")
    void shouldRejectNullCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber(null);
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }

    @Test
    @DisplayName("should reject card number shorter than 14 digits")
    void shouldRejectTooShortCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("4111111111111");
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }

    @Test
    @DisplayName("should reject card number longer than 19 digits")
    void shouldRejectTooLongCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("41111111111111111111");
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }

    @Test
    @DisplayName("should reject card number with non-numeric characters")
    void shouldRejectNonNumericCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("4111-1111-1111-1111");
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }

    @Test
    @DisplayName("should reject card number with letters")
    void shouldRejectCardNumberWithLetters() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("411111111111111a");
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }

    @Test
    @DisplayName("should reject empty card number")
    void shouldRejectEmptyCardNumber() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCardNumber("");
      assertValidationErrorWithMessage(request, "Invalid Card Number");
    }
  }

  @Nested
  @DisplayName("Expiry Month Validation")
  class ExpiryMonthValidation {
    @Test
    @DisplayName("should accept valid expiry month 1")
    void shouldAcceptExpiryMonth1() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(1);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept valid expiry month 12")
    void shouldAcceptExpiryMonth12() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(12);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject expiry month 0")
    void shouldRejectExpiryMonth0() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(0);
      assertValidationErrorWithMessage(request, "Invalid expiry month");
    }

    @Test
    @DisplayName("should reject expiry month 13")
    void shouldRejectExpiryMonth13() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(13);
      assertValidationErrorWithMessage(request, "Invalid expiry month");
    }

    @Test
    @DisplayName("should reject negative expiry month")
    void shouldRejectNegativeExpiryMonth() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(-1);
      assertValidationErrorWithMessage(request, "Invalid expiry month");
    }
  }

  @Nested
  @DisplayName("Expiry Year Validation")
  class ExpiryYearValidation {

    @Test
    @DisplayName("should accept current year")
    void shouldAcceptCurrentYear() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryYear(LocalDate.now().getYear());
      request.setExpiryMonth(LocalDate.now().getMonthValue());
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept future year")
    void shouldAcceptFutureYear() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryYear(LocalDate.now().getYear() + 5);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject negative expiry year")
    void shouldRejectNegativeExpiryYear() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryYear(-1);
      assertValidationErrorWithMessage(request, "Invalid expiry year");
    }
  }

  @Nested
  @DisplayName("Expiry Date Validation")
  class ExpiryDateValidation {

    @Test
    @DisplayName("should accept card expiring at end of current month")
    void shouldAcceptCurrentMonthExpiry() {
      LocalDate now = LocalDate.now();
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(now.getMonthValue());
      request.setExpiryYear(now.getYear());
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept card expiring next month")
    void shouldAcceptNextMonthExpiry() {
      LocalDate nextMonth = LocalDate.now().plusMonths(1);
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(nextMonth.getMonthValue());
      request.setExpiryYear(nextMonth.getYear());
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject card expired last month")
    void shouldRejectLastMonthExpiry() {
      LocalDate lastMonth = LocalDate.now().minusMonths(1);
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(lastMonth.getMonthValue());
      request.setExpiryYear(lastMonth.getYear());
      assertValidationErrorWithMessage(request, "Expiry is in the past");
    }

    @Test
    @DisplayName("should reject card expired last year")
    void shouldRejectLastYearExpiry() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setExpiryMonth(12);
      request.setExpiryYear(LocalDate.now().getYear() - 1);
      assertValidationErrorWithMessage(request, "Expiry is in the past");
    }
  }

  @Nested
  @DisplayName("Currency Validation")
  class CurrencyValidation {

    @Test
    @DisplayName("should accept USD currency")
    void shouldAcceptUSD() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("USD");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept EUR currency")
    void shouldAcceptEUR() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("EUR");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept GBP currency")
    void shouldAcceptGBP() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("GBP");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept lowercase currency codes")
    void shouldAcceptLowercaseCurrency() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("usd");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject null currency")
    void shouldRejectNullCurrency() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency(null);
      assertValidationErrorWithMessage(request, "Currency is invalid");
    }

    @Test
    @DisplayName("should reject unsupported currency")
    void shouldRejectUnsupportedCurrency() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("JPY");
      assertValidationErrorWithMessage(request, "Currency is invalid");
    }

    @Test
    @DisplayName("should reject currency with wrong length")
    void shouldRejectInvalidLengthCurrency() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("US");
      assertValidationErrorWithMessage(request, "Currency is invalid");
    }

    @Test
    @DisplayName("should reject empty currency")
    void shouldRejectEmptyCurrency() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCurrency("");
      assertValidationErrorWithMessage(request, "Currency is invalid");
    }
  }

  @Nested
  @DisplayName("Amount Validation")
  class AmountValidation {

    @Test
    @DisplayName("should accept positive amount")
    void shouldAcceptPositiveAmount() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setAmount(100);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept minimum valid amount (1)")
    void shouldAcceptMinimumAmount() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setAmount(1);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept large amount")
    void shouldAcceptLargeAmount() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setAmount(Integer.MAX_VALUE);
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject zero amount")
    void shouldRejectZeroAmount() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setAmount(0);
      assertValidationErrorWithMessage(request, "Amount is invalid");
    }

    @Test
    @DisplayName("should reject negative amount")
    void shouldRejectNegativeAmount() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setAmount(-100);
      assertValidationErrorWithMessage(request, "Amount is invalid");
    }
  }

  @Nested
  @DisplayName("CVV Validation")
  class CVVValidation {

    @Test
    @DisplayName("should accept 3-digit CVV")
    void shouldAccept3DigitCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("123");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should accept 4-digit CVV")
    void shouldAccept4DigitCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("1234");
      assertSuccessfulValidation(request);
    }

    @Test
    @DisplayName("should reject null CVV")
    void shouldRejectNullCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv(null);
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }

    @Test
    @DisplayName("should reject 2-digit CVV")
    void shouldReject2DigitCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("12");
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }

    @Test
    @DisplayName("should reject 5-digit CVV")
    void shouldReject5DigitCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("12345");
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }

    @Test
    @DisplayName("should reject CVV with letters")
    void shouldRejectCVVWithLetters() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("12a");
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }

    @Test
    @DisplayName("should reject CVV with special characters")
    void shouldRejectCVVWithSpecialCharacters() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("12-");
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }

    @Test
    @DisplayName("should reject empty CVV")
    void shouldRejectEmptyCVV() {
      PostPaymentRequest request = createValidPaymentRequest();
      request.setCvv("");
      assertValidationErrorWithMessage(request, "CVV is invalid");
    }
  }

  private void assertSuccessfulValidation(PostPaymentRequest paymentRequest) {
    assertDoesNotThrow(() -> validator.validate(paymentRequest));
  }

  private void assertValidationErrorWithMessage(PostPaymentRequest paymentRequest, String message) {
    ValidationException exception = assertThrows(ValidationException.class,
        () -> validator.validate(paymentRequest));
    if (message != null) {
      assertEquals(message, exception.getMessage());
    }
  }
}
