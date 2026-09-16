# Immutable S8 transfer batch

This page extends the [aqueous H2S/O2 kinetics guide](h2s_oxygen_kinetics.md) with a
software-accounting boundary. It does not add a kinetic model or qualify an elemental-sulfur
product yield.

## Purpose

`AqueousHydrogenSulfideOxidationS8TransferBatch.create(...)` groups a non-empty ordered list of
existing `AqueousHydrogenSulfideOxidationS8Transfer.Result` receipts into one immutable batch.
The batch:

- preserves the source order and every receipt;
- requires a single caller-supplied S8 product-identity basis;
- rejects duplicate downstream idempotency keys inside the batch;
- reports total source sulfur-equivalent mass, transferred S8 mass, unallocated
  sulfur-equivalent mass, and an aggregate closure residual in kg; and
- returns an unmodifiable defensive copy.

The batch deliberately does not sum segment mass rates. Rates from sequential exposure segments
are not additive inventories.

## Accounting equation

For ordered receipts (i=1,ldots,n), the mass-only accounting is

[
m_{S,mathrm{source}}^{mathrm{batch}} =
  sum_i m_{S,mathrm{source},i},
qquad
m_{S8,mathrm{transfer}}^{mathrm{batch}} =
  sum_i m_{S8,mathrm{transfer},i},
]

[
m_{S,mathrm{unallocated}}^{mathrm{batch}} =
  sum_i m_{S,mathrm{unallocated},i},
qquad
epsilon_m =
  m_{S,mathrm{source}}^{mathrm{batch}}
  - left(
      m_{S8,mathrm{transfer}}^{mathrm{batch}}
      + m_{S,mathrm{unallocated}}^{mathrm{batch}}
    ight).
]

All masses use kg. The implementation requires finite non-negative receipt masses, finite sums,
and aggregate closure within eight floating-point units in the last place at the total-mass scale.
An unchanged-state segment subdivision preserves the total transferred mass.

## Java and JPype use

```java
List<AqueousHydrogenSulfideOxidationS8Transfer.Result> receipts = ...;
AqueousHydrogenSulfideOxidationS8TransferBatch.Result batch =
    AqueousHydrogenSulfideOxidationS8TransferBatch.create(
        receipts, "transport-case-A-S8-batch");
double transferredKg = batch.getTotalTransferredS8MassKg();
double residualKg = batch.getMassClosureResidualKg();
```

The public Java API is directly accessible through JPype. The caller must provide a trimmed,
non-empty batch identifier no longer than 256 characters.

## Idempotency and double-counting boundary

The batch rejects duplicate `getDownstreamIdempotencyKey()` values among its own receipts. This
is a bounded duplicate-consumption guard, not a persistent exactly-once service. A downstream
persistent ledger must still enforce the keys across batches, retries, process restarts, and
distributed consumers.

A consumer may apply each transferred S8 mass at most once. It must carry each unallocated
sulfur-equivalent remainder separately and must not also apply the original source sulfur budget
as product.

## Scientific evidence and limits

Every input receipt retains the explicit lower-rate, nominal, or upper-rate path derived from
Millero et al. (1987), <https://doi.org/10.1021/es00159a003>, and the caller-defined
elemental-sulfur allocation and product-identity basis. Batching introduces no chemical
coefficient.

The result does not:

- calculate S8 moles or introduce an S8 molecular-weight constant;
- qualify elemental sulfur as the oxidation product;
- predict product selectivity, oxygen consumption, reaction heat, pH, or speciation;
- calculate water holdup, pressure effects, phase transfer, saturation, nucleation, or deposition;
- add `S8` to a thermodynamic system or stream;
- execute `TPSolidflash`, `SulfurDepositionAnalyser`, or `SulfurFilter`; or
- mutate a reactor, wall inventory, process, transient model, or pipeline.

Those actions require separately qualified product evidence and remain owned by the existing
thermodynamic, solid-flash, deposition, filter, corrosion, process, and pipeline implementations.
Coordination boundaries remain issues #3144, #2937, #2911, pipeline work, and #3153.
