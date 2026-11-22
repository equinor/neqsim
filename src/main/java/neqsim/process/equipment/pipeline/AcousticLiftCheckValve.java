package neqsim.process.equipment.pipeline;

import java.io.Serializable;

/**
 * Ideal translating, equal-pressure-area check valve for single-liquid acoustic screening.
 *
 * <p>
 * Pressure work is reciprocal: total through-flow is signed orifice flow plus effective area times lift velocity. This
 * is not a swing-disc, hydrodynamic flow-force, added-mass, rebound or cavitation model. Parameters must be
 * independently justified for an actual valve. Backward Euler uses the previous accepted lift for orifice area.
 * Perfectly inelastic contact is localized on the discrete implicit trajectory, not an exact continuous trajectory.
 *
 * @author NeqSim
 * @version 1.0
 * @see WaterHammerPipe
 */
public final class AcousticLiftCheckValve implements Serializable {
  /** Serialization identifier. */
  private static final long serialVersionUID = 1L;
  /** Effective translating mass, kg. */
  private final double mass;
  /** Linear damping, N s/m. */
  private final double damping;
  /** Linear stiffness, N/m. */
  private final double stiffness;
  /** Closing preload, N. */
  private final double preload;
  /** Equal effective pressure and swept-volume area, m2. */
  private final double pressureArea;
  /** Orifice discharge coefficient. */
  private final double dischargeCoefficient;
  /** Orifice area per unit lift, m. */
  private final double width;
  /** Upper stop, m. */
  private final double maximumLift;
  /** Initial equilibrium lift, m. */
  private final double initialLift;
  /** Declared source and applicability. */
  private final String provenance;

  /**
   * Construct immutable SI parameters; the initial velocity is zero.
   *
   * @param mass effective mass, positive kg
   * @param damping damping, nonnegative N s/m
   * @param stiffness stiffness, nonnegative N/m
   * @param preload closing preload, nonnegative N
   * @param pressureArea equal pressure/swept area, positive m2
   * @param dischargeCoefficient positive coefficient at most one
   * @param width orifice area per lift, positive m
   * @param maximumLift positive upper stop, m
   * @param initialLift initial lift between seat and upper stop, m
   * @param provenance nonempty source/applicability declaration
   * @throws IllegalArgumentException for nonfinite or nonphysical parameters
   */
  public AcousticLiftCheckValve(double mass, double damping, double stiffness, double preload, double pressureArea,
      double dischargeCoefficient, double width, double maximumLift, double initialLift, String provenance) {
    positive(mass, "effective mass");
    nonnegative(damping, "damping");
    nonnegative(stiffness, "stiffness");
    nonnegative(preload, "preload");
    positive(pressureArea, "pressure area");
    positive(dischargeCoefficient, "discharge coefficient");
    positive(width, "orifice width");
    positive(maximumLift, "maximum lift");
    nonnegative(initialLift, "initial lift");
    if (initialLift > maximumLift || dischargeCoefficient > 1.0 || provenance == null || provenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Declare bounded lift, discharge coefficient and source");
    }
    this.mass = mass;
    this.damping = damping;
    this.stiffness = stiffness;
    this.preload = preload;
    this.pressureArea = pressureArea;
    this.dischargeCoefficient = dischargeCoefficient;
    this.width = width;
    this.maximumLift = maximumLift;
    this.initialLift = initialLift;
    this.provenance = provenance;
    finite(energy(initialLift, 0.0), "initial mechanical energy");
  }

  /**
   * Get the initial equilibrium position.
   *
   * @return lift, m
   */
  public double getInitialLift() {
    return initialLift;
  }

  /**
   * Get the upper stop.
   *
   * @return maximum lift, m
   */
  public double getMaximumLift() {
    return maximumLift;
  }

  /**
   * Get the declared source and applicability.
   *
   * @return provenance declaration
   */
  public String getProvenance() {
    return provenance;
  }

  /**
   * Evaluate mechanical storage with zero reference at the stationary seat.
   *
   * @param lift position, m
   * @param velocity velocity, m/s
   * @return kinetic plus spring/preload energy, J
   */
  double energy(double lift, double velocity) {
    return 0.5 * mass * velocity * velocity + 0.5 * stiffness * lift * lift + preload * lift;
  }

  /**
   * Evaluate signed orifice flow; pressure work is always nonnegative.
   *
   * @param lift physical lift, m
   * @param pressureDifference upstream minus receiver pressure, Pa
   * @param density actual liquid density, kg/m3
   * @return signed jet flow, m3/s
   */
  double jetFlow(double lift, double pressureDifference, double density) {
    positive(density, "actual liquid density");
    finite(pressureDifference, "pressure difference");
    double flow = coefficient(lift, density)
        * Math.copySign(Math.sqrt(Math.abs(pressureDifference)), pressureDifference);
    finite(flow, "jet flow");
    return flow;
  }

  /**
   * Evaluate reciprocal swept flow at a fixed velocity.
   *
   * @param velocity translating velocity, m/s
   * @return signed displacement flow, m3/s
   */
  double sweptFlow(double velocity) {
    return pressureArea * velocity;
  }

