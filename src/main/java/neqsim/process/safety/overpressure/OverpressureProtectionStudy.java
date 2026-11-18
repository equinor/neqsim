package neqsim.process.safety.overpressure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.util.fire.ReliefValveSizing;

/**
 * Aggregates the credible overpressure relief scenarios for a protected item, selects the governing (worst-case)
 * scenario, sizes the pressure relief device for that case and checks the accumulated-pressure acceptance limit.
 *
 * <p>
 * This is the TR3001 section 4.6-4.7 relief-scenario engine: each credible cause (blocked outlet, check-valve leakage,
 * gas/liquid blow-by, control-valve failure, tube rupture, fire, etc.) is added as a {@link ReliefScenario}, and the
 * study sizes each credible positive demand, retains the largest required area and reports the maximum mass load
 * separately. The pressure relief device is sized using the applicable phase model using {@link ReliefValveSizing}, and
 * the result is checked against the ASME VIII Div 1 / TR3001 section 2 accumulated-pressure limits.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OverpressureProtectionStudy implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ProtectedItem item;
  private final List<ReliefScenario> scenarios = new ArrayList<ReliefScenario>();
  private boolean multipleDevices = false;
  private final OverpressureAcceptanceChecker acceptanceChecker = new OverpressureAcceptanceChecker();

  /**
   * Creates an overpressure protection study for a protected item.
   *
   * @param item the protected item; not null
   */
  public OverpressureProtectionStudy(ProtectedItem item) {
    this.item = item;
  }

  /**
   * Adds a relief scenario to the study.
   *
   * @param scenario the relief scenario; not null
   * @return this study for chaining
   */
  public OverpressureProtectionStudy addScenario(ReliefScenario scenario) {
    if (scenario != null) {
      scenarios.add(scenario);
    }
    return this;
  }

  /**
   * Sets whether more than one relief device protects the item, which raises the non-fire accumulated-pressure limit
   * from 110% to 116% of MAWP.
   *
   * @param multipleDevices true if multiple relief devices protect the item
   * @return this study for chaining
   */
  public OverpressureProtectionStudy setMultipleDevices(boolean multipleDevices) {
    this.multipleDevices = multipleDevices;
    return this;
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
   * Gets the documented relief scenarios.
   *
   * @return an unmodifiable list of scenarios
   */
  public List<ReliefScenario> getScenarios() {
    return Collections.unmodifiableList(scenarios);
  }

  /**
   * Determines the governing relief scenario as the credible scenario with the largest required relief rate.
   *
   * @return the governing scenario, or null when no credible scenario exists
   */
  public ReliefScenario governingScenario() {
    return OverpressureStudyResult.maximumLoad(scenarios);
  }

  /**
   * Sizes each credible positive relief demand. Equal areas retain declaration order. An unresolved credible demand
   * prevents selection of a study-wide orifice, while its known mass load is retained for disposal screening.
   *
   * @return aggregate result and case-by-case sizing evidence
   */
  public OverpressureStudyResult evaluate() {
    List<OverpressureStudyResult> cases = new ArrayList<OverpressureStudyResult>();
    List<String> warnings = new ArrayList<String>();
    OverpressureStudyResult largest = null;
    boolean complete = true;
    for (ReliefScenario scenario : scenarios) {
      if (!scenario.isCredible()) {
        continue;
      }
      OverpressureStudyResult candidate;
      try {
        candidate = sizeScenario(scenario);
      } catch (IllegalArgumentException failure) {
        candidate = new OverpressureStudyResult(item, scenario, Collections.singletonList(scenario), Double.NaN,
            Double.NaN, null, Double.NaN, Double.NaN, false, null, Collections.singletonList(failure.getMessage()),
            null);
      }
      cases.add(candidate);
      for (String warning : candidate.getWarnings()) {
        warnings.add(scenario.getName() + ": " + warning);
      }
      if (!Double.isFinite(candidate.getRequiredAreaM2()) || candidate.getRequiredAreaM2() <= 0.0) {
        complete = false;
      } else if (largest == null || candidate.getRequiredAreaM2() > largest.getRequiredAreaM2()) {
        largest = candidate;
      }
    }
    if (cases.isEmpty()) {
      warnings.add("No credible positive relief demand defined");
    } else if (!complete) {
      warnings.add("Resolve all credible relief demands before selecting an orifice");
    }
    return new OverpressureStudyResult(item, scenarios, cases, complete ? largest : null, warnings);
  }

  /**
   * Sizes one demand with its own phase and properties on the common protected-item pressure basis.
   *
   * @param governing scenario to size
   * @return sizing and acceptance for this scenario
   */
  private OverpressureStudyResult sizeScenario(ReliefScenario governing) {
    List<String> warnings = new ArrayList<String>();
    List<ReliefScenario> singleCase = Collections.singletonList(governing);
    boolean fireCase = governing.getCause() == ReliefCause.FIRE;
    double overpressureFraction = fireCase ? 0.21 : 0.10;

    double peakPressureBara = ReliefValveSizing.relievingPressurePa(item.getReliefSetPressureBara() * 1e5,
        overpressureFraction) / 1e5;
    if (!positive(item.getMaximumAllowableWorkingPressureBara()) || !positive(item.getReliefSetPressureBara())
        || item.getReliefSetPressureBara() > item.getMaximumAllowableWorkingPressureBara()
        || !Double.isFinite(item.getBackPressureBara()) || item.getBackPressureBara() < 0.0
        || !positive(peakPressureBara) || item.getBackPressureBara() >= peakPressureBara
        || !positive(governing.getReliefRateKgPerS()) || governing.getCause() == null
        || !hasSizingProperties(governing)) {
      warnings.add("Missing or invalid pressure, relief demand, cause or relieving-phase property basis");
      return new OverpressureStudyResult(item, governing, singleCase, Double.NaN, Double.NaN, null, Double.NaN,
          Double.NaN, false, null, warnings, null);
    }

    double setPressurePa = item.getReliefSetPressureBara() * 1.0e5;
    double backPressurePa = item.getBackPressureBara() * 1.0e5;
    ReliefValveSizing.PSVSizingResult sizing = null;
    double requiredAreaM2 = Double.NaN;
    double requiredAreaIn2 = Double.NaN;
    String recommendedOrifice = null;
    double selectedAreaIn2 = Double.NaN;

    if (governing.getPhase() == ReliefPhase.VAPOUR) {
      double temperatureK = governing.getReliefTemperatureK();
      sizing = ReliefValveSizing.calculateRequiredArea(governing.getReliefRateKgPerS(), setPressurePa,
          overpressureFraction, backPressurePa, temperatureK, governing.getMolarMassKgPerMol(),
          governing.getCompressibility(), governing.getSpecificHeatRatio(), false, false);
      requiredAreaM2 = sizing.getRequiredArea();
      requiredAreaIn2 = sizing.getRequiredAreaIn2();
      recommendedOrifice = sizing.getRecommendedOrifice();
      selectedAreaIn2 = sizing.getSelectedAreaIn2();
    } else if (governing.getPhase() == ReliefPhase.LIQUID) {
      double density = positive(governing.getDensityKgPerM3()) ? governing.getDensityKgPerM3()
          : governing.getLiquidDensityKgPerM3();
      double viscosity = governing.getViscosityPaS();
      double volumeFlow = governing.getReliefRateKgPerS() / density;
      ReliefValveSizing.LiquidPSVSizingResult liquidSizing = ReliefValveSizing.calculateLiquidReliefArea(volumeFlow,
          density, setPressurePa, overpressureFraction, backPressurePa, viscosity, false);
      requiredAreaM2 = liquidSizing.getRequiredAreaM2();
      requiredAreaIn2 = liquidSizing.getRequiredAreaIn2();
      recommendedOrifice = liquidSizing.getRecommendedOrifice();
      selectedAreaIn2 = liquidSizing.getSelectedAreaIn2();
    } else {
      // TWO_PHASE governing case sized with the Leung omega method (API 520 Appendix D).
      double temperatureK = governing.getReliefTemperatureK();
      requiredAreaM2 = ReliefValveSizing.calculateTwoPhaseReliefArea(governing.getReliefRateKgPerS(), setPressurePa,
          overpressureFraction, backPressurePa, temperatureK, governing.getGasMassFraction(),
          governing.getGasDensityKgPerM3(), governing.getLiquidDensityKgPerM3(), governing.getLatentHeatJPerKg(),
          governing.getLiquidHeatCapacityJPerKgK());
      requiredAreaIn2 = requiredAreaM2 / 6.4516e-4;
      selectedAreaIn2 = ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2[ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2.length
          - 1];
      recommendedOrifice = ReliefValveSizing.STANDARD_ORIFICE_LETTERS[ReliefValveSizing.STANDARD_ORIFICE_LETTERS.length
          - 1];
      for (int i = 0; i < ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2.length; i++) {
        if (ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2[i] >= requiredAreaIn2) {
          recommendedOrifice = ReliefValveSizing.STANDARD_ORIFICE_LETTERS[i];
          selectedAreaIn2 = ReliefValveSizing.STANDARD_ORIFICE_AREAS_IN2[i];
          break;
        }
      }
    }

    boolean capacityAdequate = positive(requiredAreaIn2) && selectedAreaIn2 >= requiredAreaIn2;
    double selectedCapacity = positive(requiredAreaIn2)
        ? governing.getReliefRateKgPerS() * (selectedAreaIn2 / requiredAreaIn2)
        : Double.NaN;
    if (!capacityAdequate) {
      warnings.add("A single standard orifice cannot be selected for this relief demand");
    }

    AcceptanceResult acceptance = acceptanceChecker.check(peakPressureBara, item, governing.getCause(),
        multipleDevices);
    if (!acceptance.isAccepted()) {
      warnings.add("Relieving pressure exceeds the allowable accumulated pressure: " + acceptance.getBasis());
    }

    return new OverpressureStudyResult(item, governing, singleCase, requiredAreaM2, requiredAreaIn2, recommendedOrifice,
        selectedAreaIn2, selectedCapacity, capacityAdequate, acceptance, warnings, sizing);
  }

  /**
   * Checks phase-specific inputs before invoking a sizing correlation.
   * 
   * @param scenario credible demand
   * @return whether all required properties are in their physical domains
   */
  private static boolean hasSizingProperties(ReliefScenario scenario) {
    if (scenario.getPhase() == ReliefPhase.VAPOUR) {
      return positive(scenario.getReliefTemperatureK()) && positive(scenario.getMolarMassKgPerMol())
          && positive(scenario.getCompressibility()) && Double.isFinite(scenario.getSpecificHeatRatio())
          && scenario.getSpecificHeatRatio() > 1.0;
    }
    if (scenario.getPhase() == ReliefPhase.LIQUID) {
      return (positive(scenario.getDensityKgPerM3()) || positive(scenario.getLiquidDensityKgPerM3()))
          && positive(scenario.getViscosityPaS());
    }
    return scenario.getPhase() == ReliefPhase.TWO_PHASE && positive(scenario.getReliefTemperatureK())
        && scenario.getGasMassFraction() >= 0.0 && scenario.getGasMassFraction() <= 1.0
        && positive(scenario.getGasDensityKgPerM3()) && positive(scenario.getLiquidDensityKgPerM3())
        && positive(scenario.getLatentHeatJPerKg()) && positive(scenario.getLiquidHeatCapacityJPerKgK());
  }

  /**
   * @param value physical input
   * @return whether finite and strictly positive
   */
  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}
