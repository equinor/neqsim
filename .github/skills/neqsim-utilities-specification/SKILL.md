---
name: neqsim-utilities-specification
version: "1.1.0"
description: "USE WHEN: selecting and documenting process utility levels and quality requirements: steam, cooling/heating media, instrument air, fuel gas, nitrogen, water and refrigeration. Covers utility design bases, consumer-to-header allocation and project specification reviews. Use neqsim-utility-design for screening calculations and neqsim-utilities-design-report for the complete report workflow."
last_verified: "2026-10-25"
---

# NeqSim utilities specification

Define the service conditions a utility must deliver at each consumer before sizing
its supply system. Reuse the process design basis and solved cases. Select utility
levels with [heat integration](../neqsim-heat-integration/SKILL.md), then use
[utility design](../neqsim-utility-design/SKILL.md) for screening calculations.
For the full report, load
[utilities design report](../neqsim-utilities-design-report/SKILL.md).

## Establish the requirement source

Read the project basis, applicable regulatory requirements, company specifications,
consumer datasheets and vendor operating envelopes. Use
[standards lookup](../neqsim-standards-lookup/SKILL.md) to verify designations,
editions, scope and applicability. Record each requirement's source, identifier,
value, unit, operating case, verification status and evidence. Resolve conflicts
through the project's documented precedence and deviations process.

NORSOK P-002 is a candidate process-system basis where adopted by the project;
instrument-air quality may invoke ISA-7.0.01 and ISO 8573-1. Applicability and
licensed requirements must be checked before assigning limits. Do not identify
NORSOK U-001 or IOGP S-714 as generic utility-system specifications, or cite
ASME PTC 12 as a general boiler-feedwater quality specification.

A public catalogue confirms scope and edition, not compliance with unavailable
clauses. Keep unavailable requirements as explicit gaps. Do not turn examples,
legacy skill tables or a screening model's defaults into mandatory design values.

## Shared consumer and header contract

For every service, define:

- Consumer tag, supply and return header, battery limits and physical location.
- Duty or flow by operating case, including duration and intermittent demand.
- Minimum, normal and maximum pressure, temperature and quality at the consumer;
  distinguish these from generation conditions and mechanical design conditions.
- Required availability, allowable interruption, startup needs and loss-of-utility
  response, with their requirement sources.
- Distribution losses, elevation and pressure drop; supply/return interactions;
  monitoring, sampling and control requirements.

State absolute pressure as bara and gauge pressure as barg. Convert only using an
explicit reference atmospheric pressure. State gas reference temperature, absolute
pressure, dry/wet basis and compressibility convention before using standard-volume
flows; do not assume `Nm3/h` and `Sm3/h` are interchangeable. Distinguish actual
volume, standard volume, mass and molar flow. Preserve the source unit and record
any conversion method. Missing demand is unknown, never an assumed zero.

Choose simultaneous operating cases before consolidating loads. Do not sum
unrelated individual maxima as a normal demand or apply a universal diversity,
spare-capacity or storage factor. Separate continuous, intermittent, startup,
shutdown, emergency and future loads. Record every allowance once and justify it.

## Select levels and quality by service

