package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;

/**
 * Vertical-upward gas/liquid short-opening model with a predictive drift-flux closure.
 *
 * <p>
 * The homogeneous-equilibrium model resolves the isentropic thermodynamic station and available specific kinetic
 * energy. This model closes hydrodynamic slip with the Zuber-Findlay relation {@code uGas = C0 * j + Vgj}, using
 * {@code C0 = 1.2} and the Harmathy bubble-rise velocity evaluated with a caller-declared positive interfacial tension.
 * The coupled phase-area, drift-flux and kinetic-energy equations are solved for one gas and one liquid phase. The
 * closure is limited to vertical-upward bubbly/dispersed screening and remains unqualified.
 */
public final class DriftFluxHomogeneousEquilibriumReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final double DISTRIBUTION_PARAMETER = 1.2;
  private static final double HARMATHY_COEFFICIENT = 1.53;
  private static final double GRAVITY_MS2 = 9.80665;
  private static final double MAX_GAS_AREA_FRACTION = 0.80;
  private static final double MAX_SLIP_RATIO = 128.0;
  private final HomogeneousEquilibriumReleaseModel equilibriumModel = new HomogeneousEquilibriumReleaseModel();
  private final double surfaceTensionNm;

  /**
   * Creates a bounded vertical drift-flux model.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   */
  public DriftFluxHomogeneousEquilibriumReleaseModel(double surfaceTensionNm) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "equilibrium-thermodynamics-vertical-drift-flux-orifice";
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
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "EQUILIBRIUM_PHASE_TRANSFER",
            "VERTICAL_UPWARD_DRIFT_FLUX", "BUBBLY_DISPERSED_SCREENING", "CALLER_DECLARED_SURFACE_TENSION"),
        Arrays.asList("NO_FINITE_RATE_PHASE_TRANSFER", "NO_ENTRAINMENT_TRANSPORT", "NO_DROPLET_SIZE_TRANSPORT",
            "NO_ANNULAR_JET_MODEL", "NO_SOLID_BEARING_FLOW", "EXPLICIT_SURFACE_TENSION_REQUIRED",
            "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(new ReleaseModelEvidence.Record("zuber-findlay-1965", ReleaseModelEvidence.Type.ANALYTICAL,
            "doi:10.1115/1.3689137", "Published drift-flux relation used as model basis; not rate validation", false),
            new ReleaseModelEvidence.Record("harmathy-1960", ReleaseModelEvidence.Type.ANALYTICAL,
                "doi:10.1002/aic.690060222", "Published bubble-rise relation used as model basis; not validation",
                false),
            new ReleaseModelEvidence.Record("vertical-drift-flux-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/test/java/neqsim/process/safety/release/DriftFluxHomogeneousEquilibriumReleaseModelTest.java",
                "Phase-area, kinetic-energy and drift-flux closure with nearby-case coverage", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Request required");
    }
    ReleaseFlowResult equilibrium = equilibriumModel.calculate(request);
    if (!equilibrium.isUsable()) {
      Diagnostic cause = equilibrium.getDiagnostics().get(0);
      return ReleaseFlowResult.failure(this, equilibrium.getStatus() == ReleaseFlowResult.Status.UNSUPPORTED,
          "EQUILIBRIUM_BASE_" + cause.getCode(), cause.getMessage());
    }
    List<Diagnostic> diagnostics = diagnostics(equilibrium);
    if (equilibrium.getMassFlowRateKgS() == 0.0) {
      diagnostics.add(new Diagnostic("DRIFT_FLUX_NOT_APPLIED", "No forward flow; phase velocities are not resolved"));
      return ReleaseFlowResult.success(this, 0.0, false, equilibrium.getStations(), diagnostics, null, false);
    }
    try {
      EnumMap<Station, ReleaseState> states = new EnumMap<Station, ReleaseState>(Station.class);
      states.putAll(equilibrium.getStations());
      Hydrodynamics throat = partition(equilibrium.getStations().get(Station.THROAT_CRITICAL));
      states.put(Station.THROAT_CRITICAL, throat.state);
      states.put(Station.ORIFICE_EXIT, throat.state);
      ReleaseState ambient = equilibrium.getStations().get(Station.AMBIENT_EXPANDED);
      if (ambient != null && ambient.getPhaseMassFractions().size() == 2
          && ambient.getPhaseMassFractions().containsKey("GAS")) {
        states.put(Station.AMBIENT_EXPANDED, partition(ambient).state);
      }
      diagnostics.add(new Diagnostic("PREDICTIVE_DRIFT_FLUX", "Zuber-Findlay C0=" + DISTRIBUTION_PARAMETER
          + "; Harmathy drift velocity=" + throat.driftVelocityMs + " m/s; solved uGas/uLiquid=" + throat.slipRatio));
      diagnostics.add(new Diagnostic("DRIFT_FLUX_CLOSURE",
          "surfaceTension=" + throat.surfaceTensionNm + " N/m, gasAreaFraction=" + throat.gasAreaFraction
              + ", phaseAreaSum=" + throat.phaseAreaSum + ", driftResidual=" + throat.driftResidualMs
              + " m/s, kineticEnergyError=" + throat.kineticEnergyErrorJkg + " J/kg"));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * throat.massFluxKgM2s,
          equilibrium.isChoked(), states, diagnostics, null, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "DRIFT_FLUX_REGIME_UNSUPPORTED", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "DRIFT_FLUX_CLOSURE_FAILED", ex.getMessage());
    }
  }

  private List<Diagnostic> diagnostics(ReleaseFlowResult equilibrium) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Vertical-upward Zuber-Findlay/Harmathy bubbly-dispersed slip with caller-declared surface tension and "
            + "equilibrium thermodynamics; no "
            + "finite-rate phase transfer, entrainment transport, annular jet closure, pipe friction, heat transfer "
            + "or solids"));
    for (Diagnostic diagnostic : equilibrium.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode()) && !"THROAT_MACH_MISMATCH".equals(diagnostic.getCode())
          && !"ACOUSTIC_UNAVAILABLE".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  private Hydrodynamics partition(ReleaseState reference) {
    PhaseBasis basis = phaseBasis(reference);
    double driftVelocity = HARMATHY_COEFFICIENT * Math.pow(GRAVITY_MS2 * surfaceTensionNm
        * (basis.liquidDensityKgM3 - basis.gasDensityKgM3) / (basis.liquidDensityKgM3 * basis.liquidDensityKgM3), 0.25);
    ReleaseFlowRequest.positive(driftVelocity, "Harmathy drift velocity");
    double lower = 1.0;
    double lowerResidual = residual(reference, basis, lower, driftVelocity);
    double upper = 2.0;
    double upperResidual = residual(reference, basis, upper, driftVelocity);
    while (upperResidual <= 0.0 && upper < MAX_SLIP_RATIO) {
      upper *= 2.0;
      upperResidual = residual(reference, basis, upper, driftVelocity);
    }
    if (!(lowerResidual < 0.0) || !(upperResidual > 0.0)) {
      throw new IllegalStateException("Unable to bracket positive drift-flux slip solution");
    }
    for (int iteration = 0; iteration < 80; iteration++) {
      double middle = 0.5 * (lower + upper);
      double middleResidual = residual(reference, basis, middle, driftVelocity);
      if (middleResidual > 0.0) {
        upper = middle;
      } else {
        lower = middle;
      }
    }
    double slipRatio = 0.5 * (lower + upper);
    Hydrodynamics closure = closure(reference, basis, slipRatio, surfaceTensionNm, driftVelocity);
    if (closure.gasAreaFraction > MAX_GAS_AREA_FRACTION) {
      throw new UnsupportedOperationException("Predicted gas area fraction " + closure.gasAreaFraction
          + " exceeds the bounded bubbly/dispersed screening limit " + MAX_GAS_AREA_FRACTION);
    }
    return closure;
  }

  private double residual(ReleaseState reference, PhaseBasis basis, double slipRatio, double driftVelocity) {
    Hydrodynamics closure = closure(reference, basis, slipRatio, Double.NaN, driftVelocity);
    return closure.driftResidualMs;
  }

  private Hydrodynamics closure(ReleaseState reference, PhaseBasis basis, double slipRatio, double surfaceTension,
      double driftVelocity) {
    double homogeneousVelocity = ReleaseFlowRequest.positive(reference.getVelocityMs(), "homogeneous velocity");
    double denominator = basis.gasMassFraction * slipRatio * slipRatio + basis.liquidMassFraction;
    double liquidVelocity = homogeneousVelocity / Math.sqrt(denominator);
    double gasVelocity = slipRatio * liquidVelocity;
    double specificArea = basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity)
        + basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double massFlux = 1.0 / specificArea;
    double gasAreaFraction = massFlux * basis.gasMassFraction / (basis.gasDensityKgM3 * gasVelocity);
    double liquidAreaFraction = massFlux * basis.liquidMassFraction / (basis.liquidDensityKgM3 * liquidVelocity);
    double phaseAreaSum = gasAreaFraction + liquidAreaFraction;
    double volumetricFlux = massFlux
        * (basis.gasMassFraction / basis.gasDensityKgM3 + basis.liquidMassFraction / basis.liquidDensityKgM3);
    double driftResidual = gasVelocity - DISTRIBUTION_PARAMETER * volumetricFlux - driftVelocity;
    double phaseKineticEnergy = 0.5 * (basis.gasMassFraction * gasVelocity * gasVelocity
        + basis.liquidMassFraction * liquidVelocity * liquidVelocity);
    double homogeneousKineticEnergy = 0.5 * homogeneousVelocity * homogeneousVelocity;
    double kineticEnergyError = phaseKineticEnergy - homogeneousKineticEnergy;
    if (!Double.isFinite(massFlux) || massFlux <= 0.0 || !Double.isFinite(driftResidual)
        || Math.abs(phaseAreaSum - 1.0) > 1e-10
        || Math.abs(kineticEnergyError) > 1e-9 * Math.max(1.0, homogeneousKineticEnergy)) {
      throw new IllegalStateException("Phase area, drift-flux or kinetic-energy closure failed");
    }
    Map<String, Double> velocities = new TreeMap<String, Double>();
    velocities.put("GAS", gasVelocity);
    velocities.put(basis.liquidPhase, liquidVelocity);
    double bulkVelocity = massFlux / reference.getDensityKgM3();
    return new Hydrodynamics(reference.withPhaseVelocities(bulkVelocity, velocities), massFlux, gasAreaFraction,
        phaseAreaSum, kineticEnergyError, driftResidual, driftVelocity, slipRatio, surfaceTension);
  }

  private PhaseBasis phaseBasis(ReleaseState reference) {
    if (reference == null || reference.getPhaseMassFractions().size() != 2
        || !reference.getPhaseMassFractions().containsKey("GAS")) {
      throw new UnsupportedOperationException("Exactly one gas and one liquid phase are required at the opening");
    }
    String liquid = null;
    for (String phase : reference.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported drift-flux phase: " + phase);
        }
        liquid = phase;
      }
    }
    if (liquid == null
        || !reference.getPhaseDensitiesKgM3().keySet().equals(reference.getPhaseMassFractions().keySet())) {
      throw new IllegalStateException("Phase density and mass-fraction bases do not match");
    }
    double gasMassFraction = reference.getPhaseMassFractions().get("GAS");
    double liquidMassFraction = reference.getPhaseMassFractions().get(liquid);
    if (gasMassFraction <= 1e-10 || liquidMassFraction <= 1e-10) {
      throw new UnsupportedOperationException("Both phases must have positive resolved mass fraction");
    }
    double gasDensity = ReleaseFlowRequest.positive(reference.getPhaseDensitiesKgM3().get("GAS"), "gas density");
    double liquidDensity = ReleaseFlowRequest.positive(reference.getPhaseDensitiesKgM3().get(liquid), "liquid density");
    if (liquidDensity <= gasDensity) {
      throw new UnsupportedOperationException("Harmathy closure requires liquid density greater than gas density");
    }
    return new PhaseBasis(liquid, gasMassFraction, liquidMassFraction, gasDensity, liquidDensity);
  }

  /** @return Zuber-Findlay distribution parameter used by the bounded vertical closure */
  public double getDistributionParameter() {
    return DISTRIBUTION_PARAMETER;
  }

  /** @return caller-declared gas/liquid interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return maximum gas area fraction accepted by the bubbly/dispersed screening boundary */
  public double getMaximumGasAreaFraction() {
    return MAX_GAS_AREA_FRACTION;
  }

  private static final class PhaseBasis {
    private final String liquidPhase;
    private final double gasMassFraction;
    private final double liquidMassFraction;
    private final double gasDensityKgM3;
    private final double liquidDensityKgM3;

    private PhaseBasis(String liquidPhase, double gasMassFraction, double liquidMassFraction, double gasDensityKgM3,
        double liquidDensityKgM3) {
      this.liquidPhase = liquidPhase;
      this.gasMassFraction = gasMassFraction;
      this.liquidMassFraction = liquidMassFraction;
      this.gasDensityKgM3 = gasDensityKgM3;
      this.liquidDensityKgM3 = liquidDensityKgM3;
    }
  }

  private static final class Hydrodynamics {
    private final ReleaseState state;
    private final double massFluxKgM2s;
    private final double gasAreaFraction;
    private final double phaseAreaSum;
    private final double kineticEnergyErrorJkg;
    private final double driftResidualMs;
    private final double driftVelocityMs;
    private final double slipRatio;
    private final double surfaceTensionNm;

    private Hydrodynamics(ReleaseState state, double massFluxKgM2s, double gasAreaFraction, double phaseAreaSum,
        double kineticEnergyErrorJkg, double driftResidualMs, double driftVelocityMs, double slipRatio,
        double surfaceTensionNm) {
      this.state = state;
      this.massFluxKgM2s = massFluxKgM2s;
      this.gasAreaFraction = gasAreaFraction;
      this.phaseAreaSum = phaseAreaSum;
      this.kineticEnergyErrorJkg = kineticEnergyErrorJkg;
      this.driftResidualMs = driftResidualMs;
      this.driftVelocityMs = driftVelocityMs;
      this.slipRatio = slipRatio;
      this.surfaceTensionNm = surfaceTensionNm;
    }
  }
}
