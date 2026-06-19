package neqsim.process.examples;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.examples.OilGasProcessSimulationOptimization.MaxProductionResult;
import neqsim.process.examples.OilGasProcessSimulationOptimization.ProcessOutputResults;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test to run production optimization with separator and scrubber design checks
 */
@Tag("slow")
public class ProductionOptimizationTest {
  private static final Logger logger = LogManager.getLogger(ProductionOptimizationTest.class);

  @Test
  void testMaximizeProduction() {
    logger.info("===== Production Optimization Test (Max 15000 kmol/hr) =====\n");

    // Create the simulation
    OilGasProcessSimulationOptimization simulation = new OilGasProcessSimulationOptimization();

    // Create and run the process
    simulation.createProcess();

    // Configure compressor chart for 27-KA-01
    // Design speed: 8000 RPM (at design flow of 8000 kmol/hr), Max speed: 9000 RPM
    // This creates a clean relationship: speed (RPM) = flow (kmol/hr)
    // At 8000 kmol/hr: 8000 RPM (88.9% of max)
    // At 9000 kmol/hr: 9000 RPM (100% of max) - compressor becomes limiting
    // At 10000 kmol/hr: 10000 RPM (111% of max) - overspeed
    simulation.configureCompressorCharts(8000.0, 9000.0);

    ProcessOutputResults initialResults = simulation.runSimulation();

    logger.info("=== Initial Process Results ===");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Gas Export Rate: %.2f kmole/hr\n",
	initialResults.getGasExportRate() / 1000.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Oil Export Rate: %.2f kmole/hr\n",
	initialResults.getOilExportRate() / 1000.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Feed Rate: %.2f kmol/hr\n",
	simulation.getInputParameters().getFeedRate());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total Power: %.2f kW\n",
	initialResults.getTotalPowerConsumption());

    // Print separator utilization
    logger.info("\n=== Separator Capacity Utilization (Initial) ===");
    Map<String, Double> sepCapacity = initialResults.getSeparatorCapacityUtilization();
    for (Map.Entry<String, Double> entry : sepCapacity.entrySet()) {
      logger.printf(org.apache.logging.log4j.Level.INFO, "  %s: %.1f%%\n", entry.getKey(), entry.getValue() * 100.0);
    }
    logger.printf(org.apache.logging.log4j.Level.INFO, "Any separator overloaded: %s\n",
	initialResults.isAnySeparatorOverloaded());

    // Print compressor speed utilization
    logger.info("\n=== Compressor Speed Utilization (Initial) ===");
    Map<String, Double> compSpeeds = initialResults.getCompressorSpeeds();
    Map<String, Double> compMaxSpeeds = initialResults.getCompressorMaxSpeeds();
    Map<String, Double> compSpeedUtil = initialResults.getCompressorSpeedUtilization();
    for (Map.Entry<String, Double> entry : compSpeedUtil.entrySet()) {
      String compName = entry.getKey();
      double speed = compSpeeds.get(compName);
      double maxSpeed = compMaxSpeeds.get(compName);
      double utilization = entry.getValue();
      String status = utilization > 1.0 ? " <-- OVERSPEED!" : (utilization > 0.95 ? " <-- NEAR LIMIT" : "");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  %s: Speed=%.0f RPM, Max=%.0f RPM, Utilization=%.1f%%%s\n",
	  compName, speed, maxSpeed, utilization * 100.0, status);
    }
    logger.printf(org.apache.logging.log4j.Level.INFO, "Any compressor overspeed: %s\n",
	initialResults.isAnyCompressorOverspeed());

    // Print detailed separator/scrubber design information
    printSeparatorDesignInfo(simulation.getOilProcess(), "Initial");

    // Now run optimization for max production
    logger.info("\n=== Running Production Optimization (up to 15000 kmol/hr) ===");
    MaxProductionResult maxProdResult = simulation.optimizeMaxProduction(simulation.getInputParameters());

    logger.info("\n=== Optimization Results ===");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Maximum Feed Rate: %.2f kmol/hr\n",
	maxProdResult.getMaxFeedRate());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Gas Export Rate: %.2f kmole/hr\n",
	maxProdResult.getMaxGasExportRate() / 1000.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Oil Export Rate: %.2f kmole/hr\n",
	maxProdResult.getMaxOilExportRate() / 1000.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Bottleneck Separator: %s\n",
	maxProdResult.getBottleneckSeparator());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Bottleneck Utilization: %.1f%%\n",
	maxProdResult.getBottleneckUtilization() * 100.0);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Limiting Separator: %s\n",
	maxProdResult.getLimitingSeparator());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Successful Iterations: %d\n",
	maxProdResult.getSuccessfulIterations());
    logger.printf(org.apache.logging.log4j.Level.INFO, "Total Failures: %d\n", maxProdResult.getTotalFailures());

    // Print separator capacities at max production
    if (maxProdResult.getSeparatorCapacities() != null) {
      logger.info("\n=== Separator Capacity Utilization (at Max Feed Rate) ===");
      for (Map.Entry<String, Double> entry : maxProdResult.getSeparatorCapacities().entrySet()) {
	String status = entry.getValue() > 0.95 ? " <-- NEAR LIMIT" : (entry.getValue() > 1.0 ? " <-- OVERLOADED" : "");
	logger.printf(org.apache.logging.log4j.Level.INFO, "  %s: %.1f%%%s\n", entry.getKey(), entry.getValue() * 100.0,
	    status);
      }
    }

    // Print compressor speed at max production
    if (maxProdResult.getCompressorSpeedUtilization() != null) {
      logger.info("\n=== Compressor Speed Utilization (at Max Feed Rate) ===");
      for (Map.Entry<String, Double> entry : maxProdResult.getCompressorSpeedUtilization().entrySet()) {
	String status = entry.getValue() > 1.0 ? " <-- OVERSPEED!" : (entry.getValue() > 0.95 ? " <-- NEAR LIMIT" : "");
	logger.printf(org.apache.logging.log4j.Level.INFO, "  %s: %.1f%%%s\n", entry.getKey(), entry.getValue() * 100.0,
	    status);
      }
    }

    // Run simulation at optimized feed rate to get detailed design info
    if (maxProdResult.getMaxFeedRate() > 0.0) {
      simulation.getInputParameters().setFeedRate(maxProdResult.getMaxFeedRate());
      simulation.runSimulation();
      printSeparatorDesignInfo(simulation.getOilProcess(), "Optimized");
    } else {
      logger.info("No feasible optimized feed rate found; skipping zero-flow detailed simulation.");
    }

    // Calculate improvement
    double initialFeedRate = 8000.0; // default initial feed rate
    double feedImprovement = (maxProdResult.getMaxFeedRate() - initialFeedRate) / initialFeedRate * 100;
    double oilImprovement = (maxProdResult.getMaxOilExportRate() - initialResults.getOilExportRate())
	/ initialResults.getOilExportRate() * 100;

    logger.info("\n=== Improvement ===");
    logger.printf(org.apache.logging.log4j.Level.INFO, "Feed Rate Increase: %.1f%%\n", feedImprovement);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Oil Production Increase: %.1f%%\n", oilImprovement);

    // Print optimization summary
    logger.info(maxProdResult);

    logger.info("\n===== Optimization Complete =====");
  }

  /**
   * Print detailed separator and scrubber design information
   */
  private void printSeparatorDesignInfo(ProcessSystem process, String label) {
    logger.info("\n=== Separator & Scrubber Design Check (" + label + ") ===");

    // Get all separators from process
    String[] separatorNames = { "20-VA-01", "20-VA-02", "20-VA-03", "23-VG-01", "23-VG-02", "23-VG-03", "23-VG-04" };

    for (String name : separatorNames) {
      try {
	Object unit = process.getUnit(name);
	if (unit == null)
	  continue;

	if (unit instanceof ThreePhaseSeparator) {
	  ThreePhaseSeparator sep = (ThreePhaseSeparator) unit;
	  printThreePhaseSeparatorInfo(sep);
	} else if (unit instanceof Separator) {
	  Separator sep = (Separator) unit;
	  printSeparatorInfo(sep);
	}
      } catch (Exception e) {
	// Unit not found or error
      }
    }
  }

  private void printThreePhaseSeparatorInfo(ThreePhaseSeparator sep) {
    logger.printf(org.apache.logging.log4j.Level.INFO, "\n--- %s (Three-Phase Separator) ---\n", sep.getName());
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Temperature: %.1f °C\n", sep.getTemperature() - 273.15);
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Pressure: %.2f bara\n", sep.getPressure());

    // Gas outlet
    if (sep.getGasOutStream() != null && sep.getGasOutStream().getFluid() != null) {
      double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  Gas Out: %.2f kg/hr (%.2f Am3/hr)\n", gasFlow,
	  sep.getGasOutStream().getFlowRate("Am3/hr"));
    }

    // Oil outlet
    if (sep.getOilOutStream() != null && sep.getOilOutStream().getFluid() != null) {
      double oilFlow = sep.getOilOutStream().getFlowRate("kg/hr");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  Oil Out: %.2f kg/hr\n", oilFlow);
    }

    // Water outlet
    if (sep.getWaterOutStream() != null && sep.getWaterOutStream().getFluid() != null) {
      double waterFlow = sep.getWaterOutStream().getFlowRate("kg/hr");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  Water Out: %.2f kg/hr\n", waterFlow);
    }

    // Utilization (using base class methods)
    double gasUtil = sep.getGasLoadFactor() * 100.0;
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Gas Load Factor: %.3f %s\n", sep.getGasLoadFactor(),
	gasUtil > 100 ? "<-- OVERLOADED" : (gasUtil > 90 ? "<-- HIGH" : ""));
  }

  private void printSeparatorInfo(Separator sep) {
    logger.printf(org.apache.logging.log4j.Level.INFO, "\n--- %s (Scrubber/Separator) ---\n", sep.getName());
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Temperature: %.1f °C\n", sep.getTemperature() - 273.15);
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Pressure: %.2f bara\n", sep.getPressure());

    // Gas outlet
    if (sep.getGasOutStream() != null && sep.getGasOutStream().getFluid() != null) {
      double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  Gas Out: %.2f kg/hr (%.2f Am3/hr)\n", gasFlow,
	  sep.getGasOutStream().getFlowRate("Am3/hr"));
    }

    // Liquid outlet
    if (sep.getLiquidOutStream() != null && sep.getLiquidOutStream().getFluid() != null) {
      double liqFlow = sep.getLiquidOutStream().getFlowRate("kg/hr");
      logger.printf(org.apache.logging.log4j.Level.INFO, "  Liquid Out: %.2f kg/hr\n", liqFlow);
    }

    // Utilization
    double gasUtil = sep.getGasLoadFactor() * 100.0;
    logger.printf(org.apache.logging.log4j.Level.INFO, "  Gas Load Factor: %.3f %s\n", sep.getGasLoadFactor(),
	gasUtil > 100 ? "<-- OVERLOADED" : (gasUtil > 90 ? "<-- HIGH" : ""));
  }
}
