package neqsim.thermo.component.attractiveeosterm;

import neqsim.thermo.component.ComponentEosInterface;

/**
 * <p>
 * AttractiveTermSoreideWhitson class.
 * </p>
 *
 * <p>
 * Implements the modified alpha function for the Søreide-Whitson method specifically tailored for
 * water in systems where salinity is a factor. This attractive term modifies the standard
 * Peng-Robinson 1978 alpha function for water based on reduced temperature and salinity.
 * </p>
 *
 * <p>
 * The alpha function is defined as: {@code alpha = A^2} where:
 * {@code A(Tr) = 1.0 + 0.453 * (1.0 - Tr * (1.0 - 0.0103 * salinity^1.1)) + 0.0034 * (Tr^(-3) - 1.0)}
 * and {@code Tr = T / Tc} (Reduced Temperature).
 * </p>
 *
 * <p>
 * This class extends {@link AttractiveTermPr1978} and overrides its methods for water component.
 * </p>
 *
 * @author Even Solbraa
 * @author (corrected by) NeqSim developers
 * @version $Id: $Id
 */
public class AttractiveTermSoreideWhitson extends AttractiveTermPr1978 {
  private static final long serialVersionUID = 1L;

  /**
   * Stores the salinity value for use in alpha and derivative calculations.
   */
  private double salinityFromPhase = 0.0;

  /**
   * <p>
   * Constructor for AttractiveTermSoreideWhitson.
   * </p>
   *
   * @param component The component to which this attractive term is associated.
   */
  public AttractiveTermSoreideWhitson(ComponentEosInterface component) {
    super(component);
  }

  /**
   * Sets the salinity value to be used in calculations.
   * 
   * @param salinity the salinity value to set
   */
  public void setSalinityFromPhase(double salinity) {
    this.salinityFromPhase = salinity;
  }

  /**
   * Gets the salinity value set for calculations.
   * 
   * @return the salinity value, or 0.0 if not set
   */
  private double getSalinityFromPhase() {
    return salinityFromPhase;
  }

  /**
   * <p>
   * Calculates the alpha function value for the Søreide-Whitson attractive term.
   * </p>
   *
   * <p>
   * This override applies only if the component is "water". For other components, it delegates to
   * the superclass's `alpha` method (Peng-Robinson 1978). The formula for water is:
   * </p>
   * {@code alpha = A^2}
   * <p>
   * where:
   * </p>
   * {@code A(Tr) = 1.0 + 0.453 * (1.0 - Tr * (1.0 - 0.0103 * salinity^1.1)) + 0.0034 * (Tr^(-3) - 1.0)}
   * <p>
   * and {@code Tr = T / Tc} (Reduced Temperature).
   * </p>
   *
   * @param temperature The temperature in Kelvin.
   * @return The calculated alpha value.
   */
  @Override
  public double alpha(double temperature) {
    if (!getComponent().getComponentName().equalsIgnoreCase("water")) {
      return super.alpha(temperature);
    }
    double Tr = temperature / getComponent().getTC();
    double salinity = getSalinityFromPhase();

    // Define A(Tr)
    double alpha_A = 1.0 + 0.453 * (1.0 - Tr * (1.0 - 0.0103 * Math.pow(salinityFromPhase, 1.1)))
        + 0.0034 * (Math.pow(1.0 / Tr, 3.0) - 1.0);

    return alpha_A * alpha_A;
  }

