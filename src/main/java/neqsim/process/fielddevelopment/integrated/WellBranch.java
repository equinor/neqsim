package neqsim.process.fielddevelopment.integrated;

/**
 * A well branch in an integrated production network.
 *
 * <p>
 * A well branch connects a fixed-pressure reservoir node to a downstream node (typically a wellhead or a manifold). Its
 * deliverability is represented by a {@link WellDeliverabilityCurve} (a reduced IPR + VLP surrogate). The branch is
 * reservoir-pressure aware: the curve is built at a reference reservoir pressure and the delivered rate scales with the
 * actual reservoir node pressure, so the same branch can be reused as the reservoir depletes during a
 * production-profile run.
 * </p>
 *
 * <p>
 * An optional choke factor (0..1) and a gas-lift uplift factor (&ge; 1) multiply the deliverability, allowing chokes
 * and gas lift to participate directly in the network equilibrium.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 * @see WellDeliverabilityCurve
 */
public class WellBranch implements NetworkBranch {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String fromNode;
  private final String toNode;
  private final WellDeliverabilityCurve curve;
  private double referenceReservoirPressure; // bara
  private double chokeFactor = 1.0; // 0..1
  private double liftFactor = 1.0; // >= 1.0, gas-lift deliverability uplift
  private double lastRate; // Sm3/day (diagnostic)

  /**
   * Creates a well branch.
   *
   * @param name unique branch name
   * @param reservoirNode upstream reservoir node name
   * @param downstreamNode downstream node name (wellhead/manifold)
   * @param curve well deliverability curve
   * @param referenceReservoirPressureBara reservoir pressure at which the curve was built, in bara
   */
  public WellBranch(String name, String reservoirNode, String downstreamNode, WellDeliverabilityCurve curve,
      double referenceReservoirPressureBara) {
    this.name = name;
    this.fromNode = reservoirNode;
    this.toNode = downstreamNode;
    this.curve = curve;
    this.referenceReservoirPressure = referenceReservoirPressureBara;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public String getFromNode() {
    return fromNode;
  }

  /** {@inheritDoc} */
  @Override
  public String getToNode() {
    return toNode;
  }

  /** {@inheritDoc} */
  @Override
  public double flow(double reservoirPressureBara, double downstreamPressureBara) {
    if (downstreamPressureBara >= reservoirPressureBara) {
      lastRate = 0.0;
      return 0.0;
    }
    double base = curve.rateAt(downstreamPressureBara);
    double scale = referenceReservoirPressure > 0.0 ? reservoirPressureBara / referenceReservoirPressure : 1.0;
    double q = base * scale * chokeFactor * liftFactor;
    lastRate = Math.max(0.0, q);
    return lastRate;
  }

  /**
   * Sets the choke factor (fraction of full deliverability passed by the choke).
   *
   * @param factor choke factor in [0, 1]
   */
  public void setChokeFactor(double factor) {
    this.chokeFactor = Math.max(0.0, Math.min(1.0, factor));
  }

  /**
   * Returns the choke factor.
   *
   * @return choke factor in [0, 1]
   */
  public double getChokeFactor() {
    return chokeFactor;
  }

  /**
   * Sets the gas-lift deliverability uplift factor (1.0 = no lift).
   *
   * @param factor uplift factor, &ge; 1.0
   */
  public void setLiftFactor(double factor) {
    this.liftFactor = Math.max(1.0, factor);
  }

  /**
   * Returns the gas-lift uplift factor.
   *
   * @return uplift factor, &ge; 1.0
   */
  public double getLiftFactor() {
    return liftFactor;
  }

  /**
   * Updates the reservoir pressure used to scale the deliverability curve.
   *
   * @param referenceReservoirPressureBara reference reservoir pressure in bara
   */
  public void setReferenceReservoirPressure(double referenceReservoirPressureBara) {
    this.referenceReservoirPressure = referenceReservoirPressureBara;
  }

  /**
   * Returns the deliverability curve.
   *
   * @return deliverability curve
   */
  public WellDeliverabilityCurve getCurve() {
    return curve;
  }

  /**
   * Returns the most recently evaluated rate (diagnostic).
   *
   * @return last rate in Sm3/day
   */
  public double getLastRate() {
    return lastRate;
  }
}