  /**
   * Check the declared initial static flow and force balance without fitting parameters.
   *
   * @param pressureDifference initial pressure difference, Pa
   * @param flow actual initial flow, m3/s
   * @param density actual liquid density, kg/m3
   */
  void validateInitial(double pressureDifference, double flow, double density) {
    double predicted = jetFlow(initialLift, pressureDifference, density);
    double force = pressureArea * pressureDifference - preload - stiffness * initialLift;
    if (initialLift <= 0.0 || flow <= 0.0 || pressureDifference <= 0.0
        || Math.abs(predicted - flow) > 1e-9 + 1e-6 * Math.abs(flow)
        || Math.abs(force) > 1e-10 + 1e-8 * Math.abs(pressureArea * pressureDifference)) {
      throw new IllegalArgumentException(
          "Initial lift flow/force must match declared feed and receiver; no calibration");
    }
  }

  /**
   * Solve instantaneous pressure with fixed disc velocity for a reported endpoint trace.
   *
   * @param drive incoming pressure characteristic minus receiver, Pa
   * @param resistance pressure/flow acoustic impedance, Pa s/m3
   * @param density liquid density, kg/m3
   * @param lift accepted position, m
   * @param velocity accepted velocity, m/s
   * @return compatible pressure difference, Pa
   */
  double tracePressure(double drive, double resistance, double density, double lift, double velocity) {
    return pressureRoot(drive - resistance * pressureArea * velocity, 1.0, resistance * coefficient(lift, density));
  }

  /**
   * Find an admissible implicit interval, shortening at the first detected discrete bound crossing. The accepted
   * numerical path has constant endpoint velocity over each interval; grazing continuous motion needs refinement and is
   * not claimed to be event-exact.
   *
   * @param drive incoming characteristic minus receiver, Pa
   * @param resistance acoustic impedance, Pa s/m3
   * @param density liquid density, kg/m3
   * @param lift previous accepted lift, m
   * @param velocity previous accepted velocity, m/s
   * @param duration maximum interval, s
   * @return tentative state and work; no external state is changed
   */
  Step step(double drive, double resistance, double density, double lift, double velocity, double duration) {
    positive(duration, "lift interval");
    double stationaryPressure = tracePressure(drive, resistance, density, lift, 0.0);
    double force = pressureArea * stationaryPressure - preload - stiffness * lift;
    if (velocity == 0.0 && (lift == 0.0 && force <= 0.0 || lift == maximumLift && force >= 0.0)) {
      return new Step(duration, lift, 0.0, stationaryPressure, jetFlow(lift, stationaryPressure, density), 0.0, 0.0,
          0.0, 0.0, false);
    }
    Step trial = freeStep(drive, resistance, density, lift, velocity, duration);
    boolean lowerContact = trial.lift < 0.0 || trial.lift == 0.0 && trial.velocity < 0.0;
    boolean upperContact = trial.lift > maximumLift || trial.lift == maximumLift && trial.velocity > 0.0;
    if (!lowerContact && !upperContact) {
      return trial;
    }
    double stop = lowerContact ? 0.0 : maximumLift;
    double lo = 0.0;
    double hi = duration;
    for (int iteration = 0; iteration < 100; iteration++) {
      double middle = 0.5 * lo + 0.5 * hi;
      if (middle == lo || middle == hi) {
        break;
      }
      trial = freeStep(drive, resistance, density, lift, velocity, middle);
      if ((stop == 0.0 && trial.lift > stop) || (stop == maximumLift && trial.lift < stop)) {
        lo = middle;
      } else {
        hi = middle;
      }
    }
    trial = freeStep(drive, resistance, density, lift, velocity, hi);
    if (Math.abs(trial.lift - stop) > 1e-14 * Math.max(1.0, maximumLift) || hi <= 0.0) {
      throw new IllegalStateException("Lift contact root unresolved; interval not accepted");
    }
    return new Step(hi, stop, 0.0, trial.pressure, trial.jetFlow, trial.sweptFlow, trial.dampingLoss,
        trial.numericalLoss, 0.5 * mass * trial.velocity * trial.velocity, true);
  }

