# Initial qualified fixed-cell state reuse

Base/master d6dd0cd190ac32f04a08627fafdeff7f745566e5. No active4740PR; merged4866 is reused.
Freeze concept before editing: precise restoreSaturationVolume is entered only after
runFixedCellProtocol has already TP-flashed, initialized density, refined, and checked
the named phase family at the current T/P. Reuse that state for iteration0. After any
component withdrawal, continue the existing TP flash and precision gates. Legacy
restore(false) remains unchanged. No numerical bound or EOS parameter changes.

Baseline275K/100,60,30,10,2bara SRK methane/n-butane amounts0.95/0.05mol passes
when only two components are registered. Registering n-heptane at exactly0mol fails
before any withdrawal at100bara. Diagnostic shows duplicate flash reduces total
inventory to0.999999999998mol, changes occupied volume from1.6800354597386446e-4
to1.6800354597369639e-4m3, yielding-1.0004457990173167e-12 relative residual.
Both already exceed the unchanged1e-12 precision target, so reject rather than widen.

Qualification: named zero stays exactlyzero, caller serialization unchanged, phase
inventory/nonnegative named withdrawal conservation, relative volume1e-12 at all
endpoints. Compare zero-registered and absent-registration paths by named amounts
within1e-10*max(1,initial)mol, and frozen independent binary references within existing
1e-8mol bounds. Include three scales, six component permutations, nearby T275+/-2K,
initial pressure100+/-2bara and unchanged long pressure grid; retain failed domains.
Required public API regressions, broader PVT suite, Java8 compile, coverage, Javadoc,
static differential, stable formatting/docs/hooks/inventory, runtime measurement.
No wet/critical newdomain, EOS retuning, measured field, reserves or global flash claims.

Runtime control frozen before timing comparison: existing FixedCellRuntimeProbe,
350K methane/decane0.6/0.4 at200/100/50bara; six alternating fork pairs,
heap128/512MB,50warmups,20trials x8samples. Accept checksum differences only
within1e-10relative (summing independently closed volumes); keep raw values.
No timing threshold or generalized speedup guarantee. Reuse existing driver with
only this explicitly declared output-equivalence bound, not a fitted bound.
