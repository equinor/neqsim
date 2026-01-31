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

    // Create simulator
    OperationalRiskSimulator simulator = new OperationalRiskSimulator("Platform Risk");
    simulator.setBaseProductionRate(100000.0); // 100,000 Sm3/d

    // Add equipment with reliability data (MTBF hours, MTTR hours, criticality)
    simulator.addEquipmentReliability("HP Compressor", 8760, 72, 1.0);
    simulator.addEquipmentReliability("HP Separator", 17520, 24, 0.8);
    simulator.addEquipmentReliability("Export Pump", 4380, 48, 0.6);

    // Run Monte Carlo simulation
    OperationalRiskResult result = simulator.runSimulation(1000, 365);

    System.out.println("  Availability: " + String.format("%.2f%%", result.getAvailability()));
    System.out.println(
        "  Expected Production: " + String.format("%.0f Sm3", result.getExpectedProduction()));
    System.out.println("  P10 Loss: " + String.format("%.0f Sm3", result.getProductionLossP10()));
    System.out.println("  P50 Loss: " + String.format("%.0f Sm3", result.getProductionLossP50()));
    System.out.println("  P90 Loss: " + String.format("%.0f Sm3", result.getProductionLossP90()));
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

    DynamicRiskSimulator sim = new DynamicRiskSimulator("Offshore Platform");
    sim.setBaseProductionRate(50000.0); // 50,000 bbl/d

    // Add equipment
    sim.addEquipment("Gas Compressor", 8760, 72, 1.0);
    sim.addEquipment("Oil Export Pump", 4380, 48, 0.8);
    sim.addEquipment("Water Injection Pump", 6000, 36, 0.5);

    // Configure transient profiles
    sim.setShutdownProfile(DynamicRiskSimulator.RampProfile.EXPONENTIAL);
    sim.setStartupProfile(DynamicRiskSimulator.RampProfile.S_CURVE);
    sim.setShutdownDuration(4.0); // 4 hours to shutdown
    sim.setStartupDuration(8.0); // 8 hours to full rate

    // Run simulation
    DynamicRiskResult result = sim.runSimulation(1000, 365);

    System.out.println(
        "  Expected Production: " + String.format("%.0f bbl", result.getExpectedProduction()));
    System.out.println(
        "  Steady-State Losses: " + String.format("%.0f bbl", result.getSteadyStateLoss()));
    System.out.println("  Transient Losses: "
        + String.format("%.0f bbl", result.getTransientLoss().getTotalTransientLoss()));
    System.out.println("  Shutdown Events: " + result.getTransientLoss().getShutdownCount());
    System.out.println("  Transient % of Total: "
        + String.format("%.1f%%", result.getTransientLoss().getTransientLossPercentage()));
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
    SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder("ESD-001")
        .description("High Pressure Emergency Shutdown").targetSIL(2).architecture("1oo2")
        .sensorFailureRate(1e-6).logicSolverFailureRate(1e-7).finalElementFailureRate(5e-6)
        .testInterval(8760) // Annual testing
        .build();

    System.out.println("  SIF: " + sif.getName());
    System.out.println("  Target SIL: " + sif.getTargetSIL());
    System.out.println("  Architecture: " + sif.getArchitecture());
    System.out.println("  Calculated PFDavg: " + String.format("%.2e", sif.getPfdAvg()));
    System.out.println("  Achieved SIL: " + sif.getAchievedSIL());
    System.out
        .println("  Risk Reduction Factor: " + String.format("%.0f", sif.getRiskReductionFactor()));
    System.out
        .println("  Spurious Trip Rate: " + String.format("%.4f /yr", sif.getSpuriousTripRate()));

    // LOPA Analysis
    SISIntegratedRiskModel model = new SISIntegratedRiskModel("HP Vessel Overpressure");
    model.setInitiatingEventFrequency(0.1); // per year
    model.addIndependentProtectionLayer("BPCS", 0.1);
    model.addIndependentProtectionLayer("Relief Valve", 0.01);
    model.addSIF(sif);
    model.setTolerableRiskFrequency(1e-5);

    LOPAResult lopa = model.performLOPA();
    System.out.println(
        "  LOPA - Mitigated Frequency: " + String.format("%.2e /yr", lopa.getMitigatedFrequency()));
    System.out.println("  LOPA - Meets Target: " + lopa.meetsTarget());
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
    BowTieModel bowtie = new BowTieModel("Pressure Vessel Rupture");
    bowtie.setHazard("Loss of containment from HP separator");

    // Add threats (left side)
    bowtie.addThreat("Overpressure", 0.01);
    bowtie.addThreat("Corrosion", 0.005);
    bowtie.addThreat("External Impact", 0.001);

    // Add prevention barriers
    bowtie.addPreventionBarrier("Overpressure", "Pressure Relief Valve", 0.99);
    bowtie.addPreventionBarrier("Overpressure", "High Pressure Alarm", 0.9);
    bowtie.addPreventionBarrier("Corrosion", "Inspection Program", 0.95);
    bowtie.addPreventionBarrier("External Impact", "Physical Protection", 0.8);

    // Add consequences (right side)
    bowtie.addConsequence("Fire/Explosion", 1000000.0);
    bowtie.addConsequence("Environmental Release", 500000.0);
    bowtie.addConsequence("Personnel Injury", 2000000.0);

    // Add mitigation barriers
    bowtie.addMitigationBarrier("Fire/Explosion", "Fire Detection", 0.95);
    bowtie.addMitigationBarrier("Fire/Explosion", "Deluge System", 0.9);
    bowtie.addMitigationBarrier("Environmental Release", "Secondary Containment", 0.85);
    bowtie.addMitigationBarrier("Personnel Injury", "Evacuation Procedures", 0.95);

    // Analyze
    BowTieAnalyzer analyzer = new BowTieAnalyzer(bowtie);
    analyzer.analyze();

    System.out.println("  Hazard: " + bowtie.getHazard());
    System.out.println("  Unmitigated Frequency: "
        + String.format("%.4f /yr", analyzer.getUnmitigatedFrequency()));
    System.out.println(
        "  Mitigated Frequency: " + String.format("%.6f /yr", analyzer.getMitigatedFrequency()));
    System.out.println("  Risk Reduction: " + String.format("%.0fx",
        analyzer.getUnmitigatedFrequency() / analyzer.getMitigatedFrequency()));
    System.out.println("  Most Critical Threat: " + analyzer.getMostCriticalThreat());
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

    // Add common cause scenarios
    portfolio.addCommonCauseScenario(PortfolioRiskAnalyzer.CommonCauseScenario.SEVERE_WEATHER, 0.1, // 10%
                                                                                                    // annual
                                                                                                    // probability
        0.3 // 30% production impact
    );
    portfolio.addCommonCauseScenario(PortfolioRiskAnalyzer.CommonCauseScenario.CYBER_ATTACK, 0.05,
        0.5);

    // Run analysis
    PortfolioRiskResult result = portfolio.runAnalysis(10000, 365);

    System.out.println("  Total Expected Production: "
        + String.format("%.0f bbl", result.getTotalExpectedProduction()));
    System.out
        .println("  Portfolio VaR (95%): $" + String.format("%.0f", result.getValueAtRisk95()));
    System.out.println("  Diversification Benefit: "
        + String.format("%.1f%%", result.getDiversificationBenefit() * 100));
    System.out.println("  Correlated Loss Events: " + result.getCorrelatedLossCount());
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

    ConditionBasedReliability cbr = new ConditionBasedReliability("Export Compressor");
    cbr.setBaseFailureRate(0.0001); // per hour
    cbr.setDegradationModel(ConditionBasedReliability.DegradationModel.WEIBULL);
    cbr.setWeibullShape(2.5);

    // Add condition indicators
    cbr.addIndicator(ConditionBasedReliability.IndicatorType.VIBRATION, 2.5, 1.0, 5.0, 10.0); // current,
                                                                                              // min,
                                                                                              // warning,
                                                                                              // alarm
    cbr.addIndicator(ConditionBasedReliability.IndicatorType.TEMPERATURE, 85.0, 20.0, 95.0, 110.0);
    cbr.addIndicator(ConditionBasedReliability.IndicatorType.EFFICIENCY, 0.78, 0.70, 0.75, 0.72);

    // Calculate health
    cbr.updateHealth();

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
}
