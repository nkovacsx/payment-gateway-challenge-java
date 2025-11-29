package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.GetAcquiringBankRequest;
import com.checkout.payment.gateway.model.GetAcquiringBankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class BankService {

  // TODO make this configurable
  private static final String ACQUIRING_BANK_URL = "http://localhost:8080/payments";
  private static final Logger LOG = LoggerFactory.getLogger(BankService.class);

  private final RestTemplate restTemplate;

  public BankService(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public GetAcquiringBankResponse submitBankRequest(GetAcquiringBankRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<GetAcquiringBankRequest> entity = new HttpEntity<>(request, headers);

    try {
      // 200 OK -> authorized or unauthorized (based on card number)
      ResponseEntity<GetAcquiringBankResponse> response =
          restTemplate.exchange(
              ACQUIRING_BANK_URL,
              HttpMethod.POST,
              entity,
              GetAcquiringBankResponse.class
          );

      return response.getBody();

    } catch (HttpClientErrorException.BadRequest ex) {
      // 400 -> missing required fields
      LOG.warn("Bad request from bank: {}", ex.getResponseBodyAsString());

    } catch (HttpServerErrorException.ServiceUnavailable ex) {
      // 503 -> card ends with 0
      LOG.warn("Bank unavailable: {}", ex.getResponseBodyAsString());

    } catch (RestClientException ex) {
      // Any other unexpected errors
      LOG.warn("Unexpected error calling bank: {}", ex.getMessage());
    }

    // If we encountered any error responses, we just return authorized: false
    return new GetAcquiringBankResponse(false, null);
  }
}