  /**
   * Solve the unique compatible free pressure/flow/velocity root with lagged orifice area.
   *
   * @param drive pressure characteristic minus receiver, Pa
   * @param resistance acoustic impedance, Pa s/m3
   * @param density liquid density, kg/m3
   * @param lift old position, m
   * @param velocity old velocity, m/s
   * @param duration interval, s
   * @return unconstrained tentative update
   */
  private Step freeStep(double drive, double resistance, double density, double lift, double velocity,
      double duration) {
    double denominator = mass + damping * duration + stiffness * duration * duration;
    positive(denominator, "implicit lift denominator");
    double constant = (mass * velocity - duration * (preload + stiffness * lift)) / denominator;
    double slope = duration * pressureArea / denominator;
    double pressure = pressureRoot(drive - resistance * pressureArea * constant,
        1.0 + resistance * pressureArea * slope, resistance * coefficient(lift, density));
    double nextVelocity = constant + slope * pressure;
    double nextLift = lift + duration * nextVelocity;
    double jet = jetFlow(lift, pressure, density);
    double swept = pressureArea * nextVelocity;
    double physical = damping * duration * nextVelocity * nextVelocity;
    double algorithmic = 0.5 * mass * (nextVelocity - velocity) * (nextVelocity - velocity)
        + 0.5 * stiffness * (nextLift - lift) * (nextLift - lift);
    double residual = pressure + resistance * (jet + swept) - drive;
    if (!Double.isFinite(residual)
        || Math.abs(residual) > 1e-8 + 1e-10 * Math.max(Math.abs(drive), Math.abs(pressure))) {
      throw new IllegalStateException("Lift acoustic characteristic residual unresolved; interval not accepted");
    }
    return new Step(duration, nextLift, nextVelocity, pressure, jet, swept, physical, algorithmic, 0.0, false);
  }

  /**
   * Solve alpha*p + beta*sign(p)*sqrt(abs(p)) = forcing without subtractive cancellation.
   *
   * @param forcing signed pressure forcing, Pa
   * @param alpha strictly positive linear coefficient
   * @param beta nonnegative square-root coefficient
   * @return signed pressure difference, Pa
   */
  private static double pressureRoot(double forcing, double alpha, double beta) {
    finite(forcing, "lift pressure forcing");
    positive(alpha, "lift pressure coefficient");
    nonnegative(beta, "lift orifice coefficient");
    double magnitude = Math.abs(forcing);
    double argument = alpha * magnitude;
    finite(argument, "lift root argument");
    double root = beta == 0.0 ? forcing / alpha
        : Math.copySign(Math.pow(magnitude / (0.5 * beta + 0.5 * Math.hypot(beta, 2.0 * Math.sqrt(argument))), 2.0),
            forcing);
    finite(root, "lift pressure root");
    return root;
  }

  /**
   * Compute lagged orifice flow coefficient.
   *
   * @param lift previous accepted lift, m
   * @param density liquid density, kg/m3
   * @return coefficient, m3/(s sqrt(Pa))
   */
  private double coefficient(double lift, double density) {
    positive(density, "liquid density");
    if (!Double.isFinite(lift) || lift < 0.0 || lift > maximumLift) {
      throw new IllegalStateException("Accepted lift outside physical stops");
    }
    return dischargeCoefficient * width * lift * Math.sqrt(2.0 / density);
  }

  /**
   * Require a finite value.
   *
   * @param value numerical value
   * @param name diagnostic name
   */
  private static void finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Nonfinite " + name + "; interval not accepted");
    }
  }

  /**
   * Require a finite positive input.
   *
   * @param value input value
   * @param name diagnostic name
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Require a finite nonnegative input.
   *
   * @param value input value
   * @param name diagnostic name
   */
  private static void nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Tentative interval values, committed only after every hydraulic cell and endpoint is validated.
   *
   * @author NeqSim
   * @version 1.0
   */
  static final class Step {
    /** Actual interval duration, s. */
    final double duration;
    /** Post-contact position, m. */
    final double lift;
    /** Post-contact velocity, m/s. */
    final double velocity;
    /** Held pressure difference, Pa. */
    final double pressure;
    /** Accepted signed orifice flow, m3/s. */
    final double jetFlow;
    /** Accepted swept flow before impact, m3/s. */
    final double sweptFlow;
    /** Physical damping loss, J. */
    final double dampingLoss;
    /** Explicit backward-Euler numerical loss, J. */
    final double numericalLoss;
    /** Perfectly inelastic contact loss, J. */
    final double impactLoss;
    /** True when the interval ends in contact. */
    final boolean contact;

    /**
     * Capture tentative finite interval values.
     *
     * @param duration duration, s
     * @param lift post-contact lift, m
     * @param velocity post-contact velocity, m/s
     * @param pressure held differential pressure, Pa
     * @param jetFlow signed orifice flow, m3/s
     * @param sweptFlow displacement flow, m3/s
     * @param dampingLoss physical damping, J
     * @param numericalLoss algorithmic loss, J
     * @param impactLoss inelastic impact, J
     * @param contact whether contact occurs
     */
    Step(double duration, double lift, double velocity, double pressure, double jetFlow, double sweptFlow,
        double dampingLoss, double numericalLoss, double impactLoss, boolean contact) {
      for (double value : new double[] {duration, lift, velocity, pressure, jetFlow, sweptFlow, dampingLoss,
          numericalLoss, impactLoss}) {
        finite(value, "tentative lift state/work");
      }
      this.duration = duration;
      this.lift = lift;
      this.velocity = velocity;
      this.pressure = pressure;
      this.jetFlow = jetFlow;
      this.sweptFlow = sweptFlow;
      this.dampingLoss = dampingLoss;
      this.numericalLoss = numericalLoss;
      this.impactLoss = impactLoss;
      this.contact = contact;
    }
  }
}
