"""
NeqSim Advanced Risk Framework - Python Quick Start Examples

This module demonstrates basic usage of the NeqSim advanced risk analysis
framework via JPype. Each example is self-contained and can be run independently.

Prerequisites:
    - neqsim-python package installed
    - JPype properly configured

Usage:
    python risk_framework_examples.py
"""

import jpype
import jpype.imports

# Start JVM if not running
if not jpype.isJVMStarted():
    try:
        import neqsim
        # JVM should be started by neqsim import
    except ImportError:
        # Manual JVM start if neqsim not properly configured
        jpype.startJVM(classpath=['neqsim-*.jar'])

from java.util import HashMap, ArrayList


def example_dynamic_simulation():
    """P1: Dynamic Risk Simulation with Transient Modeling.
    
    Demonstrates Monte Carlo simulation that captures shutdown and startup
    transient losses in addition to steady-state production losses.
    """
    print("\n--- P1: Dynamic Risk Simulation ---")
    
    from neqsim.process.safety.risk.dynamic import DynamicRiskSimulator
    
    # Create simulator
    simulator = DynamicRiskSimulator("Offshore Platform Risk")
    simulator.setBaseProductionRate(100.0)  # MMscf/day
    simulator.setProductionUnit("MMscf/day")
    
    # Add equipment with MTBF (hours), repair time (hours), production impact (0-1)
    simulator.addEquipment("Export Compressor", 8760, 72, 1.0)   # Full shutdown
    simulator.addEquipment("HP Separator", 17520, 24, 0.6)       # 60% impact
    simulator.addEquipment("Glycol Pump", 4380, 8, 0.1)          # 10% impact
    
    # Configure transient behavior
    simulator.setShutdownProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL)
    simulator.setStartupProfile(DynamicRiskSimulator.RampProfile.S_CURVE)
    simulator.setShutdownTime(4.0)  # hours
    simulator.setStartupTime(8.0)   # hours
    
    # Run simulation
    simulator.setSimulationHorizon(8760)  # 1 year
    simulator.setIterations(1000)
    result = simulator.runSimulation()
    
    # Display results
    print(f"Expected annual production: {result.getExpectedProduction():.0f} MMscf")
    print(f"P90 (conservative): {result.getP90Production():.0f} MMscf")
    print(f"Transient losses: {result.getTransientLoss().getTotalTransientLoss():.0f} MMscf")
    print(f"Availability: {result.getAvailability()*100:.1f}%")


def example_sis_integration():
    """P2: Safety Instrumented System Integration.
    
    Demonstrates LOPA analysis and SIL verification per IEC 61508/61511.
    """
    print("\n--- P2: SIS/SIF Integration ---")
    
    from neqsim.process.safety.risk.sis import (
        SISIntegratedRiskModel, 
        SafetyInstrumentedFunction
    )
    
    # Create SIS-integrated risk model
    model = SISIntegratedRiskModel("Separator Overpressure")
    model.setInitiatingEventFrequency(0.1)  # per year
    model.setConsequenceCategory("C4")       # Major
    
    # Add protection layers
    model.addIPL("BPCS Alarm", 10)        # RRF = 10
    model.addIPL("Operator Response", 10)
    model.addIPL("PSV Relief", 100)
    
    # Add Safety Instrumented Function
    sif = SafetyInstrumentedFunction("SIF-001", "PAHH Shutdown")
    sif.setSILTarget(2)
    sif.setArchitecture("1oo2")
    sif.setSensorPFD(0.01)
    sif.setLogicSolverPFD(0.001)
    sif.setFinalElementPFD(0.02)
    model.addSIF(sif)
    
    # Perform LOPA
    lopa = model.performLOPA()
    
    print(f"Initiating event frequency: 0.1 /year")
    print(f"Mitigated frequency: {lopa.getMitigatedFrequency():.2e} /year")
    print(f"LOPA Status: {'PASS' if lopa.isAcceptable() else 'FAIL'}")
    print(f"SIF PFDavg: {sif.calculatePFDavg():.2e}")


