---
title: Coupled sulfur/nitrogen fired-heater stack-loss balance
description: Reconcile caller-owned wet-flue-gas sensible loss to the qualified fired-heater loss envelope.
---

# Coupled sulfur/nitrogen fired-heater stack-loss balance

`RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance` combines the
wet-flue-gas molar flow from a qualified combustion receipt with caller-owned
stack temperature, reference temperature, and mean wet-flue-gas molar heat
capacity. The immutable receipt returns every input and reconciles the sensible
stack loss to the total furnace loss already qualified upstream.

With wet flue gas in kmol/h, mean heat capacity in kJ/(kmol K), and temperature
difference in K, the unit-explicit equations are:

```text
stack sensible loss [MW] = wet flue gas * mean Cp * (stack T - reference T) / 3,600,000
total furnace loss = stack sensible loss + residual furnace loss
```

```java
RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance stackLoss =
    RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance.calculate(
        combustion, stackTemperatureK, referenceTemperatureK,
        meanWetFlueGasCpKiloJoulePerKmolKelvin);

double sensibleLossMw = stackLoss.getStackSensibleLossMegaWatt();
double residualLossMw = stackLoss.getResidualFurnaceLossMegaWatt();
double fractionOfFuel = stackLoss.getStackLossFractionOfFuelChemicalPower();
```

For the public 1000 kg/h DOE/OEDI Big Hill material case, the qualified
combustion receipt produces 55.139015119 kmol/h wet flue gas and the qualified
utility receipt assigns 0.153850948 MW total furnace loss. Illustrative
caller inputs of 473.15 K stack temperature, 298.15 K reference temperature,
and 34.0 kJ/(kmol K) mean wet-flue-gas heat capacity give 0.091132539 MW stack
sensible loss, 0.062718410 MW residual furnace loss, and 8.885145634% of fuel
chemical power assigned to stack sensible loss. These values qualify the
arithmetic only; they are not defaults, measurements, recommendations, or
fitted furnace correlations.

The NIST Chemistry WebBook publishes gas-phase heat-capacity and enthalpy
relations for combustion-gas species. U.S. Department of Energy process-heating
guidance identifies flue-gas or stack loss as heat leaving with combustion
gases and relates waste-heat opportunity to exhaust-gas temperature and flow.
Those public sources establish the engineering context only: this receipt does
not embed species coefficients, temperature-dependent integration, or a site
heat-capacity value.

The receipt fails closed if the caller's sensible-loss scenario exceeds the
qualified total furnace loss. It does not predict stack temperature, derive
mixture heat capacity, model radiation or convection, calculate dew point or
corrosion, represent draft or pressure drop, size heat-recovery equipment,
estimate recoverable heat, or optimize furnace operation. Those effects require
independent engineering qualification.

Sources accessed 2026-09-26:

- [NIST Chemistry WebBook: gas-phase heat-capacity context](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7782447&Plot=on&Type=JANAFG)
- [U.S. DOE: Waste Heat Reduction and Recovery for Improving Furnace Efficiency](https://www.energy.gov/sites/prod/files/2014/05/f15/35876.pdf)
- [U.S. DOE: Install Waste Heat Recovery Systems for Fuel-Fired Furnaces](https://www.energy.gov/sites/prod/files/2014/05/f16/install_waste_heat_process_htgts8.pdf)
