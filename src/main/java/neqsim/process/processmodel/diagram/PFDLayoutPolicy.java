package neqsim.process.processmodel.diagram;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorInterface;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.expander.ExpanderInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.pump.PumpInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.SplitterInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.processmodel.graph.ProcessEdge;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessNode;
import neqsim.thermo.system.SystemInterface;

/**
 * Layout intelligence layer for generating professional oil &amp; gas PFDs.
 *
 * <p>
 * This class applies engineering layout rules to produce diagrams that follow industry conventions:
 * </p>
 * <ul>
 * <li><b>Gravity logic</b> - Gas flows upward, liquids flow downward</li>
 * <li><b>Functional zoning</b> - Separation center, gas processing upper, liquid lower</li>
 * <li><b>Equipment semantics</b> - Separator outlets positioned correctly (gas top, liquid
 * bottom)</li>
 * <li><b>Phase-aware routing</b> - Stream colors and paths based on phase</li>
 * <li><b>Stable layout</b> - Same model produces same diagram every time</li>
 * </ul>
 *
 * <p>
 * For three-phase separators, the layout follows gravity-based conventions:
 * </p>
 * <ul>
 * <li>Gas outlet exits from <b>top</b> (lightest phase)</li>
 * <li>Oil outlet exits from <b>middle</b> (intermediate density)</li>
 * <li>Aqueous/water outlet exits from <b>bottom</b> (heaviest phase)</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PFDLayoutPolicy implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Cache for equipment role classifications. */
  private final Map<ProcessEquipmentInterface, EquipmentRole> roleCache = new HashMap<>();

  /** Cache for stream phase classifications. */
  private final Map<StreamInterface, StreamPhase> phaseCache = new HashMap<>();

  /**
   * Stream phase classification based on vapor/liquid fraction.
   */
  public enum StreamPhase {
    /** Predominantly gas phase (&gt;90% vapor). */
    GAS("#87CEEB", "dashed", "Gas"),
    /** Predominantly liquid phase (&gt;90% liquid). */
    LIQUID("#4169E1", "solid", "Liquid"),
    /** Oil phase (hydrocarbon liquid). */
    OIL("#8B4513", "solid", "Oil"),
    /** Aqueous/water phase (heaviest liquid). */
    AQUEOUS("#1E90FF", "solid", "Aqueous"),
    /** Mixed phase (two-phase flow). */
    MIXED("#FFD700", "bold", "Mixed"),
    /** Unknown phase (not calculated). */
    UNKNOWN("#808080", "dotted", "Unknown");

    private final String color;
    private final String lineStyle;
    private final String label;

    StreamPhase(String color, String lineStyle, String label) {
      this.color = color;
      this.lineStyle = lineStyle;
      this.label = label;
    }

    /**
     * Gets the recommended color for this phase.
     *
     * @return hex color string
     */
    public String getColor() {
      return color;
    }

    /**
     * Gets the recommended line style for Graphviz.
     *
     * @return line style string (solid, dashed, dotted, bold)
     */
    public String getLineStyle() {
      return lineStyle;
    }

    /**
     * Gets the display label for this phase.
     *
     * @return display label
     */
    public String getLabel() {
      return label;
    }
  }

  /**
   * Separator outlet type for proper positioning.
   *
   * <p>
   * For three-phase separators:
   * </p>
   * <ul>
   * <li>Gas exits from top (north)</li>
   * <li>Oil exits from middle (east - between gas and water)</li>
   * <li>Water/Aqueous exits from bottom (south) - heaviest liquid</li>
   * </ul>
   */
  public enum SeparatorOutlet {
    /** Gas outlet - exits from top. */
    GAS_TOP("n", 0),
    /** Oil outlet (three-phase separator) - exits from middle/side. */
    OIL_MIDDLE("e", 1),
    /** Water/Aqueous outlet (three-phase separator) - exits from bottom (heaviest). */
    WATER_BOTTOM("s", 2),
    /** Generic liquid outlet (two-phase separator) - exits from bottom. */
    LIQUID_BOTTOM("s", 2),
    /** Feed inlet - enters from side. */
    FEED_SIDE("w", 1);

    private final String port;
    private final int rankOffset;

    SeparatorOutlet(String port, int rankOffset) {
      this.port = port;
      this.rankOffset = rankOffset;
    }

    /**
     * Gets the Graphviz port position.
     *
     * @return port position (n, s, e, w, etc.)
     */
    public String getPort() {
      return port;
    }

    /**
     * Gets the rank offset for layout.
     *
     * @return rank offset (0=same, positive=lower)
     */
    public int getRankOffset() {
      return rankOffset;
    }
  }

  /**
   * Horizontal process position for left-to-right flow convention.
   *
   * <p>
   * Oil &amp; gas PFDs follow a left-to-right flow convention:
   * </p>
   * <ul>
   * <li>Feed/inlet streams enter from the LEFT</li>
   * <li>Processing equipment in the CENTER</li>
   * <li>Product/outlet streams exit to the RIGHT</li>
   * </ul>
   */
  public enum ProcessPosition {
    /** Feed/inlet position - left side of diagram. */
    INLET(0, "left"),
    /** Processing position - center of diagram. */
    CENTER(1, "center"),
    /** Product/outlet position - right side of diagram. */
    OUTLET(2, "right");

    private final int horizontalRank;
    private final String description;

    ProcessPosition(int horizontalRank, String description) {
      this.horizontalRank = horizontalRank;
      this.description = description;
    }

    /**
     * Gets the horizontal rank for left-to-right ordering.
     *
     * @return horizontal rank (0=left, higher=right)
     */
    public int getHorizontalRank() {
      return horizontalRank;
    }

    /**
     * Gets the position description.
     *
     * @return description string
     */
    public String getDescription() {
      return description;
    }
  }

  /**
   * Vertical phase zone for gravity-based positioning.
   *
   * <p>
   * Oil &amp; gas PFDs follow gravity conventions for vertical positioning:
   * </p>
   * <ul>
   * <li>Gas processing equipment at TOP (lightest phase)</li>
   * <li>Oil processing equipment in MIDDLE (intermediate density)</li>
   * <li>Water processing equipment at BOTTOM (heaviest phase)</li>
   * </ul>
   */
  public enum PhaseZone {
    /** Gas zone - top of diagram. */
    GAS_TOP(0, "#87CEEB", "Gas Processing"),
    /** Oil zone - middle of diagram. */
    OIL_MIDDLE(1, "#8B4513", "Oil Processing"),
    /** Water zone - bottom of diagram. */
    WATER_BOTTOM(2, "#1E90FF", "Water Processing"),
    /** Mixed/separation zone - center anchor. */
    SEPARATION_CENTER(1, "#FFD700", "Separation"),
    /** Unknown zone. */
    UNKNOWN(1, "#808080", "Process");

    private final int verticalRank;
    private final String color;
    private final String label;

    PhaseZone(int verticalRank, String color, String label) {
      this.verticalRank = verticalRank;
      this.color = color;
      this.label = label;
    }

    /**
     * Gets the vertical rank for top-to-bottom ordering.
     *
     * @return vertical rank (0=top, higher=bottom)
     */
    public int getVerticalRank() {
      return verticalRank;
    }

    /**
     * Gets the zone color.
     *
     * @return hex color string
     */
    public String getColor() {
      return color;
    }

    /**
     * Gets the zone label.
     *
     * @return display label
     */
    public String getLabel() {
      return label;
    }
  }

  /**
   * Creates a new PFD layout policy with default settings.
   */
  public PFDLayoutPolicy() {}

  /**
   * Classifies the role of process equipment for layout purposes.
   *
   * @param equipment the equipment to classify
   * @return the equipment role
   */
  public EquipmentRole classifyEquipment(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return EquipmentRole.UNKNOWN;
    }

    // Check cache first
    EquipmentRole cached = roleCache.get(equipment);
    if (cached != null) {
      return cached;
    }

    EquipmentRole role = determineRole(equipment);
    roleCache.put(equipment, role);
    return role;
  }

  /**
   * Determines the role of equipment based on its type and context.
   *
   * @param equipment the equipment
   * @return the determined role
   */
  private EquipmentRole determineRole(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName().toLowerCase();

    // Control and utility equipment (Recycle, Adjuster, Calculator, Controller, SetPoint)
    if (className.contains("recycle") || className.contains("adjuster")
        || className.contains("calculator") || className.contains("controller")
        || className.contains("setpoint") || className.contains("control")) {
      return EquipmentRole.CONTROL;
    }

    // Separators and phase separation equipment
    if (equipment instanceof SeparatorInterface || className.contains("separator")
        || className.contains("scrubber") || className.contains("flash")
        || className.contains("knockout")) {
      return EquipmentRole.SEPARATOR;
    }

    // Distillation columns are special separators
    if (equipment instanceof DistillationColumn || className.contains("column")
        || className.contains("distillation") || className.contains("absorber")
        || className.contains("stripper")) {
      return EquipmentRole.SEPARATOR;
    }

    // Compressors handle gas
    if (equipment instanceof CompressorInterface || equipment instanceof Compressor
        || className.contains("compressor")) {
      return EquipmentRole.GAS;
    }

    // Expanders handle gas
    if (equipment instanceof ExpanderInterface || equipment instanceof Expander
        || className.contains("expander") || className.contains("turbine")) {
      return EquipmentRole.GAS;
    }

    // Pumps handle liquid
    if (equipment instanceof PumpInterface || equipment instanceof Pump
        || className.contains("pump")) {
      return EquipmentRole.LIQUID;
    }

    // Valves are control equipment
    if (equipment instanceof ValveInterface || className.contains("valve")) {
      return EquipmentRole.CONTROL;
    }

    // Heat exchangers are mixed (handle both phases)
    if (equipment instanceof HeatExchanger || className.contains("exchanger")) {
      return EquipmentRole.MIXED;
    }

    // Coolers may be gas or liquid depending on service
    if (equipment instanceof Cooler || className.contains("cooler")
        || className.contains("condenser")) {
      return classifyByPhase(equipment);
    }

    // Heaters may be gas or liquid depending on service
    if (equipment instanceof Heater || className.contains("heater")
        || className.contains("reboiler")) {
      return classifyByPhase(equipment);
    }

    // Mixers and splitters depend on phase
    if (equipment instanceof MixerInterface || equipment instanceof SplitterInterface) {
      return classifyByPhase(equipment);
    }

    // Streams - classify as feed or product based on context
    if (equipment instanceof StreamInterface) {
      return EquipmentRole.MIXED;
    }

    return EquipmentRole.UNKNOWN;
  }

  /**
   * Classifies equipment role based on the phase of its fluid.
   *
   * @param equipment the equipment
   * @return GAS, LIQUID, or MIXED based on phase types
   */
  private EquipmentRole classifyByPhase(ProcessEquipmentInterface equipment) {
    SystemInterface fluid = equipment.getFluid();
    if (fluid == null) {
      return EquipmentRole.MIXED;
    }

    try {
      // Check for phase types directly
      boolean hasGas = fluid.hasPhaseType("gas");
      boolean hasLiquid = fluid.hasPhaseType("liquid") || fluid.hasPhaseType("oil")
          || fluid.hasPhaseType("aqueous");

      int numPhases = fluid.getNumberOfPhases();

      if (numPhases == 1) {
        if (hasGas) {
          return EquipmentRole.GAS;
        }
        if (hasLiquid) {
          return EquipmentRole.LIQUID;
        }
      }

      // Multi-phase: mixed
      return EquipmentRole.MIXED;
    } catch (Exception e) {
      return EquipmentRole.MIXED;
    }
  }

  /**
   * Classifies the phase of a stream.
   *
   * @param stream the stream to classify
   * @return the stream phase
   */
  public StreamPhase classifyStreamPhase(StreamInterface stream) {
    if (stream == null) {
      return StreamPhase.UNKNOWN;
    }

    // Check cache
    StreamPhase cached = phaseCache.get(stream);
    if (cached != null) {
      return cached;
    }

    StreamPhase phase = determineStreamPhase(stream);
    phaseCache.put(stream, phase);
    return phase;
  }

  /**
   * Determines the phase of a stream based on phase types and composition.
   *
   * <p>
   * For streams leaving three-phase separators, this method distinguishes between oil and aqueous
   * phases using phase type information. For two-phase systems, it uses phase type detection.
   * </p>
   *
   * @param stream the stream
   * @return the determined phase (GAS, LIQUID, OIL, AQUEOUS, MIXED, or UNKNOWN)
   */
  private StreamPhase determineStreamPhase(StreamInterface stream) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return StreamPhase.UNKNOWN;
    }

    try {
      // Check for phase types directly using hasPhaseType
      boolean hasGas = fluid.hasPhaseType("gas");
      boolean hasOil = fluid.hasPhaseType("oil");
      boolean hasAqueous = fluid.hasPhaseType("aqueous");
      boolean hasLiquid = fluid.hasPhaseType("liquid");

      int numPhases = fluid.getNumberOfPhases();

      // Single phase systems
      if (numPhases == 1) {
        if (hasGas) {
          return StreamPhase.GAS;
        }
        if (hasOil) {
          return StreamPhase.OIL;
        }
        if (hasAqueous) {
          return StreamPhase.AQUEOUS;
        }
        if (hasLiquid) {
          return StreamPhase.LIQUID;
        }
      }

      // Two-phase systems - check which phase dominates
      if (numPhases == 2) {
        if (hasGas && (hasLiquid || hasOil || hasAqueous)) {
          // Mixed gas-liquid
          return StreamPhase.MIXED;
        }
        if (hasOil && hasAqueous) {
          // Oil-water system - could be either based on dominant phase
          return StreamPhase.LIQUID;
        }
      }

      // Multi-phase - classify as mixed
      if (numPhases > 1) {
        return StreamPhase.MIXED;
      }

      // Fallback to stream name hints
      return classifyByStreamName(stream);
    } catch (Exception e) {
      return StreamPhase.UNKNOWN;
    }
  }

  /**
   * Classifies stream phase based on stream name hints.
   *
   * @param stream the stream
   * @return the phase based on name
   */
  private StreamPhase classifyByStreamName(StreamInterface stream) {
    String streamName = stream.getName().toLowerCase();
    if (streamName.contains("gas") || streamName.contains("vapor")) {
      return StreamPhase.GAS;
    }
    if (streamName.contains("oil")) {
      return StreamPhase.OIL;
    }
    if (streamName.contains("water") || streamName.contains("aqueous")) {
      return StreamPhase.AQUEOUS;
    }
    if (streamName.contains("liquid")) {
      return StreamPhase.LIQUID;
    }
    return StreamPhase.UNKNOWN;
  }

  /**
   * Classifies a liquid-dominated stream as OIL, AQUEOUS, or LIQUID.
   *
   * <p>
   * This method uses phase type information when available, and falls back to stream name hints
   * when phase type cannot be determined.
   * </p>
   *
   * @param fluid the fluid to check
   * @param stream the stream (for name hints)
   * @return OIL, AQUEOUS, or generic LIQUID
   */
  private StreamPhase classifyLiquidPhase(SystemInterface fluid, StreamInterface stream) {
    // Try to determine phase type from the fluid
    try {
      // Check if the fluid has only one liquid phase by type
      boolean hasOil = fluid.hasPhaseType("oil");
      boolean hasAqueous = fluid.hasPhaseType("aqueous");

      // If only oil phase present
      if (hasOil && !hasAqueous) {
        return StreamPhase.OIL;
      }
      // If only aqueous phase present
      if (hasAqueous && !hasOil) {
        return StreamPhase.AQUEOUS;
      }
    } catch (Exception e) {
      // Fall through to name-based classification
    }

    // Fall back to stream name hints
    String streamName = stream.getName().toLowerCase();
    if (streamName.contains("oil")) {
      return StreamPhase.OIL;
    }
    if (streamName.contains("water") || streamName.contains("aqueous")) {
      return StreamPhase.AQUEOUS;
    }

    // Default to generic liquid
    return StreamPhase.LIQUID;
  }

  /**
   * Classifies the phase of an edge (stream connection).
   *
   * @param edge the process edge
   * @return the stream phase
   */
  public StreamPhase classifyEdgePhase(ProcessEdge edge) {
    if (edge == null) {
      return StreamPhase.UNKNOWN;
    }

    StreamInterface stream = edge.getStream();
    if (stream != null) {
      return classifyStreamPhase(stream);
    }

    // If no stream, try to infer from source equipment
    ProcessNode source = edge.getSource();
    if (source != null) {
      EquipmentRole sourceRole = classifyEquipment(source.getEquipment());
      switch (sourceRole) {
        case GAS:
          return StreamPhase.GAS;
        case LIQUID:
          return StreamPhase.LIQUID;
        default:
          return StreamPhase.MIXED;
      }
    }

    return StreamPhase.UNKNOWN;
  }

  /**
   * Determines the separator outlet type for an edge leaving a separator.
   *
   * <p>
   * For three-phase separators, outlets are positioned by gravity:
   * </p>
   * <ul>
   * <li>Gas outlet: top (north) - lightest phase</li>
   * <li>Oil outlet: middle (east) - intermediate density</li>
   * <li>Aqueous/water outlet: bottom (south) - heaviest phase</li>
   * </ul>
   *
   * @param separator the separator equipment
   * @param outletStream the outlet stream
   * @return the outlet type for positioning
   */
  public SeparatorOutlet classifySeparatorOutlet(ProcessEquipmentInterface separator,
      StreamInterface outletStream) {
    if (separator == null || outletStream == null) {
      return SeparatorOutlet.FEED_SIDE;
    }

    // For ThreePhaseSeparator, check by object identity first
    if (separator instanceof ThreePhaseSeparator) {
      ThreePhaseSeparator threePhaseSep = (ThreePhaseSeparator) separator;
      return classifyThreePhaseSeparatorOutlet(threePhaseSep, outletStream);
    }

    // For regular (two-phase) Separator class
    if (separator instanceof Separator) {
      Separator sep = (Separator) separator;
      // Check if this is the gas outlet by object reference
      if (outletStream == sep.getGasOutStream()) {
        return SeparatorOutlet.GAS_TOP;
      }
      // Check if this is the liquid outlet by object reference
      if (outletStream == sep.getLiquidOutStream()) {
        return SeparatorOutlet.LIQUID_BOTTOM;
      }
    }

    // Fall back to name-based and phase-based classification
    return classifySeparatorOutletByNameAndPhase(outletStream);
  }

  /**
   * Classifies the outlet type for a three-phase separator.
   *
   * <p>
   * Three-phase separator outlets by gravity (top to bottom):
   * </p>
   * <ol>
   * <li>Gas - lightest, exits top</li>
   * <li>Oil - intermediate, exits middle</li>
   * <li>Aqueous/Water - heaviest, exits bottom</li>
   * </ol>
   *
   * @param separator the three-phase separator
   * @param outletStream the outlet stream to classify
   * @return the separator outlet type
   */
  private SeparatorOutlet classifyThreePhaseSeparatorOutlet(ThreePhaseSeparator separator,
      StreamInterface outletStream) {
    // Check by object reference for accurate identification
    if (outletStream == separator.getGasOutStream()) {
      return SeparatorOutlet.GAS_TOP;
    }
    if (outletStream == separator.getOilOutStream()) {
      return SeparatorOutlet.OIL_MIDDLE;
    }
    if (outletStream == separator.getWaterOutStream()) {
      return SeparatorOutlet.WATER_BOTTOM;
    }

    // Fall back to name-based classification
    return classifySeparatorOutletByNameAndPhase(outletStream);
  }

  /**
   * Classifies separator outlet by stream name and phase composition.
   *
   * @param outletStream the outlet stream
   * @return the separator outlet type
   */
  private SeparatorOutlet classifySeparatorOutletByNameAndPhase(StreamInterface outletStream) {
    StreamPhase phase = classifyStreamPhase(outletStream);
    String streamName = outletStream.getName().toLowerCase();

    // Check name hints first
    if (streamName.contains("gas") || streamName.contains("vapor") || streamName.contains("top")) {
      return SeparatorOutlet.GAS_TOP;
    }
    if (streamName.contains("oil")) {
      return SeparatorOutlet.OIL_MIDDLE;
    }
    if (streamName.contains("water") || streamName.contains("aqueous")) {
      return SeparatorOutlet.WATER_BOTTOM;
    }
    if (streamName.contains("liquid") || streamName.contains("bottom")) {
      return SeparatorOutlet.LIQUID_BOTTOM;
    }

    // Fall back to phase classification
    switch (phase) {
      case GAS:
        return SeparatorOutlet.GAS_TOP;
      case LIQUID:
        return SeparatorOutlet.LIQUID_BOTTOM;
      default:
        return SeparatorOutlet.LIQUID_BOTTOM;
    }
  }

  /**
   * Gets the Graphviz rank constraint for a node based on its equipment role.
   *
   * <p>
   * Uses oil &amp; gas conventions:
   * </p>
   * <ul>
   * <li>Gas equipment: rank=min (top)</li>
   * <li>Separators: rank=same (center anchor)</li>
   * <li>Liquid equipment: rank=max (bottom)</li>
   * </ul>
   *
   * @param node the process node
   * @return rank constraint string or null if no constraint
   */
  public String getRankConstraint(ProcessNode node) {
    if (node == null) {
      return null;
    }

    EquipmentRole role = classifyEquipment(node.getEquipment());
    switch (role) {
      case GAS:
        return "min";
      case LIQUID:
        return "max";
      case SEPARATOR:
        return "same";
      default:
        return null;
    }
  }

  /**
   * Gets the vertical rank level for a node (0 = top, higher = lower).
   *
   * @param node the process node
   * @return rank level
   */
  public int getRankLevel(ProcessNode node) {
    if (node == null) {
      return 1;
    }
    return classifyEquipment(node.getEquipment()).getRankPriority();
  }

  /**
   * Clears all cached classifications.
   */
  public void clearCache() {
    roleCache.clear();
    phaseCache.clear();
  }

  /**
   * Determines the horizontal process position for a node.
   *
   * <p>
   * Uses graph topology to determine position:
   * </p>
   * <ul>
   * <li>Source nodes (no incoming edges) → INLET (left)</li>
   * <li>Sink nodes (no outgoing edges) → OUTLET (right)</li>
   * <li>All other nodes → CENTER</li>
   * </ul>
   *
   * @param node the process node
   * @param graph the process graph (for topology analysis)
   * @return the horizontal position
   */
  public ProcessPosition classifyHorizontalPosition(ProcessNode node, ProcessGraph graph) {
    if (node == null) {
      return ProcessPosition.CENTER;
    }

    // Source nodes (feeds) go on left
    if (node.isSource()) {
      return ProcessPosition.INLET;
    }

    // Sink nodes (products) go on right
    if (node.isSink()) {
      return ProcessPosition.OUTLET;
    }

    // Everything else is in the center
    return ProcessPosition.CENTER;
  }

  /**
   * Determines the vertical phase zone for a node.
   *
   * <p>
   * Uses equipment role and stream phase to determine vertical positioning:
   * </p>
   * <ul>
   * <li>Gas processing equipment → GAS_TOP</li>
   * <li>Separators → SEPARATION_CENTER</li>
   * <li>Liquid/oil processing → OIL_MIDDLE</li>
   * <li>Water processing → WATER_BOTTOM</li>
   * </ul>
   *
   * @param node the process node
   * @return the vertical phase zone
   */
  public PhaseZone classifyPhaseZone(ProcessNode node) {
    if (node == null) {
      return PhaseZone.UNKNOWN;
    }

    ProcessEquipmentInterface equipment = node.getEquipment();
    EquipmentRole role = classifyEquipment(equipment);

    switch (role) {
      case GAS:
        return PhaseZone.GAS_TOP;

      case SEPARATOR:
        return PhaseZone.SEPARATION_CENTER;

      case LIQUID:
        // Try to distinguish oil vs water processing
        return classifyLiquidZone(node);

      case MIXED:
      case CONTROL:
      case UNKNOWN:
        // For mixed/control/unknown equipment, trace upstream to determine zone
        return classifyByUpstreamPhase(node);

      default:
        return PhaseZone.UNKNOWN;
    }
  }

  /**
   * Classifies a node's phase zone by tracing its upstream connections.
   *
   * <p>
   * This method is used for equipment that could handle any phase (mixers, heaters, coolers, valves
   * etc.) by examining what streams feed into it.
   * </p>
   *
   * @param node the process node
   * @return the phase zone based on upstream connections
   */
  private PhaseZone classifyByUpstreamPhase(ProcessNode node) {
    // Check equipment name for hints first
    String name = node.getName().toLowerCase();
    if (name.contains("gas") || name.contains("vapor")) {
      return PhaseZone.GAS_TOP;
    }
    if (isWaterRelatedName(name)) {
      return PhaseZone.WATER_BOTTOM;
    }
    if (name.contains("oil") || name.contains("crude") || name.contains("condensate")) {
      return PhaseZone.OIL_MIDDLE;
    }

    // Check inlet streams
    List<ProcessEdge> incomingEdges = node.getIncomingEdges();
    for (ProcessEdge edge : incomingEdges) {
      if (edge.getStream() != null) {
        // Check stream name
        String streamName = edge.getStream().getName().toLowerCase();
        if (streamName.contains("gas") || streamName.contains("vapor")) {
          return PhaseZone.GAS_TOP;
        }
        if (isWaterRelatedName(streamName)) {
          return PhaseZone.WATER_BOTTOM;
        }
        if (streamName.contains("oil") || streamName.contains("condensate")
            || streamName.contains("crude")) {
          return PhaseZone.OIL_MIDDLE;
        }

        // Check stream phase from thermodynamic properties
        StreamPhase phase = classifyStreamPhase(edge.getStream());
        if (phase == StreamPhase.GAS) {
          return PhaseZone.GAS_TOP;
        }
        if (phase == StreamPhase.AQUEOUS) {
          return PhaseZone.WATER_BOTTOM;
        }
        if (phase == StreamPhase.OIL) {
          return PhaseZone.OIL_MIDDLE;
        }
      }

      // Check if upstream is a separator - determine which outlet
      ProcessNode sourceNode = edge.getSourceNode();
      if (sourceNode != null) {
        ProcessEquipmentInterface upstreamEquip = sourceNode.getEquipment();
        if (upstreamEquip != null) {
          EquipmentRole upstreamRole = classifyEquipment(upstreamEquip);
          if (upstreamRole == EquipmentRole.SEPARATOR) {
            // Determine which outlet based on stream phase
            if (edge.getStream() != null) {
              StreamPhase phase = classifyStreamPhase(edge.getStream());
              if (phase == StreamPhase.GAS) {
                return PhaseZone.GAS_TOP;
              }
              if (phase == StreamPhase.AQUEOUS) {
                return PhaseZone.WATER_BOTTOM;
              }
              if (phase == StreamPhase.OIL || phase == StreamPhase.LIQUID) {
                return PhaseZone.OIL_MIDDLE;
              }
            }
          }

          // Inherit zone from upstream gas or liquid equipment
          if (upstreamRole == EquipmentRole.GAS) {
            return PhaseZone.GAS_TOP;
          }
          if (upstreamRole == EquipmentRole.LIQUID) {
            return classifyLiquidZone(sourceNode);
          }
        }
      }
    }

    // Default to separation center for truly mixed equipment
    return PhaseZone.SEPARATION_CENTER;
  }

  /**
   * Classifies a liquid processing node as oil or water zone.
   *
   * <p>
   * This method traces the stream back to determine if it originates from a separator's oil or
   * water outlet, ensuring proper vertical positioning in the PFD.
   * </p>
   *
   * @param node the process node
   * @return OIL_MIDDLE or WATER_BOTTOM
   */
  private PhaseZone classifyLiquidZone(ProcessNode node) {
    // Check equipment name for hints
    String name = node.getName().toLowerCase();
    if (isWaterRelatedName(name)) {
      return PhaseZone.WATER_BOTTOM;
    }
    if (name.contains("oil") || name.contains("crude") || name.contains("condensate")) {
      return PhaseZone.OIL_MIDDLE;
    }

    // Check inlet stream phase and trace back to separator outlet
    List<ProcessEdge> incomingEdges = node.getIncomingEdges();
    for (ProcessEdge edge : incomingEdges) {
      // First check the stream name for separator outlet hints
      if (edge.getStream() != null) {
        String streamName = edge.getStream().getName().toLowerCase();
        // Check for water/aqueous outlet naming patterns
        if (isWaterRelatedName(streamName)) {
          return PhaseZone.WATER_BOTTOM;
        }
        // Check for oil outlet naming patterns
        if (streamName.contains("oil") || streamName.contains("liquid out")
            || streamName.contains("condensate") || streamName.contains("crude")) {
          return PhaseZone.OIL_MIDDLE;
        }

        // Check stream phase from thermodynamic properties
        StreamPhase phase = classifyStreamPhase(edge.getStream());
        if (phase == StreamPhase.AQUEOUS) {
          return PhaseZone.WATER_BOTTOM;
        }
        if (phase == StreamPhase.OIL) {
          return PhaseZone.OIL_MIDDLE;
        }
      }

      // Check if upstream is a separator - if so, check which outlet we're connected to
      ProcessNode sourceNode = edge.getSourceNode();
      if (sourceNode != null) {
        ProcessEquipmentInterface upstreamEquip = sourceNode.getEquipment();
        if (upstreamEquip != null) {
          EquipmentRole upstreamRole = classifyEquipment(upstreamEquip);
          if (upstreamRole == EquipmentRole.SEPARATOR) {
            // This node is downstream of a separator
            // Check the stream name or phase to determine oil vs water outlet
            if (edge.getStream() != null) {
              StreamPhase phase = classifyStreamPhase(edge.getStream());
              if (phase == StreamPhase.AQUEOUS) {
                return PhaseZone.WATER_BOTTOM;
              }
              // Non-aqueous liquid from separator = oil
              if (phase == StreamPhase.OIL || phase == StreamPhase.LIQUID) {
                return PhaseZone.OIL_MIDDLE;
              }
            }
            // Default: liquid from separator without clear phase = oil
            return PhaseZone.OIL_MIDDLE;
          }

          // If upstream is also liquid processing, inherit its zone
          if (upstreamRole == EquipmentRole.LIQUID) {
            PhaseZone upstreamZone = classifyPhaseZone(sourceNode);
            if (upstreamZone == PhaseZone.WATER_BOTTOM || upstreamZone == PhaseZone.OIL_MIDDLE) {
              return upstreamZone;
            }
          }
        }
      }
    }

    // Default to oil/middle for generic liquid
    return PhaseZone.OIL_MIDDLE;
  }

  /**
   * Checks if a name contains water-related keywords.
   *
   * @param name the name to check (should be lowercase)
   * @return true if the name indicates water/aqueous processing
   */
  private boolean isWaterRelatedName(String name) {
    return name.contains("water") || name.contains("aqueous") || name.contains("brine")
        || name.contains("produced water") || name.contains("wastewater")
        || name.contains("waste water") || name.contains("hydrate") || name.contains("dehydrat")
        || name.contains("glycol") || name.contains("meg") || name.contains("deg")
        || name.contains("teg");
  }

  /**
   * Gets the combined layout coordinates for a node.
   *
   * <p>
   * Returns a 2D coordinate where:
   * </p>
   * <ul>
   * <li>X (horizontal): 0=inlet/left, 1=center, 2=outlet/right</li>
   * <li>Y (vertical): 0=gas/top, 1=oil/middle, 2=water/bottom</li>
   * </ul>
   *
   * @param node the process node
   * @param graph the process graph
   * @return int array [x, y] representing layout position
   */
  public int[] getLayoutCoordinates(ProcessNode node, ProcessGraph graph) {
    ProcessPosition hPos = classifyHorizontalPosition(node, graph);
    PhaseZone vZone = classifyPhaseZone(node);
    return new int[] {hPos.getHorizontalRank(), vZone.getVerticalRank()};
  }
}
