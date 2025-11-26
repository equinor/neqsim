# Security Triage Policy

This document defines how security findings (CodeQL, secret scanning, Dependabot, OWASP Dependency-Check, SpotBugs, PMD, etc.) are triaged and remediated for this repository.

## 1. Sources of Findings
| Source | Type | Location |
|-------|------|----------|
| CodeQL | Static code scanning | GitHub Security tab (Code scanning alerts) |
| Secret Scanning & Push Protection | Credential leaks | GitHub Security tab (Secret scanning alerts) |
| Dependabot Alerts | Vulnerable dependencies | GitHub Security tab (Dependabot alerts) |
| OWASP Dependency-Check | Dependency CVEs (aggregate report) | CI logs / reports |
| SpotBugs / PMD | Quality & potential security issues | CI verify stage |

## 2. Severity Mapping
Severity is based on CVSS (for dependency CVEs) or CodeQL categorization. Adjustments may be made for exploit maturity and exposure.

| Severity | Examples | Action Window |
|----------|----------|---------------|
| Critical | Remote code execution, credential compromise, secret leak (active) | Fix/rotate within 24h |
| High | Deserialization bugs, SQL injection, high CVSS dependency CVE | Fix within 3 business days |
| Medium | Path traversal, SSRF, medium CVSS dependency CVE | Fix within current sprint (≤ 2 weeks) |
| Low | Minor info leak, low CVSS dependency CVE | Backlog / next maintenance window |
| Informational | Style / false positives / non-exploitable | Mark as `wont-fix` or ignore |

## 3. Triage Workflow
1. Alert surfaces (scan, PR annotation, or dashboard).
2. Assign an owner automatically via `CODEOWNERS` or manually (`@equinor/security-team`).
3. Validate: confirm reproducibility, assess exploitability in our context (e.g., library not used at runtime path?).
4. Classify severity (table above) & document rationale in issue or alert comment.
5. Decide disposition:
   - Remediate (create PR)
   - Mitigate (configuration change / rotation)
   - Accept risk (document reason) -> Mark `wont-fix` (CodeQL) or dismiss (Dependabot) with justification.
6. Track SLA: use labels `security` + `severity/<level>` on issues/PRs.
7. Verify fix: ensure updated scan passes & no regression.

## 4. Secret Incidents
If a secret is found:
- Immediately revoke/rotate the credential.
- Purge from history if high sensitivity (use GitHub guidance / BFG Repo-Cleaner).
- Document rotation in internal runbook; NEVER store secrets in repo afterwards.

## 5. False Positives & Suppression
- CodeQL: Prefer updating code or adding precise guards. If not feasible, add a `# codeql[<query-id>]: disable <reason>` comment only where needed.
- Dependabot: Dismiss with `not used at runtime` or `transitive - awaiting upstream` reason.
- OWASP Dependency-Check: Use suppression XML file if persistent FP (store as `dependency-check-suppression.xml`).

## 6. PR Requirements
- All PRs must have successful: build, tests, CodeQL scan, and (if dependency changes) dependency review.
- Security-sensitive changes (crypto, auth, input parsing) require at least 2 reviewers including one from security team.

## 7. Tooling Enhancements (Future)
- Add SARIF gating step to fail CI if Critical/High new issues appear.
- Automate issue creation for CodeQL alerts via webhook/app.
- Integrate metrics export to dashboard (MTTR, open-by-severity, secrets blocked).

## 8. Metrics & Reporting
Track monthly:
- Open alerts by severity (snapshot)
- New vs resolved counts
- MTTR for Critical/High
- Number of secrets blocked by Push Protection
- Dependency update PR merge rate

## 9. Contacts
- Security questions: `@equinor/security-team`
- Maintainers: see `CODEOWNERS`

## 10. Review
Review this policy quarterly or after major process/tooling changes.

---
_Last updated: 2025-10-29_
