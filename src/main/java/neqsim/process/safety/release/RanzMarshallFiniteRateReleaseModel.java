package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import neqsim.process.safety.release.RanzMarshallMassTransferCorrelation.Morphology;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Status;

/**
 * Ranz-Marshall component-transfer release model for bounded vertical gas-bubble dispersion.
 *
 * <p>
 * The immutable correlation predicts component external-film relaxation times from explicit SI properties. Those times
 * drive the existing component-selective phase-partition model, which retains exact component and stagnation-energy
 * closure and the bounded Zuber-Findlay/Harmathy hydrodynamics. Liquid-droplet correlation results are exposed by the
 * correlation object, but this adapter fails closed because the current release hydrodynamics represent gas bubbles in
 * a continuous liquid rather than droplets in gas.
 *
 * @author esol
 * @version 1.0
 */
public final class RanzMarshallFiniteRateReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private final double surfaceTensionNm;
  private final double residenceTimeS;
  private final RanzMarshallMassTransferCorrelation correlation;

  /**
   * Creates a release adapter from an immutable Ranz-Marshall correlation.
   *
   * @param surfaceTensionNm caller-declared gas/liquid interfacial tension in N/m
   * @param residenceTimeS caller-declared available transfer time in s
   * @param correlation explicit spherical-dispersion mass-transfer correlation
   */
  public RanzMarshallFiniteRateReleaseModel(double surfaceTensionNm, double residenceTimeS,
      RanzMarshallMassTransferCorrelation correlation) {
    this.surfaceTensionNm = ReleaseFlowRequest.positive(surfaceTensionNm, "surfaceTensionNm");
    this.residenceTimeS = ReleaseFlowRequest.positive(residenceTimeS, "residenceTimeS");
    if (correlation == null) {
      throw new IllegalArgumentException("Ranz-Marshall correlation required");
    }
    this.correlation = correlation;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelId() {
    return "ranz-marshall-component-transfer-vertical-drift-flux-orifice";
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
        Arrays.asList("SHORT_ORIFICE", "TWO_PHASE_GAS_LIQUID", "GAS_BUBBLE_DISPERSION",
            "RANZ_MARSHALL_EXTERNAL_FILM_TRANSFER", "COMPONENT_SELECTIVE_PHASE_RELAXATION",
            "VERTICAL_UPWARD_DRIFT_FLUX", "EXPLICIT_CONTINUOUS_PHASE_PROPERTIES"),
        Arrays.asList("REYNOLDS_BELOW_200", "SCHMIDT_BELOW_250", "EXTERNAL_FILM_ONLY", "CALLER_DECLARED_MORPHOLOGY",
            "CALLER_DECLARED_RESIDENCE_TIME", "LIQUID_DROPLET_HYDRODYNAMICS_UNSUPPORTED",
            "NO_INTERFACIAL_HEAT_TRANSFER", "NO_INTERNAL_DISPERSED_PHASE_RESISTANCE", "NO_STEFAN_FLOW_CORRECTION",
            "NO_BREAKUP_OR_COALESCENCE", "NO_ANNULAR_JET_MODEL", "NO_SOLID_BEARING_FLOW",
            "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(new ReleaseModelEvidence.Record("ranz-marshall-1952", ReleaseModelEvidence.Type.ANALYTICAL,
            "Ranz, W.E. and Marshall, W.R., Chemical Engineering Progress 48 (1952) 141-146, 173-180",
            "Independent published spherical-particle heat/mass-transfer correlation basis; not release-rate validation",
            true),
            new ReleaseModelEvidence.Record("ranz-marshall-stagnant-limit", ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/RanzMarshallFiniteRateReleaseModelTest.java",
                "Exact Sh=2 stagnant limit, coefficient equations and nearby-property trends", false),
            new ReleaseModelEvidence.Record("component-and-energy-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/test/java/neqsim/process/safety/release/RanzMarshallFiniteRateReleaseModelTest.java",
                "Component reconstruction, uniform-limit and stagnation-energy closure", false)));
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
    if (correlation.getMorphology() != Morphology.GAS_BUBBLES) {
      return ReleaseFlowResult.failure(this, true, "LIQUID_DROPLET_HYDRODYNAMICS_UNSUPPORTED",
          "Ranz-Marshall droplet coefficients are resolved, but this release adapter only has a "
              + "gas-bubble-in-liquid drift-flux closure");
    }
    Set<String> fluidComponents = new TreeSet<String>(Arrays.asList(request.getFluid().getComponentNames()));
    if (!fluidComponents.equals(correlation.getComponentDiffusivitiesM2S().keySet())) {
      return ReleaseFlowResult.failure(this, false, "RANZ_MARSHALL_COMPONENT_BASIS_INVALID",
          "Diffusivity components must exactly match the release-fluid components; fluid=" + fluidComponents
              + ", diffusivities=" + correlation.getComponentDiffusivitiesM2S().keySet());
    }

    ComponentSelectiveFiniteRateReleaseModel delegate = new ComponentSelectiveFiniteRateReleaseModel(surfaceTensionNm,
        correlation.getComponentRelaxationTimesS(), residenceTimeS, correlation.getParameterProvenance());
    ReleaseFlowResult result = delegate.calculate(request);
    if (!result.isUsable()) {
      Diagnostic cause = result.getDiagnostics().get(0);
      return ReleaseFlowResult.failure(this, result.getStatus() == Status.UNSUPPORTED,
          "RANZ_MARSHALL_" + cause.getCode(), cause.getMessage());
    }

    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
        "Ranz-Marshall external-film component transfer for explicit spherical gas bubbles; "
            + "caller-declared continuous-liquid properties, diffusivities, d32, relative velocity, "
            + "residence time and provenance; equilibrium caloric properties and bounded vertical "
            + "drift flux; no interfacial heat/latent kinetics, internal resistance, Stefan-flow "
            + "correction, breakup, coalescence, entrainment, annular jet or solids"));
    diagnostics.add(new Diagnostic("RANZ_MARSHALL_COMPONENT_TRANSFER",
        "morphology=" + correlation.getMorphology() + "; d32=" + correlation.getSauterMeanDiameterM()
            + " m; continuousDensity=" + correlation.getContinuousPhaseDensityKgM3() + " kg/m3; continuousViscosity="
            + correlation.getContinuousPhaseDynamicViscosityPaS() + " Pa s; relativeVelocity="
            + correlation.getRelativeVelocityMs() + " m/s; Reynolds=" + correlation.getReynoldsNumber() + "; Schmidt="
            + correlation.getSchmidtNumbers() + "; Sherwood=" + correlation.getSherwoodNumbers() + "; coefficients="
            + correlation.getMassTransferCoefficientsMs() + " m/s; relaxationTimes="
            + correlation.getComponentRelaxationTimesS() + " s; residenceTime=" + residenceTimeS + " s; provenance="
            + correlation.getParameterProvenance()));
    for (Diagnostic diagnostic : result.getDiagnostics()) {
      if (!"MODEL_ASSUMPTIONS".equals(diagnostic.getCode())
          && !"COMPONENT_SELECTIVE_PHASE_TRANSFER".equals(diagnostic.getCode())) {
        diagnostics.add(diagnostic);
      }
    }
    return ReleaseFlowResult.success(this, result.getMassFlowRateKgS(), result.isChoked(), result.getStations(),
        diagnostics, result.getThroatSoundSpeedMs(), result.getStatus() == Status.VALID_WITH_WARNINGS);
  }

  /** @return caller-declared interfacial tension in N/m */
  public double getSurfaceTensionNm() {
    return surfaceTensionNm;
  }

  /** @return caller-declared residence time in s */
  public double getResidenceTimeS() {
    return residenceTimeS;
  }

  /** @return immutable Ranz-Marshall correlation */
  public RanzMarshallMassTransferCorrelation getCorrelation() {
    return correlation;
  }
}
