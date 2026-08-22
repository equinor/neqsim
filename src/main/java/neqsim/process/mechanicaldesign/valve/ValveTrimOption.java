package neqsim.process.mechanicaldesign.valve;

import java.io.Serializable;

/**
 * Describes one vendor-qualified valve or choke trim capacity option.
 *
 * <p>
 * NeqSim does not infer capacity derating from material or construction labels. The maximum design Cv must come from
 * the applicable vendor data for the body, trim, pressure class, flow direction, and service. Material and construction
 * are retained as engineering provenance so, for example, a tungsten-carbide trim protected by a metallic brickstopper
 * can be distinguished from a standard metallic trim.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ValveTrimOption implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String identifier;
  private final double relativeTrimSizePercent;
  private final double maximumDesignCv;
  private final String material;
  private final String construction;

  /**
   * Creates a trim option without material or construction metadata.
   *
   * @param identifier unique option identifier or catalog label
   * @param relativeTrimSizePercent relative trim size in percent of the vendor reference trim
   * @param maximumDesignCv vendor-qualified maximum design Cv for this option
   * @throws IllegalArgumentException when the identifier, relative size, or maximum Cv is invalid
   */
  public ValveTrimOption(String identifier, double relativeTrimSizePercent, double maximumDesignCv) {
    this(identifier, relativeTrimSizePercent, maximumDesignCv, "", "");
  }

  /**
   * Creates a trim option with engineering provenance.
   *
   * @param identifier unique option identifier or catalog label
   * @param relativeTrimSizePercent relative trim size in percent of the vendor reference trim
   * @param maximumDesignCv vendor-qualified maximum design Cv for this option
   * @param material trim material description
   * @param construction trim construction or protection description
   * @throws IllegalArgumentException when the identifier, relative size, or maximum Cv is invalid
   */
  public ValveTrimOption(String identifier, double relativeTrimSizePercent, double maximumDesignCv, String material,
      String construction) {
    if (identifier == null || identifier.trim().isEmpty()) {
      throw new IllegalArgumentException("Trim option identifier must not be empty");
    }
    if (!Double.isFinite(relativeTrimSizePercent) || relativeTrimSizePercent <= 0.0
        || relativeTrimSizePercent > 100.0) {
      throw new IllegalArgumentException("Relative trim size must be finite and in the interval (0, 100]");
    }
    if (!Double.isFinite(maximumDesignCv) || maximumDesignCv <= 0.0) {
      throw new IllegalArgumentException("Maximum design Cv must be finite and greater than zero");
    }
    this.identifier = identifier.trim();
    this.relativeTrimSizePercent = relativeTrimSizePercent;
    this.maximumDesignCv = maximumDesignCv;
    this.material = material == null ? "" : material.trim();
    this.construction = construction == null ? "" : construction.trim();
  }

  /**
   * Gets the option identifier.
   *
   * @return option identifier or catalog label
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Gets the relative trim size.
   *
   * @return relative trim size in percent of the vendor reference trim
   */
  public double getRelativeTrimSizePercent() {
    return relativeTrimSizePercent;
  }

  /**
   * Gets the vendor-qualified maximum design Cv.
   *
   * @return maximum design Cv
   */
  public double getMaximumDesignCv() {
    return maximumDesignCv;
  }

  /**
   * Gets the trim material description.
   *
   * @return material description, or an empty string when not supplied
   */
  public String getMaterial() {
    return material;
  }

  /**
   * Gets the trim construction or protection description.
   *
   * @return construction description, or an empty string when not supplied
   */
  public String getConstruction() {
    return construction;
  }
}
