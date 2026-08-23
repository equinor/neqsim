# MCP Phase 0 acceptance fixtures

Campaign #3153 uses four public synthetic fixture scales to keep MCP acceptance evidence reproducible without relying on proprietary facility data. The authoritative machine-readable definitions are built by `McpAcceptanceFixtureCatalog` and use the same input contracts as normal `runFlash` and `runProcess` calls.

These fixtures freeze inputs and acceptance questions. They do **not** by themselves claim scientific validation, performance qualification, design certification, plant authority, or campaign completion. Exact-head execution evidence and per-result provenance remain authoritative.

| Fixture | Scale | Canonical route | Size | Primary acceptance purpose |
| --- | --- | --- | ---: | --- |
| `single-calculation` | single calculation | `runFlash` | one flash | standard envelope, finite result, provenance and validation |
| `small-recycle-train` | small train | `runProcess` / `ProcessSystem` | 10 units, 1 recycle | process composition, recycle execution, canonical replay, deterministic repeat |
| `multi-area-facility` | multi-area | `runProcess` / `ProcessModel` | 3 areas, 8 units | area construction, model execution, area ordering, convergence summary and replay |
| `large-recycle-facility` | large facility | `runProcess` / `ProcessSystem` | 154 units, 1 recycle | 150+ unit execution, recycle convergence, replay, payload and selective-result baselining |

## Fixture design

### Single calculation

The fixture reuses `ExampleCatalog.flashTPSimpleGas()`: a public SRK/classic natural-gas TP flash at explicitly declared temperature and pressure. Its purpose is to freeze the smallest MCP calculation contract, not to establish universal EOS accuracy.

### Small recycle train

The fixture reuses `ExampleCatalog.processMixerSplitterRecycle()`. It exercises an ordinary canonical `ProcessSystem` with stream, mixer, splitter, cooler, valve, separator, compressor and recycle equipment. It deliberately uses supported public JSON-builder semantics rather than a parallel MCP-only model.

### Multi-area facility

The fixture builds three named areas (`inlet-separation`, `gas-compression`, and `export-conditioning`) from existing `ExampleCatalog` process definitions and executes them through the top-level `areas` contract handled by `ProcessRunner` and `ProcessModel`.

This Phase 0 scale establishes multi-area construction and result delivery. It does not claim plant-wide pressure/flow coupling, optimization fidelity or dynamic qualification. Those concerns remain with their owner roadmaps.

### Large recycle facility

The large fixture is generated deterministically from public synthetic inputs. It contains a feed, forward-referenced recycle mixer, 75 heater/cooler pairs, an export splitter and a two-percent recycle, for 154 units total. The repeated conditioning pairs are intentional: they make the MCP payload and process execution large while avoiding proprietary plant topology and avoiding new specialist physics or optimization constraints.

The large fixture is the #3153 transport/execution scale. Generic process execution performance remains owned by #2939; plant-wide optimization and constraint fidelity remain owned by #3154. Results from this fixture must not be used to claim either roadmap complete.

## Acceptance evidence

`McpAcceptanceFixtureCatalogTests` executes all four scales through the production runners. It verifies:

- the catalog contains exactly the four required scales and identifies every fixture as public synthetic;
- the single calculation executes through `FlashRunner` with the standard provenance/validation envelope;
- the small recycle train executes through `ProcessRunner` and returns a canonical replayable `processDefinition`;
- the multi-area fixture executes as a three-area `ProcessModel` and returns convergence and replay evidence;
- the large fixture contains exactly 154 units, validates before execution, executes with a recycle, returns the canonical 154-unit definition and reports converged provenance.

Hosted exact-head CI is the execution authority when local validation is unavailable. A source fixture existing in the repository is not evidence that it passed on a particular head.

## Phase 0 measurement harness

`McpAcceptanceBaselineRunner` now runs these frozen inputs twice through the production runners and records:

- runtime and environment identity;
- memory/allocation evidence where available;
- response size before and after MCP response guarding;
- tool-call count and selective-retrieval behavior;
- convergence and warnings;
- explicit mass/component/energy closure response paths, or a confirmed response-evidence gap;
- deterministic repeat evidence;
- structural report coverage and explicit usefulness limitations.

See [ACCEPTANCE_BASELINES.md](ACCEPTANCE_BASELINES.md) for the executable contract and interpretation boundaries.
Exact-head values remain environment-specific and are retained in hosted CI logs rather than embedded as portable
performance claims. The full campaign traceability matrix, discipline-level capability/maturity matrix, and justified
closure of explicit numerical balance/report gaps remain open.
