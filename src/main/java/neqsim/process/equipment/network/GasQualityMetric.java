package neqsim.process.equipment.network;

/**
 * Gas-quality metrics supported by network quality profiles.
 */
public enum GasQualityMetric implements NetworkQualityMetric {
  /** ISO 6976 superior (gross) calorific value. */
  SUPERIOR_CALORIFIC_VALUE("superiorCalorificValue", "kJ/Sm3"),
  /** ISO 6976 inferior (net) calorific value. */
  INFERIOR_CALORIFIC_VALUE("inferiorCalorificValue", "kJ/Sm3"),
  /** ISO 6976 Wobbe index. */
  WOBBE_INDEX("wobbeIndex", "MJ/Sm3"),
  /** Gas relative density at the configured reference condition. */
  RELATIVE_DENSITY("relativeDensity", "-"),
  /** Carbon-dioxide mole percentage. */
  CO2_MOLE_PERCENT("co2MolePercent", "mol%"),
  /** Named-component mole percentage. */
  COMPONENT_MOLE_PERCENT("componentMolePercent", "mol%"),
  /** Hydrocarbon dew-point temperature at a specified pressure. */
  HYDROCARBON_DEW_POINT_TEMPERATURE("hydrocarbonDewPointTemperature", "C"),
  /** Hydrocarbon dew-point pressure at a specified temperature. */
  HYDROCARBON_DEW_POINT_PRESSURE("hydrocarbonDewPointPressure", "barg"),
  /** Water dew-point temperature. */
  WATER_DEW_POINT_TEMPERATURE("waterDewPointTemperature", "C"),
  /** Maximum pressure on the phase envelope. */
  CRICONDENBAR("cricondenbar", "bara"),
  /** Maximum temperature on the phase envelope. */
  CRICONDENTHERM("cricondentherm", "C"),
  /** Pressure at the named operating point. */
  OPERATING_PRESSURE("operatingPressure", "bara"),
  /** Temperature at the named operating point. */
  OPERATING_TEMPERATURE("operatingTemperature", "C"),
  /** Governed measured attribute not calculated from the EOS. */
  MEASURED_ATTRIBUTE("measuredAttribute", "-");

  private final String key;
  private final String defaultUnit;

  GasQualityMetric(String key, String defaultUnit) {
    this.key = key;
    this.defaultUnit = defaultUnit;
  }

  /** {@inheritDoc} */
  @Override
  public String getKey() {
    return key;
  }

  /** {@inheritDoc} */
  @Override
  public String getDefaultUnit() {
    return defaultUnit;
  }

  /** {@inheritDoc} */
  @Override
  public String getDomain() {
    return "gas";
  }
}
