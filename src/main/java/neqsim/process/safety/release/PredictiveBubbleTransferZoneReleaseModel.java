package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.release.RanzMarshallMassTransferCorrelation.Morphology;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.process.safety.release.ReleaseFlowResult.Status;

/**
 * Predictive vertical bubbly transfer-zone adapter for component-selective release screening.
 *
 * <p>
 * The adapter starts from the bounded Zuber-Findlay/Harmathy drift-flux solution and predicts the gas-to-liquid
 * relative velocity and bubble residence time from the resolved phase velocities. A conservative spherical-bubble proxy
 * for the Sauter mean diameter is the largest diameter that satisfies the configured transfer-zone geometry, the
 * physical opening, {@code Eo <= 4}, and {@code We <= 3}. The resulting diameter, velocity and residence time drive the
 * existing Ranz-Marshall external-film correlation and component-conservative finite-rate release model. The
 * hydrodynamic and component-transfer calculations are iterated to a checked fixed point.
 *
 * <p>
 * This is an unqualified screening closure for exactly one gas and one supported liquid phase. It does not represent a
 * population balance, breakup/coalescence kinetics, entrainment, annular flow, interfacial heat transfer, internal
 * bubble resistance, Stefan flow, solid-bearing transport or experimental qualification.
 *
 * @author esol
 * @version 1.0
 */
public final class PredictiveBubbleTransferZoneReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final double GRAVITY_MS2 = 9.80665;
  private static final double MAX_EOTVOS_NUMBER = 4.0;
  private static final double MAX_WEBER_NUMBER = 3.0;
  private static final double MAX_BUBBLE_TO_HYDRAULIC_DIAMETER = 0.25;
  private static final int MAX_FIXED_POINT_ITERATIONS = 24;
  private static final double FIXED_POINT_RELATIVE_TOLERANCE = 1.0e-8;

  private final double surfaceTensionNm;
  private final double transferZoneHydraulicDiameterM;
  private final double transferZoneVerticalLengthM;
  private final double continuousPhaseDynamicViscosityPaS;
  private final Map<String, Double> componentDiffusivitiesM2S;
  private final String parameterProvenance;

  /**
   * Creates a predictive vertical bubbly transfer-zone adapter.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   * @param transferZoneHydraulicDiameterM transfer-zone hydraulic diameter in m
   * @param transferZoneVerticalLengthM vertical contact length from release plane in m
   * @param continuousPhaseDynamicViscosityPaS continuous-liquid dynamic viscosity in Pa s
   * @param componentDiffusivitiesM2S continuous-liquid diffusivity in m2/s for every fluid component
   * @param parameterProvenance nonempty source or calibration identity for supplied properties and geometry
   */
  public PredictiveBubbleTransferZoneReleaseModel(double surfaceTensionNm, double transferZoneHydraulicDiameterM,
      double transferZoneVerticalLengthM, double continuousPhaseDynamicViscosityPaS,
      Map<String, Double> componentDiffusivitiesM2S, String parameterProvenance) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    this.transferZoneHydraulicDiameterM = ReleaseFlowRequest.positive(transferZoneHydraulicDiameterM,
        "transferZoneHydraulicDiameterM");
    this.transferZoneVerticalLengthM = ReleaseFlowRequest.positive(transferZoneVerticalLengthM,
        "transferZoneVerticalLengthM");
    this.continuousPhaseDynamicViscosityPaS = ReleaseFlowRequest.positive(continuousPhaseDynamicViscosityPaS,
        "continuousPhaseDynamicViscosityPaS");
    if (componentDiffusivitiesM2S == null || componentDiffusivitiesM2S.isEmpty()) {
      throw new IllegalArgumentException("Component diffusivities required");
    }
    Map<String, Double> diffusivities = new TreeMap<String, Double>();
    for (Map.Entry<String, Double> entry : componentDiffusivitiesM2S.entrySet()) {
      if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
        throw new IllegalArgumentException("Component name required");
      }
      diffusivities.put(entry.getKey(), ReleaseFlowRequest.positive(entry.getValue(), "component diffusivity"));
    }
    this.componentDiffusivitiesM2S = Collections.unmodifiableMap(diffusivities);
    if (parameterProvenance == null || parameterProvenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Parameter provenance required");
    }
    this.parameterProvenance = parameterProvenance;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "predictive-bubble-transfer-zone-vertical-drift-flux-orifice";
  }

  /** {@inheritDoc} */
  @Override
  public String getModelVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseModelEvidence getEvidence() {
    return new ReleaseModelEvidence(getModelId() + ":" + getModelVersion(),
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "GAS_BUBBLE_DISPERSION", "PREDICTIVE_BUBBLE_DIAMETER",
            "PREDICTIVE_PHASE_VELOCITY", "PREDICTIVE_RESIDENCE_TIME", "RANZ_MARSHALL_EXTERNAL_FILM_TRANSFER",
            "COMPONENT_SELECTIVE_PHASE_RELAXATION", "VERTICAL_UPWARD_DRIFT_FLUX", "EXPLICIT_SI_TRANSFER_ZONE_GEOMETRY"),
        Arrays.asList("SPHERICAL_BUBBLE_SCREENING_ONLY", "MAX_STABLE_DIAMETER_USED_AS_D32", "EOTVOS_AT_MOST_4",
            "WEBER_AT_MOST_3", "BUBBLE_DIAMETER_AT_MOST_ONE_QUARTER_HYDRAULIC_DIAMETER", "REYNOLDS_BELOW_200",
            "SCHMIDT_BELOW_250", "EXTERNAL_FILM_ONLY", "NO_POPULATION_BALANCE", "NO_BREAKUP_OR_COALESCENCE_KINETICS",
            "NO_ENTRAINMENT_OR_ANNULAR_FLOW", "NO_INTERFACIAL_HEAT_TRANSFER", "NO_INTERNAL_DISPERSED_PHASE_RESISTANCE",
            "NO_STEFAN_FLOW_CORRECTION", "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("clift-grace-weber-1978", ReleaseModelEvidence.Type.ANALYTICAL,
                "Clift, Grace and Weber, Bubbles, Drops, and Particles (1978)",
                "Published Eotvos/Weber spherical-bubble regime basis; not release-rate validation", true),
            new ReleaseModelEvidence.Record("ranz-marshall-1952", ReleaseModelEvidence.Type.ANALYTICAL,
                "Ranz, W.E. and Marshall, W.R., Chemical Engineering Progress 48 (1952) 141-146, 173-180",
                "Independent published external-film correlation basis; not release-rate validation", true),
            new ReleaseModelEvidence.Record("predictive-transfer-zone-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/test/java/neqsim/process/safety/release/PredictiveBubbleTransferZoneReleaseModelTest.java",
                "Diameter bounds, fixed-point convergence, component/energy closure and nearby-case trends", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Request required");
    }
    if (!componentDiffusivitiesM2S.keySet()
        .equals(new java.util.TreeSet<String>(Arrays.asList(request.getFluid().getComponentNames())))) {
      return ReleaseFlowResult.failure(this, false, "TRANSFER_ZONE_COMPONENT_BASIS_INVALID",
          "Diffusivity components must exactly match the release-fluid components");
    }
    ReleaseFlowResult current = new DriftFluxHomogeneousEquilibriumReleaseModel(surfaceTensionNm).calculate(request);
    if (!current.isUsable()) {
      return wrappedFailure(current, "TRANSFER_ZONE_BASE_");
    }
    if (current.getMassFlowRateKgS() == 0.0) {
      List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
      diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
          "Predictive vertical spherical-bubble transfer zone; no forward flow, so bubble transfer is not applied"));
      diagnostics.add(new Diagnostic("TRANSFER_ZONE_NOT_APPLIED", "No forward release flow"));
      return ReleaseFlowResult.success(this, 0.0, false, current.getStations(), diagnostics, null, false);
    }

    Prediction previousPrediction = null;
    double previousRateKgS = Double.NaN;
    for (int iteration = 1; iteration <= MAX_FIXED_POINT_ITERATIONS; iteration++) {
      try {
        Prediction prediction = predictHydrodynamics(current.getStations().get(Station.THROAT_CRITICAL), request);
        RanzMarshallMassTransferCorrelation correlation = new RanzMarshallMassTransferCorrelation(
            Morphology.GAS_BUBBLES, prediction.sauterMeanDiameterM, prediction.liquidDensityKgM3,
            continuousPhaseDynamicViscosityPaS, prediction.relativeVelocityMs, componentDiffusivitiesM2S,
            parameterProvenance);
        RanzMarshallFiniteRateReleaseModel delegate = new RanzMarshallFiniteRateReleaseModel(surfaceTensionNm,
            prediction.residenceTimeS, correlation);
        ReleaseFlowResult next = delegate.calculate(request);
        if (!next.isUsable()) {
          return wrappedFailure(next, "TRANSFER_ZONE_");
        }
        if (previousPrediction != null && converged(previousPrediction, prediction)
            && relativeDifference(previousRateKgS, next.getMassFlowRateKgS()) <= FIXED_POINT_RELATIVE_TOLERANCE) {
          return wrappedSuccess(next, prediction, correlation, iteration);
        }
        previousPrediction = prediction;
        previousRateKgS = next.getMassFlowRateKgS();
        current = next;
      } catch (UnsupportedOperationException ex) {
        return ReleaseFlowResult.failure(this, true, "TRANSFER_ZONE_REGIME_UNSUPPORTED", ex.getMessage());
      } catch (RuntimeException ex) {
        return ReleaseFlowResult.failure(this, false, "TRANSFER_ZONE_CLOSURE_FAILED", ex.getMessage());
      }
    }
    return ReleaseFlowResult.failure(this, false, "TRANSFER_ZONE_NOT_CONVERGED",
        "Bubble diameter, phase velocity, residence time and release rate did not converge within "
            + MAX_FIXED_POINT_ITERATIONS + " fixed-point iterations");
  }

  /**
   * Predicts bounded bubble geometry and contact time from one resolved drift-flux state.
   *
   * @param state throat state carrying phase velocities
   * @param request release geometry
   * @return immutable hydrodynamic prediction
   */
  Prediction predictHydrodynamics(ReleaseState state, ReleaseFlowRequest request) {
    if (state == null || request == null || state.getPhaseMassFractions().size() != 2
        || !state.getPhaseMassFractions().containsKey("GAS") || state.getPhaseVelocitiesMs().size() != 2) {
      throw new UnsupportedOperationException("Resolved one-gas/one-liquid phase velocities are required");
    }
    String liquid = liquidPhase(state);
    double gasDensity = ReleaseFlowRequest.positive(state.getPhaseDensitiesKgM3().get("GAS"), "gas density");
    double liquidDensity = ReleaseFlowRequest.positive(state.getPhaseDensitiesKgM3().get(liquid), "liquid density");
    if (liquidDensity <= gasDensity) {
      throw new UnsupportedOperationException("Transfer-zone closure requires liquid density greater than gas density");
    }
    double gasVelocity = ReleaseFlowRequest.positive(state.getPhaseVelocitiesMs().get("GAS"), "gas phase velocity");
    double liquidVelocity = ReleaseFlowRequest.positive(state.getPhaseVelocitiesMs().get(liquid),
        "liquid phase velocity");
    double relativeVelocity = Math.abs(gasVelocity - liquidVelocity);
    double eotvosDiameter = Math
        .sqrt(MAX_EOTVOS_NUMBER * surfaceTensionNm / (GRAVITY_MS2 * (liquidDensity - gasDensity)));
    double weberDiameter = relativeVelocity == 0.0 ? Double.POSITIVE_INFINITY
        : MAX_WEBER_NUMBER * surfaceTensionNm / (liquidDensity * relativeVelocity * relativeVelocity);
    double hydraulicDiameter = MAX_BUBBLE_TO_HYDRAULIC_DIAMETER * transferZoneHydraulicDiameterM;
    double diameter = Math.min(Math.min(eotvosDiameter, weberDiameter),
        Math.min(hydraulicDiameter, request.getDiameterM()));
    diameter = ReleaseFlowRequest.positive(diameter, "predicted bubble diameter");
    double residenceTime = ReleaseFlowRequest.positive(transferZoneVerticalLengthM / gasVelocity,
        "predicted bubble residence time");
    double eotvos = GRAVITY_MS2 * (liquidDensity - gasDensity) * diameter * diameter / surfaceTensionNm;
    double weber = liquidDensity * relativeVelocity * relativeVelocity * diameter / surfaceTensionNm;
    String limiter = limiter(diameter, eotvosDiameter, weberDiameter, hydraulicDiameter, request.getDiameterM());
    return new Prediction(liquid, gasDensity, liquidDensity, gasVelocity, liquidVelocity, relativeVelocity, diameter,
        residenceTime, eotvos, weber, limiter);
  }

  /**
   * Returns the supported liquid phase name.
   *
   * @param state resolved one-gas/one-liquid state
   * @return liquid phase name
   */
  private String liquidPhase(ReleaseState state) {
    for (String phase : state.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported transfer-zone liquid phase: " + phase);
        }
        return phase;
      }
    }
    throw new UnsupportedOperationException("Transfer-zone liquid phase is required");
  }

  /**
   * Identifies the active conservative diameter limit.
   *
   * @param diameter selected diameter in m
   * @param eotvosDiameter Eotvos-limited diameter in m
   * @param weberDiameter Weber-limited diameter in m
   * @param hydraulicDiameter geometry-limited diameter in m
   * @param openingDiameter opening diameter in m
   * @return stable limiter code
   */
  private String limiter(double diameter, double eotvosDiameter, double weberDiameter, double hydraulicDiameter,
      double openingDiameter) {
    if (diameter == eotvosDiameter) {
      return "EOTVOS";
    }
    if (diameter == weberDiameter) {
      return "WEBER";
    }
    if (diameter == hydraulicDiameter) {
      return "HYDRAULIC_DIAMETER";
    }
    if (diameter == openingDiameter) {
      return "OPENING_DIAMETER";
    }
    throw new IllegalStateException("Predicted bubble diameter has no active limiter");
  }

  /**
   * Checks fixed-point convergence of all predicted transfer-zone quantities.
   *
   * @param previous previous hydrodynamic prediction
   * @param current current hydrodynamic prediction
   * @return true when diameter, phase velocities, slip and residence time meet tolerance
   */
  private boolean converged(Prediction previous, Prediction current) {
    return relativeDifference(previous.sauterMeanDiameterM,
        current.sauterMeanDiameterM) <= FIXED_POINT_RELATIVE_TOLERANCE
        && relativeDifference(previous.gasVelocityMs, current.gasVelocityMs) <= FIXED_POINT_RELATIVE_TOLERANCE
        && relativeDifference(previous.liquidVelocityMs, current.liquidVelocityMs) <= FIXED_POINT_RELATIVE_TOLERANCE
        && relativeDifference(previous.relativeVelocityMs, current.relativeVelocityMs) <= FIXED_POINT_RELATIVE_TOLERANCE
        && relativeDifference(previous.residenceTimeS, current.residenceTimeS) <= FIXED_POINT_RELATIVE_TOLERANCE;
  }

  /**
   * Computes a scale-safe relative difference.
   *
   * @param first first finite value
   * @param second second finite value
   * @return nonnegative relative difference
   */
  private double relativeDifference(double first, double second) {
    if (!Double.isFinite(first) || !Double.isFinite(second)) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.abs(first - second) / Math.max(1.0e-15, Math.max(Math.abs(first), Math.abs(second)));
  }

  /**
   * Rebinds a successful delegated calculation to this model identity and evidence manifest.
   *
   * @param result converged delegated result
   * @param prediction final hydrodynamic prediction
   * @param correlation final Ranz-Marshall correlation
   * @param iterations completed fixed-point iterations
   * @return immutable successful result
   */
  private ReleaseFlowResult wrappedSuccess(ReleaseFlowResult result, Prediction prediction,
      RanzMarshallMassTransferCorrelation correlation, int iterations) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Predictive vertical spherical-bubble transfer zone with bounded Eotvos/Weber/geometry diameter, "
            + "resolved drift-flux phase velocities, gas residence time and Ranz-Marshall external-film transfer; "
            + "no population balance, entrainment, annular flow, interfacial heat transfer or solids"));
    diagnostics.add(new Diagnostic("PREDICTIVE_BUBBLE_TRANSFER_ZONE",
        "liquidPhase=" + prediction.liquidPhase + "; d32=" + prediction.sauterMeanDiameterM + " m; diameterLimiter="
            + prediction.diameterLimiter + "; gasVelocity=" + prediction.gasVelocityMs + " m/s; liquidVelocity="
            + prediction.liquidVelocityMs + " m/s; relativeVelocity=" + prediction.relativeVelocityMs
            + " m/s; residenceTime=" + prediction.residenceTimeS + " s; Eotvos=" + prediction.eotvosNumber + "; Weber="
            + prediction.weberNumber + "; hydraulicDiameter=" + transferZoneHydraulicDiameterM + " m; verticalLength="
            + transferZoneVerticalLengthM + " m; fixedPointIterations=" + iterations + "; tolerance="
            + FIXED_POINT_RELATIVE_TOLERANCE + "; provenance=" + parameterProvenance));
    diagnostics.add(new Diagnostic("TRANSFER_ZONE_RANZ_MARSHALL_RANGE",
        "Reynolds=" + correlation.getReynoldsNumber() + "; Schmidt=" + correlation.getSchmidtNumbers() + "; Sherwood="
            + correlation.getSherwoodNumbers() + "; relaxationTimes=" + correlation.getComponentRelaxationTimesS()
            + " s"));
    for (Diagnostic diagnostic : result.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode())
          && !"RANZ_MARSHALL_COMPONENT_TRANSFER".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return ReleaseFlowResult.success(this, result.getMassFlowRateKgS(), result.isChoked(), result.getStations(),
        diagnostics, result.getThroatSoundSpeedMs(), result.getStatus() == Status.VALID_WITH_WARNINGS);
  }

  /**
   * Rebinds a delegated failure to this model identity.
   *
   * @param result delegated unusable result
   * @param prefix stable diagnostic prefix
   * @return immutable failure result
   */
  private ReleaseFlowResult wrappedFailure(ReleaseFlowResult result, String prefix) {
    Diagnostic cause = result.getDiagnostics().get(0);
    return ReleaseFlowResult.failure(this, result.getStatus() == Status.UNSUPPORTED, prefix + cause.getCode(),
        cause.getMessage());
  }

  /** @return caller-declared gas/liquid interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return transfer-zone hydraulic diameter in m */
  public double getTransferZoneHydraulicDiameterM() {
    return transferZoneHydraulicDiameterM;
  }

  /** @return transfer-zone vertical contact length in m */
  public double getTransferZoneVerticalLengthM() {
    return transferZoneVerticalLengthM;
  }

  /** @return caller-declared continuous-liquid dynamic viscosity in Pa s */
  public double getContinuousPhaseDynamicViscosityPaS() {
    return continuousPhaseDynamicViscosityPaS;
  }

  /** @return immutable component-name-sorted continuous-liquid diffusivities in m2/s */
  public Map<String, Double> getComponentDiffusivitiesM2S() {
    return Collections.unmodifiableMap(new TreeMap<String, Double>(componentDiffusivitiesM2S));
  }

  /** @return caller-declared parameter and geometry provenance */
  public String getParameterProvenance() {
    return parameterProvenance;
  }

  /** @return maximum accepted Eotvos number for the spherical-bubble proxy */
  public double getMaximumEotvosNumber() {
    return MAX_EOTVOS_NUMBER;
  }

  /** @return maximum accepted Weber number for the spherical-bubble proxy */
  public double getMaximumWeberNumber() {
    return MAX_WEBER_NUMBER;
  }

  /** @return maximum bubble-to-transfer-zone hydraulic-diameter ratio */
  public double getMaximumBubbleToHydraulicDiameter() {
    return MAX_BUBBLE_TO_HYDRAULIC_DIAMETER;
  }

  /** @return fixed-point relative tolerance */
  public double getFixedPointRelativeTolerance() {
    return FIXED_POINT_RELATIVE_TOLERANCE;
  }

  /** Immutable predicted transfer-zone hydrodynamics. */
  static final class Prediction {
    final String liquidPhase;
    final double gasDensityKgM3;
    final double liquidDensityKgM3;
    final double gasVelocityMs;
    final double liquidVelocityMs;
    final double relativeVelocityMs;
    final double sauterMeanDiameterM;
    final double residenceTimeS;
    final double eotvosNumber;
    final double weberNumber;
    final String diameterLimiter;

    /**
     * Creates one checked transfer-zone prediction.
     *
     * @param liquidPhase native liquid phase name
     * @param gasDensityKgM3 gas density in kg/m3
     * @param liquidDensityKgM3 liquid density in kg/m3
     * @param gasVelocityMs gas phase velocity in m/s
     * @param liquidVelocityMs liquid phase velocity in m/s
     * @param relativeVelocityMs gas-to-liquid relative speed in m/s
     * @param sauterMeanDiameterM conservative d32 proxy in m
     * @param residenceTimeS vertical bubble residence time in s
     * @param eotvosNumber Eotvos number at the selected diameter
     * @param weberNumber Weber number at the selected diameter
     * @param diameterLimiter stable active-limit code
     */
    private Prediction(String liquidPhase, double gasDensityKgM3, double liquidDensityKgM3, double gasVelocityMs,
        double liquidVelocityMs, double relativeVelocityMs, double sauterMeanDiameterM, double residenceTimeS,
        double eotvosNumber, double weberNumber, String diameterLimiter) {
      this.liquidPhase = liquidPhase;
      this.gasDensityKgM3 = gasDensityKgM3;
      this.liquidDensityKgM3 = liquidDensityKgM3;
      this.gasVelocityMs = gasVelocityMs;
      this.liquidVelocityMs = liquidVelocityMs;
      this.relativeVelocityMs = relativeVelocityMs;
      this.sauterMeanDiameterM = sauterMeanDiameterM;
      this.residenceTimeS = residenceTimeS;
      this.eotvosNumber = eotvosNumber;
      this.weberNumber = weberNumber;
      this.diameterLimiter = diameterLimiter;
    }
  }
}
