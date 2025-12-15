package neqsim.thermo.mixingrule;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility class for estimating binary interaction parameters (BIPs) using correlations from the
 * literature.
 *
 * <p>
 * This class implements BIP estimation methods commonly used in PVT modeling, as described in the
 * Whitson wiki (https://wiki.whitson.com/eos/bips/). The correlations are particularly useful for
 * petroleum fluid characterization where experimental BIP data is not available.
 * </p>
 *
 * <h2>Available Methods:</h2>
 * <ul>
 * <li><b>Chueh-Prausnitz (1967)</b> - Based on critical volumes, suitable for HC-HC pairs</li>
 * <li><b>Katz-Firoozabadi (1978)</b> - Designed for methane-C7+ interactions</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * {@code
 * SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
 * fluid.addComponent("methane", 1.0);
 * fluid.addComponent("n-heptane", 0.1);
 * 
 * // Calculate BIP using Chueh-Prausnitz
 * double kij = BIPEstimator.estimateChuehPrausnitz(fluid.getComponent("methane"),
 *     fluid.getComponent("n-heptane"));
 * 
 * // Or apply to entire system
 * BIPEstimator.applyEstimatedBIPs(fluid, BIPEstimationMethod.CHUEH_PRAUSNITZ);
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see BIPEstimationMethod
 */
public final class BIPEstimator {

  /** Default exponent for Chueh-Prausnitz correlation. */
  public static final double DEFAULT_CHUEH_PRAUSNITZ_EXPONENT = 3.0;

  /** Katz-Firoozabadi coefficient A for methane BIPs with C7+. */
  private static final double KATZ_FIROOZABADI_A = 0.0289;

  /** Katz-Firoozabadi coefficient B for methane BIPs with C7+. */
  private static final double KATZ_FIROOZABADI_B = 0.0429;

  /** Private constructor to prevent instantiation. */
  private BIPEstimator() {}

  /**
   * Estimate binary interaction parameter using the Chueh-Prausnitz correlation (1967).
   *
   * <p>
   * The correlation is: kij = 1 - (2 * Vc_i^(1/3) * Vc_j^(1/3) / (Vc_i^(1/3) + Vc_j^(1/3)))^n
   * </p>
   *
   * <p>
   * where Vc is the critical volume and n is typically 3 (can be tuned).
   * </p>
   *
   * <p>
   * Reference: Chueh, P.L. and Prausnitz, J.M., "Vapor-liquid equilibria at high pressures:
   * calculation of partial molar volumes in nonpolar liquid mixtures", AIChE Journal, 13:1099-1107,
   * 1967.
   * </p>
   *
   * @param comp1 first component
   * @param comp2 second component
   * @return estimated BIP value
   */
  public static double estimateChuehPrausnitz(ComponentInterface comp1, ComponentInterface comp2) {
    return estimateChuehPrausnitz(comp1, comp2, DEFAULT_CHUEH_PRAUSNITZ_EXPONENT);
  }

  /**
   * Estimate binary interaction parameter using the Chueh-Prausnitz correlation with custom
   * exponent.
   *
   * @param comp1 first component
   * @param comp2 second component
   * @param exponent the exponent n in the correlation (typically 3)
   * @return estimated BIP value
   */
  public static double estimateChuehPrausnitz(ComponentInterface comp1, ComponentInterface comp2,
      double exponent) {
    if (comp1 == null || comp2 == null) {
      return 0.0;
    }

    double vc1 = comp1.getCriticalVolume();
    double vc2 = comp2.getCriticalVolume();

    if (vc1 <= 0 || vc2 <= 0) {
      return 0.0;
    }

    double vc1_third = Math.pow(vc1, 1.0 / 3.0);
    double vc2_third = Math.pow(vc2, 1.0 / 3.0);

    double ratio = (2.0 * Math.sqrt(vc1_third * vc2_third)) / (vc1_third + vc2_third);

    return 1.0 - Math.pow(ratio, exponent);
  }

  /**
   * Estimate binary interaction parameter for methane with C7+ components using Katz-Firoozabadi
   * correlation (1978).
   *
   * <p>
   * The correlation is designed specifically for methane interactions with heavy hydrocarbon
   * fractions: kij = A + B * (M_heavy - 86)^0.5
   * </p>
   *
   * <p>
   * where M_heavy is the molecular weight of the heavy component (g/mol), and 86 is the molecular
   * weight of hexane.
   * </p>
   *
   * <p>
   * Reference: Katz, D.L. and Firoozabadi, A., "Predicting phase behavior of condensate/crude-oil
   * systems using methane interaction coefficients", JPT, paper SPE-6721-PA, 1978.
   * </p>
   *
   * @param methaneComp methane component
   * @param heavyComp heavy hydrocarbon component (C7+)
   * @return estimated BIP value for methane-heavy HC pair
   */
  public static double estimateKatzFiroozabadi(ComponentInterface methaneComp,
      ComponentInterface heavyComp) {
    if (methaneComp == null || heavyComp == null) {
      return 0.0;
    }

    // Verify first component is methane
    String name = methaneComp.getComponentName().toLowerCase();
    if (!name.equals("methane") && !name.equals("c1")) {
      // Swap if heavy component is first
      ComponentInterface temp = methaneComp;
      methaneComp = heavyComp;
      heavyComp = temp;
      name = methaneComp.getComponentName().toLowerCase();
      if (!name.equals("methane") && !name.equals("c1")) {
        // Neither is methane - use Chueh-Prausnitz instead
        return estimateChuehPrausnitz(methaneComp, heavyComp);
      }
    }

    double molarMass = heavyComp.getMolarMass() * 1000.0; // Convert kg/mol to g/mol

    // Only apply to C7+ (MW > 86 g/mol, hexane MW)
    if (molarMass <= 86.0) {
      return estimateChuehPrausnitz(methaneComp, heavyComp);
    }

    return KATZ_FIROOZABADI_A + KATZ_FIROOZABADI_B * Math.sqrt(molarMass - 86.0);
  }

  /**
   * Estimate BIP using the specified method.
   *
   * @param comp1 first component
   * @param comp2 second component
   * @param method estimation method to use
   * @return estimated BIP value
   */
  public static double estimate(ComponentInterface comp1, ComponentInterface comp2,
      BIPEstimationMethod method) {
    switch (method) {
      case CHUEH_PRAUSNITZ:
        return estimateChuehPrausnitz(comp1, comp2);
      case KATZ_FIROOZABADI:
        return estimateKatzFiroozabadi(comp1, comp2);
      case DEFAULT:
      default:
        return 0.0;
    }
  }

  /**
   * Apply estimated BIPs to all component pairs in a fluid system.
   *
   * <p>
   * This method calculates BIPs for all component pairs using the specified estimation method and
   * applies them to the fluid's mixing rule.
   * </p>
   *
   * @param fluid the fluid system to modify
   * @param method estimation method to use
   */
  public static void applyEstimatedBIPs(SystemInterface fluid, BIPEstimationMethod method) {
    applyEstimatedBIPs(fluid, method, false);
  }

  /**
   * Apply estimated BIPs to all component pairs in a fluid system.
   *
   * @param fluid the fluid system to modify
   * @param method estimation method to use
   * @param overwriteExisting if true, overwrite existing non-zero BIPs; if false, only set BIPs
   *        that are currently zero
   */
  public static void applyEstimatedBIPs(SystemInterface fluid, BIPEstimationMethod method,
      boolean overwriteExisting) {
    if (fluid == null) {
      return;
    }

    int nComp = fluid.getNumberOfComponents();

    for (int i = 0; i < nComp; i++) {
      for (int j = i + 1; j < nComp; j++) {
        ComponentInterface comp1 = fluid.getComponent(i);
        ComponentInterface comp2 = fluid.getComponent(j);

        double estimatedBIP = estimate(comp1, comp2, method);

        // Apply the BIP using the system-level setter
        fluid.setBinaryInteractionParameter(i, j, estimatedBIP);
      }
    }
  }

  /**
   * Apply Katz-Firoozabadi BIPs specifically for methane-C7+ pairs in a fluid.
   *
   * <p>
   * This method is optimized for petroleum fluids where methane BIPs with heavy fractions are
   * critical for accurate phase behavior prediction.
   * </p>
   *
   * @param fluid the fluid system to modify
   */
  public static void applyMethaneC7PlusBIPs(SystemInterface fluid) {
    if (fluid == null) {
      return;
    }

    // Find methane component
    int methaneIndex = -1;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String name = fluid.getComponent(i).getComponentName().toLowerCase();
      if (name.equals("methane") || name.equals("c1")) {
        methaneIndex = i;
        break;
      }
    }

    if (methaneIndex < 0) {
      return; // No methane found
    }

    ComponentInterface methane = fluid.getComponent(methaneIndex);

    // Apply Katz-Firoozabadi for all C7+ components
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
      if (j == methaneIndex) {
        continue;
      }

      ComponentInterface comp = fluid.getComponent(j);
      double molarMass = comp.getMolarMass() * 1000.0; // g/mol

      // Only apply to C7+ (MW > 86 g/mol)
      if (molarMass > 86.0 || comp.isIsTBPfraction() || comp.isIsPlusFraction()) {
        double kij = estimateKatzFiroozabadi(methane, comp);
        fluid.setBinaryInteractionParameter(methaneIndex, j, kij);
      }
    }
  }

  /**
   * Calculate all BIPs for a fluid system using the specified method without applying them.
   *
   * <p>
   * This is useful for reviewing estimated values before applying them.
   * </p>
   *
   * @param fluid the fluid system
   * @param method estimation method to use
   * @return 2D array of estimated BIP values [i][j]
   */
  public static double[][] calculateBIPMatrix(SystemInterface fluid, BIPEstimationMethod method) {
    if (fluid == null) {
      return new double[0][0];
    }

    int nComp = fluid.getNumberOfComponents();
    double[][] bipMatrix = new double[nComp][nComp];

    for (int i = 0; i < nComp; i++) {
      for (int j = i + 1; j < nComp; j++) {
        double kij = estimate(fluid.getComponent(i), fluid.getComponent(j), method);
        bipMatrix[i][j] = kij;
        bipMatrix[j][i] = kij;
      }
    }

    return bipMatrix;
  }

  /**
   * Print BIP matrix to console for debugging/review.
   *
   * @param fluid the fluid system
   * @param bipMatrix the BIP matrix to print
   */
  public static void printBIPMatrix(SystemInterface fluid, double[][] bipMatrix) {
    if (fluid == null || bipMatrix == null) {
      return;
    }

    int nComp = fluid.getNumberOfComponents();
    System.out.println("Binary Interaction Parameters (kij):");
    System.out.print(String.format("%15s", ""));
    for (int j = 0; j < nComp; j++) {
      System.out.print(String.format("%12s", fluid.getComponent(j).getComponentName()));
    }
    System.out.println();

    for (int i = 0; i < nComp; i++) {
      System.out.print(String.format("%15s", fluid.getComponent(i).getComponentName()));
      for (int j = 0; j < nComp; j++) {
        System.out.print(String.format("%12.6f", bipMatrix[i][j]));
      }
      System.out.println();
    }
  }
}
