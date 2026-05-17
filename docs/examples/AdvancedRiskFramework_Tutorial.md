---
layout: default
title: "AdvancedRiskFramework Tutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# AdvancedRiskFramework Tutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`AdvancedRiskFramework_Tutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb).

---

# NeqSim Advanced Risk Framework Tutorial

This notebook demonstrates the advanced risk analysis capabilities of NeqSim, including:

1. **P1: Dynamic Simulation Integration** - Monte Carlo with transient modeling
2. **P2: SIS/SIF Integration** - Safety Instrumented Systems and LOPA
3. **P3: Real-time Digital Twin** - Continuous monitoring interface
4. **P4: Bow-Tie Diagram Generation** - Visual risk analysis
5. **P5: Multi-Asset Portfolio Risk** - Portfolio-level analysis
6. **P6: Condition-Based Reliability** - Predictive maintenance
7. **P7: ML/AI Integration** - Machine learning interface

## Prerequisites

This tutorial requires NeqSim with the advanced risk packages installed.

```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim

# Import Java classes through the jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Pump = jneqsim.process.equipment.pump.Pump

# Import Risk Framework classes
OperationalRiskSimulator = jneqsim.process.safety.risk.OperationalRiskSimulator
RiskModel = jneqsim.process.safety.risk.RiskModel
RiskMatrix = jneqsim.process.safety.risk.RiskMatrix
DynamicRiskSimulator = jneqsim.process.safety.risk.dynamic.DynamicRiskSimulator
ProductionProfile = jneqsim.process.safety.risk.dynamic.ProductionProfile
SafetyInstrumentedFunction = jneqsim.process.safety.risk.sis.SafetyInstrumentedFunction
SISIntegratedRiskModel = jneqsim.process.safety.risk.sis.SISIntegratedRiskModel
RealTimeRiskMonitor = jneqsim.process.safety.risk.realtime.RealTimeRiskMonitor
RealTimeRiskAssessment = jneqsim.process.safety.risk.realtime.RealTimeRiskAssessment
BowTieAnalyzer = jneqsim.process.safety.risk.bowtie.BowTieAnalyzer
BowTieModel = jneqsim.process.safety.risk.bowtie.BowTieModel
PortfolioRiskAnalyzer = jneqsim.process.safety.risk.portfolio.PortfolioRiskAnalyzer
PortfolioRiskResult = jneqsim.process.safety.risk.portfolio.PortfolioRiskResult
ConditionBasedReliability = jneqsim.process.safety.risk.condition.ConditionBasedReliability
RiskMLInterface = jneqsim.process.safety.risk.ml.RiskMLInterface

print("NeqSim Risk Framework loaded successfully!")
```

## Setup: Create a Sample Process System

First, we'll create a simple oil & gas processing system to use throughout this tutorial.

```python
# Create a simple natural gas system
def create_gas_system():
    fluid = SystemSrkEos(298.15, 50.0)  # 25°C, 50 bar
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.08)
    fluid.addComponent("propane", 0.04)
    fluid.addComponent("n-butane", 0.02)
    fluid.addComponent("CO2", 0.01)
    fluid.setMixingRule("classic")
    return fluid

# Create process system
fluid = create_gas_system()
process = ProcessSystem()

# Add equipment
inlet_stream = Stream("Inlet Stream", fluid)
inlet_stream.setFlowRate(100000.0, "kg/hr")  # 100 t/hr
process.add(inlet_stream)

# Inlet separator
inlet_sep = Separator("Inlet Separator", inlet_stream)
process.add(inlet_sep)

# Compressor
compressor = Compressor("Export Compressor", inlet_sep.getGasOutStream())
compressor.setOutletPressure(100.0)  # 100 bar
process.add(compressor)

# Run process
process.run()

print(f"Process created with {len(process.getUnitOperations())} units")
print(f"Inlet flow: {inlet_stream.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {compressor.getPower('kW'):.0f} kW")
```

---

## P1: Dynamic Simulation Integration

The `DynamicRiskSimulator` extends Monte Carlo analysis to include transient effects during equipment failures.
This captures startup/shutdown losses that traditional steady-state analysis misses.

