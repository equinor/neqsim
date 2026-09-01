package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests for conservative prescribed-flow species transport through a gas gathering network. */
class TransientCompositionalPipeNetworkTest extends neqsim.NeqSimTest {
  private static final double TIME_STEP_SECONDS = 60.0;
  private static final double END_TIME_SECONDS = 5400.0;

  @Test
  void finiteCo2PulseIsDelayedBroadenedAndConservative() {
    TransientCompositionalPipeNetwork network = createNetwork(12);
    network.run(END_TIME_SECONDS, TIME_STEP_SECONDS);

    TransientCompositionalPipeNetworkHistory history = network.getSpeciesHistory();
    double[] times = history.getElapsedTimeSeconds();
    double[] junctionCo2 = history.getNodeMassFractionHistory("junction", "CO2");
    double[] deliveryCo2 = history.getNodeMassFractionHistory("karsto", "CO2");
    assertEquals((int) (END_TIME_SECONDS / TIME_STEP_SECONDS), times.length);

    double baseline = deliveryCo2[0];
    double junctionPeak = maximum(junctionCo2);
    double deliveryPeak = maximum(deliveryCo2);
    double threshold = baseline + 0.10 * (junctionPeak - baseline);
    int junctionBreakthrough = firstAbove(junctionCo2, threshold);
    int deliveryBreakthrough = firstAbove(deliveryCo2, threshold);

    assertTrue(junctionPeak > baseline + 0.02, "The finite Kristin CO2 event must reach the junction.");
    assertTrue(deliveryBreakthrough > junctionBreakthrough,
        "Export-pipe linepack must delay the Kårstø response relative to the junction.");
    assertTrue(deliveryPeak < junctionPeak,
        "Finite-volume edge inventories must broaden the pulse and reduce its downstream peak.");
    assertTrue(deliveryCo2[deliveryCo2.length - 1] < baseline + 2.0e-3,
        "The delivery composition must return close to its initial state after the pulse.");

    for (String edgeName : new String[] { "asgardBranch", "kristinBranch", "export" }) {
      TransientSpeciesConservationReport[] reports = history.getEdgeReports(edgeName);
      assertEquals(times.length, reports.length);
      double cumulativeInletMass = sum(reports[reports.length - 1].getInletBoundaryMassKg());
      double firstStepInletMass = sum(reports[0].getInletBoundaryMassKg());
      assertTrue(cumulativeInletMass > firstStepInletMass, "Edge boundary masses must be cumulative in time.");
      for (TransientSpeciesConservationReport report : reports) {
        assertTrue(report.isConverged(), report.getMessage());
        assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
        assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
        assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
      }
    }
    for (TransientSpeciesConservationReport report : history.getJunctionReports("junction")) {
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
    }
    for (TransientSpeciesConservationReport report : history.getNetworkReports()) {
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
    }
  }

  @Test
  void componentIdentityUsesNamesAndHistoriesAreImmutable() {
    TransientCompositionalPipeNetwork network = createNetwork(8);
    network.run(600.0, TIME_STEP_SECONDS);
    TransientCompositionalPipeNetworkHistory history = network.getSpeciesHistory();

    assertArrayEquals(new String[] { "CO2", "methane" }, history.getComponentNames());
    double sourceACo2 = massFraction(gas(0.98, 0.02, false), "CO2");
    double sourceBCo2 = massFraction(gas(0.95, 0.05, true), "CO2");
    double expectedInitialJunctionCo2 = 0.5 * (sourceACo2 + sourceBCo2);
    double[] junctionCo2 = history.getNodeMassFractionHistory("junction", "CO2");
    assertEquals(expectedInitialJunctionCo2, junctionCo2[0], 1.0e-12,
        "Component order differences in source fluids must not swap methane and CO2.");

    double original = junctionCo2[0];
    junctionCo2[0] = 99.0;
    assertEquals(original, history.getNodeMassFractionHistory("junction", "CO2")[0], 0.0);

    TransientSpeciesConservationReport edgeReport = history.getEdgeReports("export")[0];
    double[][] profile = edgeReport.getMassFractionProfile();
    double profileValue = profile[0][0];
    profile[0][0] = 99.0;
    assertEquals(profileValue, edgeReport.getMassFractionProfile()[0][0], 0.0);
    assertTrue(history.toJson().contains("nodeMassFractionHistory"));
  }

  @Test
  void repeatedRunIsDeterministic() {
    TransientCompositionalPipeNetwork network = createNetwork(8);
    network.run(2400.0, TIME_STEP_SECONDS);
    String first = network.getSpeciesHistory().toJson();
    network.run(2400.0, TIME_STEP_SECONDS);
    assertEquals(first, network.getSpeciesHistory().toJson());
  }

  @Test
  @Tag("slow")
  void jointGridAndTimestepRefinementReducesCommonTimeDifference() {
    double[] coarse = runDeliveryHistory(4, 120.0);
    double[] medium = runDeliveryHistory(8, 60.0);
    double[] fine = runDeliveryHistory(16, 30.0);

    double coarseToMedium = commonTimeMeanAbsoluteDifference(coarse, 120.0, medium, 60.0, 120.0);
    double mediumToFine = commonTimeMeanAbsoluteDifference(medium, 60.0, fine, 30.0, 120.0);
    assertTrue(coarseToMedium > 0.0);
    assertTrue(mediumToFine < coarseToMedium, "Joint refinement must reduce outlet-history difference: coarse-medium="
        + coarseToMedium + ", medium-fine=" + mediumToFine);
  }

