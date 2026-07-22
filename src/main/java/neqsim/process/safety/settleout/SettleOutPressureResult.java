package neqsim.process.safety.settleout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.settleout.SettleOutPressureAnalyzer.SettleOutVerdict;

/**
 * Result of a {@link SettleOutPressureAnalyzer} settle-out (gas-volume equalisation) screening.
 *
 * <p>
 * Summarises the common settle-out pressure reached after a compressor trip, the settle-out temperature and
 * compressibility used, the total inventory and volume, the margin to the protected (low-pressure) system rating, the
 * verdict, and the per-compartment inventory breakdown.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SettleOutPressureResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double settleOutPressureBara;
  private final double settleOutTemperatureK;
  private final double settleOutZFactor;
  private final double totalMoles;
  private final double totalVolumeM3;
  private final double maxCompartmentPressureBara;
  private final double minCompartmentPressureBara;
  private final double protectedRatingBara;
  private final boolean ratingProvided;
  private final double marginToRatingBar;
  private final boolean exceedsRating;
  private final SettleOutVerdict verdict;
  private final List<CompartmentResult> compartments;
  private final List<String> warnings;

  /**
   * Construct a settle-out pressure result.
   *
   * @param settleOutPressureBara common settle-out pressure in bara
   * @param settleOutTemperatureK settle-out temperature in K
   * @param settleOutZFactor compressibility factor at the settle-out condition
   * @param totalMoles total conserved inventory in mol
   * @param totalVolumeM3 combined connected volume in m3
   * @param maxCompartmentPressureBara highest initial compartment pressure in bara
   * @param minCompartmentPressureBara lowest initial compartment pressure in bara
   * @param protectedRatingBara protected-system design / relief set pressure in bara
   * @param ratingProvided true if the protected-system rating was supplied
   * @param marginToRatingBar protected rating minus settle-out pressure in bar; NaN when no rating supplied
   * @param exceedsRating true if the settle-out pressure exceeds the protected rating
   * @param verdict the screening verdict
   * @param compartments per-compartment inventory breakdown
   * @param warnings list of warnings; never null
   */
  public SettleOutPressureResult(double settleOutPressureBara, double settleOutTemperatureK, double settleOutZFactor,
      double totalMoles, double totalVolumeM3, double maxCompartmentPressureBara, double minCompartmentPressureBara,
      double protectedRatingBara, boolean ratingProvided, double marginToRatingBar, boolean exceedsRating,
      SettleOutVerdict verdict, List<CompartmentResult> compartments, List<String> warnings) {
    this.settleOutPressureBara = settleOutPressureBara;
    this.settleOutTemperatureK = settleOutTemperatureK;
    this.settleOutZFactor = settleOutZFactor;
    this.totalMoles = totalMoles;
    this.totalVolumeM3 = totalVolumeM3;
    this.maxCompartmentPressureBara = maxCompartmentPressureBara;
    this.minCompartmentPressureBara = minCompartmentPressureBara;
    this.protectedRatingBara = protectedRatingBara;
    this.ratingProvided = ratingProvided;
    this.marginToRatingBar = marginToRatingBar;
    this.exceedsRating = exceedsRating;
    this.verdict = verdict;
    this.compartments = compartments != null ? compartments : new ArrayList<CompartmentResult>();
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the common settle-out pressure.
   *
   * @return settle-out pressure in bara
   */
  public double getSettleOutPressureBara() {
    return settleOutPressureBara;
  }

  /**
   * Gets the settle-out temperature.
   *
   * @return settle-out temperature in K
   */
  public double getSettleOutTemperatureK() {
    return settleOutTemperatureK;
  }

  /**
   * Gets the compressibility factor at the settle-out condition.
   *
   * @return settle-out compressibility factor
   */
  public double getSettleOutZFactor() {
    return settleOutZFactor;
  }

  /**
   * Gets the total conserved inventory.
   *
   * @return total inventory in mol
   */
  public double getTotalMoles() {
    return totalMoles;
  }

  /**
   * Gets the combined connected volume.
   *
   * @return combined volume in m3
   */
  public double getTotalVolumeM3() {
    return totalVolumeM3;
  }

  /**
   * Gets the highest initial compartment pressure.
   *
   * @return maximum compartment pressure in bara
   */
  public double getMaxCompartmentPressureBara() {
    return maxCompartmentPressureBara;
  }

  /**
   * Gets the lowest initial compartment pressure.
   *
   * @return minimum compartment pressure in bara
   */
  public double getMinCompartmentPressureBara() {
    return minCompartmentPressureBara;
  }

  /**
   * Gets the protected-system design / relief set pressure.
   *
   * @return protected-system rating in bara
   */
  public double getProtectedRatingBara() {
    return protectedRatingBara;
  }

  /**
   * Indicates whether a protected-system rating was supplied.
   *
   * @return true if a rating was supplied
   */
  public boolean isRatingProvided() {
    return ratingProvided;
  }

  /**
   * Gets the margin between the protected rating and the settle-out pressure.
   *
   * @return margin in bar; negative when the rating is exceeded, NaN when no rating supplied
   */
  public double getMarginToRatingBar() {
    return marginToRatingBar;
  }

  /**
   * Indicates whether the settle-out pressure exceeds the protected rating.
   *
   * @return true if the rating is exceeded
   */
  public boolean isExceedsRating() {
    return exceedsRating;
  }

  /**
   * Gets the screening verdict.
   *
   * @return the settle-out verdict
   */
  public SettleOutVerdict getVerdict() {
    return verdict;
  }

  /**
   * Gets the per-compartment inventory breakdown.
   *
   * @return list of compartment results
   */
  public List<CompartmentResult> getCompartments() {
    return compartments;
  }

  /**
   * Gets the warnings raised during the screening.
   *
   * @return list of warnings; never null
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Serializes this result to a human-readable JSON string.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }

  /**
   * Inventory contribution of a single connected gas volume.
   */
  public static class CompartmentResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Compartment label. */
    public final String name;
    /** Volume in m3. */
    public final double volumeM3;
    /** Initial pressure in bara. */
    public final double pressureBara;
    /** Initial temperature in K. */
    public final double temperatureK;
    /** Compressibility factor at the initial state. */
    public final double zFactor;
    /** Molar inventory in mol. */
    public final double moles;

    /**
     * Construct a compartment result.
     *
     * @param name compartment label
     * @param volumeM3 volume in m3
     * @param pressureBara initial pressure in bara
     * @param temperatureK initial temperature in K
     * @param zFactor compressibility factor at the initial state
     * @param moles molar inventory in mol
     */
    public CompartmentResult(String name, double volumeM3, double pressureBara, double temperatureK, double zFactor,
        double moles) {
      this.name = name;
      this.volumeM3 = volumeM3;
      this.pressureBara = pressureBara;
      this.temperatureK = temperatureK;
      this.zFactor = zFactor;
      this.moles = moles;
    }
  }
}