```python
# Create Dynamic Risk Simulator
dynamic_sim = DynamicRiskSimulator("Platform Production Risk")

# Set base production rate (MMscf/day)
dynamic_sim.setBaseProductionRate(150.0)
dynamic_sim.setProductionUnit("MMscf/day")

# Add equipment with failure rates and transient profiles
# Parameters: name, MTBF (hours), repair time (hours), production impact (%)
dynamic_sim.addEquipment("Export Compressor", 8760, 72, 1.0)  # Critical - full shutdown
dynamic_sim.addEquipment("Inlet Separator", 17520, 24, 0.8)   # Major impact
dynamic_sim.addEquipment("HP Pump", 4380, 12, 0.3)            # Partial impact
dynamic_sim.addEquipment("Glycol Pump", 8760, 8, 0.1)         # Minor impact

# Configure transient behavior
from neqsim.process.safety.risk.dynamic import DynamicRiskSimulator
dynamic_sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL)
dynamic_sim.setStartupProfile(DynamicRiskSimulator.RampProfile.S_CURVE)
dynamic_sim.setShutdownTime(4.0)   # 4 hours to full shutdown
dynamic_sim.setStartupTime(8.0)    # 8 hours to restore production

print("Equipment configured:")
for eq in dynamic_sim.getEquipmentList():
    print(f"  - {eq.getName()}: MTBF={eq.getMTBF():.0f}h, Impact={eq.getProductionImpact()*100:.0f}%")
```

```python
# Run Monte Carlo simulation with transient modeling
dynamic_sim.setSimulationHorizon(8760)  # 1 year
dynamic_sim.setIterations(5000)
dynamic_sim.setTimeStep(1.0)  # 1-hour resolution

result = dynamic_sim.runSimulation()

# Display results
print("\n=== Dynamic Risk Simulation Results ===")
print(f"\nProduction Statistics:")
print(f"  Expected annual production: {result.getExpectedProduction():.2f} MMscf")
print(f"  P10 (optimistic): {result.getP10Production():.2f} MMscf")
print(f"  P50 (median): {result.getP50Production():.2f} MMscf")
print(f"  P90 (conservative): {result.getP90Production():.2f} MMscf")

print(f"\nLoss Breakdown:")
print(f"  Steady-state losses: {result.getSteadyStateLoss():.2f} MMscf")
print(f"  Shutdown transient losses: {result.getTransientLoss().getShutdownLoss():.2f} MMscf")
print(f"  Startup transient losses: {result.getTransientLoss().getStartupLoss():.2f} MMscf")
print(f"  Total transient losses: {result.getTransientLoss().getTotalTransientLoss():.2f} MMscf")

print(f"\nAvailability: {result.getAvailability()*100:.2f}%")
```

```python
# Visualize production profile from a sample iteration
import matplotlib.pyplot as plt
import numpy as np

# Get sample production profile
sample_profile = result.getSampleProductionProfile()
times = [tp.getTime() for tp in sample_profile.getTimePoints()]
production = [tp.getProduction() for tp in sample_profile.getTimePoints()]

# Plot
fig, ax = plt.subplots(figsize=(12, 5))
ax.plot(times, production, 'b-', linewidth=0.5)
ax.axhline(y=150.0, color='r', linestyle='--', label='Design capacity')
ax.set_xlabel('Time (hours)')
ax.set_ylabel('Production (MMscf/day)')
ax.set_title('Sample Production Profile with Transient Effects')
ax.set_xlim(0, 8760)
ax.legend()
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.show()
```

---

## P2: SIS/SIF Integration (Safety Instrumented Systems)

The `SISIntegratedRiskModel` provides LOPA (Layer of Protection Analysis) capabilities
and SIL (Safety Integrity Level) verification per IEC 61508/61511.

