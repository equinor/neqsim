package neqsim.pvtsimulation.simulation;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Multi-stage separator test simulation for PVT analysis.
 *
 * <p>
 * Simulates oil/gas separation through multiple separator stages to determine:
 * <ul>
 * <li>Stage-by-stage GOR (Gas-Oil Ratio)</li>
 * <li>Cumulative GOR</li>
 * <li>Oil Formation Volume Factor (Bo)</li>
 * <li>Solution Gas-Oil Ratio (Rs)</li>
 * <li>Stock-tank oil properties (API gravity)</li>
 * <li>Optimal separator conditions</li>
 * </ul>
 *
 * <p>
 * Typical usage:
 * 
 * <pre>
 * MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(reservoirFluid);
 * sepTest.addSeparatorStage(50.0, 40.0); // HP separator: 50 bara, 40°C
 * sepTest.addSeparatorStage(10.0, 30.0); // LP separator: 10 bara, 30°C
 * sepTest.addSeparatorStage(1.01325, 15.0); // Stock tank: 1 atm, 15°C
 * sepTest.run();
 * 
 * System.out.println("Total GOR: " + sepTest.getTotalGOR() + " Sm3/Sm3");
 * System.out.println("Bo: " + sepTest.getBo() + " m3/Sm3");
 * System.out.println("API: " + sepTest.getStockTankAPIGravity());
 * </pre>
 *
 * @author ESOL
 */
public class MultiStageSeparatorTest extends BasePVTsimulation {

  private List<SeparatorStage> stages = new ArrayList<>();
  private List<SeparatorStageResult> results = new ArrayList<>();

  private double reservoirTemperature = 373.15; // K
  private double reservoirPressure = 300.0; // bara

  private double totalGOR = 0.0; // Sm3 gas / Sm3 stock tank oil
  private double Bo = 0.0; // reservoir m3 / Sm3 stock tank oil
  private double Rs = 0.0; // Sm3 gas dissolved / Sm3 stock tank oil at reservoir
  private double stockTankOilDensity = 0.0; // kg/m3
  private double stockTankAPIGravity = 0.0;

  /**
   * Separator stage definition.
   */
  public static class SeparatorStage {
    private final double pressure; // bara
    private final double temperature; // Celsius
    private final String name;

    /**
     * Create a separator stage.
     *
     * @param pressure Stage pressure in bara
     * @param temperature Stage temperature in Celsius
     */
    public SeparatorStage(double pressure, double temperature) {
      this(pressure, temperature, null);
    }

    /**
     * Create a named separator stage.
     *
     * @param pressure Stage pressure in bara
     * @param temperature Stage temperature in Celsius
     * @param name Stage name (e.g., "HP Separator")
     */
    public SeparatorStage(double pressure, double temperature, String name) {
      this.pressure = pressure;
      this.temperature = temperature;
      this.name = name;
    }

    public double getPressure() {
      return pressure;
    }

    public double getTemperature() {
      return temperature;
    }

    public String getName() {
      return name != null ? name : "Stage";
    }
  }

  /**
   * Results from a single separator stage.
   */
  public static class SeparatorStageResult {
    private final int stageNumber;
    private final String stageName;
    private final double pressure;
    private final double temperature;
    private double gasRate; // Sm3/h
    private double oilRate; // Sm3/h
    private double stageGOR; // Sm3 gas / Sm3 liquid to this stage
    private double cumulativeGOR; // Sm3 gas / Sm3 stock tank oil
    private double gasDensity; // kg/m3
    private double oilDensity; // kg/m3
    private double gasMW;
    private double oilMW;
    private double gasZFactor;
    private double oilViscosity; // cP

    /**
     * Create stage result.
     *
     * @param stageNumber Stage number (1-based)
     * @param stageName Stage name
     * @param pressure Stage pressure (bara)
     * @param temperature Stage temperature (°C)
     */
    public SeparatorStageResult(int stageNumber, String stageName, double pressure,
        double temperature) {
      this.stageNumber = stageNumber;
      this.stageName = stageName;
      this.pressure = pressure;
      this.temperature = temperature;
    }

    public int getStageNumber() {
      return stageNumber;
    }

    public String getStageName() {
      return stageName;
    }

    public double getPressure() {
      return pressure;
    }

    public double getTemperature() {
      return temperature;
    }

    public double getGasRate() {
      return gasRate;
    }

    public double getOilRate() {
      return oilRate;
    }

    public double getStageGOR() {
      return stageGOR;
    }

    public double getCumulativeGOR() {
      return cumulativeGOR;
    }

    public double getGasDensity() {
      return gasDensity;
    }

    public double getOilDensity() {
      return oilDensity;
    }

    public double getGasMW() {
      return gasMW;
    }

    public double getOilMW() {
      return oilMW;
    }

    public double getGasZFactor() {
      return gasZFactor;
    }

    public double getOilViscosity() {
      return oilViscosity;
    }

