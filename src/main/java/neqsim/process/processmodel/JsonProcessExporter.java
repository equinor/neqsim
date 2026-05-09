package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.AntiSurge;
import neqsim.process.equipment.compressor.BoundaryCurve;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.component.ComponentEos;
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

  /** Map from materialized boundary stream identity to named fluid reference. */
  private final IdentityHashMap<StreamInterface, String> boundaryFluidRefMap =
      new IdentityHashMap<StreamInterface, String>();

  /** Map from explicit stream unit identity to named fluid reference. */
  private final IdentityHashMap<StreamInterface, String> streamUnitFluidRefMap =
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
    boundaryFluidRefMap.clear();
    streamUnitFluidRefMap.clear();
    processed.clear();

    List<ProcessEquipmentInterface> units = process.getUnitOperations();

    // Phase 1: Build stream reference map (stream identity -> "equipmentName.port")
    buildStreamRefMap(units);

    // Phase 1b: Materialize inlet streams owned by other ProcessSystems.
    List<StreamInterface> boundaryStreams = registerExternalInletStreams(units);
    registerStreamUnitFluids(units);

    // Phase 2: Extract fluid definition from the first feed stream
    SystemInterface fluid = findFeedFluid(units);

    // Phase 3: Build JSON
    JsonObject root = new JsonObject();

    // Fluid section
    if (fluid != null) {
      root.add("fluid", exportFluid(fluid));
    }

    JsonObject namedFluids = exportStreamUnitFluids(units);
    JsonObject boundaryFluids = exportBoundaryFluids(boundaryStreams);
    for (Map.Entry<String, com.google.gson.JsonElement> entry : boundaryFluids.entrySet()) {
      namedFluids.add(entry.getKey(), entry.getValue());
    }
    if (namedFluids.size() > 0) {
      root.add("fluids", namedFluids);
    }

    // Process section — ordered array of units
    JsonArray processArray = new JsonArray();
    for (StreamInterface boundaryStream : boundaryStreams) {
      JsonObject boundaryJson = exportBoundaryStream(boundaryStream);
      if (boundaryJson != null) {
        processArray.add(boundaryJson);
      }
    }
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
   * Registers inlet streams that are not produced by, or explicitly added to, the exported process.
   *
   * <p>
   * Multi-area {@link ProcessModel}s often share stream objects between {@link ProcessSystem}
   * instances. When an area is exported independently, those shared input streams have no local
   * producer and would otherwise become blank or unresolved inlet references in JSON. This method
   * assigns each external stream a synthetic boundary stream name and stores it in
   * {@link #streamRefMap}, allowing downstream equipment to wire to an explicit feed stream after
   * JSON import.
   * </p>
   *
   * @param units process units being exported
   * @return external inlet streams in deterministic export order
   */
  private List<StreamInterface> registerExternalInletStreams(
      List<ProcessEquipmentInterface> units) {
    List<StreamInterface> boundaryStreams = new ArrayList<StreamInterface>();
    IdentityHashMap<StreamInterface, Boolean> seen =
        new IdentityHashMap<StreamInterface, Boolean>();
    Map<String, Boolean> usedNames = new LinkedHashMap<String, Boolean>();

    for (ProcessEquipmentInterface unit : units) {
      usedNames.put(unit.getName(), Boolean.TRUE);
    }

    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        continue;
      }
      for (StreamInterface inlet : getInletStreams(unit)) {
        if (inlet == null || streamRefMap.containsKey(inlet) || seen.containsKey(inlet)) {
          continue;
        }
        if (inlet.getFluid() == null) {
          continue;
        }
        String boundaryName = uniqueBoundaryStreamName(inlet, usedNames);
        String fluidRef = boundaryName + "_fluid";
        streamRefMap.put(inlet, boundaryName);
        boundaryFluidRefMap.put(inlet, fluidRef);
        boundaryStreams.add(inlet);
        seen.put(inlet, Boolean.TRUE);
        usedNames.put(boundaryName, Boolean.TRUE);
      }
    }
    return boundaryStreams;
  }

  /**
   * Gets the inlet streams for an equipment unit with a reflection fallback for legacy equipment.
   *
   * @param unit equipment unit to inspect
   * @return list of non-null inlet streams, possibly empty
   */
  private List<StreamInterface> getInletStreams(ProcessEquipmentInterface unit) {
    List<StreamInterface> inlets = new ArrayList<StreamInterface>();
    try {
      List<StreamInterface> listedInlets = unit.getInletStreams();
      if (listedInlets != null) {
        for (StreamInterface inlet : listedInlets) {
          if (inlet != null) {
            inlets.add(inlet);
          }
        }
      }
    } catch (Exception e) {
      // Fall back below for legacy equipment without a robust getInletStreams implementation.
    }
    if (inlets.isEmpty()) {
      try {
        StreamInterface inlet =
            (StreamInterface) unit.getClass().getMethod("getInletStream").invoke(unit);
        if (inlet != null) {
          inlets.add(inlet);
        }
      } catch (Exception e) {
        // No single inlet accessor available.
      }
    }
    return inlets;
  }

  /**
   * Creates a unique synthetic boundary stream name for an external inlet stream.
   *
   * @param stream external inlet stream
   * @param usedNames names already used in the exported process
   * @return unique boundary stream name safe for dot-notation references
   */
  private String uniqueBoundaryStreamName(StreamInterface stream, Map<String, Boolean> usedNames) {
    String originalName = stream.getName();
    String baseName = originalName == null ? "stream" : originalName.trim();
    if (baseName.isEmpty()) {
      baseName = "stream";
    }
    baseName = "boundary_" + baseName.replaceAll("[^A-Za-z0-9_]+", "_");
    if (baseName.endsWith("_")) {
      baseName = baseName.substring(0, baseName.length() - 1);
    }
    if (baseName.isEmpty()) {
      baseName = "boundary_stream";
    }

    String candidate = baseName;
    int suffix = 2;
    while (usedNames.containsKey(candidate)) {
      candidate = baseName + "_" + suffix;
      suffix++;
    }
    return candidate;
  }

  /**
   * Exports named fluids for all materialized boundary streams.
   *
   * @param boundaryStreams external inlet streams to export
   * @return JSON object containing one named fluid per boundary stream
   */
  private JsonObject exportBoundaryFluids(List<StreamInterface> boundaryStreams) {
    JsonObject fluids = new JsonObject();
    for (StreamInterface boundaryStream : boundaryStreams) {
      String fluidRef = boundaryFluidRefMap.get(boundaryStream);
      if (fluidRef != null && boundaryStream.getFluid() != null) {
        fluids.add(fluidRef, exportFluid(boundaryStream.getFluid()));
      }
    }
    return fluids;
  }

  /**
   * Registers named fluid references for explicit stream units.
   *
   * @param units process units to inspect
   */
  private void registerStreamUnitFluids(List<ProcessEquipmentInterface> units) {
    Map<String, Boolean> usedRefs = new LinkedHashMap<String, Boolean>();
    for (ProcessEquipmentInterface unit : units) {
      if (!(unit instanceof StreamInterface)) {
        continue;
      }
      StreamInterface stream = (StreamInterface) unit;
      if (stream.getFluid() == null) {
        continue;
      }
      String baseRef = sanitizeFluidReference(unit.getName()) + "_fluid";
      String fluidRef = makeUniqueFluidReference(baseRef, usedRefs);
      streamUnitFluidRefMap.put(stream, fluidRef);
    }
  }

  /**
   * Exports named fluids for explicit stream units.
   *
   * @param units process units to inspect
   * @return JSON object containing one named fluid per stream unit
   */
  private JsonObject exportStreamUnitFluids(List<ProcessEquipmentInterface> units) {
    JsonObject fluids = new JsonObject();
    for (ProcessEquipmentInterface unit : units) {
      if (!(unit instanceof StreamInterface)) {
        continue;
      }
      StreamInterface stream = (StreamInterface) unit;
      String fluidRef = streamUnitFluidRefMap.get(stream);
      if (fluidRef != null && stream.getFluid() != null) {
        fluids.add(fluidRef, exportFluid(stream.getFluid()));
      }
    }
    return fluids;
  }

  /**
   * Sanitizes a stream name for use as a named-fluid key.
   *
   * @param name stream name to sanitize
   * @return sanitized fluid-reference base name
   */
  private String sanitizeFluidReference(String name) {
    String sanitized = name == null ? "stream" : name.replaceAll("[^A-Za-z0-9_]+", "_");
    sanitized = sanitized.replaceAll("_+", "_");
    while (sanitized.startsWith("_")) {
      sanitized = sanitized.substring(1);
    }
    while (sanitized.endsWith("_")) {
      sanitized = sanitized.substring(0, sanitized.length() - 1);
    }
    return sanitized.isEmpty() ? "stream" : sanitized;
  }

  /**
   * Makes a unique fluid-reference key.
   *
   * @param baseRef preferred fluid-reference key
   * @param usedRefs map of already-used keys
   * @return unique fluid-reference key
   */
  private String makeUniqueFluidReference(String baseRef, Map<String, Boolean> usedRefs) {
    String candidate = baseRef;
    int suffix = 2;
    while (usedRefs.containsKey(candidate)) {
      candidate = baseRef + "_" + suffix;
      suffix++;
    }
    usedRefs.put(candidate, Boolean.TRUE);
    return candidate;
  }

  /**
   * Exports a materialized boundary stream unit for an external inlet stream.
   *
   * @param boundaryStream external inlet stream to export as a local stream
   * @return stream JSON definition, or null if the stream was not registered
   */
  private JsonObject exportBoundaryStream(StreamInterface boundaryStream) {
    String streamName = streamRefMap.get(boundaryStream);
    if (streamName == null) {
      return null;
    }

    JsonObject json = new JsonObject();
    json.addProperty("type", "Stream");
    json.addProperty("name", streamName);
    String fluidRef = boundaryFluidRefMap.get(boundaryStream);
    if (fluidRef != null) {
      json.addProperty("fluidRef", fluidRef);
    }
    exportStreamProperties(json, boundaryStream, true);
    return json;
  }

  /**
   * Gets the JSON stream reference assigned to a stream during the most recent export.
   *
   * @param stream stream object to look up
   * @return JSON reference such as {@code feed}, {@code HP Sep.gasOut}, or {@code null} when the
   *         stream was not part of the most recent export
   */
  public String getStreamReference(StreamInterface stream) {
    return streamRefMap.get(stream);
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
    } else if (unit instanceof Manifold) {
      Manifold manifold = (Manifold) unit;
      List<StreamInterface> outlets = manifold.getOutletStreams();
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

    if (fluid.getPhase(0) != null) {
      fluidJson.addProperty("useVolumeCorrection", fluid.getPhase(0).useVolumeCorrection());
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
        addFiniteProperty(charComp, "normalBoilingPoint", comp.getNormalBoilingPoint());
        addFiniteProperty(charComp, "criticalVolume", comp.getCriticalVolume());
        addFiniteProperty(charComp, "parachor", comp.getParachorParameter());
        addFiniteProperty(charComp, "racketZ", comp.getRacketZ());
        addFiniteProperty(charComp, "volumeCorrection", comp.getVolumeCorrectionConst());
        addFiniteProperty(charComp, "volumeShift", comp.getVolumeCorrectionConst());
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

    fluidJson.add("componentProperties", exportComponentProperties(fluid));

    // Export BICs (binary interaction parameters) if any non-zero values
    exportBinaryInteractionParameters(fluidJson, fluid);

    return fluidJson;
  }

  /**
   * Exports component-level property overrides for all components in a fluid.
   *
   * @param fluid the fluid system to export
   * @return component property definitions for rebuilding E300-like fluids
   */
  private JsonArray exportComponentProperties(SystemInterface fluid) {
    JsonArray componentProperties = new JsonArray();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      componentProperties.add(exportComponentProperties(fluid.getPhase(0).getComponent(i)));
    }
    return componentProperties;
  }

  /**
   * Exports component-level properties that are not preserved by component name and composition
   * alone.
   *
   * @param component the component to export
   * @return a JSON object with component properties
   */
  private JsonObject exportComponentProperties(ComponentInterface component) {
    JsonObject props = new JsonObject();
    props.addProperty("name", component.getComponentName());
    props.addProperty("moleFraction", component.getz());
    addFiniteProperty(props, "molarMass", component.getMolarMass());
    addFiniteProperty(props, "density", component.getNormalLiquidDensity());
    addFiniteProperty(props, "Tc", component.getTC());
    addFiniteProperty(props, "Pc", component.getPC());
    addFiniteProperty(props, "acentricFactor", component.getAcentricFactor());
    addFiniteProperty(props, "normalBoilingPoint", component.getNormalBoilingPoint());
    addFiniteProperty(props, "criticalVolume", component.getCriticalVolume());
    addFiniteProperty(props, "parachor", component.getParachorParameter());
    addFiniteProperty(props, "racketZ", component.getRacketZ());
    addFiniteProperty(props, "volumeCorrection", component.getVolumeCorrectionConst());
    addFiniteProperty(props, "volumeShift", component.getVolumeCorrectionConst());
    if (component instanceof ComponentEos) {
      ComponentEos eosComponent = (ComponentEos) component;
      if (eosComponent.hasOmegaAOverride()) {
        addFiniteProperty(props, "omegaA", eosComponent.getOmegaAOverride());
      }
    }
    if (component.isIsTBPfraction()) {
      props.addProperty("isTBPfraction", true);
    }
    if (component.isIsPlusFraction()) {
      props.addProperty("isPlusFraction", true);
    }
    return props;
  }

  /**
   * Adds a numeric property when the value can be represented in standard JSON.
   *
   * @param json the object to update
   * @param name the property name
   * @param value the property value
   */
  private void addFiniteProperty(JsonObject json, String name, double value) {
    if (!Double.isNaN(value) && !Double.isInfinite(value)) {
      json.addProperty(name, value);
    }
  }

  /**
   * Exports binary interaction parameters (kij) from the fluid's mixing rule. Only exports non-zero
   * BICs to keep the JSON compact. The builder uses these to override the default database BICs
   * when reconstructing the fluid.
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
      StreamInterface stream = (StreamInterface) unit;
      String fluidRef = streamUnitFluidRefMap.get(stream);
      if (fluidRef != null) {
        json.addProperty("fluidRef", fluidRef);
      }
      exportStreamProperties(json, stream);
    } else if (unit instanceof Mixer) {
      exportMixerInlets(json, (Mixer) unit);
    } else if (unit instanceof Manifold) {
      exportManifoldInlets(json, (Manifold) unit);
    } else if (unit instanceof HeatExchanger) {
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
    if (unit instanceof HeatExchanger) {
      return "HeatExchanger";
    }
    if (unit instanceof Heater) {
      return "Heater";
    }
    if (unit instanceof Mixer) {
      return "Mixer";
    }
    if (unit instanceof Manifold) {
      return "Manifold";
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
    exportStreamProperties(json, stream, false);
  }

  /**
   * Exports Stream-specific properties: flowRate, temperature, pressure.
   *
   * @param json the unit JSON object to populate
   * @param stream the stream to export
   * @param includeZeroValues true to include zero-valued state fields for boundary streams
   */
  private void exportStreamProperties(JsonObject json, StreamInterface stream,
      boolean includeZeroValues) {
    JsonObject props = new JsonObject();

    double flowRate = stream.getFlowRate("kg/hr");
    if (flowRate > 0 || includeZeroValues) {
      JsonArray flowArr = new JsonArray();
      flowArr.add(flowRate);
      flowArr.add("kg/hr");
      props.add("flowRate", flowArr);
    }

    double temperature = stream.getTemperature("C");
    if (temperature != 0 || includeZeroValues) {
      JsonArray tempArr = new JsonArray();
      tempArr.add(temperature);
      tempArr.add("C");
      props.add("temperature", tempArr);
    }

    double pressure = stream.getPressure("bara");
    if (pressure > 0 || includeZeroValues) {
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
   * Exports Manifold inlets as an "inlets" array.
   *
   * @param json the unit JSON object
   * @param manifold the manifold
   */
  private void exportManifoldInlets(JsonObject json, Manifold manifold) {
    List<StreamInterface> inlets = manifold.getInletStreams();
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
      props.addProperty("speed", comp.getSpeed());
      props.addProperty("minimumSpeed", comp.getMinimumSpeed());
      props.addProperty("maximumSpeed", comp.getMaximumSpeed());
      props.addProperty("solveSpeed", comp.isSolveSpeed());
      props.addProperty("autoSpeedMode", comp.isAutoSpeedMode());
      double targetSpeed = comp.getTargetSpeed();
      if (targetSpeed > 0.0) {
        props.addProperty("targetSpeed", targetSpeed);
      }
      double polyEff = comp.getPolytropicEfficiency();
      if (polyEff > 0 && polyEff <= 1.0) {
        props.addProperty("polytropicEfficiency", polyEff);
      }
      if (comp.usePolytropicCalc()) {
        props.addProperty("usePolytropicCalc", true);
      }
      JsonObject compressorChart = exportCompressorChart(comp);
      if (compressorChart != null && compressorChart.size() > 0) {
        props.add("compressorChart", compressorChart);
      }
      JsonObject antiSurge = exportAntiSurge(comp.getAntiSurge());
      if (antiSurge != null && antiSurge.size() > 0) {
        props.add("antiSurge", antiSurge);
      }
    } else if (unit instanceof ThrottlingValve) {
      ThrottlingValve valve = (ThrottlingValve) unit;
      double outP = valve.getOutletPressure();
      if (outP > 0) {
        props.addProperty("outletPressure", outP);
      }
    } else if (unit instanceof HeatExchanger) {
      HeatExchanger hx = (HeatExchanger) unit;
      props.addProperty("UAvalue", hx.getUAvalue());
      JsonArray guessOutTemperature = new JsonArray();
      guessOutTemperature.add(hx.getGuessOutTemperature());
      guessOutTemperature.add(hx.guessOutTemperatureUnit);
      props.add("guessOutTemperature", guessOutTemperature);
    } else if (unit instanceof Heater || unit instanceof Cooler) {
      Heater heater = (Heater) unit;
      double pressureDrop = heater.getPressureDrop();
      if (pressureDrop != 0) {
        props.addProperty("pressureDrop", pressureDrop);
      }
      if (heater.hasOutletPressureSpecification()) {
        JsonArray outPArr = new JsonArray();
        outPArr.add(heater.getSpecifiedOutletPressure());
        outPArr.add(heater.getSpecifiedOutletPressureUnit());
        props.add("outletPressure", outPArr);
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
      double[] flowRates = splitter.getFlowRates();
      if (flowRates != null && flowRates.length > 0) {
        JsonArray flowRatesArr = new JsonArray();
        for (double flowRate : flowRates) {
          flowRatesArr.add(flowRate);
        }
        props.add("flowRates", flowRatesArr);
        props.addProperty("flowUnit", splitter.getFlowUnit());
      }
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
    } else if (unit instanceof Manifold) {
      Manifold manifold = (Manifold) unit;
      double[] factors = manifold.getSplitFactors();
      if (factors != null && factors.length > 0) {
        JsonArray factorsArr = new JsonArray();
        for (double f : factors) {
          factorsArr.add(f);
        }
        props.add("splitFactors", factorsArr);
      }
    } else if (unit instanceof Recycle) {
      Recycle recycle = (Recycle) unit;
      props.addProperty("flowTolerance", recycle.getFlowTolerance());
      props.addProperty("compositionTolerance", recycle.getCompositionTolerance());
      props.addProperty("temperatureTolerance", recycle.getTemperatureTolerance());
      props.addProperty("pressureTolerance", recycle.getPressureTolerance());
      props.addProperty("priority", recycle.getPriority());
      props.addProperty("maxIterations", recycle.getMaxIterations());
      AccelerationMethod accelerationMethod = recycle.getAccelerationMethod();
      if (accelerationMethod != null) {
        props.addProperty("accelerationMethod", accelerationMethod.name());
      }
      props.addProperty("wegsteinQMin", recycle.getWegsteinQMin());
      props.addProperty("wegsteinQMax", recycle.getWegsteinQMax());
      props.addProperty("wegsteinDelayIterations", recycle.getWegsteinDelayIterations());
      if (recycle.getDownstreamProperty() != null && !recycle.getDownstreamProperty().isEmpty()) {
        JsonArray downstreamProperties = new JsonArray();
        for (String property : recycle.getDownstreamProperty()) {
          downstreamProperties.add(property);
        }
        props.add("downstreamProperty", downstreamProperties);
      }
    } else if (unit instanceof PipeLineInterface) {
      PipeLineInterface pipe = (PipeLineInterface) unit;
      double length = pipe.getLength();
      if (length > 0) {
        props.addProperty("length", length);
      }
      double diameter = pipe.getDiameter();
      if (diameter > 0) {
        props.addProperty("diameter", diameter);
      }
      props.addProperty("elevation", pipe.getElevation());
      double pipeWallRoughness = pipe.getPipeWallRoughness();
      if (pipeWallRoughness > 0) {
        props.addProperty("pipeWallRoughness", pipeWallRoughness);
      }
      int numberOfIncrements = pipe.getNumberOfIncrements();
      if (numberOfIncrements > 0) {
        props.addProperty("numberOfIncrements", numberOfIncrements);
      }
    } else if (unit instanceof Calculator) {
      JsonObject calculator = exportCalculatorProperties((Calculator) unit);
      for (Map.Entry<String, com.google.gson.JsonElement> entry : calculator.entrySet()) {
        props.add(entry.getKey(), entry.getValue());
      }
    }

    return props.size() > 0 ? props : null;
  }

  /**
   * Exports compressor chart settings and curve arrays.
   *
   * @param compressor the compressor containing the chart
   * @return chart settings, speed curves, and boundary curves
   */
  private JsonObject exportCompressorChart(Compressor compressor) {
    JsonObject chartJson = new JsonObject();
    CompressorChartInterface chart = compressor.getCompressorChart();
    if (chart == null) {
      return chartJson;
    }
    chartJson.addProperty("chartType", compressor.getCompressorChartType());
    chartJson.addProperty("class", chart.getClass().getSimpleName());
    chartJson.addProperty("useCompressorChart", chart.isUseCompressorChart());
    chartJson.addProperty("headUnit", chart.getHeadUnit());
    chartJson.addProperty("useRealKappa", chart.useRealKappa());
    double[] chartConditions = chart.getChartConditions();
    if (chartConditions != null) {
      chartJson.add("chartConditions", toJsonArray(chartConditions));
    }
    double[] speeds = chart.getSpeeds();
    double[][] flows = chart.getFlows();
    double[][] heads = chart.getHeads();
    double[][] efficiencies = chart.getPolytropicEfficiencies();
    if (speeds != null && flows != null && heads != null && efficiencies != null) {
      chartJson.add("speeds", toJsonArray(speeds));
      chartJson.add("flows", toJsonMatrix(flows));
      chartJson.add("heads", toJsonMatrix(heads));
      chartJson.add("polytropicEfficiencies", toJsonMatrix(efficiencies));
    }
    JsonObject surgeCurve = exportBoundaryCurve(chart.getSurgeCurve());
    if (surgeCurve != null && surgeCurve.size() > 0) {
      chartJson.add("surgeCurve", surgeCurve);
    }
    JsonObject stoneWallCurve = exportBoundaryCurve((BoundaryCurve) chart.getStoneWallCurve());
    if (stoneWallCurve != null && stoneWallCurve.size() > 0) {
      chartJson.add("stoneWallCurve", stoneWallCurve);
    }
    return chartJson;
  }

  /**
   * Exports a compressor boundary curve.
   *
   * @param curve the boundary curve to export
   * @return JSON object with active state, flow values, and head values
   */
  private JsonObject exportBoundaryCurve(BoundaryCurve curve) {
    JsonObject curveJson = new JsonObject();
    if (curve == null) {
      return curveJson;
    }
    curveJson.addProperty("active", curve.isActive());
    double[] flow = curve.getFlow();
    double[] head = curve.getHead();
    if (flow != null && head != null) {
      curveJson.add("flow", toJsonArray(flow));
      curveJson.add("head", toJsonArray(head));
    }
    return curveJson;
  }

  /**
   * Exports anti-surge controller settings.
   *
   * @param antiSurge the anti-surge controller
   * @return JSON object with controller settings
   */
  private JsonObject exportAntiSurge(AntiSurge antiSurge) {
    JsonObject antiSurgeJson = new JsonObject();
    if (antiSurge == null) {
      return antiSurgeJson;
    }
    antiSurgeJson.addProperty("active", antiSurge.isActive());
    antiSurgeJson.addProperty("surge", antiSurge.isSurge());
    antiSurgeJson.addProperty("surgeControlFactor", antiSurge.getSurgeControlFactor());
    antiSurgeJson.addProperty("currentSurgeFraction", antiSurge.getCurrentSurgeFraction());
    antiSurgeJson.addProperty("controlStrategy", antiSurge.getControlStrategy().name());
    antiSurgeJson.addProperty("minimumRecycleFlow", antiSurge.getMinimumRecycleFlow());
    antiSurgeJson.addProperty("maximumRecycleFlow", antiSurge.getMaximumRecycleFlow());
    antiSurgeJson.addProperty("valvePosition", antiSurge.getValvePosition());
    antiSurgeJson.addProperty("valveResponseTime", antiSurge.getValveResponseTime());
    antiSurgeJson.addProperty("valveRateLimit", antiSurge.getValveRateLimit());
    antiSurgeJson.addProperty("targetValvePosition", antiSurge.getTargetValvePosition());
    antiSurgeJson.addProperty("surgeControlLineOffset", antiSurge.getSurgeControlLineOffset());
    antiSurgeJson.addProperty("useHotGasBypass", antiSurge.isUseHotGasBypass());
    antiSurgeJson.addProperty("hotGasBypassFlow", antiSurge.getHotGasBypassFlow());
    antiSurgeJson.addProperty("surgeWarningMargin", antiSurge.getSurgeWarningMargin());
    antiSurgeJson.addProperty("predictiveHorizon", antiSurge.getPredictiveHorizon());
    return antiSurgeJson;
  }

  /**
   * Exports calculator equipment references.
   *
   * @param calculator the calculator to export
   * @return JSON object with input and output equipment names
   */
  private JsonObject exportCalculatorProperties(Calculator calculator) {
    JsonObject calculatorJson = new JsonObject();
    JsonArray inputs = new JsonArray();
    for (ProcessEquipmentInterface input : calculator.getInputVariable()) {
      if (input != null) {
        inputs.add(input.getName());
      }
    }
    if (inputs.size() > 0) {
      calculatorJson.add("calculatorInputs", inputs);
    }
    ProcessEquipmentInterface output = calculator.getOutputVariable();
    if (output != null) {
      calculatorJson.addProperty("calculatorOutput", output.getName());
    }
    if (calculator.getName().toLowerCase().startsWith("anti surge calculator")) {
      calculatorJson.addProperty("calculationType", "antiSurge");
    }
    return calculatorJson;
  }

  /**
   * Converts a double array to a JSON array.
   *
   * @param values values to convert
   * @return JSON array containing the same values
   */
  private JsonArray toJsonArray(double[] values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Converts a double matrix to a JSON array of arrays.
   *
   * @param values matrix values to convert
   * @return JSON matrix containing the same values
   */
  private JsonArray toJsonMatrix(double[][] values) {
    JsonArray matrix = new JsonArray();
    for (double[] row : values) {
      matrix.add(toJsonArray(row));
    }
    return matrix;
  }
}
