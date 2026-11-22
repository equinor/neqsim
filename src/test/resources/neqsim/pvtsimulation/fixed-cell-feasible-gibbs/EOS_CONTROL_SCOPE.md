# Run38 frozen qualification, before comparison

Parent/base: draft4866 d24962df86f5417836978a24bb0d5e9edde0f46e /
master e316ffb6d7eb42a176156dd278731a010d9a5196.

Question: does the copied feasible Gibbs reference preserve EOS choice, volume
correction, homogeneous phase branches, extensive scaling and caller state?
No production implementation, protocol, physical-domain or tolerance changes.

Matrix: the three published methane/n-butane/n-heptane inventories, scales
0.001/1/1000, classic SRK/PR, volume correction false/true, and paired nearby
(temperature offset K, pressure bara) = (-2,9.8), (0,10), (+2,10.2):108 states.
Each must have gas/oil. Check the exact identity fast path; uniform target
inventory multiplier1.125 against analytic1.125*G; and nonuniform target
inventory multipliers[1.000002,0.999997,1.000001] against freshly constructed
homogeneous systems (no clone, phaseToSystem or production reference helper).
Those diagnostic target vectors are explicit mathematical reference inputs,
not reservoir volumes or fitted EOS parameters. Source serialization must be
byte-identical after every reference operation. Repeat calls must be bit-identical.

Energy comparison tolerance frozen1e-9*sampleScale J, which scales the existing
1e-9J unit control. Independent homogeneous component closure64ulp(max(1,N));
nonnegative allocations, same T/P/phase, finite positive density/volume.
No new end-to-end depletion acceptance is inferred. Retain unexpected failures;
do not modify a model constant or bound to make them pass. Evidence is analytic
extensivity and independent construction sharing NeqSim EOS, not independent
thermodynamic simulator or measured laboratory/field accuracy.

Stop boundary: energy-reference qualification only. Existing trace-zero native
volume gate, wet/critical physics, shared solvers and whole-issue acceptance stay
outside this draft. Documentation: extend existing test receipt/README and
agent/skill qualification handoff; regenerate inventory. No notebook.
