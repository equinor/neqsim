package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
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
      TwoPhaseDriftFluxClosure.Result throat = TwoPhaseDriftFluxClosure
          .solve(equilibrium.getStations().get(Station.THROAT_CRITICAL), surfaceTensionNm);
      states.put(Station.THROAT_CRITICAL, throat.state);
      states.put(Station.ORIFICE_EXIT, throat.state);
      ReleaseState ambient = equilibrium.getStations().get(Station.AMBIENT_EXPANDED);
      if (ambient != null && ambient.getPhaseMassFractions().size() == 2
          && ambient.getPhaseMassFractions().containsKey("GAS")) {
        states.put(Station.AMBIENT_EXPANDED, TwoPhaseDriftFluxClosure.solve(ambient, surfaceTensionNm).state);
      }
      diagnostics.add(new Diagnostic("PREDICTIVE_DRIFT_FLUX",
          "Zuber-Findlay C0=" + TwoPhaseDriftFluxClosure.getDistributionParameter() + "; Harmathy drift velocity="
              + throat.driftVelocityMs + " m/s; solved uGas/uLiquid=" + throat.slipRatio));
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

  /** @return Zuber-Findlay distribution parameter used by the bounded vertical closure */
  public double getDistributionParameter() {
    return TwoPhaseDriftFluxClosure.getDistributionParameter();
  }

  /** @return caller-declared gas/liquid interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return maximum gas area fraction accepted by the bubbly/dispersed screening boundary */
  public double getMaximumGasAreaFraction() {
    return TwoPhaseDriftFluxClosure.getMaximumGasAreaFraction();
  }
}
