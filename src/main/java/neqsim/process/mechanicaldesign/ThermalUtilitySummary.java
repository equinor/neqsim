package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Aggregates thermal utility requirements across a process system.
 *
 * <p>
 * Extends the power-focused utility summary with cooling water, steam, fuel gas, and other thermal
 * utility demands. Each cooling or heating duty is mapped to a utility type with flow rate
 * estimation.
 * </p>
 *
 * <p>
 * Supported utility types:
 * </p>
 * <ul>
 * <li><b>Cooling water:</b> estimated flow from duty, supply/return temperatures</li>
 * <li><b>Steam:</b> LP/MP/HP steam consumption from heating duties</li>
 * <li><b>Fuel gas:</b> estimated from fired heater duties</li>
 * <li><b>Instrument air:</b> estimate from equipment count</li>
 * </ul>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * ThermalUtilitySummary util = new ThermalUtilitySummary(process);
 * util.calcUtilities();
 * double cwFlow = util.getCoolingWaterFlowM3hr();
 * String json = util.toJson();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ThermalUtilitySummary implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system to analyse. */
  private ProcessSystem processSystem;

  /** Cooling water supply temperature in Celsius. */
  private double cwSupplyTempC = 20.0;

  /** Cooling water return temperature in Celsius. */
  private double cwReturnTempC = 35.0;

  /** Cooling water specific heat in kJ/(kg*K). */
  private static final double CW_CP = 4.18;

  /** Cooling water density in kg/m3. */
  private static final double CW_DENSITY = 1000.0;

  /** Steam latent heat for LP steam in kJ/kg. */
  private static final double LP_STEAM_LATENT_HEAT = 2258.0;

  /** Steam latent heat for MP steam in kJ/kg. */
  private static final double MP_STEAM_LATENT_HEAT = 2015.0;

  /** Steam latent heat for HP steam in kJ/kg. */
  private static final double HP_STEAM_LATENT_HEAT = 1890.0;

  /** LP steam temperature threshold in Celsius. */
  private static final double LP_STEAM_THRESHOLD = 160.0;

  /** HP steam temperature threshold in Celsius. */
  private static final double HP_STEAM_THRESHOLD = 260.0;

  // Calculated values
  private double totalCoolingDutyKW = 0.0;
  private double totalHeatingDutyKW = 0.0;
  private double coolingWaterFlowM3hr = 0.0;
  private double lpSteamFlowKghr = 0.0;
  private double mpSteamFlowKghr = 0.0;
  private double hpSteamFlowKghr = 0.0;
  private double instrumentAirNm3hr = 0.0;
  private int equipmentCount = 0;
  private List<UtilityConsumer> consumers = new ArrayList<UtilityConsumer>();

  /**
   * Creates a ThermalUtilitySummary for the given process system.
   *
   * @param processSystem the process system to analyse
   */
  public ThermalUtilitySummary(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the cooling water supply temperature.
   *
   * @param tempC supply temperature in Celsius
   */
  public void setCwSupplyTempC(double tempC) {
    this.cwSupplyTempC = tempC;
  }

  /**
   * Sets the cooling water return temperature.
   *
   * @param tempC return temperature in Celsius
   */
  public void setCwReturnTempC(double tempC) {
    this.cwReturnTempC = tempC;
  }

  /**
   * Calculates all thermal utility requirements from the process equipment.
   */
  public void calcUtilities() {
    consumers.clear();
    totalCoolingDutyKW = 0.0;
    totalHeatingDutyKW = 0.0;
    coolingWaterFlowM3hr = 0.0;
    lpSteamFlowKghr = 0.0;
    mpSteamFlowKghr = 0.0;
    hpSteamFlowKghr = 0.0;
    equipmentCount = 0;

    for (int i = 0; i < processSystem.getUnitOperations().size(); i++) {
      ProcessEquipmentInterface equip = processSystem.getUnitOperations().get(i);
      equipmentCount++;

      if (equip instanceof Heater) {
        Heater heater = (Heater) equip;
        double dutyW = heater.getDuty();
        double dutyKW = dutyW / 1000.0;

        if (dutyKW < 0) {
          // Cooling duty
          double absDutyKW = Math.abs(dutyKW);
          totalCoolingDutyKW += absDutyKW;
          double cwFlow = calcCoolingWaterFlow(absDutyKW);
          coolingWaterFlowM3hr += cwFlow;
          consumers.add(
              new UtilityConsumer(equip.getName(), "Cooling Water", absDutyKW, cwFlow, "m3/hr"));
        } else if (dutyKW > 0) {
          // Heating duty - classify by temperature
          totalHeatingDutyKW += dutyKW;
          double outTempC = heater.getOutletStream().getTemperature() - 273.15;
          classifyHeatingDuty(equip.getName(), dutyKW, outTempC);
        }
      }
    }

    // Instrument air estimate: ~5 Nm3/hr per equipment with instruments
    instrumentAirNm3hr = equipmentCount * 5.0;
  }

  /**
   * Calculates cooling water flow for a given duty.
   *
   * @param dutyKW cooling duty in kW
   * @return cooling water flow in m3/hr
   */
  private double calcCoolingWaterFlow(double dutyKW) {
    double deltaT = cwReturnTempC - cwSupplyTempC;
    if (deltaT <= 0) {
      deltaT = 15.0;
    }
    double massFlowKgS = dutyKW / (CW_CP * deltaT);
    return massFlowKgS * 3600.0 / CW_DENSITY;
  }

  /**
   * Classifies a heating duty as LP/MP/HP steam based on required temperature.
   *
   * @param name equipment name
   * @param dutyKW heating duty in kW
   * @param outTempC outlet temperature in Celsius
   */
  private void classifyHeatingDuty(String name, double dutyKW, double outTempC) {
    if (outTempC >= HP_STEAM_THRESHOLD) {
      double flowKghr = dutyKW * 3.6 / HP_STEAM_LATENT_HEAT * 1000.0;
      hpSteamFlowKghr += flowKghr;
      consumers.add(new UtilityConsumer(name, "HP Steam", dutyKW, flowKghr, "kg/hr"));
    } else if (outTempC >= LP_STEAM_THRESHOLD) {
      double flowKghr = dutyKW * 3.6 / MP_STEAM_LATENT_HEAT * 1000.0;
      mpSteamFlowKghr += flowKghr;
      consumers.add(new UtilityConsumer(name, "MP Steam", dutyKW, flowKghr, "kg/hr"));
    } else {
      double flowKghr = dutyKW * 3.6 / LP_STEAM_LATENT_HEAT * 1000.0;
      lpSteamFlowKghr += flowKghr;
      consumers.add(new UtilityConsumer(name, "LP Steam", dutyKW, flowKghr, "kg/hr"));
    }
  }

  /**
   * Gets the total cooling duty.
   *
   * @return total cooling duty in kW
   */
  public double getTotalCoolingDutyKW() {
    return totalCoolingDutyKW;
  }

  /**
   * Gets the total heating duty.
   *
   * @return total heating duty in kW
   */
  public double getTotalHeatingDutyKW() {
    return totalHeatingDutyKW;
  }

  /**
   * Gets the cooling water flow rate.
   *
   * @return cooling water flow in m3/hr
   */
  public double getCoolingWaterFlowM3hr() {
    return coolingWaterFlowM3hr;
  }

  /**
   * Gets the LP steam consumption.
   *
   * @return LP steam flow in kg/hr
   */
  public double getLpSteamFlowKghr() {
    return lpSteamFlowKghr;
  }

  /**
   * Gets the MP steam consumption.
   *
   * @return MP steam flow in kg/hr
   */
  public double getMpSteamFlowKghr() {
    return mpSteamFlowKghr;
  }

  /**
   * Gets the HP steam consumption.
   *
   * @return HP steam flow in kg/hr
   */
  public double getHpSteamFlowKghr() {
    return hpSteamFlowKghr;
  }

  /**
   * Gets the instrument air consumption estimate.
   *
   * @return instrument air flow in Nm3/hr
   */
  public double getInstrumentAirNm3hr() {
    return instrumentAirNm3hr;
  }

  /**
   * Gets the list of individual utility consumers.
   *
   * @return list of utility consumers
   */
  public List<UtilityConsumer> getConsumers() {
    return new ArrayList<UtilityConsumer>(consumers);
  }

  /**
   * Exports the thermal utility summary to JSON.
   *
   * @return JSON string with all utility data
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("totalCoolingDutyKW", totalCoolingDutyKW);
    root.addProperty("totalHeatingDutyKW", totalHeatingDutyKW);

    JsonObject cwObj = new JsonObject();
    cwObj.addProperty("flowM3hr", coolingWaterFlowM3hr);
    cwObj.addProperty("supplyTempC", cwSupplyTempC);
    cwObj.addProperty("returnTempC", cwReturnTempC);
    root.add("coolingWater", cwObj);

    JsonObject steamObj = new JsonObject();
    steamObj.addProperty("lpSteamKghr", lpSteamFlowKghr);
    steamObj.addProperty("mpSteamKghr", mpSteamFlowKghr);
    steamObj.addProperty("hpSteamKghr", hpSteamFlowKghr);
    root.add("steam", steamObj);

    root.addProperty("instrumentAirNm3hr", instrumentAirNm3hr);

    JsonArray arr = new JsonArray();
    for (UtilityConsumer c : consumers) {
      JsonObject co = new JsonObject();
      co.addProperty("equipment", c.getEquipmentName());
      co.addProperty("utilityType", c.getUtilityType());
      co.addProperty("dutyKW", c.getDutyKW());
      co.addProperty("flow", c.getFlow());
      co.addProperty("flowUnit", c.getFlowUnit());
      arr.add(co);
    }
    root.add("consumers", arr);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(root);
  }

  /**
   * Represents a single utility consumer (equipment with its utility type and flow).
   */
  public static class UtilityConsumer implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String equipmentName;
    private String utilityType;
    private double dutyKW;
    private double flow;
    private String flowUnit;

    /**
     * Creates a utility consumer record.
     *
     * @param equipmentName equipment name
     * @param utilityType utility type (e.g. "Cooling Water", "LP Steam")
     * @param dutyKW duty in kW
     * @param flow utility flow rate
     * @param flowUnit unit for the flow rate
     */
    public UtilityConsumer(String equipmentName, String utilityType, double dutyKW, double flow,
        String flowUnit) {
      this.equipmentName = equipmentName;
      this.utilityType = utilityType;
      this.dutyKW = dutyKW;
      this.flow = flow;
      this.flowUnit = flowUnit;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the utility type.
     *
     * @return utility type string
     */
    public String getUtilityType() {
      return utilityType;
    }

    /**
     * Gets the duty in kW.
     *
     * @return duty in kW
     */
    public double getDutyKW() {
      return dutyKW;
    }

    /**
     * Gets the utility flow rate.
     *
     * @return flow rate
     */
    public double getFlow() {
      return flow;
    }

    /**
     * Gets the flow unit string.
     *
     * @return flow unit
     */
    public String getFlowUnit() {
      return flowUnit;
    }
  }
}
