# NeqSim PT Phase Envelopes

Generate, plot, interpret, validate, and troubleshoot NeqSim PT phase envelopes.

## What This Skill Does

- **Generates envelopes** using the verified `ThermodynamicOperations` API.
- **Uses structured segments** without accidental connections across `NaN` branch breaks.
- **Classifies physical branches** from cricondentherm and topology instead of trusting stored labels.
- **Handles numerical-zero components** without mutating the caller's fluid.
- **Guides solver changes** with focused regressions, Java 8 checks, formatting, and JavaDoc validation.
- **Routes related work** to EOS regression, PVT, flow assurance, CCS/H2, and troubleshooting guidance.

## Use Cases

- Generate a natural-gas or gas-condensate PT phase envelope.
- Plot dew and bubble branches and identify the retrograde region.
- Calculate cricondenbar, cricondentherm, and the critical point.
- Diagnose a singular Michelsen Jacobian caused by zero or trace components.
- Add or review phase-envelope solver code and regression tests.
- Assess CO2/H2 impurity effects or overlay a pipeline operating path.

## Safety Features

- Preserves the caller's thermodynamic system when numerical-zero components are filtered.
- Distinguishes intentional `NaN` branch separators from invalid infinite results.
- Requires physical branch classification and independent validation for decision-critical work.
- Enforces NeqSim's Java 8, Log4j2, Spotless, Checkstyle, and JavaDoc rules for code changes.

## Files In This Skill

| File | Description |
|---|---|
| `SKILL.md` | Main instructions for AI agents |
| `neqsim-phase-envelope-readme.md` | Human-readable overview |

## Usage

This skill is triggered when users ask to:

- "generate a PT phase envelope"
- "plot dew and bubble curves"
- "calculate cricondenbar or cricondentherm"
- "fix a singular phase-envelope Jacobian"
- "handle zero or trace components in the Michelsen solver"
- "interpret a retrograde condensation region"
