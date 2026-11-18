package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Candidate;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Kind;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Meter;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Status;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemPrEos;

/**
 * Checks single-boundary observability on a simulated gas and condensate separation train.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessBalanceFlowsheetTest extends NeqSimTest {
  private static final String INVENTORY = "V-101 inventory";
  private static final double METER_BOUND_KG_PER_S = 0.005;

  /** Compares the observed deficit with an independently tracked, withheld gas diversion. */
  @Test
  void detectsHiddenGasDiversionAndRetainsBoundaryMeterAlternatives() {
    SystemPrEos fluid = new SystemPrEos(298.15, 35.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("nC10", 0.15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10.0, "kg/sec");
    Separator vessel = new Separator("V-101", feed);
    vessel.setSeparatorLength(4.0);
    vessel.setInternalDiameter(1.0);
    vessel.setLiquidLevel(0.35);
    vessel.setCalculateSteadyState(false);
    Splitter tee = new Splitter("gas export tee", vessel.getGasOutStream(), 2);
    tee.setSplitFactors(new double[] {1.0, 0.0});

    ProcessSystem train = new ProcessSystem("steady separation train");
    train.add(feed);
    train.add(vessel);
    train.add(tee);
    train.run();
    double vesselMassKg = vessel.getThermoSystem().getMass("kg");
    double feedRate = feed.getFlowRate("kg/sec");
    double condensateRate = vessel.getLiquidOutStream().getFlowRate("kg/sec");
    assertTrue(vesselMassKg > 0.0 && condensateRate > 0.0);
    ProcessBalanceDiagnosis healthy = assessSteadyWindow(vesselMassKg, feedRate, condensateRate,
        tee.getSplitStream(0).getFlowRate("kg/sec"));
    assertEquals(Status.WITHIN_BOUNDS, healthy.getStatus());

    // The split occurs downstream of the vessel. The held equilibrium and inventory are unchanged.
    tee.setSplitFactors(new double[] {0.8, 0.2});
    tee.run();
    ProcessBalanceDiagnosis diverted = assessSteadyWindow(vesselMassKg, feedRate, condensateRate,
        tee.getSplitStream(0).getFlowRate("kg/sec"));

    // The omitted branch is used only for qualification, after the diagnosis.
    double actualDiversion = tee.getSplitStream(1).getFlowRate("kg/sec");
    assertEquals(0.2 * vessel.getGasOutStream().getFlowRate("kg/sec"), actualDiversion, 1.0e-8);
    assertTrue(actualDiversion > 3.0 * METER_BOUND_KG_PER_S);
    assertEquals(actualDiversion, diverted.getResiduals().get(INVENTORY).getResidualKgPerS(), 1.0e-8);
    assertEquals(Status.AMBIGUOUS, diverted.getStatus());
    assertEquals(4, diverted.getCandidates().size());
    for (Candidate candidate : diverted.getCandidates()) {
      double expectedRate = actualDiversion;
      if (candidate.getKind() == Kind.METER_BIAS && !candidate.getTag().equals("feed")) {
        expectedRate = -actualDiversion;
      }
      assertTrue(candidate.getMinimumKgPerS() <= expectedRate && expectedRate <= candidate.getMaximumKgPerS(),
          candidate.getTag());
    }
    assertTrue(diverted.getCandidates().stream()
        .anyMatch(candidate -> candidate.getKind() == Kind.UNMETERED_OUTFLOW && INVENTORY.equals(candidate.getTag())));
  }

  /**
   * Samples the same solved operating point at the endpoints and middle of a 60 s hold.
   *
   * @param inventoryKg modeled vessel inventory in kg
   * @param feed measured feed in kg/s
   * @param condensate measured liquid product in kg/s
   * @param gas measured gas export in kg/s
   * @return evidence from the three available boundary flowmeters
   */
  private static ProcessBalanceDiagnosis assessSteadyWindow(double inventoryKg, double feed, double condensate,
      double gas) {
    RcaProcessWindow window = RcaProcessWindow.builder("steady", 30.0)
        .signal(INVENTORY, new double[] {inventoryKg, inventoryKg, inventoryKg})
        .signal("feed", new double[] {feed, feed, feed})
        .signal("condensate", new double[] {condensate, condensate, condensate})
        .signal("export", new double[] {gas, gas, gas}).build();
    return ProcessBalanceDiagnosis.assess(window, Collections.singletonMap(INVENTORY, 0.0),
        Arrays.asList(new Meter("feed", null, INVENTORY, METER_BOUND_KG_PER_S),
            new Meter("condensate", INVENTORY, null, METER_BOUND_KG_PER_S),
            new Meter("export", INVENTORY, null, METER_BOUND_KG_PER_S)));
  }
}
