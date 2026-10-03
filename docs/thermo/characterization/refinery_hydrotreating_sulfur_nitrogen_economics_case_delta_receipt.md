---
title: "Coupled sulfur/nitrogen hydrotreating economics case-delta receipt"
description: "Fixed-screening-price case comparison with explicit margin attribution and closure."
---

# Coupled sulfur/nitrogen hydrotreating economics case-delta receipt

`RefineryHydrotreatingSulfurNitrogenEconomicsCaseDeltaReceipt` compares two qualified upstream cases under one identical caller-owned screening-price basis. It reports candidate-minus-baseline flow, value, cost, and margin changes and attributes the screening-margin delta to external liquid-product flow, export-gas flow, feed flow, and the aggregate qualified operating-cost change.

No default price, market forecast, or economic datum is embedded in NeqSim. The public DOE Big Hill Sweet case remains provenance for the assay-derived sulfur and nitrogen inputs and the qualified upstream bookkeeping. All illustrative prices are hypothetical caller inputs.

## Calculation contract

The baseline and candidate receipts must use exactly the same liquid-product price $p_L$, export-gas price $p_G$, and feed cost $c_F$. For candidate-minus-baseline mass-flow changes $\Delta \dot{m}_L$, $\Delta \dot{m}_G$, and $\Delta \dot{m}_F$ in t/h, and the qualified operating-cost change $\Delta C_{op}$ in currency/h:

$\Delta M_L = \Delta \dot{m}_L p_L, \qquad \Delta M_G = \Delta \dot{m}_G p_G$

$\Delta M_F = -\Delta \dot{m}_F c_F, \qquad \Delta M_O = -\Delta C_{op}$

$\Delta M_{\mathrm{attr}} = \Delta M_L + \Delta M_G + \Delta M_F + \Delta M_O$

Four explicit residuals independently close product-value delta, variable-cost delta, screening-margin delta, and case attribution. Signed flow and cost contributions are preserved.

This is a fixed-screening-price comparison, not a causal decomposition of the upstream operating-cost change. Utility quantities, emissions factors, carbon price, fuel price, and other qualified upstream assumptions can differ between cases; their combined effect remains visible as one aggregate operating-cost contribution. Use the same-physical-case economics scenario-delta receipt when only product prices or feed cost change.

## Illustrative public case

The retained Big Hill screening basis uses hypothetical prices of 600 currency/t liquid product, 100 currency/t export gas, and 450 currency/t feed. Comparing otherwise proportional qualified cases at 1 t/h and 2 t/h feed gives:

| Result | Candidate minus baseline |
| --- | ---: |
| Feed rate | 1.0 t/h |
| Liquid-product rate | 0.9954894479786512 t/h |
| Export-gas rate | 0.00761202393313295 t/h |
| Liquid-product flow contribution | 597.2936687871908 currency/h |
| Export-gas flow contribution | 0.761202393313295 currency/h |
| Feed-flow contribution | -450.0 currency/h |
| Operating-cost contribution | -54.13873899696584 currency/h |
| Screening-margin delta | 93.9161321835382 currency/h |

The four attributed contributions close to the screening-margin delta within 1e-9 currency/h.

## Acceptance and exclusions

The receipt fails closed for missing cases, changed caller-owned screening prices, non-finite arithmetic, or closure outside 1e-9 currency/h. Independently constructed but equivalent cases return zero deltas, and lower-throughput cases retain signed negative changes.

This capability is deterministic fixed-price screening bookkeeping. It is not a process optimizer, causal variance model, market forecast, product-quality model, lifecycle assessment, fixed-cost model, capital estimate, probability model, profitability forecast, or investment-decision method. It does not infer export-gas heating value, sales specification, commercial acceptability, or the cause of an upstream cost change.

## Related documentation

- [Coupled sulfur/nitrogen hydrotreating screening-economics receipt](refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt)
- [Coupled sulfur/nitrogen hydrotreating economics scenario-delta receipt](refinery_hydrotreating_sulfur_nitrogen_economics_scenario_delta_receipt)
- [Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt](refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
