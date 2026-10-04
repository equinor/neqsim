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
 * Bounded gas/liquid short-opening model with first-order phase-split relaxation and drift flux.
 *
 * <p>
 * The equilibrium model supplies isentropic thermodynamic stations and available kinetic energy. A caller-declared
 * first-order relaxation time and residence time move the upstream gas mass fraction toward the equilibrium station
 * value with {@code progress = 1 - exp(-residenceTime / relaxationTime)}. The resulting one-gas/one-liquid state is
 * closed with the same Zuber-Findlay/Harmathy hydrodynamics as the equilibrium drift-flux model. Overall component
 * fractions and equilibrium caloric properties are retained exactly; this is a screening relaxation model, not a
 * component-selective mass-transfer or experimental qualification model.
 *
 * @author esol
 * @version 1.0
 */
public final class FiniteRateDriftFluxReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private final HomogeneousEquilibriumReleaseModel equilibriumModel = new HomogeneousEquilibriumReleaseModel();
  private final double surfaceTensionNm;
  private final double phaseTransferRelaxationTimeS;
  private final double residenceTimeS;
  private final String parameterProvenance;

  /**
   * Creates a finite-rate phase-split sensitivity model.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   * @param phaseTransferRelaxationTimeS first-order phase-transfer relaxation time in s
   * @param residenceTimeS caller-declared available transfer time in s
   * @param parameterProvenance nonempty source or calibration identity for the relaxation inputs
   */
  public FiniteRateDriftFluxReleaseModel(double surfaceTensionNm, double phaseTransferRelaxationTimeS,
      double residenceTimeS, String parameterProvenance) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    this.phaseTransferRelaxationTimeS = ReleaseFlowRequest.positive(phaseTransferRelaxationTimeS,
        "phaseTransferRelaxationTimeS");
    this.residenceTimeS = ReleaseFlowRequest.positive(residenceTimeS, "residenceTimeS");
    if (parameterProvenance == null || parameterProvenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Parameter provenance required");
    }
    this.parameterProvenance = parameterProvenance;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "finite-rate-phase-relaxation-vertical-drift-flux-orifice";
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
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "FINITE_RATE_PHASE_SPLIT_RELAXATION",
            "VERTICAL_UPWARD_DRIFT_FLUX", "CALLER_DECLARED_TRANSFER_TIMES", "CALLER_DECLARED_SURFACE_TENSION"),
        Arrays.asList("UNIFORM_PHASE_SPLIT_RELAXATION", "NO_COMPONENT_SELECTIVE_TRANSFER_COEFFICIENTS",
            "NO_PREDICTIVE_RESIDENCE_TIME", "NO_ENTRAINMENT_OR_DROPLET_SIZE", "NO_ANNULAR_JET_MODEL",
            "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("first-order-phase-split-relaxation", ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/FiniteRateDriftFluxReleaseModelTest.java",
                "Closed-form first-order endpoint, refinement, component and energy checks", false),
            new ReleaseModelEvidence.Record("zuber-findlay-harmathy-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/main/java/neqsim/process/safety/release/TwoPhaseDriftFluxClosure.java",
                "Shared bounded phase-area, drift-flux and kinetic-energy closure", false)));
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
      diagnostics.add(new Diagnostic("FINITE_RATE_NOT_APPLIED", "No forward flow; phase transfer is not resolved"));
      return ReleaseFlowResult.success(this, 0.0, false, equilibrium.getStations(), diagnostics, null, false);
    }
    try {
      ReleaseState upstream = equilibrium.getStations().get(Station.UPSTREAM_STAGNATION);
      EnumMap<Station, ReleaseState> states = new EnumMap<Station, ReleaseState>(Station.class);
      states.putAll(equilibrium.getStations());
      ReleaseState relaxedThroat = relaxedState(upstream, equilibrium.getStations().get(Station.THROAT_CRITICAL));
      TwoPhaseDriftFluxClosure.Result throat = TwoPhaseDriftFluxClosure.solve(relaxedThroat, surfaceTensionNm);
      states.put(Station.THROAT_CRITICAL, throat.state);
      states.put(Station.ORIFICE_EXIT, throat.state);
      ReleaseState ambient = equilibrium.getStations().get(Station.AMBIENT_EXPANDED);
      if (ambient != null) {
        if (hasGasAndLiquid(ambient)) {
          states.put(Station.AMBIENT_EXPANDED,
              TwoPhaseDriftFluxClosure.solve(relaxedState(upstream, ambient), surfaceTensionNm).state);
        } else {
          diagnostics.add(new Diagnostic("AMBIENT_EQUILIBRIUM_ONLY",
              "Ambient-expanded diagnostic does not retain one gas and one liquid phase; finite-rate closure is "
                  + "reported only at the opening"));
        }
      }
      double componentError = componentError(upstream, throat.state);
      double energyError = stagnationEnergyError(upstream, throat.state);
      if (componentError > 1e-12 || Math.abs(energyError) > 1e-6) {
        throw new IllegalStateException("Component or stagnation-energy closure failed");
      }
      diagnostics.add(new Diagnostic("FINITE_RATE_PHASE_TRANSFER",
          "firstOrderProgress=" + getPhaseTransferProgress() + "; relaxationTime=" + phaseTransferRelaxationTimeS
              + " s; residenceTime=" + residenceTimeS + " s; provenance=" + parameterProvenance));
      diagnostics.add(new Diagnostic("FINITE_RATE_CONSERVATION",
          "maximumOverallComponentMassFractionError=" + componentError + "; stagnationEnergyError=" + energyError
              + " J/kg; analytical exponential solution is timestep independent"));
      diagnostics.add(new Diagnostic("DRIFT_FLUX_CLOSURE",
          "surfaceTension=" + throat.surfaceTensionNm + " N/m, gasAreaFraction=" + throat.gasAreaFraction
              + ", phaseAreaSum=" + throat.phaseAreaSum + ", driftResidual=" + throat.driftResidualMs
              + " m/s, kineticEnergyError=" + throat.kineticEnergyErrorJkg + " J/kg"));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * throat.massFluxKgM2s,
          equilibrium.isChoked(), states, diagnostics, null, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "FINITE_RATE_REGIME_UNSUPPORTED", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "FINITE_RATE_CLOSURE_FAILED", ex.getMessage());
    }
  }

  /**
   * Creates stable assumption diagnostics while retaining relevant equilibrium information.
   *
   * @param equilibrium equilibrium base result
   * @return mutable diagnostic list for this calculation
   */
  private List<Diagnostic> diagnostics(ReleaseFlowResult equilibrium) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Uniform first-order gas/liquid phase-split relaxation with caller-declared times and provenance; "
            + "equilibrium caloric properties and bounded vertical drift flux; no component-selective kinetics, "
            + "predictive residence time, entrainment, annular jet, pipe friction, heat transfer or solids"));
    for (Diagnostic diagnostic : equilibrium.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode()) && !"THROAT_MACH_MISMATCH".equals(diagnostic.getCode())
          && !"ACOUSTIC_UNAVAILABLE".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  /**
   * Relaxes a station phase split from the upstream state toward its equilibrium value.
   *
   * @param upstream upstream stagnation state
   * @param equilibriumStation equilibrium station
   * @return station with relaxed phase mass fractions and harmonic mixture density
   */
  private ReleaseState relaxedState(ReleaseState upstream, ReleaseState equilibriumStation) {
    if (!hasGasAndLiquid(equilibriumStation)) {
      throw new UnsupportedOperationException("Equilibrium station must contain one gas and one liquid phase");
    }
    String upstreamLiquid = liquidPhase(upstream);
    String equilibriumLiquid = liquidPhase(equilibriumStation);
    if (!upstreamLiquid.equals(equilibriumLiquid)) {
      throw new UnsupportedOperationException("Upstream and station liquid phase types must match");
    }
    double progress = getPhaseTransferProgress();
    double upstreamGas = upstream.getPhaseMassFractions().containsKey("GAS")
        ? upstream.getPhaseMassFractions().get("GAS")
        : 0.0;
    double equilibriumGas = equilibriumStation.getPhaseMassFractions().get("GAS");
    double relaxedGas = upstreamGas + progress * (equilibriumGas - upstreamGas);
    if (relaxedGas <= 1e-10 || relaxedGas >= 1.0 - 1e-10) {
      throw new UnsupportedOperationException("Relaxed state must retain positive gas and liquid mass fractions");
    }
    Map<String, Double> phaseMassFractions = new TreeMap<String, Double>();
    phaseMassFractions.put("GAS", relaxedGas);
    phaseMassFractions.put(equilibriumLiquid, 1.0 - relaxedGas);
    return equilibriumStation.withPhaseBasis(phaseMassFractions, equilibriumStation.getPhaseDensitiesKgM3());
  }

  /**
   * Resolves and validates the native liquid phase name.
   *
   * @param state state requiring exactly one gas and one liquid phase
   * @return native liquid phase name
   */
  private String liquidPhase(ReleaseState state) {
    if (state == null || state.getPhaseMassFractions().isEmpty() || state.getPhaseMassFractions().size() > 2) {
      throw new UnsupportedOperationException("One liquid phase with at most one gas phase is required");
    }
    for (String phase : state.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported finite-rate liquid phase: " + phase);
        }
        return phase;
      }
    }
    throw new UnsupportedOperationException("Liquid phase is required");
  }

  /**
   * Checks whether a state has exactly one gas and one supported liquid phase.
   *
   * @param state state to inspect
   * @return true for the bounded two-phase basis
   */
  private boolean hasGasAndLiquid(ReleaseState state) {
    if (state == null || state.getPhaseMassFractions().size() != 2
        || !state.getPhaseMassFractions().containsKey("GAS")) {
      return false;
    }
    try {
      liquidPhase(state);
      return true;
    } catch (UnsupportedOperationException ex) {
      return false;
    }
  }

  /**
   * Finds the maximum absolute overall component mass-fraction difference.
   *
   * @param upstream upstream state
   * @param station relaxed station
   * @return maximum absolute component mass-fraction error
   */
  private double componentError(ReleaseState upstream, ReleaseState station) {
    if (!upstream.getComponentMassFractions().keySet().equals(station.getComponentMassFractions().keySet())) {
      throw new IllegalStateException("Component bases differ");
    }
    double error = 0.0;
    for (Map.Entry<String, Double> entry : upstream.getComponentMassFractions().entrySet()) {
      error = Math.max(error, Math.abs(entry.getValue() - station.getComponentMassFractions().get(entry.getKey())));
    }
    return error;
  }

  /**
   * Checks stagnation enthalpy using phase-mass-weighted kinetic energy.
   *
   * @param upstream upstream stagnation state
   * @param station relaxed slip-flow station
   * @return station total energy minus upstream stagnation enthalpy in J/kg
   */
  private double stagnationEnergyError(ReleaseState upstream, ReleaseState station) {
    double kineticEnergyJkg = 0.0;
    for (Map.Entry<String, Double> entry : station.getPhaseMassFractions().entrySet()) {
      double velocityMs = station.getPhaseVelocitiesMs().get(entry.getKey());
      kineticEnergyJkg += 0.5 * entry.getValue() * velocityMs * velocityMs;
    }
    return station.getEnthalpyJkg() + kineticEnergyJkg - upstream.getEnthalpyJkg();
  }

  /** @return caller-declared interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return caller-declared first-order phase-transfer relaxation time in s */
  public double getPhaseTransferRelaxationTimeS() {
    return phaseTransferRelaxationTimeS;
  }

  /** @return caller-declared transfer residence time in s */
  public double getResidenceTimeS() {
    return residenceTimeS;
  }

  /** @return caller-declared parameter source or calibration identity */
  public String getParameterProvenance() {
    return parameterProvenance;
  }

  /** @return exact first-order relaxation progress in [0, 1) */
  public double getPhaseTransferProgress() {
    return -Math.expm1(-residenceTimeS / phaseTransferRelaxationTimeS);
  }
}
