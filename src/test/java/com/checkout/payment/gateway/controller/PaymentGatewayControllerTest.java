package com.checkout.payment.gateway.controller;


import static com.checkout.payment.gateway.util.PaymentGatewayTestUtil.createValidPaymentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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

    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + payment.getId()))
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
    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  @DisplayName("should return masked card number (last 4 digits only) when retrieving payment by ID")
  void shouldReturnMaskedCardNumberOnGetById() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111111");

    String createResponse = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    PostPaymentResponse createdPayment = mapper.readValue(createResponse, PostPaymentResponse.class);

    mvc.perform(MockMvcRequestBuilders.get("/api/v1/payments/" + createdPayment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value(1111))
        .andExpect(jsonPath("$.cardNumber").doesNotExist());
  }

  @Test
  @DisplayName("should fail when card number is too short (<14 chars)")
  void shouldFailWhenPaymentWithShortCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("123");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when card number is too long (>19 chars)")
  void shouldFailWhenPaymentWithLongCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111111111111111111111111");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when card number contains invalid character (non-numeric)")
  void shouldFailWhenPaymentWithInvalidCharacterInCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111abc111");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when card number is missing")
  void shouldFailWhenCardNumberIsMissing() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber(null);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when expiry month is zero")
  void shouldFailWhenExpiryMonthIsZero() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setExpiryMonth(0);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when expiry month is >12")
  void shouldFailWhenExpiryMonthIsGreaterThan12() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setExpiryMonth(13);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when expiry month is <0")
  void shouldFailWhenExpiryMonthIsBelowZero() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setExpiryMonth(-1);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when expiry is in the past")
  void shouldFailWhenExpiryIsInThePast() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setExpiryYear(1984);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when currency is not valid")
  void shouldFailWhenCurrencyIsInvalid() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCurrency("NOT_REAL");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when currency is not present")
  void shouldFailWhenCurrencyIsNotPresent() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCurrency(null);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when currency is not ISO Standard")
  void shouldFailWhenCurrencyIsNotISOStandard() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCurrency("USDT");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when amount is negative")
  void shouldFailWhenAmountIsNegative() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setAmount(-10000);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when amount is zero")
  void shouldFailWhenAmountIsZero() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setAmount(0);
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when CVV is <3 characters")
  void shouldFailWhenCVVIsShort() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCvv("1");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should fail when CVV is >4 characters")
  void shouldFailWhenCVVIsLong() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCvv("11111");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should return Declined if card number ends in even")
  void shouldFailWhenPaymentWithEvenCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111118");
    assertPaymentResponseStatus(req, PaymentStatus.DECLINED);
  }

  @Test
  @DisplayName("should return Authorized if card number ends in odd")
  void shouldFailWhenPaymentWithOddCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111111");
    assertPaymentResponseStatus(req, PaymentStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("should return Rejected (503) if card number ends in 0")
  void shouldFailWhenPaymentWithZeroCardNumber() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111110");
    assertPaymentResponseStatus(req, PaymentStatus.REJECTED);
  }

  @Test
  @DisplayName("should return valid payment ID in response")
  void shouldReturnValidPaymentId() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  @DisplayName("should mask card number and return only last 4 digits")
  void shouldReturnOnlyLastFourDigits() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111111");
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardNumberLastFour").value(1111));
  }

  @Test
  @DisplayName("should return correct expiry month and year")
  void shouldReturnCorrectExpiryData() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expiryMonth").value(req.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(req.getExpiryYear()));
  }

  @Test
  @DisplayName("should reject request with invalid content type")
  void shouldRejectInvalidContentType() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.TEXT_PLAIN)
            .content("invalid"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("should reject malformed JSON")
  void shouldRejectMalformedJson() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{invalid json"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should handle minimum valid card length (14 digits)")
  void shouldHandleMinimumCardLength() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("41111111111111");
    assertPaymentResponseStatus(req, PaymentStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("should handle maximum valid card length (19 digits)")
  void shouldHandleMaximumCardLength() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setCardNumber("4111111111111111111");
    assertPaymentResponseStatus(req, PaymentStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("should handle amount with maximum value")
  void shouldHandleMaximumAmount() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setAmount(Integer.MAX_VALUE);
    assertPaymentResponseStatus(req, PaymentStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("should handle current month/year as valid expiry")
  void shouldHandleCurrentMonthYearExpiry() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();
    req.setExpiryMonth(java.time.LocalDate.now().getMonthValue());
    req.setExpiryYear(java.time.LocalDate.now().getYear());
    assertPaymentResponseStatus(req, PaymentStatus.AUTHORIZED);
  }

  @Test
  @DisplayName("should create unique payment IDs for identical requests")
  void shouldCreateUniquePaymentIds() throws Exception {
    PostPaymentRequest req = createValidPaymentRequest();

    String response1 = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String response2 = mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    PostPaymentResponse payment1 = mapper.readValue(response1, PostPaymentResponse.class);
    PostPaymentResponse payment2 = mapper.readValue(response2, PostPaymentResponse.class);

    assertThat(payment1.getId()).isNotEqualTo(payment2.getId());
  }

  private void assertPaymentResponseStatus(PostPaymentRequest req, PaymentStatus status)
      throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(status.getName()));
  }

  private String paymentJson(PostPaymentRequest req) throws Exception {
    return mapper.writeValueAsString(req);
  }
}
