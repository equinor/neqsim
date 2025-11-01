package neqsim.process.equipment.flare.dto;

import java.io.Serializable;

/**
 * DTO describing utilization of a flare against its configured design capacities.
 */
public class FlareCapacityDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double heatDutyW;
  private final double designHeatDutyW;
  private final double heatUtilization;
  private final double massRateKgS;
  private final double designMassRateKgS;
  private final double massUtilization;
  private final double molarRateMoleS;
  private final double designMolarRateMoleS;
  private final double molarUtilization;
  private final boolean overloaded;

  public FlareCapacityDTO(double heatDutyW, double designHeatDutyW, double heatUtilization,
      double massRateKgS, double designMassRateKgS, double massUtilization, double molarRateMoleS,
      double designMolarRateMoleS, double molarUtilization, boolean overloaded) {
    this.heatDutyW = heatDutyW;
    this.designHeatDutyW = designHeatDutyW;
    this.heatUtilization = heatUtilization;
    this.massRateKgS = massRateKgS;
    this.designMassRateKgS = designMassRateKgS;
    this.massUtilization = massUtilization;
    this.molarRateMoleS = molarRateMoleS;
    this.designMolarRateMoleS = designMolarRateMoleS;
    this.molarUtilization = molarUtilization;
    this.overloaded = overloaded;
  }

  public double getHeatDutyW() {
    return heatDutyW;
  }

  public double getDesignHeatDutyW() {
    return designHeatDutyW;
  }

  public double getHeatUtilization() {
    return heatUtilization;
  }

  public double getMassRateKgS() {
    return massRateKgS;
  }

  public double getDesignMassRateKgS() {
    return designMassRateKgS;
  }

  public double getMassUtilization() {
    return massUtilization;
  }

  public double getMolarRateMoleS() {
    return molarRateMoleS;
  }

  public double getDesignMolarRateMoleS() {
    return designMolarRateMoleS;
  }

  public double getMolarUtilization() {
    return molarUtilization;
  }

  public boolean isOverloaded() {
    return overloaded;
  }
}
