package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import neqsim.process.safety.release.RanzMarshallMassTransferCorrelation.Morphology;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.process.safety.release.ReleaseFlowResult.Status;

/**
 * Ranz-Marshall component-transfer adapter for prescribed spherical liquid droplets in gas.
 *
 * <p>
 * The immutable correlation supplies an explicit droplet Sauter mean diameter, gas-film properties, component
 * diffusivities and gas-to-droplet relative speed. Component relaxation remains exactly conservative. A separate
 * prescribed-slip closure resolves positive gas and droplet velocities while retaining homogeneous kinetic energy,
 * phase-area closure and the caller-declared slip. The adapter fails closed unless the liquid area fraction is at most
 * 0.20, {@code Eo <= 4}, {@code We <= 12}, {@code Re < 200}, and every {@code Sc < 250}.
 *
 * <p>
 * This is an unqualified sensitivity adapter. It does not predict droplet diameter, relative speed, residence time,
 * breakup/coalescence, entrainment, annular flow, internal droplet resistance, interfacial heat transfer, Stefan flow,
 * solids, or experimental release-rate accuracy.
 *
 * @author esol
 * @version 1.0
 */
public final class RanzMarshallDropletFiniteRateReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private final HomogeneousEquilibriumReleaseModel equilibriumModel = new HomogeneousEquilibriumReleaseModel();
  private final double surfaceTensionNm;
  private final double residenceTimeS;
  private final RanzMarshallMassTransferCorrelation correlation;

  /**
   * Creates a liquid-droplet release adapter from an immutable Ranz-Marshall correlation.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   * @param residenceTimeS caller-declared available transfer time in s
   * @param correlation explicit liquid-droplet-in-gas external-film correlation
   */
  public RanzMarshallDropletFiniteRateReleaseModel(double surfaceTensionNm, double residenceTimeS,
      RanzMarshallMassTransferCorrelation correlation) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    this.residenceTimeS = ReleaseFlowRequest.positive(residenceTimeS, "residenceTimeS");
    if (correlation == null) {
      throw new IllegalArgumentException("Ranz-Marshall correlation required");
    }
    if (correlation.getMorphology() != Morphology.LIQUID_DROPLETS) {
      throw new IllegalArgumentException("Liquid-droplet morphology required");
    }
    this.correlation = correlation;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "ranz-marshall-component-transfer-prescribed-droplet-slip-orifice";
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
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "LIQUID_DROPLET_DISPERSION",
            "RANZ_MARSHALL_EXTERNAL_GAS_FILM_TRANSFER", "COMPONENT_SELECTIVE_PHASE_RELAXATION",
            "PRESCRIBED_GAS_TO_DROPLET_SLIP", "DILUTE_SPHERICAL_DROPLETS", "EXPLICIT_CONTINUOUS_GAS_PROPERTIES"),
        Arrays.asList("CALLER_DECLARED_DROPLET_DIAMETER", "CALLER_DECLARED_RELATIVE_VELOCITY",
            "CALLER_DECLARED_RESIDENCE_TIME", "LIQUID_AREA_FRACTION_AT_MOST_0_20", "EOTVOS_AT_MOST_4",
            "WEBER_AT_MOST_12", "REYNOLDS_BELOW_200", "SCHMIDT_BELOW_250", "EXTERNAL_FILM_ONLY",
            "NO_PREDICTED_DROPLET_HYDRODYNAMICS", "NO_BREAKUP_OR_COALESCENCE", "NO_ENTRAINMENT_OR_ANNULAR_FLOW",
            "NO_INTERFACIAL_HEAT_TRANSFER", "NO_INTERNAL_DISPERSED_PHASE_RESISTANCE", "NO_STEFAN_FLOW_CORRECTION",
            "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("ranz-marshall-1952", ReleaseModelEvidence.Type.ANALYTICAL,
                "Ranz, W.E. and Marshall, W.R., Chemical Engineering Progress 48 (1952) 141-146, 173-180",
                "Independent published external-film correlation basis; not release-rate validation", true),
            new ReleaseModelEvidence.Record("prescribed-droplet-slip-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/test/java/neqsim/process/safety/release/RanzMarshallDropletFiniteRateReleaseModelTest.java",
                "Phase-area, component, kinetic-energy and stagnation-energy closure with nearby-case trends", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Request required");
    }
    if (!correlation.isWithinApplicability()) {
      return ReleaseFlowResult.failure(this, true, "RANZ_MARSHALL_RANGE_UNSUPPORTED",
          correlation.getApplicabilityMessage());
    }
    Set<String> fluidComponents = new TreeSet<String>(Arrays.asList(request.getFluid().getComponentNames()));
    if (!fluidComponents.equals(correlation.getComponentDiffusivitiesM2S().keySet())) {
      return ReleaseFlowResult.failure(this, false, "RANZ_MARSHALL_COMPONENT_BASIS_INVALID",
          "Diffusivity components must exactly match the release-fluid components; fluid=" + fluidComponents
              + ", diffusivities=" + correlation.getComponentDiffusivitiesM2S().keySet());
    }
    ReleaseFlowResult equilibrium = equilibriumModel.calculate(request);
    if (!equilibrium.isUsable()) {
      Diagnostic cause = equilibrium.getDiagnostics().get(0);
      return ReleaseFlowResult.failure(this, equilibrium.getStatus() == Status.UNSUPPORTED,
          "DROPLET_EQUILIBRIUM_BASE_" + cause.getCode(), cause.getMessage());
    }
    List<Diagnostic> diagnostics = diagnostics(equilibrium);
    if (equilibrium.getMassFlowRateKgS() == 0.0) {
      diagnostics.add(new Diagnostic("DROPLET_TRANSFER_NOT_APPLIED",
          "No forward flow; liquid-droplet component transfer is not resolved"));
      return ReleaseFlowResult.success(this, 0.0, false, equilibrium.getStations(), diagnostics, null, false);
    }

    ComponentSelectiveFiniteRateReleaseModel phaseTransfer = new ComponentSelectiveFiniteRateReleaseModel(
        surfaceTensionNm, correlation.getComponentRelaxationTimesS(), residenceTimeS,
        correlation.getParameterProvenance());
    try {
      ReleaseState upstream = equilibrium.getStations().get(Station.UPSTREAM_STAGNATION);
      phaseTransfer.requireComponentBasis(upstream);
      EnumMap<Station, ReleaseState> states = new EnumMap<Station, ReleaseState>(Station.class);
      states.putAll(equilibrium.getStations());
      ReleaseState relaxedThroat = phaseTransfer.relaxedState(upstream,
          equilibrium.getStations().get(Station.THROAT_CRITICAL));
      TwoPhaseDropletSlipClosure.Result throat = TwoPhaseDropletSlipClosure.solve(relaxedThroat, surfaceTensionNm,
          correlation.getSauterMeanDiameterM(), correlation.getRelativeVelocityMs());
      states.put(Station.THROAT_CRITICAL, throat.state);
      states.put(Station.ORIFICE_EXIT, throat.state);
      ReleaseState ambient = equilibrium.getStations().get(Station.AMBIENT_EXPANDED);
      if (ambient != null) {
        if (phaseTransfer.hasGasAndLiquid(ambient)) {
          ReleaseState relaxedAmbient = phaseTransfer.relaxedState(upstream, ambient);
          states.put(Station.AMBIENT_EXPANDED, TwoPhaseDropletSlipClosure.solve(relaxedAmbient, surfaceTensionNm,
              correlation.getSauterMeanDiameterM(), correlation.getRelativeVelocityMs()).state);
        } else {
          diagnostics.add(new Diagnostic("AMBIENT_EQUILIBRIUM_ONLY",
              "Ambient-expanded diagnostic does not retain one gas and one liquid phase; prescribed-droplet "
                  + "closure is reported only at the opening"));
        }
      }
      double componentError = phaseTransfer.reconstructedComponentError(upstream, throat.state);
      double energyError = phaseTransfer.stagnationEnergyError(upstream, throat.state);
      if (componentError > 1e-12 || Math.abs(energyError) > 1e-6) {
        throw new IllegalStateException("Component or stagnation-energy closure failed");
      }
      diagnostics.add(new Diagnostic("RANZ_MARSHALL_DROPLET_TRANSFER",
          "morphology=" + correlation.getMorphology() + "; d32=" + correlation.getSauterMeanDiameterM()
              + " m; continuousGasDensity=" + correlation.getContinuousPhaseDensityKgM3()
              + " kg/m3; continuousGasViscosity=" + correlation.getContinuousPhaseDynamicViscosityPaS()
              + " Pa s; prescribedRelativeVelocity=" + correlation.getRelativeVelocityMs() + " m/s; Reynolds="
              + correlation.getReynoldsNumber() + "; Schmidt=" + correlation.getSchmidtNumbers() + "; Sherwood="
              + correlation.getSherwoodNumbers() + "; coefficients=" + correlation.getMassTransferCoefficientsMs()
              + " m/s; relaxationTimes=" + correlation.getComponentRelaxationTimesS() + " s; residenceTime="
              + residenceTimeS + " s; provenance=" + correlation.getParameterProvenance()));
      diagnostics.add(new Diagnostic("DROPLET_SLIP_CLOSURE",
          "liquidAreaFraction=" + throat.liquidAreaFraction + "; gasAreaFraction=" + throat.gasAreaFraction
              + "; phaseAreaSum=" + throat.phaseAreaSum + "; kineticEnergyError=" + throat.kineticEnergyErrorJkg
              + " J/kg; Eotvos=" + throat.eotvosNumber + "; Weber=" + throat.weberNumber + "; componentError="
              + componentError + "; stagnationEnergyError=" + energyError + " J/kg"));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * throat.massFluxKgM2s,
          equilibrium.isChoked(), states, diagnostics, null, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "DROPLET_REGIME_UNSUPPORTED", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "DROPLET_CLOSURE_FAILED", ex.getMessage());
    }
  }

  /**
   * Builds adapter diagnostics while retaining applicable equilibrium diagnostics.
   *
   * @param equilibrium usable homogeneous-equilibrium result
   * @return mutable diagnostic list
   */
  private List<Diagnostic> diagnostics(ReleaseFlowResult equilibrium) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Ranz-Marshall external-gas-film component transfer for explicit spherical liquid droplets; "
            + "caller-declared continuous-gas properties, diffusivities, d32, relative velocity, residence time and "
            + "provenance; exact component/energy closure; no predicted droplet hydrodynamics, interfacial heat, "
            + "internal resistance, Stefan-flow correction, breakup/coalescence, entrainment, annular flow or solids"));
    for (Diagnostic diagnostic : equilibrium.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode()) && !"THROAT_MACH_MISMATCH".equals(diagnostic.getCode())
          && !"ACOUSTIC_UNAVAILABLE".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  /** @return caller-declared interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return caller-declared residence time in s */
  public double getResidenceTimeS() {
    return residenceTimeS;
  }

  /** @return immutable liquid-droplet Ranz-Marshall correlation */
  public RanzMarshallMassTransferCorrelation getCorrelation() {
    return correlation;
  }
}
