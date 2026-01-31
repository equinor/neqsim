---
layout: default
title: Dependency Analysis
parent: Risk Framework
---

# Dependency Analysis

Dependency analysis answers critical operational questions:

- *"If this pump fails, what else is affected?"*
- *"Which equipment should we monitor more closely?"*
- *"What's the cascade effect across installations?"*

---

## Overview

The `DependencyAnalyzer` combines process topology with production impact analysis to determine:

1. **Direct dependencies** - Immediately downstream equipment
2. **Indirect dependencies** - Cascade effects through the process
3. **Criticality changes** - Which equipment becomes more critical
4. **Cross-installation effects** - Impacts on other platforms

---

## Using DependencyAnalyzer

### Basic Setup

```java
// Create analyzer with topology
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();

// Tag equipment with STID
topology.setFunctionalLocation("HP Separator", "1775-VG-23001");
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A");
topology.setFunctionalLocation("Compressor B", "1775-KA-23011B");

// Create dependency analyzer
DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);
```

### Analyzing a Failure

```java
// What happens if Compressor A fails?
DependencyResult result = deps.analyzeFailure("Compressor A");

// Failed equipment info
System.out.println("Failed: " + result.getFailedEquipment());
System.out.println("STID: " + result.getFailedLocation().getFullTag());

// Direct impact (immediate downstream)
System.out.println("Directly affected:");
for (String eq : result.getDirectlyAffected()) {
    System.out.println("  - " + eq);
}

// Indirect impact (cascade)
System.out.println("Indirectly affected (cascade):");
for (String eq : result.getIndirectlyAffected()) {
    System.out.println("  - " + eq);
}

// Production loss
System.out.printf("Total production loss: %.1f%%%n", result.getTotalProductionLoss());
```

---

## DependencyResult Structure

```java
public class DependencyResult {
    // What failed
    String getFailedEquipment();
    FunctionalLocation getFailedLocation();
    
    // Impact
    List<String> getDirectlyAffected();      // Immediate downstream
    List<String> getIndirectlyAffected();    // Cascade effects
    double getTotalProductionLoss();          // % production lost
    
    // Criticality changes
    Map<String, Double> getIncreasedCriticality();  // Equipment → new criticality
    
    // Equipment to watch
    List<String> getEquipmentToWatch();
    
    // Cross-installation
    List<CrossInstallationDependency> getCrossInstallationEffects();
    
    // Export
    String toJson();
}
```

---

## Equipment Monitoring Recommendations

When one equipment shows weakness, the analyzer recommends what else to watch:

```java
// Get monitoring recommendations
Map<String, String> toMonitor = deps.getEquipmentToMonitor("Compressor A");

System.out.println("Equipment to monitor when Compressor A shows weakness:");
for (Map.Entry<String, String> entry : toMonitor.entrySet()) {
    System.out.printf("  %s%n", entry.getKey());
    System.out.printf("    Reason: %s%n", entry.getValue());
}
```

Output:
```
Equipment to monitor when Compressor A shows weakness:

  Compressor B
    Reason: Parallel train - will carry additional load (100% → 200% of normal)
    
  Aftercooler A
    Reason: Directly downstream - reduced flow will affect heat transfer
    
  HP Separator
    Reason: Upstream - may need operating point adjustment
    
  Export Pipeline
    Reason: Downstream - reduced pressure and flow
```

### Monitoring Logic

The analyzer considers:

| Relationship | Monitoring Reason |
|--------------|-------------------|
| Parallel equipment | Will carry additional load |
| Downstream equipment | Flow/pressure changes |
| Upstream equipment | May need adjustment |
| Shared utilities | Common failure modes |
| Control systems | Setpoint changes needed |

---

## Criticality Changes

When equipment fails, other equipment becomes more critical:

```java
DependencyResult result = deps.analyzeFailure("Compressor A");

System.out.println("Increased Criticality:");
for (Map.Entry<String, Double> entry : result.getIncreasedCriticality().entrySet()) {
    String status = entry.getValue() > 0.9 ? "⚠️ CRITICAL" : "";
    System.out.printf("  %s: %.2f %s%n", 
        entry.getKey(), 
        entry.getValue(),
        status);
}
```

Output:
```
Increased Criticality:
  Compressor B: 0.95 ⚠️ CRITICAL  (was 0.50 - now carrying full load)
  HP Separator: 0.85              (unchanged - always critical)
  Aftercooler B: 0.70             (was 0.35 - now handling all gas)
```

### Criticality Formula

$$C_{\text{new}} = C_{\text{base}} \times \frac{\text{Load}_{\text{new}}}{\text{Load}_{\text{normal}}}$$

