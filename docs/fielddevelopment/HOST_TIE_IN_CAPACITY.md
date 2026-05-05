---
title: Host Tie-In Capacity and Holdback Planning
description: Time-series host tie-in capacity planning for brownfield tiebacks, including base-host production, satellite holdback, process equipment bottlenecks, deferred value, and debottleneck decisions.
---

# Host Tie-In Capacity and Holdback Planning

Brownfield tieback decisions often fail because a host has spare capacity on paper but not in the year when the satellite peaks. The host may already use most separator, compression, liquid-handling, produced-water, export, or power capacity. The host tie-in capacity planner turns that question into a repeatable time-series calculation:

1. Compare base-host production and satellite production against host nameplate capacities.
2. Allocate constrained capacity with a clear policy.
3. Optionally inject accepted production into an attached `ProcessSystem` and check equipment capacity constraints.
4. Quantify held-back or deferred production and screen a debottleneck investment.

Use this for early DG0-DG2 screening when the question is not only whether a route is hydraulically feasible, but whether the host can process the combined production without unacceptable holdback.

## Main API

| Class | Role |
|-------|------|
| `ProductionLoad` | Immutable load for one period: gas, oil, water, total liquid, period length, and optional commodity values |
| `ProductionProfileSeries` | Ordered base-host or satellite production time series |
| `CapacityAllocationPolicy` | Allocation rule: `BASE_FIRST`, `SATELLITE_FIRST`, `PRO_RATA`, or `VALUE_WEIGHTED` |
| `HoldbackPolicy` | Holdback rule: curtail constrained production or defer it to later periods |
| `HostTieInPoint` | Maps profile rates to a stream flow in an attached `ProcessSystem` |
| `TieInCapacityPlanner` | Runs nameplate, process-capacity, holdback, and debottleneck calculations |
| `TieInCapacityResult` | Aggregated result with period tables, totals, bottleneck summary, and decisions |

The planner complements [TiebackAnalyzer](API_GUIDE.md#7-tieback-analysis): use `TiebackAnalyzer` for route feasibility and use `TieInCapacityPlanner` to test whether the host can absorb the combined production profile.

## Nameplate Ullage Example

This example preserves base-host production first and accepts only the satellite rate that fits within the host gas capacity.

```java
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityPlanner;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityResult;

HostFacility host = HostFacility.builder("Host A")
    .gasCapacity(5.0)
    .build();

ProductionProfileSeries base = new ProductionProfileSeries("base")
    .addPeriod(2028, 4.0, 0.0, 0.0, 0.0);

ProductionProfileSeries satellite = new ProductionProfileSeries("satellite")
    .addPeriod(2028, 3.0, 0.0, 0.0, 0.0);

TieInCapacityResult result = new TieInCapacityPlanner(host)
    .setHostProductionProfile(base)
    .setSatelliteProductionProfile(satellite)
    .setAllocationPolicy(CapacityAllocationPolicy.BASE_FIRST)
    .run();

System.out.println(result.toMarkdownTable());
```

Expected behavior: the host accepts 1.0 MSm3/d of the satellite gas and holds back 2.0 MSm3/d because the base host production already uses 4.0 of 5.0 MSm3/d.

## Process-Equipment Capacity Layer

If the `HostFacility` has an attached `ProcessSystem`, the planner can set a configured stream rate, run the process, and inspect equipment capacity constraints through the existing NeqSim capacity API.

```java
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.HostTieInPoint;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityPlanner;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityResult;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(288.15, 60.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");

final Stream hostFeed = new Stream("Host Feed", gas);
hostFeed.setFlowRate(1000.0, "kg/hr");
hostFeed.addCapacityConstraint(new CapacityConstraint("hostFeedFlow", "kg/hr", ConstraintType.HARD)
    .setDesignValue(2500.0)
    .setValueSupplier(() -> hostFeed.getFlowRate("kg/hr")));

ProcessSystem hostProcess = new ProcessSystem("host process");
hostProcess.add(hostFeed);

HostFacility host = HostFacility.builder("Example Host")
    .gasCapacity(10.0)
    .processSystem(hostProcess)
    .build();

ProductionProfileSeries base = new ProductionProfileSeries("base host")
    .addPeriod(2028, 1.0, 0.0, 0.0, 0.0);
ProductionProfileSeries satellite = new ProductionProfileSeries("satellite")
    .addPeriod(2028, 4.0, 0.0, 0.0, 0.0);

HostTieInPoint tieInPoint = new HostTieInPoint("Host Feed", "kg/hr")
    .setGasToProcessRateFactor(1000.0);

TieInCapacityResult result = new TieInCapacityPlanner(host)
    .setHostProductionProfile(base)
    .setSatelliteProductionProfile(satellite)
    .setAllocationPolicy(CapacityAllocationPolicy.BASE_FIRST)
    .setTieInPoint(tieInPoint)
    .run();

System.out.println(result.getPeriodResults().get(0).getProcessBottleneck());
```

The example maps 1.0 MSm3/d gas to 1000 kg/hr. The host stream hard limit is 2500 kg/hr, so the planner accepts about 1.5 MSm3/d of the 4.0 MSm3/d satellite request after considering the base load.

## Allocation Policies

| Policy | Use when | Behavior |
|--------|----------|----------|
| `BASE_FIRST` | Existing host production has priority | Allocates capacity to base production first, then satellite production |
| `SATELLITE_FIRST` | Satellite acceleration is strategically preferred | Allocates capacity to satellite production first |
| `PRO_RATA` | Commercial sharing or joint-venture fairness is needed | Scales base and satellite production by the same feasible factor |
| `VALUE_WEIGHTED` | Highest-value production should pass first | Prioritizes the load with highest daily value |

## Holdback and Debottlenecking

With `HoldbackPolicy.CURTAIL`, unaccepted satellite production is treated as lost in the screening case. With `HoldbackPolicy.DEFER_TO_LATER_YEARS`, the held-back production is carried into the next period and re-tested against future ullage.

The result also builds a simple `DebottleneckDecision` when constrained production has material value. It reports:

- bottleneck category or equipment name,
- default or configured CAPEX in MUSD,
- discounted recovered value in MUSD,
- NPV after debottleneck CAPEX,
- simple payback.

For detailed equipment-specific investments, use the bottleneck name to route the case to compressor, separator, heat-exchanger, water-treatment, or export-system design classes.

## Interpreting Results

Useful accessors include:

```java
result.hasHoldback();
result.getTotalAcceptedGasMSm3();
result.getTotalHeldBackGasMSm3();
result.getTotalDeferredValueNpvMusd();
result.getPrimaryBottleneck();
result.getDebottleneckDecisions();
result.toCsv();
result.toMarkdownTable();
```

Each `TieInPeriodResult` contains the scheduled satellite load, deferred backlog entering the year, accepted satellite production, held-back production, primary bottleneck, process utilization summary, and deferred value.

## Related Examples

- [Field Development Decision Engine](../../examples/notebooks/field_development_decision_engine.ipynb)
- [Field Development Process and Reservoir Coupling](../../examples/notebooks/field_development_process_reservoir_coupling.ipynb)
- [Host Tie-In Capacity and Holdback Notebook](../../examples/notebooks/host_tie_in_capacity_and_holdback.ipynb)