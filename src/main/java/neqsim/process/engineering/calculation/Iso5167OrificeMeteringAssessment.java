package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot of an ISO 5167-2 concentric orifice-plate flow calculation. */
public final class Iso5167OrificeMeteringAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String standardEdition;
  private final String companionStandardEdition;
  private final String serviceType;
  private final String tapType;
  private final double betaRatio;
  private final double differentialPressurePa;
  private final double pressureRatio;
  private final double dischargeCoefficient;
  private final double expansibilityFactor;
  private final double velocityOfApproachFactor;
  private final double massFlowRateKgPerS;
  private final double actualVolumeFlowRateM3PerS;
  private final double pipeReynoldsNumber;
  private final double permanentPressureLossPa;
  private final int iterations;

  Iso5167OrificeMeteringAssessment(Iso5167OrificeMeteringKernel.Input input,
      Iso5167OrificeMeteringKernel.Computation computation) {
    standardEdition = input.getEdition().getDisplayName();
    companionStandardEdition = "ISO-5167-1 2022";
    serviceType = input.getServiceType().name();
    tapType = input.getTapType().name();
    betaRatio = computation.betaRatio;
    differentialPressurePa = computation.differentialPressurePa;
    pressureRatio = computation.pressureRatio;
    dischargeCoefficient = computation.dischargeCoefficient;
    expansibilityFactor = computation.expansibilityFactor;
    velocityOfApproachFactor = computation.velocityOfApproachFactor;
    massFlowRateKgPerS = computation.massFlowRateKgPerS;
    actualVolumeFlowRateM3PerS = computation.actualVolumeFlowRateM3PerS;
    pipeReynoldsNumber = computation.pipeReynoldsNumber;
    permanentPressureLossPa = computation.permanentPressureLossPa;
    iterations = computation.iterations;
  }

  /** @return explicit Part 2 standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return paired general-principles edition */
  public String getCompanionStandardEdition() {
    return companionStandardEdition;
  }

  /** @return fluid service enum name */
  public String getServiceType() {
    return serviceType;
  }

  /** @return pressure-tapping enum name */
  public String getTapType() {
    return tapType;
  }

  /** @return orifice-to-pipe diameter ratio */
  public double getBetaRatio() {
    return betaRatio;
  }

  /** @return upstream-to-downstream differential pressure in pascals */
  public double getDifferentialPressurePa() {
    return differentialPressurePa;
  }

  /** @return downstream-to-upstream absolute pressure ratio */
  public double getPressureRatio() {
    return pressureRatio;
  }

  /** @return Reader-Harris/Gallagher discharge coefficient */
  public double getDischargeCoefficient() {
    return dischargeCoefficient;
  }

  /** @return expansibility factor; exactly one for liquid service */
  public double getExpansibilityFactor() {
    return expansibilityFactor;
  }

  /** @return velocity-of-approach factor */
  public double getVelocityOfApproachFactor() {
    return velocityOfApproachFactor;
  }

  /** @return calculated mass flow in kg/s */
  public double getMassFlowRateKgPerS() {
    return massFlowRateKgPerS;
  }

  /** @return actual upstream volumetric flow in m3/s */
  public double getActualVolumeFlowRateM3PerS() {
    return actualVolumeFlowRateM3PerS;
  }

  /** @return Reynolds number based on upstream pipe diameter */
  public double getPipeReynoldsNumber() {
    return pipeReynoldsNumber;
  }

  /** @return estimated permanent pressure loss in pascals */
  public double getPermanentPressureLossPa() {
    return permanentPressureLossPa;
  }

  /** @return fixed-point iteration count */
  public int getIterations() {
    return iterations;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("companionStandardEdition", companionStandardEdition);
    result.put("serviceType", serviceType);
    result.put("tapType", tapType);
    result.put("betaRatio", Double.valueOf(betaRatio));
    result.put("differentialPressurePa", Double.valueOf(differentialPressurePa));
    result.put("pressureRatio", Double.valueOf(pressureRatio));
    result.put("dischargeCoefficient", Double.valueOf(dischargeCoefficient));
    result.put("expansibilityFactor", Double.valueOf(expansibilityFactor));
    result.put("velocityOfApproachFactor", Double.valueOf(velocityOfApproachFactor));
    result.put("massFlowRateKgPerS", Double.valueOf(massFlowRateKgPerS));
    result.put("actualVolumeFlowRateM3PerS", Double.valueOf(actualVolumeFlowRateM3PerS));
    result.put("pipeReynoldsNumber", Double.valueOf(pipeReynoldsNumber));
    result.put("permanentPressureLossPa", Double.valueOf(permanentPressureLossPa));
    result.put("iterations", Integer.valueOf(iterations));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
