/*
 * HeatExchanger.java
 *
 * Created on 15. mars 2001, 14:17
 */

package neqsim.process.equipment.heatexchanger;

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
public class HeatExchanger extends Heater implements HeatExchangerInterface, StateVectorProvider {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

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
    outStream[0].setName(name + "_Sout1");
    outStream[1].setName(name + "_Sout2");
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

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (useDeltaT) {
      runDeltaT(id);
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

    // Initialize and calculate mechanical design
    HeatExchangerMechanicalDesign mechDesign = getMechanicalDesign();
    if (mechDesign != null) {
      mechDesign.maxDesignDuty = designDuty;
      mechDesign.calcDesign();
    }

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
