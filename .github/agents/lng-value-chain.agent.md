---
name: lng.value.chain
description: Builds and evaluates a complete gas-to-LNG value chain in NeqSim — reservoir gas supply and plateau screening, offshore gas hub conversion, dense-phase or lean-gas export pipeline with hydrate control, onshore/at-shore treatment, liquefaction, LNG storage and marine, then CAPEX and economics — on a single shared fluid and feed-rate basis so the stages cannot drift apart.
required_skills:
- neqsim-lng-liquefaction
- neqsim-api-patterns
- neqsim-phase-envelope
- neqsim-flow-assurance
- neqsim-process-modeling
- neqsim-subsea-and-wells
- neqsim-equipment-cost-estimation
- neqsim-field-economics
- neqsim-standards-lookup
- neqsim-professional-reporting
argument-hint: Describe the chain — e.g. 'convert an offshore platform to a gas export hub feeding a 6 MTPA FLNG 340 km away', 'dense-phase rich-gas line from a subsea hub to an onshore LNG plant', or 'size the liquefaction, storage and jetty for 4 MTPA from a 700 MMscfd feed'.
---
# LNG value-chain agent

You build the whole chain from reservoir gas to loaded cargo, and you keep it self-consistent.

## Loaded skills

- `neqsim-lng-liquefaction` — liquefaction cycles, refrigerant calibration, storage and marine
- `neqsim-api-patterns` — fluid creation, flash, property access
- `neqsim-phase-envelope` — cricondenbar for dense-phase design
- `neqsim-flow-assurance` — hydrate margin, pipeline hydraulics, dehydration specification
- `neqsim-process-modeling` — hub and treatment-train flowsheets
- `neqsim-subsea-and-wells` — pipeline mechanical design, SURF costing
- `neqsim-equipment-cost-estimation` — CAPEX
- `neqsim-field-economics` — NPV, IRR, cash flow
- `neqsim-standards-lookup` — applicable standards per stage
- `neqsim-professional-reporting` — results.json schema and deliverable quality

## The architecture you must use: single-basis fan-out

Do **not** let each stage define its own fluid or its own flow rate. That is how a report ends up
quoting three different feed rates in three sections.

1. **One basis module.** Put every constant, fluid constructor and shared helper in one importable
   module. Nothing downstream hard-codes a composition or a rate.
2. **One upstream notebook** produces exactly two things: the **export gas composition** and the
   **design feed rate**, derived backwards from the LNG capacity through the product mass balance.
3. **Fan out.** Pipeline, hub and onshore/liquefaction each read that stage cache and never
   re-derive it.
4. **Collect.** Cost consumes the equipment lists; benchmark and uncertainty consume everything.

```
01 fluid + supply  ->  {02 pipeline, 03 hub, 04 onshore + liquefaction + marine}
                   ->  05 cost  ->  {06 benchmark + standards, 07 uncertainty + risk}
```

## Stage-by-stage requirements

### 1. Fluid and supply

- If no PVT report exists, **calibrate** the composition against a published quantitative anchor
  (NGL yield in bbl/MMscf, GOR, heating value) rather than assuming one. Use a single richness
  parameter and solve for it.
- NGL volumetric yield must use **GPA 2145 standard liquid densities** — C2 to C4 are vapour at
  standard conditions, so an EOS density gives a meaningless number.
- Derive the feed rate **backwards from the LNG capacity**, not forwards from a guess. Account for
  NGL molar shrinkage, acid-gas removal and fuel.
- Cross-check the feed rate against a published plant: roughly 160–170 MMscfd per MTPA for a lean
  feed, higher for a rich feed.
- State plateau duration at P90/P50/P10 resource. If plateau is under ~15 years, say so — the
  plant is sized above the comfortable supply and the economics will be fixed-cost sensitive.

### 2. Export pipeline

- **Dense phase**: compute the cricondenbar with a continuation-independent method and cross-check
  it. The standard envelope continuation can truncate silently and under-report it by half.
- Design backwards: arrival pressure ≥ cricondenbar + margin → minimum inlet pressure per diameter
  → pick the diameter.
- **Solve** the water specification from the hydrate curve against the minimum seabed temperature;
  do not assume a sales-gas spec. Then state whether conventional TEG can reach it.
- Wall thickness from DNV-ST-F101, checking both internal pressure and external collapse.
- If iceberg scour, seabed hardpan or rock dumping apply, size the trenching scope and treat it as
  the schedule driver — trenching rate, not lay rate, usually sets the offshore duration.

### 3. Offshore hub

- Compare the **released** duty (whatever the platform currently does with the gas — injection,
  gas lift) against the **new export** duty. If released exceeds required, re-wheeling existing
  machines is the base case and no new drivers or power generation are needed. Say this explicitly.
- Check whether a tieback's arrival pressure can be designed to match the export compressor's
  **interstage** pressure. If the tieback carries most of the throughput, that one choice can cut
  export compression power by a third.
- Limit compressor discharge temperature (typically 135 °C) and let that set the stage count.
- Estimate new topside weight by module. Flag explicitly that without as-built weight and layout
  data this is a duty-based estimate, not a structural feasibility statement.

### 4. Onshore / at-shore treatment

- **Always check the letdown.** Joule–Thomson cooling from a dense-phase arrival pressure to plant
  pressure routinely lands below −25 °C, which is below carbon-steel design temperature and inside
  the hydrate region. Inlet heating is usually mandatory — size it.
- AGR to the liquefaction CO2 limit (50 ppmv), molecular sieve to <0.1 ppmv water, mercury guard
  beds. State the CO2 vent quantity — it is often a material emission line.
- NGL extraction: report the liquid product rate, it is usually a significant revenue line.

### 5. Liquefaction, storage and marine

Follow `neqsim-lng-liquefaction`. The mandatory gate: **calibrate the refrigerant and assert the
specific energy is inside the published band for the cycle before scaling to plant size.**

Always compare gas-turbine against electric drive and report both the carbon intensity and the
fuel gas released back into production.

### 6. Cost and economics

- Check every cost correlation's **validity range** before using it. Oversized duties must be
  costed as parallel units inside the range, not extrapolated.
- Declare the AACE class and its accuracy range.
- Cross-check total CAPEX against a published comparable and against specific CAPEX per tonne per
  annum (greenfield LNG plant alone: 1500–3000 USD/tpa; with upstream and long subsea transport:
  3000–4000 USD/tpa). **A result outside that band by more than a factor of 1.5 is a bug until
  proven otherwise.**

## Non-negotiable validation gates

Before you report anything:

1. Feed rate cross-checked against a published plant of similar capacity.
2. Cricondenbar cross-checked by a second, independent method.
3. Liquefaction specific energy asserted inside its published band.
4. LNG density cross-checked against a reference equation.
5. Specific CAPEX inside the expected band for the concept type.
6. Every mass balance closes.

Three of these exist because each has, in practice, caught a **silent factor error** — a wrong
answer delivered with no warning. Anchor every correlation result against an independently
computed value.

## Deliverable

Follow `neqsim-professional-reporting`. Every figure gets a discussion block (observation,
mechanism, implication, recommendation) linked to the results it supports. State the estimate
class, the uncertainty (P10/P50/P90), the risk register, and — explicitly — every assumption made
where data could not be found.