def example_realtime_monitoring():
    """P3: Real-Time Risk Monitoring.
    
    Demonstrates continuous monitoring with configurable alert thresholds.
    """
    print("\n--- P3: Real-Time Risk Monitoring ---")
    
    from neqsim.process.safety.risk.realtime import RealTimeRiskMonitor
    
    # Create monitor
    monitor = RealTimeRiskMonitor("Platform Alpha", "PA-001")
    
    # Register equipment
    monitor.registerEquipment("P-100", "Export Pump", 8760.0)
    monitor.registerEquipment("C-200", "Compressor", 12000.0)
    
    # Configure alert thresholds
    vib_thresholds = RealTimeRiskMonitor.AlertThresholds()
    vib_thresholds.setWarningHigh(7.0)
    vib_thresholds.setAlarmHigh(10.0)
    monitor.setAlertThresholds("vibration", vib_thresholds)
    
    # Update with process data
    process_data = HashMap()
    process_data.put("P-100.vibration", 8.5)  # Elevated!
    process_data.put("C-200.vibration", 4.0)  # Normal
    monitor.updateProcessVariables(process_data)
    
    # Perform assessment
    assessment = monitor.performAssessment()
    
    print(f"Overall risk level: {assessment.getOverallRiskLevel()}")
    print("Equipment statuses:")
    for status in assessment.getEquipmentStatuses():
        print(f"  {status.getEquipmentId()}: Health={status.getHealthIndex():.2f}")


def example_bowtie_analysis():
    """P4: Bow-Tie Diagram Analysis.
    
    Demonstrates barrier analysis connecting threats to consequences.
    """
    print("\n--- P4: Bow-Tie Analysis ---")
    
    from neqsim.process.safety.risk.bowtie import BowTieAnalyzer
    
    # Create analyzer
    analyzer = BowTieAnalyzer("Loss of Containment")
    analyzer.setTopEvent("Hydrocarbon Release")
    
    # Add threats
    analyzer.addThreat("T1", "Corrosion", 0.001)
    analyzer.addThreat("T2", "Overpressure", 0.01)
    
    # Add prevention barriers
    analyzer.addPreventionBarrier("B1", "Inspection Program", 0.1, ["T1"])
    analyzer.addPreventionBarrier("B2", "PSV Protection", 0.01, ["T2"])
    
    # Add consequences
    analyzer.addConsequence("C1", "Fire", "Safety", 1000000)
    analyzer.addConsequence("C2", "Environmental Damage", "Environmental", 500000)
    
    # Add mitigation barriers
    analyzer.addMitigationBarrier("M1", "Fire Detection", 0.1, ["C1"])
    analyzer.addMitigationBarrier("M2", "ESD System", 0.05, ["C1", "C2"])
    
    # Analyze
    model = analyzer.analyze()
    
    print(f"Top Event: {model.getTopEvent()}")
    print(f"Unmitigated frequency: {model.getUnmitigatedFrequency():.2e}")
    print(f"Mitigated frequency: {model.getMitigatedFrequency():.2e}")
    print(f"Prevention barriers: {model.getPreventionBarriers().size()}")
    print(f"Mitigation barriers: {model.getMitigationBarriers().size()}")


def example_portfolio_risk():
    """P5: Portfolio Risk Analysis.
    
    Demonstrates multi-asset risk aggregation with VaR metrics.
    """
    print("\n--- P5: Portfolio Risk Analysis ---")
    
    from neqsim.process.safety.risk.portfolio import PortfolioRiskAnalyzer
    
    # Create portfolio analyzer
    portfolio = PortfolioRiskAnalyzer("North Sea Assets")
    
    # Add assets (name, design capacity, expected production, std dev)
    portfolio.addAsset("Platform A", 50.0, 45.0, 5.0)
    portfolio.addAsset("Platform B", 75.0, 70.0, 8.0)
    portfolio.addAsset("FPSO Alpha", 100.0, 92.0, 12.0)
    
    # Add common cause scenario
    portfolio.addCommonCause("Regional Storm", 0.05, 
                             ["Platform A", "Platform B"], 0.5)
    
    # Set correlation
    portfolio.setAssetCorrelation("Platform A", "Platform B", 0.6)
    
    # Run simulation
    portfolio.setIterations(5000)
    result = portfolio.runSimulation()
    
    print(f"Expected production: {result.getExpectedProduction():.0f} MMscf")
    print(f"VaR (95%): {result.getVaR95():.0f} MMscf")
    print(f"Diversification benefit: {result.getDiversificationBenefit()*100:.1f}%")


