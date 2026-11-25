package neqsim.process.util.scenario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Fixed-step synthetic well, gathering, separation and oil/gas export reference flowsheet. The finite vessels exchange
 * pressure-driven withdrawals. The well and short gathering line use algebraic deliverability and Beggs-Brill
 * hydraulics. The terminal relief and blowdown pressure is fixed.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class WellToExportDynamics {
  private final ProcessSystem gathering = new ProcessSystem("Well and gathering");
  private final ProcessSystem host = new ProcessSystem("Production separator");
  private final ProcessSystem oil = new ProcessSystem("Oil export");
  private final List<WellFlow> wells = new ArrayList<WellFlow>();
  private final List<ThrottlingValve> chokes = new ArrayList<ThrottlingValve>();
  private final Separator separator;
  private final ESDValve oilValve;
  private final Pump oilPump;
  private final List<CompressorAutomationStudy.Train> trains = new ArrayList<CompressorAutomationStudy.Train>();
  private final BlowdownValve blowdown;
  private final SafetyValve relief;
  private final PushButton shutdown = new PushButton("HS-101");
  private final PressureTransmitter tripPressure;
  private final LevelTransmitter tripLevel;
  private final List<ClosedLoopSafetyFunction> protection;

  /** Builds and initializes the synthetic field at its nominal well drawdown. */
  public WellToExportDynamics() {
    this(1, 1);
  }

  /**
   * Builds parallel wells and independently protected gas export trains at a common host. The nominal total field rate
   * stays fixed as the number of wells or duty trains changes.
   *
   * @param wellCount number of producing wells with dedicated gathering lines
   * @param trainCount number of gas export trains sharing the separator gas space
   */
  public WellToExportDynamics(int wellCount, int trainCount) {
    if (wellCount < 1 || trainCount < 1) {
      throw new IllegalArgumentException("At least one well and one gas export train are required");
    }
    SystemInterface fluid = new SystemSrkEos(333.15, 100.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.06);
    fluid.setMixingRule("classic");
    for (int i = 0; i < wellCount; i++) {
      addWell(101 + i, fluid, 0.55 / wellCount);
    }
    gathering.run();
    separator = new Separator("V-101", chokes.get(0).getOutletStream());
    for (int i = 1; i < chokes.size(); i++) {
      separator.addStream(chokes.get(i).getOutletStream());
    }
    separator.getMechanicalDesign().setInnerDiameter(2.0);
    separator.getMechanicalDesign().setTantanLength(5.0);
    separator.getMechanicalDesign().setDesign();
    separator.run();
    separator.setLiquidLevel(0.45);
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();
    separator.setConservativeTransientEnergyIntegrationEnabled(true);
    host.add(separator);

    StreamInterface gas = separator.getGasOutStream();
    PressureTransmitter pressure = new PressureTransmitter("PT-101", gas);
    host.add(pressure);
    installGasTrains(gas, pressure, trainCount);

    oilValve = new ESDValve("LV-101", separator.getLiquidOutStream());
    oilValve.setOutletPressure(28.0, "bara");
    oilValve.setPercentValveOpening(50.0);
    oilValve.setFailSafePosition(0.0);
    oilValve.setStrokeTime(0.5);
    oilValve.run();
    oilValve.autoSize(1.0, 50.0);
    oilValve.setCalculateSteadyState(false);
    LevelTransmitter level = new LevelTransmitter("LT-101", separator);
    host.add(level);
    oilValve.setController(controller("LIC-101", level, separator.getLiquidLevel(), "fraction", 60.0));
    Cooler oilCooler = new Cooler("E-101", oilValve.getOutletStream());
    oilCooler.setOutTemperature(285.0);
    oilCooler.run();
    oilPump = new Pump("P-101", oilCooler.getOutletStream());
    oilPump.setOutletPressure(50.0, "bara");
    oilPump.setIsentropicEfficiency(0.75);
    oil.add(oilValve);
    oil.add(oilCooler);
    oil.add(oilPump);
    oilPump.run();
    oilPump.setCalculateSteadyState(false);
    oilPump.setTripCoastdownTimeConstant(0.3);
    oilPump.setStoppedSpeed(1.0);

    relief = new SafetyValve("PSV-101", gas.clone("PSV inlet"));
    relief.setPressureSpec(38.0);
    relief.setFullOpenPressure(40.0);
    configureDisposal(relief, 22.0);
    blowdown = new BlowdownValve("BDV-101", gas.clone("BDV inlet"));
    blowdown.setOpeningTime(1.0);
    configureDisposal(blowdown, 12.0);
    tripPressure = new PressureTransmitter("PT-101-HH", gas);
    tripLevel = new LevelTransmitter("LT-101-HH", separator);
    tripPressure.setTransientSamplingEnabled(true);
    tripLevel.setTransientSamplingEnabled(true);
    host.add(tripPressure);
    host.add(tripLevel);
    host.add(shutdown);
    protection = Arrays.asList(
        protection("PSHH-101", SafetyFunctionChannel.highTripFromMeasurement(tripPressure, "bara", 36.0).build()),
        protection("LSHH-101", SafetyFunctionChannel.highTripFromMeasurement(tripLevel, "fraction", 0.8).build()),
        protection("ESD-101", SafetyFunctionChannel.manualTripFromPushButton(shutdown).build()));
  }

  /**
   * Adds one algebraic inflow and gathering line to the common field.
   *
   * @param tag well number
   * @param fluid reservoir composition
   * @param nominalRate well test rate in MSm3/day
   */
  private void addWell(int tag, SystemInterface fluid, double nominalRate) {
    Stream reservoir = new Stream("Reservoir " + tag, fluid.clone());
    reservoir.setFlowRate(nominalRate, "MSm3/day");
    WellFlow well = new WellFlow("W-" + tag);
    well.setInletStream(reservoir);
    well.setVogelParameters(nominalRate, 60.0, 100.0);
    well.solveFlowFromOutletPressure(true);
    well.setOutletPressure(60.0, "bara");
    PipeBeggsAndBrills line = new PipeBeggsAndBrills("FL-" + tag);
    line.setInletStream(well.getOutletStream());
    line.setLength(250.0);
    line.setDiameter(0.2);
    line.setPipeWallRoughness(3.0e-5);
    line.setNumberOfIncrements(4);
    ThrottlingValve choke = new ThrottlingValve("CH-" + tag, line.getOutletStream());
    choke.setOutletPressure(32.0, "bara");
    gathering.add(reservoir);
    gathering.add(well);
    gathering.add(line);
    gathering.add(choke);
    well.setCalculateSteadyState(false);
    wells.add(well);
    chokes.add(choke);
  }

  /**
   * Sizes each train from its share of the flashed host gas.
   *
   * @param gas separator gas stream
   * @param pressure common host pressure instrument
   * @param trainCount parallel duty trains
   */
  private void installGasTrains(StreamInterface gas, PressureTransmitter pressure, int trainCount) {
    CompressorAutomationStudy.Design design = new CompressorAutomationStudy.Design();
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    for (String name : gas.getFluid().getComponentNames()) {
      composition.put(name, gas.getFluid().getComponent(name).getz());
    }
    design.setComposition(composition);
    design.setFeedFlowKgPerSecond(gas.getFlowRate("kg/sec") / trainCount);
    design.setFeedTemperatureK(gas.getTemperature("K"));
    design.setFeedPressureBara(gas.getPressure("bara"));
    design.setAntiSurgeCv(design.getAntiSurgeCv() / trainCount);
    design.setSuctionLengthM(design.getSuctionLengthM() / trainCount);
    design.setDischargeLengthM(design.getDischargeLengthM() / trainCount);
    for (int i = 0; i < trainCount; i++) {
      CompressorAutomationStudy.Train train = CompressorAutomationStudy.create(design);
      train.getProcess().setName("Gas export train " + (i + 1));
      train.getProcess().setMeasurementHistoryRecordingEnabled(false);
      train.initializeDynamics();
      train.getInletValve()
          .setController(controller("PIC-101-" + (i + 1), pressure, separator.getPressure(), "bara", 5.0));
      trains.add(train);
    }
  }

  /**
   * Creates a direct-acting pressure or level controller on an outlet withdrawal.
   *
   * @param name instrument tag
   * @param device source instrument
   * @param setpoint operating setpoint
   * @param unit engineering unit
   * @param gain opening percent per engineering unit
   * @return configured controller
   */
  private static ControllerDeviceBaseClass controller(String name, MeasurementDeviceInterface device, double setpoint,
      String unit, double gain) {
    ControllerDeviceBaseClass pid = new ControllerDeviceBaseClass(name);
    pid.setTransmitter(device);
    pid.setControllerSetPoint(setpoint, unit);
    pid.setControllerParameters(gain, 10.0, 0.0);
    pid.setOutputLimits(0.0, 100.0);
    return pid;
  }

  /**
   * Configures a gas withdrawal to the fixed 3 bara disposal boundary.
   *
   * @param valve final element
   * @param cv synthetic flow coefficient
   */
  private static void configureDisposal(ThrottlingValve valve, double cv) {
    valve.setOutletPressure(3.0, "bara");
    valve.setCv(cv);
    valve.setPercentValveOpening(0.0);
    valve.setAllowChoked(true);
    valve.setCalculateSteadyState(false);
  }

  /**
   * Builds an independent latched shutdown path for one initiating channel.
   *
   * @param name function tag
   * @param channel actual initiating signal
   * @return active function
   */
  private ClosedLoopSafetyFunction protection(String name, SafetyFunctionChannel channel) {
    ParallelActionGroup actions = new ParallelActionGroup(name);
    actions.addAction(new TripValveAction(oilValve));
    actions.addAction(new ActivateBlowdownAction(blowdown));
    ESDLogic logic = new ESDLogic(name + " actions");
    logic.addAction(actions, 0.0);
    ClosedLoopSafetyFunction function = ClosedLoopSafetyFunction
        .builder(name, name, host, VotingPattern.ONE_OUT_OF_ONE, logic).addChannel(channel).build();
    function.activate();
    return function;
  }

  /**
   * Advances the live plant by one explicit physical interval. Evaluate parallel gas withdrawals from the same
   * beginning-of-step separator state before integrating its component and energy inventories.
   *
   * @param dt step duration in seconds
   */
  public void runTransient(double dt) {
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Step duration must be finite and positive");
    }
    UUID id = UUID.randomUUID();
    tripPressure.sampleTransient(dt, id);
    tripLevel.sampleTransient(dt, id);
    boolean trip = false;
    for (ClosedLoopSafetyFunction function : protection) {
      function.execute(dt);
      trip |= function.hasActuated();
    }
    if (trip) {
      oilPump.trip();
      // Ideal well shut-in boundary. No tubing or gathering-line inventory is released in this model.
      for (WellFlow well : wells) {
        well.setOutletPressure(well.getInletStream().getPressure("bara"), "bara");
      }
    }
    for (ThrottlingValve choke : chokes) {
      choke.setOutletPressure(separator.getPressure(), "bara");
    }
    gathering.runTransient(dt, id);
    StreamInterface gas = separator.getGasOutStream();
    SystemInterface supply = gas.getFluid().clone();
    if (separator.getThermoSystem().hasPhaseType("gas")) {
      // Cv calculations need a finite intensive-state workspace even after all branches closed.
      supply.setTotalNumberOfMoles(1.0);
    } else {
      supply.setTotalNumberOfMoles(0.0);
    }
    double withdrawn = 0.0;
    for (CompressorAutomationStudy.Train train : trains) {
      if (trip) {
        ((PushButton) train.getProcess().getMeasurementDevice("30-HS-100")).push();
      }
      train.getFeed().setFluid(supply.clone());
      train.getProcess().runTransient(dt, id);
      withdrawn += train.getInletValve().getOutletStream().getMolarRate();
    }
    relief.getInletStream().setFluid(supply.clone());
    blowdown.getInletStream().setFluid(supply.clone());
    relief.runTransient(dt, id);
    blowdown.runTransient(dt, id);
    withdrawn += relief.getOutletStream().getMolarRate() + blowdown.getOutletStream().getMolarRate();
    gas.getFluid().setTotalNumberOfMoles(withdrawn);
    oil.runTransient(dt, id);
    host.runTransient(dt, id);
  }

  /** @return live gathering area, including the well and line */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Study inputs and fault injection must address the same live equipment that the transient advances")
  public ProcessSystem getGathering() {
    return gathering;
  }

  /** @return live production separator area and its protection instruments */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Study inputs and fault injection must address the same live equipment that the transient advances")
  public ProcessSystem getHost() {
    return host;
  }

  /** @return live oil export valve and pump area */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Study inputs and fault injection must address the same live equipment that the transient advances")
  public ProcessSystem getOilExport() {
    return oil;
  }

  /** @return live controlled and protected gas export trains in installation order */
  public List<CompressorAutomationStudy.Train> getGasTrains() {
    return Collections.unmodifiableList(trains);
  }

  /** @return production separator relief valve */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Study inputs and fault injection must address the same live equipment that the transient advances")
  public SafetyValve getReliefValve() {
    return relief;
  }

  /** @return production separator emergency blowdown valve */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Study inputs and fault injection must address the same live equipment that the transient advances")
  public BlowdownValve getBlowdownValve() {
    return blowdown;
  }
}
