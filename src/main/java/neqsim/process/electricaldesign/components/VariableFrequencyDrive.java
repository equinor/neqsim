package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model of a Variable Frequency Drive (VFD) for motor speed control.
 *
 * <p>
 * Supports sizing based on motor ratings, efficiency estimation, harmonic distortion calculation
 * per IEEE 519, and different topology types (2-level, 3-level, multi-level, AFE).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class VariableFrequencyDrive implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double ratedPowerKW;
  private double ratedCurrentA;
  private double inputVoltageV;
  private double outputVoltageV;
  private double maxOutputFrequencyHz = 60.0;
  private double minOutputFrequencyHz = 5.0;

  // === VFD characteristics ===
  private double efficiencyPercent = 97.0;
  private double inputPowerFactor = 0.95;
  private String topologyType = "2-level";
  private boolean hasActiveRectifier = false;

  // === Harmonics ===
  private double thdCurrentPercent = 35.0;
  private boolean requiresInputFilter = false;
  private String pulseConfiguration = "6-pulse";

  // === Speed range ===
  private double minSpeedPercent = 20.0;
  private double maxSpeedPercent = 100.0;
  private boolean hasFieldWeakeningRegion = false;

  // === Cooling ===
  private double heatDissipationKW;
  private String coolingMethod = "Air";

  // === Physical ===
  private double weightKg;
  private double estimatedCostUSD;
  private String enclosureRating = "IP21";

  /**
   * Size the VFD based on motor parameters.
   *
   * <p>
   * Selects VFD ratings, topology, harmonic characteristics, and cost based on the motor's rated
   * power and voltage.
   * </p>
   *
   * @param motor the motor this VFD drives
   */
  public void sizeVFD(ElectricalMotor motor) {
    // VFD rated for motor power with margin
    ratedPowerKW = motor.getRatedPowerKW();
    inputVoltageV = motor.getRatedVoltageV();
    outputVoltageV = motor.getRatedVoltageV();

    // Current rating with 10% margin
    ratedCurrentA = motor.getRatedCurrentA() * 1.10;

    // Select topology based on voltage and power
    selectTopology(ratedPowerKW, inputVoltageV);

    // Set efficiency based on topology
    setEfficiencyByTopology();

    // Calculate heat dissipation
    heatDissipationKW = ratedPowerKW * (1.0 - efficiencyPercent / 100.0);

    // Select cooling method
    if (ratedPowerKW > 500 || inputVoltageV > 3300) {
      coolingMethod = "Water";
    } else {
      coolingMethod = "Air";
    }

    // Estimate weight and cost
    weightKg = estimateWeight(ratedPowerKW, inputVoltageV);
    estimatedCostUSD = estimateCost(ratedPowerKW, inputVoltageV);

    // Set enclosure rating
    if (inputVoltageV > 1000) {
      enclosureRating = "IP42";
    } else if ("Water".equals(coolingMethod)) {
      enclosureRating = "IP54";
    }
  }

  /**
   * Select VFD topology based on power and voltage.
   *
   * @param powerKW rated power in kW
   * @param voltageV rated voltage in V
   */
  private void selectTopology(double powerKW, double voltageV) {
    if (voltageV > 3300) {
      // Medium voltage - use multi-level or 3-level
      if (powerKW > 2000) {
        topologyType = "Multi-level";
        pulseConfiguration = "AFE";
        hasActiveRectifier = true;
        thdCurrentPercent = 3.0;
        requiresInputFilter = false;
      } else {
        topologyType = "3-level";
        pulseConfiguration = "12-pulse";
        hasActiveRectifier = false;
        thdCurrentPercent = 10.0;
        requiresInputFilter = false;
      }
    } else if (powerKW > 250) {
      // High power LV - use AFE
      topologyType = "2-level";
      pulseConfiguration = "AFE";
      hasActiveRectifier = true;
      thdCurrentPercent = 5.0;
      requiresInputFilter = false;
    } else {
      // Standard LV - 6-pulse
      topologyType = "2-level";
      pulseConfiguration = "6-pulse";
      hasActiveRectifier = false;
      thdCurrentPercent = 35.0;
      requiresInputFilter = (powerKW > 100);
    }
  }

  /**
   * Set efficiency based on VFD topology.
   */
  private void setEfficiencyByTopology() {
    if (hasActiveRectifier) {
      efficiencyPercent = 96.5;
      inputPowerFactor = 0.98;
    } else if ("3-level".equals(topologyType)) {
      efficiencyPercent = 97.0;
      inputPowerFactor = 0.95;
    } else if ("Multi-level".equals(topologyType)) {
      efficiencyPercent = 97.5;
      inputPowerFactor = 0.99;
    } else {
      efficiencyPercent = 97.5;
      inputPowerFactor = 0.95;
    }
  }

  /**
   * Get electrical input power including VFD losses.
   *
   * @param motorInputKW motor electrical input power in kW
   * @return total electrical input in kW
   */
  public double getElectricalInputKW(double motorInputKW) {
    if (efficiencyPercent <= 0) {
      return motorInputKW;
    }
    return motorInputKW / (efficiencyPercent / 100.0);
  }

  /**
   * Get VFD efficiency at a given load and speed fraction.
   *
   * @param loadFraction load fraction (0-1)
   * @param speedFraction speed fraction (0-1)
   * @return efficiency in percent
   */
  public double getEfficiency(double loadFraction, double speedFraction) {
    // VFD efficiency drops at low loads/speeds
    double baseEff = efficiencyPercent;
    if (loadFraction < 0.25) {
      baseEff -= 5.0;
    } else if (loadFraction < 0.5) {
      baseEff -= 2.0;
    }
    if (speedFraction < 0.3) {
      baseEff -= 2.0;
    }
    return Math.max(85.0, baseEff);
  }

  /**
   * Estimate VFD weight.
   *
   * @param powerKW rated power in kW
   * @param voltageV rated voltage in V
   * @return weight in kg
   */
  private double estimateWeight(double powerKW, double voltageV) {
    double baseWeight;
    if (voltageV > 3300) {
      baseWeight = powerKW * 1.5 + 500.0;
    } else if (voltageV > 690) {
      baseWeight = powerKW * 1.0 + 200.0;
    } else {
      baseWeight = powerKW * 0.6 + 50.0;
    }
    return baseWeight;
  }

  /**
   * Estimate VFD cost in USD.
   *
   * @param powerKW rated power in kW
   * @param voltageV rated voltage in V
   * @return estimated cost in USD
   */
  private double estimateCost(double powerKW, double voltageV) {
    double costPerKW;
    if (voltageV > 3300) {
      costPerKW = 120.0;
    } else if (voltageV > 690) {
      costPerKW = 80.0;
    } else {
      costPerKW = 50.0;
    }
    if (hasActiveRectifier) {
      costPerKW *= 1.4;
    }
    return powerKW * costPerKW + 5000.0;
  }

  /**
   * Serialize VFD data to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert VFD data to a map.
   *
   * @return map of VFD parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("ratedPowerKW", ratedPowerKW);
    map.put("ratedCurrentA", ratedCurrentA);
    map.put("inputVoltageV", inputVoltageV);
    map.put("outputVoltageV", outputVoltageV);
    map.put("topologyType", topologyType);
    map.put("efficiencyPercent", efficiencyPercent);
    map.put("inputPowerFactor", inputPowerFactor);
    map.put("thdCurrentPercent", thdCurrentPercent);
    map.put("pulseConfiguration", pulseConfiguration);
    map.put("hasActiveRectifier", hasActiveRectifier);
    map.put("requiresInputFilter", requiresInputFilter);
    map.put("minSpeedPercent", minSpeedPercent);
    map.put("maxSpeedPercent", maxSpeedPercent);
    map.put("coolingMethod", coolingMethod);
    map.put("heatDissipationKW", heatDissipationKW);
    map.put("enclosureRating", enclosureRating);
    map.put("weightKg", weightKg);
    map.put("estimatedCostUSD", estimatedCostUSD);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get rated power in kW.
   *
   * @return rated power in kW
   */
  public double getRatedPowerKW() {
    return ratedPowerKW;
  }

  /**
   * Set rated power in kW.
   *
   * @param ratedPowerKW rated power in kW
   */
  public void setRatedPowerKW(double ratedPowerKW) {
    this.ratedPowerKW = ratedPowerKW;
  }

  /**
   * Get rated current in A.
   *
   * @return rated current in A
   */
  public double getRatedCurrentA() {
    return ratedCurrentA;
  }

  /**
   * Set rated current in A.
   *
   * @param ratedCurrentA rated current in A
   */
  public void setRatedCurrentA(double ratedCurrentA) {
    this.ratedCurrentA = ratedCurrentA;
  }

  /**
   * Get input voltage in V.
   *
   * @return input voltage in V
   */
  public double getInputVoltageV() {
    return inputVoltageV;
  }

  /**
   * Set input voltage in V.
   *
   * @param inputVoltageV input voltage in V
   */
  public void setInputVoltageV(double inputVoltageV) {
    this.inputVoltageV = inputVoltageV;
  }

  /**
   * Get output voltage in V.
   *
   * @return output voltage in V
   */
  public double getOutputVoltageV() {
    return outputVoltageV;
  }

  /**
   * Set output voltage in V.
   *
   * @param outputVoltageV output voltage in V
   */
  public void setOutputVoltageV(double outputVoltageV) {
    this.outputVoltageV = outputVoltageV;
  }

  /**
   * Get maximum output frequency in Hz.
   *
   * @return max output frequency in Hz
   */
  public double getMaxOutputFrequencyHz() {
    return maxOutputFrequencyHz;
  }

  /**
   * Set maximum output frequency in Hz.
   *
   * @param maxOutputFrequencyHz max output frequency in Hz
   */
  public void setMaxOutputFrequencyHz(double maxOutputFrequencyHz) {
    this.maxOutputFrequencyHz = maxOutputFrequencyHz;
  }

  /**
   * Get minimum output frequency in Hz.
   *
   * @return min output frequency in Hz
   */
  public double getMinOutputFrequencyHz() {
    return minOutputFrequencyHz;
  }

  /**
   * Set minimum output frequency in Hz.
   *
   * @param minOutputFrequencyHz min output frequency in Hz
   */
  public void setMinOutputFrequencyHz(double minOutputFrequencyHz) {
    this.minOutputFrequencyHz = minOutputFrequencyHz;
  }

  /**
   * Get VFD efficiency in percent.
   *
   * @return efficiency percent
   */
  public double getEfficiencyPercent() {
    return efficiencyPercent;
  }

  /**
   * Set VFD efficiency in percent.
   *
   * @param efficiencyPercent efficiency percent
   */
  public void setEfficiencyPercent(double efficiencyPercent) {
    this.efficiencyPercent = efficiencyPercent;
  }

  /**
   * Get input power factor.
   *
   * @return input power factor
   */
  public double getInputPowerFactor() {
    return inputPowerFactor;
  }

  /**
   * Set input power factor.
   *
   * @param inputPowerFactor input power factor
   */
  public void setInputPowerFactor(double inputPowerFactor) {
    this.inputPowerFactor = inputPowerFactor;
  }

  /**
   * Get the topology type.
   *
   * @return topology type
   */
  public String getTopologyType() {
    return topologyType;
  }

  /**
   * Set the topology type.
   *
   * @param topologyType topology type (2-level, 3-level, Multi-level)
   */
  public void setTopologyType(String topologyType) {
    this.topologyType = topologyType;
  }

  /**
   * Check if VFD has active front-end rectifier.
   *
   * @return true if AFE is present
   */
  public boolean isHasActiveRectifier() {
    return hasActiveRectifier;
  }

  /**
   * Set whether VFD has active front-end rectifier.
   *
   * @param hasActiveRectifier true for AFE
   */
  public void setHasActiveRectifier(boolean hasActiveRectifier) {
    this.hasActiveRectifier = hasActiveRectifier;
  }

  /**
   * Get total harmonic distortion of current in percent.
   *
   * @return THD-i in percent
   */
  public double getThdCurrentPercent() {
    return thdCurrentPercent;
  }

  /**
   * Set total harmonic distortion of current in percent.
   *
   * @param thdCurrentPercent THD-i in percent
   */
  public void setThdCurrentPercent(double thdCurrentPercent) {
    this.thdCurrentPercent = thdCurrentPercent;
  }

  /**
   * Check if an input filter is required.
   *
   * @return true if input filter is required
   */
  public boolean isRequiresInputFilter() {
    return requiresInputFilter;
  }

  /**
   * Set whether an input filter is required.
   *
   * @param requiresInputFilter true if input filter is required
   */
  public void setRequiresInputFilter(boolean requiresInputFilter) {
    this.requiresInputFilter = requiresInputFilter;
  }

  /**
   * Get pulse configuration.
   *
   * @return pulse configuration (6-pulse, 12-pulse, 18-pulse, AFE)
   */
  public String getPulseConfiguration() {
    return pulseConfiguration;
  }

  /**
   * Set pulse configuration.
   *
   * @param pulseConfiguration pulse configuration
   */
  public void setPulseConfiguration(String pulseConfiguration) {
    this.pulseConfiguration = pulseConfiguration;
  }

  /**
   * Get heat dissipation in kW.
   *
   * @return heat dissipation in kW
   */
  public double getHeatDissipationKW() {
    return heatDissipationKW;
  }

  /**
   * Get cooling method.
   *
   * @return cooling method (Air, Water, Oil)
   */
  public String getCoolingMethod() {
    return coolingMethod;
  }

  /**
   * Set cooling method.
   *
   * @param coolingMethod cooling method
   */
  public void setCoolingMethod(String coolingMethod) {
    this.coolingMethod = coolingMethod;
  }

  /**
   * Get weight in kg.
   *
   * @return weight in kg
   */
  public double getWeightKg() {
    return weightKg;
  }

  /**
   * Get estimated cost in USD.
   *
   * @return estimated cost in USD
   */
  public double getEstimatedCostUSD() {
    return estimatedCostUSD;
  }

  /**
   * Get enclosure rating.
   *
   * @return IP enclosure rating
   */
  public String getEnclosureRating() {
    return enclosureRating;
  }

  /**
   * Set enclosure rating.
   *
   * @param enclosureRating IP enclosure rating
   */
  public void setEnclosureRating(String enclosureRating) {
    this.enclosureRating = enclosureRating;
  }

  /**
   * Get minimum speed as percent of rated.
   *
   * @return min speed percent
   */
  public double getMinSpeedPercent() {
    return minSpeedPercent;
  }

  /**
   * Set minimum speed as percent of rated.
   *
   * @param minSpeedPercent min speed percent
   */
  public void setMinSpeedPercent(double minSpeedPercent) {
    this.minSpeedPercent = minSpeedPercent;
  }

  /**
   * Get maximum speed as percent of rated.
   *
   * @return max speed percent
   */
  public double getMaxSpeedPercent() {
    return maxSpeedPercent;
  }

  /**
   * Set maximum speed as percent of rated.
   *
   * @param maxSpeedPercent max speed percent
   */
  public void setMaxSpeedPercent(double maxSpeedPercent) {
    this.maxSpeedPercent = maxSpeedPercent;
  }
}