def example_condition_based_reliability():
    """P6: Condition-Based Reliability.
    
    Demonstrates health monitoring and RUL estimation.
    """
    print("\n--- P6: Condition-Based Reliability ---")
    
    from neqsim.process.safety.risk.condition import ConditionBasedReliability
    
    # Create CBR model
    cbr = ConditionBasedReliability("C-200", "Export Compressor")
    cbr.setBaselineMTBF(12000)  # hours
    cbr.setOperatingHours(15000)
    
    # Add condition indicators
    cbr.addIndicator("vibration", 5.0, 15.0, 
                     ConditionBasedReliability.DegradationModel.LINEAR)
    cbr.addIndicator("temperature", 60.0, 90.0, 
                     ConditionBasedReliability.DegradationModel.EXPONENTIAL)
    
    cbr.setIndicatorWeight("vibration", 0.6)
    cbr.setIndicatorWeight("temperature", 0.4)
    
    # Update conditions
    conditions = HashMap()
    conditions.put("vibration", 9.0)      # Elevated
    conditions.put("temperature", 72.0)   # Slightly elevated
    cbr.updateConditions(conditions)
    
    # Calculate
    health = cbr.calculateHealthIndex()
    adjusted_mtbf = cbr.calculateAdjustedMTBF()
    rul = cbr.estimateRUL()
    
    print(f"Health Index: {health*100:.1f}%")
    print(f"Adjusted MTBF: {adjusted_mtbf:.0f} hours")
    print(f"Estimated RUL: {rul:.0f} hours ({rul/24:.0f} days)")
    print(f"Recommended: {cbr.getRecommendedAction()}")


def example_ml_integration():
    """P7: ML/AI Integration.
    
    Demonstrates machine learning model registration and prediction.
    """
    print("\n--- P7: ML/AI Integration ---")
    
    from neqsim.process.safety.risk.ml import RiskMLInterface
    
    # Create ML interface
    ml_interface = RiskMLInterface("Platform Risk ML")
    
    # Register a failure prediction model
    model = ml_interface.createFailurePredictionModel(
        "compressor-failure-v1",
        "Compressor Failure Predictor"
    )
    model.setVersion("1.0.0")
    model.setAccuracy(0.92)
    
    # Create anomaly detection model
    anomaly_model = ml_interface.createAnomalyDetectionModel(
        "anomaly-v1",
        "Process Anomaly Detector"
    )
    
    print(f"ML Interface: {ml_interface.getName()}")
    print(f"Registered models: {ml_interface.getModels().size()}")
    for m in ml_interface.getModels().values():
        print(f"  - {m.getModelId()}: {m.getModelName()} ({m.getModelType()})")


def main():
    """Run all examples."""
    print("=" * 60)
    print("     NeqSim Risk Framework - Python Quick Start")
    print("=" * 60)
    
    # Run all examples
    try:
        example_dynamic_simulation()
        example_sis_integration()
        example_realtime_monitoring()
        example_bowtie_analysis()
        example_portfolio_risk()
        example_condition_based_reliability()
        example_ml_integration()
        
        print("\n" + "=" * 60)
        print("All examples completed successfully!")
        print("=" * 60)
    except Exception as e:
        print(f"\nError: {e}")
        print("Make sure NeqSim is properly installed with the risk framework.")
        raise


if __name__ == "__main__":
    main()
