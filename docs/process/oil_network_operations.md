---
title: "Oil Pipeline and Terminal Operations"
description: "Pump edges, crude assays, parcels, tanks, thermodynamic blending, oil quality, and cargo scheduling for oil networks."
---

NeqSim separates steady oil-pipeline hydraulics from discrete tank, parcel, and
cargo scheduling. `LoopedPipeNetwork` handles pipes and pumps;
`OilNetworkSchedule` handles receipts, storage, blending, and loading windows.

The executed companion notebook is
[`norwegian_ncs_oil_network_optimization.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process/norwegian_ncs_oil_network_optimization.ipynb).
It uses synthetic Sture/Mongstad-style data and synthetic quality limits. It
does not represent operator acceptance criteria.

## Liquid pump and booster edges

`NetworkElementType.PUMP` reuses NeqSim's process `Pump` model and participates
in the Newton-Raphson network solve.

```java
LoopedPipeNetwork.NetworkPipe booster =
    network.addPump(
        "suction", "delivery", "export booster",
        35.0,     // fixed outlet pressure, bara
        0.78);    // isentropic efficiency

booster.setPumpRatedPowerKW(4500.0);
booster.setPumpMinimumFlowKgS(25.0);
booster.setPumpReverseFlowPolicy(
    LoopedPipeNetwork.PumpReverseFlowPolicy.CHECK_VALVE);
```

Available modes:

| Mode | Factory | Governing input |
| --- | --- | --- |
| Fixed outlet pressure | `addPump(...)` | outlet pressure in bara |
| Differential pressure | `addPumpDifferentialPressure(...)` | rise [bar] |
| Head/flow curve | `addPumpWithCurve(...)` | `Pump` chart and speed |

The edge reports head, pressure rise, shaft power, efficiency, operating
status, rated-power residual, minimum-flow residual, and NPSH residual when
NPSHA/NPSHR data are available. Reverse-flow behavior is explicit:
`CHECK_VALVE` blocks a nonphysical pressure rise; `BYPASS` permits passive
reverse flow without pump work.

Curve inputs must document flow unit, head unit, reference density, speed, and
efficiency basis. A chart-backed pump must be reattached after JSON restoration.

## Crude assays and common component slates

`CrudeAssay` contains:

- a thermodynamic fluid;
- a declared common-slate identifier;
- source/effective-date metadata;
- measured quality attributes with explicit blending rules.

Before thermodynamic blending, every feed must use compatible component
identities and TBP/plus-fraction definitions. Unlike characterizations are
rejected with a re-cut diagnostic; components are never aligned by array
position.

```java
CrudeAssay assay = new CrudeAssay(
    "Synthetic Oseberg-like",
    "public-example-C10-C16",
    oil,
    "Synthetic representative data",
    "2026-01-01");

assay.addMeasuredAttribute(
    "sulfurMassPercent", 0.30, "mass%",
    "Synthetic assay", "mass-weighted");
```

Supported measured-attribute rules:

| Rule | Behavior |
| --- | --- |
| `mass-weighted` | weighted by parcel mass |
| `volume-weighted` | weighted by EOS-calculated parcel volume |
| `calculate-from-EOS` | calculate the typed EOS metric instead |
| `no-blend` | retain only for a single, unblended parcel |

Vapor pressure, density/API, viscosity, bubble point, and other nonlinear
thermodynamic properties are recalculated on the blended fluid. They are not
linearly averaged.

## Tanks, caverns, and parcels

`OilTerminalTank` enforces capacity, heel, receipt rate, withdrawal rate, and
availability. Two mixing modes are available:

- `PERFECT_MIXED`: every withdrawal has the calculated tank blend;
- `SEGREGATED`: FIFO parcel identity is retained and a withdrawal cannot cross
  a parcel boundary without an explicit blend.

```java
OilTerminalTank cavern = new OilTerminalTank(
    "Cavern A",
    500000.0,  // capacity, kg
    20000.0,   // heel, kg
    100.0,     // maximum receipt, kg/s
    100.0,     // maximum withdrawal, kg/s
    OilTerminalTank.MixingMode.PERFECT_MIXED);

cavern.addOpeningInventory(new CrudeParcel(
    "opening-A", 150000.0, assay, -1,
    "opening inventory", "Synthetic"));
```

Every `CrudeParcel` records identity, mass, assay, entry period, route, and
provenance. Period results expose opening/closing inventories and component
mass for audit.

## Cargo scheduling

`OilNetworkSchedule` uses explicit periods. It receives parcels into tanks and
loads nominated cargoes subject to preferred tanks, loading windows, berth
occupancy, loading rate, tank availability, heel, and quality.

```java
OilTerminalNode terminal = new OilTerminalNode("Synthetic terminal");
terminal.addTank(cavern);

OilNetworkSchedule schedule = new OilNetworkSchedule(terminal);
schedule.addHourlyPeriods("2026-01-01T00:00:00Z", 24);
schedule.addReceipt("Cavern A", receiptParcel);
schedule.addCargoNomination(new CargoNomination(
    "cargo-1", 50000.0, 4, 8, "berth-1", 50.0,
    cargoQualityProfile, Arrays.asList("Cavern A")));
schedule.setTankAvailability("Cavern A", 12, 16, false);
schedule.setHydraulicNetwork(oilPipelineNetwork);

OilNetworkScheduleResult result = schedule.optimize();
```

When a hydraulic network is attached, each period also checks network
convergence, configured edge constraints, pump power, pump minimum flow, and
NPSH residuals.

The v1 scheduler is deterministic and feasible-first. It is not a
mixed-integer global optimizer and does not model plug-flow pipeline batch
interfaces or transmix. Those can be added above the existing parcel object
without placing storage dynamics inside the steady hydraulic Jacobian.

## Oil quality profiles

Typed oil metrics include TVP, RVP/VPCR4, density, API gravity, dynamic and
kinematic viscosity, and bubble point. Sulfur, TAN, water/BS&W, sediment, salt,
H2S, and other assay-backed properties use governed measured attributes.

```java
NetworkQualityProfile cargoProfile =
    new NetworkQualityProfile("Synthetic cargo limits")
        .withProvenance(
            "Educational values; not operator criteria");
cargoProfile.addRange(
    OilQualityMetric.API_GRAVITY, 25.0, 45.0, "degAPI",
    QualityReference.atTemperature(15.0, "C"));
cargoProfile.addUpperLimit(
    OilQualityMetric.TRUE_VAPOR_PRESSURE, 1.2, "bara",
    QualityReference.atTemperature(37.8, "C"));
cargoProfile.addMeasuredAttributeLimit(
    "oil", "sulfurMassPercent", null, 1.0,
    "mass%", "Synthetic assay");
```

Use the same profile/report API for a pipeline handover, intermediate blend
tank, or delivered cargo. Missing assay data produce `NOT_CALCULABLE`, never an
implicit pass.

## Conservation and diagnostics

Accept a schedule only after checking:

```java
if (!result.isFeasible()
    || Math.abs(result.getMassBalanceResidualKg()) > 1.0e-6
    || result.getMaxComponentBalanceResidualKg() > 1.0e-6) {
  throw new IllegalStateException(
      result.getActiveConstraints().toString());
}
```

The result records period movements, loaded parcels, tank inventories, cargo
quality, total/component closure, active constraints, and a stable JSON
representation. Thermodynamic assay fluids are transient in result JSON;
reattach governed assay inputs before attempting to rerun a deserialized
schedule.

## Public context and limitations

- [Equinor: public crude oil assays](https://www.equinor.com/energy/crude-oil-assays)
- [Norsk Petroleum: the oil and gas pipeline system](https://www.norskpetroleum.no/en/production-and-exports/the-oil-and-gas-pipeline-system/)
- [Equinor: Sture and Kollsnes public context](https://www.equinor.com/energy/oygarden)
- [Equinor: Mongstad public context](https://www.equinor.com/energy/mongstad)

Public assay sheets are representative source material, not commercial
acceptance specifications. Use approved current assays, contracts, operating
envelopes, pump curves, tank data, and facility constraints for asset work.
