---
title: "Coupled sulfur/nitrogen hydrotreating economics scenario-delta receipt"
description: "Same-physical-case price and feed-cost attribution with explicit screening-margin closure."
---

# Coupled sulfur/nitrogen hydrotreating economics scenario-delta receipt

`RefineryHydrotreatingSulfurNitrogenEconomicsScenarioDeltaReceipt` compares two caller-priced screening scenarios over the same qualified physical receipt. It reports candidate-minus-baseline price, value, cost, and screening-margin deltas and attributes the margin change to the three caller-owned scenario inputs.

No default price, market forecast, or economic datum is embedded in NeqSim. The public DOE Big Hill Sweet case remains provenance for the already-qualified assay-derived sulfur and nitrogen inputs and upstream physical bookkeeping. All illustrative prices are hypothetical caller inputs.

## Calculation contract

For unchanged qualified liquid-product, export-gas, and feed rates $dot m_L$, $dot m_G$, and $dot m_F$ in t/h, define candidate-minus-baseline price and feed-cost changes $Delta p_L$, $Delta p_G$, and $Delta c_F$.

$$Delta M_L=dot m_LDelta p_L,qquad Delta M_G=dot m_GDelta p_G,qquad Delta M_F=-dot m_FDelta c_F$$

The attributed screening-margin change is:

$$Delta M_{mathrm{attr}}=Delta M_L+Delta M_G+Delta M_F$$

Four explicit residuals independently close product-value delta, variable-cost delta, screening-margin delta, and price attribution. Signed contributions are preserved; an unfavorable price or higher feed cost produces a negative contribution.

The baseline and candidate must share the same in-memory qualified net product-intensity receipt. This identity requirement prevents a change in throughput, yield, energy, emissions, or operating cost from being mislabeled as a price effect. Compare different physical cases with a separate engineering comparison that exposes their changed physical assumptions.

## Illustrative public case

The retained Big Hill scenario uses 1000 kg/h feed, 995.4894479786512 kg/h liquid product, and 7.61202393313295 kg/h export gas. A hypothetical baseline of 600 currency/t liquid product, 100 currency/t export gas, and 450 currency/t feed is compared with a candidate of 625, 80, and 460 currency/t on the same respective bases:

| Result | Candidate minus baseline |
| --- | ---: |
| Liquid-product price | 25.0 currency/t |
| Export-gas price | -20.0 currency/t |
| Feed cost | 10.0 currency/t |
| Liquid-product margin contribution | 24.88723619946628 currency/h |
| Export-gas margin contribution | -0.152240478662659 currency/h |
| Feed-cost margin contribution | -10.0 currency/h |
| Total product-value delta | 24.73499572080362 currency/h |
| Total variable-cost delta | 10.0 currency/h |
| Screening-margin delta | 14.73499572080362 currency/h |

The attributed contribution sum equals the screening-margin delta within 1e-9 currency/h. Throughput scaling preserves the three caller-input deltas and scales the hourly contributions and margin delta with the qualified mass rates.

## Acceptance and exclusions

The receipt fails closed for missing scenarios, distinct upstream physical receipts, non-finite arithmetic, or closure outside 1e-9 currency/h. An unchanged scenario returns zero deltas, and adverse scenarios retain signed negative margin changes.

This capability is deterministic same-physical-case screening attribution. It is not a multi-variable solver, optimizer, market forecast, product-quality model, lifecycle assessment, fixed-cost model, capital estimate, depreciation schedule, tax or financing model, probability model, profitability forecast, or investment-decision method. It does not infer export-gas heating value, composition quality, sales specification, or commercial acceptability.

## Related documentation

- [Coupled sulfur/nitrogen hydrotreating screening-economics receipt](refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt)
- [Coupled sulfur/nitrogen hydrotreating break-even economics receipt](refinery_hydrotreating_sulfur_nitrogen_break_even_economics_receipt)
- [Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt](refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
