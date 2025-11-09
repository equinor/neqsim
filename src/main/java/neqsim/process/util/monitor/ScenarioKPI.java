package neqsim.process.util.monitor;

/**
 * Key Performance Indicators for scenario execution.
 * 
 * <p>
 * Captures critical metrics across safety, process, environmental, and economic dimensions to
 * enable comprehensive scenario comparison and analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScenarioKPI {
  // Safety Performance Metrics
  private double timeToESDActivation = -1.0; // seconds (-1 = not activated)
  private double peakPressure = 0.0; // bara
  private double peakTemperature = 0.0; // °C
  private double safetyMarginToMAWP = 0.0; // bara
  private int safetySystemActuations = 0;
  private boolean psvActivated = false;
  private boolean hippsTripped = false;

  // Process Performance Metrics
  private double productionLoss = 0.0; // kg
  private double recoveryTime = 0.0; // seconds
  private double steadyStateDeviation = 0.0; // %
  private double averageFlowRate = 0.0; // kg/hr

  // Environmental Impact Metrics
  private double flareGasVolume = 0.0; // Nm³
  private double co2Emissions = 0.0; // kg
  private double flaringDuration = 0.0; // seconds
  private double ventedMass = 0.0; // kg

  // Economic Impact Metrics
  private double lostProductionValue = 0.0; // USD
  private double operatingCost = 0.0; // USD
  private double energyConsumption = 0.0; // kWh

  // Operational Metrics
  private double simulationDuration = 0.0; // seconds
  private int errorCount = 0;
  private int warningCount = 0;
  private String finalStatus = "UNKNOWN";

  /**
   * Builder for ScenarioKPI.
   */
  public static class Builder {
    private final ScenarioKPI kpi = new ScenarioKPI();

    public Builder timeToESDActivation(double time) {
      kpi.timeToESDActivation = time;
      return this;
    }

    public Builder peakPressure(double pressure) {
      kpi.peakPressure = pressure;
      return this;
    }

    public Builder peakTemperature(double temperature) {
      kpi.peakTemperature = temperature;
      return this;
    }

    public Builder safetyMarginToMAWP(double margin) {
      kpi.safetyMarginToMAWP = margin;
      return this;
    }

    public Builder safetySystemActuations(int count) {
      kpi.safetySystemActuations = count;
      return this;
    }

    public Builder psvActivated(boolean activated) {
      kpi.psvActivated = activated;
      return this;
    }

    public Builder hippsTripped(boolean tripped) {
      kpi.hippsTripped = tripped;
      return this;
    }

    public Builder productionLoss(double loss) {
      kpi.productionLoss = loss;
      return this;
    }

    public Builder recoveryTime(double time) {
      kpi.recoveryTime = time;
      return this;
    }

    public Builder steadyStateDeviation(double deviation) {
      kpi.steadyStateDeviation = deviation;
      return this;
    }

    public Builder averageFlowRate(double flowRate) {
      kpi.averageFlowRate = flowRate;
      return this;
    }

    public Builder flareGasVolume(double volume) {
      kpi.flareGasVolume = volume;
      return this;
    }

    public Builder co2Emissions(double emissions) {
      kpi.co2Emissions = emissions;
      return this;
    }

    public Builder flaringDuration(double duration) {
      kpi.flaringDuration = duration;
      return this;
    }

    public Builder ventedMass(double mass) {
      kpi.ventedMass = mass;
      return this;
    }

    public Builder lostProductionValue(double value) {
      kpi.lostProductionValue = value;
      return this;
    }

    public Builder operatingCost(double cost) {
      kpi.operatingCost = cost;
      return this;
    }

    public Builder energyConsumption(double energy) {
      kpi.energyConsumption = energy;
      return this;
    }

    public Builder simulationDuration(double duration) {
      kpi.simulationDuration = duration;
      return this;
    }

    public Builder errorCount(int count) {
      kpi.errorCount = count;
      return this;
    }

    public Builder warningCount(int count) {
      kpi.warningCount = count;
      return this;
    }

    public Builder finalStatus(String status) {
      kpi.finalStatus = status;
      return this;
    }

    public ScenarioKPI build() {
      return kpi;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public double getTimeToESDActivation() {
    return timeToESDActivation;
  }

  public double getPeakPressure() {
    return peakPressure;
  }

  public double getPeakTemperature() {
    return peakTemperature;
  }

  public double getSafetyMarginToMAWP() {
    return safetyMarginToMAWP;
  }

  public int getSafetySystemActuations() {
    return safetySystemActuations;
  }

  public boolean isPsvActivated() {
    return psvActivated;
  }

  public boolean isHippsTripped() {
    return hippsTripped;
  }

  public double getProductionLoss() {
    return productionLoss;
  }

  public double getRecoveryTime() {
    return recoveryTime;
  }

  public double getSteadyStateDeviation() {
    return steadyStateDeviation;
  }

  public double getAverageFlowRate() {
    return averageFlowRate;
  }

  public double getFlareGasVolume() {
    return flareGasVolume;
  }

  public double getCo2Emissions() {
    return co2Emissions;
  }

  public double getFlaringDuration() {
    return flaringDuration;
  }

  public double getVentedMass() {
    return ventedMass;
  }

  public double getLostProductionValue() {
    return lostProductionValue;
  }

  public double getOperatingCost() {
    return operatingCost;
  }

  public double getEnergyConsumption() {
    return energyConsumption;
  }

  public double getSimulationDuration() {
    return simulationDuration;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public int getWarningCount() {
    return warningCount;
  }

  public String getFinalStatus() {
    return finalStatus;
  }

  /**
   * Calculates overall safety score (0-100, higher is better).
   */
  public double calculateSafetyScore() {
    double score = 100.0;

    // Deduct points for safety system activations
    if (hippsTripped)
      score -= 15.0;
    if (psvActivated)
      score -= 25.0;
    score -= Math.min(safetySystemActuations * 5.0, 30.0);

    // Deduct points based on safety margin
    if (safetyMarginToMAWP < 5.0)
      score -= 20.0;
    else if (safetyMarginToMAWP < 10.0)
      score -= 10.0;

    return Math.max(0.0, score);
  }

  /**
   * Calculates environmental impact score (0-100, higher is better = less impact).
   */
  public double calculateEnvironmentalScore() {
    double score = 100.0;

    // Deduct points for flaring
    score -= Math.min(flareGasVolume / 10.0, 40.0); // Up to 400 Nm³ = -40 points
    score -= Math.min(co2Emissions / 100.0, 30.0); // Up to 3000 kg = -30 points
    score -= Math.min(flaringDuration / 60.0, 20.0); // Up to 60s = -20 points

    return Math.max(0.0, score);
  }

  /**
   * Calculates process performance score (0-100, higher is better).
   */
  public double calculateProcessScore() {
    double score = 100.0;

    // Deduct points for production loss
    score -= Math.min(productionLoss / 1000.0, 40.0); // Up to 1000 kg = -40 points
    score -= Math.min(recoveryTime / 100.0, 30.0); // Up to 100s = -30 points
    score -= Math.min(steadyStateDeviation, 20.0); // Up to 20% = -20 points

    return Math.max(0.0, score);
  }

  /**
   * Calculates overall composite score.
   */
  public double calculateOverallScore() {
    return (calculateSafetyScore() * 0.5 + calculateEnvironmentalScore() * 0.25
        + calculateProcessScore() * 0.25);
  }
}
