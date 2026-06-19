package neqsim.process.fielddevelopment.integrated;

/**
 * Gas-reservoir drive with steady-state aquifer water influx (Fetkovich-style support).
 *
 * <p>
 * Pure volumetric depletion ({@link MaterialBalanceGasDrive}) overestimates pressure decline when
 * an aquifer provides pressure support. This model adds a Fetkovich steady-state influx term: the
 * water influx rate is proportional to the drawdown between the aquifer and the reservoir,
 * </p>
 *
 * <p>
 * q_w = J_aq (p_aq - p_res)
 * </p>
 *
 * <p>
 * and the cumulative influx partially replaces the produced gas voidage, slowing the p/z decline.
 * The aquifer pressure itself is depleted by the cumulative influx relative to its capacity. The
 * model remains a fast linearised surrogate suitable for integrated network coupling.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see ReservoirDrive
 * @see MaterialBalanceGasDrive
 */
public class AquiferDrive implements ReservoirDrive {
  private static final long serialVersionUID = 1000L;

  private final double initialPressure; // bara
  private final double giipSm3; // initial gas in place, surface Sm3
  private final double zFactor; // average gas deviation factor
  private final double aquiferProductivity; // Sm3/day per bar drawdown (gas-equivalent voidage)
  private final double aquiferCapacitySm3; // equivalent gas-voidage capacity of the aquifer
  private double cumulativeProduction; // Sm3
  private double cumulativeInflux; // Sm3 gas-equivalent voidage replaced
  private double reservoirPressure; // bara

  /**
   * Creates an aquifer-supported gas drive.
   *
   * @param initialPressureBara initial reservoir pressure in bara
   * @param giipSm3 initial gas in place in surface Sm3
   * @param averageZ average gas deviation factor (dimensionless, &gt; 0)
   * @param aquiferProductivity aquifer influx index in Sm3/day of gas-equivalent voidage per bar
   * @param aquiferCapacitySm3 total gas-equivalent voidage the aquifer can supply, in Sm3
   */
  public AquiferDrive(double initialPressureBara, double giipSm3, double averageZ,
      double aquiferProductivity, double aquiferCapacitySm3) {
    if (initialPressureBara <= 0.0 || giipSm3 <= 0.0 || averageZ <= 0.0) {
      throw new IllegalArgumentException("pressure, GIIP and z must be positive");
    }
    this.initialPressure = initialPressureBara;
    this.giipSm3 = giipSm3;
    this.zFactor = averageZ;
    this.aquiferProductivity = Math.max(0.0, aquiferProductivity);
    this.aquiferCapacitySm3 = Math.max(0.0, aquiferCapacitySm3);
    this.reservoirPressure = initialPressureBara;
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    return Math.max(0.0, reservoirPressure);
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
    // Aquifer pressure declines as it gives up voidage.
    double aquiferDepletion =
        aquiferCapacitySm3 > 0.0 ? cumulativeInflux / aquiferCapacitySm3 : 1.0;
    double aquiferPressure = initialPressure * (1.0 - Math.min(1.0, aquiferDepletion));
    double drawdown = Math.max(0.0, aquiferPressure - reservoirPressure);
    double influxStep = aquiferProductivity * drawdown * Math.max(0.0, dtDays);
    double remainingCapacity = Math.max(0.0, aquiferCapacitySm3 - cumulativeInflux);
    influxStep = Math.min(influxStep, remainingCapacity);
    cumulativeInflux += influxStep;
    // Net voidage = produced gas not replaced by aquifer influx.
    double netVoidage = Math.max(0.0, cumulativeProduction - cumulativeInflux);
    double recovery = netVoidage / giipSm3;
    double pOverZ = (initialPressure / zFactor) * (1.0 - Math.min(1.0, recovery));
    reservoirPressure = Math.max(0.0, pOverZ * zFactor);
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

  /**
   * Returns the cumulative aquifer influx (gas-equivalent voidage).
   *
   * @return cumulative influx in Sm3
   */
  public double getCumulativeInflux() {
    return cumulativeInflux;
  }
}
