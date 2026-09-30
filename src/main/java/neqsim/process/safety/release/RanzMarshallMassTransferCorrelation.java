package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable Ranz-Marshall external-film mass-transfer correlation for spherical dispersions.
 *
 * <p>
 * The caller supplies the Sauter mean diameter, continuous-phase density and dynamic viscosity, relative velocity, and
 * one continuous-phase diffusivity for every component. The correlation evaluates
 * {@code Sh_i = 2 + 0.6 sqrt(Re) cbrt(Sc_i)}, {@code k_i = Sh_i D_i / d32}, and the external-film relaxation time
 * {@code tau_i = d32 / (6 k_i)}. No property, morphology, or component value is inferred.
 *
 * <p>
 * This object evaluates the correlation for either gas bubbles or liquid droplets. A release-flow adapter must still
 * supply a morphology-compatible hydrodynamic closure. The documented range is {@code 0 <= Re < 200} and
 * {@code 0 < Sc_i < 250}; values outside that range remain available for diagnostics but are not supported for release
 * calculations.
 *
 * @author esol
 * @version 1.0
 */
public final class RanzMarshallMassTransferCorrelation implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Explicit dispersed-phase morphology. */
  public enum Morphology {
    /** Spherical gas bubbles dispersed in a continuous liquid phase. */
    GAS_BUBBLES,
    /** Spherical liquid droplets dispersed in a continuous gas phase. */
    LIQUID_DROPLETS
  }

  private static final double MAX_REYNOLDS_EXCLUSIVE = 200.0;
  private static final double MAX_SCHMIDT_EXCLUSIVE = 250.0;

  private final Morphology morphology;
  private final double sauterMeanDiameterM;
  private final double continuousPhaseDensityKgM3;
  private final double continuousPhaseDynamicViscosityPaS;
  private final double relativeVelocityMs;
  private final Map<String, Double> componentDiffusivitiesM2S;
  private final String parameterProvenance;
  private final double reynoldsNumber;
  private final Map<String, Double> schmidtNumbers;
  private final Map<String, Double> sherwoodNumbers;
  private final Map<String, Double> massTransferCoefficientsMs;
  private final Map<String, Double> componentRelaxationTimesS;

  /**
   * Creates an explicit spherical-dispersion mass-transfer correlation.
   *
   * @param morphology explicit gas-bubble or liquid-droplet morphology
   * @param sauterMeanDiameterM caller-declared Sauter mean diameter d32 in m
   * @param continuousPhaseDensityKgM3 continuous-phase density in kg/m3
   * @param continuousPhaseDynamicViscosityPaS continuous-phase dynamic viscosity in Pa s
   * @param relativeVelocityMs dispersed-to-continuous relative speed magnitude in m/s
   * @param componentDiffusivitiesM2S continuous-phase diffusivity in m2/s for every component
   * @param parameterProvenance nonempty source or calibration identity for all supplied inputs
   */
  public RanzMarshallMassTransferCorrelation(Morphology morphology, double sauterMeanDiameterM,
      double continuousPhaseDensityKgM3, double continuousPhaseDynamicViscosityPaS, double relativeVelocityMs,
      Map<String, Double> componentDiffusivitiesM2S, String parameterProvenance) {
    if (morphology == null) {
      throw new IllegalArgumentException("Dispersed-phase morphology required");
    }
    this.morphology = morphology;
    this.sauterMeanDiameterM = ReleaseFlowRequest.positive(sauterMeanDiameterM, "sauterMeanDiameterM");
    this.continuousPhaseDensityKgM3 = ReleaseFlowRequest.positive(continuousPhaseDensityKgM3,
        "continuousPhaseDensityKgM3");
    this.continuousPhaseDynamicViscosityPaS = ReleaseFlowRequest.positive(continuousPhaseDynamicViscosityPaS,
        "continuousPhaseDynamicViscosityPaS");
    this.relativeVelocityMs = ReleaseFlowRequest.nonnegative(relativeVelocityMs, "relativeVelocityMs");
    if (componentDiffusivitiesM2S == null || componentDiffusivitiesM2S.isEmpty()) {
      throw new IllegalArgumentException("Component diffusivities required");
    }
    if (parameterProvenance == null || parameterProvenance.trim().isEmpty()) {
      throw new IllegalArgumentException("Parameter provenance required");
    }
    this.parameterProvenance = parameterProvenance;

    Map<String, Double> diffusivities = new TreeMap<String, Double>();
    Map<String, Double> schmidt = new TreeMap<String, Double>();
    Map<String, Double> sherwood = new TreeMap<String, Double>();
    Map<String, Double> coefficients = new TreeMap<String, Double>();
    Map<String, Double> relaxationTimes = new TreeMap<String, Double>();
    double reynolds = continuousPhaseDensityKgM3 * relativeVelocityMs * sauterMeanDiameterM
        / continuousPhaseDynamicViscosityPaS;
    if (!Double.isFinite(reynolds) || reynolds < 0.0) {
      throw new IllegalArgumentException("Reynolds number must be finite and nonnegative");
    }
    this.reynoldsNumber = reynolds;
    for (Map.Entry<String, Double> entry : componentDiffusivitiesM2S.entrySet()) {
      if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
        throw new IllegalArgumentException("Component name required");
      }
      double diffusivity = ReleaseFlowRequest.positive(entry.getValue(), "component diffusivity");
      double componentSchmidt = continuousPhaseDynamicViscosityPaS / (continuousPhaseDensityKgM3 * diffusivity);
      double componentSherwood = 2.0 + 0.6 * Math.sqrt(reynoldsNumber) * Math.cbrt(componentSchmidt);
      double coefficient = componentSherwood * diffusivity / sauterMeanDiameterM;
      double relaxationTime = sauterMeanDiameterM / (6.0 * coefficient);
      requireDerived(componentSchmidt, "Schmidt number");
      requireDerived(componentSherwood, "Sherwood number");
      requireDerived(coefficient, "mass-transfer coefficient");
      requireDerived(relaxationTime, "component relaxation time");
      diffusivities.put(entry.getKey(), diffusivity);
      schmidt.put(entry.getKey(), componentSchmidt);
      sherwood.put(entry.getKey(), componentSherwood);
      coefficients.put(entry.getKey(), coefficient);
      relaxationTimes.put(entry.getKey(), relaxationTime);
    }
    this.componentDiffusivitiesM2S = immutable(diffusivities);
    this.schmidtNumbers = immutable(schmidt);
    this.sherwoodNumbers = immutable(sherwood);
    this.massTransferCoefficientsMs = immutable(coefficients);
    this.componentRelaxationTimesS = immutable(relaxationTimes);
  }

  /**
   * Requires a finite positive derived correlation value.
   *
   * @param value derived value to validate
   * @param name diagnostic quantity name
   * @throws IllegalArgumentException if the value is non-finite or non-positive
   */
  private static void requireDerived(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Copies numeric component values into an immutable deterministic map.
   *
   * @param values component values to copy
   * @return immutable component-name-sorted map
   */
  private static Map<String, Double> immutable(Map<String, Double> values) {
    return Collections.unmodifiableMap(new TreeMap<String, Double>(values));
  }

  /** @return true only inside the documented Reynolds and all component Schmidt ranges */
  public boolean isWithinApplicability() {
    if (reynoldsNumber >= MAX_REYNOLDS_EXCLUSIVE) {
      return false;
    }
    for (double schmidt : schmidtNumbers.values()) {
      if (schmidt >= MAX_SCHMIDT_EXCLUSIVE) {
        return false;
      }
    }
    return true;
  }

  /** @return deterministic detail for the first applicability violation, or {@code SUPPORTED} */
  public String getApplicabilityMessage() {
    if (reynoldsNumber >= MAX_REYNOLDS_EXCLUSIVE) {
      return "Reynolds number " + reynoldsNumber + " is outside 0 <= Re < 200";
    }
    for (Map.Entry<String, Double> entry : schmidtNumbers.entrySet()) {
      if (entry.getValue() >= MAX_SCHMIDT_EXCLUSIVE) {
        return "Schmidt number for " + entry.getKey() + " is " + entry.getValue() + " and outside 0 < Sc < 250";
      }
    }
    return "SUPPORTED";
  }

  /** @return explicit dispersed-phase morphology */
  public Morphology getMorphology() {
    return morphology;
  }

  /** @return caller-declared Sauter mean diameter in m */
  public double getSauterMeanDiameterM() {
    return sauterMeanDiameterM;
  }

  /** @return caller-declared continuous-phase density in kg/m3 */
  public double getContinuousPhaseDensityKgM3() {
    return continuousPhaseDensityKgM3;
  }

  /** @return caller-declared continuous-phase dynamic viscosity in Pa s */
  public double getContinuousPhaseDynamicViscosityPaS() {
    return continuousPhaseDynamicViscosityPaS;
  }

  /** @return caller-declared relative speed magnitude in m/s */
  public double getRelativeVelocityMs() {
    return relativeVelocityMs;
  }

  /** @return Reynolds number based on d32 and the continuous phase */
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /** @return immutable sorted continuous-phase component diffusivities in m2/s */
  public Map<String, Double> getComponentDiffusivitiesM2S() {
    return immutable(componentDiffusivitiesM2S);
  }

  /** @return immutable sorted component Schmidt numbers */
  public Map<String, Double> getSchmidtNumbers() {
    return immutable(schmidtNumbers);
  }

  /** @return immutable sorted component Sherwood numbers */
  public Map<String, Double> getSherwoodNumbers() {
    return immutable(sherwoodNumbers);
  }

  /** @return immutable sorted external-film mass-transfer coefficients in m/s */
  public Map<String, Double> getMassTransferCoefficientsMs() {
    return immutable(massTransferCoefficientsMs);
  }

  /** @return immutable sorted external-film component relaxation times in s */
  public Map<String, Double> getComponentRelaxationTimesS() {
    return immutable(componentRelaxationTimesS);
  }

  /** @return caller-declared parameter source or calibration identity */
  public String getParameterProvenance() {
    return parameterProvenance;
  }
}
