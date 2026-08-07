package neqsim.process.corrosion;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Converts a laboratory pH measured at ambient temperature into the in-situ pH at operating temperature for an
 * amine-buffered aqueous fluid, and reports the alkaline margin above neutrality at both temperatures.
 *
 * <p>
 * Closed heating- and cooling-medium loops are routinely controlled on a pH measured on a cooled sample, typically at
 * 20-25 &deg;C, while the corrosion they are meant to prevent happens at 100-150 &deg;C or more. Two things move with
 * temperature and they move by different amounts:
 * </p>
 *
 * <ul>
 * <li>The amine pKa falls, so a buffered fluid becomes less alkaline in absolute pH terms.</li>
 * <li>Neutrality itself falls, because the water ion product rises: neutral water is pH 7.00 at 25 &deg;C but about pH
 * 5.85 at 150 &deg;C.</li>
 * </ul>
 *
 * <p>
 * Comparing a hot-system pH against the familiar pH 7 neutral point is therefore meaningless. What matters for the
 * stability of a protective magnetite film is the <i>alkaline margin</i>, {@code pH(T) - pH_neutral(T)}, which this
 * class reports at both the measurement and the operating temperature.
 * </p>
 *
 * <p>
 * For a buffered solution the base-to-acid ratio is fixed by mass balance and does not change with temperature, so the
 * Henderson-Hasselbalch relation gives the exact result that the pH shift equals the pKa shift:
 * </p>
 *
 * <p>
 * {@code pH(T_op) = pH_meas + [pKa(T_op) - pKa(T_meas)]}
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * AmineBufferedPH calc = new AmineBufferedPH();
 * calc.setAmine(BufferAmine.DEA);
 * calc.setMeasuredPH(8.7, 20.0);
 * calc.setOperatingTemperature(150.0);
 * AmineBufferedPHResult r = calc.calculate();
 * </pre>
 *
 * <p>
 * <b>Basis and limitations.</b> The calculation is an ideal-solution buffer shift: activity coefficients are not
 * included, so it is a screening estimate rather than a rigorous electrolyte solution. A full electrolyte flash on the
 * same reaction data gives a somewhat smaller shift at high temperature, so the true in-situ pH is bracketed between
 * the two. Any co-solvent such as a glycol lowers the dielectric constant and raises the apparent pKa; that effect is
 * not corrected here and is reported as a warning when a glycol fraction is declared.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see RobustAqueousPH
 */
public class AmineBufferedPH {
  private static final Logger logger = LogManager.getLogger(AmineBufferedPH.class);

  /** Upper temperature of the range over which the amine correlations are well established [C]. */
  private static final double CORRELATION_LIMIT_C = 150.0;

  /** Glycol mass fraction above which the co-solvent effect on pKa is flagged. */
  private static final double GLYCOL_WARNING_FRACTION = 0.05;

  private BufferAmine amine = BufferAmine.DEA;
  private double measuredPH = Double.NaN;
  private double measurementTemperatureC = 25.0;
  private double operatingTemperatureC = Double.NaN;
  private double glycolMassFraction = 0.0;

  /**
   * Create a buffered-pH temperature correction with no inputs set.
   */
  public AmineBufferedPH() {
  }

  /**
   * Set the buffering amine.
   *
   * @param amine the amine; must not be null
   * @return this calculator for chaining
   * @throws IllegalArgumentException if the amine is null
   */
  public AmineBufferedPH setAmine(BufferAmine amine) {
    if (amine == null) {
      throw new IllegalArgumentException("Amine must not be null");
    }
    this.amine = amine;
    return this;
  }

  /**
   * Set the laboratory pH and the temperature at which it was measured.
   *
   * @param pH the measured pH; must lie between 0 and 14
   * @param measurementTemperatureC the sample temperature at measurement [C]
   * @return this calculator for chaining
   * @throws IllegalArgumentException if the pH is outside 0 to 14
   */
  public AmineBufferedPH setMeasuredPH(double pH, double measurementTemperatureC) {
    if (!(pH >= 0.0) || !(pH <= 14.0)) {
      throw new IllegalArgumentException("Measured pH must be between 0 and 14");
    }
    this.measuredPH = pH;
    this.measurementTemperatureC = measurementTemperatureC;
    return this;
  }

  /**
   * Set the temperature at which the corrosion process of interest occurs.
   *
   * @param temperatureC operating temperature [C]
   * @return this calculator for chaining
   */
  public AmineBufferedPH setOperatingTemperature(double temperatureC) {
    this.operatingTemperatureC = temperatureC;
    return this;
  }