```python
# Create SIS-integrated risk model
sis_model = SISIntegratedRiskModel("HP Separator Overpressure Protection")

# Define the hazardous event
sis_model.setInitiatingEventFrequency(0.1)  # 0.1 per year
sis_model.setConsequenceCategory("C4")      # Major environmental/safety

# Add Independent Protection Layers (IPLs)
sis_model.addIPL("BPCS High Pressure Alarm", 10)      # PFD = 0.1
sis_model.addIPL("Operator Response", 10)              # PFD = 0.1
sis_model.addIPL("PSV Relief", 100)                    # PFD = 0.01

# Add Safety Instrumented Function
sif = SafetyInstrumentedFunction("SIF-001", "HP Separator PAHH")
sif.setSILTarget(2)  # SIL 2 target
sif.setArchitecture("1oo2")  # 1-out-of-2 voting
sif.setSensorPFD(0.01)    # PT sensor
sif.setLogicSolverPFD(0.001)  # SIS logic solver
sif.setFinalElementPFD(0.02)  # ESD valve
sif.setProofTestInterval(8760)  # Annual testing

sis_model.addSIF(sif)

print("SIS Configuration:")
print(f"  SIF: {sif.getSifId()} - {sif.getDescription()}")
print(f"  Target SIL: {sif.getSILTarget()}")
print(f"  Architecture: {sif.getArchitecture()}")
print(f"  Calculated PFDavg: {sif.calculatePFDavg():.2e}")
```

```python
# Perform LOPA Analysis
lopa_result = sis_model.performLOPA()

print("\n=== LOPA Analysis Results ===")
print(f"\nInitiating Event: {sis_model.getInitiatingEventDescription()}")
print(f"Initiating Event Frequency: {sis_model.getInitiatingEventFrequency():.2e} /year")

print("\nProtection Layers:")
for layer in lopa_result.getProtectionLayers():
    print(f"  {layer.getName()}: RRF = {layer.getRiskReductionFactor()}")

print(f"\nMitigated Event Frequency: {lopa_result.getMitigatedFrequency():.2e} /year")
print(f"Target Frequency: {lopa_result.getTargetFrequency():.2e} /year")
print(f"LOPA Status: {'PASS' if lopa_result.isAcceptable() else 'FAIL'}")
print(f"Required SIF RRF: {lopa_result.getRequiredSIFRRF():.0f}")
```

```python
# SIL Verification
sil_result = sif.verifySIL()

print("\n=== SIL Verification Results ===")
print(f"\nTarget SIL: {sif.getSILTarget()}")
print(f"Achieved SIL: {sil_result.getAchievedSIL()}")
print(f"Verification: {'PASS' if sil_result.isVerified() else 'FAIL'}")

print("\nPFD Breakdown:")
print(f"  Sensor contribution: {sif.getSensorPFD():.2e}")
print(f"  Logic solver contribution: {sif.getLogicSolverPFD():.2e}")
print(f"  Final element contribution: {sif.getFinalElementPFD():.2e}")
print(f"  Total PFDavg: {sif.calculatePFDavg():.2e}")

if sil_result.getIssues():
    print("\nVerification Issues:")
    for issue in sil_result.getIssues():
        print(f"  - {issue}")
```

---

## P3: Real-time Digital Twin Interface

The `RealTimeRiskMonitor` provides continuous risk monitoring with configurable alerts
and Key Risk Indicators (KRIs).

```python
# Create Real-time Risk Monitor
monitor = RealTimeRiskMonitor("Platform Alpha", "PA-001")

# Register equipment
monitor.registerEquipment("P-100A", "HP Pump A", 8760.0)
monitor.registerEquipment("P-100B", "HP Pump B", 8760.0)
monitor.registerEquipment("C-200", "Export Compressor", 12000.0)
monitor.registerEquipment("V-300", "HP Separator", 17520.0)

# Configure alert thresholds
vibration_thresholds = RealTimeRiskMonitor.AlertThresholds()
vibration_thresholds.setWarningHigh(7.0)   # mm/s
vibration_thresholds.setAlarmHigh(10.0)    # mm/s
vibration_thresholds.setShutdownHigh(15.0) # mm/s
monitor.setAlertThresholds("vibration", vibration_thresholds)

pressure_thresholds = RealTimeRiskMonitor.AlertThresholds()
pressure_thresholds.setAlarmLow(45.0)      # bar
pressure_thresholds.setWarningLow(50.0)    # bar
pressure_thresholds.setWarningHigh(90.0)   # bar
pressure_thresholds.setAlarmHigh(95.0)     # bar
pressure_thresholds.setShutdownHigh(100.0) # bar
monitor.setAlertThresholds("pressure", pressure_thresholds)

print("Real-time monitor configured:")
print(f"  Facility: {monitor.getFacilityName()}")
print(f"  Registered equipment: {len(monitor.getRegisteredEquipment())}")
```

