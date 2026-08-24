package neqsim.thermo.util.solid;

import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Helmholtz-energy equation of state for hcp phase-I solid para-hydrogen.
 *
 * <p>
 * The model implements the Vinet, Debye, three Einstein, internal-vibration, and anharmonic terms reported by
 * Sannerhaugen (2026). Its stated range is temperatures below 200 K and pressures below 10 GPa. Derivatives are
 * propagated to second order with forward automatic differentiation, matching the hyperdual-number method used in the
 * thesis.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class ParaHydrogenSolidHelmholtzEquation implements SolidHelmholtzEquation {
  private static final long serialVersionUID = 1000L;

  /** Triple-point temperature in K. */
  public static final double TRIPLE_POINT_TEMPERATURE = 13.8033;
  /** Triple-point pressure in bara. */
  public static final double TRIPLE_POINT_PRESSURE = 0.07042;
  /** Experimental triple-point enthalpy of fusion in J/mol. */
  public static final double TRIPLE_POINT_ENTHALPY_OF_FUSION = 118.0;

  private static final double MAXIMUM_TEMPERATURE = 200.0;
  private static final double MAXIMUM_PRESSURE_BARA = 100000.0;
  private static final double V0 = 23.14e-6;
  private static final double B0 = 180.5e6;
  private static final double B0_PRIME = 7.131;
  private static final double THETA_D0 = 127.0;
  private static final double GAMMA_D0 = 2.843;
  private static final double Q_D = 1.647;
  private static final double A_D = 0.0008734;
  private static final double B_D = 0.9295;
  private static final double C_D = 1.020;
  private static final double[] EINSTEIN_WEIGHTS = { 0.09616, 0.1597, 0.004324 };
  private static final double[] EINSTEIN_TEMPERATURES = { 51.62, 247.0, 187.9 };
  private static final double[] EINSTEIN_GRUNEISEN = { 2.290, 0.9952, 1.367 };
  private static final double THETA_INTERNAL0 = 1086.0;
  private static final double GAMMA_INTERNAL = 2.775;
  private static final double EXTERNAL_MODES_PER_MOLECULE = 5.0;
  private static final double B1 = 0.08337;
  private static final double B2 = 22.97;
  private static final double B3 = -5.5412;
  private static final double C1 = -1.704;
  private static final double C2 = -0.08174;
  private static final double C3 = -0.03986;
  private static final double C4 = 3.828;
  private static final double MINIMUM_VOLUME = 1.0e-7;
  private static final double MAXIMUM_VOLUME = 1.0e-3;
  private static final int MAXIMUM_BRACKET_ITERATIONS = 160;
  private static final int MAXIMUM_SOLVER_ITERATIONS = 100;
  private static final double SOLVER_TOLERANCE = 1.0e-10;
  private static final int QUADRATURE_ORDER = 64;
  private static final double[] QUADRATURE_NODES = new double[QUADRATURE_ORDER];
  private static final double[] QUADRATURE_WEIGHTS = new double[QUADRATURE_ORDER];

  static {
    initializeGaussLegendreQuadrature();
  }

  private final double triplePointGibbsShift;
  private final double triplePointEntropyShift;

  /** Construct the raw thesis equation without a fluid-reference shift. */
  public ParaHydrogenSolidHelmholtzEquation() {
    this(0.0, 0.0);
  }

  /**
   * Construct the equation with the thesis triple-point reference adjustment.
   *
   * @param triplePointGibbsShift value added to solid Gibbs energy at the triple point in J/mol
   * @param triplePointEntropyShift value added to solid entropy in J/(mol K)
   */
  public ParaHydrogenSolidHelmholtzEquation(double triplePointGibbsShift, double triplePointEntropyShift) {
    if (!Double.isFinite(triplePointGibbsShift) || !Double.isFinite(triplePointEntropyShift)) {
      throw new IllegalArgumentException("Reference shifts must be finite.");
    }
    this.triplePointGibbsShift = triplePointGibbsShift;
    this.triplePointEntropyShift = triplePointEntropyShift;
  }

  /** {@inheritDoc} */
  @Override
  public SolidHelmholtzState evaluate(double temperature, double pressure) {
    validateState(temperature, pressure);
    double pressurePa = pressure * 1.0e5;
    double molarVolume = solveMolarVolume(temperature, pressurePa);
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    if (!(helmholtz.dvv > 0.0) || !Double.isFinite(helmholtz.dvv)) {
      throw new IllegalStateException("Solid para-hydrogen root is mechanically unstable.");
    }

    double entropy = -helmholtz.dt;
    double internalEnergy = helmholtz.value + temperature * entropy;
    double enthalpy = internalEnergy + pressurePa * molarVolume;
    double gibbsEnergy = helmholtz.value + pressurePa * molarVolume;
    double heatCapacityCv = -temperature * helmholtz.dtt;
    double heatCapacityCp = heatCapacityCv + temperature * helmholtz.dtv * helmholtz.dtv / helmholtz.dvv;
    double logFugacityCoefficient = gibbsEnergy / (ThermodynamicConstantsInterface.R * temperature)
        - Math.log(pressure);
    return new SolidHelmholtzState(molarVolume, helmholtz.value, internalEnergy, entropy, enthalpy, gibbsEnergy,
        heatCapacityCp, heatCapacityCv, logFugacityCoefficient);
  }

  /**
   * Calculate pressure directly at a specified temperature and molar volume.
   *
   * @param temperature temperature in K, greater than zero and no greater than 200 K
   * @param molarVolume molar volume in m3/mol, greater than zero
   * @return pressure in Pa
   */
  public double calculatePressure(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    return -calculateHelmholtzDerivatives(temperature, molarVolume).dv;
  }

  /**
   * Calculate molar Helmholtz energy at a specified temperature and molar volume.
   *
   * @param temperature temperature in K, greater than zero and no greater than 200 K
   * @param molarVolume molar volume in m3/mol, greater than zero
   * @return molar Helmholtz energy in J/mol, including the configured reference shift
   */
  public double calculateHelmholtzEnergy(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    return calculateHelmholtzDerivatives(temperature, molarVolume).value;
  }

  /**
   * Calculate the pressure contribution from the canonical Vinet cold curve.
   *
   * @param molarVolume molar volume in m3/mol, greater than zero
   * @return Vinet cold-curve pressure in Pa
   */
  double calculateVinetPressure(double molarVolume) {
    if (!(molarVolume > 0.0) || !Double.isFinite(molarVolume)) {
      throw new IllegalArgumentException("Molar volume must be finite and positive.");
    }
    Derivative2 reducedVolume = Derivative2.volume(molarVolume).multiply(1.0 / V0);
    return -calculateVinetEnergy(reducedVolume).dv;
  }

  /**
   * Return the configured triple-point Gibbs shift.
   *
   * @return Gibbs shift in J/mol
   */
  public double getTriplePointGibbsShift() {
    return triplePointGibbsShift;
  }

  /**
   * Return the configured triple-point entropy shift.
   *
   * @return entropy shift in J/(mol K)
   */
  public double getTriplePointEntropyShift() {
    return triplePointEntropyShift;
  }

  /** {@inheritDoc} */
  @Override
  public double getTriplePointPressure() {
    return TRIPLE_POINT_PRESSURE;
  }

  /**
   * Validate an evaluation state.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara
   */
  private static void validateState(double temperature, double pressure) {
    if (!(pressure > 0.0) || pressure > MAXIMUM_PRESSURE_BARA || !Double.isFinite(pressure)) {
      throw new IllegalArgumentException("Pressure must be above zero and no greater than 10 GPa.");
    }
    validateTemperatureAndVolume(temperature, V0);
  }

  /**
   * Validate temperature and molar volume inputs.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   */
  private static void validateTemperatureAndVolume(double temperature, double molarVolume) {
    if (!(temperature > 0.0) || temperature > MAXIMUM_TEMPERATURE || !Double.isFinite(temperature)) {
      throw new IllegalArgumentException("Temperature must be above zero and no greater than 200 K.");
    }
    if (!(molarVolume > 0.0) || !Double.isFinite(molarVolume)) {
      throw new IllegalArgumentException("Molar volume must be finite and positive.");
    }
  }

  /**
   * Solve molar volume from temperature and pressure in logarithmic volume.
   *
   * @param temperature temperature in K
   * @param pressurePa target pressure in Pa
   * @return molar volume in m3/mol
   */
  private double solveMolarVolume(double temperature, double pressurePa) {
    double guessedVolume = V0;
    if (temperature >= TRIPLE_POINT_TEMPERATURE) {
      double meltingVolume = (27.1788 - 0.283044 * temperature) * 1.0e-6;
      if (meltingVolume > MINIMUM_VOLUME) {
        guessedVolume = meltingVolume;
      }
    }

    double lower = Math.log(guessedVolume);
    double upper = lower;
    double lowerResidual = pressureResidual(temperature, lower, pressurePa);
    double upperResidual = lowerResidual;
    double expansion = Math.log(1.1);
    for (int iteration = 0; iteration < MAXIMUM_BRACKET_ITERATIONS
        && lowerResidual * upperResidual >= 0.0; iteration++) {
      lower = Math.max(Math.log(MINIMUM_VOLUME), lower - expansion);
      upper = Math.min(Math.log(MAXIMUM_VOLUME), upper + expansion);
      lowerResidual = pressureResidual(temperature, lower, pressurePa);
      upperResidual = pressureResidual(temperature, upper, pressurePa);
    }
    if (lowerResidual * upperResidual >= 0.0) {
      throw new IllegalStateException("Could not bracket the solid para-hydrogen volume root.");
    }

    double current = Math.min(upper, Math.max(lower, Math.log(guessedVolume)));
    for (int iteration = 0; iteration < MAXIMUM_SOLVER_ITERATIONS; iteration++) {
      double volume = Math.exp(current);
      Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, volume);
      double residual = -helmholtz.dv - pressurePa;
      if (Math.abs(residual) / Math.max(pressurePa, 1000.0) < SOLVER_TOLERANCE) {
        return volume;
      }

      double derivative = -helmholtz.dvv * volume;
      double candidate = current - residual / derivative;
      if (!(derivative < 0.0) || !Double.isFinite(candidate) || candidate <= lower || candidate >= upper) {
        candidate = 0.5 * (lower + upper);
      }
      double candidateResidual = pressureResidual(temperature, candidate, pressurePa);
      if (lowerResidual * candidateResidual <= 0.0) {
        upper = candidate;
        upperResidual = candidateResidual;
      } else {
        lower = candidate;
        lowerResidual = candidateResidual;
      }
      current = candidate;
    }
    throw new IllegalStateException("Solid para-hydrogen volume solver did not converge.");
  }

  /**
   * Evaluate the pressure residual in logarithmic volume.
   *
   * @param temperature temperature in K
   * @param logVolume natural logarithm of molar volume in m3/mol
   * @param pressurePa target pressure in Pa
   * @return pressure residual in Pa
   */
  private double pressureResidual(double temperature, double logVolume, double pressurePa) {
    return calculatePressure(temperature, Math.exp(logVolume)) - pressurePa;
  }

  /**
   * Evaluate Helmholtz energy and all first and second derivatives.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   * @return second-order Helmholtz derivative state
   */
  private Derivative2 calculateHelmholtzDerivatives(double temperature, double molarVolume) {
    Derivative2 temp = Derivative2.temperature(temperature);
    Derivative2 volume = Derivative2.volume(molarVolume);
    Derivative2 reducedVolume = volume.multiply(1.0 / V0);

    Derivative2 vinet = calculateVinetEnergy(reducedVolume);

    Derivative2 thetaDTemperature = temp.pow(2.0).multiply(-B_D).add(temp.pow(3.0).multiply(-C_D)).exp().subtract(1.0)
        .multiply(A_D).add(THETA_D0);
    Derivative2 thetaD = reducedVolume.pow(Q_D).multiply(-1.0).add(1.0).multiply(GAMMA_D0 / Q_D).exp()
        .multiply(thetaDTemperature);
    double einsteinWeightSum = 0.0;
    for (double weight : EINSTEIN_WEIGHTS) {
      einsteinWeightSum += weight;
    }
    Derivative2 debye = debyeFreeEnergy(thetaD.divide(temp)).multiply(temp)
        .multiply(ThermodynamicConstantsInterface.R * EXTERNAL_MODES_PER_MOLECULE * (1.0 - einsteinWeightSum));

    Derivative2 einstein = Derivative2.constant(0.0);
    for (int i = 0; i < EINSTEIN_WEIGHTS.length; i++) {
      Derivative2 theta = Derivative2.constant(1.0).subtract(reducedVolume).multiply(EINSTEIN_GRUNEISEN[i]).exp()
          .multiply(EINSTEIN_TEMPERATURES[i]);
      einstein = einstein.add(logOneMinusExpNegative(theta.divide(temp)).multiply(EINSTEIN_WEIGHTS[i]));
    }
    einstein = einstein.multiply(temp).multiply(ThermodynamicConstantsInterface.R * EXTERNAL_MODES_PER_MOLECULE);

    Derivative2 thetaInternal = Derivative2.constant(1.0).subtract(reducedVolume).multiply(GAMMA_INTERNAL).exp()
        .multiply(THETA_INTERNAL0);
    Derivative2 internal = logOneMinusExpNegative(thetaInternal.divide(temp)).multiply(temp)
        .multiply(ThermodynamicConstantsInterface.R);

    Derivative2 temperatureRatio = temp.multiply(1.0 / THETA_D0);
    Derivative2 anharmonicTemperature = temperatureRatio.pow(4.0)
        .divide(temperatureRatio.pow(2.0).multiply(B2).add(1.0)).multiply(thetaDTemperature).multiply(B1)
        .multiply(reducedVolume.subtract(1.0).multiply(B3).exp()).multiply(ThermodynamicConstantsInterface.R);
    Derivative2 anharmonicCold = Derivative2.constant(1.0).subtract(reducedVolume).multiply(C2).exp().multiply(C1)
        .add(reducedVolume.reciprocal().multiply(C3)
            .multiply(Derivative2.constant(1.0).subtract(reducedVolume).multiply(C4).exp()))
        .multiply(ThermodynamicConstantsInterface.R);

    Derivative2 referenceShift = temp.subtract(TRIPLE_POINT_TEMPERATURE).multiply(-triplePointEntropyShift)
        .add(triplePointGibbsShift);
    return vinet.add(debye).add(einstein).add(internal).add(anharmonicTemperature).add(anharmonicCold)
        .add(referenceShift);
  }

  /**
   * Evaluate the canonical Vinet cold-curve Helmholtz energy.
   *
   * @param reducedVolume molar volume divided by the zero-pressure volume
   * @return Vinet Helmholtz energy and volume derivatives
   */
  private static Derivative2 calculateVinetEnergy(Derivative2 reducedVolume) {
    Derivative2 x = reducedVolume.pow(1.0 / 3.0);
    double vinetExponentCoefficient = 1.5 * (B0_PRIME - 1.0);
    Derivative2 vinetExponent = Derivative2.constant(1.0).subtract(x).multiply(vinetExponentCoefficient);
    return vinetExponent.subtract(1.0).multiply(vinetExponent.exp()).add(1.0)
        .multiply(4.0 * B0 * V0 / Math.pow(B0_PRIME - 1.0, 2.0));
  }

  /**
   * Evaluate the dimensionless Debye Helmholtz function.
   *
   * @param reducedTemperature Debye temperature divided by temperature
   * @return dimensionless Debye Helmholtz function and derivatives
   */
  private static Derivative2 debyeFreeEnergy(Derivative2 reducedTemperature) {
    double z = reducedTemperature.value;
    if (!(z > 0.0) || !Double.isFinite(z)) {
      throw new IllegalStateException("Reduced Debye temperature must be finite and positive.");
    }
    double debye = debyeFunction3(z);
    double debyeFirst;
    double debyeSecond;
    if (z < 1.0e-3) {
      debyeFirst = -1.0 / 8.0 + z / 30.0 - Math.pow(z, 3.0) / 1260.0 + Math.pow(z, 5.0) / 45360.0;
      debyeSecond = 1.0 / 30.0 - Math.pow(z, 2.0) / 420.0 + Math.pow(z, 4.0) / 9072.0;
    } else {
      double q;
      double qFirst;
      if (z > 50.0) {
        double exponential = Math.exp(-z);
        q = exponential;
        qFirst = -exponential;
      } else {
        double denominator = Math.expm1(z);
        double exponential = Math.exp(z);
        q = 1.0 / denominator;
        qFirst = -exponential / (denominator * denominator);
      }
      debyeFirst = q - 3.0 * debye / z;
      debyeSecond = qFirst - 3.0 * debyeFirst / z + 3.0 * debye / (z * z);
    }

    double logarithm;
    double logarithmFirst;
    double logarithmSecond;
    if (z > 50.0) {
      double exponential = Math.exp(-z);
      logarithm = Math.log1p(-exponential);
      logarithmFirst = exponential;
      logarithmSecond = -exponential;
    } else {
      logarithm = Math.log(-Math.expm1(-z));
      double denominator = Math.expm1(z);
      logarithmFirst = 1.0 / denominator;
      logarithmSecond = -Math.exp(z) / (denominator * denominator);
    }
    return reducedTemperature.applyUnary(logarithm - debye, logarithmFirst - debyeFirst, logarithmSecond - debyeSecond);
  }

  /**
   * Evaluate the thesis third-order Debye function.
   *
   * @param z positive reduced Debye temperature
   * @return Debye function defined as the integral divided by z cubed
   */
  static double debyeFunction3(double z) {
    if (z < 1.0e-3) {
      return 1.0 / 3.0 - z / 8.0 + z * z / 60.0 - Math.pow(z, 4.0) / 5040.0 + Math.pow(z, 6.0) / 272160.0;
    }
    if (z > 50.0) {
      return Math.pow(Math.PI, 4.0) / (15.0 * Math.pow(z, 3.0));
    }
    double integral = 0.0;
    for (int i = 0; i < QUADRATURE_ORDER; i++) {
      double x = 0.5 * z * (QUADRATURE_NODES[i] + 1.0);
      integral += QUADRATURE_WEIGHTS[i] * x * x * x / Math.expm1(x);
    }
    return 0.5 * z * integral / Math.pow(z, 3.0);
  }

  /**
   * Evaluate the logarithmic harmonic-oscillator contribution.
   *
   * @param z positive characteristic temperature divided by temperature
   * @return logarithm of one minus exp(-z), with derivatives
   */
  private static Derivative2 logOneMinusExpNegative(Derivative2 z) {
    double value = z.value;
    if (!(value > 0.0) || !Double.isFinite(value)) {
      throw new IllegalStateException("Reduced oscillator temperature must be positive.");
    }
    if (value > 50.0) {
      double exponential = Math.exp(-value);
      return z.applyUnary(Math.log1p(-exponential), exponential, -exponential);
    }
    double denominator = Math.expm1(value);
    return z.applyUnary(Math.log(-Math.expm1(-value)), 1.0 / denominator,
        -Math.exp(value) / (denominator * denominator));
  }

  /** Initialize nodes and weights for 64-point Gauss-Legendre quadrature. */
  private static void initializeGaussLegendreQuadrature() {
    int midpoint = (QUADRATURE_ORDER + 1) / 2;
    for (int i = 0; i < midpoint; i++) {
      double root = Math.cos(Math.PI * (i + 0.75) / (QUADRATURE_ORDER + 0.5));
      double previous;
      double derivative = 0.0;
      do {
        previous = root;
        double p0 = 1.0;
        double p1 = root;
        for (int order = 2; order <= QUADRATURE_ORDER; order++) {
          double polynomial = ((2.0 * order - 1.0) * root * p1 - (order - 1.0) * p0) / order;
          p0 = p1;
          p1 = polynomial;
        }
        derivative = QUADRATURE_ORDER * (root * p1 - p0) / (root * root - 1.0);
        root = previous - p1 / derivative;
      } while (Math.abs(root - previous) > 1.0e-15);
      double weight = 2.0 / ((1.0 - root * root) * derivative * derivative);
      QUADRATURE_NODES[i] = -root;
      QUADRATURE_NODES[QUADRATURE_ORDER - 1 - i] = root;
      QUADRATURE_WEIGHTS[i] = weight;
      QUADRATURE_WEIGHTS[QUADRATURE_ORDER - 1 - i] = weight;
    }
  }

  /** Second-order value and derivatives with respect to temperature and molar volume. */
  private static final class Derivative2 {
    private final double value;
    private final double dt;
    private final double dv;
    private final double dtt;
    private final double dtv;
    private final double dvv;

    /**
     * Construct a second-order derivative value.
     *
     * @param value scalar value
     * @param dt first temperature derivative
     * @param dv first volume derivative
     * @param dtt second temperature derivative
     * @param dtv mixed temperature-volume derivative
     * @param dvv second volume derivative
     */
    private Derivative2(double value, double dt, double dv, double dtt, double dtv, double dvv) {
      this.value = value;
      this.dt = dt;
      this.dv = dv;
      this.dtt = dtt;
      this.dtv = dtv;
      this.dvv = dvv;
    }

    /**
     * Create a constant.
     *
     * @param value scalar value
     * @return constant derivative value
     */
    private static Derivative2 constant(double value) {
      return new Derivative2(value, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Create the temperature independent variable.
     *
     * @param value temperature value
     * @return temperature derivative value
     */
    private static Derivative2 temperature(double value) {
      return new Derivative2(value, 1.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Create the molar-volume independent variable.
     *
     * @param value molar-volume value
     * @return volume derivative value
     */
    private static Derivative2 volume(double value) {
      return new Derivative2(value, 0.0, 1.0, 0.0, 0.0, 0.0);
    }

    /**
     * Add another derivative value.
     *
     * @param other value to add
     * @return sum
     */
    private Derivative2 add(Derivative2 other) {
      return new Derivative2(value + other.value, dt + other.dt, dv + other.dv, dtt + other.dtt, dtv + other.dtv,
          dvv + other.dvv);
    }

    /**
     * Add a scalar.
     *
     * @param scalar value to add
     * @return sum
     */
    private Derivative2 add(double scalar) {
      return add(constant(scalar));
    }

    /**
     * Subtract another derivative value.
     *
     * @param other value to subtract
     * @return difference
     */
    private Derivative2 subtract(Derivative2 other) {
      return add(other.multiply(-1.0));
    }

    /**
     * Subtract a scalar.
     *
     * @param scalar value to subtract
     * @return difference
     */
    private Derivative2 subtract(double scalar) {
      return add(-scalar);
    }

    /**
     * Multiply by a scalar.
     *
     * @param scalar multiplier
     * @return product
     */
    private Derivative2 multiply(double scalar) {
      return new Derivative2(value * scalar, dt * scalar, dv * scalar, dtt * scalar, dtv * scalar, dvv * scalar);
    }

    /**
     * Multiply by another derivative value.
     *
     * @param other multiplier
     * @return product
     */
    private Derivative2 multiply(Derivative2 other) {
      return new Derivative2(value * other.value, dt * other.value + value * other.dt,
          dv * other.value + value * other.dv, dtt * other.value + 2.0 * dt * other.dt + value * other.dtt,
          dtv * other.value + dt * other.dv + dv * other.dt + value * other.dtv,
          dvv * other.value + 2.0 * dv * other.dv + value * other.dvv);
    }

    /**
     * Divide by another derivative value.
     *
     * @param other divisor
     * @return quotient
     */
    private Derivative2 divide(Derivative2 other) {
      return multiply(other.reciprocal());
    }

    /**
     * Calculate the reciprocal.
     *
     * @return reciprocal derivative value
     */
    private Derivative2 reciprocal() {
      return applyUnary(1.0 / value, -1.0 / (value * value), 2.0 / (value * value * value));
    }

    /**
     * Raise the value to a constant power.
     *
     * @param exponent constant exponent
     * @return powered derivative value
     */
    private Derivative2 pow(double exponent) {
      double result = Math.pow(value, exponent);
      return applyUnary(result, exponent * Math.pow(value, exponent - 1.0),
          exponent * (exponent - 1.0) * Math.pow(value, exponent - 2.0));
    }

    /**
     * Calculate the exponential.
     *
     * @return exponential derivative value
     */
    private Derivative2 exp() {
      double result = Math.exp(value);
      return applyUnary(result, result, result);
    }

    /**
     * Apply a scalar unary function using supplied first and second derivatives.
     *
     * @param functionValue scalar function value
     * @param firstDerivative scalar first derivative
     * @param secondDerivative scalar second derivative
     * @return composed derivative value
     */
    private Derivative2 applyUnary(double functionValue, double firstDerivative, double secondDerivative) {
      return new Derivative2(functionValue, firstDerivative * dt, firstDerivative * dv,
          secondDerivative * dt * dt + firstDerivative * dtt, secondDerivative * dt * dv + firstDerivative * dtv,
          secondDerivative * dv * dv + firstDerivative * dvv);
    }
  }
}
