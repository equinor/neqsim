---
title: "Compressor Deposit and Performance Degradation"
description: "Mass-based compressor fouling model that combines deposit-generating calculations (elemental sulfur, salt from entrained water, mineral scale, wax) to compute deposit amount, its effect on polytropic head and efficiency, a degraded performance chart after N operating hours, where deposits form across the impellers, and online (live) washing to plan, recommend a wash fluid, and simulate deposit removal."
---

# Compressor Deposit and Performance Degradation

This model links **deposit-generating process calculations** (elemental sulfur drop-out,
salt from entrained produced water, mineral scale, wax, corrosion products) to
**centrifugal compressor performance**. It answers three questions:

1. **How much deposit** accumulates (from one or several mechanisms combined)?
2. **What is the effect on performance** — polytropic efficiency, head, power, discharge
   temperature, and the degraded compressor chart after `N` operating hours?
3. **Where do the deposits form** — which impeller / stage?

All deposit-to-performance relations are screening-level and the coefficients are exposed
for calibration to measured (e.g. root-cause-analysis) degradation points.

## Components

| Class | Purpose |
|-------|---------|
| `DepositMechanism` | Deposit types (S8, NaCl, CaCO3/BaSO4/CaSO4, FeS, wax, ...) each with a solid density |
| `CompressorDeposit` | Mass-based, multi-mechanism deposit inventory → efficiency and head multipliers |
| `DepositSource` | Interface: a precipitation calculation that yields a deposition rate |
| `SolidFlashDepositSource` | Deposition rate from a `TPSolidflash` (S8, wax) on a stream |
| `EntrainedSaltDepositSource` | Salt precipitation from evaporating entrained produced water |
| `CompressorDepositProfile` | Per-impeller deposit location along the compression path |
| `CompressorChart.getDegradedChart(...)` | Scaled (degraded) performance map |
| `WashFluid` | Wash fluids (water, xylene, toluene, condensate, methanol) with deposit solubilities |
| `CompressorDepositWash` | Recommend a wash fluid, plan the rate/duration, simulate the wash |

## 1. Deposit mass to performance effect

`CompressorDeposit` converts an accumulated deposit **mass** to a fractional flow-area
blockage and hence to a polytropic efficiency multiplier and a head multiplier:

$$
V = \sum_i \frac{m_i}{\rho_i}, \quad t = \frac{V}{A_\text{wetted}}, \quad
b = \min\!\left(\frac{t}{h_\text{passage}},\, b_\text{max}\right)
$$

$$
m_\text{eff} = \max\!\left(1 - k_\text{eff}\, b,\ m_\text{eff,min}\right), \qquad
m_\text{head} = \max\!\left((1-b)^{n_\text{head}},\ m_\text{head,min}\right)
$$

where $m_i$ / $\rho_i$ are the deposit mass and density of each mechanism, $A_\text{wetted}$
is the foulable impeller area, $h_\text{passage}$ the flow-passage half-height, and the
default coefficients are $k_\text{eff} = 1.6$ and $n_\text{head} = 2$.

```java
// Size the foulable geometry from the compressor inlet flow, then add deposits.
CompressorDeposit dep = CompressorDeposit.fromCompressor(compressor);
dep.addDeposit(DepositMechanism.SULFUR_S8, 1.2);   // kg from a sulfur study
dep.addDeposit(DepositMechanism.SALT_NACL, 0.4);   // kg from a salt study

double effLoss  = 1.0 - dep.getEfficiencyMultiplier(); // fractional efficiency loss
double headLoss = 1.0 - dep.getHeadMultiplier();       // fractional head loss
double blockage = dep.getAreaBlockageFraction();
```

Calibrate to measured degradation:

```java
dep.setEfficiencyBlockageCoefficient(1.6); // tune k_eff
dep.setHeadBlockageExponent(2.0);          // tune n_head
// Inverse: how much S8 deposit is consistent with a measured efficiency multiplier?
double kg = dep.depositMassForEfficiencyMultiplier(DepositMechanism.SULFUR_S8, 0.77);
```

