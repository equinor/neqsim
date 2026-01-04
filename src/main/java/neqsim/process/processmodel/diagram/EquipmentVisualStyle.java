package neqsim.process.processmodel.diagram;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.dexpi.DexpiProcessUnit;

/**
 * Defines visual styling for process equipment in PFD diagrams.
 *
 * <p>
 * This class provides Graphviz-compatible visual attributes for each equipment type following oil
 * &amp; gas industry conventions:
 * </p>
 * <ul>
 * <li>Shapes that resemble P&amp;ID symbols</li>
 * <li>Colors that indicate function</li>
 * <li>Consistent sizing for professional appearance</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EquipmentVisualStyle implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Default node width in inches. */
  private static final String DEFAULT_WIDTH = "1.5";

  /** Default node height in inches. */
  private static final String DEFAULT_HEIGHT = "0.75";

  /** Font name for labels. */
  private static final String FONT_NAME = "Arial";

  /** Font size for labels. */
  private static final String FONT_SIZE = "10";

  /** Cached styles by equipment type. */
  private static final Map<String, EquipmentVisualStyle> STYLE_CACHE = new HashMap<>();

  /** Cached styles by EquipmentEnum. */
  private static final Map<EquipmentEnum, EquipmentVisualStyle> ENUM_CACHE = new HashMap<>();

  private final String shape;
  private final String fillColor;
  private final String borderColor;
  private final String fontColor;
  private final String width;
  private final String height;
  private final String style;

  static {
    initializeDefaultStyles();
  }

  /**
   * Creates a new visual style with the specified attributes.
   *
   * @param shape Graphviz shape name
   * @param fillColor fill color (hex or named)
   * @param borderColor border color (hex or named)
   * @param fontColor font color
   * @param width node width in inches
   * @param height node height in inches
   * @param style additional style (filled, rounded, etc.)
   */
  public EquipmentVisualStyle(String shape, String fillColor, String borderColor, String fontColor,
      String width, String height, String style) {
    this.shape = shape;
    this.fillColor = fillColor;
    this.borderColor = borderColor;
    this.fontColor = fontColor;
    this.width = width;
    this.height = height;
    this.style = style;
  }

  /**
   * Creates a visual style with default dimensions.
   *
   * @param shape Graphviz shape name
   * @param fillColor fill color
   * @param borderColor border color
   */
  public EquipmentVisualStyle(String shape, String fillColor, String borderColor) {
    this(shape, fillColor, borderColor, "black", DEFAULT_WIDTH, DEFAULT_HEIGHT, "filled,rounded");
  }

  /**
   * Initializes default styles for all NeqSim equipment types.
   *
   * <p>
   * Equipment categories follow industry P&amp;ID conventions:
   * </p>
   * <ul>
   * <li>Separators/Vessels: Cylinder shapes, green tones</li>
   * <li>Rotating equipment: Trapezoid/circle shapes, blue tones</li>
   * <li>Heat exchangers: Rectangle shapes, orange/yellow tones</li>
   * <li>Valves: Diamond shapes, pink tones</li>
   * <li>Control/Utility: Dashed outlines, gray tones</li>
   * </ul>
   */
  private static void initializeDefaultStyles() {
    // ========== SEPARATORS & VESSELS (Cylinder, Green tones) ==========
    STYLE_CACHE.put("separator", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22", "black",
        "1.2", "1.5", "filled"));
    STYLE_CACHE.put("threephaseseparator", new EquipmentVisualStyle("cylinder", "#98FB98",
        "#228B22", "black", "1.4", "1.8", "filled"));
    STYLE_CACHE.put("twophaseseparator", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22",
        "black", "1.2", "1.5", "filled"));
    STYLE_CACHE.put("scrubber", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22", "black",
        "1.0", "1.8", "filled"));
    STYLE_CACHE.put("gasscrubber", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22",
        "black", "1.0", "1.8", "filled"));
    STYLE_CACHE.put("simplegasscrubber", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22",
        "black", "1.0", "1.6", "filled"));
    STYLE_CACHE.put("knockout", new EquipmentVisualStyle("cylinder", "#90EE90", "#228B22", "black",
        "1.0", "1.5", "filled"));
    STYLE_CACHE.put("hydrocyclone", new EquipmentVisualStyle("invtrapezium", "#90EE90", "#228B22",
        "black", "0.8", "1.5", "filled"));
    STYLE_CACHE.put("flash", new EquipmentVisualStyle("cylinder", "#98FB98", "#228B22", "black",
        "1.0", "1.2", "filled"));

    // ========== DISTILLATION & ABSORPTION COLUMNS (Tall cylinder, Green) ==========
    STYLE_CACHE.put("distillationcolumn", new EquipmentVisualStyle("cylinder", "#98FB98", "#228B22",
        "black", "1.5", "3.0", "filled"));
    STYLE_CACHE.put("absorber", new EquipmentVisualStyle("cylinder", "#98FB98", "#228B22", "black",
        "1.3", "2.5", "filled"));
    STYLE_CACHE.put("simpletegabsorber", new EquipmentVisualStyle("cylinder", "#98FB98", "#228B22",
        "black", "1.3", "2.5", "filled"));
    STYLE_CACHE.put("waterstripper", new EquipmentVisualStyle("cylinder", "#98FB98", "#228B22",
        "black", "1.3", "2.5", "filled"));
    STYLE_CACHE.put("adsorber", new EquipmentVisualStyle("cylinder", "#D3D3D3", "#696969", "black",
        "1.2", "2.0", "filled"));

    // ========== COMPRESSORS (Parallelogram, Blue tones) ==========
    STYLE_CACHE.put("compressor", new EquipmentVisualStyle("parallelogram", "#87CEEB", "#4682B4",
        "black", "1.5", "0.8", "filled"));
    STYLE_CACHE.put("singlestagecompressor", new EquipmentVisualStyle("parallelogram", "#87CEEB",
        "#4682B4", "black", "1.5", "0.8", "filled"));
    STYLE_CACHE.put("multistagecompressor", new EquipmentVisualStyle("parallelogram", "#ADD8E6",
        "#4682B4", "black", "1.8", "1.0", "filled"));
    STYLE_CACHE.put("compressormodule", new EquipmentVisualStyle("parallelogram", "#B0E0E6",
        "#4682B4", "black", "2.0", "1.2", "filled"));

    // ========== EXPANDERS & TURBINES (Inverted trapezoid, Blue) ==========
    STYLE_CACHE.put("expander", new EquipmentVisualStyle("invtrapezium", "#87CEFA", "#4169E1",
        "black", "1.5", "0.8", "filled"));
    STYLE_CACHE.put("turboexpander", new EquipmentVisualStyle("invtrapezium", "#87CEFA", "#4169E1",
        "black", "1.5", "0.8", "filled"));
    STYLE_CACHE.put("turboexpandercompressor", new EquipmentVisualStyle("invtrapezium", "#87CEFA",
        "#4169E1", "black", "1.8", "1.0", "filled"));

    // ========== PUMPS (Circle, Dark Blue) ==========
    STYLE_CACHE.put("pump",
        new EquipmentVisualStyle("circle", "#4169E1", "#00008B", "white", "0.8", "0.8", "filled"));
    STYLE_CACHE.put("centrifugalpump",
        new EquipmentVisualStyle("circle", "#4169E1", "#00008B", "white", "0.8", "0.8", "filled"));
    STYLE_CACHE.put("esppump",
        new EquipmentVisualStyle("circle", "#4169E1", "#00008B", "white", "0.9", "0.9", "filled"));

    // ========== HEAT EXCHANGERS (Circle with arrow for heaters/coolers) ==========
    STYLE_CACHE.put("heatexchanger", new EquipmentVisualStyle("rect", "#FFD700", "#FF8C00", "black",
        "1.5", "0.6", "filled,rounded"));
    STYLE_CACHE.put("cooler",
        new EquipmentVisualStyle("circle", "#87CEEB", "#4682B4", "black", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("aircooler",
        new EquipmentVisualStyle("circle", "#87CEEB", "#4682B4", "black", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("watercooler",
        new EquipmentVisualStyle("circle", "#87CEEB", "#4682B4", "black", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("heater",
        new EquipmentVisualStyle("circle", "#FF6347", "#8B0000", "white", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("steamheater",
        new EquipmentVisualStyle("circle", "#FF6347", "#8B0000", "white", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("condenser",
        new EquipmentVisualStyle("circle", "#87CEEB", "#4682B4", "black", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("reboiler",
        new EquipmentVisualStyle("circle", "#FF6347", "#8B0000", "white", "0.5", "0.5", "filled"));
    STYLE_CACHE.put("multistreamheatexchanger", new EquipmentVisualStyle("rect", "#FFD700",
        "#FF8C00", "black", "2.0", "1.0", "filled,rounded"));

    // ========== VALVES (Bowtie/Butterfly shape, Pink tones) ==========
    // Using polygon with 4 sides and orientation for butterfly valve appearance
    STYLE_CACHE.put("valve", new EquipmentVisualStyle("polygon", "#FFB6C1", "#FF69B4", "black",
        "0.3", "0.25", "filled"));
    STYLE_CACHE.put("throttlingvalve", new EquipmentVisualStyle("polygon", "#FFB6C1", "#FF69B4",
        "black", "0.3", "0.25", "filled"));
    STYLE_CACHE.put("controlvalve", new EquipmentVisualStyle("polygon", "#FFB6C1", "#FF69B4",
        "black", "0.35", "0.3", "filled"));
    STYLE_CACHE.put("safetyvalve", new EquipmentVisualStyle("polygon", "#FF6347", "#8B0000",
        "white", "0.3", "0.25", "filled"));
    STYLE_CACHE.put("esdvalve", new EquipmentVisualStyle("polygon", "#FF0000", "#8B0000", "white",
        "0.35", "0.3", "filled"));
    STYLE_CACHE.put("blowdownvalve", new EquipmentVisualStyle("polygon", "#FF4500", "#8B0000",
        "white", "0.3", "0.25", "filled"));
    STYLE_CACHE.put("checkvalve", new EquipmentVisualStyle("polygon", "#DDA0DD", "#8B008B", "black",
        "0.25", "0.2", "filled"));
    STYLE_CACHE.put("hippsvalve", new EquipmentVisualStyle("polygon", "#FF0000", "#8B0000", "white",
        "0.35", "0.3", "filled"));
    STYLE_CACHE.put("psdvalve", new EquipmentVisualStyle("polygon", "#FF4500", "#8B0000", "white",
        "0.3", "0.25", "filled"));
    STYLE_CACHE.put("levelcontrolvalve", new EquipmentVisualStyle("polygon", "#FFB6C1", "#FF69B4",
        "black", "0.3", "0.25", "filled"));
    STYLE_CACHE.put("pressurecontrolvalve", new EquipmentVisualStyle("polygon", "#FFB6C1",
        "#FF69B4", "black", "0.3", "0.25", "filled"));
    STYLE_CACHE.put("rupturedisc", new EquipmentVisualStyle("polygon", "#FF6347", "#8B0000",
        "white", "0.25", "0.2", "filled"));

    // ========== STREAMS (Ellipse, White) ==========
    STYLE_CACHE.put("stream",
        new EquipmentVisualStyle("ellipse", "#FFFFFF", "#000000", "black", "1.2", "0.5", "filled"));
    STYLE_CACHE.put("neqstream",
        new EquipmentVisualStyle("ellipse", "#FFFFFF", "#000000", "black", "1.2", "0.5", "filled"));
    STYLE_CACHE.put("virtualstream", new EquipmentVisualStyle("ellipse", "#F5F5F5", "#808080",
        "gray", "1.0", "0.4", "filled,dashed"));
    STYLE_CACHE.put("energystream",
        new EquipmentVisualStyle("ellipse", "#FFD700", "#FF8C00", "black", "1.0", "0.4", "filled"));

    // ========== MIXERS & SPLITTERS (Triangle shapes, Purple) ==========
    STYLE_CACHE.put("mixer", new EquipmentVisualStyle("invtriangle", "#DDA0DD", "#8B008B", "black",
        "0.8", "0.6", "filled"));
    STYLE_CACHE.put("staticmixer", new EquipmentVisualStyle("invtriangle", "#DDA0DD", "#8B008B",
        "black", "0.8", "0.6", "filled"));
    STYLE_CACHE.put("phasemixer", new EquipmentVisualStyle("invtriangle", "#DDA0DD", "#8B008B",
        "black", "0.8", "0.6", "filled"));
    STYLE_CACHE.put("splitter", new EquipmentVisualStyle("triangle", "#DDA0DD", "#8B008B", "black",
        "0.8", "0.6", "filled"));
    STYLE_CACHE.put("componentsplitter", new EquipmentVisualStyle("triangle", "#DDA0DD", "#8B008B",
        "black", "0.8", "0.6", "filled"));
    STYLE_CACHE.put("manifold", new EquipmentVisualStyle("triangle", "#DDA0DD", "#8B008B", "black",
        "0.8", "0.6", "filled"));

    // ========== REACTORS (Hexagon, Orange) ==========
    STYLE_CACHE.put("reactor",
        new EquipmentVisualStyle("hexagon", "#FF8C00", "#8B4513", "black", "1.5", "1.2", "filled"));
    STYLE_CACHE.put("gibbsreactor",
        new EquipmentVisualStyle("hexagon", "#FF8C00", "#8B4513", "black", "1.5", "1.2", "filled"));
    STYLE_CACHE.put("furnaceburner",
        new EquipmentVisualStyle("hexagon", "#FF4500", "#8B0000", "white", "1.5", "1.2", "filled"));

    // ========== PIPELINES (Rectangle, Gray) ==========
    STYLE_CACHE.put("pipe",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.0", "0.3", "filled"));
    STYLE_CACHE.put("pipeline",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.5", "0.3", "filled"));
    STYLE_CACHE.put("simplepipeline",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.0", "0.3", "filled"));
    STYLE_CACHE.put("onephasepipeline",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.0", "0.3", "filled"));
    STYLE_CACHE.put("twophasepipeline",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.5", "0.3", "filled"));
    STYLE_CACHE.put("twofluidpipe",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.5", "0.3", "filled"));
    STYLE_CACHE.put("simpleflowline",
        new EquipmentVisualStyle("rect", "#C0C0C0", "#808080", "black", "2.0", "0.3", "filled"));

    // ========== TANKS & STORAGE (Cylinder, Gray) ==========
    STYLE_CACHE.put("tank", new EquipmentVisualStyle("cylinder", "#D3D3D3", "#696969", "black",
        "1.5", "1.8", "filled"));
    STYLE_CACHE.put("storagetank", new EquipmentVisualStyle("cylinder", "#D3D3D3", "#696969",
        "black", "1.8", "2.0", "filled"));
    STYLE_CACHE.put("depressurizer", new EquipmentVisualStyle("cylinder", "#D3D3D3", "#696969",
        "black", "1.5", "1.8", "filled"));

    // ========== WELLS & RESERVOIR (House shape, Brown) ==========
    STYLE_CACHE.put("well",
        new EquipmentVisualStyle("house", "#8B4513", "#654321", "white", "1.0", "1.2", "filled"));
    STYLE_CACHE.put("subseawell",
        new EquipmentVisualStyle("house", "#4682B4", "#191970", "white", "1.0", "1.2", "filled"));
    STYLE_CACHE.put("reservoir",
        new EquipmentVisualStyle("house", "#8B4513", "#654321", "white", "1.2", "1.4", "filled"));
    STYLE_CACHE.put("wellflow",
        new EquipmentVisualStyle("house", "#8B4513", "#654321", "white", "1.0", "1.2", "filled"));

    // ========== CONTROL & UTILITY EQUIPMENT (Dashed, Gray) ==========
    STYLE_CACHE.put("recycle", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black",
        "1.0", "0.5", "filled,dashed"));
    STYLE_CACHE.put("adjuster", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black",
        "1.0", "0.5", "filled,dashed"));
    STYLE_CACHE.put("calculator", new EquipmentVisualStyle("rect", "#FFFACD", "#DAA520", "black",
        "1.0", "0.5", "filled,dashed"));
    STYLE_CACHE.put("controller", new EquipmentVisualStyle("rect", "#FFFACD", "#DAA520", "black",
        "1.0", "0.5", "filled,dashed"));
    STYLE_CACHE.put("setpoint", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black",
        "0.8", "0.4", "filled,dashed"));
    STYLE_CACHE.put("setter", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black", "0.8",
        "0.4", "filled,dashed"));
    STYLE_CACHE.put("flowsetter", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black",
        "0.8", "0.4", "filled,dashed"));
    STYLE_CACHE.put("streamsaturator", new EquipmentVisualStyle("rect", "#E6E6FA", "#9370DB",
        "black", "1.0", "0.5", "filled,dashed"));
    STYLE_CACHE.put("gormatch", new EquipmentVisualStyle("rect", "#F5F5F5", "#808080", "black",
        "0.8", "0.4", "filled,dashed"));

    // ========== EJECTOR (Special shape) ==========
    STYLE_CACHE.put("ejector", new EquipmentVisualStyle("trapezium", "#ADD8E6", "#4682B4", "black",
        "1.2", "0.6", "filled"));

    // ========== MEMBRANE (Special) ==========
    STYLE_CACHE.put("membrane",
        new EquipmentVisualStyle("rect", "#E6E6FA", "#9370DB", "black", "1.5", "0.8", "filled"));

    // ========== FILTER (Special) ==========
    STYLE_CACHE.put("filter",
        new EquipmentVisualStyle("rect", "#F0E68C", "#BDB76B", "black", "1.0", "0.8", "filled"));
    STYLE_CACHE.put("charcoalfilter",
        new EquipmentVisualStyle("rect", "#696969", "#2F4F4F", "white", "1.0", "0.8", "filled"));

    // ========== FLARE SYSTEM (Special, Red/Orange) ==========
    STYLE_CACHE.put("flare",
        new EquipmentVisualStyle("note", "#FF4500", "#8B0000", "white", "1.0", "1.5", "filled"));
    STYLE_CACHE.put("flarestack",
        new EquipmentVisualStyle("note", "#FF4500", "#8B0000", "white", "0.8", "1.8", "filled"));

    // ========== POWER GENERATION (Special shapes) ==========
    STYLE_CACHE.put("gasturbine",
        new EquipmentVisualStyle("octagon", "#FFD700", "#FF8C00", "black", "1.5", "1.0", "filled"));
    STYLE_CACHE.put("fuelcell",
        new EquipmentVisualStyle("octagon", "#90EE90", "#228B22", "black", "1.5", "1.0", "filled"));
    STYLE_CACHE.put("electrolyzer",
        new EquipmentVisualStyle("octagon", "#ADD8E6", "#4682B4", "black", "1.5", "1.0", "filled"));
    STYLE_CACHE.put("solarpanel",
        new EquipmentVisualStyle("rect", "#FFD700", "#FF8C00", "black", "1.5", "0.8", "filled"));
    STYLE_CACHE.put("windturbine", new EquipmentVisualStyle("triangle", "#87CEEB", "#4682B4",
        "black", "1.2", "1.5", "filled"));

    // ========== FLOW MEASUREMENT (Small, Gray) ==========
    STYLE_CACHE.put("orificeplate",
        new EquipmentVisualStyle("rect", "#D3D3D3", "#808080", "black", "0.6", "0.4", "filled"));
    STYLE_CACHE.put("dpflowmeter",
        new EquipmentVisualStyle("rect", "#D3D3D3", "#808080", "black", "0.6", "0.4", "filled"));

    // ========== DEFAULT ==========
    STYLE_CACHE.put("default", new EquipmentVisualStyle("rect", "#FFFFFF", "#000000", "black",
        DEFAULT_WIDTH, DEFAULT_HEIGHT, "filled,rounded"));

    // Initialize EquipmentEnum mappings for DEXPI/unified access
    initializeEnumMappings();
  }

  /**
   * Initializes EquipmentEnum to style mappings for unified equipment type access.
   *
   * <p>
   * This enables consistent styling whether equipment comes from direct NeqSim creation or DEXPI
   * import.
   * </p>
   */
  private static void initializeEnumMappings() {
    ENUM_CACHE.put(EquipmentEnum.Separator, STYLE_CACHE.get("separator"));
    ENUM_CACHE.put(EquipmentEnum.ThreePhaseSeparator, STYLE_CACHE.get("threephaseseparator"));
    ENUM_CACHE.put(EquipmentEnum.Compressor, STYLE_CACHE.get("compressor"));
    ENUM_CACHE.put(EquipmentEnum.Expander, STYLE_CACHE.get("expander"));
    ENUM_CACHE.put(EquipmentEnum.Pump, STYLE_CACHE.get("pump"));
    ENUM_CACHE.put(EquipmentEnum.HeatExchanger, STYLE_CACHE.get("heatexchanger"));
    ENUM_CACHE.put(EquipmentEnum.Cooler, STYLE_CACHE.get("cooler"));
    ENUM_CACHE.put(EquipmentEnum.Heater, STYLE_CACHE.get("heater"));
    ENUM_CACHE.put(EquipmentEnum.ThrottlingValve, STYLE_CACHE.get("throttlingvalve"));
    ENUM_CACHE.put(EquipmentEnum.Mixer, STYLE_CACHE.get("mixer"));
    ENUM_CACHE.put(EquipmentEnum.Splitter, STYLE_CACHE.get("splitter"));
    ENUM_CACHE.put(EquipmentEnum.Stream, STYLE_CACHE.get("stream"));
    ENUM_CACHE.put(EquipmentEnum.VirtualStream, STYLE_CACHE.get("virtualstream"));
    ENUM_CACHE.put(EquipmentEnum.Reactor, STYLE_CACHE.get("reactor"));
    ENUM_CACHE.put(EquipmentEnum.Column, STYLE_CACHE.get("distillationcolumn"));
    ENUM_CACHE.put(EquipmentEnum.Recycle, STYLE_CACHE.get("recycle"));
    ENUM_CACHE.put(EquipmentEnum.Adjuster, STYLE_CACHE.get("adjuster"));
    ENUM_CACHE.put(EquipmentEnum.SetPoint, STYLE_CACHE.get("setpoint"));
    ENUM_CACHE.put(EquipmentEnum.Calculator, STYLE_CACHE.get("calculator"));
    ENUM_CACHE.put(EquipmentEnum.Ejector, STYLE_CACHE.get("ejector"));
    ENUM_CACHE.put(EquipmentEnum.Tank, STYLE_CACHE.get("tank"));
    ENUM_CACHE.put(EquipmentEnum.Flare, STYLE_CACHE.get("flare"));
    ENUM_CACHE.put(EquipmentEnum.ComponentSplitter, STYLE_CACHE.get("componentsplitter"));
    ENUM_CACHE.put(EquipmentEnum.SimpleTEGAbsorber, STYLE_CACHE.get("simpletegabsorber"));
    ENUM_CACHE.put(EquipmentEnum.Manifold, STYLE_CACHE.get("manifold"));
    ENUM_CACHE.put(EquipmentEnum.FlareStack, STYLE_CACHE.get("flarestack"));
    ENUM_CACHE.put(EquipmentEnum.FuelCell, STYLE_CACHE.get("fuelcell"));
    ENUM_CACHE.put(EquipmentEnum.CO2Electrolyzer, STYLE_CACHE.get("electrolyzer"));
    ENUM_CACHE.put(EquipmentEnum.Electrolyzer, STYLE_CACHE.get("electrolyzer"));
    ENUM_CACHE.put(EquipmentEnum.WindTurbine, STYLE_CACHE.get("windturbine"));
    ENUM_CACHE.put(EquipmentEnum.SolarPanel, STYLE_CACHE.get("solarpanel"));
  }

  /**
   * Gets the visual style for an equipment type.
   *
   * @param equipmentType the equipment type (class name or simplified name)
   * @return the visual style, or default if not found
   */
  public static EquipmentVisualStyle getStyle(String equipmentType) {
    if (equipmentType == null) {
      return STYLE_CACHE.get("default");
    }

    String key = equipmentType.toLowerCase().replace(" ", "");

    // Direct match
    EquipmentVisualStyle style = STYLE_CACHE.get(key);
    if (style != null) {
      return style;
    }

    // Partial match
    for (Map.Entry<String, EquipmentVisualStyle> entry : STYLE_CACHE.entrySet()) {
      if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
        return entry.getValue();
      }
    }

    return STYLE_CACHE.get("default");
  }

  /**
   * Gets the visual style for an EquipmentEnum type.
   *
   * <p>
   * This method provides unified access to styles whether equipment comes from direct NeqSim
   * creation or DEXPI import. DEXPI-imported equipment uses
   * {@code DexpiProcessUnit.getMappedEquipment()} to get the EquipmentEnum.
   * </p>
   *
   * @param equipmentEnum the canonical equipment type
   * @return the visual style, or default if not found
   */
  public static EquipmentVisualStyle getStyle(EquipmentEnum equipmentEnum) {
    if (equipmentEnum == null) {
      return STYLE_CACHE.get("default");
    }

    EquipmentVisualStyle style = ENUM_CACHE.get(equipmentEnum);
    if (style != null) {
      return style;
    }

    // Fall back to string-based lookup
    return getStyle(equipmentEnum.name());
  }

  /**
   * Gets the visual style for a process equipment instance.
   *
   * <p>
   * This method automatically handles DEXPI-imported equipment by using the mapped EquipmentEnum,
   * ensuring consistent styling regardless of equipment origin.
   * </p>
   *
   * @param equipment the process equipment
   * @return the visual style, or default if not found
   */
  public static EquipmentVisualStyle getStyleForEquipment(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return STYLE_CACHE.get("default");
    }

    // Check if this is DEXPI-imported equipment
    if (equipment instanceof DexpiProcessUnit) {
      DexpiProcessUnit dexpiUnit = (DexpiProcessUnit) equipment;
      EquipmentEnum mappedType = dexpiUnit.getMappedEquipment();
      if (mappedType != null) {
        return getStyle(mappedType);
      }
      // Fall back to DEXPI class name
      return getStyle(dexpiUnit.getDexpiClass());
    }

    // Use class name for regular equipment
    return getStyle(equipment.getClass().getSimpleName());
  }

  /**
   * Generates Graphviz node attributes string.
   *
   * @param label the node label
   * @return Graphviz attribute string
   */
  public String toGraphvizAttributes(String label) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append("label=\"").append(escapeLabel(label)).append("\"");
    sb.append(", shape=").append(shape);
    sb.append(", style=\"").append(style).append("\"");
    sb.append(", fillcolor=\"").append(fillColor).append("\"");
    sb.append(", color=\"").append(borderColor).append("\"");
    sb.append(", fontcolor=\"").append(fontColor).append("\"");
    sb.append(", fontname=\"").append(FONT_NAME).append("\"");
    sb.append(", fontsize=").append(FONT_SIZE);
    sb.append(", width=").append(width);
    sb.append(", height=").append(height);

    // Add polygon-specific attributes for bow-tie/hourglass valve shape
    // Creates classic valve symbol: two triangles meeting at center (⧓)
    if ("polygon".equals(shape)) {
      sb.append(", sides=4");
      sb.append(", skew=0.6"); // Skew creates the hourglass/bow-tie pinch effect
      sb.append(", orientation=0"); // Keep horizontal alignment
    }

    sb.append("]");
    return sb.toString();
  }

  /**
   * Escapes special characters in label for Graphviz.
   *
   * @param label the label
   * @return escaped label
   */
  private String escapeLabel(String label) {
    if (label == null) {
      return "";
    }
    return label.replace("\"", "\\\"").replace("\n", "\\n");
  }

  // Getters

  /**
   * Gets the shape.
   *
   * @return the shape
   */
  public String getShape() {
    return shape;
  }

  /**
   * Gets the fill color.
   *
   * @return the fill color
   */
  public String getFillColor() {
    return fillColor;
  }

  /**
   * Gets the border color.
   *
   * @return the border color
   */
  public String getBorderColor() {
    return borderColor;
  }

  /**
   * Gets the font color.
   *
   * @return the font color
   */
  public String getFontColor() {
    return fontColor;
  }

  /**
   * Gets the width.
   *
   * @return the width
   */
  public String getWidth() {
    return width;
  }

  /**
   * Gets the height.
   *
   * @return the height
   */
  public String getHeight() {
    return height;
  }

  /**
   * Gets the style.
   *
   * @return the style
   */
  public String getStyle() {
    return style;
  }
}
