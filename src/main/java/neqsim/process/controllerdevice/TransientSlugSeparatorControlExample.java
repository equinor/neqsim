package neqsim.process.controllerdevice;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Example showing a transient flowline with slug tracking feeding an inlet separator.
 *
 * <p>
 * Pressure on the separator gas outlet and liquid level are controlled by two throttling valves
 * using standard transmitters and PID controllers.
 */
public final class TransientSlugSeparatorControlExample {

  private TransientSlugSeparatorControlExample() {}

  /** Simple holder for the results of the simulation. */
  public static final class SimulationResult {
    private final String slugStatistics;
    private final double liquidLevel;
    private final double gasOutletPressure;
    private final List<Double> times;
    private final List<Double> liquidLevelHistory;
    private final List<Double> gasOutletPressureHistory;

    SimulationResult(
        String slugStatistics,
        double liquidLevel,
        double gasOutletPressure,
        List<Double> times,
        List<Double> liquidLevelHistory,
        List<Double> gasOutletPressureHistory) {
      this.slugStatistics = slugStatistics;
      this.liquidLevel = liquidLevel;
      this.gasOutletPressure = gasOutletPressure;
      this.times = List.copyOf(times);
      this.liquidLevelHistory = List.copyOf(liquidLevelHistory);
      this.gasOutletPressureHistory = List.copyOf(gasOutletPressureHistory);
    }

    public String getSlugStatistics() {
      return slugStatistics;
    }

    public double getLiquidLevel() {
      return liquidLevel;
    }

    public double getGasOutletPressure() {
      return gasOutletPressure;
    }

    public List<Double> getTimes() {
      return times;
    }

    public List<Double> getLiquidLevelHistory() {
      return liquidLevelHistory;
    }

    public List<Double> getGasOutletPressureHistory() {
      return gasOutletPressureHistory;
    }
  }

