package neqsim.thermo.util.amines;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validated Kent-Eisenberg CO2 solubility model for aqueous alkanolamine solutions.
 *
 * <p>
 * Implements the apparent-equilibrium-constant (Kent-Eisenberg, 1976) speciation model for the equilibrium CO2 partial
 * pressure over loaded amine solutions. The model solves the coupled carbonate, water, amine-protonation, and (for
 * primary/secondary amines) carbamate equilibria together with the charge, carbon, and amine balances, then applies
 * Henry's law to the free molecular CO2 concentration:
 * </p>
 *
 * $$ p_{CO_2} = H_{CO_2}(T) \cdot [CO_2]_{free} $$
 *
 * <p>
 * Carbonate and water dissociation constants are the molality-based correlations of Edwards, Maurer, Newman and
 * Prausnitz (AIChE J., 1978), and the physical CO2 Henry's constant is the Versteeg and van Swaaij (1988) correlation.
 * The amine protonation constants (and, for MEA/DEA, the carbamate reversion constants) are apparent constants tuned so
 * the model reproduces the classic vapour-liquid-equilibrium datasets of Jou, Mather and Otto (Can. J. Chem. Eng.,
 * 1982, for MDEA) and Jou, Mather and Otto / Lee, Otto and Mather (for MEA).
 * </p>
 *
 * <p>
 * The model is intended as a robust screening correlation. Like all Kent-Eisenberg implementations it is most accurate
 * in the engineering loading window (roughly 0.2-0.5 mol/mol for primary and secondary amines, 0.1-1.0 mol/mol for
 * tertiary amines) where its CO2 partial pressure is typically within a factor of about two of the data. It does not
 * resolve activity-coefficient or ionic-strength effects rigorously; for that use the electrolyte equation-of-state
 * path exposed by {@link AmineSystem#getCO2PartialPressureRigorous()}.
 * </p>
 *
 * <table>
 * <caption>Supported amines and validation status</caption>
 * <tr>
 * <th>Amine</th>
 * <th>Class</th>
 * <th>Carbamate</th>
 * <th>Validation</th>
 * </tr>
 * <tr>
 * <td>MDEA</td>
 * <td>tertiary</td>
 * <td>no</td>
 * <td>regression-tested vs Jou et al. (1982)</td>
 * </tr>
 * <tr>
 * <td>MEA</td>
 * <td>primary</td>
 * <td>yes</td>
 * <td>regression-tested vs Jou/Lee et al.</td>
 * </tr>
 * <tr>
 * <td>DEA</td>
 * <td>secondary</td>
 * <td>yes</td>
 * <td>screening only (constants not regression-tuned)</td>
 * </tr>
 * </table>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AmineKentEisenberg implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger object for class. */
  private static final transient Logger logger = LogManager.getLogger(AmineKentEisenberg.class);

  /** Maximum bisection iterations for the hydrogen-ion charge-balance solve. */
  private static final int MAX_BISECTION = 200;

  /** Maximum inner iterations for the free-CO2 carbon-balance solve (carbamate amines). */
  private static final int MAX_INNER = 200;

  /**
   * Alkanolamine type supported by the model.
   */
  public enum AmineType {
    /** Monoethanolamine (primary, forms carbamate). */
    MEA,
    /** Diethanolamine (secondary, forms carbamate). */
    DEA,
    /** Methyldiethanolamine (tertiary, no carbamate). */
    MDEA
  }

  /**
   * Private constructor; the model is used through its static methods.
   */
  private AmineKentEisenberg() {
  }

  /**
   * CO2 hydration constant, CO2 + H2O = H+ + HCO3-, Edwards et al. (1978), molality basis.
   *
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return equilibrium constant (mol/kg)
   */
  private static double kCO2(double temperatureK) {
    return Math.exp(-12092.1 / temperatureK - 36.7816 * Math.log(temperatureK) + 235.482);
  }

  /**
   * Bicarbonate dissociation constant, HCO3- = H+ + CO3--, Edwards et al. (1978), molality basis.
   *
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return equilibrium constant (mol/kg)
   */
  private static double kHCO3(double temperatureK) {
    return Math.exp(-12431.7 / temperatureK - 35.4819 * Math.log(temperatureK) + 220.067);
  }

  /**
   * Water dissociation constant, H2O = H+ + OH-, Edwards et al. (1978), molality basis.
   *
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return ion product of water (mol/kg)^2
   */
  private static double kWater(double temperatureK) {
    return Math.exp(-13445.9 / temperatureK - 22.4773 * Math.log(temperatureK) + 140.932);
  }

  /**
   * Physical CO2 Henry's constant in water, Versteeg and van Swaaij (1988).
   *
   * <p>
   * Returns the Henry coefficient in kPa.L/mol so that the CO2 partial pressure in kPa equals the coefficient
   * multiplied by the free molecular CO2 concentration in mol/L.
   * </p>
   *
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return Henry coefficient in kPa.L/mol
   */
  private static double henryCO2(double temperatureK) {
    return 2.82e6 * Math.exp(-2044.0 / temperatureK);
  }

  /**
   * Apparent amine protonation (dissociation) constant, AmineH+ = Amine + H+.
   *
   * @param type amine type (must not be null)
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return apparent dissociation constant (mol/L)
   */
  private static double kAmine(AmineType type, double temperatureK) {
    switch (type) {
    case MDEA:
      return Math.exp(-6.40 - 4210.0 / temperatureK);
    case MEA:
      return Math.exp(-3.10 - 5630.0 / temperatureK);
    case DEA:
      return Math.exp(-3.66 - 5000.0 / temperatureK);
    default:
      return Math.exp(-6.40 - 4210.0 / temperatureK);
    }
  }

  /**
   * Apparent carbamate reversion constant, AmineCOO- + H2O = Amine + HCO3-.
   *
   * <p>
   * A larger value means a less stable carbamate. Tertiary amines (MDEA) do not form carbamate and return
   * {@link Double#NaN}.
   * </p>
   *
   * @param type amine type (must not be null)
   * @param temperatureK temperature in Kelvin (must be positive)
   * @return apparent carbamate reversion constant (mol/L), or NaN for tertiary amines
   */
  private static double kCarbamate(AmineType type, double temperatureK) {
    switch (type) {
    case MEA:
      return Math.exp(3.90 - 1800.0 / temperatureK);
    case DEA:
      return Math.exp(4.80 - 1800.0 / temperatureK);
    default:
      return Double.NaN;
    }
  }

  /**
   * Estimates the amine molarity (mol/L) of an aqueous solution from its mass fraction.
   *
   * <p>
   * Uses a simple linear solution-density estimate, rho [g/L] = 1000 + 80 * massFraction, which reproduces typical
   * alkanolamine solution densities (about 1.04 g/mL for 50 wt% MDEA and about 1.02 g/mL for 30 wt% MEA) closely enough
   * for the apparent-constant model.
   * </p>
   *
   * @param massFraction amine mass fraction of the aqueous solution (0 to 1)
   * @param amineMolarMass amine molar mass in g/mol (must be positive)
   * @return amine concentration in mol/L
   */
  public static double amineMolarity(double massFraction, double amineMolarMass) {
    double densityGramPerLitre = 1000.0 + 80.0 * massFraction;
    return massFraction * densityGramPerLitre / amineMolarMass;
  }

  /**
   * Calculates the equilibrium CO2 partial pressure over a loaded amine solution.
   *
   * @param type amine type (must not be null)
   * @param temperatureK temperature in Kelvin (must be positive)
   * @param amineMolarity total amine concentration in mol/L (must be positive)
   * @param loading CO2 loading in mol CO2 per mol amine (must be non-negative)
   * @return equilibrium CO2 partial pressure in bara
   */
  public static double partialPressureCO2Bara(AmineType type, double temperatureK, double amineMolarity,
      double loading) {
    if (type == null) {
      throw new IllegalArgumentException("amine type must not be null");
    }
    if (temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperature must be positive Kelvin");
    }
    if (amineMolarity <= 0.0) {
      throw new IllegalArgumentException("amine molarity must be positive");
    }
    if (loading < 0.0) {
      throw new IllegalArgumentException("loading must be non-negative");
    }
    if (loading == 0.0) {
      return 0.0;
    }
    double pkPa = Double.isNaN(kCarbamate(type, temperatureK))
        ? solveTertiary(type, temperatureK, amineMolarity, loading)
        : solveCarbamate(type, temperatureK, amineMolarity, loading);
    return pkPa / 100.0;
  }

  /**
   * Solves the speciation for a tertiary amine (MDEA, no carbamate).
   *
   * @param type amine type (must not be null)
   * @param temperatureK temperature in Kelvin (must be positive)
   * @param amineMolarity total amine concentration in mol/L (must be positive)
   * @param loading CO2 loading in mol CO2 per mol amine (must be positive)
   * @return CO2 partial pressure in kPa
   */
  private static double solveTertiary(AmineType type, double temperatureK, double amineMolarity, double loading) {
    final double k1 = kCO2(temperatureK);
    final double k2 = kHCO3(temperatureK);
    final double kw = kWater(temperatureK);
    final double kam = kAmine(type, temperatureK);
    final double carbonTotal = loading * amineMolarity;

    double lo = 1.0e-14;
    double hi = 1.0;
    double resLo = tertiaryResidual(lo, k1, k2, kw, kam, amineMolarity, carbonTotal);
    for (int i = 0; i < MAX_BISECTION; i++) {
      double mid = Math.sqrt(lo * hi);
      double resMid = tertiaryResidual(mid, k1, k2, kw, kam, amineMolarity, carbonTotal);
      if (resLo * resMid <= 0.0) {
        hi = mid;
      } else {
        lo = mid;
        resLo = resMid;
      }
    }
    double h = Math.sqrt(lo * hi);
    double denom = 1.0 + k1 / h + k1 * k2 / (h * h);
    double freeCO2 = carbonTotal / denom;
    return henryCO2(temperatureK) * freeCO2;
  }

  /**
   * Charge-balance residual for the tertiary-amine speciation as a function of hydrogen-ion concentration.
   *
   * @param h hydrogen-ion concentration in mol/L (must be positive)
   * @param k1 CO2 hydration constant
   * @param k2 bicarbonate dissociation constant
   * @param kw water ion product
   * @param kam amine dissociation constant
   * @param amineMolarity total amine concentration in mol/L
   * @param carbonTotal total carbon concentration in mol/L
   * @return charge-balance residual (mol/L)
   */
  private static double tertiaryResidual(double h, double k1, double k2, double kw, double kam, double amineMolarity,
      double carbonTotal) {
    double oh = kw / h;
    double denom = 1.0 + k1 / h + k1 * k2 / (h * h);
    double co2 = carbonTotal / denom;
    double hco3 = k1 * co2 / h;
    double co3 = k2 * hco3 / h;
    double amine = amineMolarity / (1.0 + h / kam);
    double amineProtonated = amineMolarity - amine;
    return (h + amineProtonated) - (oh + hco3 + 2.0 * co3);
  }

  /**
   * Solves the speciation for a primary or secondary amine (MEA, DEA) including carbamate.
   *
   * @param type amine type (must not be null)
   * @param temperatureK temperature in Kelvin (must be positive)
   * @param amineMolarity total amine concentration in mol/L (must be positive)
   * @param loading CO2 loading in mol CO2 per mol amine (must be positive)
   * @return CO2 partial pressure in kPa
   */
  private static double solveCarbamate(AmineType type, double temperatureK, double amineMolarity, double loading) {
    final double k1 = kCO2(temperatureK);
    final double k2 = kHCO3(temperatureK);
    final double kw = kWater(temperatureK);
    final double kam = kAmine(type, temperatureK);
    final double kc = kCarbamate(type, temperatureK);
    final double carbonTotal = loading * amineMolarity;

    double lo = 1.0e-14;
    double hi = 1.0;
    double resLo = carbamateResidual(lo, k1, k2, kw, kam, kc, amineMolarity, carbonTotal);
    for (int i = 0; i < MAX_BISECTION; i++) {
      double mid = Math.sqrt(lo * hi);
      double resMid = carbamateResidual(mid, k1, k2, kw, kam, kc, amineMolarity, carbonTotal);
      if (resLo * resMid <= 0.0) {
        hi = mid;
      } else {
        lo = mid;
        resLo = resMid;
      }
    }
    double h = Math.sqrt(lo * hi);
    double freeCO2 = freeCO2AtH(h, k1, k2, kam, kc, amineMolarity, carbonTotal);
    return henryCO2(temperatureK) * freeCO2;
  }

  /**
   * Charge-balance residual for the carbamate-forming amine speciation as a function of hydrogen-ion concentration.
   *
   * @param h hydrogen-ion concentration in mol/L (must be positive)
   * @param k1 CO2 hydration constant
   * @param k2 bicarbonate dissociation constant
   * @param kw water ion product
   * @param kam amine dissociation constant
   * @param kc carbamate reversion constant
   * @param amineMolarity total amine concentration in mol/L
   * @param carbonTotal total carbon concentration in mol/L
   * @return charge-balance residual (mol/L)
   */
  private static double carbamateResidual(double h, double k1, double k2, double kw, double kam, double kc,
      double amineMolarity, double carbonTotal) {
    double freeCO2 = freeCO2AtH(h, k1, k2, kam, kc, amineMolarity, carbonTotal);
    double oh = kw / h;
    double hco3 = k1 * freeCO2 / h;
    double co3 = k2 * hco3 / h;
    double amine = amineMolarity / (1.0 + h / kam + hco3 / kc);
    double amineProtonated = amine * h / kam;
    double carbamate = amine * hco3 / kc;
    return (h + amineProtonated) - (oh + hco3 + 2.0 * co3 + carbamate);
  }

  /**
   * Solves the carbon balance for the free molecular CO2 concentration at a fixed hydrogen-ion concentration for a
   * carbamate-forming amine.
   *
   * @param h hydrogen-ion concentration in mol/L (must be positive)
   * @param k1 CO2 hydration constant
   * @param k2 bicarbonate dissociation constant
   * @param kam amine dissociation constant
   * @param kc carbamate reversion constant
   * @param amineMolarity total amine concentration in mol/L
   * @param carbonTotal total carbon concentration in mol/L
   * @return free molecular CO2 concentration in mol/L
   */
  private static double freeCO2AtH(double h, double k1, double k2, double kam, double kc, double amineMolarity,
      double carbonTotal) {
    double co2 = carbonTotal;
    for (int i = 0; i < MAX_INNER; i++) {
      double hco3 = k1 * co2 / h;
      double co3 = k2 * hco3 / h;
      double amine = amineMolarity / (1.0 + h / kam + hco3 / kc);
      double carbamate = amine * hco3 / kc;
      double carbon = co2 + hco3 + co3 + carbamate;
      double co2New = co2 * carbonTotal / Math.max(carbon, 1.0e-30);
      if (Math.abs(co2New - co2) < 1.0e-12 * (co2 + 1.0e-12)) {
        return co2New;
      }
      co2 = co2New;
    }
    logger.debug("freeCO2AtH inner loop did not fully converge at h={}", h);
    return co2;
  }
}
