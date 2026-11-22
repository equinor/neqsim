package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;

/**
 * Pure-CO2 background viscosity of Laesecke and Muzny, JPCRD 46, 013107 (2017).
 *
 * Includes the dilute, linear-in-density Rainwater-Friend, and residual contributions in equations 4-9. The critical
 * enhancement is not included. Table 6 of the paper specifies check values for exactly this background correlation.
 * NIST IR 8608 (2026), section 11, retains the 2017 viscosity formulation as the reference.
 *
 * Inputs are temperature in K and mass density in kg/m3; viscosity is in Pa s. The background API accepts 100-2000 K
 * and nonnegative density. Callers must separately establish a physical fluid state inside the paper's gas or
 * compressed-fluid range; mathematical input checks do not enforce the melting curve. The dilute-only API additionally
 * permits the paper's documented extrapolation to 10000 K.
 *
 * @author Even Solbraa
 * @version 1.1
 */
public class CO2ViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Fixed molar mass for the pure-CO2 transport correlation in kg/mol. */
  private static final double MOLAR_MASS = 0.0440095;
  /** Gas constant used by the residual scaling in J/(mol K). */
  private static final double GAS_CONSTANT = 8.314462618;
  /** Triple-point temperature used in the published residual correlation in K. */
  private static final double TRIPLE_TEMPERATURE = 216.592;
  /** Triple-point liquid density used in the residual correlation in kg/m3. */
  private static final double TRIPLE_LIQUID_DENSITY = 1178.53;
  /** Residual-viscosity scaling factor from equation 9 in Pa s. */
  private static final double RESIDUAL_SCALE = Math.pow(TRIPLE_LIQUID_DENSITY, 2.0 / 3.0)
      * Math.sqrt(GAS_CONSTANT * TRIPLE_TEMPERATURE) / (Math.pow(MOLAR_MASS, 1.0 / 6.0) * 84446887.43579945);
  /** Energy scaling parameter epsilon/kB in K, fitted in the 2017 paper. */
  private static final double ENERGY_SCALE = 200.76;
  /** Length scaling parameter sigma in m, fitted in the 2017 paper. */
  private static final double LENGTH_SCALE = 0.378421e-9;
  /** Converts dimensionless viscosity virial coefficient to a mass-density coefficient in m3/kg. */
  private static final double VIRIAL_SCALE = 6.02214076e23 * LENGTH_SCALE * LENGTH_SCALE * LENGTH_SCALE / MOLAR_MASS;

  /**
   * Creates a pure-CO2 model bound to the phase's current state.
   *
   * @param phase physical-property package providing temperature, pressure and mass density
   */
  public CO2ViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    if (phase.getPhase().getNumberOfComponents() != 1
        || !"CO2".equalsIgnoreCase(phase.getPhase().getComponent(0).getName())) {
      throw new IllegalArgumentException("CO2 viscosity model supports pure CO2 only");
    }
    double temperature = phase.getPhase().getTemperature();
    requireTemperature(temperature, 2000.0);
    double density = phase.getPhase().getDensity();
    if (!Double.isFinite(density) || density <= 0.0 || density > 1e6) {
      double[] properties = NeqSimSpanWagner.getProperties(temperature, phase.getPhase().getPressure() * 1e5,
          phase.getPhase().getType());
      density = properties[0] * phase.getPhase().getComponent(0).getMolarMass();
    }
    return calculateViscosity(temperature, density);
  }

  /**
   * Evaluates the noncritical pure-CO2 viscosity without an EOS or a flash.
   *
   * @param temperature temperature in K, from 100 to 2000 K
   * @param density mass density in kg/m3, finite and nonnegative
   * @return background viscosity in Pa s; critical enhancement is excluded
   * @throws IllegalArgumentException for invalid inputs or a nonfinite/nonpositive result
   */
  public static double calculateViscosity(double temperature, double density) {
    requireTemperature(temperature, 2000.0);
    if (!Double.isFinite(density) || density < 0.0) {
      throw new IllegalArgumentException("CO2 viscosity requires finite nonnegative mass density in kg/m3");
    }
    double dilute = diluteViscosity(temperature);
    if (density == 0.0) {
      return dilute;
    }

    // Table 3 / equation 6: use u = (epsilon/kB/T)^(1/4) to evaluate the fractional-power series without nine pow
    // calls.
    double u = Math.sqrt(Math.sqrt(ENERGY_SCALE / temperature));
    double u2 = u * u;
    double u4 = u2 * u2;
    double u10 = u4 * u4 * u2;
    double u22 = u10 * u10 * u2;
    double reducedVirial = -19.572881
        + u * (219.73999 + u * (-1015.3226 + u * (2471.0125 + u * (-3375.1717 + u * (2491.6597 - u * 787.26086)))))
        + 14.085455 * u10 - 0.34664158 * u22;
    // Equation 5: eta1 = eta0 * N_A * sigma^3 * B_eta* / M when rho is a mass density.
    double initialDensity = dilute * VIRIAL_SCALE * reducedVirial * density;

    double reducedTemperature = temperature / TRIPLE_TEMPERATURE;
    double reducedDensity = density / TRIPLE_LIQUID_DENSITY;
    double densitySquared = reducedDensity * reducedDensity;
    double residual = RESIDUAL_SCALE * (0.360603235428487 * reducedTemperature * densitySquared * reducedDensity
        + (densitySquared + Math.pow(reducedDensity, 8.06282737481277)) / (reducedTemperature - 0.121550806591497));
    double result = dilute + initialDensity + residual;
    if (!Double.isFinite(result) || result <= 0.0) {
      throw new IllegalArgumentException("CO2 viscosity parameters produce a nonfinite or nonpositive result");
    }
    return result;
  }

  /**
   * Evaluates equation 4 in the zero-density limit, including its documented high-temperature extrapolation.
   *
   * @param temperature temperature in K, from 100 to 10000 K; values above 2000 K are extrapolation
   * @return dilute-gas viscosity in Pa s
   * @throws IllegalArgumentException for temperatures outside the stated interval
   */
  public static double calculateDiluteGasViscosity(double temperature) {
    requireTemperature(temperature, 10000.0);
    return diluteViscosity(temperature);
  }

  /**
   * Evaluates the published dilute-gas equation after temperature validation.
   *
   * @param temperature validated temperature in K
   * @return dilute-gas viscosity in Pa s
   */
  private static double diluteViscosity(double temperature) {
    double cubeRoot = Math.cbrt(temperature);
    double sixthRoot = Math.sqrt(cubeRoot);
    double squareRoot = Math.sqrt(temperature);
    double denominator = 1749.354893188350 - 369.069300007128 * sixthRoot
        + 5423856.34887691 * Math.exp(-2.21283852168356 * cubeRoot)
        + (-269503.247933569 + 73145.021531826 * cubeRoot) * Math.exp(-cubeRoot) + 5.34368649509278 * squareRoot;
    return 0.0010055 * squareRoot / denominator;
  }

  /**
   * Validates the deliberately supported temperature range.
   *
   * @param temperature temperature in K
   * @param upperLimit upper temperature limit in K
   * @throws IllegalArgumentException if temperature is nonfinite or outside the interval
   */
  private static void requireTemperature(double temperature, double upperLimit) {
    if (!Double.isFinite(temperature) || temperature < 100.0 || temperature > upperLimit) {
      throw new IllegalArgumentException("CO2 viscosity requires temperature from 100 to " + upperLimit + " K");
    }
  }
}
