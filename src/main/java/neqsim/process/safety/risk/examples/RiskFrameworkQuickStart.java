package neqsim.process.safety.risk.examples;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.safety.risk.OperationalRiskSimulator;
import neqsim.process.safety.risk.OperationalRiskResult;
import neqsim.process.safety.risk.RiskMatrix;
import neqsim.process.safety.risk.dynamic.DynamicRiskSimulator;
import neqsim.process.safety.risk.dynamic.DynamicRiskResult;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;
import neqsim.process.safety.risk.sis.SISIntegratedRiskModel;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.risk.bowtie.BowTieModel;
import neqsim.process.safety.risk.bowtie.BowTieAnalyzer;
import neqsim.process.safety.risk.portfolio.PortfolioRiskAnalyzer;
import neqsim.process.safety.risk.portfolio.PortfolioRiskResult;
import neqsim.process.safety.risk.condition.ConditionBasedReliability;
import neqsim.process.safety.risk.condition.ProcessEquipmentMonitor;
import neqsim.process.safety.risk.realtime.RealTimeRiskMonitor;
import neqsim.process.safety.risk.realtime.PhysicsBasedRiskMonitor;
import neqsim.process.safety.risk.ml.RiskMLInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quick-start examples for the NeqSim Advanced Risk Framework.
 *
 * <p>
 * This class provides runnable examples demonstrating all 7 priority features of the risk
 * framework. Each example is self-contained and can be run independently.
 * </p>
 *
 * <h2>Features Demonstrated</h2>
 * <ul>
 * <li>P1: Dynamic Simulation with Transients</li>
 * <li>P2: SIS/SIF Integration per IEC 61508/61511</li>
 * <li>P3: Real-time Digital Twin Monitoring</li>
 * <li>P4: Bow-Tie Diagram Analysis</li>
 * <li>P5: Multi-Asset Portfolio Risk</li>
 * <li>P6: Condition-Based Reliability</li>
 * <li>P7: ML/AI Integration Interface</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @since 3.3.0
 */
public class RiskFrameworkQuickStart {

  /**
   * Main entry point - runs all examples.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println("==============================================");
    System.out.println("NeqSim Risk Framework - Quick Start Examples");
    System.out.println("==============================================\n");

    exampleOperationalRiskSimulation();
    exampleDynamicSimulation();
    exampleSISIntegration();
    exampleRealTimeMonitoring();
    exampleBowTieAnalysis();
    examplePortfolioRisk();
    exampleConditionBasedReliability();
    exampleMLIntegration();

    System.out.println("\n==============================================");
    System.out.println("All examples completed successfully!");
    System.out.println("==============================================");
  }

  /**
   * Example: Basic Operational Risk Simulation.
   *
   * <p>
   * Demonstrates Monte Carlo simulation for production availability analysis.
   * </p>
   */
  public static void exampleOperationalRiskSimulation() {
    System.out.println("--- Example: Operational Risk Simulation ---");

    // Create a simple process system for demonstration
    ProcessSystem process = createSimpleProcessSystem();

    // Create simulator with process system
    OperationalRiskSimulator simulator = new OperationalRiskSimulator(process);
    simulator.setFeedStreamName("Feed");
    simulator.setProductStreamName("Gas Out");

    // Add equipment with reliability data (failure rate per year, MTTR hours)
    simulator.addEquipmentReliability("HP Compressor", 1.0, 72);
    simulator.addEquipmentReliability("HP Separator", 0.5, 24);
    simulator.addEquipmentReliability("Export Pump", 2.0, 48);

    // Run Monte Carlo simulation
    OperationalRiskResult result = simulator.runSimulation(1000, 365);

    System.out.println("  Availability: " + String.format("%.2f%%", result.getAvailability()));
    System.out
        .println("  Mean Production: " + String.format("%.0f Sm3", result.getMeanProduction()));
    System.out.println("  P10 Production: " + String.format("%.0f Sm3", result.getP10Production()));
    System.out.println("  P50 Production: " + String.format("%.0f Sm3", result.getP50Production()));
    System.out.println("  P90 Production: " + String.format("%.0f Sm3", result.getP90Production()));
    System.out.println();
  }

