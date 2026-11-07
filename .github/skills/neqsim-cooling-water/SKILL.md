---
name: neqsim-cooling-water
description: "Model fresh-water and seawater cooling in NeqSim heat exchangers, reference IAPWS utility properties, coolant flow and pump duty, fixed-UA or outlet-temperature cases and seasonal/fouling checks. Use for water-cooled gas coolers, closed cooling water, seawater exchangers and cooling-water utility sizing."
last_verified: "2026-11-07"
---

# Reference cooling water and seawater

Read `docs/process/equipment/reference_cooling_water.md` before constructing the
model. Verify the source/JAR version and APIs; a newly published source API may
not exist in the installed release. Use `@process-model` for the two-stream
flowsheet and `@utility-design` for plant supply/pumping. Coordinate with
`@thermo-fluid` when composition or property validity is uncertain.

## Select the model

| Need | Route |
| --- | --- |
| Pure-water utility stream | `neqsim.thermo.system.SystemWaterIF97` |
| Fixed-salinity standard-composition seawater utility | `neqsim.thermo.system.SystemSeawater` |
| Both inlet flows known; solve duty/return temperatures | `neqsim.process.equipment.heatexchanger.HeatExchanger` |
| Process outlet target; size utility demand from supply/return temperatures | `neqsim.process.equipment.heatexchanger.WaterCooler` |
| Cooling duty roll-up, actual volume and pump power | `neqsim.process.equipment.heatexchanger.CoolingWaterSystem` |
| Glycol, arbitrary brine, gas dissolution, pH, scale, dilution or salt mixing | Use the appropriate mixture/electrolyte workflow; the bulk utility model cannot track salt species |

Keep the process-side EOS and transport models. A `WATER` physical-property enum
on an SRK/PR/CPA system does not select reference-water enthalpy. Reference-system
constructors already add one nominal component; set total flow, without adding
salts or setting an EOS mixing rule. Use the tested Java guide through the
verified Java/JPype interface; do not invent a curated MCP execution route.

## Define and execute the case

1. Record process composition, model/mixing rule, flow, T/P and duty or outlet
   target. Record utility supply T/P, return-temperature limit, mass flow or
   unknown demand, salinity basis and seasonal range. Keep bara separate from
   barg and hydraulic pressure drop.
2. Use IAPWS-IF97 for fresh water. For seawater, set Reference-Composition
   Salinity in **g/kg solution**. Practical salinity 35 corresponds to 35.16504
   g/kg for Reference Composition. Do not substitute g/kg solvent or an
   arbitrary NaCl concentration. Treat missing salinity as a stated assumption.
3. For two streams, create independent fluids and named supply/return streams;
   preserve them in the canonical `ProcessSystem`. The fixed-salinity model
   carries bulk-solution mass using nominal water moles; use mass-based results.
4. For `WaterCooler`, use `setOutletTemperature`, `setWaterInletTemperature`,
   `setWaterOutletTemperature`, `setWaterPressure` and
   `setCoolingWaterSalinity`. Do not force the water property package on the
   process gas. Require return temperature above supply temperature. Utility
   demand alone does not prove a specified UA can reach the process target.
5. For `CoolingWaterSystem`, use `setCoolingWaterPressure` in bara separately
   from `setSystemPressureDrop` in bar. Set `setCoolingWaterSalinity`; supply
   and return temperature setters use Celsius. Remove tower fan demand for a
   once-through seawater system by setting its power fraction to zero.
6. Establish equilibrium and call `initProperties()` before transport getters
   or geometry-based rating. Liquid water uses IAPWS viscosity/conductivity;
   seawater uses the G10-15 conductivity increment and a published salinity
   viscosity multiplier. Do not replace these with unexplained constants.

## Validate and report

- Close total mass and enthalpy on each side; check utility demand against the
  independent reference enthalpy rise and actual inlet volume. Duty is W in
  `HeatExchanger`/`WaterCooler`; `CoolingWaterSystem` requirements are kW.
- Check terminal approaches, positive flow and capacity ordering. Retain the
  existing secant heat-capacity/NTU approximation; a terminal approach check
  does not qualify internal pinch behavior or boiling.
- Inspect `neqsim.thermo.util.steam.SeawaterProperties` validity before using
  results. The implemented bulk cooling envelope is 273.15–353 K, 1–120 bara,
  0–120 g/kg. Above 313 K or 42 g/kg, saline pressure derivatives are
  extrapolations. Report this and the high-pressure extension of the seawater
  viscosity multiplier. Reject actual states outside the implemented envelope.
- Test nearby seasonal T/S/flow, repeated runs, changed salinity and fouling
  cases. Preserve rejected or nonconverged points. The seawater capacity probe
  may be bounded below the hot-feed temperature; this does not permit an actual
  utility outlet outside its model range.
- Change salinity on the live stream fluid after a run, then rerun dependent
  equipment. `Stream.run()` clones fluid state; the original fluid passed to
  its constructor need not remain the live object.
- Cite IAPWS AN5-13(2016), R13-08, R12-08, R15-11, G10-15 and the viscosity
  source. Record version, units, salinity basis, model, assumptions and balance
  residuals. Use `CoolingWaterReferenceTest`, `ReferenceCoolingWaterTest` and
  `ReferenceWaterHeatExchangerTest` as executable evidence, not a claim of
  field qualification or material/corrosion suitability.
- Escalate freeze/boiling margin, fouling allowances, discharge limits,
  chlorination, corrosion, material selection and mechanical design to their
  engineering workflows using actual design evidence. Do not infer those
  results from thermophysical properties alone.