    @Override
    public String toString() {
      return String.format(
          "Stage %d (%s): P=%.1f bara, T=%.1f°C, GOR=%.1f Sm3/Sm3, ρ_oil=%.1f kg/m3", stageNumber,
          stageName, pressure, temperature, stageGOR, oilDensity);
    }
  }

  /**
   * Constructor for MultiStageSeparatorTest.
   *
   * @param system Reservoir fluid system
   */
  public MultiStageSeparatorTest(SystemInterface system) {
    super(system);
  }

  /**
   * Set reservoir conditions.
   *
   * @param pressure Reservoir pressure in bara
   * @param temperature Reservoir temperature in Celsius
   */
  public void setReservoirConditions(double pressure, double temperature) {
    this.reservoirPressure = pressure;
    this.reservoirTemperature = temperature + 273.15;
  }

  /**
   * Add a separator stage.
   *
   * @param pressure Stage pressure in bara
   * @param temperature Stage temperature in Celsius
   */
  public void addSeparatorStage(double pressure, double temperature) {
    stages.add(new SeparatorStage(pressure, temperature));
  }

  /**
   * Add a named separator stage.
   *
   * @param pressure Stage pressure in bara
   * @param temperature Stage temperature in Celsius
   * @param name Stage name
   */
  public void addSeparatorStage(double pressure, double temperature, String name) {
    stages.add(new SeparatorStage(pressure, temperature, name));
  }

  /**
   * Add stock tank conditions (1 atm, 15°C by default).
   */
  public void addStockTankStage() {
    stages.add(new SeparatorStage(1.01325, 15.0, "Stock Tank"));
  }

  /**
   * Clear all separator stages.
   */
  public void clearStages() {
    stages.clear();
    results.clear();
  }

  /**
   * Set up a typical 3-stage separator train.
   *
   * @param hpPressure HP separator pressure (bara)
   * @param hpTemperature HP separator temperature (°C)
   * @param lpPressure LP separator pressure (bara)
   * @param lpTemperature LP separator temperature (°C)
   */
  public void setTypicalThreeStage(double hpPressure, double hpTemperature, double lpPressure,
      double lpTemperature) {
    clearStages();
    addSeparatorStage(hpPressure, hpTemperature, "HP Separator");
    addSeparatorStage(lpPressure, lpTemperature, "LP Separator");
    addStockTankStage();
  }

  @Override
  public void run() {
    if (stages.isEmpty()) {
      throw new IllegalStateException("No separator stages defined. Use addSeparatorStage().");
    }

    results.clear();

    // Start with reservoir fluid at reservoir conditions
    SystemInterface currentFluid = getThermoSystem().clone();
    currentFluid.setPressure(reservoirPressure);
    currentFluid.setTemperature(reservoirTemperature);

    ThermodynamicOperations ops = new ThermodynamicOperations(currentFluid);
    ops.TPflash();
    currentFluid.initPhysicalProperties();

    // Store reservoir oil volume for Bo calculation
    double reservoirOilVolume = 0.0;
    if (currentFluid.hasPhaseType("oil")) {
      reservoirOilVolume = currentFluid.getPhase("oil").getVolume();
    } else {
      // Single phase - use total volume
      reservoirOilVolume = currentFluid.getVolume();
    }

    double totalGasVolumeSC = 0.0;
    double stockTankOilVolumeSC = 0.0;

    // Process each separator stage
    for (int i = 0; i < stages.size(); i++) {
      SeparatorStage stage = stages.get(i);
      SeparatorStageResult result = new SeparatorStageResult(i + 1, stage.getName(),
          stage.getPressure(), stage.getTemperature());

      // Flash to stage conditions
      currentFluid.setPressure(stage.getPressure());
      currentFluid.setTemperature(stage.getTemperature() + 273.15);
      ops.setSystem(currentFluid);
      ops.TPflash();
      currentFluid.initPhysicalProperties();

      // Calculate gas and oil volumes at standard conditions
      if (currentFluid.getNumberOfPhases() > 1 && currentFluid.hasPhaseType("gas")) {
        // Get gas phase at stage conditions
        SystemInterface gasPhase = currentFluid.phaseToSystem("gas");
        gasPhase.setPressure(1.01325);
        gasPhase.setTemperature(288.15);
        ThermodynamicOperations gasOps = new ThermodynamicOperations(gasPhase);
        gasOps.TPflash();
        gasPhase.initPhysicalProperties();

        double gasVolumeSC = gasPhase.getVolume();
        totalGasVolumeSC += gasVolumeSC;

        result.gasRate = gasVolumeSC;
        result.gasDensity = currentFluid.getPhase("gas").getDensity("kg/m3");
        result.gasMW = currentFluid.getPhase("gas").getMolarMass() * 1000;
        result.gasZFactor = currentFluid.getPhase("gas").getZ();

        // Continue with liquid phase
        if (currentFluid.hasPhaseType("oil")) {
          currentFluid = currentFluid.phaseToSystem("oil");
          ops = new ThermodynamicOperations(currentFluid);

          result.oilDensity = currentFluid.getDensity("kg/m3");
          result.oilMW = currentFluid.getMolarMass() * 1000;
          if (currentFluid.hasPhaseType("oil")) {
            result.oilViscosity = currentFluid.getPhase("oil").getViscosity("cP");
          }
        }
      } else {
        // Single phase (liquid)
        result.gasRate = 0.0;
        result.oilDensity = currentFluid.getDensity("kg/m3");
        result.oilMW = currentFluid.getMolarMass() * 1000;
      }

      // For last stage (stock tank), calculate stock tank oil volume
      if (i == stages.size() - 1) {
        SystemInterface stockTankOil = currentFluid.clone();
        stockTankOil.setPressure(1.01325);
        stockTankOil.setTemperature(288.15);
        ThermodynamicOperations stOps = new ThermodynamicOperations(stockTankOil);
        stOps.TPflash();
        stockTankOil.initPhysicalProperties();

        stockTankOilVolumeSC = stockTankOil.getVolume();
        stockTankOilDensity = stockTankOil.getDensity("kg/m3");

        // Calculate API gravity
        double specificGravity = stockTankOilDensity / 999.0; // relative to water
        stockTankAPIGravity = (141.5 / specificGravity) - 131.5;
      }

      results.add(result);
    }

    // Calculate overall results
    if (stockTankOilVolumeSC > 0) {
      totalGOR = totalGasVolumeSC / stockTankOilVolumeSC;
      Bo = reservoirOilVolume / stockTankOilVolumeSC;
      Rs = totalGOR; // For saturated oil, Rs = GOR
    }

    // Update stage results with cumulative GOR
    double cumulativeGas = 0.0;
    for (SeparatorStageResult result : results) {
      cumulativeGas += result.gasRate;
      if (stockTankOilVolumeSC > 0) {
        result.cumulativeGOR = cumulativeGas / stockTankOilVolumeSC;
        result.stageGOR = result.gasRate / stockTankOilVolumeSC;
      }
    }
  }

