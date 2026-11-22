# Frozen wet-gas fixed-cell off-grid qualification, run45

Before generating or comparing the new numerical rows: use owned PR4878 head
c84b3d81c2312455074670ebd3f3e4a8160068af, frozen base69de066 and fresh
master f901a7b445bd8bd923dc21ee49be8befc3a8b13f. No production algorithm,
shared flash default, EOS input, tolerance or unsupported-domain boundary changes.

Same explicit initial-occupied-volume gas/aqueous experiment, gas-only withdrawal,
retained gas and aqueous disappearance. Appearance/reappearance, oil, wet upper-dew
inference, critical-degenerate paths and measured laboratory/field accuracy remain
unsupported. Laboratory and reservoir calibration/holdouts stay separate.

Independent off-grid classic unshifted SRK CH4/water, with the already declared
R/Tc/Pc/omega/kij values unchanged. Temperatures442.5/452.5/457.5K;
water mole fractions0.06/0.085/0.095; initial0.01/10/100mol. All27paths use
pressures200/180/160/140/120/100/90/80/70/60/50/40/30/20/15/10/7.5/5/3.5/2bara
(20points); both immutable checkpoint selections give54outcomes/1080checks.
The separator remains1.01325bara/298.15K and reports actual final-product volumes.

Freeze unchanged comparison bounds:1e-8mol absolute phase/retained/withdrawn
amounts;1e-8phase composition;1e-8relative independent EOS volume;
1e-10*max(1,initial named component mol) conservation. Internal1e-12equilibrium/
volume gates stay unchanged. Every declared case and failure must be retained;
do not remove failing points or silently extrapolate absent-phase properties.
Unexpected reference topology/domain failure remains an explicit failed boundary,
not authority to widen support or tune anything after comparison.

Refactor only the independent script's output loop into a callable generator;
require byte-identical regeneration of the original reference fixture before using
it for the new matrix. Record repeat and optimized-Python reproduction. The finite
composition-grid lower-support screen remains bounded, not a global stability proof.

Compare the new matrix on the owned source and a separately identified combination
with the two newly merged master flash/phase implementations. Do not rebase/merge
master into the PR, change another campaign's solver, or create another PR.
Record unmodified-master control first. Retain focused/broader tests, coverage,
Java8 compatibility, Javadocs, stable formatting/docs/hooks and runtime receipts.
Production source remains byte identical, so no new speed claim is justified.
