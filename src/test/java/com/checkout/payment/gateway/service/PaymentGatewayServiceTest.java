package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.ValidationException;
import com.checkout.payment.gateway.model.GetAcquiringBankRequest;
import com.checkout.payment.gateway.model.GetAcquiringBankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;
import java.util.UUID;

import static com.checkout.payment.gateway.util.PaymentGatewayTestUtil.createValidPaymentRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentGatewayServiceTest {
  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private PaymentValidator paymentValidator;

  @Mock
  private BankService bankService;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("should return payment when ID exists")
  void shouldReturnPaymentWhenIdExists() {
    UUID paymentId = UUID.randomUUID();
    PostPaymentResponse expectedResponse = createPaymentResponse(paymentId, PaymentStatus.AUTHORIZED);

    when(paymentsRepository.get(paymentId)).thenReturn(Optional.of(expectedResponse));

    PostPaymentResponse actual = paymentGatewayService.getPaymentById(paymentId);

    assertNotNull(actual);
    assertEquals(paymentId, actual.getId());
    assertEquals(PaymentStatus.AUTHORIZED, actual.getStatus());
  }

  @Test
  @DisplayName("should throw exception when payment ID not found")
  void shouldThrowExceptionWhenPaymentIdNotFound() {
    UUID paymentId = UUID.randomUUID();
    when(paymentsRepository.get(paymentId)).thenReturn(Optional.empty());

    assertThrows(EventProcessingException.class, () -> paymentGatewayService.getPaymentById(paymentId));
  }

  @Test
  @DisplayName("should return AUTHORIZED status when bank authorizes payment")
  void shouldReturnAuthorizedStatusWhenBankAuthorizesPayment() {
    PostPaymentRequest request = createValidPaymentRequest();
    GetAcquiringBankResponse bankResponse = createBankResponse(true);

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(1111, response.getCardNumberLastFour());
    verify(paymentsRepository).add(any(PostPaymentResponse.class));
  }

  @Test
  @DisplayName("should return DECLINED status when bank declines payment")
  void shouldReturnDeclinedStatusWhenBankDeclinesPayment() {
    PostPaymentRequest request = createValidPaymentRequest();
    GetAcquiringBankResponse bankResponse = createBankResponse(false);

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.DECLINED, response.getStatus());
    verify(paymentsRepository).add(any(PostPaymentResponse.class));
  }

  @Test
  @DisplayName("should return REJECTED status when validation fails")
  void shouldReturnRejectedStatusWhenValidationFails() {
    PostPaymentRequest request = createValidPaymentRequest();

    doThrow(new ValidationException("Invalid card")).when(paymentValidator).validate(any());

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.REJECTED, response.getStatus());
    verify(bankService, never()).submitBankRequest(any());
    verify(paymentsRepository).add(any(PostPaymentResponse.class));
  }

  @Test
  @DisplayName("should return REJECTED status when bank service returns null")
  void shouldReturnRejectedStatusWhenBankServiceReturnsNull() {
    PostPaymentRequest request = createValidPaymentRequest();

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(null);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(PaymentStatus.REJECTED, response.getStatus());
    verify(paymentsRepository).add(any(PostPaymentResponse.class));
  }

  @Test
  @DisplayName("should extract last 4 digits from card number correctly")
  void shouldExtractLastFourDigitsFromCardNumberCorrectly() {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setCardNumber("4111111111111111");
    GetAcquiringBankResponse bankResponse = createBankResponse(true);

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(1111, response.getCardNumberLastFour());
  }

  @Test
  @DisplayName("should handle short card number gracefully")
  void shouldHandleShortCardNumberGracefully() {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setCardNumber("123");
    GetAcquiringBankResponse bankResponse = createBankResponse(true);

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(bankResponse);

    PostPaymentResponse response = paymentGatewayService.processPayment(request);

    assertEquals(123, response.getCardNumberLastFour());
  }

  @Test
  @DisplayName("should pass correct bank request parameters")
  void shouldPassCorrectBankRequestParameters() {
    PostPaymentRequest request = createValidPaymentRequest();
    request.setCardNumber("4111111111111111");
    request.setExpiryMonth(12);
    request.setExpiryYear(2025);
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");

    GetAcquiringBankResponse bankResponse = createBankResponse(true);

    doNothing().when(paymentValidator).validate(any());
    when(bankService.submitBankRequest(any())).thenReturn(bankResponse);

    ArgumentCaptor<GetAcquiringBankRequest> captor =
        ArgumentCaptor.forClass(GetAcquiringBankRequest.class);

    paymentGatewayService.processPayment(request);

    verify(bankService).submitBankRequest(captor.capture());
    GetAcquiringBankRequest capturedRequest = captor.getValue();

    assertEquals("4111111111111111", capturedRequest.getCardNumber());
    assertEquals("12/2025", capturedRequest.getExpiryDate());
    assertEquals("USD", capturedRequest.getCurrency());
    assertEquals(100, capturedRequest.getAmount());
    assertEquals("123", capturedRequest.getCvv());
  }

  private GetAcquiringBankResponse createBankResponse(boolean authorized) {
    GetAcquiringBankResponse response = new GetAcquiringBankResponse();
    response.setAuthorized(authorized);
    response.setAuthorizationCode(authorized ? "AUTH123" : null);
    return response;
  }

  private PostPaymentResponse createPaymentResponse(UUID id, PaymentStatus status) {
    return new PostPaymentResponse(id, status, 1234, 12, 2025, "USD", 100);
  }
}
