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
   * A notebook-style speed increase should draw down the suction inventory.
   *
   * <p>
   * With no controller in the loop the suction pressure exhibits a sustained limit-cycle oscillation, so an
   * instantaneous single-sample comparison samples an arbitrary phase of the cycle and is platform dependent (it can
   * flip sign between operating systems for the same build). The maneuver is therefore evaluated with pressures
   * time-averaged over a window that spans the oscillation, which cancels the limit-cycle phase and yields a
   * deterministic, platform-robust result. The discharge inventory must not collapse over the same maneuver.
   * </p>
   */
  @Test
  void compressorSpeedUpDrawsDownSuctionInventory() {
    DynamicCompressorProcess model = createNotebookStyleProcess(false);
    runTransientSteps(model.process, 40);
    double[] baseline = averagePressures(model, 120);
    double baselineSuction = baseline[0];
    double baselineDischarge = baseline[1];

    model.compressor.setSpeed(11000.0);
    runTransientSteps(model.process, 80);
    double[] settled = averagePressures(model, 120);
    double settledSuction = settled[0];
    double settledDischarge = settled[1];

    assertTrue(settledSuction < baselineSuction - 10.0,
        "time-averaged suction pressure should draw down after compressor speed-up: baseline=" + baselineSuction
            + " bara, settled=" + settledSuction + " bara");
    assertTrue(settledDischarge > baselineDischarge - 0.5,
        "time-averaged discharge pressure should not collapse after compressor speed-up: baseline=" + baselineDischarge
            + " bara, settled=" + settledDischarge + " bara");
  }

  /**
   * The reverse-acting discharge-pressure controller must move compressor speed in the physically correct direction: a
   * higher pressure set point commands a higher speed, a lower set point commands a lower speed, and a reachable set
   * point is tracked.
   *
   * <p>
   * Exact tracking of an arbitrary discharge-pressure set point is <em>not</em> asserted, because the two dynamic
   * separator inventories on either side of the machine are slow, hysteretic integrators: at a fixed compressor speed
   * the discharge volume can settle on more than one packed/de-packed operating branch, so the pressure that a fixed
   * speed produces depends on the approach path. Which branch the loop lands on therefore depends on the exact
   * controller trajectory (this is the same limit-cycle / path-dependence pathology handled by
   * {@link #compressorSpeedUpDrawsDownSuctionInventory()}). The deterministic, platform-robust invariant that a
   * regression test can rely on is the controller's manipulated-variable response: the compressor speed moves toward
   * its high limit when more discharge pressure is demanded and toward its low limit when less is demanded, and a set
   * point inside the achievable range is reached.
   * </p>
   */
  @Test
  void pressureControllerReachesDischargePressureSetpoint() {
    // A discharge-pressure set point below the current operating pressure: the reverse-acting controller reduces
    // compressor speed toward its low limit until the measured pressure settles at the (reachable) set point.
    DynamicCompressorProcess low = createNotebookStyleProcess(true);
    double reachableSetpoint = 11.0;
    addDischargePressureController(low, reachableSetpoint);
    runTransientSteps(low.process, 160);
    double controlledPressure = low.dischargeSeparator.getGasOutStream().getPressure("bara");
    double lowDemandSpeed = low.compressor.getSpeed();

    assertTrue(Math.abs(controlledPressure - reachableSetpoint) < 1.0,
        "controller should track a reachable discharge-pressure set point: setpoint=" + reachableSetpoint
            + " bara, final=" + controlledPressure + " bara, speed=" + lowDemandSpeed);

    // A set point above the compressor's achievable discharge pressure: the controller must drive the speed up toward
    // its maximum output limit in the attempt.
    DynamicCompressorProcess high = createNotebookStyleProcess(true);
    double aggressiveSetpoint = 22.0;
    addDischargePressureController(high, aggressiveSetpoint);
    runTransientSteps(high.process, 160);
    double highDemandSpeed = high.compressor.getSpeed();

    assertTrue(highDemandSpeed > lowDemandSpeed + 500.0,
        "a higher discharge-pressure set point must command a higher compressor speed: lowDemandSpeed=" + lowDemandSpeed
            + ", highDemandSpeed=" + highDemandSpeed);
    assertTrue(highDemandSpeed >= 12200.0 - 1.0,
        "an unreachable discharge-pressure set point should saturate the compressor at its maximum output limit: speed="
            + highDemandSpeed);
  }

  /**
   * Adds a reverse-acting discharge-pressure controller that manipulates compressor speed.
   *
   * @param model process model to instrument
   * @param pressureSetpoint discharge-pressure set point in bara
   */
  private static void addDischargePressureController(DynamicCompressorProcess model, double pressureSetpoint) {
    PressureTransmitter dischargePressureTransmitter = new PressureTransmitter("PT discharge",
        model.dischargeSeparator.getGasOutStream());
    ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass("PC discharge");
    pressureController.setTransmitter(dischargePressureTransmitter);
    pressureController.setControllerSetPoint(pressureSetpoint, "bara");
    pressureController.setReverseActing(true);
    pressureController.setOutputLimits(8600.0, 12200.0);
    pressureController.setControllerParameters(300.0, 25.0, 0.0);
    model.compressor.addController("PC discharge", pressureController);
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

  private static double[] averagePressures(DynamicCompressorProcess model, int steps) {
    double suction = 0.0;
    double discharge = 0.0;
    for (int step = 0; step < steps; step++) {
      model.process.runTransient(TIME_STEP_SECONDS, UUID.randomUUID());
      suction += model.suctionSeparator.getGasOutStream().getPressure("bara");
      discharge += model.dischargeSeparator.getGasOutStream().getPressure("bara");
    }
    return new double[] { suction / steps, discharge / steps };
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
