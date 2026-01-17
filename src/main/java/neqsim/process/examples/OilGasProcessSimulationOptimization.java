package neqsim.process.examples;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartGenerator;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Oil and Gas Process Simulation and Optimization Example.
 * 
 * <p>
 * This class implements a comprehensive oil and gas separation process simulation based on the
 * workflow presented in:
 * <ul>
 * <li>Andreasen, A. Applied Process Simulation-Driven Oil and Gas Separation Plant Optimization
 * Using Surrogate Modeling and Evolutionary Algorithms. ChemEngineering 2020, 4, 11.</li>
 * </ul>
 * 
 * <p>
 * The process consists of:
 * <ul>
 * <li>Three-stage oil/gas separation</li>
 * <li>Gas recompression with scrubbers</li>
 * <li>Export gas compression with dew point control</li>
 * <li>Export oil pumping</li>
 * </ul>
 * 
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OilGasProcessSimulationOptimization {

  /** Logger for this class. */
  private static final Logger logger =
      LogManager.getLogger(OilGasProcessSimulationOptimization.class);

  /** The well fluid used for simulation. */
  private SystemInterface wellFluid;

  /** The process system object. */
  private ProcessSystem oilProcess;

  /** Default input parameters for simulation. */
  private ProcessInputParameters inputParameters;

  /** Design speed for compressor 27-KA-01 in RPM. */
  private double compressor27KA01DesignSpeed = 0.0;

  /** Maximum allowable speed for compressor 27-KA-01 in RPM. */
  private double compressor27KA01MaxSpeed = 0.0;

  /**
   * Data class to hold process input parameters.
   * 
   * <p>
   * This class contains all input parameters required for running the oil and gas separation
   * process simulation, including flow rates, temperatures, pressures, and pressure drops.
   * </p>
   * 
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ProcessInputParameters {
    // Flow rates
    private double feedRate = 8000.0; // kgmole/hr

    // Separator temperatures (°C)
    private double Tsep1 = 70.0;
    private double Tsep2 = 68.2;
    private double Tsep3 = 65.0;

    // Separator pressures (barg)
    private double Psep1 = 31.5;
    private double Psep2 = 8.0;
    private double Psep3 = 1.5;

    // Scrubber temperatures (°C)
    private double Tscrub1 = 32.0;
    private double Tscrub2 = 32.0;
    private double Tscrub3 = 32.0;
    private double Tscrub4 = 30.0;

    // Compressor and export parameters
    private double Pcomp1 = 90.0; // barg
    private double Trefrig = 10.0; // °C
    private double P_oil_export = 60.0; // barg
    private double T_oil_export = 48.5; // °C
    private double P_gas_export = 188.6; // barg
    private double T_gas_export = 40.0; // °C

    // Pressure drops over heaters (bar)
    private double dP_20_HA_01 = 0.5;
    private double dP_20_HA_02 = 0.5;
    private double dP_20_HA_03 = 0.5;
    private double dP_21_HA_01 = 0.5;
    private double dP_23_HA_01 = 0.3;
    private double dP_23_HA_02 = 1.0;
    private double dP_23_HA_03 = 1.0;
    private double dP_24_HA_01 = 1.0;
    private double dP_25_HA_01 = 0.5;
    private double dP_25_HA_02 = 0.5;
    private double dP_27_HA_01 = 0.0;

    // Getters and setters
    public double getFeedRate() {
      return feedRate;
    }

    public void setFeedRate(double feedRate) {
      this.feedRate = feedRate;
    }

    public double getTsep1() {
      return Tsep1;
    }

    public void setTsep1(double tsep1) {
      Tsep1 = tsep1;
    }

    public double getTsep2() {
      return Tsep2;
    }

    public void setTsep2(double tsep2) {
      Tsep2 = tsep2;
    }

    public double getTsep3() {
      return Tsep3;
    }

    public void setTsep3(double tsep3) {
      Tsep3 = tsep3;
    }

    public double getPsep1() {
      return Psep1;
    }

    public void setPsep1(double psep1) {
      Psep1 = psep1;
    }

    public double getPsep2() {
      return Psep2;
    }

    public void setPsep2(double psep2) {
      Psep2 = psep2;
    }

    public double getPsep3() {
      return Psep3;
    }

    public void setPsep3(double psep3) {
      Psep3 = psep3;
    }

    public double getTscrub1() {
      return Tscrub1;
    }

    public void setTscrub1(double tscrub1) {
      Tscrub1 = tscrub1;
    }

    public double getTscrub2() {
      return Tscrub2;
    }

    public void setTscrub2(double tscrub2) {
      Tscrub2 = tscrub2;
    }

    public double getTscrub3() {
      return Tscrub3;
    }

    public void setTscrub3(double tscrub3) {
      Tscrub3 = tscrub3;
    }

    public double getTscrub4() {
      return Tscrub4;
    }

    public void setTscrub4(double tscrub4) {
      Tscrub4 = tscrub4;
    }

    public double getPcomp1() {
      return Pcomp1;
    }

    public void setPcomp1(double pcomp1) {
      Pcomp1 = pcomp1;
    }

    public double getTrefrig() {
      return Trefrig;
    }

    public void setTrefrig(double trefrig) {
      Trefrig = trefrig;
    }

    public double getP_oil_export() {
      return P_oil_export;
    }

    public void setP_oil_export(double p_oil_export) {
      P_oil_export = p_oil_export;
    }

    public double getT_oil_export() {
      return T_oil_export;
    }

    public void setT_oil_export(double t_oil_export) {
      T_oil_export = t_oil_export;
    }

    public double getP_gas_export() {
      return P_gas_export;
    }

    public void setP_gas_export(double p_gas_export) {
      P_gas_export = p_gas_export;
    }

    public double getT_gas_export() {
      return T_gas_export;
    }

    public void setT_gas_export(double t_gas_export) {
      T_gas_export = t_gas_export;
    }

    public double getdP_20_HA_01() {
      return dP_20_HA_01;
    }

    public void setdP_20_HA_01(double dP_20_HA_01) {
      this.dP_20_HA_01 = dP_20_HA_01;
    }

    public double getdP_20_HA_02() {
      return dP_20_HA_02;
    }

    public void setdP_20_HA_02(double dP_20_HA_02) {
      this.dP_20_HA_02 = dP_20_HA_02;
    }

    public double getdP_20_HA_03() {
      return dP_20_HA_03;
    }

    public void setdP_20_HA_03(double dP_20_HA_03) {
      this.dP_20_HA_03 = dP_20_HA_03;
    }

    public double getdP_21_HA_01() {
      return dP_21_HA_01;
    }

    public void setdP_21_HA_01(double dP_21_HA_01) {
      this.dP_21_HA_01 = dP_21_HA_01;
    }

    public double getdP_23_HA_01() {
      return dP_23_HA_01;
    }

    public void setdP_23_HA_01(double dP_23_HA_01) {
      this.dP_23_HA_01 = dP_23_HA_01;
    }

    public double getdP_23_HA_02() {
      return dP_23_HA_02;
    }

    public void setdP_23_HA_02(double dP_23_HA_02) {
      this.dP_23_HA_02 = dP_23_HA_02;
    }

    public double getdP_23_HA_03() {
      return dP_23_HA_03;
    }

    public void setdP_23_HA_03(double dP_23_HA_03) {
      this.dP_23_HA_03 = dP_23_HA_03;
    }

    public double getdP_24_HA_01() {
      return dP_24_HA_01;
    }

    public void setdP_24_HA_01(double dP_24_HA_01) {
      this.dP_24_HA_01 = dP_24_HA_01;
    }

    public double getdP_25_HA_01() {
      return dP_25_HA_01;
    }

    public void setdP_25_HA_01(double dP_25_HA_01) {
      this.dP_25_HA_01 = dP_25_HA_01;
    }

    public double getdP_25_HA_02() {
      return dP_25_HA_02;
    }

    public void setdP_25_HA_02(double dP_25_HA_02) {
      this.dP_25_HA_02 = dP_25_HA_02;
    }

    public double getdP_27_HA_01() {
      return dP_27_HA_01;
    }

    public void setdP_27_HA_01(double dP_27_HA_01) {
      this.dP_27_HA_01 = dP_27_HA_01;
    }

    /**
     * Copies all parameters from another ProcessInputParameters instance.
     *
     * @param other the source parameters to copy from
     */
    public void copyFrom(ProcessInputParameters other) {
      this.feedRate = other.feedRate;
      this.Tsep1 = other.Tsep1;
      this.Tsep2 = other.Tsep2;
      this.Tsep3 = other.Tsep3;
      this.Psep1 = other.Psep1;
      this.Psep2 = other.Psep2;
      this.Psep3 = other.Psep3;
      this.Tscrub1 = other.Tscrub1;
      this.Tscrub2 = other.Tscrub2;
      this.Tscrub3 = other.Tscrub3;
      this.Tscrub4 = other.Tscrub4;
      this.Pcomp1 = other.Pcomp1;
      this.Trefrig = other.Trefrig;
      this.P_oil_export = other.P_oil_export;
      this.T_oil_export = other.T_oil_export;
      this.P_gas_export = other.P_gas_export;
      this.T_gas_export = other.T_gas_export;
      this.dP_20_HA_01 = other.dP_20_HA_01;
      this.dP_20_HA_02 = other.dP_20_HA_02;
      this.dP_20_HA_03 = other.dP_20_HA_03;
      this.dP_21_HA_01 = other.dP_21_HA_01;
      this.dP_23_HA_01 = other.dP_23_HA_01;
      this.dP_23_HA_02 = other.dP_23_HA_02;
      this.dP_23_HA_03 = other.dP_23_HA_03;
      this.dP_24_HA_01 = other.dP_24_HA_01;
      this.dP_25_HA_01 = other.dP_25_HA_01;
      this.dP_25_HA_02 = other.dP_25_HA_02;
      this.dP_27_HA_01 = other.dP_27_HA_01;
    }
  }

  /**
   * Data class to hold process output results.
   * 
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ProcessOutputResults {
    private double massBalance;
    private double gasExportRate;
    private double oilExportRate;
    private double fuelGasRate;
    private double totalCompressorPower;
    private double totalPumpPower;
    private Map<String, Double> compressorPowers;
    private Map<String, Double> compressorSpeeds;
    private Map<String, Double> compressorMaxSpeeds;
    private Map<String, Double> compressorSpeedUtilization;
    private Map<String, Double> streamFlowRates;
    private Map<String, Double> separatorLoadFactors;
    private Map<String, Double> separatorCapacityUtilization;
    private boolean anySeparatorOverloaded;
    private boolean anyCompressorOverspeed;

    /**
     * Default constructor.
     */
    public ProcessOutputResults() {
      compressorPowers = new HashMap<>();
      compressorSpeeds = new HashMap<>();
      compressorMaxSpeeds = new HashMap<>();
      compressorSpeedUtilization = new HashMap<>();
      streamFlowRates = new HashMap<>();
      separatorLoadFactors = new HashMap<>();
      separatorCapacityUtilization = new HashMap<>();
      anySeparatorOverloaded = false;
      anyCompressorOverspeed = false;
    }

    // Getters and setters
    public double getMassBalance() {
      return massBalance;
    }

    public void setMassBalance(double massBalance) {
      this.massBalance = massBalance;
    }

    public double getGasExportRate() {
      return gasExportRate;
    }

    public void setGasExportRate(double gasExportRate) {
      this.gasExportRate = gasExportRate;
    }

    public double getOilExportRate() {
      return oilExportRate;
    }

    public void setOilExportRate(double oilExportRate) {
      this.oilExportRate = oilExportRate;
    }

    public double getFuelGasRate() {
      return fuelGasRate;
    }

    public void setFuelGasRate(double fuelGasRate) {
      this.fuelGasRate = fuelGasRate;
    }

    public double getTotalCompressorPower() {
      return totalCompressorPower;
    }

    public void setTotalCompressorPower(double totalCompressorPower) {
      this.totalCompressorPower = totalCompressorPower;
    }

    public double getTotalPumpPower() {
      return totalPumpPower;
    }

    public void setTotalPumpPower(double totalPumpPower) {
      this.totalPumpPower = totalPumpPower;
    }

    public Map<String, Double> getCompressorPowers() {
      return compressorPowers;
    }

    public void setCompressorPowers(Map<String, Double> compressorPowers) {
      this.compressorPowers = compressorPowers;
    }

    public Map<String, Double> getCompressorSpeeds() {
      return compressorSpeeds;
    }

    public void setCompressorSpeeds(Map<String, Double> compressorSpeeds) {
      this.compressorSpeeds = compressorSpeeds;
    }

    public Map<String, Double> getCompressorMaxSpeeds() {
      return compressorMaxSpeeds;
    }

    public void setCompressorMaxSpeeds(Map<String, Double> compressorMaxSpeeds) {
      this.compressorMaxSpeeds = compressorMaxSpeeds;
    }

    public Map<String, Double> getCompressorSpeedUtilization() {
      return compressorSpeedUtilization;
    }

    public void setCompressorSpeedUtilization(Map<String, Double> compressorSpeedUtilization) {
      this.compressorSpeedUtilization = compressorSpeedUtilization;
    }

    public boolean isAnyCompressorOverspeed() {
      return anyCompressorOverspeed;
    }

    public void setAnyCompressorOverspeed(boolean anyCompressorOverspeed) {
      this.anyCompressorOverspeed = anyCompressorOverspeed;
    }

    public Map<String, Double> getStreamFlowRates() {
      return streamFlowRates;
    }

    public void setStreamFlowRates(Map<String, Double> streamFlowRates) {
      this.streamFlowRates = streamFlowRates;
    }

    public Map<String, Double> getSeparatorLoadFactors() {
      return separatorLoadFactors;
    }

    public void setSeparatorLoadFactors(Map<String, Double> separatorLoadFactors) {
      this.separatorLoadFactors = separatorLoadFactors;
    }

    public Map<String, Double> getSeparatorCapacityUtilization() {
      return separatorCapacityUtilization;
    }

    public void setSeparatorCapacityUtilization(Map<String, Double> separatorCapacityUtilization) {
      this.separatorCapacityUtilization = separatorCapacityUtilization;
    }

    public boolean isAnySeparatorOverloaded() {
      return anySeparatorOverloaded;
    }

    public void setAnySeparatorOverloaded(boolean anySeparatorOverloaded) {
      this.anySeparatorOverloaded = anySeparatorOverloaded;
    }

    /**
     * Get total power consumption (compressors + pumps) in kW.
     * 
     * @return total power consumption in kW
     */
    public double getTotalPowerConsumption() {
      return totalCompressorPower + totalPumpPower;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("===== Process Simulation Results =====\n");
      sb.append(String.format("Mass Balance: %.4f %%\n", massBalance));
      sb.append(String.format("Gas Export Rate: %.2f kmole/hr\n", gasExportRate / 1000.0));
      sb.append(String.format("Oil Export Rate: %.2f kmole/hr\n", oilExportRate / 1000.0));
      sb.append(String.format("Fuel Gas Rate: %.2f kg/hr\n", fuelGasRate));
      sb.append(String.format("Total Compressor Power: %.2f kW\n", totalCompressorPower));
      sb.append(String.format("Total Pump Power: %.2f kW\n", totalPumpPower));
      sb.append(String.format("Total Power Consumption: %.2f kW\n", getTotalPowerConsumption()));
      sb.append(String.format("Any Separator Overloaded: %s\n", anySeparatorOverloaded));
      sb.append(String.format("Any Compressor Overspeed: %s\n", anyCompressorOverspeed));
      sb.append("\n--- Separator Capacity Utilization ---\n");
      for (Map.Entry<String, Double> entry : separatorCapacityUtilization.entrySet()) {
        sb.append(String.format("  %s: %.1f %%\n", entry.getKey(), entry.getValue() * 100.0));
      }
      sb.append("\n--- Compressor Powers ---\n");
      for (Map.Entry<String, Double> entry : compressorPowers.entrySet()) {
        sb.append(String.format("  %s: %.2f kW\n", entry.getKey(), entry.getValue()));
      }
      sb.append("\n--- Compressor Speed Utilization ---\n");
      for (Map.Entry<String, Double> entry : compressorSpeedUtilization.entrySet()) {
        String status = entry.getValue() > 1.0 ? " <-- OVERSPEED"
            : (entry.getValue() > 0.95 ? " <-- NEAR LIMIT" : "");
        sb.append(String.format("  %s: %.1f%% (%.0f / %.0f RPM)%s\n", entry.getKey(),
            entry.getValue() * 100.0, compressorSpeeds.getOrDefault(entry.getKey(), 0.0),
            compressorMaxSpeeds.getOrDefault(entry.getKey(), 0.0), status));
      }
      return sb.toString();
    }
  }

  /**
   * Default constructor. Creates the well fluid with default composition.
   */
  public OilGasProcessSimulationOptimization() {
    createWellFluid();
    inputParameters = new ProcessInputParameters();
  }

  /**
   * Creates the well fluid using Peng-Robinson EOS with the fluid characterization from the
   * reference paper.
   * 
   * <p>
   * The fluid composition is based on the characterization given in:
   * https://onlinelibrary.wiley.com/doi/abs/10.1002/apj.159
   * </p>
   */
  private void createWellFluid() {
    wellFluid = new SystemPrEos(273.15 + 60.0, 33.01);

    // Set TBP model for plus fractions
    wellFluid.getCharacterization().setTBPModel("PedersernSRK");

    // Add components with molar composition
    wellFluid.addComponent("CO2", 1.5870);
    wellFluid.addComponent("methane", 52.51);
    wellFluid.addComponent("ethane", 6.24);
    wellFluid.addComponent("propane", 4.23);
    wellFluid.addComponent("i-butane", 0.855);
    wellFluid.addComponent("n-butane", 2.213);
    wellFluid.addComponent("i-pentane", 1.124);
    wellFluid.addComponent("n-pentane", 1.271);
    wellFluid.addComponent("n-hexane", 2.289);

    // Add TBP fractions (C7+ cuts)
    wellFluid.addTBPfraction("C7+_cut1", 0.8501, 108.47 / 1000.0, 0.7411);
    wellFluid.addTBPfraction("C7+_cut2", 1.2802, 120.4 / 1000.0, 0.755);
    wellFluid.addTBPfraction("C7+_cut3", 1.6603, 133.64 / 1000.0, 0.7695);
    wellFluid.addTBPfraction("C7+_cut4", 6.5311, 164.70 / 1000.0, 0.799);
    wellFluid.addTBPfraction("C7+_cut5", 6.3311, 215.94 / 1000.0, 0.8387);
    wellFluid.addTBPfraction("C7+_cut6", 4.9618, 273.34 / 1000.0, 0.8754);
    wellFluid.addTBPfraction("C7+_cut7", 2.9105, 334.92 / 1000.0, 0.90731);
    wellFluid.addTBPfraction("C7+_cut8", 3.0505, 412.79 / 1000.0, 0.94575);

    // Initialize after adding TBP fractions to ensure proper component setup
    wellFluid.init(0);

    wellFluid.setMixingRule("classic");
    wellFluid.setMultiPhaseCheck(true); // Enable multi-phase check for better phase detection
  }

  /**
   * Creates the oil and gas separation process system.
   * 
   * <p>
   * The process includes:
   * <ul>
   * <li>Three-stage separation</li>
   * <li>Gas recompression with scrubbers</li>
   * <li>Export gas compression with dew point control</li>
   * <li>Export oil pumping</li>
   * </ul>
   * 
   * @return the configured ProcessSystem
   */
  public ProcessSystem createProcess() {
    // Create well stream
    Stream wellStream = new Stream("well stream", wellFluid.clone());
    wellStream.setTemperature(60.0, "C");
    wellStream.setPressure(33.01, "bara");

    // Heater before first stage separator
    Heater wellStreamCooler = new Heater("20-HA-01", wellStream);

    // First-stage separator - HP separator
    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("20-VA-01", wellStreamCooler.getOutletStream());
    // Set design parameters based on expected nominal flow (K-factor for HP separator)
    firstStageSeparator.setDesignGasLoadFactor(0.11);
    firstStageSeparator.setInternalDiameter(2.5); // Initial estimate, will be sized
    firstStageSeparator.setSeparatorLength(8.0);

    // Throttling valve from first-stage separator oil out
    ThrottlingValve oilValve1 =
        new ThrottlingValve("VLV-100", firstStageSeparator.getOilOutStream());

    // Placeholder streams for dew point scrubber liquids (recycle destinations)
    // These will be updated by Recycle objects during process run
    Stream dewPointLiquidReflux1 = wellStream.clone();
    dewPointLiquidReflux1.setName("dew point liquid reflux 1");
    dewPointLiquidReflux1.setFlowRate(10.0, "kg/hr"); // Small initial flow
    dewPointLiquidReflux1.setPressure(9.5, "bara"); // MP pressure

    Stream dewPointLiquidReflux2 = wellStream.clone();
    dewPointLiquidReflux2.setName("dew point liquid reflux 2");
    dewPointLiquidReflux2.setFlowRate(10.0, "kg/hr"); // Small initial flow
    dewPointLiquidReflux2.setPressure(9.5, "bara"); // MP pressure

    // Mixer for second-stage inlet
    Mixer oil2ndStageMixer = new Mixer("MIX-101");
    oil2ndStageMixer.addStream(oilValve1.getOutletStream());
    // Add placeholder streams for dew point scrubber liquids
    oil2ndStageMixer.addStream(dewPointLiquidReflux1);
    oil2ndStageMixer.addStream(dewPointLiquidReflux2);

    // Heater before second stage separator
    Heater oilHeaterFromFirstStage = new Heater("20-HA-02", oil2ndStageMixer.getOutletStream());

    // Second-stage separator - MP separator
    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("20-VA-02", oilHeaterFromFirstStage.getOutletStream());
    // Set design parameters for MP separator
    secondStageSeparator.setDesignGasLoadFactor(0.10);
    secondStageSeparator.setInternalDiameter(2.0);
    secondStageSeparator.setSeparatorLength(6.0);

    // Throttling valve from second-stage separator oil out
    ThrottlingValve oilValve2 =
        new ThrottlingValve("VLV-102", secondStageSeparator.getOilOutStream());

    // Reflux stream for third stage (recycle destination)
    Stream oilReflux = wellStream.clone();
    oilReflux.setName("third stage reflux");
    oilReflux.setFlowRate(100.0, "kg/hr"); // Initial estimate, will be updated by recycle

    // Mixer for third-stage inlet
    Mixer thirdStageOilMixer = new Mixer("MIX-102");
    thirdStageOilMixer.addStream(oilValve2.getOutletStream());
    thirdStageOilMixer.addStream(oilReflux);

    // Heater before third stage separator
    Heater oilHeaterFromSecondStage = new Heater("20-HA-03", thirdStageOilMixer.getOutletStream());

    // Third-stage separator - LP separator
    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("20-VA-03", oilHeaterFromSecondStage.getOutletStream());
    // Set design parameters for LP separator
    thirdStageSeparator.setDesignGasLoadFactor(0.09);
    thirdStageSeparator.setInternalDiameter(2.2);
    thirdStageSeparator.setSeparatorLength(7.0);

    // Cooler for first stage gas recompression
    Cooler firstStageCooler = new Cooler("23-HA-03", thirdStageSeparator.getGasOutStream());

    // First stage scrubber
    Separator firstStageScrubber = new Separator("23-VG-03", firstStageCooler.getOutletStream());
    // Set design parameters for scrubbers (higher K-factor since mostly gas)
    firstStageScrubber.setDesignGasLoadFactor(0.11);
    firstStageScrubber.setInternalDiameter(1.5);
    firstStageScrubber.setSeparatorLength(5.0);

    // Pump for first stage scrubber liquid
    Pump firstStageScrubberPump = new Pump("23-PA-01", firstStageScrubber.getLiquidOutStream());

    // LP recycle to third stage
    Recycle lpRecycle = new Recycle("LP oil recycle");
    lpRecycle.addStream(firstStageScrubberPump.getOutletStream());
    lpRecycle.setOutletStream(oilReflux);
    lpRecycle.setTolerance(1e-3); // Looser tolerance for better convergence

    // First-stage recompressor
    Compressor firstStageRecompressor =
        new Compressor("23-KA-03", firstStageScrubber.getGasOutStream());
    firstStageRecompressor.setIsentropicEfficiency(0.75);

    // Mixer combining first-stage recompressor gas and second-stage separator gas
    Mixer firstStageGasMixer = new Mixer("MIX-103");
    firstStageGasMixer.addStream(firstStageRecompressor.getOutletStream());
    firstStageGasMixer.addStream(secondStageSeparator.getGasOutStream());

    // Second-stage cooler
    Cooler secondStageCooler = new Cooler("23-HA-02", firstStageGasMixer.getOutletStream());

    // Second-stage scrubber
    Separator secondStageScrubber = new Separator("23-VG-02", secondStageCooler.getOutletStream());

    // Add second-stage scrubber liquid to third-stage oil mixer
    thirdStageOilMixer.addStream(secondStageScrubber.getLiquidOutStream());

    // Second-stage recompressor
    Compressor secondStageRecompressor =
        new Compressor("23-KA-02", secondStageScrubber.getGasOutStream());
    secondStageRecompressor.setIsentropicEfficiency(0.75);

    // Mixer combining second-stage recompressor gas and first-stage separator gas
    Mixer exportGasMixer = new Mixer("MIX-100");
    exportGasMixer.addStream(secondStageRecompressor.getOutletStream());
    exportGasMixer.addStream(firstStageSeparator.getGasOutStream());

    // Dew point cooler
    Cooler dewPointCooler = new Cooler("23-HA-01", exportGasMixer.getOutletStream());

    // Dew point scrubber
    Separator dewPointScrubber = new Separator("23-VG-01", dewPointCooler.getOutletStream());

    // Recycle for dew point scrubber 1 liquid to second-stage mixer
    Recycle dewPointRecycle1 = new Recycle("dew point recycle 1");
    dewPointRecycle1.addStream(dewPointScrubber.getLiquidOutStream());
    dewPointRecycle1.setOutletStream(dewPointLiquidReflux1);
    dewPointRecycle1.setTolerance(1e-3);

    // First stage export compressor
    Compressor firstStageExportCompressor =
        new Compressor("23-KA-01", dewPointScrubber.getGasOutStream());
    firstStageExportCompressor.setIsentropicEfficiency(0.75);

    // Cooler after first stage export compressor
    Cooler dewPointCooler2 = new Cooler("24-HA-01", firstStageExportCompressor.getOutletStream());

    // Scrubber after second dew point cooler
    Separator dewPointScrubber2 = new Separator("24-VG-01", dewPointCooler2.getOutletStream());

    // Recycle for dew point scrubber 2 liquid to second-stage mixer
    Recycle dewPointRecycle2 = new Recycle("dew point recycle 2");
    dewPointRecycle2.addStream(dewPointScrubber2.getLiquidOutStream());
    dewPointRecycle2.setOutletStream(dewPointLiquidReflux2);
    dewPointRecycle2.setTolerance(1e-3);

    // Gas splitter for fuel gas takeoff
    Splitter gasSplitter = new Splitter("splitter", dewPointScrubber2.getGasOutStream());
    gasSplitter.setSplitNumber(2);
    gasSplitter.setFlowRates(new double[] {-1, 2966.0}, "kg/hr");

    // Fuel gas stream
    gasSplitter.getSplitStream(1).setName("fuel gas");

    // Heat exchanger for gas cooling
    HeatExchanger gasHeatExchanger = new HeatExchanger("25-HA-01", gasSplitter.getSplitStream(0));
    gasHeatExchanger.setGuessOutTemperature(273.15 + 15.0);
    gasHeatExchanger.setUAvalue(800e3);

    // Dew point cooler #3
    Cooler dewPointCooler3 = new Cooler("25-HA-02", gasHeatExchanger.getOutStream(0));

    // Dew point scrubber #3
    Separator dewPointScrubber3 = new Separator("25-VG-01", dewPointCooler3.getOutletStream());

    // Add dew point scrubber #3 liquid to the export gas mixer
    exportGasMixer.addStream(dewPointScrubber3.getLiquidOutStream());

    // Set feed for the heat exchanger
    gasHeatExchanger.setFeedStream(1, dewPointScrubber3.getGasOutStream());

    // Second-stage export compressor
    Compressor secondStageExportCompressor =
        new Compressor("27-KA-01", gasHeatExchanger.getOutStream(1));
    secondStageExportCompressor.setIsentropicEfficiency(0.75);

    // Cooler after second-stage export compressor
    Cooler exportCompressorCooler =
        new Cooler("27-HA-01", secondStageExportCompressor.getOutletStream());

    // Final export gas stream
    exportCompressorCooler.getOutletStream().setName("export gas");

    // Export oil cooler
    Cooler exportOilCooler = new Cooler("21-HA-01", thirdStageSeparator.getOilOutStream());

    // Export oil pump
    Pump exportOilPump = new Pump("21-PA-01", exportOilCooler.getOutletStream());
    exportOilPump.getOutletStream().setName("export oil");

    // Create process system and add units in order
    oilProcess = new ProcessSystem();
    oilProcess.add(wellStream);
    oilProcess.add(wellStreamCooler);
    oilProcess.add(firstStageSeparator);
    oilProcess.add(oilValve1);
    oilProcess.add(dewPointLiquidReflux1); // Placeholder for dew point recycle 1
    oilProcess.add(dewPointLiquidReflux2); // Placeholder for dew point recycle 2
    oilProcess.add(oil2ndStageMixer);
    oilProcess.add(oilHeaterFromFirstStage);
    oilProcess.add(secondStageSeparator);
    oilProcess.add(oilValve2);
    oilProcess.add(oilReflux);
    oilProcess.add(thirdStageOilMixer);
    oilProcess.add(oilHeaterFromSecondStage);
    oilProcess.add(thirdStageSeparator);
    oilProcess.add(firstStageCooler);
    oilProcess.add(firstStageScrubber);
    oilProcess.add(firstStageScrubberPump);
    oilProcess.add(lpRecycle);
    oilProcess.add(firstStageRecompressor);
    oilProcess.add(firstStageGasMixer);
    oilProcess.add(secondStageCooler);
    oilProcess.add(secondStageScrubber);
    oilProcess.add(secondStageRecompressor);
    oilProcess.add(exportGasMixer);
    oilProcess.add(dewPointCooler);
    oilProcess.add(dewPointScrubber);
    oilProcess.add(dewPointRecycle1); // Recycle for dew point scrubber 1 liquid
    oilProcess.add(firstStageExportCompressor);
    oilProcess.add(dewPointCooler2);
    oilProcess.add(dewPointScrubber2);
    oilProcess.add(dewPointRecycle2); // Recycle for dew point scrubber 2 liquid
    oilProcess.add(gasSplitter);
    oilProcess.add(gasHeatExchanger);
    oilProcess.add(dewPointCooler3);
    oilProcess.add(dewPointScrubber3);
    oilProcess.add(secondStageExportCompressor);
    oilProcess.add(exportCompressorCooler);
    oilProcess.add(exportOilCooler);
    oilProcess.add(exportOilPump);
    oilProcess.add(exportCompressorCooler.getOutletStream());
    oilProcess.add(exportOilPump.getOutletStream());
    oilProcess.add(gasSplitter.getSplitStream(1));

    return oilProcess;
  }

  /**
   * Configures compressor charts for the export compressor (27-KA-01). This should be called after
   * createProcess() and an initial run to establish operating points.
   *
   * <p>
   * The compressor chart is generated based on the current operating point, with speed curves
   * ranging from 70% to 110% of the design speed.
   * </p>
   *
   * @param designSpeed the design speed in RPM (e.g., 10000)
   * @param maxSpeed the maximum allowable speed in RPM (e.g., 11000)
   */
  public void configureCompressorCharts(double designSpeed, double maxSpeed) {
    // Store in class fields for use in getOutput()
    this.compressor27KA01DesignSpeed = designSpeed;
    this.compressor27KA01MaxSpeed = maxSpeed;

    try {
      Compressor comp27KA01 = (Compressor) oilProcess.getUnit("27-KA-01");

      // Set the maximum speed constraint
      comp27KA01.setMaximumSpeed(maxSpeed);

      // Set design speed as starting point
      comp27KA01.setSpeed(designSpeed);

      // Enable polytropic calculations
      comp27KA01.setUsePolytropicCalc(true);
      comp27KA01.setPolytropicEfficiency(0.75);

      // Generate compressor chart with surge and stonewall curves
      // Use "interpolate and extrapolate" chart type for best optimization behavior
      CompressorChartGenerator generator = new CompressorChartGenerator(comp27KA01);
      generator.setChartType("interpolate and extrapolate");

      // Generate chart with 5 speed curves (from 80% to 120% of design speed)
      // This creates curves with surge and stonewall limits automatically
      CompressorChartInterface chart = generator.generateCompressorChart("normal curves", 5);

      // Set the chart on the compressor and enable it
      comp27KA01.setCompressorChart(chart);
      comp27KA01.getCompressorChart().setUseCompressorChart(true);

      // Set minimum speed from the chart's minimum speed curve
      double chartMinSpeed = chart.getMinSpeedCurve();
      if (!Double.isNaN(chartMinSpeed) && chartMinSpeed > 0) {
        comp27KA01.setMinimumSpeed(chartMinSpeed);
      }

      // Enable speed calculation from compressor chart during simulation
      // This allows the compressor to determine required speed based on operating conditions
      comp27KA01.setSolveSpeed(true);

      // Run the process once to calculate actual power for setting design power
      oilProcess.run();

      // Set design power based on calculated power with margin for optimization headroom
      // Note: maxDesignPower should be in Watts (same units as getPower())
      double currentPowerWatts = comp27KA01.getPower(); // Returns Watts
      if (currentPowerWatts > 0 && !Double.isNaN(currentPowerWatts)) {
        // Set design power to 120% of current power to allow for flow variations
        comp27KA01.getMechanicalDesign().maxDesignPower = currentPowerWatts * 1.2;
      }

      // Reinitialize capacity constraints now that chart is configured
      // This enables surge margin and stonewall margin constraints during optimization
      comp27KA01.reinitializeCapacityConstraints();

      logger.info(
          String.format("Configured compressor 27-KA-01: design speed=%.0f RPM, max speed=%.0f RPM",
              designSpeed, maxSpeed));
      logger.info(String.format(
          "Compressor chart generated with %d speed curves, surge and stonewall curves enabled",
          chart.getSpeeds().length));
    } catch (Exception e) {
      logger.warn("Failed to configure compressor 27-KA-01: " + e.getMessage());
    }
  }

  /**
   * Updates the process model with the given input parameters.
   * 
   * @param params the input parameters to apply
   */
  public void updateInput(ProcessInputParameters params) {
    this.inputParameters = params;

    try {
      // Well stream
      Stream wellStream = (Stream) oilProcess.getUnit("well stream");
      wellStream.setFlowRate(params.getFeedRate() * 1e3 / 3600, "mol/sec");
      wellStream.setPressure(params.getPsep1() + params.getdP_20_HA_01() + 1.01325, "bara");
      wellStream.setTemperature(60.0, "C");

      // 20-HA-01
      Heater heater20HA01 = (Heater) oilProcess.getUnit("20-HA-01");
      heater20HA01.setOutTemperature(params.getTsep1() + 273.15);
      heater20HA01.setOutPressure(params.getPsep1() + 1.01325);

      // VLV-100
      ThrottlingValve vlv100 = (ThrottlingValve) oilProcess.getUnit("VLV-100");
      vlv100.setOutletPressure(params.getPsep2() + params.getdP_20_HA_02() + 1.01325);

      // 20-HA-02
      Heater heater20HA02 = (Heater) oilProcess.getUnit("20-HA-02");
      heater20HA02.setOutTemperature(params.getTsep2() + 273.15);
      heater20HA02.setOutPressure(params.getPsep2() + 1.01325);

      // VLV-102
      ThrottlingValve vlv102 = (ThrottlingValve) oilProcess.getUnit("VLV-102");
      vlv102.setOutletPressure(params.getPsep3() + params.getdP_20_HA_03() + 1.01325);

      // 20-HA-03
      Heater heater20HA03 = (Heater) oilProcess.getUnit("20-HA-03");
      heater20HA03.setOutTemperature(params.getTsep3() + 273.15);
      heater20HA03.setOutPressure(params.getPsep3() + 1.01325);

      // 23-HA-03
      Cooler cooler23HA03 = (Cooler) oilProcess.getUnit("23-HA-03");
      cooler23HA03.setOutTemperature(params.getTscrub1() + 273.15);
      cooler23HA03.setOutPressure(params.getPsep3() - params.getdP_23_HA_03() + 1.01325);

      // 23-PA-01
      Pump pump23PA01 = (Pump) oilProcess.getUnit("23-PA-01");
      pump23PA01.setOutletPressure(params.getPsep3() + params.getdP_20_HA_03() + 1.01325);

      // 23-KA-03
      Compressor comp23KA03 = (Compressor) oilProcess.getUnit("23-KA-03");
      comp23KA03.setOutletPressure(params.getPsep2() + 1.01325);

      // 23-HA-02
      Cooler cooler23HA02 = (Cooler) oilProcess.getUnit("23-HA-02");
      cooler23HA02.setOutTemperature(params.getTscrub2() + 273.15);
      cooler23HA02.setOutPressure(params.getPsep2() - params.getdP_23_HA_02() + 1.01325);

      // 23-KA-02
      Compressor comp23KA02 = (Compressor) oilProcess.getUnit("23-KA-02");
      comp23KA02.setOutletPressure(params.getPsep1() + 1.01325);

      // 23-HA-01
      Cooler cooler23HA01 = (Cooler) oilProcess.getUnit("23-HA-01");
      cooler23HA01.setOutTemperature(params.getTscrub3() + 273.15);
      cooler23HA01.setOutPressure(params.getPsep1() - params.getdP_23_HA_01() + 1.01325);

      // 23-KA-01
      Compressor comp23KA01 = (Compressor) oilProcess.getUnit("23-KA-01");
      comp23KA01.setOutletPressure(params.getPcomp1() + 1.01325);

      // 24-HA-01
      Cooler cooler24HA01 = (Cooler) oilProcess.getUnit("24-HA-01");
      cooler24HA01.setOutTemperature(params.getTscrub4() + 273.15);
      cooler24HA01.setOutPressure(params.getPcomp1() - params.getdP_24_HA_01() + 1.01325);

      // 25-HA-02
      Cooler cooler25HA02 = (Cooler) oilProcess.getUnit("25-HA-02");
      cooler25HA02.setOutTemperature(params.getTrefrig() + 273.15);
      cooler25HA02.setOutPressure(
          params.getPcomp1() - params.getdP_25_HA_01() - params.getdP_25_HA_02() + 1.01325);

      // 27-KA-01
      Compressor comp27KA01 = (Compressor) oilProcess.getUnit("27-KA-01");
      comp27KA01.setOutletPressure(params.getP_gas_export() + 1.01325);

      // 27-HA-01
      Cooler cooler27HA01 = (Cooler) oilProcess.getUnit("27-HA-01");
      cooler27HA01.setOutTemperature(params.getT_gas_export() + 273.15);
      cooler27HA01.setOutPressure(params.getP_gas_export() - params.getdP_27_HA_01() + 1.01325);

      // 21-HA-01
      Cooler cooler21HA01 = (Cooler) oilProcess.getUnit("21-HA-01");
      cooler21HA01.setOutTemperature(params.getT_oil_export() + 273.15);
      cooler21HA01.setOutPressure(params.getPsep3() - params.getdP_21_HA_01() + 1.01325);

      // 21-PA-01
      Pump pump21PA01 = (Pump) oilProcess.getUnit("21-PA-01");
      pump21PA01.setOutletPressure(params.getP_oil_export() + 1.01325);

    } catch (Exception e) {
      logger.error("Failed to update unit parameters: " + e.getMessage(), e);
    }
  }

  /**
   * Retrieves simulation results from the process.
   * 
   * @return ProcessOutputResults containing all simulation results
   */
  public ProcessOutputResults getOutput() {
    ProcessOutputResults results = new ProcessOutputResults();

    try {
      // Get flow rates
      double feedFlow = ((Stream) oilProcess.getUnit("well stream")).getFlowRate("kg/hr");
      double fuelFlow = ((Stream) oilProcess.getUnit("fuel gas")).getFlowRate("kg/hr");
      double exportGasFlow =
          ((Compressor) oilProcess.getUnit("27-KA-01")).getOutletStream().getFlowRate("kg/hr");
      double exportOilFlow = ((ThreePhaseSeparator) oilProcess.getUnit("20-VA-03"))
          .getOilOutStream().getFlowRate("kg/hr");

      // Calculate mass balance
      double massBalance = (feedFlow - fuelFlow - exportGasFlow - exportOilFlow) / feedFlow * 100;
      results.setMassBalance(massBalance);

      // Get stream flow rates
      results.setFuelGasRate(fuelFlow);
      results.setGasExportRate(((Stream) oilProcess.getUnit("export gas")).getFlowRate("mole/hr"));
      results.setOilExportRate(((Stream) oilProcess.getUnit("export oil")).getFlowRate("mole/hr"));

      // Get compressor powers
      double power23KA01 = ((Compressor) oilProcess.getUnit("23-KA-01")).getPower() / 1e3; // kW
      double power23KA02 = ((Compressor) oilProcess.getUnit("23-KA-02")).getPower() / 1e3;
      double power23KA03 = ((Compressor) oilProcess.getUnit("23-KA-03")).getPower() / 1e3;
      double power27KA01 = ((Compressor) oilProcess.getUnit("27-KA-01")).getPower() / 1e3;

      results.getCompressorPowers().put("23-KA-01", power23KA01);
      results.getCompressorPowers().put("23-KA-02", power23KA02);
      results.getCompressorPowers().put("23-KA-03", power23KA03);
      results.getCompressorPowers().put("27-KA-01", power27KA01);

      double totalCompressorPower = power23KA01 + power23KA02 + power23KA03 + power27KA01;
      results.setTotalCompressorPower(totalCompressorPower);

      // Get pump power
      double power21PA01 = ((Pump) oilProcess.getUnit("21-PA-01")).getPower() / 1e3; // kW
      results.setTotalPumpPower(power21PA01);

      // Get compressor speed data for 27-KA-01 (export compressor)
      // Speed is estimated based on flow rate - higher flow requires higher speed
      // to maintain outlet pressure. We use a simple affinity law approximation:
      // Speed ratio ≈ (Flow ratio)^1 for centrifugal compressors at constant head
      boolean anyOverspeed = false;
      try {
        // Use the stored design parameters from configureCompressorCharts()
        // If not configured, fall back to reading from compressor object
        double designSpeed = this.compressor27KA01DesignSpeed;
        double maxSpeed27KA01 = this.compressor27KA01MaxSpeed;

        // If not configured yet, use default values
        if (designSpeed <= 0 || maxSpeed27KA01 <= 0) {
          Compressor comp27KA01 = (Compressor) oilProcess.getUnit("27-KA-01");
          if (designSpeed <= 0) {
            designSpeed = 10000.0; // default design speed
          }
          if (maxSpeed27KA01 <= 0) {
            maxSpeed27KA01 = comp27KA01.getMaximumSpeed();
            if (maxSpeed27KA01 <= 0) {
              maxSpeed27KA01 = 11000.0; // default max speed
            }
          }
        }

        // Calculate speed using affinity law: n2/n1 = (Q2/Q1) for constant head
        // Reference: at 8000 kmol/hr, speed = designSpeed
        // Get the feed rate in mole/hr for a stable reference
        Stream wellStream = (Stream) oilProcess.getUnit("well stream");
        double currentFeedMoleHr = wellStream.getFlowRate("mole/hr");
        // Convert to kmol/hr: mole/hr / 1000 = kmol/hr
        double currentFeedKmolHr = currentFeedMoleHr / 1000.0;

        // Reference: at 8000 kmol/hr, speed = designSpeed
        double referenceFeedKmolHr = 8000.0;
        double flowRatio = currentFeedKmolHr / referenceFeedKmolHr;

        // Apply affinity law: n2/n1 = (Q2/Q1) for constant head
        double calculatedSpeed = designSpeed * flowRatio;

        double speedUtil27KA01 = calculatedSpeed / maxSpeed27KA01;

        results.getCompressorSpeeds().put("27-KA-01", calculatedSpeed);
        results.getCompressorMaxSpeeds().put("27-KA-01", maxSpeed27KA01);
        results.getCompressorSpeedUtilization().put("27-KA-01", speedUtil27KA01);

        if (speedUtil27KA01 > 1.0) {
          anyOverspeed = true;
          logger
              .info(String.format("27-KA-01 overspeed: %.0f RPM (max %.0f RPM), feed %.0f kmol/hr",
                  calculatedSpeed, maxSpeed27KA01, currentFeedKmolHr));
        } else {
          logger.debug(String.format(
              "27-KA-01 speed: %.0f RPM (max %.0f RPM, util %.1f%%), feed %.0f kmol/hr",
              calculatedSpeed, maxSpeed27KA01, speedUtil27KA01 * 100, currentFeedKmolHr));
        }
      } catch (Exception ex) {
        logger.warn("Could not get 27-KA-01 speed: " + ex.getMessage());
      }
      results.setAnyCompressorOverspeed(anyOverspeed);

      // Get separator capacity data
      boolean anyOverloaded = false;
      Map<String, Double> loadFactors = results.getSeparatorLoadFactors();
      Map<String, Double> capacityUtil = results.getSeparatorCapacityUtilization();

      // First stage separator (20-VA-01)
      try {
        Separator sep1 = (Separator) oilProcess.getUnit("20-VA-01");
        double lf1 = sep1.getGasLoadFactor();
        double cu1 = sep1.getCapacityUtilization();
        if (!Double.isNaN(lf1) && !Double.isInfinite(lf1)) {
          loadFactors.put("20-VA-01", lf1);
        }
        if (!Double.isNaN(cu1) && !Double.isInfinite(cu1)) {
          capacityUtil.put("20-VA-01", cu1);
          if (cu1 > 1.0) {
            anyOverloaded = true;
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not get 20-VA-01 capacity: " + ex.getMessage());
      }

      // Second stage separator (20-VA-02)
      try {
        Separator sep2 = (Separator) oilProcess.getUnit("20-VA-02");
        double lf2 = sep2.getGasLoadFactor();
        double cu2 = sep2.getCapacityUtilization();
        if (!Double.isNaN(lf2) && !Double.isInfinite(lf2)) {
          loadFactors.put("20-VA-02", lf2);
        }
        if (!Double.isNaN(cu2) && !Double.isInfinite(cu2)) {
          capacityUtil.put("20-VA-02", cu2);
          if (cu2 > 1.0) {
            anyOverloaded = true;
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not get 20-VA-02 capacity: " + ex.getMessage());
      }

      // Third stage separator (20-VA-03)
      try {
        Separator sep3 = (Separator) oilProcess.getUnit("20-VA-03");
        double lf3 = sep3.getGasLoadFactor();
        double cu3 = sep3.getCapacityUtilization();
        if (!Double.isNaN(lf3) && !Double.isInfinite(lf3)) {
          loadFactors.put("20-VA-03", lf3);
        }
        if (!Double.isNaN(cu3) && !Double.isInfinite(cu3)) {
          capacityUtil.put("20-VA-03", cu3);
          if (cu3 > 1.0) {
            anyOverloaded = true;
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not get 20-VA-03 capacity: " + ex.getMessage());
      }

      // First scrubber (23-VG-03)
      try {
        Separator scrub1 = (Separator) oilProcess.getUnit("23-VG-03");
        double lfs = scrub1.getGasLoadFactor();
        double cus = scrub1.getCapacityUtilization();
        if (!Double.isNaN(lfs) && !Double.isInfinite(lfs)) {
          loadFactors.put("23-VG-03", lfs);
        }
        if (!Double.isNaN(cus) && !Double.isInfinite(cus)) {
          capacityUtil.put("23-VG-03", cus);
          if (cus > 1.0) {
            anyOverloaded = true;
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not get 23-VG-03 capacity: " + ex.getMessage());
      }

      results.setSeparatorLoadFactors(loadFactors);
      results.setSeparatorCapacityUtilization(capacityUtil);
      results.setAnySeparatorOverloaded(anyOverloaded);

    } catch (Exception e) {
      logger.error("Failed to retrieve output: " + e.getMessage(), e);
    }

    return results;
  }

  /**
   * Runs the simulation with the current input parameters.
   * 
   * @return ProcessOutputResults containing simulation results
   */
  public ProcessOutputResults runSimulation() {
    updateInput(inputParameters);
    oilProcess.run();
    return getOutput();
  }

  /**
   * Runs the simulation with custom input parameters.
   * 
   * @param params the input parameters to use
   * @return ProcessOutputResults containing simulation results
   */
  public ProcessOutputResults runSimulation(ProcessInputParameters params) {
    updateInput(params);
    oilProcess.run();
    return getOutput();
  }

  /**
   * Performs a simple optimization to minimize total power consumption.
   * 
   * <p>
   * This is a basic grid search optimization that varies key parameters to find the minimum total
   * power consumption.
   * </p>
   * 
   * @param baseParams the base input parameters to start from
   * @return the optimized input parameters
   */
  public ProcessInputParameters optimizePowerConsumption(ProcessInputParameters baseParams) {
    logger.info("Starting power consumption optimization...");

    ProcessInputParameters bestParams = baseParams;
    double bestPower = Double.MAX_VALUE;

    // Define parameter ranges for optimization
    double[] feedRateRange = {5000.0, 6000.0, 7000.0, 8000.0, 9000.0, 10000.0}; // kgmole/hr
    double[] tsep1Range = {65.0, 67.5, 70.0, 72.5, 75.0};
    double[] tsep2Range = {65.0, 66.0, 67.0, 68.0, 69.0, 70.0};
    double[] psep1Range = {30.0, 31.0, 32.0, 33.0, 34.0, 35.0};

    int iterations = 0;
    int successfulIterations = 0;
    int consecutiveFailures = 0;
    int totalFailures = 0;
    final int MAX_CONSECUTIVE_FAILURES = 10;
    final int MAX_TOTAL_FAILURES = 50;
    int totalIterations =
        feedRateRange.length * tsep1Range.length * tsep2Range.length * psep1Range.length;

    outerLoop: for (double feedRate : feedRateRange) {
      for (double tsep1 : tsep1Range) {
        for (double tsep2 : tsep2Range) {
          // Constraint: Tsep1 >= Tsep2
          if (tsep1 < tsep2) {
            continue;
          }

          for (double psep1 : psep1Range) {
            // Check if we should stop due to too many failures
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
              logger.warn(String.format(
                  "Stopping optimization: %d consecutive failures. Total failures: %d",
                  consecutiveFailures, totalFailures));
              break outerLoop;
            }
            if (totalFailures >= MAX_TOTAL_FAILURES) {
              logger.warn(String.format("Stopping optimization: reached max total failures (%d)",
                  totalFailures));
              break outerLoop;
            }

            iterations++;

            ProcessInputParameters testParams = new ProcessInputParameters();
            testParams.copyFrom(baseParams);
            // Override optimization variables
            testParams.setFeedRate(feedRate);
            testParams.setTsep1(tsep1);
            testParams.setTsep2(tsep2);
            testParams.setPsep1(psep1);

            try {
              ProcessOutputResults results = runSimulation(testParams);
              double totalPower = results.getTotalPowerConsumption();

              // Reset consecutive failures on success
              consecutiveFailures = 0;
              successfulIterations++;

              // Check separator capacity constraints
              if (results.isAnySeparatorOverloaded()) {
                logger.debug(String.format(
                    "Iteration %d/%d: Skipped - separator capacity exceeded at feedRate=%.0f",
                    iterations, totalIterations, feedRate));
                continue;
              }

              if (totalPower < bestPower && Math.abs(results.getMassBalance()) < 1.0) {
                bestPower = totalPower;
                bestParams = testParams;
                logger.info(String.format(
                    "Iteration %d/%d: Found better solution with feedRate=%.0f, power = %.2f kW",
                    iterations, totalIterations, feedRate, totalPower));
              }
            } catch (Exception e) {
              consecutiveFailures++;
              totalFailures++;
              logger.debug("Simulation failed for iteration " + iterations + ": " + e.getMessage());
            }
          }
        }
      }
    }

    logger.info(String.format(
        "Optimization complete. Successful: %d, Failed: %d. Best feed rate: %.0f kgmole/hr, Best power: %.2f kW",
        successfulIterations, totalFailures, bestParams.getFeedRate(), bestPower));
    return bestParams;
  }

  /**
   * Optimizes for maximum production (feed rate) while respecting separator capacity constraints.
   * 
   * <p>
   * This optimization finds the highest possible feed rate that does not overload any separator. It
   * reports which separator becomes the bottleneck and at what utilization.
   * </p>
   * 
   * @param baseParams the base input parameters to start from
   * @return OptimizationResult containing optimal parameters and bottleneck information
   */
  public MaxProductionResult optimizeMaxProduction(ProcessInputParameters baseParams) {
    logger.info("Starting maximum production optimization...");
    logger.info("Searching for highest feed rate without exceeding separator capacity...");

    // Initialize the process first
    createProcess();

    MaxProductionResult result = new MaxProductionResult();
    ProcessInputParameters bestParams = baseParams;
    double bestFeedRate = 0.0;
    double bestOilExportRate = 0.0;
    double bestGasExportRate = 0.0;

    // Define parameter ranges - focus on feed rate, use fixed operating conditions
    double[] feedRateRange = {5000.0, 6000.0, 7000.0, 8000.0, 9000.0, 10000.0, 11000.0, 12000.0,
        13000.0, 14000.0, 15000.0};

    int successfulIterations = 0;
    int consecutiveFailures = 0;
    int totalFailures = 0;
    final int MAX_CONSECUTIVE_FAILURES = 5;

    for (double feedRate : feedRateRange) {
      if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
        logger.warn("Stopping: " + consecutiveFailures + " consecutive failures");
        break;
      }

      ProcessInputParameters testParams = new ProcessInputParameters();
      testParams.copyFrom(baseParams);
      testParams.setFeedRate(feedRate);

      try {
        ProcessOutputResults simResults = runSimulation(testParams);
        consecutiveFailures = 0;
        successfulIterations++;

        // Find bottleneck equipment (highest utilization from separators AND compressor speed)
        String bottleneckEquip = null;
        double maxUtil = 0.0;
        boolean isCompressorBottleneck = false;

        // Check separator utilization
        for (Map.Entry<String, Double> entry : simResults.getSeparatorCapacityUtilization()
            .entrySet()) {
          if (entry.getValue() != null && entry.getValue() > maxUtil) {
            maxUtil = entry.getValue();
            bottleneckEquip = entry.getKey();
            isCompressorBottleneck = false;
          }
        }

        // Check compressor speed utilization
        for (Map.Entry<String, Double> entry : simResults.getCompressorSpeedUtilization()
            .entrySet()) {
          if (entry.getValue() != null && entry.getValue() > maxUtil) {
            maxUtil = entry.getValue();
            bottleneckEquip = entry.getKey();
            isCompressorBottleneck = true;
          }
        }

        // Log compressor speed status
        for (Map.Entry<String, Double> entry : simResults.getCompressorSpeedUtilization()
            .entrySet()) {
          double speedUtil = entry.getValue() != null ? entry.getValue() : 0.0;
          double speed = simResults.getCompressorSpeeds().getOrDefault(entry.getKey(), 0.0);
          double maxSpeed = simResults.getCompressorMaxSpeeds().getOrDefault(entry.getKey(), 0.0);
          logger.debug(String.format("  Compressor %s: %.0f / %.0f RPM (%.1f%%)", entry.getKey(),
              speed, maxSpeed, speedUtil * 100));
        }

        String bottleneckType = isCompressorBottleneck ? "compressor speed" : "separator";
        logger.info(String.format(
            "Feed rate %.0f kgmole/hr: Bottleneck=%s (%s) at %.1f%%, Overloaded=%s, Overspeed=%s",
            feedRate, bottleneckEquip, bottleneckType, maxUtil * 100,
            simResults.isAnySeparatorOverloaded(), simResults.isAnyCompressorOverspeed()));

        // Check if this is a valid solution (no separator overload, no compressor overspeed,
        // reasonable mass balance)
        boolean isValidSolution =
            !simResults.isAnySeparatorOverloaded() && !simResults.isAnyCompressorOverspeed()
                && Math.abs(simResults.getMassBalance()) < 5.0;

        if (isValidSolution) {
          if (feedRate > bestFeedRate) {
            bestFeedRate = feedRate;
            bestParams = testParams;
            bestOilExportRate = simResults.getOilExportRate();
            bestGasExportRate = simResults.getGasExportRate();
            result.setBottleneckSeparator(bottleneckEquip);
            result.setBottleneckUtilization(maxUtil);
            result.setSeparatorCapacities(
                new java.util.HashMap<>(simResults.getSeparatorCapacityUtilization()));
            result.setCompressorSpeedUtilization(
                new java.util.HashMap<>(simResults.getCompressorSpeedUtilization()));
          }
        } else if (simResults.isAnySeparatorOverloaded() || simResults.isAnyCompressorOverspeed()) {
          // Found the limit - the previous feed rate was maximum
          String reason =
              simResults.isAnySeparatorOverloaded() ? "separator capacity" : "compressor speed";
          logger.info(String.format(
              "Feed rate %.0f kgmole/hr exceeds %s. Maximum production found at %.0f kgmole/hr",
              feedRate, reason, bestFeedRate));
          result.setLimitingFeedRate(feedRate);
          result.setLimitingSeparator(bottleneckEquip);
          result.setLimitingUtilization(maxUtil);
          break;
        }

      } catch (Exception e) {
        consecutiveFailures++;
        totalFailures++;
        logger.debug("Simulation failed at feed rate " + feedRate + ": " + e.getMessage());
      }
    }

    result.setOptimalParams(bestParams);
    result.setMaxFeedRate(bestFeedRate);
    result.setMaxOilExportRate(bestOilExportRate);
    result.setMaxGasExportRate(bestGasExportRate);
    result.setSuccessfulIterations(successfulIterations);
    result.setTotalFailures(totalFailures);

    logger.info(String.format(
        "Maximum production optimization complete. Max feed rate: %.0f kgmole/hr", bestFeedRate));

    return result;
  }

  /**
   * Result class for maximum production optimization.
   */
  public static class MaxProductionResult {
    private ProcessInputParameters optimalParams;
    private double maxFeedRate;
    private double maxOilExportRate;
    private double maxGasExportRate;
    private String bottleneckSeparator;
    private double bottleneckUtilization;
    private String limitingSeparator;
    private double limitingFeedRate;
    private double limitingUtilization;
    private Map<String, Double> separatorCapacities;
    private Map<String, Double> compressorSpeedUtilization;
    private int successfulIterations;
    private int totalFailures;

    public ProcessInputParameters getOptimalParams() {
      return optimalParams;
    }

    public void setOptimalParams(ProcessInputParameters optimalParams) {
      this.optimalParams = optimalParams;
    }

    public double getMaxFeedRate() {
      return maxFeedRate;
    }

    public void setMaxFeedRate(double maxFeedRate) {
      this.maxFeedRate = maxFeedRate;
    }

    public double getMaxOilExportRate() {
      return maxOilExportRate;
    }

    public void setMaxOilExportRate(double maxOilExportRate) {
      this.maxOilExportRate = maxOilExportRate;
    }

    public double getMaxGasExportRate() {
      return maxGasExportRate;
    }

    public void setMaxGasExportRate(double maxGasExportRate) {
      this.maxGasExportRate = maxGasExportRate;
    }

    public String getBottleneckSeparator() {
      return bottleneckSeparator;
    }

    public void setBottleneckSeparator(String bottleneckSeparator) {
      this.bottleneckSeparator = bottleneckSeparator;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public void setBottleneckUtilization(double bottleneckUtilization) {
      this.bottleneckUtilization = bottleneckUtilization;
    }

    public String getLimitingSeparator() {
      return limitingSeparator;
    }

    public void setLimitingSeparator(String limitingSeparator) {
      this.limitingSeparator = limitingSeparator;
    }

    public double getLimitingFeedRate() {
      return limitingFeedRate;
    }

    public void setLimitingFeedRate(double limitingFeedRate) {
      this.limitingFeedRate = limitingFeedRate;
    }

    public double getLimitingUtilization() {
      return limitingUtilization;
    }

    public void setLimitingUtilization(double limitingUtilization) {
      this.limitingUtilization = limitingUtilization;
    }

    public Map<String, Double> getSeparatorCapacities() {
      return separatorCapacities;
    }

    public void setSeparatorCapacities(Map<String, Double> separatorCapacities) {
      this.separatorCapacities = separatorCapacities;
    }

    public Map<String, Double> getCompressorSpeedUtilization() {
      return compressorSpeedUtilization;
    }

    public void setCompressorSpeedUtilization(Map<String, Double> compressorSpeedUtilization) {
      this.compressorSpeedUtilization = compressorSpeedUtilization;
    }

    public int getSuccessfulIterations() {
      return successfulIterations;
    }

    public void setSuccessfulIterations(int successfulIterations) {
      this.successfulIterations = successfulIterations;
    }

    public int getTotalFailures() {
      return totalFailures;
    }

    public void setTotalFailures(int totalFailures) {
      this.totalFailures = totalFailures;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("===== Maximum Production Optimization Results =====\n");
      sb.append(String.format("Maximum Feed Rate: %.0f kgmole/hr\n", maxFeedRate));
      sb.append(String.format("Oil Export Rate: %.2f kmole/hr\n", maxOilExportRate / 1000.0));
      sb.append(String.format("Gas Export Rate: %.2f kmole/hr\n", maxGasExportRate / 1000.0));
      sb.append(String.format("\nBottleneck Separator: %s\n", bottleneckSeparator));
      sb.append(String.format("Bottleneck Utilization: %.1f%%\n", bottleneckUtilization * 100));
      if (limitingSeparator != null) {
        sb.append(String.format("\nLimiting Separator: %s\n", limitingSeparator));
        sb.append(String.format("Limiting Feed Rate (would exceed capacity): %.0f kgmole/hr\n",
            limitingFeedRate));
        sb.append(String.format("Utilization at Limit: %.1f%%\n", limitingUtilization * 100));
      }
      sb.append("\n--- Separator Capacities at Max Production ---\n");
      if (separatorCapacities != null) {
        for (Map.Entry<String, Double> entry : separatorCapacities.entrySet()) {
          String status = "";
          if (entry.getValue() != null) {
            if (entry.getValue() > 0.9) {
              status = " <-- NEAR LIMIT";
            } else if (entry.getValue() > 0.8) {
              status = " <-- HIGH";
            }
            sb.append(
                String.format("  %s: %.1f%%%s\n", entry.getKey(), entry.getValue() * 100, status));
          }
        }
      }
      sb.append("\n--- Compressor Speed Utilization at Max Production ---\n");
      if (compressorSpeedUtilization != null) {
        for (Map.Entry<String, Double> entry : compressorSpeedUtilization.entrySet()) {
          String status = "";
          if (entry.getValue() != null) {
            if (entry.getValue() > 1.0) {
              status = " <-- OVERSPEED";
            } else if (entry.getValue() > 0.95) {
              status = " <-- NEAR LIMIT";
            } else if (entry.getValue() > 0.9) {
              status = " <-- HIGH";
            }
            sb.append(
                String.format("  %s: %.1f%%%s\n", entry.getKey(), entry.getValue() * 100, status));
          }
        }
      }
      sb.append(String.format("\nIterations: %d successful, %d failed\n", successfulIterations,
          totalFailures));
      return sb.toString();
    }
  }

  /**
   * Gets the input parameters.
   * 
   * @return the current input parameters
   */
  public ProcessInputParameters getInputParameters() {
    return inputParameters;
  }

  /**
   * Sets the input parameters.
   * 
   * @param inputParameters the input parameters to set
   */
  public void setInputParameters(ProcessInputParameters inputParameters) {
    this.inputParameters = inputParameters;
  }

  /**
   * Gets the process system.
   * 
   * @return the process system
   */
  public ProcessSystem getOilProcess() {
    return oilProcess;
  }

  /**
   * Gets the well fluid.
   * 
   * @return the well fluid system interface
   */
  public SystemInterface getWellFluid() {
    return wellFluid;
  }

  /**
   * Main method to demonstrate the simulation and optimization.
   * 
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    logger.info("=================================================");
    logger.info("Oil and Gas Process Simulation and Optimization");
    logger.info("=================================================");

    // Create the simulation object
    OilGasProcessSimulationOptimization sim = new OilGasProcessSimulationOptimization();

    // Create the process
    logger.info("Creating process model...");
    sim.createProcess();

    // Run simulation with default parameters
    logger.info("Running simulation with default parameters...");
    long startTime = System.currentTimeMillis();
    ProcessOutputResults results = sim.runSimulation();
    long endTime = System.currentTimeMillis();

    logger.info("Simulation completed in " + (endTime - startTime) / 1000.0 + " seconds");
    System.out.println(results);

    // Run optimization (optional - can be time-consuming)
    boolean runOptimization = false;
    if (runOptimization) {
      logger.info("Starting optimization...");
      startTime = System.currentTimeMillis();
      ProcessInputParameters optimizedParams =
          sim.optimizePowerConsumption(sim.getInputParameters());
      endTime = System.currentTimeMillis();
      logger.info("Optimization completed in " + (endTime - startTime) / 1000.0 + " seconds");

      // Run final simulation with optimized parameters
      logger.info("Running final simulation with optimized parameters...");
      ProcessOutputResults optimizedResults = sim.runSimulation(optimizedParams);
      System.out.println("\n===== Optimized Results =====");
      System.out.println(optimizedResults);
    }
  }
}
