# NRC-3144: Make the qualified PHREEQC Pitzer catalog the default

- Status: PROPOSED
- Owners: @EvenSol (thermodynamics subsystem); independent domain reviewer required
- Created: 2026-08-25
- Target release: next major NeqSim release after acceptance

## Context and problem

NeqSim now ships a versioned Pitzer parameter catalog transcribed from the public-domain
PHREEQC 3.9.0-17591 `pitzer.dat`, pinned to source commit
`b0b3be767158ccc3322d2c816625cf470045e67e` and database blob
`324f852784be84650b77bd7f07f8316aafd8188b`. The catalog contains 54 `B0`, 48
`B1`, 8 `B2`, 32 `C0`, 30 `theta`, 59 `psi`, 27 `lambda`, and 10 `zeta`
rows, including temperature functions and explicit provenance.

Users currently must know which dataset-selection method to call. That is error-prone for
process simulations and makes the smaller historical binary table the accidental default.
The desired engineering workflow is a gas/oil EOS coupled to a Pitzer aqueous phase, with
ions confined to water, aqueous properties and reactions, scale calculations, and process
equipment calculations without a parameter-selection step.

Changing an existing thermodynamic default changes numerical behavior. Under
`GOVERNANCE.md` it is a replacement of a thermodynamic method used by an existing public
API and requires an NRC. Under the API lifecycle policy it must be released at a compatible
major-version boundary with migration documentation and compatibility tests.

## Decision

For `SystemPitzer`, make the qualified, versioned PHREEQC catalog the automatic default at
the next major-version boundary.

The runtime selector shall:

1. inspect the active aqueous ion and neutral topology;
2. require every applicable binary, same-sign, ternary, and neutral-ion interaction from
   one coherent dataset;
3. validate the complete topology before mutating phase state;
4. apply the catalog once, lazily, on first Pitzer evaluation;
5. treat hydrocarbons as EOS gas/oil species rather than requiring aqueous neutral Pitzer
   interactions;
6. continue to require explicit `lambda`/`zeta` coverage for active non-hydrocarbon
   aqueous neutrals; and
7. fail closed with a missing-interaction diagnostic for unsupported mixed brines.

No missing coefficient may be inferred as zero. Parameters from Pitzer, SIT, eNRTL,
Extended UNIQUAC, electrolyte EOS, reaction-equilibrium, or mineral-solubility formalisms
shall not be interchanged.

A pre-evaluation `useLegacyPitzerParameters()` compatibility method shall reproduce the
historical dataset when explicitly requested. Dataset identity and qualification level
shall remain queryable and visible in calculation provenance.

## Public contracts affected

- The numerical default of `SystemPitzer` and `PhasePitzer`.
- Pitzer activity coefficients, water activity, osmotic coefficients, derivatives, and
  downstream flash/property/scale results for topologies covered by the PHREEQC catalog.
- Repeated and serialized calculations whose provenance did not previously pin a dataset.

No PR, SRK, CPA, electrolyte-EOS, or non-electrolyte path changes. No component naming,
salt-input API, generic TP-flash ownership, or process-equipment contract changes are part
of this decision.

## Engineering and safety boundary

The parameter catalog is availability evidence, not universal qualification. Each species
family and observable range needs independent validation before project use. Calculation
provenance must distinguish `AVAILABLE`, `REGRESSION_TESTED`, and independently
`VALIDATED` coverage.

The implementation shall preserve electroneutrality, material and elemental balance,
normalized non-negative phase state, activity/fugacity closure, deterministic changed-state
execution, clone/serialization/thread safety, and process mass/energy balance. Mineral
precipitation additionally requires saturation/complementarity evidence and independently
qualified solid standard states.

This decision does not qualify asset decisions or permit unreviewed extrapolation. Ba/Sr
sulfate remains outside qualified default use until a complete redistributable
`C0`/ternary family and held-out barite/celestite evidence are available.

## Compatibility and migration

