package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Lee-Gonzalez-Eakin gas viscosity correlation (1966).
 *
 * <p>
 * This is a simple correlation for estimating natural gas viscosity, suitable for first-order
 * estimates when more sophisticated methods are not required.
 * </p>
 *
 * <p>
 * The correlation is: μ = K * exp(X * ρ^Y)
 * </p>
 *
 * <p>
 * where:
 * <ul>
 * <li>K = (9.4 + 0.02*M) * T^1.5 / (209 + 19*M + T)</li>
 * <li>X = 3.5 + 986/T + 0.01*M</li>
 * <li>Y = 2.4 - 0.2*X</li>
 * <li>M = molecular weight (g/mol)</li>
 * <li>T = temperature (Rankine)</li>
 * <li>ρ = density (g/cm³)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Reference: Lee, A.L., Gonzalez, M.H., and Eakin, B.E., "The Viscosity of Natural Gases", Journal
 * of Petroleum Technology, paper SPE-1340-PA, 1966.
 * </p>
 *
 * <p>
 * As noted in the Whitson wiki (https://wiki.whitson.com/bopvt/visc_correlations/#lee-correlation),
 * this correlation is not widely used in industry anymore but provides simple first-order
 * estimates.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see LBCViscosityMethod
 * @see PFCTViscosityMethod
 */
public class LeeViscosityMethod extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for LeeViscosityMethod.
   *
   * @param phase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public LeeViscosityMethod(PhysicalProperties phase) {
    super(phase);
  }

  /**
   * Calculate gas viscosity using the Lee-Gonzalez-Eakin correlation.
   *
   * @return viscosity in Pa·s
   */
  @Override
  public double calcViscosity() {
    // Get mixture properties
    double molarMass = phase.getPhase().getMolarMass() * 1000.0; // Convert kg/mol to g/mol

    // Temperature in Rankine (required by correlation)
    double temperatureK = phase.getPhase().getTemperature();
    double temperatureR = temperatureK * 9.0 / 5.0; // Kelvin to Rankine

    // Density in g/cm³ (required by correlation)
    double densityKgM3 = phase.getPhase().getDensity("kg/m3");
    double densityGCm3 = densityKgM3 / 1000.0; // kg/m³ to g/cm³

    // Calculate correlation parameters
    double K = (9.4 + 0.02 * molarMass) * Math.pow(temperatureR, 1.5)
        / (209.0 + 19.0 * molarMass + temperatureR);

    double X = 3.5 + 986.0 / temperatureR + 0.01 * molarMass;

    double Y = 2.4 - 0.2 * X;

    // Calculate viscosity in cP (centipoise)
    double viscosityCp = K * 1e-4 * Math.exp(X * Math.pow(densityGCm3, Y));

    // Convert to Pa·s (1 cP = 0.001 Pa·s)
    return viscosityCp * 0.001;
  }

  /**
   * Calculate gas viscosity using the Lee correlation with explicit inputs.
   *
   * <p>
   * This static method can be used independently without creating a phase object.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param densityKgM3 density in kg/m³
   * @param molarMassKgMol molar mass in kg/mol
   * @return viscosity in Pa·s
   */
  public static double calcViscosity(double temperatureK, double densityKgM3,
      double molarMassKgMol) {
    // Convert units
    double molarMassGMol = molarMassKgMol * 1000.0; // kg/mol to g/mol
    double temperatureR = temperatureK * 9.0 / 5.0; // Kelvin to Rankine
    double densityGCm3 = densityKgM3 / 1000.0; // kg/m³ to g/cm³

    // Calculate correlation parameters
    double K = (9.4 + 0.02 * molarMassGMol) * Math.pow(temperatureR, 1.5)
        / (209.0 + 19.0 * molarMassGMol + temperatureR);

    double X = 3.5 + 986.0 / temperatureR + 0.01 * molarMassGMol;

    double Y = 2.4 - 0.2 * X;

    // Calculate viscosity in cP
    double viscosityCp = K * 1e-4 * Math.exp(X * Math.pow(densityGCm3, Y));

    // Convert to Pa·s
    return viscosityCp * 0.001;
  }

  /**
   * Calculate low-pressure gas viscosity using the Lee correlation.
   *
   * <p>
   * At low pressures (near atmospheric), the density term becomes negligible and the correlation
   * simplifies to: μ ≈ K * 1e-4
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param molarMassKgMol molar mass in kg/mol
   * @return low-pressure viscosity in Pa·s
   */
  public static double calcLowPressureViscosity(double temperatureK, double molarMassKgMol) {
    double molarMassGMol = molarMassKgMol * 1000.0;
    double temperatureR = temperatureK * 9.0 / 5.0;

    double K = (9.4 + 0.02 * molarMassGMol) * Math.pow(temperatureR, 1.5)
        / (209.0 + 19.0 * molarMassGMol + temperatureR);

    // Low pressure: exp(X * ρ^Y) ≈ 1 when ρ → 0
    double viscosityCp = K * 1e-4;

    return viscosityCp * 0.001;
  }
}
