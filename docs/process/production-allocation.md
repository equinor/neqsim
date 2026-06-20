---
title: Linear Recovery-Factor Production Allocation
description: Allocate metered production back to wells, templates, and commingled sources using a fast linear proxy network of frozen per-unit, per-component split factors. Works for any conservative oil and gas process, including recycle and reflux loops.
---

# Linear Recovery-Factor Production Allocation

The `neqsim.process.allocation` package allocates the metered production of a
process flowsheet back to its individual **sources** (wells, templates,
commingled feeds) using frozen per-unit, per-component **split factors** and a
**linear proxy network**.

It is a fast, scalable alternative to per-source component tagging: one rigorous
base-case simulation with a single common component slate is enough, after which
any number of sources can be allocated by **superposition** at a cost that scales
with components × units, not with the number of sources.

## When to use it

- Commingled production from multiple wells/templates into shared facilities.
- Custody/fiscal allocation of export gas, export oil/condensate and produced
  water back to the contributing reservoirs.
- Back-allocation studies, ownership/equity splits, and what-if production
  re-allocation without re-running per-source tagged simulations.

## Method

Every non-stream unit that has outlet streams becomes a network **node**;
streams are the **edges**. From the base case each node fixes a split factor

$$
f_u(s,k) = \frac{\dot n_{k}\,\text{leaving outlet } s}{\dot n_{k}\,\text{entering unit } u}
$$

Holding these factors frozen, the per-component node throughput vector
$\mathbf{v}$ contributed by a source obeys the linear balance

$$
(\mathbf{I} - \mathbf{A}_k)\,\mathbf{v} = \mathbf{b}
$$

where $\mathbf{A}_k$ is the routing matrix built from the split factors and
$\mathbf{b}$ injects the source feed at its entry node. Because every
conservative unit's split factors sum to one across its outlets, the spectral
radius of $\mathbf{A}_k$ is below one, so the system has a unique, convergent
solution — **recycle and reflux loops included**. The custody allocation is

$$
\text{alloc}(j,s,k) = f_w(s,k)\,v_w
$$

The solver uses a direct linear solve, with a Neumann-series (fixed-point)
fallback for ill-conditioned loops. Results can optionally be renormalised so
each custody outlet's per-component totals exactly match the measured base-case
values (mass closure).

## Scope and limitations

- Applies to any conservative separation / scrubber / column / valve / cooler /
  heater / mixer / splitter / pump / compressor network — all oil and gas field
  process configurations.
- Reactive or mass-transfer contacting units (amine, glycol, MEG, scavengers)
  are treated as black boxes that reproduce the base-case redistribution. For
  hydrocarbon allocation, water and MEG handling does not affect the result.
- The split factors are linearised around the base case; for very different
  operating points, re-extract the factors from a representative base case.

## Quick start

```java
import neqsim.process.allocation.ProductType;
import neqsim.process.allocation.ProductionAllocationResult;
import neqsim.process.allocation.SourceAllocator;

// process is an already-run ProcessSystem with commingled wells
SourceAllocator allocator = new SourceAllocator();
allocator.setBaseCase(process);

// Tag sources by their feed streams
allocator.addSource("Well-A", wellAFeed);
allocator.addSource("Well-B", wellBFeed);

// Tag custody outlets at the producing equipment's outlet streams
allocator.addCustodyOutlet("ExportGas", separator.getGasOutStream(), ProductType.GAS);
allocator.addCustodyOutlet("ExportOil", separator.getLiquidOutStream(), ProductType.OIL);

ProductionAllocationResult result = allocator.allocate();

double gasFromA = result.getProductAllocation("Well-A", ProductType.GAS, "kg/hr");
double oilFromB = result.getAllocatedFlow("Well-B", "ExportOil", "kg/hr");
String json = result.toJson();
```

If no sources or custody outlets are tagged, they are **auto-detected** from the
flowsheet topology: external feed streams become sources, terminal product
streams become custody outlets (with an inferred product type).

```java
SourceAllocator allocator = new SourceAllocator();
allocator.setBaseCase(process);
ProductionAllocationResult result = allocator.allocate(); // auto-detect
```

## Key classes

| Class | Responsibility |
|-------|----------------|
| `SourceAllocator` | High-level facade: tag sources/custody, auto-detect, run allocation. |
| `RecoveryFactorExtractor` | Builds the master component slate and frozen split factors from a solved base case. |
| `AllocationNetwork` | Assembles routing matrices and resolves source/custody streams. |
| `LinearAllocationSolver` | Solves `(I − A_k) v = b` per component (direct + Neumann fallback). |
| `ProductionAllocationResult` | Queryable result: unit conversion, product aggregation, mass closure, JSON export. |
| `ProductType` | Product category (GAS, OIL, WATER, MIXED, UNKNOWN). |

