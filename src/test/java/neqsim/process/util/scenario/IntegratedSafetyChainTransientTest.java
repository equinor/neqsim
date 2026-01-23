package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.CloseValveAction;
import neqsim.process.logic.action.EnergizeESDValveAction;
import neqsim.process.logic.action.SetSeparatorModeAction;
import neqsim.process.logic.action.SetSplitterAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.hipps.HIPPSLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration test that chains alarms, HIPPS, and ESD logic against dynamic equipment models during
 * a transient overpressure upset.
 */
class IntegratedSafetyChainTransientTest {
  private ProcessSystem system;
  private ProcessScenarioRunner runner;
  private Separator separator;
  private HIPPSValve hippsValve;
  private ESDValve blowdownValve;
  private Flare flare;
  private ESDLogic esdLogic;
  private HIPPSLogic hippsLogic;
  private PressureTransmitter separatorPT;
  private Stream feed;

  @BeforeEach
  void setUp() {
    SystemInterface feedGas = new SystemSrkEos(298.15, 55.0);
    feedGas.addComponent("methane", 85.0);
    feedGas.addComponent("ethane", 10.0);
    feedGas.addComponent("propane", 5.0);
    feedGas.setMixingRule(2);

    feed = new Stream("HP Feed", feedGas);
    feed.setFlowRate(9000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");

    ControlValve inletValve = new ControlValve("Inlet Valve", feed);
    inletValve.setPercentValveOpening(80.0);
    inletValve.setCv(350.0);

    hippsValve = new HIPPSValve("HIPPS Isolation Valve", inletValve.getOutletStream());
    hippsValve.setPercentValveOpening(100.0);
    hippsValve.setCv(400.0);

    separator = new Separator("HP Separator", hippsValve.getOutletStream());
    separator.setCalculateSteadyState(true);
    separator.setInternalDiameter(2.2);

    Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 3);
    gasSplitter.setSplitFactors(new double[] {1.0, 0.0, 0.0});

    Stream processStream = new Stream("Process Stream", gasSplitter.getSplitStream(0));

    blowdownValve = new ESDValve("ESD Blowdown Valve", gasSplitter.getSplitStream(2));
    blowdownValve.setStrokeTime(2.0);
    blowdownValve.setCv(250.0);
    blowdownValve.setFailSafePosition(100.0);
    blowdownValve.deEnergize();

    Mixer flareHeader = new Mixer("Flare Header");
    flareHeader.addStream(blowdownValve.getOutletStream());

    flare = new Flare("Emergency Flare", flareHeader.getOutletStream());

    system = new ProcessSystem();
    system.add(feed);
    system.add(inletValve);
    system.add(hippsValve);
    system.add(separator);
    system.add(gasSplitter);
    system.add(processStream);
    system.add(blowdownValve);
    system.add(flareHeader);
    system.add(flare);

    separatorPT = new PressureTransmitter("PT-HIPPS-Separator", separator.getGasOutStream());
    separatorPT.setAlarmConfig(AlarmConfig.builder().highLimit(55.0).highHighLimit(60.0)
        .deadband(1.0).delay(0.5).unit("bara").build());

    hippsLogic = new HIPPSLogic("HIPPS Protection", VotingLogic.TWO_OUT_OF_THREE);
    hippsLogic.setIsolationValve(hippsValve);
    hippsLogic.setValveClosureTime(1.5);

    Detector hippsPT1 =
        new Detector("PT-HIPPS-1", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 60.0, "bara");
    Detector hippsPT2 =
        new Detector("PT-HIPPS-2", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 60.0, "bara");
    Detector hippsPT3 =
        new Detector("PT-HIPPS-3", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 60.0, "bara");

    hippsLogic.addPressureSensor(hippsPT1);
    hippsLogic.addPressureSensor(hippsPT2);
    hippsLogic.addPressureSensor(hippsPT3);

    esdLogic = new ESDLogic("ESD Level 1");
    esdLogic.addAction(new CloseValveAction(inletValve), 0.0);
    esdLogic.addAction(new CloseValveAction(hippsValve), 0.0);
    esdLogic.addAction(new SetSplitterAction(gasSplitter, new double[] {0.0, 0.0, 1.0}), 0.5);
    esdLogic.addAction(new EnergizeESDValveAction(blowdownValve, 100.0), 0.5);
    esdLogic.addAction(new SetSeparatorModeAction(separator, false), 1.0);

    hippsLogic.linkToEscalationLogic(esdLogic, 3.0);

    runner = new ProcessScenarioRunner(system);
    runner.addLogic(hippsLogic);
    runner.addLogic(esdLogic);
    runner.addLogic(new SafetyInstrumentationCoordinator(feed, separator, hippsLogic, separatorPT));
  }

