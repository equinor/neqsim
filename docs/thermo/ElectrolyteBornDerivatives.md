---
title: Electrolyte Born derivatives and phase-size invariance
description: Born-term chain rules, the Mod2004 scaling correction, related EOS coverage, and literature-based validation requirements.
---

## Correction for issue #3862

`ComponentModifiedFurstElectrolyteEosMod2004.dFBorndN` previously added
`FBornD()` directly to the mole derivative. `FBornD()` is a derivative with
respect to relative permittivity, not mole number, and scales with phase amount.
The resulting chemical potentials and fugacity coefficients changed when all
mole numbers and the phase volume were multiplied by the same factor.

The correction removes this unweighted term. It retains the 2004 variant's
existing convention of zero solvent-permittivity composition derivatives,
consistent with its Born mixed derivatives. Public method signatures, ion
diameters and fitted interaction parameters are unchanged. Calculated fugacity
coefficients and flash results can change, especially for large phase amounts;
the old dependence on the chosen amount basis was erroneous.

Dividing `FBornD()` by total moles would restore scaling but would still not be
the chain-rule derivative. It is therefore not the correction used here.

## Derivation from the implemented free energy

For composition-independent ionic diameters, the Furst-based implementations use

$$F_B=\frac{A_B}{RT}=C(T)X_B\left(\frac{1}{\varepsilon_s}-1\right),\qquad C(T)=\frac{N_Ae^2}{4\pi\varepsilon_0RT},\qquad X_B=\sum_j\frac{n_jz_j^2}{d_j}.$$

Here $n_j$ is the amount in mol, $d_j$ the diameter in m, $z_j$ the signed
charge number, $T$ the temperature in K, $R$ the molar gas constant, $N_A$
Avogadro's constant, $e$ the elementary charge and $\varepsilon_0$ the vacuum
permittivity. $\varepsilon_s$ is the dimensionless solvent relative permittivity.
The sum includes ions; neutral components have $z_j=0$. $F_B$ is extensive and
has units of mol under this molar convention; its mole derivative is dimensionless.

Direct differentiation at fixed temperature, total volume and other amounts gives

$$\frac{\mu_i^B}{RT}=\frac{\partial F_B}{\partial n_i}=C(T)\left[\left(\frac{1}{\varepsilon_s}-1\right)\frac{z_i^2}{d_i}-\frac{X_B}{\varepsilon_s^2}\frac{\partial\varepsilon_s}{\partial n_i}\right].$$

In code this is `FBornX() * XBorni + FBornD() * solventdiElectricdn`.
For the solvent mole-average mixing rule,

$$\varepsilon_s=\frac{\sum_{j\in S}n_j\varepsilon_j(T)}{n_S},\qquad n_S=\sum_{j\in S}n_j,$$

$$\frac{\partial\varepsilon_s}{\partial n_i}=\begin{cases}(\varepsilon_i-\varepsilon_s)/n_S,&i\in S,\\0,&i\notin S.\end{cases}$$

$S$ denotes the neutral-solvent components included by that mixing rule.
$X_B$ scales with amount, while the dielectric derivative scales inversely with
amount. Their product is intensive. A neutral component can therefore have a
nonzero Born chemical-potential contribution in a mixed solvent even though its
charge is zero. For one neutral solvent, this composition derivative vanishes.

The MM implementation uses Born radii rather than diameters, with the equivalent
$8\pi$ prefactor. Radius/diameter conventions must be kept consistent. If radii
depend on temperature or composition, their derivatives also belong in the
corresponding chain rule. A density-dependent permittivity additionally gives a
Born pressure contribution through $-RT(\partial F_B/\partial V)$.

These equations are a direct derivation from NeqSim's implemented free energy;
they are not a claim that one dielectric mixing rule is universally accurate.

## Related EOS audit

The audit starts from master commit
`a7f0e929559b27e70c841b763a77b6bf04c964ff` and checks the Born implementations
and their inheritance paths.

