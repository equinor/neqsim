---
title: "Coupled sulfur/nitrogen hydrotreating screening economics receipt"
description: "Caller-priced liquid-product, export-gas, feed-cost, operating-cost, and screening-margin bookkeeping."
---

# Coupled sulfur/nitrogen hydrotreating screening economics receipt

`RefineryHydrotreatingSulfurNitrogenScreeningEconomicsReceipt` adds a transparent variable-cost screening layer to the qualified coupled sulfur/nitrogen hydrotreating chain. It consumes the qualified net product-intensity receipt and accepts three caller-owned scenario inputs: liquid-product price, export-gas price, and liquid-feed cost, each in currency units per tonne of its stated mass basis.

No default price, market forecast, or economic datum is embedded in NeqSim. The public DOE Big Hill Sweet case remains provenance for the qualified assay-derived sulfur and nitrogen inputs and the upstream material bookkeeping; the illustrative prices below are explicitly hypothetical caller inputs.

## Calculation contract

For liquid product rate \(\dot m_L\), export gas rate \(\dot m_G\), and feed rate \(\dot m_F\), all in t/h, the caller supplies prices \(p_L\), \(p_G\), and feed cost \(c_F\):

\[
V_L = \dot m_L p_L, \qquad V_G = \dot m_G p_G, \qquad V = V_L + V_G
\]

\[
C_F = \dot m_F c_F, \qquad C_V = C_F + C_{op}, \qquad M = V - C_V
\]

Here \(C_{op}\) is the already-qualified net operating cost from the upstream receipt and \(M\) is a signed screening margin. Value, variable cost, and margin are reported per hour, per tonne of feed, and per tonne of external liquid product. Three explicit residuals close product value, variable cost, and screening margin independently.

Export gas is valued only on its qualified total mass flow. This receipt makes no heating-value, composition-quality, sales-specification, or allocation claim.

## Illustrative public case

The retained Big Hill scenario uses 1000 kg/h feed and produces 995.4894479786512 kg/h liquid product plus 7.61202393313295 kg/h export gas. With hypothetical caller inputs of 600 currency/t liquid product, 100 currency/t export gas, and 450 currency/t feed, the receipt reports:

| Result | Value |
| --- | ---: |
| Liquid-product value | 597.2936687871908 currency/h |
| Export-gas value | 0.761202393313295 currency/h |
| Total product value | 598.054871180504 currency/h |
| Feed cost | 450.0 currency/h |
| Qualified net operating cost | 54.13873899696584 currency/h |
| Total variable cost | 504.13873899696586 currency/h |
| Screening margin | 93.9161321835382 currency/h |

These values test arithmetic, unit bases, scaling, signed-margin behavior, and closure only. They are not contemporary market values or an economic recommendation.

## Acceptance and exclusions

The receipt fails closed for missing upstream evidence and for negative or non-finite caller prices or feed cost. A negative screening margin is valid and remains finite. The upstream receipt guarantees positive feed and liquid-product denominators and qualified operating-cost closure.

This capability is not a product-quality or specification model, market forecast, lifecycle assessment, fixed-cost model, capital estimate, depreciation schedule, tax or financing model, inventory model, optimization, compliance determination, profitability forecast, or investment-decision method. It does not credit internal recycle and does not infer export-gas energy content or commercial acceptability.

## Related documentation

- [Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt](refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt)
- [Coupled sulfur/nitrogen product-distribution receipt](refinery_hydrotreating_sulfur_nitrogen_product_distribution_receipt)
- [Net coupled sulfur/nitrogen hydrotreating operating receipt](refinery_hydrotreating_sulfur_nitrogen_net_operating_receipt)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