| Service | Requirements and decisions to establish |
|---|---|
| Steam and condensate | Define pressure levels from process temperature profiles, heat-integration targets and consumer requirements. Calculate saturation properties at absolute pressure; check superheat, steam quality, pressure losses and condensate backpressure. Include letdown, desuperheating, flash recovery, traps and return contamination. Set approach temperatures and margins from the project basis, not a fixed increment. |
| Heating water, hot oil and tracing | Define supply/return temperatures, minimum circulation, fluid properties, freeze protection, thermal stability, expansion and degradation limits. Match the required temperature grade and distinguish recovered heat from fired or electrical input. |
| Cooling water, seawater and air cooling | Use site climate and seasonal cases, seawater intake or cooling-tower conditions, material/corrosion constraints and discharge permits. Set return temperatures, allowable pressure drop, fouling and water chemistry from evidence. Check the complete exchanger temperature profile, including approach limits and turndown; do not select cooling medium solely from one process temperature. |
| Instrument and plant air | Specify delivery pressure, particles, oil and moisture separately by service and consumer. Distinguish pressure dew point at its stated measurement pressure from atmospheric dew point; never compare them without conversion. Specify quality class and test basis where adopted. Derive receiver autonomy from the essential demand during the defined failure and usable pressure range; include actuator stroking and dryer regeneration. |
| Fuel gas | Use the burner/turbine vendor envelope for composition, heating-value basis, Wobbe index, delivery conditions, contaminants and allowable variation. State LHV versus HHV, reference conditions and relative-density basis. Evaluate water/hydrocarbon dew points across the pressure-temperature envelope, liquid separation, filtration, heating and supply interruptions. No universal H2S limit, Wobbe tolerance or dew-point margin is assumed. |
| Nitrogen | Define oxygen concentration and other contaminants, purity convention, dryness, pressure and availability for each purge, inerting or blanketing service. Derive the oxygen target from the material and operating conditions, and validate the purge method, mixing/displacement assumption, leakage and measurement endpoint. Do not prescribe a universal vessel-volume multiplier. Assess supply method, storage and oxygen-deficiency hazards. |
| Water systems | Keep potable, service, demineralized, boiler-feed and cooling-water requirements separate. Define quality from duty, boiler pressure/regime, OEM requirements, metallurgy, corrosion/scale control and applicable health/discharge rules. State conductivity reference temperature and concentration bases. Do not prescribe a universal conductivity, dissolved-oxygen limit or chemical-treatment recipe. |
| Refrigeration | Select the medium/cycle against required temperature profiles, climate, refrigerant properties, safety and environmental restrictions. Check evaporator/condenser approaches, heat rejection, operating envelope and part-load performance. Identify a Carnot-fraction estimate as screening rather than a simulated or vendor-qualified cycle. |

Do not automatically connect nitrogen to instrument air. Any backup connection
needs an engineered change covering equipment compatibility, asphyxiation,
backflow prevention, pressure control and the project's safety requirements.

For firewater, drains, vents/flare, electrical supply and other utility interfaces,
load the relevant specialist skill and use the agreed interface conditions.
Do not infer their adequacy from a heat-duty roll-up.

## Check sizing hand-off and economics

Pass the selected levels, source requirements and simultaneous case loads to
`@utility-design`. Keep raw process heat and shaft-work signs distinct from
positive utility consumption. Confirm current NeqSim API signatures and units
before extracting duties. Identify internal heat recovery so neither recovered
heat nor utility generation is counted twice.

For liquid thermal utilities, use the enthalpy rise across supply/return conditions
for mass-flow calculations. A constant heat-capacity approximation needs a stated
range and justification. Check quality and temperature feasibility before accepting
a calculated flow.

Use sourced tariffs, fuel/emission factors, currency, price date and case durations
for annual cost and emissions. Distinguish thermal energy, shaft power and
purchased electrical energy, including stated efficiencies. Do not multiply every
peak load by full-year hours or count internal transfer energy as purchased energy.

## Acceptance and hand-off

- Link each consumer to one identified service/header and all applicable cases.
- Reconcile utility levels with temperature feasibility and consumer delivery limits.
- Identify source requirements and explain each unresolved or conflicting value.
- Keep project allowances, demand diversity, standby capacity and storage separate.
- Show quality and capacity checks at the limiting consumer, including loss cases.
- Record costs/emissions only with their assumptions and evidence.
- Hand over the specification register, source references, case loads and open gaps
  to [utilities design report](../neqsim-utilities-design-report/SKILL.md).

Use readiness labels consistent with the main process report. Screening estimates
and unconfirmed requirements cannot establish detailed-design adequacy or standards
compliance.

## Related skills

- [Heat integration](../neqsim-heat-integration/SKILL.md)
- [Utility design](../neqsim-utility-design/SKILL.md)
- [Power generation](../neqsim-power-generation/SKILL.md)
- [Standards lookup](../neqsim-standards-lookup/SKILL.md)
- [Utilities design report](../neqsim-utilities-design-report/SKILL.md)