| Implementation | Unweighted extensive Born term? | Evidence/scope |
|---|---|---|
| Furst Mod2004 | Yes, corrected | Direct derivative, Euler identity, scaling and two-phase flash regression |
| Base Furst | No | Includes the solvent-permittivity chain rule; scaling regression |
| Electrolyte CPA and Statoil | No | Inherit the base component Born derivative; scaling regression |
| Electrolyte CPA Advanced | No | Uses the inherited chain rule and overridden Born radii/sum; scaling regression |
| Electrolyte CPA Old | No in this path | Source inspection: inherits the base Born derivative |
| Electrolyte CPA MM | No | Explicit chain rule; scaling regression for molar, volume, Looyenga and Lichtenecker solvent mixing |
| Ordinary SRK, PR, CPA and non-electrolyte SAFT models | This specific defect does not apply | Do not inherit the faulty Mod2004 Born implementation |
| Pitzer / activity-coefficient models | This specific defect does not apply | Separate implementation path; not a full derivative qualification |

Passing the scaling checks does not establish complete derivative consistency
or accuracy against experimental electrolyte data. It answers the narrower
question of whether the same extensive term contaminates the chemical potential.

## Derivative consistency of the base Furst and electrolyte CPA terms

The base Furst implementation and its electrolyte CPA subclasses also require
the complete dielectric and stationary-shielding chain rules. These corrections
retain the implemented free energy and fitted parameter tables:

- The ion-ion and ion-volume dielectric second derivatives retain the
  solvent-permittivity contrast, $\varepsilon_s-1$.
- The Born temperature curvature includes both mixed terms and both solvent
  dielectric derivatives:
  $F_{B,TT}=F_{B,TT}|_{\varepsilon_s}+2F_{B,T\varepsilon_s}\varepsilon_{s,T}
  +F_{B,\varepsilon_s\varepsilon_s}\varepsilon_{s,T}^2
  +F_{B,\varepsilon_s}\varepsilon_{s,TT}$.
- The long-range shielding parameter is an implicit stationary variable.
  If $F_\Gamma=0$, eliminating it gives
  $\overline F_{ab}=F_{ab}-F_{a\Gamma}F_{\Gamma b}/F_{\Gamma\Gamma}$
  for thermodynamic coordinates $a,b$ in temperature, total volume and species
  amounts. Holding shielding fixed in the Hessian omits this response, even
  though the first derivative can use the envelope theorem.

The shielding solve uses a residual scaled by the two terms in its defining
equation. An absolute cutoff on that dimensional equation can return a
nonstationary shielding value and spoil numerical derivative comparisons.
The initial estimate uses the analytic upper bound obtained by omitting the
finite-ion-diameter denominators, avoiding unnecessary iterations without
weakening convergence. The zero-ion limit remains explicit; this does not
qualify composition curvature at the singular zero-concentration boundary.

These are derivative repairs, not a new electrolyte fit or a replacement flash
solver. They can change heat capacities, composition Jacobians and convergence.
The separate legacy Mod2004 implementation is outside this correction. Complete
third-order volume derivatives and general mixture accuracy are not established
by second-derivative tests.

### Checking a model without hiding failures

`ThermodynamicModelTest` checks each active phase and each derivative entry,
rejects non-finite values, and reports numerical mismatches instead of returning
success unconditionally. Its finite differences use detached system snapshots
to test the API's unconstrained partial derivatives. Separate electrolyte
regressions use charge-compatible composition directions. Checking a model
must preserve the caller's state and must not change the caller's tolerance.
Analytical identities, finite differences and experimental validation answer
different questions; none replaces the other two.

### Neutral-component dielectric data remains unqualified

At the audited master snapshot `d28b180969e248837e68b5e982374db69e11a376`,
the nitrogen, n-butane and n-hexane rows in `COMP.csv` and `COMP_EXT.csv` give
relative permittivity about $826674.56$ at 298.15 K under
`Component.getDielectricConstant(T)`. The same malformed polynomial occurs in
14 rows of each table. These inputs affect all four currently failing salting-out
fixtures, although identifying the defect does not prove that a data correction
alone will resolve every prediction. The coefficients resemble incorrectly scaled
water coefficients; simply repairing their exponents would supply a water curve,
not species-valid gas or alkane data. Correcting derivatives does not qualify
these inputs or the resulting salting-out predictions.

