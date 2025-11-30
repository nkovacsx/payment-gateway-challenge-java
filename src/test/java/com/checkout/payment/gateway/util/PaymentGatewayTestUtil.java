package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.model.PostPaymentRequest;

public class PaymentGatewayTestUtil {
  public static PostPaymentRequest createValidPaymentRequest() {
    return new PostPaymentRequest(
        "4111111111111111",
        12,
        2030,
        "USD",
        100,
        "123"
    );
  }
}
