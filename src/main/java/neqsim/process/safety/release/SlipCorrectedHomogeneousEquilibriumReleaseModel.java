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
 * Equilibrium-thermodynamic short-opening model with caller-declared gas/liquid velocity slip.
 *
 * <p>
 * The homogeneous-equilibrium model resolves the isentropic station and available specific kinetic energy. This model
 * then partitions that kinetic energy between one gas and one liquid phase using the declared velocity ratio
 * {@code uGas/uLiquid}. Total mass flux follows from phase-area closure. A ratio of one recovers the homogeneous-flow
 * limit. The model does not infer slip, entrainment or finite-rate phase transfer and remains unqualified.
 */
public final class SlipCorrectedHomogeneousEquilibriumReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private final double gasToLiquidSlipRatio;
  private final HomogeneousEquilibriumReleaseModel equilibriumModel = new HomogeneousEquilibriumReleaseModel();

  /**
   * Creates a prescribed-slip model.
   *
   * @param gasToLiquidSlipRatio gas velocity divided by liquid velocity, at least one
   */
  public SlipCorrectedHomogeneousEquilibriumReleaseModel(double gasToLiquidSlipRatio) {
    if (!Double.isFinite(gasToLiquidSlipRatio) || gasToLiquidSlipRatio < 1.0) {
      throw new IllegalArgumentException("Gas/liquid slip ratio must be finite and at least one");
    }
    this.gasToLiquidSlipRatio = gasToLiquidSlipRatio;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "equilibrium-thermodynamics-prescribed-slip-orifice";
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
            "CALLER_DECLARED_VELOCITY_SLIP"),
        Arrays.asList("PRESCRIBED_CONSTANT_SLIP", "NO_FINITE_RATE_PHASE_TRANSFER", "NO_ENTRAINMENT_CORRELATION",
            "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(new ReleaseModelEvidence.Record("prescribed-slip-closure", ReleaseModelEvidence.Type.CONSERVATION,
            "src/test/java/neqsim/process/safety/release/SlipCorrectedHomogeneousEquilibriumReleaseModelTest.java",
            "Homogeneous limit, phase-area closure and phase kinetic-energy partition", false)));
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
      diagnostics.add(new Diagnostic("SLIP_NOT_APPLIED", "No forward flow; phase velocities are not resolved"));
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
      diagnostics.add(new Diagnostic("PRESCRIBED_PHASE_SLIP",
          "uGas/uLiquid=" + gasToLiquidSlipRatio + "; no inferred slip or entrainment correlation"));
      diagnostics.add(new Diagnostic("SLIP_CLOSURE",
          "throat gasVelocity=" + throat.gasVelocityMs + " m/s, liquidVelocity=" + throat.liquidVelocityMs
              + " m/s, gasAreaFraction=" + throat.gasAreaFraction + ", phaseAreaSum=" + throat.phaseAreaSum
              + ", kineticEnergyError=" + throat.kineticEnergyErrorJkg + " J/kg"));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * throat.massFluxKgM2s,
          equilibrium.isChoked(), states, diagnostics, null, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "SLIP_REGIME_UNSUPPORTED", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "SLIP_CLOSURE_FAILED", ex.getMessage());
    }
  }

  private List<Diagnostic> diagnostics(ReleaseFlowResult equilibrium) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Equilibrium phase state with prescribed gas/liquid velocity slip; no pipe friction, heat transfer, "
            + "finite-rate phase transfer, entrainment correlation or solids"));
    for (Diagnostic diagnostic : equilibrium.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode()) && !"THROAT_MACH_MISMATCH".equals(diagnostic.getCode())
          && !"ACOUSTIC_UNAVAILABLE".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  private Hydrodynamics partition(ReleaseState reference) {
    if (reference == null || reference.getPhaseMassFractions().size() != 2
        || !reference.getPhaseMassFractions().containsKey("GAS")) {
      throw new UnsupportedOperationException("Exactly one gas and one liquid phase are required at the opening");
    }
    String liquid = null;
    for (String phase : reference.getPhaseMassFractions().keySet()) {
      if (!"GAS".equals(phase)) {
        if (!"OIL".equals(phase) && !"LIQUID".equals(phase) && !"AQUEOUS".equals(phase)) {
          throw new UnsupportedOperationException("Unsupported slip phase: " + phase);
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
    double gasDensity = reference.getPhaseDensitiesKgM3().get("GAS");
    double liquidDensity = reference.getPhaseDensitiesKgM3().get(liquid);
    double homogeneousVelocity = reference.getVelocityMs();
    ReleaseFlowRequest.positive(homogeneousVelocity, "homogeneous throat velocity");
    double denominator = gasMassFraction * gasToLiquidSlipRatio * gasToLiquidSlipRatio + liquidMassFraction;
    double liquidVelocity = homogeneousVelocity / Math.sqrt(denominator);
    double gasVelocity = gasToLiquidSlipRatio * liquidVelocity;
    double specificArea = gasMassFraction / (gasDensity * gasVelocity)
        + liquidMassFraction / (liquidDensity * liquidVelocity);
    double massFlux = 1.0 / specificArea;
    double gasAreaFraction = massFlux * gasMassFraction / (gasDensity * gasVelocity);
    double liquidAreaFraction = massFlux * liquidMassFraction / (liquidDensity * liquidVelocity);
    double phaseAreaSum = gasAreaFraction + liquidAreaFraction;
    double phaseKineticEnergy = 0.5
        * (gasMassFraction * gasVelocity * gasVelocity + liquidMassFraction * liquidVelocity * liquidVelocity);
    double homogeneousKineticEnergy = 0.5 * homogeneousVelocity * homogeneousVelocity;
    double kineticEnergyError = phaseKineticEnergy - homogeneousKineticEnergy;
    if (!Double.isFinite(massFlux) || massFlux <= 0.0 || Math.abs(phaseAreaSum - 1.0) > 1e-10
        || Math.abs(kineticEnergyError) > 1e-9 * Math.max(1.0, homogeneousKineticEnergy)) {
      throw new IllegalStateException("Phase area or kinetic-energy closure failed");
    }
    Map<String, Double> velocities = new TreeMap<String, Double>();
    velocities.put("GAS", gasVelocity);
    velocities.put(liquid, liquidVelocity);
    double bulkVelocity = massFlux / reference.getDensityKgM3();
    return new Hydrodynamics(reference.withPhaseVelocities(bulkVelocity, velocities), massFlux, gasVelocity,
        liquidVelocity, gasAreaFraction, phaseAreaSum, kineticEnergyError);
  }

  /** @return caller-declared gas velocity divided by liquid velocity */
  public double getGasToLiquidSlipRatio() {
    return gasToLiquidSlipRatio;
  }

  private static final class Hydrodynamics {
    private final ReleaseState state;
    private final double massFluxKgM2s;
    private final double gasVelocityMs;
    private final double liquidVelocityMs;
    private final double gasAreaFraction;
    private final double phaseAreaSum;
    private final double kineticEnergyErrorJkg;

    private Hydrodynamics(ReleaseState state, double massFluxKgM2s, double gasVelocityMs, double liquidVelocityMs,
        double gasAreaFraction, double phaseAreaSum, double kineticEnergyErrorJkg) {
      this.state = state;
      this.massFluxKgM2s = massFluxKgM2s;
      this.gasVelocityMs = gasVelocityMs;
      this.liquidVelocityMs = liquidVelocityMs;
      this.gasAreaFraction = gasAreaFraction;
      this.phaseAreaSum = phaseAreaSum;
      this.kineticEnergyErrorJkg = kineticEnergyErrorJkg;
    }
  }
}
