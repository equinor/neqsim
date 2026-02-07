package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of ISO 15112 - Natural gas - Energy determination.
 *
 * <p>
 * ISO 15112 specifies methods for the determination of energy (in joules) flowing through a natural
 * gas metering station. It bridges composition analysis (via ISO 6976) with volume measurement to
 * calculate energy flow.
 * </p>
 *
 * <p>
 * Key calculations:
 * </p>
 * <ul>
 * <li>Energy flow rate (MJ/h, kWh/h, BTU/h) from volume flow and calorific value</li>
 * <li>Accumulated energy (GJ, MWh) over a period</li>
 * <li>Conversion between volumetric and mass-based energy</li>
 * <li>Correction from metering conditions to standard reference conditions</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO15112 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Standard_ISO15112.class);

  /** Internal ISO 6976 calculator for calorific value. */
  private Standard_ISO6976 iso6976;

  /** Volume flow rate at metering conditions in m3/h. */
  private double volumeFlowRate = 0.0;

  /** Volume reference temperature in degrees C. */
  private double volumeRefTempC = 15.0;

  /** Energy reference temperature in degrees C. */
  private double energyRefTempC = 25.0;

  /** Metering pressure in bara. */
  private double meteringPressure = 1.01325;

  /** Metering temperature in degrees C. */
  private double meteringTemperature = 15.0;

  /** Calculated gross calorific value in MJ/m3. */
  private double grossCalorificValue = 0.0;

  /** Calculated net calorific value in MJ/m3. */
  private double netCalorificValue = 0.0;

  /** Calculated energy flow rate in MJ/h. */
  private double energyFlowRate = 0.0;

  /** Calculated standard volume flow rate in Sm3/h. */
  private double standardVolumeFlowRate = 0.0;

  /** Total accumulated energy in GJ. */
  private double accumulatedEnergy = 0.0;

  /** Accumulation period in hours. */
  private double accumulationPeriod = 1.0;

  /**
   * Constructor for Standard_ISO15112.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO15112(SystemInterface thermoSystem) {
    super("Standard_ISO15112", "Natural gas - Energy determination", thermoSystem);
    this.iso6976 = new Standard_ISO6976(thermoSystem, volumeRefTempC, energyRefTempC, "volume");
  }

  /**
   * Constructor for Standard_ISO15112 with specified conditions.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param volumeFlowRateM3h volume flow rate at metering conditions in m3/h
   * @param volumeRefTempC volume reference temperature in degrees C
   * @param energyRefTempC energy reference temperature in degrees C
   */
  public Standard_ISO15112(SystemInterface thermoSystem, double volumeFlowRateM3h,
      double volumeRefTempC, double energyRefTempC) {
    this(thermoSystem);
    this.volumeFlowRate = volumeFlowRateM3h;
    this.volumeRefTempC = volumeRefTempC;
    this.energyRefTempC = energyRefTempC;
    this.iso6976 = new Standard_ISO6976(thermoSystem, volumeRefTempC, energyRefTempC, "volume");
  }

  /**
   * Sets the volume flow rate at metering conditions.
   *
   * @param flowRate volume flow rate in m3/h
   */
  public void setVolumeFlowRate(double flowRate) {
    this.volumeFlowRate = flowRate;
  }

  /**
   * Gets the volume flow rate at metering conditions.
   *
   * @return volume flow rate in m3/h
   */
  public double getVolumeFlowRate() {
    return volumeFlowRate;
  }

  /**
   * Sets the metering conditions.
   *
   * @param temperatureC metering temperature in degrees C
   * @param pressureBara metering pressure in bara
   */
  public void setMeteringConditions(double temperatureC, double pressureBara) {
    this.meteringTemperature = temperatureC;
    this.meteringPressure = pressureBara;
  }

  /**
   * Sets the accumulation period for energy integration.
   *
   * @param hours accumulation period in hours
   */
  public void setAccumulationPeriod(double hours) {
    this.accumulationPeriod = hours;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      // Step 1: Calculate calorific values using ISO 6976
      iso6976.setReferenceState("real");
      iso6976.setReferenceType("volume");
      iso6976.calculate();

      grossCalorificValue = iso6976.getValue("SuperiorCalorificValue") / 1000.0; // kJ/m3 to MJ/m3
      netCalorificValue = iso6976.getValue("InferiorCalorificValue") / 1000.0; // kJ/m3 to MJ/m3

      // Step 2: Convert volume flow from metering to standard conditions
      // V_std = V_meter * (P_meter / P_std) * (T_std / T_meter) * (Z_std / Z_meter)
      double pStd = ThermodynamicConstantsInterface.referencePressure;
      double tStdK = volumeRefTempC + 273.15;
      double tMeterK = meteringTemperature + 273.15;
      double zStd = iso6976.getValue("CompressionFactor");
      double zMeter = zStd; // Approximation for near-atmospheric metering

      if (volumeFlowRate > 0.0) {
        standardVolumeFlowRate =
            volumeFlowRate * (meteringPressure / pStd) * (tStdK / tMeterK) * (zStd / zMeter);
      }

      // Step 3: Calculate energy flow rate (E = Hs * Vs)
      energyFlowRate = grossCalorificValue * standardVolumeFlowRate;

      // Step 4: Calculate accumulated energy
      accumulatedEnergy = energyFlowRate * accumulationPeriod / 1000.0; // MJ to GJ
    } catch (Exception ex) {
      logger.error("ISO 15112 calculation failed", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    double value = getValue(returnParameter);

    if ("energyFlowRate".equals(returnParameter)) {
      if ("kWh/h".equals(returnUnit) || "kW".equals(returnUnit)) {
        return value / 3.6; // MJ/h to kW
      }
      if ("BTU/h".equals(returnUnit)) {
        return value * 947.817; // MJ/h to BTU/h
      }
    }
    if ("accumulatedEnergy".equals(returnParameter)) {
      if ("MWh".equals(returnUnit)) {
        return value * 1000.0 / 3.6; // GJ to MWh (GJ*1000=MJ, MJ/3.6=kWh, kWh/1000=MWh)
      }
      if ("therms".equals(returnUnit)) {
        return value * 9.4781712; // GJ to therms
      }
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("GCV".equals(returnParameter) || "grossCalorificValue".equals(returnParameter)) {
      return grossCalorificValue;
    }
    if ("NCV".equals(returnParameter) || "netCalorificValue".equals(returnParameter)) {
      return netCalorificValue;
    }
    if ("energyFlowRate".equals(returnParameter)) {
      return energyFlowRate;
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      return standardVolumeFlowRate;
    }
    if ("accumulatedEnergy".equals(returnParameter)) {
      return accumulatedEnergy;
    }
    return energyFlowRate;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("GCV".equals(returnParameter) || "grossCalorificValue".equals(returnParameter)
        || "NCV".equals(returnParameter) || "netCalorificValue".equals(returnParameter)) {
      return "MJ/m3";
    }
    if ("energyFlowRate".equals(returnParameter)) {
      return "MJ/h";
    }
    if ("standardVolumeFlowRate".equals(returnParameter)) {
      return "Sm3/h";
    }
    if ("accumulatedEnergy".equals(returnParameter)) {
      return "GJ";
    }
    return "MJ/h";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return grossCalorificValue > 0.0;
  }

  /**
   * Gets the internal ISO 6976 standard used for calorific value calculation.
   *
   * @return the ISO 6976 standard instance
   */
  public Standard_ISO6976 getISO6976() {
    return iso6976;
  }
}
