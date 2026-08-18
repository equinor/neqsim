package neqsim.process.processmodel.dexpi;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable, caller-supplied provenance and plant identity for a native DEXPI 2.0 Plant
 * exchange.
 *
 * <p>The export timestamp and plant identity are deliberately required inputs. The exporter never
 * invents a current timestamp or project metadata, and use of this class does not imply drawing
 * approval or DEXPI qualification.</p>
 */
public final class Dexpi20PlantExportMetadata {
  /** Data properties defined by {@code Plant/Diagram.PlantMetaData}. */
  public enum PlantProperty {
    ENTERPRISE_IDENTIFICATION_CODE("EnterpriseIdentificationCode"),
    ENTERPRISE_NAME("EnterpriseName"),
    INDUSTRIAL_COMPLEX_IDENTIFICATION_CODE("IndustrialComplexIdentificationCode"),
    INDUSTRIAL_COMPLEX_NAME("IndustrialComplexName"),
    PLANT_AREA_IDENTIFICATION_CODE("PlantAreaIdentificationCode"),
    PLANT_AREA_NAME("PlantAreaName"),
    PLANT_SECTION_IDENTIFICATION_CODE("PlantSectionIdentificationCode"),
    PLANT_SECTION_NAME("PlantSectionName"),
    PLANT_SYSTEM_IDENTIFICATION_CODE("PlantSystemIdentificationCode"),
    PLANT_SYSTEM_NAME("PlantSystemName"),
    PLANT_TRAIN_IDENTIFICATION_CODE("PlantTrainIdentificationCode"),
    PLANT_TRAIN_NAME("PlantTrainName"),
    PROCESS_PLANT_IDENTIFICATION_CODE("ProcessPlantIdentificationCode"),
    PROCESS_PLANT_NAME("ProcessPlantName"),
    SITE_IDENTIFICATION_CODE("SiteIdentificationCode"),
    SITE_NAME("SiteName");

    private final String dexpiProperty;

    PlantProperty(String dexpiProperty) {
      this.dexpiProperty = dexpiProperty;
    }

    /**
     * Returns the exact DEXPI 2.0 data-property name.
     *
     * @return DEXPI property name
     */
    public String getDexpiProperty() {
      return dexpiProperty;
    }
  }

  private final String exportDateTime;
  private final String originatingSystemName;
  private final String originatingSystemVendorName;
  private final String originatingSystemVersion;
  private final Map<PlantProperty, String> plantProperties;

  private Dexpi20PlantExportMetadata(Builder builder) {
    exportDateTime = builder.exportDateTime;
    originatingSystemName = builder.originatingSystemName;
    originatingSystemVendorName = builder.originatingSystemVendorName;
    originatingSystemVersion = builder.originatingSystemVersion;
    plantProperties = Collections.unmodifiableMap(
        new EnumMap<PlantProperty, String>(builder.plantProperties));
  }

  /**
   * Starts a controlled metadata definition.
   *
   * @param exportDateTime ISO-8601 date-time including an offset, supplied by the exporting workflow
   * @param originatingSystemName name of the originating system
   * @param originatingSystemVendorName vendor or responsible organization name
   * @param originatingSystemVersion exact originating-system version
   * @return metadata builder
   */
  public static Builder builder(String exportDateTime, String originatingSystemName,
      String originatingSystemVendorName, String originatingSystemVersion) {
    return new Builder(exportDateTime, originatingSystemName, originatingSystemVendorName,
        originatingSystemVersion);
  }

  /** @return caller-supplied ISO-8601 export date-time */
  public String getExportDateTime() {
    return exportDateTime;
  }

  /** @return originating-system name */
  public String getOriginatingSystemName() {
    return originatingSystemName;
  }

  /** @return originating-system vendor or responsible organization */
  public String getOriginatingSystemVendorName() {
    return originatingSystemVendorName;
  }

  /** @return exact originating-system version */
  public String getOriginatingSystemVersion() {
    return originatingSystemVersion;
  }

  /**
   * Returns plant properties in stable DEXPI declaration order.
   *
   * @return immutable plant-property map
   */
  public Map<PlantProperty, String> getPlantProperties() {
    return plantProperties;
  }

  /** Builder for {@link Dexpi20PlantExportMetadata}. */
  public static final class Builder {
    private final String exportDateTime;
    private final String originatingSystemName;
    private final String originatingSystemVendorName;
    private final String originatingSystemVersion;
    private final EnumMap<PlantProperty, String> plantProperties =
        new EnumMap<PlantProperty, String>(PlantProperty.class);

    private Builder(String exportDateTime, String originatingSystemName,
        String originatingSystemVendorName, String originatingSystemVersion) {
      this.exportDateTime = requiredDateTime(exportDateTime);
      this.originatingSystemName = required(originatingSystemName, "originatingSystemName");
      this.originatingSystemVendorName =
          required(originatingSystemVendorName, "originatingSystemVendorName");
      this.originatingSystemVersion =
          required(originatingSystemVersion, "originatingSystemVersion");
    }

    /**
     * Adds one reviewed DEXPI PlantMetaData value.
     *
     * @param property official PlantMetaData property
     * @param value controlled source value
     * @return this builder
     */
    public Builder plantProperty(PlantProperty property, String value) {
      if (property == null) {
        throw new IllegalArgumentException("property must not be null");
      }
      plantProperties.put(property, required(value, property.getDexpiProperty()));
      return this;
    }

    /**
     * Builds immutable metadata. At least one real plant value is required so an empty metadata
     * object cannot be emitted merely to satisfy a validator.
     *
     * @return immutable export metadata
     */
    public Dexpi20PlantExportMetadata build() {
      if (plantProperties.isEmpty()) {
        throw new IllegalStateException("at least one controlled PlantMetaData value is required");
      }
      return new Dexpi20PlantExportMetadata(this);
    }
  }

  private static String requiredDateTime(String value) {
    String result = required(value, "exportDateTime");
    try {
      OffsetDateTime.parse(result);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          "exportDateTime must be an ISO-8601 date-time with an offset", ex);
    }
    return result;
  }

  private static String required(String value, String name) {
    String result = value == null ? "" : value.trim();
    if (result.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return result;
  }
}
