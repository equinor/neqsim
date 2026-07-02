package neqsim.process.equipment.compressor;

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.ControlAlgorithm;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.DualRecycleValveCommand;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.FaultTolerantDecision;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.InstrumentSignal;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.RecycleSizingResult;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.ReducedCoordinatePoint;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.SensorFault;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.SurgeOscillationState;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.VotingMode;
import neqsim.process.equipment.compressor.AdvancedAntiSurgeControlSystem.VotingResult;

/**
 * Tests for the advanced anti-surge screening and control utilities.
 *
 * @author NeqSim
 * @version 1.0
 */
public class AdvancedAntiSurgeControlSystemTest {
  @Test
  void testReducedCoordinateAndControlLineDistance() {
    ReducedCoordinatePoint point = AdvancedAntiSurgeControlSystem.calculateReducedCoordinates(50.0, 100.0, 300.0, 360.0,
        0.8);

    Assertions.assertEquals(2.0, point.getPressureRatio(), 1.0e-12);
    Assertions.assertTrue(point.getReducedHead() > 0.0, "reduced head should be positive for compression");
    Assertions.assertEquals(0.016, point.getReducedFlow(), 1.0e-12);

    double controlLineDistance = AdvancedAntiSurgeControlSystem.distanceToReducedControlLine(0.016, 0.014, 0.10);
    Assertions.assertTrue(controlLineDistance > 0.0, "point should sit above the reduced surge-control line");
  }

  @Test
  void testVotingRejectsFaultedSignalAndReportsDegradedStatus() {
    InstrumentSignal goodA = new InstrumentSignal("AS-FT-1", -1.0, 1.0, 0.0);
    InstrumentSignal goodB = new InstrumentSignal("AS-FT-2", -1.0, 1.0, 0.0);
    InstrumentSignal bad = new InstrumentSignal("AS-FT-3", -1.0, 1.0, 0.0);

    goodA.update(0.08, 1.0);
    goodB.update(0.10, 1.0);
    bad.setFault(SensorFault.INVALID, 0.0);
    bad.update(0.09, 1.0);

    VotingResult result = AdvancedAntiSurgeControlSystem.vote(Arrays.asList(goodA, goodB, bad), VotingMode.MEDIAN,
        0.05);

    Assertions.assertTrue(result.isValid());
    Assertions.assertTrue(result.isDegraded(), "invalid third sensor should degrade the vote");
    Assertions.assertEquals(2, result.getAcceptedSignals());
    Assertions.assertEquals(0.09, result.getValue(), 1.0e-12);
  }

  @Test
  void testFaultTolerantDecisionUsesFallbackOpeningWhenVoteIsDegraded() {
    AdvancedAntiSurgeControlSystem controller = new AdvancedAntiSurgeControlSystem();
    controller.setPiTuning(100.0, 0.0);
    InstrumentSignal low = new InstrumentSignal("AS-MARGIN-1", -1.0, 1.0, 0.0);
    InstrumentSignal high = new InstrumentSignal("AS-MARGIN-2", -1.0, 1.0, 0.0);
    low.update(0.09, 1.0);
    high.update(0.22, 1.0);

    FaultTolerantDecision decision = controller.evaluateFaultTolerant(Arrays.asList(low, high), VotingMode.SELECT_LOW,
        -0.01, 1.0);

    Assertions.assertTrue(decision.isValid());
    Assertions.assertTrue(decision.isFallbackActive(), "large transmitter disagreement should activate fallback");
    Assertions.assertTrue(decision.getValveOpening() >= 50.0, "fallback should enforce a minimum recycle opening");
  }

  @Test
  void testRecycleSizingIncreasesWithLowerInletFlowAndScreensVolumeResponse() {
    RecycleSizingResult smallDeficit = AdvancedAntiSurgeControlSystem.sizeRecycleSystem(9500.0, 9000.0, 0.10, 35.0, 4.0,
        8.0, 10.0);
    RecycleSizingResult largeDeficit = AdvancedAntiSurgeControlSystem.sizeRecycleSystem(7000.0, 9000.0, 0.10, 35.0, 4.0,
        8.0, 5.0);

    Assertions.assertTrue(largeDeficit.getRequiredRecycleFlow() > smallDeficit.getRequiredRecycleFlow());
    Assertions.assertTrue(largeDeficit.getValveCvScreening() > smallDeficit.getValveCvScreening());
    Assertions.assertFalse(largeDeficit.isVolumeResponseAcceptable(),
        "large recycle piping volume should fail a very fast response target");
  }

  @Test
  void testDualRecycleSplitUsesHotValveNearSurge() {
    AdvancedAntiSurgeControlSystem controller = new AdvancedAntiSurgeControlSystem();
    controller.setDualValveActivation(0.05, -0.02);

    DualRecycleValveCommand normal = controller.splitDualRecycleCommand(60.0, 0.20, 0.0);
    DualRecycleValveCommand emergency = controller.splitDualRecycleCommand(60.0, 0.01, -0.04);

    Assertions.assertEquals(0.0, normal.getHotValveOpening(), 1.0e-12);
    Assertions.assertEquals(60.0, normal.getColdValveOpening(), 1.0e-12);
    Assertions.assertTrue(emergency.getHotValveOpening() > 0.0,
        "hot recycle should open during fast low-margin events");
    Assertions.assertEquals(60.0, emergency.getHotValveOpening() + emergency.getColdValveOpening(), 1.0e-12);
  }

  @Test
  void testAdvancedControlAlgorithmsOpenForFallingPredictedMargin() {
    AdvancedAntiSurgeControlSystem controller = new AdvancedAntiSurgeControlSystem();
    controller.setMarginSetPoint(0.10);
    controller.setPredictionHorizon(5.0);
    controller.setMarginGainPerValvePercent(0.002);

    controller.setAlgorithm(ControlAlgorithm.PI);
    double piDemand = controller.calculateValveCommand(0.14, -0.02, 1.0);
    controller.reset();
    controller.setAlgorithm(ControlAlgorithm.MPC_SCREENING);
    double mpcDemand = controller.calculateValveCommand(0.14, -0.02, 1.0);
    controller.setAlgorithm(ControlAlgorithm.FUZZY_SCREENING);
    double fuzzyDemand = controller.calculateValveCommand(0.14, -0.02, 1.0);

    Assertions.assertEquals(0.0, piDemand, 1.0e-12, "plain PI should stay closed while measured margin is healthy");
    Assertions.assertTrue(mpcDemand > 0.0, "MPC screening should open for low predicted margin");
    Assertions.assertTrue(fuzzyDemand > 0.0, "fuzzy screening should react to fast approach");
  }

  @Test
  void testEducationalSurgeOscillationModelRemainsFiniteAndCountsReversal() {
    SurgeOscillationState state = new SurgeOscillationState(0.10, 0.50, 0);

    SurgeOscillationState next = AdvancedAntiSurgeControlSystem.stepSurgeOscillation(state, 0.1, 0.6, 0.0, 1.0);

    Assertions.assertTrue(Double.isFinite(next.getFlow()));
    Assertions.assertTrue(Double.isFinite(next.getPlenumPressure()));
    Assertions.assertTrue(next.getSurgeCycleCount() >= 1, "flow reversal should count as a surge-cycle indicator");
  }

  @Test
  void testMachineryProtectionStatusIsExplicitlyNotVendorCertified() {
    AdvancedAntiSurgeControlSystem controller = new AdvancedAntiSurgeControlSystem();

    Assertions.assertEquals(AdvancedAntiSurgeControlSystem.CertificationStatus.NOT_VENDOR_CERTIFIED,
        controller.getCertificationStatus());
  }
}
