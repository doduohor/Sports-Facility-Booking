# Central API Error Mapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Централизовать преобразование результатов сервисов в HTTP-ошибки, сохранив существующий API-контракт для Facility, Booking, Equipment, Measurement и Incident.

**Architecture:** Чистый `ApiErrorMapper` в `api/mapper` преобразует sealed-result-типы в `ApiError(status, ErrorResponse)`. `Routing.kt` использует общий helper ответа и оставляет только разбор DTO, вызов сервиса и успешные response DTO; `StatusPages.kt` продолжает обрабатывать исключения Ktor.

**Tech Stack:** Kotlin 2.4.0, Ktor 3.5.0, kotlinx.serialization, JUnit Jupiter 6.1.0, Gradle Wrapper.

**Spec:** `docs/superpowers/specs/2026-09-03-central-api-error-mapping-design.md`

## Global Constraints

- Сохранить публичные HTTP status codes и поля `ErrorResponse` (`code`, `name`, `text`).
- Не менять service/domain sealed-result-типы.
- Не добавлять зависимости.
- Каждая задача выполняется отдельным свежим под-агентом; под-агент не создаёт других агентов.
- Перед production-кодом написать failing test и подтвердить ожидаемый RED-запуск.
- После каждой задачи запустить целевые тесты, полный `./gradlew test --no-daemon --console=plain`, выполнить отдельное task-review.

---

### Task 1: Error value and pure service-result mapper

**Files:**
- Create: `src/main/kotlin/api/mapper/ApiErrorMapper.kt`
- Create: `src/test/kotlin/api/mapper/ApiErrorMapperTest.kt`

**Interfaces:**
- Produces `data class ApiError(val status: HttpStatusCode, val response: ErrorResponse)` and `object ApiErrorMapper` functions for Facility, Booking, Equipment, Measurement, Incident and Monitoring result types.
- A successful result returns `null`; every error result returns the existing status/code/name/text tuple.

- [ ] **Step 1: Write failing mapper tests** covering every result branch, including nested `MonitoringServiceResult.MeasurementCreateError` and `OutboxPersistenceError`.
- [ ] **Step 2: Run `./gradlew test --tests 'com.doduohor.api.mapper.ApiErrorMapperTest' --no-daemon --console=plain` and confirm RED because the mapper does not exist.
- [ ] **Step 3: Implement the minimal immutable mapper and `ApiError` value.
- [ ] **Step 4: Run the focused test and full `./gradlew test --no-daemon --console=plain`; both must pass.
- [ ] **Step 5: Commit `feat: add centralized API error mapper`.

### Task 2: Migrate Facility, Booking and Equipment routes

**Files:**
- Modify: `src/main/kotlin/Routing.kt`
- Modify: `src/main/kotlin/api/mapper/ApiErrorMapper.kt` only if Task 1 exposed a missing branch.
- Create or modify: `src/test/kotlin/ServerTest.kt` for route-level contract assertions.

**Interfaces:**
- Consumes `ApiErrorMapper` from Task 1 and a shared `ApplicationCall.respondApiError(ApiError)` helper.
- Produces routes that use the mapper for Facility, Booking and Equipment service results while returning unchanged success DTOs.

- [ ] **Step 1:** Add route assertions that exercise representative 400, 404 and 409 errors in all three route groups and record exact `ErrorResponse` values.
- [ ] **Step 2:** Run the focused ServerTest selection and confirm the new structural/contract expectation fails before migration.
- [ ] **Step 3: Add the shared response helper and replace inline error construction in Facility, Booking and Equipment service-result branches.
- [ ] **Step 4: Run the focused API tests and full `./gradlew test --no-daemon --console=plain`.
- [ ] **Step 5: Commit `refactor: centralize facility booking equipment API errors`.

### Task 3: Migrate Measurement, Monitoring and Incident routes

**Files:**
- Modify: `src/main/kotlin/Routing.kt`
- Modify: `src/main/kotlin/api/mapper/ApiErrorMapper.kt` only if required for a discovered result branch.
- Create or modify: `src/test/kotlin/ServerTest.kt` for exact Measurement and Incident error contracts.

**Interfaces:**
- Consumes the mapper and helper from Tasks 1–2.
- Produces complete mapping for `MonitoringServiceResult`, `CreateMeasurementResult`, `IncidentServiceResult`, and collection lookup result types.

- [ ] **Step 1:** Add or strengthen API assertions for measurement invalid input/not-found/conflict/internal and incident invalid input/not-found/conflict cases.
- [ ] **Step 2:** Run the focused tests and confirm the expected failure or gap before migration.
- [ ] **Step 3: Replace all remaining inline service-result `ErrorResponse` creation in Measurement and Incident routes; preserve successful responses and StatusPages behavior.
- [ ] **Step 4: Run focused API tests and full `./gradlew test --no-daemon --console=plain`.
- [ ] **Step 5: Commit `refactor: centralize measurement and incident API errors`.

### Task 4: Contract hardening and final verification

**Files:**
- Modify: `src/test/kotlin/ServerTest.kt` only for missing contract cases.
- Modify: `src/test/kotlin/api/mapper/ApiErrorMapperTest.kt` only for missing branches.
- Modify: `src/main/kotlin/Routing.kt` only for defects found by tests or review.

**Interfaces:**
- Consumes the complete mapper and route migration from Tasks 1–3.
- Produces a test suite proving the five required categories and unchanged public contracts.

- [ ] **Step 1:** Compare every service result sealed branch with mapper coverage and every route branch with mapper usage.
- [ ] **Step 2:** Add failing tests for any uncovered branch, then run them RED.
- [ ] **Step 3: Make the smallest implementation correction and run the affected tests GREEN.
- [ ] **Step 4: Run `git diff --check`, focused mapper/API tests, and full `./gradlew test --no-daemon --console=plain`.
- [ ] **Step 5: Commit `test: harden centralized API error contract` if changes are needed.

### Task 5: Independent whole-branch code review

**Files:**
- Read-only review of all changes from the branch base to `HEAD`.

- [ ] **Step 1: Dispatch a fresh independent reviewer with the plan, spec, base SHA and head SHA.
- [ ] **Step 2: Fix every Critical/Important finding in a fresh implementation sub-agent and run a scoped re-review.
- [ ] **Step 3: Run final verification and record the review verdict.