  /**
   * Set up a terrain-influenced flowline that generates slugs and connects it to a separator with
   * basic pressure and level control.
   */
  public static SimulationResult runSimulation() {
    SystemInterface richGas = new SystemSrkCPAstatoil(283.15, 80.0);
    richGas.addComponent("methane", 0.78);
    richGas.addComponent("n-butane", 0.08);
    richGas.addComponent("n-hexane", 0.06);
    richGas.addComponent("water", 0.08);
    richGas.createDatabase(true);
    richGas.setMixingRule(10);
    richGas.setMultiPhaseCheck(true);

    Stream pipelineInlet = new Stream("pipelineInlet", richGas);
    pipelineInlet.setFlowRate(10.0, "kg/sec");
    pipelineInlet.setPressure(80.0, "bara");
    pipelineInlet.run();

    TransientPipe flowline = new TransientPipe("terrainFlowline", pipelineInlet);
    flowline.setLength(1500.0);
    flowline.setDiameter(0.2);
    flowline.setNumberOfSections(20);
    flowline.setMaxSimulationTime(20.0);

    double[] elevations = new double[20];
    for (int i = 0; i < elevations.length; i++) {
      double position = i / (elevations.length - 1.0);
      elevations[i] = -20.0 * Math.sin(Math.PI * position); // valley to induce slug shedding
    }
    flowline.setElevationProfile(elevations);
    flowline.run();

    ThrottlingValve inletChoke =
        new ThrottlingValve("inletChokeValve", flowline.getOutletStream());
    inletChoke.setOutletPressure(75.0);
    inletChoke.setCalculateSteadyState(false);

    Separator inletSeparator = new Separator("inletSeparator");
    inletSeparator.addStream(inletChoke.getOutletStream());
    inletSeparator.setCalculateSteadyState(false);

    ThrottlingValve liquidCv =
        new ThrottlingValve("liquidControlValve", inletSeparator.getLiquidOutStream());
    liquidCv.setOutletPressure(60.0);
    liquidCv.setCalculateSteadyState(false);

    ThrottlingValve gasCv = new ThrottlingValve("gasControlValve", inletSeparator.getGasOutStream());
    gasCv.setOutletPressure(65.0);
    gasCv.setCalculateSteadyState(false);

    LevelTransmitter levelTt = new LevelTransmitter("LT-100", inletSeparator);
    levelTt.setMaximumValue(1.0);
    levelTt.setMinimumValue(0.0);

    ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LIC-100");
    levelController.setTransmitter(levelTt);
    levelController.setControllerSetPoint(0.45);
    levelController.setControllerParameters(2.0, 200.0, 5.0);
    levelController.setReverseActing(false);
    liquidCv.setController(levelController);

    PressureTransmitter separatorPressureTt =
        new PressureTransmitter("PT-SEP", inletSeparator.getGasOutStream());
    separatorPressureTt.setUnit("bar");
    separatorPressureTt.setMaximumValue(90.0);
    separatorPressureTt.setMinimumValue(30.0);

    ControllerDeviceBaseClass separatorPressureController =
        new ControllerDeviceBaseClass("PIC-SEP");
    separatorPressureController.setTransmitter(separatorPressureTt);
    separatorPressureController.setControllerSetPoint(70.0);
    separatorPressureController.setControllerParameters(1.2, 180.0, 5.0);
    separatorPressureController.setReverseActing(false);
    inletChoke.setController(separatorPressureController);

    PressureTransmitter exportPressureTt = new PressureTransmitter("PT-EXPORT", gasCv.getOutletStream());
    exportPressureTt.setUnit("bar");
    exportPressureTt.setMaximumValue(90.0);
    exportPressureTt.setMinimumValue(20.0);

    ControllerDeviceBaseClass exportPressureController = new ControllerDeviceBaseClass("PIC-100");
    exportPressureController.setTransmitter(exportPressureTt);
    exportPressureController.setControllerSetPoint(55.0);
    exportPressureController.setControllerParameters(1.5, 150.0, 5.0);
    exportPressureController.setReverseActing(false);
    gasCv.setController(exportPressureController);

    ProcessSystem process = new ProcessSystem();
    process.add(pipelineInlet);
    process.add(flowline);
    process.add(inletChoke);
    process.add(inletSeparator);
    process.add(liquidCv);
    process.add(gasCv);
    process.add(levelTt);
    process.add(separatorPressureTt);
    process.add(exportPressureTt);

    process.run();

    double timeStep = 0.5;
    int numberOfSteps = 10;
    process.setTimeStep(timeStep);

    List<Double> times = new ArrayList<>(numberOfSteps + 1);
    List<Double> liquidLevels = new ArrayList<>(numberOfSteps + 1);
    List<Double> gasPressures = new ArrayList<>(numberOfSteps + 1);

    times.add(0.0);
    liquidLevels.add(inletSeparator.getLiquidLevel());
    gasPressures.add(inletSeparator.getGasOutStream().getPressure());

    for (int i = 1; i <= numberOfSteps; i++) {
      process.runTransient();

      times.add(i * timeStep);
      liquidLevels.add(inletSeparator.getLiquidLevel());
      gasPressures.add(inletSeparator.getGasOutStream().getPressure());
    }

    return new SimulationResult(
        flowline.getSlugTracker().getStatisticsString(),
        inletSeparator.getLiquidLevel(),
        inletSeparator.getGasOutStream().getPressure(),
        times,
        liquidLevels,
        gasPressures);
  }

  /**
   * Run the example and print key results to stdout.
   *
   * @param args program args (unused)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SimulationResult results = runSimulation();
    boolean printSeries = args.length > 0 && "--series".equals(args[0]);

    if (printSeries) {
      System.out.println("time_s,liquid_level,gas_outlet_pressure_bar");
      for (int i = 0; i < results.getTimes().size(); i++) {
        System.out.printf(
            "%4.1f,%.6f,%.6f%n",
            results.getTimes().get(i),
            results.getLiquidLevelHistory().get(i),
            results.getGasOutletPressureHistory().get(i));
      }
      return;
    }

    System.out.println("Slug statistics: " + results.getSlugStatistics());
    System.out.println("Separator liquid level: " + results.getLiquidLevel());
    System.out.println("Gas outlet pressure: " + results.getGasOutletPressure());
  }
}