## Result queries

| Method | Returns |
|--------|---------|
| `getAllocatedFlow(source, custody, unit)` | Total flow from a source to a custody outlet. |
| `getAllocatedComponentFlow(source, custody, component, unit)` | Single-component flow. |
| `getProductAllocation(source, ProductType, unit)` | Aggregated allocation across all custody outlets of a product type. |
| `getSourceTotal(source, unit)` / `getCustodyTotal(custody, unit)` | Roll-up totals. |
| `getMaxResidual()` | Largest per-component solver residual (closure quality). |
| `toJson()` | Schema-versioned JSON report with allocations, totals, product summaries and solver diagnostics. |

## Concepts and terminology

| Term | Meaning |
|------|---------|
| **Source** | An external feed entering the process (a well, template, satellite tie-in or commingled feed). Allocation answers *"how much of each custody product came from this source?"* |
| **Custody outlet** | A terminal, metered product stream leaving the process (export gas, stabilised oil/condensate, produced water). These are the fiscal/custody transfer points whose totals are *measured*. |
| **Node** | Any non-stream unit operation that has at least one outlet stream (separator, scrubber, column, valve, cooler, heater, mixer, splitter, pump, compressor). Nodes are the vertices of the proxy network. |
| **Edge** | A `StreamInterface` connecting two nodes. Edges carry the per-component molar flow that the routing matrix redistributes. |
| **Split factor** `f_u(s,k)` | Fraction of component `k` entering node `u` that leaves through outlet `s`. Frozen from the base case; the "recovery factor" of the unit for that component. |
| **Master component slate** | The single common list of components used across the whole flowsheet. The extractor unions every fluid's components so all streams share one indexing. |
| **Mass closure** | Optional renormalisation so the allocated per-component totals at each custody outlet exactly equal the measured base-case totals. |

## Mathematical method (detailed)

### 1. Freeze split factors

For a converged base case, each node `u` with inlet component flow
$\dot n_k^{\text{in},u}$ and outlet `s` carrying $\dot n_k^{s}$ defines

$$
f_u(s,k) = \frac{\dot n_{k}^{s}}{\dot n_{k}^{\text{in},u}}, \qquad
\sum_{s \in \text{outlets}(u)} f_u(s,k) = 1
$$

for every conservative unit (no component generation/destruction). The factors
are dimensionless and lie in $[0,1]$.

### 2. Assemble the per-component routing matrix

Let there be `N` nodes. For component `k` define the routing matrix
$\mathbf{A}_k \in \mathbb{R}^{N \times N}$ whose entry

$$
A_k[u][w] = f_w(\text{edge } w \rightarrow u,\, k)
$$

is the fraction of component `k` leaving node `w` that flows directly into node
`u`. Each column `w` sums to (at most) one across its destinations.

### 3. Solve by superposition

A source `j` injects its feed at its **entry node** $e_j$. The per-component
node inlet vector $\mathbf{v}^{(j)}_k$ attributable to that source satisfies the
fixed-point balance $\mathbf{v} = \mathbf{b} + \mathbf{A}_k \mathbf{v}$, i.e.

$$
(\mathbf{I} - \mathbf{A}_k)\,\mathbf{v}^{(j)}_k = \mathbf{b}^{(j)}_k,
\qquad b^{(j)}_k[i] = \begin{cases} x_{j,k} & i = e_j \\ 0 & \text{otherwise}\end{cases}
$$

where $x_{j,k}$ is source `j`'s feed flow of component `k`. Because the spectral
radius $\rho(\mathbf{A}_k) < 1$ (physical loop gain below unity), $\mathbf{I} -
\mathbf{A}_k$ is invertible and the Neumann series
$\sum_{m\ge 0}\mathbf{A}_k^{m}$ converges — **so recycle and reflux loops are
handled exactly**. All sources are solved simultaneously as the multiple
right-hand sides of one LU factorization per component.

### 4. Map node flows to custody outlets

A custody outlet `s` produced by node `w` receives, from source `j`,

$$
\text{alloc}(j,s,k) = f_w(s,k)\; v^{(j)}_k[w]
$$

Summing over components and converting molar flow to the requested unit yields
the mass/standard-volume allocation.

### Why it scales

One base-case simulation fixes all split factors. Allocation cost is then
$\mathcal{O}(K \cdot N^3)$ for the factorizations plus $\mathcal{O}(K \cdot N^2
\cdot S)$ for `S` sources — **independent of how many tagged tracer simulations
would otherwise be needed**. Adding a source is one extra right-hand side, not a
new simulation.

## Worked example

Two wells commingle, flash in one separator, and split into export gas and
export oil. With per-component split factors frozen from the base case, the
allocation is a direct superposition:

