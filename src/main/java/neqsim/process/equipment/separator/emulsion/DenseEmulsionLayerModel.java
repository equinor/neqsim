package neqsim.process.equipment.separator.emulsion;

/**
 * Immutable constant-area water-in-oil dense-emulsion-layer model of Zlobin et al. (2026).
 *
 * <p>
 * Implements the mechanical-equilibrium functions and stochastic interfacial coalescence law in equations (1)-(14), DOI
 * 10.1002/aic.70429. The conserved state is dispersed water volume divided by horizontal interface area, in metres.
 * This is not the total layer height: the layer also contains trapped continuous oil. The fitted microscopic
 * coalescence parameter must be supplied; it is not predicted from the equation of state.
 * </p>
 *
 * <p>
 * Assumptions are monodisperse droplets, constant properties and horizontal cross-section, immediate mechanical
 * equilibration, and coalescence predominantly with the underlying free-water phase. Initial monolayer formation, wall
 * effects, internal coalescence avalanches, outlet-level control, and industrial crude-oil qualification are outside
 * the model. Empty initialization uses its mathematical continuous limit; the paper's experimental comparisons start
 * after initial layers have formed. Nothing is installed on an equipment or process calculation path by this class.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class DenseEmulsionLayerModel {
  /** Continuous-oil fraction at monodisperse close packing, from the paper. */
  public static final double CLOSE_PACKING_OIL_FRACTION = 0.26;

  /** Dimensionless mechanical-equilibrium coefficient, from the paper. */
  public static final double PACKING_COEFFICIENT = 7.3;

  /** Standard acceleration due to gravity [m/s2]. */
  private static final double GRAVITY = 9.80665;

  /** Exact limiting contact area, printed as 1.106 in the paper. */
  private static final double MAX_CONTACT_AREA = Math.pow(1.0 - CLOSE_PACKING_OIL_FRACTION, -1.0 / 3.0);

  /** Normalization of the dimensionless dispersed-water depth. */
  private static final double DEPTH_NORMALIZATION = PACKING_COEFFICIENT * Math.pow(CLOSE_PACKING_OIL_FRACTION, 1.5);

  /** Four-point Gauss-Legendre abscissas for stable evaluation close to close packing. */
  private static final double[] QUADRATURE_NODES = {-0.8611363115940526, -0.3399810435848563, 0.3399810435848563,
      0.8611363115940526};

  /** Four-point Gauss-Legendre weights. */
  private static final double[] QUADRATURE_WEIGHTS = {0.3478548451374538, 0.6521451548625461, 0.6521451548625461,
      0.3478548451374538};

  /** Capillary-gravity length ell [m]. */
  private final double characteristicLengthM;

  /** Dispersed-water depth normalization [m]. */
  private final double waterDepthScaleM;

  /** Coalescence-flux prefactor (4 pi / 3) omega R cubed [m/s]. */
  private final double coalescenceScaleMPerS;

  /** Limiting interfacial coalescence capacity [m/s]. */
  private final double criticalFluxMPerS;

  /**
   * Creates a model with the paper's monodisperse packing parameters and lambda equal to one.
   *
   * @param gammaNPerM oil-water interfacial tension [N/m], strictly positive
   * @param deltaRhoKgPerM3 water density minus oil density [kg/m3], strictly positive
   * @param radiusM dispersed-water droplet radius [m], strictly positive; not diameter
   * @param omegaPerM2Second stochastic coalescence frequency per contact area [1/(m2 s)], nonnegative
   * @throws IllegalArgumentException if an input or derived scale is invalid or unrepresentable
   */
  public DenseEmulsionLayerModel(double gammaNPerM, double deltaRhoKgPerM3, double radiusM, double omegaPerM2Second) {
    requirePositive(gammaNPerM, "interfacial tension");
    requirePositive(deltaRhoKgPerM3, "water-oil density difference");
    requirePositive(radiusM, "droplet radius");
    requireNonnegative(omegaPerM2Second, "coalescence frequency");
    characteristicLengthM = gammaNPerM / deltaRhoKgPerM3 / GRAVITY / radiusM;
    requirePositive(characteristicLengthM, "derived capillary-gravity length");
    waterDepthScaleM = characteristicLengthM * DEPTH_NORMALIZATION;
    requirePositive(waterDepthScaleM, "derived dispersed-water depth scale");
    coalescenceScaleMPerS = omegaPerM2Second == 0.0 ? 0.0
        : (4.0 * Math.PI / 3.0) * omegaPerM2Second * radiusM * radiusM * radiusM;
    if (omegaPerM2Second > 0.0) {
      requirePositive(coalescenceScaleMPerS, "derived coalescence-flux scale");
    }
    criticalFluxMPerS = coalescenceScaleMPerS * MAX_CONTACT_AREA;
    requireNonnegative(criticalFluxMPerS, "derived critical flux");
  }

  /**
   * Gets the capillary-gravity length gamma / (deltaRho g radius).
   *
   * @return characteristic length [m]
   */
  public double getCharacteristicLengthM() {
    return characteristicLengthM;
  }

  /**
   * Gets the maximum incoming dispersed-water flux admitting a finite stationary layer.
   *
   * @return critical dispersed-water volume flux per horizontal interface area [m/s]
   */
  public double getCriticalFluxMPerS() {
    return criticalFluxMPerS;
  }

  /**
   * Calculates the total layer height from its conserved dispersed-water inventory.
   *
   * @param dispersedWaterDepthM dispersed-water volume divided by interface area [m]
   * @return total water-plus-trapped-oil layer height [m]
   * @throws IllegalArgumentException if the inventory or result is invalid or unrepresentable
   */
  public double getLayerHeightM(double dispersedWaterDepthM) {
    requireNonnegative(dispersedWaterDepthM, "dispersed-water depth");
    if (dispersedWaterDepthM == 0.0) {
      return 0.0;
    }
    double normalizedDepth = dispersedWaterDepthM / waterDepthScaleM;
    requirePositive(normalizedDepth, "normalized dispersed-water depth");
    double height;
    if (normalizedDepth < 1.0e-28) {
      // The exact continuous limit is accurate to double precision at this scale.
      height = dispersedWaterDepthM / (1.0 - CLOSE_PACKING_OIL_FRACTION);
    } else if (normalizedDepth < 1.0e-4) {
      double gap = solveSquareRootFractionGap(normalizedDepth);
      double delta = CLOSE_PACKING_OIL_FRACTION * gap * (2.0 - gap);
      height = characteristicLengthM * integrateHeightNearClosePacking(delta);
    } else {
      double squareRootFraction = solveSquareRootFraction(normalizedDepth);
      double y = Math.sqrt(CLOSE_PACKING_OIL_FRACTION) * squareRootFraction;
      double a = Math.sqrt(CLOSE_PACKING_OIL_FRACTION);
      double coefficient = 3.0 - 2.0 * CLOSE_PACKING_OIL_FRACTION
          - CLOSE_PACKING_OIL_FRACTION * CLOSE_PACKING_OIL_FRACTION;
      double logarithm = Math.log1p(y) - Math.log1p(-y) + Math.log1p(-a) - Math.log1p(a);
      // F-G has a finite limit as phi tends to zero; this avoids subtracting huge inventories.
      double trappedOil = PACKING_COEFFICIENT * (3.0 * a - a * a * a + (2.0 * CLOSE_PACKING_OIL_FRACTION - 3.0) * y
          - y * y * y + 0.5 * coefficient * logarithm);
      height = dispersedWaterDepthM + characteristicLengthM * trappedOil;
    }
    requirePositive(height, "calculated layer height");
    return height;
  }

  /**
   * Calculates coalescence into the free-water phase using equation (10).
   *
   * @param dispersedWaterDepthM dispersed-water volume divided by interface area [m]
   * @return coalesced-water volume flux per interface area [m/s]
   * @throws IllegalArgumentException if the inventory is invalid or unrepresentable
   */
  public double getCoalescenceFluxMPerS(double dispersedWaterDepthM) {
    requireNonnegative(dispersedWaterDepthM, "dispersed-water depth");
    if (dispersedWaterDepthM == 0.0 || coalescenceScaleMPerS == 0.0) {
      return 0.0;
    }
    double normalizedDepth = dispersedWaterDepthM / waterDepthScaleM;
    requirePositive(normalizedDepth, "normalized dispersed-water depth");
    return coalescenceScaleMPerS * contactAreaFromNormalizedDepth(normalizedDepth);
  }

  /**
   * Calculates the finite stationary layer by balancing incoming water and interfacial coalescence.
   *
   * @param incomingFluxMPerS incoming dispersed-water volume per interface area and time [m/s]
   * @return stationary layer height [m], zero for zero incoming flux, or positive infinity when incoming flux is at or
   * above the critical value
   * @throws IllegalArgumentException if the incoming flux or finite result is unrepresentable
   */
  public double getSteadyLayerHeightM(double incomingFluxMPerS) {
    requireNonnegative(incomingFluxMPerS, "incoming dispersed-water flux");
    if (incomingFluxMPerS == 0.0) {
      return 0.0;
    }
    if (incomingFluxMPerS >= criticalFluxMPerS) {
      return Double.POSITIVE_INFINITY;
    }
    double targetArea = incomingFluxMPerS / coalescenceScaleMPerS;
    double upper = Math.min(1.0, 4.0 * targetArea / DEPTH_NORMALIZATION);
    requirePositive(upper, "stationary normalized inventory bracket");
    while (contactAreaFromNormalizedDepth(upper) < targetArea) {
      upper *= 2.0;
      requirePositive(upper, "stationary normalized inventory bracket");
    }
    double lower = 0.0;
    for (int i = 0; i < 100; i++) {
      double midpoint = lower + 0.5 * (upper - lower);
      if (midpoint == lower || midpoint == upper) {
        break;
      }
      if (contactAreaFromNormalizedDepth(midpoint) < targetArea) {
        lower = midpoint;
      } else {
        upper = midpoint;
      }
    }
    return getLayerHeightM(waterDepthScaleM * (lower + 0.5 * (upper - lower)));
  }

  /**
   * Advances conserved dispersed-water inventory with a monotone implicit-Euler step.
   *
   * <p>
   * Solves x1 + dt qc(x1) = x0 + dt qin. The unique nonnegative solution is bounded by the water budget; cumulative
   * coalesced water is that budget minus x1. The method is first-order accurate and unconditionally positivity
   * preserving. Refine the time step to assess transient accuracy.
   * </p>
   *
   * @param initialDispersedWaterDepthM initial dispersed-water volume per interface area [m]
   * @param incomingFluxMPerS constant incoming dispersed-water flux during this step [m/s]
   * @param dtSeconds elapsed time [s], nonnegative
   * @return final dispersed-water volume per interface area [m]
   * @throws IllegalArgumentException if inputs or their finite-time water budget are unrepresentable
   */
  public double advance(double initialDispersedWaterDepthM, double incomingFluxMPerS, double dtSeconds) {
    requireNonnegative(initialDispersedWaterDepthM, "initial dispersed-water depth");
    requireNonnegative(incomingFluxMPerS, "incoming dispersed-water flux");
    requireNonnegative(dtSeconds, "time step");
    double incomingDepth = incomingFluxMPerS * dtSeconds;
    double budget = initialDispersedWaterDepthM + incomingDepth;
    requireNonnegative(budget, "finite-time water budget");
    if (dtSeconds == 0.0 || budget == 0.0 || coalescenceScaleMPerS == 0.0) {
      return budget;
    }
    double maximumCoalescedDepth = criticalFluxMPerS * dtSeconds;
    requireNonnegative(maximumCoalescedDepth, "finite-time coalescence capacity");
    double initialSlope = coalescenceScaleMPerS / characteristicLengthM / (2.0 * (1.0 - CLOSE_PACKING_OIL_FRACTION));
    requirePositive(initialSlope, "initial coalescence-flux slope");
    double slopeTime = initialSlope * dtSeconds;
    double lower = Double.isInfinite(slopeTime) ? budget / dtSeconds / initialSlope : budget / (1.0 + slopeTime);
    lower = Math.max(lower, Math.max(0.0, budget - maximumCoalescedDepth));
    double upper = budget;
    for (int i = 0; i < 110; i++) {
      double midpoint = lower > 0.0 ? Math.sqrt(lower) * Math.sqrt(upper) : 0.5 * upper;
      if (midpoint <= lower || midpoint >= upper) {
        break;
      }
      // Compare without summing two potentially very large positive quantities.
      if (dtSeconds * getCoalescenceFluxMPerS(midpoint) > budget - midpoint) {
        upper = midpoint;
      } else {
        lower = midpoint;
      }
    }
    return Math.max(0.0, Math.min(budget, lower + 0.5 * (upper - lower)));
  }

  /**
   * Evaluates F(phi) in the paper from the local bottom oil volume fraction.
   *
   * @param bottomOilFraction local continuous-oil volume fraction, strictly positive and at most 0.26
   * @return dimensionless layer height L/ell
   * @throws IllegalArgumentException if the fraction is outside the mechanical-equilibrium domain
   */
  public static double dimensionlessLayerHeight(double bottomOilFraction) {
    requireOilFraction(bottomOilFraction);
    double delta = CLOSE_PACKING_OIL_FRACTION - bottomOilFraction;
    if (delta < 0.01 * CLOSE_PACKING_OIL_FRACTION) {
      return integrateHeightNearClosePacking(delta);
    }
    double a = Math.sqrt(CLOSE_PACKING_OIL_FRACTION);
    double y = Math.sqrt(bottomOilFraction);
    double logarithm = Math.log1p(y) - Math.log1p(-y) + Math.log1p(-a) - Math.log1p(a);
    double coefficient = 3.0 - 2.0 * CLOSE_PACKING_OIL_FRACTION
        - CLOSE_PACKING_OIL_FRACTION * CLOSE_PACKING_OIL_FRACTION;
    return PACKING_COEFFICIENT * ((a - y) * (3.0 + a * a * a / y) + 0.5 * coefficient * logarithm);
  }

  /**
   * Evaluates the dimensionless contact area in equation (3).
   *
   * @param bottomOilFraction local continuous-oil volume fraction, from zero through 0.26
   * @return dimensionless contact area, with its exact limiting value at zero oil fraction
   * @throws IllegalArgumentException if the fraction is outside the mechanical-equilibrium domain
   */
  public static double dimensionlessContactArea(double bottomOilFraction) {
    requireNonnegative(bottomOilFraction, "bottom oil fraction");
    if (bottomOilFraction > CLOSE_PACKING_OIL_FRACTION) {
      throw new IllegalArgumentException("Bottom oil fraction must not exceed 0.26");
    }
    if (bottomOilFraction == 0.0) {
      return MAX_CONTACT_AREA;
    }
    double delta = CLOSE_PACKING_OIL_FRACTION - bottomOilFraction;
    double numerator = PACKING_COEFFICIENT * delta * delta;
    return Math.pow((1.0 - bottomOilFraction) / (1.0 - CLOSE_PACKING_OIL_FRACTION), 1.0 / 3.0) * numerator
        / (numerator + 2.0 * (1.0 - bottomOilFraction) * Math.sqrt(bottomOilFraction));
  }

  /**
   * Evaluates contact area using G, avoiding cancellation in (phi_c-phi) near an empty layer.
   *
   * @param normalizedDepth G divided by K phi_c to the power 3/2
   * @return dimensionless contact area
   */
  private static double contactAreaFromNormalizedDepth(double normalizedDepth) {
    double squareRootFraction = solveSquareRootFraction(normalizedDepth);
    double oilFraction = CLOSE_PACKING_OIL_FRACTION * squareRootFraction * squareRootFraction;
    double g = normalizedDepth * DEPTH_NORMALIZATION;
    double oilComplement = 1.0 - oilFraction;
    return Math.pow(oilComplement / (1.0 - CLOSE_PACKING_OIL_FRACTION), 1.0 / 3.0) / (1.0 + 2.0 * oilComplement / g);
  }

  /**
   * Inverts G using the square-root oil fraction scaled by its close-packing value.
   *
   * @param normalizedDepth G divided by K phi_c to the power 3/2
   * @return square root of phi/phi_c
   */
  private static double solveSquareRootFraction(double normalizedDepth) {
    if (normalizedDepth < 0.01) {
      return 1.0 - solveSquareRootFractionGap(normalizedDepth);
    }
    double lower = 0.0;
    double upper = Math.min(1.0, 1.0 / normalizedDepth);
    for (int i = 0; i < 80; i++) {
      double midpoint = lower + 0.5 * (upper - lower);
      if (midpoint == lower || midpoint == upper) {
        break;
      }
      double complement = 1.0 - midpoint * midpoint;
      if (complement * complement / midpoint > normalizedDepth) {
        lower = midpoint;
      } else {
        upper = midpoint;
      }
    }
    return lower + 0.5 * (upper - lower);
  }

  /**
   * Resolves tiny compaction without subtracting indistinguishable near-unity oil fractions.
   *
   * @param normalizedDepth G divided by K phi_c to the power 3/2, less than 0.01
   * @return one minus the square root of phi/phi_c
   */
  private static double solveSquareRootFractionGap(double normalizedDepth) {
    double lower = 0.0;
    double upper = Math.sqrt(normalizedDepth);
    for (int i = 0; i < 80; i++) {
      double midpoint = lower + 0.5 * (upper - lower);
      if (midpoint == lower || midpoint == upper) {
        break;
      }
      double product = midpoint * (2.0 - midpoint);
      if (product * product / (1.0 - midpoint) < normalizedDepth) {
        lower = midpoint;
      } else {
        upper = midpoint;
      }
    }
    return lower + 0.5 * (upper - lower);
  }

  /**
   * Integrates the exact derivative -F'(phi) where the closed form suffers subtractive cancellation.
   *
   * @param delta positive difference phi_c-phi, no greater than one percent of phi_c
   * @return dimensionless layer height
   */
  private static double integrateHeightNearClosePacking(double delta) {
    double sum = 0.0;
    for (int i = 0; i < QUADRATURE_NODES.length; i++) {
      double u = 0.5 * delta * (1.0 + QUADRATURE_NODES[i]);
      double phi = CLOSE_PACKING_OIL_FRACTION - u;
      double integrand = PACKING_COEFFICIENT * u * (CLOSE_PACKING_OIL_FRACTION + 3.0 * phi)
          / (2.0 * phi * Math.sqrt(phi) * (1.0 - phi));
      sum += QUADRATURE_WEIGHTS[i] * integrand;
    }
    return 0.5 * delta * sum;
  }

  /**
   * Checks a positive finite physical value.
   *
   * @param value value to check
   * @param name physical name for error reporting
   * @throws IllegalArgumentException if the value is not finite and strictly positive
   */
  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and strictly positive");
    }
  }

  /**
   * Checks a nonnegative finite physical value.
   *
   * @param value value to check
   * @param name physical name for error reporting
   * @throws IllegalArgumentException if the value is not finite and nonnegative
   */
  private static void requireNonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /**
   * Checks the mechanical-equilibrium oil-fraction domain.
   *
   * @param value local bottom continuous-oil fraction
   * @throws IllegalArgumentException if the value is not finite or outside (0, 0.26]
   */
  private static void requireOilFraction(double value) {
    requirePositive(value, "bottom oil fraction");
    if (value > CLOSE_PACKING_OIL_FRACTION) {
      throw new IllegalArgumentException("Bottom oil fraction must not exceed 0.26");
    }
  }
}
