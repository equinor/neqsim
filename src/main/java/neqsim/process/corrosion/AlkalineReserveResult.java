package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Buffer capacity of an amine-buffered aqueous fluid, expressed as how much of the usable alkaline reserve has already
 * been consumed and how much acid the fluid can still absorb.
 *
 * <p>
 * {@link AmineBufferedPHResult} answers "how alkaline is the fluid now". This answers the question an operator actually
 * acts on: "how much is left". A closed heating- or cooling-medium loop loses alkalinity continuously to organic acids
 * from glycol oxidation, to dissolved CO<sub>2</sub>, and to the corrosion reaction itself, so a margin that looks
 * small may be the remnant of a buffer that is nearly exhausted, or it may sit on a large untouched reserve. The two
 * cases call for different actions and the margin alone cannot distinguish them.
 * </p>
 *
 * <p>
 * The reserve is measured along the titration curve between two end points:
 * </p>
 *
 * <ul>
 * <li><b>Fresh</b> - all amine present as free base, {@code f = 1}.</li>
 * <li><b>Zero margin</b> - the laboratory pH at which the alkaline margin at the operating temperature reaches zero,
 * that is, the fluid is no longer alkaline where the corrosion happens.</li>
 * </ul>
 *
 * <p>
 * With {@code f} the free-base fraction from the Henderson-Hasselbalch relation, the acid consumed is proportional to
 * {@code (1 - f)}, so the <b>amine concentration cancels</b> out of both the spent fraction and the remaining capacity
 * expressed in mg/L. Neither needs a dosing record, which is what makes this usable on a fluid sample alone.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see AmineBufferedPH#calculateAlkalineReserve(double, double)
 */
