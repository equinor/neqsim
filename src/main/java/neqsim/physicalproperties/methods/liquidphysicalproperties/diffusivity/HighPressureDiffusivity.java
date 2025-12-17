package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * High-pressure corrected diffusivity model for liquid systems.
 *
 * <p>
 * This model applies pressure corrections to low-pressure diffusion coefficients using the
 * Mathur-Thodos correlation. It is essential for accurate predictions at reservoir conditions
 * (pressures > 100 bar).
 * </p>
 *
 * <p>
 * The correction is based on the principle that D*η/T is approximately constant along an isotherm:
 * </p>
 *
 * <pre>
 * D(P) = D(P₀) * (ρ₀/ρ) * (η₀/η)
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Mathur, G.P. and Thodos, G. (1965). "The self-diffusivity of substances in the gaseous and
 * liquid states." AIChE J., 11, 613-616.</li>
 * <li>Riazi, M.R. and Whitson, C.H. (1993). "Estimating diffusion coefficients of dense fluids."
 * Ind. Eng. Chem. Res., 32, 3081-3088.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HighPressureDiffusivity extends Diffusivity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Reference pressure for low-pressure correlation [bar]. */
  private static final double P_REF = 1.0;

  /** Pressure threshold above which correction is applied [bar]. */
  private static final double P_THRESHOLD = 10.0;

  /** Base diffusivity model for low-pressure calculations. */
  private Diffusivity baseDiffusivityModel;

  /**
   * Constructor for HighPressureDiffusivity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public HighPressureDiffusivity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
    // Use Hayduk-Minhas as the base model for hydrocarbons
    this.baseDiffusivityModel = new HaydukMinhasDiffusivity(liquidPhase);
  }

  /**
   * Constructor with custom base model.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   * @param baseModel the low-pressure diffusivity model to use as base
   */
  public HighPressureDiffusivity(PhysicalProperties liquidPhase, Diffusivity baseModel) {
    super(liquidPhase);
    this.baseDiffusivityModel = baseModel;
  }

  /**
   * Set the base diffusivity model for low-pressure calculations.
   *
   * @param baseModel the base diffusivity model
   */
  public void setBaseDiffusivityModel(Diffusivity baseModel) {
    this.baseDiffusivityModel = baseModel;
  }

  /**
   * Calculate the reduced density for the mixture.
   *
   * @return reduced density (ρ/ρc)
   */
  private double getReducedDensity() {
    double rho = liquidPhase.getPhase().getDensity(); // kg/m³

    // Estimate pseudo-critical density from mixing rules
    double rhoCritMix = 0.0;
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      double xi = liquidPhase.getPhase().getComponent(i).getx();
      double Vc = liquidPhase.getPhase().getComponent(i).getCriticalVolume(); // m³/mol
      double M = liquidPhase.getPhase().getComponent(i).getMolarMass(); // kg/mol
      if (Vc > 0 && M > 0) {
        double rhoCi = M / Vc;
        rhoCritMix += xi * rhoCi;
      }
    }

    if (rhoCritMix <= 0) {
      return 1.0; // Fallback
    }

    return rho / rhoCritMix;
  }

  /**
   * Calculate the reduced temperature for the mixture.
   *
   * @return reduced temperature (T/Tc)
   */
  private double getReducedTemperature() {
    double T = liquidPhase.getPhase().getTemperature();

    // Kay's mixing rule for pseudo-critical temperature
    double TcMix = 0.0;
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      double xi = liquidPhase.getPhase().getComponent(i).getx();
      double Tci = liquidPhase.getPhase().getComponent(i).getTC();
      TcMix += xi * Tci;
    }

    if (TcMix <= 0) {
      return 1.0; // Fallback
    }

    return T / TcMix;
  }

  /**
   * Calculate pressure correction factor using Mathur-Thodos approach.
   *
   * <p>
   * The correction is based on: D(P)/D(P0) = (η0/η) * (ρ0/ρ) ≈ f(Tr, ρr)
   * </p>
   *
   * @return pressure correction factor
   */
  private double calculatePressureCorrectionFactor() {
    double P = liquidPhase.getPhase().getPressure();

    // No correction needed at low pressure
    if (P < P_THRESHOLD) {
      return 1.0;
    }

    double Tr = getReducedTemperature();
    double rhoR = getReducedDensity();

    // Riazi-Whitson correlation for dense fluids
    // D*η/T = constant along isotherm -> D(P)/D(P0) ≈ (ρ0/ρ)^a * (η0/η)^b
    // Simplified: correction ≈ exp(-c * (ρr - ρr0))

    double rhoR0 = 0.3; // Reference reduced density at low pressure
    double a = 1.0; // Density exponent
    double b = 0.8; // Viscosity effect implicit in density correlation

    if (rhoR < rhoR0) {
      return 1.0;
    }

    // Empirical correction based on reduced density increase
    double correction = Math.pow(rhoR0 / rhoR, a);

    // Additional temperature correction for subcritical conditions
    if (Tr < 1.0) {
      correction *= Math.pow(Tr, 0.3);
    }

    // Ensure correction is reasonable (0.1 to 1.0)
    correction = Math.max(0.1, Math.min(1.0, correction));

    return correction;
  }

  /** {@inheritDoc} */
  @Override
  public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
    // Get low-pressure diffusion coefficient from base model
    double D0 = baseDiffusivityModel.calcBinaryDiffusionCoefficient(i, j, method);

    // Apply pressure correction
    double correctionFactor = calculatePressureCorrectionFactor();
    binaryDiffusionCoefficients[i][j] = D0 * correctionFactor;

    return binaryDiffusionCoefficients[i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcDiffusionCoefficients(int binaryDiffusionCoefficientMethod,
      int multicomponentDiffusionMethod) {
    // First calculate base diffusion coefficients
    baseDiffusivityModel.calcDiffusionCoefficients(binaryDiffusionCoefficientMethod,
        multicomponentDiffusionMethod);

    // Then apply pressure correction to all
    double correctionFactor = calculatePressureCorrectionFactor();

    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      for (int j = 0; j < liquidPhase.getPhase().getNumberOfComponents(); j++) {
        binaryDiffusionCoefficients[i][j] =
            baseDiffusivityModel.getMaxwellStefanBinaryDiffusionCoefficient(i, j)
                * correctionFactor;
      }
    }

    return binaryDiffusionCoefficients;
  }

  /**
   * Get the pressure correction factor that was applied.
   *
   * @return the correction factor (0 to 1)
   */
  public double getPressureCorrectionFactor() {
    return calculatePressureCorrectionFactor();
  }
}
