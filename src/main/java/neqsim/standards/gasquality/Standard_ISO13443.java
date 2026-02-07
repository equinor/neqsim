package neqsim.standards.gasquality;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Implementation of ISO 13443 - Natural gas - Standard reference conditions.
 *
 * <p>
 * ISO 13443 defines the standard reference conditions for natural gas measurements:
 * </p>
 * <ul>
 * <li>Metering reference condition: 15 degrees C (288.15 K) and 101.325 kPa</li>
 * <li>Combustion reference condition: 25 degrees C (298.15 K) for enthalpy of combustion of
 * components, 15 degrees C (288.15 K) for volume of the gas</li>
 * </ul>
 *
 * <p>
 * The standard also allows for alternate reference conditions used in various countries:
 * </p>
 * <ul>
 * <li>0 degrees C (273.15 K) - Normal conditions (used in some European countries)</li>
 * <li>60 degrees F (15.556 degrees C, 288.706 K) and 14.696 psia - US/Canada</li>
 * <li>20 degrees C (293.15 K) - Some industrial applications</li>
 * </ul>
 *
 * <p>
 * This class provides utilities for converting gas volumes and properties between different
 * reference conditions, including the ideal-gas and real-gas volume conversions.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Standard_ISO13443 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Universal gas constant in J/(mol*K). */
  private static final double R = 8.314510;

  /** Standard pressure in kPa (101.325 kPa = 1 atm). */
  private static final double P_STD_KPA = 101.325;

  /** ISO 13443 metering temperature: 15C. */
  public static final double T_METER_15C = 288.15;

  /** Normal conditions temperature: 0C. */
  public static final double T_NORMAL_0C = 273.15;

  /** US/Canada standard temperature: 60F. */
  public static final double T_US_60F = 288.706;

  /** Temperature at 20C. */
  public static final double T_20C = 293.15;

  /** ISO 13443 combustion reference temperature: 25C. */
  public static final double T_COMBUSTION_25C = 298.15;

  /** Reference temperature 1 in K (from condition). */
  private double refTemp1 = T_METER_15C;

  /** Reference pressure 1 in kPa. */
  private double refPressure1 = P_STD_KPA;

  /** Reference temperature 2 in K (to condition). */
  private double refTemp2 = T_NORMAL_0C;

  /** Reference pressure 2 in kPa. */
  private double refPressure2 = P_STD_KPA;

  /** Compression factor at condition 1. */
  private double z1 = 1.0;

  /** Compression factor at condition 2. */
  private double z2 = 1.0;

  /** Volume conversion factor from condition 1 to 2. */
  private double volumeConversionFactor = 1.0;

  /** Ideal gas molar volume at condition 1 in m3/mol. */
  private double molarVolume1 = 0.0;

  /** Ideal gas molar volume at condition 2 in m3/mol. */
  private double molarVolume2 = 0.0;

  /**
   * Constructor for Standard_ISO13443.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO13443(SystemInterface thermoSystem) {
    super("Standard_ISO13443", "Natural gas - Standard reference conditions", thermoSystem);
  }

  /**
   * Sets the conversion from one reference condition to another.
   *
   * @param fromTempK source reference temperature in Kelvin
   * @param fromPressureKPa source reference pressure in kPa
   * @param toTempK target reference temperature in Kelvin
   * @param toPressureKPa target reference pressure in kPa
   */
  public void setConversionConditions(double fromTempK, double fromPressureKPa, double toTempK,
      double toPressureKPa) {
    this.refTemp1 = fromTempK;
    this.refPressure1 = fromPressureKPa;
    this.refTemp2 = toTempK;
    this.refPressure2 = toPressureKPa;
  }

  /**
   * Sets conversion using named reference conditions.
   *
   * @param fromCondition "15C", "0C", "60F", or "20C"
   * @param toCondition "15C", "0C", "60F", or "20C"
   */
  public void setConversionConditions(String fromCondition, String toCondition) {
    refTemp1 = getTemperatureForCondition(fromCondition);
    refTemp2 = getTemperatureForCondition(toCondition);
    refPressure1 = P_STD_KPA;
    refPressure2 = P_STD_KPA;
  }

  /**
   * Gets the temperature for a named reference condition.
   *
   * @param condition the condition name
   * @return temperature in Kelvin
   */
  private double getTemperatureForCondition(String condition) {
    if ("0C".equals(condition) || "normal".equals(condition) || "NTP".equals(condition)) {
      return T_NORMAL_0C;
    }
    if ("15C".equals(condition) || "standard".equals(condition) || "STP".equals(condition)) {
      return T_METER_15C;
    }
    if ("60F".equals(condition) || "US".equals(condition)) {
      return T_US_60F;
    }
    if ("20C".equals(condition)) {
      return T_20C;
    }
    if ("25C".equals(condition)) {
      return T_COMBUSTION_25C;
    }
    return T_METER_15C;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    // Calculate ideal gas molar volumes
    molarVolume1 = R * refTemp1 / (refPressure1 * 1000.0); // m3/mol
    molarVolume2 = R * refTemp2 / (refPressure2 * 1000.0); // m3/mol

    // Get compression factors using ISO 6976 summation factor method
    Standard_ISO6976 iso6976Cond1 =
        new Standard_ISO6976(thermoSystem, refTemp1 - 273.15, refTemp1 - 273.15, "volume");
    iso6976Cond1.setReferenceState("real");
    iso6976Cond1.calculate();
    z1 = iso6976Cond1.getValue("CompressionFactor");

    Standard_ISO6976 iso6976Cond2 =
        new Standard_ISO6976(thermoSystem, refTemp2 - 273.15, refTemp2 - 273.15, "volume");
    iso6976Cond2.setReferenceState("real");
    iso6976Cond2.calculate();
    z2 = iso6976Cond2.getValue("CompressionFactor");

    // Volume conversion factor: V2 = V1 * f
    // f = (P1/P2) * (T2/T1) * (Z2/Z1)
    volumeConversionFactor = (refPressure1 / refPressure2) * (refTemp2 / refTemp1) * (z2 / z1);
  }

  /**
   * Converts a volume from reference condition 1 to condition 2.
   *
   * @param volume volume at condition 1
   * @return volume at condition 2
   */
  public double convertVolume(double volume) {
    return volume * volumeConversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if ("volumeConversionFactor".equals(returnParameter)) {
      return volumeConversionFactor;
    }
    if ("Z1".equals(returnParameter)) {
      return z1;
    }
    if ("Z2".equals(returnParameter)) {
      return z2;
    }
    if ("molarVolume1".equals(returnParameter)) {
      return molarVolume1;
    }
    if ("molarVolume2".equals(returnParameter)) {
      return molarVolume2;
    }
    if ("refTemp1".equals(returnParameter)) {
      return refTemp1;
    }
    if ("refTemp2".equals(returnParameter)) {
      return refTemp2;
    }
    return volumeConversionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if ("volumeConversionFactor".equals(returnParameter) || "Z1".equals(returnParameter)
        || "Z2".equals(returnParameter)) {
      return "-";
    }
    if ("molarVolume1".equals(returnParameter) || "molarVolume2".equals(returnParameter)) {
      return "m3/mol";
    }
    if ("refTemp1".equals(returnParameter) || "refTemp2".equals(returnParameter)) {
      return "K";
    }
    return "-";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return volumeConversionFactor > 0.0;
  }
}
