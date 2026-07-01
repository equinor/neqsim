package neqsim.process.equipment.compressor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.ApplicationStatus;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.CertificationStatus;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.CommissioningReport;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.OperatingMode;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.ScanInput;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.ScanResult;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.SequenceState;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.StageApplication;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.StageScanInput;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for compressor anti-surge application design and simulation support.
 *
 * @author NeqSim
 * @version 1.0
 */
public class CompressorAntiSurgeApplicationTest {
  /**
   * Build a simple compressor with an initialized gas stream for topology-binding tests.
   *
   * @return compressor with suction and outlet streams
   */
  private Compressor buildTopologyCompressor() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule(2);
    fluid.setTotalFlowRate(1.0, "MSm3/day");
    Stream feed = new Stream("topology feed", fluid);
    feed.run();
    Compressor compressor = new Compressor("topology compressor", feed);
    compressor.setOutletPressure(80.0, "bara");
    compressor.setSpeed(10000.0);
    compressor.run();
    return compressor;
  }

  @Test
  void testHeaderCoordinationUsesMostLimitingCompressorMargin() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("export compression");
    application.addStage("LP");
    application.addStage("HP");
    application.addHeader("shared suction", "LP", "HP");
    application.setRunningMode();

    ScanInput input = new ScanInput();
    input.putStageInput("LP", new StageScanInput(0.18, 0.0, 11000.0, 9000.0, 30.0));
    input.putStageInput("HP", new StageScanInput(0.03, -0.01, 9000.0, 8800.0, 40.0));

    ScanResult result = application.scan(input, 0.25);

    Assertions.assertEquals(ApplicationStatus.ALARM, result.getStatus());
    Assertions.assertEquals(1, result.getHeaderDecisions().size());
    Assertions.assertEquals(0.03, result.getHeaderDecisions().get(0).getMinimumMargin(), 1.0e-12);
    Assertions.assertTrue(result.getHeaderDecisions().get(0).getMaximumRecycleDemand() > 0.0);
  }

  @Test
  void testStartupSequencePreopensRecycleBeforeNormalControl() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("startup train");
    application.addStage("stage 1");
    application.setSequencePreopenDemand(45.0);
    application.startStartupSequence();

    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.30, 0.0, 12000.0, 9000.0, 35.0));

    ScanResult result = application.scan(input, 0.5);

    Assertions.assertEquals(OperatingMode.STARTUP, result.getOperatingMode());
    Assertions.assertEquals(SequenceState.RECYCLE_PREOPEN, result.getSequenceState());
    Assertions.assertTrue(result.getStageDecisions().get(0).getTotalRecycleDemand() >= 45.0);
  }

  @Test
  void testTripModeForcesFullRecycleAndTripStatus() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("trip train");
    application.addStage("stage 1");
    application.forceTripMode();

    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.25, 0.0, 12000.0, 9000.0, 35.0));

    ScanResult result = application.scan(input, 0.25);

    Assertions.assertEquals(ApplicationStatus.TRIP_DEMAND, result.getStatus());
    Assertions.assertEquals(100.0, result.getStageDecisions().get(0).getTotalRecycleDemand(), 1.0e-12);
    Assertions.assertEquals(100.0, result.getStageDecisions().get(0).getValveCommand().getHotValveOpening()
        + result.getStageDecisions().get(0).getValveCommand().getColdValveOpening(), 1.0e-12);
  }

  @Test
  void testLowMarginFastApproachUsesHotRecycleValveSplit() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("dual recycle train");
    application.addStage("stage 1");
    application.setRunningMode();
    application.getSupervisor().setDualValveActivation(0.05, -0.02);

    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.02, -0.05, 8200.0, 9000.0, 35.0));

    ScanResult result = application.scan(input, 0.25);

    Assertions.assertTrue(result.getStageDecisions().get(0).getValveCommand().getHotValveOpening() > 0.0);
    Assertions.assertTrue(result.getStageDecisions().get(0).getValveCommand().getColdValveOpening() >= 0.0);
  }

  @Test
  void testCommissioningReportIncludesChecksAndNonCertifiedBoundary() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("commissioning train");
    StageApplication stage = application.addStage("stage 1");
    stage.setDesignBasis(10000.0, 9000.0, 35.0);
    stage.setRecycleDesign(0.10, 4.0, 1.0, 10.0);
    stage.setValveStrokeTimes(2.0, 6.0);

    CommissioningReport report = application.runCommissioningChecks();

    Assertions.assertFalse(report.getChecks().isEmpty());
    Assertions.assertEquals(CertificationStatus.NOT_CERTIFIED_FOR_PROTECTION, report.getCertificationStatus());
    Assertions.assertTrue(report.getCertificationStatement().contains("not a certified"));
  }

  @Test
  void testSensorFaultDegradesScanButKeepsDeterministicResult() {
    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("fault tolerant train");
    StageApplication stage = application.addStage("stage 1");
    stage.setSignalFault(1, AdvancedAntiSurgeControlSystem.SensorFault.INVALID, 0.0);
    application.setRunningMode();

    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.12, -0.01, 10500.0, 9000.0, 35.0));

    ScanResult result = application.scan(input, 0.25);

    Assertions.assertTrue(
        result.getStatus() == ApplicationStatus.HEALTHY || result.getStatus() == ApplicationStatus.DEGRADED);
    Assertions.assertTrue(Double.isFinite(result.getStageDecisions().get(0).getTotalRecycleDemand()));
    Assertions.assertEquals(CertificationStatus.NOT_CERTIFIED_FOR_PROTECTION, result.getCertificationStatus());
  }

  @Test
  void testScanAppliesCommandsToBoundRecycleValves() {
    Compressor compressor = buildTopologyCompressor();
    ThrottlingValve hotValve = new ThrottlingValve("hot recycle valve", compressor.getOutletStream());
    ThrottlingValve coldValve = new ThrottlingValve("cold recycle valve", compressor.getOutletStream());

    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("bound train");
    StageApplication stage = application.addStage("stage 1");
    stage.bindTopology(null, compressor, hotValve, coldValve);
    application.setRunningMode();
    application.getSupervisor().setDualValveActivation(0.05, -0.02);

    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.02, -0.05, 8200.0, 9000.0, 35.0));
    ScanResult result = application.scan(input, 0.25);

    Assertions.assertEquals(result.getStageDecisions().get(0).getValveCommand().getHotValveOpening(),
        hotValve.getPercentValveOpening(), 1.0e-12);
    Assertions.assertEquals(result.getStageDecisions().get(0).getValveCommand().getColdValveOpening(),
        coldValve.getPercentValveOpening(), 1.0e-12);
    Assertions.assertTrue(hotValve.getPercentValveOpening() > 0.0);
  }

  @Test
  void testBoundTopologyCanRunBackCompressorSpeed() {
    Compressor compressor = buildTopologyCompressor();
    ThrottlingValve hotValve = new ThrottlingValve("speed hot recycle valve", compressor.getOutletStream());
    ThrottlingValve coldValve = new ThrottlingValve("speed cold recycle valve", compressor.getOutletStream());

    CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication("speed bound train");
    StageApplication stage = application.addStage("stage 1");
    CompressorAntiSurgeApplication.TopologyBinding binding = stage.bindTopology(null, compressor, hotValve, coldValve);
    binding.enableSpeedControl(70.0, 10.0, 8000.0, 12000.0, 200.0);
    application.setRunningMode();

    double initialSpeed = compressor.getSpeed();
    ScanInput input = new ScanInput();
    input.putStageInput("stage 1", new StageScanInput(0.02, -0.01, 8200.0, 9000.0, 35.0));
    application.scan(input, 1.0);

    Assertions.assertTrue(compressor.getSpeed() < initialSpeed);
    Assertions.assertTrue(compressor.getSpeed() >= 8000.0);
  }
}
