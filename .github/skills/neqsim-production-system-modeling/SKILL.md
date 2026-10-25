---
name: neqsim-production-system-modeling
description: "Build NeqSim models from natural language in Python or Java: reservoir, production/injection wells, SURF templates/manifolds/PLEMs, multiphase flowlines and risers, facilities, utilities and downstream processing. USE WHEN: building reservoir-to-products models, coupled deliverability/capacity and equivalent language implementations. Compose core/community specialists and optional enterprise overlays."
---

# Natural-language production-system modeling

Translate the requested system into one explicit topology, physics basis and
executable model in the user's chosen language. Reuse NeqSim Java models through
the Python bridge or directly from Java; do not create a second Python physics
implementation. Load [references/chain-contract.md](references/chain-contract.md)
for stage-specific inputs, APIs, specialist routes and evidence.

## 1. Translate the request into a model contract

Record the objective, requested Python/Java deliverable, steady/transient/time
horizon, production and injection paths, topology and fidelity at each stage.
Extract fluids, well count, routes, geometry, elevations, thermal environment,
facility/utility capacities, export/product requirements and controls. Distinguish
user data, measured/calibrated values, synthetic assumptions and unresolved gaps.
Ask only for missing decision-critical inputs; use labeled synthetic assumptions
when the request permits a demonstration. Do not invent plant or reservoir facts.

Represent every requested stage and interface in the plan. Keep the reservoir,
wells, SURF, flowlines, risers, process, utilities and downstream sections visible,
including stages deliberately outside the requested boundary. A stream chain with
fixed rates does not establish a reservoir-constrained production solution.

## 2. Discover before building

Use `capability_search.py` in a source checkout and runtime MCP search/inspection
for the deployed artifact. Use `agent_search.py` and `skill_search.py` to discover
installed specialists across core, community, enterprise and plugins. Verify
exact constructors, public methods, setup order, units and test assertions.
Follow the core discovery guide `docs/development/AGENT_CAPABILITY_DISCOVERY.md`.

Choose model fidelity per stage. `SimpleReservoir`, tank/material-balance drives
and decline curves are explicit simplifications. Use the installed reservoir
specialist and an external reservoir engine such as OPM Flow when spatial
reservoir simulation is needed; keep its deck, PVT/VFP interface and provenance.
Do not substitute a tank for a requested 3D model without making that gap explicit.
Use Beggs & Brill for an applicable steady correlation model, and assess the
current `TwoFluidPipe` qualification for transient/slugging requirements. Neither
an agent name nor source presence qualifies all requested regimes.

## 3. Compose reusable NeqSim objects

- Use one `ProcessSystem` per meaningful process area and combine areas with
  `ProcessModel.add(name, processSystem)`. Preserve named live inlet/outlet
  streams and the canonical model objects for later scenarios and optimization.
- Keep an explicit connection table for reservoir/well, well/manifold,
  manifold/flowline, flowline/riser, riser/host, host/utilities and host/downstream.
  Include direction, fluid identity, composition basis, rate basis, absolute
  pressure, temperature, enthalpy/energy basis and ownership of each boundary.
- Match pseudo-component definitions and reference/standard conditions before
  transferring fluids. Map and verify external PVT/VFP tables explicitly.
- Define which variable is specified and which is solved at each hydraulic
  boundary. Solve deliverability/backpressure consistently; do not independently
  prescribe incompatible rate and pressure at both ends of a well or pipeline.
- Attach utility supply/demand and capacity to the process. Include fuel gas,
  power, steam, cooling/heating and refrigeration when requested. Re-evaluate
  utility limits during operating-point changes instead of applying a final
  aggregate correction to an otherwise infeasible process.
- Preserve export, water-handling, treating, dehydration, NGL/LNG or refinery
  requirements as explicit downstream units/constraints, not prose placeholders.

## 4. Use the requested language with the same physics

| Concern | Python | Java |
| --- | --- | --- |
| Engine | Current NeqSim Java classes through JPype | The same public Java classes |
| Workspace bootstrap | Selected interpreter; `neqsim_init(project_root=...)`, then `ns.*` / `ns.JClass` | Current project dependencies; Java 8-compatible source/tests |
| Installed runtime | Inspect the installed bridge/JAR version; resolve classes from that artifact | Pin/report the Maven/JAR version used |
| Composition | Same component names, pseudo-components, EOS, mixing rule and phase options | Same definition and initialization sequence |
| Results | Explicit numeric conversion and arrays/units; preserve JVM model objects | Explicit typed results and units; preserve model objects |
| State | Clone before independent scenarios; never share mutable fluid state accidentally | Same cloning and calculation-identity discipline |

Do not assume a Python convenience wrapper covers every Java feature. Resolve
missing wrappers through the bridge's Java class access, inspect the signature,
and test it. Report an unavailable class/runtime instead of silently changing the
model. Do not restart a running JVM to swap JARs inside a study.

When both languages are requested, run the same case specification against the
same engine build, model settings and tolerances. Compare phase split, boundary
streams, rates/pressures, duties/power and limiting constraints. Matching output
formats alone is not language parity. When only one language is requested,
deliver that language and retain a language-neutral case/connection specification.

## 5. Solve and validate the connected system

Establish and validate a base case before optimization. Check component and total
mass conservation at every mixing/splitting/interface boundary, energy balances,
pressure/temperature profiles, phase states, constraints and product specifications.
Check host backpressure against well/network deliverability, shared utility
capacity, and changes in water cut, GOR and reservoir pressure over time.

For transient coupling, state timestep/time basis, event order, inventory and
boundary ownership, synchronization with external engines and failure/rollback
behavior. Check inventory conservation and timestep sensitivity; steady equipment
must not be described as dynamic merely because it is called in a time loop.
For scenarios, reject stale outputs after a failed/nonconverged run.

Use independent benchmarks and justified tolerances for the intended fidelity.
Measure representative whole-chain runtime and solver work before performance
changes. Preserve robustness, constraints and physical accuracy while optimizing.
Cache immutable case data only; reinitialize/recompute properties when state or
model selection changes.

## 6. Deliver and improve

Deliver the runnable script/classes or executed notebook, case inputs, connection
table, canonical model object, results with units, validation evidence, version,
assumptions, gaps and limitations. Use the existing task/report workflow for
formal studies. Track verified implementation/documentation gaps upstream under
existing user authorization; search first to avoid duplicate issues.

Keep generic workflows and public/synthetic examples in core/community. Keep
company data connectors, governed assets, policy overlays and plugin distribution
configuration in their private owning repositories. Enterprise extensions may
supply verified inputs or specialist methods; they must preserve the public
model/interface contract and remain optional for a public synthetic case.
Read only the enterprise materials authorized and available in the session.
