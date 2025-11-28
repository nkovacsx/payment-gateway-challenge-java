package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;

  @Autowired
  ObjectMapper mapper;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }
  @Nested
  @DisplayName("Verifications")
  class Verification {

    @Test
    @DisplayName("should fail when card number is too short (<14 chars)")
    void shouldFailWhenPaymentWithShortCardNumber() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCardNumber("123");
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when card number is too long (>19 chars)")
    void shouldFailWhenPaymentWithLongCardNumber() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCardNumber("4111111111111111111111111111111111");
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when card number contains invalid character (non-numeric)")
    void shouldFailWhenPaymentWithInvalidCharacterInCardNumber() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCardNumber("4111111111abc111");
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when card number is missing")
    void shouldFailWhenCardNumberIsMissing() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCardNumber(null);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when expiry month is zero")
    void shouldFailWhenExpiryMonthIsZero() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setExpiryMonth(0);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when expiry month is >12")
    void shouldFailWhenExpiryMonthIsGreaterThan12() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setExpiryMonth(13);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when expiry month is <0")
    void shouldFailWhenExpiryMonthIsBelowZero() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setExpiryMonth(-1);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when expiry is in the past")
    void shouldFailWhenExpiryIsInThePast() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setExpiryYear(1984);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when currency is not valid")
    void shouldFailWhenCurrencyIsInvalid() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCurrency("NOT_REAL");
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when currency is not present")
    void shouldFailWhenCurrencyIsNotPresent() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCurrency(null);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when currency is not ISO Standard")
    void shouldFailWhenCurrencyIsNotISOStandard() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCurrency("USDT");
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when amount is negative")
    void shouldFailWhenAmountIsNegative() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setAmount(-10000);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when amount is zero")
    void shouldFailWhenAmountIsZero() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setAmount(0);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when CVV is <3 characters")
    void shouldFailWhenCVVIsShort() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCvv(1);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }

    @Test
    @DisplayName("should fail when CVV is >4 characters")
    void shouldFailWhenCVVIsLong() throws Exception {
      PostPaymentRequest req = validPayment();
      req.setCvv(11111);
      assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
    }
  }

  private void assertPaymentResponseStatus(PostPaymentRequest req, PaymentStatus status) throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(status.getName()));
  }

  private String paymentJson(PostPaymentRequest req) throws Exception {
    return mapper.writeValueAsString(req);
  }

  private static PostPaymentRequest validPayment() {
    return new PostPaymentRequest(
        "4111111111111111",
        12,
        2030,
        "USD",
        100,
        123
    );
  }
}