### Applying degradation to the simulation

Attaching the deposit model makes `run()` use the degraded machine — for a fixed outlet
pressure the polytropic efficiency drops, so power and discharge temperature rise:

```java
compressor.setPolytropicEfficiency(0.78);   // clean design efficiency
compressor.setDepositModel(dep);            // degradation now applied in run()
compressor.run();

double degradedEff = compressor.getPolytropicEfficiency(); // < 0.78
double power       = compressor.getPower();                // higher than clean
compressor.setDepositModel(null);           // restore clean machine
```

The degradation is applied from a fixed clean baseline, so repeated `run()` calls do not
compound it.

## 2. Deposit amount from the process (precipitation bridge)

Instead of supplying the deposit mass by hand, compute it from the process thermodynamics.
A `DepositSource` turns a precipitation calculation into a deposition rate, and
`CompressorDeposit.accumulate(source, hours)` integrates it over an operating interval.

### Elemental sulfur (and wax) — solid flash

```java
// S8 carried in the (entrained-liquid-bearing) compressor feed drops out on flash.
SolidFlashDepositSource s8 =
    new SolidFlashDepositSource(feed, "S8", DepositMechanism.SULFUR_S8, 0.3); // 30% capture
double s8Rate = s8.getPrecipitationRate("kg/hr"); // thermodynamic drop-out rate
dep.accumulate(s8, 500.0);                          // deposit after 500 h
```

### Salt from entrained produced water — mass balance

Salt (e.g. NaCl) is non-volatile, so it precipitates when entrained brine droplets dry out
in the hot compressed gas:

```java
EntrainedSaltDepositSource salt =
    new EntrainedSaltDepositSource(10.0, 0.05); // 10 kg/hr entrained water, 5 wt% salt
// Optionally estimate the evaporated water fraction from the actual duty:
double evap = EntrainedSaltDepositSource
    .estimateWaterEvaporatedFraction(feed, dischargeT_C, dischargeP_bara);
salt.setEvaporatedFraction(evap);
salt.setCaptureFraction(0.5);
dep.accumulate(salt, 500.0);
```

Model the entrainment upstream by mixing the carried-over liquid into the compressor feed
(or via an imperfect separator efficiency); the flash then determines what precipitates.

## 3. Degraded chart after N operating hours

For a chart-based (fixed-speed) machine, degradation is applied through a **degraded chart**
whose head and efficiency curves are scaled by the deposit multipliers:

```java
compressor.setCompressorChart(cleanChart);
compressor.setDepositModel(dep);            // dep accumulated over 500 h

CompressorChart chart500 = compressor.buildDegradedChart(); // map after 500 h
double head500 = chart500.getPolytropicHead(flow, speed);       // reduced head
double eff500  = chart500.getPolytropicEfficiency(flow, speed); // reduced efficiency
```

`CompressorChart.getDegradedChart(headMultiplier, efficiencyMultiplier)` can also be called
directly. When a chart is in use, the run-time efficiency multiplier is not additionally
applied, so the efficiency loss is never double-counted.

## 4. Where the deposits form (per-impeller location)

Deposition happens where the local pressure and temperature cross the solubility limit. In a
single compressor body the temperature rises from suction to discharge, so a
solubility-limited species such as S8 concentrates on the **cold first impeller** and
re-dissolves toward the hot last impeller; across a multi-stage train the gas re-cools in
each intercooler and can re-deposit on the first impeller after each cooler.

Screening (interpolated stage pressure and temperature):

```java
List<CompressorDepositProfile.StageDeposit> profile =
    CompressorDepositProfile.compute(feed, dischargeT_C, dischargeP_bara, 5, "S8");
int worst = CompressorDepositProfile.worstStage(profile); // usually 1 for a single body
for (CompressorDepositProfile.StageDeposit s : profile) {
  // s.getStage(), s.getPressureBara(), s.getTemperatureC(), s.getSolidRateKgHr()
}
```

