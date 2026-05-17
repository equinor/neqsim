/*
 * FilippovConductivityMethod.java
 *
 * Liquid thermal conductivity with Filippov mixing rule and optional pressure correction.
 */

package neqsim.physicalproperties.methods.liquidphysicalproperties.conductivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.methods.liquidphysicalproperties.LiquidPhysicalPropertyMethod;
import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;

/**
 * Liquid thermal conductivity using temperature-dependent pure component correlations with the
 * Filippov (1955) mixing rule and an optional Missenard (1965) pressure correction.
 *
 * <p>
 * The Filippov mixing rule accounts for non-linear mixing of liquid thermal conductivities:
 * </p>
 *
 * <p>
 * lambda_mix = sum_i(w_i * lambda_i) - 0.72 * sum_i sum_j&gt;i (w_i * w_j * |lambda_i - lambda_j|)
 * </p>
 *
 * <p>
 * The Missenard pressure correction adjusts the low-pressure conductivity for compressed liquids:
 * </p>
 *
 * <p>
 * lambda(T,P) = lambda(T,Psat) * (1 + Q * (P - Psat))
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Filippov, L.P. (1955). Vest. Mosk. Univ. Ser. Fiz.-Mat. Est. Nauk, 8, 67-69.</li>
 * <li>Missenard, F.A. (1965). Comptes Rendus, 260, 5521.</li>
 * <li>Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001). The Properties of Gases and Liquids,
 * 5th edition, Chapter 10.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FilippovConductivityMethod extends LiquidPhysicalPropertyMethod
    implements ConductivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FilippovConductivityMethod.class);

  double conductivity = 0;

  /** Pure component conductivities at current temperature. */
  public double[] pureComponentConductivity;

  /** Filippov binary interaction parameter. Default 0.72 (original Filippov). */
  private double filippovCoefficient = 0.72;

  /** Whether to apply Missenard pressure correction. */
  private boolean usePressureCorrection = true;

  /**
   * Constructor for FilippovConductivityMethod.
   *
   * @param liquidPhase a {@link neqsim.physicalproperties.system.PhysicalProperties} object
   */
  public FilippovConductivityMethod(PhysicalProperties liquidPhase) {
    super(liquidPhase);
    pureComponentConductivity = new double[liquidPhase.getPhase().getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public FilippovConductivityMethod clone() {
    FilippovConductivityMethod properties = null;

    try {
      properties = (FilippovConductivityMethod) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    calcPureComponentConductivity();

    int ncomp = liquidPhase.getPhase().getNumberOfComponents();

    // Linear mixing term: sum(w_i * lambda_i)
    double linearSum = 0.0;
    for (int i = 0; i < ncomp; i++) {
      double wi = liquidPhase.getPhase().getWtFrac(i);
      linearSum += wi * pureComponentConductivity[i];
    }

    // Filippov correction: -0.72 * sum_i sum_{j>i} w_i * w_j * |lambda_i - lambda_j|
    double correctionSum = 0.0;
    for (int i = 0; i < ncomp; i++) {
      double wi = liquidPhase.getPhase().getWtFrac(i);
      for (int j = i + 1; j < ncomp; j++) {
        double wj = liquidPhase.getPhase().getWtFrac(j);
        correctionSum +=
            wi * wj * Math.abs(pureComponentConductivity[i] - pureComponentConductivity[j]);
      }
    }

    conductivity = linearSum - filippovCoefficient * correctionSum;

    // Apply Missenard pressure correction if enabled
    if (usePressureCorrection) {
      conductivity *= getMissenardPressureCorrection();
    }

    if (conductivity < 1e-10) {
      conductivity = 1e-10;
    }

    return conductivity;
  }

  /**
   * Calculates pure component liquid thermal conductivities using polynomial correlation from
   * database parameters.
   */
  public void calcPureComponentConductivity() {
    double temp = liquidPhase.getPhase().getTemperature();
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      pureComponentConductivity[i] =
          liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(0)
              + liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(1) * temp
              + liquidPhase.getPhase().getComponent(i).getLiquidConductivityParameter(2) * temp
                  * temp;
      if (pureComponentConductivity[i] < 1e-10) {
        pureComponentConductivity[i] = 1e-10;
      }
    }
  }

  /**
   * Calculates the Missenard pressure correction factor for compressed liquids.
   *
   * <p>
   * The correction is based on the reduced pressure and temperature of the mixture. At typical
   * process conditions (P &lt; 50 bar), this is a small (1-3%) correction.
   * </p>
   *
   * @return pressure correction factor (dimensionless, &gt;= 1.0)
   */
  private double getMissenardPressureCorrection() {
    double pressure = liquidPhase.getPhase().getPressure(); // bara
    double temp = liquidPhase.getPhase().getTemperature();

    // Estimate mixture critical properties
    double tcMix = 0.0;
    double pcMix = 0.0;
    for (int i = 0; i < liquidPhase.getPhase().getNumberOfComponents(); i++) {
      double xi = liquidPhase.getPhase().getComponent(i).getx();
      tcMix += xi * liquidPhase.getPhase().getComponent(i).getTC();
      pcMix += xi * liquidPhase.getPhase().getComponent(i).getPC();
    }

    if (pcMix < 1e-10 || tcMix < 1e-10) {
      return 1.0;
    }

    double tr = temp / tcMix;
    double pr = pressure / pcMix;

    // Missenard correlation: Q = 0.98 + 0.0079 * Pr * Tr^0.5
    // Valid for Tr < 0.95
    if (tr >= 0.95 || pr < 0.01) {
      return 1.0;
    }

    double qFactor = 0.98 + 0.0079 * pr * Math.sqrt(tr);
    if (qFactor < 1.0) {
      qFactor = 1.0;
    }
    return qFactor;
  }

  /**
   * Sets the Filippov binary interaction coefficient. Default is 0.72 per the original paper. A
   * value of 0.0 gives a simple weight-fraction linear mixing rule.
   *
   * @param coefficient the Filippov coefficient (typically 0.5-1.0)
   */
  public void setFilippovCoefficient(double coefficient) {
    this.filippovCoefficient = coefficient;
  }

  /**
   * Gets the Filippov binary interaction coefficient.
   *
   * @return the Filippov coefficient
   */
  public double getFilippovCoefficient() {
    return filippovCoefficient;
  }

  /**
   * Enables or disables the Missenard pressure correction for compressed liquids.
   *
   * @param use true to enable pressure correction (default), false to disable
   */
  public void setUsePressureCorrection(boolean use) {
    this.usePressureCorrection = use;
  }

  /**
   * Gets whether pressure correction is enabled.
   *
   * @return true if pressure correction is enabled
   */
  public boolean isUsePressureCorrection() {
    return usePressureCorrection;
  }
}
