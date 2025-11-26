package neqsim.process.equipment.flare.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * DTO encapsulating emission, radiation, dispersion and capacity responses for a flare.
 */
public class FlarePerformanceDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String label;
  private final double heatDutyW;
  private final double massRateKgS;
  private final double molarRateMoleS;
  private final double co2EmissionKgS;
  private final double heatFluxAt30mWm2;
  private final double distanceTo4kWm2;
  private final FlareDispersionSurrogateDTO dispersion;
  private final Map<String, Double> emissions;
  private final FlareCapacityDTO capacity;

  public FlarePerformanceDTO(String label, double heatDutyW, double massRateKgS,
      double molarRateMoleS, double co2EmissionKgS, double heatFluxAt30mWm2,
      double distanceTo4kWm2, FlareDispersionSurrogateDTO dispersion,
      Map<String, Double> emissions, FlareCapacityDTO capacity) {
    this.label = label;
    this.heatDutyW = heatDutyW;
    this.massRateKgS = massRateKgS;
    this.molarRateMoleS = molarRateMoleS;
    this.co2EmissionKgS = co2EmissionKgS;
    this.heatFluxAt30mWm2 = heatFluxAt30mWm2;
    this.distanceTo4kWm2 = distanceTo4kWm2;
    this.dispersion = dispersion;
    this.emissions = emissions == null ? Collections.emptyMap() : Collections.unmodifiableMap(emissions);
    this.capacity = capacity;
  }

  public String getLabel() {
    return label;
  }

  public double getHeatDutyW() {
    return heatDutyW;
  }

  public double getHeatDutyMW() {
    return heatDutyW * 1.0e-6;
  }

  public double getMassRateKgS() {
    return massRateKgS;
  }

  public double getMolarRateMoleS() {
    return molarRateMoleS;
  }

  public double getCo2EmissionKgS() {
    return co2EmissionKgS;
  }

  public double getCo2EmissionTonPerDay() {
    return co2EmissionKgS * 86400.0 / 1000.0;
  }

  public double getHeatFluxAt30mWm2() {
    return heatFluxAt30mWm2;
  }

  public double getDistanceTo4kWm2() {
    return distanceTo4kWm2;
  }

  public FlareDispersionSurrogateDTO getDispersion() {
    return dispersion;
  }

  public Map<String, Double> getEmissions() {
    return emissions;
  }

  public FlareCapacityDTO getCapacity() {
    return capacity;
  }

  public boolean isOverloaded() {
    return capacity != null && capacity.isOverloaded();
  }
}