This is an intentional numerical behavior change and shall ship only after NRC acceptance
at the next major-version boundary.

Migration guidance:

- New calculations normally use the catalog automatically and should record the returned
  dataset identity.
- Historical reproductions call `useLegacyPitzerParameters()` before the first activity,
  property, or flash evaluation.
- Applications that already select the PHREEQC catalog explicitly may remove the redundant
  call after migrating, but keeping it remains supported.
- Unsupported mixed topologies must address the reported missing interaction or explicitly
  choose a separately qualified dataset; they must not depend on silent zero substitution.
- Regression baselines must record both NeqSim version and Pitzer dataset identity.

The legacy opt-out remains supported for at least one normal release cycle and through any
LTS support period required by the API lifecycle policy.

## Validation and qualification evidence

The implementation PR is [#3249](https://github.com/equinor/neqsim/pull/3249), currently
draft and blocked from merge until this NRC is accepted and its exact-head gates pass.

Required evidence includes:

- exact PHREEQC source version, commit, blob, license, equation and standard-state mapping;
- independent held-out mean-activity, osmotic-coefficient, and water-activity data;
- mixed-brine validation without refitting;
- total/elemental balance and aqueous electroneutrality;
- complete interaction coverage and fail-closed missing-row diagnostics;
- gas/oil/aqueous phase roles, ion confinement, and fugacity/activity closure;
- scale saturation/precipitation complementarity where solids are enabled;
- density, enthalpy, heat capacity, and process mass/energy closure;
- deterministic repeated/changed-state, cloning, serialization, and parallel-system tests;
- Java and Python composability; and
- kernel, complete calculation, catalog-selection, and neutral PR/SRK/CPA performance
  controls, normally allowing no more than about 1% median neutral-path overhead.

Current independent evidence includes Robinson and Bower (1966), NBS Journal of Research
70A, Table 2, [DOI 10.6028/jres.070A.026](https://doi.org/10.6028/jres.070A.026), a U.S.
Government public-domain work, for mixed CaCl2-MgCl2 osmotic and water-activity behavior at
298.15 K. Source-derived fixtures are not parameter-fitting data.

## Alternatives considered

### Keep the historical table as the default

Rejected as the long-term design because it makes the broad versioned catalog difficult to
discover and requires every process user to know an implementation-specific selection call.
It remains available as an explicit compatibility mode.

### Require every user to choose a named dataset

Scientifically explicit, but rejected as the normal supported workflow. NeqSim should choose
its qualified built-in default while always exposing the selected identity and allowing an
expert override before evaluation.

### Combine all open-literature values into one largest table

Rejected. Numerical availability does not prove compatible Pitzer equation variants,
standard states, temperature functions, or redistribution rights. Source families remain
versioned and cannot be mixed merely because species names match.

### Substitute zero for absent interactions

Rejected because it can create apparently converged but unqualified mixed-brine results.
Incomplete topology must fail closed.

### Map Pitzer coefficients into electrolyte EOS or reaction tables

Rejected because these parameter families have different thermodynamic semantics. Pitzer is
the aqueous GE model in the hybrid system; the gas/oil EOS and reaction/mineral databases
retain their own independently qualified parameters.

## Rollback strategy

Before release, rejection of this NRC means implementation PR #3249 does not merge with the
new default. Its independent validation and diagnostics may be retained in a separately
reviewed non-default form.

After release, users can immediately restore historical behavior per system with
`useLegacyPitzerParameters()`. A defect in the selector or catalog can be mitigated by a
documented patch that changes the catalog qualification status or disables an affected
topology while retaining fail-closed behavior. Parameter rows are never silently overwritten;
a corrected source is published under a new dataset identity with migration notes.

## Decision record

- 2026-08-25: PROPOSED in response to issue #3144 and the explicit request that the largest
  qualified Pitzer database be used without manual parameter selection.
- Acceptance requires the thermodynamics subsystem owner, one independent domain reviewer,
  the release/versioning decision, and resolution of implementation PR #3249 review and CI.