```python
# Simulate real-time data update
import random

# Simulated process variables
process_data = HashMap()
process_data.put("P-100A.vibration", 5.5)
process_data.put("P-100A.temperature", 65.0)
process_data.put("P-100B.vibration", 8.2)  # Elevated!
process_data.put("P-100B.temperature", 72.0)
process_data.put("C-200.vibration", 4.0)
process_data.put("C-200.discharge_pressure", 92.0)  # High!
process_data.put("V-300.pressure", 55.0)
process_data.put("V-300.level", 45.0)

# Update monitor
monitor.updateProcessVariables(process_data)

# Perform real-time assessment
assessment = monitor.performAssessment()

print("\n=== Real-time Risk Assessment ===")
print(f"\nTimestamp: {assessment.getTimestamp()}")
print(f"Overall Risk Level: {assessment.getOverallRiskLevel()}")

print("\nEquipment Status:")
for status in assessment.getEquipmentStatuses():
    health_icon = "🟢" if status.getHealthIndex() > 0.8 else "🟡" if status.getHealthIndex() > 0.5 else "🔴"
    print(f"  {health_icon} {status.getEquipmentId()}: Health={status.getHealthIndex():.2f}, Risk={status.getRiskScore():.2f}")

print("\nKey Risk Indicators:")
for kri, value in assessment.getKeyRiskIndicators().items():
    print(f"  {kri}: {value:.3f}")
```

```python
# Set up alert listener
alerts_received = []

class AlertHandler(RealTimeRiskMonitor.AlertListener):
    def onAlert(self, alert):
        alerts_received.append(alert)
        level_icon = "⚠️" if alert.getLevel().toString() == "WARNING" else "🚨" if alert.getLevel().toString() == "ALARM" else "🔴"
        print(f"{level_icon} ALERT: {alert.getMessage()} (Level: {alert.getLevel()})")

# Register listener and check alerts
monitor.addAlertListener(AlertHandler())
monitor.checkAlerts()

print(f"\nTotal alerts triggered: {len(alerts_received)}")
```

---

## P4: Bow-Tie Diagram Generation

The `BowTieAnalyzer` creates structured barrier diagrams connecting threats to consequences
with prevention and mitigation barriers.

```python
# Create Bow-Tie Analyzer
analyzer = BowTieAnalyzer("HP Separator Loss of Containment")

# Define the Top Event
analyzer.setTopEvent("Loss of Containment from HP Separator")

# Add Threats (causes)
analyzer.addThreat("T1", "External Corrosion", 0.001)
analyzer.addThreat("T2", "Internal Erosion", 0.0005)
analyzer.addThreat("T3", "Overpressure", 0.01)
analyzer.addThreat("T4", "Mechanical Impact", 0.0001)

# Add Prevention Barriers
analyzer.addPreventionBarrier("B1", "Corrosion Monitoring", 0.1, ["T1"])
analyzer.addPreventionBarrier("B2", "Protective Coating", 0.05, ["T1"])
analyzer.addPreventionBarrier("B3", "Erosion Monitoring", 0.1, ["T2"])
analyzer.addPreventionBarrier("B4", "PAHH + ESD", 0.01, ["T3"])
analyzer.addPreventionBarrier("B5", "PSV Protection", 0.01, ["T3"])
analyzer.addPreventionBarrier("B6", "Physical Barriers", 0.1, ["T4"])

# Add Consequences
analyzer.addConsequence("C1", "Personnel Injury", "Safety", 1000000)
analyzer.addConsequence("C2", "Environmental Release", "Environmental", 500000)
analyzer.addConsequence("C3", "Production Loss", "Financial", 100000)

# Add Mitigation Barriers
analyzer.addMitigationBarrier("M1", "Gas Detection", 0.1, ["C1", "C2"])
analyzer.addMitigationBarrier("M2", "Emergency Shutdown", 0.05, ["C1", "C2", "C3"])
analyzer.addMitigationBarrier("M3", "Fire & Gas System", 0.1, ["C1"])
analyzer.addMitigationBarrier("M4", "Containment Bund", 0.1, ["C2"])
analyzer.addMitigationBarrier("M5", "Spare Capacity", 0.5, ["C3"])

print("Bow-Tie model configured")
```

