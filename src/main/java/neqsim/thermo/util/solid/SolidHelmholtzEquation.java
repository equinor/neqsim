package neqsim.thermo.util.solid;

import java.io.Serializable;

/**
 * Contract for a pure-solid fundamental Helmholtz equation of state.
 *
 * <p>
 * Implementations own the density or molar-volume solve and return a thermodynamically consistent state at the
 * requested temperature and pressure. Substance-specific equations and coefficients belong in implementations of this
 * interface, while the NeqSim phase lifecycle is handled by the EOS-derived solid phase classes.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public interface SolidHelmholtzEquation extends Serializable {

  /**
   * Return the triple-point pressure used to distinguish sublimation from melting equilibrium.
   *
   * @return triple-point pressure in bara
   */
  default double getTriplePointPressure() {
    return Double.NaN;
  }

  /**
   * Evaluate the solid state at a specified temperature and pressure.
   *
   * @param temperature temperature in K; must be positive
   * @param pressure pressure in bara; must be positive
   * @return evaluated molar thermodynamic state
   */
  SolidHelmholtzState evaluate(double temperature, double pressure);
}