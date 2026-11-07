---
title: "Integrated compression and power supply"
description: "Match common-shaft compression to direct gas turbine, external electric or turbine-generator-motor drive, including losses, limits, fuel and feasibility."
---

# Integrated compression and power supply

`CompressorPowerTrain` matches an existing `CompressorShaft` to its power supply.
The compressors retain their normal NeqSim fluid, charts and stream connections.
Their calculated shaft power drives the supply calculation. A supply shortfall
does not silently change the calculated discharge pressure: reduce throughput or
re-solve the common speed before accepting that process point.

| Arrangement | Matching chain | Principal outputs |
| --- | --- | --- |
| Direct turbine | Fuel → turbine → gearbox → compressor bodies | Fuel LHV energy, fuel mass, CO₂, available shaft power and unmet duty |
| External electric | Electrical supply → motor/VFD → gearbox → bodies | Electrical input, losses, motor speed capability and unmet duty |
| Gas-electric | Fuel → turbine → generator → auxiliaries and motor/VFD → gearbox → bodies | Turbine fuel/CO₂, generator losses, motor electrical input, auxiliary consumption and unmet duty |

In the gas-electric arrangement the generator and compressor have **separate
shafts**. Compressor rpm selects the motor/VFD operating point. It is not imposed
on the generator turbine.

## Model basis and units

The adapter uses `GasTurbineUnit` site rating, ambient pressure, degradation and
exhaust-temperature derating, `ElectricMotor` inverse conversion and its optional
`ElectricMotorDriver`, and `Generator` inverse conversion with an optional
`LoadEfficiencyCurve`. Motor environmental derating is configured on its
performance model in degrees Celsius and altitude in metres; turbine ambient
temperature is in K. The motor/VFD model already includes drive losses: do not
apply a second generic VFD efficiency to the same stage.

All power-train powers are positive **W**, mass rates **kg/s**, speed **rpm** and
absolute temperature **K**. Gearbox/coupling efficiency belongs to the shaft:

$$P_{driver,required}=\frac{\sum_i P_{compressor,i}}{\eta_{gearbox}}$$

For an electric train with electrical auxiliaries, motor efficiency and idle loss:

$$P_{electric}=\frac{P_{driver}}{\eta_{motor}}+P_{motor,idle}+P_{auxiliary}$$

For a gas-electric train, the generator inverse model gives the turbine duty:

$$P_{turbine}=\frac{P_{electric}}{\eta_{generator}}+P_{generator,idle}$$

The efficiencies may depend on load. Capability is obtained by bounded bisection
through the inverse conversion chain, including each input/output limit. The
configured inverse-input curves must be non-decreasing with useful output;
arbitrary nonphysical efficiency curves are not qualified by this solver.

Electrical auxiliaries are included only while positive mechanical power is
delivered. Standby loads should be separate electrical-bus consumers. Setting
`setElectricalSupplyLimitW(0.0)` represents a blackout; positive infinity removes
that limit. This differs from the legacy shaft `setElectricSupplyLimit` API,
where zero clears its approximate constant-efficiency supply cap.

## Executable example

