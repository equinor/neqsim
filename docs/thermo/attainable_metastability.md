---
title: "Attainable Metastability — Volume Balancing Method"
description: "Predicting the superheat and pressure-undershoot limit of a rapidly depressurising liquid with NeqSim's volume balancing method (Log, 2025). Covers the rarefaction outflow balance, Plesset-Zwick bubble growth, the n_bub tuning parameter, and a CO2 example."
keywords: "metastability, superheat limit, depressurisation, rarefaction wave, bubble nucleation, Plesset-Zwick, volume balancing, CO2 blowdown, flashing flow, attainable superheat"
---

# Attainable Metastability — Volume Balancing Method

When a liquid is depressurised rapidly — for example the rarefaction wave that
travels into a pipe after a full-bore rupture — phase change is delayed and the
liquid becomes **metastable** (superheated) below its saturation pressure. The
maximum attainable superheat (equivalently, the pressure undershoot) controls
the driving force for the subsequent flashing two-phase flow and is an important
input to depressurisation and pipeline-rupture safety studies.

NeqSim implements the **volume balancing method** of Log (2025) in
[`AttainableMetastabilityVolumeBalance`](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/util/nucleation/AttainableMetastabilityVolumeBalance.java)
(package `neqsim.util.nucleation`).

---

## Background

Classical nucleation theory (CNT, see
[`ClassicalNucleationTheory`](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/util/nucleation/ClassicalNucleationTheory.java))
reproduces the attainable superheat near the critical point but strongly
over-predicts it at low reduced temperature, where an empirical,
temperature-dependent correction factor is normally required.

The volume balancing method removes that empirical factor by recognising that,
away from the critical point, the limit is set by **evaporation into
pre-existing bubbles** (gas pockets trapped in wall crevices) rather than by
homogeneous nucleation. The metastability limit is the point on the
depressurisation path where the rate of vapour volume *created* by the growth of
those bubbles first balances the rate of liquid volume *lost* through the
rarefaction outflow:

$$
\left.\frac{dV}{dt}\right|_{\text{evaporation}} \;\ge\; \left.\frac{dV}{dt}\right|_{\text{outflow}}
\quad\Longrightarrow\quad \text{metastability limit reached.}
$$

---

## Method

### Volume loss (rarefaction outflow)

The depressurising liquid is treated as an isentropic rarefaction wave. Starting
from the initial pressure $p_0$ at constant molar entropy, the outflow speed $u$
is integrated along the metastable isentrope using the Riemann invariant for a
simple wave,

$$
u_i = u_{i-1} - \frac{2\,(p_{i-1} - p_i)}{\rho_i c_i + \rho_{i-1} c_{i-1}},
\qquad
\left.\frac{dV}{dt}\right|_{\text{outflow}} = -\,u_i\,A_{\text{pipe}},
$$

where $c$ is the single-phase (metastable) liquid speed of sound and
$A_{\text{pipe}} = \pi r_{\text{pipe}}^2$ the pipe cross-sectional area. The
metastable liquid root is obtained with
`setForceSinglePhase(PhaseType.LIQUID)`.

### Volume creation (bubble growth)

Each pre-existing bubble grows by heat-transfer-limited evaporation following
the asymptotic Plesset & Zwick (1954) solution (Collier & Thome, Eq. 4.27).
Evaluated at a short reference time $\Delta t$, the bubble radius and growth rate
are

$$
R = \frac{2\,\Delta T_{\text{sat}}\, k_l}{h_{lg}\,\rho_g}\sqrt{\frac{3\,\Delta t}{\pi\,\alpha_l}},
\qquad
\frac{dR}{dt} = \frac{R}{2\,\Delta t},
\qquad
\left.\frac{dV}{dt}\right|_{\text{evaporation}} = 4\pi R^2\,\frac{dR}{dt}\,n_{\text{bub}},
$$