```java
import neqsim.process.allocation.ProductType;
import neqsim.process.allocation.ProductionAllocationResult;
import neqsim.process.allocation.SourceAllocator;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluidA = new SystemSrkEos(298.15, 60.0);
fluidA.addComponent("methane", 80.0);
fluidA.addComponent("nC10", 20.0);
fluidA.setMixingRule("classic");

SystemSrkEos fluidB = new SystemSrkEos(298.15, 60.0);
fluidB.addComponent("methane", 50.0);
fluidB.addComponent("nC10", 50.0);
fluidB.setMixingRule("classic");

Stream wellA = new Stream("Well-A", fluidA);
wellA.setFlowRate(100.0, "kg/hr");
Stream wellB = new Stream("Well-B", fluidB);
wellB.setFlowRate(100.0, "kg/hr");

Mixer commingle = new Mixer("Commingle");
commingle.addStream(wellA);
commingle.addStream(wellB);

Separator sep = new Separator("Inlet Separator", commingle.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(wellA);
process.add(wellB);
process.add(commingle);
process.add(sep);
process.run();

SourceAllocator allocator = new SourceAllocator();
allocator.setBaseCase(process);
allocator.addSource("Well-A", wellA);
allocator.addSource("Well-B", wellB);
allocator.addCustodyOutlet("ExportGas", sep.getGasOutStream(), ProductType.GAS);
allocator.addCustodyOutlet("ExportOil", sep.getLiquidOutStream(), ProductType.OIL);

ProductionAllocationResult result = allocator.allocate();

// Custody totals close to the measured base case:
double gasTotal = result.getCustodyTotal("ExportGas", "kg/hr");
double oilTotal = result.getCustodyTotal("ExportOil", "kg/hr");

// Per-source contributions:
double gasFromA = result.getProductAllocation("Well-A", ProductType.GAS, "kg/hr");
double gasFromB = result.getProductAllocation("Well-B", ProductType.GAS, "kg/hr");

// Conservation check: each source total equals its feed:
double sourceTotalA = result.getSourceTotal("Well-A", "kg/hr"); // ≈ 100 kg/hr
```

The methane-richer **Well-A** is allocated a larger share of the export gas,
while the heavier **Well-B** dominates the export oil — and each source total
sums back to its 100 kg/hr feed (conservation).

## API reference

### `SourceAllocator`

| Method | Purpose |
|--------|---------|
| `setBaseCase(ProcessSystem)` | Sets and extracts split factors from an already-run base case. **Call first.** Fluent. |
| `addSource(String, StreamInterface)` / `addSource(AllocationSource)` | Tag a production source. |
| `addCustodyOutlet(String, StreamInterface, ProductType)` / `addCustodyOutlet(CustodyOutlet)` | Tag a metered product outlet. |
| `setEnforceMassClosure(boolean)` | Toggle renormalisation to measured custody totals (default `true`). Fluent. |
| `allocate()` | Run the allocation and return a `ProductionAllocationResult`. Auto-detects sources/custody if none were tagged. |
| `getExtractor()` / `getNetwork()` / `getSolver()` | Access the underlying components (e.g. to tune solver tolerances). |

### `RecoveryFactorExtractor`

| Method | Purpose |
|--------|---------|
| `RecoveryFactorExtractor(ProcessSystem)` | Construct around a base-case process. |
| `extract()` | Build the master slate and frozen split factors. Fluent; returns `this`. |
| `getComponentNames()` | Unmodifiable master component slate (shared indexing). |
| `getMolarMass(String)` | Molar mass of a component (kg/mol). |
| `getUnitSplits()` | Unmodifiable map of unit name → `UnitSplit`. |
| `componentFlow(StreamInterface, String)` *(static)* | Component molar flow (mole/sec) of a named component in a stream, or `0`. |

### `LinearAllocationSolver`

Tunable solver for `(I − A_k) v = b`. Defaults are robust; tune only for
pathological loops.

| Setter | Default | Meaning |
|--------|---------|---------|
| `maxIterations` | `1000` | Cap on the Neumann fixed-point fallback iterations. |
| `tolerance` | `1.0e-10` | Relative Frobenius residual convergence target for the fallback. |
| `negativeClipTolerance` | `1.0e-9` | Magnitude below which a negative round-off node flow is clipped to zero. |

`solve(network, sourceEntryUnits, sourceInjections)` returns a `SolverResult`
exposing `getNodeFlow(componentIndex, nodeIndex, sourceIndex)` and
`getDiagnostics()`.

## Solver diagnostics

Each component solve records a `ComponentDiagnostics` entry:

