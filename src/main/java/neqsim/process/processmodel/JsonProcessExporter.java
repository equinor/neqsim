package neqsim.process.processmodel;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Exports a {@link ProcessSystem} to the JSON schema consumed by {@link JsonProcessBuilder}.
 *
 * <p>
 * The exported JSON is round-trippable: {@code ProcessSystem -> toJson() -> JsonProcessBuilder ->
 * ProcessSystem}. Streams are serialized first, followed by equipment in topological order. Each
 * equipment unit references its inlet stream(s) by name using dot-notation for specific outlet
 * ports (e.g. {@code "HP Sep.gasOut"}).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class JsonProcessExporter {

  /** Map from stream identity to the reference string that reaches it. */
  private final IdentityHashMap<StreamInterface, String> streamRefMap =
      new IdentityHashMap<StreamInterface, String>();

  /** Map from equipment name to processed flag. */
  private final Map<String, Boolean> processed = new LinkedHashMap<String, Boolean>();

  /**
   * Exports a ProcessSystem to a JSON string that can be consumed by
   * {@link JsonProcessBuilder#build(String)}.
   *
   * @param process the process system to export
   * @return JSON string in the JsonProcessBuilder schema
   */
  public String toJson(ProcessSystem process) {
    return toJson(process, true);
  }

  /**
   * Exports a ProcessSystem to a JSON string.
   *
   * @param process the process system to export
   * @param prettyPrint whether to format the JSON with indentation
   * @return JSON string in the JsonProcessBuilder schema
   */
  public String toJson(ProcessSystem process, boolean prettyPrint) {
    JsonObject root = toJsonObject(process);
    Gson gson;
    if (prettyPrint) {
      gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    } else {
      gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    }
    return gson.toJson(root);
  }

  /**
   * Exports a ProcessSystem to a JsonObject.
   *
   * @param process the process system to export
   * @return JsonObject in the JsonProcessBuilder schema
   */
  public JsonObject toJsonObject(ProcessSystem process) {
    streamRefMap.clear();
    processed.clear();

    List<ProcessEquipmentInterface> units = process.getUnitOperations();

    // Phase 1: Build stream reference map (stream identity -> "equipmentName.port")
    buildStreamRefMap(units);

    // Phase 2: Extract fluid definition from the first feed stream
    SystemInterface fluid = findFeedFluid(units);

    // Phase 3: Build JSON
    JsonObject root = new JsonObject();

    // Fluid section
    if (fluid != null) {
      root.add("fluid", exportFluid(fluid));
    }

    // Process section — ordered array of units
    JsonArray processArray = new JsonArray();
    for (ProcessEquipmentInterface unit : units) {
      JsonObject unitJson = exportUnit(unit);
      if (unitJson != null) {
        processArray.add(unitJson);
      }
    }
    root.add("process", processArray);

    return root;
  }

  /**
   * Builds a mapping from stream object identity to string reference. Feed streams (top-level
   * Stream objects) map to their name. Outlet streams from equipment map to
   * "equipmentName.portName".
   *
   * @param units the ordered list of unit operations
   */
  private void buildStreamRefMap(List<ProcessEquipmentInterface> units) {
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        streamRefMap.put((StreamInterface) unit, unit.getName());
      } else {
        mapOutletStreams(unit);
      }
    }
  }

  /**
   * Maps outlet streams of an equipment unit to their reference strings.
   *
   * @param unit the equipment unit
   */
  private void mapOutletStreams(ProcessEquipmentInterface unit) {
    String name = unit.getName();

    if (unit instanceof Separator) {
      Separator sep = (Separator) unit;
      StreamInterface gasOut = sep.getGasOutStream();
      StreamInterface liqOut = sep.getLiquidOutStream();
      if (gasOut != null) {
        streamRefMap.put(gasOut, name + ".gasOut");
      }
      if (liqOut != null) {
        streamRefMap.put(liqOut, name + ".liquidOut");
      }
      if (unit instanceof ThreePhaseSeparator) {
        ThreePhaseSeparator sep3 = (ThreePhaseSeparator) unit;
        try {
          StreamInterface waterOut = sep3.getWaterOutStream();
          if (waterOut != null) {
            streamRefMap.put(waterOut, name + ".waterOut");
          }
        } catch (Exception e) {
          // no water outlet
        }
      }
    } else if (unit instanceof ComponentSplitter) {
      ComponentSplitter compSplitter = (ComponentSplitter) unit;
      List<StreamInterface> outlets = compSplitter.getOutletStreams();
      if (outlets != null) {
        for (int i = 0; i < outlets.size(); i++) {
          if (outlets.get(i) != null) {
            streamRefMap.put(outlets.get(i), name + ".split" + i);
          }
        }
      }
    } else if (unit instanceof Splitter) {
      Splitter splitter = (Splitter) unit;
      List<StreamInterface> outlets = splitter.getOutletStreams();
      if (outlets != null) {
        for (int i = 0; i < outlets.size(); i++) {
          if (outlets.get(i) != null) {
            streamRefMap.put(outlets.get(i), name + ".split" + i);
          }
        }
      }
    } else if (unit instanceof HeatExchanger) {
      HeatExchanger hx = (HeatExchanger) unit;
      try {
        StreamInterface out0 = hx.getOutStream(0);
        StreamInterface out1 = hx.getOutStream(1);
        if (out0 != null) {
          streamRefMap.put(out0, name + ".outlet");
        }
        if (out1 != null) {
          streamRefMap.put(out1, name + ".outlet1");
        }
      } catch (Exception e) {
        // fallback
        List<StreamInterface> outlets = unit.getOutletStreams();
        if (outlets != null) {
          for (int i = 0; i < outlets.size(); i++) {
            if (outlets.get(i) != null) {
              streamRefMap.put(outlets.get(i), name + ".outlet");
            }
          }
        }
      }
    } else if (unit instanceof Mixer) {
      Mixer mixer = (Mixer) unit;
      List<StreamInterface> outlets = mixer.getOutletStreams();
      if (outlets != null && !outlets.isEmpty() && outlets.get(0) != null) {
        streamRefMap.put(outlets.get(0), name + ".outlet");
      }
    } else {
      // Generic two-port equipment (Compressor, Valve, Heater, Cooler, Pump, etc.)
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets != null && !outlets.isEmpty() && outlets.get(0) != null) {
        streamRefMap.put(outlets.get(0), name + ".outlet");
      }
    }
  }

  /**
   * Finds the first feed stream's fluid for the fluid section.
   *
   * @param units the ordered list of unit operations
   * @return the fluid from the first Stream, or null
   */
  private SystemInterface findFeedFluid(List<ProcessEquipmentInterface> units) {
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        SystemInterface fluid = unit.getFluid();
        if (fluid != null) {
          return fluid;
        }
      }
    }
    return null;
  }

  /**
   * Exports a fluid (SystemInterface) to a JSON object matching the JsonProcessBuilder schema.
   *
   * <p>
   * For characterized (TBP/plus) fractions, exports critical properties (Tc, Pc, acentric factor,
   * molar mass, density) so the fluid can be reconstructed without the original E300/PVT source
   * file. Binary interaction parameters (BICs) are exported when any non-zero value is present.
   * </p>
   *
   * @param fluid the fluid to export
   * @return JsonObject with model, temperature, pressure, mixingRule, components, and optionally
   *         characterizedComponents and binaryInteractionParameters
   */
  private JsonObject exportFluid(SystemInterface fluid) {
    JsonObject fluidJson = new JsonObject();

    // Map model name to the short form used by JsonProcessBuilder
    fluidJson.addProperty("model", mapModelName(fluid.getModelName()));
    fluidJson.addProperty("temperature", fluid.getTemperature());
    fluidJson.addProperty("pressure", fluid.getPressure());

    String mixingRule = fluid.getMixingRuleName();
    if (mixingRule != null && !mixingRule.trim().isEmpty()) {
      fluidJson.addProperty("mixingRule", mixingRule);
    } else {
      fluidJson.addProperty("mixingRule", "classic");
    }

    if (fluid.doMultiPhaseCheck()) {
      fluidJson.addProperty("multiPhaseCheck", true);
    }

    // Components: separate database components from characterized fractions
    JsonObject components = new JsonObject();
    JsonArray characterizedComponents = new JsonArray();
    boolean hasCharacterized = false;

    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      ComponentInterface comp = fluid.getPhase(0).getComponent(i);
      String compName = comp.getComponentName();
      double moleFraction = comp.getz();

      if (comp.isIsTBPfraction() || comp.isIsPlusFraction()) {
        // Characterized fraction — export full properties for reconstruction
        JsonObject charComp = new JsonObject();
        charComp.addProperty("name", compName);
        charComp.addProperty("moleFraction", moleFraction);
        charComp.addProperty("molarMass", comp.getMolarMass());
        charComp.addProperty("density", comp.getNormalLiquidDensity());
        charComp.addProperty("Tc", comp.getTC());
        charComp.addProperty("Pc", comp.getPC());
        charComp.addProperty("acentricFactor", comp.getAcentricFactor());
        if (comp.isIsPlusFraction()) {
          charComp.addProperty("isPlusFraction", true);
        }
        characterizedComponents.add(charComp);
        hasCharacterized = true;
      } else {
        // Standard database component
        components.addProperty(compName, moleFraction);
      }
    }

    fluidJson.add("components", components);
    if (hasCharacterized) {
      fluidJson.add("characterizedComponents", characterizedComponents);
    }

    // Export BICs (binary interaction parameters) if any non-zero values
    exportBinaryInteractionParameters(fluidJson, fluid);

    return fluidJson;
  }

  /**
   * Exports binary interaction parameters (kij) from the fluid's mixing rule. Only exports
   * non-zero BICs to keep the JSON compact. The builder uses these to override the default
   * database BICs when reconstructing the fluid.
   *
   * @param fluidJson the fluid JSON object to add BICs to
   * @param fluid the fluid system
   */
  private void exportBinaryInteractionParameters(JsonObject fluidJson, SystemInterface fluid) {
    PhaseInterface phase = fluid.getPhase(0);
    if (phase == null) {
      return;
    }

    Object mixRule = phase.getMixingRule();
    if (!(mixRule instanceof EosMixingRulesInterface)) {
      return;
    }

    EosMixingRulesInterface eosMixRule = (EosMixingRulesInterface) mixRule;
    int nComp = fluid.getNumberOfComponents();
    JsonArray bicsArray = new JsonArray();
    boolean hasNonZero = false;

    for (int i = 0; i < nComp; i++) {
      for (int j = i + 1; j < nComp; j++) {
        double kij = eosMixRule.getBinaryInteractionParameter(i, j);
        if (Math.abs(kij) > 1e-15) {
          JsonObject bic = new JsonObject();
          bic.addProperty("comp1", fluid.getPhase(0).getComponent(i).getComponentName());
          bic.addProperty("comp2", fluid.getPhase(0).getComponent(j).getComponentName());
          bic.addProperty("kij", kij);
          bicsArray.add(bic);
          hasNonZero = true;
        }
      }
    }

    if (hasNonZero) {
      fluidJson.add("binaryInteractionParameters", bicsArray);
    }
  }

  /**
   * Maps internal NeqSim model names to the short identifiers used by JsonProcessBuilder.
   *
   * @param modelName the internal model name (e.g. "SRK-EOS")
   * @return short identifier (e.g. "SRK")
   */
  private String mapModelName(String modelName) {
    if (modelName == null) {
      return "SRK";
    }
    if (modelName.contains("SRK") && modelName.contains("CPA")) {
      return "CPA";
    }
    if (modelName.contains("SRK") || modelName.contains("ScRK")) {
      return "SRK";
    }
    if (modelName.contains("PR") && modelName.contains("UMR")) {
      return "UMRPRU";
    }
    if (modelName.contains("PR")) {
      return "PR";
    }
    if (modelName.contains("GERG2008") || modelName.contains("GERG-2008")) {
      return "GERG2008";
    }
    if (modelName.contains("PC-SAFT") || modelName.contains("PCSAFT")) {
      return "PCSAFT";
    }
    return "SRK";
  }

  /**
   * Exports a single equipment unit to a JSON object.
   *
   * @param unit the equipment to export
   * @return JsonObject describing the unit, or null if the unit should be skipped
   */
  private JsonObject exportUnit(ProcessEquipmentInterface unit) {
    JsonObject json = new JsonObject();
    String typeName = getTypeName(unit);
    json.addProperty("type", typeName);
    json.addProperty("name", unit.getName());

    if (unit instanceof StreamInterface) {
      exportStreamProperties(json, (StreamInterface) unit);
    } else if (unit instanceof Mixer) {
      exportMixerInlets(json, (Mixer) unit);
    } else if (unit instanceof HeatExchanger && !(unit instanceof Heater)
        && !(unit instanceof Cooler)) {
      exportHeatExchangerInlets(json, (HeatExchanger) unit);
    } else {
      // Single-inlet equipment — resolve inlet stream reference
      List<StreamInterface> inlets = unit.getInletStreams();
      StreamInterface inlet = null;
      if (inlets != null && !inlets.isEmpty()) {
        inlet = inlets.get(0);
      }
      // Fallback: try reflection for equipment that doesn't override getInletStreams()
      if (inlet == null) {
        try {
          inlet = (StreamInterface) unit.getClass().getMethod("getInletStream").invoke(unit);
        } catch (Exception e) {
          // no getInletStream method
        }
      }
      if (inlet != null) {
        String ref = streamRefMap.get(inlet);
        if (ref != null) {
          json.addProperty("inlet", ref);
        }
      }
    }

    // Export equipment-specific properties
    JsonObject props = exportProperties(unit);
    if (props != null && props.size() > 0) {
      json.add("properties", props);
    }

    return json;
  }

  /**
   * Gets the type name string for an equipment unit matching the JsonProcessBuilder type names.
   *
   * @param unit the equipment
   * @return the type name string
   */
  private String getTypeName(ProcessEquipmentInterface unit) {
    if (unit instanceof StreamInterface) {
      return "Stream";
    }
    if (unit instanceof ThreePhaseSeparator) {
      return "ThreePhaseSeparator";
    }
    if (unit instanceof Separator) {
      return "Separator";
    }
    if (unit instanceof Expander) {
      return "Expander";
    }
    if (unit instanceof Compressor) {
      return "Compressor";
    }
    if (unit instanceof ThrottlingValve) {
      return "ThrottlingValve";
    }
    if (unit instanceof Cooler) {
      return "Cooler";
    }
    if (unit instanceof Heater) {
      return "Heater";
    }
    if (unit instanceof HeatExchanger) {
      return "HeatExchanger";
    }
    if (unit instanceof Mixer) {
      return "Mixer";
    }
    if (unit instanceof ComponentSplitter) {
      return "ComponentSplitter";
    }
    if (unit instanceof Splitter) {
      return "Splitter";
    }
    if (unit instanceof Pump) {
      return "Pump";
    }
    if (unit instanceof Recycle) {
      return "Recycle";
    }
    if (unit instanceof Adjuster) {
      return "Adjuster";
    }
    if (unit instanceof AdiabaticPipe) {
      return "AdiabaticPipe";
    }
    // Fallback to class simple name
    return unit.getClass().getSimpleName();
  }

  /**
   * Exports Stream-specific properties: flowRate, temperature, pressure.
   *
   * @param json the unit JSON object to populate
   * @param stream the stream to export
   */
  private void exportStreamProperties(JsonObject json, StreamInterface stream) {
    JsonObject props = new JsonObject();

    double flowRate = stream.getFlowRate("kg/hr");
    if (flowRate > 0) {
      JsonArray flowArr = new JsonArray();
      flowArr.add(flowRate);
      flowArr.add("kg/hr");
      props.add("flowRate", flowArr);
    }

    double temperature = stream.getTemperature("C");
    if (temperature != 0) {
      JsonArray tempArr = new JsonArray();
      tempArr.add(temperature);
      tempArr.add("C");
      props.add("temperature", tempArr);
    }

    double pressure = stream.getPressure("bara");
    if (pressure > 0) {
      JsonArray pressArr = new JsonArray();
      pressArr.add(pressure);
      pressArr.add("bara");
      props.add("pressure", pressArr);
    }

    if (props.size() > 0) {
      json.add("properties", props);
    }
  }

  /**
   * Exports Mixer inlets as an "inlets" array.
   *
   * @param json the unit JSON object
   * @param mixer the mixer
   */
  private void exportMixerInlets(JsonObject json, Mixer mixer) {
    List<StreamInterface> inlets = mixer.getInletStreams();
    if (inlets != null && !inlets.isEmpty()) {
      JsonArray inletsArr = new JsonArray();
      for (StreamInterface inlet : inlets) {
        String ref = streamRefMap.get(inlet);
        if (ref != null) {
          inletsArr.add(ref);
        }
      }
      if (inletsArr.size() > 0) {
        json.add("inlets", inletsArr);
      }
    }
  }

  /**
   * Exports HeatExchanger inlets as an "inlets" array (shell-side + tube-side).
   *
   * @param json the unit JSON object
   * @param hx the heat exchanger
   */
  private void exportHeatExchangerInlets(JsonObject json, HeatExchanger hx) {
    List<StreamInterface> inlets = hx.getInletStreams();
    if (inlets != null && !inlets.isEmpty()) {
      JsonArray inletsArr = new JsonArray();
      for (StreamInterface inlet : inlets) {
        String ref = streamRefMap.get(inlet);
        if (ref != null) {
          inletsArr.add(ref);
        }
      }
      if (inletsArr.size() > 0) {
        json.add("inlets", inletsArr);
      }
    }
  }

  /**
   * Exports equipment-specific design properties (outlet pressure, efficiency, split factors etc.).
   *
   * @param unit the equipment
   * @return JsonObject with properties, or null if no properties to export
   */
  private JsonObject exportProperties(ProcessEquipmentInterface unit) {
    JsonObject props = new JsonObject();

    if (unit instanceof Compressor) {
      Compressor comp = (Compressor) unit;
      double outP = comp.getOutletPressure();
      if (outP > 0) {
        props.addProperty("outletPressure", outP);
      }
      double polyEff = comp.getPolytropicEfficiency();
      if (polyEff > 0 && polyEff <= 1.0) {
        props.addProperty("polytropicEfficiency", polyEff);
      }
      if (comp.usePolytropicCalc()) {
        props.addProperty("usePolytropicCalc", true);
      }
    } else if (unit instanceof ThrottlingValve) {
      ThrottlingValve valve = (ThrottlingValve) unit;
      double outP = valve.getOutletPressure();
      if (outP > 0) {
        props.addProperty("outletPressure", outP);
      }
    } else if (unit instanceof Heater || unit instanceof Cooler) {
      Heater heater = (Heater) unit;
      double pressureDrop = heater.getPressureDrop();
      if (pressureDrop != 0) {
        props.addProperty("pressureDrop", pressureDrop);
      }
      // Export outlet temperature from the outlet stream
      List<StreamInterface> heaterOuts = heater.getOutletStreams();
      if (heaterOuts != null && !heaterOuts.isEmpty() && heaterOuts.get(0) != null) {
        double outT = heaterOuts.get(0).getTemperature("C");
        JsonArray outTArr = new JsonArray();
        outTArr.add(outT);
        outTArr.add("C");
        props.add("outletTemperature", outTArr);
      }
    } else if (unit instanceof Pump) {
      Pump pump = (Pump) unit;
      double outP = pump.getOutletPressure();
      if (outP > 0) {
        props.addProperty("outletPressure", outP);
      }
    } else if (unit instanceof Splitter) {
      Splitter splitter = (Splitter) unit;
      double[] factors = splitter.getSplitFactors();
      if (factors != null && factors.length > 0) {
        JsonArray factorsArr = new JsonArray();
        for (double f : factors) {
          factorsArr.add(f);
        }
        props.add("splitFactors", factorsArr);
      }
    } else if (unit instanceof ComponentSplitter) {
      ComponentSplitter compSplitter = (ComponentSplitter) unit;
      double[] factors = compSplitter.getSplitFactors();
      if (factors != null && factors.length > 0) {
        JsonArray factorsArr = new JsonArray();
        for (double f : factors) {
          factorsArr.add(f);
        }
        props.add("splitFactors", factorsArr);
      }
    } else if (unit instanceof AdiabaticPipe) {
      AdiabaticPipe pipe = (AdiabaticPipe) unit;
      double length = pipe.getLength();
      if (length > 0) {
        props.addProperty("length", length);
      }
      double diameter = pipe.getDiameter();
      if (diameter > 0) {
        props.addProperty("diameter", diameter);
      }
    }

    return props.size() > 0 ? props : null;
  }
}
