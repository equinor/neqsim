---
title: "Generalized Model Impact Analysis"
description: "Propagate governed model changes through engineering dependencies to identify recalculation, regeneration, revalidation, and reapproval work."
keywords: "GeneralizedImpactAnalyzer, model impact analysis, recalculation, revalidation, reapproval, engineering graph"
---

# Generalized model impact analysis

`GeneralizedImpactAnalyzer` converts a model-change event and the canonical engineering graph into a controlled work
list. It reports direct and propagated objects, explains the relationship path, orders affected calculations, detects
calculation cycles, and identifies approvals that must be repeated.

The default rule set covers NeqSim dependencies and the relationships needed across a wider digital-twin toolchain:

| Relationship | Propagation | Default response |
|---|---|---|
| `DEPENDS_ON` | target to source | revalidate; recalculate calculation nodes |
| `GENERATED_FROM` | target to source | regenerate |
| `APPROVES` | approved subject to approval | reapprove |
| `REPRESENTS_SAME_AS` | both directions | revalidate |
| `SYNCHRONIZED_FROM` | authoritative target to local source | revalidate |
| `VALIDATED_AGAINST` | evidence target to validated source | revalidate |
| `CONSUMED_BY_MODEL` | source artifact to consuming model | revalidate |
| `INVALIDATES` | source to target | revalidate |
| `REQUIRES_REAPPROVAL` | source to approval target | reapprove |

```java
ImpactAnalysisResult impact = new GeneralizedImpactAnalyzer().analyze(engineeringGraph, modelChangeEvent);

for (String calculationId : impact.getRecalculationOrder()) {
  // Dispatch controlled recalculation work.
}
```

Projects can supply their own `ImpactAnalysisRule` list for additional graph relationships. A rule declares the edge
kind, propagation direction, and required action; the traversal algorithm stays unchanged.

The result is deterministic and explicitly sets `fitnessForConstruction` to `false`. It is a work proposal and audit
record, not an approval decision.
