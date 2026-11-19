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

## Midpoint sensitivity

A follow-up diagnostic applies the fixed algebraic midpoint to the complete block in
every phase:

$
W_{mathrm{CO_2},i}^{(alpha)}(T)=alpha W_{mathrm{CO_2},i}(T),
qquad alpha=0.5.
$

The midpoint was selected after the endpoint results were known, so it is a
post-endpoint curvature diagnostic rather than a blind preregistered test. It is not a
fit or a candidate production parameter. Scaling $W^{(0)}$, $W^{(1)}$, and $W^{(2)}$
together preserves the declared gauge and leaves all other model terms unchanged.

The same 16 states are retained, including the two 353 K and 20.4 atm source-magnitude
anomalies. Those rows are labeled in the generated evidence and are not corrected or
excluded. Curvature in the apparent salt effect is reported as

$
C=E(0.5)-rac{E(0)+E(1)}{2},
$

where $E(0)$ is the zero-block ablation and $E(1)$ is the current baseline. The
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
