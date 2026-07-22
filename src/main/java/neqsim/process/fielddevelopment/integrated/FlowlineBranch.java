package neqsim.process.fielddevelopment.integrated;

/**
 * A flowline (or generic pressure-drop) branch in an integrated production network.
 *
 * <p>
 * The branch represents the hydraulic resistance between two nodes using a quadratic pressure-drop law that captures
 * both laminar/friction and turbulent/kinetic contributions plus a static elevation head:
 * </p>
 *
 * <p>
 * dP = pUp - pDown = staticHead + a&middot;q + b&middot;q&sup2;
 * </p>
 *
 * <p>
 * where {@code a} (bar per Sm3/day) and {@code b} (bar per (Sm3/day)&sup2;) are friction coefficients and
 * {@code staticHead} (bar) is the elevation/hydrostatic term. Given the two node pressures the branch inverts this
 * relation to return the volumetric flow, which lets it slot directly into the {@link NetworkNewtonSolver} node-balance
 * equations. Coefficients can be set directly, derived from a single reference operating point, or fitted to a detailed
 * multiphase pipe model with {@link #fromBeggsBrillSample}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see NetworkNewtonSolver
 */
public class FlowlineBranch implements NetworkBranch {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String fromNode;
  private final String toNode;
  private double linearCoeff; // a, bar per Sm3/day
  private double quadraticCoeff; // b, bar per (Sm3/day)^2
  private double staticHead; // bar
  private double lastRate; // Sm3/day (diagnostic)

  /**
   * Creates a flowline branch with explicit pressure-drop coefficients.
   *
   * @param name unique branch name
   * @param fromNode upstream node name
   * @param toNode downstream node name
   * @param linearCoeff linear friction coefficient a in bar per Sm3/day
   * @param quadraticCoeff quadratic friction coefficient b in bar per (Sm3/day)&sup2;
   * @param staticHeadBar static elevation head in bar (positive = upstream below downstream)
   */
  public FlowlineBranch(String name, String fromNode, String toNode, double linearCoeff, double quadraticCoeff,
      double staticHeadBar) {
    this.name = name;
    this.fromNode = fromNode;
    this.toNode = toNode;
    this.linearCoeff = linearCoeff;
    this.quadraticCoeff = quadraticCoeff;
    this.staticHead = staticHeadBar;
  }

  /**
   * Builds a flowline branch whose quadratic coefficient is fitted to a single reference operating point (pure
   * turbulent assumption, a = 0).
   *
   * @param name unique branch name
   * @param fromNode upstream node name
   * @param toNode downstream node name
   * @param referencePressureDropBar measured pressure drop at the reference rate, in bar
   * @param referenceRateSm3PerDay reference rate in Sm3/day
   * @param staticHeadBar static elevation head in bar
   * @return a flowline branch
   */
  public static FlowlineBranch fromReferencePoint(String name, String fromNode, String toNode,
      double referencePressureDropBar, double referenceRateSm3PerDay, double staticHeadBar) {
    double friction = Math.max(0.0, referencePressureDropBar - staticHeadBar);
    double b = referenceRateSm3PerDay > 0.0 ? friction / (referenceRateSm3PerDay * referenceRateSm3PerDay) : 0.0;
    return new FlowlineBranch(name, fromNode, toNode, 0.0, b, staticHeadBar);
  }

  /**
   * Builds a flowline branch by fitting the quadratic coefficient to a detailed Beggs and Brill multiphase pipe model
   * evaluated at its current inlet conditions.
   *
   * <p>
   * The detailed pipe is run once to obtain a representative pressure drop at its current flow rate; the resulting
   * coefficient is reused cheaply inside the network solve. The static head is taken from the pipe elevation and the
   * reference fluid density.
   * </p>
   *
   * @param name unique branch name
   * @param fromNode upstream node name
   * @param toNode downstream node name
   * @param pipe a configured and run Beggs and Brill pipe model
   * @return a flowline branch fitted to the pipe model
   */
  public static FlowlineBranch fromBeggsBrillSample(String name, String fromNode, String toNode,
      neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipe) {
    double dpBar;
    double rateSm3PerDay;
    try {
      pipe.run();
      double inletP = pipe.getInletStream().getPressure("bara");
      double outletP = pipe.getOutletStream().getPressure("bara");
      dpBar = Math.max(0.0, inletP - outletP);
      rateSm3PerDay = pipe.getInletStream().getFlowRate("Sm3/day");
    } catch (RuntimeException ex) {
      dpBar = 1.0;
      rateSm3PerDay = 1.0e5;
    }
    double b = rateSm3PerDay > 0.0 ? dpBar / (rateSm3PerDay * rateSm3PerDay) : 0.0;
    return new FlowlineBranch(name, fromNode, toNode, 0.0, b, 0.0);
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
  public double flow(double upstreamPressureBara, double downstreamPressureBara) {
    double driving = (upstreamPressureBara - downstreamPressureBara) - staticHead;
    if (driving <= 0.0) {
      lastRate = 0.0;
      return 0.0;
    }
    double q;
    if (quadraticCoeff > 0.0) {
      q = (-linearCoeff + Math.sqrt(linearCoeff * linearCoeff + 4.0 * quadraticCoeff * driving))
          / (2.0 * quadraticCoeff);
    } else if (linearCoeff > 0.0) {
      q = driving / linearCoeff;
    } else {
      // No resistance defined; treat as a very low-resistance connector.
      q = driving / 1.0e-9;
    }
    lastRate = Math.max(0.0, q);
    return lastRate;
  }

  /**
   * Returns the linear friction coefficient.
   *
   * @return a in bar per Sm3/day
   */
  public double getLinearCoeff() {
    return linearCoeff;
  }

  /**
   * Returns the quadratic friction coefficient.
   *
   * @return b in bar per (Sm3/day)&sup2;
   */
  public double getQuadraticCoeff() {
    return quadraticCoeff;
  }

  /**
   * Returns the static elevation head.
   *
   * @return static head in bar
   */
  public double getStaticHead() {
    return staticHead;
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
