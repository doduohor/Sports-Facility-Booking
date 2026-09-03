# Task 2 report

## Result

Facility, Booking and Equipment service-result error branches now use Task 1's `ApiErrorMapper`. A shared `ApplicationCall.respondApiError(ApiError)` helper writes the mapped `HttpStatusCode` and serialized `ErrorResponse`. Existing status, code, name, text and successful DTO responses were preserved. Measurement, Monitoring and Incident routing branches were not migrated.

## TDD evidence

Added failing-first API tests in `src/test/kotlin/ServerTest.kt` for:

- the shared error responder with an exact 409 response;
- representative exact 400/404 errors for Facility, Booking and Equipment;
- representative exact 409 errors for Facility and Booking.

The first focused test run was attempted before production changes. The repository wrapper could not start because it contains CRLF line endings:

```text
./gradlew test --no-daemon --console=plain --tests com.doduohor.ServerTest
zsh:1: ./gradlew: bad interpreter: /bin/sh^M: no such file or directory
```

Thus the test runner could not reach a Kotlin test failure. An alternate wrapper invocation with `bash ./gradlew ...` confirmed the same CRLF failure. Invoking `GradleWrapperMain` directly with a writable temporary Gradle home reached dependency distribution setup but failed before compilation because the environment could not resolve `services.gradle.org`:

```text
GRADLE_USER_HOME=/tmp/sports-facility-booking-gradle java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --console=plain --tests com.doduohor.ServerTest
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-9.5.1-bin.zip
Attempt 1/1 failed. Reason: services.gradle.org
java.net.UnknownHostException: services.gradle.org
```

## Verification

The required full command was also attempted:

```text
./gradlew test --no-daemon --console=plain
zsh:1: ./gradlew: bad interpreter: /bin/sh^M: no such file or directory
```

`git diff --check` completed without whitespace errors. Automated tests are **not claimed as passed** because Gradle could not start/download.

## Files changed

- `src/main/kotlin/api/mapper/ApiErrorMapper.kt` — shared response helper.
- `src/main/kotlin/Routing.kt` — Facility, Booking and Equipment result-branch migration only.
- `src/test/kotlin/ServerTest.kt` — exact API contract coverage.

## Concerns

- Gradle verification remains blocked by the CRLF wrapper, read-only default Gradle home, and unavailable `services.gradle.org`.
- Equipment service-result branches expose 400/404 but no 409 result; 409 coverage therefore uses the Facility and Booking routes.
- Git reports the repository's existing LF-to-CRLF normalization warning for touched Kotlin files; no wrapper or unrelated files were changed.

## Review fix

The shared responder contract test now calls `configureSerialization()` before installing its minimal routing application, matching the existing application/test setup and enabling `ErrorResponse` serialization. No production code was changed.

Focused verification after the fix:

```text
./gradlew test --no-daemon --console=plain --tests com.doduohor.ServerTest --tests 'com.doduohor.ServerTest.api error responder preserves status and exact error response'
zsh:1: ./gradlew: bad interpreter: /bin/sh^M: no such file or directory
```

The direct wrapper fallback was also attempted and remained blocked before compilation:

```text
GRADLE_USER_HOME=/tmp/sports-facility-booking-gradle java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --console=plain --tests com.doduohor.ServerTest
Downloading https://services.gradle.org/distributions/gradle-9.5.1-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Tests are not claimed as passed.
