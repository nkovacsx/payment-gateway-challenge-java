package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.ValidationException;
import com.checkout.payment.gateway.model.GetAcquiringBankRequest;
import com.checkout.payment.gateway.model.GetAcquiringBankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for processing payment transactions through the gateway.
 * Validates requests, communicates with acquiring banks, and persists payment records.
 */
@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private static final int LAST_FOUR_DIGITS_LENGTH = 4;

  private final PaymentsRepository paymentsRepository;
  private final PaymentValidator paymentValidator;
  private final BankService bankService;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      PaymentValidator paymentValidator,
      BankService bankService
  ) {
    this.paymentsRepository = paymentsRepository;
    this.paymentValidator = paymentValidator;
    this.bankService = bankService;
  }

  /**
   * Retrieves a payment by its unique identifier.
   *
   * @param id the payment UUID
   * @return the payment response
   * @throws EventProcessingException if payment not found
   */
  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  /**
   * Processes a payment request through validation and bank authorization.
   *
   * @param paymentRequest the payment details
   * @return the payment response with status
   */
  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID paymentId = UUID.randomUUID();

    if (!isValidPayment(paymentRequest)) {
      return createAndStoreRejectedPayment(paymentId, paymentRequest);
    }

    GetAcquiringBankResponse bankResponse = submitToBankingService(paymentRequest);
    PostPaymentResponse paymentResponse = createPaymentResponse(paymentId, paymentRequest, bankResponse);

    paymentsRepository.add(paymentResponse);
    return paymentResponse;
  }

  private boolean isValidPayment(PostPaymentRequest request) {
    try {
      paymentValidator.validate(request);
      return true;
    } catch (ValidationException e) {
      LOG.warn("Payment validation failed: {}", e.getMessage());
      return false;
    }
  }

  private PostPaymentResponse createAndStoreRejectedPayment(UUID paymentId, PostPaymentRequest request) {
    PostPaymentResponse rejectedResponse = buildPaymentResponse(
        paymentId,
        PaymentStatus.REJECTED,
        request
    );
    paymentsRepository.add(rejectedResponse);
    return rejectedResponse;
  }

  private GetAcquiringBankResponse submitToBankingService(PostPaymentRequest request) {
    return bankService.submitBankRequest(
        new GetAcquiringBankRequest(
            request.getCardNumber(),
            request.getExpiryDate(),
            request.getCurrency(),
            request.getAmount(),
            request.getCvv()
        )
    );
  }

  private PostPaymentResponse createPaymentResponse(
      UUID paymentId,
      PostPaymentRequest request,
      GetAcquiringBankResponse bankResponse
  ) {
    PaymentStatus status = determinePaymentStatus(bankResponse);
    return buildPaymentResponse(paymentId, status, request);
  }

  private PaymentStatus determinePaymentStatus(GetAcquiringBankResponse bankResponse) {
    if (bankResponse == null) {
      return PaymentStatus.REJECTED;
    }
    return bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }

  private PostPaymentResponse buildPaymentResponse(
      UUID paymentId,
      PaymentStatus status,
      PostPaymentRequest request
  ) {
    return new PostPaymentResponse(
        paymentId,
        status,
        extractLastFourDigits(request.getCardNumber()),
        request.getExpiryMonth(),
        request.getExpiryYear(),
        request.getCurrency(),
        request.getAmount()
    );
  }

  private int extractLastFourDigits(String cardNumber) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      return 0;
    }

    String lastDigits = cardNumber.length() <= LAST_FOUR_DIGITS_LENGTH
        ? cardNumber : cardNumber.substring(cardNumber.length() - LAST_FOUR_DIGITS_LENGTH);

    try {
      return Integer.parseInt(lastDigits);
    } catch (NumberFormatException e) {
      LOG.warn("Invalid card number format: {}", cardNumber);
      return 0;
    }
  }
}