  /**
   * Get total GOR (gas-oil ratio) at standard conditions.
   *
   * @return GOR in Sm3 gas / Sm3 stock tank oil
   */
  public double getTotalGOR() {
    return totalGOR;
  }

  /**
   * Get oil formation volume factor.
   *
   * @return Bo in reservoir m3 / Sm3 stock tank oil
   */
  public double getBo() {
    return Bo;
  }

  /**
   * Get solution gas-oil ratio.
   *
   * @return Rs in Sm3 gas / Sm3 stock tank oil
   */
  public double getRs() {
    return Rs;
  }

  /**
   * Get stock tank oil density.
   *
   * @return Density in kg/m3
   */
  public double getStockTankOilDensity() {
    return stockTankOilDensity;
  }

  /**
   * Get stock tank oil API gravity.
   *
   * @return API gravity (°API)
   */
  public double getStockTankAPIGravity() {
    return stockTankAPIGravity;
  }

  /**
   * Get results for all separator stages.
   *
   * @return List of stage results
   */
  public List<SeparatorStageResult> getStageResults() {
    return new ArrayList<>(results);
  }

  /**
   * Get number of separator stages.
   *
   * @return Number of stages
   */
  public int getNumberOfStages() {
    return stages.size();
  }

  /**
   * Generate a summary report of the separator test.
   *
   * @return Formatted report string
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Multi-Stage Separator Test Results ===\n\n");

    sb.append(String.format("Reservoir Conditions: P = %.1f bara, T = %.1f °C\n", reservoirPressure,
        reservoirTemperature - 273.15));
    sb.append(String.format("Number of Stages: %d\n\n", stages.size()));

    sb.append("Stage-by-Stage Results:\n");
    sb.append(String.format("%-20s %10s %10s %12s %12s %12s\n", "Stage", "P (bara)", "T (°C)",
        "GOR", "Cum GOR", "ρ_oil"));
    sb.append(String.format("%-20s %10s %10s %12s %12s %12s\n", "", "", "", "(Sm3/Sm3)",
        "(Sm3/Sm3)", "(kg/m3)"));
    sb.append(StringUtils.repeat("-", 78) + "\n");

    for (SeparatorStageResult r : results) {
      sb.append(String.format("%-20s %10.1f %10.1f %12.1f %12.1f %12.1f\n", r.getStageName(),
          r.getPressure(), r.getTemperature(), r.getStageGOR(), r.getCumulativeGOR(),
          r.getOilDensity()));
    }

    sb.append("\nOverall Results:\n");
    sb.append(String.format("  Total GOR:           %.1f Sm3/Sm3\n", totalGOR));
    sb.append(String.format("  Bo (FVF):            %.4f rm3/Sm3\n", Bo));
    sb.append(String.format("  Stock Tank Density:  %.1f kg/m3\n", stockTankOilDensity));
    sb.append(String.format("  API Gravity:         %.1f °API\n", stockTankAPIGravity));

    return sb.toString();
  }
}
