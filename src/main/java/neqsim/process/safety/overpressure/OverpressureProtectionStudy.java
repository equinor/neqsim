package neqsim.process.safety.overpressure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.fire.ReliefValveSizing;

/**
 * Aggregates the credible overpressure relief scenarios for a protected item, selects the governing (worst-case)
 * scenario, sizes the pressure relief device for that case and checks the accumulated-pressure acceptance limit.
 *
 * <p>
 * This is the TR3001 section 4.6-4.7 relief-scenario engine: each credible cause (blocked outlet, check-valve leakage,
 * gas/liquid blow-by, control-valve failure, tube rupture, fire, etc.) is added as a {@link ReliefScenario}, and the
 * study determines the governing case by the largest credible required relief rate (API STD 521 section 4.4). The
 * pressure relief device is sized for the governing vapour case using {@link ReliefValveSizing}, and the result is
 * checked against the ASME VIII Div 1 / TR3001 section 2 accumulated-pressure limits.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OverpressureProtectionStudy implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(OverpressureProtectionStudy.class);

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
    ReliefScenario governing = null;
    for (ReliefScenario scenario : scenarios) {
      if (!scenario.isCredible()) {
        continue;
      }
      if (governing == null || scenario.getReliefRateKgPerS() > governing.getReliefRateKgPerS()) {
        governing = scenario;
      }
    }
    return governing;
  }

  /**
   * Evaluates the study: selects the governing case, sizes the relief device for the governing vapour case and checks
   * the accumulated-pressure acceptance limit.
   *
   * @return the {@link OverpressureStudyResult}
   */
  public OverpressureStudyResult evaluate() {
    List<String> warnings = new ArrayList<String>();
    ReliefScenario governing = governingScenario();

    if (governing == null) {
      warnings.add("No credible relief scenario defined; nothing to size");
      logger.warn("Overpressure study for {} has no credible scenario", item.getName());
      return new OverpressureStudyResult(item, null, scenarios, Double.NaN, Double.NaN, null, Double.NaN, Double.NaN,
          false, null, warnings, null);
    }

    boolean fireCase = governing.getCause() == ReliefCause.FIRE;
    double overpressureFraction = fireCase ? 0.21 : 0.10;

    ReliefValveSizing.PSVSizingResult sizing = null;
    double requiredAreaM2 = Double.NaN;
    double requiredAreaIn2 = Double.NaN;
    String recommendedOrifice = null;
    double selectedAreaIn2 = Double.NaN;
    double selectedCapacity = Double.NaN;
    boolean capacityAdequate = false;

    if (governing.getPhase() == ReliefPhase.VAPOUR) {
      if (governing.getReliefRateKgPerS() <= 0.0) {
        warnings.add("Governing relief rate is zero; relief device cannot be sized");
      } else {
        double setPressurePa = item.getReliefSetPressureBara() * 1.0e5;
        double backPressurePa = item.getBackPressureBara() * 1.0e5;
        double temperatureK = !Double.isNaN(governing.getReliefTemperatureK()) ? governing.getReliefTemperatureK()
            : 288.15;
        sizing = ReliefValveSizing.calculateRequiredArea(governing.getReliefRateKgPerS(), setPressurePa,
            overpressureFraction, backPressurePa, temperatureK, governing.getMolarMassKgPerMol(),
            governing.getCompressibility(), governing.getSpecificHeatRatio(), false, false);
        requiredAreaM2 = sizing.getRequiredArea();
        requiredAreaIn2 = sizing.getRequiredAreaIn2();
        recommendedOrifice = sizing.getRecommendedOrifice();
        selectedAreaIn2 = sizing.getSelectedAreaIn2();
        // The API 520 sizing equation is linear in mass flow, so the capacity of the
        // selected standard orifice scales with its area relative to the required area.
        // This stays self-consistent within the sizing method (unlike re-deriving the
        // capacity from the theoretical isentropic-nozzle equation, which uses a
        // different coefficient basis and is not its inverse).
        selectedCapacity = requiredAreaIn2 > 0.0 ? governing.getReliefRateKgPerS() * (selectedAreaIn2 / requiredAreaIn2)
            : Double.NaN;
        capacityAdequate = selectedAreaIn2 >= requiredAreaIn2;
        if (!capacityAdequate) {
          warnings.add("Required relief area exceeds the largest standard orifice; "
              + "multiple or larger relief devices are required");
        }
        logger.info("Overpressure study for {}: governing case {} ({}), required orifice {} ({} in2)", item.getName(),
            governing.getName(), governing.getCause().getLabel(), recommendedOrifice,
            String.format("%.3f", selectedAreaIn2));
      }
    } else if (governing.getPhase() == ReliefPhase.LIQUID) {
      if (governing.getReliefRateKgPerS() <= 0.0) {
        warnings.add("Governing relief rate is zero; relief device cannot be sized");
      } else {
        double density = !Double.isNaN(governing.getDensityKgPerM3()) && governing.getDensityKgPerM3() > 0.0
            ? governing.getDensityKgPerM3()
            : (!Double.isNaN(governing.getLiquidDensityKgPerM3()) ? governing.getLiquidDensityKgPerM3() : Double.NaN);
        if (Double.isNaN(density) || density <= 0.0) {
          density = 700.0;
          warnings.add("Liquid density not set; using default 700 kg/m3 for the liquid relief sizing");
        }
        double viscosity = !Double.isNaN(governing.getViscosityPaS()) && governing.getViscosityPaS() > 0.0
            ? governing.getViscosityPaS()
            : 5.0e-4;
        double volumeFlow = governing.getReliefRateKgPerS() / density;
        double setPressurePa = item.getReliefSetPressureBara() * 1.0e5;
        double backPressurePa = item.getBackPressureBara() * 1.0e5;
        ReliefValveSizing.LiquidPSVSizingResult liquidSizing = ReliefValveSizing.calculateLiquidReliefArea(volumeFlow,
            density, setPressurePa, overpressureFraction, backPressurePa, viscosity, false);
        requiredAreaM2 = liquidSizing.getRequiredAreaM2();
        requiredAreaIn2 = liquidSizing.getRequiredAreaIn2();
        recommendedOrifice = liquidSizing.getRecommendedOrifice();
        selectedAreaIn2 = liquidSizing.getSelectedAreaIn2();
        selectedCapacity = requiredAreaIn2 > 0.0 ? governing.getReliefRateKgPerS() * (selectedAreaIn2 / requiredAreaIn2)
            : Double.NaN;
        capacityAdequate = selectedAreaIn2 >= requiredAreaIn2;
        if (!capacityAdequate) {
          warnings.add("Required liquid relief area exceeds the largest standard orifice; "
              + "multiple or larger relief devices are required");
        }
        logger.info("Overpressure study for {}: governing liquid case {} ({}), required orifice {} ({} in2)",
            item.getName(), governing.getName(), governing.getCause().getLabel(), recommendedOrifice,
            String.format("%.3f", selectedAreaIn2));
      }
    } else {
      // TWO_PHASE governing case sized with the Leung omega method (API 520 Appendix D).
      if (governing.getReliefRateKgPerS() <= 0.0) {
        warnings.add("Governing relief rate is zero; relief device cannot be sized");
      } else if (Double.isNaN(governing.getGasMassFraction()) || Double.isNaN(governing.getGasDensityKgPerM3())
          || Double.isNaN(governing.getLiquidDensityKgPerM3()) || Double.isNaN(governing.getLatentHeatJPerKg())
          || Double.isNaN(governing.getLiquidHeatCapacityJPerKgK())) {
        warnings.add("Two-phase governing case is missing omega-method inputs "
            + "(gas fraction, gas/liquid density, latent heat, liquid Cp); two-phase sizing skipped");
        logger.warn("Overpressure study for {}: two-phase omega inputs incomplete; sizing skipped", item.getName());
      } else {
        double setPressurePa = item.getReliefSetPressureBara() * 1.0e5;
        double backPressurePa = item.getBackPressureBara() * 1.0e5;
        double temperatureK = !Double.isNaN(governing.getReliefTemperatureK()) ? governing.getReliefTemperatureK()
            : 288.15;
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
        selectedCapacity = requiredAreaIn2 > 0.0 ? governing.getReliefRateKgPerS() * (selectedAreaIn2 / requiredAreaIn2)
            : Double.NaN;
        capacityAdequate = selectedAreaIn2 >= requiredAreaIn2;
        if (!capacityAdequate) {
          warnings.add("Required two-phase relief area exceeds the largest standard orifice; "
              + "multiple or larger relief devices are required");
        }
        logger.info("Overpressure study for {}: governing two-phase case {} ({}), required orifice {} ({} in2)",
            item.getName(), governing.getName(), governing.getCause().getLabel(), recommendedOrifice,
            String.format("%.3f", selectedAreaIn2));
      }
    }

    double peakPressureBara = item.getReliefSetPressureBara() * (1.0 + overpressureFraction);
    AcceptanceResult acceptance = acceptanceChecker.check(peakPressureBara, item, governing.getCause(),
        multipleDevices);
    if (!acceptance.isAccepted()) {
      warnings.add("Relieving pressure exceeds the allowable accumulated pressure: " + acceptance.getBasis());
    }

    return new OverpressureStudyResult(item, governing, scenarios, requiredAreaM2, requiredAreaIn2, recommendedOrifice,
        selectedAreaIn2, selectedCapacity, capacityAdequate, acceptance, warnings, sizing);
  }
}
