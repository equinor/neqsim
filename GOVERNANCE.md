# NeqSim Governance

NeqSim is an open-source engineering calculation library. Repository governance
protects the correctness, maintainability, and traceability of the software; it
does not grant project engineering approval or construction authority.

## Decision model

Routine changes are decided through pull-request review. The subsystem owner in
`CODEOWNERS` is responsible for finding an appropriately qualified reviewer and
for ensuring that the change satisfies the repository quality gates.

The following changes require a NeqSim Request for Comments (NRC):

- incompatible public Java, Python, JSON, DEXPI, or MCP API changes;
- new canonical identities, lifecycle states, schema major versions, or
  engineering approval semantics;
- replacement of a thermodynamic or engineering method used by an existing
  public API;
- changes to qualification levels or industrial deployment boundaries; and
- changes affecting two or more owned subsystems.

The NRC process is described in `docs/development/rfc-process.md`.

## Review requirements

| Change class | Minimum review |
|---|---|
| Documentation or isolated maintenance | One maintainer |
| Public API or serialized contract | Subsystem owner plus one independent maintainer |
| Thermodynamic or engineering method | Subsystem owner plus one independent domain reviewer |
| Safety, approval, or production-readiness semantics | Safety/engineering owner plus one independent domain reviewer |
| Major schema or compatibility change | Accepted NRC and two maintainer approvals |

An author cannot provide the independent approval for their own change.
Automated review and CI evidence support, but do not replace, accountable human
review.

## Maintainer responsibilities

Maintainers are expected to:

- protect physical and engineering correctness, not only compilation;
- require reproducible benchmark or regression evidence appropriate to risk;
- preserve Java 8 compatibility in the core library;
- apply the API lifecycle and deprecation policy;
- keep known limitations and qualification boundaries explicit;
- respond to security reports through Equinor's responsible-disclosure path;
- identify a backup before planned absence or role transition; and
- record material architecture decisions through an NRC.

## Release authority

A release may be issued only when the configured CI and security gates pass and
the release owner confirms the compatibility and migration notes. A software
release is not evidence that every method is qualified for every engineering
application. Method-specific applicability and validation evidence remain part
of the calculation provenance and project engineering basis.

## Ownership changes

Repository administrators appoint or remove maintainers through a reviewed pull
request updating both `CODEOWNERS` and `MAINTAINERS.md`. Every critical
subsystem should have a primary and an independent backup. An unfilled backup
role is a visible governance action and prevents the repository from claiming
that hero dependency has been closed.

## Conduct and escalation

Contributors follow `CODE_OF_CONDUCT.md`. Technical disagreements should first
be resolved with evidence in the pull request or NRC. Unresolved cross-domain
decisions are escalated to the repository maintainers; safety or regulatory
decisions remain with the accountable external engineering authority.
