package neqsim.process.safety.firewater;

import java.io.Serializable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Deluge nozzle net layout and hydraulic screening.
 *
 * <p>
 * Converts a required water application rate over a protected area into a nozzle count, a grid spacing and a header
 * flow, using the two independent criteria that govern a real deluge net:
 * </p>
 * <ol>
 * <li><b>Flow criterion</b> — the summed nozzle discharge must deliver at least the required density. Nozzle discharge
 * follows the orifice law <em>Q = K&middot;&radic;p</em> with <em>K</em> in (l/min)/&radic;bar.</li>
 * <li><b>Coverage criterion</b> — nozzles must be close enough that their spray patterns overlap, so the number of
 * nozzles cannot be less than the protected area divided by the effective coverage area of one nozzle.</li>
 * </ol>
 *
 * <p>
 * The governing nozzle count is the larger of the two. Designing on the flow criterion alone is the classic error: it
 * produces too few, too widely spaced nozzles that meet the average density but leave dry patches between spray cones.
 * </p>
 *
 * <p>
 * The class also checks the operating pressure against the nozzle minimum, reports the delivered density, and flags the
 * obstruction-clearance requirement that governs whether nozzles can be placed above congested equipment. References:
 * NFPA 15 (water spray fixed systems), NFPA 16 (foam-water deluge), NORSOK S-001, ISO 13702.
 * </p>
 *
 * @author esol
 * @version 1.1
 */
