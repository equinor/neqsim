---
applyTo: "src/**/*.java,src/test/**/*.java,.github/skills/**/*.md,docs/**/*.md"
---

# Minimal thermodynamic initialization level

Use the lowest NeqSim thermodynamic initialization level that is sufficient for the quantities being evaluated. Do not raise an initialization level defensively or because a higher level is available.

## Required rule

- Use `init(0)` only for composition and phase-amount bookkeeping that does not require EOS state.
- Use `init(1)` for fixed-temperature/fixed-pressure phase-equilibrium work: cubic-root selection, molar volume and compressibility factor, fugacity coefficients, chemical potentials, tangent-plane or Gibbs-equilibrium comparisons, phase stability, and TP-flash iteration state.
- Use `init(2)` only when first temperature derivatives or caloric properties are actually required, such as enthalpy, entropy, heat capacity, Joule-Thomson coefficients, or temperature derivatives of residual Helmholtz/Gibbs terms.
- Use `init(3)` only when second composition derivatives, full derivative matrices, or another explicitly documented level-3 quantity is required.
- Use `initProperties()` only when physical/transport properties are required. Do not substitute `init(3)` for physical-property initialization.

Before adding or increasing an initialization level, identify the exact downstream getter or derivative that requires it and verify that requirement in the relevant phase/component implementation. If the code only evaluates cubic roots, fugacity equality, fixed-T/P Gibbs equilibrium, phase fractions, or compositions, `init(1)` is normally sufficient.

Higher initialization levels add avoidable calculations in flash and process-simulation hot paths. A change from `init(1)` to `init(2)` or `init(3)` therefore requires a correctness-based justification and focused validation; it must not be accepted solely from an automated review suggestion.

When reducing an existing initialization level, add focused tests showing unchanged requested results and benchmark stable metrics where the path is performance-sensitive.
