package neqsim.thermo.component;

import neqsim.thermo.component.attractiveeosterm.AttractiveTermPr;

/**
 * <p>
 * ComponentPR class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentPR extends ComponentEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Universal exponent from 3D Ising model critical exponent beta = 0.326. n = 8 * beta - 3 =
   * -0.392. Used in Cachadina et al. (2024) correlation.
   */
  private static final double CACHADINA_N = -0.392;

  /**
   * Constant model coefficient m0 in mol^(2/3). From Cachadina et al. (2024) Table 3.
   */
  private static final double CACHADINA_M0_CONST = 5.983e-17;

  /**
   * Constant model coefficient m1 in mol^(2/3). From Cachadina et al. (2024) Table 3.
   */
  private static final double CACHADINA_M1_CONST = 4.060e-17;

  /**
   * Constant model coefficient m2 in mol^(2/3). From Cachadina et al. (2024) Table 3.
   */
  private static final double CACHADINA_M2_CONST = -1.810e-17;

  /**
   * Influence parameter model type for gradient theory surface tension. 0 = Linear Zuo-Stenby
   * (default), 1 = Cachadina et al. (2024) three-coefficient model.
   */
  private int influenceParameterModel = 0;

  /**
   * Per-fluid Cachadina influence parameter coefficients [m0, m1, m2]. Units: mol^(2/3). When null,
   * the general constant model is used.
   */
  private double[] cachadinaCoeff = null;

  /**
   * <p>
   * Constructor for ComponentPR.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentPR(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    a = .45724333333 * R * R * criticalTemperature * criticalTemperature / criticalPressure;
    b = .077803333 * R * criticalTemperature / criticalPressure;
    // m = 0.37464 + 1.54226 * acentricFactor - 0.26992* acentricFactor *
    // acentricFactor;

    delta1 = 1.0 + Math.sqrt(2.0);
    delta2 = 1.0 - Math.sqrt(2.0);
    setAttractiveParameter(new AttractiveTermPr(this));

    double[] surfTensInfluenceParamtemp = {1.3192, 1.6606, 1.1173, 0.8443};
    this.surfTensInfluenceParam = surfTensInfluenceParamtemp;
  }

  /**
   * <p>
   * Constructor for ComponentPR.
   * </p>
   *
   * @param number a int. Not used.
   * @param TC Critical temperature [K]
   * @param PC Critical pressure [bara]
   * @param M Molar mass
   * @param a Acentric factor
   * @param moles Total number of moles of component.
   */
  public ComponentPR(int number, double TC, double PC, double M, double a, double moles) {
    super(number, TC, PC, M, a, moles);
  }

  /** {@inheritDoc} */
  @Override
  public ComponentPR clone() {
    ComponentPR clonedComponent = null;
    try {
      clonedComponent = (ComponentPR) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedComponent;
  }

  /** {@inheritDoc} */
  @Override
  public double calca() {
    double oa = hasOmegaAOverride() ? omegaAOverride : 0.45724333333;
    return oa * R * R * criticalTemperature * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double calcb() {
    return .077803333 * R * criticalTemperature / criticalPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumeCorrection() {
    if (hasVolumeCorrection()) {
      return super.getVolumeCorrection();
    }
    if (ionicCharge != 0) {
      return 0.0;
    }
    if (Math.abs(getVolumeCorrectionConst()) > 1.0e-10) {
      return getVolumeCorrectionConst() * b;
    } else if (Math.abs(this.getRacketZ()) < 1e-10) {
      racketZ = 0.29056 - 0.08775 * getAcentricFactor();
    }
    return 0.50033 * (0.25969 - this.getRacketZ()) * R * criticalTemperature / criticalPressure;
  }

  /**
   * <p>
   * getQpure.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getQpure(double temperature) {
    return this.getaT() / (this.getb() * R * temperature);
  }

  /**
   * <p>
   * getdQpuredT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredT(double temperature) {
    return dqPuredT;
  }

  /**
   * <p>
   * getdQpuredTdT.
   * </p>
   *
   * @param temperature a double
   * @return a double
   */
  public double getdQpuredTdT(double temperature) {
    return dqPuredTdT;
  }

  /** {@inheritDoc} */
  @Override
  public double getSurfaceTenisionInfluenceParameter(double temperature) {
    if (influenceParameterModel == 1) {
      return calcCachadinaInfluenceParameter(temperature);
    }

    // Original Zuo-Stenby linear model (model == 0, default)
    double TR = 1.0 - temperature / getTC();
    if (TR < 0) {
      TR = 0.5;
    }
    double AA =
        -1.0e-16 / (surfTensInfluenceParam[0] + surfTensInfluenceParam[1] * getAcentricFactor());
    double BB =
        1.0e-16 / (surfTensInfluenceParam[2] + surfTensInfluenceParam[3] * getAcentricFactor());

    // System.out.println("scale2 " + aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) *
    // (AA * TR + BB));
    if (componentName.equals("water")) {
      AA = -6.99632E-17;
      BB = 5.68347E-17;
    }
    // System.out.println("AA " + AA + " BB " + BB);
    if (componentName.equals("MEG")) {
      return 0.00000000000000000007101030813216131;
    }
    return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);
    // Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 2.0 / 3.0);
  }

  /**
   * Calculates the influence parameter using the Cachadina et al. (2024) three-coefficient
   * correlation with universal exponent from the 3D Ising model.
   *
   * <p>
   * The reduced influence parameter c* is calculated using Eq. 20: c*(t) = m0*(t^n - 1) + m1 + (m2
   * - n*m0)*(t-1) - 0.5*n*(n-1)*m0*(t-1)^2 where t = (Tc - T)/(Tc - Tt) is the reduced temperature
   * and n = -0.392.
   * </p>
   *
   * <p>
   * Reference: Cachadina, I.; Maghari, A.; Generino, J.; Mulero, A. (2024). "New Correlations for
   * the Influence Parameter and the Calculation of the Surface Tension of Normal Alkanes Using
   * Gradient Theory with PR78." Molecules 29, 5643.
   * </p>
   *
   * @param temperature temperature in Kelvin
   * @return influence parameter c in J*m^5/mol^2 (SI derived units)
   */
  private double calcCachadinaInfluenceParameter(double temperature) {
    double tc = getTC();
    double tt = getTriplePointTemperature();

    // Guard against invalid or missing triple point data (default is 1000 K)
    if (tt <= 0 || tt >= tc) {
      double tb = getNormalBoilingPoint();
      if (tb > 0 && tb < tc) {
        tt = 0.6 * tb;
      } else {
        tt = 0.3 * tc;
      }
    }

    // Reduced temperature: t = (Tc - T) / (Tc - Tt)
    // t = 0 at critical point, t = 1 at triple point
    double t = (tc - temperature) / (tc - tt);
    if (t <= 0) {
      t = 1e-10;
    }
    if (t > 1.0) {
      t = 1.0;
    }

    double n = CACHADINA_N;
    double m0;
    double m1;
    double m2;

    if (cachadinaCoeff != null) {
      m0 = cachadinaCoeff[0];
      m1 = cachadinaCoeff[1];
      m2 = cachadinaCoeff[2];
    } else {
      m0 = CACHADINA_M0_CONST;
      m1 = CACHADINA_M1_CONST;
      m2 = CACHADINA_M2_CONST;
    }

    // Eq. 20 from Cachadina et al. (2024)
    double tPowN = Math.pow(t, n);
    double tMinus1 = t - 1.0;
    double cStar = m0 * (tPowN - 1.0) + m1 + (m2 - n * m0) * tMinus1
        - 0.5 * n * (n - 1.0) * m0 * tMinus1 * tMinus1;

    return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * cStar;
  }

  /**
   * Sets the influence parameter model type for gradient theory surface tension calculations.
   *
   * @param model 0 for linear Zuo-Stenby (default), 1 for Cachadina et al. (2024) three-coefficient
   *        model
   */
  public void setInfluenceParameterModel(int model) {
    this.influenceParameterModel = model;
  }

  /**
   * Gets the influence parameter model type.
   *
   * @return 0 for linear Zuo-Stenby, 1 for Cachadina et al. (2024)
   */
  public int getInfluenceParameterModel() {
    return influenceParameterModel;
  }

  /**
   * Sets per-fluid Cachadina influence parameter coefficients. When set, these override the general
   * constant model in the Cachadina (2024) correlation.
   *
   * @param m0 coefficient m0 in mol^(2/3), controls non-linear behavior near critical point
   * @param m1 coefficient m1 in mol^(2/3), value at triple point
   * @param m2 coefficient m2 in mol^(2/3), linear slope correction
   */
  public void setCachadinaInfluenceParameters(double m0, double m1, double m2) {
    this.cachadinaCoeff = new double[] {m0, m1, m2};
  }

  /**
   * Gets the per-fluid Cachadina influence parameter coefficients.
   *
   * @return array [m0, m1, m2] in mol^(2/3), or null if using default constant model
   */
  public double[] getCachadinaInfluenceParameters() {
    return cachadinaCoeff;
  }
}
