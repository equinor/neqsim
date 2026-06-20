package neqsim.process.fielddevelopment.integrated;

/**
 * Undersaturated oil-reservoir depletion drive based on a single-tank compressibility balance.
 *
 * <p>
 * Above the bubble point an oil reservoir behaves as a slightly compressible tank. The pressure decline with cumulative
 * produced volume follows from the total-compressibility material balance:
 * </p>
 *
 * <p>
 * p = p_i - N_p / (N c_t)
 * </p>
 *
 * <p>
 * where {@code N} is the stock-tank oil in place (STOIIP, Sm3), {@code N_p} is the cumulative oil produced and
 * {@code c_t} is the effective total compressibility expressed per Sm3 of pore-volume basis. To keep inputs intuitive
 * this class is parameterised by a depletion gradient (bar of pressure drop per fraction of STOIIP recovered), which is
 * equivalent to {@code 1/(N c_t)} scaled by {@code N}. The pressure is clamped at a configurable abandonment floor.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ReservoirDrive
 */
public class OilTankDrive implements ReservoirDrive {
  private static final long serialVersionUID = 1000L;

  private final double initialPressure; // bara
  private final double stoiipSm3; // stock-tank oil in place, Sm3
  private final double depletionGradientBarPerFraction; // bar drop per fraction recovered
  private final double abandonmentPressure; // bara floor
  private double cumulativeProduction; // Sm3

  /**
   * Creates an oil-tank depletion drive.
   *
   * @param initialPressureBara initial reservoir pressure in bara
   * @param stoiipSm3 stock-tank oil in place in Sm3
   * @param depletionGradientBarPerFraction pressure drop in bar per unit fraction of STOIIP produced
   * @param abandonmentPressureBara minimum (abandonment) pressure in bara
   */
  public OilTankDrive(double initialPressureBara, double stoiipSm3, double depletionGradientBarPerFraction,
      double abandonmentPressureBara) {
    if (initialPressureBara <= 0.0 || stoiipSm3 <= 0.0) {
      throw new IllegalArgumentException("pressure and STOIIP must be positive");
    }
    this.initialPressure = initialPressureBara;
    this.stoiipSm3 = stoiipSm3;
    this.depletionGradientBarPerFraction = Math.max(0.0, depletionGradientBarPerFraction);
    this.abandonmentPressure = Math.max(0.0, abandonmentPressureBara);
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    double recovery = cumulativeProduction / stoiipSm3;
    double p = initialPressure - depletionGradientBarPerFraction * recovery;
    return Math.max(abandonmentPressure, p);
  }

  /** {@inheritDoc} */
  @Override
  public void produce(double producedVolumeSm3, double dtDays) {
    if (producedVolumeSm3 > 0.0) {
      cumulativeProduction += producedVolumeSm3;
      if (cumulativeProduction > stoiipSm3) {
	cumulativeProduction = stoiipSm3;
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
    return stoiipSm3;
  }
}
