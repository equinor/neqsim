---
title: "Event and Fault Trees (IEC 61025 / IEC 62502)"
description: "Quantitative event-tree and fault-tree analysis with EventTreeAnalyzer and FaultTreeAnalyzer — outcome frequencies, minimal cut sets, voting (k/N) gates, and IEC 61508 β-factor common-cause modelling for AND and OR gates."
---

# Event and Fault Trees

NeqSim provides two complementary risk-quantification engines under
`neqsim.process.safety.risk`:

| Class | Purpose | Standard |
|-------|---------|----------|
| `EventTreeAnalyzer` | Forward propagation of an initiating event through binary branches | IEC 62502 (2010) |
| `FaultTreeAnalyzer` | Top-down decomposition of a top event via AND / OR / k-of-N gates | IEC 61025 (2006) |

## Event tree

```java
EventTreeAnalyzer eta = new EventTreeAnalyzer("Gas leak", 1.0e-4);
eta.addBranch("Immediate ignition",   0.10);
eta.addBranch("Delayed ignition",     0.20);
eta.addBranch("Unfavourable wind",    0.50);

double fJet = eta.outcomeFrequency(new boolean[] { true,  false, false }); // jet fire
double fVCE = eta.outcomeFrequency(new boolean[] { false, true,  true  }); // VCE
String tree = eta.toTextTree();
```

## Fault tree

```java
FaultTreeNode b1 = FaultTreeNode.basic("PT fail",  1.0e-2);
FaultTreeNode b2 = FaultTreeNode.basic("LT fail",  1.0e-2);
FaultTreeNode g  = FaultTreeNode.or("Sensor failure", b1, b2)
                                .withCCF(0.10);          // β-factor
FaultTreeNode top = FaultTreeNode.and("ESD fails",
    g, FaultTreeNode.basic("Solver fail", 5.0e-3));

double pTop = FaultTreeAnalyzer.topEventProbability(top);
List<List<String>> cuts = FaultTreeAnalyzer.minimalCutSets(top, 3);

// k-of-N voting
FaultTreeNode v = FaultTreeNode.voting("2oo3 PT", 2,
    FaultTreeNode.basic("PT-1", 1e-2),
    FaultTreeNode.basic("PT-2", 1e-2),
    FaultTreeNode.basic("PT-3", 1e-2));
```

## β-factor common-cause semantics

Per IEC 61508 Part 6, a β-factor models the fraction of failures that are
common-cause. NeqSim uses a convex combination:

$$
P_{\text{gate, CCF}} \;=\; (1-\beta)\,P_{\text{indep}} \;+\; \beta\,\max_i P_{\text{basic}_i}
$$

The directional effect differs between gate types:

| Gate | Independent only | With β > 0 | Reason |
|------|------------------|------------|--------|
| **AND** | $\prod_i P_i$ — small | larger | Common cause defeats redundancy → failures coincide |
| **OR**  | $1 - \prod_i (1-P_i)$ — slightly above $\max P_i$ | smaller | Replaces independent disjunction with single correlated event |

For **redundant safety systems** (AND gates) β-factor *increases* the top-event
probability — this is the dominant CCF mechanism. For **series systems** (OR
gates) the convex combination simply caps probability at `max P_i`.

## See also

- [HAZOP Worksheet](HAZOP.md)
- [FMEA Worksheet](FMEA.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
