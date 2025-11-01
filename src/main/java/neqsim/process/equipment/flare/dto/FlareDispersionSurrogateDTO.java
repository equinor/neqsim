package neqsim.process.equipment.flare.dto;

import java.io.Serializable;

/**
 * DTO describing surrogate parameters used for dispersion screening of flare releases.
 */
public class FlareDispersionSurrogateDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double massRateKgS;
  private final double molarRateMoleS;
  private final double exitVelocityMs;
  private final double momentumFlux;
  private final double momentumPerMass;
  private final double standardVolumeSm3PerSec;

  public FlareDispersionSurrogateDTO(double massRateKgS, double molarRateMoleS,
      double exitVelocityMs, double momentumFlux, double momentumPerMass,
      double standardVolumeSm3PerSec) {
    this.massRateKgS = massRateKgS;
    this.molarRateMoleS = molarRateMoleS;
    this.exitVelocityMs = exitVelocityMs;
    this.momentumFlux = momentumFlux;
    this.momentumPerMass = momentumPerMass;
    this.standardVolumeSm3PerSec = standardVolumeSm3PerSec;
  }

  public double getMassRateKgS() {
    return massRateKgS;
  }

  public double getMolarRateMoleS() {
    return molarRateMoleS;
  }

  public double getExitVelocityMs() {
    return exitVelocityMs;
  }

  public double getMomentumFlux() {
    return momentumFlux;
  }

  public double getMomentumPerMass() {
    return momentumPerMass;
  }

  public double getStandardVolumeSm3PerSec() {
    return standardVolumeSm3PerSec;
  }
}
