# P4: Bow-Tie Diagram Analysis

## Overview

The Bow-Tie package provides tools for creating and analyzing bow-tie diagrams - a visual representation of risk scenarios showing threats, prevention barriers, top events, mitigation barriers, and consequences.

```
THREATS          PREVENTION         TOP EVENT        MITIGATION        CONSEQUENCES
                  BARRIERS                            BARRIERS

[Corrosion] ──▶ [Inspection] ─┐                  ┌─▶ [Detection] ──▶ [Fire]
                              │                  │
[Erosion] ───▶ [Monitoring] ──┼──▶ [Loss of] ────┼─▶ [ESD] ─────────▶ [Injury]
                              │    Containment   │
[Overpressure]▶ [PSV] ────────┘                  └─▶ [Containment] ─▶ [Pollution]
```

## Key Classes

### BowTieAnalyzer

Creates and analyzes bow-tie models:

```java
BowTieAnalyzer analyzer = new BowTieAnalyzer("Loss of Containment Analysis");

// Set the central hazardous event
analyzer.setTopEvent("Loss of Containment from HP Separator");
```

### Adding Threats

Threats are the causes that can lead to the top event:

```java
// addThreat(id, description, baseFrequency)
analyzer.addThreat("T1", "External Corrosion", 0.001);  // per year
analyzer.addThreat("T2", "Internal Erosion", 0.0005);
analyzer.addThreat("T3", "Overpressure", 0.01);
analyzer.addThreat("T4", "Mechanical Impact", 0.0001);
analyzer.addThreat("T5", "Fatigue Failure", 0.0002);
```

### Adding Prevention Barriers

Prevention barriers reduce the likelihood of threats causing the top event:

```java
// addPreventionBarrier(id, description, PFD, threatIds[])
analyzer.addPreventionBarrier("B1", "Corrosion Monitoring Program", 0.1, 
    new String[]{"T1"});
analyzer.addPreventionBarrier("B2", "Protective Coating", 0.05, 
    new String[]{"T1"});
analyzer.addPreventionBarrier("B3", "Erosion/Corrosion Monitoring", 0.1, 
    new String[]{"T2"});
analyzer.addPreventionBarrier("B4", "PAHH + ESD (SIF-001)", 0.01, 
    new String[]{"T3"});
analyzer.addPreventionBarrier("B5", "PSV Protection", 0.01, 
    new String[]{"T3"});
analyzer.addPreventionBarrier("B6", "Physical Barriers/Guards", 0.1, 
    new String[]{"T4"});
analyzer.addPreventionBarrier("B7", "Fatigue Monitoring", 0.15, 
    new String[]{"T5"});
```

### Adding Consequences

Consequences are the outcomes if the top event occurs:

```java
// addConsequence(id, description, category, cost)
analyzer.addConsequence("C1", "Personnel Injury", "Safety", 1000000);
analyzer.addConsequence("C2", "Environmental Release", "Environmental", 500000);
analyzer.addConsequence("C3", "Production Loss", "Financial", 100000);
analyzer.addConsequence("C4", "Reputation Damage", "Reputation", 200000);
```

### Adding Mitigation Barriers

Mitigation barriers reduce the severity of consequences:

```java
// addMitigationBarrier(id, description, PFD, consequenceIds[])
analyzer.addMitigationBarrier("M1", "Gas Detection System", 0.1, 
    new String[]{"C1", "C2"});
analyzer.addMitigationBarrier("M2", "Emergency Shutdown", 0.05, 
    new String[]{"C1", "C2", "C3"});
analyzer.addMitigationBarrier("M3", "Fire & Gas System", 0.1, 
    new String[]{"C1"});
analyzer.addMitigationBarrier("M4", "Containment Bund", 0.1, 
    new String[]{"C2"});
analyzer.addMitigationBarrier("M5", "Spare Capacity", 0.5, 
    new String[]{"C3"});
analyzer.addMitigationBarrier("M6", "Emergency Response Plan", 0.2, 
    new String[]{"C1", "C2", "C4"});
```

## Performing Analysis

```java
BowTieModel model = analyzer.analyze();

// Get overall frequencies
System.out.println("Top Event: " + model.getTopEvent());
System.out.println("Unmitigated Frequency: " + 
    model.getUnmitigatedFrequency() + " /year");
System.out.println("Mitigated Frequency: " + 
    model.getMitigatedFrequency() + " /year");

// Analyze each threat path
for (BowTieModel.Threat threat : model.getThreats()) {
    double reducedFreq = model.getReducedFrequencyForThreat(threat.getId());
    System.out.println(threat.getDescription() + 
        ": " + threat.getFrequency() + " -> " + reducedFreq);
}

// Analyze each consequence
for (BowTieModel.Consequence consequence : model.getConsequences()) {
    double mitigatedRisk = model.getMitigatedRiskForConsequence(
        consequence.getId());
    System.out.println(consequence.getDescription() + 
        ": $" + mitigatedRisk + "/year");
}
```

## Visualization

### ASCII Diagram

```java
String diagram = model.toAsciiDiagram();
System.out.println(diagram);
```

