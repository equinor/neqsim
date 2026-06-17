package neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Calculates the viscosity of aqueous amine solutions (MEA, DEA, MDEA, aMDEA).
 *
 * <p>
 * Uses published correlations from Weiland et al. (1998) for CO2-loaded solutions and Teng et al.
 * (1994) for MDEA. Supports single amines and activated MDEA blends (MDEA + piperazine).
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Weiland, R.H., Dingman, J.C., Cronin, D.B., Browning, G.J. (1998). Density, Viscosity, and
 * Surface Tension of Partially Carbonated Aqueous Alkanolamine Solutions. J. Chem. Eng. Data, 43,
 * 378-382.</li>
 * <li>Teng, T.T., Maham, Y., Hepler, L.G., Mather, A.E. (1994). Viscosity of Aqueous Solutions of
 * N-Methyldiethanolamine and of Diethanolamine. J. Chem. Eng. Data, 39, 290-293.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class AmineViscosity extends Viscosity {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AmineViscosity.class);

  /**
   * Enum representing supported amine types for viscosity calculation.
   */
  public enum AmineType {
    /** Monoethanolamine. */
    MEA,
    /** Diethanolamine. */
    DEA,
    /** Methyldiethanolamine. */
    MDEA,
    /** Activated MDEA (MDEA + Piperazine blend). */
    AMDEA,
    /** Unknown amine type. */
    UNKNOWN
  }

  /**
   * Constructor for AmineViscosity.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public AmineViscosity(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    super.calcViscosity();
    AmineType type = detectAmineType();
    double T = liquidPhase.getPhase().getTemperature();
    double viscosity;

    switch (type) {
      case MEA:
        viscosity = calcMEAViscosity(T);
        break;
      case DEA:
        viscosity = calcDEAViscosity(T);
        break;
      case MDEA:
        viscosity = calcMDEAViscosity(T);
        break;
      case AMDEA:
        viscosity = calcAMDEAViscosity(T);
        break;
      default:
        viscosity = calcMDEAViscosity(T);
        break;
    }

    return viscosity;
  }

  /**
   * Detects which amine type is present in the liquid phase.
   *
   * @return the detected {@link AmineType}
   */
  AmineType detectAmineType() {
    boolean hasMEA = hasComponent("MEA");
    boolean hasDEA = hasComponent("DEA");
    boolean hasMDEA = hasComponent("MDEA");
    boolean hasPiperazine = hasComponent("Piperazine");

    if (hasMDEA && hasPiperazine) {
      return AmineType.AMDEA;
    } else if (hasMEA) {
      return AmineType.MEA;
    } else if (hasDEA) {
      return AmineType.DEA;
    } else if (hasMDEA) {
      return AmineType.MDEA;
    }
    return AmineType.UNKNOWN;
  }

  /**
   * Checks if a component is present in the liquid phase.
   *
   * @param name the component name to check
   * @return true if the component exists and has non-negligible mole fraction
   */
  private boolean hasComponent(String name) {
    try {
      double x = liquidPhase.getPhase().getComponent(name).getx();
      return x > 1.0e-20;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Gets the effective weight fraction of an amine, including its protonated form.
   *
   * @param amineName the neutral amine name (e.g., "MEA")
   * @param ionName the protonated ion name (e.g., "MEA+")
   * @return the combined weight fraction of amine + ion
   */
  private double getAmineWtFrac(String amineName, String ionName) {
    double wtFrac = 0.0;
    try {
      wtFrac += liquidPhase.getPhase().getComponent(amineName).getx()
          * liquidPhase.getPhase().getComponent(amineName).getMolarMass()
          / liquidPhase.getPhase().getMolarMass();
    } catch (Exception e) {
      // component not present
    }
    try {
      wtFrac += liquidPhase.getPhase().getComponent(ionName).getx()
          * liquidPhase.getPhase().getComponent(ionName).getMolarMass()
          / liquidPhase.getPhase().getMolarMass();
    } catch (Exception e) {
      // component not present
    }
    return wtFrac;
  }

  /**
   * Calculates viscosity of water at the given temperature using the DIPPR correlation.
   *
   * @param T temperature in Kelvin
   * @return water viscosity in Pa.s
   */
  private double calcWaterViscosity(double T) {
    // Simplified Andrade correlation for water viscosity (Pa.s)
    return Math.exp(-52.843 + 3703.6 / T + 5.866 * Math.log(T) - 5.879e-29 * Math.pow(T, 10));
  }

  /**
   * Calculates the viscosity of a loaded MEA solution.
   *
   * <p>
   * Uses the Weiland et al. (1998) correlation for loaded MEA solutions: ln(mu/mu_w) = [(alpha*a +
   * b)*w/T + (alpha*c + d)*w] * (alpha*e + 1)
   * </p>
   *
   * @param T temperature in Kelvin
   * @return viscosity in Pa.s (N.s/m2)
   */
  private double calcMEAViscosity(double T) {
    double w = getAmineWtFrac("MEA", "MEA+");
    if (w < 1.0e-10) {
      return calcWaterViscosity(T);
    }

    // Weiland et al. (1998) coefficients for MEA
    double a = 0.0;
    double b = 21.186;
    double c = 2373.0;
    double d = 0.01015;
    double e = 0.0093;

    double alpha = estimateCO2Loading("MEA", "MEA+");

    double muW = calcWaterViscosity(T);
    double exponent = ((alpha * a + b) * w / T + (alpha * c + d) * w) * (alpha * e + 1.0);
    return muW * Math.exp(exponent);
  }

  /**
   * Calculates the viscosity of a loaded DEA solution.
   *
   * <p>
   * Uses the Weiland et al. (1998) correlation for loaded DEA solutions.
   * </p>
   *
   * @param T temperature in Kelvin
   * @return viscosity in Pa.s
   */
  private double calcDEAViscosity(double T) {
    double w = getAmineWtFrac("DEA", "DEA+");
    if (w < 1.0e-10) {
      return calcWaterViscosity(T);
    }

    // Weiland et al. (1998) coefficients for DEA
    double a = 0.0;
    double b = 24.702;
    double c = 3271.2;
    double d = 0.01236;
    double e = 0.01045;

    double alpha = estimateCO2Loading("DEA", "DEA+");

    double muW = calcWaterViscosity(T);
    double exponent = ((alpha * a + b) * w / T + (alpha * c + d) * w) * (alpha * e + 1.0);
    return muW * Math.exp(exponent);
  }

  /**
   * Calculates the viscosity of a loaded MDEA solution.
   *
   * <p>
   * Uses the Teng et al. (1994) correlation for unloaded MDEA. For loaded solutions, applies
   * Weiland et al. (1998) loading correction via blending.
   * </p>
   *
   * @param T temperature in Kelvin
   * @return viscosity in Pa.s
   */
  private double calcMDEAViscosity(double T) {
    double w = getAmineWtFrac("MDEA", "MDEA+");
    if (w < 1.0e-10) {
      return calcWaterViscosity(T);
    }

    // Teng et al. (1994) fit for MDEA (original model)
    double viscA = -12.197 - 8.905 * w;
    double viscB = 1438.717 + 4218.749 * w;
    double logviscosity = viscA + viscB / T;
    double viscTeng = Math.exp(logviscosity);

    double alpha = estimateCO2Loading("MDEA", "MDEA+");

    // Weiland loading correction for loaded solutions
    if (alpha > 0.001) {
      double a = 0.0;
      double b = 21.186;
      double c = 2373.0;
      double d = 0.01015;
      double e = 0.0093;
      double muW = calcWaterViscosity(T);
      double exponent = ((alpha * a + b) * w / T + (alpha * c + d) * w) * (alpha * e + 1.0);
      double viscWeiland = muW * Math.exp(exponent);
      return viscTeng * (1.0 - alpha) + viscWeiland * alpha;
    }

    return viscTeng;
  }

  /**
   * Calculates the viscosity of an activated MDEA (MDEA + Piperazine) solution.
   *
   * <p>
   * Uses a logarithmic mixing rule between MDEA viscosity and piperazine contribution.
   * </p>
   *
   * @param T temperature in Kelvin
   * @return viscosity in Pa.s
   */
  private double calcAMDEAViscosity(double T) {
    double wMDEA = getAmineWtFrac("MDEA", "MDEA+");
    double wPZ = 0.0;
    try {
      wPZ = liquidPhase.getPhase().getComponent("Piperazine").getx()
          * liquidPhase.getPhase().getComponent("Piperazine").getMolarMass()
          / liquidPhase.getPhase().getMolarMass();
    } catch (Exception e) {
      // no piperazine
    }
    try {
      wPZ += liquidPhase.getPhase().getComponent("Piperazine+").getx()
          * liquidPhase.getPhase().getComponent("Piperazine+").getMolarMass()
          / liquidPhase.getPhase().getMolarMass();
    } catch (Exception e) {
      // no Piperazine+
    }

    double viscMDEA = calcMDEAViscosity(T);

    if (wPZ < 1.0e-10) {
      return viscMDEA;
    }

    // Piperazine contribution
    double muW = calcWaterViscosity(T);
    double viscPZ = muW * Math.exp(21.186 * wPZ / T + 0.01015 * wPZ);

    // Logarithmic mixing rule for blend
    double wTotal = wMDEA + wPZ;
    if (wTotal < 1.0e-10) {
      return muW;
    }
    double fracMDEA = wMDEA / wTotal;
    double fracPZ = wPZ / wTotal;

    return Math.exp(fracMDEA * Math.log(viscMDEA) + fracPZ * Math.log(viscPZ));
  }

  /**
   * Estimates the CO2 loading (mol CO2 / mol amine) from the liquid phase composition.
   *
   * <p>
   * Estimates loading from the fraction of amine that has been protonated (reacted).
   * </p>
   *
   * @param amineName the neutral amine component name
   * @param ionName the protonated ion component name
   * @return estimated CO2 loading (dimensionless)
   */
  private double estimateCO2Loading(String amineName, String ionName) {
    try {
      double xAmine = liquidPhase.getPhase().getComponent(amineName).getx();
      double xIon = liquidPhase.getPhase().getComponent(ionName).getx();
      double totalAmine = xAmine + xIon;
      if (totalAmine < 1.0e-20) {
        return 0.0;
      }
      return xIon / totalAmine;
    } catch (Exception e) {
      return 0.0;
    }
  }
}
