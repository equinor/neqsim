package neqsim.process.safety.selfheating;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of a {@link SelfHeatingInductionSolver} transient run.
 *
 * <p>
 * Reports whether ignition occurred, the induction time if it did, and the retained temperature history for plotting.
 * The induction time is the operationally important number: it sets how long after a spill the hazard persists, and how
 * much time is available for detection and intervention.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SelfHeatingInductionResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SelfHeatingGeometry geometry;
  private final double characteristicDimensionM;
  private final double boundaryTemperatureK;
  private final double initialTemperatureK;
  private final double ignitionRiseK;
  private final boolean ignited;
  private final double inductionTimeS;
  private final double peakTemperatureK;
  private final double peakTemperatureRiseK;
  private final boolean steadyStateReached;
  private final double simulatedTimeS;
  private final List<SelfHeatingTimePoint> history;
  private final List<String> warnings;

  /**
   * Construct a transient self-heating result.
   *
   * @param geometry the body shape used
   * @param characteristicDimensionM characteristic half-dimension in m
   * @param boundaryTemperatureK boundary temperature in K
   * @param initialTemperatureK uniform initial temperature in K
   * @param ignitionRiseK temperature rise above the boundary that defines ignition in K
   * @param ignited true if the ignition criterion was reached
   * @param inductionTimeS induction time in s, NaN if ignition did not occur
   * @param peakTemperatureK peak temperature at the end of the run in K
   * @param peakTemperatureRiseK peak temperature minus boundary temperature in K
   * @param steadyStateReached true if a steady temperature profile was reached
   * @param simulatedTimeS total simulated time in s
   * @param history retained temperature history; may be null
   * @param warnings list of warnings; may be null
   */
  public SelfHeatingInductionResult(SelfHeatingGeometry geometry, double characteristicDimensionM,
      double boundaryTemperatureK, double initialTemperatureK, double ignitionRiseK, boolean ignited,
      double inductionTimeS, double peakTemperatureK, double peakTemperatureRiseK, boolean steadyStateReached,
      double simulatedTimeS, List<SelfHeatingTimePoint> history, List<String> warnings) {
    this.geometry = geometry;
    this.characteristicDimensionM = characteristicDimensionM;
    this.boundaryTemperatureK = boundaryTemperatureK;
    this.initialTemperatureK = initialTemperatureK;
    this.ignitionRiseK = ignitionRiseK;
    this.ignited = ignited;
    this.inductionTimeS = inductionTimeS;
    this.peakTemperatureK = peakTemperatureK;
    this.peakTemperatureRiseK = peakTemperatureRiseK;
    this.steadyStateReached = steadyStateReached;
    this.simulatedTimeS = simulatedTimeS;
    this.history = history != null ? history : new ArrayList<SelfHeatingTimePoint>();
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Gets the body shape used in the run.
   *
   * @return the geometry
   */
  public SelfHeatingGeometry getGeometry() {
    return geometry;
  }

  /**
   * Gets the characteristic half-dimension of the body.
   *
   * @return characteristic dimension in m
   */
  public double getCharacteristicDimensionM() {
    return characteristicDimensionM;
  }

  /**
   * Gets the boundary temperature held at the outer surface.
   *
   * @return boundary temperature in K
   */
  public double getBoundaryTemperatureK() {
    return boundaryTemperatureK;
  }

  /**
   * Gets the uniform initial temperature.
   *
   * @return initial temperature in K
   */
  public double getInitialTemperatureK() {
    return initialTemperatureK;
  }

  /**
   * Gets the temperature rise above the boundary temperature used as the ignition criterion.
   *
   * @return ignition temperature rise in K
   */
  public double getIgnitionRiseK() {
    return ignitionRiseK;
  }

  /**
   * Reports whether the ignition criterion was reached.
   *
   * @return true if the body ignited
   */
  public boolean isIgnited() {
    return ignited;
  }

  /**
   * Gets the induction time to ignition.
   *
   * @return induction time in s, or NaN if ignition did not occur
   */
  public double getInductionTimeS() {
    return inductionTimeS;
  }

  /**
   * Gets the induction time expressed in hours.
   *
   * @return induction time in hours, or NaN if ignition did not occur
   */
  public double getInductionTimeHours() {
    return inductionTimeS / 3600.0;
  }

  /**
   * Gets the peak temperature at the end of the run.
   *
   * @return peak temperature in K
   */
  public double getPeakTemperatureK() {
    return peakTemperatureK;
  }

  /**
   * Gets the peak temperature excess above the boundary temperature.
   *
   * @return peak temperature rise in K
   */
  public double getPeakTemperatureRiseK() {
    return peakTemperatureRiseK;
  }

  /**
   * Reports whether a steady temperature profile was reached, which indicates a subcritical case.
   *
   * @return true if a steady state was reached
   */
  public boolean isSteadyStateReached() {
    return steadyStateReached;
  }

  /**
   * Gets the total simulated time.
   *
   * @return simulated time in s
   */
  public double getSimulatedTimeS() {
    return simulatedTimeS;
  }

  /**
   * Gets the retained temperature history.
   *
   * @return an unmodifiable list of history samples; never null
   */
  public List<SelfHeatingTimePoint> getHistory() {
    return Collections.unmodifiableList(history);
  }

  /**
   * Gets the warnings recorded during the run.
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
