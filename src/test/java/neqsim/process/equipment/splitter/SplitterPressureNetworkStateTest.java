package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Algebraic pressure-network refresh, demand aggregation and bounded hydraulic rollback checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SplitterPressureNetworkStateTest {
  /** Refresh transports current intensive state while retaining prior mol/s, including exact zero, without a clock. */
  @Test
  void refreshHasExplicitMolarBasisAndDoesNotCommitStepMetadata() {
    Splitter tee = splitter();
    tee.getSplitStream(0).setFlowRate(3.0, "mole/sec");
    tee.getSplitStream(1).setFlowRate(0.0, "mole/sec");
    SystemInterface changed = tee.getInletStream().getFluid().clone();
    changed.setMolarComposition(new double[] {0.2, 0.8});
    changed.setPressure(42.0, "bara");
    changed.setTemperature(320.0);
    changed.initProperties();
    tee.getInletStream().setThermoSystem(changed);
    UUID before = tee.getCalculationIdentifier();
    double time = tee.getTime();
    tee.refreshTransientBranchIntensiveState();
    assertEquals(3.0, tee.getSplitStream(0).getFlowRate("mole/sec"), 1.0e-12);
    assertEquals(0.0, tee.getSplitStream(1).getFlowRate("mole/sec"));
    assertEquals(42.0, tee.getSplitStream(0).getPressure("bara"));
    assertEquals(320.0, tee.getSplitStream(0).getTemperature("K"));
    assertEquals(0.2, tee.getSplitStream(0).getFluid().getMolarComposition()[0], 1.0e-12);
    assertEquals(before, tee.getCalculationIdentifier());
    assertEquals(time, tee.getTime());
  }

  /** New explicit algebraic mode aggregates actual downstream demands while the legacy true flag remains prescribed. */
  @Test
  void explicitAlgebraicDemandAndLegacyDispatchRemainDistinct() {
    Splitter tee = splitter();
    tee.getSplitStream(0).setFlowRate(1.0, "kg/sec");
    tee.getSplitStream(1).setFlowRate(2.0, "kg/sec");
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS);
    tee.runTransient(0.1, UUID.randomUUID());
    assertTrue(tee.getCalculateSteadyState());
    assertEquals(3.0, tee.getInletStream().getFlowRate("kg/sec"), 1.0e-12);
    assertEquals(1.0, tee.getSplitStream(0).getFlowRate("kg/sec"), 1.0e-12);
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND);
    tee.setSplitFactors(new double[] {0.5, 0.5});
    tee.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.5, tee.getSplitStream(0).getFlowRate("kg/sec"), 1.0e-12);
  }

  /** Both exactly closed branch demands remain zero in the native mixer aggregation. */
  @Test
  void zeroDemandAggregationHasNoEpsilonLeak() {
    Splitter tee = splitter();
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS);
    for (int i = 0; i < 2; i++) {
      tee.getSplitStream(i).setFlowRate(0.0, "mole/sec");
    }
    tee.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.0, tee.getInletStream().getFlowRate("mole/sec"));
    assertTrue(Double.isFinite(tee.getInletStream().getPressure("bara")));
  }

  /** A complete connected branch subgraph rolls back PSV state and branch streams without changing graph aliases. */
  @Test
  void connectedBranchTransactionRestoresNativeAliasesAndReplay() {
    Splitter tee = splitter();
    tee.setTransientSplitMode(Splitter.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS);
    ThrottlingValve pcv = new ThrottlingValve("PCV", tee.getSplitStream(0));
    pcv.setCv(2.0);
    pcv.setOutletPressure(35.0, "bara");
    pcv.setCalculateSteadyState(false);
    SafetyReliefValve psv = new SafetyReliefValve("PSV", tee.getSplitStream(1));
    psv.configureConventionalSnap(39.0, 0.1, 0.03, 5.0);
    psv.setOutletPressure(1.2, "bara");
    psv.setCalculateSteadyState(false);
    pcv.runTransient(0.0, UUID.randomUUID());
    psv.runTransient(0.0, UUID.randomUUID());
    ProcessSystem process = new ProcessSystem();
    process.add((Stream) tee.getInletStream());
    process.add(pcv);
    process.add(psv);
    process.add(tee);
    assertTrue(process.getTransientTransactionCoverage().isComplete());
    Stream branch = (Stream) tee.getSplitStream(1);
    double initialMoles = branch.getFlowRate("mole/sec");
    UUID id = UUID.randomUUID();
    TransientStepTransaction transaction = process.beginTransientStepTransaction();
    tee.refreshTransientBranchIntensiveState();
    process.runTransient(0.1, id);
    double lift = psv.getOpenFraction();
    double flow = psv.getOutletStream().getFlowRate("kg/sec");
    psv.setLiftMotionAvailable(false);
    psv.setRatedCv(1.0);
    transaction.rollback();
    assertSame(branch, tee.getSplitStream(1));
    assertSame(branch, psv.getInletStream());
    assertEquals(initialMoles, branch.getFlowRate("mole/sec"));
    assertTrue(psv.isLiftMotionAvailable());
    tee.refreshTransientBranchIntensiveState();
    process.runTransient(0.1, id);
    assertEquals(lift, psv.getOpenFraction());
    assertEquals(flow, psv.getOutletStream().getFlowRate("kg/sec"));
    assertNull(tee.getTransientStateCoverageIssue());
  }

  /**
   * Creates independent branch streams using native steady topology initialization.
   *
   * @return initialized actual splitter
   */
  private static Splitter splitter() {
    SystemInterface fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(5.0, "kg/sec");
    Stream inlet = new Stream("GAS", fluid);
    inlet.run();
    Splitter tee = new Splitter("TEE", inlet, 2);
    tee.setSplitFactors(new double[] {0.5, 0.5});
    tee.run();
    return tee;
  }
}
