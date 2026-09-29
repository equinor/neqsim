---
title: "Coupled sulfur/nitrogen hydrotreating break-even economics receipt"
description: "Single-variable break-even prices, margin sensitivities, and explicit closure over qualified screening economics."
---

# Coupled sulfur/nitrogen hydrotreating break-even economics receipt

`RefineryHydrotreatingSulfurNitrogenBreakEvenEconomicsReceipt` adds transparent single-variable break-even arithmetic to the qualified caller-priced screening-economics receipt. It consumes no new assay, market, price, or cost data. Each result holds the other caller-owned scenario terms fixed and solves only the named price or feed-cost axis.

No default price, market forecast, or economic datum is embedded in NeqSim. The public DOE Big Hill Sweet case remains provenance for the qualified assay-derived sulfur and nitrogen inputs and upstream material bookkeeping. The illustrative prices remain hypothetical caller inputs.

## Calculation contract

For qualified liquid-product, export-gas, and feed rates \(\dot m_L\), \(\dot m_G\), and \(\dot m_F\) in t/h, variable cost \(C_V\), operating cost \(C_{op}\), product value \(V\), and the caller-owned scenario prices \(p_L\), \(p_G\), and \(c_F\), the single-variable break-even values are:

\[
p_{L,BE}=\frac{C_V-V_G}{\dot m_L}, \qquad
p_{G,BE}=\frac{C_V-V_L}{\dot m_G}, \qquad
c_{F,BE}=\frac{V-C_{op}}{\dot m_F}
\]

The exact first-order margin sensitivities are \(\partial M/\partial p_L=\dot m_L\), \(\partial M/\partial p_G=\dot m_G\), and \(\partial M/\partial c_F=-\dot m_F\). Three explicit residuals reconstruct zero margin from the current signed screening margin plus the applicable sensitivity multiplied by the reported break-even delta.

Break-even values and deltas are signed. In particular, a negative export-gas break-even price is valid algebraic evidence that the other product value already covers total variable cost; it is not a market forecast or a claim that disposal has commercial value.

## Illustrative public case

For the retained Big Hill scenario at 1000 kg/h feed, 995.4894479786512 kg/h liquid product, and 7.61202393313295 kg/h export gas, the hypothetical caller inputs of 600 currency/t liquid product, 100 currency/t export gas, and 450 currency/t feed produce:

| Result | Value |
| --- | ---: |
| Break-even liquid-product price | 505.6583348279225 currency/t |
| Break-even export-gas price | -12237.86611921223 currency/t |
| Break-even feed cost | 543.9161321835383 currency/t |
| Liquid-product price delta to break even | -94.34166517207751 currency/t |
| Export-gas price delta to break even | -12337.86611921223 currency/t |
| Feed-cost delta to break even | 93.91613218353825 currency/t |

The three reconstructed zero-margin residuals close within 1e-9 currency/h. Throughput scaling preserves all break-even prices and deltas while scaling the margin sensitivities with the qualified mass rates.

## Acceptance and exclusions

The receipt fails closed for missing upstream evidence and requires positive finite feed, external liquid-product, and export-gas rates. It preserves signed break-even values and does not clamp unfavorable results.

This capability is a deterministic screening receipt, not a multi-variable solver, optimizer, market forecast, product-quality model, lifecycle assessment, fixed-cost model, capital estimate, depreciation schedule, tax or financing model, inventory model, profitability forecast, or investment-decision method. It does not infer export-gas heating value, composition quality, sales specification, or commercial acceptability.

## Related documentation

- [Coupled sulfur/nitrogen hydrotreating screening-economics receipt](refinery_hydrotreating_sulfur_nitrogen_screening_economics_receipt)
- [Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt](refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt)
- [Coupled sulfur/nitrogen product-distribution receipt](refinery_hydrotreating_sulfur_nitrogen_product_distribution_receipt)
- [DOE Big Hill Sweet refinery assay validation](refinery_big_hill_validation)
