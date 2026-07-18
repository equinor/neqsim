# NeqSim Maintainers

This file records accountable repository-maintenance roles. GitHub handles in
`CODEOWNERS` enforce review routing; this table explains the competence area and
backup status behind those rules.

The initial ownership mapping uses the current repository maintainer as a
temporary fallback. Independent backup maintainers must be appointed before the
governance action is considered complete.

| Area | Paths | Primary | Backup | Status |
|---|---|---|---|---|
| Repository and releases | `.github/`, root build and governance files | `@EvenSol` | Unassigned | Backup required |
| Thermodynamics and physical properties | `thermo`, `thermodynamicoperations`, `physicalproperties` | `@EvenSol` | Unassigned | Backup required |
| Process simulation | `process` excluding the separately owned areas below | `@EvenSol` | Unassigned | Backup required |
| Engineering workflows | `process/engineering` and engineering schemas | `@EvenSol` | Unassigned | Backup required |
| DEXPI and engineering exchange | `process/processmodel/dexpi`, DEXPI documentation | `@EvenSol` | Unassigned | Backup required |
| Safety and risk | `process/safety`, safety documentation | `@EvenSol` | Unassigned | Backup required |
| MCP and agent-facing contracts | `mcp`, `neqsim-mcp-server` | `@EvenSol` | Unassigned | Backup required |

## Appointment criteria

A primary or backup maintainer should have:

- demonstrated understanding of the owned code and its engineering domain;
- capacity to review changes within a reasonable time;
- familiarity with the relevant validation and compatibility requirements; and
- agreement from the repository administrators.

Domain reviewers may approve the physical or engineering basis without having
merge permission. Maintainers remain responsible for verifying that this review
is recorded and that repository gates pass.

## Updating this file

Ownership changes require a pull request updating this table and
`.github/CODEOWNERS` together. The pull request should state the incoming
maintainer's agreement and identify any temporary coverage gap.
