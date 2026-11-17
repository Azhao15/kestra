# Security model — Kestra (Azhao15/kestra)

Kestra OSS fork: Java/Micronaut backend, Vue 3 SPA, Helm chart in `charts/kestra`. `SECURITY.md` covers vulnerability disclosure; this file describes the threat model security scans are judged against. The first scan using it covered `webserver/` and `ui/` only.

## User-provided facts (these drive reranking)
- **Exposure:** the deployment is internet-facing.
- **Attackers that matter:** unauthenticated internet users, webhook callers (anyone who knows/guesses a webhook URL+key), and malicious flow authors (flow YAML/namespace files they control, rendered or executed by Kestra).
- **Trusted:** users who hold the Basic-auth credentials are fully trusted with full access. Issues that only an authenticated Basic-auth user can trigger against themselves/the instance are accepted risk (except where a *malicious flow author's* content reaches another user or an unauthenticated path).
- **Most critical asset:** secrets and the KV store (namespace secrets, KV values, credentials stored in them).
- **Out of scope for this scan:** everything outside `webserver/` and `ui/` (core engine, worker, script runners, JDBC, Helm) — not scanned; findings there would not exist.
- **Tags wanted:** OWASP Top 10 category (plus Component for filtering).

## Components
| Component | Covers |
|---|---|
| Webserver API | `webserver/src/main/java/io/kestra/webserver/controllers/**` (flows, executions, namespaces, namespace files, templates, triggers, logs, dashboards, blueprints proxy, AI copilot), `services/`, `converters/`, `utils/` |
| Authentication | `webserver/.../filter/AuthenticationFilter.java`, `services/BasicAuthService.java`, `BasicAuthCredentials.java`, `MiscController` `/configs`, `/basicAuth`, `/basicAuthValidationErrors`; UI `ui/src/utils/basicAuth*`, `ui/src/utils/axios.ts` |
| Secrets & storage | `KVController`, `SecretController`, `NamespaceSecretController`, `NamespaceFileController`, execution file/storage download endpoints |
| Execution engine | `core/`, `executor/`, `scheduler/`, `jdbc*` (not in scan scope; context only) |
| Worker & script runners | `worker/`, `script/` (not in scan scope; context only) |
| Web UI | `ui/src/**` (Vue SPA: Markdown rendering, `v-html` sinks, Monaco editor, AI copilot UI, inputs forms) |
| Deployment (Helm) | `charts/kestra` (context only) |

## System overview & trust boundaries
1. **Internet → Webserver API** (`/api/v1/**`). `AuthenticationFilter` enforces Basic auth (header or cookie) when configured, except: paths ending `/configs`; `/basicAuth` and `/basicAuthValidationErrors` until Basic auth is initialized; `open-urls` from `cli/src/main/resources/application.yml` (`/ping`, `/api/v1/*/executions/webhook/`, `/api/v1/basicAuthValidationErrors`); Micronaut management endpoints.
2. **Webhook callers → ExecutionController** `POST/GET /api/v1/{tenant}/executions/webhook/{namespace}/{id}/{key}` — unauthenticated by design; the key is the only secret.
3. **Flow author content → server/UI**: flow YAML, namespace files, templates, markdown descriptions, inputs and outputs are stored and later rendered (Pebble on server, Markdown/`v-html` in UI) or imported (ZIP import in `FlowController`, `TemplateController`, `NamespaceFileController`).
4. **Webserver → external services**: `BlueprintController.fastForwardToKestraApi` proxies to api.kestra.io via Micronaut `HttpClient`; `AiController` calls configured LLM providers.
5. **Webserver → secrets/KV/internal storage** (most critical asset), namespace-scoped (`KVStoreService` namespace inheritance checks).

## Entry points & who reaches them
- Unauthenticated: `/configs`, `/basicAuth*` (pre-init), `/ping`, webhook endpoints, management endpoints, static UI (`StaticFilter`).
- Webhook callers: webhook endpoints (body/headers/path become execution trigger data).
- Malicious flow authors: content they create is rendered for other users (UI XSS), executed server-side (Pebble), or parsed (ZIP/YAML import).
- Basic-auth users: everything else (trusted).

## Assets
1. **Secrets & KV values** (critical, user-named).
2. Basic-auth credentials (gate to everything).
3. Execution data, logs, internal storage files, namespace files.
4. Availability of the webserver.

## Existing controls
- Basic auth filter with salted password hash; credentials can be set once via `/basicAuth` while uninitialized.
- Namespace scoping for KV/secrets/files; tenant path attribute (`tenants/`).
- Webhook key required on webhook endpoints.

## Accepted risk / ruled out (from the user)
- Abuse by authenticated Basic-auth users (full trust).

## Open questions
- Is Basic auth always enabled in the internet-facing deployment, or is it sometimes left uninitialized?
- Are webhook keys treated as secrets (high entropy) by flow authors?
- Does anyone other than the admin author flows (i.e. who are "malicious flow authors" in practice — compromised Git sync, imported blueprints, other teams)?
