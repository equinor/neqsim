package neqsim.process.safety.overpressure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.process.util.fire.ReliefValveSizing;

/**
 * Result of an {@link OverpressureProtectionStudy} evaluation for one protected item.
 *
 * <p>
 * The result records the governing relief scenario (largest credible relief rate), the full set of documented
 * scenarios, the pressure relief device sizing for the governing case, whether the selected standard orifice has
 * adequate capacity, and the accumulated-pressure acceptance check.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OverpressureStudyResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ProtectedItem item;
  private final ReliefScenario governingScenario;
  private final List<ReliefScenario> scenarios;
  private final double requiredAreaM2;
  private final double requiredAreaIn2;
  private final String recommendedOrifice;
  private final double selectedAreaIn2;
  private final double selectedOrificeCapacityKgPerS;
  private final boolean capacityAdequate;
  private final AcceptanceResult acceptance;
  private final List<String> warnings;
  private final transient ReliefValveSizing.PSVSizingResult sizingResult;

  /**
   * Creates an immutable overpressure study result.
   *
   * @param item the protected item; not null
   * @param governingScenario the governing relief scenario; may be null when no credible scenario exists
   * @param scenarios the full list of documented scenarios; not null
   * @param requiredAreaM2 the required relief orifice area in m^2, or NaN when not sized
   * @param requiredAreaIn2 the required relief orifice area in in^2, or NaN when not sized
   * @param recommendedOrifice the recommended standard orifice letter, or null when not sized
   * @param selectedAreaIn2 the selected standard orifice area in in^2, or NaN when not sized
   * @param selectedOrificeCapacityKgPerS the selected orifice mass-flow capacity in kg/s, or NaN
   * @param capacityAdequate true if the selected orifice capacity meets the governing relief rate
   * @param acceptance the accumulated-pressure acceptance result; may be null when not checked
   * @param warnings the list of warnings raised during evaluation; not null
   * @param sizingResult the full PSV sizing result (transient), or null when not sized
   */
  public OverpressureStudyResult(ProtectedItem item, ReliefScenario governingScenario, List<ReliefScenario> scenarios,
      double requiredAreaM2, double requiredAreaIn2, String recommendedOrifice, double selectedAreaIn2,
      double selectedOrificeCapacityKgPerS, boolean capacityAdequate, AcceptanceResult acceptance,
      List<String> warnings, ReliefValveSizing.PSVSizingResult sizingResult) {
    this.item = item;
    this.governingScenario = governingScenario;
    this.scenarios = Collections.unmodifiableList(new ArrayList<ReliefScenario>(scenarios));
    this.requiredAreaM2 = requiredAreaM2;
    this.requiredAreaIn2 = requiredAreaIn2;
    this.recommendedOrifice = recommendedOrifice;
    this.selectedAreaIn2 = selectedAreaIn2;
    this.selectedOrificeCapacityKgPerS = selectedOrificeCapacityKgPerS;
    this.capacityAdequate = capacityAdequate;
    this.acceptance = acceptance;
    this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    this.sizingResult = sizingResult;
  }

  /**
   * Gets the protected item.
   *
   * @return the protected item
   */
  public ProtectedItem getItem() {
    return item;
  }

  /**
   * Gets the governing (worst-case) relief scenario.
   *
   * @return the governing scenario, or null when no credible scenario exists
   */
  public ReliefScenario getGoverningScenario() {
    return governingScenario;
  }

  /**
   * Gets the full list of documented relief scenarios.
   *
   * @return an unmodifiable list of scenarios
   */
  public List<ReliefScenario> getScenarios() {
    return scenarios;
  }

  /**
   * Gets the required relief orifice area for the governing case.
   *
   * @return the required area in m^2, or NaN when not sized
   */
  public double getRequiredAreaM2() {
    return requiredAreaM2;
  }

  /**
   * Gets the required relief orifice area for the governing case.
   *
   * @return the required area in in^2, or NaN when not sized
   */
  public double getRequiredAreaIn2() {
    return requiredAreaIn2;
  }

  /**
   * Gets the recommended standard orifice letter for the governing case.
   *
   * @return the recommended orifice letter, or null when not sized
   */
  public String getRecommendedOrifice() {
    return recommendedOrifice;
  }

  /**
   * Gets the selected standard orifice area.
   *
   * @return the selected area in in^2, or NaN when not sized
   */
  public double getSelectedAreaIn2() {
    return selectedAreaIn2;
  }

  /**
   * Gets the mass-flow capacity of the selected standard orifice at the relieving conditions.
   *
   * @return the selected orifice capacity in kg/s, or NaN when not sized
   */
  public double getSelectedOrificeCapacityKgPerS() {
    return selectedOrificeCapacityKgPerS;
  }

  /**
   * Indicates whether the selected standard orifice has adequate capacity for the governing case.
   *
   * @return true if the selected orifice capacity meets the governing relief rate
   */
  public boolean isCapacityAdequate() {
    return capacityAdequate;
  }

  /**
   * Gets the accumulated-pressure acceptance result.
   *
   * @return the acceptance result, or null when not checked
   */
  public AcceptanceResult getAcceptance() {
    return acceptance;
  }

  /**
   * Gets the warnings raised during the study evaluation.
   *
   * @return an unmodifiable list of warning strings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Gets the full pressure relief device sizing result for the governing case.
   *
   * @return the PSV sizing result, or null when not sized or after deserialization
   */
  public ReliefValveSizing.PSVSizingResult getSizingResult() {
    return sizingResult;
  }

  /**
   * Serializes this overpressure study result to a human-readable JSON string. The transient PSV sizing result is
   * omitted; NaN fields (when a quantity is not sized) are written as {@code NaN}.
   *
   * @return JSON representation of this result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