For parallel equipment:
$$C_{\text{parallel}} = C_{\text{base}} \times \frac{n}{n - f}$$

Where $n$ = total trains, $f$ = failed trains.

---

## Cross-Installation Dependencies

### Defining Cross-Platform Dependencies

```java
// Gullfaks C exports gas that feeds Åsgard A
deps.addCrossInstallationDependency(
    "Export Compressor",        // Source equipment
    "Åsgard Inlet Separator",   // Target equipment  
    "Åsgard A",                 // Target installation
    "gas_export"                // Dependency type
);

// With STID tags (preferred)
FunctionalLocation source = new FunctionalLocation("1775-KA-23011A");
FunctionalLocation target = new FunctionalLocation("2540-VG-30001");
deps.addCrossInstallationDependency(source, target, "gas_export", 0.6);
```

### Dependency Types

| Type | Description |
|------|-------------|
| `gas_export` | Gas pipeline connection |
| `oil_export` | Oil pipeline connection |
| `utility` | Shared utilities (power, water) |
| `control` | Shared control systems |
| `personnel` | Shared crew/expertise |

### Analyzing Cross-Installation Effects

```java
DependencyResult result = deps.analyzeFailure("Export Compressor");

System.out.println("Cross-Installation Effects:");
for (CrossInstallationDependency effect : result.getCrossInstallationEffects()) {
    System.out.printf("  %s → %s%n", 
        effect.getSourceInstallation(),
        effect.getTargetInstallation());
    System.out.printf("    Target equipment: %s%n", effect.getTargetEquipment());
    System.out.printf("    Dependency type: %s%n", effect.getDependencyType());
    System.out.printf("    Impact factor: %.0f%%%n", effect.getImpactFactor() * 100);
}
```

Output:
```
Cross-Installation Effects:
  Gullfaks C → Åsgard A
    Target equipment: Åsgard Inlet Separator
    Dependency type: gas_export
    Impact factor: 60%
```

### Impact Factor

The impact factor (0-1) indicates how much the target is affected:

| Factor | Meaning |
|--------|---------|
| 1.0 | Complete dependency (100% affected) |
| 0.6 | Major dependency (60% affected) |
| 0.3 | Partial dependency (30% affected) |
| 0.0 | No dependency |

---

## Cascade Analysis

### Full Cascade Tree

```java
// Get complete cascade for a failure
Map<String, List<String>> cascadeTree = deps.getCascadeTree("HP Separator");

System.out.println("Cascade Tree for HP Separator failure:");
printTree(cascadeTree, "HP Separator", 0);

void printTree(Map<String, List<String>> tree, String node, int depth) {
    String indent = StringUtils.repeat("  ", depth);
    System.out.println(indent + "└─ " + node);
    for (String child : tree.getOrDefault(node, Collections.emptyList())) {
        printTree(tree, child, depth + 1);
    }
}
```

Output:
```
Cascade Tree for HP Separator failure:
└─ HP Separator
  └─ Compressor A
    └─ Aftercooler A
      └─ Export Gas
  └─ Compressor B
    └─ Aftercooler B
  └─ Condensate Pump
    └─ Storage Tank
```

### Cascade Timing

Equipment fails at different times after the initial failure:

```java
Map<String, Double> cascadeTiming = deps.getCascadeTiming("HP Separator");

System.out.println("Cascade Timing:");
for (Map.Entry<String, Double> entry : cascadeTiming.entrySet()) {
    System.out.printf("  %s: +%.0f minutes%n", 
        entry.getKey(), entry.getValue());
}
```

Output:
```
Cascade Timing:
  Compressor A: +0 minutes (immediate - starved)
  Compressor B: +0 minutes (immediate - starved)
  Aftercooler A: +2 minutes (flow dies out)
  Condensate Pump: +5 minutes (level drops)
  Export Gas: +10 minutes (pressure decays)
  Storage Tank: +30 minutes (pump stops)
```

---

## Example: Complete Dependency Analysis

