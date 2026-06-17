package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Multi-tank LNG ship model that orchestrates parallel ageing of all cargo tanks.
 *
 * <p>
 * A typical LNG carrier has 4-5 cargo tanks sharing a common BOG header. Each tank may contain
 * different cargo (spot-market LNG from different sources, or loaded at different times). The ship
 * model coordinates:
 * </p>
 * <ul>
 * <li>Parallel simulation of all tanks with their own compositions and temperatures</li>
 * <li>Aggregation of total BOG production from all tanks</li>
 * <li>Distribution of fuel demand across the common BOG header</li>
 * <li>Per-tank and aggregate quality tracking (WI, GCV, MN)</li>
 * <li>Overall cargo quantity and value tracking</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGShipModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1025L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGShipModel.class);

  /** Ship name or voyage identifier. */
  private String shipName;

  /** List of tank scenarios (one per cargo tank). */
  private List<LNGAgeingScenario> tankScenarios;

  /** Common BOG handling network for the ship. */
  private LNGBOGHandlingNetwork bogNetwork;

  /** Voyage profile shared across all tanks. */
  private LNGVoyageProfile voyageProfile;

  /** Total simulation time (hours). */
  private double simulationTime = 480.0;

  /** Time step for simulation (hours). */
  private double timeStepHours = 1.0;

  /** Per-tank results keyed by tank name. */
  private Map<String, List<LNGAgeingResult>> tankResults;

  /** Aggregate ship-level results. */
  private List<ShipResult> shipResults;

  /**
   * Constructor with ship name.
   *
   * @param shipName ship name or voyage identifier
   */
  public LNGShipModel(String shipName) {
    this.shipName = shipName;
    this.tankScenarios = new ArrayList<LNGAgeingScenario>();
    this.bogNetwork = new LNGBOGHandlingNetwork();
    this.tankResults = new LinkedHashMap<String, List<LNGAgeingResult>>();
    this.shipResults = new ArrayList<ShipResult>();
  }

  /**
   * Add a tank scenario to the ship.
   *
   * @param scenario pre-configured tank ageing scenario
   */
  public void addTank(LNGAgeingScenario scenario) {
    tankScenarios.add(scenario);
  }

  /**
   * Run all tanks in parallel (stepped in lockstep).
   *
   * <p>
   * Each time step: (1) step all tanks, (2) aggregate BOG, (3) distribute fuel demand, (4) record
   * ship-level results.
   * </p>
   */
  public void run() {
    if (tankScenarios.isEmpty()) {
      logger.error("No tanks configured in ship model");
      return;
    }

    // Initialise all tank models
    for (LNGAgeingScenario scenario : tankScenarios) {
      scenario.setSimulationTime(simulationTime);
      scenario.setTimeStepHours(timeStepHours);
      if (voyageProfile != null) {
        scenario.setVoyageProfile(voyageProfile);
      }
    }

    // Run each scenario independently (they handle their own time-stepping)
    for (LNGAgeingScenario scenario : tankScenarios) {
      scenario.run();
      tankResults.put(scenario.getName(), scenario.getResults());
    }

    // Build aggregate ship results from per-tank results
    buildShipResults();

    logger.info(String.format("Ship model '%s' complete: %d tanks, %.0f hours", shipName,
        tankScenarios.size(), simulationTime));
  }

  /**
   * Build aggregate ship-level results from per-tank data.
   */
  private void buildShipResults() {
    shipResults.clear();
    if (tankResults.isEmpty()) {
      return;
    }

    // Find the maximum number of results across all tanks
    int maxSteps = 0;
    for (List<LNGAgeingResult> results : tankResults.values()) {
      if (results.size() > maxSteps) {
        maxSteps = results.size();
      }
    }

    for (int step = 0; step < maxSteps; step++) {
      ShipResult shipResult = new ShipResult();
      double totalBOG = 0;
      double totalLiquidMass = 0;
      double totalLiquidVolume = 0;
      double totalHeatIngress = 0;
      double weightedWI = 0;
      double weightedGCV = 0;
      double weightedMN = 0;
      double weightedDensity = 0;
      double weightedTemp = 0;
      int activeTanks = 0;

      for (Map.Entry<String, List<LNGAgeingResult>> entry : tankResults.entrySet()) {
        List<LNGAgeingResult> results = entry.getValue();
        if (step < results.size()) {
          LNGAgeingResult r = results.get(step);
          totalBOG += r.getBogMassFlowRate();
          totalLiquidMass += r.getLiquidMass();
          totalLiquidVolume += r.getLiquidVolume();
          totalHeatIngress += r.getHeatIngressKW();

          double mass = r.getLiquidMass();
          weightedWI += r.getWobbeIndex() * mass;
          weightedGCV += r.getGcvVolumetric() * mass;
          weightedMN += r.getMethaneNumber() * mass;
          weightedDensity += r.getDensity() * mass;
          weightedTemp += r.getTemperature() * mass;

          if (r.getTimeHours() > 0 || step == 0) {
            shipResult.timeHours = r.getTimeHours();
          }
          activeTanks++;
        }
      }

      shipResult.totalBOGRate = totalBOG;
      shipResult.totalLiquidMass = totalLiquidMass;
      shipResult.totalLiquidVolume = totalLiquidVolume;
      shipResult.totalHeatIngressKW = totalHeatIngress;
      shipResult.numberOfTanks = activeTanks;

      if (totalLiquidMass > 0) {
        shipResult.averageWobbeIndex = weightedWI / totalLiquidMass;
        shipResult.averageGCV = weightedGCV / totalLiquidMass;
        shipResult.averageMN = weightedMN / totalLiquidMass;
        shipResult.averageDensity = weightedDensity / totalLiquidMass;
        shipResult.averageTemperature = weightedTemp / totalLiquidMass;
      }

      // BOG disposition through the common BOG network
      LNGBOGHandlingNetwork.BOGDisposition disp = bogNetwork.calculateDisposition(totalBOG);
      shipResult.bogToFuel = disp.bogToFuel;
      shipResult.bogReliquefied = disp.bogReliquefied;
      shipResult.bogToGCU = disp.bogToGCU;
      shipResult.bogVented = disp.bogVented;
      shipResult.netCargoLoss = disp.netCargoLoss;

      shipResults.add(shipResult);
    }
  }

  /**
   * Get per-tank results.
   *
   * @return map of tank name to result list
   */
  public Map<String, List<LNGAgeingResult>> getTankResults() {
    return tankResults;
  }

  /**
   * Get aggregate ship results.
   *
   * @return list of ship-level results
   */
  public List<ShipResult> getShipResults() {
    return shipResults;
  }

  /**
   * Get total cargo loss as percentage of initial mass across all tanks.
   *
   * @return cargo loss percentage
   */
  public double getTotalCargoLossPct() {
    if (shipResults.isEmpty()) {
      return 0;
    }
    ShipResult first = shipResults.get(0);
    ShipResult last = shipResults.get(shipResults.size() - 1);
    if (first.totalLiquidMass > 0) {
      return (first.totalLiquidMass - last.totalLiquidMass) / first.totalLiquidMass * 100.0;
    }
    return 0;
  }

  /**
   * Get a summary string of ship-level results.
   *
   * @return formatted summary
   */
  public String getShipSummary() {
    if (shipResults.isEmpty()) {
      return "No results — simulation not yet run.";
    }

    ShipResult first = shipResults.get(0);
    ShipResult last = shipResults.get(shipResults.size() - 1);

    StringBuilder sb = new StringBuilder();
    sb.append("=== LNG Ship Model Summary ===\n");
    sb.append(String.format("Ship: %s, Tanks: %d\n", shipName, tankScenarios.size()));
    sb.append(
        String.format("Duration: %.1f hours (%.1f days)\n", simulationTime, simulationTime / 24.0));
    sb.append(String.format("\n--- Initial vs Final (Ship Totals) ---\n"));
    sb.append(String.format("Total liquid mass: %.0f -> %.0f tonnes (loss: %.2f%%)\n",
        first.totalLiquidMass / 1000.0, last.totalLiquidMass / 1000.0, getTotalCargoLossPct()));
    sb.append(String.format("Avg density: %.1f -> %.1f kg/m3\n", first.averageDensity,
        last.averageDensity));
    sb.append(String.format("Avg WI: %.2f -> %.2f MJ/Sm3\n", first.averageWobbeIndex,
        last.averageWobbeIndex));
    sb.append(String.format("Avg MN: %.1f -> %.1f\n", first.averageMN, last.averageMN));
    sb.append(String.format("Total BOG rate (final): %.0f kg/hr\n", last.totalBOGRate));
    return sb.toString();
  }

  // ─── Getters and setters ───

  /**
   * Get ship name.
   *
   * @return ship name
   */
  public String getShipName() {
    return shipName;
  }

  /**
   * Set ship name.
   *
   * @param shipName ship name
   */
  public void setShipName(String shipName) {
    this.shipName = shipName;
  }

  /**
   * Get tank scenarios.
   *
   * @return list of tank scenarios
   */
  public List<LNGAgeingScenario> getTankScenarios() {
    return tankScenarios;
  }

  /**
   * Get shared BOG network.
   *
   * @return BOG handling network
   */
  public LNGBOGHandlingNetwork getBogNetwork() {
    return bogNetwork;
  }

  /**
   * Set shared BOG network.
   *
   * @param bogNetwork BOG handling network
   */
  public void setBogNetwork(LNGBOGHandlingNetwork bogNetwork) {
    this.bogNetwork = bogNetwork;
  }

  /**
   * Get voyage profile.
   *
   * @return voyage profile
   */
  public LNGVoyageProfile getVoyageProfile() {
    return voyageProfile;
  }

  /**
   * Set voyage profile (shared across all tanks).
   *
   * @param voyageProfile voyage profile
   */
  public void setVoyageProfile(LNGVoyageProfile voyageProfile) {
    this.voyageProfile = voyageProfile;
  }

  /**
   * Set total simulation time.
   *
   * @param hours simulation time (hours)
   */
  public void setSimulationTime(double hours) {
    this.simulationTime = hours;
  }

  /**
   * Get total simulation time.
   *
   * @return simulation time (hours)
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Set time step.
   *
   * @param hours time step (hours)
   */
  public void setTimeStepHours(double hours) {
    this.timeStepHours = hours;
  }

  /**
   * Get time step.
   *
   * @return time step (hours)
   */
  public double getTimeStepHours() {
    return timeStepHours;
  }

  /**
   * Aggregate ship-level result for a single time step.
   */
  public static class ShipResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1026L;

    /** Time elapsed (hours). */
    public double timeHours;

    /** Number of active tanks. */
    public int numberOfTanks;

    /** Total BOG rate from all tanks (kg/hr). */
    public double totalBOGRate;

    /** Total liquid mass across all tanks (kg). */
    public double totalLiquidMass;

    /** Total liquid volume across all tanks (m3). */
    public double totalLiquidVolume;

    /** Total heat ingress across all tanks (kW). */
    public double totalHeatIngressKW;

    /** Mass-weighted average Wobbe Index (MJ/Sm3). */
    public double averageWobbeIndex;

    /** Mass-weighted average GCV (MJ/Sm3). */
    public double averageGCV;

    /** Mass-weighted average Methane Number. */
    public double averageMN;

    /** Mass-weighted average density (kg/m3). */
    public double averageDensity;

    /** Mass-weighted average temperature (K). */
    public double averageTemperature;

    /** BOG consumed as fuel (kg/hr). */
    public double bogToFuel;

    /** BOG reliquefied (kg/hr). */
    public double bogReliquefied;

    /** BOG burned in GCU (kg/hr). */
    public double bogToGCU;

    /** BOG vented (kg/hr). */
    public double bogVented;

    /** Net cargo loss (kg/hr). */
    public double netCargoLoss;

    /**
     * Convert to a map for serialization.
     *
     * @return map of field values
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("timeHours", timeHours);
      map.put("numberOfTanks", numberOfTanks);
      map.put("totalBOGRate_kghr", totalBOGRate);
      map.put("totalLiquidMass_kg", totalLiquidMass);
      map.put("totalLiquidVolume_m3", totalLiquidVolume);
      map.put("totalHeatIngress_kW", totalHeatIngressKW);
      map.put("averageWobbeIndex_MJSm3", averageWobbeIndex);
      map.put("averageGCV_MJSm3", averageGCV);
      map.put("averageMN", averageMN);
      map.put("averageDensity_kgm3", averageDensity);
      map.put("averageTemperature_K", averageTemperature);
      map.put("bogToFuel_kghr", bogToFuel);
      map.put("bogReliquefied_kghr", bogReliquefied);
      map.put("bogToGCU_kghr", bogToGCU);
      map.put("netCargoLoss_kghr", netCargoLoss);
      return map;
    }
  }
}
