package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of an {@link AmineBufferedPH} temperature correction.
 *
 * <p>
 * The headline numbers are {@link #getOperatingPH()} and {@link #getMarginAtOperating()}. The margin is the more useful
 * of the two, because it states how far above neutrality the fluid sits at the temperature where corrosion actually
 * occurs, and neutrality is not pH 7 at operating temperature.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AmineBufferedPHResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final BufferAmine amine;
  private final double measuredPH;
  private final double measurementTemperatureC;
  private final double operatingTemperatureC;
  private final double pKaAtMeasurement;
  private final double pKaAtOperating;
  private final double pHShift;
  private final double operatingPH;
  private final double neutralPHAtMeasurement;
  private final double neutralPHAtOperating;
  private final double marginAtMeasurement;
  private final double marginAtOperating;
  private final AlkalineMarginVerdict verdict;
  private final List<String> warnings;

  /**
   * Construct a buffered-pH temperature-correction result.
   *
   * @param amine the buffering amine
   * @param measuredPH the laboratory pH as measured
   * @param measurementTemperatureC the temperature at which the pH was measured in C
   * @param operatingTemperatureC the operating temperature in C
   * @param pKaAtMeasurement amine pKa at the measurement temperature
   * @param pKaAtOperating amine pKa at the operating temperature
   * @param pHShift the pH shift from measurement to operating temperature, equal to the pKa shift
   * @param operatingPH the in-situ pH at operating temperature
   * @param neutralPHAtMeasurement neutral pH of water at the measurement temperature
   * @param neutralPHAtOperating neutral pH of water at the operating temperature
   * @param marginAtMeasurement alkaline margin above neutrality at the measurement temperature
   * @param marginAtOperating alkaline margin above neutrality at the operating temperature
   * @param verdict the screening verdict on the operating-temperature margin
   * @param warnings list of warnings; may be null
   */
  public AmineBufferedPHResult(BufferAmine amine, double measuredPH, double measurementTemperatureC,
      double operatingTemperatureC, double pKaAtMeasurement, double pKaAtOperating, double pHShift, double operatingPH,
      double neutralPHAtMeasurement, double neutralPHAtOperating, double marginAtMeasurement, double marginAtOperating,
      AlkalineMarginVerdict verdict, List<String> warnings) {
    this.amine = amine;
    this.measuredPH = measuredPH;
    this.measurementTemperatureC = measurementTemperatureC;
    this.operatingTemperatureC = operatingTemperatureC;
    this.pKaAtMeasurement = pKaAtMeasurement;
    this.pKaAtOperating = pKaAtOperating;
    this.pHShift = pHShift;
    this.operatingPH = operatingPH;
    this.neutralPHAtMeasurement = neutralPHAtMeasurement;
    this.neutralPHAtOperating = neutralPHAtOperating;
    this.marginAtMeasurement = marginAtMeasurement;
    this.marginAtOperating = marginAtOperating;
    this.verdict = verdict;
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
   * Gets the operating temperature.
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
   * Gets the amine pKa at the operating temperature.
   *
   * @return pKa, dimensionless
   */
  public double getPKaAtOperating() {
    return pKaAtOperating;
  }

  /**
   * Gets the pH shift from the measurement to the operating temperature, which for a buffered fluid equals the pKa
   * shift.
   *
   * @return pH shift in pH units, normally negative
   */
  public double getPHShift() {
    return pHShift;
  }

  /**
   * Gets the in-situ pH at the operating temperature.
   *
   * @return operating pH
   */
  public double getOperatingPH() {
    return operatingPH;
  }

  /**
   * Gets the neutral pH of water at the measurement temperature.
   *
   * @return neutral pH
   */
  public double getNeutralPHAtMeasurement() {
    return neutralPHAtMeasurement;
  }

  /**
   * Gets the neutral pH of water at the operating temperature.
   *
   * @return neutral pH
   */
  public double getNeutralPHAtOperating() {
    return neutralPHAtOperating;
  }

  /**
   * Gets the alkaline margin above neutrality at the measurement temperature.
   *
   * @return margin in pH units
   */
  public double getMarginAtMeasurement() {
    return marginAtMeasurement;
  }

  /**
   * Gets the alkaline margin above neutrality at the operating temperature. This is the number that matters for
   * protective-film stability.
   *
   * @return margin in pH units
   */
  public double getMarginAtOperating() {
    return marginAtOperating;
  }

  /**
   * Gets the amount of alkaline margin lost between the measurement and operating temperatures.
   *
   * @return margin loss in pH units, positive when margin is lost on heating
   */
  public double getMarginLoss() {
    return marginAtMeasurement - marginAtOperating;
  }

  /**
   * Gets the screening verdict on the operating-temperature margin.
   *
   * @return the verdict
   */
  public AlkalineMarginVerdict getVerdict() {
    return verdict;
  }

  /**
   * Gets the warnings recorded during the calculation.
   *
   * @return an unmodifiable list of warnings; never null
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Serialise this result to pretty-printed JSON.
   *
   * @return a JSON representation of the result
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
  }
}
