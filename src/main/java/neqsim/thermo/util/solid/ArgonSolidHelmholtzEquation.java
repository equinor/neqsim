package neqsim.thermo.util.solid;

import java.util.Map;
import java.util.TreeMap;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * Fundamental Helmholtz-energy equation of state for face-centered-cubic solid argon.
 *
 * <p>
 * The implementation follows Maltby, Hammer, and Wilhelmsen, J. Phys. Chem. Ref. Data 53, 043102 (2024),
 * doi:10.1063/5.0237497. It combines a three-body-corrected Buckingham static lattice energy evaluated by the
 * coordination-sphere method, zero-point energy, Debye and Einstein vibrational contributions, and the reported
 * anharmonic corrections. The stated range is temperatures up to 300 K and pressures up to 16 GPa.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class ArgonSolidHelmholtzEquation implements SolidHelmholtzEquation {
  private static final long serialVersionUID = 1000L;

  /** Triple-point temperature in K. */
  public static final double TRIPLE_POINT_TEMPERATURE = 83.8058;
  /** Triple-point pressure in bara. */
  public static final double TRIPLE_POINT_PRESSURE = 0.68891;
  /** Entropy of melting at the triple point in J/(mol K). */
  public static final double TRIPLE_POINT_ENTROPY_OF_MELTING = 14.3;

  private static final double GAS_CONSTANT = 8.314462618;
  private static final double AVOGADRO_CONSTANT = 6.02214076e23;
  private static final double BOLTZMANN_CONSTANT = GAS_CONSTANT / AVOGADRO_CONSTANT;
  private static final double MAXIMUM_TEMPERATURE = 300.0;
  private static final double MAXIMUM_PRESSURE_BARA = 160000.0;

  private static final double EPSILON = BOLTZMANN_CONSTANT * 134.7;
  private static final double REPULSIVE_EXPONENT = 14.19;
  private static final double MINIMUM_POTENTIAL_DISTANCE = 3.802e-10;
  private static final double THREE_BODY_COEFFICIENT = BOLTZMANN_CONSTANT * 3.202e5 * 1.0e-90;

  private static final double A_D = 10.4;
  private static final double B_D = 0.02503;
  private static final double C_D = 0.0001568;
  private static final double V0 = 22.56e-6;
  private static final double THETA_D0 = 92.0;
  private static final double GAMMA_D0 = 2.563;
  private static final double Q_D = 0.2874;
  private static final double B1 = -0.0004475;
  private static final double B2 = 2.041e-6;
  private static final double B3 = 5.75e-7;
  private static final double C1 = 0.7204;
  private static final double C2 = -1.614;
  private static final double C3 = -0.01943;
  private static final double C4 = -27.64;
  private static final double[] EINSTEIN_WEIGHTS = { 0.0261, 0.03784, 0.04512 };
  private static final double[] EINSTEIN_TEMPERATURES = { 77.81, 550.0, 45.36 };
  private static final double[] EINSTEIN_GRUNEISEN = { 6.221, 1.617e-6, 3.1278 };

  private static final double Z1 = 140.0;
  private static final double Z2 = 2.34;
  private static final double Z3 = 0.683;
  private static final double Z4 = 19.7e-6;

  /*
   * Table SI.1 lists the FCC neighbours used by the coordination-sphere method. The published sample calculations are
   * reproduced by the first 20 physical shells, ending at r^2/r_NN^2 = 21 (the FCC sequence contains no shell at ratio
   * 14). Adding an independent continuum tail to the printed, rounded parameter set does not reproduce Table 8, so the
   * reference implementation keeps the same finite lattice sum as the publication calculation.
   */
  private static final int COORDINATION_SHELL_COUNT = 20;
  private static final int FCC_ENUMERATION_LIMIT = 24;
  private static final int[] FCC_SQUARED_DISTANCES = new int[COORDINATION_SHELL_COUNT];
  private static final int[] FCC_COORDINATION_NUMBERS = new int[COORDINATION_SHELL_COUNT];
  private static final double BUCKINGHAM_ZERO_DISTANCE = calculateBuckinghamZeroDistance();

  private static final double MINIMUM_VOLUME = 1.0e-7;
  private static final double MAXIMUM_VOLUME = 1.0e-3;
  private static final int MAXIMUM_BRACKET_ITERATIONS = 600;
  private static final int MAXIMUM_SOLVER_ITERATIONS = 100;
  private static final double SOLVER_TOLERANCE = 1.0e-9;
  private static final int QUADRATURE_ORDER = 64;
  private static final double[] QUADRATURE_NODES = new double[QUADRATURE_ORDER];
  private static final double[] QUADRATURE_WEIGHTS = new double[QUADRATURE_ORDER];

  static {
    initializeFccCoordinationShells();
    initializeGaussLegendreQuadrature();
  }

  private final double triplePointGibbsShift;
  private final double triplePointEntropyShift;

  /** Construct the raw published equation without its fluid-reference adjustment. */
  public ArgonSolidHelmholtzEquation() {
    this(0.0, 0.0);
  }

  /**
   * Construct the equation with the published triple-point reference adjustment.
   *
   * @param triplePointGibbsShift value added to solid Gibbs energy at the triple point in J/mol
   * @param triplePointEntropyShift value added to solid entropy in J/(mol K)
   */
  public ArgonSolidHelmholtzEquation(double triplePointGibbsShift, double triplePointEntropyShift) {
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
    return createState(temperature, pressurePa, molarVolume);
  }

  /**
   * Evaluate the equation directly at temperature and molar volume.
   *
   * @param temperature temperature in K, greater than zero and no greater than 300 K
   * @param molarVolume molar volume in m3/mol, greater than zero
   * @return evaluated solid state at the pressure implied by the Helmholtz derivative
   */
  public SolidHelmholtzState evaluateAtMolarVolume(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    double pressurePa = calculatePressure(temperature, molarVolume);
    if (!(pressurePa > 0.0) || pressurePa > MAXIMUM_PRESSURE_BARA * 1.0e5 || !Double.isFinite(pressurePa)) {
      throw new IllegalArgumentException(
          "The temperature-volume state must imply a pressure above zero and no greater than 16 GPa.");
    }
    return createState(temperature, pressurePa, molarVolume);
  }

  /**
   * Calculate pressure directly at a specified temperature and molar volume.
   *
   * @param temperature temperature in K, greater than zero and no greater than 300 K
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
   * @param temperature temperature in K, greater than zero and no greater than 300 K
   * @param molarVolume molar volume in m3/mol, greater than zero
   * @return molar Helmholtz energy in J/mol, including the configured reference adjustment
   */
  public double calculateHelmholtzEnergy(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    return calculateHelmholtzDerivatives(temperature, molarVolume).value;
  }

  /**
   * Calculate the volumetric thermal expansivity.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   * @return thermal expansivity in 1/K
   */
  public double calculateThermalExpansivity(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    validateMechanicalStability(helmholtz);
    return -helmholtz.dtv / (molarVolume * helmholtz.dvv);
  }

  /**
   * Calculate the thermal Gruneisen coefficient.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   * @return dimensionless thermal Gruneisen coefficient
   */
  public double calculateThermalGruneisenCoefficient(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    validateMechanicalStability(helmholtz);
    double heatCapacityCv = -temperature * helmholtz.dtt;
    double thermalExpansivity = -helmholtz.dtv / (molarVolume * helmholtz.dvv);
    double isothermalCompressibility = 1.0 / (molarVolume * helmholtz.dvv);
    return thermalExpansivity * molarVolume / (isothermalCompressibility * heatCapacityCv);
  }

  /**
   * Calculate isothermal compressibility.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   * @return isothermal compressibility in 1/Pa
   */
  public double calculateIsothermalCompressibility(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    validateMechanicalStability(helmholtz);
    return 1.0 / (molarVolume * helmholtz.dvv);
  }

  /**
   * Calculate isentropic compressibility.
   *
   * @param temperature temperature in K
   * @param molarVolume molar volume in m3/mol
   * @return isentropic compressibility in 1/Pa
   */
  public double calculateIsentropicCompressibility(double temperature, double molarVolume) {
    validateTemperatureAndVolume(temperature, molarVolume);
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    validateMechanicalStability(helmholtz);
    double heatCapacityCv = -temperature * helmholtz.dtt;
    double heatCapacityCp = heatCapacityCv + temperature * helmholtz.dtv * helmholtz.dtv / helmholtz.dvv;
    return heatCapacityCv / heatCapacityCp / (molarVolume * helmholtz.dvv);
  }

  /** @return configured triple-point Gibbs shift in J/mol */
  public double getTriplePointGibbsShift() {
    return triplePointGibbsShift;
  }

  /** @return configured triple-point entropy shift in J/(mol K) */
  public double getTriplePointEntropyShift() {
    return triplePointEntropyShift;
  }

  /** {@inheritDoc} */
  @Override
  public double getTriplePointPressure() {
    return TRIPLE_POINT_PRESSURE;
  }

  /**
   * Create a thermodynamic state from Helmholtz derivatives.
   *
   * @param temperature temperature in K
   * @param pressurePa pressure in Pa
   * @param molarVolume molar volume in m3/mol
   * @return solid Helmholtz state
   */
  private SolidHelmholtzState createState(double temperature, double pressurePa, double molarVolume) {
    Derivative2 helmholtz = calculateHelmholtzDerivatives(temperature, molarVolume);
    validateMechanicalStability(helmholtz);
    double entropy = -helmholtz.dt;
    double internalEnergy = helmholtz.value + temperature * entropy;
    double enthalpy = internalEnergy + pressurePa * molarVolume;
    double gibbsEnergy = helmholtz.value + pressurePa * molarVolume;
    double heatCapacityCv = -temperature * helmholtz.dtt;
    double heatCapacityCp = heatCapacityCv + temperature * helmholtz.dtv * helmholtz.dtv / helmholtz.dvv;
    double pressureBara = pressurePa / 1.0e5;
    double logFugacityCoefficient = gibbsEnergy / (ThermodynamicConstantsInterface.R * temperature)
        - Math.log(pressureBara);
    return new SolidHelmholtzState(molarVolume, helmholtz.value, internalEnergy, entropy, enthalpy, gibbsEnergy,
        heatCapacityCp, heatCapacityCv, logFugacityCoefficient);
  }

  /** Validate mechanical stability of a derivative state. */
  private static void validateMechanicalStability(Derivative2 helmholtz) {
    if (!(helmholtz.dvv > 0.0) || !Double.isFinite(helmholtz.dvv)) {
      throw new IllegalStateException("Solid argon state is mechanically unstable.");
    }
  }

  /** Validate an evaluation state. */
  private static void validateState(double temperature, double pressure) {
    if (!(pressure > 0.0) || pressure > MAXIMUM_PRESSURE_BARA || !Double.isFinite(pressure)) {
      throw new IllegalArgumentException("Pressure must be above zero and no greater than 16 GPa.");
    }
    validateTemperatureAndVolume(temperature, V0);
  }

  /** Validate temperature and molar-volume inputs. */
  private static void validateTemperatureAndVolume(double temperature, double molarVolume) {
    if (!(temperature > 0.0) || temperature > MAXIMUM_TEMPERATURE || !Double.isFinite(temperature)) {
      throw new IllegalArgumentException("Temperature must be above zero and no greater than 300 K.");
    }
    if (!(molarVolume > 0.0) || !Double.isFinite(molarVolume)) {
      throw new IllegalArgumentException("Molar volume must be finite and positive.");
    }
  }

  /** Solve molar volume from temperature and pressure in logarithmic volume. */
  private double solveMolarVolume(double temperature, double pressurePa) {
    double guessedVolume = V0;
    double lower = Math.log(guessedVolume);
    double upper = lower;
    double lowerResidual = pressureResidual(temperature, lower, pressurePa);
    double upperResidual = lowerResidual;
    // A fine scan is required near 16 GPa, where the physical Buckingham branch is narrow.
    double expansion = Math.log(1.02);
    if (lowerResidual > 0.0) {
      for (int iteration = 0; iteration < MAXIMUM_BRACKET_ITERATIONS && upperResidual > 0.0; iteration++) {
        lower = upper;
        lowerResidual = upperResidual;
        upper = Math.min(Math.log(MAXIMUM_VOLUME), upper + expansion);
        upperResidual = pressureResidual(temperature, upper, pressurePa);
      }
    } else {
      for (int iteration = 0; iteration < MAXIMUM_BRACKET_ITERATIONS && lowerResidual < 0.0; iteration++) {
        upper = lower;
        upperResidual = lowerResidual;
        lower = Math.max(Math.log(MINIMUM_VOLUME), lower - expansion);
        lowerResidual = pressureResidual(temperature, lower, pressurePa);
      }
    }
    if (lowerResidual * upperResidual >= 0.0) {
      throw new IllegalStateException("Could not bracket the solid argon volume root.");
    }

    double current = 0.5 * (lower + upper);
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
      if (upper - lower < 1.0e-14) {
        return Math.exp(0.5 * (lower + upper));
      }
    }
    throw new IllegalStateException("Solid argon volume solver did not converge.");
  }

  /** Return the pressure residual at logarithmic molar volume. */
  private double pressureResidual(double temperature, double logVolume, double pressurePa) {
    return calculatePressure(temperature, Math.exp(logVolume)) - pressurePa;
  }

  /** Evaluate Helmholtz energy and its first and second derivatives. */
  private Derivative2 calculateHelmholtzDerivatives(double temperature, double molarVolume) {
    Derivative2 temp = Derivative2.temperature(temperature);
    Derivative2 volume = Derivative2.volume(molarVolume);
    Derivative2 reducedVolume = volume.multiply(1.0 / V0);

    Derivative2 staticLattice = calculateStaticLatticeEnergy(volume);
    Derivative2 zeroPoint = reducedVolume.multiply(V0 / Z4).pow(Z3).multiply(-1.0).add(1.0).multiply(Z2 / Z3).exp()
        .multiply(Z1 * GAS_CONSTANT);

    Derivative2 thetaDTemperature = temp.pow(2.0).multiply(-B_D).add(temp.pow(3.0).multiply(-C_D)).exp().subtract(1.0)
        .multiply(A_D).add(THETA_D0);
    Derivative2 thetaD = reducedVolume.pow(Q_D).multiply(-1.0).add(1.0).multiply(GAMMA_D0 / Q_D).exp()
        .multiply(thetaDTemperature);
    double einsteinWeightSum = 0.0;
    for (double weight : EINSTEIN_WEIGHTS) {
      einsteinWeightSum += weight;
    }
    Derivative2 debye = debyeFreeEnergy(thetaD.divide(temp)).multiply(temp)
        .multiply(3.0 * GAS_CONSTANT * (1.0 - einsteinWeightSum));

    Derivative2 einstein = Derivative2.constant(0.0);
    for (int i = 0; i < EINSTEIN_WEIGHTS.length; i++) {
      Derivative2 theta = Derivative2.constant(1.0).subtract(reducedVolume).multiply(EINSTEIN_GRUNEISEN[i]).exp()
          .multiply(EINSTEIN_TEMPERATURES[i]);
      einstein = einstein.add(logOneMinusExpNegative(theta.divide(temp)).multiply(EINSTEIN_WEIGHTS[i]));
    }
    einstein = einstein.multiply(temp).multiply(3.0 * GAS_CONSTANT);

    Derivative2 temperatureRatio = temp.multiply(1.0 / THETA_D0);
    Derivative2 anharmonicTemperature = temperatureRatio.pow(4.0)
        .divide(temperatureRatio.pow(2.0).multiply(B2).add(1.0)).multiply(thetaDTemperature).multiply(B1)
        .multiply(reducedVolume.subtract(1.0).multiply(B3).exp()).multiply(GAS_CONSTANT);
    Derivative2 anharmonicCold = Derivative2
        .constant(1.0).subtract(reducedVolume).multiply(C2).exp().multiply(C1).add(reducedVolume.reciprocal()
            .multiply(C3).multiply(Derivative2.constant(1.0).subtract(reducedVolume).multiply(C4).exp()))
        .multiply(GAS_CONSTANT);

    Derivative2 referenceShift = temp.subtract(TRIPLE_POINT_TEMPERATURE).multiply(-triplePointEntropyShift)
        .add(triplePointGibbsShift);
    return staticLattice.add(zeroPoint).add(debye).add(einstein).add(anharmonicTemperature).add(anharmonicCold)
        .add(referenceShift);
  }

  /** Evaluate the published finite FCC Buckingham static-lattice sum. */
  private static Derivative2 calculateStaticLatticeEnergy(Derivative2 volume) {
    Derivative2 latticeConstant = volume.multiply(4.0 / AVOGADRO_CONSTANT).pow(1.0 / 3.0);
    Derivative2 threeBodyFactor = volume.reciprocal()
        .multiply(THREE_BODY_COEFFICIENT * AVOGADRO_CONSTANT / (EPSILON * Math.pow(BUCKINGHAM_ZERO_DISTANCE, 6.0)))
        .multiply(-1.0).add(1.0);

    Derivative2 shellEnergy = Derivative2.constant(0.0);
    for (int i = 0; i < COORDINATION_SHELL_COUNT; i++) {
      Derivative2 distance = latticeConstant.multiply(0.5 * Math.sqrt(FCC_SQUARED_DISTANCES[i]));
      Derivative2 effectivePotential = calculateBuckinghamPairPotential(distance).multiply(threeBodyFactor);
      shellEnergy = shellEnergy.add(effectivePotential.multiply(FCC_COORDINATION_NUMBERS[i]));
    }
    return shellEnergy.multiply(0.5 * AVOGADRO_CONSTANT);
  }

  /** Evaluate the Buckingham exp-6 pair potential. */
  private static Derivative2 calculateBuckinghamPairPotential(Derivative2 distance) {
    Derivative2 repulsive = distance.multiply(-REPULSIVE_EXPONENT / MINIMUM_POTENTIAL_DISTANCE).add(REPULSIVE_EXPONENT)
        .exp().multiply(EPSILON * 6.0 / (REPULSIVE_EXPONENT - 6.0));
    Derivative2 attractive = distance.reciprocal().multiply(MINIMUM_POTENTIAL_DISTANCE).pow(6.0)
        .multiply(EPSILON * REPULSIVE_EXPONENT / (REPULSIVE_EXPONENT - 6.0));
    return repulsive.subtract(attractive);
  }

  /** Calculate the physically relevant zero of the Buckingham potential. */
  private static double calculateBuckinghamZeroDistance() {
    double lower = 0.5 * MINIMUM_POTENTIAL_DISTANCE;
    double upper = MINIMUM_POTENTIAL_DISTANCE;
    for (int iteration = 0; iteration < 100; iteration++) {
      double middle = 0.5 * (lower + upper);
      if (buckinghamPairPotential(middle) > 0.0) {
        lower = middle;
      } else {
        upper = middle;
      }
    }
    return 0.5 * (lower + upper);
  }

  /** Evaluate the scalar Buckingham pair potential. */
  private static double buckinghamPairPotential(double distance) {
    double repulsive = EPSILON * 6.0 / (REPULSIVE_EXPONENT - 6.0)
        * Math.exp(REPULSIVE_EXPONENT * (1.0 - distance / MINIMUM_POTENTIAL_DISTANCE));
    double attractive = EPSILON * REPULSIVE_EXPONENT / (REPULSIVE_EXPONENT - 6.0)
        * Math.pow(MINIMUM_POTENTIAL_DISTANCE / distance, 6.0);
    return repulsive - attractive;
  }

  /** Generate complete FCC coordination shells in increasing distance. */
  private static void initializeFccCoordinationShells() {
    Map<Integer, Integer> shellCounts = new TreeMap<Integer, Integer>();
    for (int h = -FCC_ENUMERATION_LIMIT; h <= FCC_ENUMERATION_LIMIT; h++) {
      for (int k = -FCC_ENUMERATION_LIMIT; k <= FCC_ENUMERATION_LIMIT; k++) {
        for (int l = -FCC_ENUMERATION_LIMIT; l <= FCC_ENUMERATION_LIMIT; l++) {
          int squaredDistance = h * h + k * k + l * l;
          if (squaredDistance == 0 || (h + k + l) % 2 != 0) {
            continue;
          }
          Integer count = shellCounts.get(squaredDistance);
          shellCounts.put(squaredDistance, count == null ? 1 : count + 1);
        }
      }
    }
    int shellIndex = 0;
    for (Map.Entry<Integer, Integer> shell : shellCounts.entrySet()) {
      if (shellIndex == COORDINATION_SHELL_COUNT) {
        break;
      }
      FCC_SQUARED_DISTANCES[shellIndex] = shell.getKey();
      FCC_COORDINATION_NUMBERS[shellIndex] = shell.getValue();
      shellIndex++;
    }
    if (shellIndex != COORDINATION_SHELL_COUNT) {
      throw new IllegalStateException("FCC enumeration did not generate enough shells.");
    }
  }

  /** Evaluate the dimensionless Debye Helmholtz function. */
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

  /** Evaluate the third-order Debye function used by the publication. */
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

  /** Evaluate log(1-exp(-z)) and its derivatives. */
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

    private Derivative2(double value, double dt, double dv, double dtt, double dtv, double dvv) {
      this.value = value;
      this.dt = dt;
      this.dv = dv;
      this.dtt = dtt;
      this.dtv = dtv;
      this.dvv = dvv;
    }

    private static Derivative2 constant(double value) {
      return new Derivative2(value, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static Derivative2 temperature(double value) {
      return new Derivative2(value, 1.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static Derivative2 volume(double value) {
      return new Derivative2(value, 0.0, 1.0, 0.0, 0.0, 0.0);
    }

    private Derivative2 add(Derivative2 other) {
      return new Derivative2(value + other.value, dt + other.dt, dv + other.dv, dtt + other.dtt, dtv + other.dtv,
          dvv + other.dvv);
    }

    private Derivative2 add(double scalar) {
      return add(constant(scalar));
    }

    private Derivative2 subtract(Derivative2 other) {
      return add(other.multiply(-1.0));
    }

    private Derivative2 subtract(double scalar) {
      return add(-scalar);
    }

    private Derivative2 multiply(double scalar) {
      return new Derivative2(value * scalar, dt * scalar, dv * scalar, dtt * scalar, dtv * scalar, dvv * scalar);
    }

    private Derivative2 multiply(Derivative2 other) {
      return new Derivative2(value * other.value, dt * other.value + value * other.dt,
          dv * other.value + value * other.dv, dtt * other.value + 2.0 * dt * other.dt + value * other.dtt,
          dtv * other.value + dt * other.dv + dv * other.dt + value * other.dtv,
          dvv * other.value + 2.0 * dv * other.dv + value * other.dvv);
    }

    private Derivative2 divide(Derivative2 other) {
      return multiply(other.reciprocal());
    }

    private Derivative2 reciprocal() {
      return applyUnary(1.0 / value, -1.0 / (value * value), 2.0 / (value * value * value));
    }

    private Derivative2 pow(double exponent) {
      double result = Math.pow(value, exponent);
      return applyUnary(result, exponent * Math.pow(value, exponent - 1.0),
          exponent * (exponent - 1.0) * Math.pow(value, exponent - 2.0));
    }

    private Derivative2 exp() {
      double result = Math.exp(value);
      return applyUnary(result, result, result);
    }

    private Derivative2 applyUnary(double functionValue, double firstDerivative, double secondDerivative) {
      return new Derivative2(functionValue, firstDerivative * dt, firstDerivative * dv,
          secondDerivative * dt * dt + firstDerivative * dtt, secondDerivative * dt * dv + firstDerivative * dtv,
          secondDerivative * dv * dv + firstDerivative * dvv);
    }
  }
}
