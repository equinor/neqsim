package neqsim.process.util.report;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Generates industry-standard Heat and Material Balance (HMB) reports from a ProcessSystem.
 *
 * <p>
 * Produces two main sections:
 * <ul>
 * <li><b>Stream Table:</b> All stream conditions (T, P, flow, composition) in a tabular format</li>
 * <li><b>Equipment Summary:</b> Duties, power, pressure changes for all equipment</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * ProcessSystem process = ...;
 * process.run();
 *
 * HeatMaterialBalance hmb = new HeatMaterialBalance(process);
 * String json = hmb.toJson();
 * String csv = hmb.streamTableToCSV();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see ProcessSystem
 */
public class HeatMaterialBalance implements Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(HeatMaterialBalance.class);

  /** The process system to report on. */
  private final ProcessSystem process;

  /** Unit for temperature display. */
  private String temperatureUnit = "C";

  /** Unit for pressure display. */
  private String pressureUnit = "bara";

  /** Unit for mass flow display. */
  private String flowUnit = "kg/hr";

  /**
   * Creates an HMB report generator for the given process.
   *
   * @param process the process system (must have been run)
   */
  public HeatMaterialBalance(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("ProcessSystem cannot be null");
    }
    this.process = process;
  }

  /**
   * Sets the temperature unit for display.
   *
   * @param unit temperature unit (e.g., "C", "K", "F")
   * @return this for chaining
   */
  public HeatMaterialBalance setTemperatureUnit(String unit) {
    this.temperatureUnit = unit;
    return this;
  }

  /**
   * Sets the pressure unit for display.
   *
   * @param unit pressure unit (e.g., "bara", "barg", "psi", "kPa")
   * @return this for chaining
   */
  public HeatMaterialBalance setPressureUnit(String unit) {
    this.pressureUnit = unit;
    return this;
  }

  /**
   * Sets the mass flow unit for display.
   *
   * @param unit flow unit (e.g., "kg/hr", "kg/sec", "lb/hr")
   * @return this for chaining
   */
  public HeatMaterialBalance setFlowUnit(String unit) {
    this.flowUnit = unit;
    return this;
  }

  /**
   * Collects all unique streams from the process system.
   *
   * @return ordered list of all streams
   */
  public List<StreamInterface> getAllStreams() {
    List<StreamInterface> streams = new ArrayList<>();
    List<String> seenNames = new ArrayList<>();

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      for (StreamInterface s : unit.getInletStreams()) {
        if (!seenNames.contains(s.getName())) {
          streams.add(s);
          seenNames.add(s.getName());
        }
      }
      for (StreamInterface s : unit.getOutletStreams()) {
        if (!seenNames.contains(s.getName())) {
          streams.add(s);
          seenNames.add(s.getName());
        }
      }
    }
    return streams;
  }

  /**
   * Creates a stream data map for one stream.
   *
   * @param stream the stream to extract data from
   * @return map of property name to value
   */
  public Map<String, Object> getStreamData(StreamInterface stream) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", stream.getName());

    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      data.put("status", "no fluid");
      return data;
    }

    double tempC = fluid.getTemperature("C");
    double pressure = fluid.getPressure(pressureUnit);
    double totalMolarFlow = fluid.getTotalNumberOfMoles();

    data.put("temperature_C", round(tempC, 2));
    data.put("pressure_" + pressureUnit, round(pressure, 4));
    data.put("totalMolarFlow_mol_per_sec", round(totalMolarFlow, 4));

    try {
      data.put("massFlow_" + flowUnit.replace("/", "_per_"),
          round(stream.getFlowRate(flowUnit), 2));
    } catch (Exception e) {
      logger.debug("Could not get flow rate for stream {}: {}", stream.getName(), e.getMessage());
    }

    data.put("numberOfPhases", fluid.getNumberOfPhases());

    try {
      data.put("molarMass_kg_per_mol", round(fluid.getMolarMass("kg/mol"), 6));
    } catch (Exception e) {
      logger.debug("Could not get molar mass for stream {}", stream.getName());
    }

    try {
      data.put("density_kg_per_m3", round(fluid.getDensity("kg/m3"), 4));
    } catch (Exception e) {
      logger.debug("Could not get density for stream {}", stream.getName());
    }

    try {
      data.put("enthalpy_J_per_mol", round(fluid.getEnthalpy("J/mol"), 2));
    } catch (Exception e) {
      logger.debug("Could not get enthalpy for stream {}", stream.getName());
    }

    try {
      data.put("entropy_J_per_molK", round(fluid.getEntropy("J/molK"), 4));
    } catch (Exception e) {
      logger.debug("Could not get entropy for stream {}", stream.getName());
    }

    // Vapour fraction
    if (fluid.getNumberOfPhases() > 0) {
      data.put("vapourFraction", round(fluid.getBeta(0), 6));
    }

    // Composition
    Map<String, Double> composition = new LinkedHashMap<>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      String compName = fluid.getComponent(i).getComponentName();
      double moleFrac = fluid.getComponent(i).getz();
      if (moleFrac > 1e-15) {
        composition.put(compName, round(moleFrac, 8));
      }
    }
    data.put("composition_mole_fraction", composition);

    return data;
  }

  /**
   * Creates an equipment summary for one unit operation.
   *
   * @param unit the equipment to summarize
   * @return map of property name to value, or null if no relevant data
   */
  public Map<String, Object> getEquipmentData(ProcessEquipmentInterface unit) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", unit.getName());
    data.put("type", unit.getClass().getSimpleName());

    if (unit instanceof Compressor) {
      Compressor comp = (Compressor) unit;
      data.put("power_kW", round(comp.getPower("kW"), 2));
      data.put("inletPressure_bara", round(comp.getInletStream().getPressure("bara"), 2));
      data.put("outletPressure_bara", round(comp.getOutletStream().getPressure("bara"), 2));
      data.put("polytropicEfficiency", round(comp.getPolytropicEfficiency(), 4));
      return data;
    }

    if (unit instanceof Pump) {
      Pump pump = (Pump) unit;
      data.put("power_kW", round(pump.getPower("kW"), 2));
      return data;
    }

    if (unit instanceof Heater) {
      Heater heater = (Heater) unit;
      data.put("duty_kW", round(heater.getDuty() / 1000.0, 2));
      if (heater.getInletStream() != null && heater.getOutletStream() != null) {
        data.put("inletTemperature_C", round(heater.getInletStream().getTemperature("C"), 2));
        data.put("outletTemperature_C", round(heater.getOutletStream().getTemperature("C"), 2));
      }
      return data;
    }

    if (unit instanceof HeatExchanger) {
      HeatExchanger hx = (HeatExchanger) unit;
      data.put("duty_kW", round(hx.getDuty() / 1000.0, 2));
      data.put("UAvalue", round(hx.getUAvalue(), 2));
      return data;
    }

    // Generic equipment with inlet/outlet
    List<StreamInterface> inlets = unit.getInletStreams();
    List<StreamInterface> outlets = unit.getOutletStreams();
    if (!inlets.isEmpty() && !outlets.isEmpty()) {
      data.put("inletStreams", inlets.size());
      data.put("outletStreams", outlets.size());
      return data;
    }

    return null;
  }

  /**
   * Generates the complete HMB report as a JSON string.
   *
   * @return JSON representation of the HMB
   */
  public String toJson() {
    JsonObject report = new JsonObject();
    report.addProperty("reportType", "Heat and Material Balance");
    report.addProperty("processName", process.getName());
    report.addProperty("temperatureUnit", temperatureUnit);
    report.addProperty("pressureUnit", pressureUnit);
    report.addProperty("flowUnit", flowUnit);

    // Stream table
    JsonArray streamArray = new JsonArray();
    for (StreamInterface stream : getAllStreams()) {
      Map<String, Object> streamData = getStreamData(stream);
      String streamJson =
          new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(streamData);
      streamArray.add(com.google.gson.JsonParser.parseString(streamJson));
    }
    report.add("streamTable", streamArray);

    // Equipment summary
    JsonArray equipArray = new JsonArray();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Map<String, Object> equipData = getEquipmentData(unit);
      if (equipData != null) {
        String equipJson =
            new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(equipData);
        equipArray.add(com.google.gson.JsonParser.parseString(equipJson));
      }
    }
    report.add("equipmentSummary", equipArray);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  /**
   * Generates a CSV-formatted stream table.
   *
   * <p>
   * Columns: Stream Name, Temperature (C), Pressure (bara), Mass Flow (unit), Molar Mass, Density,
   * Vapour Fraction, then one column per component.
   * </p>
   *
   * @return CSV string of the stream table
   */
  public String streamTableToCSV() {
    List<StreamInterface> streams = getAllStreams();
    if (streams.isEmpty()) {
      return "No streams found";
    }

    // Collect all component names across all streams
    List<String> allComponents = new ArrayList<>();
    for (StreamInterface stream : streams) {
      SystemInterface fluid = stream.getFluid();
      if (fluid != null) {
        for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
          String name = fluid.getComponent(i).getComponentName();
          if (!allComponents.contains(name)) {
            allComponents.add(name);
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    // Header
    sb.append("Stream Name,Temperature (C),Pressure (").append(pressureUnit).append("),");
    sb.append("Mass Flow (").append(flowUnit).append("),");
    sb.append("Molar Mass (kg/mol),Density (kg/m3),Vapour Fraction");
    for (String comp : allComponents) {
      sb.append(",").append(comp);
    }
    sb.append("\n");

    // Data rows
    for (StreamInterface stream : streams) {
      Map<String, Object> data = getStreamData(stream);
      sb.append(data.get("name")).append(",");
      sb.append(data.getOrDefault("temperature_C", "")).append(",");
      sb.append(data.getOrDefault("pressure_" + pressureUnit, "")).append(",");
      sb.append(data.getOrDefault("massFlow_" + flowUnit.replace("/", "_per_"), "")).append(",");
      sb.append(data.getOrDefault("molarMass_kg_per_mol", "")).append(",");
      sb.append(data.getOrDefault("density_kg_per_m3", "")).append(",");
      sb.append(data.getOrDefault("vapourFraction", ""));

      @SuppressWarnings("unchecked")
      Map<String, Double> composition = (Map<String, Double>) data.get("composition_mole_fraction");
      for (String comp : allComponents) {
        sb.append(",");
        if (composition != null && composition.containsKey(comp)) {
          sb.append(composition.get(comp));
        } else {
          sb.append("0.0");
        }
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Rounds a double to the specified number of decimal places.
   *
   * @param value the value to round
   * @param places number of decimal places
   * @return rounded value
   */
  private static double round(double value, int places) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    double factor = Math.pow(10, places);
    return Math.round(value * factor) / factor;
  }
}
