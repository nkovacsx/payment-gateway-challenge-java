# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Design Decisions

- **Synchronous processing**: Payment requests are handled synchronously for simplicity.
  Bank failures (503, 400, etc.) result in `REJECTED` status, meaning merchants must retry manually.
- **No idempotency**: Duplicate requests create new payment records (new UUIDs).
- **No retry logic**: If the bank is unavailable, the payment is rejected immediately.
- **Trade-offs**: For production, we should consider:
  - Async processing with message queues (Kafka, RabbitMQ)
  - Idempotency keys to prevent duplicate charges
  - Timeout configuration and exponential backoff
- **Configurable currencies**: Supported currencies are defined in `application.properties`
  (`payment.gateway.supported-currencies`). Currently configured for USD, GBP, EUR.
  Change the config to support different currencies without code changes.
- **Payment status model**: Three states: `AUTHORIZED` (successful bank approval), `DECLINED` (rejected by bank),
  `REJECTED` (validation/technical failures on bank-side). Status is immutable once set.
- **Data masking**: Card numbers are masked in responses (only last 4 digits exposed) and logs for PCI-DSS compliance.
  Full card details are sent to bank but never persisted or returned to merchant.
- **No authentication**: No merchant authentication/authorization implemented.
  Production requires OAuth2/JWT with merchant-scoped access control.
- **Bank integration**: Direct REST calls to bank simulator. Assumes bank is reliable and fast.
    Production needs connection pooling, timeout handling, and monitoring.