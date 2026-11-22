---
title: Coupled sulfur/nitrogen recycle-throughput balance
description: Scale qualified H2, H2S, NH3, and non-H2 recycle receipts to process rates.
---

# Coupled sulfur/nitrogen recycle-throughput balance

`RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance` converts a
qualified coupled recycle-and-purge basis to explicit kg/h and kmol/h. It preserves
the upstream sulfur, nitrogen, hydrogen-supply, recovery, and purge assumptions and
does not re-solve or mutate the qualified receipt.

For feed flow `m_feed` and basis mass `M_basis`, the scale is `m_feed / M_basis` in
1/h. Basis masses are multiplied by that scale for kg/h; molar amounts are also
divided by 1000 for kmol/h. Internal recycle is reported separately and excluded
from the external closure:

```text
feed + fresh makeup = liquid product + export gas
```

```java
RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance rate =
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance.calculate(
        recycle, 1000.0);

double freshMakeupKgPerHour = rate.getFreshMakeupGasMassFlowKgPerHour();
double recycleKgPerHour = rate.getRecycleGasMassFlowKgPerHour();
double exportKgPerHour = rate.getExportGasMassFlowKgPerHour();
double freshHydrogenKmolPerHour = rate.getFreshHydrogenMolarFlowKmolPerHour();
double exportAmmoniaKmolPerHour = rate.getExportAmmoniaMolarFlowKmolPerHour();
```

On the public 1000 kg/h DOE/OEDI Big Hill basis with the qualified illustrative
recovery and purge scenario, the receipt requires 3.101471912 kg/h fresh makeup
gas, exports 7.612023933 kg/h gas, reports 0.604754608 kmol/h fresh H2 and
0.241056033 kmol/h recycle H2, and closes the external mass-rate residual to
numerical zero.

This rate receipt inherits the public DOE/OEDI Big Hill assay and the EIA, AIChE,
and NIST evidence cited by the upstream coupled material, supply, and recycle
receipts. It introduces no fitted parameter or new physical correlation.

The calculation is unit conversion and conservation bookkeeping. It does not
predict reaction kinetics, catalyst performance, phase equilibrium, separator
recovery, compressor duty, pressure, temperature, heat duty, emissions,
optimization, or compliance. Those effects require independent engineering
qualification.
