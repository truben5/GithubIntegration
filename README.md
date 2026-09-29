# GitHub Integration

A Spring Boot service that merges a GitHub user's profile (`GET /users/{username}`) and repositories
(`GET /users/{username}/repos`) into a single JSON response, with caching in front of the GitHub API.

## Quick start

**Requirements:** JDK 25. Maven is not needed because the Maven wrapper is included.

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

> **IntelliJ IDEA:** if the IDE build fails with *"constructor HttpClientGitHubConnector … cannot be applied to
> given types"*, open **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner** and check
> **Delegate IDE build/run actions to Maven**. kohsuke's jar is a multi-release jar: it contains a Java 8
> placeholder of that class plus the real Java 11+ version, and IntelliJ's built-in builder can pick the placeholder.
> Maven (and `./mvnw`) compiles it correctly.

The service listens on **https://localhost:8443** with a bundled self-signed certificate, so use `-k` with curl:

```bash
curl -k https://localhost:8443/api/v1/users/octocat
```

### Optional: GitHub token

Unauthenticated GitHub requests are limited to **60 per hour per IP**. Each uncached lookup uses at least two, so
set a [personal access token](https://github.com/settings/tokens) (no scopes needed) to raise the limit to 5,000:

```bash
GITHUB_TOKEN=<your token> ./mvnw spring-boot:run
```

The token is read from the environment and is never committed, and it is masked if the settings are ever logged. Without one, the service still works and logs
a warning at startup.

### Run the tests

```bash
./mvnw test
```

The tests never call the real GitHub API, so they run offline and aren't affected by rate limits.

## API

### `GET /api/v1/users/{username}`

**200 OK**

```json
{
  "user_name": "octocat",
  "display_name": "The Octocat",
  "avatar": "https://avatars.githubusercontent.com/u/583231?v=4",
  "geo_location": "San Francisco",
  "email": null,
  "url": "https://api.github.com/users/octocat",
  "created_at": "Tue, 25 Jan 2011 18:44:36 GMT",
  "repos": [
    { "name": "boysenberry-repo-1", "url": "https://api.github.com/repos/octocat/boysenberry-repo-1" },
    { "name": "git-consortium", "url": "https://api.github.com/repos/octocat/git-consortium" }
  ]
}
```

(The `repos` list above is shortened.) Field mapping from GitHub: user fields come from `GET /users/{username}`,
and each entry in `repos` comes from `GET /users/{username}/repos` (all pages are fetched, 100 repos per request):

| Response field | GitHub source | Notes |
|---|---|---|
| `user_name` | `login` | GitHub's canonical casing, even if the request used different case |
| `display_name` | `name` | `null` if not set |
| `avatar` | `avatar_url` | Always set; GitHub generates a default avatar if the user hasn't uploaded one |
| `geo_location` | `location` | `null` if not set |
| `email` | `email` | `null` unless the user made it public |
| `url` | `url` | The API URL, as in the spec |
| `created_at` | `created_at` | HTTP-date (RFC 1123 format, two-digit day) in GMT, e.g. `Tue, 25 Jan 2011 18:44:36 GMT` |
| `repos[].name` | `name` | The repository name without the owner, e.g. `Hello-World` |
| `repos[].url` | `url` | The API URL (not the `html_url` web page), as in the spec |

### Errors

Errors use the standard [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` format:

```json
{ "title": "Not Found", "status": 404, "detail": "GitHub user was not found", "instance": "/api/v1/users/no-such-user" }
```

| Status | When |
|---|---|
| `400 Bad Request` | The username isn't 1–39 letters, digits or hyphens. It is rejected **before** calling GitHub, which saves rate-limit budget and stops path tricks such as `../` |
| `404 Not Found` | GitHub has no such user, or the path is unknown |
| `405 Method Not Allowed` | Any method other than GET (includes an `Allow: GET` header) |
| `502 Bad Gateway` | GitHub returned an error or couldn't be reached |
| `503 Service Unavailable` | GitHub's rate limit was hit (includes a `Retry-After` header) |
| `500 Internal Server Error` | Anything unexpected. The body is a generic message, and details are only logged, never returned to the client |

## Design

```
GithubController ──> GithubService ──> GithubClient ──> kohsuke github-api ──> api.github.com
  (validation)        (Caffeine cache)   (only code that
                            │             touches the library)
                            └──> GithubUserMapper (records → response)
```

| Package | Responsibility |
|---|---|
| `controller` | HTTP only: routing, input validation, and turning exceptions into ProblemDetail responses (`ControllerExceptionHandler`) |
| `service` | Caching and orchestration (`GithubService`), plus mapping to the response format (`GithubUserMapper`) |
| `client` | Wraps the GitHub library and returns plain records (`GithubUser`, `GithubRepo`). Library exceptions are translated into `GithubException` |
| `data` | Response records, serialized in snake_case |
| `exception` | `GithubError` (each failure type with its HTTP status and message) and `GithubException` |
| `config` | Builds the `GitHub` client from `app.github.*` properties |

### Key decisions

- **[kohsuke github-api](https://github-api.kohsuke.org/)** provides typed models, pagination and GitHub-specific
  exceptions (e.g. `GHFileNotFoundException`), so the service doesn't need to write its own DTOs or HTTP handling. It's
  configured to *fail fast* on rate limits instead of its default behavior, which is to block the request thread
  until the limit resets. It's also given an explicit connect timeout, because kohsuke's default HTTP client has
  none, and a GitHub that can't be reached would otherwise hang the request thread and every request waiting on the
  same cache key.
- **All library code stays in `client`.** No other layer imports `org.kohsuke`, so switching to Spring's
  `RestClient` would only mean changing that one class, and the service and mapper can be tested with plain records.
- **Caffeine cache** caches the merged response (so a hit costs zero GitHub calls instead of two). Design choices:
  - **Case-insensitive keys:** GitHub usernames are case-insensitive, so `OctoCat` and `octocat` share one entry.
    Keys are lowercased with `Locale.ROOT` so the server's locale can't change them.
  - **Size limit (10) and TTL (15 min)** keep memory use bounded and data reasonably fresh. Both are configurable.
  - **Only one fetch per user at a time:** `Cache.get(key, loader)` loads each key once. If several requests for the
    same uncached user arrive together, only one goes to GitHub and the others wait for its result.
  - **Failures aren't cached**, so a GitHub outage or an unknown user is retried on the next request.
  - **The library's own cache is cleared before every fetch.** kohsuke keeps every user it has fetched in an
    internal map and returns that copy instead of calling GitHub, so without this an expired entry would be
    reloaded with the stale profile, and the map would grow without limit. It is cleared synchronously at the start
    of each cache miss. (An earlier version cleared it from a Caffeine removal listener, but that listener runs
    asynchronously, *after* the reload, so stale data was served; `CacheExpiryIntegrationTest` covers this.) The
    trade-off: if two different users load at the same moment, one load can clear the other's entry, which costs
    at most one extra GitHub call and never returns stale data.
- **Repos are fetched using the `login` GitHub returned**, not the raw input, so the second call always uses the
  canonical name.
- **HTTPS with TLS 1.2/1.3 and HTTP/2** is used even locally, to reflect how the service would run in production.
- **API versioning** (`/api/v1`) leaves room for future breaking changes to the response format.

## Configuration

Settings are in `src/main/resources/application.yml`. All `app.github.*` settings are bound to one validated
record (`GithubProperties`), so an invalid value such as `cache.size: 0` stops the application at startup with a
clear message instead of failing later. Any setting can be overridden with an environment variable
(for example `APP_GITHUB_CACHE_TTL=5m`) or a command-line argument (for example `--app.github.cache.size=100`).

| Property | Default | Purpose |
|---|---|---|
| `app.github.token` | `${GITHUB_TOKEN}` | Optional personal access token |
| `app.github.api-url` | `https://api.github.com` | GitHub API base URL, e.g. for GitHub Enterprise; the integration test points it at a stub server |
| `app.github.connect-timeout` | `5s` | How long to wait when connecting to GitHub before failing with a 502 |
| `app.github.cache.size` | `10` | Maximum number of cached users |
| `app.github.cache.ttl` | `15m` | How long an entry stays cached after it is fetched |
| `server.port` | `8443` | HTTPS port |

## Testing

There are 44 tests at three levels, and each one checks the behavior of a single layer:

| Test class | Type | Covers |
|---|---|---|
| `GithubUserMapperTest` | Unit | Field mapping, the RFC 1123 date (including zero-padded days), null handling, repo order |
| `GithubClientTest` | Unit (Mockito) | Library → record mapping, API (not HTML) repo URLs, empty repos, and translation of every library exception (for both the user call and paging) |
| `GithubServiceTest` | Unit (Mockito) | Cache hits, case-insensitive keys, failures not cached, 404 skips the repos call, the library cache is cleared before each fetch (and not on a cache hit), TTL expiry refetches |
| `GithubControllerTest` | `@WebMvcTest` slice | The exact JSON shape, 400 validation (GitHub is never called), the 404/502/503/500 mapping and headers, and Spring's own 404/405 handling |
| `GithubUserEndpointIntegrationTest` | `@SpringBootTest` + WireMock | End to end, with only GitHub stubbed. It uses the real library's HTTP calls and JSON parsing, and checks the exact response (strict JSON comparison), a real GitHub 404 becoming our 404, and that a repeated request reaches GitHub only once |
| `CacheExpiryIntegrationTest` | `@SpringBootTest` + WireMock | With a short TTL and the real library, a request after expiry returns GitHub's current profile rather than a stale copy |
| `GithubPropertiesTest` | Unit | `toString()` masks the GitHub token so it can't leak into logs |
| `ApplicationTests` | `@SpringBootTest` | The application context starts |

## Limitations and possible improvements

- **kohsuke github-api would be replaced with Spring's `RestClient`.** The library was a reasonable starting point,
  but it hides response headers, has no response timeout, and keeps its own user cache that has to be worked around
  (see Key decisions). The service only reads 9 fields and follows one `Link` header for paging, so a small
  `RestClient`-based `GithubClient` would be simple. Because all library code is in `client`, only that class and
  its config would change. It would also fix the fixed `Retry-After`, the missing response timeout, and the
  403 ambiguity described below, since `RestClient` exposes response headers and supports read timeouts.
- **The cache is per instance and in memory.** With several instances, a shared cache such as Redis would avoid
  duplicate GitHub calls and survive restarts.
- **`Retry-After` is a fixed 60 seconds.** It could use GitHub's `x-ratelimit-reset` header instead to give the
  exact wait time.
- **Only connecting to GitHub has a timeout, not waiting for its response.** kohsuke's JDK-based connector builds
  each request itself, so there's no way to set a response timeout. Switching to its OkHttp connector (an extra
  dependency) would allow a timeout on the whole call.
- **Any 403 counts as a rate limit.** GitHub also uses 403 for some other errors. Checking
  `x-ratelimit-remaining: 0` would tell them apart.
- **Users with a very large number of repositories** are loaded completely into one response. For such accounts,
  a limit or pagination on this API would keep responses a manageable size.
- **The self-signed certificate is for local development only.** In a real deployment, TLS would normally be
  handled by a load balancer or use a trusted certificate.
- **There are no metrics yet.** Adding Spring Boot Actuator would expose health checks and cache hit rates.
- **Logging is at DEBUG level** for this package to make local runs easy to follow. Production would use INFO.
