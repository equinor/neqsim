---
title: Agent to Skill Map
description: Generated cross-catalog map of NeqSim community and enterprise agents, trust scopes, orchestration types, and required skill dependencies.
---

# Agent to Skill Map

This page summarizes the current agent-to-skill wiring across the public community and internal enterprise catalogs. It is intended for review and governance: every `required_skills` entry should resolve through the loaded core, community, or enterprise skill catalogs.

Generated from:

- `neqsim-community-agents/community-agents.yaml`
- `neqsim-enterprise-agents/enterprise-agents.yaml`

| Trust | Agent | Type | Required Skills |
| --- | --- | --- | --- |
| `community` | `pvt-agent` |  | `neqsim-fluid-quality-check` |
| `community` | `e300-fluid-agent` |  | `neqsim-e300-fluid-io` |
| `community` | `hydrate-screening-agent` |  | `neqsim-hydrate-screening` |
| `community` | `tie-in-screening-agent` |  | `neqsim-fluid-quality-check`, `neqsim-hydrate-screening`, `neqsim-separator-modelling`, `neqsim-resource-classification-screening` |
| `community` | `teg-dehydration-agent` |  | `neqsim-teg-dehydration-modeling` |
| `community` | `process-screening-agent` |  | `neqsim-separator-modelling` |
| `community` | `process-safety-agent` |  | `neqsim-relief-load-screening`, `neqsim-depressurization-screening`, `neqsim-vacuum-collapse-screening`, `neqsim-flare-radiation-screening`, `neqsim-psv-orifice-screening`, `neqsim-safety-function-coverage-screening` |
| `community` | `process-engineer-agent` |  | `neqsim-line-velocity-check`, `neqsim-compressor-operating-window-check`, `neqsim-pressure-drop-screening`, `neqsim-fired-heater-duty-screening` |
| `community` | `compressor-antisurge-agent` |  | `neqsim-compressor-antisurge-recycle` |
| `community` | `dynamic-process-preparation-agent` |  | `neqsim-dynamic-process-preparation` |
| `community` | `dynamic-instrument-controller-agent` |  | `neqsim-dynamic-instrument-controller-setup` |
| `community` | `flow-assurance-engineer-agent` |  | `neqsim-hydrate-margin-check`, `neqsim-wax-margin-check` |
| `community` | `piping-integrity-agent` |  | `neqsim-line-velocity-check`, `neqsim-pressure-drop-screening`, `neqsim-pipe-wall-thickness-screening`, `neqsim-flow-induced-vibration-screening` |
| `community` | `gas-treatment-agent` |  | `neqsim-water-dewpoint-dehydration-screening`, `neqsim-compressor-power-screening` |
| `community` | `subsea-layout-screening-agent` |  | `neqsim-field-layout-import`, `neqsim-subsea-layout-geometry`, `neqsim-bathymetry-profile-screening`, `neqsim-pipe-route-profile` |
| `community` | `reservoir-to-facility-screening-agent` |  | `neqsim-reservoir-depletion-screening`, `neqsim-production-network-routing`, `neqsim-subsea-layout-geometry`, `neqsim-step-out-screening` |
| `community` | `pipe-route-screening-agent` |  | `neqsim-pipe-route-profile`, `neqsim-pressure-drop-screening`, `neqsim-line-velocity-check`, `neqsim-hydrate-margin-check` |
| `community` | `asset-economics-agent` |  | `neqsim-capex-opex-screening`, `neqsim-energy-emissions-screening`, `neqsim-asset-value-npv-screening` |
| `community` | `field-development-economics-agent` |  | `neqsim-fluid-quality-check`, `neqsim-reservoir-depletion-screening`, `neqsim-hydrate-margin-check`, `neqsim-separator-modelling`, `neqsim-line-velocity-check`, `neqsim-capex-opex-screening`, `neqsim-energy-emissions-screening`, `neqsim-asset-value-npv-screening` |
| `community` | `energy-emissions-agent` |  | `neqsim-energy-emissions-screening` |
| `community` | `gas-export-pipeline-agent` |  | `neqsim-line-velocity-check`, `neqsim-pressure-drop-screening`, `neqsim-compressor-power-screening` |
| `community` | `subsea-cooldown-agent` |  | `neqsim-surf-cooldown-screening` |
| `community` | `sand-erosion-agent` |  | `neqsim-sand-erosion-screening` |
| `community` | `produced-water-scale-agent` |  | `neqsim-produced-water-scale-screening` |
| `community` | `reciprocating-compressor-agent` |  | `neqsim-reciprocating-compressor-screening` |
| `community` | `gas-turbine-screening-agent` |  | `neqsim-gas-turbine-performance-screening` |
| `community` | `piping-mechanical-agent` |  | `neqsim-piping-flexibility-screening`, `neqsim-acoustic-induced-vibration-screening` |
| `community` | `utilities-screening-agent` |  | `neqsim-utility-balance-screening` |
| `community` | `artificial-lift-agent` |  | `neqsim-artificial-lift-screening` |
| `community` | `production-optimization-agent` |  | `neqsim-separator-modelling`, `neqsim-compressor-operating-window-check`, `neqsim-compressor-power-screening`, `neqsim-production-network-routing` |
| `community` | `debottlenecking-agent` |  | `neqsim-separator-modelling`, `neqsim-compressor-operating-window-check`, `neqsim-line-velocity-check`, `neqsim-pressure-drop-screening` |
| `community` | `gas-lift-allocation-agent` |  | `neqsim-artificial-lift-screening`, `neqsim-production-network-routing`, `neqsim-reservoir-depletion-screening` |
| `community` | `concept-selection-agent` |  | `neqsim-resource-classification-screening`, `neqsim-capex-opex-screening`, `neqsim-asset-value-npv-screening`, `neqsim-energy-emissions-screening`, `neqsim-step-out-screening` |
| `internal` | `pvt-agent` |  | `neqsim-fluid-quality-check` |
| `internal` | `tie-in-screening-agent` |  | `neqsim-fluid-quality-check`, `neqsim-hydrate-screening`, `neqsim-separator-modelling` |
| `internal` | `flow-assurance-agent` |  | `neqsim-hydrate-screening` |
| `internal` | `hydrate-margin-agent` |  | `neqsim-hydrate-margin-check`, `enterprise-hydrate-margin-check` |
| `internal` | `surf-cooldown-agent` |  | `neqsim-surf-cooldown-screening`, `enterprise-surf-cooldown` |
| `internal` | `sand-erosion-agent` |  | `neqsim-sand-erosion-screening`, `enterprise-sand-erosion` |
| `internal` | `process-screening-agent` |  | `neqsim-separator-modelling` |
| `internal` | `process-model-build-verify-agent` |  | `enterprise-stid-live-lookup`, `enterprise-stid-document-retrieval`, `enterprise-pid-tag-extraction`, `enterprise-stid-evidence`, `enterprise-process-model-build-verify`, `enterprise-plant-data`, `enterprise-seeq-connect` |
| `internal` | `compressor-agent` |  | `neqsim-compressor-power-screening`, `neqsim-compressor-operating-window-check` |
| `internal` | `operations-agent` |  | `neqsim-fluid-quality-check`, `neqsim-hydrate-screening`, `neqsim-separator-modelling`, `neqsim-compressor-power-screening`, `enterprise-alarm-events` |
| `internal` | `plant-data-agent` |  | `neqsim-plant-data`, `neqsim-pid-process-operations`, `neqsim-model-calibration-and-data-reconciliation`, `neqsim-water-hammer`, `enterprise-api-portal`, `enterprise-alarm-events` |
| `internal` | `stid-agent` | enterprise-coordinator | _none_ |
| `internal` | `technical-reader-agent` |  | `enterprise-technical-document-reading`, `enterprise-pid-process-operations`, `enterprise-trapped-liquid-fire-rupture`, `enterprise-water-hammer`, `enterprise-pdf-to-html`, `enterprise-pdf-ocr`, `enterprise-pid-tag-extraction` |
| `internal` | `stid-reader-agent` |  | `enterprise-stid-live-lookup`, `enterprise-pdf-to-html`, `enterprise-pdf-ocr`, `enterprise-pid-tag-extraction`, `enterprise-drawing-tracer`, `enterprise-standard-requirements`, `enterprise-sharepoint-navigator`, `enterprise-lci-toolkit` |
| `internal` | `literature-scout-agent` |  | `enterprise-literature-research`, `enterprise-stid-document-retrieval`, `enterprise-stid-live-lookup`, `enterprise-technical-document-reading`, `enterprise-pdf-to-html`, `enterprise-pdf-ocr` |
| `internal` | `standards-review-agent` |  | `enterprise-standard-requirements`, `enterprise-standards-compliance-screening`, `enterprise-technical-document-reading`, `enterprise-process-design-compliance`, `enterprise-material-selection-review`, `enterprise-engineering-numbering-review`, `enterprise-piping-valve-compliance` |
| `internal` | `stid-evidence-agent` |  | `enterprise-stid-live-lookup`, `enterprise-stid-evidence`, `enterprise-drawing-tracer`, `enterprise-standard-requirements` |
| `internal` | `stid-neqsim-study-agent` |  | `enterprise-stid-live-lookup`, `enterprise-stid-evidence`, `enterprise-stid-neqsim-operational-study`, `enterprise-html-to-pdf`, `enterprise-pptx-presentation`, `enterprise-interactive-artifact` |
| `internal` | `stid-study-review-agent` | enterprise-orchestrator | _none_ |
| `internal` | `stid-safety-study-agent` |  | `enterprise-stid-live-lookup`, `enterprise-stid-evidence`, `enterprise-stid-safety-evidence`, `enterprise-hazop-preparation`, `enterprise-lopa-sil-screening`, `enterprise-barrier-register`, `enterprise-norsok-s001-clause10`, `enterprise-process-safety-indicators`, `enterprise-trapped-liquid-fire-rupture`, `enterprise-tr2000-api`, `enterprise-html-to-pdf`, `enterprise-pptx-presentation`, `enterprise-interactive-artifact` |
| `internal` | `stid-well-integrity-agent` |  | `enterprise-stid-live-lookup`, `enterprise-stid-evidence`, `enterprise-well-barrier-evidence`, `enterprise-well-barrier-verification`, `enterprise-well-integrity-screening`, `enterprise-well-operating-envelope`, `enterprise-well-intervention-risk`, `enterprise-html-to-pdf`, `enterprise-pptx-presentation`, `enterprise-interactive-artifact` |
| `internal` | `seeq-connect-agent` |  | `enterprise-seeq-connect` |
| `internal` | `ecalc-compressor-agent` |  | `enterprise-ecalc-compressor-performance` |
| `internal` | `maintenance-agent` |  | `enterprise-maintenance-api` |
| `internal` | `tr2000-agent` |  | `enterprise-tr2000-api` |
| `internal` | `well-production-routing-agent` |  | `enterprise-well-production-routing`, `enterprise-stid-live-lookup`, `enterprise-stid-evidence`, `enterprise-plant-data`, `enterprise-seeq-connect` |
| `internal` | `pid-operator-agent` |  | `enterprise-pid-topology`, `enterprise-pid-annotation`, `enterprise-pid-completeness` |
| `internal` | `field-development-decision-gate-agent` |  | `enterprise-field-development-decision-gate`, `enterprise-adura-production-technology-requirements`, `enterprise-asset-economics`, `enterprise-cost-estimate` |
| `internal` | `rotating-equipment-agent` |  | `neqsim-compressor-power-screening`, `neqsim-reciprocating-compressor-screening`, `neqsim-dry-gas-seal-screening`, `enterprise-compressor-package-review`, `enterprise-dry-gas-seal-review` |
| `internal` | `piping-engineering-agent` |  | `neqsim-piping-flexibility-screening`, `neqsim-line-velocity-check`, `enterprise-piping-engineering-review` |
| `internal` | `utility-systems-agent` |  | `neqsim-utility-balance-screening`, `enterprise-utility-systems-review` |
| `internal` | `automation-safety-agent` |  | `neqsim-safety-function-coverage-screening`, `enterprise-automation-safety-review` |
| `internal` | `ram-availability-agent` |  | `neqsim-reliability-data-screening`, `enterprise-ram-availability` |
| `internal` | `investment-decision-gate-agent` |  | `neqsim-asset-value-npv-screening`, `enterprise-investment-decision-gate` |
| `internal` | `production-surveillance-agent` |  | `neqsim-reservoir-depletion-screening`, `neqsim-compressor-power-screening`, `enterprise-plant-data`, `enterprise-alarm-events`, `enterprise-maintenance-api` |
| `internal` | `host-capacity-backout-agent` |  | `neqsim-separator-modelling`, `neqsim-compressor-operating-window-check`, `enterprise-process-model-build-verify`, `enterprise-stid-neqsim-operational-study` |
