package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Conservation, direction, refinement and process integration for a two-sided pipe rupture. */
class TwoSidedRealGasPipeRuptureTest extends neqsim.NeqSimTest {

  private static SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(300.0, 80.0);
    fluid.addComponent("methane", 0.88);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("nitrogen", 0.04);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static TwoSidedRealGasPipeRupture rupture(double locationM, int cells) {
    return new TwoSidedRealGasPipeRupture("full-bore", gas(), 100.0, locationM, 0.15, 1.0, 101325.0, 0.01, cells, 0.30);
  }

  @Test
  void symmetricRuptureRetainsOpposingJetsAndWholePipeConservation() {
    TwoSidedRealGasPipeRupture rupture = rupture(50.0, 24);
    ProcessSystem process = new ProcessSystem();
    rupture.addTo(process);
    SourceTermSession session = new SourceTermSession("symmetric-full-bore", process);
    rupture.registerSources(session);

    session.runSteadyState();
    List<SourceTermFrame> frames = session.step(0.05);

    assertEquals(2, frames.size());
    assertEquals(rupture.getLeftBranch().getReleaseResult().getMassFlowRateKgS(),
        rupture.getRightBranch().getReleaseResult().getMassFlowRateKgS(), 1.0e-12);
    assertEquals(rupture.getInitialMassKg(), rupture.getRemainingMassKg() + rupture.getReleasedMassKg(),
        rupture.getInitialMassKg() * 3.0e-10);
    assertEquals(rupture.getInitialEnergyJ(), rupture.getRemainingEnergyJ() + rupture.getReleasedEnergyJ(),
        Math.abs(rupture.getInitialEnergyJ()) * 3.0e-10);
    assertEquals(TwoSidedRealGasPipeRupture.AxialDirection.POSITIVE_PIPE_AXIS,
        rupture.getLeftBranch().getAxialDirection());
    assertEquals(TwoSidedRealGasPipeRupture.AxialDirection.NEGATIVE_PIPE_AXIS,
        rupture.getRightBranch().getAxialDirection());
  }

  @Test
  void offCentreRuptureRetainsBothInventoriesAndNearbyTrend() {
    TwoSidedRealGasPipeRupture nearLeft = rupture(30.0, 24);
    TwoSidedRealGasPipeRupture fartherRight = rupture(40.0, 24);
    assertEquals(30.0, nearLeft.getLeftBranch().getBranchLengthM(), 0.0);
    assertEquals(70.0, nearLeft.getRightBranch().getBranchLengthM(), 0.0);
    assertTrue(nearLeft.getLeftBranch().getSegment().getInitialMassKg() < nearLeft.getRightBranch().getSegment()
        .getInitialMassKg());
    assertTrue(nearLeft.getLeftBranch().getSegment().getInitialMassKg() < fartherRight.getLeftBranch().getSegment()
        .getInitialMassKg());
    assertEquals(nearLeft.getInitialMassKg(), fartherRight.getInitialMassKg(), nearLeft.getInitialMassKg() * 1.0e-12);
    assertNotEquals(nearLeft.getLeftBranch().getReleaseProvenance().get("ruptureBranchLengthM"),
        nearLeft.getRightBranch().getReleaseProvenance().get("ruptureBranchLengthM"));
  }

  @Test
  void callerTimestepRefinementBoundsCombinedRelease() {
    double[] timeSteps = new double[] {0.04, 0.02, 0.01};
    double[] released = new double[timeSteps.length];
    for (int i = 0; i < timeSteps.length; i++) {
      TwoSidedRealGasPipeRupture rupture = rupture(35.0, 24);
      ProcessSystem process = new ProcessSystem();
      rupture.addTo(process);
      SourceTermSession session = new SourceTermSession("refinement-" + i, process);
      rupture.registerSources(session);
      session.runSteadyState();
      int steps = (int) Math.round(0.08 / timeSteps[i]);
      for (int step = 0; step < steps; step++) {
        session.step(timeSteps[i]);
      }
      released[i] = rupture.getReleasedMassKg();
    }
    double minimum = Math.min(released[0], Math.min(released[1], released[2]));
    double maximum = Math.max(released[0], Math.max(released[1], released[2]));
    assertTrue((maximum - minimum) / released[2] < 1.0e-4,
        "released=" + released[0] + "," + released[1] + "," + released[2]);
  }

  @Test
  void framesRemainDistinctAndSchemaValidForBothProcessContainers() {
    for (boolean useModel : new boolean[] {false, true}) {
      TwoSidedRealGasPipeRupture rupture = rupture(35.0, 24);
      ProcessSystem process = new ProcessSystem();
      rupture.addTo(process);
      SourceTermSession session;
      if (useModel) {
        ProcessModel model = new ProcessModel();
        model.add("pipeline", process);
        session = new SourceTermSession("two-sided-model", model);
        rupture.registerSources(session, "pipeline");
      } else {
        session = new SourceTermSession("two-sided-system", process);
        rupture.registerSources(session);
      }
      session.runSteadyState();
      List<SourceTermFrame> frames = session.step(0.02);
      assertEquals(2, frames.size());
      JsonObject left = JsonParser.parseString(frames.get(0).toJson()).getAsJsonObject();
      JsonObject right = JsonParser.parseString(frames.get(1).toJson()).getAsJsonObject();
      SourceTermFrame.verifyEnvelope(frames.get(0).toJson());
      SourceTermFrame.verifyEnvelope(frames.get(1).toJson());
      assertEquals(rupture.getLeftSourceId(), left.get("sourceId").getAsString());
      assertEquals(rupture.getRightSourceId(), right.get("sourceId").getAsString());
      assertEquals("POSITIVE_PIPE_AXIS",
          left.getAsJsonObject("provenance").get("ruptureJetAxialDirection").getAsString());
      assertEquals("NEGATIVE_PIPE_AXIS",
          right.getAsJsonObject("provenance").get("ruptureJetAxialDirection").getAsString());
      assertEquals("TWO_SIDED_FULL_BORE_PIPE_RUPTURE",
          left.getAsJsonObject("provenance").get("ruptureTopology").getAsString());
    }
  }

  @Test
  void invalidTopologyFailsBeforeCreatingPartialBranches() {
    assertThrows(IllegalArgumentException.class,
        () -> new TwoSidedRealGasPipeRupture("rupture", gas(), 100.0, 0.0, 0.15, 1.0, 101325.0, 0.01, 24, 0.30));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoSidedRealGasPipeRupture("rupture", gas(), 100.0, 100.0, 0.15, 1.0, 101325.0, 0.01, 24, 0.30));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoSidedRealGasPipeRupture("rupture", gas(), 100.0, 50.0, 0.15, 1.0, 101325.0, 0.01, 15, 0.30));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoSidedRealGasPipeRupture("rupture", gas(), 100.0, 50.0, 0.15, 1.0, 101325.0, 0.01, 801, 0.30));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoSidedRealGasPipeRupture(" ", gas(), 100.0, 50.0, 0.15, 1.0, 101325.0, 0.01, 24, 0.30));
  }
}