  /**
   * Example: Dynamic Simulation with Transients (P1).
   *
   * <p>
   * Demonstrates Monte Carlo with ramp-up/shutdown modeling for more accurate production loss
   * estimates.
   * </p>
   */
  public static void exampleDynamicSimulation() {
    System.out.println("--- Example: P1 - Dynamic Simulation with Transients ---");

    // Create a simple process system for dynamic simulation
    ProcessSystem process = createSimpleProcessSystem();

    DynamicRiskSimulator sim = new DynamicRiskSimulator(process);
    sim.setFeedStreamName("Feed");
    sim.setProductStreamName("Gas Out");

    // Add equipment with reliability data (failure rate per year, MTTR hours)
    sim.addEquipmentReliability("HP Compressor", 1.0, 72);
    sim.addEquipmentReliability("HP Separator", 0.5, 24);

    // Configure transient profiles
    sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL);
    sim.setRampUpProfile(DynamicRiskSimulator.RampProfile.S_CURVE);
    sim.setShutdownTimeHours(4.0); // 4 hours to shutdown
    sim.setRampUpTimeHours(8.0); // 8 hours to full rate

    // Run dynamic simulation
    DynamicRiskResult result = sim.runDynamicSimulation(1000, 365);

    System.out
        .println("  Mean Production: " + String.format("%.0f kg", result.getMeanProduction()));
    System.out.println(
        "  Steady-State Losses: " + String.format("%.0f kg", result.getMeanSteadyStateLoss()));
    System.out
        .println("  Transient Losses: " + String.format("%.0f kg", result.getMeanTransientLoss()));
    System.out.println("  Transient Events: " + result.getTotalTransientEvents());
    System.out.println(
        "  Transient % of Total: " + String.format("%.1f%%", result.getTransientLossPercent()));
    System.out.println();
  }

  /**
   * Example: SIS/SIF Integration (P2).
   *
   * <p>
   * Demonstrates Safety Instrumented Function modeling per IEC 61508/61511.
   * </p>
   */
  public static void exampleSISIntegration() {
    System.out.println("--- Example: P2 - SIS/SIF Integration (IEC 61508/61511) ---");

    // Create a Safety Instrumented Function using builder
    SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder().name("ESD-001")
        .description("High Pressure Emergency Shutdown").sil(2).pfd(0.005) // PFD within SIL 2 range
        .architecture("1oo2").testIntervalHours(8760) // Annual testing
        .build();

    System.out.println("  SIF: " + sif.getName());
    System.out.println("  SIL: " + sif.getSil());
    System.out.println("  Architecture: " + sif.getArchitecture());
    System.out.println("  Calculated PFDavg: " + String.format("%.2e", sif.getPfdAvg()));
    System.out
        .println("  Risk Reduction Factor: " + String.format("%.0f", sif.getRiskReductionFactor()));
    System.out
        .println("  Spurious Trip Rate: " + String.format("%.4f /yr", sif.getSpuriousTripRate()));

    // LOPA Analysis - create model and add initiating event first
    SISIntegratedRiskModel model = new SISIntegratedRiskModel("HP Vessel Overpressure");
    model.addInitiatingEvent("Overpressure", 0.1,
        neqsim.process.safety.risk.RiskEvent.ConsequenceCategory.MAJOR); // 0.1 per year

    // Add Independent Protection Layers
    SISIntegratedRiskModel.IndependentProtectionLayer bpcs =
        new SISIntegratedRiskModel.IndependentProtectionLayer("BPCS", 0.1,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.BPCS);
    bpcs.addApplicableEvent("Overpressure");
    model.addIPL(bpcs);

    SISIntegratedRiskModel.IndependentProtectionLayer reliefValve =
        new SISIntegratedRiskModel.IndependentProtectionLayer("Relief Valve", 0.01,
            SISIntegratedRiskModel.IndependentProtectionLayer.IPLType.MECHANICAL);
    reliefValve.addApplicableEvent("Overpressure");
    model.addIPL(reliefValve);

    // Add the SIF with initiating event reference
    sif = SafetyInstrumentedFunction.builder().name("ESD-001").sil(2).pfd(0.005)
        .initiatingEvent("Overpressure").build();
    model.addSIF(sif);

    LOPAResult lopa = model.performLOPA("Overpressure");
    System.out.println(
        "  LOPA - Mitigated Frequency: " + String.format("%.2e /yr", lopa.getMitigatedFrequency()));
    System.out.println("  LOPA - Meets Target: " + lopa.isTargetMet());
    System.out.println();
  }

  /**
   * Example: Real-time Digital Twin Monitoring (P3).
   *
   * <p>
   * Demonstrates continuous risk monitoring with physics-based integration.
   * </p>
   */
  public static void exampleRealTimeMonitoring() {
    System.out.println("--- Example: P3 - Real-time Digital Twin Monitoring ---");

    // Create a simple process for demonstration
    SystemInterface fluid = new SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    // Create physics-based risk monitor
    PhysicsBasedRiskMonitor monitor = new PhysicsBasedRiskMonitor(process);
    monitor.setBaseFailureRate("HP Separator", 0.0001);
    monitor.setDesignTemperatureRange("HP Separator", 273.15, 373.15);
    monitor.setDesignPressureRange("HP Separator", 1.0, 100.0);

    // Run process to get physics values
    process.run();

    // Assess risk based on physics
    PhysicsBasedRiskMonitor.PhysicsBasedRiskAssessment assessment = monitor.assess();

    System.out.println(
        "  Overall Risk Score: " + String.format("%.2f", assessment.getOverallRiskScore()));
    System.out.println("  System Capacity Margin: "
        + String.format("%.1f%%", assessment.getSystemCapacityMargin() * 100));
    System.out.println("  Highest Risk Equipment: " + assessment.getHighestRiskEquipment());
    System.out.println("  Warnings: " + assessment.getWarnings().size());
    System.out.println();
  }

  /**
   * Example: Bow-Tie Analysis (P4).
   *
   * <p>
   * Demonstrates barrier analysis with threat-consequence modeling.
   * </p>
   */
  public static void exampleBowTieAnalysis() {
    System.out.println("--- Example: P4 - Bow-Tie Analysis ---");

    // Create bow-tie model for vessel rupture
    BowTieModel bowtie = new BowTieModel("PVR-001", "Loss of containment from HP separator");

    // Add threats (left side) using Threat objects
    BowTieModel.Threat overpressure = new BowTieModel.Threat("T-001", "Overpressure", 0.01);
    BowTieModel.Threat corrosion = new BowTieModel.Threat("T-002", "Corrosion", 0.005);
    BowTieModel.Threat externalImpact = new BowTieModel.Threat("T-003", "External Impact", 0.001);
    bowtie.addThreat(overpressure);
    bowtie.addThreat(corrosion);
    bowtie.addThreat(externalImpact);

    // Add prevention barriers
    BowTieModel.Barrier psv = new BowTieModel.Barrier("B-001", "Pressure Relief Valve", 0.01);
    psv.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    BowTieModel.Barrier alarm = new BowTieModel.Barrier("B-002", "High Pressure Alarm", 0.1);
    alarm.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    BowTieModel.Barrier inspection = new BowTieModel.Barrier("B-003", "Inspection Program", 0.05);
    inspection.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    BowTieModel.Barrier physicalProtection =
        new BowTieModel.Barrier("B-004", "Physical Protection", 0.2);
    physicalProtection.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    bowtie.addBarrier(psv);
    bowtie.addBarrier(alarm);
    bowtie.addBarrier(inspection);
    bowtie.addBarrier(physicalProtection);

    // Link prevention barriers to threats
    bowtie.linkBarrierToThreat("T-001", "B-001");
    bowtie.linkBarrierToThreat("T-001", "B-002");
    bowtie.linkBarrierToThreat("T-002", "B-003");
    bowtie.linkBarrierToThreat("T-003", "B-004");

    // Add consequences (right side)
    BowTieModel.Consequence fireExplosion =
        new BowTieModel.Consequence("C-001", "Fire/Explosion", 5);
    BowTieModel.Consequence envRelease =
        new BowTieModel.Consequence("C-002", "Environmental Release", 4);
    BowTieModel.Consequence injury = new BowTieModel.Consequence("C-003", "Personnel Injury", 5);
    bowtie.addConsequence(fireExplosion);
    bowtie.addConsequence(envRelease);
    bowtie.addConsequence(injury);

    // Add mitigation barriers
    BowTieModel.Barrier fireDetection = new BowTieModel.Barrier("B-005", "Fire Detection", 0.05);
    fireDetection.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    BowTieModel.Barrier deluge = new BowTieModel.Barrier("B-006", "Deluge System", 0.1);
    deluge.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    BowTieModel.Barrier containment =
        new BowTieModel.Barrier("B-007", "Secondary Containment", 0.15);
    containment.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    BowTieModel.Barrier evacuation =
        new BowTieModel.Barrier("B-008", "Evacuation Procedures", 0.05);
    evacuation.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    bowtie.addBarrier(fireDetection);
    bowtie.addBarrier(deluge);
    bowtie.addBarrier(containment);
    bowtie.addBarrier(evacuation);

    // Link mitigation barriers to consequences
    bowtie.linkBarrierToConsequence("C-001", "B-005");
    bowtie.linkBarrierToConsequence("C-001", "B-006");
    bowtie.linkBarrierToConsequence("C-002", "B-007");
    bowtie.linkBarrierToConsequence("C-003", "B-008");

    // Calculate risk (calls internal analyze method)
    bowtie.calculate();

    System.out.println("  Hazard: " + bowtie.getHazardDescription());
    System.out.println(
        "  Unmitigated Frequency: " + String.format("%.4f /yr", bowtie.getUnmitigatedFrequency()));
    System.out.println(
        "  Mitigated Frequency: " + String.format("%.6f /yr", bowtie.getMitigatedFrequency()));
    double riskReduction = bowtie.getUnmitigatedFrequency() > 0
        ? bowtie.getUnmitigatedFrequency() / Math.max(bowtie.getMitigatedFrequency(), 1e-10)
        : 1.0;
    System.out.println("  Risk Reduction: " + String.format("%.0fx", riskReduction));
    System.out.println("  Number of Threats: " + bowtie.getThreats().size());
    System.out.println();
  }

  /**
   * Example: Multi-Asset Portfolio Risk (P5).
   *
   * <p>
   * Demonstrates portfolio-level risk analysis with common cause scenarios.
   * </p>
   */
  public static void examplePortfolioRisk() {
    System.out.println("--- Example: P5 - Multi-Asset Portfolio Risk ---");

    PortfolioRiskAnalyzer portfolio = new PortfolioRiskAnalyzer("North Sea Portfolio");

    // Add assets
    portfolio.addAsset("Platform Alpha", 50000, 0.95, 80.0);
    portfolio.addAsset("Platform Beta", 30000, 0.92, 80.0);
    portfolio.addAsset("FPSO Gamma", 75000, 0.90, 80.0);

    // Add common cause scenarios using proper CommonCauseScenario objects
    PortfolioRiskAnalyzer.CommonCauseScenario weatherScenario =
        new PortfolioRiskAnalyzer.CommonCauseScenario("WEATHER-NS", "Severe North Sea Weather",
            PortfolioRiskAnalyzer.CommonCauseScenario.CommonCauseType.WEATHER, 0.1);
    weatherScenario.addAffectedAsset("Platform Alpha", 0.3);
    weatherScenario.addAffectedAsset("Platform Beta", 0.3);
    weatherScenario.addAffectedAsset("FPSO Gamma", 0.2);
    portfolio.addCommonCauseScenario(weatherScenario);

    PortfolioRiskAnalyzer.CommonCauseScenario cyberScenario =
        new PortfolioRiskAnalyzer.CommonCauseScenario("CYBER-001", "Cyber Attack on OT Systems",
            PortfolioRiskAnalyzer.CommonCauseScenario.CommonCauseType.CYBER, 0.05);
    cyberScenario.addAffectedAsset("Platform Alpha", 0.5);
    cyberScenario.addAffectedAsset("Platform Beta", 0.5);
    cyberScenario.addAffectedAsset("FPSO Gamma", 0.5);
    portfolio.addCommonCauseScenario(cyberScenario);

    // Run analysis (uses default iterations and period from the analyzer)
    PortfolioRiskResult result = portfolio.run();

    System.out.println("  Total Expected Production: "
        + String.format("%.0f bbl", result.getTotalExpectedProduction()));
    System.out
        .println("  Portfolio VaR (95%): $" + String.format("%.0f", result.getValueAtRisk(95)));
    System.out.println("  Diversification Benefit: "
        + String.format("%.1f%%", result.getDiversificationBenefit() * 100));
    System.out.println("  Common Cause Fraction: "
        + String.format("%.1f%%", result.getCommonCauseFraction() * 100));
    System.out.println();
  }

  /**
   * Example: Condition-Based Reliability (P6).
   *
   * <p>
   * Demonstrates equipment health monitoring with degradation models.
   * </p>
   */
  public static void exampleConditionBasedReliability() {
    System.out.println("--- Example: P6 - Condition-Based Reliability ---");

    ConditionBasedReliability cbr =
        new ConditionBasedReliability("COMP-001", "Export Compressor", 0.0001);
    cbr.setDegradationModel(ConditionBasedReliability.DegradationModel.WEIBULL);

    // Add condition indicators using convenience methods
    ConditionBasedReliability.ConditionIndicator vibration =
        cbr.addVibrationIndicator("VIB-001", "Bearing Vibration", 1.0, 5.0, 10.0);
    vibration.updateValue(2.5); // Current value

    ConditionBasedReliability.ConditionIndicator temp =
        cbr.addTemperatureIndicator("TEMP-001", "Bearing Temperature", 60.0, 95.0, 110.0);
    temp.updateValue(85.0); // Current value

    // Recalculate health based on updated indicators
    cbr.recalculateHealth();

    System.out.println("  Equipment: " + cbr.getEquipmentName());
    System.out.println("  Health Index: " + String.format("%.2f", cbr.getHealthIndex()));
    System.out.println(
        "  Adjusted Failure Rate: " + String.format("%.6f /hr", cbr.getAdjustedFailureRate()));
    System.out.println(
        "  Remaining Useful Life: " + String.format("%.0f hours", cbr.getRemainingUsefulLife()));
    String priority = cbr.getHealthIndex() > 0.8 ? "LOW"
        : cbr.getHealthIndex() > 0.5 ? "MEDIUM" : cbr.getHealthIndex() > 0.2 ? "HIGH" : "CRITICAL";
    System.out.println("  Maintenance Priority: " + priority);
    System.out.println();
  }

  /**
   * Example: ML/AI Integration (P7).
   *
   * <p>
   * Demonstrates the interface for integrating machine learning models.
   * </p>
   */
  public static void exampleMLIntegration() {
    System.out.println("--- Example: P7 - ML/AI Integration Interface ---");

    RiskMLInterface mlInterface = new RiskMLInterface("Risk ML Interface");

    // Register feature extractors using the functional interface
    mlInterface.registerFeatureExtractor("process_features", processData -> {
      // Extract features from process data map
      Map<String, Double> features = new HashMap<>();
      features.put("temperature",
          processData.containsKey("temperature")
              ? ((Number) processData.get("temperature")).doubleValue()
              : 85.0);
      features.put("pressure",
          processData.containsKey("pressure") ? ((Number) processData.get("pressure")).doubleValue()
              : 45.0);
      features.put("vibration",
          processData.containsKey("vibration")
              ? ((Number) processData.get("vibration")).doubleValue()
              : 2.5);
      features.put("flow_rate",
          processData.containsKey("flow_rate")
              ? ((Number) processData.get("flow_rate")).doubleValue()
              : 1000.0);
      return features;
    });

    // Create anomaly detection model
    RiskMLInterface.MLModel anomalyModel =
        mlInterface.createAnomalyDetectionModel("anomaly_detector", "Anomaly Detector");
    anomalyModel.setPredictor(features -> {
      // Simple anomaly score based on thresholds
      double temp = features.getOrDefault("temperature", 0.0);
      double vib = features.getOrDefault("vibration", 0.0);
      double score = 0.0;
      if (temp > 90)
        score += 0.3;
      if (vib > 3.0)
        score += 0.4;
      RiskMLInterface.MLPrediction pred =
          new RiskMLInterface.MLPrediction(anomalyModel.getModelId());
      pred.setPrediction(score);
      pred.setConfidence(0.85);
      pred.setLabel(score > 0.5 ? "ANOMALY" : "NORMAL");
      return pred;
    });

    // Create failure prediction model
    RiskMLInterface.MLModel failureModel =
        mlInterface.createFailurePredictionModel("failure_predictor", "Failure Predictor");
    failureModel.setPredictor(features -> {
      // Simple failure probability
      double temp = features.getOrDefault("temperature", 0.0);
      double prob = temp > 100 ? 0.8 : 0.1;
      RiskMLInterface.MLPrediction pred =
          new RiskMLInterface.MLPrediction(failureModel.getModelId());
      pred.setPrediction(prob);
      pred.setConfidence(0.9);
      pred.setLabel(prob > 0.5 ? "HIGH_RISK" : "LOW_RISK");
      return pred;
    });

    // Run predictions with explicit features
    Map<String, Double> testFeatures = new HashMap<>();
    testFeatures.put("temperature", 85.0);
    testFeatures.put("pressure", 45.0);
    testFeatures.put("vibration", 2.5);
    testFeatures.put("flow_rate", 1000.0);

    RiskMLInterface.MLPrediction anomalyResult =
        mlInterface.predict("anomaly_detector", testFeatures);
    RiskMLInterface.MLPrediction failureResult =
        mlInterface.predict("failure_predictor", testFeatures);

    System.out.println("  Registered Models: " + mlInterface.getModels().size());
    System.out.println("  Anomaly Score: " + String.format("%.2f", anomalyResult.getPrediction()));
    System.out.println("  Anomaly Label: " + anomalyResult.getLabel());
    System.out
        .println("  Failure Probability: " + String.format("%.2f", failureResult.getPrediction()));
    System.out.println("  Failure Label: " + failureResult.getLabel());
    System.out.println();
    System.out.println("  Note: Replace placeholder models with trained TensorFlow/PyTorch");
    System.out.println("        models for production use via registerModel().");
    System.out.println();
  }

  /**
   * Creates a simple process system for demonstration.
   *
   * @return a ProcessSystem with basic equipment
   */
  private static ProcessSystem createSimpleProcessSystem() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("HP Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0);
    process.add(compressor);

    Stream gasOut = new Stream("Gas Out", compressor.getOutletStream());
    process.add(gasOut);

    process.run();
    return process;
  }
}
