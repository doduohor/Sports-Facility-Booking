# Task 3 report

## Result

Migrated Measurement, Monitoring and Incident service-result error branches in `Routing.kt` to `ApiErrorMapper` and `respondApiError`. Successful measurement and incident DTO responses, HTTP statuses, and existing error status/code/name/text tuples are preserved. Added exact JSON contract assertions for measurement invalid input, not-found, invalid value, conflict, and incident invalid input, not-found, and conflict cases.

`StatusPages.kt`, service/domain result types, and dependencies were not changed.

## TDD and verification

- Contract assertions were changed before production routing code.
- `git diff --check` passed.
- Focused command requested: `./gradlew test --tests com.doduohor.ServerTest --no-daemon --console=plain`.
  - Could not start: `./gradlew` has a CRLF shebang and failed with `bad interpreter: /bin/sh^M`.
  - A temporary LF-normalized wrapper first failed because `/home/doduohor/.gradle` is read-only.
  - With `GRADLE_USER_HOME` under `/tmp`, Gradle 9.5.1 could not resolve/download dependencies: `UnknownHostException: services.gradle.org`.
  - With the cached Gradle binary, configuration failed because plugin `org.gradle.toolchains.foojay-resolver-convention:1.0.0` could not be resolved from Maven/Gradle repositories.
- Full command requested: `./gradlew test --no-daemon --console=plain`.
  - Same CRLF shebang prevents direct wrapper execution.
  - Cached Gradle 9.5.1 invocation reached project configuration, then failed to resolve `org.gradle.toolchains.foojay-resolver-convention:1.0.0` because repository access is unavailable.

No test cases executed; the failures occurred before test compilation/execution.

## Fix follow-up

- Restored the original Routing texts in `ApiErrorMapper` for incident list not-found results:
  - `FindIncidentsByFacilityIdResult.NotFindFacilityId` → `An incorrect Facility ID has been specified`.
  - `FindIncidentsByEquipmentIdResult.NotFindEquipmentId` → `An incorrect Equipment ID has been specified`.
- Updated mapper unit contract assertions for both exact tuples.
- Added a minimal `respondMonitoringResult` seam and route-level JSON assertions for `notSupportedEquipmentType`, `measurementRangeNotConfigured`, `equipmentContextLost`, `incidentCreateError`, and `outboxPersistenceError`. The seam does not alter service/domain result types and is used by the corresponding production Monitoring branches.
- `git diff --check` passed.
- Focused offline Gradle command:
  `gradle test --offline --tests com.doduohor.api.mapper.ApiErrorMapperTest --tests 'com.doduohor.ServerTest.measurement monitoring internal errors keep exact API contracts' --no-daemon --console=plain`.
  - Production `compileKotlin` completed successfully with `-Dkotlin.compiler.execution.strategy=in-process`.
  - Test compilation failed before execution: Kotlin daemon cannot write under read-only `/home/doduohor/.local/share/kotlin/daemon`; fallback compilation reported existing type-inference errors in `ApiErrorMapperTest.kt` at lines 66, 71, 83, 88, 105, 110, 133, 138, 143, and 155.

## WIP follow-up (stopped by user)

- Added explicit generic types to all overloaded-mapper assertion collections in `ApiErrorMapperTest.kt`.
- Began replacing the artificial monitoring test route with a real `POST /api/measurements` test using an injectable production route callback; implementation and verification were interrupted before completion.
- No further checks were run after interruption.

## Fix-loop completion

- Replaced the ineffective `assertEquals(ErrorResponse, ApiError?)` calls with typed `assertMapped` assertions that verify both `HttpStatusCode` and the complete `ErrorResponse`; all assertion collections retain explicit `List<Pair<ResultType, ErrorResponse>>` types.
- The five Monitoring internal-error checks now send authenticated `POST /api/measurements` requests through the production `measurementRoutes`. A test-only callback supplies the requested `MonitoringServiceResult`; mapping and response serialization remain production code.
- `compileTestKotlin` passed offline with `-Dkotlin.compiler.execution.strategy=in-process`. The only output warning is the pre-existing unchecked cast in `RabbitMqConnectionTest.kt:49`.
- Focused tests passed offline:
  `test --offline --tests com.doduohor.api.mapper.ApiErrorMapperTest --tests 'com.doduohor.ServerTest.measurement monitoring internal errors keep exact API contracts' -Dkotlin.compiler.execution.strategy=in-process --no-daemon --console=plain`.
  Result: `BUILD SUCCESSFUL`; 5 Gradle tasks, 1 executed and 4 up-to-date.
- The Kotlin daemon still attempts to write to read-only `/home/doduohor/.local/share/kotlin/daemon`; the in-process fallback completed successfully.
