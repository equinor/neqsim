package neqsim.process.fielddevelopment.integrated;

/**
 * Volumetric (depletion) gas-reservoir drive based on the p/z material balance.
 *
 * <p>
 * For a dry/wet gas reservoir without water influx the p/z material balance is linear in cumulative
 * production:
 * </p>
 *
 * <p>
 * p/z = (p_i/z_i) (1 - G_p / G)
 * </p>
 *
 * <p>
 * where {@code p_i} and {@code z_i} are the initial pressure and gas deviation factor, {@code G} is
 * the initial gas in place (GIIP, surface Sm3) and {@code G_p} is the cumulative gas produced. A
 * constant average {@code z} is used here for a fast surrogate suitable for screening and
 * integrated network coupling; the pressure therefore declines essentially linearly with recovery.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ReservoirDrive
 */
public class MaterialBalanceGasDrive implements ReservoirDrive {
  private static final long serialVersionUID = 1000L;

  private final double initialPressure; // bara
  private final double giipSm3; // initial gas in place, surface Sm3
  private final double zFactor; // average gas deviation factor
  private double cumulativeProduction; // Sm3

  /**
   * Creates a volumetric gas-drive model.
   *
   * @param initialPressureBara initial reservoir pressure in bara
   * @param giipSm3 initial gas in place in surface Sm3
   * @param averageZ average gas deviation factor (dimensionless, &gt; 0)
   */
  public MaterialBalanceGasDrive(double initialPressureBara, double giipSm3, double averageZ) {
    if (initialPressureBara <= 0.0 || giipSm3 <= 0.0 || averageZ <= 0.0) {
      throw new IllegalArgumentException("pressure, GIIP and z must be positive");
    }
    this.initialPressure = initialPressureBara;
    this.giipSm3 = giipSm3;
    this.zFactor = averageZ;
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    double recovery = cumulativeProduction / giipSm3;
    double pOverZ = (initialPressure / zFactor) * (1.0 - recovery);
    double p = pOverZ * zFactor;
    return Math.max(0.0, p);
  }

  /** {@inheritDoc} */
  @Override
  public void produce(double producedVolumeSm3, double dtDays) {
    if (producedVolumeSm3 > 0.0) {
      cumulativeProduction += producedVolumeSm3;
      if (cumulativeProduction > giipSm3) {
        cumulativeProduction = giipSm3;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getCumulativeProduction() {
    return cumulativeProduction;
  }

  /** {@inheritDoc} */
  @Override
  public double getInPlaceVolume() {
    return giipSm3;
  }
}
