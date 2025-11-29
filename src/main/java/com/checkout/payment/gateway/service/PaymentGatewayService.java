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

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

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

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID deduplicationId = UUID.randomUUID();

    // Perform the initial validations and return REJECTED if anything fails
    try {
      paymentValidator.validate(paymentRequest);
    } catch (ValidationException e) {
      PostPaymentResponse rejectedResponse = toPaymentResponse(deduplicationId, PaymentStatus.REJECTED, paymentRequest);
      paymentsRepository.add(rejectedResponse);
      return rejectedResponse;
    }

    // Go to the acquirer bank and get a response
    GetAcquiringBankResponse bankResponse = bankService.submitBankRequest(
        new GetAcquiringBankRequest(
            paymentRequest.getCardNumber(),
            paymentRequest.getExpiryDate(),
            paymentRequest.getCurrency(),
            paymentRequest.getAmount(),
            paymentRequest.getCvv()
        )
    );

    PostPaymentResponse paymentResponse = new PostPaymentResponse(
        deduplicationId,
        toPaymentStatus(bankResponse),
        lastFourDigits(paymentRequest.getCardNumber()),
        paymentRequest.getExpiryMonth(),
        paymentRequest.getExpiryYear(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount()
    );
    paymentsRepository.add(paymentResponse);
    return paymentResponse;
  }

  private PostPaymentResponse toPaymentResponse(
      UUID deduplicationId,
      PaymentStatus status,
      PostPaymentRequest request
  ) {
    return new PostPaymentResponse(
        deduplicationId,
        status,
        lastFourDigits(request.getCardNumber()),
        request.getExpiryMonth(),
        request.getExpiryYear(),
        request.getCurrency(),
        request.getAmount()
    );
  }

  private PaymentStatus toPaymentStatus(GetAcquiringBankResponse bankResponse) {
    // technical failure
    if (bankResponse == null) {
      return PaymentStatus.REJECTED;
    }

    // odd ending
    if (bankResponse.isAuthorized()) {
      return PaymentStatus.AUTHORIZED;
    }

    // even ending
    return PaymentStatus.DECLINED;
  }

  private int lastFourDigits(String cardNumber) {
    if (cardNumber == null) return 0;

    String truncated = cardNumber.length() <= 4
        ? cardNumber : cardNumber.substring(cardNumber.length() - 4);

    try {
      return Integer.parseInt(truncated);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
