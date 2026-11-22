---
title: Coupled sulfur/nitrogen fired-heater heat-recovery balance
description: Apply a caller-owned recovery fraction to qualified wet-flue-gas sensible stack loss.
---

# Coupled sulfur/nitrogen fired-heater heat-recovery balance

`RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance` consumes a
qualified stack-loss receipt and a caller-owned recovery fraction. The immutable
receipt returns recovered heat, remaining wet-flue-gas sensible loss, unchanged
residual furnace loss, post-recovery total furnace loss, and explicit closure.

The unit-explicit equations are:

```text
recovered heat [MW] = stack sensible loss * recovery fraction
remaining stack sensible loss = stack sensible loss - recovered heat
post-recovery furnace loss = remaining stack sensible loss + residual furnace loss
original furnace loss = recovered heat + post-recovery furnace loss
```

```java
RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance recovery =
    RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance.calculate(
        stackLoss, callerSpecifiedRecoveryFraction);

double recoveredHeatMw = recovery.getRecoveredHeatMegaWatt();
double remainingStackLossMw = recovery.getRemainingStackSensibleLossMegaWatt();
double postRecoveryLossMw = recovery.getPostRecoveryFurnaceLossMegaWatt();
```

For the public 1000 kg/h DOE/OEDI Big Hill chain, the qualified stack-loss
receipt contains 0.091132539 MW stack sensible loss and 0.062718410 MW residual
furnace loss. An illustrative caller-owned recovery fraction of 0.60 gives
0.054679523 MW recovered heat, 0.036453016 MW remaining stack sensible loss,
and 0.099171425 MW post-recovery furnace loss with numerical-zero closure.
The 0.60 value qualifies the arithmetic only; it is not a measured or
recommended recovery efficiency.

U.S. Department of Energy process-heating guidance identifies exhaust-gas
temperature and flow as important inputs to waste-heat opportunity and notes
that practical recovery depends on process and equipment constraints. This
receipt therefore keeps the recovery fraction caller-owned. It does not claim
avoided fuel, emissions credits, utility savings, or economic benefit.

The receipt accepts recovery fractions from zero through one and fails closed
for non-finite or out-of-range values. It does not size heat-recovery equipment,
predict outlet or stack temperature, derive heat capacity, perform pinch or
exchanger-network analysis, or model pressure drop or operability. It does not
establish dew-point or corrosion limits. Those decisions require independent
engineering qualification.

Sources accessed 2026-09-26:

- [U.S. DOE: Waste Heat Reduction and Recovery for Improving Furnace Efficiency](https://www.energy.gov/sites/prod/files/2014/05/f15/35876.pdf)
- [U.S. DOE: Install Waste Heat Recovery Systems for Fuel-Fired Furnaces](https://www.energy.gov/sites/prod/files/2014/05/f16/install_waste_heat_process_htgts8.pdf)
