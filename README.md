# sportsfacilitybooking

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:
 * [Ktor Documentation](https://ktor.io/docs/home.html)
 * [Ktor GitHub page](https://github.com/ktorio/ktor)
 * [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).


## Features
Here's a list of features included in this project:

| Name | Description |
|------|-------------|
| [Call Logging](https://start.ktor.io/p/io.ktor/server-call-logging) | Logs client requests |
| [Status Pages](https://start.ktor.io/p/io.ktor/server-status-pages) | Provides exception handling for routes |
| [Content Negotiation](https://start.ktor.io/p/io.ktor/server-content-negotiation) | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/io.ktor/server-kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library |


## Building & Running
To build or run the project, use one of the following tasks:


| Task | Description |
|------|-------------|
| `./gradlew test`    | Run the tests     |
| `./gradlew build`   | Build the project |
| `./gradlew run`     | Run the server    |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Docker

Build and run the API, worker, PostgreSQL, RabbitMQ and MongoDB from a clean checkout:

```bash
docker compose --env-file .env.example up --build
```

The checked-in `.env.example` contains local-only placeholder credentials. Copy it to
`.env` if you need to override values; real credentials and Telegram tokens must stay
outside the repository.

Check the API:

```bash
curl http://localhost:8080/health
```

Run the isolated Docker smoke test (it removes only its own containers and volumes):

```bash
bash scripts/docker-smoke.sh
```

The smoke test builds the single multi-stage `Dockerfile`, waits for the API healthcheck,
checks `/health`, prints service logs on failure, and cleans up the smoke project.

The API container receives database settings through environment variables:

| Variable | Default in compose |
|----------|--------------------|
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/sports_facility_booking` |
| `DATABASE_USER` | `sports` |
| `DATABASE_PASSWORD` | `change_me` |

## Technical Debt

- After the MVP is complete, revisit `EventPublisher`: replace plain `mutableSetOf(...)` with a safer subscriber storage approach and consider buffered channels for SSE subscribers.