Rigorous (uses the compressor's real per-step flashed fluid states):

```java
compressor.setPolytropicMethod("detailed");
compressor.getPropertyProfile().setActive(true);
compressor.run();
List<CompressorDepositProfile.StageDeposit> profile =
    CompressorDepositProfile.computeFromPropertyProfile(compressor, 5, "S8");
```

## 5. Live (online) washing — plan, recommend, and simulate

Washing removes deposit by **dissolving** it in a wash fluid. The `WashFluid` enum carries a
screening solubility of each deposit mechanism, so a fluid only removes the deposits it can
dissolve: **water** dissolves salt but not sulfur; **xylene/toluene** dissolve sulfur and
wax but not salt; **condensate** dissolves wax. `CompressorDepositWash` uses this to
**recommend** a fluid, **plan** the rate/duration, and **simulate** the wash.

$$
\text{removed}_m = \min\!\big(\text{deposit}_m,\ \dot m_\text{fluid}\, S_m\, \eta_\text{contact}\, \Delta t\big)
$$

where $S_m$ is the solubility (kg deposit / kg fluid) and $\eta_\text{contact}$ the contact
efficiency (lower for online washing).

### Recommend a wash fluid

```java
WashFluid fluid = CompressorDepositWash.recommend(deposit);
// salt-dominated -> WATER; sulfur-dominated -> XYLENE/TOLUENE; wax -> CONDENSATE
```

### Plan the wash

```java
CompressorDepositWash washer = new CompressorDepositWash();
washer.setContactEfficiency(0.7);                        // online wash, imperfect contact

double rate = washer.requiredFluidRateKgHr(deposit, fluid, 4.0, 2.0); // 4 kg in 2 h
double hours = washer.hoursToRemoveMass(deposit, fluid, 200.0, 4.0);  // at 200 kg/hr
double dissRate = washer.dissolutionRateKgHr(deposit, fluid, 200.0);  // kg deposit/hr
```

### Simulate the wash (updates the deposit model in place)

```java
CompressorDepositWash.WashResult result = washer.wash(deposit, fluid, 200.0, 2.0);
result.getTotalRemovedKg();
result.getRemovedKg();                       // per mechanism
result.getEfficiencyMultiplierBefore();      // -> getEfficiencyMultiplierAfter()
result.getHeadMultiplierBefore();            // -> getHeadMultiplierAfter()
result.getRemainingDepositKg();
```

### Online wash of a running compressor

```java
compressor.setDepositModel(deposit);
WashFluid best = CompressorDepositWash.recommend(deposit);
CompressorDepositWash.WashResult r = compressor.washOnline(best, 300.0, 3.0);
compressor.run();   // power/discharge temperature recover toward clean
```

### Recommending a wash strategy for mixed deposits

For mixed salt + sulfur fouling, a single fluid cannot remove everything — plan a sequence
(e.g. water for the salt, then xylene for the sulfur). `recommend` returns the fluid that
removes the most mass first; re-run it after each wash to plan the next step.

## Scope and limitations

- Deposit-to-performance relations are screening-level; calibrate `kEff` / `headExponent`
  to measured degradation before quantitative use.
- The screening `compute` interpolates stage pressure geometrically and temperature linearly;
  `computeFromPropertyProfile` uses the rigorous per-step fluid states instead.
- Salt precipitation uses a non-volatile mass balance (robust, avoids solid-salt electrolyte
  convergence); for rigorous solid-salt equilibrium use the electrolyte-CPA scale tools.
- Capture fractions (how much precipitated solid sticks to the impeller) are user inputs.
- Wash-fluid deposit solubilities and contact efficiency are screening values; qualify a wash
  solvent with a laboratory test before field use.

## Related documentation

- [Compressor Design Feasibility Report](compressor_design_feasibility.md)
- [Compressor Mechanical Design](CompressorMechanicalDesign.md)
- [Screening Calculators](screening_calculators.md)
