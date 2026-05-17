package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model of an electric motor for process equipment.
 *
 * <p>
 * Supports IEC and NEMA sizing standards, efficiency classes IE1-IE4 per IEC 60034-30-1, hazardous
 * area Ex ratings, and part-load performance estimation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ElectricalMotor implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Standard IEC motor rated power steps in kW. Motors are selected as the next size up from the
   * required power.
   */
  private static final double[] IEC_STANDARD_POWERS_KW = {0.37, 0.55, 0.75, 1.1, 1.5, 2.2, 3.0, 4.0,
      5.5, 7.5, 11.0, 15.0, 18.5, 22.0, 30.0, 37.0, 45.0, 55.0, 75.0, 90.0, 110.0, 132.0, 160.0,
      200.0, 250.0, 315.0, 355.0, 400.0, 450.0, 500.0, 560.0, 630.0, 710.0, 800.0, 900.0, 1000.0,
      1120.0, 1250.0, 1400.0, 1600.0, 1800.0, 2000.0, 2240.0, 2500.0, 2800.0, 3150.0, 3550.0,
      4000.0, 4500.0, 5000.0, 5600.0, 6300.0, 7100.0, 8000.0, 9000.0, 10000.0};

  // === Nameplate data ===
  private double ratedPowerKW;
  private double ratedVoltageV = 400.0;
  private double ratedCurrentA;
  private double ratedSpeedRPM;
  private int poles = 4;
  private double frequencyHz = 50.0;
  private double efficiencyPercent = 95.0;
  private double powerFactorFL = 0.86;
  private String efficiencyClass = "IE3";

  // === Motor sizing ===
  private String frameSize = "";
  private String enclosureType = "TEFC";
  private String insulationClass = "F";
  private String dutyType = "S1";
  private double serviceFactor = 1.0;

  // === Starting characteristics ===
  private double lockedRotorCurrentMultiplier = 7.0;
  private String startingMethod = "DOL";
  private double startingTorquePercent = 150.0;

  // === Hazardous area ===
  private String exProtection = "";
  private String temperatureClass = "T3";
  private String gasGroup = "IIA";

  // === Weight and cost ===
  private double weightKg;
  private double estimatedCostUSD;

  /**
   * Size the motor based on required shaft power.
   *
   * <p>
   * Selects the next standard IEC motor size above the required power multiplied by the sizing
   * margin, and estimates efficiency, power factor, weight, and cost.
   * </p>
   *
   * @param shaftPowerKW required shaft power in kW
   * @param margin sizing margin (e.g. 1.10 for 10% margin)
   * @param standard motor standard ("IEC" or "NEMA")
   */
  public void sizeMotor(double shaftPowerKW, double margin, String standard) {
    double requiredPower = shaftPowerKW * margin;

    // Select next standard motor size
    ratedPowerKW = selectStandardPower(requiredPower);

    // Estimate synchronous speed
    double synchronousSpeed = (120.0 * frequencyHz) / poles;
    ratedSpeedRPM = synchronousSpeed * (1.0 - estimateSlip(ratedPowerKW));

    // Estimate efficiency based on power and efficiency class
    efficiencyPercent = estimateEfficiency(ratedPowerKW, poles, efficiencyClass);

    // Estimate power factor at full load
    powerFactorFL = estimatePowerFactor(ratedPowerKW);

    // Calculate rated current
    if (ratedVoltageV > 0 && efficiencyPercent > 0 && powerFactorFL > 0) {
      ratedCurrentA = (ratedPowerKW * 1000.0)
          / (Math.sqrt(3) * ratedVoltageV * (efficiencyPercent / 100.0) * powerFactorFL);
    }

    // Estimate frame size
    frameSize = estimateFrameSize(ratedPowerKW, poles, standard);

    // Estimate weight and cost
    weightKg = estimateWeight(ratedPowerKW);
    estimatedCostUSD = estimateCost(ratedPowerKW);

    // Set starting characteristics
    if (ratedPowerKW <= 7.5) {
      lockedRotorCurrentMultiplier = 6.0;
      startingTorquePercent = 200.0;
    } else if (ratedPowerKW <= 75) {
      lockedRotorCurrentMultiplier = 6.5;
      startingTorquePercent = 170.0;
    } else if (ratedPowerKW <= 500) {
      lockedRotorCurrentMultiplier = 7.0;
      startingTorquePercent = 150.0;
    } else {
      lockedRotorCurrentMultiplier = 6.5;
      startingTorquePercent = 120.0;
    }
  }

  /**
   * Select the next standard IEC motor power rating above the required power.
   *
   * @param requiredPowerKW required power in kW
   * @return next standard motor power rating in kW
   */
  private double selectStandardPower(double requiredPowerKW) {
    for (double stdPower : IEC_STANDARD_POWERS_KW) {
      if (stdPower >= requiredPowerKW) {
        return stdPower;
      }
    }
    // Above largest standard size - round up
    return Math.ceil(requiredPowerKW / 100.0) * 100.0;
  }

  /**
   * Estimate motor slip based on power rating.
   *
   * @param powerKW motor power in kW
   * @return estimated slip fraction
   */
  private double estimateSlip(double powerKW) {
    if (powerKW <= 7.5) {
      return 0.05;
    } else if (powerKW <= 75) {
      return 0.03;
    } else if (powerKW <= 375) {
      return 0.02;
    } else {
      return 0.015;
    }
  }

  /**
   * Estimate motor efficiency based on power, poles, and efficiency class.
   *
   * <p>
   * Based on IEC 60034-30-1 minimum efficiency values for 4-pole machines at 50 Hz.
   * </p>
   *
   * @param powerKW motor power in kW
   * @param motorPoles number of poles
   * @param iecClass efficiency class (IE1-IE4)
   * @return estimated efficiency in percent
   */
  private double estimateEfficiency(double powerKW, int motorPoles, String iecClass) {
    // Base efficiency for IE3, 4-pole (IEC 60034-30-1 approximate)
    double baseEfficiency;
    if (powerKW <= 1.1) {
      baseEfficiency = 82.5;
    } else if (powerKW <= 4.0) {
      baseEfficiency = 87.7;
    } else if (powerKW <= 11.0) {
      baseEfficiency = 91.0;
    } else if (powerKW <= 37.0) {
      baseEfficiency = 93.3;
    } else if (powerKW <= 110.0) {
      baseEfficiency = 95.0;
    } else if (powerKW <= 375.0) {
      baseEfficiency = 96.0;
    } else {
      baseEfficiency = 96.5;
    }

    // Adjust for efficiency class
    double classOffset = 0.0;
    if ("IE1".equals(iecClass)) {
      classOffset = -3.0;
    } else if ("IE2".equals(iecClass)) {
      classOffset = -1.5;
    } else if ("IE3".equals(iecClass)) {
      classOffset = 0.0;
    } else if ("IE4".equals(iecClass)) {
      classOffset = 1.0;
    }

    // Adjust for pole count (2-pole slightly lower, 6/8-pole lower)
    double poleOffset = 0.0;
    if (motorPoles == 2) {
      poleOffset = -0.5;
    } else if (motorPoles == 6) {
      poleOffset = -1.0;
    } else if (motorPoles == 8) {
      poleOffset = -1.5;
    }

    return Math.min(99.0, baseEfficiency + classOffset + poleOffset);
  }

  /**
   * Estimate power factor at full load based on motor size.
   *
   * @param powerKW motor power in kW
   * @return estimated power factor
   */
  private double estimatePowerFactor(double powerKW) {
    if (powerKW <= 4.0) {
      return 0.78;
    } else if (powerKW <= 15.0) {
      return 0.83;
    } else if (powerKW <= 55.0) {
      return 0.86;
    } else if (powerKW <= 200.0) {
      return 0.88;
    } else if (powerKW <= 1000.0) {
      return 0.90;
    } else {
      return 0.91;
    }
  }

  /**
   * Estimate IEC frame size designation.
   *
   * @param powerKW motor power in kW
   * @param motorPoles number of poles
   * @param standard "IEC" or "NEMA"
   * @return frame size string
   */
  private String estimateFrameSize(double powerKW, int motorPoles, String standard) {
    if ("NEMA".equals(standard)) {
      if (powerKW <= 1.1) {
        return "143T";
      } else if (powerKW <= 3.7) {
        return "182T";
      } else if (powerKW <= 7.5) {
        return "213T";
      } else if (powerKW <= 22.0) {
        return "256T";
      } else if (powerKW <= 55.0) {
        return "326T";
      } else if (powerKW <= 150.0) {
        return "405T";
      } else {
        return "449T";
      }
    }
    // IEC frame sizes
    if (powerKW <= 0.75) {
      return "80";
    } else if (powerKW <= 1.5) {
      return "90";
    } else if (powerKW <= 3.0) {
      return "100";
    } else if (powerKW <= 5.5) {
      return "112";
    } else if (powerKW <= 11.0) {
      return "160";
    } else if (powerKW <= 22.0) {
      return "180";
    } else if (powerKW <= 45.0) {
      return "225";
    } else if (powerKW <= 90.0) {
      return "280";
    } else if (powerKW <= 200.0) {
      return "315";
    } else if (powerKW <= 500.0) {
      return "355";
    } else {
      return "450";
    }
  }

  /**
   * Estimate motor weight in kg.
   *
   * @param powerKW motor power in kW
   * @return estimated weight in kg
   */
  private double estimateWeight(double powerKW) {
    // Approximate: 2-5 kg/kW for smaller motors, decreasing for larger
    if (powerKW <= 10.0) {
      return powerKW * 5.0 + 10.0;
    } else if (powerKW <= 100.0) {
      return powerKW * 3.5 + 25.0;
    } else if (powerKW <= 1000.0) {
      return powerKW * 2.5 + 125.0;
    } else {
      return powerKW * 1.8 + 825.0;
    }
  }

  /**
   * Estimate motor cost in USD.
   *
   * @param powerKW motor power in kW
   * @return estimated cost in USD
   */
  private double estimateCost(double powerKW) {
    // Approximate cost per kW decreases with size
    if (powerKW <= 10.0) {
      return powerKW * 150.0 + 500.0;
    } else if (powerKW <= 100.0) {
      return powerKW * 80.0 + 1200.0;
    } else if (powerKW <= 1000.0) {
      return powerKW * 55.0 + 3700.0;
    } else {
      return powerKW * 40.0 + 18700.0;
    }
  }

  /**
   * Get efficiency at a given load fraction.
   *
   * <p>
   * Motors typically have peak efficiency around 75% load.
   * </p>
   *
   * @param loadFraction load fraction (0-1)
   * @return efficiency in percent at the given load
   */
  public double getEfficiencyAtLoad(double loadFraction) {
    if (loadFraction <= 0.0) {
      return 0.0;
    }
    // Peak efficiency at ~75% load; drops at very low loads
    double normalizedLoad = Math.min(loadFraction, 1.5);
    double efficiencyDrop = 0.0;
    if (normalizedLoad < 0.25) {
      efficiencyDrop = (0.25 - normalizedLoad) * 20.0;
    } else if (normalizedLoad > 1.0) {
      efficiencyDrop = (normalizedLoad - 1.0) * 5.0;
    }
    return Math.max(0.0, efficiencyPercent - efficiencyDrop);
  }

  /**
   * Get power factor at a given load fraction.
   *
   * @param loadFraction load fraction (0-1)
   * @return power factor at the given load
   */
  public double getPowerFactorAtLoad(double loadFraction) {
    if (loadFraction <= 0.0) {
      return 0.3;
    }
    // Power factor drops significantly at low loads
    double reduction = 0.0;
    if (loadFraction < 0.25) {
      reduction = 0.25;
    } else if (loadFraction < 0.50) {
      reduction = 0.12;
    } else if (loadFraction < 0.75) {
      reduction = 0.05;
    }
    return Math.max(0.3, powerFactorFL - reduction);
  }

  /**
   * Serialize motor data to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert motor data to a map.
   *
   * @return map of motor parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("ratedPowerKW", ratedPowerKW);
    map.put("ratedVoltageV", ratedVoltageV);
    map.put("ratedCurrentA", ratedCurrentA);
    map.put("ratedSpeedRPM", ratedSpeedRPM);
    map.put("poles", poles);
    map.put("frequencyHz", frequencyHz);
    map.put("efficiencyPercent", efficiencyPercent);
    map.put("efficiencyClass", efficiencyClass);
    map.put("powerFactorFL", powerFactorFL);
    map.put("frameSize", frameSize);
    map.put("enclosureType", enclosureType);
    map.put("insulationClass", insulationClass);
    map.put("dutyType", dutyType);
    map.put("serviceFactor", serviceFactor);
    map.put("startingMethod", startingMethod);
    map.put("lockedRotorCurrentMultiplier", lockedRotorCurrentMultiplier);
    map.put("startingTorquePercent", startingTorquePercent);
    if (exProtection != null && !exProtection.trim().isEmpty()) {
      map.put("exProtection", exProtection);
      map.put("temperatureClass", temperatureClass);
      map.put("gasGroup", gasGroup);
    }
    map.put("weightKg", weightKg);
    map.put("estimatedCostUSD", estimatedCostUSD);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get the rated power in kW.
   *
   * @return rated power in kW
   */
  public double getRatedPowerKW() {
    return ratedPowerKW;
  }

  /**
   * Set the rated power in kW.
   *
   * @param ratedPowerKW rated power in kW
   */
  public void setRatedPowerKW(double ratedPowerKW) {
    this.ratedPowerKW = ratedPowerKW;
  }

  /**
   * Get the rated voltage in volts.
   *
   * @return rated voltage in V
   */
  public double getRatedVoltageV() {
    return ratedVoltageV;
  }

  /**
   * Set the rated voltage in volts.
   *
   * @param ratedVoltageV rated voltage in V
   */
  public void setRatedVoltageV(double ratedVoltageV) {
    this.ratedVoltageV = ratedVoltageV;
  }

  /**
   * Get rated current in amperes.
   *
   * @return rated current in A
   */
  public double getRatedCurrentA() {
    return ratedCurrentA;
  }

  /**
   * Set rated current in amperes.
   *
   * @param ratedCurrentA rated current in A
   */
  public void setRatedCurrentA(double ratedCurrentA) {
    this.ratedCurrentA = ratedCurrentA;
  }

  /**
   * Get rated speed in RPM.
   *
   * @return rated speed in RPM
   */
  public double getRatedSpeedRPM() {
    return ratedSpeedRPM;
  }

  /**
   * Set rated speed in RPM.
   *
   * @param ratedSpeedRPM rated speed in RPM
   */
  public void setRatedSpeedRPM(double ratedSpeedRPM) {
    this.ratedSpeedRPM = ratedSpeedRPM;
  }

  /**
   * Get number of poles.
   *
   * @return number of poles (2, 4, 6, 8)
   */
  public int getPoles() {
    return poles;
  }

  /**
   * Set number of poles.
   *
   * @param poles number of poles (2, 4, 6, 8)
   */
  public void setPoles(int poles) {
    this.poles = poles;
  }

  /**
   * Get the frequency in Hz.
   *
   * @return frequency in Hz
   */
  public double getFrequencyHz() {
    return frequencyHz;
  }

  /**
   * Set the frequency in Hz.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Get full-load efficiency in percent.
   *
   * @return efficiency in percent
   */
  public double getEfficiencyPercent() {
    return efficiencyPercent;
  }

  /**
   * Set full-load efficiency in percent.
   *
   * @param efficiencyPercent efficiency in percent
   */
  public void setEfficiencyPercent(double efficiencyPercent) {
    this.efficiencyPercent = efficiencyPercent;
  }

  /**
   * Get full-load power factor.
   *
   * @return power factor at full load
   */
  public double getPowerFactorFL() {
    return powerFactorFL;
  }

  /**
   * Set full-load power factor.
   *
   * @param powerFactorFL power factor at full load
   */
  public void setPowerFactorFL(double powerFactorFL) {
    this.powerFactorFL = powerFactorFL;
  }

  /**
   * Get the efficiency class per IEC 60034-30-1.
   *
   * @return efficiency class (IE1, IE2, IE3, IE4)
   */
  public String getEfficiencyClass() {
    return efficiencyClass;
  }

  /**
   * Set the efficiency class per IEC 60034-30-1.
   *
   * @param efficiencyClass efficiency class (IE1, IE2, IE3, IE4)
   */
  public void setEfficiencyClass(String efficiencyClass) {
    this.efficiencyClass = efficiencyClass;
  }

  /**
   * Get IEC/NEMA frame size.
   *
   * @return frame size
   */
  public String getFrameSize() {
    return frameSize;
  }

  /**
   * Set IEC/NEMA frame size.
   *
   * @param frameSize frame size
   */
  public void setFrameSize(String frameSize) {
    this.frameSize = frameSize;
  }

  /**
   * Get the enclosure type.
   *
   * @return enclosure type (TEFC, TENV, ODP, etc.)
   */
  public String getEnclosureType() {
    return enclosureType;
  }

  /**
   * Set the enclosure type.
   *
   * @param enclosureType enclosure type
   */
  public void setEnclosureType(String enclosureType) {
    this.enclosureType = enclosureType;
  }

  /**
   * Get the insulation class.
   *
   * @return insulation class (B, F, H)
   */
  public String getInsulationClass() {
    return insulationClass;
  }

  /**
   * Set the insulation class.
   *
   * @param insulationClass insulation class (B, F, H)
   */
  public void setInsulationClass(String insulationClass) {
    this.insulationClass = insulationClass;
  }

  /**
   * Get the duty type per IEC.
   *
   * @return duty type (S1, S2, S3, etc.)
   */
  public String getDutyType() {
    return dutyType;
  }

  /**
   * Set the duty type per IEC.
   *
   * @param dutyType duty type (S1, S2, S3, etc.)
   */
  public void setDutyType(String dutyType) {
    this.dutyType = dutyType;
  }

  /**
   * Get the service factor.
   *
   * @return service factor (1.0 for IEC, 1.15 for NEMA)
   */
  public double getServiceFactor() {
    return serviceFactor;
  }

  /**
   * Set the service factor.
   *
   * @param serviceFactor service factor
   */
  public void setServiceFactor(double serviceFactor) {
    this.serviceFactor = serviceFactor;
  }

  /**
   * Get locked rotor current multiplier.
   *
   * @return multiplier relative to full-load current
   */
  public double getLockedRotorCurrentMultiplier() {
    return lockedRotorCurrentMultiplier;
  }

  /**
   * Set locked rotor current multiplier.
   *
   * @param lockedRotorCurrentMultiplier multiplier relative to full-load current
   */
  public void setLockedRotorCurrentMultiplier(double lockedRotorCurrentMultiplier) {
    this.lockedRotorCurrentMultiplier = lockedRotorCurrentMultiplier;
  }

  /**
   * Get the starting method.
   *
   * @return starting method (DOL, Star-Delta, Soft-Start, VFD)
   */
  public String getStartingMethod() {
    return startingMethod;
  }

  /**
   * Set the starting method.
   *
   * @param startingMethod starting method
   */
  public void setStartingMethod(String startingMethod) {
    this.startingMethod = startingMethod;
  }

  /**
   * Get starting torque as percent of full-load torque.
   *
   * @return starting torque percent
   */
  public double getStartingTorquePercent() {
    return startingTorquePercent;
  }

  /**
   * Set starting torque as percent of full-load torque.
   *
   * @param startingTorquePercent starting torque percent
   */
  public void setStartingTorquePercent(double startingTorquePercent) {
    this.startingTorquePercent = startingTorquePercent;
  }

  /**
   * Get Ex protection type.
   *
   * @return Ex protection designation
   */
  public String getExProtection() {
    return exProtection;
  }

  /**
   * Set Ex protection type.
   *
   * @param exProtection Ex protection designation (e.g. "Ex d IIB T3")
   */
  public void setExProtection(String exProtection) {
    this.exProtection = exProtection;
  }

  /**
   * Get temperature class.
   *
   * @return temperature class (T1-T6)
   */
  public String getTemperatureClass() {
    return temperatureClass;
  }

  /**
   * Set temperature class.
   *
   * @param temperatureClass temperature class (T1-T6)
   */
  public void setTemperatureClass(String temperatureClass) {
    this.temperatureClass = temperatureClass;
  }

  /**
   * Get gas group.
   *
   * @return gas group (IIA, IIB, IIC)
   */
  public String getGasGroup() {
    return gasGroup;
  }

  /**
   * Set gas group.
   *
   * @param gasGroup gas group (IIA, IIB, IIC)
   */
  public void setGasGroup(String gasGroup) {
    this.gasGroup = gasGroup;
  }

  /**
   * Get motor weight in kg.
   *
   * @return weight in kg
   */
  public double getWeightKg() {
    return weightKg;
  }

  /**
   * Set motor weight in kg.
   *
   * @param weightKg weight in kg
   */
  public void setWeightKg(double weightKg) {
    this.weightKg = weightKg;
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
   * Set estimated cost in USD.
   *
   * @param estimatedCostUSD estimated cost in USD
   */
  public void setEstimatedCostUSD(double estimatedCostUSD) {
    this.estimatedCostUSD = estimatedCostUSD;
  }
}
