---
title: Gas Scrubbers
description: "Vocabulary-first entry point for gas scrubbers and knockout drums in NeqSim — points to the main separator reference for the API, mechanical design, TR3500 conformity, and entrainment modeling."
keywords: "scrubber, gas scrubber, knock-out drum, knockout drum, KO drum, suction scrubber, mist eliminator, demister, vane pack, swirl deck, GasScrubber"
---

# Gas Scrubbers

A **gas scrubber** (also: knock-out drum, KO drum, suction scrubber) is a
vertical, gas-dominated separator whose purpose is to remove residual liquid
droplets from a gas stream — typically upstream of a compressor, expander, or
custody-transfer meter. In NeqSim, gas scrubbers share their entire API
surface with two-phase separators; they are documented together with the
rest of the separator family.

## Where to read

| You want to …                                                  | Go to                                                                                                                       |
|----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Class API (`GasScrubber`, `GasScrubberSimple`)                 | [Separator Equipment → Separator Types](separators.md#separator-types)                                                      |
| Choose between ideal flash, user-specified, or physics-based entrainment | [Separator Equipment → Choosing an entrainment / carry-over model](separators.md#choosing-an-entrainment--carry-over-model) |
| Mechanical design, internals (mesh, vane, cyclone deck), inlet device | [Separator Equipment → Gas Scrubber Design Parameters](separators.md#gas-scrubber-design-parameters)                        |
| TR3500 conformity (K-factor, inlet ρv², mesh-K, drainage head, cyclone ΔP) | [Separator Equipment → Gas Scrubber Mechanical Design and Conformity Checking](separators.md#gas-scrubber-mechanical-design-and-conformity-checking) |
| Physics-based grade-efficiency / cut-size / DSD model          | [Enhanced Separator Entrainment Modeling](separator-entrainment-modeling.md)                                                |
| Capacity-constraint provenance, hard vs soft limits, empirical carry-over | [Separator Equipment → Constraint Sources](separators.md#constraint-sources-conformity-vs-user-rules-vs-empirical)          |

## Minimal example

```java
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;

GasScrubber scrubber = new GasScrubber("V-101", feedStream);
scrubber.setInternalDiameter(2.0);     // m
scrubber.setSeparatorLength(6.0);      // m, tan-tan
scrubber.setOrientation("vertical");
scrubber.run();

// Mechanical design + TR3500 conformity
scrubber.initMechanicalDesign();
GasScrubberMechanicalDesign d = (GasScrubberMechanicalDesign) scrubber.getMechanicalDesign();
d.setMaxOperationPressure(100.0);
d.setInletNozzleID(0.581);
d.setMeshPad(Math.PI / 4.0 * 2.0 * 2.0, 250.0);  // full cross-section, 250 mm thick
d.setDemistingCyclones(256, 0.110, 3.287, 0.943);
d.setInletDevice("schoepentoeter");
d.setConformityRules("TR3500");

// Inspect the converged process result (T, P, flows for each outlet stream)
scrubber.displayResult();

// Run the TR3500 rule set and print PASS / WARN / FAIL for every check
System.out.println(d.checkConformity().toTextReport());

// Carry-over model — pick one of the three options described in separators.md
// (1) Default: nothing to do.
// (2) User-specified: scrubber.setEntrainment(0.001, "volume", "feed", "oil", "gas");
// (3) Physics-based: scrubber.setEnhancedEntrainmentCalculation(true);
```

For everything beyond this stub, follow the links above into
[separators.md](separators.md).

## Related Documentation

- [Separator Equipment](separators.md) — main reference (API, mechanical design, conformity, constraints)
- [Enhanced Separator Entrainment Modeling](separator-entrainment-modeling.md) — physics-based 7-stage chain
- [Compressors](compressors.md) — most common downstream consumer of a suction scrubber
- [Process Package](../) — package overview
