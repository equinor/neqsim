package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests for coupled transient gas-network hydraulics, linepack, and composition transport. */
class TransientGasNetworkTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TransientGasNetworkTest.class);
  private static final double EVENT_START_SECONDS = 6.0 * 3600.0;
  private static final double EVENT_END_SECONDS = 18.0 * 3600.0;
  private static final double ASGARD_FLOW_KG_S = 343.125;
  private static final double KRISTIN_FLOW_KG_S = 114.375;
  private static final double KRISTIN_EVENT_FLOW_KG_S = 142.96875;

  @Test
  void asgardKristinRateEventSolvesPressureLinepackAndComposition() {
    TransientGasNetwork network = createExportNetwork(12);
    network.run(36.0 * 3600.0, 1800.0);

    TransientGasNetworkHistory history = network.getHistory();
    double[] times = history.getElapsedTimeSeconds();
    double[] asgardPressure = history.getSourcePressureBaraHistory("asgard");
    double[] kristinPressure = history.getSourcePressureBaraHistory("kristin");
    double[] sinkPressure = history.getNodePressureBaraHistory("karsto");
    double[] exportInletFlow = history.getEdgeInletMassFlowKgSHistory("export");
    double[] exportOutletFlow = history.getEdgeOutletMassFlowKgSHistory("export");
    double[] exportLinepack = history.getEdgeLinepackKgHistory("export");

    assertEquals(72, times.length);
    for (double pressureBara : sinkPressure) {
      assertEquals(110.0, pressureBara, 1.0e-12, "The fixed Kårstø boundary must remain exactly 110 bara.");
    }

    double baselinePressure = mean(asgardPressure, 0, 12);
    double eventPeakPressure = maximum(asgardPressure, 12, 36);
    double finalPressure = asgardPressure[asgardPressure.length - 1];
    logger.info("Transient export pressure benchmark: baseline={} bara, event peak={} bara, final={} bara",
        baselinePressure, eventPeakPressure, finalPressure);
    assertEquals(200.0, baselinePressure, 6.0,
        "The local-EOS Darcy model should remain within 3% of the 200 bara quasi-steady baseline.");
    assertTrue(eventPeakPressure > baselinePressure + 2.0,
        "Solved source pressure must rise during the +6.25% total-rate event.");
    assertEquals(207.03, eventPeakPressure, 5.0,
        "The transient peak should remain physically comparable with the 207.03 bara quasi-steady benchmark.");
    assertTrue(Math.abs(finalPressure - baselinePressure) < Math.abs(eventPeakPressure - baselinePressure),
        "Solved source pressure must relax toward baseline after the rate event.");
    assertArrayEquals(asgardPressure, kristinPressure, 0.02,
        "One-metre source branches should give practically equal solved source pressures.");

    assertTrue(maximum(exportInletFlow, 12, 36) > maximum(exportOutletFlow, 12, 36),
        "During packing, export inlet flow must exceed the delayed outlet flow.");
    assertTrue(maximum(exportLinepack, 12, 36) > mean(exportLinepack, 0, 12),
        "The higher source rate must increase export linepack.");

    double[] karstoCo2 = history.getNodeMassFractionHistory("karsto", "CO2");
    double[] junctionCo2 = history.getNodeMassFractionHistory("junction", "CO2");
    assertTrue(maximum(junctionCo2, 12, 36) > junctionCo2[0],
        "The scheduled Kristin CO2 event must be visible at the junction.");
    assertTrue(maximum(karstoCo2, 12, karstoCo2.length) < maximum(junctionCo2, 12, 36),
        "Distributed export inventory must delay and broaden the Kårstø composition response.");

    for (TransientGasNetworkStepReport report : history.getStepReports()) {
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumNodeMassResidualKgS() <= 1.0e-6, report.getMessage());
      assertTrue(Math.abs(report.getRelativeTotalMassResidual()) <= 1.0e-8, report.getMessage());
      assertTrue(report.getMaximumComponentRelativeResidual() <= 1.0e-8, report.getMessage());
      assertTrue(report.getMaximumJunctionRelativeResidual() <= 1.0e-8, report.getMessage());
    }
    assertTrue(history.toJson().contains("nodePressureBaraHistory"));
    assertTrue(history.toJson().contains("edgeLinepackKgHistory"));
  }

  @Test
  void repeatedRunAndHistoryCopiesAreDeterministic() {
    TransientGasNetwork network = createExportNetwork(6);
    network.run(8.0 * 3600.0, 1800.0);
    String first = network.getHistory().toJson();
    double[] pressure = network.getHistory().getNodePressureBaraHistory("asgard");
    double original = pressure[0];
    pressure[0] = -999.0;
    assertEquals(original, network.getHistory().getNodePressureBaraHistory("asgard")[0], 0.0);
    network.run(8.0 * 3600.0, 1800.0);
    assertEquals(first, network.getHistory().toJson());
  }

  @Test
  void unsupportedAndInfeasibleCasesFailLoudly() {
    TransientGasNetwork reverse = singlePipeNetwork(gas(asgardComposition(), 200.0));
    IllegalArgumentException reverseError = assertThrows(IllegalArgumentException.class,
        () -> reverse.setSourceSchedule("source", new double[] { 0.0 },
            new SystemInterface[] { gas(asgardComposition(), 200.0) }, new double[] { -1.0 }));
    assertTrue(reverseError.getMessage().contains("reverse flow"));

    TransientGasNetwork pressureLimited = singlePipeNetwork(gas(asgardComposition(), 200.0));
    pressureLimited.setSourceSchedule("source", new double[] { 0.0 },
        new SystemInterface[] { gas(asgardComposition(), 200.0) },
        new double[] { ASGARD_FLOW_KG_S + KRISTIN_FLOW_KG_S });
    pressureLimited.setSourcePressureLimits("source", 110.0, 150.0, "bara");
    IllegalStateException pressureError = assertThrows(IllegalStateException.class,
        () -> pressureLimited.run(1800.0, 1800.0));
    assertTrue(pressureError.getMessage().contains("Infeasible source pressure"));
    assertTrue(pressureLimited.getLastDiagnostic().contains("Infeasible source pressure"));

    TransientGasNetwork capacityLimited = singlePipeNetwork(gas(asgardComposition(), 200.0));
    capacityLimited.setSourceSchedule("source", new double[] { 0.0 },
        new SystemInterface[] { gas(asgardComposition(), 200.0) },
        new double[] { ASGARD_FLOW_KG_S + KRISTIN_FLOW_KG_S });
    capacityLimited.setMaximumEdgeVelocity("pipe", 1.0);
    IllegalStateException capacityError = assertThrows(IllegalStateException.class,
        () -> capacityLimited.run(1800.0, 1800.0));
    assertTrue(capacityError.getMessage().contains("edge capacity"));

    TransientGasNetwork phaseAppearance = singlePipeNetwork(twoPhaseFluid());
    phaseAppearance.setSourceSchedule("source", new double[] { 0.0 }, new SystemInterface[] { twoPhaseFluid() },
        new double[] { 10.0 });
    IllegalArgumentException phaseError = assertThrows(IllegalArgumentException.class,
        () -> phaseAppearance.run(60.0, 60.0));
    assertTrue(phaseError.getMessage().contains("phase appearance"));
  }

  @Test
  @Tag("slow")
  void jointGridAndTimestepRefinementReducesOutletDifference() {
    double[] coarse = runKarstoCo2(48, 450.0);
    double[] medium = runKarstoCo2(96, 225.0);
    double[] fine = runKarstoCo2(192, 112.5);
    double coarseToMedium = commonTimeMeanAbsoluteDifference(coarse, 450.0, medium, 225.0, 450.0);
    double mediumToFine = commonTimeMeanAbsoluteDifference(medium, 225.0, fine, 112.5, 450.0);
    assertTrue(coarseToMedium > 0.0);
    assertTrue(mediumToFine < coarseToMedium,
        "Joint refinement must reduce Kårstø composition-history difference: coarse-medium=" + coarseToMedium
            + ", medium-fine=" + mediumToFine);
    double mediumPeak = maximum(medium, 0, medium.length);
    double finePeak = maximum(fine, 0, fine.length);
    double arrivalThreshold = fine[0] + 0.1 * (finePeak - fine[0]);
    double mediumArrival = firstThresholdTime(medium, 225.0, arrivalThreshold);
    double fineArrival = firstThresholdTime(fine, 112.5, arrivalThreshold);
    assertTrue(Math.abs(mediumPeak - finePeak) / finePeak <= 0.07,
        "Refined Kårstø CO2 peaks must agree within 7%: medium=" + mediumPeak + ", fine=" + finePeak);
    assertTrue(Math.abs(mediumArrival - fineArrival) <= 2.0 * 3600.0,
        "Refined 10%-response arrival times must agree within two hours: medium=" + mediumArrival + " s, fine="
            + fineArrival + " s");
  }

  private static double[] runKarstoCo2(int cells, double timeStepSeconds) {
    TransientGasNetwork network = createExportNetwork(cells);
    network.run(72.0 * 3600.0, timeStepSeconds);
    return network.getHistory().getNodeMassFractionHistory("karsto", "CO2");
  }

  private static TransientGasNetwork createExportNetwork(int exportCells) {
    SystemInterface asgard = gas(asgardComposition(), 200.0);
    SystemInterface kristin = gas(kristinComposition(), 200.0);
    SystemInterface kristinEvent = gas(kristinEventComposition(), 200.0);
    SystemInterface mixed = gas(mixedComposition(), 155.0);
    TransientGasNetwork network = new TransientGasNetwork("Synthetic Asgard and Kristin to Karsto");
    network.addNode("asgard");
    network.addNode("kristin");
    network.addNode("junction");
    network.addNode("karsto");
    network.addPipe("asgardBranch", "asgard", "junction", 1.0, 1.0, 50.0e-6, 1, asgard);
    network.addPipe("kristinBranch", "kristin", "junction", 1.0, 1.0, 50.0e-6, 1, kristin);
    network.addPipe("export", "junction", "karsto", 700000.0, 0.987, 50.0e-6, exportCells, mixed);
    network.setSourceSchedule("asgard", new double[] { 0.0 }, new SystemInterface[] { asgard },
        new double[] { ASGARD_FLOW_KG_S });
    network.setSourceSchedule("kristin", new double[] { 0.0, EVENT_START_SECONDS, EVENT_END_SECONDS },
        new SystemInterface[] { kristin, kristinEvent, kristin },
        new double[] { KRISTIN_FLOW_KG_S, KRISTIN_EVENT_FLOW_KG_S, KRISTIN_FLOW_KG_S });
    network.setFixedPressureBoundary("karsto", 110.0, "bara");
    network.setInitialNodePressure("asgard", 200.0, "bara");
    network.setInitialNodePressure("kristin", 200.0, "bara");
    network.setInitialNodePressure("junction", 200.0, "bara");
    network.setSourcePressureLimits("asgard", 110.0, 240.0, "bara");
    network.setSourcePressureLimits("kristin", 110.0, 240.0, "bara");
    network.setSolverControls(40, 1.0e-6, 1.0e-8);
    return network;
  }

  private static TransientGasNetwork singlePipeNetwork(SystemInterface fluid) {
    TransientGasNetwork network = new TransientGasNetwork("single pipe");
    network.addNode("source");
    network.addNode("sink");
    network.addPipe("pipe", "source", "sink", 700000.0, 0.987, 50.0e-6, 8, fluid);
    network.setFixedPressureBoundary("sink", 110.0, "bara");
    network.setInitialNodePressure("source", 200.0, "bara");
    return network;
  }

  private static SystemInterface gas(double[] composition, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(288.15, pressureBara);
    String[] names = new String[] { "methane", "ethane", "propane", "CO2", "nitrogen" };
    for (int index = 0; index < names.length; index++) {
      fluid.addComponent(names[index], composition[index]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static double[] asgardComposition() {
    return new double[] { 0.940, 0.035, 0.006, 0.008, 0.011 };
  }

  private static double[] kristinComposition() {
    return new double[] { 0.925, 0.040, 0.008, 0.012, 0.015 };
  }

  private static double[] kristinEventComposition() {
    return new double[] { 0.897, 0.040, 0.008, 0.040, 0.015 };
  }

  private static double[] mixedComposition() {
    return new double[] { 0.93625, 0.03625, 0.0065, 0.009, 0.012 };
  }

  private static SystemInterface twoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(240.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static double mean(double[] values, int startInclusive, int endExclusive) {
    double sum = 0.0;
    for (int index = startInclusive; index < endExclusive; index++) {
      sum += values[index];
    }
    return sum / (endExclusive - startInclusive);
  }

  private static double maximum(double[] values, int startInclusive, int endExclusive) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (int index = startInclusive; index < endExclusive; index++) {
      maximum = Math.max(maximum, values[index]);
    }
    return maximum;
  }

  private static double commonTimeMeanAbsoluteDifference(double[] coarse, double coarseStepSeconds, double[] fine,
      double fineStepSeconds, double comparisonStepSeconds) {
    int points = (int) Math
        .floor(Math.min(coarse.length * coarseStepSeconds, fine.length * fineStepSeconds) / comparisonStepSeconds);
    double totalDifference = 0.0;
    for (int point = 1; point <= points; point++) {
      int coarseIndex = (int) Math.round(point * comparisonStepSeconds / coarseStepSeconds) - 1;
      int fineIndex = (int) Math.round(point * comparisonStepSeconds / fineStepSeconds) - 1;
      totalDifference += Math.abs(coarse[coarseIndex] - fine[fineIndex]);
    }
    return totalDifference / points;
  }

  private static double firstThresholdTime(double[] values, double timeStepSeconds, double threshold) {
    for (int index = 0; index < values.length; index++) {
      if (values[index] >= threshold) {
        return (index + 1) * timeStepSeconds;
      }
    }
    return Double.POSITIVE_INFINITY;
  }
}