```python
# Analyze the Bow-Tie
bowtie_model = analyzer.analyze()

print("\n=== Bow-Tie Analysis Results ===")
print(f"\nTop Event: {bowtie_model.getTopEvent()}")
print(f"Unmitigated Frequency: {bowtie_model.getUnmitigatedFrequency():.2e} /year")
print(f"Mitigated Frequency: {bowtie_model.getMitigatedFrequency():.2e} /year")

print("\nThreats:")
for threat in bowtie_model.getThreats():
    reduced_freq = threat.getFrequency()
    for barrier in bowtie_model.getPreventionBarriersForThreat(threat.getId()):
        reduced_freq *= barrier.getPFD()
    print(f"  {threat.getId()}: {threat.getDescription()} - Base: {threat.getFrequency():.2e}, After barriers: {reduced_freq:.2e}")

print("\nConsequences (after mitigation):")
for consequence in bowtie_model.getConsequences():
    mitigated_risk = bowtie_model.getMitigatedRiskForConsequence(consequence.getId())
    print(f"  {consequence.getId()}: {consequence.getDescription()} - Risk: ${mitigated_risk:,.0f}/year")
```

```python
# Generate ASCII visualization
print("\n=== Bow-Tie Diagram ===")
print(bowtie_model.toAsciiDiagram())
```

---

## P5: Multi-Asset Portfolio Risk

The `PortfolioRiskAnalyzer` aggregates risk across multiple assets considering correlations
and common cause failures.

```python
# Create Portfolio Risk Analyzer
portfolio = PortfolioRiskAnalyzer("North Sea Assets")

# Add assets with their risk profiles
portfolio.addAsset("Platform A", 50.0, 45.0, 5.0)   # Expected: 50 MMscf/d, Mean: 45, StdDev: 5
portfolio.addAsset("Platform B", 75.0, 70.0, 8.0)
portfolio.addAsset("Platform C", 40.0, 38.0, 4.0)
portfolio.addAsset("FPSO Alpha", 100.0, 92.0, 12.0)

# Add common cause failure scenarios
portfolio.addCommonCause("Regional Storm", 0.05, ["Platform A", "Platform B"], 0.5)  # 5% probability, 50% production loss
portfolio.addCommonCause("Pipeline Issue", 0.02, ["Platform A", "Platform B", "Platform C"], 0.8)
portfolio.addCommonCause("Market Curtailment", 0.1, ["Platform A", "Platform B", "Platform C", "FPSO Alpha"], 0.2)

# Set correlation matrix (simplified)
portfolio.setAssetCorrelation("Platform A", "Platform B", 0.6)   # Adjacent platforms
portfolio.setAssetCorrelation("Platform A", "Platform C", 0.3)
portfolio.setAssetCorrelation("Platform B", "Platform C", 0.4)
portfolio.setAssetCorrelation("FPSO Alpha", "Platform A", 0.2)   # Different field

print(f"Portfolio configured with {portfolio.getAssetCount()} assets")
print(f"Total design capacity: {portfolio.getTotalDesignCapacity():.0f} MMscf/day")
```