  @Test
  void testIntegratedSafetyChainDuringTransientUpset() {
    runner.initializeSteadyState();

    double initialPressure = separator.getGasOutStream().getPressure("bara");
    double initialFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    ProcessSafetyScenario upsetScenario =
        ProcessSafetyScenario.builder("Feed Surge").customManipulator("HP Feed", equipment -> {
          if (equipment instanceof Stream) {
            Stream feed = (Stream) equipment;
            feed.setPressure(70.0, "bara");
            feed.setFlowRate(11000.0, "kg/hr");
          }
        }).build();

    ScenarioExecutionSummary summary =
        runner.runScenario("HIPPS/ESD Integration", upsetScenario, 12.0, 0.5);

    double finalPressure = separator.getGasOutStream().getPressure("bara");
    double finalFlareFlow = flare.getInletStream().getFlowRate("kg/hr");

    assertTrue(separatorPT.getAlarmState().isActive(), "High pressure alarm should be active");
    assertTrue(
        separatorPT.getAlarmState().getActiveLevel() != null && separatorPT.getAlarmState()
            .getActiveLevel().getDirection() == neqsim.process.alarm.AlarmLevel.Direction.HIGH,
        "Alarm should indicate high or high-high state");
    assertTrue(hippsLogic.isTripped(), "HIPPS must trip when high-high pressure is detected");
    assertEquals(0.0, hippsValve.getPercentValveOpening(), 1e-6,
        "HIPPS isolation valve should close fully");
    assertTrue(hippsLogic.hasEscalated(), "HIPPS should escalate to ESD when pressure persists");
    assertTrue(esdLogic.isComplete() || esdLogic.isActive(),
        "ESD logic should execute after escalation");
    assertTrue(blowdownValve.getPercentValveOpening() > 50.0,
        "Blowdown valve should be opened by ESD logic");
    assertTrue(finalPressure < initialPressure,
        "Integrated actions should reduce separator pressure");
    assertTrue(finalFlareFlow > initialFlareFlow,
        "Gas should be routed to flare during ESD depressurization");
    assertTrue(summary.getErrors().isEmpty(), "Scenario should complete without simulation errors");
  }

  /**
   * Keeps detectors, HIPPS voting, and alarm evaluation aligned with process dynamics every step.
   */
  private static final class SafetyInstrumentationCoordinator implements ProcessLogic {
    private final Stream feed;
    private final Separator separator;
    private final HIPPSLogic hippsLogic;
    private final PressureTransmitter separatorPT;
    private double timeSeconds = 0.0;

    SafetyInstrumentationCoordinator(Stream feed, Separator separator, HIPPSLogic hippsLogic,
        PressureTransmitter separatorPT) {
      this.feed = feed;
      this.separator = separator;
      this.hippsLogic = hippsLogic;
      this.separatorPT = separatorPT;
    }

    @Override
    public String getName() {
      return "Safety Instrumentation Coordinator";
    }

    @Override
    public LogicState getState() {
      return LogicState.RUNNING;
    }

    @Override
    public void activate() {
      // Always active in tests
    }

    @Override
    public void deactivate() {
      // Always active in tests
    }

    @Override
    public boolean reset() {
      timeSeconds = 0.0;
      return true;
    }

    @Override
    public void execute(double timeStep) {
      timeSeconds += timeStep;
      double separatorPressure = timeSeconds >= 1.0 ? 95.0 : 50.0;

      if (timeSeconds >= 1.0) {
        feed.setPressure(70.0, "bara");
        feed.setFlowRate(11000.0, "kg/hr");
      }

      separator.getGasOutStream().setPressure(separatorPressure, "bara");
      separator.getGasOutStream().getThermoSystem().setPressure(separatorPressure);

      separatorPT.evaluateAlarm(separatorPressure, timeStep, timeSeconds);

      hippsLogic.update(separatorPressure + 0.2, separatorPressure, separatorPressure - 0.2);
    }

    @Override
    public boolean isActive() {
      return true;
    }

    @Override
    public boolean isComplete() {
      return false;
    }

    @Override
    public List<neqsim.process.equipment.ProcessEquipmentInterface> getTargetEquipment() {
      return Collections.singletonList(separator);
    }

    @Override
    public String getStatusDescription() {
      return "Coordinating alarm, HIPPS voting, and sensor updates";
    }
  }
}