  /**
   * <p>
   * Calculates the first derivative of the alpha function with respect to temperature.
   * </p>
   *
   * <p>
   * This override applies only if the component is "water". For other components, it delegates to
   * the superclass's `diffalphaT` method. The derivative is calculated using the chain rule:
   * </p>
   * {@code d(alpha)/dT = d(A^2)/dT = 2 * A * dA/dT}
   * <p>
   * where:
   * </p>
   * {@code dA/dT = (dA/dTr) * (dTr/dT)}
   * <p>
   * and
   * </p>
   * {@code dA/dTr = -0.453 * (1.0 - 0.0103 * salinity^1.1) - 3.0 * 0.0034 * Tr^(-4)}
   * {@code dTr/dT = 1 / Tc}
   *
   * @param temperature The temperature in Kelvin.
   * @return The first derivative of alpha with respect to temperature.
   */
  @Override
  public double diffalphaT(double temperature) {
    if (!getComponent().getComponentName().equalsIgnoreCase("water")) {
      return super.diffalphaT(temperature);
    }
    double Tr = temperature / getComponent().getTC();
    double Tc = getComponent().getTC();
    double salinity = getSalinityFromPhase();

    double dTrdT = 1.0 / Tc;
    double powSal = Math.pow(salinity, 1.1);

    // Corrected calculation of dA/dTr: derivative of A with respect to Tr
    // dA/dTr = d/dTr [1.0 + C1 * (1.0 - Tr * S_term) + C2 * (Tr^(-3) - 1.0)]
    // = -C1 * S_term + C2 * (-3) * Tr^(-4)
    // C1 = 0.453, C2 = 0.0034, S_term = (1.0 - 0.0103 * powSal)
    double dAlpha_dTr = -0.453 * (1.0 - 0.0103 * powSal) - 3.0 * 0.0034 * Math.pow(1.0 / Tr, 4.0);

    // Recalculate A(Tr) to ensure consistency with the alpha() method
    double alpha_A = 1.0 + 0.453 * (1.0 - Tr * (1.0 - 0.0103 * powSal))
        + 0.0034 * (Math.pow(1.0 / Tr, 3.0) - 1.0);

    // Total derivative: d(alpha^2)/dT = 2 * A * (dA/dTr * dTr/dT)
    return 2.0 * alpha_A * dAlpha_dTr * dTrdT;
  }

  /**
   * <p>
   * Calculates the second derivative of the alpha function with respect to temperature.
   * </p>
   *
   * <p>
   * This override applies only if the component is "water". For other components, it delegates to
   * the superclass's `diffdiffalphaT` method. The second derivative is calculated using the product
   * and chain rules:
   * </p>
   * {@code d^2(alpha)/dT^2 = d^2(A^2)/dT^2 = 2 * (dA/dT)^2 + 2 * A * (d^2A/dT^2)}
   * <p>
   * where:
   * </p>
   * {@code dA/dT = dAlpha_dTr * dTrdT} (from `diffalphaT`)
   * <p>
   * and:
   * </p>
   * {@code d^2A/dT^2 = (d^2A/dTr^2) * (dTr/dT)^2}
   * <p>
   * with:
   * </p>
   * {@code d^2A/dTr^2 = d/dTr [ -0.453 * S_term - 3.0 * 0.0034 * Tr^(-4) ]}
   * {@code           = 12.0 * 0.0034 * Tr^(-5)} {@code dTr/dT = 1 / Tc}
   *
   * @param temperature The temperature in Kelvin.
   * @return The second derivative of alpha with respect to temperature.
   */
  @Override
  public double diffdiffalphaT(double temperature) {
    if (!getComponent().getComponentName().equalsIgnoreCase("water")) {
      return super.diffdiffalphaT(temperature);
    }
    double Tr = temperature / getComponent().getTC();
    double Tc = getComponent().getTC();
    double salinity = getSalinityFromPhase();

    double dTrdT = 1.0 / Tc;
    double powSal = Math.pow(salinity, 1.1);

    // dA/dTr (same as in diffalphaT)
    double dAlpha_dTr = -0.453 * (1.0 - 0.0103 * powSal) - 3.0 * 0.0034 * Math.pow(1.0 / Tr, 4.0);

    // Corrected calculation of d^2A/dTr^2: second derivative of A with respect to
    // Tr
    // d^2A/dTr^2 = d/dTr [-3.0 * C2 * Tr^(-4)] = -3.0 * C2 * (-4) * Tr^(-5) = 12.0
    // * C2 * Tr^(-5)
    // C2 = 0.0034
    double d2Alpha_dTr2 = 12.0 * 0.0034 * Math.pow(1.0 / Tr, 5.0);

    // Recalculate A(Tr) to ensure consistency
    double alpha_A = 1.0 + 0.453 * (1.0 - Tr * (1.0 - 0.0103 * powSal))
        + 0.0034 * (Math.pow(1.0 / Tr, 3.0) - 1.0);

    // Total second derivative: 2 * (dA/dT)^2 + 2 * A * (d^2A/dT^2)
    // where dA/dT = dAlpha_dTr * dTrdT
    // and d^2A/dT^2 = d2Alpha_dTr2 * dTrdT^2
    return 2.0 * dAlpha_dTr * dAlpha_dTr * dTrdT * dTrdT
        + 2.0 * alpha_A * d2Alpha_dTr2 * dTrdT * dTrdT;
  }
}
