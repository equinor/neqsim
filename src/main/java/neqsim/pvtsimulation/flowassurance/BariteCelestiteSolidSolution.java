package neqsim.pvtsimulation.flowassurance;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Solid solution model for the (Ba,Sr)SO4 system.
 *
 * <p>
 * In oilfield systems, barium and strontium sulphate co-precipitate as a solid solution rather than
 * as separate pure phases. This class implements the regular solution model of Hanor (2000) and
 * Prieto et al. (1993) to compute the composition of the co-precipitated solid and the effective
 * saturation index.
 * </p>
 *
 * <p>
 * The solid is modeled as a two-component regular solution: BaSO4 (barite) and SrSO4 (celestite)
 * with a Margules interaction parameter. The equilibrium condition is:
 * </p>
 *
 * <pre>
 * {@code
 * x_Ba * gamma_Ba(s) * Ksp_BaSO4 = a_Ba2+ * a_SO42-
 * x_Sr * gamma_Sr(s) * Ksp_SrSO4 = a_Sr2+ * a_SO42-
 * }
 * </pre>
 *
 * <p>
 * where x_Ba + x_Sr = 1 in the solid phase, and gamma values are from the Margules one-parameter
 * model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class BariteCelestiteSolidSolution implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Margules parameter W/(RT) for (Ba,Sr)SO4 solid solution. Typical value 2.1-2.5 from Prieto et
   * al. (1993). Dimensionless (W/RT at 25°C).
   */
  private double margules = 2.3;

  /** Temperature in Kelvin. */
  private double temperatureK = 298.15;

  /** Aqueous Ba2+ activity (mol/kg * gamma). */
  private double aBa = 0.0;

  /** Aqueous Sr2+ activity (mol/kg * gamma). */
  private double aSr = 0.0;

  /** Aqueous SO4 2- activity (mol/kg * gamma). */
  private double aSO4 = 0.0;

  /** Ksp of pure BaSO4 at current T. */
  private double kspBaSO4 = 1.08e-10;

  /** Ksp of pure SrSO4 at current T. */
  private double kspSrSO4 = 3.44e-7;

  /** Mole fraction of BaSO4 in the solid solution. */
  private double xBa = 0.5;

  /** Total saturation index of the solid solution. */
  private double totalSI = 0.0;

  /** Whether calculation has been performed. */
  private boolean calculated = false;

  /**
   * Creates a new BariteCelestiteSolidSolution model.
   */
  public BariteCelestiteSolidSolution() {}

  /**
   * Sets the Margules interaction parameter W/(RT).
   *
   * @param w Margules parameter (dimensionless)
   */
  public void setMargules(double w) {
    this.margules = w;
    this.calculated = false;
  }

  /**
   * Sets the temperature.
   *
   * @param TK temperature in Kelvin
   */
  public void setTemperature(double TK) {
    this.temperatureK = TK;
    this.calculated = false;
  }

  /**
   * Sets the aqueous ion activities.
   *
   * @param actBa Ba2+ activity (molality * gamma)
   * @param actSr Sr2+ activity (molality * gamma)
   * @param actSO4 SO4 2- activity (molality * gamma)
   */
  public void setAqueousActivities(double actBa, double actSr, double actSO4) {
    this.aBa = actBa;
    this.aSr = actSr;
    this.aSO4 = actSO4;
    this.calculated = false;
  }

  /**
   * Sets the pure-end-member Ksp values.
   *
   * @param kspBa Ksp of BaSO4 at current temperature
   * @param kspSr Ksp of SrSO4 at current temperature
   */
  public void setEndMemberKsp(double kspBa, double kspSr) {
    this.kspBaSO4 = kspBa;
    this.kspSrSO4 = kspSr;
    this.calculated = false;
  }

  /**
   * Calculates the solid solution equilibrium.
   *
   * <p>
   * Solves for xBa (mole fraction of BaSO4 in solid) by finding the root of:
   * </p>
   *
   * <pre>
   * {@code
   * F(xBa) = (aBa * aSO4) / (Ksp_BaSO4 * xBa * gammaBa)
   *         - (aSr * aSO4) / (Ksp_SrSO4 * (1-xBa) * gammaSr) = 0
   * }
   * </pre>
   */
  public void calculate() {
    if (aBa <= 0 && aSr <= 0) {
      xBa = 0.5;
      totalSI = Double.NEGATIVE_INFINITY;
      calculated = true;
      return;
    }

    // If only one cation is present, it's a pure end-member
    if (aSr <= 1e-30) {
      xBa = 1.0;
      double iap = aBa * aSO4;
      totalSI = iap > 0 && kspBaSO4 > 0 ? Math.log10(iap / kspBaSO4) : Double.NEGATIVE_INFINITY;
      calculated = true;
      return;
    }
    if (aBa <= 1e-30) {
      xBa = 0.0;
      double iap = aSr * aSO4;
      totalSI = iap > 0 && kspSrSO4 > 0 ? Math.log10(iap / kspSrSO4) : Double.NEGATIVE_INFINITY;
      calculated = true;
      return;
    }

    // Bisection solver for xBa in [0.001, 0.999]
    double xLo = 0.001;
    double xHi = 0.999;

    for (int iter = 0; iter < 100; iter++) {
      double xMid = 0.5 * (xLo + xHi);
      double fMid = objectiveFunction(xMid);

      if (Math.abs(fMid) < 1e-12 || (xHi - xLo) < 1e-10) {
        xBa = xMid;
        break;
      }

      if (fMid * objectiveFunction(xLo) < 0) {
        xHi = xMid;
      } else {
        xLo = xMid;
      }
      xBa = xMid;
    }

    // Calculate total SI using the solid solution
    double gammaBa = solidActivityCoefficient(xBa);
    double gammaSr = solidActivityCoefficient(1.0 - xBa);
    double siBa = (aBa * aSO4) / (kspBaSO4 * xBa * gammaBa);
    totalSI = Math.log10(Math.max(siBa, 1e-30));
    calculated = true;
  }

  /**
   * Objective function for xBa solver.
   *
   * @param x candidate xBa
   * @return F(x) - should be zero at equilibrium
   */
  private double objectiveFunction(double x) {
    double xSr = 1.0 - x;
    double gammaBa = solidActivityCoefficient(x);
    double gammaSr = solidActivityCoefficient(xSr);
    double rBa = aBa * aSO4 / (kspBaSO4 * x * gammaBa);
    double rSr = aSr * aSO4 / (kspSrSO4 * xSr * gammaSr);
    return rBa - rSr;
  }

  /**
   * Margules one-parameter activity coefficient for the solid phase.
   *
   * <pre>
   * {@code
   * ln(gamma_i) = W * (1 - x_i) ^ 2
   * }
   * </pre>
   *
   * @param xi mole fraction of component i in solid
   * @return activity coefficient
   */
  private double solidActivityCoefficient(double xi) {
    double xj = 1.0 - xi;
    return Math.exp(margules * xj * xj);
  }

  /**
   * Returns the mole fraction of BaSO4 in the solid solution.
   *
   * @return xBa (0 to 1)
   */
  public double getBaSO4MoleFraction() {
    if (!calculated) {
      calculate();
    }
    return xBa;
  }

  /**
   * Returns the mole fraction of SrSO4 in the solid solution.
   *
   * @return xSr (0 to 1)
   */
  public double getSrSO4MoleFraction() {
    if (!calculated) {
      calculate();
    }
    return 1.0 - xBa;
  }

  /**
   * Returns the total saturation index for the solid solution.
   *
   * @return SI value
   */
  public double getTotalSaturationIndex() {
    if (!calculated) {
      calculate();
    }
    return totalSI;
  }

  /**
   * Returns a comprehensive JSON report.
   *
   * @return JSON string
   */
  public String toJson() {
    if (!calculated) {
      calculate();
    }
    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("temperatureK", temperatureK);
    report.put("margules_W_RT", margules);
    report.put("xBaSO4_solid", xBa);
    report.put("xSrSO4_solid", 1.0 - xBa);
    report.put("totalSaturationIndex", totalSI);
    report.put("supersaturated", totalSI > 0);
    report.put("Ksp_BaSO4", kspBaSO4);
    report.put("Ksp_SrSO4", kspSrSO4);

    Gson gson =
        new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(report);
  }
}