```python
# Run portfolio Monte Carlo
portfolio.setIterations(10000)
portfolio.setSimulationHorizon(365)  # 1 year

portfolio_result = portfolio.runSimulation()

print("\n=== Portfolio Risk Analysis Results ===")
print(f"\nAnnual Production Statistics (MMscf):")
print(f"  Expected: {portfolio_result.getExpectedProduction():.0f}")
print(f"  P10: {portfolio_result.getP10():.0f}")
print(f"  P50: {portfolio_result.getP50():.0f}")
print(f"  P90: {portfolio_result.getP90():.0f}")
print(f"  Standard Deviation: {portfolio_result.getStandardDeviation():.0f}")

print(f"\nValue at Risk (VaR):")
gas_price = 5.0  # $/MMscf
print(f"  VaR (95%): ${portfolio_result.getVaR95() * gas_price:,.0f}")
print(f"  VaR (99%): ${portfolio_result.getVaR99() * gas_price:,.0f}")
print(f"  CVaR (95%): ${portfolio_result.getCVaR95() * gas_price:,.0f}")

print(f"\nDiversification Benefit: {portfolio_result.getDiversificationBenefit()*100:.1f}%")
```

```python
# Breakdown by asset
print("\n=== Asset Risk Contribution ===")
print(f"{'Asset':<15} {'Expected':>12} {'Std Dev':>10} {'VaR 95%':>12} {'Contribution':>12}")
print("-" * 65)

for asset_result in portfolio_result.getAssetResults():
    print(f"{asset_result.getAssetName():<15} "
          f"{asset_result.getExpectedProduction():>12,.0f} "
          f"{asset_result.getStandardDeviation():>10,.0f} "
          f"{asset_result.getVaR95():>12,.0f} "
          f"{asset_result.getRiskContribution()*100:>11.1f}%")
```

---

## P6: Condition-Based Reliability

The `ConditionBasedReliability` class adjusts failure rates based on real-time equipment
condition monitoring data.

```python
# Create Condition-Based Reliability model
cbr = ConditionBasedReliability("C-200", "Export Compressor")

# Set baseline reliability
cbr.setBaselineMTBF(12000)  # hours
cbr.setInstallationDate("2020-01-15")
cbr.setOperatingHours(15000)

# Add condition indicators
cbr.addIndicator("vibration", 5.0, 15.0, ConditionBasedReliability.DegradationModel.LINEAR)
cbr.addIndicator("bearing_temp", 60.0, 90.0, ConditionBasedReliability.DegradationModel.EXPONENTIAL)
cbr.addIndicator("oil_particulates", 0.0, 100.0, ConditionBasedReliability.DegradationModel.LINEAR)
cbr.addIndicator("efficiency", 85.0, 70.0, ConditionBasedReliability.DegradationModel.LINEAR)  # Decreasing is bad

# Set indicator weights
cbr.setIndicatorWeight("vibration", 0.35)
cbr.setIndicatorWeight("bearing_temp", 0.25)
cbr.setIndicatorWeight("oil_particulates", 0.20)
cbr.setIndicatorWeight("efficiency", 0.20)

print("Condition-Based Reliability model configured")
print(f"  Equipment: {cbr.getEquipmentId()} - {cbr.getEquipmentName()}")
print(f"  Baseline MTBF: {cbr.getBaselineMTBF()} hours")
print(f"  Operating hours: {cbr.getOperatingHours()}")
```

```python
# Update with current condition readings
current_conditions = HashMap()
current_conditions.put("vibration", 8.5)        # Elevated
current_conditions.put("bearing_temp", 72.0)    # Slightly elevated
current_conditions.put("oil_particulates", 35.0) # Moderate
current_conditions.put("efficiency", 80.0)      # Slightly degraded

cbr.updateConditions(current_conditions)

# Calculate adjusted reliability
health_index = cbr.calculateHealthIndex()
adjusted_mtbf = cbr.calculateAdjustedMTBF()
rul = cbr.estimateRUL()  # Remaining Useful Life

print("\n=== Condition Assessment Results ===")
print(f"\nCurrent Condition Indicators:")
for indicator in cbr.getIndicators():
    status = "🟢" if indicator.getNormalizedValue() < 0.3 else "🟡" if indicator.getNormalizedValue() < 0.7 else "🔴"
    print(f"  {status} {indicator.getName()}: {indicator.getCurrentValue():.1f} "
          f"(Normalized: {indicator.getNormalizedValue()*100:.0f}%)")

print(f"\nOverall Health Index: {health_index*100:.1f}%")
print(f"Baseline MTBF: {cbr.getBaselineMTBF()} hours")
print(f"Adjusted MTBF: {adjusted_mtbf:.0f} hours")
print(f"Estimated RUL: {rul:.0f} hours ({rul/24:.0f} days)")
print(f"Recommended Action: {cbr.getRecommendedAction()}")
```

