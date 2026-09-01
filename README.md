# MercadoX Appointments Service

`mercado-x-appointments` is the tenant-aware scheduling bounded context for MercadoX. It
will own Google Calendar connections, provider/calendar mappings, availability policies,
slot calculation, and conflict-safe bookings. `mercado-x-ai` will call its internal HTTP
API from Claude tools; it will not receive or store Google credentials.

This repository is currently a planning-only Spring Boot/Maven scaffold. It contains the
project coordinates, dependency set, Maven Wrapper, CI metadata, and the ordered delivery
backlog. There are intentionally no application classes, runtime configuration, migrations,
or tests yet.

Implementation will proceed milestone by milestone from
[TODO-APPOINTMENTS.md](TODO-APPOINTMENTS.md). Architecture and contract decisions in
Milestone 0 must be completed before source code is added.

The dependency baseline in `pom.xml` is based on `mercado-x-email` and the shared MercadoX
libraries so Kafka/Avro, HTTP, security, persistence, Redis, Flyway, OAuth client support,
OpenAPI, Actuator, and testing dependencies are available when their corresponding tasks are
approved.
