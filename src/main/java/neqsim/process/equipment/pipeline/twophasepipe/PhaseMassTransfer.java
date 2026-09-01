package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;

/**
 * Immutable phase-resolved flash mass-transfer sources for a pipe cell.
 *
 * <p>
 * Positive source values add mass to a phase and negative values remove mass. All values use SI units of kg/(m s). A
 * valid result conserves mass across gas, hydrocarbon liquid, and aqueous liquid to floating-point precision.
 * </p>
 */
public final class PhaseMassTransfer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double gasSourceKgPerMetreSecond;
  private final double oilSourceKgPerMetreSecond;
  private final double waterSourceKgPerMetreSecond;
  private final boolean flashConverged;
  private final boolean applicable;
  private final String errorMessage;

  /**
   * Create a phase-resolved mass-transfer result.
   *
   * @param gasSourceKgPerMetreSecond gas source in kg/(m s)
   * @param oilSourceKgPerMetreSecond hydrocarbon-liquid source in kg/(m s)
   * @param waterSourceKgPerMetreSecond aqueous-liquid source in kg/(m s)
   * @param flashConverged whether the equilibrium flash converged
   * @param applicable whether phase transfer was applicable at the evaluated state
   * @param errorMessage diagnostic message, or {@code null} when no error occurred
   */
  public PhaseMassTransfer(double gasSourceKgPerMetreSecond, double oilSourceKgPerMetreSecond,
      double waterSourceKgPerMetreSecond, boolean flashConverged, boolean applicable, String errorMessage) {
    if (!Double.isFinite(gasSourceKgPerMetreSecond) || !Double.isFinite(oilSourceKgPerMetreSecond)
        || !Double.isFinite(waterSourceKgPerMetreSecond)) {
      throw new IllegalArgumentException("Phase mass-transfer sources must be finite");
    }
    double scale = Math.max(1.0, Math.max(Math.abs(gasSourceKgPerMetreSecond),
        Math.max(Math.abs(oilSourceKgPerMetreSecond), Math.abs(waterSourceKgPerMetreSecond))));
    double residual = gasSourceKgPerMetreSecond + oilSourceKgPerMetreSecond + waterSourceKgPerMetreSecond;
    if (Math.abs(residual) > 1.0e-12 * scale) {
      throw new IllegalArgumentException("Phase mass-transfer sources must sum to zero");
    }
    this.gasSourceKgPerMetreSecond = gasSourceKgPerMetreSecond;
    this.oilSourceKgPerMetreSecond = oilSourceKgPerMetreSecond;
    this.waterSourceKgPerMetreSecond = waterSourceKgPerMetreSecond;
    this.flashConverged = flashConverged;
    this.applicable = applicable;
    this.errorMessage = errorMessage;
  }

  /**
   * Create a zero-source result.
   *
   * @param flashConverged whether the equilibrium flash converged
   * @param applicable whether phase transfer was applicable at the evaluated state
   * @param errorMessage diagnostic message, or {@code null}
   * @return immutable zero-source result
   */
  public static PhaseMassTransfer zero(boolean flashConverged, boolean applicable, String errorMessage) {
    return new PhaseMassTransfer(0.0, 0.0, 0.0, flashConverged, applicable, errorMessage);
  }

  /**
   * Get the gas source.
   *
   * @return gas source in kg/(m s)
   */
  public double getGasSourceKgPerMetreSecond() {
    return gasSourceKgPerMetreSecond;
  }

  /**
   * Get the hydrocarbon-liquid source.
   *
   * @return oil source in kg/(m s)
   */
  public double getOilSourceKgPerMetreSecond() {
    return oilSourceKgPerMetreSecond;
  }

  /**
   * Get the aqueous-liquid source.
   *
   * @return water source in kg/(m s)
   */
  public double getWaterSourceKgPerMetreSecond() {
    return waterSourceKgPerMetreSecond;
  }

  /**
   * Get the sum of all phase sources.
   *
   * @return total source in kg/(m s), nominally zero
   */
  public double getTotalSourceKgPerMetreSecond() {
    return gasSourceKgPerMetreSecond + oilSourceKgPerMetreSecond + waterSourceKgPerMetreSecond;
  }

  /**
   * Check whether the flash converged.
   *
   * @return {@code true} when the equilibrium flash converged
   */
  public boolean isFlashConverged() {
    return flashConverged;
  }

  /**
   * Check whether the transfer calculation was applicable.
   *
   * @return {@code true} when the result applies to the evaluated state
   */
  public boolean isApplicable() {
    return applicable;
  }

  /**
   * Get the diagnostic error message.
   *
   * @return error message, or {@code null}
   */
  public String getErrorMessage() {
    return errorMessage;
  }
}
