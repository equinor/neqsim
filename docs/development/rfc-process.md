---
title: NeqSim Request for Comments process
description: Review process for major public contracts, architecture, and engineering-governance changes.
---

# NeqSim Request for Comments process

A NeqSim Request for Comments (NRC) records a material design decision before
the implementation makes it expensive to change. Small fixes and additive local
features do not require an NRC.

## When an NRC is required

Use an NRC for the change classes listed in `GOVERNANCE.md`, especially public
contract incompatibilities, schema major versions, canonical identity changes,
and safety or qualification semantics.

## Workflow

1. Create `docs/rfcs/NNNN-short-title.md` from the template below.
2. Open a draft pull request containing the proposal and no production-code
   implementation.
3. Request the owners of every affected subsystem.
4. Record alternatives, compatibility effects, validation needs, and the
   engineering-governance boundary.
5. Resolve material comments and record the decision as `ACCEPTED`, `REJECTED`,
   or `SUPERSEDED`.
6. Link implementation pull requests back to the accepted NRC.

## Required sections

```markdown
# NRC-NNNN: Title

- Status: PROPOSED
- Owners: GitHub handles
- Created: YYYY-MM-DD
- Target release: version or UNPLANNED

## Context and problem
## Decision
## Public contracts affected
## Engineering and safety boundary
## Compatibility and migration
## Validation and qualification evidence
## Alternatives considered
## Rollback strategy
## Decision record
```

Acceptance records architectural agreement. It does not qualify an engineering
method or approve its use on an asset.
