# Utility calculation and qualification method

Use sourced project requirements and qualified properties. These relations are engineering
bookkeeping and independent checks, not numerical requirements attributed to a standard.

## Demand and availability

For each header/case, sum physically simultaneous active consumers after documented shedding.
Add network losses and utility-plant self-consumption; subtract feasible recovery/import once.
Aggregate like quantities on one pressure, temperature and gas-reference basis. Report the chain
`raw load -> event/coincidence selection -> losses -> design allowance -> design load`.
Demand factors need a meaning, source and valid case; event overlap needs time-resolved profiles.
Average operating factors cannot size peak headers. An unavailable demand leaves capacity
assessment unresolved, rather than vanishing from the total.

Available capacity includes only supply units available in that case after site/vendor
derating and specified outages. Check delivery quality/pressure/temperature, transfer delay,
shared dependencies and common-cause exposure. A standby source needing the failed utility is
not independent. Capacity margin is available minus required demand in consistent units;
relative margin needs a stated denominator and is unavailable for zero/unknown demand.
Annual energy/cost needs case duration and tariff/emissions basis, not peak times a guessed year.

## Thermal utilities

Use positive utility consumption: cooling duty is
`Q_cooling = mass_flow * (h_return - h_supply)`, while heating duty is
`Q_heating = mass_flow * (h_supply - h_return)`. A single-phase constant-cp check
uses the corresponding temperature difference with the same sign. Keep this
consumption convention separate from signed heat added to the process fluid.
Use enthalpy differences for phase changes, steam, refrigerants and changing properties.
Check approach across the whole exchanger at worst ambient/intake conditions; an energy balance
does not establish temperature-grade feasibility or area. Keep circulation, make-up and purge
separate. State whether refrigeration COP is a cycle/vendor result or a screening Carnot fraction.
Check rejection heat equals evaporator load plus compressor work, identify auxiliaries and add
condenser rejection to its receiving utility without counting process cooling twice.

Steam networks reconcile generation/import, consumers, letdown, recovery, vents, condensate,
feedwater and blowdown by pressure-level mass/enthalpy balance. Check steam state against
pressure and boiler-efficiency LHV/HHV basis. Heating-medium freeze risk, degradation and
expansion/storage are separate requirements. Pinch targeting is an opportunity; credit only
recovery implemented in the evaluated model or identified as a separate alternative.

## Distribution and storage

Check supply pressure minus static/friction losses against the governing consumer minimum.
State lengths, diameters, roughness, elevation, fittings, properties and method qualification.
Verify pump head/power/NPSH/envelope against evidence. Gas/two-phase headers need suitable
methods; constant-density liquid approximations cannot silently be extended to them.

Liquid usable inventory is the integrated positive supply deficit over the required event;
show dead volume, freeboard, replenishment and concurrent events separately. Gas storage needs
a mass balance between maximum/minimum absolute pressure and temperature using suitable
real-gas properties, followed by explicit reference-volume conversion. Include start/transfer
delay, regulator limits and temperature response. Never size receivers from an arbitrary fixed
number of minutes.

## Air, nitrogen and fuel gas

Separate continuous air leakage/purge from intermittent strokes, packages and start-up events.
Preserve actual/standard volume, pressure/atmospheric dew point and the pressure basis for
quality classes. Check dryer switching/regeneration and loss of a train. Any backup connection
needs approved contamination, pressure, oxygen-deficiency and operating-procedure assessment.

Nitrogen needs residual oxygen/purity basis, pressure, dryness and contaminants. Derive purge
from displacement/dilution/pressure-cycle method and target concentration, considering mixing
and dead legs. Ideal well-mixed dilution at constant volume, temperature and
pressure gives `C = C_in + (C0 - C_in) * exp(-purge_volume / vessel_volume)`;
`C_in` is the oxygen concentration in the purge gas and purge volume is at the
vessel conditions. This is an independent check, not a universal purge requirement.
For decreasing oxygen concentration, a target at or below `C_in` is not reachable
in a finite purge under this model. Verify source purity and target feasibility
before solving. Verify vent destination, asphyxiation risk and service compatibility.

Fuel gas needs composition envelope, LHV/HHV, reference conditions, relative density/Wobbe,
water/hydrocarbon dew points and contaminants checked against actual driver/burner vendor
requirements. Verify APIs and ranges. Keep fuel thermal input, shaft output and electrical
output distinct. Assess start-up fuel and loss-of-fuel explicitly.

## Water and safety interfaces

Keep water quality/treatment, consumption, circulation, reject/discharge and environmental
limits separate. Determine storage autonomy from reliability and replenishment logistics.
Include blowdown/regeneration rejects in demand and drain loads. Drains need credible slugs,
segregation, gas blow-by risk, backpressure, recovery/disposal and containment.

Relief/flare needs approved scenarios and qualified simultaneous-load/backpressure analysis,
not all PSV maxima added together. Firewater needs approved scenarios, areas/objects, simultaneity,
duration, source availability, hydraulics and coverage. Screening tools do not prove compliance
or justify replacing deluge with monitors/PFP. Electrical connected-load/energy balances do not
qualify AC load-flow, short-circuit, protection, stability or black-start performance.

## Minimum method evidence

Record source/model revision, input classification/range, equation/correlation, property model,
assumptions, convergence, independent check/benchmark, sensitivity/residuals, uncertainty and
qualification (screening, validated within stated envelope, external, not assessed). API presence
is not qualification. Avoid stale/mutated solved fluids between cases; preserve case identity and
check repeatability. Upgrade a method only with evidence, not a changed label.
