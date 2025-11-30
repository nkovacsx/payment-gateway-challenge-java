package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.GetAcquiringBankRequest;
import com.checkout.payment.gateway.model.GetAcquiringBankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class BankServiceTest {

  @Mock
  private RestTemplate restTemplate;

  @InjectMocks
  private BankService bankService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("should return authorized response when bank authorizes payment")
  void shouldReturnAuthorizedResponseWhenBankAuthorizesPayment() {
    GetAcquiringBankRequest request = new GetAcquiringBankRequest();
    GetAcquiringBankResponse expectedResponse = new GetAcquiringBankResponse();
    expectedResponse.setAuthorized(true);
    expectedResponse.setAuthorizationCode("AUTH123");

    ResponseEntity<GetAcquiringBankResponse> responseEntity =
        new ResponseEntity<>(expectedResponse, HttpStatus.OK);

    when(restTemplate.exchange(
        anyString(),
        any(),
        any(),
        eq(GetAcquiringBankResponse.class)
    )).thenReturn(responseEntity);

    GetAcquiringBankResponse actual = bankService.submitBankRequest(request);

    assertNotNull(actual);
    assertTrue(actual.isAuthorized());
    assertEquals("AUTH123", actual.getAuthorizationCode());
  }

  @Test
  @DisplayName("should return declined response when bank declines payment")
  void shouldReturnDeclinedResponseWhenBankDeclinesPayment() {
    GetAcquiringBankRequest request = new GetAcquiringBankRequest();
    GetAcquiringBankResponse expectedResponse = new GetAcquiringBankResponse();
    expectedResponse.setAuthorized(false);
    expectedResponse.setAuthorizationCode(null);

    ResponseEntity<GetAcquiringBankResponse> responseEntity =
        new ResponseEntity<>(expectedResponse, HttpStatus.OK);

    when(restTemplate.exchange(
        anyString(),
        any(),
        any(),
        eq(GetAcquiringBankResponse.class)
    )).thenReturn(responseEntity);

    GetAcquiringBankResponse actual = bankService.submitBankRequest(request);

    assertNotNull(actual);
    assertFalse(actual.isAuthorized());
    assertNull(actual.getAuthorizationCode());
  }

  @Test
  @DisplayName("should return null when acquiring bank returns 400 BadRequest")
  void shouldReturnNullWhenBankReturnsBadRequest() {
    GetAcquiringBankRequest request = new GetAcquiringBankRequest();

    when(restTemplate.exchange(anyString(), any(), any(), eq(GetAcquiringBankResponse.class)))
        .thenThrow(HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

    GetAcquiringBankResponse actual = bankService.submitBankRequest(request);

    assertNull(actual);
  }

  @Test
  @DisplayName("should throw exception when acquiring bank returns 503 Service Unavailable")
  void shouldReturnNullWhenBankReturns503() {
    GetAcquiringBankRequest request = new GetAcquiringBankRequest();

    when(restTemplate.exchange(anyString(), any(), any(), eq(GetAcquiringBankResponse.class)))
        .thenThrow(HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null));

    GetAcquiringBankResponse actual = bankService.submitBankRequest(request);

    assertNull(actual);
  }
}
