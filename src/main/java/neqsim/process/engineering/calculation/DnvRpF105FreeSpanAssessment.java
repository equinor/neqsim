package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable first-mode and dimensionless free-span screening result. */
public final class DnvRpF105FreeSpanAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String standardEdition;
  private final double secondMomentOfAreaM4;
  private final double eulerCriticalCompressionN;
  private final double spanToHydrodynamicDiameter;
  private final double fundamentalNaturalFrequencyHz;
  private final double currentVortexSheddingFrequencyHz;
  private final double currentFrequencyRatio;
  private final double currentReducedVelocity;
  private final double waveFrequencyHz;
  private final double waveFrequencyRatio;
  private final double waveReducedVelocity;
  private final double keuleganCarpenterNumber;
  private final List<String> detailedResponseTriggers;
  private final boolean detailedResponseAssessmentRequired;

  DnvRpF105FreeSpanAssessment(DnvRpF105FreeSpanScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    secondMomentOfAreaM4 = DnvRpF105FreeSpanScreeningKernel.secondMomentOfArea(input);
    eulerCriticalCompressionN = Math.PI * Math.PI * input.getYoungsModulusPa() * secondMomentOfAreaM4
        / (input.getSpanLengthM() * input.getSpanLengthM());
    spanToHydrodynamicDiameter = input.getSpanLengthM() / input.getHydrodynamicDiameterM();
    double waveNumber = Math.PI / input.getSpanLengthM();
    double omegaSquared = (input.getYoungsModulusPa() * secondMomentOfAreaM4 * Math.pow(waveNumber, 4.0)
        + input.getEffectiveAxialForceN() * waveNumber * waveNumber) / input.getEffectiveMassPerLengthKgPerM();
    fundamentalNaturalFrequencyHz = Math.sqrt(omegaSquared) / (2.0 * Math.PI);
    currentVortexSheddingFrequencyHz = input.getStrouhalNumber() * input.getCurrentVelocityMPerS()
        / input.getHydrodynamicDiameterM();
    currentFrequencyRatio = currentVortexSheddingFrequencyHz / fundamentalNaturalFrequencyHz;
    currentReducedVelocity = input.getCurrentVelocityMPerS()
        / (fundamentalNaturalFrequencyHz * input.getHydrodynamicDiameterM());
    if (input.getWaveOrbitalVelocityAmplitudeMPerS() > 0.0) {
      waveFrequencyHz = 1.0 / input.getWavePeriodS();
      waveFrequencyRatio = waveFrequencyHz / fundamentalNaturalFrequencyHz;
      waveReducedVelocity = input.getWaveOrbitalVelocityAmplitudeMPerS()
          / (fundamentalNaturalFrequencyHz * input.getHydrodynamicDiameterM());
      keuleganCarpenterNumber = input.getWaveOrbitalVelocityAmplitudeMPerS() * input.getWavePeriodS()
          / input.getHydrodynamicDiameterM();
    } else {
      waveFrequencyHz = 0.0;
      waveFrequencyRatio = 0.0;
      waveReducedVelocity = 0.0;
      keuleganCarpenterNumber = 0.0;
    }
    List<String> triggers = new ArrayList<String>();
    if (input.getCurrentVelocityMPerS() > 0.0 && currentFrequencyRatio >= input.getLockInFrequencyRatioLower()
        && currentFrequencyRatio <= input.getLockInFrequencyRatioUpper()) {
      triggers.add("CURRENT_FREQUENCY_RATIO_BAND");
    }
    if (currentReducedVelocity > input.getMaxCurrentReducedVelocityForScreening()) {
      triggers.add("CURRENT_REDUCED_VELOCITY");
    }
    if (waveReducedVelocity > input.getMaxWaveReducedVelocityForScreening()) {
      triggers.add("WAVE_REDUCED_VELOCITY");
    }
    detailedResponseTriggers = Collections.unmodifiableList(triggers);
    detailedResponseAssessmentRequired = !detailedResponseTriggers.isEmpty();
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return steel cross-section second moment of area in m4 */
  public double getSecondMomentOfAreaM4() {
    return secondMomentOfAreaM4;
  }

  /** @return simply supported Euler critical compression magnitude in N */
  public double getEulerCriticalCompressionN() {
    return eulerCriticalCompressionN;
  }

  /** @return span length divided by hydrodynamic diameter */
  public double getSpanToHydrodynamicDiameter() {
    return spanToHydrodynamicDiameter;
  }

  /** @return simply supported first-mode natural frequency in Hz */
  public double getFundamentalNaturalFrequencyHz() {
    return fundamentalNaturalFrequencyHz;
  }

  /** @return current vortex-shedding frequency in Hz */
  public double getCurrentVortexSheddingFrequencyHz() {
    return currentVortexSheddingFrequencyHz;
  }

  /** @return current vortex-shedding frequency divided by natural frequency */
  public double getCurrentFrequencyRatio() {
    return currentFrequencyRatio;
  }

  /** @return current reduced velocity */
  public double getCurrentReducedVelocity() {
    return currentReducedVelocity;
  }

  /** @return inverse wave period in Hz, or zero without wave input */
  public double getWaveFrequencyHz() {
    return waveFrequencyHz;
  }

  /** @return wave frequency divided by natural frequency */
  public double getWaveFrequencyRatio() {
    return waveFrequencyRatio;
  }

  /** @return wave reduced velocity, or zero without wave input */
  public double getWaveReducedVelocity() {
    return waveReducedVelocity;
  }

  /** @return Keulegan-Carpenter number, or zero without wave input */
  public double getKeuleganCarpenterNumber() {
    return keuleganCarpenterNumber;
  }

  /** @return immutable project-controlled detailed-response trigger identifiers */
  public List<String> getDetailedResponseTriggers() {
    return Collections.unmodifiableList(new ArrayList<String>(detailedResponseTriggers));
  }

  /** @return whether any caller-controlled response trigger was reached */
  public boolean isDetailedResponseAssessmentRequired() {
    return detailedResponseAssessmentRequired;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("structuralModel", "SIMPLY_SUPPORTED_EULER_BERNOULLI_FIRST_MODE");
    result.put("secondMomentOfAreaM4", Double.valueOf(secondMomentOfAreaM4));
    result.put("eulerCriticalCompressionN", Double.valueOf(eulerCriticalCompressionN));
    result.put("spanToHydrodynamicDiameter", Double.valueOf(spanToHydrodynamicDiameter));
    result.put("fundamentalNaturalFrequencyHz", Double.valueOf(fundamentalNaturalFrequencyHz));
    result.put("currentVortexSheddingFrequencyHz", Double.valueOf(currentVortexSheddingFrequencyHz));
    result.put("currentFrequencyRatio", Double.valueOf(currentFrequencyRatio));
    result.put("currentReducedVelocity", Double.valueOf(currentReducedVelocity));
    result.put("waveFrequencyHz", Double.valueOf(waveFrequencyHz));
    result.put("waveFrequencyRatio", Double.valueOf(waveFrequencyRatio));
    result.put("waveReducedVelocity", Double.valueOf(waveReducedVelocity));
    result.put("keuleganCarpenterNumber", Double.valueOf(keuleganCarpenterNumber));
    result.put("detailedResponseTriggers", getDetailedResponseTriggers());
    result.put("detailedResponseAssessmentRequired", Boolean.valueOf(detailedResponseAssessmentRequired));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
