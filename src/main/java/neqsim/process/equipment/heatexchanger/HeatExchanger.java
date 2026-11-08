/*
 * HeatExchanger.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.process.equipment.heatexchanger;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.process.conditionmonitor.ConditionMonitorSpecifications;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.ml.StateVector;
import neqsim.process.ml.StateVectorProvider;
import neqsim.process.util.monitor.HXResponse;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.process.util.report.ReportConfig;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSeawater;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import org.apache.commons.lang3.SerializationUtils;

/**
 * HeatExchanger class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class HeatExchanger extends Heater
    implements HeatExchangerInterface, StateVectorProvider, TransientStateParticipant<HeatExchanger.Snapshot> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Design mode for the heat exchanger.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum DesignMode {
    /** Sizing mode: given UA, compute outlet temperatures (default). */
    SIZING,
    /**
     * Rating mode: compute UA from geometry and fluid properties, then find outlet temperatures.
     */
    RATING
  }

  boolean setTemperature = false;
  StreamInterface[] outStream = new Stream[2];
  StreamInterface[] inStream = new Stream[2];
  SystemInterface system;
  volatile double NTU;
  protected double temperatureOut = 0;
  protected String temperatureOutUnit = "K";

  protected double dT = 0.0;

  double dH = 0.0;
  private double UAvalue = 500.0;
  volatile double duty = 0.0;
  private double hotColdDutyBalance = 1.0;
  volatile boolean firstTime = true;
  public double guessOutTemperature = 273.15 + 130.0;
  public String guessOutTemperatureUnit = "K";
  int outStreamSpecificationNumber = 0;
  public volatile double thermalEffectiveness = 0.0;
  private String flowArrangement = "concentric tube counterflow";
  /** Last side-0 utility composition, atomically published for recalculation checks [g/kg solution]. */
  private volatile double lastSalinity0 = Double.NaN;
  /** Last bulk utility composition on side 1 [g/kg solution]. */
  private volatile double lastSalinity1 = Double.NaN;
  private boolean useDeltaT = false;
  private double deltaT = 1.0;

  /** Design mode: SIZING (default, uses user-supplied UA) or RATING (calculates UA). */
  private DesignMode designMode = DesignMode.SIZING;

  /** Tube-side shell-and-tube geometry for rating mode. */
  private transient neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator ratingCalculator;

  /** Number of shell passes for LMTD correction (1 = TEMA E, 2 = TEMA F). */
  private int shellPasses = 1;

  /** Calculated overall U value from rating mode (W/(m2*K)). */
  private double ratingU = 0.0;

  /** Heat transfer area used in rating mode (m2). */
  private double ratingArea = 0.0;

  /** Opt-in single-phase rating with correlated pressure losses on both outlet streams. */
  private boolean useRatingPressureDrop = false;

  // ============ Capacity Constraint Fields ============
  /** Design duty in Watts for capacity constraint. */
  private double designDuty = 0.0;
  /** Design UA value in W/K for capacity constraint. */
  private double designUAValue = 0.0;
  /** Minimum approach temperature in K. */
  private double minApproachTemperature = 5.0;
  /** Maximum shell-side pressure drop in bar. */
  private double maxShellPressureDrop = 0.5;
  /** Maximum tube-side pressure drop in bar. */
  private double maxTubePressureDrop = 0.5;
  /** Capacity constraints map. */
  private java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> hxCapacityConstraints = new java.util.LinkedHashMap<String, neqsim.process.equipment.capacity.CapacityConstraint>();
  /** Flag for HX-specific capacity analysis. */
  private boolean hxCapacityAnalysisEnabled = true;

  /** Cached inlet stream 2 temperature for needRecalculation check. */
  private double lastInStream2Temperature = 0.0;
  /** Cached inlet stream 2 pressure for needRecalculation check. */
  private double lastInStream2Pressure = 0.0;
  /** Cached inlet stream 2 flow rate for needRecalculation check. */
  private double lastInStream2FlowRate = 0.0;
  /** Cached UA value for needRecalculation check. */
  private double lastUAvalue = 0.0;
  /** Cached inlet stream 2 composition for needRecalculation check. */
  private double[] lastInStream2Composition = null;

  // Dynamic simulation fields
  /** Metal wall mass in kg. */
  private double wallMass = 0.0;
  /** Metal wall specific heat capacity in J/(kg*K). */
  private double wallCp = 500.0;
  /** Metal wall temperature in Kelvin — state variable for dynamic simulation. */
  private double wallTemperature = Double.NaN;
  /** Shell-side fluid holdup volume in m3. */
  private double shellHoldupVolume = 0.0;
  /** Tube-side fluid holdup volume in m3. */
  private double tubeHoldupVolume = 0.0;
  /** Shell-side heat transfer coefficient in W/(m2*K). */
  private double shellSideHtc = 500.0;
  /** Tube-side heat transfer coefficient in W/(m2*K). */
  private double tubeSideHtc = 1000.0;
  /** Heat transfer area in m2 (for dynamic model). */
  private double heatTransferArea = 0.0;
  /** Whether the dynamic heat exchanger model is enabled. */
  private volatile boolean dynamicModelEnabled = false;
  /** Whether the newly selected execution mode still needs its first completed calculation. */
  private volatile boolean modeObservationPending;

  /** Stable identity of this live transaction participant. */
  private volatile String transientIdentity = UUID.randomUUID().toString();
  /** Atomic identity repair for fields absent from legacy serialized exchangers. */
  private static final AtomicReferenceFieldUpdater<HeatExchanger, String> TRANSIENT_IDENTITY_UPDATER = AtomicReferenceFieldUpdater
      .newUpdater(HeatExchanger.class, String.class, "transientIdentity");
  /** Last committed inventory, independent of connected inlet flow magnitudes. */
  private ThermalState thermalState;
  /** Start state retained for repeated evaluations of one physical interval. */
  private ThermalState stepStart;
  /** Last physical interval identity. */
  private UUID lastPhysicalId;
  /** Duration of the retained interval in seconds. */
  private volatile double lastStepSeconds;
  /** Clock at the retained interval start. */
  private volatile double stepStartTime;
  /** Last accepted thermal first-law receipt. */
  private Balance lastBalance;

  /**
   * Constructor for HeatExchanger.
   *
   * @param name name of heat exchanger
   */
  public HeatExchanger(String name) {
    super(name);
  }

  /**
   * Constructor for HeatExchanger.
   *
   * @param name name of heat exchanger
   * @param inStream1 input stream
   */
  public HeatExchanger(String name, StreamInterface inStream1) {
    this(name, inStream1, inStream1);
  }

  /**
   * Constructor for HeatExchanger.
   *
   * @param name name of heat exchanger
   * @param inStream1 input stream 1
   * @param inStream2 input stream 2
   */
  public HeatExchanger(String name, StreamInterface inStream1, StreamInterface inStream2) {
    this(name);
    this.inStream[0] = inStream1;
    this.inStream[1] = inStream2;
    outStream[0] = inStream1.clone();
    outStream[1] = inStream2.clone();
    setName(name);
  }

  /**
   * Add inlet stream.
   *
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addInStream(StreamInterface inStream) {
    // todo: this is probably intended to specifically set the second stream. should
    // be deprecated
    // and replaced by setFeedStream?
    this.inStream[1] = inStream;
  }

  /**
   * setFeedStream. Will also set name of outstreams.
   *
   * @param number a int
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setFeedStream(int number, StreamInterface inStream) {
    this.inStream[number] = inStream;
    outStream[number] = inStream.clone();
    setName(getName());
  }

  /** {@inheritDoc} */
  @Override
  public HeatExchangerMechanicalDesign getMechanicalDesign() {
    return super.getMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    super.initMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    if (outStream[0] != null) {
      outStream[0].setName(name + "_Sout1");
    }
    if (outStream[1] != null) {
      outStream[1].setName(name + "_Sout2");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setdT(double dT) {
    this.dT = dT;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutStream(int i) {
    return outStream[i];
  }

  /**
   * Returns both inlet streams of the heat exchanger.
   *
   * @return a list containing the non-null inlet streams for side 0 and side 1
   */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inlets = new ArrayList<StreamInterface>();
    if (inStream[0] != null) {
      inlets.add(inStream[0]);
    }
    if (inStream[1] != null) {
      inlets.add(inStream[1]);
    }
    return inlets;
  }

  /**
   * Returns the first outlet stream (side 0) of the heat exchanger.
   *
   * <p>
   * HeatExchanger has two outlet streams corresponding to the two feed sides. This method returns the outlet for feed
   * side 0. Use {@link #getOutStream(int)} to access a specific side.
   * </p>
   *
   * @return the outlet stream for feed side 0
   */
  @Override
  public StreamInterface getOutletStream() {
    return outStream[0];
  }

  /**
   * Returns both outlet streams of the heat exchanger.
   *
   * @return a list containing both outlet streams (side 0 and side 1)
   */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>();
    if (outStream[0] != null) {
      outlets.add(outStream[0]);
    }
    if (outStream[1] != null) {
      outlets.add(outStream[1]);
    }
    return outlets;
  }

  /**
   * Getter for the field <code>inStream</code>.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getInStream(int i) {
    return inStream[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    this.temperatureOut = temperature;
    this.temperatureOutUnit = "K";
  }

  /**
   * Set a fixed outlet temperature for one side of the exchanger and use the {@code "outTemperature"} specification.
   * The specified side (see {@link #setOutStreamSpecificationNumber(int)}, default 0) is pinned to this temperature at
   * its inlet pressure and the opposite side is energy-balanced against it. This lets a caller reproduce a known
   * process-side outlet temperature exactly, independent of the flows.
   *
   * @param temperature outlet temperature value
   * @param unit temperature unit (e.g. "K" or "C")
   */
  public void setOutTemperature(double temperature, String unit) {
    this.temperatureOut = temperature;
    this.temperatureOutUnit = unit;
    setSpecification("outTemperature");
  }

  /**
   * Set which side (0 or 1) is pinned when using the {@code "out stream"} or {@code "outTemperature"} specification.
   *
   * @param streamNumber index of the specified outlet side (0 or 1)
   */
  public void setOutStreamSpecificationNumber(int streamNumber) {
    this.outStreamSpecificationNumber = streamNumber;
  }

  /**
   * Get temperature of outstream i.
   *
   * @param i a int
   * @return a double
   */
  public double getOutTemperature(int i) {
    return outStream[i].getThermoSystem().getTemperature();
  }

  /**
   * Get temperature of instream i.
   *
   * @param i a int
   * @return a double
   */
  public double getInTemperature(int i) {
    return inStream[i].getThermoSystem().getTemperature();
  }

  /**
   * Setter for the field <code>outStream</code>.
   *
   * @param outStream the outStream to set
   * @param streamNumber a int
   */
  public void setOutStream(int streamNumber, StreamInterface outStream) {
    this.outStream[streamNumber] = outStream;
    outStreamSpecificationNumber = streamNumber;
  }

  /**
   * runSpecifiedStream.
   *
   * @param id UUID of run
   */
  public void runSpecifiedStream(UUID id) {
    int nonOutStreamSpecifiedStreamNumber = 0;
    if (outStreamSpecificationNumber == 0) {
      nonOutStreamSpecifiedStreamNumber = 1;
    }

    SystemInterface systemOut0 = inStream[nonOutStreamSpecifiedStreamNumber].getThermoSystem().clone();
    // SystemInterface systemOut1 =
    // inStream[outStreamSpecificationNumber].getThermoSystem().clone();

    if (getSpecification().equals("out stream")) {
      outStream[outStreamSpecificationNumber]
          .setFlowRate(getInStream(outStreamSpecificationNumber).getFlowRate("kg/sec"), "kg/sec");
      outStream[outStreamSpecificationNumber].run(id);
      temperatureOut = outStream[outStreamSpecificationNumber].getTemperature();
      // system =
      // outStream[outStreamSpecificationNumber].getThermoSystem().clone();
    } else if (getSpecification().equals("outTemperature")) {
      // Pin the specified side to a fixed outlet temperature at its inlet
      // pressure, re-evaluated every iteration so the outlet pressure tracks the
      // live inlet and only the temperature is held constant.
      SystemInterface specifiedOut = inStream[outStreamSpecificationNumber].getThermoSystem().clone();
      specifiedOut.setTemperature(temperatureOut, temperatureOutUnit);
      ThermodynamicOperations specOps = new ThermodynamicOperations(specifiedOut);
      specOps.TPflash();
      // Rebuild caloric properties before using the pinned side in the energy balance.
      specifiedOut.init(2);
      outStream[outStreamSpecificationNumber]
          .setFlowRate(getInStream(outStreamSpecificationNumber).getFlowRate("kg/sec"), "kg/sec");
      outStream[outStreamSpecificationNumber].setThermoSystem(specifiedOut);
      // Do NOT overwrite temperatureOut here: it is the user-specified pin, held
      // constant every iteration. Reading specifiedOut.getTemperature() returns
      // Kelvin and would corrupt the "C"-based pin, escalating on each recycle
      // pass.
    }

    double deltaEnthalpy = outStream[outStreamSpecificationNumber].getFluid().getEnthalpy()
        - inStream[outStreamSpecificationNumber].getFluid().getEnthalpy();
    double enthalpyOutRef = inStream[nonOutStreamSpecifiedStreamNumber].getFluid().getEnthalpy() - deltaEnthalpy;

    ThermodynamicOperations testOps = new ThermodynamicOperations(systemOut0);
    testOps.PHflash(enthalpyOutRef);
    outStream[nonOutStreamSpecifiedStreamNumber].setFluid(systemOut0);
  }

  /**
   * runDeltaT.
   *
   * @param id UUID of run
   */
  public void runDeltaT(UUID id) {
    if (getSpecification().equals("out stream") || getSpecification().equals("outTemperature")) {
      runSpecifiedStream(id);
    } else if (firstTime) {
      firstTime = false;
      SystemInterface systemOut0 = inStream[0].getThermoSystem().clone();
      outStream[0].setThermoSystem(systemOut0);
      outStream[0].getThermoSystem().setTemperature(guessOutTemperature, guessOutTemperatureUnit);
      outStream[0].run(id);
      run(id);
    } else {
      int streamToCalculate = 0;

      for (StreamInterface stream : inStream) {
        stream.run();
      }

      int streamToSet = 1;
      SystemInterface systemOut0 = inStream[streamToSet].getThermoSystem().clone();
      SystemInterface systemOut1 = inStream[streamToCalculate].getThermoSystem().clone();
      double sign = Math.signum(inStream[streamToCalculate].getTemperature() - inStream[streamToSet].getTemperature());
      // systemOut1.setTemperature(inTemp1);
      outStream[streamToSet].setThermoSystem(systemOut0);
      outStream[streamToCalculate].setThermoSystem(systemOut1);
      outStream[streamToSet].setTemperature(inStream[streamToCalculate].getTemperature() + sign * deltaT, "K");
      if (!outStream[streamToSet].getSpecification().equals("TP")) {
        outStream[streamToSet].runTPflash();
      }
      outStream[streamToSet].run(id);
      double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();
      double C1 = Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
          - inStream[streamToSet].getThermoSystem().getTemperature()));

      outStream[streamToCalculate]
          .setTemperature(inStream[streamToSet].getThermoSystem().getTemperature() - sign * deltaT, "K");
      if (!outStream[streamToCalculate].getSpecification().equals("TP")) {
        outStream[streamToCalculate].runTPflash();
      }
      outStream[streamToCalculate].run(id);
      double dEntalphy2 = outStream[streamToCalculate].getThermoSystem().getEnthalpy()
          - inStream[streamToCalculate].getThermoSystem().getEnthalpy();
      double C2 = Math.abs(dEntalphy2) / Math.abs(outStream[streamToCalculate].getThermoSystem().getTemperature()
          - inStream[streamToCalculate].getThermoSystem().getTemperature());
      double Cmin = C1;
      double Cmax = C2;
      if (C2 < C1) {
        Cmin = C2;
        Cmax = C1;
      }
      double Cr = Cmin / Cmax;
      if (Math.abs(dEntalphy1) < Math.abs(dEntalphy2)) {
        int streamCHange = streamToCalculate;
        streamToCalculate = streamToSet;
        streamToSet = streamCHange;
      }

      double dEntalphy = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();
      // System.out.println("dent " + dEntalphy);
      ThermodynamicOperations testOps = new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
      testOps.PHflash(inStream[streamToCalculate].getThermoSystem().getEnthalpy() - dEntalphy, 0);

      if (Math.abs(thermalEffectiveness - 1.0) > 1e-10) {
        testOps = new ThermodynamicOperations(outStream[streamToSet].getThermoSystem());
        testOps.PHflash(inStream[streamToSet].getThermoSystem().getEnthalpy() + dEntalphy, 0);
      }
      duty = dEntalphy;
      hotColdDutyBalance = 1.0;

      UAvalue = dEntalphy / (outStream[streamToSet].getThermoSystem().getTemperature()
          - inStream[streamToSet].getThermoSystem().getTemperature());
    }

    updateLastState();
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (dynamicModelEnabled) {
      return true;
    }
    // The caller can change geometry or fouling through the mutable rating calculator.
    if (designMode == DesignMode.RATING) {
      return true;
    }
    if (firstTime || inStream[0] == null || inStream[1] == null) {
      return true;
    }
    if (utilitySalinity(inStream[0].getThermoSystem()) != lastSalinity0
        || utilitySalinity(inStream[1].getThermoSystem()) != lastSalinity1) {
      return true;
    }
    if (super.needRecalculation()) {
      return true;
    }
    if (inStream[1].getThermoSystem() == null) {
      return true;
    }
    SystemInterface sys2 = inStream[1].getThermoSystem();
    if (sys2.getTemperature() != lastInStream2Temperature || sys2.getPressure() != lastInStream2Pressure
        || UAvalue != lastUAvalue) {
      return true;
    }
    double flow2 = sys2.getFlowRate("kg/hr");
    if (flow2 > 0 && Math.abs(flow2 - lastInStream2FlowRate) / flow2 > 1e-6) {
      return true;
    }
    if (lastInStream2Composition == null) {
      return true;
    }
    // Allocation-free composition comparison.
    neqsim.thermo.phase.PhaseInterface ph0 = sys2.getPhase(0);
    int n = ph0.getNumberOfComponents();
    if (n != lastInStream2Composition.length) {
      return true;
    }
    for (int i = 0; i < n; i++) {
      if (ph0.getComponent(i).getz() != lastInStream2Composition[i]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Updates cached state for needRecalculation checks. Saves both HeatExchanger stream-2 state and Heater base-class
   * stream-1 state so that super.needRecalculation() returns false when inputs are unchanged.
   */
  private void updateLastState() {
    if (inStream[0] != null && inStream[1] != null) {
      lastSalinity0 = utilitySalinity(inStream[0].getThermoSystem());
      lastSalinity1 = utilitySalinity(inStream[1].getThermoSystem());
    }
    // Save stream 2 state (HeatExchanger-specific)
    if (inStream[1] != null && inStream[1].getThermoSystem() != null) {
      lastInStream2Temperature = inStream[1].getThermoSystem().getTemperature();
      lastInStream2Pressure = inStream[1].getThermoSystem().getPressure();
      lastInStream2FlowRate = inStream[1].getThermoSystem().getFlowRate("kg/hr");
    }
    lastUAvalue = UAvalue;

    // Save Heater base-class state (stream 0) so super.needRecalculation() works correctly.
    // The Heater's inStream field (not shadowed array) refers to stream 0 via the constructor.
    if (inStream[0] != null && inStream[0].getThermoSystem() != null) {
      lastTemperature = inStream[0].getThermoSystem().getTemperature();
      lastPressure = inStream[0].getThermoSystem().getPressure();
      lastFlowRate = inStream[0].getThermoSystem().getFlowRate("kg/hr");
    }
    lastDuty = getDuty();
    lastOutPressure = pressureOut;
    lastOutTemperature = temperatureOut;
    lastPressureDrop = getPressureDrop();
    // Composition tracking for both streams
    if (inStream[0] != null && inStream[0].getThermoSystem() != null) {
      lastComposition = inStream[0].getThermoSystem().getMolarComposition().clone();
    }
    if (inStream[1] != null && inStream[1].getThermoSystem() != null) {
      lastInStream2Composition = inStream[1].getThermoSystem().getMolarComposition().clone();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (dynamicModelEnabled) {
      runThermalSteadyState(id);
      return;
    }
    if (useRatingPressureDrop) {
      runThermalHydraulicRating(id);
      return;
    }
    if (useDeltaT) {
      runDeltaT(id);
      publishHeatDuty();
      updateLastState();
      return;
    }
    if (getSpecification().equals("out stream") || getSpecification().equals("outTemperature")) {
      runSpecifiedStream(id);
    } else if (firstTime) {
      firstTime = false;
      SystemInterface systemOut0 = inStream[0].getThermoSystem().clone();
      outStream[0].setThermoSystem(systemOut0);
      outStream[0].getThermoSystem().setTemperature(guessOutTemperature, guessOutTemperatureUnit);
      outStream[0].run(id);
      run(id);
    } else {
      int streamToCalculate = 0;

      // double cP0 = inStream[0].getThermoSystem().getCp();
      // double cP1 = inStream[1].getThermoSystem().getCp();
      // if (cP0 < cP1) {
      // streamToCalculate = 1;
      // streamToSet = 0;
      // }

      // Make sure these streams to run because of the issues with enthalpy
      // calculations if not run
      for (StreamInterface stream : inStream) {
        stream.run();
      }

      int streamToSet = 1;
      SystemInterface systemOut0 = inStream[streamToSet].getThermoSystem().clone();
      SystemInterface systemOut1 = inStream[streamToCalculate].getThermoSystem().clone();

      // systemOut1.setTemperature(inTemp1);
      outStream[streamToSet].setThermoSystem(systemOut0);
      outStream[streamToCalculate].setThermoSystem(systemOut1);
      outStream[streamToSet]
          .setTemperature(capacityEvaluationTemperature(systemOut0, inStream[streamToCalculate].getTemperature()), "K");
      if (!outStream[streamToSet].getSpecification().equals("TP")) {
        outStream[streamToSet].runTPflash();
      }
      outStream[streamToSet].run(id);
      double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();
      double C1 = Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
          - inStream[streamToSet].getThermoSystem().getTemperature()));

      outStream[streamToCalculate].setTemperature(
          capacityEvaluationTemperature(systemOut1, inStream[streamToSet].getThermoSystem().getTemperature()), "K");
      if (!outStream[streamToCalculate].getSpecification().equals("TP")) {
        outStream[streamToCalculate].runTPflash();
      }
      outStream[streamToCalculate].run(id);
      double dEntalphy2 = outStream[streamToCalculate].getThermoSystem().getEnthalpy()
          - inStream[streamToCalculate].getThermoSystem().getEnthalpy();
      double C2 = Math.abs(dEntalphy2) / Math.abs(outStream[streamToCalculate].getThermoSystem().getTemperature()
          - inStream[streamToCalculate].getThermoSystem().getTemperature());
      double Cmin = C1;
      double Cmax = C2;
      if (C2 < C1) {
        Cmin = C2;
        Cmax = C1;
      }
      double Cr = Cmin / Cmax;
      boolean referenceUtility = systemOut0 instanceof neqsim.thermo.system.SystemSeawater
          || systemOut1 instanceof neqsim.thermo.system.SystemSeawater;
      if (referenceUtility ? C1 > C2 : Math.abs(dEntalphy1) > Math.abs(dEntalphy2)) {
        int streamCHange = streamToCalculate;
        streamToCalculate = streamToSet;
        streamToSet = streamCHange;
      }

      double dEntalphy = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();

      // A saline stream must not be evaluated at a fictitious hot-feed temperature
      // outside its range. Its secant Cp uses a bounded liquid interval; Qmax still
      // uses the actual inlet temperature difference, as required by the NTU model.
      if (referenceUtility) {
        dEntalphy = Cmin * (inStream[streamToCalculate].getTemperature() - inStream[streamToSet].getTemperature());
      }

      // Rating mode: compute UA from correlations instead of using user-supplied value
      if (designMode == DesignMode.RATING && ratingCalculator != null && ratingArea > 0) {
        updateRatingCalculatorFromStreams();
        ratingCalculator.calculate();
        ratingU = ratingCalculator.getOverallU();
        if (ratingU > 0) {
          UAvalue = ratingU * ratingArea;
        }
      }

      NTU = UAvalue / Cmin;

      thermalEffectiveness = calcThermalEffectivenes(NTU, Cr);
      // double corrected_Entalphy = dEntalphy; // *
      // inStream[1].getThermoSystem().getNumberOfMoles() /
      // inStream[0].getThermoSystem().getNumberOfMoles();
      dEntalphy = thermalEffectiveness * dEntalphy;
      // System.out.println("dent " + dEntalphy);
      ThermodynamicOperations testOps = new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
      testOps.PHflash(inStream[streamToCalculate].getThermoSystem().getEnthalpy() - dEntalphy, 0);

      if (Math.abs(thermalEffectiveness - 1.0) > 1e-10) {
        testOps = new ThermodynamicOperations(outStream[streamToSet].getThermoSystem());
        testOps.PHflash(inStream[streamToSet].getThermoSystem().getEnthalpy() + dEntalphy, 0);
      }
      duty = dEntalphy;
      hotColdDutyBalance = 1.0;
      // outStream[0].displayResult();
      // outStream[1].displayResult();
      // System.out.println("temperatur Stream 1 out " +
      // outStream[0].getTemperature());
      // System.out.println("temperatur Stream 0 out " +
      // outStream[1].getTemperature());
      // outStream[0].setThermoSystem(systemOut0);
      // System.out.println("temperature out " +
      // outStream[streamToCalculate].getTemperature());
      /*
       * if (systemOut0.getTemperature() <= inTemp1 - dT) { systemOut0.setTemperature(inTemp1);
       * outStream[0].setThermoSystem(systemOut0); outStream[0].run(); //inStream[0].run();
       *
       * dEntalphy = outStream[0].getThermoSystem().getEnthalpy() - inStream[0].getThermoSystem().getEnthalpy();
       * corrected_Entalphy = dEntalphy * inStream[0].getThermoSystem().getNumberOfMoles() /
       * inStream[1].getThermoSystem().getNumberOfMoles();
       *
       * systemOut1 = inStream[1].getThermoSystem().clone(); System.out.println("dent " + dEntalphy); testOps = new
       * ThermodynamicOperations(systemOut1); testOps.PHflash(systemOut1.getEnthalpy() - corrected_Entalphy, 0);
       * outStream[1].setThermoSystem(systemOut1); System.out.println("temperatur out " +
       * outStream[1].getTemperature()); }
       */
    }

    publishHeatDuty();
    updateLastState();
    setCalculationIdentifier(id);
  }

  /**
   * Returns the bulk utility composition parameter, or zero for ordinary mixtures.
   *
   * @param fluid stream fluid, which may be null during equipment setup
   * @return salinity [g/kg solution]
   */
  private static double utilitySalinity(SystemInterface fluid) {
    return fluid instanceof neqsim.thermo.system.SystemSeawater
        ? ((neqsim.thermo.system.SystemSeawater) fluid).getSalinity()
        : 0.0;
  }

  /**
   * Keeps the capacity-rate probe of a saline utility inside its liquid property envelope.
   *
   * @param fluid fluid whose heat-capacity rate is being estimated
   * @param oppositeTemperature opposite inlet temperature [K]
   * @return temperature for the existing secant heat-capacity approximation [K]
   * @throws IllegalStateException if the utility has no remaining temperature interval inside the model envelope
   */
  private static double capacityEvaluationTemperature(SystemInterface fluid, double oppositeTemperature) {
    if (!(fluid instanceof neqsim.thermo.system.SystemSeawater)) {
      return oppositeTemperature;
    }
    double limit = Math.max(273.15, Math.min(353.0, oppositeTemperature));
    if (Math.abs(limit - fluid.getTemperature()) < 1e-10
        && Math.abs(oppositeTemperature - fluid.getTemperature()) > 1e-10) {
      throw new IllegalStateException("Seawater capacity evaluation has no interval inside 273.15-353 K");
    }
    return limit;
  }

  /**
   * Publishes recoverable exchanger duty to the inherited calculated heat port.
   */
  private void publishHeatDuty() {
    if (getEnergyPort("heatDuty").isConnected()
        && getEnergyPort("heatDuty").getMode() == neqsim.process.equipment.stream.EnergyPortMode.CALCULATED) {
      getEnergyPort("heatDuty").setDuty(Math.abs(duty));
    }
    modeObservationPending = false;
  }

  /** {@inheritDoc} */
  @Override
  public double getDuty() {
    return duty;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    outStream[0].displayResult();
    outStream[1].displayResult();
  }

  /**
   * getUAvalue.
   *
   * @return the UAvalue
   */
  public double getUAvalue() {
    return UAvalue;
  }

  /**
   * setUAvalue.
   *
   * @param UAvalue the UAvalue to set
   */
  public void setUAvalue(double UAvalue) {
    this.UAvalue = UAvalue;
  }

  /**
   * Getter for the field <code>guessOutTemperature</code>.
   *
   * @return a double
   */
  public double getGuessOutTemperature() {
    return guessOutTemperature;
  }

  /**
   * Setter for the field <code>guessOutTemperature</code>.
   *
   * @param guessOutTemperature a double
   */
  public void setGuessOutTemperature(double guessOutTemperature) {
    this.guessOutTemperature = guessOutTemperature;
    this.guessOutTemperatureUnit = "K";
  }

  /**
   * Setter for the field <code>guessOutTemperature</code>.
   *
   * @param guessOutTemperature a double
   * @param unit a String
   */
  public void setGuessOutTemperature(double guessOutTemperature, String unit) {
    this.guessOutTemperature = guessOutTemperature;
    this.guessOutTemperatureUnit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entrop = 0.0;

    for (int i = 0; i < 2; i++) {
      UUID id = UUID.randomUUID();
      inStream[i].run(id);
      inStream[i].getFluid().init(2);
      outStream[i].run(id);
      outStream[i].getFluid().init(2);
      entrop += outStream[i].getThermoSystem().getEntropy(unit) - inStream[i].getThermoSystem().getEntropy(unit);
    }

    int stream1 = 0;
    int stream2 = 1;
    if (inStream[0].getTemperature() < inStream[1].getTemperature()) {
      stream2 = 0;
      stream1 = 1;
    }
    double heatTransferEntropyProd = Math.abs(getDuty())
        * (1.0 / inStream[stream2].getTemperature() - 1.0 / (inStream[stream1].getTemperature()));
    // System.out.println("heat entropy " + heatTransferEntropyProd);

    return entrop + heatTransferEntropyProd;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double mass = 0.0;

    for (int i = 0; i < 2; i++) {
      inStream[i].run();
      inStream[i].getFluid().init(3);
      outStream[i].run();
      outStream[i].getFluid().init(3);
      mass += outStream[i].getThermoSystem().getFlowRate(unit) - inStream[i].getThermoSystem().getFlowRate(unit);
    }
    return mass;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {
    double heatBalanceError = 0.0;
    conditionAnalysisMessage += name + " condition analysis started/";
    HeatExchanger refEx = (HeatExchanger) refExchanger;
    for (int i = 0; i < 2; i++) {
      inStream[i].getFluid().initProperties();
      outStream[i].getFluid().initProperties();
      heatBalanceError += outStream[i].getThermoSystem().getEnthalpy() - inStream[i].getThermoSystem().getEnthalpy();

      if (Math.abs(refEx.getInStream(i).getTemperature("C")
          - getInStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      } else if (Math.abs(refEx.getOutStream(i).getTemperature("C")
          - getOutStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      }
    }
    heatBalanceError = heatBalanceError
        / (outStream[0].getThermoSystem().getEnthalpy() - inStream[0].getThermoSystem().getEnthalpy()) * 100.0;
    if (Math.abs(heatBalanceError) > 10.0) {
      String error = "Heat balance not fulfilled. Error: " + heatBalanceError + " ";
      conditionAnalysisMessage += error;
    } else {
      String error = "Heat balance ok. Enthalpy balance deviation: " + heatBalanceError + " %";
      conditionAnalysisMessage += error;
    }

    conditionAnalysisMessage += name + "/analysis ended/";

    // this.run();
    double duty1 = Math.abs(outStream[0].getThermoSystem().getEnthalpy() - inStream[0].getThermoSystem().getEnthalpy());
    double duty2 = Math.abs(outStream[1].getThermoSystem().getEnthalpy() - inStream[1].getThermoSystem().getEnthalpy());
    thermalEffectiveness = ((HeatExchanger) refExchanger).getThermalEffectiveness() * (duty1 + duty2) / 2.0
        / Math.abs(((HeatExchanger) refExchanger).getDuty());
    hotColdDutyBalance = duty1 / duty2;
  }

  /**
   * runConditionAnalysis.
   */
  public void runConditionAnalysis() {
    runConditionAnalysis(this);
  }

  /**
   * Getter for the field <code>thermalEffectiveness</code>.
   *
   * @return a double
   */
  public double getThermalEffectiveness() {
    return thermalEffectiveness;
  }

  /**
   * Setter for the field <code>thermalEffectiveness</code>.
   *
   * @param thermalEffectiveness a double
   */
  public void setThermalEffectiveness(double thermalEffectiveness) {
    this.thermalEffectiveness = thermalEffectiveness;
  }

  /**
   * Getter for the field <code>flowArrangement</code>.
   *
   * @return String
   */
  public String getFlowArrangement() {
    return flowArrangement;
  }

  /**
   * Setter for the field <code>flowArrangement</code>.
   *
   * @param flowArrangement name of flow arrangement
   */
  public void setFlowArrangement(String flowArrangement) {
    this.flowArrangement = flowArrangement;
  }

  /**
   * Gets the design mode.
   *
   * @return SIZING or RATING
   */
  public DesignMode getDesignMode() {
    return designMode;
  }

  /**
   * Updates the rating calculator with current stream fluid properties. Extracts density, viscosity, Cp, and thermal
   * conductivity from both inlet streams.
   */
  private void updateRatingCalculatorFromStreams() {
    if (ratingCalculator == null) {
      return;
    }

    // Tube-side properties from stream 0 (process side)
    try {
      SystemInterface tubeFluid = inStream[0].getThermoSystem();
      tubeFluid.initProperties();
      double tubeDensity = tubeFluid.getDensity("kg/m3");
      double tubeViscosity = tubeFluid.getViscosity("kg/msec");
      double tubeCp = tubeFluid.getCp("J/kgK");
      double tubeConductivity = tubeFluid.getThermalConductivity("W/mK");
      double tubeMassFlow = tubeFluid.getFlowRate("kg/sec");
      boolean heating = outStream[0].getTemperature() > inStream[0].getTemperature();
      ratingCalculator.setTubeSideFluid(tubeDensity, tubeViscosity, tubeCp, tubeConductivity, tubeMassFlow, heating);
    } catch (Exception ex) {
      if (useRatingPressureDrop) {
        throw new IllegalStateException("Could not extract tube-side rating properties", ex);
      }
      // Use defaults if property extraction fails
    }

    // Shell-side properties from stream 1 (utility side)
    try {
      SystemInterface shellFluid = inStream[1].getThermoSystem();
      shellFluid.initProperties();
      double shellDensity = shellFluid.getDensity("kg/m3");
      double shellViscosity = shellFluid.getViscosity("kg/msec");
      double shellCp = shellFluid.getCp("J/kgK");
      double shellConductivity = shellFluid.getThermalConductivity("W/mK");
      double shellMassFlow = shellFluid.getFlowRate("kg/sec");
      ratingCalculator.setShellSideFluid(shellDensity, shellViscosity, shellCp, shellConductivity, shellMassFlow);
    } catch (Exception ex) {
      if (useRatingPressureDrop) {
        throw new IllegalStateException("Could not extract shell-side rating properties", ex);
      }
      // Use defaults if property extraction fails
    }
  }

  /**
   * Sets the design mode. In RATING mode, the exchanger computes UA from geometry and fluid properties instead of using
   * a user-supplied value.
   *
   * @param mode SIZING or RATING
   */
  public void setDesignMode(DesignMode mode) {
    this.designMode = mode;
  }

  /**
   * Gets the ThermalDesignCalculator used in rating mode.
   *
   * @return rating calculator, or null if not in rating mode or not yet run
   */
  public neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator getRatingCalculator() {
    return ratingCalculator;
  }

  /**
   * Sets the ThermalDesignCalculator for rating mode. The caller should configure the calculator with geometry and
   * fluid properties before calling run().
   *
   * @param calculator configured thermal design calculator
   */
  public void setRatingCalculator(neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator calculator) {
    this.ratingCalculator = calculator;
    this.designMode = DesignMode.RATING;
  }

  /**
   * Sets the heat transfer area for rating mode.
   *
   * @param area heat transfer area (m2)
   */
  public void setRatingArea(double area) {
    this.ratingArea = area;
  }

  /**
   * Gets the heat transfer area used in rating mode.
   *
   * @return area (m2)
   */
  public double getRatingArea() {
    return ratingArea;
  }

  /**
   * Enables coupled single-phase thermal-hydraulic rating. Stream 0 is the tube side and stream 1 is the shell side,
   * independently of which stream is hotter. Correlations use inlet properties; outlet states are PH-flashed at inlet
   * pressure minus the correlated loss. Rating area is based on the nominal outside tube surface. This mode requires a
   * rating calculator, positive area and the ordinary UA specification; fixed-temperature and delta-T specifications
   * are rejected. The default is false, preserving the existing thermal-only rating behavior.
   *
   * @param enabled true to apply correlated pressure drops in single-phase rating
   */
  public void setUseRatingPressureDrop(boolean enabled) {
    useRatingPressureDrop = enabled;
  }

  /**
   * Reports whether single-phase thermal-hydraulic rating is enabled.
   *
   * @return true when correlated rating pressure drops are applied
   */
  public boolean isUseRatingPressureDrop() {
    return useRatingPressureDrop;
  }

  /**
   * Runs a fixed-geometry single-phase rating and commits outlet states only after both flashes succeed. The heat
   * capacity rates are enthalpy secants across the inlet temperature interval, consistent with the legacy rating
   * calculation. This is a lumped rating approximation, not a segmented boiling or condensation model.
   *
   * @param id calculation identifier
   * @throws IllegalStateException if configuration, phase envelope or pressure budget is unsuitable
   */
  private void runThermalHydraulicRating(UUID id) {
    if (designMode != DesignMode.RATING || ratingCalculator == null || !Double.isFinite(ratingArea) || ratingArea <= 0.0
        || useDeltaT || getSpecification().equals("out stream") || getSpecification().equals("outTemperature")) {
      throw new IllegalStateException("Thermal-hydraulic rating requires RATING mode, calculator, positive outside "
          + "area and a UA specification without fixed outlet temperature or delta-T");
    }
    SystemInterface[] inlet = new SystemInterface[2];
    SystemInterface[] outlet = new SystemInterface[2];
    double[] inletEnthalpy = new double[2];
    for (int side = 0; side < 2; side++) {
      if (inStream[side] == null) {
        throw new IllegalStateException("Thermal-hydraulic rating requires both inlet streams");
      }
      inStream[side].run(id);
      inlet[side] = inStream[side].getThermoSystem();
      inlet[side].initProperties();
      requireSinglePhaseRatingState(inlet[side]);
      inletEnthalpy[side] = inlet[side].getEnthalpy();
      outlet[side] = inlet[side].clone();
    }
    updateRatingCalculatorFromStreams();
    ratingCalculator.calculateStrict();
    double calculatedU = ratingCalculator.getOverallU();
    double calculatedUA = calculatedU * ratingArea;
    if (!Double.isFinite(calculatedUA) || calculatedUA <= 0.0) {
      throw new IllegalStateException("Thermal-hydraulic rating produced invalid UA");
    }
    double[] drops = {ratingCalculator.getTubeSidePressureDropBar(), ratingCalculator.getShellSidePressureDropBar()};
    for (int side = 0; side < 2; side++) {
      double pressure = inlet[side].getPressure() - drops[side];
      if (!Double.isFinite(drops[side]) || drops[side] < 0.0 || !Double.isFinite(pressure) || pressure <= 0.0) {
        throw new IllegalStateException("Correlated pressure drop exhausts the inlet pressure on side " + side);
      }
      outlet[side].setPressure(pressure);
    }
    double temperatureDifference = inlet[0].getTemperature() - inlet[1].getTemperature();
    double calculatedNtu = 0.0;
    double effectiveness = 0.0;
    double heatFromTube = 0.0;
    if (Math.abs(temperatureDifference) > 1e-10) {
      double[] capacities = new double[2];
      for (int side = 0; side < 2; side++) {
        SystemInterface limit = inlet[side].clone();
        double evaluationTemperature = capacityEvaluationTemperature(limit, inlet[1 - side].getTemperature());
        limit.setTemperature(evaluationTemperature);
        new ThermodynamicOperations(limit).TPflash();
        limit.initProperties();
        requireSinglePhaseRatingState(limit);
        if (limit.getPhase(0).getType() != inlet[side].getPhase(0).getType()) {
          throw new IllegalStateException("Rating temperature interval crosses a phase transition on side " + side);
        }
        capacities[side] = Math
            .abs((limit.getEnthalpy() - inletEnthalpy[side]) / (evaluationTemperature - inlet[side].getTemperature()));
        if (!Double.isFinite(capacities[side]) || capacities[side] <= 0.0) {
          throw new IllegalStateException("Thermal-hydraulic rating requires positive finite heat capacity rates");
        }
      }
      double minimumCapacity = Math.min(capacities[0], capacities[1]);
      double capacityRatio = minimumCapacity / Math.max(capacities[0], capacities[1]);
      calculatedNtu = calculatedUA / minimumCapacity;
      // The counterflow limit also removes the 0/0 singularity of the legacy shell-and-tube alias at Cr=1.
      effectiveness = Math.abs(capacityRatio - 1.0) < 1e-10 && !flowArrangement.equals("concentric tube paralellflow")
          ? calculatedNtu / (1.0 + calculatedNtu)
          : calcThermalEffectivenes(calculatedNtu, capacityRatio);
      heatFromTube = effectiveness * minimumCapacity * temperatureDifference;
      if (!Double.isFinite(heatFromTube) || !Double.isFinite(effectiveness) || effectiveness < 0.0
          || effectiveness > 1.0) {
        throw new IllegalStateException("Thermal-hydraulic rating produced invalid effectiveness or heat duty");
      }
    }
    for (int side = 0; side < 2; side++) {
      double targetEnthalpy = inletEnthalpy[side] + (side == 0 ? -heatFromTube : heatFromTube);
      new ThermodynamicOperations(outlet[side]).PHflash(targetEnthalpy);
      outlet[side].initProperties();
      requireSinglePhaseRatingState(outlet[side]);
      if (outlet[side].getPhase(0).getType() != inlet[side].getPhase(0).getType()) {
        throw new IllegalStateException("Thermal-hydraulic rating outlet changed phase on side " + side);
      }
      if (!Double.isFinite(outlet[side].getEnthalpy())
          || Math.abs(outlet[side].getEnthalpy() - targetEnthalpy) > 1e-5 * Math.max(1.0, Math.abs(targetEnthalpy))) {
        throw new IllegalStateException("Thermal-hydraulic rating outlet PH flash failed energy closure");
      }
    }
    for (int side = 0; side < 2; side++) {
      outStream[side].setThermoSystem(outlet[side]);
      outStream[side].setCalculationIdentifier(id);
    }
    ratingU = calculatedU;
    UAvalue = calculatedUA;
    NTU = calculatedNtu;
    thermalEffectiveness = effectiveness;
    duty = Math.abs(heatFromTube);
    hotColdDutyBalance = 1.0;
    firstTime = false;
    publishHeatDuty();
    updateLastState();
    setCalculationIdentifier(id);
  }

  /**
   * Checks the applicability of the inlet-property single-phase correlations.
   *
   * @param fluid fluid state to check
   * @throws IllegalStateException for multiphase, zero-flow or invalid transport states
   */
  private static void requireSinglePhaseRatingState(SystemInterface fluid) {
    double[] properties = {fluid.getFlowRate("kg/sec"), fluid.getDensity("kg/m3"), fluid.getViscosity("kg/msec"),
        fluid.getCp("J/kgK"), fluid.getThermalConductivity("W/mK"), fluid.getTemperature(), fluid.getPressure()};
    if (fluid.getNumberOfPhases() != 1) {
      throw new IllegalStateException("Thermal-hydraulic rating currently supports single-phase streams only");
    }
    for (double value : properties) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalStateException("Thermal-hydraulic rating requires positive finite flow and fluid properties");
      }
    }
  }

  /**
   * Gets the overall U value computed from rating mode correlations.
   *
   * @return overall U (W/(m2*K)), or 0 if not in rating mode
   */
  public double getRatingU() {
    return ratingU;
  }

  /**
   * Sets the number of shell passes for LMTD correction factor calculation.
   *
   * @param shellPasses number of shell passes (1, 2, 3, ...)
   */
  public void setShellPasses(int shellPasses) {
    this.shellPasses = shellPasses;
  }

  /**
   * Gets the number of shell passes.
   *
   * @return number of shell passes
   */
  public int getShellPasses() {
    return shellPasses;
  }

  /**
   * calcThermalEffectivenes.
   *
   * @param NTU a double
   * @param Cr a double
   * @return a double
   */
  public double calcThermalEffectivenes(double NTU, double Cr) {
    if (Cr == 0.0) {
      return 1.0 - Math.exp(-NTU);
    }
    if (flowArrangement.equals("concentric tube counterflow")) {
      if (Cr == 1.0) {
        return NTU / (1.0 + NTU);
      } else {
        return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
      }
    } else if (flowArrangement.equals("concentric tube paralellflow")) {
      return (1.0 - Math.exp(-NTU * (1 + Cr))) / ((1 + Cr));
    } else if (flowArrangement.equals("shell and tube")) {
      return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
    } else {
      return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
    }
  }

  /**
   * Convert a temperature from Kelvin to the requested unit.
   *
   * @param tK temperature in Kelvin
   * @param unit target unit, one of "K", "C", "F" or "R" (case-insensitive)
   * @return the temperature expressed in {@code unit}
   * @throws IllegalArgumentException if {@code unit} is not a supported temperature unit
   */
  private static double convertFromKelvin(double tK, String unit) {
    String u = unit.trim();
    if (u.equalsIgnoreCase("K")) {
      return tK;
    } else if (u.equalsIgnoreCase("C")) {
      return tK - 273.15;
    } else if (u.equalsIgnoreCase("F")) {
      return (tK - 273.15) * 9.0 / 5.0 + 32.0;
    } else if (u.equalsIgnoreCase("R")) {
      return tK * 1.8;
    }
    throw new IllegalArgumentException("Unsupported temperature unit: " + unit + " (use K, C, F or R).");
  }

  /**
   * Run an effectiveness-NTU fouling / UA-degradation screening sweep for the two connected inlet streams.
   *
   * <p>
   * For each supplied UA fraction the operating UA is taken as {@code cleanUA * fraction} (a fraction of 1.0 is the
   * clean exchanger; 0.75 represents a 25&#37; UA loss). The classic effectiveness-NTU method with constant
   * heat-capacity rates is applied: the hot and cold heat-capacity rates are estimated from the inlet streams, NTU = UA
   * / Cmin, the effectiveness follows the exchanger's {@link #getFlowArrangement() flow arrangement} via
   * {@link #calcThermalEffectivenes(double, double)}, and the cold- and hot-side outlet temperatures follow from an
   * energy balance. This is a side-effect-free screening call: the exchanger's own inlet/outlet streams and UA value
   * are not modified. It is intended for quick cooling-medium / seawater fouling studies where the cold-outlet (e.g.
   * cooling-medium supply) temperature as a function of UA loss is required.
   * </p>
   *
   * @param cleanUA the clean (unfouled) overall UA value in W/K
   * @param uaFractions the UA fractions to evaluate (each &ge; 0; 1.0 = clean); must be non-empty
   * @param temperatureUnit the unit for the reported temperatures ("K", "C", "F" or "R"); defaults to "K" when null or
   * blank
   * @return a {@link FoulingScreeningResult} with, per UA fraction, the cold- and hot-side outlet temperatures, the
   * thermal effectiveness, NTU and duty (W)
   * @throws IllegalStateException if both inlet streams have not been connected
   * @throws IllegalArgumentException if {@code uaFractions} is null or empty
   */
  public FoulingScreeningResult foulingScreening(double cleanUA, double[] uaFractions, String temperatureUnit) {
    if (inStream[0] == null || inStream[1] == null) {
      throw new IllegalStateException("HeatExchanger.foulingScreening requires both inlet streams "
          + "to be connected (use setFeedStream/setInStream).");
    }
    if (uaFractions == null || uaFractions.length == 0) {
      throw new IllegalArgumentException("HeatExchanger.foulingScreening requires at least one UA fraction.");
    }
    String unit = (temperatureUnit == null || temperatureUnit.trim().isEmpty()) ? "K" : temperatureUnit;

    inStream[0].run();
    inStream[1].run();
    SystemInterface sysA = inStream[0].getThermoSystem();
    SystemInterface sysB = inStream[1].getThermoSystem();

    SystemInterface hotSys;
    SystemInterface coldSys;
    if (sysA.getTemperature() >= sysB.getTemperature()) {
      hotSys = sysA;
      coldSys = sysB;
    } else {
      hotSys = sysB;
      coldSys = sysA;
    }
    double tHotInK = hotSys.getTemperature();
    double tColdInK = coldSys.getTemperature();
    double hHotIn = hotSys.getEnthalpy();
    double hColdIn = coldSys.getEnthalpy();
    double dTspan = Math.abs(tHotInK - tColdInK);

    // Full driving-temperature-difference enthalpy changes (signed) via TPflash on clones, so
    // latent heat (e.g. condensation of a cooled hydrocarbon) is captured. The heat-capacity rate
    // is the secant |dH|/dTspan, matching the definition used in HeatExchanger.run().
    SystemInterface hotAtColdIn = hotSys.clone();
    double hotProbeTemperature = capacityEvaluationTemperature(hotAtColdIn, tColdInK);
    hotAtColdIn.setTemperature(hotProbeTemperature);
    new ThermodynamicOperations(hotAtColdIn).TPflash();
    double dHhotFull = hotAtColdIn.getEnthalpy() - hHotIn;

    SystemInterface coldAtHotIn = coldSys.clone();
    double coldProbeTemperature = capacityEvaluationTemperature(coldAtHotIn, tHotInK);
    coldAtHotIn.setTemperature(coldProbeTemperature);
    new ThermodynamicOperations(coldAtHotIn).TPflash();
    double dHcoldFull = coldAtHotIn.getEnthalpy() - hColdIn;

    double cHot = (dTspan > 0.0) ? Math.abs(dHhotFull / (hotProbeTemperature - tHotInK)) : 0.0;
    double cCold = (dTspan > 0.0) ? Math.abs(dHcoldFull / (coldProbeTemperature - tColdInK)) : 0.0;
    double cMin = Math.min(cHot, cCold);
    double cMax = Math.max(cHot, cCold);
    double cr = (cMax > 0.0) ? cMin / cMax : 0.0;

    // The stream with the smaller enthalpy change (Cmin) is the "set" stream in the
    // effectiveness-NTU balance; the other is solved by PHflash, mirroring HeatExchanger.run().
    boolean setIsHot = cHot <= cCold;
    double dHset = setIsHot ? dHhotFull : dHcoldFull;
    if (hotSys instanceof neqsim.thermo.system.SystemSeawater
        || coldSys instanceof neqsim.thermo.system.SystemSeawater) {
      dHset = (setIsHot ? -1.0 : 1.0) * cMin * dTspan;
    }
    SystemInterface sysSet = setIsHot ? hotSys : coldSys;
    SystemInterface sysCalc = setIsHot ? coldSys : hotSys;
    double hSetIn = setIsHot ? hHotIn : hColdIn;
    double hCalcIn = setIsHot ? hColdIn : hHotIn;

    FoulingScreeningResult result = new FoulingScreeningResult(uaFractions.length, unit);
    for (int i = 0; i < uaFractions.length; i++) {
      double frac = uaFractions[i];
      double ua = cleanUA * frac;
      double ntu = (cMin > 0.0) ? ua / cMin : 0.0;
      double eff = calcThermalEffectivenes(ntu, cr);
      double dE = eff * dHset;

      SystemInterface calcOut = sysCalc.clone();
      new ThermodynamicOperations(calcOut).PHflash(hCalcIn - dE, 0);
      SystemInterface setOut = sysSet.clone();
      new ThermodynamicOperations(setOut).PHflash(hSetIn + dE, 0);

      double tHotOutK = setIsHot ? setOut.getTemperature() : calcOut.getTemperature();
      double tColdOutK = setIsHot ? calcOut.getTemperature() : setOut.getTemperature();
      result.set(i, frac, convertFromKelvin(tColdOutK, unit), convertFromKelvin(tHotOutK, unit), eff, ntu,
          Math.abs(dE));
    }
    return result;
  }

  /**
   * Convenience overload of {@link #foulingScreening(double, double[], String)} that uses the exchanger's current
   * {@link #getUAvalue() UA value} as the clean UA and reports temperatures in degrees Celsius.
   *
   * @param uaFractions the UA fractions to evaluate (each &ge; 0; 1.0 = clean); must be non-empty
   * @return a {@link FoulingScreeningResult} with temperatures in &#176;C
   */
  public FoulingScreeningResult foulingScreening(double[] uaFractions) {
    return foulingScreening(getUAvalue(), uaFractions, "C");
  }

  /**
   * Convenience overload of {@link #foulingScreening(double, double[], String)} that builds an evenly spaced sweep of
   * UA fractions between {@code minFraction} and {@code maxFraction}.
   *
   * @param cleanUA the clean (unfouled) overall UA value in W/K
   * @param steps the number of UA fractions to generate (&ge; 2)
   * @param minFraction the smallest UA fraction (e.g. 0.5 for 50&#37; UA loss)
   * @param maxFraction the largest UA fraction (e.g. 1.0 for the clean exchanger)
   * @param temperatureUnit the unit for the reported temperatures ("K", "C", "F" or "R")
   * @return a {@link FoulingScreeningResult} for the generated sweep
   * @throws IllegalArgumentException if {@code steps} is less than 2
   */
  public FoulingScreeningResult foulingScreening(double cleanUA, int steps, double minFraction, double maxFraction,
      String temperatureUnit) {
    if (steps < 2) {
      throw new IllegalArgumentException("HeatExchanger.foulingScreening requires at least 2 steps to build a sweep.");
    }
    double[] fractions = new double[steps];
    for (int i = 0; i < steps; i++) {
      fractions[i] = minFraction + (maxFraction - minFraction) * i / (steps - 1);
    }
    return foulingScreening(cleanUA, fractions, temperatureUnit);
  }

  /**
   * Result of an effectiveness-NTU fouling / UA-degradation screening sweep.
   *
   * <p>
   * Holds, per evaluated UA fraction, the cold- and hot-side outlet temperatures (in the requested unit), the thermal
   * effectiveness, the NTU and the duty in watts. Produced by
   * {@link HeatExchanger#foulingScreening(double, double[], String)}.
   * </p>
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class FoulingScreeningResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private final double[] uaFraction;
    private final double[] coldOutletTemperature;
    private final double[] hotOutletTemperature;
    private final double[] effectiveness;
    private final double[] ntu;
    private final double[] duty;
    private final String temperatureUnit;

    /**
     * Constructor.
     *
     * @param size the number of UA fractions in the sweep
     * @param temperatureUnit the unit of the reported temperatures
     */
    FoulingScreeningResult(int size, String temperatureUnit) {
      this.uaFraction = new double[size];
      this.coldOutletTemperature = new double[size];
      this.hotOutletTemperature = new double[size];
      this.effectiveness = new double[size];
      this.ntu = new double[size];
      this.duty = new double[size];
      this.temperatureUnit = temperatureUnit;
    }

    /**
     * Store one evaluated point of the sweep.
     *
     * @param i the point index
     * @param uaFraction the UA fraction (1.0 = clean)
     * @param coldOutletTemperature the cold-side outlet temperature in {@link #getTemperatureUnit()}
     * @param hotOutletTemperature the hot-side outlet temperature in {@link #getTemperatureUnit()}
     * @param effectiveness the thermal effectiveness (0-1)
     * @param ntu the number of transfer units
     * @param duty the exchanger duty in W
     */
    void set(int i, double uaFraction, double coldOutletTemperature, double hotOutletTemperature, double effectiveness,
        double ntu, double duty) {
      this.uaFraction[i] = uaFraction;
      this.coldOutletTemperature[i] = coldOutletTemperature;
      this.hotOutletTemperature[i] = hotOutletTemperature;
      this.effectiveness[i] = effectiveness;
      this.ntu[i] = ntu;
      this.duty[i] = duty;
    }

    /**
     * Getter for the evaluated UA fractions.
     *
     * @return the UA fractions (1.0 = clean exchanger)
     */
    public double[] getUaFraction() {
      return uaFraction.clone();
    }

    /**
     * Getter for the cold-side outlet temperatures.
     *
     * @return the cold-side outlet temperatures in {@link #getTemperatureUnit()}
     */
    public double[] getColdOutletTemperature() {
      return coldOutletTemperature.clone();
    }

    /**
     * Getter for the hot-side outlet temperatures.
     *
     * @return the hot-side outlet temperatures in {@link #getTemperatureUnit()}
     */
    public double[] getHotOutletTemperature() {
      return hotOutletTemperature.clone();
    }

    /**
     * Getter for the thermal effectiveness values.
     *
     * @return the thermal effectiveness (0-1) for each UA fraction
     */
    public double[] getEffectiveness() {
      return effectiveness.clone();
    }

    /**
     * Getter for the NTU values.
     *
     * @return the number of transfer units for each UA fraction
     */
    public double[] getNtu() {
      return ntu.clone();
    }

    /**
     * Getter for the duty values.
     *
     * @return the exchanger duty in W for each UA fraction
     */
    public double[] getDuty() {
      return duty.clone();
    }

    /**
     * Getter for the temperature unit of the reported temperatures.
     *
     * @return the temperature unit ("K", "C", "F" or "R")
     */
    public String getTemperatureUnit() {
      return temperatureUnit;
    }

    /**
     * Serialize the screening result to JSON.
     *
     * @return a JSON representation of the sweep
     */
    public String toJson() {
      return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(this);
    }
  }

  /**
   * Getter for the field <code>hotColdDutyBalance</code>.
   *
   * @return a double
   */
  public double getHotColdDutyBalance() {
    return hotColdDutyBalance;
  }

  /**
   * Setter for the field <code>hotColdDutyBalance</code>.
   *
   * @param hotColdDutyBalance a double
   */
  public void setHotColdDutyBalance(double hotColdDutyBalance) {
    this.hotColdDutyBalance = hotColdDutyBalance;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(new HXResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    HXResponse res = new HXResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  // ============================================================================
  // AutoSizeable Implementation (overrides Heater methods for two-stream HX)
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (inStream[0] == null || inStream[1] == null) {
      throw new IllegalStateException("Both inlet streams must be connected before auto-sizing heat exchanger");
    }

    // Calculate duty from heat transfer
    double calculatedDuty = Math.abs(this.duty);
    if (calculatedDuty <= 0) {
      calculatedDuty = 1000000.0; // Default 1 MW if duty not calculable
    }

    // Apply safety factor to duty
    double designDuty = calculatedDuty * safetyFactor;

    // Update instance field so constraint init picks it up
    this.designDuty = designDuty;

    // Initialize and calculate mechanical design
    HeatExchangerMechanicalDesign mechDesign = getMechanicalDesign();
    if (mechDesign != null) {
      mechDesign.maxDesignDuty = designDuty;
      mechDesign.calcDesign();
    }

    // Reinitialize capacity constraints with the new design values
    initializeHxCapacityConstraints();

    // Mark as auto-sized (don't call super.autoSize as it will fail on inStream check)
    setAutoSized(true);
  }

  /**
   * Sets the autoSized flag. Protected to allow subclass access.
   *
   * @param autoSized true if equipment has been auto-sized
   */
  protected void setAutoSized(boolean autoSized) {
    // Access parent's autoSized field via reflection or use a setter
    // For now, we track our own state since HeatExchanger has different streams
    this.hxAutoSized = autoSized;
  }

  /** Internal auto-sized flag for heat exchanger. */
  private boolean hxAutoSized = false;

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return hxAutoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Heat Exchanger Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(isAutoSized()).append("\n");
    sb.append("Flow Arrangement: ").append(flowArrangement).append("\n");

    if (inStream[0] != null && inStream[1] != null) {
      sb.append("\n--- Hot Side (Stream 0) ---\n");
      sb.append("Inlet Temperature: ").append(String.format(Locale.US, "%.2f C", inStream[0].getTemperature("C")))
          .append("\n");
      sb.append("Outlet Temperature: ").append(String.format(Locale.US, "%.2f C", outStream[0].getTemperature("C")))
          .append("\n");
      sb.append("Flow Rate: ").append(String.format(Locale.US, "%.3f kg/s", inStream[0].getFlowRate("kg/sec")))
          .append("\n");

      sb.append("\n--- Cold Side (Stream 1) ---\n");
      sb.append("Inlet Temperature: ").append(String.format(Locale.US, "%.2f C", inStream[1].getTemperature("C")))
          .append("\n");
      sb.append("Outlet Temperature: ").append(String.format(Locale.US, "%.2f C", outStream[1].getTemperature("C")))
          .append("\n");
      sb.append("Flow Rate: ").append(String.format(Locale.US, "%.3f kg/s", inStream[1].getFlowRate("kg/sec")))
          .append("\n");

      sb.append("\n--- Heat Transfer ---\n");
      sb.append("Duty: ").append(String.format(Locale.US, "%.2f kW", duty / 1000.0)).append("\n");
      sb.append("UA Value: ").append(String.format(Locale.US, "%.2f W/K", UAvalue)).append("\n");
      sb.append("Thermal Effectiveness: ").append(String.format(Locale.US, "%.3f", thermalEffectiveness)).append("\n");

      // Calculate LMTD
      double hotIn = inStream[0].getTemperature("K");
      double hotOut = outStream[0].getTemperature("K");
      double coldIn = inStream[1].getTemperature("K");
      double coldOut = outStream[1].getTemperature("K");
      double deltaT1 = hotIn - coldOut;
      double deltaT2 = hotOut - coldIn;
      double lmtd = (Math.abs(deltaT1 - deltaT2) < 1e-6) ? (deltaT1 + deltaT2) / 2.0
          : (deltaT1 - deltaT2) / Math.log(deltaT1 / deltaT2);
      sb.append("LMTD: ").append(String.format(Locale.US, "%.2f K", lmtd)).append("\n");

      HeatExchangerMechanicalDesign mechDesign = getMechanicalDesign();
      if (mechDesign != null) {
        sb.append("\n--- Mechanical Design ---\n");
        sb.append("Max Design Duty: ").append(String.format(Locale.US, "%.2f kW", mechDesign.maxDesignDuty / 1000.0))
            .append("\n");
        sb.append("Duty Utilization: ")
            .append(String.format(Locale.US, "%.1f%%", Math.abs(duty) / mechDesign.maxDesignDuty * 100)).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Setter for the field <code>useDeltaT</code>.
   *
   * @param useDeltaT a boolean
   */
  public void setUseDeltaT(boolean useDeltaT) {
    this.useDeltaT = useDeltaT;
  }

  /**
   * Getter for the field <code>deltaT</code>.
   *
   * @return a double
   */
  public double getDeltaT() {
    return deltaT;
  }

  /**
   * Setter for the field <code>deltaT</code>.
   *
   * @param deltaT a double
   */
  public void setDeltaT(double deltaT) {
    useDeltaT = true;
    this.deltaT = deltaT;
  }

  /**
   * Observes temperatures, flows and duty with features for the initialized execution mode.
   *
   * <p>
   * Thermal mode reports wall temperature in K. Steady mode retains the configured UA in W/K and finite thermal
   * effectiveness when available. Initialize the newly selected mode before capturing feature names or values.
   * </p>
   *
   * @return mode-specific operating observations, preserving feature order across subsequent thermal steps
   * @throws IllegalStateException if a mode change still needs a completed calculation
   */
  @Override
  public StateVector getStateVector() {
    if (modeObservationPending || (dynamicModelEnabled && thermalState == null)) {
      throw new IllegalStateException("Initialize the selected exchanger mode before observing its state vector");
    }
    StateVector state = new StateVector();

    // Hot side temperatures
    if (inStream[0] != null) {
      state.add("hot_inlet_temp", inStream[0].getTemperature("K"), 200.0, 600.0, "K");
      state.add("hot_flow", inStream[0].getFlowRate("kg/sec"), 0.0, 500.0, "kg/s");
    }
    if (outStream[0] != null) {
      state.add("hot_outlet_temp", outStream[0].getTemperature("K"), 200.0, 600.0, "K");
    }

    // Cold side temperatures
    if (inStream[1] != null) {
      state.add("cold_inlet_temp", inStream[1].getTemperature("K"), 200.0, 600.0, "K");
      state.add("cold_flow", inStream[1].getFlowRate("kg/sec"), 0.0, 500.0, "kg/s");
    }
    if (outStream[1] != null) {
      state.add("cold_outlet_temp", outStream[1].getTemperature("K"), 200.0, 600.0, "K");
    }

    // Performance
    state.add("duty", getDuty() / 1000.0, 0.0, 100000.0, "kW");
    if (dynamicModelEnabled) {
      state.add("wall_temperature", getWallTemperature(), 200.0, 600.0, "K");
    } else {
      state.add("ua_value", getUAvalue(), 0.0, 100000.0, "W/K");
      if (Double.isFinite(getThermalEffectiveness())) {
        state.add("effectiveness", getThermalEffectiveness(), 0.0, 1.0, "fraction");
      }
    }

    return state;
  }

  /**
   * Creates a new Builder for constructing a HeatExchanger with a fluent API.
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * HeatExchanger hx = HeatExchanger.builder("E-100").hotStream(hotFeed).coldStream(coldFeed).UAvalue(5000.0)
   *     .flowArrangement("counterflow").build();
   * </pre>
   *
   * @param name the name of the heat exchanger
   * @return a new Builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /**
   * Sets the design duty for capacity calculations.
   *
   * @param duty design duty in Watts
   */
  public void setDesignDuty(double duty) {
    this.designDuty = duty;
    initializeHxCapacityConstraints();
  }

  /**
   * Gets the design duty.
   *
   * @return design duty in Watts
   */
  public double getDesignDuty() {
    return designDuty;
  }

  /**
   * Sets the design UA value for capacity calculations.
   *
   * @param uaValue design UA value in W/K
   */
  public void setDesignUAValue(double uaValue) {
    this.designUAValue = uaValue;
    initializeHxCapacityConstraints();
  }

  /**
   * Gets the design UA value.
   *
   * @return design UA value in W/K
   */
  public double getDesignUAValue() {
    return designUAValue;
  }

  /**
   * Sets the minimum approach temperature.
   *
   * @param temperature minimum approach temperature in K
   */
  public void setMinApproachTemperature(double temperature) {
    this.minApproachTemperature = temperature;
    initializeHxCapacityConstraints();
  }

  /**
   * Gets the minimum approach temperature.
   *
   * @return minimum approach temperature in K
   */
  public double getMinApproachTemperature() {
    return minApproachTemperature;
  }

  /**
   * Sets the maximum shell-side pressure drop.
   *
   * @param pressureDrop maximum pressure drop in bar
   */
  public void setMaxShellPressureDrop(double pressureDrop) {
    this.maxShellPressureDrop = pressureDrop;
    initializeHxCapacityConstraints();
  }

  /**
   * Sets the maximum tube-side pressure drop.
   *
   * @param pressureDrop maximum pressure drop in bar
   */
  public void setMaxTubePressureDrop(double pressureDrop) {
    this.maxTubePressureDrop = pressureDrop;
    initializeHxCapacityConstraints();
  }

  /**
   * Calculates the current approach temperature.
   *
   * @return current approach temperature in K
   */
  public double getApproachTemperature() {
    if (inStream[0] == null || inStream[1] == null || outStream[0] == null || outStream[1] == null) {
      return Double.MAX_VALUE;
    }
    double hotOut = outStream[0].getTemperature("K");
    double coldIn = inStream[1].getTemperature("K");
    double hotIn = inStream[0].getTemperature("K");
    double coldOut = outStream[1].getTemperature("K");

    // For counterflow: min of (hot_out - cold_in) and (hot_in - cold_out)
    // For parallel flow: (hot_out - cold_out)
    if (flowArrangement.contains("counter")) {
      return Math.min(hotOut - coldIn, hotIn - coldOut);
    } else {
      return hotOut - coldOut;
    }
  }

  /**
   * Initialize heat exchanger specific capacity constraints.
   */
  private void initializeHxCapacityConstraints() {
    hxCapacityConstraints.clear();

    // Duty utilization constraint
    if (designDuty > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint dutyConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "dutyUtilization", "W", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN);
      dutyConstraint.setDesignValue(designDuty);
      dutyConstraint.setDescription("Heat duty utilization");
      dutyConstraint.setValueSupplier(() -> Math.abs(this.duty));
      hxCapacityConstraints.put("dutyUtilization", dutyConstraint);
    }

    // UA utilization constraint
    if (designUAValue > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint uaConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "uaUtilization", "W/K", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      uaConstraint.setDesignValue(designUAValue);
      uaConstraint.setDescription("UA value utilization");
      uaConstraint.setValueSupplier(() -> this.UAvalue);
      hxCapacityConstraints.put("uaUtilization", uaConstraint);
    }

    // Approach temperature constraint (inverse - we want to be above minimum)
    if (minApproachTemperature > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint approachConstraint = new neqsim.process.equipment.capacity.CapacityConstraint(
          "approachTemperature", "K", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      approachConstraint.setDesignValue(minApproachTemperature);
      approachConstraint.setDescription("Minimum temperature approach");
      // Utilization is inverted: approaches 100% as we get close to minimum
      approachConstraint.setValueSupplier(() -> {
        double approach = getApproachTemperature();
        return approach > 0 ? minApproachTemperature / approach : 1.0;
      });
      hxCapacityConstraints.put("approachTemperature", approachConstraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityAnalysisEnabled() {
    return hxCapacityAnalysisEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.hxCapacityAnalysisEnabled = enabled;
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    return java.util.Collections.unmodifiableMap(hxCapacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints.values()) {
      if (constraint.isEnabled()) {
        double util = constraint.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = constraint;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints.values()) {
      if (constraint.isEnabled() && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints.values()) {
      if (constraint.isEnabled()
          && constraint.getType() == neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD
          && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints.values()) {
      if (constraint.isEnabled()) {
        maxUtil = Math.max(maxUtil, constraint.getUtilization());
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    hxCapacityConstraints.put(constraint.getName(), constraint);
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return hxCapacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    hxCapacityConstraints.clear();
  }

  // ============ Dynamic Simulation Getters/Setters ============

  /**
   * Gets the metal wall mass used in the dynamic heat exchanger model.
   *
   * @return wall mass in kg
   */
  public double getWallMass() {
    return wallMass;
  }

  /**
   * Sets the metal wall mass for the dynamic heat exchanger model.
   *
   * @param wallMass wall mass in kg (must be non-negative)
   */
  public void setWallMass(double wallMass) {
    this.wallMass = Math.max(0.0, wallMass);
  }

  /**
   * Gets the metal wall specific heat capacity.
   *
   * @return wall Cp in J/(kg*K)
   */
  public double getWallCp() {
    return wallCp;
  }

  /**
   * Sets the metal wall specific heat capacity.
   *
   * @param wallCp specific heat in J/(kg*K)
   */
  public void setWallCp(double wallCp) {
    this.wallCp = wallCp;
  }

  /**
   * Gets the current metal wall temperature (dynamic state variable).
   *
   * @return wall temperature in Kelvin (NaN if not yet initialized)
   */
  public double getWallTemperature() {
    return wallTemperature;
  }

  /**
   * Sets the metal wall temperature.
   *
   * @param wallTemperature wall temperature in Kelvin
   */
  public void setWallTemperature(double wallTemperature) {
    this.wallTemperature = wallTemperature;
  }

  /**
   * Gets the shell-side holdup volume.
   *
   * @return shell holdup volume in m3
   */
  public double getShellHoldupVolume() {
    return shellHoldupVolume;
  }

  /**
   * Sets the shell-side holdup volume.
   *
   * @param shellHoldupVolume volume in m3
   */
  public void setShellHoldupVolume(double shellHoldupVolume) {
    this.shellHoldupVolume = Math.max(0.0, shellHoldupVolume);
  }

  /**
   * Gets the tube-side holdup volume.
   *
   * @return tube holdup volume in m3
   */
  public double getTubeHoldupVolume() {
    return tubeHoldupVolume;
  }

  /**
   * Sets the tube-side holdup volume.
   *
   * @param tubeHoldupVolume volume in m3
   */
  public void setTubeHoldupVolume(double tubeHoldupVolume) {
    this.tubeHoldupVolume = Math.max(0.0, tubeHoldupVolume);
  }

  /**
   * Gets the shell-side heat transfer coefficient.
   *
   * @return heat transfer coefficient in W/(m2*K)
   */
  public double getShellSideHtc() {
    return shellSideHtc;
  }

  /**
   * Sets the shell-side heat transfer coefficient.
   *
   * @param shellSideHtc heat transfer coefficient in W/(m2*K)
   */
  public void setShellSideHtc(double shellSideHtc) {
    this.shellSideHtc = shellSideHtc;
  }

  /**
   * Gets the tube-side heat transfer coefficient.
   *
   * @return heat transfer coefficient in W/(m2*K)
   */
  public double getTubeSideHtc() {
    return tubeSideHtc;
  }

  /**
   * Sets the tube-side heat transfer coefficient.
   *
   * @param tubeSideHtc heat transfer coefficient in W/(m2*K)
   */
  public void setTubeSideHtc(double tubeSideHtc) {
    this.tubeSideHtc = tubeSideHtc;
  }

  /**
   * Gets the heat transfer area used in the dynamic model.
   *
   * @return heat transfer area in m2
   */
  public double getHeatTransferArea() {
    return heatTransferArea;
  }

  /**
   * Sets the heat transfer area for the dynamic model.
   *
   * @param heatTransferArea area in m2
   */
  public void setHeatTransferArea(double heatTransferArea) {
    this.heatTransferArea = Math.max(0.0, heatTransferArea);
  }

  /**
   * Returns whether the dynamic heat exchanger model is enabled.
   *
   * @return true if dynamic model is active
   */
  public boolean isDynamicModelEnabled() {
    return dynamicModelEnabled;
  }

  /**
   * Selects the thermal execution mode on this existing equipment. Enabling uses the same mixed-fluid and wall enthalpy
   * equations in steady initialization and transient integration; disabling restores the default steady specification.
   * Changing mode clears thermal inventory and steady caches while preserving streams, ports and tags. On the first
   * transient call, inventory starts from the published outlet temperatures. Calling run() explicitly establishes the
   * lumped steady equilibrium. External heat boundaries are rejected before any mode change.
   *
   * @param enabled true to enable
   */
  public void setDynamicModelEnabled(boolean enabled) {
    if (dynamicModelEnabled == enabled) {
      return;
    }
    if (enabled) {
      requireInternalThermalPort();
    }
    dynamicModelEnabled = enabled;
    modeObservationPending = true;
    setCalculateSteadyState(!enabled);
    thermalState = null;
    stepStart = null;
    lastPhysicalId = null;
    lastBalance = null;
    firstTime = true;
    lastFlowRate = Double.NaN;
    NTU = Double.NaN;
    thermalEffectiveness = Double.NaN;
  }

  /**
   * Builder class for constructing HeatExchanger instances with a fluent API.
   *
   * <p>
   * Provides a readable and maintainable way to construct heat exchangers with hot and cold stream configurations,
   * thermal specifications, and flow arrangements.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Builder {
    private final String name;
    private StreamInterface hotStream = null;
    private StreamInterface coldStream = null;
    private double uaValue = 500.0;
    private double thermalEffectiveness = -1.0;
    private double deltaT = -1.0;
    private double outTemperature = -1.0;
    private String outTemperatureUnit = "K";
    private int outStreamSpecificationNumber = -1;
    private String flowArrangement = "concentric tube counterflow";
    private double guessOutTemperature = 273.15 + 130.0;
    private String guessOutTemperatureUnit = "K";

    /**
     * Creates a new Builder with the specified heat exchanger name.
     *
     * @param name the name of the heat exchanger
     */
    public Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the hot side inlet stream (stream index 0).
     *
     * @param stream the hot side inlet stream
     * @return this builder for chaining
     */
    public Builder hotStream(StreamInterface stream) {
      this.hotStream = stream;
      return this;
    }

    /**
     * Sets the cold side inlet stream (stream index 1).
     *
     * @param stream the cold side inlet stream
     * @return this builder for chaining
     */
    public Builder coldStream(StreamInterface stream) {
      this.coldStream = stream;
      return this;
    }

    /**
     * Sets the overall heat transfer coefficient times area (UA value) in W/K.
     *
     * @param ua the UA value in W/K
     * @return this builder for chaining
     */
    public Builder UAvalue(double ua) {
      this.uaValue = ua;
      return this;
    }

    /**
     * Sets the thermal effectiveness (0.0-1.0).
     *
     * @param effectiveness thermal effectiveness fraction
     * @return this builder for chaining
     */
    public Builder thermalEffectiveness(double effectiveness) {
      this.thermalEffectiveness = effectiveness;
      return this;
    }

    /**
     * Sets the approach temperature difference (minimum temperature difference).
     *
     * @param deltaT temperature difference in Kelvin
     * @return this builder for chaining
     */
    public Builder deltaT(double deltaT) {
      this.deltaT = deltaT;
      return this;
    }

    /**
     * Sets the outlet temperature for a specified stream.
     *
     * @param temperature outlet temperature value
     * @param unit temperature unit ("K", "C", or "F")
     * @param streamNumber 0 for hot stream, 1 for cold stream
     * @return this builder for chaining
     */
    public Builder outTemperature(double temperature, String unit, int streamNumber) {
      this.outTemperature = temperature;
      this.outTemperatureUnit = unit;
      this.outStreamSpecificationNumber = streamNumber;
      return this;
    }

    /**
     * Sets the flow arrangement type.
     *
     * @param arrangement flow arrangement (e.g., "counterflow", "parallel flow", "concentric tube counterflow", "cross
     * flow")
     * @return this builder for chaining
     */
    public Builder flowArrangement(String arrangement) {
      this.flowArrangement = arrangement;
      return this;
    }

    /**
     * Sets an initial guess for outlet temperature to help convergence.
     *
     * @param temperature guess temperature value
     * @param unit temperature unit ("K", "C", or "F")
     * @return this builder for chaining
     */
    public Builder guessOutTemperature(double temperature, String unit) {
      this.guessOutTemperature = temperature;
      this.guessOutTemperatureUnit = unit;
      return this;
    }

    /**
     * Builds and returns the configured HeatExchanger instance.
     *
     * @return a new HeatExchanger instance with the specified configuration
     * @throws IllegalStateException if required streams are not set
     */
    public HeatExchanger build() {
      if (hotStream == null) {
        throw new IllegalStateException("Hot stream must be set. Use hotStream(stream)");
      }

      HeatExchanger hx;
      if (coldStream != null) {
        hx = new HeatExchanger(name, hotStream, coldStream);
      } else {
        hx = new HeatExchanger(name, hotStream);
      }

      hx.setUAvalue(uaValue);
      hx.setFlowArrangement(flowArrangement);
      hx.guessOutTemperature = guessOutTemperature;
      hx.guessOutTemperatureUnit = guessOutTemperatureUnit;

      if (thermalEffectiveness > 0) {
        hx.setThermalEffectiveness(thermalEffectiveness);
      }

      if (deltaT > 0) {
        hx.setUseDeltaT(true);
        hx.setDeltaT(deltaT);
      }

      if (outStreamSpecificationNumber >= 0 && outTemperature > 0) {
        hx.setOutletTemperature(outTemperature);
      }

      return hx;
    }
  }

  /**
   * Copies all equipment and thermal state with a fresh independent participant identity.
   *
   * @return independent deep copy
   */
  public HeatExchanger copy() {
    HeatExchanger result = SerializationUtils.clone(this);
    result.transientIdentity = UUID.randomUUID().toString();
    return result;
  }

  /**
   * Rejects external heat boundaries before changing the physical model or live state.
   *
   * @throws IllegalArgumentException if the heat port is externally connected or specified
   */
  private void requireInternalThermalPort() {
    if (!hasOnlyInternalCalculatedEnergyPort("heatDuty")) {
      throw new IllegalArgumentException("Closed two-fluid dynamics requires its internal calculated heatDuty port");
    }
  }

  /**
   * Solves the lumped steady equations and initializes the nominal-mass inventories without advancing time.
   *
   * @param id calculation identity
   * @throws IllegalArgumentException if configuration or inlet states are unsupported
   * @throws IllegalStateException if the nonlinear thermal balance does not converge
   */
  private void runThermalSteadyState(UUID id) {
    validateThermalConfiguration();
    SystemInterface[] fluids = inletWorkspaces();
    ThermalState initial = initialState(fluids);
    Evaluation result = solve(fluids, initial, Double.POSITIVE_INFINITY);
    double[] mass = new double[] {getShellHoldupVolume() * result.fluids[0].getDensity("kg/m3"),
        getTubeHoldupVolume() * result.fluids[1].getDensity("kg/m3")};
    ThermalState initialized = new ThermalState(result.temperatures, result.enthalpies, mass, initial.signatures,
        structuralConfiguration());
    publish(result, id);
    thermalState = initialized;
    stepStart = null;
    lastPhysicalId = null;
    lastBalance = null;
  }

  /**
   * Advances one implicit interval, or refines the same UUID from its retained start state.
   *
   * @param dt finite positive timestep in seconds
   * @param id non-null physical timestep identity; use a new identity for the next interval
   * @throws IllegalArgumentException if inputs, identity or fixed-inventory assumptions are invalid
   * @throws IllegalStateException if the nonlinear thermal balance does not converge
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt <= 0.0 || id == null) {
      throw new IllegalArgumentException("A finite positive timestep and physical UUID are required");
    }
    if (!dynamicModelEnabled) {
      run(id);
      increaseTime(dt);
      return;
    }
    validateThermalConfiguration();
    SystemInterface[] fluids = inletWorkspaces();
    boolean repeated = id.equals(lastPhysicalId);
    if (repeated && Double.compare(dt, lastStepSeconds) != 0) {
      throw new IllegalArgumentException("One physical UUID cannot identify different interval durations");
    }
    ThermalState start = repeated ? stepStart : thermalState;
    if (start == null) {
      start = initialState(fluids);
    }
    requireSameInventory(start, fluids);
    Evaluation result = solve(fluids, start, dt);
    double oldEnergy = start.energy();
    ThermalState accepted = new ThermalState(result.temperatures, result.enthalpies, start.mass, start.signatures,
        start.structure);
    double boundaryPower = 0.0;
    for (int side = 0; side < 2; side++) {
      boundaryPower += getInStream(side).getFlowRate("kg/sec")
          * (fluids[side].getEnthalpy("J/kg") - result.enthalpies[side]);
    }
    Balance receipt = new Balance(oldEnergy, accepted.energy(), dt * boundaryPower, dt);
    double startTime = repeated ? stepStartTime : getTime();
    if (!Double.isFinite(startTime + dt) || !Double.isFinite(receipt.getResidualJ())) {
      throw new IllegalArgumentException("Thermal interval overflows the finite clock or first-law receipt");
    }
    publish(result, id);
    thermalState = accepted;
    stepStart = start;
    lastPhysicalId = id;
    lastStepSeconds = dt;
    stepStartTime = startTime;
    lastBalance = receipt;
    setTime(startTime + dt);
  }

  /**
   * Returns the last accepted interval's thermal enthalpy accumulation receipt.
   *
   * @return immutable receipt, or null before the first transient interval
   */
  public Balance getLastThermalBalance() {
    return lastBalance;
  }

  /**
   * Returns fluid-plus-wall thermal inventory on this constant-pressure model basis.
   *
   * @return thermal inventory in J, or NaN before initialization
   */
  public double getThermalInventoryEnergyJ() {
    return thermalState == null ? Double.NaN : thermalState.energy();
  }

  /** {@inheritDoc} */
  @Override
  public double getDuty(String unit) {
    return new neqsim.util.unit.PowerUnit(getDuty(), "W").getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = super.validateSetup();
    try {
      if (dynamicModelEnabled) {
        validateThermalConfiguration();
      }
    } catch (IllegalArgumentException ex) {
      result.addError("thermal configuration", ex.getMessage(),
          "Configure positive wall mass/Cp, films and area, nonnegative fluid volumes and closed thermal dynamics");
    }
    return result;
  }

  /**
   * Reads the physical thermal model inputs for a coordinated P&amp;ID/DEXPI handoff.
   *
   * @return detached parameter map with explicit units and model scope
   */
  public Map<String, Object> getThermalConfiguration() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("dynamicModeEnabled", dynamicModelEnabled);
    values.put("steadyOnlyUaWPerK", getUAvalue());
    values.put("model", "SINGLE_PHASE_FIXED_PRESSURE_TWO_MIXED_FLUIDS_AND_WALL");
    values.put("integrator", "BACKWARD_EULER_ENTHALPY_NEWTON_EJML");
    values.put("wallMassKg", getWallMass());
    values.put("wallCpJPerKgK", getWallCp());
    values.put("firstNominalVolumeM3", getShellHoldupVolume());
    values.put("secondNominalVolumeM3", getTubeHoldupVolume());
    values.put("firstFilmWPerM2K", getShellSideHtc());
    values.put("secondFilmWPerM2K", getTubeSideHtc());
    values.put("areaM2", getHeatTransferArea());
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    String identity = transientIdentity;
    if (identity == null || identity.isEmpty()) {
      String generated = UUID.randomUUID().toString();
      identity = TRANSIENT_IDENTITY_UPDATER.compareAndSet(this, identity, generated) ? generated : transientIdentity;
    }
    return "equipment:heat-exchanger:" + identity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != HeatExchanger.class || !dynamicModelEnabled) {
      return "Thermal snapshots require the concrete HeatExchanger in enabled dynamic mode";
    }
    if (ratingCalculator != null || designMode != DesignMode.SIZING || useRatingPressureDrop
        || !hxCapacityConstraints.isEmpty()) {
      return "Thermal snapshots do not cover mutable rating calculators or runtime capacity constraints";
    }
    if (!hasOnlyInternalCalculatedEnergyPort("heatDuty")) {
      return "Thermal snapshots require the original internal calculated heatDuty port";
    }
    String heaterIssue = getHeaterTransientStateCoverageIssue();
    if (heaterIssue != null) {
      return heaterIssue;
    }
    String baseIssue = getBaseTransientStateCoverageIssue(false, true);
    if (baseIssue != null) {
      return baseIssue;
    }
    for (int side = 0; side < 2; side++) {
      if (getOutStream(side).getClass() != Stream.class) {
        return "Dynamic exchanger outlets require concrete, independently owned streams";
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Snapshot captureTransientState() {
    String issue = getTransientStateCoverageIssue();
    if (issue != null) {
      throw new IllegalStateException(issue);
    }
    return new Snapshot(this);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(Snapshot snapshot) {
    if (snapshot == null || !getTransientStateIdentity().equals(snapshot.identity)) {
      throw new IllegalArgumentException("Snapshot belongs to another exchanger");
    }
    restoreBaseTransientState(snapshot.base);
    setWallMass(snapshot.configuration[0]);
    setWallCp(snapshot.configuration[1]);
    setShellHoldupVolume(snapshot.configuration[2]);
    setTubeHoldupVolume(snapshot.configuration[3]);
    setShellSideHtc(snapshot.configuration[4]);
    setTubeSideHtc(snapshot.configuration[5]);
    setHeatTransferArea(snapshot.configuration[6]);
    dynamicModelEnabled = snapshot.enabled;
    setWallTemperature(snapshot.wallTemperature);
    thermalState = snapshot.state;
    stepStart = snapshot.start;
    lastPhysicalId = snapshot.physicalId;
    lastStepSeconds = snapshot.stepSeconds;
    stepStartTime = snapshot.startTime;
    lastBalance = snapshot.balance;
    duty = snapshot.dutyW;
    snapshot.steady.restore(this);
    restoreHeaterSpecificationState(snapshot.heater);
    restoreInternalCalculatedEnergyPort("heatDuty", snapshot.internalPort, snapshot.internalEnergyStream);
    getEnergyPort("heatDuty").setMode(snapshot.portMode);
    getEnergyPort("heatDuty").setRequestedPower(snapshot.requestedPower);
    getEnergyPort("heatDuty").setDuty(snapshot.portDuty);
    for (int side = 0; side < 2; side++) {
      ((Stream) getOutStream(side)).restoreTransientState(snapshot.outlets[side]);
    }
  }

  /**
   * Validates physical parameters before any live state is mutated.
   *
   * @throws IllegalArgumentException if a thermal model prerequisite is absent
   */
  private void validateThermalConfiguration() {
    double[] positive = {getWallMass(), getWallCp(), getHeatTransferArea(), getShellSideHtc(), getTubeSideHtc()};
    for (double value : positive) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("Wall mass/Cp, both film coefficients and area must be finite and positive");
      }
    }
    for (double volume : new double[] {getShellHoldupVolume(), getTubeHoldupVolume()}) {
      if (!Double.isFinite(volume) || volume < 0.0) {
        throw new IllegalArgumentException("Nominal fluid volumes must be finite and nonnegative");
      }
    }
    if (!dynamicModelEnabled) {
      throw new IllegalArgumentException("Enable thermal dynamics before running the thermal model");
    }
    requireInternalThermalPort();
  }

  /**
   * Creates normalized, detached property workspaces so zero flow cannot erase thermal inventory.
   *
   * @return initialized independent inlet fluids at one kg/s
   */
  private SystemInterface[] inletWorkspaces() {
    SystemInterface[] result = new SystemInterface[2];
    for (int side = 0; side < 2; side++) {
      double flow = getInStream(side).getFlowRate("kg/sec");
      if (!Double.isFinite(flow) || flow <= 0.0) {
        throw new IllegalArgumentException("This thermal model requires finite positive inlet mass flow");
      }
      result[side] = getInStream(side).getFluid().clone();
      result[side].setTotalFlowRate(1.0, "kg/sec");
      flashSinglePhase(result[side]);
    }
    return result;
  }

  /**
   * Initializes nominal masses and the explicitly configured or mean wall temperature.
   *
   * @param fluids initialized inlet property workspaces
   * @return detached starting inventory
   */
  private ThermalState initialState(SystemInterface[] fluids) {
    SystemInterface[] inventory = new SystemInterface[2];
    for (int side = 0; side < 2; side++) {
      inventory[side] = fluids[side].clone();
      inventory[side].setTemperature(getOutStream(side).getTemperature());
      flashSinglePhase(inventory[side]);
    }
    double first = inventory[0].getTemperature();
    double second = inventory[1].getTemperature();
    double wall = Double.isNaN(getWallTemperature()) ? 0.5 * (first + second) : getWallTemperature();
    if (!Double.isFinite(wall) || wall <= 0.0) {
      throw new IllegalArgumentException("Initial wall temperature must be finite and positive");
    }
    return new ThermalState(new double[] {first, second, wall},
        new double[] {inventory[0].getEnthalpy("J/kg"), inventory[1].getEnthalpy("J/kg")},
        new double[] {getShellHoldupVolume() * inventory[0].getDensity("kg/m3"),
            getTubeHoldupVolume() * inventory[1].getDensity("kg/m3")},
        new String[] {signature(fluids[0]), signature(fluids[1])}, structuralConfiguration());
  }

  /**
   * Rejects inventory-model changes that would otherwise silently create energy or components.
   *
   * @param start retained starting state
   * @param fluids current detached inlet fluids
   */
  private void requireSameInventory(ThermalState start, SystemInterface[] fluids) {
    if (!Arrays.equals(start.structure, structuralConfiguration())) {
      throw new IllegalArgumentException("Wall/holdup configuration changed; reinitialize with run() before dynamics");
    }
    for (int side = 0; side < 2; side++) {
      if (!start.signatures[side].equals(signature(fluids[side]))) {
        throw new IllegalArgumentException(
            "Pressure, composition, salinity or property package changed during dynamics");
      }
    }
  }

  /**
   * Captures invariant wall capacity and nominal fluid-volume inputs.
   *
   * @return detached invariant configuration
   */
  private double[] structuralConfiguration() {
    return new double[] {getWallMass(), getWallCp(), getShellHoldupVolume(), getTubeHoldupVolume()};
  }

  /**
   * Identifies a supported fixed-composition, fixed-pressure property basis.
   *
   * @param fluid normalized initialized fluid
   * @return deterministic identity excluding temperature and throughput
   */
  private static String signature(SystemInterface fluid) {
    Class<?> type = fluid.getClass();
    if (type != neqsim.thermo.system.SystemSrkEos.class && type != neqsim.thermo.system.SystemPrEos.class
        && type != neqsim.thermo.system.SystemWaterIF97.class && type != SystemSeawater.class) {
      throw new IllegalArgumentException("Audited thermal fluids are SRK, PR, reference IF97 water and seawater");
    }
    StringBuilder value = new StringBuilder(fluid.getClass().getName());
    value.append('|').append(fluid.getPressure("bara")).append('|').append(fluid.getModelName()).append('|')
        .append(fluid.getMixingRule());
    if (fluid instanceof SystemSeawater) {
      value.append('|').append(((SystemSeawater) fluid).getSalinity());
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      ComponentInterface item = fluid.getComponent(component);
      value.append('|').append(item.getComponentName()).append(':').append(Math.rint(item.getz() * 1e12) / 1e12)
          .append(':').append(item.getTC()).append(':').append(item.getPC()).append(':').append(item.getMolarMass())
          .append(':').append(item.getAcentricFactor()).append(':').append(item.getCpA()).append(':')
          .append(item.getCpB()).append(':').append(item.getCpC()).append(':').append(item.getCpD()).append(':')
          .append(item.getCpE());
      if (item.getAttractiveTerm() != null) {
        value.append(':').append(item.getAttractiveTerm().getClass().getName()).append(':')
            .append(item.getAttractiveTerm().getm());
        for (int parameter = 0; parameter < item.getAttractiveTerm().getNumberOfParameters(); parameter++) {
          value.append(':').append(item.getAttractiveTerm().getParameters(parameter));
        }
      }
    }
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase) instanceof PhaseEosInterface) {
        EosMixingRulesInterface rule = ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule();
        if (rule == null) {
          continue;
        }
        String ruleClass = rule.getClass().getSimpleName();
        if (!"ClassicVdW".equals(ruleClass) && !"ClassicSRK".equals(ruleClass)) {
          throw new IllegalArgumentException("Thermal inventory currently supports classic EOS mixing rules only");
        }
        value.append('|').append(rule.getClass().getName()).append(':').append(rule.getName()).append(':')
            .append(rule.getBmixType());
        for (int first = 0; first < fluid.getNumberOfComponents(); first++) {
          for (int second = 0; second < fluid.getNumberOfComponents(); second++) {
            value.append(':').append(rule.getBinaryInteractionParameter(first, second)).append(':')
                .append(rule.getBinaryInteractionParameterT1(first, second));
          }
        }
      }
    }
    return value.toString();
  }

  /**
   * Solves both enthalpy inventories and the metal wall simultaneously with bounded Newton iterations.
   *
   * @param inlet normalized inlet property states
   * @param start interval starting inventory
   * @param dt timestep, or positive infinity for the lumped steady equations
   * @return fully converged detached thermal evaluation
   */
  private Evaluation solve(SystemInterface[] inlet, ThermalState start, double dt) {
    double[] temperature = start.temperature.clone();
    double minimum = Math.min(Math.min(temperature[0], temperature[1]), temperature[2]);
    double maximum = Math.max(Math.max(temperature[0], temperature[1]), temperature[2]);
    for (SystemInterface fluid : inlet) {
      minimum = Math.min(minimum, fluid.getTemperature());
      maximum = Math.max(maximum, fluid.getTemperature());
    }
    for (int iteration = 0; iteration < 40; iteration++) {
      Evaluation evaluated = evaluate(inlet, start, temperature, dt);
      // Bound the energy residual at small dt without asking for sub-ulp temperature changes.
      if (evaluated.maximumResidual < Math.max(1.0e-5, 1.0e-5 / dt)) {
        return evaluated;
      }
      double[] change = new EjmlLinearAlgebra().solve(evaluated.jacobian,
          new double[] {-evaluated.residual[0], -evaluated.residual[1], -evaluated.residual[2]});
      boolean accepted = false;
      for (double fraction = 1.0; fraction >= 1.0e-6; fraction *= 0.5) {
        double[] trial = new double[3];
        boolean bounded = true;
        for (int node = 0; node < 3; node++) {
          trial[node] = temperature[node] + fraction * change[node];
          bounded &= Double.isFinite(trial[node]) && trial[node] >= minimum - 1.0e-8 && trial[node] <= maximum + 1.0e-8;
        }
        if (bounded && evaluate(inlet, start, trial, dt).maximumResidual < evaluated.maximumResidual) {
          temperature = trial;
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        throw new IllegalStateException("Bounded exchanger enthalpy solve failed to reduce its residual");
      }
    }
    throw new IllegalStateException("Exchanger enthalpy solve exceeded 40 iterations");
  }

  /**
   * Evaluates the nonlinear thermal first law and its exact local enthalpy derivative.
   *
   * @param inlet normalized inlet workspaces
   * @param start fixed interval starting state
   * @param temperature candidate fluid and wall temperatures in K
   * @param dt timestep, or positive infinity for steady state
   * @return residual, Jacobian and candidate fluids
   */
  private Evaluation evaluate(SystemInterface[] inlet, ThermalState start, double[] temperature, double dt) {
    SystemInterface[] fluids = new SystemInterface[2];
    double[] enthalpy = new double[2];
    double[] residual = new double[3];
    double[][] jacobian = new double[3][3];
    double[] conductance = {getShellSideHtc() * getHeatTransferArea(), getTubeSideHtc() * getHeatTransferArea()};
    double wallCapacity = getWallMass() * getWallCp();
    residual[2] = wallCapacity / dt * (temperature[2] - start.temperature[2]);
    jacobian[2][2] = wallCapacity / dt;
    for (int side = 0; side < 2; side++) {
      fluids[side] = inlet[side].clone();
      fluids[side].setTemperature(temperature[side]);
      flashSinglePhase(fluids[side]);
      enthalpy[side] = fluids[side].getEnthalpy("J/kg");
      double massFlow = getInStream(side).getFlowRate("kg/sec");
      double wallTransfer = conductance[side] * (temperature[side] - temperature[2]);
      residual[side] = start.mass[side] / dt * (enthalpy[side] - start.enthalpy[side])
          + massFlow * (enthalpy[side] - inlet[side].getEnthalpy("J/kg")) + wallTransfer;
      jacobian[side][side] = (start.mass[side] / dt + massFlow) * fluids[side].getCp("J/kgK") + conductance[side];
      jacobian[side][2] = -conductance[side];
      jacobian[2][side] = -conductance[side];
      jacobian[2][2] += conductance[side];
      residual[2] -= wallTransfer;
    }
    return new Evaluation(temperature, enthalpy, fluids, residual, jacobian);
  }

  /**
   * Flashes a detached property state and enforces the bounded single-phase thermal contract.
   *
   * @param fluid normalized detached fluid
   */
  private static void flashSinglePhase(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() != 1 || !Double.isFinite(fluid.getEnthalpy("J/kg"))
        || !Double.isFinite(fluid.getCp("J/kgK")) || fluid.getCp("J/kgK") <= 0.0
        || !Double.isFinite(fluid.getDensity("kg/m3")) || fluid.getDensity("kg/m3") <= 0.0) {
      throw new IllegalArgumentException("Thermal inventory requires a finite stable single-phase fluid");
    }
  }

  /**
   * Publishes both solved mixed outlets atomically after every flash has succeeded.
   *
   * @param result detached converged evaluation
   * @param id calculation identity
   */
  private void publish(Evaluation result, UUID id) {
    for (int side = 0; side < 2; side++) {
      result.fluids[side].setTotalFlowRate(getInStream(side).getFlowRate("kg/sec"), "kg/sec");
      // Rescaling flow reinitializes phase counts in reference systems. Reflash
      // the detached state instead of publishing duplicated phase properties.
      flashSinglePhase(result.fluids[side]);
    }
    for (int side = 0; side < 2; side++) {
      getOutStream(side).setThermoSystem(result.fluids[side]);
      getOutStream(side).setCalculationIdentifier(id);
    }
    setWallTemperature(result.temperatures[2]);
    duty = getShellSideHtc() * getHeatTransferArea() * (result.temperatures[0] - result.temperatures[2]);
    publishHeatDuty();
    firstTime = false;
    NTU = Double.NaN;
    thermalEffectiveness = Double.NaN;
    isSolved = true;
    setCalculationIdentifier(id);
  }

  /**
   * Immutable nonlinear solve workspace, never retained as live process state.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Evaluation {
    private final double[] temperatures;
    private final double[] enthalpies;
    private final SystemInterface[] fluids;
    private final double[] residual;
    private final double[][] jacobian;
    private final double maximumResidual;

    /**
     * Stores one detached evaluation.
     *
     * @param temperatures candidate temperatures
     * @param enthalpies candidate specific enthalpies
     * @param fluids detached candidate fluids
     * @param residual thermal residuals in W
     * @param jacobian thermal derivative matrix
     */
    private Evaluation(double[] temperatures, double[] enthalpies, SystemInterface[] fluids, double[] residual,
        double[][] jacobian) {
      this.temperatures = temperatures.clone();
      this.enthalpies = enthalpies;
      this.fluids = fluids;
      this.residual = residual;
      this.jacobian = jacobian;
      maximumResidual = Math.max(Math.max(Math.abs(residual[0]), Math.abs(residual[1])), Math.abs(residual[2]));
    }
  }

  /**
   * Immutable fixed-mass thermal inventory.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ThermalState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] temperature;
    private final double[] enthalpy;
    private final double[] mass;
    private final String[] signatures;
    private final double[] structure;

    /**
     * Copies the thermal state and its fixed-inventory assumptions.
     *
     * @param temperature fluid and wall temperatures
     * @param enthalpy fluid specific enthalpies
     * @param mass nominal fixed fluid masses
     * @param signatures composition and pressure identities
     * @param structure wall and nominal-volume parameters
     */
    private ThermalState(double[] temperature, double[] enthalpy, double[] mass, String[] signatures,
        double[] structure) {
      this.temperature = temperature.clone();
      this.enthalpy = enthalpy.clone();
      this.mass = mass.clone();
      this.signatures = signatures.clone();
      this.structure = structure.clone();
    }

    /**
     * Calculates the total retained thermal inventory.
     *
     * @return fluid enthalpy inventory plus metal sensible energy in J
     */
    private double energy() {
      return mass[0] * enthalpy[0] + mass[1] * enthalpy[1] + structure[0] * structure[1] * temperature[2];
    }
  }

  /**
   * Immutable accepted-interval first-law evidence.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Balance implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double initialEnergyJ;
    private final double finalEnergyJ;
    private final double boundaryEnergyJ;
    private final double seconds;

    /**
     * Records actual inventory change against backward-Euler boundary quadrature.
     *
     * @param initial initial inventory in J
     * @param end final inventory in J
     * @param boundary net inlet-minus-outlet enthalpy over the interval in J
     * @param dt interval duration in seconds
     */
    private Balance(double initial, double end, double boundary, double dt) {
      initialEnergyJ = initial;
      finalEnergyJ = end;
      boundaryEnergyJ = boundary;
      seconds = dt;
    }

    /**
     * Reads the accepted inventory accumulation.
     *
     * @return accumulated fluid-plus-metal thermal energy change in J
     */
    public double getAccumulationJ() {
      return finalEnergyJ - initialEnergyJ;
    }

    /**
     * Reads integrated inlet-minus-outlet boundary power.
     *
     * @return net boundary energy entering the two circuits in J
     */
    public double getBoundaryEnergyJ() {
      return boundaryEnergyJ;
    }

    /**
     * Compares accumulation against net boundary energy.
     *
     * @return signed first-law residual in J
     */
    public double getResidualJ() {
      return getAccumulationJ() - boundaryEnergyJ;
    }

    /**
     * Exports a detached thermal balance receipt.
     *
     * @return unit-explicit first-law receipt
     */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("initialThermalEnergyJ", initialEnergyJ);
      values.put("finalThermalEnergyJ", finalEnergyJ);
      values.put("boundaryEnergyJ", boundaryEnergyJ);
      values.put("accumulationJ", getAccumulationJ());
      values.put("residualJ", getResidualJ());
      values.put("timeStepSeconds", seconds);
      values.put("basis", "FIXED_MASS_CONSTANT_PRESSURE_ENTHALPY_INVENTORY_AND_METAL");
      return values;
    }
  }

  /**
   * Immutable snapshot preserving the live exchanger, outlet and measurement-reference identities.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final SteadyStateSnapshot steady;
    private final HeaterSpecificationState heater;
    private final neqsim.process.equipment.stream.EnergyPort internalPort;
    private final neqsim.process.equipment.stream.EnergyStream internalEnergyStream;
    private final neqsim.process.equipment.stream.EnergyPortMode portMode;
    private final double portDuty;
    private final double requestedPower;
    private final String identity;
    private final ProcessEquipmentTransientState base;
    private final ThermalState state;
    private final ThermalState start;
    private final UUID physicalId;
    private final double stepSeconds;
    private final double startTime;
    private final double[] configuration;
    private final double wallTemperature;
    private final double dutyW;
    private final boolean enabled;
    private final Balance balance;
    private final Stream.TransientState[] outlets;

    /**
     * Captures all fields changed by supported thermal execution and thermal-parameter setters.
     *
     * @param exchanger actual source participant
     */
    private Snapshot(HeatExchanger exchanger) {
      steady = new SteadyStateSnapshot(exchanger);
      heater = exchanger.captureHeaterSpecificationState();
      internalPort = exchanger.getEnergyPort("heatDuty");
      internalEnergyStream = exchanger.getEnergyStream();
      portMode = exchanger.getEnergyPort("heatDuty").getMode();
      portDuty = exchanger.getEnergyPort("heatDuty").getDuty();
      requestedPower = exchanger.getEnergyPort("heatDuty").getRequestedPower();
      identity = exchanger.getTransientStateIdentity();
      base = exchanger.captureBaseTransientState();
      state = exchanger.thermalState;
      start = exchanger.stepStart;
      physicalId = exchanger.lastPhysicalId;
      stepSeconds = exchanger.lastStepSeconds;
      startTime = exchanger.stepStartTime;
      configuration = new double[] {exchanger.getWallMass(), exchanger.getWallCp(), exchanger.getShellHoldupVolume(),
          exchanger.getTubeHoldupVolume(), exchanger.getShellSideHtc(), exchanger.getTubeSideHtc(),
          exchanger.getHeatTransferArea()};
      wallTemperature = exchanger.getWallTemperature();
      dutyW = exchanger.duty;
      enabled = exchanger.isDynamicModelEnabled();
      balance = exchanger.lastBalance;
      outlets = new Stream.TransientState[] {((Stream) exchanger.getOutStream(0)).captureTransientState(),
          ((Stream) exchanger.getOutStream(1)).captureTransientState()};
    }
  }

  /**
   * Detached specification and cache state used by the thermal transaction boundary.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class SteadyStateSnapshot implements java.io.Serializable {
    private static final long serialVersionUID = 1000L;
    private final double NTU;
    private final double temperatureOut;
    private final double dT;
    private final double dH;
    private final double UAvalue;
    private final double hotColdDutyBalance;
    private final double guessOutTemperature;
    private final double thermalEffectiveness;
    private final double lastSalinity0;
    private final double lastSalinity1;
    private final double deltaT;
    private final double ratingU;
    private final double ratingArea;
    private final double designDuty;
    private final double designUAValue;
    private final double minApproachTemperature;
    private final double maxShellPressureDrop;
    private final double maxTubePressureDrop;
    private final double lastInStream2Temperature;
    private final double lastInStream2Pressure;
    private final double lastInStream2FlowRate;
    private final double lastUAvalue;
    private final double lastTemperature;
    private final double lastPressure;
    private final double lastFlowRate;
    private final double lastOutPressure;
    private final double lastOutTemperature;
    private final double lastDuty;
    private final double lastPressureDrop;
    private final boolean setTemperature;
    private final boolean firstTime;
    private final boolean useDeltaT;
    private final boolean useRatingPressureDrop;
    private final boolean hxCapacityAnalysisEnabled;
    private final boolean hxAutoSized;
    private final boolean modeObservationPending;
    private final String temperatureOutUnit;
    private final String guessOutTemperatureUnit;
    private final String flowArrangement;
    private final int outStreamSpecificationNumber;
    private final int shellPasses;
    private final DesignMode designMode;
    private final double[] lastInStream2Composition;
    private final double[] lastComposition;

    /**
     * Captures the current specification without replacing referenced data.
     *
     * @param source actual equipment instance
     */
    private SteadyStateSnapshot(HeatExchanger source) {
      NTU = source.NTU;
      temperatureOut = source.temperatureOut;
      dT = source.dT;
      dH = source.dH;
      UAvalue = source.UAvalue;
      hotColdDutyBalance = source.hotColdDutyBalance;
      guessOutTemperature = source.guessOutTemperature;
      thermalEffectiveness = source.thermalEffectiveness;
      lastSalinity0 = source.lastSalinity0;
      lastSalinity1 = source.lastSalinity1;
      deltaT = source.deltaT;
      ratingU = source.ratingU;
      ratingArea = source.ratingArea;
      designDuty = source.designDuty;
      designUAValue = source.designUAValue;
      minApproachTemperature = source.minApproachTemperature;
      maxShellPressureDrop = source.maxShellPressureDrop;
      maxTubePressureDrop = source.maxTubePressureDrop;
      lastInStream2Temperature = source.lastInStream2Temperature;
      lastInStream2Pressure = source.lastInStream2Pressure;
      lastInStream2FlowRate = source.lastInStream2FlowRate;
      lastUAvalue = source.lastUAvalue;
      lastTemperature = source.lastTemperature;
      lastPressure = source.lastPressure;
      lastFlowRate = source.lastFlowRate;
      lastOutPressure = source.lastOutPressure;
      lastOutTemperature = source.lastOutTemperature;
      lastDuty = source.lastDuty;
      lastPressureDrop = source.lastPressureDrop;
      setTemperature = source.setTemperature;
      firstTime = source.firstTime;
      useDeltaT = source.useDeltaT;
      useRatingPressureDrop = source.useRatingPressureDrop;
      hxCapacityAnalysisEnabled = source.hxCapacityAnalysisEnabled;
      hxAutoSized = source.hxAutoSized;
      modeObservationPending = source.modeObservationPending;
      temperatureOutUnit = source.temperatureOutUnit;
      guessOutTemperatureUnit = source.guessOutTemperatureUnit;
      flowArrangement = source.flowArrangement;
      outStreamSpecificationNumber = source.outStreamSpecificationNumber;
      shellPasses = source.shellPasses;
      designMode = source.designMode;
      lastInStream2Composition = source.lastInStream2Composition == null ? null
          : source.lastInStream2Composition.clone();
      lastComposition = source.lastComposition == null ? null : source.lastComposition.clone();
    }

    /**
     * Restores specification and cache values on the same live equipment.
     *
     * @param target original equipment instance
     */
    private void restore(HeatExchanger target) {
      target.NTU = NTU;
      target.temperatureOut = temperatureOut;
      target.dT = dT;
      target.dH = dH;
      target.UAvalue = UAvalue;
      target.hotColdDutyBalance = hotColdDutyBalance;
      target.guessOutTemperature = guessOutTemperature;
      target.thermalEffectiveness = thermalEffectiveness;
      target.lastSalinity0 = lastSalinity0;
      target.lastSalinity1 = lastSalinity1;
      target.deltaT = deltaT;
      target.ratingU = ratingU;
      target.ratingArea = ratingArea;
      target.designDuty = designDuty;
      target.designUAValue = designUAValue;
      target.minApproachTemperature = minApproachTemperature;
      target.maxShellPressureDrop = maxShellPressureDrop;
      target.maxTubePressureDrop = maxTubePressureDrop;
      target.lastInStream2Temperature = lastInStream2Temperature;
      target.lastInStream2Pressure = lastInStream2Pressure;
      target.lastInStream2FlowRate = lastInStream2FlowRate;
      target.lastUAvalue = lastUAvalue;
      target.lastTemperature = lastTemperature;
      target.lastPressure = lastPressure;
      target.lastFlowRate = lastFlowRate;
      target.lastOutPressure = lastOutPressure;
      target.lastOutTemperature = lastOutTemperature;
      target.lastDuty = lastDuty;
      target.lastPressureDrop = lastPressureDrop;
      target.setTemperature = setTemperature;
      target.firstTime = firstTime;
      target.useDeltaT = useDeltaT;
      target.useRatingPressureDrop = useRatingPressureDrop;
      target.hxCapacityAnalysisEnabled = hxCapacityAnalysisEnabled;
      target.hxAutoSized = hxAutoSized;
      target.modeObservationPending = modeObservationPending;
      target.temperatureOutUnit = temperatureOutUnit;
      target.guessOutTemperatureUnit = guessOutTemperatureUnit;
      target.flowArrangement = flowArrangement;
      target.outStreamSpecificationNumber = outStreamSpecificationNumber;
      target.shellPasses = shellPasses;
      target.designMode = designMode;
      target.lastInStream2Composition = lastInStream2Composition == null ? null : lastInStream2Composition.clone();
      target.lastComposition = lastComposition == null ? null : lastComposition.clone();
      target.ratingCalculator = null;
      target.hxCapacityConstraints.clear();
    }
  }

}
