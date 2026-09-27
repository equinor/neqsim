package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;

/**
 * Component-selective first-order gas/liquid phase-transfer sensitivity with bounded drift flux.
 *
 * <p>
 * Each overall component mass is partitioned independently between gas and liquid. Caller-declared relaxation times
 * move the upstream gas-held mass of each component toward its equilibrium-station value over one declared residence
 * time. The phase compositions reconstructed from those component inventories are explicit and mass conservative. The
 * resulting one-gas/one-liquid state uses the shared Zuber-Findlay/Harmathy hydrodynamic closure.
 *
 * <p>
 * This model does not predict transfer coefficients, residence time, heat transfer, droplet area or entrainment. It is
 * a parameterized sensitivity model and remains unqualified.
 *
 * @author esol
 * @version 1.0
 */
public final class ComponentSelectiveFiniteRateReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private final HomogeneousEquilibriumReleaseModel equilibriumModel = new HomogeneousEquilibriumReleaseModel();
  private final double surfaceTensionNm;
  private final Map<String, Double> componentRelaxationTimesS;
  private final double residenceTimeS;
  private final String parameterProvenance;

  /**
   * Creates a component-selective finite-rate sensitivity model.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   * @param componentRelaxationTimesS positive first-order relaxation time in s for every fluid component
   * @param residenceTimeS caller-declared available transfer time in s
   * @param parameterProvenance nonempty source or calibration identity for every supplied parameter
   */
  public ComponentSelectiveFiniteRateReleaseModel(double surfaceTensionNm,
      Map<String, Double> componentRelaxationTimesS, double residenceTimeS, String parameterProvenance) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    this.residenceTimeS = ReleaseFlowRequest.positive(residenceTimeS, "residenceTimeS");
    if (componentRelaxationTimesS == null || componentRelaxationTimesS.isEmpty()) {
      throw new IllegalArgumentException("Component relaxation times required");
    }
    Map<String, Double> copy = new TreeMap<String, Double>();
    for (Map.Entry<String, Double> entry : componentRelaxationTimesS.entrySet()) {
      if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
        throw new IllegalArgumentException("Component name required");
      }
      copy.put(entry.getKey(), ReleaseFlowRequest.positive(entry.getValue(), "component relaxation time"));
    }
    this.componentRelaxationTimesS = Collections.unmodifiableMap(copy);
    if (parameterProvenance == null || parameterProvenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Parameter provenance required");
    }
    this.parameterProvenance = parameterProvenance;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "component-selective-finite-rate-vertical-drift-flux-orifice";
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
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "COMPONENT_SELECTIVE_PHASE_RELAXATION",
            "VERTICAL_UPWARD_DRIFT_FLUX", "CALLER_DECLARED_COMPONENT_TIMES", "CALLER_DECLARED_SURFACE_TENSION"),
        Arrays.asList("NO_PREDICTED_TRANSFER_COEFFICIENTS", "NO_PREDICTIVE_RESIDENCE_TIME",
            "NO_INTERFACIAL_HEAT_TRANSFER", "NO_ENTRAINMENT_OR_DROPLET_SIZE", "NO_ANNULAR_JET_MODEL",
            "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("component-wise-first-order-relaxation",
                ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/ComponentSelectiveFiniteRateReleaseModelTest.java",
                "Closed-form component endpoints, reconstruction, conservation and uniform-time limit", false),
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
      diagnostics.add(new Diagnostic("COMPONENT_TRANSFER_NOT_APPLIED",
          "No forward flow; component phase transfer is not resolved"));
      return ReleaseFlowResult.success(this, 0.0, false, equilibrium.getStations(), diagnostics, null, false);
    }
    try {
      ReleaseState upstream = equilibrium.getStations().get(Station.UPSTREAM_STAGNATION);
      requireComponentBasis(upstream);
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
              "Ambient-expanded diagnostic does not retain one gas and one liquid phase; component-selective "
                  + "closure is reported only at the opening"));
        }
      }
      double componentError = reconstructedComponentError(upstream, throat.state);
      double energyError = stagnationEnergyError(upstream, throat.state);
      if (componentError > 1e-12 || Math.abs(energyError) > 1e-6) {
        throw new IllegalStateException("Component or stagnation-energy closure failed");
      }
      diagnostics.add(new Diagnostic("COMPONENT_SELECTIVE_PHASE_TRANSFER", "residenceTime=" + residenceTimeS
          + " s; componentRelaxationTimes=" + componentRelaxationTimesS + " s; provenance=" + parameterProvenance));
      diagnostics.add(new Diagnostic("COMPONENT_TRANSFER_CONSERVATION",
          "maximumReconstructedComponentMassFractionError=" + componentError + "; stagnationEnergyError=" + energyError
              + " J/kg; analytical component exponentials are timestep independent"));
      diagnostics.add(new Diagnostic("DRIFT_FLUX_CLOSURE",
          "surfaceTension=" + throat.surfaceTensionNm + " N/m, gasAreaFraction=" + throat.gasAreaFraction
              + ", phaseAreaSum=" + throat.phaseAreaSum + ", driftResidual=" + throat.driftResidualMs
              + " m/s, kineticEnergyError=" + throat.kineticEnergyErrorJkg + " J/kg"));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * throat.massFluxKgM2s,
          equilibrium.isChoked(), states, diagnostics, null, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "COMPONENT_TRANSFER_REGIME_UNSUPPORTED", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "COMPONENT_TRANSFER_CLOSURE_FAILED", ex.getMessage());
    }
  }

  private List<Diagnostic> diagnostics(ReleaseFlowResult equilibrium) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Independent first-order component phase-partition relaxation with caller-declared times and provenance; "
            + "equilibrium caloric properties and bounded vertical drift flux; no predicted transfer coefficients, "
            + "interfacial heat transfer, entrainment, annular jet, pipe friction, heat transfer or solids"));
    for (Diagnostic diagnostic : equilibrium.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode()) && !"THROAT_MACH_MISMATCH".equals(diagnostic.getCode())
          && !"ACOUSTIC_UNAVAILABLE".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  private void requireComponentBasis(ReleaseState upstream) {
    if (!upstream.getComponentMassFractions().keySet().equals(componentRelaxationTimesS.keySet())) {
      throw new UnsupportedOperationException("Relaxation-time components must exactly match the fluid components");
    }
  }

  private ReleaseState relaxedState(ReleaseState upstream, ReleaseState equilibriumStation) {
    if (!hasGasAndLiquid(equilibriumStation)) {
      throw new UnsupportedOperationException("Equilibrium station must contain one gas and one liquid phase");
    }
    String liquid = liquidPhase(equilibriumStation);
    Map<String, Double> gasHeld = new TreeMap<String, Double>();
    double gasMassFraction = 0.0;
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double initial = gasHeldMassFraction(upstream, component);
      double target = gasHeldMassFraction(equilibriumStation, component);
      double progress = getComponentTransferProgress(component);
      double relaxed = initial + progress * (target - initial);
      if (!Double.isFinite(relaxed) || relaxed < -1e-14
          || relaxed > upstream.getComponentMassFractions().get(component) + 1e-14) {
        throw new IllegalStateException("Component phase allocation is outside its conserved inventory");
      }
      relaxed = Math.max(0.0, Math.min(upstream.getComponentMassFractions().get(component), relaxed));
      gasHeld.put(component, relaxed);
      gasMassFraction += relaxed;
    }
    if (gasMassFraction <= 1e-10 || gasMassFraction >= 1.0 - 1e-10) {
      throw new UnsupportedOperationException("Relaxed state must retain positive gas and liquid mass fractions");
    }
    Map<String, Double> phaseMassFractions = new TreeMap<String, Double>();
    phaseMassFractions.put("GAS", gasMassFraction);
    phaseMassFractions.put(liquid, 1.0 - gasMassFraction);
    Map<String, Double> gasComposition = new TreeMap<String, Double>();
    Map<String, Double> liquidComposition = new TreeMap<String, Double>();
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double overall = upstream.getComponentMassFractions().get(component);
      gasComposition.put(component, gasHeld.get(component) / gasMassFraction);
      liquidComposition.put(component, (overall - gasHeld.get(component)) / (1.0 - gasMassFraction));
    }
    Map<String, Map<String, Double>> phaseComponents = new TreeMap<String, Map<String, Double>>();
    phaseComponents.put("GAS", gasComposition);
    phaseComponents.put(liquid, liquidComposition);
    return equilibriumStation.withPhaseBasis(phaseMassFractions, equilibriumStation.getPhaseDensitiesKgM3(),
        phaseComponents);
  }

  private double gasHeldMassFraction(ReleaseState state, String component) {
    if (!state.getPhaseMassFractions().containsKey("GAS")) {
      return 0.0;
    }
    Map<String, Double> gasComposition = state.getPhaseComponentMassFractions().get("GAS");
    if (gasComposition == null || !gasComposition.containsKey(component)) {
      throw new IllegalStateException("Gas-phase component partition is unavailable");
    }
    return state.getPhaseMassFractions().get("GAS") * gasComposition.get(component);
  }

  private String liquidPhase(ReleaseState state) {
    for (String phase : state.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported component-transfer liquid phase: " + phase);
        }
        return phase;
      }
    }
    throw new UnsupportedOperationException("Liquid phase is required");
  }

  private boolean hasGasAndLiquid(ReleaseState state) {
    if (state == null || state.getPhaseMassFractions().size() != 2 || !state.getPhaseMassFractions().containsKey("GAS")
        || state.getPhaseComponentMassFractions().isEmpty()) {
      return false;
    }
    try {
      liquidPhase(state);
      return true;
    } catch (UnsupportedOperationException ex) {
      return false;
    }
  }

  private double reconstructedComponentError(ReleaseState upstream, ReleaseState station) {
    double maximum = 0.0;
    for (String component : upstream.getComponentMassFractions().keySet()) {
      double reconstructed = 0.0;
      for (Map.Entry<String, Double> phase : station.getPhaseMassFractions().entrySet()) {
        reconstructed += phase.getValue() * station.getPhaseComponentMassFractions().get(phase.getKey()).get(component);
      }
      maximum = Math.max(maximum, Math.abs(reconstructed - upstream.getComponentMassFractions().get(component)));
    }
    return maximum;
  }

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

  /** @return immutable sorted component relaxation times in s */
  public Map<String, Double> getComponentRelaxationTimesS() {
    return Collections.unmodifiableMap(new TreeMap<String, Double>(componentRelaxationTimesS));
  }

  /** @return caller-declared residence time in s */
  public double getResidenceTimeS() {
    return residenceTimeS;
  }

  /** @return caller-declared parameter source or calibration identity */
  public String getParameterProvenance() {
    return parameterProvenance;
  }

  /**
   * Returns the exact first-order transfer progress for one component.
   *
   * @param component component name used by the fluid
   * @return exact progress in [0, 1)
   */
  public double getComponentTransferProgress(String component) {
    Double relaxationTimeS = componentRelaxationTimesS.get(component);
    if (relaxationTimeS == null) {
      throw new IllegalArgumentException("No relaxation time for component " + component);
    }
    return -Math.expm1(-residenceTimeS / relaxationTimeS);
  }
}
