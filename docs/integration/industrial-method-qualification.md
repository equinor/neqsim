---
title: "Industrial method qualification and applicability"
description: "Qualify exact NeqSim method versions against independent benchmarks, intended use and machine-evaluable project-service envelopes."
---

# Industrial method qualification and applicability

NeqSim separates successful calculation from qualification for a particular engineering service. A converged result,
an internal regression test and a project approval record answer different questions and must not be treated as
interchangeable evidence.

`EngineeringMethodQualificationRegistry` evaluates the exact `method@version` against four independent controls:

1. the qualification record is complete and approved;
2. an independently reviewed, non-regression benchmark qualifies the same method version;
3. the requested use is explicitly included; and
4. supplied project-service inputs are within a structured applicability envelope.

The result is machine-readable and can report `QUALIFIED_FOR_SERVICE`, `UNREGISTERED_METHOD`,
`INCOMPLETE_QUALIFICATION`, `MISSING_INDEPENDENT_BENCHMARK`, `USE_NOT_QUALIFIED`,
`INSUFFICIENT_SERVICE_CONTEXT` or `OUTSIDE_QUALIFIED_ENVELOPE`.

## Define and assess a method

```java
EngineeringMethodApplicabilityEnvelope envelope =
    new EngineeringMethodApplicabilityEnvelope("gas-pressure-drop", "A")
        .requireInput("compositionBasis")
        .numericRange("pressure", "bara", 1.0, 150.0)
        .numericRange("temperature", "K", 250.0, 400.0)
        .allowedValues("phase", "GAS")
        .knownLimitation("Not qualified for two-phase service")
        .uncertaintyBasis("INDEPENDENT-CALC-21 uncertainty budget, revision A")
        .prohibitExtrapolation(true);

EngineeringMethodQualification qualification =
    new EngineeringMethodQualification("gas-method", "2.1",
        EngineeringMethodQualification.Level.PROJECT_QUALIFIED)
        .addStandardReference("PROJECT-METHOD-BASIS-A")
        .addApplicabilityLimit("Structured envelope gas-pressure-drop revision A")
        .addEvidenceReference("INDEPENDENT-CALC-21")
        .approve("Technical authority / APPROVAL-21")
        .applicabilityEnvelope(envelope)
        .qualifyFor(EngineeringMethodQualification.IntendedUse.FEED_SUPPORT)
        .addAcceptanceCriterion("pressureDrop", "bar", 0.05, 0.01);

EngineeringMethodServiceContext service =
    new EngineeringMethodServiceContext(EngineeringMethodQualification.IntendedUse.FEED_SUPPORT)
        .numericValue("pressure", 80.0, "bara")
        .numericValue("temperature", 300.0, "K")
        .categoricalValue("phase", "GAS")
        .suppliedInput("compositionBasis");

EngineeringMethodQualificationRegistry registry =
    new EngineeringMethodQualificationRegistry("project-methods", "A").register(qualification);

EngineeringMethodQualificationRegistry.Result assessment =
    registry.assess("gas-method@2.1", service, independentBenchmarkReport);
```

Attach the registry and every executed-method assessment to `EngineeringProductionReadinessBasis`. Activating the
registry adds the fail-closed `INDUSTRIAL_METHOD_APPLICABILITY` readiness gate: every method executed by the final
design iteration must have a qualifying assessment for its actual service.

## Interpretation boundaries

- Units are compared exactly; conversion must occur before assessment so the evidence value is unambiguous.
- An intended use such as FEED support does not grant FEED approval.
- Permitting an extrapolated calculation supports investigation only; the result remains outside the qualified
  envelope.
- The registry does not create standards, independent calculations, vendor evidence or approval records.
- Any method-version, envelope, input, intended-use or benchmark change requires reassessment.
