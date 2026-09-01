package neqsim.process.equipment.network;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Explicit pressure, temperature, standard-volume, and test basis for a quality limit.
 */
public class QualityReference implements Serializable {
  private static final long serialVersionUID = 1000L;

  private Double pressure;
  private String pressureUnit;
  private Double temperature;
  private String temperatureUnit;
  private Double volumeReferenceTemperatureC;
  private Double combustionReferenceTemperatureC;
  private String basis;

  /** Create an empty reference condition. */
  public QualityReference() {
  }

  /**
   * Create a pressure reference.
   *
   * @param value pressure value
   * @param unit pressure unit, including bara or barg
   * @return reference
   */
  public static QualityReference atPressure(double value, String unit) {
    QualityReference reference = new QualityReference();
    reference.pressure = value;
    reference.pressureUnit = unit;
    return reference;
  }

  /**
   * Create a temperature reference.
   *
   * @param value temperature value
   * @param unit C or K
   * @return reference
   */
  public static QualityReference atTemperature(double value, String unit) {
    QualityReference reference = new QualityReference();
    reference.temperature = value;
    reference.temperatureUnit = unit;
    return reference;
  }

  /**
   * Create a pressure and temperature reference.
   *
   * @param pressureValue pressure value
   * @param pressureUnit pressure unit
   * @param temperatureValue temperature value
   * @param temperatureUnit temperature unit
   * @return reference
   */
  public static QualityReference atPressureAndTemperature(double pressureValue, String pressureUnit,
      double temperatureValue, String temperatureUnit) {
    QualityReference reference = atPressure(pressureValue, pressureUnit);
    reference.temperature = temperatureValue;
    reference.temperatureUnit = temperatureUnit;
    return reference;
  }

  /**
   * Set ISO 6976 volume and combustion reference temperatures.
   *
   * @param volumeTemperatureC standard-volume reference in C
   * @param combustionTemperatureC combustion reference in C
   * @return this reference
   */
  public QualityReference withIso6976Reference(double volumeTemperatureC, double combustionTemperatureC) {
    volumeReferenceTemperatureC = volumeTemperatureC;
    combustionReferenceTemperatureC = combustionTemperatureC;
    return this;
  }

  /**
   * Set a human-readable test or calculation basis.
   *
   * @param value basis
   * @return this reference
   */
  public QualityReference withBasis(String value) {
    basis = value;
    return this;
  }

  /** @return pressure, or null when unspecified */
  public Double getPressure() {
    return pressure;
  }

  /** @return pressure unit, or null */
  public String getPressureUnit() {
    return pressureUnit;
  }

  /** @return temperature, or null */
  public Double getTemperature() {
    return temperature;
  }

  /** @return temperature unit, or null */
  public String getTemperatureUnit() {
    return temperatureUnit;
  }

  /** @return volume reference temperature in C, or null */
  public Double getVolumeReferenceTemperatureC() {
    return volumeReferenceTemperatureC;
  }

  /** @return combustion reference temperature in C, or null */
  public Double getCombustionReferenceTemperatureC() {
    return combustionReferenceTemperatureC;
  }

  /** @return test or calculation basis */
  public String getBasis() {
    return basis;
  }

  /**
   * Convert the configured pressure to bara.
   *
   * @return absolute pressure in bar
   */
  public double getPressureBara() {
    if (pressure == null || pressureUnit == null) {
      throw new IllegalStateException("A pressure reference is required");
    }
    if ("bara".equalsIgnoreCase(pressureUnit) || "bar".equalsIgnoreCase(pressureUnit)) {
      return pressure;
    }
    if ("barg".equalsIgnoreCase(pressureUnit)) {
      return pressure + 1.01325;
    }
    if ("Pa".equalsIgnoreCase(pressureUnit)) {
      return pressure / 1.0e5;
    }
    if ("kPa".equalsIgnoreCase(pressureUnit)) {
      return pressure / 100.0;
    }
    throw new IllegalArgumentException("Unsupported pressure unit: " + pressureUnit);
  }

  /**
   * Convert the configured temperature to K.
   *
   * @return absolute temperature in K
   */
  public double getTemperatureK() {
    if (temperature == null || temperatureUnit == null) {
      throw new IllegalStateException("A temperature reference is required");
    }
    if ("K".equalsIgnoreCase(temperatureUnit)) {
      return temperature;
    }
    if ("C".equalsIgnoreCase(temperatureUnit) || "degC".equalsIgnoreCase(temperatureUnit)) {
      return temperature + 273.15;
    }
    throw new IllegalArgumentException("Unsupported temperature unit: " + temperatureUnit);
  }

  /**
   * Serialize this reference condition.
   *
   * @return JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
