---
title: "CO2-ion Furst layer ablation for low-salinity divalent brines"
description: "Controlled no-fit diagnostic of the CO2-ion Furst contribution to CaCl2 and MgCl2 salting trends."
---

## Scope

This diagnostic follows the no-fit Liu et al. (2021) CaCl2 and MgCl2 CO2-solubility
assessment owned by issue #4234 under campaign #3144. It tests whether the sign error
in the calculated salt response is introduced by the Furst CO2-ion interaction layer.
It does not qualify a production parameter change.

The calibration/identifiability screen remains the independent single-salt Liu series.
The Dholabhai Table A.24 mixed brine and all independent hydrate-equilibrium families
remain frozen holdouts and are not used to choose this diagnostic.

## Controlled comparison

The baseline is the current mixing-rule-10
`SystemElectrolyteCPAstatoil` calculation. For the ablated candidate, only the Furst
CO2-ion terms are set to zero in every phase:

$$
W_{\mathrm{CO_2},i}(T)=W^{(0)}_{\mathrm{CO_2},i}
+W^{(1)}_{\mathrm{CO_2},i}\left(\frac{1}{T}-\frac{1}{T_0}\right)
+W^{(2)}_{\mathrm{CO_2},i}\left(\frac{T_0-T}{T}
+\ln\frac{T}{T_0}\right)=0,
\qquad T_0=298.15\ \mathrm{K},\quad
i\in\{\mathrm{Ca^{2+}},\mathrm{Mg^{2+}},\mathrm{Cl^-}\}.
$$

The equation above is the implemented `EosMixingRuleHandler.getWij` convention. The EOS, association treatment, CPA binary interactions, all ion-water and ion-ion
terms, phase-equilibrium solver, source composition, pressure, temperature, and
one-kilogram aqueous-water basis are unchanged. Because the single-ion convention is
gauge-dependent, cation and chloride effects are removed as one block and are not
interpreted separately.

The predeclared ablation matrix contains 16 states: CaCl2 and MgCl2 at 15 000 ppm,
20.4 and 170.1 atm, and 298, 333, 353, and 373 K. This covers the source
temperature range and its lowest/highest pressures while limiting the slow-test cost.
Every state must retain the baseline phase set, component balance residual no larger
than $10^{-10}$ mol, and molecular log-fugacity residual below $10^{-8}$.

## Midpoint sensitivity and initialization correction

The intended follow-up diagnostic applies the fixed algebraic midpoint to the
complete block in every phase:

$$
W_{\mathrm{CO_2},i}^{(\alpha)}(T)=\alpha W_{\mathrm{CO_2},i}(T),
\qquad \alpha=0.5.
$$

The first retained midpoint artifact from merged PR #5154 did not execute this
physical state. The diagnostic read the coefficient arrays before electrolyte
initialization, when the CO2-ion entries were still zero, and its setters then marked
those zero entries as fitted. Consequently, the nominal $\alpha=0.5$ state collapsed
exactly onto the $\alpha=0$ ablation. Its reported curvature is invalid and must not
be used to select, fit, or reject a Furst scale.

The corrected diagnostic calls initialization level zero after the complete species
set and mixing rule are configured, then reads and scales $W^{(0)}$, $W^{(1)}$, and
$W^{(2)}$ together. Every phase must retain at least one nonzero initialized CO2-ion
coefficient. Setter readback must equal the scaled coefficients, and the
temperature-resolved value must satisfy

$$
W_{\mathrm{CO_2},i}^{(\alpha)}(T)
=\alpha W_{\mathrm{CO_2},i}^{(1)}(T).
$$

For $0<\alpha<1$, each nonzero resolved interaction must lie strictly between the
zero-block and full-block values. The flashed midpoint dissolved CO2 must also differ
from both endpoints. These checks distinguish parameter-initialization failure from
thermodynamic nonlinearity without changing production equations or coefficients.

The midpoint was selected after the endpoint results were known, so it remains a
post-endpoint curvature diagnostic rather than a blind preregistered test. It is not
a fit or a candidate production parameter. The same 16 states are retained,
including the two 353 K and 20.4 atm source-magnitude anomalies; those rows are
labeled and are not corrected or excluded. Once a non-collapsed midpoint artifact is
verified, curvature in the apparent salt effect is reported as

$$
C=E(0.5)-\frac{E(0)+E(1)}{2}.
$$

Here $E(0)$ is the zero-block ablation and $E(1)$ is the current baseline. The
diagnostic can test endpoint nonlinearity and salt-family incompatibility at one
concentration, four temperatures, and two pressures. It cannot identify an optimum
scale or establish behavior across concentration.

## Reported observables

The generated CSV retains the baseline source/model decomposition and adds zero-block and midpoint diagnostics:

- dissolved CO2 on the source mol-per-kilogram-solution basis;
- apparent salt effect, $\ln(m_{\mathrm{CO_2}}^0/m_{\mathrm{CO_2}})/n_s$;
- dissolved-CO2, guest-fugacity, and effective-(f/x) log ratios against matched
  pure water;
- water activity, phase identity, component balance, and fugacity closure;
- ablated-minus-baseline dissolved CO2;
- midpoint curvature, evidence label, decomposition identity, and the same numerical closure observables.

The comparison is diagnostic. A production formulation or parameter update requires
the hydration/catalog/assay convention, the seven anomalous Liu rows, the mixed-brine
CaCl2/MgCl2 split, and the single-ion gauge to be resolved or bounded, followed by
frozen-holdout validation and adjacent NaCl, KCl, mixed-salt, and Burgass controls.

## Provenance

The experimental fixture and its source-page provenance are documented by the merged
Liu assessment. The Furst and CPA model/parameter conventions are documented in
`co2_kcl_interaction_provenance.md`. No empirical temperature offset, new fitted
coefficient, or relaxed tolerance is introduced here.