| Field | Meaning |
|-------|---------|
| `getComponentIndex()` | Index of the component on the master slate. |
| `getMethod()` | `"direct"` (LU) or `"iterative"` (Neumann fallback). |
| `getResidual()` | Relative Frobenius residual of the solution. |
| `getIterations()` | Fixed-point iterations used (`0` for direct solves). |

`ProductionAllocationResult.getMaxResidual()` returns the worst residual across
all components — a single quality indicator. A large residual indicates a loop
with near-unity gain or an inconsistent base case.

## Units

`getAllocatedFlow`, `getAllocatedComponentFlow`, `getProductAllocation`,
`getSourceTotal` and `getCustodyTotal` accept these unit strings:

`mole/sec`, `mol/sec`, `mole/hr`, `mol/hr`, `kmole/hr`, `kmol/hr`, `kg/sec`,
`kg/hr`, `kg/day`, `tonnes/year`.

Mass-based units use the per-component molar mass from the extractor. An
unsupported unit string raises `IllegalArgumentException`.

## JSON report schema

`ProductionAllocationResult.toJson()` returns a pretty-printed, schema-versioned
report (`schemaVersion = "1.0"`):

```json
{
  "schemaVersion": "1.0",
  "sources": ["Well-A", "Well-B"],
  "custodyOutlets": ["ExportGas", "ExportOil"],
  "allocations": [
    {
      "source": "Well-A",
      "totalMolePerSec": 0.0123,
      "totalKgPerHr": 100.0,
      "custody": {
        "ExportGas": { "molePerSec": 0.0101, "kgPerHr": 58.2, "productType": "GAS" },
        "ExportOil": { "molePerSec": 0.0022, "kgPerHr": 41.8, "productType": "OIL" }
      },
      "productKgPerHr": { "GAS": 58.2, "OIL": 41.8 }
    }
  ],
  "custodyTotals": {
    "ExportGas": { "molePerSec": 0.018, "kgPerHr": 95.0, "productType": "GAS" },
    "ExportOil": { "molePerSec": 0.012, "kgPerHr": 105.0, "productType": "OIL" }
  },
  "solverDiagnostics": [
    { "component": "methane", "method": "direct", "residual": 1.1e-15, "iterations": 0 },
    { "component": "nC10", "method": "direct", "residual": 9.4e-16, "iterations": 0 }
  ],
  "maxResidual": 1.1e-15
}
```

(The numbers above are illustrative.)

## Auto-detection

When no sources or custody outlets are tagged, `allocate()` infers them from
topology:

- **Sources** — terminal inlet streams not produced by any node
  (`AllocationNetwork.findSourceStreams()`). Unnamed streams get `Source-1`,
  `Source-2`, …
- **Custody outlets** — terminal outlet streams not consumed by any node
  (`AllocationNetwork.findCustodyStreams()`). The product type is inferred from
  the stream's dominant phase (gas / aqueous / liquid → `GAS` / `WATER` /
  `OIL`), defaulting to `UNKNOWN` if classification fails.

Explicit tagging is recommended for fiscal work so custody points map to the
correct metering streams and product labels.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| A source allocates **zero** | Its feed stream is not the canonical edge into a node, or it is downstream of a node. | Tag the source with the actual external feed `Stream` object. A warning is logged. |
| A custody outlet receives **zero** | Tagged with a downstream wrapper `Stream` whose object identity differs from the producing equipment's outlet (`findProducer` uses identity matching). | Tag custody at the producing equipment's outlet stream, e.g. `separator.getGasOutStream()`. |
| `InvalidInputException` on the base case | Component name not in the NeqSim database. | Use database names, e.g. `nC10` (not `n-decane`). |
| `Unknown source` / `Unknown custody outlet` / `Unknown component` | Query name not tagged / not on the master slate. | Use names exactly as tagged; check `getSourceNames()` / `getCustodyNames()` / `getComponentNames()`. |
| Large `maxResidual` | Loop gain close to one or inconsistent base case. | Re-extract from a fully converged base case; raise `maxIterations` if the iterative fallback was used. |

## Scope and limitations

- Applies to any conservative separation / scrubber / column / valve / cooler /
  heater / mixer / splitter / pump / compressor network — i.e. all oil and gas
  field process configurations, **including recycle and reflux loops**.
- Reactive or mass-transfer contacting units (amine, glycol, MEG, scavengers)
  are treated as black boxes reproducing the base-case redistribution. For
  hydrocarbon allocation, water and MEG handling does not affect the result.
- Split factors are linearised around the base case. For operating points far
  from the base case, re-extract from a representative converged run.

## Related documentation

- [Process Simulation Documentation](README.md)
- [ProcessSystem and flowsheet management](processmodel/)
- [Production optimization and bottleneck analysis](optimization/OPTIMIZATION_OVERVIEW.md)
