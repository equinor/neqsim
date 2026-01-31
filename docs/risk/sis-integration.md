# P2: Safety Instrumented System (SIS) Integration

## Overview

The SIS Integration package provides tools for analyzing Safety Instrumented Functions (SIFs) and performing Layer of Protection Analysis (LOPA) per IEC 61508 and IEC 61511 standards.

## Key Classes

### SafetyInstrumentedFunction

Models a single SIF with its components and calculates PFD (Probability of Failure on Demand):

```java
SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction(
    "SIF-001",                    // SIF ID
    "HP Separator PAHH Shutdown"  // Description
);

// Set SIL target
sif.setSILTarget(2);  // SIL 1, 2, 3, or 4

// Configure architecture
sif.setArchitecture("1oo2");  // Options: "1oo1", "1oo2", "2oo2", "2oo3", "1oo3"

// Set component PFDs
sif.setSensorPFD(0.01);        // Pressure transmitter PFD
sif.setLogicSolverPFD(0.001);  // SIS logic solver PFD
sif.setFinalElementPFD(0.02);  // Shutdown valve PFD

// Set proof test interval
sif.setProofTestInterval(8760);  // Annual testing (hours)
```

### SIL Ranges

| SIL | PFDavg Range | RRF Range |
|-----|--------------|-----------|
| SIL 1 | 0.1 - 0.01 | 10 - 100 |
| SIL 2 | 0.01 - 0.001 | 100 - 1,000 |
| SIL 3 | 0.001 - 0.0001 | 1,000 - 10,000 |
| SIL 4 | 0.0001 - 0.00001 | 10,000 - 100,000 |

### Calculating PFD

```java
// Calculate PFDavg
double pfdAvg = sif.calculatePFDavg();
System.out.println("PFDavg: " + pfdAvg);

// Get achieved SIL
int achievedSIL = sif.getAchievedSIL();
System.out.println("Achieved SIL: " + achievedSIL);

// Risk Reduction Factor
double rrf = sif.calculateRRF();
System.out.println("RRF: " + rrf);
```

### SIL Verification

```java
SILVerificationResult result = sif.verifySIL();

System.out.println("Target SIL: " + result.getTargetSIL());
System.out.println("Achieved SIL: " + result.getAchievedSIL());
System.out.println("Verified: " + result.isVerified());

// Check for issues
if (result.getIssues().size() > 0) {
    System.out.println("Issues:");
    for (String issue : result.getIssues()) {
        System.out.println("  - " + issue);
    }
}
```

## SISIntegratedRiskModel

Combines SIFs with other protection layers for LOPA analysis:

```java
SISIntegratedRiskModel model = new SISIntegratedRiskModel(
    "Separator Overpressure Protection"
);

// Define initiating event
model.setInitiatingEventDescription("Loss of cooling leading to overpressure");
model.setInitiatingEventFrequency(0.1);  // per year

// Set consequence category (per risk matrix)
model.setConsequenceCategory("C4");  // Major safety/environmental
model.setTargetMitigatedFrequency(1e-5);  // Target frequency
```

### Adding Independent Protection Layers (IPLs)

```java
// Add non-SIS protection layers
model.addIPL("BPCS High Pressure Alarm", 10);     // RRF = 10, PFD = 0.1
model.addIPL("Operator Response", 10);             // RRF = 10
model.addIPL("PSV Relief System", 100);            // RRF = 100, PFD = 0.01

// Add Safety Instrumented Function
SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction("SIF-001", "PAHH");
sif.setSILTarget(2);
sif.setArchitecture("1oo2");
sif.setSensorPFD(0.01);
sif.setLogicSolverPFD(0.001);
sif.setFinalElementPFD(0.02);
model.addSIF(sif);
```

### Performing LOPA

```java
LOPAResult lopa = model.performLOPA();

System.out.println("Initiating Event Frequency: " + 
    lopa.getInitiatingEventFrequency() + " /year");

System.out.println("\nProtection Layers:");
for (LOPAResult.ProtectionLayer layer : lopa.getProtectionLayers()) {
    System.out.println("  " + layer.getName() + 
        ": PFD=" + layer.getPFD() + 
        ", RRF=" + layer.getRiskReductionFactor());
}

System.out.println("\nMitigated Frequency: " + 
    lopa.getMitigatedFrequency() + " /year");
System.out.println("Target Frequency: " + 
    lopa.getTargetFrequency() + " /year");
System.out.println("LOPA Status: " + 
    (lopa.isAcceptable() ? "PASS" : "FAIL"));

// Required SIF performance
System.out.println("Required SIF RRF: " + lopa.getRequiredSIFRRF());
```

### LOPA Calculation

The LOPA calculation follows:

```
Mitigated Frequency = IE × PFD_IPL1 × PFD_IPL2 × ... × PFD_SIF

Where:
- IE = Initiating Event Frequency
- PFD_IPLn = Probability of Failure on Demand for each IPL
- PFD_SIF = PFDavg for the Safety Instrumented Function
```

