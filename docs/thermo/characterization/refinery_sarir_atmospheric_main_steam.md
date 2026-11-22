---
title: "Sarir atmospheric main-column steam screening"
description: "Explicit tray, pressure-basis, and thermodynamic-state boundaries for source-bounded steam injection."
---

# Sarir atmospheric main-column steam screening

`SarirAtmosphericMainSteamScreen` adds the published main-column steam service to the qualified
Sarir assay → heating → atmospheric-fractionation workflow. It separates published evidence from
the engineering inputs required to make that evidence executable.

## Source boundary

The source is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using Aspen
HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51–64, published 31 March 2022,
DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The
[open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY
4.0.

The published steam rows are retained exactly as source evidence:

| Receiving service | Flow (kg/h) | Temperature (degC) | Pressure (kPa, as reported) |
| --- | ---: | ---: | ---: |
| Main atmospheric column | 340.2 | 150.0 | 476.0 |
| Kerosene side stripper | 68.04 | 150.0 | 476.0 |
| Diesel side stripper | 226.8 | 150.0 | 476.0 |

The source does not publish injection trays, steam quality, steam enthalpy, a complete
thermodynamic state, or an explicit absolute-versus-gauge pressure basis. The three numeric columns
are therefore not, by themselves, an executable stream specification.

## Explicit engineering inputs

The screen models only the main atmospheric-column row. The caller must provide:

- a bottom-up NeqSim injection-tray index;
- an explicit `ReportedPressureBasis.ABSOLUTE` or `ReportedPressureBasis.GAUGE` interpretation;
- a prepared material stream matching the published flow and temperature and the resulting
  absolute pressure; and
- a nonblank description of the independent thermodynamic-state evidence used to prepare it.

For `GAUGE`, the implementation uses 101.325 kPa as the gauge reference and retains the original
476 kPa value separately. The prepared stream must expose exactly one gas phase, a vapor mole
fraction of one, and essentially pure water composition. This verifies the supplied NeqSim state;
it does not derive vapor quality from the source temperature and pressure.

## Java and JPype-accessible workflow

~~~java
SystemInterface water = fractionation.getFeedStream().getFluid().getEmptySystemClone();
water.setTemperature(150.0, "C");
water.setPressure(476.0, "kPa");
water.addComponent("water", 1.0);
water.setMixingRule("classic");
water.setTotalFlowRate(340.2, "kg/hr");

// Example syntax only: the caller must establish and document this gas state independently.
water.setNumberOfPhases(1);
water.setMaxNumberOfPhases(1);
water.setForcePhaseTypes(true);
water.setPhaseType(0, PhaseType.GAS);
water.init(3);
Stream preparedSteam = new Stream("prepared Sarir steam", water);

SarirAtmosphericMainSteamScreen screen =
    SarirAtmosphericMainSteamScreen.configure(
        fractionation,
        1,
        preparedSteam,
        SarirAtmosphericMainSteamScreen.ReportedPressureBasis.ABSOLUTE,
        "independent saturated-vapor enthalpy calculation");

// Only after an engineering study independently qualifies the tray and state basis:
SarirAtmosphericMainSteamScreen.Result result = screen.run(UUID.randomUUID());
double totalClosure = result.getTotalMassClosureRelativeError();
~~~

The tray index `1`, absolute-pressure interpretation, and state-basis text above demonstrate API
syntax only. They are not source values or a qualified Sarir mapping. The repository does not claim
that this example reproduces the refinery steam state or injection location. The same constructors,
enum values, and getters are callable through JPype.

## Acceptance contract

Configuration rejects the request before column mutation when any of these conditions holds:

- the tray index is outside the qualified Sarir simple-tray range;
- the pressure basis or independent state-basis description is missing;
- the column has been solved or already has an additional feed;
- the prepared stream is the crude feed;
- prepared flow, temperature, or interpreted absolute pressure differs from the source boundary;
- water is absent or not essentially pure;
- the prepared system does not retain the crude component slate and component numbering from an
  empty clone; or
- the prepared state is not exactly one gas phase with vapor mole fraction one.

Evaluation rechecks the immutable source row, connection identity, and prepared-stream state. It
then applies the existing MESH-residual, fallback-rejection, product-ordering, column mass-balance,
and column energy-balance gates. In addition, it reports and requires closure of crude plus steam
against the four material products within the qualified 0.05 relative column tolerance.

The result retains the explicit tray, pressure interpretation, state basis, published and modeled
flow/temperature/pressure, vapor fraction, total inlet and product flow, total closure, and column
balance diagnostics. Published plant product rates remain comparison evidence, not tuning targets
or acceptance thresholds.

## Scope boundary

This capability does not infer a tray, pressure basis, quality, enthalpy, or phase state. It does not
build a side stripper, redirect either side-stripper steam row into the main column, configure
pump-arounds, alter the shared column solver, tune to plant rates, or claim plant calibration.
Side-stripper topology and steam states require separate degrees-of-freedom analysis and
independent qualification.
