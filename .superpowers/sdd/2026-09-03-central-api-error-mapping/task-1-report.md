# Task 1 report

Status: implemented; focused verification blocked by the environment.

Commit: final commit SHA is reported in the task handoff (`test: cover API error mapper branches`)

## Changes

- Added immutable `ApiError` containing `HttpStatusCode` and `ErrorResponse`.
- Added overloaded pure `ApiErrorMapper.map` functions for facility, booking, equipment, measurement, incident, lifecycle and monitoring service results.
- Added explicit successful-branch assertions for booking creation/search, equipment search, measurement creation/search, incident creation/search/lifecycle, and `MonitoringServiceResult.SuccessWithIncident`.
- Added explicit nested delegation coverage for `MonitoringServiceResult.MeasurementCreateError(CreateMeasurementResult.Success(...))`.
- Existing error-branch assertions remain unchanged; no production code or public contract was changed.
- No service/domain result types or dependencies were changed.

## TDD and test output

RED attempt:

```text
./gradlew test --tests 'com.doduohor.api.mapper.ApiErrorMapperTest' --no-daemon --console=plain
zsh: ./gradlew: bad interpreter: /bin/sh^M: no such file or directory
```

The normalized wrapper then reached Gradle but could not create its distribution in `/home/doduohor/.gradle` because that filesystem is read-only. A direct installed Gradle 9.0.0 run with a writable `/tmp` Gradle home failed during settings configuration because the `foojay-resolver-convention:1.0.0` plugin was not cached and network access to plugin repositories was unavailable. Therefore a compiler-level RED/GREEN result and full test result could not be obtained.

Focused command attempted after implementation:

```text
BUILD FAILED
Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention', version: '1.0.0'] was not found
```

Focused command attempted after the review fix:

```text
./gradlew test --tests 'com.doduohor.api.mapper.ApiErrorMapperTest' --no-daemon --console=plain
zsh: ./gradlew: bad interpreter: /bin/sh^M: no such file or directory

bash gradlew test --tests 'com.doduohor.api.mapper.ApiErrorMapperTest' --no-daemon --console=plain
gradlew: line 2: $'\\r': command not found
... syntax error near unexpected token `newline'

GRADLE_USER_HOME=/tmp/sports-facility-gradle gradle test --tests 'com.doduohor.api.mapper.ApiErrorMapperTest' --no-daemon --console=plain
zsh: command not found: gradle
```

Full test command attempted:

```text
BUILD FAILED
Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention', version: '1.0.0'] was not found
```

Static checks completed: `git diff --check` passed.

## Concerns

- Gradle tests remain unverified until the wrapper is normalized or a Gradle executable is available, and the Foojay Gradle plugin is available locally or network access is restored.
- The report is committed together with the test changes.
