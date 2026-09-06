package neqsim.standards.oilquality;

import java.io.Serializable;
import neqsim.thermo.characterization.SarirAtmosphericReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductQualityReference;
import neqsim.thermo.characterization.SarirAtmosphericReference.ProductSpecificationReference;

/**
 * Strict comparison of a calculated NeqSim product T95 with the published Sarir refinery evidence.
 *
 * <p>
 * The NeqSim value is obtained only through
 * {@link Standard_ASTM_D86#getQualifiedD86Temperature(double)}, at the qualified 95 liquid-volume-percent
 * Riazi-Daubert reference point. The published laboratory, HYSYS, and specification values are retained as read-only
 * comparison evidence and are never used to tune the NeqSim calculation.
 * </p>
 *
 * <p>
 * Sarir T5 data are intentionally excluded because 5 liquid volume percent is not one of the seven qualified
 * Riazi-Daubert recovery points. The open-ended residual specification is also excluded from numeric comparison.
 * </p>
 */
public final class SarirD86ProductComparison {
  private static final double RECOVERY_VOLUME_PERCENT = 95.0;

  private SarirD86ProductComparison() {
  }

  /**
   * Compare the strict NeqSim T95 with one numeric Sarir product row.
   *
   * @param standard successfully calculated ASTM D86 standard for the product stream
   * @param productName exact Sarir source-table product label
   * @return immutable comparison result
   * @throws IllegalArgumentException if the standard or label is null, the label has no numeric Table 5 row, or the
   *     product has no numeric T95 specification
   * @throws IllegalStateException if the standard has not produced a finite strict T95
   */
  public static Result compareT95(Standard_ASTM_D86 standard, String productName) {
    if (standard == null) {
      throw new IllegalArgumentException("ASTM D86 standard cannot be null");
    }
    ProductQualityReference quality = requireProductQuality(productName);
    ProductSpecificationReference specification =
        SarirAtmosphericReference.getProductSpecification(productName);
    if (!specification.hasNumericSpecificationTemperature()) {
      throw new IllegalArgumentException(
          "Sarir product has no numeric T95 specification: " + productName);
    }

    double neqsimT95C =
        standard.getQualifiedD86Temperature(RECOVERY_VOLUME_PERCENT, "C");
    if (!Double.isFinite(neqsimT95C)) {
      throw new IllegalStateException("Calculated strict NeqSim T95 must be finite");
    }

    return new Result(
        productName,
        neqsimT95C,
        quality.getLaboratoryNinetyFivePercentCelsius(),
        quality.getSimulationNinetyFivePercentCelsius(),
        specification.getSpecificationTemperatureCelsius());
  }

  private static ProductQualityReference requireProductQuality(String productName) {
    if (productName == null) {
      throw new IllegalArgumentException("Product name cannot be null");
    }
    for (ProductQualityReference quality : SarirAtmosphericReference.getProductQualities()) {
      if (quality.getName().equals(productName)) {
        return quality;
      }
    }
    throw new IllegalArgumentException(
        "Unknown or nonnumeric Sarir product-quality row: " + productName);
  }

  /** Immutable strict T95 comparison result. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String productName;
    private final double neqsimT95Celsius;
    private final double laboratoryT95Celsius;
    private final double hysysT95Celsius;
    private final double specificationT95Celsius;

    private Result(
        String productName,
        double neqsimT95Celsius,
        double laboratoryT95Celsius,
        double hysysT95Celsius,
        double specificationT95Celsius) {
      this.productName = productName;
      this.neqsimT95Celsius = neqsimT95Celsius;
      this.laboratoryT95Celsius = laboratoryT95Celsius;
      this.hysysT95Celsius = hysysT95Celsius;
      this.specificationT95Celsius = specificationT95Celsius;
    }

    /** @return exact Sarir source-table product label */
    public String getProductName() {
      return productName;
    }

    /** @return qualified recovery point, always 95 liquid volume percent */
    public double getRecoveryVolumePercent() {
      return RECOVERY_VOLUME_PERCENT;
    }

    /** @return strict NeqSim ASTM D86 T95 in degrees Celsius */
    public double getNeqsimT95Celsius() {
      return neqsimT95Celsius;
    }

    /** @return published laboratory ASTM D86 T95 in degrees Celsius */
    public double getLaboratoryT95Celsius() {
      return laboratoryT95Celsius;
    }

    /** @return published HYSYS ASTM D86 T95 in degrees Celsius */
    public double getHysysT95Celsius() {
      return hysysT95Celsius;
    }

    /** @return published Sarir Table 2 ASTM D86 T95 specification in degrees Celsius */
    public double getSpecificationT95Celsius() {
      return specificationT95Celsius;
    }

    /** @return absolute relative NeqSim-versus-laboratory T95 error in percent */
    public double getNeqsimAbsoluteRelativeErrorPercent() {
      return SarirAtmosphericReference.calculateAbsoluteRelativeErrorPercent(
          laboratoryT95Celsius, neqsimT95Celsius);
    }

    /** @return absolute relative HYSYS-versus-laboratory T95 error in percent */
    public double getHysysAbsoluteRelativeErrorPercent() {
      return SarirAtmosphericReference.calculateAbsoluteRelativeErrorPercent(
          laboratoryT95Celsius, hysysT95Celsius);
    }

    /**
     * Return the published specification reference minus the calculated NeqSim T95.
     *
     * <p>
     * A non-negative value means the calculated value is at or below the published numeric reference. It is not an
     * ASTM compliance determination.
     * </p>
     *
     * @return specification margin in degrees Celsius
     */
    public double getSpecificationMarginCelsius() {
      return specificationT95Celsius - neqsimT95Celsius;
    }
  }
}
