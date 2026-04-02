/*
 * Rackett.java
 *
 * Created on 2. April 2026
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.density;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * <p>
 * Spencer-Danner modified Rackett equation for saturated liquid density.
 * </p>
 *
 * <p>
 * The Rackett equation relates saturated liquid molar volume to reduced temperature using a single
 * substance-specific parameter Z_RA. The Spencer-Danner modification (1972) uses an empirical Z_RA
 * parameter fitted to experimental data instead of the critical compressibility factor Z_c.
 * </p>
 *
 * <p>
 * For mixtures, the method uses the Li (1971) mixing rule:
 * </p>
 * <ul>
 * <li>Z_RA_m = sum(x_i * Z_RA_i)</li>
 * <li>Tc_m = sum(x_i * Vc_i * Tc_i) / sum(x_i * Vc_i)</li>
 * <li>Pc_m = R * Tc_m * sum(x_i * Z_c_i) / sum(x_i * Vc_i)</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Rackett, H.G., J. Chem. Eng. Data 15, 514-517 (1970)</li>
 * <li>Spencer, C.F. and Danner, R.P., J. Chem. Eng. Data 17, 236-241 (1972)</li>
 * <li>Poling, B.E. et al., The Properties of Gases and Liquids, 5th ed., McGraw-Hill (2001),
 * Chapter 4</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Rackett extends LiquidPhysicalPropertyMethod implements DensityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Rackett.class);

  /** Gas constant in cm3 bar / (mol K). */
  private static final double R_CM3_BAR = 83.14;

  /**
   * <p>
   * Constructor for Rackett.
   * </p>
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public Rackett(PhysicalProperties liquidPhase) {
    super(liquidPhase);
  }

  /** {@inheritDoc} */
  @Override
  public Rackett clone() {
    Rackett properties = null;

    try {
      properties = (Rackett) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates liquid density using the Spencer-Danner modified Rackett equation. Returns density
   * in kg/m3.
   * </p>
   *
   * <p>
   * The saturated liquid molar volume is:
   * </p>
   *
   * <pre>
   * V_s = (R * Tc / Pc) * Z_RA ^ (1 + (1 - Tr) ^ (2 / 7))
   * </pre>
   *
   * <p>
   * For mixtures, the Li (1971) mixing rules are used to compute pseudo-critical properties and
   * mixed Z_RA.
   * </p>
   */
  @Override
  public double calcDensity() {
    int nc = liquidPhase.getPhase().getNumberOfComponents();

    // --- Mixture parameters via Li (1971) mixing rules ---
    double sumXiVci = 0.0;
    double sumXiVciTci = 0.0;
    double sumXiZci = 0.0;
    double zraMix = 0.0;

    for (int i = 0; i < nc; i++) {
      double xi = liquidPhase.getPhase().getComponent(i).getx();
      double vci = liquidPhase.getPhase().getComponent(i).getCriticalVolume(); // cm3/mol
      double tci = liquidPhase.getPhase().getComponent(i).getTC(); // K
      double pci = liquidPhase.getPhase().getComponent(i).getPC(); // bar
      double zraI = getZRA(i);

      double zci = 0.0;
      if (tci > 0.0) {
        zci = pci * vci / (R_CM3_BAR * tci);
      }

      sumXiVci += xi * vci;
      sumXiVciTci += xi * vci * tci;
      sumXiZci += xi * zci;
      zraMix += xi * zraI;
    }

    if (sumXiVci <= 0.0) {
      logger.warn("Rackett: sum(xi*Vci) <= 0, falling back to EOS density");
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    double tcMix = sumXiVciTci / sumXiVci;
    double pcMix = R_CM3_BAR * tcMix * sumXiZci / sumXiVci;

    double temperature = liquidPhase.getPhase().getTemperature();
    double reducedTemperature = temperature / tcMix;

    if (reducedTemperature >= 1.0) {
      logger.warn("Rackett: reduced temperature {} >= 1.0, method not valid above Tc",
          reducedTemperature);
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    if (pcMix <= 0.0) {
      logger.warn("Rackett: pseudo-critical pressure <= 0, falling back to EOS density");
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    // Rackett equation: V_s = (R * Tc / Pc) * Z_RA^(1 + (1 - Tr)^(2/7))
    double tau = 1.0 - reducedTemperature;
    double exponent = 1.0 + Math.pow(tau, 2.0 / 7.0);
    double vsLiquid = (R_CM3_BAR * tcMix / pcMix) * Math.pow(zraMix, exponent); // cm3/mol

    if (vsLiquid <= 0.0) {
      logger.warn("Rackett: computed molar volume <= 0, falling back to EOS density");
      return 1.0 / liquidPhase.getPhase().getMolarVolume() * liquidPhase.getPhase().getMolarMass()
          * 1.0e5;
    }

    // Convert cm3/mol to density kg/m3: rho = M [kg/mol] / (V [cm3/mol] * 1e-6 [m3/cm3])
    double molarMass = liquidPhase.getPhase().getMolarMass();
    return molarMass / (vsLiquid * 1.0e-6);
  }

  /**
   * Gets the Rackett parameter Z_RA for a component.
   *
   * <p>
   * Priority order:
   * </p>
   * <ol>
   * <li>Database Z_RA value (Component.getRacketZ()) if positive</li>
   * <li>Estimated from critical compressibility: Z_RA = 0.29056 - 0.08775 * omega (Yamada-Gunn,
   * 1973)</li>
   * </ol>
   *
   * @param compIdx component index in the phase
   * @return Z_RA dimensionless Rackett parameter
   */
  private double getZRA(int compIdx) {
    double zra = liquidPhase.getPhase().getComponent(compIdx).getRacketZ();
    if (zra > 0.0) {
      return zra;
    }

    // Yamada-Gunn correlation: Z_RA = 0.29056 - 0.08775 * omega
    double omega = liquidPhase.getPhase().getComponent(compIdx).getAcentricFactor();
    return 0.29056 - 0.08775 * omega;
  }
}
