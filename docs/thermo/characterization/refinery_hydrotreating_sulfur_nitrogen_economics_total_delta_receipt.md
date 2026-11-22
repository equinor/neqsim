---
title: "Coupled sulfur/nitrogen hydrotreating total economics-delta receipt"
description: "Symmetric flow, price, feed-cost, and operating-cost attribution with exact closure."
---

# Coupled sulfur/nitrogen hydrotreating total economics-delta receipt

`RefineryHydrotreatingSulfurNitrogenEconomicsTotalDeltaReceipt` compares arbitrary qualified baseline and candidate screening cases. Both the physical flows and the caller-owned liquid-product price, export-gas price, and feed cost may change. The receipt separates the resulting screening-margin delta into symmetric physical-flow and price/cost contributions and retains the qualified operating-cost change as one aggregate contribution.

No default price, market forecast, or economic datum is embedded in NeqSim. The public DOE Big Hill Sweet chain remains provenance for the assay-derived sulfur and nitrogen inputs and qualified upstream bookkeeping. All illustrative prices remain hypothetical caller inputs.

## Symmetric calculation contract

For a value term $q p$, the exact candidate-minus-baseline identity is:

$\Delta(qp)=\Delta q\,\bar{p}+\Delta p\,\bar{q}$

where $\bar{p}=(p_1+p_0)/2$ and $\bar{q}=(q_1+q_0)/2$. The midpoint identity gives the same contribution magnitudes with opposite signs when the comparison direction is reversed. It does not depend on choosing a physical-first or price-first bridge.

The receipt applies that identity independently to liquid product, export gas, and feed. Feed-flow and feed-cost contributions carry negative margin signs. The qualified operating-cost contribution is $-\Delta C_{op}$. Seven contributions therefore close the total screening-margin change:

$\Delta M=\Delta \dot{m}_L\bar{p}_L+\Delta p_L\bar{\dot{m}}_L+\Delta \dot{m}_G\bar{p}_G+\Delta p_G\bar{\dot{m}}_G-\Delta \dot{m}_F\bar{c}_F-\Delta c_F\bar{\dot{m}}_F-\Delta C_{op}$

Four explicit residuals independently close product-value delta, variable-cost delta, screening-margin delta, and the full symmetric attribution.

## Illustrative public case

The retained Big Hill screening basis compares a 1 t/h baseline at hypothetical prices of 600 currency/t liquid product, 100 currency/t export gas, and 450 currency/t feed with a proportional 2 t/h candidate at 625, 80, and 460 currency/t, respectively.

| Symmetric contribution | Candidate minus baseline |
| --- | ---: |
| Liquid-product flow | 609.7372868869239 currency/h |
| Liquid-product price | 37.33085429919942 currency/h |
| Export-gas flow | 0.6850821539819655 currency/h |
| Export-gas price | -0.22836071799398852 currency/h |
| Feed flow | -455.0 currency/h |
| Feed cost | -15.0 currency/h |
| Qualified operating cost | -54.13873899696584 currency/h |
| Screening-margin delta | 123.38612362514567 currency/h |

The contribution sum and all four residuals close within 1e-9 currency/h. A pure scenario change reduces to price and feed-cost contributions. A fixed-price physical change reduces to flow and aggregate operating-cost contributions.

## Interpretation and exclusions

This is symmetric algebraic attribution, not causal decomposition. The midpoint split shares each bilinear flow-price interaction equally between its flow and price terms. It does not establish why a physical flow, price, feed cost, or upstream operating cost changed.

The receipt is deterministic screening bookkeeping. It is not a process optimizer, market forecast, product-quality model, lifecycle assessment, fixed-cost model, capital estimate, probability model, profitability forecast, or investment-decision method. It does not infer export-gas heating value, commercial acceptability, plant performance, or the cause of an upstream cost change.

## Related documentation

- [Screening-economics receipt](refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt)
- [Same-physical-case scenario-delta receipt](refinery_hydrotreating_sulfur_nitrogen_economics_scenario_delta_receipt)
- [Fixed-screening-price case-delta receipt](refinery_hydrotreating_sulfur_nitrogen_economics_case_delta_receipt)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