```python
# Trend analysis
print("\n=== Trend Analysis ===")

# Simulate historical data points
historical_vibration = [5.0, 5.2, 5.5, 6.0, 6.5, 7.2, 7.8, 8.5]
for i, vib in enumerate(historical_vibration):
    cbr.addHistoricalReading("vibration", vib, f"2024-0{i+1}-01")

trend = cbr.calculateTrend("vibration")
print(f"Vibration Trend: {trend.getDirection()} ({trend.getRateOfChange():.2f} per month)")
print(f"Projected time to alarm threshold: {trend.getTimeToThreshold():.0f} days")

# Failure probability
failure_prob_30d = cbr.calculateFailureProbability(30 * 24)  # 30 days
failure_prob_90d = cbr.calculateFailureProbability(90 * 24)  # 90 days
print(f"\nFailure Probability:")
print(f"  30 days: {failure_prob_30d*100:.1f}%")
print(f"  90 days: {failure_prob_90d*100:.1f}%")
```

---

## P7: ML/AI Integration

The `RiskMLInterface` provides a standardized way to integrate machine learning models
for failure prediction, anomaly detection, and RUL estimation.

```python
# Create ML Interface
ml_interface = RiskMLInterface("Platform Risk ML System")

# Register failure prediction model
failure_model = ml_interface.createFailurePredictionModel(
    "compressor-failure-v1",
    "Compressor Failure Predictor"
)
failure_model.setVersion("1.2.0")
failure_model.setAccuracy(0.92)
failure_model.addMetadata("framework", "scikit-learn")
failure_model.addMetadata("features", 12)

# Register anomaly detection model
anomaly_model = ml_interface.createAnomalyDetectionModel(
    "process-anomaly-v1",
    "Process Anomaly Detector"
)
anomaly_model.setVersion("2.0.0")
anomaly_model.addMetadata("framework", "TensorFlow")

# Register RUL prediction model
rul_model = ml_interface.createRULModel(
    "pump-rul-v1",
    "Pump RUL Estimator"
)

print("ML Models Registered:")
for model in ml_interface.getModels().values():
    print(f"  - {model.getModelId()}: {model.getModelName()} ({model.getModelType()})")
```

```python
# Define a predictor function (in real use, this would call your trained model)
class FailurePredictor(RiskMLInterface.MLPredictor):
    def predict(self, features):
        # Simple heuristic predictor (replace with actual ML model)
        prediction = RiskMLInterface.MLPrediction("compressor-failure-v1")
        
        # Calculate risk score based on features
        vibration = features.get("vibration") or 0
        temperature = features.get("temperature") or 0
        pressure = features.get("pressure") or 0
        operating_hours = features.get("operating_hours") or 0
        
        # Weighted risk calculation
        risk_score = (
            0.35 * min(vibration / 15.0, 1.0) +
            0.25 * min(max(temperature - 60, 0) / 30.0, 1.0) +
            0.20 * min(max(pressure - 80, 0) / 20.0, 1.0) +
            0.20 * min(operating_hours / 20000, 1.0)
        )
        
        prediction.setPrediction(risk_score)
        prediction.setConfidence(0.88)
        prediction.setLabel("HIGH_RISK" if risk_score > 0.7 else "MEDIUM_RISK" if risk_score > 0.3 else "LOW_RISK")
        
        # Feature importance
        importance = HashMap()
        importance.put("vibration", 0.35)
        importance.put("temperature", 0.25)
        importance.put("pressure", 0.20)
        importance.put("operating_hours", 0.20)
        prediction.setFeatureImportance(importance)
        
        return prediction

# Register the predictor
failure_model.setPredictor(FailurePredictor())

print("Failure predictor registered")
```

