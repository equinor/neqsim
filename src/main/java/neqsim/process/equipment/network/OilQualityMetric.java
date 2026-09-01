package neqsim.process.equipment.network;

/**
 * Oil-quality metrics supported by network quality profiles.
 */
public enum OilQualityMetric implements NetworkQualityMetric {
  /** True vapor pressure at the configured reference temperature. */
  TRUE_VAPOR_PRESSURE("trueVaporPressure", "bara"),
  /** Reid vapor pressure. */
  REID_VAPOR_PRESSURE("reidVaporPressure", "bara"),
  /** ASTM D6377 vapor-pressure result at a vapor/liquid ratio of 4. */
  VPCR4("vpcr4", "bara"),
  /** Mass density at the configured reference temperature. */
  DENSITY("density", "kg/m3"),
  /** API gravity calculated from reference-condition density. */
  API_GRAVITY("apiGravity", "degAPI"),
  /** Dynamic viscosity at the configured reference temperature. */
  DYNAMIC_VISCOSITY("dynamicViscosity", "mPa.s"),
  /** Kinematic viscosity at the configured reference temperature. */
  KINEMATIC_VISCOSITY("kinematicViscosity", "cSt"),
  /** Bubble-point pressure at the configured temperature. */
  BUBBLE_POINT_PRESSURE("bubblePointPressure", "bara"),
  /** Dissolved gas content on the configured standard-volume basis. */
  GAS_CONTENT("gasContent", "Sm3/Sm3"),
  /** Sulfur mass percentage supplied as a governed assay attribute. */
  SULFUR_MASS_PERCENT("sulfurMassPercent", "mass%"),
  /** Total acid number supplied as a governed assay attribute. */
  TOTAL_ACID_NUMBER("totalAcidNumber", "mgKOH/g"),
  /** Basic sediment and water volume percentage. */
  WATER_BSW_VOLUME_PERCENT("waterBswVolumePercent", "vol%"),
  /** Sediment volume percentage. */
  SEDIMENT_VOLUME_PERCENT("sedimentVolumePercent", "vol%"),
  /** Salt content. */
  SALT("salt", "mg/L"),
  /** Hydrogen-sulfide mass concentration. */
  H2S("h2s", "mg/kg"),
  /** Governed measured attribute not calculated from the EOS. */
  MEASURED_ATTRIBUTE("measuredAttribute", "-");

  private final String key;
  private final String defaultUnit;

  OilQualityMetric(String key, String defaultUnit) {
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
    return "oil";
  }
}