public class AlkalineReserveResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final BufferAmine amine;
  private final double measuredPH;
  private final double measurementTemperatureC;
  private final double operatingTemperatureC;
  private final double pKaAtMeasurement;
  private final double measuredPHAtZeroMargin;
  private final double freeBaseFractionAsFound;
  private final double freeBaseFractionAtZeroMargin;
  private final double reserveSpentFraction;
  private final double measuredAcidMgPerL;
  private final double acidMolarMassGPerMol;
  private final double remainingAcidCapacityMgPerL;
  private final double remainingAcidCapacityMmolPerL;
  private final double derivedAmineInventoryMmolPerL;
  private final List<String> warnings;

  /**
   * Construct an alkaline-reserve result.
   *
   * @param amine the buffering amine
   * @param measuredPH the laboratory pH as measured
   * @param measurementTemperatureC the temperature at which the pH was measured in C
   * @param operatingTemperatureC the operating temperature in C
   * @param pKaAtMeasurement amine pKa at the measurement temperature
   * @param measuredPHAtZeroMargin laboratory pH at which the operating-temperature margin reaches zero
   * @param freeBaseFractionAsFound free-base fraction of the amine at the measured pH, between 0 and 1
   * @param freeBaseFractionAtZeroMargin free-base fraction at the zero-margin end point, between 0 and 1
   * @param reserveSpentFraction fraction of the usable reserve already consumed, between 0 and 1
   * @param measuredAcidMgPerL measured total acid load in mg/L, or NaN if not supplied
   * @param acidMolarMassGPerMol molar mass used for the acid load in g/mol, or NaN if not supplied
   * @param remainingAcidCapacityMgPerL further acid the fluid can absorb in mg/L, or NaN if no acid load was supplied
   * @param remainingAcidCapacityMmolPerL further acid the fluid can absorb in mmol/L, or NaN
   * @param derivedAmineInventoryMmolPerL amine inventory implied by the acid load and the free-base fraction, or NaN
   * @param warnings list of warnings; may be null
   */
  public AlkalineReserveResult(BufferAmine amine, double measuredPH, double measurementTemperatureC,
      double operatingTemperatureC, double pKaAtMeasurement, double measuredPHAtZeroMargin,
      double freeBaseFractionAsFound, double freeBaseFractionAtZeroMargin, double reserveSpentFraction,
      double measuredAcidMgPerL, double acidMolarMassGPerMol, double remainingAcidCapacityMgPerL,
      double remainingAcidCapacityMmolPerL, double derivedAmineInventoryMmolPerL, List<String> warnings) {
    this.amine = amine;
    this.measuredPH = measuredPH;
    this.measurementTemperatureC = measurementTemperatureC;
    this.operatingTemperatureC = operatingTemperatureC;
    this.pKaAtMeasurement = pKaAtMeasurement;
    this.measuredPHAtZeroMargin = measuredPHAtZeroMargin;
    this.freeBaseFractionAsFound = freeBaseFractionAsFound;
    this.freeBaseFractionAtZeroMargin = freeBaseFractionAtZeroMargin;
    this.reserveSpentFraction = reserveSpentFraction;
    this.measuredAcidMgPerL = measuredAcidMgPerL;
    this.acidMolarMassGPerMol = acidMolarMassGPerMol;
    this.remainingAcidCapacityMgPerL = remainingAcidCapacityMgPerL;
    this.remainingAcidCapacityMmolPerL = remainingAcidCapacityMmolPerL;
    this.derivedAmineInventoryMmolPerL = derivedAmineInventoryMmolPerL;
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the buffering amine.
   *
   * @return the amine
   */
  public BufferAmine getAmine() {
    return amine;
  }

  /**
   * Gets the laboratory pH as measured.
   *
   * @return the measured pH
   */
  public double getMeasuredPH() {
    return measuredPH;
  }

  /**
   * Gets the temperature at which the pH was measured.
   *
   * @return measurement temperature in C
   */
  public double getMeasurementTemperatureC() {
    return measurementTemperatureC;
  }

  /**
   * Gets the operating temperature the reserve is evaluated against.
   *
   * @return operating temperature in C
   */
  public double getOperatingTemperatureC() {
    return operatingTemperatureC;
  }

  /**
   * Gets the amine pKa at the measurement temperature.
   *
   * @return pKa, dimensionless
   */
  public double getPKaAtMeasurement() {
    return pKaAtMeasurement;
  }

  /**
   * Gets the laboratory pH at which the alkaline margin at the operating temperature reaches zero. This is the end
   * point of the titration and therefore the practical control floor, which is normally well above the pH at which the
   * fluid would be called acidic on a laboratory reading.
   *
   * @return the laboratory pH at zero operating-temperature margin
   */
  public double getMeasuredPHAtZeroMargin() {
    return measuredPHAtZeroMargin;
  }

  /**
   * Gets the fraction of the amine still present as free base at the measured pH.
   *
   * @return free-base fraction, between 0 and 1
   */
  public double getFreeBaseFractionAsFound() {
    return freeBaseFractionAsFound;
  }

  /**
   * Gets the fraction of the amine present as free base at the zero-margin end point.
   *
   * @return free-base fraction, between 0 and 1
   */
  public double getFreeBaseFractionAtZeroMargin() {
    return freeBaseFractionAtZeroMargin;
  }

  /**
   * Gets the fraction of the usable alkaline reserve that has already been consumed. Independent of the amine
   * concentration, so it needs no dosing record.
   *
   * @return spent fraction, between 0 and 1
   */
  public double getReserveSpentFraction() {
    return reserveSpentFraction;
  }

  /**
   * Gets the fraction of the usable alkaline reserve that remains.
   *
   * @return remaining fraction, between 0 and 1
   */
  public double getReserveRemainingFraction() {
    return 1.0 - reserveSpentFraction;
  }

  /**
   * Gets the measured total acid load used to scale the remaining capacity.
   *
   * @return acid load in mg/L, or NaN if none was supplied
   */
  public double getMeasuredAcidMgPerL() {
    return measuredAcidMgPerL;
  }

  /**
   * Gets the acid molar mass used to convert the acid load to moles.
   *
   * @return molar mass in g/mol, or NaN if no acid load was supplied
   */
  public double getAcidMolarMassGPerMol() {
    return acidMolarMassGPerMol;
  }

  /**
   * Gets the further acid load the fluid can absorb before the operating-temperature margin reaches zero.
   *
   * @return remaining capacity in mg/L, or NaN if no acid load was supplied
   */
  public double getRemainingAcidCapacityMgPerL() {
    return remainingAcidCapacityMgPerL;
  }

  /**
   * Gets the further acid load the fluid can absorb, on a molar basis. Dissolved CO<sub>2</sub> titrates the same
   * buffer mole for mole at these pH values, so this number also prices a CO<sub>2</sub> ingress.
   *
   * @return remaining capacity in mmol/L, or NaN if no acid load was supplied
   */
  public double getRemainingAcidCapacityMmolPerL() {
    return remainingAcidCapacityMmolPerL;
  }

  /**
   * Gets the amine inventory implied by the measured acid load and the free-base fraction. This is a derived value, not
   * a measurement; compare it against the inhibitor dosing record as a consistency check.
   *
   * @return implied amine inventory in mmol/L, or NaN if no acid load was supplied
   */
  public double getDerivedAmineInventoryMmolPerL() {
    return derivedAmineInventoryMmolPerL;
  }

  /**
   * Gets the warnings raised while computing the reserve.
   *
   * @return an unmodifiable list of warnings
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Serialise the result to JSON.
   *
   * @return a JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
  }
}
