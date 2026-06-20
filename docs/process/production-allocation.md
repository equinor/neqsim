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
