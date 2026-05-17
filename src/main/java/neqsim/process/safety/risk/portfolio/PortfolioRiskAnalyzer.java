package neqsim.process.safety.risk.portfolio;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.google.gson.GsonBuilder;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.OperationalRiskResult;
import neqsim.process.safety.risk.OperationalRiskSimulator;

/**
 * Portfolio Risk Analyzer for multi-asset analysis.
 *
 * <p>
 * Performs Monte Carlo simulation across multiple assets to analyze portfolio-level risk, including
 * common cause failures, correlated events, and aggregated production impact. Essential for
 * corporate risk management and insurance/contract negotiations.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PortfolioRiskAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Analyzer name. */
  private String name;

  /** Assets in portfolio. */
  private List<Asset> assets;

  /** Common cause scenarios. */
  private List<CommonCauseScenario> commonCauseScenarios;

  /** Number of simulations. */
  private int numberOfSimulations = 10000;

  /** Simulation period in years. */
  private double simulationPeriodYears = 1.0;

  /** Random number generator. */
  private Random random;

  /** Results of last simulation. */
  private PortfolioRiskResult lastResult;

  /**
   * Asset in the portfolio.
   */
  public static class Asset implements Serializable {
    private static final long serialVersionUID = 1L;

    private String assetId;
    private String assetName;
    private String region;
    private String assetType;
    private double maxProduction; // boe/day
    private double insuranceValue; // USD
    private transient ProcessSystem processSystem;
    private transient OperationalRiskSimulator riskSimulator;
    private double systemAvailability;
    private double expectedProductionLoss;

    public Asset(String id, String name, double maxProduction) {
      this.assetId = id;
      this.assetName = name;
      this.maxProduction = maxProduction;
      this.systemAvailability = 0.95; // Default
    }

    public void setProcessSystem(ProcessSystem system) {
      this.processSystem = system;
      this.riskSimulator = new OperationalRiskSimulator(system);
    }

    /**
     * Runs risk simulation for this asset.
     *
     * @param iterations number of Monte Carlo iterations
     */
    public void runRiskSimulation(int iterations) {
      if (riskSimulator != null) {
        // Run simulation for 365 days (1 year)
        OperationalRiskResult result = riskSimulator.runSimulation(iterations, 365.0);
        // Availability is returned as percentage (0-100), convert to fraction (0-1)
        this.systemAvailability = result.getAvailability() / 100.0;
        this.expectedProductionLoss = result.getExpectedProductionLoss();
      }
    }

    public String getAssetId() {
      return assetId;
    }

    public String getAssetName() {
      return assetName;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getAssetType() {
      return assetType;
    }

    public void setAssetType(String type) {
      this.assetType = type;
    }

    public double getMaxProduction() {
      return maxProduction;
    }

    public double getInsuranceValue() {
      return insuranceValue;
    }

    public void setInsuranceValue(double value) {
      this.insuranceValue = value;
    }

    public double getSystemAvailability() {
      return systemAvailability;
    }

    public void setSystemAvailability(double availability) {
      this.systemAvailability = availability;
    }

    public double getExpectedProductionLoss() {
      return expectedProductionLoss;
    }

    public void setExpectedProductionLoss(double loss) {
      this.expectedProductionLoss = loss;
    }

    public double getExpectedProduction() {
      return maxProduction * systemAvailability;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("assetId", assetId);
      map.put("assetName", assetName);
      map.put("region", region);
      map.put("assetType", assetType);
      map.put("maxProduction", maxProduction);
      map.put("insuranceValue", insuranceValue);
      map.put("systemAvailability", systemAvailability);
      map.put("expectedProductionLoss", expectedProductionLoss);
      map.put("expectedProduction", getExpectedProduction());
      return map;
    }
  }

  /**
   * Common cause scenario affecting multiple assets.
   */
  public static class CommonCauseScenario implements Serializable {
    private static final long serialVersionUID = 1L;

    private String scenarioId;
    private String description;
    private double frequency; // events per year
    private List<String> affectedAssetIds;
    private Map<String, Double> assetImpact; // Impact factor per asset (0-1)
    private double duration; // days
    private CommonCauseType type;

    public enum CommonCauseType {
      WEATHER, REGIONAL_INFRASTRUCTURE, SUPPLY_CHAIN, MARKET, CYBER, PANDEMIC, GEOPOLITICAL
    }

    public CommonCauseScenario(String id, String description, CommonCauseType type,
        double frequency) {
      this.scenarioId = id;
      this.description = description;
      this.type = type;
      this.frequency = frequency;
      this.affectedAssetIds = new ArrayList<>();
      this.assetImpact = new HashMap<>();
      this.duration = 7.0; // Default 7 days
    }

    public void addAffectedAsset(String assetId, double impactFactor) {
      affectedAssetIds.add(assetId);
      assetImpact.put(assetId, impactFactor);
    }

    public String getScenarioId() {
      return scenarioId;
    }

    public String getDescription() {
      return description;
    }

    public double getFrequency() {
      return frequency;
    }

    public void setFrequency(double freq) {
      this.frequency = freq;
    }

    public List<String> getAffectedAssetIds() {
      return new ArrayList<>(affectedAssetIds);
    }

    public double getAssetImpact(String assetId) {
      return assetImpact.getOrDefault(assetId, 0.0);
    }

    public double getDuration() {
      return duration;
    }

    public void setDuration(double days) {
      this.duration = days;
    }

    public CommonCauseType getType() {
      return type;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("scenarioId", scenarioId);
      map.put("description", description);
      map.put("type", type.name());
      map.put("frequency", frequency);
      map.put("affectedAssets", affectedAssetIds);
      map.put("assetImpacts", assetImpact);
      map.put("durationDays", duration);
      return map;
    }
  }

  /**
   * Creates a portfolio risk analyzer.
   *
   * @param name analyzer name
   */
  public PortfolioRiskAnalyzer(String name) {
    this.name = name;
    this.assets = new ArrayList<>();
    this.commonCauseScenarios = new ArrayList<>();
    this.random = new Random();
  }

  /**
   * Adds an asset to the portfolio.
   *
   * @param asset asset to add
   */
  public void addAsset(Asset asset) {
    assets.add(asset);
  }

  /**
   * Creates and adds an asset.
   *
   * @param id asset ID
   * @param name asset name
   * @param maxProduction maximum production rate
   * @return created asset
   */
  public Asset addAsset(String id, String name, double maxProduction) {
    Asset asset = new Asset(id, name, maxProduction);
    assets.add(asset);
    return asset;
  }

  /**
   * Adds a common cause scenario.
   *
   * @param scenario common cause scenario
   */
  public void addCommonCauseScenario(CommonCauseScenario scenario) {
    commonCauseScenarios.add(scenario);
  }

  /**
   * Creates a regional weather scenario.
   *
   * @param region affected region
   * @param frequency annual frequency
   * @param duration duration in days
   * @return created scenario
   */
  public CommonCauseScenario createRegionalWeatherScenario(String region, double frequency,
      double duration) {
    CommonCauseScenario scenario =
        new CommonCauseScenario("WEATHER-" + region, "Severe weather event in " + region,
            CommonCauseScenario.CommonCauseType.WEATHER, frequency);
    scenario.setDuration(duration);

    // Add all assets in region
    for (Asset asset : assets) {
      if (region.equals(asset.getRegion())) {
        scenario.addAffectedAsset(asset.getAssetId(), 0.5); // 50% production impact
      }
    }

    commonCauseScenarios.add(scenario);
    return scenario;
  }

  /**
   * Sets number of simulations.
   *
   * @param n number of simulations
   */
  public void setNumberOfSimulations(int n) {
    this.numberOfSimulations = n;
  }

  /**
   * Sets simulation period.
   *
   * @param years simulation period in years
   */
  public void setSimulationPeriodYears(double years) {
    this.simulationPeriodYears = years;
  }

  /**
   * Runs the portfolio risk simulation.
   *
   * @return simulation results
   */
  public PortfolioRiskResult run() {
    // Run individual asset simulations
    for (Asset asset : assets) {
      asset.runRiskSimulation(numberOfSimulations / 10);
    }

    // Initialize result
    PortfolioRiskResult result = new PortfolioRiskResult(name);
    result.setNumberOfSimulations(numberOfSimulations);
    result.setSimulationPeriodYears(simulationPeriodYears);

    // Track production loss distribution
    List<Double> portfolioLosses = new ArrayList<>();
    List<Double> commonCauseLosses = new ArrayList<>();
    Map<String, List<Double>> assetLosses = new HashMap<>();
    for (Asset asset : assets) {
      assetLosses.put(asset.getAssetId(), new ArrayList<>());
    }

    // Monte Carlo simulation
    for (int sim = 0; sim < numberOfSimulations; sim++) {
      double totalPortfolioLoss = 0;
      double totalCommonCauseLoss = 0;
      Map<String, Double> simAssetLoss = new HashMap<>();

      // Initialize asset losses from individual availability
      for (Asset asset : assets) {
        double individualLoss = asset.getMaxProduction() * (1 - asset.getSystemAvailability())
            * simulationPeriodYears * 365;
        simAssetLoss.put(asset.getAssetId(), individualLoss);
        totalPortfolioLoss += individualLoss;
      }

      // Simulate common cause events
      for (CommonCauseScenario scenario : commonCauseScenarios) {
        // Poisson sampling for number of events
        int events = samplePoisson(scenario.getFrequency() * simulationPeriodYears);

        for (int e = 0; e < events; e++) {
          double eventLoss = 0;
          for (String assetId : scenario.getAffectedAssetIds()) {
            Asset asset = getAsset(assetId);
            if (asset != null) {
              double impact = scenario.getAssetImpact(assetId);
              double loss = asset.getMaxProduction() * impact * scenario.getDuration();
              eventLoss += loss;

              // Update asset-specific loss
              double currentLoss = simAssetLoss.getOrDefault(assetId, 0.0);
              simAssetLoss.put(assetId, currentLoss + loss);
            }
          }
          totalCommonCauseLoss += eventLoss;
          totalPortfolioLoss += eventLoss;
        }
      }

      portfolioLosses.add(totalPortfolioLoss);
      commonCauseLosses.add(totalCommonCauseLoss);

      for (Map.Entry<String, Double> entry : simAssetLoss.entrySet()) {
        assetLosses.get(entry.getKey()).add(entry.getValue());
      }
    }

    // Calculate statistics
    result.setExpectedPortfolioLoss(mean(portfolioLosses));
    result.setPortfolioLossStdDev(stdDev(portfolioLosses));
    result.setP10PortfolioLoss(percentile(portfolioLosses, 10));
    result.setP50PortfolioLoss(percentile(portfolioLosses, 50));
    result.setP90PortfolioLoss(percentile(portfolioLosses, 90));
    result.setP99PortfolioLoss(percentile(portfolioLosses, 99));
    result.setMaxPortfolioLoss(max(portfolioLosses));

    result.setExpectedCommonCauseLoss(mean(commonCauseLosses));
    result.setCommonCauseFraction(mean(commonCauseLosses) / mean(portfolioLosses));

    // Calculate portfolio production
    double totalMaxProduction = 0;
    double totalExpectedProduction = 0;
    for (Asset asset : assets) {
      totalMaxProduction += asset.getMaxProduction();
      totalExpectedProduction += asset.getExpectedProduction();
    }
    result.setTotalMaxProduction(totalMaxProduction);
    result.setTotalExpectedProduction(totalExpectedProduction);
    result.setPortfolioAvailability(totalExpectedProduction / totalMaxProduction);

    // Asset results
    for (Asset asset : assets) {
      PortfolioRiskResult.AssetResult ar = new PortfolioRiskResult.AssetResult();
      ar.setAssetId(asset.getAssetId());
      ar.setAssetName(asset.getAssetName());
      ar.setMaxProduction(asset.getMaxProduction());
      ar.setExpectedProduction(asset.getExpectedProduction());
      ar.setAvailability(asset.getSystemAvailability());

      List<Double> losses = assetLosses.get(asset.getAssetId());
      ar.setExpectedLoss(mean(losses));
      ar.setP90Loss(percentile(losses, 90));

      ar.setContributionToPortfolioRisk(ar.getExpectedLoss() / result.getExpectedPortfolioLoss());
      result.addAssetResult(ar);
    }

    // Diversification benefit
    double sumIndividualVaR = 0;
    for (Asset asset : assets) {
      sumIndividualVaR += percentile(assetLosses.get(asset.getAssetId()), 90);
    }
    result.setDiversificationBenefit(1.0 - result.getP90PortfolioLoss() / sumIndividualVaR);

    lastResult = result;
    return result;
  }

  private int samplePoisson(double lambda) {
    double L = Math.exp(-lambda);
    int k = 0;
    double p = 1.0;
    do {
      k++;
      p *= random.nextDouble();
    } while (p > L);
    return k - 1;
  }

  private Asset getAsset(String assetId) {
    for (Asset asset : assets) {
      if (asset.getAssetId().equals(assetId)) {
        return asset;
      }
    }
    return null;
  }

  private double mean(List<Double> values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.size();
  }

  private double stdDev(List<Double> values) {
    double m = mean(values);
    double sumSq = 0;
    for (double v : values) {
      sumSq += (v - m) * (v - m);
    }
    return Math.sqrt(sumSq / (values.size() - 1));
  }

  private double percentile(List<Double> values, int p) {
    List<Double> sorted = new ArrayList<>(values);
    java.util.Collections.sort(sorted);
    int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    index = Math.max(0, Math.min(index, sorted.size() - 1));
    return sorted.get(index);
  }

  private double max(List<Double> values) {
    double m = Double.NEGATIVE_INFINITY;
    for (double v : values) {
      if (v > m) {
        m = v;
      }
    }
    return m;
  }

  // Getters

  public String getName() {
    return name;
  }

  public List<Asset> getAssets() {
    return new ArrayList<>(assets);
  }

  public List<CommonCauseScenario> getCommonCauseScenarios() {
    return new ArrayList<>(commonCauseScenarios);
  }

  public PortfolioRiskResult getLastResult() {
    return lastResult;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("numberOfSimulations", numberOfSimulations);
    map.put("simulationPeriodYears", simulationPeriodYears);

    List<Map<String, Object>> assetList = new ArrayList<>();
    for (Asset asset : assets) {
      assetList.add(asset.toMap());
    }
    map.put("assets", assetList);

    List<Map<String, Object>> ccList = new ArrayList<>();
    for (CommonCauseScenario cc : commonCauseScenarios) {
      ccList.add(cc.toMap());
    }
    map.put("commonCauseScenarios", ccList);

    if (lastResult != null) {
      map.put("result", lastResult.toMap());
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  @Override
  public String toString() {
    return String.format("PortfolioRiskAnalyzer[%s, assets=%d, scenarios=%d]", name, assets.size(),
        commonCauseScenarios.size());
  }
}
