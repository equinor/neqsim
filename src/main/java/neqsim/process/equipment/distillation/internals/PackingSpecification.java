package neqsim.process.equipment.distillation.internals;

import java.io.Serializable;

/**
 * Immutable packed-column packing data used by hydraulic and rate-based column models.
 *
 * <p>
 * The specification stores geometry, hydraulic capacity, material wetting data, and optional
 * Billet-Schultes constants in one reusable object. Nominal size is stored in millimetres to match
 * vendor and NeqSim design-data tables.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PackingSpecification implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Packing display name. */
  private final String name;

  /** Packing category such as random or structured. */
  private final String category;

  /** Packing material such as metal, plastic, or ceramic. */
  private final String material;

  /** Nominal packing size in millimetres. */
  private final double nominalSizeMm;

  /** Specific surface area in square metres per cubic metre. */
  private final double specificSurfaceArea;

  /** Packing void fraction. */
  private final double voidFraction;

  /** Packing factor used by GPDC-style hydraulic correlations. */
  private final double packingFactor;

  /** Critical surface tension in newtons per metre. */
  private final double criticalSurfaceTension;

  /** Billet-Schultes liquid-side constant. */
  private final double billetLiquidConstant;

  /** Billet-Schultes gas-side constant. */
  private final double billetGasConstant;

  /** Source text for traceability. */
  private final String source;

  /**
   * Create a packing specification.
   *
   * @param name packing display name
   * @param category packing category, normally random or structured
   * @param material packing material, for example metal, plastic, or ceramic
   * @param nominalSizeMm nominal packing size in millimetres, or zero for structured packing
   * @param specificSurfaceArea specific surface area in m2/m3, must be positive
   * @param voidFraction void fraction from zero to one, must be positive
   * @param packingFactor hydraulic packing factor in 1/m, must be positive
   * @param criticalSurfaceTension critical surface tension in N/m, must be positive
   * @param billetLiquidConstant Billet-Schultes liquid-side constant, must be positive
   * @param billetGasConstant Billet-Schultes gas-side constant, must be positive
   * @param source source description for the data
   * @throws IllegalArgumentException if a required numeric value is outside its valid range
   */
  public PackingSpecification(String name, String category, String material, double nominalSizeMm,
      double specificSurfaceArea, double voidFraction, double packingFactor,
      double criticalSurfaceTension, double billetLiquidConstant, double billetGasConstant,
      String source) {
    this.name = requireText(name, "name");
    this.category = requireText(category, "category");
    this.material = requireText(material, "material");
    validateNonNegative(nominalSizeMm, "nominalSizeMm");
    validatePositive(specificSurfaceArea, "specificSurfaceArea");
    validateFraction(voidFraction, "voidFraction");
    validatePositive(packingFactor, "packingFactor");
    validatePositive(criticalSurfaceTension, "criticalSurfaceTension");
    validatePositive(billetLiquidConstant, "billetLiquidConstant");
    validatePositive(billetGasConstant, "billetGasConstant");
    this.nominalSizeMm = nominalSizeMm;
    this.specificSurfaceArea = specificSurfaceArea;
    this.voidFraction = voidFraction;
    this.packingFactor = packingFactor;
    this.criticalSurfaceTension = criticalSurfaceTension;
    this.billetLiquidConstant = billetLiquidConstant;
    this.billetGasConstant = billetGasConstant;
    this.source = source == null ? "unspecified" : source;
  }

  /**
   * Require a non-empty text field.
   *
   * @param value text value to check
   * @param fieldName name of the field being checked
   * @return trimmed text value
   * @throws IllegalArgumentException if the value is null or empty
   */
  private static String requireText(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must be specified");
    }
    return value.trim();
  }

  /**
   * Validate that a numeric value is positive and finite.
   *
   * @param value value to validate
   * @param fieldName name of the field being checked
   * @throws IllegalArgumentException if the value is not positive or finite
   */
  private static void validatePositive(double value, String fieldName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }

  /**
   * Validate that a numeric value is non-negative and finite.
   *
   * @param value value to validate
   * @param fieldName name of the field being checked
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  private static void validateNonNegative(double value, String fieldName) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
  }

  /**
   * Validate that a numeric value is a fraction between zero and one.
   *
   * @param value value to validate
   * @param fieldName name of the field being checked
   * @throws IllegalArgumentException if the value is outside the open interval zero to one
   */
  private static void validateFraction(double value, String fieldName) {
    if (!Double.isFinite(value) || value <= 0.0 || value >= 1.0) {
      throw new IllegalArgumentException(fieldName + " must be between zero and one");
    }
  }

  /**
   * Get the packing display name.
   *
   * @return packing display name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the packing category.
   *
   * @return packing category
   */
  public String getCategory() {
    return category;
  }

  /**
   * Get the packing material.
   *
   * @return packing material
   */
  public String getMaterial() {
    return material;
  }

  /**
   * Get the nominal packing size.
   *
   * @return nominal size in millimetres
   */
  public double getNominalSizeMm() {
    return nominalSizeMm;
  }

  /**
   * Get the nominal packing size.
   *
   * @return nominal size in metres
   */
  public double getNominalSizeM() {
    return nominalSizeMm / 1000.0;
  }

  /**
   * Get the specific surface area.
   *
   * @return specific surface area in m2/m3
   */
  public double getSpecificSurfaceArea() {
    return specificSurfaceArea;
  }

  /**
   * Get the void fraction.
   *
   * @return void fraction
   */
  public double getVoidFraction() {
    return voidFraction;
  }

  /**
   * Get the hydraulic packing factor.
   *
   * @return packing factor in 1/m
   */
  public double getPackingFactor() {
    return packingFactor;
  }

  /**
   * Get the critical surface tension.
   *
   * @return critical surface tension in N/m
   */
  public double getCriticalSurfaceTension() {
    return criticalSurfaceTension;
  }

  /**
   * Get the Billet-Schultes liquid-side constant.
   *
   * @return liquid-side constant
   */
  public double getBilletLiquidConstant() {
    return billetLiquidConstant;
  }

  /**
   * Get the Billet-Schultes gas-side constant.
   *
   * @return gas-side constant
   */
  public double getBilletGasConstant() {
    return billetGasConstant;
  }

  /**
   * Get the source description.
   *
   * @return data source description
   */
  public String getSource() {
    return source;
  }

  /**
   * Check whether this packing is structured.
   *
   * @return true if the category is structured
   */
  public boolean isStructured() {
    return "structured".equalsIgnoreCase(category);
  }
}
