---
title: "Cryogenic EOS regression cases"
description: "Reference sources, model scope and tolerances for pure methane, normal hydrogen and LNG flash regression cases."
---

[CryogenicEosRegressionTest](../../src/test/java/neqsim/thermo/system/CryogenicEosRegressionTest.java)
runs the public bubble-point temperature and TP flash APIs. All pressures are absolute.
Density is phase mass per volume in the saturation and measured cases, and total mass per
phase-additive volume in the synthetic TP sweep. All mixture compositions are mole fractions.

## Reference implementation comparisons

The following fixtures use CoolProp HEOS 8.1.0, revision
[`4e19537788421c957d7dab7ba5d6bff34404a023`](https://github.com/CoolProp/CoolProp/tree/4e19537788421c957d7dab7ba5d6bff34404a023).
The values are calculated reference results. The fixture introduces no fitted NeqSim parameters.

| Fixture | State and generation | NeqSim scope |
| --- | --- | --- |
| [Pure saturation](../../src/test/resources/neqsim/thermo/cryogenic/pure-saturation-heos.csv) | Pure fluid, mole fraction 1. For each pressure in Pa, evaluate HEOS at molar vapor qualities 0 and 1. Record temperature, both mass densities and the vapor-minus-liquid enthalpy in J/mol. | Methane in PR, SRK, PR78, Statoil SRK-CPA, UMR-PRU-MC and GERG-2008. Normal hydrogen in Leachman. |
| [Synthetic LNG TP flash](../../src/test/resources/neqsim/thermo/cryogenic/lng-flash-heos.csv) | HEOS PT flash at 10 bara. The feed is 0.92 methane, 0.05 ethane, 0.02 propane and 0.01 nitrogen. Record topology, the vapor mole fraction (0 for liquid and 1 for gas), and bulk mass density. | Standard GERG-2008, from 110 to 205 K in 5 K increments, with independent cold starts, repeated states and a warm sweep. |
| Joule–Thomson | HEOS Hydrogen at 25 K / 5 bara, 100 K / 10 bara and 250 K / 35 bara. Evaluate the first derivative of temperature with respect to pressure at constant molar enthalpy and convert from K/Pa to K/bar. | Leachman normal hydrogen. The public getters are also compared against centered differences of independent TP-flashed enthalpies. |

The HEOS methane reference uses [Setzmann and Wagner (1991)](https://doi.org/10.1063/1.555898).
The HEOS `Hydrogen` reference and NeqSim's `hydrogen` component both use the normal-hydrogen
formulation of [Leachman et al. (2009)](https://doi.org/10.1063/1.3160306).
The normal-hydrogen agreement checks separate implementations of the same EOS. Para-hydrogen
and ortho-hydrogen require separately identified fixtures.

The normal-boiling methane point is the seed from issue #5284 and agrees with the approximately
111.67 K reported in [Nwosi (2025), Table 1](https://doi.org/10.53022/oarjet.2025.8.2.0038).
The additional pressures exercise saturation away from the seed. Nwosi's Table 2 lacks an
unambiguous thermodynamic state and caloric reference for regression. The saturated enthalpy
difference is obtained from the same HEOS fluid on both sides to cancel the energy reference.

| Saturation model | Temperature tolerance (K) | Phase-density tolerance | Latent-heat tolerance |
| --- | ---: | ---: | ---: |
| Leachman, normal hydrogen | 0.01 | 0.1% | 0.1% |
| GERG-2008, methane | 0.05 | 0.3% | 0.3% |
| SRK and Statoil SRK-CPA, methane | 1.0 | 5% | 5% |
| PR, PR78 and UMR-PRU-MC, methane | 0.5 | 15% | 3% |

The cubic tolerances account for different liquid volumes and pure-fluid correlations.
The synthetic LNG sweep checks vapor mole fraction within 0.001 and bulk density within 1%.
The tests also check pressure, phase ordering and component balance. The equilibrium pairs
must close component log fugacities within 1e-6. For pure saturation, this applies before and
after property initialization.

## Measured LNG density

[LNG-Norway](../../src/test/resources/neqsim/thermo/cryogenic/lng-norway-measured.csv)
contains four homogeneous liquid measurements from Rafael Christoph Lentner's
[2017 dissertation](https://d-nb.info/1142001709/34), Table E.1, with the normalized feed from
Table A.1. Its mole fractions are 0.91798 methane, 0.05698 ethane, 0.01303 propane,
0.00396 n-butane and 0.00805 nitrogen. The fixture converts MPa to bara by a factor of ten.
The published expanded relative density uncertainty is 0.044% (`k = 2`, Table 3.2).

These measurements postdate the standard GERG-2008 fit and serve as held-out density checks
for that model. The related [Lentner et al. (2017) study](https://doi.org/10.1016/j.jct.2017.04.002)
compares the measurements with GERG-2008. The regression uses a 0.104% relative allowance,
which combines the 0.044% experimental uncertainty with a 0.06% model allowance
([Richter et al., 2016](https://doi.org/10.1016/j.jct.2015.09.034)).
The measured fixture tests liquid density at the reported T/P and composition. The separate
synthetic fixture supplies phase topology and VLE checks.