  @Test
  void unsupportedReverseFlowAndPhaseAppearanceFailLoudly() {
    TransientCompositionalPipeNetwork reverse = new TransientCompositionalPipeNetwork("reverse");
    reverse.addNode("source");
    reverse.addNode("sink");
    reverse.addPipe("pipe", "source", "sink", 1000.0, 0.3, 4, gas(0.98, 0.02, false));
    IllegalArgumentException reverseError = assertThrows(IllegalArgumentException.class,
        () -> reverse.setSourceSchedule("source", new double[] { 0.0 },
            new SystemInterface[] { gas(0.98, 0.02, false) }, new double[] { -1.0 }));
    assertTrue(reverseError.getMessage().contains("reverse flow"));

    TransientCompositionalPipeNetwork phaseAppearance = new TransientCompositionalPipeNetwork("two phase");
    phaseAppearance.addNode("source");
    phaseAppearance.addNode("sink");
    SystemInterface twoPhase = twoPhaseFluid();
    phaseAppearance.addPipe("pipe", "source", "sink", 1000.0, 0.3, 4, twoPhase);
    phaseAppearance.setSourceSchedule("source", new double[] { 0.0 }, new SystemInterface[] { twoPhase },
        new double[] { 10.0 });
    IllegalArgumentException phaseError = assertThrows(IllegalArgumentException.class,
        () -> phaseAppearance.run(60.0, 60.0));
    assertTrue(phaseError.getMessage().contains("phase appearance"));

    TransientCompositionalPipeNetwork liquidNetwork = new TransientCompositionalPipeNetwork("liquid");
    liquidNetwork.addNode("source");
    liquidNetwork.addNode("sink");
    SystemInterface liquid = singlePhaseLiquid();
    liquidNetwork.addPipe("pipe", "source", "sink", 1000.0, 0.3, 4, liquid);
    liquidNetwork.setSourceSchedule("source", new double[] { 0.0 }, new SystemInterface[] { liquid },
        new double[] { 10.0 });
    IllegalArgumentException liquidError = assertThrows(IllegalArgumentException.class,
        () -> liquidNetwork.run(60.0, 60.0));
    assertTrue(liquidError.getMessage().contains("exactly one gas phase"));
  }

  private static double[] runDeliveryHistory(int cells, double timeStepSeconds) {
    TransientCompositionalPipeNetwork network = createNetwork(cells);
    network.run(END_TIME_SECONDS, timeStepSeconds);
    return network.getSpeciesHistory().getNodeMassFractionHistory("karsto", "CO2");
  }

  private static TransientCompositionalPipeNetwork createNetwork(int cells) {
    SystemInterface initialMixed = gas(0.965, 0.035, false);
    TransientCompositionalPipeNetwork network = new TransientCompositionalPipeNetwork("Norwegian export teaching case");
    network.addNode("asgard");
    network.addNode("kristin");
    network.addNode("junction");
    network.addNode("karsto");
    network.addPipe("asgardBranch", "asgard", "junction", 2000.0, 0.4, cells, gas(0.98, 0.02, false));
    network.addPipe("kristinBranch", "kristin", "junction", 2000.0, 0.4, cells, gas(0.95, 0.05, true));
    network.addPipe("export", "junction", "karsto", 4000.0, 0.4, cells, initialMixed);

    network.setSourceSchedule("asgard", new double[] { 0.0 }, new SystemInterface[] { gas(0.98, 0.02, false) },
        new double[] { 20.0 });
    network.setSourceSchedule("kristin", new double[] { 0.0, 600.0, 1800.0 },
        new SystemInterface[] { gas(0.95, 0.05, true), gas(0.75, 0.25, false), gas(0.95, 0.05, true) },
        new double[] { 20.0, 18.0, 20.0 });
    return network;
  }

  private static SystemInterface gas(double methaneMoleFraction, double co2MoleFraction, boolean reverseOrder) {
    SystemInterface fluid = new SystemSrkEos(300.0, 70.0);
    if (reverseOrder) {
      fluid.addComponent("CO2", co2MoleFraction);
      fluid.addComponent("methane", methaneMoleFraction);
    } else {
      fluid.addComponent("methane", methaneMoleFraction);
      fluid.addComponent("CO2", co2MoleFraction);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static SystemInterface twoPhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(240.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static SystemInterface singlePhaseLiquid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static double massFraction(SystemInterface fluid, String componentName) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    double total = 0.0;
    double selected = 0.0;
    double[] z = fluid.getMolarComposition();
    for (int index = 0; index < fluid.getNumberOfComponents(); index++) {
      double mass = z[index] * fluid.getPhase(0).getComponent(index).getMolarMass();
      total += mass;
      if (componentName.equals(fluid.getPhase(0).getComponent(index).getComponentName())) {
        selected = mass;
      }
    }
    return selected / total;
  }

  private static int firstAbove(double[] values, double threshold) {
    for (int index = 0; index < values.length; index++) {
      if (values[index] > threshold) {
        return index;
      }
    }
    return -1;
  }

  private static double maximum(double[] values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }

  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  private static double commonTimeMeanAbsoluteDifference(double[] coarse, double coarseStep, double[] fine,
      double fineStep, double comparisonStep) {
    int points = (int) Math.floor(Math.min(coarse.length * coarseStep, fine.length * fineStep) / comparisonStep);
    double totalDifference = 0.0;
    for (int point = 1; point <= points; point++) {
      int coarseIndex = (int) Math.round(point * comparisonStep / coarseStep) - 1;
      int fineIndex = (int) Math.round(point * comparisonStep / fineStep) - 1;
      totalDifference += Math.abs(coarse[coarseIndex] - fine[fineIndex]);
    }
    return totalDifference / points;
  }
}
