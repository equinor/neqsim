package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.scenario.AntiSurgeDynamicBenchmark;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for the dynamic compressor scenarios demonstrated in the public {@code dynamiccompressor.ipynb}
 * notebook.
 *
 * @author NeqSim
 * @version 1.0
 */
class DynamicCompressorNotebookRegressionTest {
  private static final double TIME_STEP_SECONDS = 1.0;

  /**
   * A notebook-style speed increase should draw down the suction volume and pack the discharge volume.
   */
  @Test
  void compressorSpeedUpDropsSuctionPressureAndRaisesDischargePressure() {
    DynamicCompressorProcess model = createNotebookStyleProcess(false);
    runTransientSteps(model.process, 20);

    double initialSuctionPressure = model.suctionSeparator.getGasOutStream().getPressure("bara");
    double initialDischargePressure = model.dischargeSeparator.getGasOutStream().getPressure("bara");

    model.compressor.setSpeed(11000.0);
    runTransientSteps(model.process, 80);

    double finalSuctionPressure = model.suctionSeparator.getGasOutStream().getPressure("bara");
    double finalDischargePressure = model.dischargeSeparator.getGasOutStream().getPressure("bara");

    assertTrue(finalSuctionPressure < initialSuctionPressure,
        "suction pressure should drop after compressor speed-up: initial=" + initialSuctionPressure + " bara, final="
            + finalSuctionPressure + " bara");
    assertTrue(finalDischargePressure > initialDischargePressure,
        "discharge pressure should rise after compressor speed-up: initial=" + initialDischargePressure
            + " bara, final=" + finalDischargePressure + " bara");
  }

  /**
   * The pressure controller from the notebook scenario should move compressor speed so discharge pressure reaches the
   * requested set point within a transient tolerance.
   */
  @Test
  void pressureControllerReachesDischargePressureSetpoint() {
    DynamicCompressorProcess model = createNotebookStyleProcess(true);
    double pressureSetpoint = 22.0;

    PressureTransmitter dischargePressureTransmitter = new PressureTransmitter("PT discharge",
        model.dischargeSeparator.getGasOutStream());
    ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass("PC discharge");
    pressureController.setTransmitter(dischargePressureTransmitter);
    pressureController.setControllerSetPoint(pressureSetpoint, "bara");
    pressureController.setReverseActing(true);
    pressureController.setOutputLimits(8600.0, 12200.0);
    pressureController.setControllerParameters(300.0, 25.0, 0.0);
    model.compressor.addController("PC discharge", pressureController);

    runTransientSteps(model.process, 160);
    double controlledPressure = model.dischargeSeparator.getGasOutStream().getPressure("bara");

    assertTrue(Math.abs(controlledPressure - pressureSetpoint) < 0.5,
        "discharge pressure controller should reach setpoint: setpoint=" + pressureSetpoint + " bara, final="
            + controlledPressure + " bara, speed=" + model.compressor.getSpeed());
  }

  /**
   * During the notebook flow-reduction disturbance the production anti-surge controller should open the recycle valve.
   */
  @Test
  void antiSurgeOpensRecycleValveDuringFlowReduction() {
    AntiSurgeDynamicBenchmark benchmark = new AntiSurgeDynamicBenchmark();
    benchmark.run(true);

    assertTrue(benchmark.getMaximumValveOpening() > 5.0,
        "anti-surge valve should open during flow reduction; max opening = " + benchmark.getMaximumValveOpening());
    assertTrue(benchmark.isSurgeAvoided(),
        "anti-surge controller should keep the compressor out of surge; min margin = "
            + benchmark.getMinimumSurgeMargin());
  }

  /**
   * Creates a compact dynamic compressor process with feed and discharge valves, suction and discharge separator
   * inventories, and a generated compressor map.
   *
   * @param dynamicValvePressureCalculation true to calculate valve outlet pressures from Cv during transient runs
   * @return configured process model and key units
   */
  private static DynamicCompressorProcess createNotebookStyleProcess(boolean dynamicValvePressureCalculation) {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 35.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");

    Stream feed = new Stream("compressor feed", gas);
    feed.setFlowRate(12000.0, "kg/hr");
    feed.setPressure(35.0, "bara");
    feed.setTemperature(25.0, "C");

    ThrottlingValve feedValve = new ThrottlingValve("feed valve", feed);
    feedValve.setOutletPressure(10.0, "bara");
    feedValve.setCv(2600.0);
    feedValve.setPercentValveOpening(75.0);
    feedValve.setIsCalcOutPressure(dynamicValvePressureCalculation);

    Separator suctionSeparator = new Separator("suction separator", feedValve.getOutletStream());
    suctionSeparator.setInternalDiameter(1.0);
    suctionSeparator.setSeparatorLength(2.0);

    Compressor compressor = new Compressor("dynamic compressor", suctionSeparator.getGasOutStream());
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.setOutletPressure(18.0, "bara");
    compressor.setSpeed(9800.0);
    compressor.setMinimumSpeed(8200.0);
    compressor.setMaximumSpeed(12500.0);

    Separator dischargeSeparator = new Separator("discharge separator", compressor.getOutletStream());
    dischargeSeparator.setInternalDiameter(1.0);
    dischargeSeparator.setSeparatorLength(2.0);

    ThrottlingValve dischargeValve = new ThrottlingValve("discharge valve", dischargeSeparator.getGasOutStream());
    dischargeValve.setOutletPressure(15.0, "bara");
    dischargeValve.setCv(dynamicValvePressureCalculation ? 2000.0 : 1200.0);
    dischargeValve.setPercentValveOpening(55.0);
    dischargeValve.setIsCalcOutPressure(dynamicValvePressureCalculation);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(feedValve);
    process.add(suctionSeparator);
    process.add(compressor);
    process.add(dischargeSeparator);
    process.add(dischargeValve);
    process.run();

    compressor.generateCompressorCurves();
    compressor.getCompressorChart().setUseCompressorChart(true);
    process.run();

    feedValve.setCalculateSteadyState(false);
    suctionSeparator.setCalculateSteadyState(false);
    compressor.setCalculateSteadyState(false);
    dischargeSeparator.setCalculateSteadyState(false);
    dischargeValve.setCalculateSteadyState(false);

    return new DynamicCompressorProcess(process, suctionSeparator, compressor, dischargeSeparator);
  }

  /**
   * Runs a fixed number of dynamic process steps.
   *
   * @param process process to run
   * @param steps number of transient steps
   */
  private static void runTransientSteps(ProcessSystem process, int steps) {
    for (int step = 0; step < steps; step++) {
      process.runTransient(TIME_STEP_SECONDS, UUID.randomUUID());
    }
  }

  /**
   * Small holder for the units inspected by the regression tests.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static final class DynamicCompressorProcess {
    private final ProcessSystem process;
    private final Separator suctionSeparator;
    private final Compressor compressor;
    private final Separator dischargeSeparator;

    /**
     * Creates a holder for the process and selected units.
     *
     * @param process process model
     * @param suctionSeparator suction separator inventory
     * @param compressor dynamic compressor
     * @param dischargeSeparator discharge separator inventory
     */
    private DynamicCompressorProcess(ProcessSystem process, Separator suctionSeparator, Compressor compressor,
        Separator dischargeSeparator) {
      this.process = process;
      this.suctionSeparator = suctionSeparator;
      this.compressor = compressor;
      this.dischargeSeparator = dischargeSeparator;
    }
  }
}
