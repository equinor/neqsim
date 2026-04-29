package neqsim.process.equipment.pipeline.routing;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builds a serial route-level piping hydraulics model from structured line-list data.
 *
 * <p>
 * The builder stores route segments with from/to nodes, hydraulic diameter, straight length,
 * optional wall thickness, roughness, elevation change, and K-value minor losses. Calling
 * {@link #build(StreamInterface)} creates a {@link ProcessSystem} containing the inlet stream and
 * one {@link PipeBeggsAndBrills} unit per segment, wired in the same order as the line-list rows.
 * </p>
 *
 * <p>
 * Minor losses are converted from resistance coefficient K to equivalent length ratio L/D using a
 * configurable Darcy friction factor assumption. The default assumption is 0.02, a typical
 * turbulent screening value.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipingRouteBuilder implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Default Darcy friction factor used for converting K values to L/D values. */
  private static final double DEFAULT_MINOR_LOSS_FRICTION_FACTOR = 0.02;
  /** Pattern for values written as a number with an optional unit suffix. */
  private static final Pattern NUMBER_WITH_OPTIONAL_UNIT = Pattern
      .compile("^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*([A-Za-z_/]+)?\\s*$");
  /** Segment identifier column aliases. */
  private static final String[] SEGMENT_ID_COLUMNS = {"segment_id", "segmentid", "segment",
      "line_number", "line_no", "line", "row_id", "route_segment", "pipe_segment"};
  /** Upstream node column aliases. */
  private static final String[] FROM_NODE_COLUMNS = {"from_node", "from", "upstream_node",
      "upstream", "source_node", "source", "start_node", "start", "from_equipment"};
  /** Downstream node column aliases. */
  private static final String[] TO_NODE_COLUMNS = {"to_node", "to", "downstream_node", "downstream",
      "target_node", "target", "destination", "end_node", "end", "to_equipment"};
  /** Straight length column aliases. */
  private static final String[] LENGTH_COLUMNS =
      {"length", "straight_length", "pipe_length", "run_length", "segment_length"};
  /** Straight length unit column aliases. */
  private static final String[] LENGTH_UNIT_COLUMNS = {"length_unit", "straight_length_unit",
      "pipe_length_unit", "run_length_unit", "segment_length_unit"};
  /** Hydraulic diameter column aliases. */
  private static final String[] DIAMETER_COLUMNS = {"nominal_diameter", "nominal_bore",
      "nominal_size", "nominal_pipe_size", "nps", "internal_diameter", "inner_diameter",
      "inside_diameter", "hydraulic_diameter", "diameter", "bore", "id"};
  /** Hydraulic diameter unit column aliases. */
  private static final String[] DIAMETER_UNIT_COLUMNS =
      {"diameter_unit", "nominal_diameter_unit", "nominal_bore_unit", "nominal_size_unit",
          "nominal_pipe_size_unit", "nps_unit", "internal_diameter_unit", "inner_diameter_unit",
          "inside_diameter_unit", "hydraulic_diameter_unit", "bore_unit", "id_unit"};
  /** Optional wall thickness column aliases. */
  private static final String[] WALL_THICKNESS_COLUMNS =
      {"wall_thickness", "thickness", "pipe_wall_thickness", "schedule_wall_thickness"};
  /** Optional wall thickness unit column aliases. */
  private static final String[] WALL_THICKNESS_UNIT_COLUMNS = {"wall_thickness_unit",
      "thickness_unit", "pipe_wall_thickness_unit", "schedule_wall_thickness_unit"};
  /** Optional elevation-change column aliases. */
  private static final String[] ELEVATION_CHANGE_COLUMNS = {"elevation_change", "delta_elevation",
      "elevation_delta", "height_change", "vertical_rise", "rise"};
  /** Optional elevation unit column aliases. */
  private static final String[] ELEVATION_UNIT_COLUMNS = {"elevation_unit", "elevation_change_unit",
      "delta_elevation_unit", "height_change_unit", "rise_unit"};
  /** Optional wall roughness column aliases. */
  private static final String[] ROUGHNESS_COLUMNS =
      {"roughness", "pipe_wall_roughness", "wall_roughness", "absolute_roughness"};
  /** Optional wall roughness unit column aliases. */
  private static final String[] ROUGHNESS_UNIT_COLUMNS = {"roughness_unit",
      "pipe_wall_roughness_unit", "wall_roughness_unit", "absolute_roughness_unit"};
  /** Minor-loss list column aliases. */
  private static final String[] MINOR_LOSS_LIST_COLUMNS =
      {"minor_losses", "minor_loss_list", "fittings", "valves", "fittings_and_valves", "k_values"};
  /** Minor-loss description column aliases. */
  private static final String[] MINOR_LOSS_TYPE_COLUMNS =
      {"fitting_type", "minor_loss_type", "loss_type", "valve_type", "fitting", "valve"};
  /** Minor-loss K-value column aliases. */
  private static final String[] MINOR_LOSS_K_COLUMNS =
      {"k_value", "k", "minor_loss_k", "minor_loss_coefficient", "resistance_coefficient",
          "resistance_k", "total_k", "k_total", "sum_k"};
  /** Unit suffixes recognized in suffixed table headers. */
  private static final String[] LENGTH_UNIT_SUFFIXES = {"m", "meter", "meters", "metre", "metres",
      "km", "kilometer", "kilometers", "kilometre", "kilometres", "cm", "centimeter", "centimeters",
      "centimetre", "centimetres", "mm", "millimeter", "millimeters", "millimetre", "millimetres",
      "um", "micrometer", "micrometers", "micrometre", "micrometres", "micron", "microns", "in",
      "inch", "inches", "ft", "foot", "feet"};

  private final List<RouteSegment> segments = new ArrayList<RouteSegment>();
  private double defaultPipeWallRoughnessMeters = 1.0e-5;
  private int defaultNumberOfIncrements = 5;
  private double minorLossFrictionFactor = DEFAULT_MINOR_LOSS_FRICTION_FACTOR;
  private PipeBeggsAndBrills.HeatTransferMode defaultHeatTransferMode =
      PipeBeggsAndBrills.HeatTransferMode.ISOTHERMAL;

  /**
   * Creates an empty piping route builder.
   */
  public PipingRouteBuilder() {}

  /**
   * Creates a route builder from structured line-list rows.
   *
   * <p>
   * Column names are matched case-insensitively after normalizing spaces, hyphens, brackets, and
   * punctuation to underscores. Common aliases such as {@code line_number}, {@code from_node},
   * {@code to_node}, {@code length_m}, {@code internal_diameter_mm}, {@code wall_thickness_mm},
   * {@code elevation_change_m}, and {@code minor_losses} are accepted. Units can be supplied either
   * as separate unit columns or as suffixes in the header/value.
   * </p>
   *
   * @param rows line-list rows, each represented as a column-name to cell-value map
   * @return populated route builder
   * @throws IllegalArgumentException if required columns are missing or any row contains invalid
   *         values
   * @throws NullPointerException if {@code rows} is null
   */
  public static PipingRouteBuilder fromLineListRows(List<Map<String, String>> rows) {
    return new PipingRouteBuilder().addLineListRows(rows);
  }

  /**
   * Creates a route builder from a comma-separated line-list file.
   *
   * <p>
   * The first non-empty row is interpreted as the header. Subsequent non-empty rows are parsed as
   * line-list rows and passed to {@link #fromLineListRows(List)}. Quoted fields and escaped double
   * quotes are supported for simple engineering CSV exports.
   * </p>
   *
   * @param csvPath path to a UTF-8 CSV file containing route rows
   * @return populated route builder
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if the CSV structure or values are invalid
   * @throws NullPointerException if {@code csvPath} is null
   */
  public static PipingRouteBuilder fromCsv(Path csvPath) throws IOException {
    Objects.requireNonNull(csvPath, "csvPath");
    return fromCsvLines(Files.readAllLines(csvPath, StandardCharsets.UTF_8));
  }

  /**
   * Creates a route builder from CSV lines.
   *
   * @param csvLines CSV lines, with the first non-empty line used as the header
   * @return populated route builder
   * @throws IllegalArgumentException if the CSV structure or values are invalid
   * @throws NullPointerException if {@code csvLines} is null
   */
  public static PipingRouteBuilder fromCsvLines(List<String> csvLines) {
    return fromLineListRows(parseCsvRows(csvLines));
  }

  /**
   * Adds multiple structured line-list rows.
   *
   * @param rows line-list rows, each represented as a column-name to cell-value map
   * @return this builder for chaining
   * @throws IllegalArgumentException if required columns are missing or any row contains invalid
   *         values
   * @throws NullPointerException if {@code rows} is null
   */
  public PipingRouteBuilder addLineListRows(List<Map<String, String>> rows) {
    Objects.requireNonNull(rows, "rows");
    for (Map<String, String> row : rows) {
      addLineListRow(row);
    }
    return this;
  }

  /**
   * Adds one structured line-list row.
   *
   * <p>
   * Required fields are upstream node, downstream node, straight length, and hydraulic diameter.
   * Segment id, wall thickness, roughness, elevation change, and minor losses are optional. When
   * the segment id is omitted, the builder generates ids using the same {@code S1}, {@code S2}
   * pattern as {@link #addSegment(String, String, double, String, double, String)}.
   * </p>
   *
   * @param row line-list row represented as a column-name to cell-value map
   * @return this builder for chaining
   * @throws IllegalArgumentException if required columns are missing or values are invalid
   * @throws NullPointerException if {@code row} is null
   */
  public PipingRouteBuilder addLineListRow(Map<String, String> row) {
    Map<String, String> normalizedRow = normalizeRow(row);
    String segmentId = getOptionalValue(normalizedRow, SEGMENT_ID_COLUMNS);
    String fromNode = getRequiredValue(normalizedRow, FROM_NODE_COLUMNS, "fromNode");
    String toNode = getRequiredValue(normalizedRow, TO_NODE_COLUMNS, "toNode");
    Quantity length =
        readRequiredQuantity(normalizedRow, LENGTH_COLUMNS, LENGTH_UNIT_COLUMNS, "m", "length");
    Quantity diameter = readRequiredQuantity(normalizedRow, DIAMETER_COLUMNS, DIAMETER_UNIT_COLUMNS,
        "m", "nominalDiameter");

    if (segmentId == null) {
      addSegment(fromNode, toNode, length.value, length.unit, diameter.value, diameter.unit);
    } else {
      addSegment(segmentId, fromNode, toNode, length.value, length.unit, diameter.value,
          diameter.unit);
    }

    RouteSegment segment = segments.get(segments.size() - 1);
    applyOptionalQuantity(normalizedRow, WALL_THICKNESS_COLUMNS, WALL_THICKNESS_UNIT_COLUMNS, "m",
        "wallThickness", segment.getSegmentId());
    applyOptionalQuantity(normalizedRow, ELEVATION_CHANGE_COLUMNS, ELEVATION_UNIT_COLUMNS, "m",
        "elevationChange", segment.getSegmentId());
    applyOptionalQuantity(normalizedRow, ROUGHNESS_COLUMNS, ROUGHNESS_UNIT_COLUMNS, "m",
        "roughness", segment.getSegmentId());
    applyMinorLosses(normalizedRow, segment.getSegmentId());
    return this;
  }

  /**
   * Adds one route segment and assigns a generated segment id such as {@code S1}.
   *
   * @param fromNode upstream node name from the line list
   * @param toNode downstream node name from the line list
   * @param length straight pipe length, must be positive
   * @param lengthUnit unit for {@code length}, for example {@code m}, {@code km}, {@code ft}
   * @param nominalDiameter hydraulic diameter used by the pipe model, must be positive
   * @param diameterUnit unit for {@code nominalDiameter}, for example {@code m}, {@code mm},
   *        {@code inch}
   * @return this builder for chaining
   * @throws IllegalArgumentException if inputs are blank, non-positive, duplicated, or unsupported
   */
  public PipingRouteBuilder addSegment(String fromNode, String toNode, double length,
      String lengthUnit, double nominalDiameter, String diameterUnit) {
    String generatedId = "S" + (segments.size() + 1);
    return addSegment(generatedId, fromNode, toNode, length, lengthUnit, nominalDiameter,
        diameterUnit);
  }

  /**
   * Adds one route segment with an explicit segment id.
   *
   * @param segmentId unique segment id, for example a line number or {@code S1}
   * @param fromNode upstream node name from the line list
   * @param toNode downstream node name from the line list
   * @param length straight pipe length, must be positive
   * @param lengthUnit unit for {@code length}, for example {@code m}, {@code km}, {@code ft}
   * @param nominalDiameter hydraulic diameter used by the pipe model, must be positive
   * @param diameterUnit unit for {@code nominalDiameter}, for example {@code m}, {@code mm},
   *        {@code inch}
   * @return this builder for chaining
   * @throws IllegalArgumentException if inputs are blank, non-positive, duplicated, or unsupported
   */
  public PipingRouteBuilder addSegment(String segmentId, String fromNode, String toNode,
      double length, String lengthUnit, double nominalDiameter, String diameterUnit) {
    String cleanSegmentId = requireText(segmentId, "segmentId");
    if (findSegment(cleanSegmentId) != null) {
      throw new IllegalArgumentException("Route segment already exists: " + cleanSegmentId);
    }

    RouteSegment segment = new RouteSegment(cleanSegmentId, requireText(fromNode, "fromNode"),
        requireText(toNode, "toNode"), requirePositive(length, "length"),
        convertLengthToMeters(length, lengthUnit),
        requirePositive(nominalDiameter, "nominalDiameter"),
        convertLengthToMeters(nominalDiameter, diameterUnit));
    segments.add(segment);
    return this;
  }

  /**
   * Sets segment wall thickness metadata and pipe wall thickness on the generated pipe.
   *
   * @param segmentId segment id or {@code fromNode->toNode} route reference
   * @param wallThickness wall thickness, must be non-negative
   * @param unit unit for {@code wallThickness}, for example {@code m}, {@code mm}, {@code inch}
   * @return this builder for chaining
   * @throws IllegalArgumentException if the segment cannot be found or the value/unit is invalid
   */
  public PipingRouteBuilder setSegmentWallThickness(String segmentId, double wallThickness,
      String unit) {
    RouteSegment segment = findSegmentOrThrow(segmentId);
    requireNonNegative(wallThickness, "wallThickness");
    segment.wallThicknessMeters = convertLengthToMeters(wallThickness, unit);
    return this;
  }

  /**
   * Sets segment elevation change from inlet to outlet.
   *
   * @param segmentId segment id or {@code fromNode->toNode} route reference
   * @param elevationChange elevation change, positive for uphill and negative for downhill
   * @param unit unit for {@code elevationChange}, for example {@code m}, {@code ft}
   * @return this builder for chaining
   * @throws IllegalArgumentException if the segment cannot be found or the unit is invalid
   */
  public PipingRouteBuilder setSegmentElevationChange(String segmentId, double elevationChange,
      String unit) {
    RouteSegment segment = findSegmentOrThrow(segmentId);
    requireFinite(elevationChange, "elevationChange");
    segment.elevationChangeMeters = convertLengthToMeters(elevationChange, unit);
    return this;
  }

  /**
   * Sets segment pipe wall roughness.
   *
   * @param segmentId segment id or {@code fromNode->toNode} route reference
   * @param roughness roughness, must be non-negative
   * @param unit unit for {@code roughness}, for example {@code m}, {@code mm}, {@code micrometer}
   * @return this builder for chaining
   * @throws IllegalArgumentException if the segment cannot be found or the value/unit is invalid
   */
  public PipingRouteBuilder setSegmentPipeWallRoughness(String segmentId, double roughness,
      String unit) {
    RouteSegment segment = findSegmentOrThrow(segmentId);
    requireNonNegative(roughness, "roughness");
    segment.pipeWallRoughnessMeters = convertLengthToMeters(roughness, unit);
    return this;
  }

  /**
   * Adds one K-value minor loss to a segment.
   *
   * @param segmentId segment id or {@code fromNode->toNode} route reference
   * @param fittingType fitting, valve, strainer, bend, or equipment-loss description
   * @param kValue resistance coefficient K, must be non-negative
   * @return this builder for chaining
   * @throws IllegalArgumentException if the segment cannot be found or the K value is invalid
   */
  public PipingRouteBuilder addMinorLoss(String segmentId, String fittingType, double kValue) {
    RouteSegment segment = findSegmentOrThrow(segmentId);
    requireNonNegative(kValue, "kValue");
    String cleanFittingType = requireText(fittingType, "fittingType");
    segment.minorLosses
        .add(new MinorLoss(cleanFittingType, kValue, kValue / minorLossFrictionFactor));
    return this;
  }

  /**
   * Sets the default wall roughness used for segments without explicit roughness.
   *
   * @param roughness roughness, must be non-negative
   * @param unit unit for {@code roughness}, for example {@code m}, {@code mm}, {@code micrometer}
   * @return this builder for chaining
   * @throws IllegalArgumentException if the roughness or unit is invalid
   */
  public PipingRouteBuilder setDefaultPipeWallRoughness(double roughness, String unit) {
    requireNonNegative(roughness, "roughness");
    defaultPipeWallRoughnessMeters = convertLengthToMeters(roughness, unit);
    return this;
  }

  /**
   * Sets the Darcy friction factor assumption used to convert K values to equivalent L/D values.
   *
   * @param frictionFactor Darcy friction factor, must be positive
   * @return this builder for chaining
   * @throws IllegalArgumentException if the friction factor is not positive
   */
  public PipingRouteBuilder setMinorLossFrictionFactor(double frictionFactor) {
    minorLossFrictionFactor = requirePositive(frictionFactor, "frictionFactor");
    recalculateMinorLossEquivalentLengths();
    return this;
  }

  /**
   * Sets the number of increments used for each generated Beggs-and-Brill pipe.
   *
   * @param numberOfIncrements number of increments, must be positive
   * @return this builder for chaining
   * @throws IllegalArgumentException if {@code numberOfIncrements} is not positive
   */
  public PipingRouteBuilder setDefaultNumberOfIncrements(int numberOfIncrements) {
    if (numberOfIncrements <= 0) {
      throw new IllegalArgumentException(
          "numberOfIncrements must be positive, got: " + numberOfIncrements);
    }
    defaultNumberOfIncrements = numberOfIncrements;
    return this;
  }

  /**
   * Sets the heat transfer mode applied to each generated Beggs-and-Brill pipe.
   *
   * @param heatTransferMode heat transfer mode, must not be null
   * @return this builder for chaining
   * @throws NullPointerException if {@code heatTransferMode} is null
   */
  public PipingRouteBuilder setDefaultHeatTransferMode(
      PipeBeggsAndBrills.HeatTransferMode heatTransferMode) {
    defaultHeatTransferMode = Objects.requireNonNull(heatTransferMode, "heatTransferMode");
    return this;
  }

  /**
   * Builds a serial {@link ProcessSystem} route model from the stored route segments.
   *
   * @param inletStream inlet stream for the first pipe segment, must not be null
   * @return process system containing the inlet stream and route pipe units
   * @throws IllegalArgumentException if no route segments have been added
   * @throws NullPointerException if {@code inletStream} is null
   */
  public ProcessSystem build(StreamInterface inletStream) {
    Objects.requireNonNull(inletStream, "inletStream");

    ProcessSystem process = new ProcessSystem("Piping route");
    process.add(inletStream);
    addToProcessSystem(process, inletStream);
    return process;
  }

  /**
   * Adds the route pipe units to an existing {@link ProcessSystem} and returns the outlet stream of
   * the final generated pipe.
   *
   * <p>
   * Use this method when a line-list route is only one section of a larger flowsheet. The caller is
   * responsible for adding the upstream feed stream or upstream equipment to the process system.
   * The returned stream can be passed directly to downstream equipment constructors.
   * </p>
   *
   * @param process process system that will receive the generated pipe units, must not be null
   * @param inletStream stream connected to the first generated pipe, must not be null
   * @return outlet stream from the last generated pipe
   * @throws IllegalArgumentException if no route segments have been added
   * @throws NullPointerException if {@code process} or {@code inletStream} is null
   */
  public StreamInterface addToProcessSystem(ProcessSystem process, StreamInterface inletStream) {
    Objects.requireNonNull(inletStream, "inletStream");
    return addToProcessSystem(process, inletStream, inletStream.getName(), "outlet");
  }

  /**
   * Adds the route pipe units to an existing {@link ProcessSystem} with explicit source-equipment
   * metadata for the first route connection.
   *
   * <p>
   * This overload is useful when {@code inletStream} is the outlet stream of an upstream equipment
   * item. The stream object performs the actual process wiring, while {@code sourceEquipmentName}
   * and {@code sourcePortName} preserve the topology metadata in {@link ProcessConnection} records.
   * </p>
   *
   * @param process process system that will receive the generated pipe units, must not be null
   * @param inletStream stream connected to the first generated pipe, must not be null
   * @param sourceEquipmentName upstream equipment name for the first material connection
   * @param sourcePortName upstream port name for the first material connection
   * @return outlet stream from the last generated pipe
   * @throws IllegalArgumentException if no route segments have been added or source metadata is
   *         blank
   * @throws NullPointerException if {@code process} or {@code inletStream} is null
   */
  public StreamInterface addToProcessSystem(ProcessSystem process, StreamInterface inletStream,
      String sourceEquipmentName, String sourcePortName) {
    Objects.requireNonNull(process, "process");
    Objects.requireNonNull(inletStream, "inletStream");
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("At least one piping route segment is required");
    }

    StreamInterface currentStream = inletStream;
    String currentEquipmentName = requireText(sourceEquipmentName, "sourceEquipmentName");
    String currentPortName = requireText(sourcePortName, "sourcePortName");
    for (RouteSegment segment : segments) {
      PipeBeggsAndBrills pipe = createPipe(segment, currentStream);
      process.add(pipe);
      process.connect(currentEquipmentName, currentPortName, pipe.getName(), "inlet",
          ProcessConnection.ConnectionType.MATERIAL);
      currentStream = pipe.getOutletStream();
      currentEquipmentName = pipe.getName();
      currentPortName = "outlet";
    }
    return currentStream;
  }

  /**
   * Returns route segments in build order.
   *
   * @return unmodifiable list of route segments
   */
  public List<RouteSegment> getSegments() {
    return Collections.unmodifiableList(segments);
  }

  /**
   * Gets a route segment by id or by {@code fromNode->toNode} reference.
   *
   * @param segmentId segment id or route reference
   * @return matching route segment, or null if no segment matches
   */
  public RouteSegment getSegment(String segmentId) {
    return findSegment(segmentId);
  }

  /**
   * Exports route geometry, assumptions, and minor losses as JSON.
   *
   * @return pretty-printed JSON route definition
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Creates and configures one Beggs-and-Brill pipe for a route segment.
   *
   * @param segment route segment definition
   * @param inletStream inlet stream connected to the pipe
   * @return configured pipe unit
   */
  private PipeBeggsAndBrills createPipe(RouteSegment segment, StreamInterface inletStream) {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(segment.getPipeName(), inletStream);
    pipe.setLength(segment.lengthMeters);
    pipe.setDiameter(segment.nominalDiameterMeters);
    pipe.setElevation(segment.elevationChangeMeters);
    pipe.setPipeWallRoughness(segment.getPipeWallRoughness(defaultPipeWallRoughnessMeters));
    pipe.setNumberOfIncrements(defaultNumberOfIncrements);
    pipe.setHeatTransferMode(defaultHeatTransferMode);

    if (!Double.isNaN(segment.wallThicknessMeters)) {
      pipe.setWallThickness(segment.wallThicknessMeters);
      pipe.setThickness(segment.wallThicknessMeters);
    }

    for (MinorLoss minorLoss : segment.minorLosses) {
      pipe.addFitting(minorLoss.fittingType, minorLoss.equivalentLengthRatio);
    }
    return pipe;
  }

  /**
   * Recalculates stored equivalent length ratios after the friction factor assumption changes.
   */
  private void recalculateMinorLossEquivalentLengths() {
    for (RouteSegment segment : segments) {
      for (MinorLoss minorLoss : segment.minorLosses) {
        minorLoss.equivalentLengthRatio = minorLoss.kValue / minorLossFrictionFactor;
      }
    }
  }

  /**
   * Finds a segment or throws an exception.
   *
   * @param segmentId segment id or route reference
   * @return matching route segment
   * @throws IllegalArgumentException if no segment matches
   */
  private RouteSegment findSegmentOrThrow(String segmentId) {
    RouteSegment segment = findSegment(segmentId);
    if (segment == null) {
      throw new IllegalArgumentException("Unknown route segment: " + segmentId);
    }
    return segment;
  }

  /**
   * Finds a segment by id or by route reference.
   *
   * @param segmentId segment id or route reference
   * @return matching route segment, or null if none matches
   */
  private RouteSegment findSegment(String segmentId) {
    if (segmentId == null) {
      return null;
    }
    String reference = segmentId.trim();
    for (RouteSegment segment : segments) {
      if (segment.segmentId.equals(reference) || segment.getArrowReference().equals(reference)
          || segment.getTextReference().equals(reference)) {
        return segment;
      }
    }
    return null;
  }

  /**
   * Converts the builder state to a JSON-friendly map.
   *
   * @return map representation of the route
   */
  private Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("type", "PipingRouteBuilder");
    result.put("pipeModel", PipeBeggsAndBrills.class.getSimpleName());
    result.put("defaultPipeWallRoughness_m", defaultPipeWallRoughnessMeters);
    result.put("defaultNumberOfIncrements", defaultNumberOfIncrements);
    result.put("defaultHeatTransferMode", defaultHeatTransferMode.toString());
    result.put("minorLossFrictionFactor", minorLossFrictionFactor);
    result.put("minorLossMethod", "K values converted to L/D using K divided by Darcy f");

    List<Map<String, Object>> segmentMaps = new ArrayList<Map<String, Object>>();
    for (RouteSegment segment : segments) {
      segmentMaps.add(segment.toMap(defaultPipeWallRoughnessMeters));
    }
    result.put("segments", segmentMaps);
    return result;
  }

  /**
   * Parses CSV lines into table rows.
   *
   * @param csvLines CSV lines with a header row
   * @return list of row maps keyed by header text
   * @throws IllegalArgumentException if no header row is present
   * @throws NullPointerException if {@code csvLines} is null
   */
  private static List<Map<String, String>> parseCsvRows(List<String> csvLines) {
    Objects.requireNonNull(csvLines, "csvLines");
    int headerLineIndex = -1;
    List<String> headers = null;
    for (int i = 0; i < csvLines.size(); i++) {
      String line = csvLines.get(i);
      if (line != null && !line.trim().isEmpty()) {
        headerLineIndex = i;
        headers = parseCsvLine(line);
        break;
      }
    }
    if (headers == null || headers.isEmpty()) {
      throw new IllegalArgumentException("CSV line list must contain a header row");
    }

    List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
    for (int i = headerLineIndex + 1; i < csvLines.size(); i++) {
      String line = csvLines.get(i);
      if (line == null || line.trim().isEmpty()) {
        continue;
      }
      List<String> values = parseCsvLine(line);
      Map<String, String> row = new LinkedHashMap<String, String>();
      for (int column = 0; column < headers.size(); column++) {
        String value = column < values.size() ? values.get(column) : "";
        row.put(headers.get(column), value);
      }
      rows.add(row);
    }
    return rows;
  }

  /**
   * Parses one CSV line.
   *
   * @param line line text
   * @return parsed fields
   * @throws IllegalArgumentException if the row contains an unclosed quote
   */
  private static List<String> parseCsvLine(String line) {
    List<String> fields = new ArrayList<String>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (character == ',' && !inQuotes) {
        fields.add(field.toString().trim());
        field.setLength(0);
      } else {
        field.append(character);
      }
    }
    if (inQuotes) {
      throw new IllegalArgumentException("CSV line contains an unclosed quoted field: " + line);
    }
    fields.add(field.toString().trim());
    return fields;
  }

  /**
   * Normalizes a line-list row's column names.
   *
   * @param row raw row map
   * @return normalized row map
   * @throws NullPointerException if {@code row} is null
   */
  private static Map<String, String> normalizeRow(Map<String, String> row) {
    Objects.requireNonNull(row, "row");
    Map<String, String> normalizedRow = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> entry : row.entrySet()) {
      String normalizedKey = normalizeColumnName(entry.getKey());
      if (normalizedKey.isEmpty()) {
        continue;
      }
      String value = entry.getValue() == null ? "" : entry.getValue();
      if (!normalizedRow.containsKey(normalizedKey) || normalizedRow.get(normalizedKey).isEmpty()) {
        normalizedRow.put(normalizedKey, value);
      }
    }
    return normalizedRow;
  }

  /**
   * Normalizes a column name for alias matching.
   *
   * @param columnName raw column name
   * @return lower-case column name with punctuation collapsed to underscores
   */
  private static String normalizeColumnName(String columnName) {
    if (columnName == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    boolean previousWasSeparator = false;
    String lowerCase = columnName.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lowerCase.length(); i++) {
      char character = lowerCase.charAt(i);
      if (Character.isLetterOrDigit(character)) {
        builder.append(character);
        previousWasSeparator = false;
      } else if (!previousWasSeparator && builder.length() > 0) {
        builder.append('_');
        previousWasSeparator = true;
      }
    }
    int length = builder.length();
    if (length > 0 && builder.charAt(length - 1) == '_') {
      builder.setLength(length - 1);
    }
    return builder.toString();
  }

  /**
   * Returns a required value from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param aliases accepted column aliases
   * @param fieldName field name for error messages
   * @return non-blank row value
   * @throws IllegalArgumentException if no alias has a non-blank value
   */
  private static String getRequiredValue(Map<String, String> normalizedRow, String[] aliases,
      String fieldName) {
    String value = getOptionalValue(normalizedRow, aliases);
    if (value == null) {
      throw new IllegalArgumentException("Missing required line-list field: " + fieldName);
    }
    return value;
  }

  /**
   * Returns an optional value from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param aliases accepted column aliases
   * @return non-blank row value, or null when absent
   */
  private static String getOptionalValue(Map<String, String> normalizedRow, String[] aliases) {
    for (String alias : aliases) {
      String value = normalizedRow.get(normalizeColumnName(alias));
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }

  /**
   * Reads a required length-like quantity from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param valueAliases accepted value column aliases
   * @param unitAliases accepted unit column aliases
   * @param defaultUnit default unit when the table omits a unit
   * @param fieldName field name for error messages
   * @return parsed quantity
   * @throws IllegalArgumentException if no value exists or the value is invalid
   */
  private static Quantity readRequiredQuantity(Map<String, String> normalizedRow,
      String[] valueAliases, String[] unitAliases, String defaultUnit, String fieldName) {
    Quantity quantity =
        readOptionalQuantity(normalizedRow, valueAliases, unitAliases, defaultUnit, fieldName);
    if (quantity == null) {
      throw new IllegalArgumentException("Missing required line-list field: " + fieldName);
    }
    return quantity;
  }

  /**
   * Reads an optional length-like quantity from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param valueAliases accepted value column aliases
   * @param unitAliases accepted unit column aliases
   * @param defaultUnit default unit when the table omits a unit
   * @param fieldName field name for error messages
   * @return parsed quantity, or null when no matching value exists
   */
  private static Quantity readOptionalQuantity(Map<String, String> normalizedRow,
      String[] valueAliases, String[] unitAliases, String defaultUnit, String fieldName) {
    for (String alias : valueAliases) {
      String normalizedAlias = normalizeColumnName(alias);
      String value = normalizedRow.get(normalizedAlias);
      if (value != null && !value.trim().isEmpty()) {
        String explicitUnit = getOptionalValue(normalizedRow, unitAliases);
        return parseQuantity(value, explicitUnit,
            getDefaultUnitForAlias(normalizedAlias, defaultUnit), fieldName);
      }
      for (String unitSuffix : LENGTH_UNIT_SUFFIXES) {
        String key = normalizedAlias + "_" + normalizeColumnName(unitSuffix);
        String suffixedValue = normalizedRow.get(key);
        if (suffixedValue != null && !suffixedValue.trim().isEmpty()) {
          return parseQuantity(suffixedValue, unitSuffix, unitSuffix, fieldName);
        }
      }
    }
    return null;
  }

  /**
   * Gets a field-specific default unit for an alias.
   *
   * @param normalizedAlias normalized alias text
   * @param defaultUnit caller-supplied default unit
   * @return default unit for the alias
   */
  private static String getDefaultUnitForAlias(String normalizedAlias, String defaultUnit) {
    if ("nps".equals(normalizedAlias) || "nominal_size".equals(normalizedAlias)
        || "nominal_pipe_size".equals(normalizedAlias)) {
      return "inch";
    }
    return defaultUnit;
  }

  /**
   * Parses a quantity value that may include an inline unit.
   *
   * @param value raw source value
   * @param explicitUnit unit from a separate unit column, may be null
   * @param defaultUnit default unit when no other unit is available
   * @param fieldName field name for error messages
   * @return parsed quantity
   * @throws IllegalArgumentException if the value or unit is invalid
   */
  private static Quantity parseQuantity(String value, String explicitUnit, String defaultUnit,
      String fieldName) {
    String cleanValue = requireText(value, fieldName);
    Matcher matcher = NUMBER_WITH_OPTIONAL_UNIT.matcher(cleanValue);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          fieldName + " must be a number with optional unit: " + value);
    }
    double numericValue;
    try {
      numericValue = Double.parseDouble(matcher.group(1));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(fieldName + " is not a valid number: " + value, error);
    }
    requireFinite(numericValue, fieldName);
    String inlineUnit = matcher.group(2);
    String unit =
        explicitUnit != null ? explicitUnit : inlineUnit != null ? inlineUnit : defaultUnit;
    convertLengthToMeters(numericValue, unit);
    return new Quantity(numericValue, unit);
  }

  /**
   * Applies an optional route quantity to the active segment.
   *
   * @param normalizedRow normalized row map
   * @param valueAliases accepted value column aliases
   * @param unitAliases accepted unit column aliases
   * @param defaultUnit default unit when omitted
   * @param quantityType quantity type: wallThickness, elevationChange, or roughness
   * @param segmentId segment id
   */
  private void applyOptionalQuantity(Map<String, String> normalizedRow, String[] valueAliases,
      String[] unitAliases, String defaultUnit, String quantityType, String segmentId) {
    Quantity quantity =
        readOptionalQuantity(normalizedRow, valueAliases, unitAliases, defaultUnit, quantityType);
    if (quantity == null) {
      return;
    }
    if ("wallThickness".equals(quantityType)) {
      setSegmentWallThickness(segmentId, quantity.value, quantity.unit);
    } else if ("elevationChange".equals(quantityType)) {
      setSegmentElevationChange(segmentId, quantity.value, quantity.unit);
    } else if ("roughness".equals(quantityType)) {
      setSegmentPipeWallRoughness(segmentId, quantity.value, quantity.unit);
    }
  }

  /**
   * Applies minor-loss columns from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param segmentId target segment id
   */
  private void applyMinorLosses(Map<String, String> normalizedRow, String segmentId) {
    String minorLossType = getOptionalValue(normalizedRow, MINOR_LOSS_TYPE_COLUMNS);
    Double minorLossK = readOptionalKValue(normalizedRow, MINOR_LOSS_K_COLUMNS);
    if (minorLossK != null) {
      addMinorLoss(segmentId, minorLossType == null ? "minor loss" : minorLossType,
          minorLossK.doubleValue());
    }

    applyNamedMinorLoss(normalizedRow, segmentId, "valve",
        new String[] {"valve_k", "valve_loss_k"});
    applyNamedMinorLoss(normalizedRow, segmentId, "fitting",
        new String[] {"fittings_k", "fitting_loss_k"});
    applyNamedMinorLoss(normalizedRow, segmentId, "bend", new String[] {"bend_k", "elbow_k"});
    applyNamedMinorLoss(normalizedRow, segmentId, "tee", new String[] {"tee_k"});
    applyNamedMinorLoss(normalizedRow, segmentId, "strainer", new String[] {"strainer_k"});
    applyNamedMinorLoss(normalizedRow, segmentId, "reducer",
        new String[] {"reducer_k", "expander_k"});

    String minorLossList = getOptionalValue(normalizedRow, MINOR_LOSS_LIST_COLUMNS);
    if (minorLossList != null) {
      addMinorLossList(segmentId, minorLossList);
    }
  }

  /**
   * Applies one named optional K-value column.
   *
   * @param normalizedRow normalized row map
   * @param segmentId target segment id
   * @param fittingType fitting type label
   * @param aliases accepted K-value column aliases
   */
  private void applyNamedMinorLoss(Map<String, String> normalizedRow, String segmentId,
      String fittingType, String[] aliases) {
    Double kValue = readOptionalKValue(normalizedRow, aliases);
    if (kValue != null) {
      addMinorLoss(segmentId, fittingType, kValue.doubleValue());
    }
  }

  /**
   * Reads an optional K value from a normalized row.
   *
   * @param normalizedRow normalized row map
   * @param aliases accepted K-value column aliases
   * @return K value, or null when absent
   */
  private static Double readOptionalKValue(Map<String, String> normalizedRow, String[] aliases) {
    String value = getOptionalValue(normalizedRow, aliases);
    if (value == null) {
      return null;
    }
    return Double.valueOf(parseKValue(value, aliases[0]));
  }

  /**
   * Parses and adds a list of K-value minor losses.
   *
   * @param segmentId target segment id
   * @param minorLossList minor-loss list text
   */
  private void addMinorLossList(String segmentId, String minorLossList) {
    String[] entries = minorLossList.split("[;|]");
    for (String entry : entries) {
      String cleanEntry = entry.trim();
      if (cleanEntry.isEmpty()) {
        continue;
      }
      int separatorIndex = Math.max(cleanEntry.lastIndexOf(':'), cleanEntry.lastIndexOf('='));
      if (separatorIndex < 0) {
        separatorIndex = cleanEntry.lastIndexOf(' ');
      }
      if (separatorIndex < 0) {
        addMinorLoss(segmentId, "minor loss", parseKValue(cleanEntry, "minorLoss"));
      } else {
        String fittingType = cleanEntry.substring(0, separatorIndex).trim();
        String kValueText = cleanEntry.substring(separatorIndex + 1).trim();
        addMinorLoss(segmentId, fittingType.isEmpty() ? "minor loss" : fittingType,
            parseKValue(kValueText, "minorLoss"));
      }
    }
  }

  /**
   * Parses a non-negative K value.
   *
   * @param value raw value text
   * @param fieldName field name for error messages
   * @return parsed K value
   * @throws IllegalArgumentException if the value is invalid or negative
   */
  private static double parseKValue(String value, String fieldName) {
    String cleanValue = requireText(value, fieldName);
    Matcher matcher = NUMBER_WITH_OPTIONAL_UNIT.matcher(cleanValue);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(fieldName + " must be a dimensionless K value: " + value);
    }
    String inlineUnit = matcher.group(2);
    if (inlineUnit != null && !"dimensionless".equalsIgnoreCase(inlineUnit)) {
      throw new IllegalArgumentException(
          fieldName + " must be dimensionless, got unit: " + inlineUnit);
    }
    try {
      return requireNonNegative(Double.parseDouble(matcher.group(1)), fieldName);
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(fieldName + " is not a valid K value: " + value, error);
    }
  }

  /**
   * Requires a non-empty text value.
   *
   * @param value text value to validate
   * @param fieldName field name used in error messages
   * @return trimmed text value
   * @throws IllegalArgumentException if the value is null or blank
   */
  private static String requireText(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Requires a finite positive value.
   *
   * @param value numeric value to validate
   * @param fieldName field name used in error messages
   * @return validated value
   * @throws IllegalArgumentException if the value is not finite and positive
   */
  private static double requirePositive(double value, String fieldName) {
    requireFinite(value, fieldName);
    if (value <= 0.0) {
      throw new IllegalArgumentException(fieldName + " must be positive, got: " + value);
    }
    return value;
  }

  /**
   * Requires a finite non-negative value.
   *
   * @param value numeric value to validate
   * @param fieldName field name used in error messages
   * @return validated value
   * @throws IllegalArgumentException if the value is not finite or is negative
   */
  private static double requireNonNegative(double value, String fieldName) {
    requireFinite(value, fieldName);
    if (value < 0.0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + value);
    }
    return value;
  }

  /**
   * Requires a finite value.
   *
   * @param value numeric value to validate
   * @param fieldName field name used in error messages
   * @return validated value
   * @throws IllegalArgumentException if the value is NaN or infinite
   */
  private static double requireFinite(double value, String fieldName) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(fieldName + " must be finite, got: " + value);
    }
    return value;
  }

  /**
   * Converts a length-like value to meters.
   *
   * @param value value to convert
   * @param unit source unit, for example {@code m}, {@code km}, {@code mm}, {@code inch},
   *        {@code ft}, or {@code micrometer}
   * @return value converted to meters
   * @throws IllegalArgumentException if the unit is blank or unsupported
   */
  private static double convertLengthToMeters(double value, String unit) {
    String cleanUnit = requireText(unit, "unit").toLowerCase(Locale.ROOT);
    if ("m".equals(cleanUnit) || "meter".equals(cleanUnit) || "meters".equals(cleanUnit)
        || "metre".equals(cleanUnit) || "metres".equals(cleanUnit)) {
      return value;
    } else if ("km".equals(cleanUnit) || "kilometer".equals(cleanUnit)
        || "kilometers".equals(cleanUnit) || "kilometre".equals(cleanUnit)
        || "kilometres".equals(cleanUnit)) {
      return value * 1000.0;
    } else if ("cm".equals(cleanUnit) || "centimeter".equals(cleanUnit)
        || "centimeters".equals(cleanUnit) || "centimetre".equals(cleanUnit)
        || "centimetres".equals(cleanUnit)) {
      return value * 0.01;
    } else if ("mm".equals(cleanUnit) || "millimeter".equals(cleanUnit)
        || "millimeters".equals(cleanUnit) || "millimetre".equals(cleanUnit)
        || "millimetres".equals(cleanUnit)) {
      return value * 1.0e-3;
    } else if ("um".equals(cleanUnit) || "micrometer".equals(cleanUnit)
        || "micrometers".equals(cleanUnit) || "micrometre".equals(cleanUnit)
        || "micrometres".equals(cleanUnit) || "micron".equals(cleanUnit)
        || "microns".equals(cleanUnit)) {
      return value * 1.0e-6;
    } else if ("in".equals(cleanUnit) || "inch".equals(cleanUnit) || "inches".equals(cleanUnit)) {
      return value * 0.0254;
    } else if ("ft".equals(cleanUnit) || "foot".equals(cleanUnit) || "feet".equals(cleanUnit)) {
      return value * 0.3048;
    }
    throw new IllegalArgumentException("Unsupported length unit: " + unit);
  }

  /**
   * Parsed value and unit from a line-list cell.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class Quantity {
    private final double value;
    private final String unit;

    /**
     * Creates a parsed quantity.
     *
     * @param value numeric source value
     * @param unit source unit text
     */
    private Quantity(double value, String unit) {
      this.value = value;
      this.unit = unit;
    }
  }

  /**
   * One route segment from a line-list table.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class RouteSegment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String segmentId;
    private final String fromNode;
    private final String toNode;
    private final double sourceLength;
    private final double lengthMeters;
    private final double sourceNominalDiameter;
    private final double nominalDiameterMeters;
    private double pipeWallRoughnessMeters = Double.NaN;
    private double wallThicknessMeters = Double.NaN;
    private double elevationChangeMeters = 0.0;
    private final List<MinorLoss> minorLosses = new ArrayList<MinorLoss>();

    /**
     * Creates one route segment.
     *
     * @param segmentId unique segment id
     * @param fromNode upstream node name
     * @param toNode downstream node name
     * @param sourceLength original length value before unit conversion
     * @param lengthMeters converted length in meters
     * @param sourceNominalDiameter original diameter value before unit conversion
     * @param nominalDiameterMeters converted hydraulic diameter in meters
     */
    private RouteSegment(String segmentId, String fromNode, String toNode, double sourceLength,
        double lengthMeters, double sourceNominalDiameter, double nominalDiameterMeters) {
      this.segmentId = segmentId;
      this.fromNode = fromNode;
      this.toNode = toNode;
      this.sourceLength = sourceLength;
      this.lengthMeters = lengthMeters;
      this.sourceNominalDiameter = sourceNominalDiameter;
      this.nominalDiameterMeters = nominalDiameterMeters;
    }

    /**
     * Returns the segment id.
     *
     * @return segment id
     */
    public String getSegmentId() {
      return segmentId;
    }

    /**
     * Returns the upstream node name.
     *
     * @return upstream node name
     */
    public String getFromNode() {
      return fromNode;
    }

    /**
     * Returns the downstream node name.
     *
     * @return downstream node name
     */
    public String getToNode() {
      return toNode;
    }

    /**
     * Returns straight segment length in meters.
     *
     * @return length in meters
     */
    public double getLengthMeters() {
      return lengthMeters;
    }

    /**
     * Returns hydraulic diameter in meters.
     *
     * @return hydraulic diameter in meters
     */
    public double getNominalDiameterMeters() {
      return nominalDiameterMeters;
    }

    /**
     * Returns elevation change in meters.
     *
     * @return elevation change in meters
     */
    public double getElevationChangeMeters() {
      return elevationChangeMeters;
    }

    /**
     * Returns wall thickness in meters.
     *
     * @return wall thickness in meters, or NaN when not specified
     */
    public double getWallThicknessMeters() {
      return wallThicknessMeters;
    }

    /**
     * Returns pipe wall roughness in meters.
     *
     * @return pipe wall roughness in meters, or NaN when not specified
     */
    public double getPipeWallRoughnessMeters() {
      return pipeWallRoughnessMeters;
    }

    /**
     * Returns minor losses for this segment.
     *
     * @return unmodifiable minor-loss list
     */
    public List<MinorLoss> getMinorLosses() {
      return Collections.unmodifiableList(minorLosses);
    }

    /**
     * Returns the total K value for all minor losses on the segment.
     *
     * @return total K value
     */
    public double getTotalKValue() {
      double totalKValue = 0.0;
      for (MinorLoss minorLoss : minorLosses) {
        totalKValue += minorLoss.kValue;
      }
      return totalKValue;
    }

    /**
     * Returns total equivalent length ratio L/D for all minor losses on the segment.
     *
     * @return total equivalent length ratio
     */
    public double getTotalEquivalentLengthRatio() {
      double totalLengthRatio = 0.0;
      for (MinorLoss minorLoss : minorLosses) {
        totalLengthRatio += minorLoss.equivalentLengthRatio;
      }
      return totalLengthRatio;
    }

    /**
     * Returns the generated pipe equipment name used in the built process system.
     *
     * @return generated pipe equipment name
     */
    public String getPipeName() {
      return "Pipe " + segmentId + " " + fromNode + " to " + toNode;
    }

    /**
     * Returns the route reference using an arrow separator.
     *
     * @return route reference string
     */
    private String getArrowReference() {
      return fromNode + "->" + toNode;
    }

    /**
     * Returns the route reference using a text separator.
     *
     * @return route reference string
     */
    private String getTextReference() {
      return fromNode + " to " + toNode;
    }

    /**
     * Returns the segment roughness or the builder default roughness.
     *
     * @param defaultRoughnessMeters builder default roughness in meters
     * @return roughness in meters
     */
    private double getPipeWallRoughness(double defaultRoughnessMeters) {
      return Double.isNaN(pipeWallRoughnessMeters) ? defaultRoughnessMeters
          : pipeWallRoughnessMeters;
    }

    /**
     * Converts the segment to a JSON-friendly map.
     *
     * @param defaultRoughnessMeters builder default roughness in meters
     * @return map representation of the segment
     */
    private Map<String, Object> toMap(double defaultRoughnessMeters) {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("segmentId", segmentId);
      map.put("fromNode", fromNode);
      map.put("toNode", toNode);
      map.put("pipeName", getPipeName());
      map.put("sourceLength", sourceLength);
      map.put("length_m", lengthMeters);
      map.put("sourceNominalDiameter", sourceNominalDiameter);
      map.put("nominalDiameter_m", nominalDiameterMeters);
      map.put("wallThickness_m", wallThicknessMeters);
      map.put("elevationChange_m", elevationChangeMeters);
      map.put("pipeWallRoughness_m", getPipeWallRoughness(defaultRoughnessMeters));
      map.put("totalKValue", getTotalKValue());
      map.put("totalEquivalentLengthRatio", getTotalEquivalentLengthRatio());

      List<Map<String, Object>> lossMaps = new ArrayList<Map<String, Object>>();
      for (MinorLoss minorLoss : minorLosses) {
        lossMaps.add(minorLoss.toMap());
      }
      map.put("minorLosses", lossMaps);
      return map;
    }
  }

  /**
   * One K-value minor loss attached to a route segment.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class MinorLoss implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String fittingType;
    private final double kValue;
    private double equivalentLengthRatio;

    /**
     * Creates one minor loss.
     *
     * @param fittingType fitting or valve description
     * @param kValue resistance coefficient K
     * @param equivalentLengthRatio equivalent length ratio L/D
     */
    private MinorLoss(String fittingType, double kValue, double equivalentLengthRatio) {
      this.fittingType = fittingType;
      this.kValue = kValue;
      this.equivalentLengthRatio = equivalentLengthRatio;
    }

    /**
     * Returns the fitting type description.
     *
     * @return fitting type description
     */
    public String getFittingType() {
      return fittingType;
    }

    /**
     * Returns resistance coefficient K.
     *
     * @return resistance coefficient K
     */
    public double getKValue() {
      return kValue;
    }

    /**
     * Returns equivalent length ratio L/D.
     *
     * @return equivalent length ratio L/D
     */
    public double getEquivalentLengthRatio() {
      return equivalentLengthRatio;
    }

    /**
     * Converts the minor loss to a JSON-friendly map.
     *
     * @return map representation of the minor loss
     */
    private Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("fittingType", fittingType);
      map.put("kValue", kValue);
      map.put("equivalentLengthRatio", equivalentLengthRatio);
      return map;
    }
  }
}
