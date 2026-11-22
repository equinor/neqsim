# Run49 frozen refinement/limit scope (before independent generation/native comparisons)
Continue sole draft4885 on baseb20e324/head65e1c25. No production changes.
Extend its same synthetic gas-only laboratory-to-tank concept with continuous
well-mixed constant-rate component balance limits. Keep all run48 reference
inputs/results and tolerances unchanged. No EOS or volume fitting in this batch.

Use exactly the calibrated400K/200bara/1017.3m3 initial methane/ethane SRK sample
and constants/bases from reference.py. Five scenarios: existing four-interval
alternating injection; one interval equal total injection/withdrawal100000mol
(injection pure ethane); withdrawal100000mol/no injection; injection pure ethane
100000mol/no withdrawal; neither. Each interval86400s. Repeat1,2,4,8,16,32,64
substeps with fresh identical initial state for every scenario/refinement.

Independent continuous solution from d(ni)/dt=Ii-P*ni/N and N=N0+(I-P)t:
xi=xinj_i+(xi0-xinj_i)*(N/N0)^(-I/(I-P)); I=P uses exp(-I/N0);
I=0 keeps composition fixed. Produced components follow initial+injected-final.
Independent finite explicit reference uses current start composition, removes P/m,
then adds Ii/m. Check native against every finite reference with unchanged1e-4mol,
1e-6bara bounds, conservation1e-9relative. Compare aggregate produced composition
and surface gas volume when production exists; no producedFluid when zero production.
For nontrivial mixing, retained component maximum error versus continuous solution
must decrease monotonically, halve with refinement (ratio0.45..0.55 for each
successive pair), and be at most1mol at64substeps. This is a declared absolute synthetic numerical accuracy bound, not measured
uncertainty.
For pure withdrawal/pure injection/no-flow, continuous and finite endpoints agree
within1e-4mol/1e-6bara for all refinements. Constant total inventory remains initial
within1e-4mol and changes composition according to exponential limit. All outcomes
including continuous differences and timings retained. Repeat reference60/75digits
and preserve byte-identical original reference.json. Unsupported two-phase/critical,
measured field, aquifer/Bayesian/multi-tank/Tran acceptance remains open.

## Pre-native analytical finding and refinement response
The independent reference generated after this freeze fails the1mol target at64:
alternating4.2435408926mol; constant-inventory11.1381683959mol. Do not loosen1mol
or present64as converged to that bound. Retain these as negative controls and
extend only the two mixing cases through128,256,512,1024substeps. Require1024
rather than64to meet the unchanged1mol numerical target; test64remains above it.
The first-order ratio bound0.45..0.55 remains unchanged at every refinement.
60/75digit repeat may differ in arithmetic-roundoff diagnostics far below1e-40mol;
compare complete primary physical outputs, retain raw diagnostic discrepancies.
This mesh extension was chosen from the independent ODE before any native run.