This complete Java program is compiled and executed from this exact guide by
`IntegratedCompressionPowerDocumentationTest`. The chart and turbine rating are
synthetic test inputs, not an OEM selection. Run with assertions enabled (`java -ea`).

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorShaft;
import neqsim.process.equipment.energy.CompressorPowerTrain;
import neqsim.process.equipment.energy.ElectricMotor;
import neqsim.process.equipment.energy.Generator;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineSpec;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class IntegratedCompressionPowerExample {
  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(20.0);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.run();
    compressor.generateCompressorChart("normal curves", 5);

    Stream fuel = new Stream("fuel composition", fluid.clone());
    fuel.setFlowRate(1.0, "kg/sec");
    fuel.run();
    GasTurbineSpec spec = new GasTurbineSpec("synthetic", GasTurbineSpec.TurbineType.AERODERIVATIVE,
        2.0e6, 9000.0, 10.0, 750.0, 15.0, 5.0, "Synthetic matching example");
    GasTurbineUnit turbine = new GasTurbineUnit("turbine", fuel, spec);
    turbine.getPerformanceMap().setPartLoadHeatRateTable(
        new double[] {0.30, 0.50, 0.75, 1.0}, new double[] {1.45, 1.18, 1.05, 1.0});

    CompressorShaft shaft = new CompressorShaft("compression train");
    shaft.addCompressor(compressor);
    shaft.setMechanicalEfficiency(0.98);
    double speedRpm = compressor.getSpeed();

    shaft.setPowerTrain(CompressorPowerTrain.directDrive(turbine));
    shaft.runAtFixedSpeed(speedRpm, null);
    CompressorPowerTrain.Result direct = shaft.getPowerTrainResult();
    assert direct.isPowerFeasible();
    assert direct.getFuelMassKgPerS() > 0.0;

    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    CompressorPowerTrain electric = CompressorPowerTrain.electricDrive(motor);
    electric.setElectricalAuxiliariesW(10000.0);
    electric.setElectricalSupplyLimitW(3.0e6);
    shaft.setPowerTrain(electric);
    shaft.runAtFixedSpeed(speedRpm, null);
    CompressorPowerTrain.Result grid = shaft.getPowerTrainResult();
    double expectedInputW = shaft.getRequiredDriverPower("W") / 0.95 + 10000.0;
    assert Math.abs(grid.getElectricalInputW() - expectedInputW) < 1.0e-5;
    assert grid.isFeasible();

    Generator generator = new Generator("generator", 0.97);
    generator.setRatedOutputPower(1.9e6);
    shaft.setPowerTrain(CompressorPowerTrain.gasElectricDrive(turbine, generator, motor));
    shaft.runAtFixedSpeed(speedRpm, null);
    CompressorPowerTrain.Result gasElectric = shaft.getPowerTrainResult();
    assert gasElectric.isPowerFeasible();
    assert Math.abs(gasElectric.getTurbineShaftW()
        - gasElectric.getElectricalInputW() / 0.97) < 1.0e-5;

    electric.setElectricalSupplyLimitW(0.0);
    shaft.setPowerTrain(electric);
    shaft.runAtFixedSpeed(speedRpm, null);
    assert !shaft.getOperatingReport().isFeasible();
    assert shaft.getPowerTrainResult().getDeliveredPowerW() == 0.0;
    assert shaft.getPowerTrainResult().getPowerShortfallW() > 0.0;
  }
}
```

For variable-speed operation, configure real compressor maps, member gear ratios
and speed bounds, then use the shaft's existing `solveSpeed` or
`DriverLimitedThroughputSolver` with a surrounding-flowsheet callback. The
integrated supply limits enter the shaft power checks. Generated charts are
synthetic models and require the usual map-range, surge and stonewall checks.

## Results and acceptance

`shaft.getPowerTrainResult()` returns the latest immutable supply snapshot;
`shaft.getOperatingReport().getPowerTrain()` includes it in the normal shaft
report, including JSON. `isPowerFeasible()` tests demand against available supply.
`isFeasible()` also rejects a running turbine below its configured minimum load.
Neither flag establishes compressor chart validity or safety-system qualification.

The snapshot distinguishes requested and delivered mechanical power, available
power, shortfall, electrical input including auxiliaries, turbine shaft power,
conversion losses, fuel energy/mass and fuel-derived CO₂. Conversion loss includes
generator, motor and electrical auxiliaries, and excludes turbine fuel conversion
and the downstream gearbox. The residual checks that supply-side shaft/electrical
power equals delivered driver power plus these losses. Fuel heat input is reported
separately; recoverable exhaust heat needs its own model and balance.

A fixed-speed shaft run refreshes driver accounting automatically. An idle shaft
clears fuel demand. A configured driver that supplies zero power reports an
overload for positive compressor duty; zero capability is never accepted as an
unlimited driver. An explicit installed shaft cap also limits actual supply
matching. A saturated `GasTurbineUnit` retains requested-load overload diagnostics
but evaluates fuel heat rate and exhaust conditions at actual delivered load.
`CompressorShaftCalculator` also refreshes supply accounting during process
iteration and flags `DRIVER_BELOW_MINIMUM_LOAD` when applicable. A pressure target
reached exactly at a speed bound still undergoes driver-power checks.

A loaded `GasTurbineUnit` requires a positive finite ISO 6976 fuel heating value.
It throws for a missing or noncombustible fuel instead of substituting 50 MJ/kg.
This is an intentional input-validation change; a nitrogen-only stream cannot
be used as a surrogate fuel for a loaded turbine.

## Supplied turbine data

`GasTurbinePerformanceMap.setPartLoadHeatRateTable(loadFractions, factors)` replaces
the default polynomial. Load is relative to **site-available shaft power**, and
heat-rate factors are relative to full-load heat rate at that ambient condition.
The table must cover minimum stable load and end at load 1 with factor 1. Data
must be finite, load strictly increasing, factors positive. Interpolation is linear
and queries beyond the table are endpoint-clamped; a below-minimum-load result
remains infeasible. Arrays are copied. Use `clearPartLoadHeatRateTable()` to return
to the polynomial. Ambient heat-rate corrections remain separate, so do not apply
an ambient correction twice to an already site-corrected table.

## Engineering limits and qualification roadmap

This delivery qualifies the steady-state **matching equations** through analytical
and real-fluid software tests. Catalogue ratings, generic part-load/exhaust
curves and default motor/VFD loss assumptions remain screening data. The adapter
does not add turbine power-speed surfaces, corrected-flow component matching,
combustor chemistry, humidity/inlet/exhaust-loss corrections, governor dynamics,
rotor inertia, torsional analysis, AC power flow or electrical protection studies.

`GasTurbineUnit`'s outlet represents consumed fuel, not a material exhaust stream.
For a closed process mass balance, extract the calculated fuel with a real process
splitter and converge that flow with the process model. Do not connect the fuel
outlet to an HRSG as if it were exhaust. The adapter calculates supply conversion
requirements without changing motor/generator port allocation. Use the existing
typed energy buses and `CoupledProcessEnergySolver` for shared generation,
multiple trains, electrical dispatch and heat recovery. Use each equipment object
in one owned supply train; sharing one mutable turbine between independent trains
would overwrite its demand.

Connections retain live object references. `setPowerTrain` attaches the supplied
train, and `getTurbine` returns its connected turbine. Later blackout, trip and
derating changes affect subsequent shaft calculations. Serialize the complete
connected graph together to preserve those identities. Coordinate configuration
and calculation on one thread; the mutable train and equipment are not thread-safe.

Follow [roadmap #4909](https://github.com/equinor/neqsim/issues/4909) for OEM maps,
independent package benchmarks, dynamic/control qualification and multi-train
integration. The engineering basis includes:

- [ASME PTC 22](https://www.asme.org/codes-standards/find-codes-standards/gas-turbines):
  corrected power, heat rate, exhaust metrics and measurement uncertainty.
- [NASA T-MATS](https://ntrs.nasa.gov/citations/20150002325) and
  [NPSS model conversion](https://ntrs.nasa.gov/citations/20150002326): component
  maps, model matching and rotor/actuator dynamics.
- [DOE adjustable-speed drive efficiency](https://www.energy.gov/sites/default/files/2014/04/f15/motor_tip_sheet11.pdf):
  load-dependent motor/drive losses and chain efficiency.

These references guide the model requirements; this implementation is not a
claim of performance-test-code conformity or an OEM guarantee.

## Related guides

- [Common compressor shaft](compressor_shaft.md)
- [Power generation](power_generation.md)
- [Energy conversion and networks](energy_conversion.md)