with liquid superheat $\Delta T_{\text{sat}} = T - T_{\text{sat}}(p)$, liquid
thermal conductivity $k_l$, thermal diffusivity
$\alpha_l = k_l / (\rho_l c_{p,l})$, latent heat $h_{lg}$, saturated vapour
density $\rho_g$, and a single temperature-independent tuning parameter
$n_{\text{bub}}$ (the number of pre-existing bubbles).

All thermodynamic and transport properties are taken from the supplied NeqSim
`SystemInterface`, so any equation of state available in NeqSim (SRK, PR,
GERG-2008, reference EOS, …) can be used. Unlike the original reference
implementation, the liquid thermal conductivity is taken from NeqSim's general
transport-property models rather than a substance-specific correlation, so the
method is not limited to a single fluid.

---

## Parameters

| Parameter | Setter | Default | Meaning |
|-----------|--------|---------|---------|
| Number of bubbles $n_{\text{bub}}$ | `setNumberOfBubbles` | `1.0e8` | Single tuning parameter; more bubbles → faster evaporation → smaller attainable superheat |
| Pipe radius $r_{\text{pipe}}$ [m] | `setPipeRadius` | `0.02` | Sets the outflow cross-sectional area |
| Minimum pressure [Pa] | `setMinimumPressure` | `3.0e5` | Lower bound of the depressurisation search |
| Number of steps | `setNumberOfSteps` | `1000` | Pressure steps used to integrate the isentrope |
| Reference time $\Delta t$ [s] | `setBubbleReferenceTime` | `1.0e-4` | Time at which the asymptotic growth rate is evaluated |

> **Note:** $n_{\text{bub}}$ is a free parameter fitted to data; values in the
> range $10^8$–$10^9$ reproduce the CO₂ depressurisation experiments in Log
> (2025). It is the only temperature-independent tuning constant in the model.

---

## Example — liquid CO₂ blowdown

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.nucleation.AttainableMetastabilityVolumeBalance;

// Pure liquid CO2 at 0 C and 120 bara
SystemInterface co2 = new SystemSrkEos(273.15, 120.0);
co2.addComponent("CO2", 1.0);
co2.setMixingRule("classic");

AttainableMetastabilityVolumeBalance vb = new AttainableMetastabilityVolumeBalance(co2);
vb.setNumberOfBubbles(1.0e8);
vb.setPipeRadius(0.02);
vb.setNumberOfSteps(600);

vb.calculateLimit(273.15, 120.0e5); // T0 [K], p0 [Pa]

if (vb.isLimitFound()) {
  double pLimitBar = vb.getLimitPressure() / 1.0e5; // metastability limit pressure [bara]
  double superheat = vb.getSuperheat();             // attainable superheat [K]
}

// Machine-readable summary (LinkedHashMap / JSON)
String json = vb.toJson();
```

`toMap()` / `toJson()` return the inputs and, when a limit is found, the limit
pressure, temperature, saturation temperature, and superheat — suitable for
direct inclusion in a study `results.json`.

---

## Scope and related tools

This is a screening / research method for a pure (or pseudo-pure) liquid. It
predicts the **limit** of metastability, not the subsequent two-phase flashing
flow.

- For the equilibrium blowdown driving force, use
  [`DepressurizationSimulator`](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/process/safety/depressurization/DepressurizationSimulator.java).
- For metastable / spinodal region classification, use
  [`SpinodalDecompositionDetector`](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/util/nucleation/SpinodalDecompositionDetector.java).

---

## References

1. Log, A.M. (2025). On the attainable metastability of liquids during
   depressurisation — effect of pre-existing bubbles. *Journal of Fluid
   Mechanics* **1018**, R2. <https://doi.org/10.1017/jfm.2025.10545>
2. Plesset, M.S. and Zwick, S.A. (1954). The growth of vapor bubbles in
   superheated liquids. *Journal of Applied Physics* **25**, 493–500.
3. Collier, J.G. and Thome, J.R. (1994). *Convective Boiling and Condensation*,
   3rd ed. Oxford University Press, Eq. 4.27.
