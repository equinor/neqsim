package neqsim.process.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Runs fast water-hammer and liquid-hammer screening studies from process, STID, and tag data.
 *
 * <p>
 * The study facade converts a compact JSON specification into a {@link WaterHammerPipe}, applies
 * route geometry from a pipe object or STID-style segment list, optionally overrides operating data
 * from tagreader values, runs valve-closure transients, and returns a structured JSON result for
 * notebooks, agents, and MCP tools.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class WaterHammerStudy {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
  private static final double DEFAULT_MINOR_LOSS_FRICTION_FACTOR = 0.02;
  private static final int MAX_TIME_STEPS = 100000;

  /**
   * Private constructor for utility class.
   */
  private WaterHammerStudy() {}

  /**
   * Runs a water-hammer study from a JSON input specification.
   *
   * @param studyJson JSON study input with fluid, route, pipe, tagreader, and valve-event data
   * @return JSON result with pressure surge metrics, time series, envelopes, and warnings
   */
  public static String run(String studyJson) {
    JsonObject root = new JsonObject();
    try {
      if (studyJson == null || studyJson.trim().isEmpty()) {
        throw new IllegalArgumentException("Water-hammer input JSON cannot be null or empty");
      }
      JsonObject input = JsonParser.parseString(studyJson).getAsJsonObject();
      return GSON.toJson(run(input));
    } catch (Exception e) {
      root.addProperty("status", "error");
      root.addProperty("message", e.getMessage());
      root.addProperty("remediation",
          "Provide components, temperature/pressure, flowRate, and pipe or route geometry.");
      return GSON.toJson(root);
    }
  }

  /**
   * Runs a parsed water-hammer study input.
   *
   * @param input parsed JSON input object
   * @return JSON result object
   */
  private static JsonObject run(JsonObject input) {
    List<String> warnings = new ArrayList<String>();
    PipeGeometry geometry = readGeometry(input, warnings);
    SystemInterface fluid = createFluid(input);
    Stream feed = createFeedStream(input, fluid);
    feed.run();

    WaterHammerPipe pipe = createPipe(input, geometry, feed);
    JsonArray events = getEventSchedule(input, pipe.getValveOpening());
    UUID id = UUID.randomUUID();
    pipe.run(id);
    pipe.resetEnvelopes();

    double initialOutletPressureBar = last(pipe.getPressureProfile("bar"));
    double initialVelocity = last(pipe.getVelocityProfile());
    double stableTimeStep = pipe.getMaxStableTimeStep();
    double requestedTimeStep = getDoubleAny(input, stableTimeStep, "timeStep_s", "dt_s");
    double timeStep = Math.min(requestedTimeStep, stableTimeStep);
    if (requestedTimeStep > stableTimeStep) {
      warnings.add("Requested time step exceeded the Courant limit and was reduced to "
          + stableTimeStep + " s");
    }

    double defaultDuration = Math.max(pipe.getWaveRoundTripTime() * 3.0,
        getDefaultClosureDuration(input) * 4.0);
    double simulationTime = getDoubleAny(input, defaultDuration, "simulationTime_s", "duration_s");
    int numberOfSteps = (int) Math.ceil(simulationTime / timeStep);
    if (numberOfSteps > MAX_TIME_STEPS) {
      numberOfSteps = MAX_TIME_STEPS;
      simulationTime = numberOfSteps * timeStep;
      warnings.add("Simulation was capped at " + MAX_TIME_STEPS + " time steps for screening.");
    }
    int sampleInterval = Math.max(1, numberOfSteps / 500);

    JsonArray sampledTime = new JsonArray();
    JsonArray sampledOutletPressure = new JsonArray();
    JsonArray sampledValveOpening = new JsonArray();
    addSample(sampledTime, sampledOutletPressure, sampledValveOpening, 0.0, initialOutletPressureBar,
        pipe.getValveOpening());

    for (int step = 1; step <= numberOfSteps; step++) {
      double time = step * timeStep;
      applyValveEvents(pipe, events, time);
      pipe.runTransient(timeStep, id);
      if (step % sampleInterval == 0 || step == numberOfSteps) {
        addSample(sampledTime, sampledOutletPressure, sampledValveOpening, time,
            last(pipe.getPressureProfile("bar")), pipe.getValveOpening());
      }
    }

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("studyType", "water_hammer_screening");
    result.addProperty("studyName", getString(input, "studyName", "Water hammer screening"));
    result.addProperty("calculation", "Method of Characteristics single-line acoustic transient");
    result.add("inputSummary", buildInputSummary(input, geometry, pipe, feed));
    result.add("keyResults", buildKeyResults(input, pipe, initialOutletPressureBar,
        initialVelocity, timeStep, simulationTime));
    result.add("timeSeries", buildTimeSeries(sampledTime, sampledOutletPressure,
        sampledValveOpening));
    result.add("pressureEnvelope", buildPressureEnvelope(pipe));
    result.add("validation", buildValidation(input, pipe, warnings));
    result.add("sourceReferences", getSourceReferences(input, geometry));
    result.add("warnings", toJsonArray(warnings));
    result.addProperty("screeningLimitations",
        "Equivalent single-line acoustic model. Validate high-risk cases with detailed surge "
            + "software, vendor valve curves, support loads, and as-built isometrics.");
    return result;
  }

  /**
   * Creates a thermodynamic fluid from the study input.
   *
   * @param input parsed JSON input
   * @return configured NeqSim thermodynamic system
   */
  private static SystemInterface createFluid(JsonObject input) {
    JsonObject fieldData = getObject(input, "fieldData", "tagreader", "tags");
    double temperatureC = getDoubleAny(fieldData,
        getDoubleAny(input, 20.0, "temperature_C", "inletTemperature_C"), "temperature_C",
        "inletTemperature_C");
    double pressureBara = getDoubleAny(fieldData,
        getDoubleAny(input, 10.0, "pressure_bara", "inletPressure_bara"), "pressure_bara",
        "inletPressure_bara");
    String model = getString(input, "model", "SRK").toUpperCase(Locale.ROOT);

    SystemInterface fluid;
    if ("PR".equals(model)) {
      fluid = new SystemPrEos(273.15 + temperatureC, pressureBara);
    } else {
      fluid = new SystemSrkEos(273.15 + temperatureC, pressureBara);
    }

    JsonObject components = getObject(input, "components", "composition");
    if (components == null || components.entrySet().isEmpty()) {
      fluid.addComponent("water", 1.0);
    } else {
      for (String component : components.keySet()) {
        fluid.addComponent(component, components.get(component).getAsDouble());
      }
    }
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Creates the inlet stream and applies flow-rate input or tagreader override.
   *
   * @param input parsed JSON input
   * @param fluid configured fluid
   * @return feed stream for the transient pipe
   */
  private static Stream createFeedStream(JsonObject input, SystemInterface fluid) {
    Stream feed = new Stream("water hammer feed", fluid);
    JsonObject fieldData = getObject(input, "fieldData", "tagreader", "tags");
    double flowValue = 100000.0;
    String flowUnit = "kg/hr";
    if (input.has("flowRate") && input.get("flowRate").isJsonObject()) {
      JsonObject flowRate = input.getAsJsonObject("flowRate");
      flowValue = getDoubleAny(flowRate, flowValue, "value", "amount");
      flowUnit = getString(flowRate, "unit", flowUnit);
    }
    flowValue = getDoubleAny(fieldData, flowValue, "flowRate_kg_hr", "massFlow_kg_hr");
    feed.setFlowRate(flowValue, flowUnit);
    return feed;
  }

  /**
   * Creates and configures the water-hammer pipe from normalized geometry and input options.
   *
   * @param input parsed JSON input
   * @param geometry normalized pipe geometry
   * @param feed feed stream
   * @return configured water-hammer pipe
   */
  private static WaterHammerPipe createPipe(JsonObject input, PipeGeometry geometry, Stream feed) {
    WaterHammerPipe pipe = new WaterHammerPipe(getString(input, "pipeName", "water hammer pipe"),
        feed);
    JsonObject pipeInput = getObject(input, "pipe", "pipeline");
    JsonObject fieldData = getObject(input, "fieldData", "tagreader", "tags");
    pipe.setLength(geometry.lengthMeters);
    pipe.setDiameter(geometry.diameterMeters);
    pipe.setElevationChange(geometry.elevationChangeMeters);
    pipe.setPipeWallRoughness(geometry.roughnessMeters);
    pipe.setWallThickness(geometry.wallThicknessMeters);
    pipe.setNumberOfNodes((int) Math.round(getDoubleAny(pipeInput,
        getDoubleAny(input, 80.0, "numberOfNodes", "nodes"), "numberOfNodes", "nodes")));
    pipe.setCourantNumber(getDoubleAny(pipeInput,
        getDoubleAny(input, 0.9, "courantNumber"), "courantNumber"));
    double waveSpeed = getDoubleAny(pipeInput, getDoubleAny(input, Double.NaN, "waveSpeed_m_s",
        "waveSpeed"), "waveSpeed_m_s", "waveSpeed");
    if (!Double.isNaN(waveSpeed)) {
      pipe.setWaveSpeed(waveSpeed);
    }
    pipe.setUpstreamBoundary(getString(input, "upstreamBoundary", "RESERVOIR"));
    pipe.setDownstreamBoundary(getString(input, "downstreamBoundary", "VALVE"));
    pipe.setValveOpening(getDoubleAny(fieldData,
        getDoubleAny(input, 1.0, "valveOpening", "initialValveOpening"), "valveOpening",
        "initialValveOpening"));
    return pipe;
  }

  /**
   * Reads pipe geometry from pipe fields or a STID-style route segment list.
   *
   * @param input parsed JSON input
   * @param warnings mutable warning list
   * @return normalized equivalent pipe geometry
   */
  private static PipeGeometry readGeometry(JsonObject input, List<String> warnings) {
    JsonObject pipe = getObject(input, "pipe", "pipeline");
    PipeGeometry geometry = new PipeGeometry();
    geometry.lengthMeters = getDoubleAny(pipe, getDoubleAny(input, 1000.0, "length_m"),
        "length_m", "lengthMeters", "length");
    geometry.diameterMeters = getDoubleAny(pipe, getDoubleAny(input, 0.2, "diameter_m"),
        "diameter_m", "internalDiameter_m", "diameterMeters", "diameter");
    geometry.wallThicknessMeters = getDoubleAny(pipe,
        getDoubleAny(input, 0.01, "wallThickness_m", "wallThickness"), "wallThickness_m",
        "wallThickness", "wall_thickness_m");
    geometry.roughnessMeters = getDoubleAny(pipe,
        getDoubleAny(input, 4.6e-5, "roughness_m", "pipeWallRoughness"), "roughness_m",
        "pipeWallRoughness", "roughness");
    geometry.elevationChangeMeters = getDoubleAny(pipe,
        getDoubleAny(input, 0.0, "elevation_m", "elevationChange_m"), "elevation_m",
        "elevationChange_m", "elevationChange");

    JsonObject route = getObject(input, "stidRoute", "route", "lineListRoute");
    if (route != null && route.has("segments") && route.get("segments").isJsonArray()) {
      applyRouteSegments(route.getAsJsonArray("segments"), geometry, warnings);
    }
    return geometry;
  }

  /**
   * Aggregates route segments into equivalent single-line geometry for fast screening.
   *
   * @param segments route segment array
   * @param geometry geometry object to update
   * @param warnings mutable warning list
   */
  private static void applyRouteSegments(JsonArray segments, PipeGeometry geometry,
      List<String> warnings) {
    double totalLength = 0.0;
    double totalElevation = 0.0;
    double firstDiameter = Double.NaN;
    double firstWallThickness = Double.NaN;
    double firstRoughness = Double.NaN;
    double equivalentMinorLossLength = 0.0;
    for (JsonElement element : segments) {
      JsonObject segment = element.getAsJsonObject();
      double segmentLength = getDoubleAny(segment, 0.0, "length_m", "lengthMeters", "length");
      double diameter = getDoubleAny(segment, geometry.diameterMeters, "diameter_m",
          "internalDiameter_m", "diameterMeters", "diameter");
      double wallThickness = getDoubleAny(segment, geometry.wallThicknessMeters,
          "wallThickness_m", "wallThickness", "wall_thickness_m");
      double roughness = getDoubleAny(segment, geometry.roughnessMeters, "roughness_m",
          "pipeWallRoughness", "roughness");
      if (Double.isNaN(firstDiameter)) {
        firstDiameter = diameter;
        firstWallThickness = wallThickness;
        firstRoughness = roughness;
      } else if (Math.abs(diameter - firstDiameter) > 1.0e-6) {
        warnings.add("Route has varying diameters; equivalent screening uses the first segment "
            + "diameter. Split into separate studies for final checks.");
      }
      totalLength += segmentLength;
      totalElevation += getDoubleAny(segment, 0.0, "elevation_m", "elevationChange_m",
          "elevationChange");
      equivalentMinorLossLength += estimateEquivalentMinorLossLength(segment, diameter);
    }
    if (totalLength > 0.0) {
      geometry.lengthMeters = totalLength + equivalentMinorLossLength;
      geometry.equivalentMinorLossLengthMeters = equivalentMinorLossLength;
      geometry.elevationChangeMeters = totalElevation;
      geometry.diameterMeters = firstDiameter;
      geometry.wallThicknessMeters = firstWallThickness;
      geometry.roughnessMeters = firstRoughness;
    }
  }

  /**
   * Estimates equivalent length for segment minor losses using a screening friction factor.
   *
   * @param segment route segment JSON object
   * @param diameter segment diameter in meters
   * @return equivalent length added by minor losses in meters
   */
  private static double estimateEquivalentMinorLossLength(JsonObject segment, double diameter) {
    double equivalentLength = 0.0;
    if (!segment.has("minorLosses") || !segment.get("minorLosses").isJsonArray()) {
      return equivalentLength;
    }
    for (JsonElement element : segment.getAsJsonArray("minorLosses")) {
      JsonObject loss = element.getAsJsonObject();
      double kValue = getDoubleAny(loss, 0.0, "kValue", "k_value", "K");
      equivalentLength += kValue * diameter / DEFAULT_MINOR_LOSS_FRICTION_FACTOR;
    }
    return equivalentLength;
  }

  /**
   * Returns the event schedule or creates a default valve closure event.
   *
   * @param input parsed JSON input
   * @param initialOpening initial valve opening fraction
   * @return event schedule array
   */
  private static JsonArray getEventSchedule(JsonObject input, double initialOpening) {
    if (input.has("eventSchedule") && input.get("eventSchedule").isJsonArray()) {
      return input.getAsJsonArray("eventSchedule");
    }
    JsonArray events = new JsonArray();
    JsonObject event = new JsonObject();
    event.addProperty("type", "VALVE_CLOSURE");
    event.addProperty("startTime_s", getDoubleAny(input, 0.0, "closureStart_s"));
    event.addProperty("duration_s", getDefaultClosureDuration(input));
    event.addProperty("startOpening", initialOpening);
    event.addProperty("endOpening", 0.0);
    events.add(event);
    return events;
  }

  /**
   * Gets the default valve closure duration from input or a screening default.
   *
   * @param input parsed JSON input
   * @return closure duration in seconds
   */
  private static double getDefaultClosureDuration(JsonObject input) {
    return getDoubleAny(input, 0.2, "closureTime_s", "valveClosureTime_s", "closureDuration_s");
  }

  /**
   * Applies all active valve events at the current transient time.
   *
   * @param pipe transient pipe
   * @param events event schedule array
   * @param time current simulation time in seconds
   */
  private static void applyValveEvents(WaterHammerPipe pipe, JsonArray events, double time) {
    Double opening = null;
    for (JsonElement element : events) {
      JsonObject event = element.getAsJsonObject();
      String type = getString(event, "type", "VALVE_CLOSURE").toUpperCase(Locale.ROOT);
      if (!type.contains("VALVE")) {
        continue;
      }
      double startTime = getDoubleAny(event, 0.0, "startTime_s", "time_s");
      double duration = Math.max(1.0e-12, getDoubleAny(event, 0.0, "duration_s"));
      double startOpening = getDoubleAny(event, pipe.getValveOpening(), "startOpening",
          "startOpeningFraction");
      double endOpening = getDoubleAny(event, type.contains("OPEN") ? 1.0 : 0.0, "endOpening",
          "endOpeningFraction");
      if (time >= startTime + duration) {
        opening = endOpening;
      } else if (time >= startTime) {
        double fraction = (time - startTime) / duration;
        opening = startOpening + fraction * (endOpening - startOpening);
      }
    }
    if (opening != null) {
      pipe.setValveOpening(opening.doubleValue());
    }
  }

  /**
   * Adds a sampled transient point to the output arrays.
   *
   * @param timeArray time output array
   * @param pressureArray pressure output array
   * @param openingArray valve opening output array
   * @param time time in seconds
   * @param pressureBar outlet pressure in bar
   * @param valveOpening valve opening fraction
   */
  private static void addSample(JsonArray timeArray, JsonArray pressureArray,
      JsonArray openingArray, double time, double pressureBar, double valveOpening) {
    timeArray.add(time);
    pressureArray.add(pressureBar);
    openingArray.add(valveOpening);
  }

  /**
   * Builds the input summary for the JSON result.
   *
   * @param input parsed JSON input
   * @param geometry normalized geometry
   * @param pipe configured pipe
   * @param feed inlet stream
   * @return input summary JSON object
   */
  private static JsonObject buildInputSummary(JsonObject input, PipeGeometry geometry,
      WaterHammerPipe pipe, Stream feed) {
    JsonObject summary = new JsonObject();
    summary.addProperty("temperature_C", feed.getTemperature("C"));
    summary.addProperty("pressure_bara", feed.getPressure("bara"));
    summary.addProperty("flowRate_kg_hr", feed.getFlowRate("kg/hr"));
    summary.addProperty("length_m", geometry.lengthMeters);
    summary.addProperty("diameter_m", geometry.diameterMeters);
    summary.addProperty("wallThickness_m", geometry.wallThicknessMeters);
    summary.addProperty("roughness_m", geometry.roughnessMeters);
    summary.addProperty("elevationChange_m", geometry.elevationChangeMeters);
    summary.addProperty("equivalentMinorLossLength_m", geometry.equivalentMinorLossLengthMeters);
    summary.addProperty("numberOfNodes", pipe.getNumberOfNodes());
    summary.addProperty("upstreamBoundary", pipe.getUpstreamBoundaryName());
    summary.addProperty("downstreamBoundary", pipe.getDownstreamBoundaryName());
    summary.add("events", getEventSchedule(input, pipe.getValveOpening()));
    return summary;
  }

  /**
   * Builds key study results.
   *
   * @param input parsed JSON input
   * @param pipe simulated pipe
   * @param initialOutletPressureBar initial outlet pressure in bar
   * @param initialVelocity initial pipe velocity in meters per second
   * @param timeStep time step used in seconds
   * @param simulationTime simulated duration in seconds
   * @return key results JSON object
   */
  private static JsonObject buildKeyResults(JsonObject input, WaterHammerPipe pipe,
      double initialOutletPressureBar, double initialVelocity, double timeStep,
      double simulationTime) {
    JsonObject key = new JsonObject();
    double maxPressureBar = pipe.getMaxPressure("bar");
    double minPressureBar = pipe.getMinPressure("bar");
    double surgeBar = maxPressureBar - initialOutletPressureBar;
    double joukowskyBar = pipe.calcJoukowskyPressureSurge(initialVelocity, "bar");
    key.addProperty("initialOutletPressure_bara", initialOutletPressureBar);
    key.addProperty("maxPressure_bara", maxPressureBar);
    key.addProperty("minPressure_bara", minPressureBar);
    key.addProperty("pressureSurge_bar", surgeBar);
    key.addProperty("joukowskySurgeEstimate_bar", joukowskyBar);
    key.addProperty("waveSpeed_m_s", pipe.getWaveSpeed());
    key.addProperty("waveRoundTripTime_s", pipe.getWaveRoundTripTime());
    key.addProperty("maxStableTimeStep_s", pipe.getMaxStableTimeStep());
    key.addProperty("timeStepUsed_s", timeStep);
    key.addProperty("simulationTime_s", simulationTime);
    key.addProperty("closureRegime", getDefaultClosureDuration(input) <= pipe.getWaveRoundTripTime()
        ? "fast_closure" : "slow_closure");
    return key;
  }

  /**
   * Builds sampled transient time-series output.
   *
   * @param sampledTime sampled time array
   * @param sampledOutletPressure sampled outlet pressure array
   * @param sampledValveOpening sampled valve opening array
   * @return time-series JSON object
   */
  private static JsonObject buildTimeSeries(JsonArray sampledTime, JsonArray sampledOutletPressure,
      JsonArray sampledValveOpening) {
    JsonObject timeSeries = new JsonObject();
    timeSeries.add("time_s", sampledTime);
    timeSeries.add("outletPressure_bara", sampledOutletPressure);
    timeSeries.add("valveOpening", sampledValveOpening);
    return timeSeries;
  }

  /**
   * Builds pressure envelope arrays for the pipe nodes.
   *
   * @param pipe simulated pipe
   * @return pressure envelope JSON object
   */
  private static JsonObject buildPressureEnvelope(WaterHammerPipe pipe) {
    JsonObject envelope = new JsonObject();
    envelope.add("maxPressure_bara", toJsonArray(pipe.getMaxPressureEnvelope("bar")));
    envelope.add("minPressure_bara", toJsonArray(pipe.getMinPressureEnvelope("bar")));
    envelope.add("finalPressure_bara", toJsonArray(pipe.getPressureProfile("bar")));
    envelope.add("finalVelocity_m_s", toJsonArray(pipe.getVelocityProfile()));
    return envelope;
  }

  /**
   * Builds validation and design-margin output.
   *
   * @param input parsed JSON input
   * @param pipe simulated pipe
   * @param warnings warning list
   * @return validation JSON object
   */
  private static JsonObject buildValidation(JsonObject input, WaterHammerPipe pipe,
      List<String> warnings) {
    JsonObject validation = new JsonObject();
    validation.addProperty("method", "MOC transient with Courant-limited time step");
    validation.addProperty("timeStepWithinCourantLimit", true);
    double designPressure = getDoubleAny(input, Double.NaN, "designPressure_bara",
        "maxAllowablePressure_bara", "MAOP_bara");
    if (!Double.isNaN(designPressure)) {
      double maxPressure = pipe.getMaxPressure("bar");
      validation.addProperty("designPressure_bara", designPressure);
      validation.addProperty("maxPressureToDesignPressureRatio", maxPressure / designPressure);
      validation.addProperty("designPressureExceeded", maxPressure > designPressure);
      if (maxPressure > designPressure) {
        warnings.add("Calculated peak pressure exceeds the supplied design pressure.");
      }
    }
    validation.addProperty("requiresDetailedSurgeStudy", pipe.getMaxPressure("bar")
        - last(pipe.getPressureProfile("bar")) > 5.0 || warnings.size() > 0);
    return validation;
  }

  /**
   * Gets source references from top-level input and route geometry.
   *
   * @param input parsed JSON input
   * @param geometry normalized geometry
   * @return source reference JSON array
   */
  private static JsonArray getSourceReferences(JsonObject input, PipeGeometry geometry) {
    JsonArray references = new JsonArray();
    if (input.has("sourceReferences") && input.get("sourceReferences").isJsonArray()) {
      for (JsonElement element : input.getAsJsonArray("sourceReferences")) {
        references.add(element);
      }
    }
    for (String reference : geometry.sourceReferences) {
      references.add(reference);
    }
    return references;
  }

  /**
   * Gets the final array value or NaN for an empty array.
   *
   * @param values source array
   * @return final value or NaN
   */
  private static double last(double[] values) {
    if (values == null || values.length == 0) {
      return Double.NaN;
    }
    return values[values.length - 1];
  }

  /**
   * Gets one of several possible nested objects.
   *
   * @param input source JSON object
   * @param names accepted field names
   * @return first matching object or null
   */
  private static JsonObject getObject(JsonObject input, String... names) {
    if (input == null) {
      return null;
    }
    for (String name : names) {
      if (input.has(name) && input.get(name).isJsonObject()) {
        return input.getAsJsonObject(name);
      }
    }
    return null;
  }

  /**
   * Gets a string field with a default value.
   *
   * @param input source JSON object
   * @param name field name
   * @param defaultValue default value if missing
   * @return string value or default
   */
  private static String getString(JsonObject input, String name, String defaultValue) {
    if (input != null && input.has(name) && !input.get(name).isJsonNull()) {
      return input.get(name).getAsString();
    }
    return defaultValue;
  }

  /**
   * Gets the first matching finite double from a JSON object.
   *
   * @param input source JSON object
   * @param defaultValue default value if fields are missing
   * @param names accepted field names
   * @return field value or default
   */
  private static double getDoubleAny(JsonObject input, double defaultValue, String... names) {
    if (input == null) {
      return defaultValue;
    }
    for (String name : names) {
      if (input.has(name) && !input.get(name).isJsonNull()) {
        return input.get(name).getAsDouble();
      }
    }
    return defaultValue;
  }

  /**
   * Converts a double array to JSON.
   *
   * @param values values to convert
   * @return JSON array
   */
  private static JsonArray toJsonArray(double[] values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Converts a string list to JSON.
   *
   * @param values values to convert
   * @return JSON array
   */
  private static JsonArray toJsonArray(List<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Normalized equivalent pipe geometry for a screening water-hammer study.
   */
  private static final class PipeGeometry {
    private double lengthMeters;
    private double diameterMeters;
    private double wallThicknessMeters;
    private double roughnessMeters;
    private double elevationChangeMeters;
    private double equivalentMinorLossLengthMeters;
    private List<String> sourceReferences = new ArrayList<String>();
  }
}