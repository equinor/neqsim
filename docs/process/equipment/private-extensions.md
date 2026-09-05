---
title: "Private entrainment models"
description: How to supply a proprietary carry-over correlation to NeqSim as a separate JAR, using the entrainment provider Service Provider Interface, without publishing the correlation itself.
---

NeqSim computes separator carry-over through a small Service Provider Interface (SPI), so a
proprietary correlation can be supplied as a separate JAR without any of its content entering
the public repository.

## Models that ship with NeqSim

| Id | What it does |
|----|--------------|
| `zero` | Reports no carry-over. Useful as a deliberate baseline. |
| `spe-0.1gal-mmscf` | **Default.** A fixed 13.4 L of liquid per MSm³ of gas — the SI equivalent of the 0.1 US gal/MMscf rule of thumb from *The Savvy Separator: A Century of Carry-Over — 0.1 gal/MMscf* (SPE / JPT). Split between oil and water at the feed water cut. |
| `neqsim-7stage` | The open-source 7-stage physics chain, evaluated through `SeparatorPerformanceCalculator`. |

Select one on a separator:

```java
separator.setEntrainmentProvider("neqsim-7stage");
EntrainmentResult result = separator.getEntrainmentResult();
double oilCarryOver = result.getOilInGasKgPerHr();
```

With nothing selected the default applies. Selection affects `getEntrainmentResult()` only — it
does not change the entrainment applied during `run()`, so adding it to an existing model cannot
move that model's results.

### The default is an assumption, not a prediction

`spe-0.1gal-mmscf` returns the same litres per MSm³ whatever the separator is doing. It does not
respond to gas load factor, mesh pad selection or overload, so a vessel running at twice its
capacity still reports 13.4 L/MSm³. That is the point of a rule-of-thumb default, but it has one
consequence worth stating in any report: **a capacity constraint fed from this model can never
trip on carry-over.** Use `neqsim-7stage`, or a calibrated private model, when the number has to
respond to the design.

## Supplying a private model

Implement `EnhancedEntrainmentProvider` in your own project, depending on public NeqSim only:

```java
public class MyCorrelationProvider implements EnhancedEntrainmentProvider {
  public String getId() {
    return "my-correlation-v1";
  }

  public String getVersion() {
    return "1.0.0";
  }

  public EntrainmentApplicability checkApplicability(Separator separator) {
    // report every input outside your validity envelope
    return EntrainmentApplicability.ok();
  }

  public EntrainmentResult compute(Separator separator) {
    // your correlation here
    return new EntrainmentResult(getId(), getVersion(), oilKgPerHr, waterKgPerHr, gasKgPerHr, band);
  }
}
```

Register it by adding one line to your JAR:

```
META-INF/services/neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider
```

containing the fully-qualified class name. When that JAR is on the classpath, `ServiceLoader`
finds it and `setEntrainmentProvider("my-correlation-v1")` works. When it is absent, the call
fails with a message naming the models that *are* available, rather than silently substituting a
different correlation.

## What stays public and what stays private

The public repository holds the maximum structure that leaks nothing: the interface, the result
and applicability types, the registry, and provider *ids*. The id `eqn-pi-v1` is public; the
correlation behind it is not. Model parameters, validity envelopes, vendor-tagged data and the
tests that check the correlation reproduces its reference cases live in the private repository.

A maintainer reading public NeqSim can therefore see *what* a provider must accept and return
without being able to reconstruct *how* it computes.

## Stability contract

- Existing methods on `EnhancedEntrainmentProvider` will not change signature.
- New capability is added only as `default` methods, so an existing plug-in keeps compiling
  against newer NeqSim without recompilation.
- When the SPI gains a capability an old plug-in cannot express, `CURRENT_API_VERSION` is raised.
  A provider declaring a higher `getApiVersion()` than the core supports is refused at lookup
  with an explanatory message, so a mismatch fails at registration rather than silently at
  runtime.

## Implementer checklist

- `getId()` is globally unique, stable, and carries no version number — the version belongs in
  `getVersion()`.
- `compute()` is deterministic for a given separator state and mutates no global state; it is
  called from both the steady-state and transient solvers.
- `checkApplicability()` fails fast outside the validity envelope rather than extrapolating
  silently.
- Document on the implementing class what `compute()` does when inputs are out of range: throw,
  widen the confidence band, or return `NaN`.

## Related documentation

- [Separators](separators.md)
- [Enhanced entrainment modeling](separator-entrainment-modeling.md)
