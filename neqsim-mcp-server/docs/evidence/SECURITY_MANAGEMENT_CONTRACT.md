# Security-management contract qualification

## Engineering question and maturity

Can the existing `manageSecurity` MCP surface preserve its default-disabled desktop behavior, bootstrap recovery path,
administrator gate, structured failures, and standardized packaged-MCP envelope without implying transport security or
production identity assurance?

This Phase 0 increment qualifies that bounded software contract. Inventory remains **1.25 / 20 explicit + 24
contract-tested + 27 confirmed gaps**, and `manageSecurity` remains `CONFIRMED_GAP` until a separate merged promotion
increment updates the inventory atomically. No production runner, schema, deployment profile, numerical model or
companion repository changes here.

## Authoritative behavior

`NeqSimTools.manageSecurity` applies deployment-profile and security access checks, delegates the request JSON to
`SecurityRunner.run`, then applies the standard MCP response envelope and response-size guard.

A fresh server process starts with enforcement disabled. In that desktop-oriented mode, calls are allowed and
`authenticate` reports that security is disabled. When enforcement is enabled:

- `manageSecurity` stays bootstrap-reachable so status and authentication remain accessible;
- `createApiKey`, `revokeApiKey`, `setConfig`, `getAuditLog` and `getRateLimits` require the configured
  administrator token;
- protected tools resolve credentials from the transport-bound `McpRequestContext`, not normal MCP tool arguments;
- missing, invalid and rate-limited credentials return structured errors; and
- malformed/non-object requests and unknown actions fail with structured `SECURITY_ERROR` or `UNKNOWN_ACTION`
  records.

The administrator token used by the packaged qualification is synthetic, fixed to the test process, never printed, and
not a deployment credential. The test enables enforcement, verifies that unprivileged key creation is denied, confirms
that bootstrap status remains available, and disables enforcement again with the synthetic administrator token. It does
not create an API key.

## State, units, assumptions and determinism

API keys, audit entries, rate counters, configuration and the request counter are process-local static state. The
implementation does not provide an external database, vault, durable audit sink or cross-process consistency.
`resetForTests` exists only for package-level Java tests; the packaged protocol harness instead uses a fresh JVM and
restores enforcement before shutdown.

The default rate limit is 60 requests per 60-second window. This is a software throttling parameter, not a process,
thermodynamic or SI engineering unit. Audit timestamps and generated API keys are intentionally nondeterministic.
Qualification checks response shapes, state transitions and authorization decisions, not exact timestamps, UUIDs,
iteration order of concurrent maps or wall-clock expiry.

The desktop-disabled mode is a compatibility default, not a secure production deployment. A production operator must
configure the administrator token before enabling enforcement and must supply transport protection and identity
integration outside this runner.

## Acceptance evidence

- `SecurityRunnerTest` covers status and process-local operational views plus null-input rejection.
- `McpSecurityEnforcementTest` covers disabled-mode admission, a transport-bound principal, anonymous denial,
  bootstrap reachability, administrator-gated key creation and fail-closed missing administrator configuration.
- `test_security_protocol.py` repeats the public boundary through the real packaged STDIO server using synthetic
  credentials and proves that Phase 0 classification remains unpromoted.
- `test_mcp_server.py` retains the broad real-protocol call and 71-tool registration/accounting checks.
- `mcp_protocol_qualification.yml` runs the focused Java and packaged-MCP security contracts before the comprehensive
  protocol regression.

Java and JSON/MCP views are applicable. Python is used only as a dependency-free protocol driver. Notebook, rendered
document, whole-sheet, thermodynamic benchmark, material-balance, convergence and numerical-robustness gates are not
applicable to this process-local security software contract.

## Security and advisory boundary

This evidence qualifies application-level behavior only. It does **not** establish TLS, OIDC, OAuth deployment,
certificate validation, external IAM, durable user/session isolation, vault-backed secret lifecycle, credential
rotation, revocation propagation across processes, distributed rate limiting, tamper-evident audit retention, log
redaction across a hosting stack, denial-of-service resistance, penetration resistance, vulnerability absence,
certification, regulatory compliance or production hardening.

The test does not expose a real credential, mint an API key, connect to a network identity provider, grant repository or
plant access, or authorize simulation, control, design, safety, certification or accountable engineering decisions.
Security configuration remains an operator responsibility and must be combined with deployment-platform controls.

## Explicit limitations and stop boundary

This increment does not change authentication policy, API-key format, roles, tool tiers, approval requirements, audit
retention, rate-limit semantics, response schemas or production defaults. It does not validate scientific fidelity,
canonical-model construction, units in engineering results, conservation, convergence, uncertainty, standards currency
or independent benchmarks.

The underlying canonical model, simulation evidence, deployment configuration, transport identity, secrets platform and
organizational controls remain authoritative. Qualification stops at deterministic, synthetic, process-local
software-contract behavior over packaged STDIO MCP.