public class DelugeNozzleLayout implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Deluge nozzle characteristic. */
  public static class Nozzle implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Nozzle designation. */
    public final String name;
    /** Discharge coefficient in (l/min)/sqrt(bar). */
    public final double kFactorLpmPerSqrtBar;
    /** Minimum operating pressure in barg. */
    public final double minPressureBarg;
    /** Maximum permitted spacing between adjacent nozzles in m. */
    public final double maxSpacingM;
    /** Clear depth required in front of the nozzle in m. */
    public final double requiredClearDepthM;

    /**
     * Create a nozzle characteristic.
     *
     * @param name nozzle designation
     * @param kFactorLpmPerSqrtBar discharge coefficient in (l/min)/sqrt(bar), must be positive
     * @param minPressureBarg minimum operating pressure in barg, must be positive
     * @param maxSpacingM maximum permitted nozzle spacing in m, must be positive
     * @param requiredClearDepthM clear depth required in front of the nozzle in m
     */
    public Nozzle(String name, double kFactorLpmPerSqrtBar, double minPressureBarg, double maxSpacingM,
        double requiredClearDepthM) {
      if (kFactorLpmPerSqrtBar <= 0.0 || minPressureBarg <= 0.0 || maxSpacingM <= 0.0) {
        throw new IllegalArgumentException("nozzle K, minimum pressure and maximum spacing must be > 0");
      }
      this.name = name;
      this.kFactorLpmPerSqrtBar = kFactorLpmPerSqrtBar;
      this.minPressureBarg = minPressureBarg;
      this.maxSpacingM = maxSpacingM;
      this.requiredClearDepthM = requiredClearDepthM;
    }

    /**
     * Discharge at a given operating pressure.
     *
     * @param pressureBarg operating pressure at the nozzle in barg, must be zero or greater
     * @return discharge in l/min
     */
    public double flowLpm(double pressureBarg) {
      if (pressureBarg < 0.0) {
        return 0.0;
      }
      return kFactorLpmPerSqrtBar * Math.sqrt(pressureBarg);
    }

    /**
     * Minimum discharge, at the nozzle minimum operating pressure.
     *
     * @return discharge in l/min
     */
    public double minFlowLpm() {
      return flowLpm(minPressureBarg);
    }
  }

  /**
   * Generic medium-velocity deluge nozzle, representative of an MV-class water-spray nozzle.
   *
   * @return nozzle characteristic
   */
  public static Nozzle mediumVelocity() {
    return new Nozzle("MV (generic medium velocity)", 28.0, 1.4, 3.0, 0.6);
  }

  /**
   * Generic high-velocity deluge nozzle, representative of an HV-class water-spray nozzle.
   *
   * @return nozzle characteristic
   */
  public static Nozzle highVelocity() {
    return new Nozzle("HV (generic high velocity)", 42.9, 3.5, 3.0, 1.0);
  }

  /**
   * Generic high-capacity open deluge nozzle for large open decks.
   *
   * @return nozzle characteristic
   */
  public static Nozzle highCapacity() {
    return new Nozzle("HC (generic high capacity)", 79.0, 3.5, 3.7, 1.0);
  }

  private final double protectedAreaM2;
  private final double requiredDensityLpmPerM2;
  private final Nozzle nozzle;
  private double operatingPressureBarg = 5.0;
  private double coverageEfficiency = 1.0;

  /**
   * Create a nozzle layout screening for a protected area.
   *
   * @param protectedAreaM2 area to be covered in m2, must be greater than zero
   * @param requiredDensityLpmPerM2 required application rate in (l/min)/m2, must be greater than zero
   * @param nozzle nozzle characteristic, must not be null
   */
  public DelugeNozzleLayout(double protectedAreaM2, double requiredDensityLpmPerM2, Nozzle nozzle) {
    if (protectedAreaM2 <= 0.0) {
      throw new IllegalArgumentException("protectedAreaM2 must be > 0, got " + protectedAreaM2);
    }
    if (requiredDensityLpmPerM2 <= 0.0) {
      throw new IllegalArgumentException("requiredDensityLpmPerM2 must be > 0, got " + requiredDensityLpmPerM2);
    }
    if (nozzle == null) {
      throw new IllegalArgumentException("nozzle must not be null");
    }
    this.protectedAreaM2 = protectedAreaM2;
    this.requiredDensityLpmPerM2 = requiredDensityLpmPerM2;
    this.nozzle = nozzle;
  }

  /**
   * Set the nozzle operating pressure.
   *
   * @param pressureBarg pressure at the nozzle in barg, must be greater than zero
   * @return this layout, for chaining
   */
  public DelugeNozzleLayout setOperatingPressureBarg(double pressureBarg) {
    if (pressureBarg <= 0.0) {
      throw new IllegalArgumentException("operating pressure must be > 0, got " + pressureBarg);
    }
    this.operatingPressureBarg = pressureBarg;
    return this;
  }

  /**
   * Set the fraction of the nominal nozzle footprint that counts as effective coverage.
   *
   * <p>
   * A value below one tightens the grid to account for wind exposure on an open deck, for shadowing behind congested
   * equipment, or for a project rule demanding overlap beyond the nominal spacing. The default of 1.0 uses the nominal
   * maximum spacing directly.
   * </p>
   *
   * @param efficiency coverage efficiency in the range 0 (exclusive) to 1 (inclusive)
   * @return this layout, for chaining
   */
  public DelugeNozzleLayout setCoverageEfficiency(double efficiency) {
    if (efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("coverage efficiency must be in (0,1], got " + efficiency);
    }
    this.coverageEfficiency = efficiency;
    return this;
  }

  /**
   * Total flow required to satisfy the density over the protected area.
   *
   * @return required flow in l/min
   */
  public double requiredFlowLpm() {
    return protectedAreaM2 * requiredDensityLpmPerM2;
  }

  /**
   * Discharge of one nozzle at the operating pressure.
   *
   * @return discharge in l/min
   */
  public double flowPerNozzleLpm() {
    return nozzle.flowLpm(operatingPressureBarg);
  }

  /**
   * Effective area covered by one nozzle, from the maximum permitted spacing.
   *
   * @return effective coverage area in m2
   */
  public double coveragePerNozzleM2() {
    return nozzle.maxSpacingM * nozzle.maxSpacingM * coverageEfficiency;
  }

  /**
   * Nozzle count implied by the flow criterion alone.
   *
   * @return nozzle count, rounded up
   */
  public int nozzleCountFromFlow() {
    return (int) Math.ceil(requiredFlowLpm() / flowPerNozzleLpm());
  }

  /**
   * Nozzle count implied by the spray-coverage criterion alone.
   *
   * @return nozzle count, rounded up
   */
  public int nozzleCountFromCoverage() {
    return (int) Math.ceil(protectedAreaM2 / coveragePerNozzleM2());
  }

  /**
   * Governing nozzle count, the larger of the flow and coverage criteria.
   *
   * @return nozzle count
   */
  public int nozzleCount() {
    return Math.max(nozzleCountFromFlow(), nozzleCountFromCoverage());
  }

  /**
   * Name of the criterion that governs the nozzle count.
   *
   * @return either "flow" or "coverage"
   */
  public String governingCriterion() {
    return nozzleCountFromCoverage() > nozzleCountFromFlow() ? "coverage" : "flow";
  }

  /**
   * Square-grid pitch corresponding to the governing nozzle count.
   *
   * @return nozzle spacing in m
   */
  public double gridSpacingM() {
    return Math.sqrt(protectedAreaM2 / nozzleCount());
  }

  /**
   * Water actually delivered by the governing nozzle count at the operating pressure.
   *
   * @return delivered flow in l/min
   */
  public double deliveredFlowLpm() {
    return nozzleCount() * flowPerNozzleLpm();
  }

  /**
   * Density actually delivered over the protected area.
   *
   * @return delivered density in (l/min)/m2
   */
  public double deliveredDensityLpmPerM2() {
    return deliveredFlowLpm() / protectedAreaM2;
  }

  /**
   * Whether the operating pressure is at or above the nozzle minimum.
   *
   * @return true when the nozzle will produce its design spray pattern
   */
  public boolean isPressureAdequate() {
    return operatingPressureBarg >= nozzle.minPressureBarg;
  }

  /**
   * Whether the delivered density meets the requirement.
   *
   * @return true when the delivered density is at least the required density
   */
  public boolean isDensityMet() {
    return deliveredDensityLpmPerM2() >= requiredDensityLpmPerM2;
  }

  /**
   * Check whether a stated free space in front of a nozzle satisfies the clearance requirement.
   *
   * <p>
   * Deluge nozzles need an unobstructed depth in front of the orifice for the spray cone to develop. Where equipment
   * must be worked on, opened or lifted, that clearance is often the reason nozzles cannot be placed above the item,
   * which pushes the design towards side-mounted nozzles or dedicated object protection.
   * </p>
   *
   * @param availableClearDepthM free space available in front of the nozzle in m
   * @return true when the available clearance is at least the nozzle requirement
   */
  public boolean isClearanceAdequate(double availableClearDepthM) {
    return availableClearDepthM >= nozzle.requiredClearDepthM;
  }

  /**
   * Get the nozzle characteristic used.
   *
   * @return the nozzle
   */
  public Nozzle getNozzle() {
    return nozzle;
  }

  /**
   * Get the operating pressure.
   *
   * @return operating pressure in barg
   */
  public double getOperatingPressureBarg() {
    return operatingPressureBarg;
  }

  /**
   * Serialise the layout to JSON for agent and report consumption.
   *
   * @return pretty-printed JSON document
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("protectedAreaM2", protectedAreaM2);
    root.addProperty("requiredDensityLpmPerM2", requiredDensityLpmPerM2);
    root.addProperty("requiredFlowLpm", requiredFlowLpm());
    JsonObject noz = new JsonObject();
    noz.addProperty("name", nozzle.name);
    noz.addProperty("kFactorLpmPerSqrtBar", nozzle.kFactorLpmPerSqrtBar);
    noz.addProperty("minPressureBarg", nozzle.minPressureBarg);
    noz.addProperty("maxSpacingM", nozzle.maxSpacingM);
    noz.addProperty("requiredClearDepthM", nozzle.requiredClearDepthM);
    root.add("nozzle", noz);
    root.addProperty("operatingPressureBarg", operatingPressureBarg);
    root.addProperty("flowPerNozzleLpm", flowPerNozzleLpm());
    root.addProperty("coveragePerNozzleM2", coveragePerNozzleM2());
    root.addProperty("nozzleCountFromFlow", nozzleCountFromFlow());
    root.addProperty("nozzleCountFromCoverage", nozzleCountFromCoverage());
    root.addProperty("nozzleCount", nozzleCount());
    root.addProperty("governingCriterion", governingCriterion());
    root.addProperty("gridSpacingM", gridSpacingM());
    root.addProperty("deliveredFlowLpm", deliveredFlowLpm());
    root.addProperty("deliveredDensityLpmPerM2", deliveredDensityLpmPerM2());
    root.addProperty("pressureAdequate", isPressureAdequate());
    root.addProperty("densityMet", isDensityMet());
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }
}