```java
// Complete example
ProcessSystem process = createGasPlant();
ProcessTopologyAnalyzer topology = new ProcessTopologyAnalyzer(process);
topology.buildTopology();

// Tag equipment
topology.setFunctionalLocation("HP Separator", "1775-VG-23001");
topology.setFunctionalLocation("Compressor A", "1775-KA-23011A");
topology.setFunctionalLocation("Compressor B", "1775-KA-23011B");

// Create analyzer
DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);

// Add cross-installation dependency
deps.addCrossInstallationDependency(
    new FunctionalLocation("1775-KA-23011A"),
    new FunctionalLocation("2540-VG-30001"),
    "gas_export", 0.6
);

// Analyze Compressor A failure
System.out.println(StringUtils.repeat("═", 70));
System.out.println("DEPENDENCY ANALYSIS: Compressor A Failure");
System.out.println(StringUtils.repeat("═", 70));

DependencyResult result = deps.analyzeFailure("Compressor A");

System.out.println("\n📍 FAILED EQUIPMENT:");
System.out.printf("  Name: %s%n", result.getFailedEquipment());
System.out.printf("  STID: %s%n", result.getFailedLocation().getFullTag());
System.out.printf("  Installation: %s%n", result.getFailedLocation().getInstallationName());

System.out.println("\n🔴 DIRECTLY AFFECTED:");
for (String eq : result.getDirectlyAffected()) {
    System.out.println("  • " + eq);
}

System.out.println("\n🟠 INDIRECTLY AFFECTED (CASCADE):");
for (String eq : result.getIndirectlyAffected()) {
    System.out.println("  • " + eq);
}

System.out.println("\n⚠️ INCREASED CRITICALITY:");
for (Map.Entry<String, Double> entry : result.getIncreasedCriticality().entrySet()) {
    System.out.printf("  %s: %.2f%n", entry.getKey(), entry.getValue());
}

System.out.println("\n🔍 EQUIPMENT TO MONITOR:");
Map<String, String> monitor = deps.getEquipmentToMonitor("Compressor A");
for (Map.Entry<String, String> entry : monitor.entrySet()) {
    System.out.printf("  %s%n    └─ %s%n", entry.getKey(), entry.getValue());
}

System.out.println("\n🌐 CROSS-INSTALLATION EFFECTS:");
for (CrossInstallationDependency cross : result.getCrossInstallationEffects()) {
    System.out.printf("  %s → %s (%.0f%% impact)%n",
        cross.getSourceInstallation(),
        cross.getTargetInstallation(),
        cross.getImpactFactor() * 100);
}

System.out.printf("%n💰 TOTAL PRODUCTION LOSS: %.1f%%%n", result.getTotalProductionLoss());
System.out.println("═".repeat(70));
```

Output:
```
══════════════════════════════════════════════════════════════════════
DEPENDENCY ANALYSIS: Compressor A Failure
══════════════════════════════════════════════════════════════════════

📍 FAILED EQUIPMENT:
  Name: Compressor A
  STID: 1775-KA-23011A
  Installation: Gullfaks C

🔴 DIRECTLY AFFECTED:
  • Aftercooler A

🟠 INDIRECTLY AFFECTED (CASCADE):
  • Export Gas

⚠️ INCREASED CRITICALITY:
  Compressor B: 0.95
  Aftercooler B: 0.70

🔍 EQUIPMENT TO MONITOR:
  Compressor B
    └─ Parallel train - will carry 100% load
  HP Separator
    └─ Upstream - may need pressure adjustment
  Aftercooler A
    └─ Downstream - no flow

🌐 CROSS-INSTALLATION EFFECTS:
  Gullfaks C → Åsgard A (60% impact)

💰 TOTAL PRODUCTION LOSS: 45.0%
══════════════════════════════════════════════════════════════════════
```

---

## JSON Export

```java
String json = result.toJson();
```

```json
{
  "failedEquipment": "Compressor A",
  "stidTag": "1775-KA-23011A",
  "installation": "Gullfaks C",
  "directlyAffected": ["Aftercooler A"],
  "indirectlyAffected": ["Export Gas"],
  "increasedCriticality": {
    "Compressor B": 0.95,
    "Aftercooler B": 0.70
  },
  "equipmentToWatch": ["Compressor B", "HP Separator", "Aftercooler A"],
  "totalProductionLossPercent": 45.0,
  "crossInstallationEffects": [
    {
      "targetInstallation": "Åsgard A",
      "targetEquipment": "Åsgard Inlet Separator",
      "dependencyType": "gas_export",
      "impactFactor": 0.6
    }
  ]
}
```

---

## Best Practices

1. **Build topology first** - Dependencies require topology
2. **Tag all equipment** - STID enables better analysis
3. **Define cross-installation links** - For multi-platform operations
4. **Consider timing** - Not all cascade effects are immediate
5. **Update regularly** - Process changes affect dependencies
6. **Validate with operators** - Local knowledge is valuable

---

## See Also

- [Process Topology Analysis](topology.md)
- [STID & Functional Location](stid-tagging.md)
- [Production Impact Analysis](production-impact.md)
- [API Reference](api-reference.md#dependencyanalyzer)