Harvey and Lemmon's primary correlation calculates gas-component permittivity
from temperature and density and supplies a mixture-polarization treatment
([NIST paper, DOI 10.1007/s10765-005-2351-5](https://www.nist.gov/system/files/documents/mml/acmd/fluids_modeling/Harvey-Lemmon-50062.pdf)).
The existing temperature-only component API cannot directly represent that
correlation. No arbitrary constant, water correlation or fitted offset is
substituted here. A phase-aware dielectric model needs its own consistent
derivatives, reference-state decision and independent property/phase-equilibrium
validation before these predictions can be qualified. A separate dataset repair
must establish species-specific property sources, applicability ranges and phase
or reference-state conventions, then rerun matched-fugacity molality-based
salting benchmarks. Gas-ion interaction parameters require their own validation
after the dielectric inputs are corrected.

## What modern electrolyte-model development requires

The literature supports explicit ion-solvation contributions with a documented
permittivity model, while also showing that their physical interpretation and
parameterization remain active research topics:

- Maribo-Mogensen, Thomsen and Kontogeorgis (2015) extend CPA with Debye-Huckel
  and Born contributions and a self-consistent static-permittivity model. Their
  reported validation includes activity/osmotic coefficients and phase equilibria.
  [DOI: 10.1002/aic.14829](https://doi.org/10.1002/aic.14829).
- Simonin (2019) compares a semiempirical Born contribution with a nonprimitive
  ion-dipole model and checks Gibbs-Duhem consistency. The contributions need
  not agree, including in sign for sufficiently small ions; adding a Born term
  alone is not proof of physical accuracy.
  [DOI: 10.1063/1.5096598](https://doi.org/10.1063/1.5096598).
- Novak et al. (2023) calculate mixed-solvent MIAC, VLE and LLE with eSAFT-VR Mie.
  Their results emphasize the importance of the permittivity treatment and the
  accuracy of the underlying salt-free mixture, particularly for LLE.
  [DOI: 10.1021/acs.iecr.3c00717](https://doi.org/10.1021/acs.iecr.3c00717).
- Novak et al. (2025) compare e-CPA, ePPC-SAFT and eSAFT-VR Mie contributions
  with explicit-water molecular simulations. Similar total performance can
  arise from different balances of individual terms; component-level agreement
  cannot be inferred solely from fitting total activity coefficients.
  [DOI: 10.1016/j.fluid.2025.114339](https://doi.org/10.1016/j.fluid.2025.114339).

The source claims above were checked against the publisher/institutional
abstracts. The implementation recommendations below follow from differentiating
the model energy and are not presented as verbatim prescriptions from those papers.

Recommended development order:

1. Enforce extensivity of free energy and intensivity of chemical potentials
   over multiple amount scales, including both solvents and ions.
2. Use one free-energy expression and differentiate the actual selected
   permittivity and radius models consistently with respect to $T$, $V$ and
   every component amount. Validate analytical derivatives against finite
   differences or an independent automatic-differentiation calculation.
3. Check mixed-derivative symmetry, the Euler identity and Gibbs-Duhem behavior,
   including solvent derivatives. Treat scale invariance as necessary, not sufficient.
4. Validate water activity, mean ionic activity, osmotic coefficients, densities,
   enthalpy/heat capacity and VLE/LLE/SLE against independent data over the intended
   salt, solvent, temperature and pressure ranges. Declare standard states and
   concentration bases explicitly.
5. Only then compare alternative dielectric decrement, density dependence,
   ion-pairing or ion-specific radius models, with parameter revalidation.

### Remaining Mod2004 limitation

The 2004 phase recalculates solvent permittivity from temperature and neutral
composition, but its derivative code suppresses that dependence. Removing the
spurious extensive term fixes issue #3862; it does not make the legacy variant
the full derivative of its recomputed energy for mixed solvents or thermal
perturbations. Restoring these dependencies requires coordinated changes to
the Born and long-range derivatives and validation of affected properties.
Do not infer modern mixed-solvent/caloric qualification from this correction.

## Regression coverage

`ElectrolyteBornExtensivityTest` checks water/methanol/NaCl liquid states at
amount multipliers from $10^{-6}$ to $10^6$. It checks extensive Born energy,
intensive Born mole derivatives and full log fugacity coefficients, and the
Born Euler identity for these volume-independent solvent-permittivity models.
It also checks the Mod2004 Born derivative against an independent energy finite
difference in a single-solvent state and repeats the methane/water/NaCl TP
flash from issue #3862 at several feed amounts.

The single-solvent finite difference intentionally holds the solvent dielectric
constant unchanged at the selected temperature; it does not qualify the omitted
mixed-solvent or thermal chain rules described above.