## Use Cases

### 1. SIF Design Verification

```java
// Define SIF requirements
SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction(
    "SIF-101", 
    "Compressor High Vibration Shutdown"
);
sif.setSILTarget(2);

// Try different architectures
String[] architectures = {"1oo1", "1oo2", "2oo3"};
for (String arch : architectures) {
    sif.setArchitecture(arch);
    sif.setSensorPFD(0.02);
    sif.setLogicSolverPFD(0.001);
    sif.setFinalElementPFD(0.03);
    
    int achieved = sif.getAchievedSIL();
    System.out.println(arch + ": Achieved SIL " + achieved + 
        (achieved >= 2 ? " ✓" : " ✗"));
}
```

### 2. Complete LOPA Study

```java
// LOPA for high-pressure scenario
SISIntegratedRiskModel model = new SISIntegratedRiskModel("HP LOPA Study");

// Scenario definition
model.setInitiatingEventDescription("Blocked outlet + heat input");
model.setInitiatingEventFrequency(0.5);  // Expected once every 2 years
model.setConsequenceCategory("C5");      // Catastrophic

// IPLs
model.addIPL("BPCS High Pressure Trip", 10);
model.addIPL("Manual Intervention", 10);
model.addIPL("PSV-101 Relief", 100);

// SIF
SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction(
    "SIF-001", "PAHH with ESD Valve");
sif.setSILTarget(2);
sif.setArchitecture("1oo2");
sif.setSensorPFD(0.01);
sif.setLogicSolverPFD(0.001);
sif.setFinalElementPFD(0.015);
model.addSIF(sif);

// Run LOPA
LOPAResult result = model.performLOPA();
System.out.println("Final mitigated frequency: " + 
    result.getMitigatedFrequency() + " /year");
```

### 3. SIF Inventory Management

```java
// Manage multiple SIFs
SISIntegratedRiskModel platform = new SISIntegratedRiskModel("Platform SIS");

// Add all platform SIFs
SafetyInstrumentedFunction[] sifs = {
    createSIF("SIF-001", "Separator PAHH", 2),
    createSIF("SIF-002", "Compressor Vibration", 1),
    createSIF("SIF-003", "Gas Detection ESD", 3),
    createSIF("SIF-004", "Fire Detection", 2)
};

for (SafetyInstrumentedFunction sif : sifs) {
    platform.addSIF(sif);
    SILVerificationResult result = sif.verifySIL();
    String status = result.isVerified() ? "✓" : "✗";
    System.out.println(status + " " + sif.getSifId() + 
        ": Target SIL " + sif.getSILTarget() + 
        ", Achieved SIL " + result.getAchievedSIL());
}
```

## Output Format

### JSON Export

```java
String json = model.toJson();
```

Example output:
```json
{
  "modelName": "HP Separator Protection LOPA",
  "initiatingEvent": {
    "description": "Blocked outlet with heat input",
    "frequency": 0.5
  },
  "consequenceCategory": "C5",
  "targetFrequency": 1.0e-6,
  "protectionLayers": [
    {"name": "BPCS High Pressure Trip", "type": "IPL", "pfd": 0.1, "rrf": 10},
    {"name": "Manual Intervention", "type": "IPL", "pfd": 0.1, "rrf": 10},
    {"name": "PSV-101 Relief", "type": "IPL", "pfd": 0.01, "rrf": 100},
    {"name": "SIF-001 PAHH ESD", "type": "SIF", "pfd": 0.0065, "rrf": 154}
  ],
  "lopaResult": {
    "mitigatedFrequency": 3.25e-7,
    "targetMet": true,
    "margin": 3.08
  },
  "sifs": [
    {
      "sifId": "SIF-001",
      "description": "PAHH with ESD Valve",
      "silTarget": 2,
      "silAchieved": 2,
      "verified": true,
      "architecture": "1oo2",
      "pfdAvg": 0.0065,
      "rrf": 154
    }
  ]
}
```

## Best Practices

1. **Conservative IPL Selection**: Only credit IPLs that are truly independent and have documented PFD values

2. **Architecture Selection**: Use redundant architectures (1oo2, 2oo3) for higher SIL requirements

3. **Proof Testing**: Shorter proof test intervals reduce PFD but increase operational costs

4. **Common Cause Failures**: Account for CCF in redundant systems using beta factor method

5. **Documentation**: Maintain complete LOPA worksheets for regulatory compliance

## Standards References

- IEC 61508: Functional safety of electrical/electronic/programmable electronic safety-related systems
- IEC 61511: Functional safety – Safety instrumented systems for the process industry sector
- ANSI/ISA 84.00.01: Functional Safety: Safety Instrumented Systems for the Process Industry Sector
- CCPS Guidelines for Safe Automation of Chemical Processes
