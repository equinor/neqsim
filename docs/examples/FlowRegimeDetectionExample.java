package examples;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Example showing flow regime detection for various multiphase models.
 */
public class FlowRegimeDetectionExample {
  private static final Logger logger = LogManager.getLogger(FlowRegimeDetectionExample.class);

  public static void main(String[] args) {
    logger.info("================================================================");
    logger.info("  Flow Regime Detection Comparison");
    logger.info("  1 km horizontal pipeline, 300 mm diameter");
    logger.info("================================================================\n");

    double length = 1000.0;
    double diameter = 0.3;
    double roughness = 4.5e-5;
    double pressure = 50.0;
    double temperature = 40.0;

    // Test pure gas
    logger.info("============================================================");
    logger.info("  PURE GAS (Single Phase)");
    logger.info("============================================================");
    testFlowRegimes(createPureGasFluid(temperature, pressure), 20.0, length, diameter, roughness, temperature,
        pressure);

    // Test two-phase at different flow rates
    logger.info("\n============================================================");
    logger.info("  TWO-PHASE (Gas + Oil) at Various Flow Rates");
    logger.info("============================================================");
    double[] flowRates = { 1.0, 5.0, 20.0, 60.0, 100.0 };
    for (double flowRate : flowRates) {
      logger.info("{}", String.format("%nFlow Rate: %.0f kg/s", flowRate));
      logger.info("----------------------------");
      testFlowRegimes(createTwoPhaseFluid(temperature, pressure), flowRate, length, diameter, roughness, temperature,
          pressure);
    }

    // Test three-phase
    logger.info("\n============================================================");
    logger.info("  THREE-PHASE (Gas + Oil + Water) at Various Flow Rates");
    logger.info("============================================================");
    for (double flowRate : flowRates) {
      logger.info("{}", String.format("%nFlow Rate: %.0f kg/s", flowRate));
      logger.info("----------------------------");
      testFlowRegimes(createThreePhaseFluid(temperature, pressure), flowRate, length, diameter, roughness, temperature,
          pressure);
    }
  }

  private static void testFlowRegimes(SystemInterface fluid, double flowRate, double length, double diameter,
      double roughness, double tempC, double pressure) {
    // Beggs & Brill
    try {
      Stream inlet = new Stream("BB_inlet", fluid.clone());
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("BB", inlet);
      pipe.setDiameter(diameter);
      pipe.setLength(length);
      pipe.setPipeWallRoughness(roughness);
      pipe.setAngle(0);
      pipe.setNumberOfIncrements(20);
      pipe.run();

      logger.info("{}", String.format("  Beggs-Brill:  %s", pipe.getFlowRegime()));
    } catch (Exception e) {
      logger.error("Beggs-Brill calculation failed: {}", e.getMessage(), e);
    }

    // Drift-Flux (TransientPipe)
    try {
      Stream inlet = new Stream("DF_inlet", fluid.clone());
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      TransientPipe pipe = new TransientPipe("DF", inlet);
      pipe.setLength(length);
      pipe.setDiameter(diameter);
      pipe.setRoughness(roughness);
      pipe.setNumberOfSections(25);
      pipe.setMaxSimulationTime(60);
      pipe.run();

      // Get flow regimes from sections
      PipeSection[] sections = pipe.getSections();
      if (sections != null && sections.length > 0) {
        Map<String, Integer> regimeCounts = new HashMap<>();
        for (PipeSection section : sections) {
          String regime = section.getFlowRegime().toString();
          regimeCounts.merge(regime, 1, Integer::sum);
        }
        logger.info("{}", String.format("  Drift-Flux:   %s", formatRegimeCounts(regimeCounts)));
      }
    } catch (Exception e) {
      logger.error("Drift-Flux calculation failed: {}", e.getMessage(), e);
    }

    // Two-Fluid (TwoFluidPipe)
    try {
      Stream inlet = new Stream("TF_inlet", fluid.clone());
      inlet.setFlowRate(flowRate, "kg/sec");
      inlet.setTemperature(tempC, "C");
      inlet.setPressure(pressure, "bara");
      inlet.run();

      TwoFluidPipe pipe = new TwoFluidPipe("TF", inlet);
      pipe.setLength(length);
      pipe.setDiameter(diameter);
      pipe.setRoughness(roughness);
      pipe.setNumberOfSections(25);
      pipe.run();

      // Get flow regimes
      PipeSection.FlowRegime[] regimes = pipe.getFlowRegimeProfile();
      if (regimes != null && regimes.length > 0) {
        Map<String, Integer> regimeCounts = new HashMap<>();
        for (PipeSection.FlowRegime regime : regimes) {
          regimeCounts.merge(regime.toString(), 1, Integer::sum);
        }
        logger.info("{}", String.format("  Two-Fluid:    %s", formatRegimeCounts(regimeCounts)));
      }
    } catch (Exception e) {
      logger.error("Two-Fluid calculation failed: {}", e.getMessage(), e);
    }
  }

  private static String formatRegimeCounts(Map<String, Integer> counts) {
    if (counts.size() == 1) {
      return counts.keySet().iterator().next();
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
    }
    return sb.toString();
  }

  private static SystemInterface createPureGasFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nitrogen", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static SystemInterface createTwoPhaseFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.addComponent("n-octane", 0.04);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static SystemInterface createThreePhaseFluid(double tempC, double pressure) {
    SystemInterface fluid = new SystemSrkEos(tempC + 273.15, pressure);
    fluid.addComponent("methane", 0.55);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent("n-octane", 0.03);
    fluid.addComponent("water", 0.22);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }
}
