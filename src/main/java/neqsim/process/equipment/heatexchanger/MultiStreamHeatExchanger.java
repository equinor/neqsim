/*
 * MultiStreamHeatExchanger.java
 *
 * Created on [Date]
 */

package neqsim.process.equipment.heatexchanger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.conditionmonitor.ConditionMonitorSpecifications;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * MultiStreamHeatExchanger class.
 * </p>
 *
 * Extends the Heater class to support multiple input and output streams, enabling the simulation of
 * complex heat exchange processes such as those found in LNG heat exchangers.
 *
 * @author [Your Name]
 * @version 1.0
 */
public class MultiStreamHeatExchanger extends Heater implements MultiStreamHeatExchangerInterface {
  private static final long serialVersionUID = 1001;
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchanger.class);

  private boolean setTemperature = false;
  private List<StreamInterface> outStreams = new ArrayList<>();
  private List<StreamInterface> inStreams = new ArrayList<>();
  private SystemInterface system;
  private double NTU;
  protected double temperatureOut = 0;

  protected double dT = 0.0;

  private double UAvalue = 500.0; // Overall heat transfer coefficient times area
  private double duty = 0.0;
  private double hotColdDutyBalance = 1.0;
  private boolean firstTime = true;
  private double guessOutTemperature = 273.15 + 130.0; // Default guess in K
  private String guessOutTemperatureUnit = "K";
  private int outStreamSpecificationNumber = 0;
  private double thermalEffectiveness = 0.0;
  private String flowArrangement = "counterflow"; // Default arrangement
  private boolean useDeltaT = false;
  private double deltaT = 1.0;

  /**
   * Constructor for MultiStreamHeatExchanger.
   *
   * @param name Name of the heat exchanger
   */
  public MultiStreamHeatExchanger(String name) {
    super(name);
  }

  /**
   * Constructor for MultiStreamHeatExchanger with initial input streams.
   *
   * @param name Name of the heat exchanger
   * @param inStreams Initial list of input streams
   */
  public MultiStreamHeatExchanger(String name, List<StreamInterface> inStreams) {
    this(name);
    this.inStreams.addAll(inStreams);
    // Initialize outStreams as clones of inStreams
    for (StreamInterface inStream : inStreams) {
      StreamInterface outStream = inStream.clone();
      outStream.setName(name + "_Sout" + (outStreams.size() + 1));
      this.outStreams.add(outStream);
    }
    setName(name);
  }

  /**
   * Adds an inlet stream to the heat exchanger.
   *
   * @param inStream Input stream to add
   */
  public void addInStream(StreamInterface inStream) {
    this.inStreams.add(inStream);
    StreamInterface outStream = inStream.clone();
    outStream.setName(getName() + "_Sout" + (outStreams.size() + 1));
    this.outStreams.add(outStream);
  }

  /**
   * Sets the feed stream at a specific index.
   *
   * @param index Index of the stream to set
   * @param inStream Input stream to set
   */
  public void setFeedStream(int index, StreamInterface inStream) {
    if (index < inStreams.size()) {
      this.inStreams.set(index, inStream);
      this.outStreams.set(index, inStream.clone());
      setName(getName());
    } else {
      throw new IndexOutOfBoundsException("Stream index out of bounds.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    for (int i = 0; i < outStreams.size(); i++) {
      outStreams.get(i).setName(name + "_Sout" + (i + 1));
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
    return outStreams.get(i);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getInStream(int i) {
    return inStreams.get(i);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutTemperature(double temperature) {
    this.temperatureOut = temperature;
  }

  /**
   * Gets the output temperature of a specific stream.
   *
   * @param i Index of the stream
   * @return Temperature of the output stream
   */
  public double getOutTemperature(int i) {
    return outStreams.get(i).getThermoSystem().getTemperature();
  }

  /**
   * Gets the input temperature of a specific stream.
   *
   * @param i Index of the stream
   * @return Temperature of the input stream
   */
  public double getInTemperature(int i) {
    return inStreams.get(i).getThermoSystem().getTemperature();
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
    for (StreamInterface outStream : outStreams) {
      outStream.displayResult();
    }
  }

  /**
   * Gets the overall UA value.
   *
   * @return UA value
   */
  public double getUAvalue() {
    return UAvalue;
  }

  /**
   * Sets the overall UA value.
   *
   * @param UAvalue UA value to set
   */
  public void setUAvalue(double UAvalue) {
    this.UAvalue = UAvalue;
  }

  /**
   * Gets the guessed outlet temperature.
   *
   * @return Guessed outlet temperature
   */
  public double getGuessOutTemperature() {
    return guessOutTemperature;
  }

  /**
   * Sets the guessed outlet temperature in Kelvin.
   *
   * @param guessOutTemperature Guessed outlet temperature
   */
  public void setGuessOutTemperature(double guessOutTemperature) {
    this.guessOutTemperature = guessOutTemperature;
    this.guessOutTemperatureUnit = "K";
  }

  /**
   * Sets the guessed outlet temperature with specified unit.
   *
   * @param guessOutTemperature Guessed outlet temperature
   * @param unit Unit of the temperature
   */
  public void setGuessOutTemperature(double guessOutTemperature, String unit) {
    this.guessOutTemperature = guessOutTemperature;
    this.guessOutTemperatureUnit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entropyProduction = 0.0;

    for (int i = 0; i < inStreams.size(); i++) {
      UUID id = UUID.randomUUID();
      inStreams.get(i).run(id);
      inStreams.get(i).getFluid().init(3);
      outStreams.get(i).run(id);
      outStreams.get(i).getFluid().init(3);
      entropyProduction += outStreams.get(i).getThermoSystem().getEntropy(unit)
          - inStreams.get(i).getThermoSystem().getEntropy(unit);
    }

    // Additional entropy production due to heat transfer
    // Assuming the first stream is the hot stream and the last is cold
    if (inStreams.size() >= 2) {
      int hotStream = 0;
      int coldStream = inStreams.size() - 1;
      double heatTransferEntropyProd =
          Math.abs(getDuty()) * (1.0 / inStreams.get(coldStream).getTemperature()
              - 1.0 / inStreams.get(hotStream).getTemperature());
      entropyProduction += heatTransferEntropyProd;
    }

    return entropyProduction;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double massBalance = 0.0;

    for (int i = 0; i < inStreams.size(); i++) {
      inStreams.get(i).run();
      inStreams.get(i).getFluid().init(3);
      outStreams.get(i).run();
      outStreams.get(i).getFluid().init(3);
      massBalance += outStreams.get(i).getThermoSystem().getFlowRate(unit)
          - inStreams.get(i).getThermoSystem().getFlowRate(unit);
    }
    return massBalance;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {
    double heatBalanceError = 0.0;
    conditionAnalysisMessage += getName() + " condition analysis started/";
    MultiStreamHeatExchanger refEx = (MultiStreamHeatExchanger) refExchanger;

    for (int i = 0; i < inStreams.size(); i++) {
      inStreams.get(i).getFluid().initProperties();
      outStreams.get(i).getFluid().initProperties();
      heatBalanceError += outStreams.get(i).getThermoSystem().getEnthalpy()
          - inStreams.get(i).getThermoSystem().getEnthalpy();

      if (Math.abs(refEx.getInStream(i).getTemperature("C")
          - getInStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      } else if (Math.abs(refEx.getOutStream(i).getTemperature("C")
          - getOutStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
        conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
      }
    }

    heatBalanceError = heatBalanceError / (outStreams.get(0).getThermoSystem().getEnthalpy()
        - inStreams.get(0).getThermoSystem().getEnthalpy()) * 100.0;

    if (Math.abs(heatBalanceError) > 10.0) {
      String error = "Heat balance not fulfilled. Error: " + heatBalanceError + " ";
      conditionAnalysisMessage += error;
    } else {
      String message = "Heat balance ok. Enthalpy balance deviation: " + heatBalanceError + " %";
      conditionAnalysisMessage += message;
    }

    conditionAnalysisMessage += getName() + "/analysis ended/";

    // Calculate thermal effectiveness and duty
    double totalDuty = 0.0;
    for (int i = 0; i < inStreams.size(); i++) {
      double dutyStream = Math.abs(outStreams.get(i).getThermoSystem().getEnthalpy()
          - inStreams.get(i).getThermoSystem().getEnthalpy());
      totalDuty += dutyStream;
    }

    double referenceDuty = Math.abs(((MultiStreamHeatExchanger) refExchanger).getDuty());
    thermalEffectiveness = ((MultiStreamHeatExchanger) refExchanger).getThermalEffectiveness()
        * (totalDuty) / referenceDuty;

    // Optionally, calculate duty balance among streams
    // This can be customized based on specific requirements
  }

  /**
   * Runs condition analysis by comparing the exchanger with itself.
   */
  public void runConditionAnalysis() {
    runConditionAnalysis(this);
  }

  /**
   * Gets the thermal effectiveness of the heat exchanger.
   *
   * @return Thermal effectiveness
   */
  public double getThermalEffectiveness() {
    return thermalEffectiveness;
  }

  /**
   * Sets the thermal effectiveness of the heat exchanger.
   *
   * @param thermalEffectiveness Thermal effectiveness to set
   */
  public void setThermalEffectiveness(double thermalEffectiveness) {
    this.thermalEffectiveness = thermalEffectiveness;
  }

  /**
   * Gets the flow arrangement of the heat exchanger.
   *
   * @return Flow arrangement
   */
  public String getFlowArrangement() {
    return flowArrangement;
  }

  /**
   * Sets the flow arrangement of the heat exchanger.
   *
   * @param flowArrangement Name of the flow arrangement
   */
  public void setFlowArrangement(String flowArrangement) {
    this.flowArrangement = flowArrangement;
  }

  /**
   * Calculates the thermal effectiveness based on NTU and capacity ratio.
   *
   * @param NTU NTU value
   * @param Cr Capacity ratio (Cmin/Cmax)
   * @return Thermal effectiveness
   */
  public double calcThermalEffectiveness(double NTU, double Cr) {
    switch (flowArrangement.toLowerCase()) {
      case "counterflow":
        if (Cr == 1.0) {
          return NTU / (1.0 + NTU);
        } else {
          return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
        }
      case "parallelflow":
        return (1.0 - Math.exp(-NTU * (1 + Cr))) / (1.0 + Cr);
      case "crossflow":
        // Simplified model for crossflow; more complex models can be implemented
        return 1 - Math.exp(-NTU * Math.pow(1 + Cr, 0.22));
      default:
        // Default to counterflow if arrangement is unrecognized
        if (Cr == 1.0) {
          return NTU / (1.0 + NTU);
        } else {
          return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
        }
    }
  }

  /**
   * Gets the hot and cold duty balance.
   *
   * @return Hot and cold duty balance
   */
  public double getHotColdDutyBalance() {
    return hotColdDutyBalance;
  }

  /**
   * Sets the hot and cold duty balance.
   *
   * @param hotColdDutyBalance Hot and cold duty balance to set
   */
  public void setHotColdDutyBalance(double hotColdDutyBalance) {
    this.hotColdDutyBalance = hotColdDutyBalance;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    // return new GsonBuilder().serializeSpecialFloatingPointValues().create()
    // .toJson(new HXResponse(this));
    return super.toJson();
  }

  /**
   * Sets whether to use delta T for the calculations.
   *
   * @param useDeltaT Boolean flag to use delta T
   */
  public void setUseDeltaT(boolean useDeltaT) {
    this.useDeltaT = useDeltaT;
  }

  /**
   * Gets the delta T value.
   *
   * @return Delta T
   */
  public double getDeltaT() {
    return deltaT;
  }

  /**
   * Sets the delta T value and enables its usage.
   *
   * @param deltaT Delta T to set
   */
  public void setDeltaT(double deltaT) {
    this.useDeltaT = true;
    this.deltaT = deltaT;
  }

  /**
   * Runs the heat exchanger simulation considering multiple streams.
   *
   * @param id Unique identifier for the run
   */
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
      // Initialize all outStreams with guessed temperatures
      for (StreamInterface outStream : outStreams) {
        SystemInterface systemOut =
            inStreams.get(outStreams.indexOf(outStream)).getThermoSystem().clone();
        outStream.setThermoSystem(systemOut);
        outStream.getThermoSystem().setTemperature(guessOutTemperature, guessOutTemperatureUnit);
        outStream.run(id);
      }
      run(id);
    } else {
      // Ensure all input streams are run
      for (StreamInterface inStream : inStreams) {
        inStream.run();
      }

      // Clone thermo systems for all out streams
      List<SystemInterface> systemsOut = new ArrayList<>();
      for (StreamInterface inStream : inStreams) {
        systemsOut.add(inStream.getThermoSystem().clone());
      }

      // Set thermo systems to out streams
      for (int i = 0; i < outStreams.size(); i++) {
        outStreams.get(i).setThermoSystem(systemsOut.get(i));
        // Set temperature based on some logic, e.g., maintaining a certain delta T
        outStreams.get(i).setTemperature(inStreams.get(i).getTemperature() + 10, "K");
        if (!outStreams.get(i).getSpecification().equals("TP")) {
          outStreams.get(i).runTPflash();
        }
        outStreams.get(i).run(id);
      }

      // Calculate enthalpy changes and capacity rates
      List<Double> deltaEnthalpies = new ArrayList<>();
      List<Double> capacities = new ArrayList<>();
      for (int i = 0; i < inStreams.size(); i++) {
        double deltaH = outStreams.get(i).getThermoSystem().getEnthalpy()
            - inStreams.get(i).getThermoSystem().getEnthalpy();
        deltaEnthalpies.add(deltaH);
        double C = Math.abs(deltaH) / Math.abs(outStreams.get(i).getThermoSystem().getTemperature()
            - inStreams.get(i).getThermoSystem().getTemperature());
        capacities.add(C);
      }

      // Determine Cmin and Cmax among all streams
      double Cmin = capacities.stream().min(Double::compare).orElse(1.0);
      double Cmax = capacities.stream().max(Double::compare).orElse(1.0);
      double Cr = Cmin / Cmax;

      // Calculate NTU and thermal effectiveness
      NTU = UAvalue / Cmin;
      thermalEffectiveness = calcThermalEffectiveness(NTU, Cr);

      // Adjust enthalpies based on effectiveness
      duty = 0.0;
      for (int i = 0; i < deltaEnthalpies.size(); i++) {
        deltaEnthalpies.set(i, thermalEffectiveness * deltaEnthalpies.get(i));
        duty += deltaEnthalpies.get(i);
      }

      // Update thermo systems based on adjusted enthalpies
      for (int i = 0; i < outStreams.size(); i++) {
        ThermodynamicOperations thermoOps =
            new ThermodynamicOperations(outStreams.get(i).getThermoSystem());
        thermoOps.PHflash(inStreams.get(i).getThermoSystem().getEnthalpy() - deltaEnthalpies.get(i),
            0);
        if (Math.abs(thermalEffectiveness - 1.0) > 1e-10) {
          thermoOps = new ThermodynamicOperations(outStreams.get(i).getThermoSystem());
          thermoOps.PHflash(
              inStreams.get(i).getThermoSystem().getEnthalpy() + deltaEnthalpies.get(i), 0);
        }
      }

      hotColdDutyBalance = 1.0; // Adjust as needed for specific applications
    }

    setCalculationIdentifier(id);
  }

  /**
   * Runs the heat exchanger simulation using a specified stream approach.
   *
   * @param id Unique identifier for the run
   */
  public void runSpecifiedStream(UUID id) {
    // Implementation similar to the two-stream case but generalized for multiple streams
    // This method needs to be defined based on specific requirements
  }

  /**
   * Runs the heat exchanger simulation using a delta T approach.
   *
   * @param id Unique identifier for the run
   */
  public void runDeltaT(UUID id) {
    // Implementation similar to the two-stream case but generalized for multiple streams
    // This method needs to be defined based on specific requirements
  }
}