```python
# Make a prediction
features = HashMap()
features.put("vibration", 9.5)
features.put("temperature", 78.0)
features.put("pressure", 88.0)
features.put("operating_hours", 15000.0)

prediction = ml_interface.predict("compressor-failure-v1", features)

print("\n=== ML Prediction Results ===")
print(f"\nModel: {prediction.getModelId()}")
print(f"Prediction: {prediction.getPrediction():.3f}")
print(f"Label: {prediction.getLabel()}")
print(f"Confidence: {prediction.getConfidence()*100:.1f}%")

print("\nFeature Importance:")
for feature, importance in prediction.getFeatureImportance().items():
    bar = "█" * int(importance * 20)
    print(f"  {feature:<20} {bar} {importance*100:.0f}%")
```

```python
# Feature extraction from process data
def extract_compressor_features(process_data):
    features = HashMap()
    features.put("vibration", float(process_data.get("C-200.vibration")))
    features.put("temperature", float(process_data.get("C-200.bearing_temp")))
    features.put("pressure", float(process_data.get("C-200.discharge_pressure")))
    features.put("operating_hours", float(process_data.get("C-200.run_hours")))
    return features

ml_interface.registerFeatureExtractor("compressor", extract_compressor_features)

# Use with raw process data
raw_data = HashMap()
raw_data.put("C-200.vibration", 10.2)
raw_data.put("C-200.bearing_temp", 82.0)
raw_data.put("C-200.discharge_pressure", 92.0)
raw_data.put("C-200.run_hours", 16500.0)

prediction2 = ml_interface.predictWithExtraction("compressor-failure-v1", "compressor", raw_data)

print("\n=== Prediction from Raw Process Data ===")
print(f"Risk Score: {prediction2.getPrediction():.3f}")
print(f"Classification: {prediction2.getLabel()}")
```

---

## Integration Example: Complete Risk Workflow

This example shows how all components work together in a unified risk management workflow.

```python
# Integrated Risk Dashboard
print("="*60)
print("       INTEGRATED RISK MANAGEMENT DASHBOARD")
print("="*60)

# 1. Real-time condition assessment
print("\n📊 REAL-TIME EQUIPMENT STATUS")
print("-" * 40)
health = cbr.calculateHealthIndex()
print(f"Export Compressor Health: {health*100:.1f}%")
print(f"Estimated RUL: {cbr.estimateRUL():.0f} hours")

# 2. ML-based failure prediction
print("\n🤖 ML FAILURE PREDICTION")
print("-" * 40)
print(f"Risk Score: {prediction2.getPrediction():.3f}")
print(f"Classification: {prediction2.getLabel()}")

# 3. SIS status
print("\n🛡️ SAFETY SYSTEM STATUS")
print("-" * 40)
print(f"SIF-001 Status: ARMED")
print(f"SIL Target: {sif.getSILTarget()} | Achieved: {sil_result.getAchievedSIL()}")
print(f"PFDavg: {sif.calculatePFDavg():.2e}")

# 4. Portfolio risk summary
print("\n💰 PORTFOLIO RISK SUMMARY")
print("-" * 40)
print(f"Expected Production: {portfolio_result.getExpectedProduction():.0f} MMscf/year")
print(f"VaR (95%): ${portfolio_result.getVaR95() * gas_price:,.0f}")

# 5. Active alerts
print("\n⚠️ ACTIVE ALERTS")
print("-" * 40)
if alerts_received:
    for alert in alerts_received[-3:]:
        print(f"  [{alert.getLevel()}] {alert.getMessage()}")
else:
    print("  No active alerts")

print("\n" + "="*60)
```

## Summary

This tutorial demonstrated the advanced risk analysis capabilities in NeqSim:

| Feature | Key Capability | Industry Standard |
|---------|---------------|-------------------|
| P1: Dynamic Simulation | Transient loss modeling | - |
| P2: SIS/SIF Integration | LOPA, SIL verification | IEC 61508/61511 |
| P3: Real-time Monitoring | Continuous KRI tracking | - |
| P4: Bow-Tie Analysis | Barrier visualization | ISO 31000 |
| P5: Portfolio Risk | Multi-asset VaR | - |
| P6: Condition-Based | Predictive maintenance | ISO 14224 |
| P7: ML Integration | AI-powered prediction | - |

These tools integrate seamlessly with NeqSim's process simulation capabilities to provide
comprehensive risk assessment for oil & gas operations.

