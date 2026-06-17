/*
 * HeatExchanger.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.process.equipment.heatexchanger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.ml.StateVector;
import neqsim.process.ml.StateVectorProvider;
import neqsim.process.conditionmonitor.ConditionMonitorSpecifications;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.util.monitor.HXResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HeatExchanger class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class HeatExchanger extends Heater implements HeatExchangerInterface, StateVectorProvider,
    neqsim.process.equipment.capacity.CapacityConstrainedEquipment {
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
  double NTU;
  protected double temperatureOut = 0;

  protected double dT = 0.0;

  double dH = 0.0;
  private double UAvalue = 500.0;
  double duty = 0.0;
  private double hotColdDutyBalance = 1.0;
  boolean firstTime = true;
  public double guessOutTemperature = 273.15 + 130.0;
  public String guessOutTemperatureUnit = "K";
  int outStreamSpecificationNumber = 0;
  public double thermalEffectiveness = 0.0;
  private String flowArrangement = "concentric tube counterflow";
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
  private java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> hxCapacityConstraints =
      new java.util.LinkedHashMap<String, neqsim.process.equipment.capacity.CapacityConstraint>();
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
  private boolean dynamicModelEnabled = false;

  /** Shell-side fluid temperature in K — state variable for fluid accumulation model. */
  private double shellFluidTemperature = Double.NaN;
  /** Tube-side fluid temperature in K — state variable for fluid accumulation model. */
  private double tubeFluidTemperature = Double.NaN;

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
   * <p>
   * Add inlet stream.
   * </p>
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
   * <p>
   * setFeedStream. Will also set name of outstreams.
   * </p>
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
   * HeatExchanger has two outlet streams corresponding to the two feed sides. This method returns
   * the outlet for feed side 0. Use {@link #getOutStream(int)} to access a specific side.
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
   * <p>
   * Getter for the field <code>inStream</code>.
   * </p>
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
  }

  /**
   * <p>
   * Get temperature of outstream i.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getOutTemperature(int i) {
    return outStream[i].getThermoSystem().getTemperature();
  }

  /**
   * <p>
   * Get temperature of instream i.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getInTemperature(int i) {
    return inStream[i].getThermoSystem().getTemperature();
  }

  /**
   * <p>
   * Setter for the field <code>outStream</code>.
   * </p>
   *
   * @param outStream the outStream to set
   * @param streamNumber a int
   */
  public void setOutStream(int streamNumber, StreamInterface outStream) {
    this.outStream[streamNumber] = outStream;
    outStreamSpecificationNumber = streamNumber;
  }

  /**
   * <p>
   * runSpecifiedStream.
   * </p>
   *
   * @param id UUID of run
   */
  public void runSpecifiedStream(UUID id) {
    int nonOutStreamSpecifiedStreamNumber = 0;
    if (outStreamSpecificationNumber == 0) {
      nonOutStreamSpecifiedStreamNumber = 1;
    }

    SystemInterface systemOut0 =
        inStream[nonOutStreamSpecifiedStreamNumber].getThermoSystem().clone();
    // SystemInterface systemOut1 =
    // inStream[outStreamSpecificationNumber].getThermoSystem().clone();

    if (getSpecification().equals("out stream")) {
      outStream[outStreamSpecificationNumber]
          .setFlowRate(getInStream(outStreamSpecificationNumber).getFlowRate("kg/sec"), "kg/sec");
      outStream[outStreamSpecificationNumber].run(id);
      temperatureOut = outStream[outStreamSpecificationNumber].getTemperature();
      // system =
      // outStream[outStreamSpecificationNumber].getThermoSystem().clone();
    }

    double deltaEnthalpy = outStream[outStreamSpecificationNumber].getFluid().getEnthalpy()
        - inStream[outStreamSpecificationNumber].getFluid().getEnthalpy();
    double enthalpyOutRef =
        inStream[nonOutStreamSpecifiedStreamNumber].getFluid().getEnthalpy() - deltaEnthalpy;

    ThermodynamicOperations testOps = new ThermodynamicOperations(systemOut0);
    testOps.PHflash(enthalpyOutRef);
    System.out.println("out temperature " + systemOut0.getTemperature("C"));
    outStream[nonOutStreamSpecifiedStreamNumber].setFluid(systemOut0);
  }

  /**
   * <p>
   * runDeltaT.
   * </p>
   *
   * @param id UUID of run
   */
  public void runDeltaT(UUID id) {
    if (getSpecification().equals("out stream")) {
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
      double sign = Math.signum(
          inStream[streamToCalculate].getTemperature() - inStream[streamToSet].getTemperature());
      // systemOut1.setTemperature(inTemp1);
      outStream[streamToSet].setThermoSystem(systemOut0);
      outStream[streamToCalculate].setThermoSystem(systemOut1);
      outStream[streamToSet]
          .setTemperature(inStream[streamToCalculate].getTemperature() + sign * deltaT, "K");
      if (!outStream[streamToSet].getSpecification().equals("TP")) {
        outStream[streamToSet].runTPflash();
      }
      outStream[streamToSet].run(id);
      double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();
      double C1 =
          Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
              - inStream[streamToSet].getThermoSystem().getTemperature()));

      outStream[streamToCalculate].setTemperature(
          inStream[streamToSet].getThermoSystem().getTemperature() - sign * deltaT, "K");
      if (!outStream[streamToCalculate].getSpecification().equals("TP")) {
        outStream[streamToCalculate].runTPflash();
      }
      outStream[streamToCalculate].run(id);
      double dEntalphy2 = outStream[streamToCalculate].getThermoSystem().getEnthalpy()
          - inStream[streamToCalculate].getThermoSystem().getEnthalpy();
      double C2 = Math.abs(dEntalphy2)
          / Math.abs(outStream[streamToCalculate].getThermoSystem().getTemperature()
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
      ThermodynamicOperations testOps =
          new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
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
    if (firstTime || inStream[0] == null || inStream[1] == null) {
      return true;
    }
    if (super.needRecalculation()) {
      return true;
    }
    if (inStream[1].getThermoSystem() == null) {
      return true;
    }
    SystemInterface sys2 = inStream[1].getThermoSystem();
    if (sys2.getTemperature() != lastInStream2Temperature
        || sys2.getPressure() != lastInStream2Pressure || UAvalue != lastUAvalue) {
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
   * Updates cached state for needRecalculation checks. Saves both HeatExchanger stream-2 state and
   * Heater base-class stream-1 state so that super.needRecalculation() returns false when inputs
   * are unchanged.
   */
  private void updateLastState() {
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
    if (useDeltaT) {
      runDeltaT(id);
      updateLastState();
      return;
    }
    if (getSpecification().equals("out stream")) {
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
      outStream[streamToSet].setTemperature(inStream[streamToCalculate].getTemperature(), "K");
      if (!outStream[streamToSet].getSpecification().equals("TP")) {
        outStream[streamToSet].runTPflash();
      }
      outStream[streamToSet].run(id);
      double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();
      double C1 =
          Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
              - inStream[streamToSet].getThermoSystem().getTemperature()));

      outStream[streamToCalculate]
          .setTemperature(inStream[streamToSet].getThermoSystem().getTemperature(), "K");
      if (!outStream[streamToCalculate].getSpecification().equals("TP")) {
        outStream[streamToCalculate].runTPflash();
      }
      outStream[streamToCalculate].run(id);
      double dEntalphy2 = outStream[streamToCalculate].getThermoSystem().getEnthalpy()
          - inStream[streamToCalculate].getThermoSystem().getEnthalpy();
      double C2 = Math.abs(dEntalphy2)
          / Math.abs(outStream[streamToCalculate].getThermoSystem().getTemperature()
              - inStream[streamToCalculate].getThermoSystem().getTemperature());
      double Cmin = C1;
      double Cmax = C2;
      if (C2 < C1) {
        Cmin = C2;
        Cmax = C1;
      }
      double Cr = Cmin / Cmax;
      if (Math.abs(dEntalphy1) > Math.abs(dEntalphy2)) {
        int streamCHange = streamToCalculate;
        streamToCalculate = streamToSet;
        streamToSet = streamCHange;
      }

      double dEntalphy = outStream[streamToSet].getThermoSystem().getEnthalpy()
          - inStream[streamToSet].getThermoSystem().getEnthalpy();

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
      ThermodynamicOperations testOps =
          new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
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
       * dEntalphy = outStream[0].getThermoSystem().getEnthalpy() -
       * inStream[0].getThermoSystem().getEnthalpy(); corrected_Entalphy = dEntalphy *
       * inStream[0].getThermoSystem().getNumberOfMoles() /
       * inStream[1].getThermoSystem().getNumberOfMoles();
       *
       * systemOut1 = inStream[1].getThermoSystem().clone(); System.out.println("dent " +
       * dEntalphy); testOps = new ThermodynamicOperations(systemOut1);
       * testOps.PHflash(systemOut1.getEnthalpy() - corrected_Entalphy, 0);
       * outStream[1].setThermoSystem(systemOut1); System.out.println("temperatur out " +
       * outStream[1].getTemperature()); }
       */
    }

    updateLastState();
    setCalculationIdentifier(id);
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
   * <p>
   * getUAvalue.
   * </p>
   *
   * @return the UAvalue
   */
  public double getUAvalue() {
    return UAvalue;
  }

  /**
   * <p>
   * setUAvalue.
   * </p>
   *
   * @param UAvalue the UAvalue to set
   */
  public void setUAvalue(double UAvalue) {
    this.UAvalue = UAvalue;
  }

  /**
   * <p>
   * Getter for the field <code>guessOutTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getGuessOutTemperature() {
    return guessOutTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>guessOutTemperature</code>.
   * </p>
   *
   * @param guessOutTemperature a double
   */
  public void setGuessOutTemperature(double guessOutTemperature) {
    this.guessOutTemperature = guessOutTemperature;
    this.guessOutTemperatureUnit = "K";
  }

  /**
   * <p>
   * Setter for the field <code>guessOutTemperature</code>.
   * </p>
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
      inStream[i].getFluid().init(3);
      outStream[i].run(id);
      outStream[i].getFluid().init(3);
      entrop += outStream[i].getThermoSystem().getEntropy(unit)
          - inStream[i].getThermoSystem().getEntropy(unit);
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
      mass += outStream[i].getThermoSystem().getFlowRate(unit)
          - inStream[i].getThermoSystem().getFlowRate(unit);
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
      heatBalanceError += outStream[i].getThermoSystem().getEnthalpy()
          - inStream[i].getThermoSystem().getEnthalpy();

      if (Math.abs(refEx.getInStream(i).getTemperature("C")
          - getInStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      } else if (Math.abs(refEx.getOutStream(i).getTemperature("C")
          - getOutStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      }
    }
    heatBalanceError = heatBalanceError / (outStream[0].getThermoSystem().getEnthalpy()
        - inStream[0].getThermoSystem().getEnthalpy()) * 100.0;
    if (Math.abs(heatBalanceError) > 10.0) {
      String error = "Heat balance not fulfilled. Error: " + heatBalanceError + " ";
      conditionAnalysisMessage += error;
    } else {
      String error = "Heat balance ok. Enthalpy balance deviation: " + heatBalanceError + " %";
      conditionAnalysisMessage += error;
    }

    conditionAnalysisMessage += name + "/analysis ended/";

    // this.run();
    double duty1 = Math.abs(
        outStream[0].getThermoSystem().getEnthalpy() - inStream[0].getThermoSystem().getEnthalpy());
    double duty2 = Math.abs(
        outStream[1].getThermoSystem().getEnthalpy() - inStream[1].getThermoSystem().getEnthalpy());
    thermalEffectiveness = ((HeatExchanger) refExchanger).getThermalEffectiveness()
        * (duty1 + duty2) / 2.0 / Math.abs(((HeatExchanger) refExchanger).getDuty());
    hotColdDutyBalance = duty1 / duty2;
  }

  /**
   * <p>
   * runConditionAnalysis.
   * </p>
   */
  public void runConditionAnalysis() {
    runConditionAnalysis(this);
  }

  /**
   * <p>
   * Getter for the field <code>thermalEffectiveness</code>.
   * </p>
   *
   * @return a double
   */
  public double getThermalEffectiveness() {
    return thermalEffectiveness;
  }

  /**
   * <p>
   * Setter for the field <code>thermalEffectiveness</code>.
   * </p>
   *
   * @param thermalEffectiveness a double
   */
  public void setThermalEffectiveness(double thermalEffectiveness) {
    this.thermalEffectiveness = thermalEffectiveness;
  }

  /**
   * <p>
   * Getter for the field <code>flowArrangement</code>.
   * </p>
   *
   * @return String
   */
  public String getFlowArrangement() {
    return flowArrangement;
  }

  /**
   * <p>
   * Setter for the field <code>flowArrangement</code>.
   * </p>
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
   * Updates the rating calculator with current stream fluid properties. Extracts density,
   * viscosity, Cp, and thermal conductivity from both inlet streams.
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
      ratingCalculator.setTubeSideFluid(tubeDensity, tubeViscosity, tubeCp, tubeConductivity,
          tubeMassFlow, heating);
    } catch (Exception ex) {
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
      ratingCalculator.setShellSideFluid(shellDensity, shellViscosity, shellCp, shellConductivity,
          shellMassFlow);
    } catch (Exception ex) {
      // Use defaults if property extraction fails
    }
  }

  /**
   * Sets the design mode. In RATING mode, the exchanger computes UA from geometry and fluid
   * properties instead of using a user-supplied value.
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
   * Sets the ThermalDesignCalculator for rating mode. The caller should configure the calculator
   * with geometry and fluid properties before calling run().
   *
   * @param calculator configured thermal design calculator
   */
  public void setRatingCalculator(
      neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator calculator) {
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
   * <p>
   * calcThermalEffectivenes.
   * </p>
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
   * <p>
   * Getter for the field <code>hotColdDutyBalance</code>.
   * </p>
   *
   * @return a double
   */
  public double getHotColdDutyBalance() {
    return hotColdDutyBalance;
  }

  /**
   * <p>
   * Setter for the field <code>hotColdDutyBalance</code>.
   * </p>
   *
   * @param hotColdDutyBalance a double
   */
  public void setHotColdDutyBalance(double hotColdDutyBalance) {
    this.hotColdDutyBalance = hotColdDutyBalance;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new HXResponse(this));
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
      throw new IllegalStateException(
          "Both inlet streams must be connected before auto-sizing heat exchanger");
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
      sb.append("Inlet Temperature: ")
          .append(String.format("%.2f C", inStream[0].getTemperature("C"))).append("\n");
      sb.append("Outlet Temperature: ")
          .append(String.format("%.2f C", outStream[0].getTemperature("C"))).append("\n");
      sb.append("Flow Rate: ").append(String.format("%.3f kg/s", inStream[0].getFlowRate("kg/sec")))
          .append("\n");

      sb.append("\n--- Cold Side (Stream 1) ---\n");
      sb.append("Inlet Temperature: ")
          .append(String.format("%.2f C", inStream[1].getTemperature("C"))).append("\n");
      sb.append("Outlet Temperature: ")
          .append(String.format("%.2f C", outStream[1].getTemperature("C"))).append("\n");
      sb.append("Flow Rate: ").append(String.format("%.3f kg/s", inStream[1].getFlowRate("kg/sec")))
          .append("\n");

      sb.append("\n--- Heat Transfer ---\n");
      sb.append("Duty: ").append(String.format("%.2f kW", duty / 1000.0)).append("\n");
      sb.append("UA Value: ").append(String.format("%.2f W/K", UAvalue)).append("\n");
      sb.append("Thermal Effectiveness: ").append(String.format("%.3f", thermalEffectiveness))
          .append("\n");

      // Calculate LMTD
      double hotIn = inStream[0].getTemperature("K");
      double hotOut = outStream[0].getTemperature("K");
      double coldIn = inStream[1].getTemperature("K");
      double coldOut = outStream[1].getTemperature("K");
      double deltaT1 = hotIn - coldOut;
      double deltaT2 = hotOut - coldIn;
      double lmtd = (Math.abs(deltaT1 - deltaT2) < 1e-6) ? (deltaT1 + deltaT2) / 2.0
          : (deltaT1 - deltaT2) / Math.log(deltaT1 / deltaT2);
      sb.append("LMTD: ").append(String.format("%.2f K", lmtd)).append("\n");

      HeatExchangerMechanicalDesign mechDesign = getMechanicalDesign();
      if (mechDesign != null) {
        sb.append("\n--- Mechanical Design ---\n");
        sb.append("Max Design Duty: ")
            .append(String.format("%.2f kW", mechDesign.maxDesignDuty / 1000.0)).append("\n");
        sb.append("Duty Utilization: ")
            .append(String.format("%.1f%%", Math.abs(duty) / mechDesign.maxDesignDuty * 100))
            .append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * <p>
   * Setter for the field <code>useDeltaT</code>.
   * </p>
   *
   * @param useDeltaT a boolean
   */
  public void setUseDeltaT(boolean useDeltaT) {
    this.useDeltaT = useDeltaT;
  }

  /**
   * <p>
   * Getter for the field <code>deltaT</code>.
   * </p>
   *
   * @return a double
   */
  public double getDeltaT() {
    return deltaT;
  }

  /**
   * <p>
   * Setter for the field <code>deltaT</code>.
   * </p>
   *
   * @param deltaT a double
   */
  public void setDeltaT(double deltaT) {
    useDeltaT = true;
    this.deltaT = deltaT;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns state vector containing:
   * <ul>
   * <li>hot_inlet_temp - Hot side inlet temperature [K]</li>
   * <li>hot_outlet_temp - Hot side outlet temperature [K]</li>
   * <li>cold_inlet_temp - Cold side inlet temperature [K]</li>
   * <li>cold_outlet_temp - Cold side outlet temperature [K]</li>
   * <li>duty - Heat duty [kW]</li>
   * <li>ua_value - UA value [W/K]</li>
   * <li>effectiveness - Thermal effectiveness [fraction]</li>
   * <li>lmtd - Log mean temperature difference [K]</li>
   * <li>hot_flow - Hot side mass flow [kg/s]</li>
   * <li>cold_flow - Cold side mass flow [kg/s]</li>
   * </ul>
   */
  @Override
  public StateVector getStateVector() {
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
    state.add("ua_value", getUAvalue(), 0.0, 100000.0, "W/K");
    state.add("effectiveness", getThermalEffectiveness(), 0.0, 1.0, "fraction");

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
   * HeatExchanger hx = HeatExchanger.builder("E-100").hotStream(hotFeed).coldStream(coldFeed)
   *     .UAvalue(5000.0).flowArrangement("counterflow").build();
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
    if (inStream[0] == null || inStream[1] == null || outStream[0] == null
        || outStream[1] == null) {
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
      neqsim.process.equipment.capacity.CapacityConstraint dutyConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("dutyUtilization", "W",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN);
      dutyConstraint.setDesignValue(designDuty);
      dutyConstraint.setDescription("Heat duty utilization");
      dutyConstraint.setValueSupplier(() -> Math.abs(this.duty));
      hxCapacityConstraints.put("dutyUtilization", dutyConstraint);
    }

    // UA utilization constraint
    if (designUAValue > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint uaConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("uaUtilization", "W/K",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      uaConstraint.setDesignValue(designUAValue);
      uaConstraint.setDescription("UA value utilization");
      uaConstraint.setValueSupplier(() -> this.UAvalue);
      hxCapacityConstraints.put("uaUtilization", uaConstraint);
    }

    // Approach temperature constraint (inverse - we want to be above minimum)
    if (minApproachTemperature > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint approachConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("approachTemperature", "K",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
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
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints
        .values()) {
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
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints
        .values()) {
      if (constraint.isEnabled() && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints
        .values()) {
      if (constraint.isEnabled()
          && constraint
              .getType() == neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD
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
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : hxCapacityConstraints
        .values()) {
      if (constraint.isEnabled()) {
        maxUtil = Math.max(maxUtil, constraint.getUtilization());
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
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
   * Enables or disables the dynamic heat exchanger model. When enabled, the runTransient method
   * integrates the metal wall energy balance instead of using steady-state calculations.
   *
   * @param enabled true to enable
   */
  public void setDynamicModelEnabled(boolean enabled) {
    this.dynamicModelEnabled = enabled;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Dynamic heat exchanger model. When {@code dynamicModelEnabled} is true, integrates the metal
   * wall energy ODE using forward Euler:
   * </p>
   *
   * <p>
   * M_wall * Cp_wall * dT_wall/dt = h_shell * A * (T_shell - T_wall) - h_tube * A * (T_wall -
   * T_tube)
   * </p>
   *
   * <p>
   * The heat duty to each fluid side is then calculated and applied via enthalpy adjustment.
   * </p>
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!dynamicModelEnabled || wallMass <= 0.0 || heatTransferArea <= 0.0) {
      super.runTransient(dt, id);
      return;
    }

    // Initialize wall temperature on first call to the average of inlet temperatures
    if (Double.isNaN(wallTemperature)) {
      double tShellIn = inStream[0].getThermoSystem().getTemperature();
      double tTubeIn = inStream[1].getThermoSystem().getTemperature();
      wallTemperature = 0.5 * (tShellIn + tTubeIn);
    }

    double tShellIn = inStream[0].getThermoSystem().getTemperature();
    double tTubeIn = inStream[1].getThermoSystem().getTemperature();

    // --- Shell-side fluid accumulation ODE (CSTR model) ---
    // When shellHoldupVolume > 0, track shell-side fluid temperature as a state
    // variable. The fluid temperature evolves via mixing with incoming fluid and
    // heat exchange with the wall. This adds thermal inertia from the fluid mass.
    double tShellForWall = tShellIn; // default: no holdup → use inlet T
    if (shellHoldupVolume > 0.0) {
      if (Double.isNaN(shellFluidTemperature)) {
        shellFluidTemperature = tShellIn;
      }
      double shellRho = inStream[0].getThermoSystem().getDensity("kg/m3");
      double shellCp = inStream[0].getThermoSystem().getCp("J/kgK");
      double shellFluidMass = shellRho * shellHoldupVolume;
      if (shellFluidMass > 0.0 && shellCp > 0.0) {
        double shellMassFlow = inStream[0].getThermoSystem().getFlowRate("kg/sec");
        double qInletMixing = shellMassFlow * shellCp * (tShellIn - shellFluidTemperature);
        double qFluidToWall =
            shellSideHtc * heatTransferArea * (shellFluidTemperature - wallTemperature);
        double dTshellFluid = (qInletMixing - qFluidToWall) / (shellFluidMass * shellCp);
        shellFluidTemperature += dTshellFluid * dt;
      }
      tShellForWall = shellFluidTemperature;
    }

    // --- Tube-side fluid accumulation ODE (CSTR model) ---
    double tTubeForWall = tTubeIn; // default: no holdup → use inlet T
    if (tubeHoldupVolume > 0.0) {
      if (Double.isNaN(tubeFluidTemperature)) {
        tubeFluidTemperature = tTubeIn;
      }
      double tubeRho = inStream[1].getThermoSystem().getDensity("kg/m3");
      double tubeCp = inStream[1].getThermoSystem().getCp("J/kgK");
      double tubeFluidMass = tubeRho * tubeHoldupVolume;
      if (tubeFluidMass > 0.0 && tubeCp > 0.0) {
        double tubeMassFlow = inStream[1].getThermoSystem().getFlowRate("kg/sec");
        double qInletMixing = tubeMassFlow * tubeCp * (tTubeIn - tubeFluidTemperature);
        double qWallToFluid =
            tubeSideHtc * heatTransferArea * (wallTemperature - tubeFluidTemperature);
        double dTtubeFluid = (qInletMixing + qWallToFluid) / (tubeFluidMass * tubeCp);
        tubeFluidTemperature += dTtubeFluid * dt;
      }
      tTubeForWall = tubeFluidTemperature;
    }

    // Metal wall energy balance (forward Euler) — uses fluid temperatures when holdup is active
    double qShellToWall = shellSideHtc * heatTransferArea * (tShellForWall - wallTemperature);
    double qWallToTube = tubeSideHtc * heatTransferArea * (wallTemperature - tTubeForWall);
    double dTwall = (qShellToWall - qWallToTube) / (wallMass * wallCp);
    wallTemperature += dTwall * dt;

    // Apply duty to each fluid side using PH flash
    // Shell side loses heat: outlet enthalpy = inlet enthalpy - qShellToWall * dt_effective
    SystemInterface shellOut = inStream[0].getThermoSystem().clone();
    double shellInletH = inStream[0].getThermoSystem().getEnthalpy();
    double shellOutH = shellInletH - qShellToWall;
    neqsim.thermodynamicoperations.ThermodynamicOperations shellOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(shellOut);
    shellOps.PHflash(shellOutH);
    outStream[0].setThermoSystem(shellOut);
    outStream[0].setCalculationIdentifier(id);

    // Tube side gains heat: outlet enthalpy = inlet enthalpy + qWallToTube * dt_effective
    SystemInterface tubeOut = inStream[1].getThermoSystem().clone();
    double tubeInletH = inStream[1].getThermoSystem().getEnthalpy();
    double tubeOutH = tubeInletH + qWallToTube;
    neqsim.thermodynamicoperations.ThermodynamicOperations tubeOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(tubeOut);
    tubeOps.PHflash(tubeOutH);
    outStream[1].setThermoSystem(tubeOut);
    outStream[1].setCalculationIdentifier(id);

    duty = qShellToWall;
    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  /**
   * Builder class for constructing HeatExchanger instances with a fluent API.
   *
   * <p>
   * Provides a readable and maintainable way to construct heat exchangers with hot and cold stream
   * configurations, thermal specifications, and flow arrangements.
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
     * @param arrangement flow arrangement (e.g., "counterflow", "parallel flow", "concentric tube
     *        counterflow", "cross flow")
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
        hx.setOutTemperature(outTemperature);
      }

      return hx;
    }
  }
}
