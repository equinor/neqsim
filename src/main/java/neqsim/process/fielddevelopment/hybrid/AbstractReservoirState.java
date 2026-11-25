package neqsim.process.fielddevelopment.hybrid;

/**
 * Base class of reservoir-state proxies: keeps the cumulative produced and injected volumes.
 *
 * @author ESOL
 * @version 1.0
 */
public abstract class AbstractReservoirState implements ReservoirStateModel {
  private static final long serialVersionUID = 1000L;
  private final String name;
  private final double[] cumulative = new double[CumulativeQuantity.values().length];

  /**
   * Creates a proxy with zero cumulative volumes.
   *
   * @param name name of the well or drainage unit
   */
  protected AbstractReservoirState(String name) {
    this.name = name;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public void produce(double oilSm3, double waterSm3, double gasSm3) {
    cumulative[CumulativeQuantity.OIL_PRODUCED.ordinal()] += oilSm3;
    cumulative[CumulativeQuantity.WATER_PRODUCED.ordinal()] += waterSm3;
    cumulative[CumulativeQuantity.GAS_PRODUCED.ordinal()] += gasSm3;
    cumulative[CumulativeQuantity.LIQUID_PRODUCED.ordinal()] += oilSm3 + waterSm3;
  }

  /** {@inheritDoc} */
  @Override
  public void inject(double waterSm3, double gasSm3) {
    cumulative[CumulativeQuantity.WATER_INJECTED.ordinal()] += waterSm3;
    cumulative[CumulativeQuantity.GAS_INJECTED.ordinal()] += gasSm3;
  }

  /** {@inheritDoc} */
  @Override
  public void elapse(double days) {
    cumulative[CumulativeQuantity.ELAPSED_TIME.ordinal()] += days;
  }

  /** {@inheritDoc} */
  @Override
  public double getCumulative(CumulativeQuantity quantity) {
    return cumulative[quantity.ordinal()];
  }

  /**
   * Sets one cumulative volume, for example to start a model part-way through field life.
   *
   * @param quantity cumulative quantity
   * @param valueSm3 cumulative volume in Sm3
   */
  public void setCumulative(CumulativeQuantity quantity, double valueSm3) {
    cumulative[quantity.ordinal()] = valueSm3;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getCumulativeState() {
    return cumulative.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setCumulativeState(double[] state) {
    if (state == null || state.length != cumulative.length) {
      throw new IllegalArgumentException("Cumulative state must have " + cumulative.length + " values");
    }
    System.arraycopy(state, 0, cumulative, 0, cumulative.length);
  }
}
