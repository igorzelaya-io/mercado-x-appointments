# MercadoX Appointments Service

`mercado-x-appointments` is the tenant-aware scheduling bounded context for MercadoX. It
will own Google Calendar connections, provider/calendar mappings, availability policies,
slot calculation, and conflict-safe bookings. `mercado-x-ai` will call its internal HTTP
API from Claude tools; it will not receive or store Google credentials.

This repository is a contract-first Spring Boot service under progressive implementation.
It owns appointment business logic, orchestration, controllers, and infrastructure adapters.
Shared appointment entities and DTOs live in `mercado-x-library-entity`; repositories and the
centralized Flyway chain live in `mercado-x-library-jpa`. The service now contains the
application and security bootstrap plus the first Google Calendar OAuth endpoint.

The Google Calendar tenant-onboarding contract is defined in
[`openapi/google-calendar-onboarding-v1.yaml`](openapi/google-calendar-onboarding-v1.yaml).
It covers connection authorization, status and disconnect operations, visible-calendar
discovery, and provider/calendar assignment. The authorization-start operation is
implemented; callback and token exchange remain the next endpoint slice.

Implementation will proceed milestone by milestone from
[TODO-APPOINTMENTS.md](TODO-APPOINTMENTS.md). Architecture and contract decisions in
Milestone 0 must be completed before source code is added.

The dependency baseline in `pom.xml` is based on `mercado-x-email` and the shared MercadoX
libraries so Kafka/Avro, HTTP, security, persistence, Redis, Flyway, OAuth client support,
OpenAPI, Actuator, and testing dependencies are available when their corresponding tasks are
approved.
