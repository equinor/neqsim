package neqsim.process.processmodel.diagram;

/**
 * Defines the visual style for process flow diagrams.
 *
 * <p>
 * Different process simulators have distinct visual conventions for PFDs. This enum allows
 * selecting the desired style when generating diagrams.
 * </p>
 *
 * <p>
 * Visual conventions are based on:
 * </p>
 * <ul>
 * <li>ISO 10628 - Diagrams for the chemical and petrochemical industry</li>
 * <li>ANSI Y32.11 - Graphical Symbols for Process Flow Diagrams</li>
 * <li>Industry-standard simulator appearances (HYSYS, Aspen Plus, PRO/II)</li>
 * </ul>
 *
 * <p>
 * Simulator-specific styling:
 * </p>
 * <ul>
 * <li><b>HYSYS:</b> Cyan/teal icons, dark blue streams, white background, icon-based symbols</li>
 * <li><b>Aspen Plus:</b> Function-specific colors (red=heat, blue=cool, green=columns), curved
 * streams</li>
 * <li><b>PRO/II:</b> Black &amp; white technical style, numbered streams, simple geometric
 * shapes</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public enum DiagramStyle {
  /**
   * NeqSim default style with colored phase zone clusters.
   *
   * <p>
   * Features:
   * </p>
   * <ul>
   * <li>Colored background clusters (blue for gas, tan for oil, etc.)</li>
   * <li>Rich equipment symbols with HTML labels</li>
   * <li>Phase-colored stream lines</li>
   * <li>Gravity-based vertical layout</li>
   * </ul>
   */
  NEQSIM("NeqSim", "white", "#000000", true, true),

  /**
   * HYSYS-style clean process flow diagram.
   *
   * <p>
   * AspenTech HYSYS uses a distinctive cyan/teal color scheme with clean professional appearance.
   * Equipment is rendered as compact icons with names displayed below.
   * </p>
   *
   * <p>
   * Key visual characteristics:
   * </p>
   * <ul>
   * <li>White background with no colored clusters</li>
   * <li>Equipment icons in cyan (#00B8B8) with teal outline (#008080)</li>
   * <li>Material streams in dark blue (#0066CC) with 2.5pt line weight</li>
   * <li>Equipment names displayed below icons in 9pt Arial</li>
   * <li>Stream numbers at connection points</li>
   * <li>Orthogonal (right-angle) stream routing</li>
   * </ul>
   */
  HYSYS("HYSYS", "white", "#0066CC", false, true),

  /**
   * PRO/II style diagram (AVEVA/Schneider Electric).
   *
   * <p>
   * PRO/II uses a traditional engineering drawing style with minimal color, emphasizing clarity and
   * printability.
   * </p>
   *
   * <p>
   * Key visual characteristics:
   * </p>
   * <ul>
   * <li>Light gray background (#F5F5F5)</li>
   * <li>White equipment with thin black outlines (1pt)</li>
   * <li>Black stream lines (1.5pt) with numbered labels</li>
   * <li>Simple geometric shapes (circles, rectangles, triangles)</li>
   * <li>Stream numbers prominently displayed</li>
   * <li>Orthogonal routing with minimal curves</li>
   * </ul>
   */
  PROII("PRO/II", "#F5F5F5", "#000000", false, false),

  /**
   * Aspen Plus style diagram (AspenTech).
   *
   * <p>
   * Aspen Plus uses function-specific colors for different equipment categories, with curved stream
   * routing for a modern appearance.
   * </p>
   *
   * <p>
   * Key visual characteristics:
   * </p>
   * <ul>
   * <li>White background with subtle grid (optional)</li>
   * <li>Red/orange (#E74C3C) for heaters, furnaces, fired equipment</li>
   * <li>Blue (#3498DB) for coolers, condensers, chillers</li>
   * <li>Green (#27AE60) for columns, reactors, absorbers</li>
   * <li>Gray (#95A5A6) for vessels, drums, separators</li>
   * <li>Purple (#9B59B6) for compressors, expanders</li>
   * <li>Royal blue streams (#2980B9) with curved spline routing</li>
   * </ul>
   */
  ASPEN_PLUS("Aspen Plus", "white", "#2980B9", false, true);

  private final String displayName;
  private final String backgroundColor;
  private final String streamColor;
  private final boolean showClusters;
  private final boolean useHtmlLabels;

  /**
   * Creates a diagram style.
   *
   * @param displayName the display name
   * @param backgroundColor the background color (hex or name)
   * @param streamColor the default stream color
   * @param showClusters whether to show phase zone clusters
   * @param useHtmlLabels whether to use HTML table labels for equipment
   */
  DiagramStyle(String displayName, String backgroundColor, String streamColor, boolean showClusters,
      boolean useHtmlLabels) {
    this.displayName = displayName;
    this.backgroundColor = backgroundColor;
    this.streamColor = streamColor;
    this.showClusters = showClusters;
    this.useHtmlLabels = useHtmlLabels;
  }

  /**
   * Gets the display name.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the background color.
   *
   * @return the background color (hex or name)
   */
  public String getBackgroundColor() {
    return backgroundColor;
  }

  /**
   * Gets the default stream color.
   *
   * @return the stream color (hex)
   */
  public String getStreamColor() {
    return streamColor;
  }

  /**
   * Whether to show phase zone clusters.
   *
   * @return true if clusters should be shown
   */
  public boolean showClusters() {
    return showClusters;
  }

  /**
   * Whether to use HTML table labels for equipment.
   *
   * @return true if HTML labels should be used
   */
  public boolean useHtmlLabels() {
    return useHtmlLabels;
  }

  /**
   * Gets the equipment fill color for this style.
   *
   * <p>
   * Colors are matched to actual simulator UI appearances:
   * </p>
   * <ul>
   * <li>HYSYS: Consistent cyan/teal theme (#00B8B8 base)</li>
   * <li>Aspen Plus: Function-specific colors matching Aspen palette</li>
   * <li>PRO/II: White fill for all equipment (black outline only)</li>
   * </ul>
   *
   * @param equipmentType the type of equipment
   * @return the fill color (hex)
   */
  public String getEquipmentFillColor(String equipmentType) {
    switch (this) {
      case HYSYS:
        // HYSYS uses a unified cyan/teal color palette
        // All equipment uses similar shades for visual consistency
        if (equipmentType.contains("Stream")) {
          return "#FFFFFF"; // White for stream nodes
        }
        if (equipmentType.contains("Separator") || equipmentType.contains("Flash")
            || equipmentType.contains("Vessel") || equipmentType.contains("Drum")) {
          return "#00B8B8"; // Cyan for vessels
        }
        if (equipmentType.contains("Heater") || equipmentType.contains("Reboiler")
            || equipmentType.contains("Furnace")) {
          return "#00A0A0"; // Slightly darker cyan for heaters
        }
        if (equipmentType.contains("Cooler") || equipmentType.contains("Condenser")) {
          return "#00C8C8"; // Lighter cyan for coolers
        }
        if (equipmentType.contains("Compressor") || equipmentType.contains("Expander")) {
          return "#009090"; // Darker teal for compressors
        }
        if (equipmentType.contains("Pump")) {
          return "#00A8A8"; // Medium teal for pumps
        }
        if (equipmentType.contains("Valve")) {
          return "#80D0D0"; // Light cyan for valves
        }
        if (equipmentType.contains("Column") || equipmentType.contains("Distillation")
            || equipmentType.contains("Absorber") || equipmentType.contains("Stripper")) {
          return "#00B0B0"; // Standard cyan for columns
        }
        if (equipmentType.contains("Mixer") || equipmentType.contains("Splitter")) {
          return "#60C8C8"; // Pale cyan for mixers
        }
        if (equipmentType.contains("HeatExchanger") || equipmentType.contains("Exchanger")) {
          return "#00B8B8"; // Standard cyan for exchangers
        }
        if (equipmentType.contains("Recycle")) {
          return "#40B8B8"; // Recycle blocks
        }
        return "#00B8B8"; // Default cyan

      case PROII:
        // PRO/II uses pure white fill with black outlines
        // Technical drawing style - no color coding
        return "#FFFFFF";

      case ASPEN_PLUS:
        // Aspen Plus uses distinct colors for each equipment function
        // Colors match the actual Aspen Plus V12+ interface
        if (equipmentType.contains("Stream")) {
          return "#FFFFFF";
        }
        if (equipmentType.contains("Heater") || equipmentType.contains("Reboiler")
            || equipmentType.contains("Furnace") || equipmentType.contains("Fired")) {
          return "#E74C3C"; // Aspen red for heating
        }
        if (equipmentType.contains("Cooler") || equipmentType.contains("Condenser")
            || equipmentType.contains("Chiller")) {
          return "#3498DB"; // Aspen blue for cooling
        }
        if (equipmentType.contains("Column") || equipmentType.contains("Distillation")
            || equipmentType.contains("RadFrac") || equipmentType.contains("Absorber")
            || equipmentType.contains("Stripper")) {
          return "#27AE60"; // Aspen green for columns
        }
        if (equipmentType.contains("Reactor") || equipmentType.contains("RCSTR")
            || equipmentType.contains("RPlug")) {
          return "#27AE60"; // Green for reactors too
        }
        if (equipmentType.contains("Separator") || equipmentType.contains("Flash")
            || equipmentType.contains("Vessel") || equipmentType.contains("Drum")) {
          return "#95A5A6"; // Gray for vessels
        }
        if (equipmentType.contains("Compressor") || equipmentType.contains("Expander")
            || equipmentType.contains("MCompr")) {
          return "#9B59B6"; // Purple for rotating equipment
        }
        if (equipmentType.contains("Pump")) {
          return "#5DADE2"; // Light blue for pumps
        }
        if (equipmentType.contains("Valve")) {
          return "#F39C12"; // Orange/yellow for valves
        }
        if (equipmentType.contains("HeatExchanger") || equipmentType.contains("Exchanger")
            || equipmentType.contains("HeatX") || equipmentType.contains("MHeatX")) {
          return "#E67E22"; // Orange for heat exchangers
        }
        if (equipmentType.contains("Mixer") || equipmentType.contains("Splitter")
            || equipmentType.contains("FSplit")) {
          return "#BDC3C7"; // Light gray for mixers/splitters
        }
        if (equipmentType.contains("Recycle")) {
          return "#1ABC9C"; // Teal for recycle blocks
        }
        return "#95A5A6"; // Default gray

      case NEQSIM:
      default:
        return null; // Use default EquipmentVisualStyle colors
    }
  }

  /**
   * Gets the equipment outline color for this style.
   *
   * @return the outline color (hex)
   */
  public String getEquipmentOutlineColor() {
    switch (this) {
      case HYSYS:
        return "#008080"; // Teal outline
      case PROII:
        return "#000000"; // Black outline (technical drawing style)
      case ASPEN_PLUS:
        return "#2C3E50"; // Dark slate (Aspen style)
      case NEQSIM:
      default:
        return "#000000"; // Black
    }
  }

  /**
   * Gets the font color for equipment labels.
   *
   * @return the font color (hex)
   */
  public String getFontColor() {
    switch (this) {
      case HYSYS:
        return "#000000"; // Black text
      case PROII:
        return "#000000"; // Black text
      case ASPEN_PLUS:
        return "#2C3E50"; // Dark slate
      case NEQSIM:
      default:
        return "#000000"; // Black
    }
  }

  /**
   * Gets the arrow style for stream lines.
   *
   * @return the Graphviz arrowhead style
   */
  public String getArrowStyle() {
    switch (this) {
      case HYSYS:
        return "normal"; // Filled triangular arrow
      case PROII:
        return "vee"; // Simple V-shaped arrow (technical style)
      case ASPEN_PLUS:
        return "normal"; // Filled arrow
      case NEQSIM:
      default:
        return "normal";
    }
  }

  /**
   * Gets the edge (stream) line style.
   *
   * @return the Graphviz style attribute value
   */
  public String getEdgeStyle() {
    switch (this) {
      case HYSYS:
        return "bold"; // Thick visible streams
      case PROII:
        return "solid"; // Standard lines
      case ASPEN_PLUS:
        return "bold"; // Prominent streams
      case NEQSIM:
      default:
        return "solid";
    }
  }

  /**
   * Gets the spline type for edge routing.
   *
   * @return the Graphviz splines attribute value
   */
  public String getSplineType() {
    switch (this) {
      case HYSYS:
        return "ortho"; // Right-angle routing (HYSYS default)
      case PROII:
        return "ortho"; // Right-angle routing (engineering drawing)
      case ASPEN_PLUS:
        return "spline"; // Curved smooth routing (Aspen style)
      case NEQSIM:
      default:
        return "ortho";
    }
  }

  /**
   * Gets the pen width (line thickness) for equipment outlines.
   *
   * @return the pen width in points
   */
  public double getPenWidth() {
    switch (this) {
      case HYSYS:
        return 2.0; // Bold equipment outlines
      case PROII:
        return 1.0; // Thin technical lines
      case ASPEN_PLUS:
        return 1.5; // Medium weight
      case NEQSIM:
      default:
        return 1.0;
    }
  }

  /**
   * Gets the stream line width.
   *
   * @return the line width in points
   */
  public double getStreamWidth() {
    switch (this) {
      case HYSYS:
        return 2.5; // Bold streams
      case PROII:
        return 1.5; // Standard streams
      case ASPEN_PLUS:
        return 2.0; // Medium streams
      case NEQSIM:
      default:
        return 1.5;
    }
  }

  /**
   * Gets the font name for equipment labels.
   *
   * @return the font name
   */
  public String getFontName() {
    switch (this) {
      case HYSYS:
        return "Arial"; // HYSYS uses Arial
      case PROII:
        return "Courier"; // Monospace for technical look
      case ASPEN_PLUS:
        return "Arial"; // Aspen uses Arial
      case NEQSIM:
      default:
        return "Arial";
    }
  }

  /**
   * Gets the font size for equipment labels.
   *
   * @return the font size in points
   */
  public int getFontSize() {
    switch (this) {
      case HYSYS:
        return 9; // Compact labels
      case PROII:
        return 8; // Small technical labels
      case ASPEN_PLUS:
        return 10; // Standard size
      case NEQSIM:
      default:
        return 10;
    }
  }

  /**
   * Whether to show stream numbers instead of names.
   *
   * @return true if streams should be numbered
   */
  public boolean useStreamNumbers() {
    switch (this) {
      case HYSYS:
        return false; // Shows stream names
      case PROII:
        return true; // Uses numbered streams (S1, S2, etc.)
      case ASPEN_PLUS:
        return false; // Shows stream names
      case NEQSIM:
      default:
        return false;
    }
  }

  /**
   * Gets the recycle stream color.
   *
   * @return the recycle stream color (hex)
   */
  public String getRecycleColor() {
    switch (this) {
      case HYSYS:
        return "#CC6600"; // Orange for recycles
      case PROII:
        return "#000000"; // Black (same as other streams)
      case ASPEN_PLUS:
        return "#8E44AD"; // Purple for recycles
      case NEQSIM:
      default:
        return "#9932CC"; // Dark orchid
    }
  }

  /**
   * Gets the energy stream color.
   *
   * @return the energy stream color (hex)
   */
  public String getEnergyStreamColor() {
    switch (this) {
      case HYSYS:
        return "#FF0000"; // Red for energy streams
      case PROII:
        return "#FF0000"; // Red
      case ASPEN_PLUS:
        return "#E74C3C"; // Aspen red
      case NEQSIM:
      default:
        return "#FF4500"; // Orange red
    }
  }
}