  /**
   * Declare the glycol mass fraction of the fluid. The value is not used to correct the pKa; it only triggers a warning
   * that the co-solvent effect is unmodelled.
   *
   * @param massFraction glycol mass fraction, between 0 and 1
   * @return this calculator for chaining
   * @throws IllegalArgumentException if the fraction is outside 0 to 1
   */
  public AmineBufferedPH setGlycolMassFraction(double massFraction) {
    if (!(massFraction >= 0.0) || !(massFraction <= 1.0)) {
      throw new IllegalArgumentException("Glycol mass fraction must be between 0 and 1");
    }
    this.glycolMassFraction = massFraction;
    return this;
  }

  /**
   * Compute the neutral pH of water at a given temperature from the ion product, using
   * {@code pKw = 4470.99/T - 6.0875 + 0.01706*T}. The correlation reproduces pKw 14.00 at 25 &deg;C, 12.26 at 100
   * &deg;C and 11.70 at 150 &deg;C.
   *
   * @param temperatureC temperature [C]; must be above absolute zero
   * @return the neutral pH, equal to pKw / 2
   * @throws IllegalArgumentException if the temperature is at or below absolute zero
   */
  public static double neutralPH(double temperatureC) {
    double t = temperatureC + 273.15;
    if (!(t > 0.0)) {
      throw new IllegalArgumentException("Temperature must be above absolute zero");
    }
    return (4470.99 / t - 6.0875 + 0.01706 * t) / 2.0;
  }

  /**
   * Classify an alkaline margin into a screening band.
   *
   * @param margin the alkaline margin {@code pH - pH_neutral}
   * @return the screening verdict
   */
  public static AlkalineMarginVerdict classify(double margin) {
    if (margin >= 2.0) {
      return AlkalineMarginVerdict.ROBUST;
    } else if (margin >= 1.5) {
      return AlkalineMarginVerdict.ADEQUATE;
    } else if (margin >= 1.0) {
      return AlkalineMarginVerdict.MARGINAL;
    }
    return AlkalineMarginVerdict.INSUFFICIENT;
  }

  /**
   * Run the temperature correction.
   *
   * @return an immutable result
   * @throws IllegalStateException if the measured pH or the operating temperature has not been set
   */
  public AmineBufferedPHResult calculate() {
    if (Double.isNaN(measuredPH)) {
      throw new IllegalStateException("Measured pH must be set");
    }
    if (Double.isNaN(operatingTemperatureC)) {
      throw new IllegalStateException("Operating temperature must be set");
    }
    List<String> warnings = new ArrayList<String>();

    double pKaMeasurement = amine.getPKa(measurementTemperatureC + 273.15);
    double pKaOperating = amine.getPKa(operatingTemperatureC + 273.15);
    double shift = pKaOperating - pKaMeasurement;
    double operatingPH = measuredPH + shift;

    double neutralAtMeasurement = neutralPH(measurementTemperatureC);
    double neutralAtOperating = neutralPH(operatingTemperatureC);
    double marginAtMeasurement = measuredPH - neutralAtMeasurement;
    double marginAtOperating = operatingPH - neutralAtOperating;

    AlkalineMarginVerdict verdict = classify(marginAtOperating);

    if (operatingTemperatureC > CORRELATION_LIMIT_C) {
      warnings.add("Operating temperature " + operatingTemperatureC + " C is above the range over which the amine "
          + "correlation is well established (" + CORRELATION_LIMIT_C + " C); treat the result as indicative");
    }
    if (glycolMassFraction > GLYCOL_WARNING_FRACTION) {
      warnings.add("Glycol mass fraction " + glycolMassFraction + " lowers the dielectric constant and raises the "
          + "apparent pKa; this co-solvent effect is not corrected, and a glycol/water sample measured with an "
          + "aqueous-calibrated electrode carries a further offset");
    }
    warnings.add("Ideal-solution buffer shift: activity coefficients are not included, so this is a screening "
        + "estimate; a rigorous electrolyte flash on the same reaction data gives a smaller shift at high "
        + "temperature and the true value is bracketed between the two");

    logger.info("Amine buffered pH: {} at {} C -> {} at {} C (neutral {}), margin {} -> {}, verdict {}", measuredPH,
        measurementTemperatureC, operatingPH, operatingTemperatureC, neutralAtOperating, marginAtMeasurement,
        marginAtOperating, verdict);

    return new AmineBufferedPHResult(amine, measuredPH, measurementTemperatureC, operatingTemperatureC, pKaMeasurement,
        pKaOperating, shift, operatingPH, neutralAtMeasurement, neutralAtOperating, marginAtMeasurement,
        marginAtOperating, verdict, warnings);
  }
}
