package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Letsou-Stiel saturated-liquid viscosity estimation from critical constants and acentric factor.
 *
 * <p>
 * This opt-in pure-component estimator uses the polynomial representation of the Letsou-Stiel tables documented in the
 * ChemSep book, equations 10.158-10.161. It is limited to 0.76 &lt;= T/Tc &lt;= 0.98. It includes neither a pressure
 * correction nor a mixture rule, and does not check saturation. The result is a saturated-liquid estimate even if the
 * supplied liquid phase is compressed. Prefer reference data when available.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class LetsouStielViscosity extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Creates a pure-liquid estimator in the existing physical-property architecture.
   *
   * @param liquidPhase physical-property package for a pure liquid
   */
  public LetsouStielViscosity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    if (liquidPhase.getPhase().getNumberOfComponents() != 1) {
      throw new IllegalArgumentException("Letsou-Stiel supports pure liquids only; use PFCT or LBC for mixtures");
    }
    PhaseType type = liquidPhase.getPhase().getType();
    if (type != PhaseType.OIL && type != PhaseType.LIQUID && type != PhaseType.AQUEOUS) {
      throw new IllegalArgumentException("Letsou-Stiel requires a liquid phase");
    }
    ComponentInterface component = liquidPhase.getPhase().getComponent(0);
    return calculateViscosity(liquidPhase.getPhase().getTemperature(), component.getMolarMass(), component.getTC(),
        component.getPC(), component.getAcentricFactor());
  }

  /** {@inheritDoc} */
  @Override
  public void calcPureComponentViscosity() {
    pureComponentViscosity = new double[] {calcViscosity() * 1000.0};
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    if (i != 0) {
      throw new IndexOutOfBoundsException("Letsou-Stiel has only one pure component");
    }
    // Preserve the liquid-property interface's mPa s convention without a stale cached value.
    return calcViscosity() * 1000.0;
  }

  /**
   * Estimates saturated-liquid viscosity without a flash or fitted viscosity coefficients.
   *
   * @param temperature temperature in K
   * @param molarMass molar mass in kg/mol
   * @param criticalTemperature critical temperature in K
   * @param criticalPressure critical pressure in bara
   * @param acentricFactor dimensionless acentric factor
   * @return saturated-liquid viscosity in Pa s
   * @throws IllegalArgumentException for invalid parameters or outside 0.76 &lt;= T/Tc &lt;= 0.98
   */
  public static double calculateViscosity(double temperature, double molarMass, double criticalTemperature,
      double criticalPressure, double acentricFactor) {
    if (!Double.isFinite(temperature) || temperature <= 0.0 || !Double.isFinite(molarMass) || molarMass <= 0.0
        || !Double.isFinite(criticalTemperature) || criticalTemperature <= 0.0 || !Double.isFinite(criticalPressure)
        || criticalPressure <= 0.0 || !Double.isFinite(acentricFactor)) {
      throw new IllegalArgumentException("Letsou-Stiel requires finite parameters and positive T, M, Tc and Pc");
    }
    double tr = temperature / criticalTemperature;
    if (tr < 0.76 || tr > 0.98) {
      throw new IllegalArgumentException("Letsou-Stiel requires 0.76 <= T/Tc <= 0.98");
    }
    double xi0 = (1.5174 - tr * (2.135 - 0.75 * tr)) * 1e-5;
    double xi1 = (4.2552 - tr * (7.674 - 3.4 * tr)) * 1e-5;
    double xi = 2173.424 * Math.pow(criticalTemperature, 1.0 / 6.0)
        / (Math.sqrt(molarMass * 1000.0) * Math.pow(criticalPressure * 1e5, 2.0 / 3.0));
    double viscosity = (xi0 + acentricFactor * xi1) / xi;
    if (!Double.isFinite(viscosity) || viscosity <= 0.0) {
      throw new IllegalArgumentException("Letsou-Stiel parameters produce a nonpositive or nonfinite viscosity");
    }
    return viscosity;
  }
}
