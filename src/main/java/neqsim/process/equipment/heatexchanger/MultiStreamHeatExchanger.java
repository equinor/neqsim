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
  private double temperatureApproach = 0.0;
  private boolean UAvalueIsSet = false;

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
  int MAX_ITERATIONS = 100;
  int iterations = 0;

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
    UAvalueIsSet = true;
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
    if (firstTime) {
      firstTime = false;

      // 1. Identify the hottest and coldest inlet streams
      double hottestTemperature = Double.NEGATIVE_INFINITY;
      double coldestTemperature = Double.POSITIVE_INFINITY;
      int hottestIndex = -1;
      int coldestIndex = -1;

      for (int i = 0; i < inStreams.size(); i++) {
        StreamInterface inStream = inStreams.get(i);
        // Ensure the inlet stream is run to get the latest temperature
        inStream.run();
        double currentTemp = inStream.getThermoSystem().getTemperature("K");

        if (currentTemp > hottestTemperature) {
          hottestTemperature = currentTemp;
          hottestIndex = i;
        }

        if (currentTemp < coldestTemperature) {
          coldestTemperature = currentTemp;
          coldestIndex = i;
        }
      }

      // Check if valid indices were found
      if (hottestIndex == -1 || coldestIndex == -1) {
        throw new IllegalStateException("Unable to determine hottest or coldest inlet streams.");
      }

      // 2. Set the outlet temperatures accordingly
      for (int i = 0; i < outStreams.size(); i++) {
        StreamInterface outStream = outStreams.get(i);
        SystemInterface systemOut = inStreams.get(i).getThermoSystem().clone();
        outStream.setThermoSystem(systemOut);

        if (i == hottestIndex) {
          // Set the outlet temperature of the hottest inlet stream to the coldest inlet temperature
          outStream.getThermoSystem().setTemperature(coldestTemperature + temperatureApproach, "K");
        } else if (i == coldestIndex) {
          // Set the outlet temperature of the coldest inlet stream to the hottest inlet temperature
          outStream.getThermoSystem().setTemperature(hottestTemperature - temperatureApproach, "K");
        } else {
          // Set the outlet temperature of other streams to the hottest inlet temperature
          outStream.getThermoSystem().setTemperature(hottestTemperature - temperatureApproach, "K");
        }

        // Run the outlet stream with the given ID
        outStream.run(id);
      }

      // Finalize the setup
      run();
      return;
    }

    else {
      // Run all input and output streams to ensure they are up-to-date
      for (StreamInterface inStream : inStreams) {
        inStream.run(id);
      }
      for (StreamInterface outStream : outStreams) {
        outStream.run(id);
      }

      // Identify heated and cooled streams
      List<Integer> heatedStreamIndices = new ArrayList<>();
      List<Integer> cooledStreamIndices = new ArrayList<>();
      double totalHeatGained = 0.0; // Total Q for heated streams
      double totalHeatLost = 0.0; // Total Q for cooled streams

      for (int i = 0; i < inStreams.size(); i++) {
        double enthalpyIn = inStreams.get(i).getThermoSystem().getEnthalpy();
        double enthalpyOut = outStreams.get(i).getThermoSystem().getEnthalpy();
        double deltaH = enthalpyOut - enthalpyIn;

        if (deltaH > 0) {
          // Stream is being heated
          heatedStreamIndices.add(i);
          totalHeatGained += deltaH;
        } else if (deltaH < 0) {
          // Stream is being cooled
          cooledStreamIndices.add(i);
          totalHeatLost += Math.abs(deltaH);
        }
        // Streams with deltaH == 0 are neither heated nor cooled
      }

      logger.debug(": Total Heat Gained = " + totalHeatGained + " J");
      logger.debug(": Total Heat Lost = " + totalHeatLost + " J");

      // Determine the limiting side
      double limitingHeat;
      boolean heatingIsLimiting;

      if (totalHeatGained < totalHeatLost) {
        limitingHeat = totalHeatGained;
        heatingIsLimiting = true;
        logger.debug("Limiting side: Heating");
      } else {
        limitingHeat = totalHeatLost;
        heatingIsLimiting = false;
        logger.debug("Limiting side: Cooling");
      }

      // Calculate scaling factors for each side
      double scalingFactor = 1.0;

      if (heatingIsLimiting) {
        // Scale down the heat lost by cooled streams
        scalingFactor = limitingHeat / totalHeatLost;
        logger.debug("Scaling factor for cooled streams: " + scalingFactor);
      } else {
        // Scale down the heat gained by heated streams
        scalingFactor = limitingHeat / totalHeatGained;
        logger.debug("Scaling factor for heated streams: " + scalingFactor);
      }

      // Apply scaling factors to adjust outlet enthalpies
      double maxTemperatureChange = 0.0;

      for (int i : cooledStreamIndices) {
        StreamInterface inStream = inStreams.get(i);
        StreamInterface outStream = outStreams.get(i);

        double enthalpyIn = inStream.getThermoSystem().getEnthalpy();
        double targetDeltaH =
            -(outStream.getThermoSystem().getEnthalpy() - enthalpyIn) * scalingFactor;

        // Adjust the outlet enthalpy
        double adjustedEnthalpyOut = enthalpyIn - (Math.abs(targetDeltaH));
        ThermodynamicOperations ops = new ThermodynamicOperations(outStream.getThermoSystem());
        ops.PHflash(adjustedEnthalpyOut);

        // Calculate temperature change for convergence check
        double oldTemp = outStream.getThermoSystem().getTemperature("K");
        outStream.run(id); // Re-run to update temperature based on adjusted enthalpy
        double newTemp = outStream.getThermoSystem().getTemperature("K");
        double tempChange = Math.abs(newTemp - oldTemp);
        if (tempChange > maxTemperatureChange) {
          maxTemperatureChange = tempChange;
        }

        logger.debug("Adjusted cooled stream " + i + ": ΔH = " + targetDeltaH);
      }

      scalingFactor = 1.0;
      for (int i : heatedStreamIndices) {
        StreamInterface inStream = inStreams.get(i);
        StreamInterface outStream = outStreams.get(i);

        double enthalpyIn = inStream.getThermoSystem().getEnthalpy();
        double targetDeltaH =
            (outStream.getThermoSystem().getEnthalpy() - enthalpyIn) * scalingFactor;

        // Adjust the outlet enthalpy
        double adjustedEnthalpyOut = enthalpyIn + (Math.abs(targetDeltaH));
        ThermodynamicOperations ops = new ThermodynamicOperations(outStream.getThermoSystem());
        ops.PHflash(adjustedEnthalpyOut);

        // Calculate temperature change for convergence check
        double oldTemp = outStream.getThermoSystem().getTemperature("K");
        outStream.run(id); // Re-run to update temperature based on adjusted enthalpy
        double newTemp = outStream.getThermoSystem().getTemperature("K");
        double tempChange = Math.abs(newTemp - oldTemp);
        if (tempChange > maxTemperatureChange) {
          maxTemperatureChange = tempChange;
        }

        logger.debug("Adjusted heated stream " + i + ": ΔH = " + targetDeltaH);
      }

      // ----------------------- LMTD and UA Calculations -----------------------

      // Re-identify the hottest and coldest inlet streams after adjustment
      double adjustedHottestTemp = Double.NEGATIVE_INFINITY;
      double adjustedColdestTemp = Double.POSITIVE_INFINITY;
      int adjustedHottestIndex = -1;
      int adjustedColdestIndex = -1;

      for (int i = 0; i < inStreams.size(); i++) {
        StreamInterface inStream = inStreams.get(i);
        double currentTemp = inStream.getThermoSystem().getTemperature("K");

        if (currentTemp > adjustedHottestTemp) {
          adjustedHottestTemp = currentTemp;
          adjustedHottestIndex = i;
        }

        if (currentTemp < adjustedColdestTemp) {
          adjustedColdestTemp = currentTemp;
          adjustedColdestIndex = i;
        }
      }

      // Ensure valid indices
      if (adjustedHottestIndex == -1 || adjustedColdestIndex == -1) {
        throw new IllegalStateException(
            "Unable to determine adjusted hottest or coldest inlet streams.");
      }

      // Outlet temperatures after adjustment
      double hotInletTemp = adjustedHottestTemp;
      double coldInletTemp = adjustedColdestTemp;

      StreamInterface hotOutletStream = outStreams.get(adjustedHottestIndex);
      double hotOutletTemp = hotOutletStream.getThermoSystem().getTemperature("K");

      StreamInterface coldOutletStream = outStreams.get(adjustedColdestIndex);
      double coldOutletTemp = coldOutletStream.getThermoSystem().getTemperature("K");

      // Calculate temperature differences
      double deltaT1 = hotInletTemp - coldOutletTemp; // Hot inlet - Cold outlet
      double deltaT2 = hotOutletTemp - coldInletTemp; // Hot outlet - Cold inlet

      // Validate temperature differences
      if (deltaT1 <= 0 || deltaT2 <= 0) {
        throw new IllegalStateException("Invalid temperature differences for LMTD calculation.");
      }

      // Calculate LMTD
      double LMTD;
      if (deltaT1 == deltaT2) {
        // Avoid division by zero in logarithm
        LMTD = deltaT1;
      } else {
        LMTD = (deltaT1 - deltaT2) / Math.log(deltaT1 / deltaT2);
      }

      // Total heat transfer rate (assuming energy balance is achieved)
      double totalQ = heatingIsLimiting ? totalHeatGained : totalHeatLost;

      // Calculate UA
      double UA = totalQ / LMTD;
      // setUAvalue(UA);
      logger.info("Overall LMTD: " + LMTD + " K");
      logger.info("Overall UA: " + UA + " W/K");

      if (UAvalueIsSet && Math.abs((UA - getUAvalue()) / getUAvalue()) > 0.001
          && iterations < MAX_ITERATIONS) {
        iterations++;
        setTemperatureApproach(getTemperatureApproach() * UA / getUAvalue());
        firstTime = true;
        run(id);
        return;
      }
      // Log the results
      logger.info("Overall LMTD: " + LMTD + " K");
      logger.info("Overall UA: " + UA + " W/K");
      logger.info("iterations: " + iterations);
      iterations = 0;
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


  public double getTemperatureApproach() {
    return temperatureApproach;
  }

  public void setTemperatureApproach(double temperatureApproach) {
    this.temperatureApproach = temperatureApproach;
  }
}