Output:
```
================================================================================
                              BOW-TIE DIAGRAM
                        Loss of Containment from HP Separator
================================================================================

THREATS                 PREVENTION              TOP EVENT              MITIGATION              CONSEQUENCES
-------                 ----------              ---------              ----------              ------------

[T1] External Corrosion ─┬─[B1] Corrosion Mon.─┐                    ┌─[M1] Gas Detection ──┬─[C1] Personnel Injury
     (1.00E-03/yr)       └─[B2] Protective Coa.┤                    ├─[M2] Emergency Shutd.┤
                                                │                    ├─[M3] Fire & Gas Sys.─┘
[T2] Internal Erosion ────[B3] Erosion/Corros.─┤                    │
     (5.00E-04/yr)                              │                    ├─[M1] Gas Detection ──┬─[C2] Environmental Release
                                                │   ┌─────────┐      ├─[M2] Emergency Shutd.┤
[T3] Overpressure ──────┬─[B4] PAHH + ESD ────┼──▶│ LOSS OF │──────├─[M4] Containment Bun.┘
     (1.00E-02/yr)       └─[B5] PSV Protection─┤   │CONTAINM.│      ├─[M6] Emergency Respo.
                                                │   └─────────┘      │
[T4] Mechanical Impact ──[B6] Physical Barrie.─┤                    ├─[M2] Emergency Shutd.──[C3] Production Loss
     (1.00E-04/yr)                              │                    └─[M5] Spare Capacity
                                                │
[T5] Fatigue Failure ────[B7] Fatigue Monitor.─┘                    ┌─[M1] Gas Detection ──┬─[C4] Reputation Damage
     (2.00E-04/yr)                                                   └─[M6] Emergency Respo.┘

================================================================================
SUMMARY:
  Unmitigated Top Event Frequency: 1.18E-02 /year
  Mitigated Risk: $45,230 /year
================================================================================
```

### JSON Export

```java
String json = model.toJson();
```

## Use Cases

### 1. Barrier Effectiveness Analysis

```java
BowTieAnalyzer analyzer = new BowTieAnalyzer("Barrier Analysis");
analyzer.setTopEvent("HC Release");

analyzer.addThreat("T1", "Corrosion", 0.01);
analyzer.addPreventionBarrier("B1", "Inspection", 0.1, new String[]{"T1"});
analyzer.addConsequence("C1", "Fire", "Safety", 1000000);
analyzer.addMitigationBarrier("M1", "Detection", 0.1, new String[]{"C1"});

BowTieModel model = analyzer.analyze();

// Calculate barrier contribution
double withBarrier = model.getReducedFrequencyForThreat("T1");
double withoutBarrier = model.getThreats().get(0).getFrequency();
double barrierEffectiveness = 1 - (withBarrier / withoutBarrier);

System.out.println("Barrier reduces frequency by " + 
    (barrierEffectiveness * 100) + "%");
```

### 2. SIF Integration

```java
// Create SIF
SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction(
    "SIF-001", "PAHH Shutdown");
sif.setSILTarget(2);
sif.setArchitecture("1oo2");
sif.setSensorPFD(0.01);
sif.setLogicSolverPFD(0.001);
sif.setFinalElementPFD(0.02);

// Add to bow-tie with calculated PFD
analyzer.addPreventionBarrier(
    "B-SIF", 
    "SIF-001 PAHH Shutdown", 
    sif.calculatePFDavg(),  // Use calculated PFD
    new String[]{"T3"}      // Overpressure threat
);
```

### 3. Risk Ranking

```java
BowTieModel model = analyzer.analyze();

// Rank threats by risk contribution
List<RiskContribution> contributions = new ArrayList<>();
for (BowTieModel.Threat threat : model.getThreats()) {
    double riskContribution = model.getRiskContributionForThreat(threat.getId());
    contributions.add(new RiskContribution(threat.getDescription(), riskContribution));
}
contributions.sort((a, b) -> Double.compare(b.risk, a.risk));

System.out.println("Threat Risk Ranking:");
for (RiskContribution c : contributions) {
    System.out.println("  " + c.name + ": $" + c.risk + "/year");
}
```

### 4. What-If Analysis

```java
// Baseline
BowTieModel baseline = analyzer.analyze();
double baselineRisk = baseline.getMitigatedRisk();

// What if barrier B1 fails?
analyzer.setBarrierStatus("B1", false);  // Disable barrier
BowTieModel degraded = analyzer.analyze();
double degradedRisk = degraded.getMitigatedRisk();

System.out.println("Baseline risk: $" + baselineRisk + "/year");
System.out.println("Risk with B1 failed: $" + degradedRisk + "/year");
System.out.println("Risk increase: " + 
    ((degradedRisk - baselineRisk) / baselineRisk * 100) + "%");

// Restore barrier
analyzer.setBarrierStatus("B1", true);
```

## Calculation Methods

### Unmitigated Top Event Frequency

```
f_top = Σ (f_threat_i × Π PFD_prevention_j)

Where:
- f_threat_i = Base frequency of threat i
- PFD_prevention_j = PFD of each prevention barrier for threat i
```

### Mitigated Consequence Risk

```
Risk_consequence = f_top × Π PFD_mitigation_k × Consequence_cost

Where:
- f_top = Top event frequency
- PFD_mitigation_k = PFD of each mitigation barrier for consequence
- Consequence_cost = Cost/severity of consequence
```

## Best Practices

1. **Complete Barrier Identification**: Ensure all relevant barriers are identified through HAZOP or similar studies

2. **Realistic PFD Values**: Use documented PFD values from standards (IEC 61511) or reliability databases

3. **Independence**: Verify that barriers are truly independent (no common cause failures)

4. **Regular Review**: Update bow-tie models when equipment or procedures change

5. **Barrier Ownership**: Assign clear ownership for each barrier's maintenance and testing

## Standards References

- ISO 31000: Risk management — Guidelines
- Energy Institute: Guidance on bow-tie methodology
- CCPS: Layer of Protection Analysis
- UK HSE: Reducing Risks, Protecting People (R2P2)
