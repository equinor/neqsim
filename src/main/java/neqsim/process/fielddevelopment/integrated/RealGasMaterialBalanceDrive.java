package neqsim.process.fielddevelopment.integrated;

/**
 * Real-gas p/z material-balance drive with pressure support.
 *
 * <p>
 * The volumetric balance is {@code p/z = (p/z)_i (1 - (1 - s) Gp / G)} where {@code s} is the fraction of withdrawn
 * voidage replaced by water influx (0 for pure depletion). Unlike {@link MaterialBalanceGasDrive}, the gas deviation
 * factor follows the reservoir pressure through a {@link GasZFactor}, so the pressure-versus-recovery path is curved
 * the way the real p/z line implies. Fit {@code (p/z)_i} and {@code G} to field data with
 * {@link MaterialBalanceHistoryMatch}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ReservoirDrive
 * @see MaterialBalanceHistoryMatch
 */
public class RealGasMaterialBalanceDrive implements ReservoirDrive {
  private static final long serialVersionUID = 1000L;

  private final double initialPOverZ;
  private final double giipSm3;
  private final double temperatureK;
  private final GasZFactor zFactor;
  private final double support;
  private double cumulativeProduction;

  /**
   * Creates the drive.
   *
   * @param initialPOverZBara initial p/z in bara
   * @param giipSm3 gas initially in place in surface Sm3
   * @param reservoirTemperatureK reservoir temperature in K
   * @param zFactor deviation-factor model
   * @param supportFraction fraction of withdrawn voidage replaced by influx, 0 &le; s &lt; 1
   */
  public RealGasMaterialBalanceDrive(double initialPOverZBara, double giipSm3, double reservoirTemperatureK,
      GasZFactor zFactor, double supportFraction) {
    if (initialPOverZBara <= 0.0 || giipSm3 <= 0.0 || reservoirTemperatureK <= 0.0 || zFactor == null) {
      throw new IllegalArgumentException("p/z, GIIP, temperature and z model must be valid");
    }
    if (supportFraction < 0.0 || supportFraction >= 1.0) {
      throw new IllegalArgumentException("support fraction must be in [0, 1)");
    }
    this.initialPOverZ = initialPOverZBara;
    this.giipSm3 = giipSm3;
    this.temperatureK = reservoirTemperatureK;
    this.zFactor = zFactor;
    this.support = supportFraction;
  }

  /**
   * Returns the current p/z.
   *
   * @return p/z in bara
   */
  public double getPOverZ() {
    double recovery = cumulativeProduction * (1.0 - support) / giipSm3;
    return initialPOverZ * Math.max(1.0 - recovery, 1.0e-6);
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    return pressureForPOverZ(getPOverZ());
  }

  /**
   * Solves {@code p / z(p) = target} for p by bisection.
   *
   * @param target p/z value in bara
   * @return pressure in bara
   */
  private double pressureForPOverZ(double target) {
    double lo = 0.0;
    double hi = Math.max(target * 2.0, 10.0);
    while (hi / zFactor.z(hi, temperatureK) < target && hi < 1.0e5) {
      hi *= 2.0;
    }
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      if (mid / zFactor.z(Math.max(mid, 1.0e-3), temperatureK) < target) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }

  /** {@inheritDoc} */
  @Override
  public void produce(double producedVolumeSm3, double dtDays) {
    if (producedVolumeSm3 > 0.0) {
      cumulativeProduction = Math.min(cumulativeProduction + producedVolumeSm3, giipSm3);
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
