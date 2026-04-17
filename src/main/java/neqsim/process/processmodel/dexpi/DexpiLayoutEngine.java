package neqsim.process.processmodel.dexpi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Computes auto-layout positions for DEXPI XML export.
 *
 * <p>
 * Equipment is arranged left-to-right following process flow topology using a topological sort of
 * the process graph. Each column holds one or more equipment items at a fixed X spacing. Multi-
 * outlet equipment (separators) fan out vertically to separate rows.
 * </p>
 *
 * <p>
 * Positions and labels are written as DEXPI-compliant {@code <Position>}, {@code <Scale>}, and
 * {@code <Label>} XML elements on each Equipment and PipingComponent element.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class DexpiLayoutEngine {

  /** Horizontal spacing between equipment columns (mm in drawing space). */
  private static final double X_SPACING = 100.0;
  /** Vertical spacing for branch lines below the main process line. */
  private static final double Y_BRANCH_OFFSET = 60.0;
  /** Starting X coordinate. */
  private static final double X_START = 80.0;
  /** Base Y coordinate for the main process line. */
  private static final double Y_BASE = 150.0;
  /** Default scale factor for equipment shapes. */
  private static final double DEFAULT_SCALE = 1.0;
  /** Font name for labels. */
  private static final String FONT_NAME = "Calibri";
  /** Font height for tag name labels. */
  private static final double TAG_FONT_HEIGHT = 4.5;
  /** Vertical offset of tag name label above equipment center. */
  private static final double TAG_LABEL_OFFSET_Y = -18.0;
  /** Font height for equipment bar label text. */
  private static final double BAR_FONT_HEIGHT = 2.5;
  /** Width of equipment bar label box. */
  private static final double BAR_WIDTH = 50.0;
  /** Row height within equipment bar label table. */
  private static final double BAR_ROW_HEIGHT = 3.5;
  /** Vertical offset from equipment center to top of bar label. */
  private static final double BAR_OFFSET_Y = 22.0;
  /** Vertical offset from process line to instrument bubble center. */
  static final double INSTRUMENT_OFFSET_Y = 45.0;
  /** Horizontal spacing between instrument bubbles on the same equipment. */
  static final double INSTRUMENT_X_SPACING = 15.0;
  /** Radius of instrument bubble for connection point calculations. */
  static final double INSTRUMENT_BUBBLE_RADIUS = 3.75;
  /** Line weight for measuring/signal lines. */
  private static final double SIGNAL_LINE_WEIGHT = 0.2;
  /** Line weight for process piping lines. */
  private static final double PROCESS_LINE_WEIGHT = 0.5;
  /** Presentation colour R for process piping lines (olive). */
  private static final String LINE_COLOR_R = "0.501960784";
  /** Presentation colour G for process piping lines (olive). */
  private static final String LINE_COLOR_G = "0.501960784";
  /** Presentation colour B for process piping lines (olive). */
  private static final String LINE_COLOR_B = "0";

  // ---- Drawing border & title block constants ----
  /** Minimum drawing sheet width (A3 landscape in mm). */
  private static final double MIN_SHEET_WIDTH = 420.0;
  /** Minimum drawing sheet height (A3 landscape in mm). */
  private static final double MIN_SHEET_HEIGHT = 297.0;
  /** Margin between outer edge and inner border. */
  private static final double BORDER_MARGIN = 4.0;
  /** Title block height at the bottom-right. */
  private static final double TITLE_BLOCK_HEIGHT = 30.0;
  /** Title block width. */
  private static final double TITLE_BLOCK_WIDTH = 120.0;
  /** Row height inside title block. */
  private static final double TITLE_ROW_HEIGHT = 5.0;
  /** Font height for title block text. */
  private static final double TITLE_FONT_HEIGHT = 2.5;
  /** Font height for the main drawing title. */
  private static final double TITLE_MAIN_FONT_HEIGHT = 4.0;

  // ---- Flow arrow constants ----
  /** Length of flow direction arrow heads. */
  private static final double ARROW_LENGTH = 3.0;
  /** Half-width of flow direction arrow heads. */
  private static final double ARROW_HALF_WIDTH = 1.5;

  // ---- Stream table constants ----
  /** Height of the stream data table header row. */
  private static final double TABLE_ROW_HEIGHT = 4.5;
  /** Width of each stream column in the stream table. */
  private static final double TABLE_COL_WIDTH = 28.0;
  /** Width of the row-label column in the stream table. */
  private static final double TABLE_LABEL_WIDTH = 35.0;
  /** Font height for stream table text. */
  private static final double TABLE_FONT_HEIGHT = 2.0;
  /** Top of the stream table (Y coordinate). */
  private static final double TABLE_TOP_Y = 55.0;

  // ---- Battery limit boundary constants ----
  /** Padding around equipment for battery limit boundary (mm). */
  private static final double BATTERY_LIMIT_PADDING = 30.0;
  /** Line weight for battery limit boundary (bold dashed). */
  private static final double BATTERY_LIMIT_LINE_WEIGHT = 0.7;

  // ---- Symbol legend constants ----
  /** Width of the symbol legend box (mm). */
  private static final double LEGEND_WIDTH = 70.0;
  /** Row height in the legend box (mm). */
  private static final double LEGEND_ROW_HEIGHT = 5.0;
  /** Font height for legend text. */
  private static final double LEGEND_FONT_HEIGHT = 2.2;

  // ---- Revision table constants ----
  /** Row height in revision history table (mm). */
  private static final double REVISION_ROW_HEIGHT = 4.5;

  private DexpiLayoutEngine() {}

  /**
   * Computes layout positions for all equipment in a process system.
   *
   * <p>
   * Returns a map from equipment name to its {@link EquipmentPosition}. The positions are computed
   * using a topological ordering of the process graph, placing equipment left-to-right. Multi-
   * outlet equipment branches are fanned out vertically.
   * </p>
   *
   * @param processSystem the process system to lay out
   * @return map of equipment name to computed position
   */
  static Map<String, EquipmentPosition> computeLayout(ProcessSystem processSystem) {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    Map<String, EquipmentPosition> positions = new LinkedHashMap<>();

    // Build adjacency: equipment name -> list of downstream equipment names
    Map<String, List<String>> adjacency = new HashMap<>();
    Map<String, ProcessEquipmentInterface> unitByName = new LinkedHashMap<>();

    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      unitByName.put(unit.getName(), unit);
      adjacency.put(unit.getName(), new ArrayList<String>());
    }

    // Resolve edges using inlet stream identity matching
    Map<Integer, String> outletStreamToEquipment = new HashMap<>();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      StreamInterface gasOut = DexpiStreamUtils.getGasOutletStream(unit);
      if (gasOut != null) {
        outletStreamToEquipment.put(System.identityHashCode(gasOut), unit.getName());
      }
      StreamInterface liqOut = DexpiStreamUtils.getLiquidOutletStream(unit);
      if (liqOut != null) {
        outletStreamToEquipment.put(System.identityHashCode(liqOut), unit.getName());
      }
      StreamInterface waterOut = DexpiStreamUtils.getWaterOutletStream(unit);
      if (waterOut != null) {
        outletStreamToEquipment.put(System.identityHashCode(waterOut), unit.getName());
      }
    }

    // Also register pass-through streams (Stream wrapping separator outlets)
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream && !(unit instanceof DexpiStream)) {
        Stream stream = (Stream) unit;
        if (stream.getFluid() != null) {
          int fluidHash = System.identityHashCode(stream.getFluid());
          for (Map.Entry<Integer, String> entry : new HashMap<>(outletStreamToEquipment)
              .entrySet()) {
            ProcessEquipmentInterface src = unitByName.get(entry.getValue());
            if (src != null) {
              StreamInterface srcGas = DexpiStreamUtils.getGasOutletStream(src);
              if (srcGas != null && srcGas.getFluid() != null
                  && System.identityHashCode(srcGas.getFluid()) == fluidHash) {
                outletStreamToEquipment.put(System.identityHashCode(stream), entry.getValue());
              }
              StreamInterface srcLiq = DexpiStreamUtils.getLiquidOutletStream(src);
              if (srcLiq != null && srcLiq.getFluid() != null
                  && System.identityHashCode(srcLiq.getFluid()) == fluidHash) {
                outletStreamToEquipment.put(System.identityHashCode(stream), entry.getValue());
              }
            }
          }
        }
      }
    }

    // Build edges — handle both TwoPortEquipment (getInletStream) and
    // multi-port equipment like Separator/Mixer (getInletStreams)
    // Also track which equipment is fed by a liquid/water outlet for branch detection.
    Set<String> liquidBranchEquipment = new HashSet<>();
    // Build lookup: outlet stream identity -> whether it's a liquid/water stream
    Map<Integer, Boolean> outletIsLiquid = new HashMap<>();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      StreamInterface liqOut = DexpiStreamUtils.getLiquidOutletStream(unit);
      if (liqOut != null) {
        outletIsLiquid.put(System.identityHashCode(liqOut), Boolean.TRUE);
      }
      StreamInterface waterOut = DexpiStreamUtils.getWaterOutletStream(unit);
      if (waterOut != null) {
        outletIsLiquid.put(System.identityHashCode(waterOut), Boolean.TRUE);
      }
    }

    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      List<StreamInterface> inletStreams = resolveInletStreams(unit);
      for (StreamInterface inletStream : inletStreams) {
        String upstream = outletStreamToEquipment.get(System.identityHashCode(inletStream));
        if (upstream != null && adjacency.containsKey(upstream)) {
          adjacency.get(upstream).add(unit.getName());
        }
        // If this inlet is a liquid/water outlet from its source, mark as liquid branch
        if (outletIsLiquid.containsKey(System.identityHashCode(inletStream))) {
          liquidBranchEquipment.add(unit.getName());
        }
      }
    }

    // Topological sort (Kahn's algorithm)
    Map<String, Integer> inDegree = new HashMap<>();
    for (String name : adjacency.keySet()) {
      inDegree.put(name, 0);
    }
    for (List<String> neighbors : adjacency.values()) {
      for (String neighbor : neighbors) {
        Integer current = inDegree.get(neighbor);
        if (current != null) {
          inDegree.put(neighbor, current + 1);
        }
      }
    }

    List<String> queue = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    List<String> topoOrder = new ArrayList<>();
    while (!queue.isEmpty()) {
      String current = queue.remove(0);
      topoOrder.add(current);
      List<String> neighbors = adjacency.get(current);
      if (neighbors != null) {
        for (String neighbor : neighbors) {
          Integer deg = inDegree.get(neighbor);
          if (deg != null) {
            inDegree.put(neighbor, deg - 1);
            if (deg - 1 == 0) {
              queue.add(neighbor);
            }
          }
        }
      }
    }

    // Add any units not reached by topological sort (isolated or in cycles)
    Set<String> placed = new HashSet<>(topoOrder);
    for (String name : unitByName.keySet()) {
      if (!placed.contains(name)) {
        topoOrder.add(name);
      }
    }

    // Propagate liquid branch: if A is on the liquid branch and A -> B, then B
    // is also on the liquid branch (unless B is also fed by a gas path).
    boolean changed = true;
    while (changed) {
      changed = false;
      for (String name : liquidBranchEquipment.toArray(new String[0])) {
        List<String> downstream = adjacency.get(name);
        if (downstream != null) {
          for (String ds : downstream) {
            if (liquidBranchEquipment.add(ds)) {
              changed = true;
            }
          }
        }
      }
    }

    // Assign positions: each unit gets its own column. Liquid branch equipment is
    // shifted downward by Y_BRANCH_OFFSET for visual separation.
    int col = 0;
    for (String name : topoOrder) {
      double x = X_START + col * X_SPACING;
      double y = Y_BASE;
      if (liquidBranchEquipment.contains(name)) {
        y = Y_BASE - Y_BRANCH_OFFSET;
      }
      positions.put(name, new EquipmentPosition(x, y, DEFAULT_SCALE, DEFAULT_SCALE));
      col++;
    }

    return positions;
  }

  /**
   * Appends a DEXPI Position element to an equipment element.
   *
   * @param document the XML document
   * @param element the equipment element
   * @param pos the computed position
   */
  static void appendPosition(Document document, Element element, EquipmentPosition pos) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(pos.x));
    location.setAttribute("Y", String.valueOf(pos.y));
    location.setAttribute("Z", "0");
    position.appendChild(location);

    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);

    // Apply rotation via Reference vector
    Element reference = document.createElement("Reference");
    if (Math.abs(pos.rotation) > 0.01) {
      double rad = Math.toRadians(pos.rotation);
      reference.setAttribute("X", String.valueOf(Math.cos(rad)));
      reference.setAttribute("Y", String.valueOf(Math.sin(rad)));
      reference.setAttribute("Z", "0");
    } else {
      reference.setAttribute("X", "1");
      reference.setAttribute("Y", "0");
      reference.setAttribute("Z", "0");
    }
    position.appendChild(reference);

    element.appendChild(position);
  }

  /**
   * Appends a DEXPI Scale element to an equipment element.
   *
   * @param document the XML document
   * @param element the equipment element
   * @param pos the computed position containing scale factors
   */
  static void appendScale(Document document, Element element, EquipmentPosition pos) {
    Element scale = document.createElement("Scale");
    scale.setAttribute("X", String.valueOf(pos.scaleX));
    scale.setAttribute("Y", String.valueOf(pos.scaleY));
    element.appendChild(scale);
  }

  /**
   * Appends an EquipmentTagNameLabel to an equipment element.
   *
   * @param document the XML document
   * @param element the equipment element
   * @param tagName the equipment tag name to display
   * @param pos the equipment position
   * @param labelId the unique label ID
   * @param equipmentId the equipment element ID (for TextStringFormatSpecification)
   */
  static void appendTagNameLabel(Document document, Element element, String tagName,
      EquipmentPosition pos, String labelId, String equipmentId) {
    Element label = document.createElement("Label");
    label.setAttribute("ID", labelId);
    label.setAttribute("ComponentClass", "EquipmentTagNameLabel");
    label.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/EquipmentTagNameLabel");

    Element text = document.createElement("Text");
    text.setAttribute("String", tagName);
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", String.valueOf(TAG_FONT_HEIGHT));
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("R", "0");
    presentation.setAttribute("G", "0");
    presentation.setAttribute("B", "0");
    text.appendChild(presentation);

    Element textPosition = document.createElement("Position");
    Element textLocation = document.createElement("Location");
    textLocation.setAttribute("X", String.valueOf(pos.x));
    textLocation.setAttribute("Y", String.valueOf(pos.y + TAG_LABEL_OFFSET_Y));
    textLocation.setAttribute("Z", "0");
    textPosition.appendChild(textLocation);

    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPosition.appendChild(textAxis);

    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPosition.appendChild(textRef);

    text.appendChild(textPosition);

    Element formatSpec = document.createElement("TextStringFormatSpecification");
    Element objRef = document.createElement("ObjectAttributesReference");
    objRef.setAttribute("ItemID", equipmentId);
    objRef.setAttribute("DependantAttribute", "TagName");
    objRef.setAttribute("DependantAttributeContents", "Value");
    formatSpec.appendChild(objRef);
    text.appendChild(formatSpec);

    label.appendChild(text);
    element.appendChild(label);
  }

  /**
   * Appends a process piping line (CenterLine) connecting two nozzle positions.
   *
   * <p>
   * Uses orthogonal routing (horizontal-vertical-horizontal segments) when source and target are at
   * different Y coordinates. Horizontal connections use a single straight segment. Line style
   * matches the DEXPI standard process piping presentation (olive colour, 0.5 weight).
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element (PipingNetworkSegment)
   * @param fromX source X coordinate
   * @param fromY source Y coordinate
   * @param toX destination X coordinate
   * @param toY destination Y coordinate
   */
  static void appendConnectionLine(Document document, Element parent, double fromX, double fromY,
      double toX, double toY) {
    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", String.valueOf(PROCESS_LINE_WEIGHT));
    presentation.setAttribute("R", LINE_COLOR_R);
    presentation.setAttribute("G", LINE_COLOR_G);
    presentation.setAttribute("B", LINE_COLOR_B);

    boolean sameY = Math.abs(fromY - toY) < 0.5;

    if (sameY) {
      // Straight horizontal line — 2 points
      Element centerLine = document.createElement("CenterLine");
      centerLine.setAttribute("NumPoints", "2");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, fromX, fromY);
      appendCoordinate(document, centerLine, toX, toY);
      parent.appendChild(centerLine);
    } else {
      // Orthogonal routing: horizontal to midpoint, vertical, horizontal to target
      double midX = (fromX + toX) / 2.0;
      Element centerLine = document.createElement("CenterLine");
      centerLine.setAttribute("NumPoints", "4");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, fromX, fromY);
      appendCoordinate(document, centerLine, midX, fromY);
      appendCoordinate(document, centerLine, midX, toY);
      appendCoordinate(document, centerLine, toX, toY);
      parent.appendChild(centerLine);
    }
  }

  /**
   * Appends a single Coordinate element to a CenterLine.
   *
   * @param document the XML document
   * @param parent the CenterLine element
   * @param x the X coordinate
   * @param y the Y coordinate
   */
  private static void appendCoordinate(Document document, Element parent, double x, double y) {
    Element coord = document.createElement("Coordinate");
    coord.setAttribute("X", String.valueOf(x));
    coord.setAttribute("Y", String.valueOf(y));
    parent.appendChild(coord);
  }

  /**
   * Holds computed position data for a single equipment item.
   */
  static final class EquipmentPosition {
    /** X coordinate in drawing space. */
    final double x;
    /** Y coordinate in drawing space. */
    final double y;
    /** Scale factor in X direction. */
    final double scaleX;
    /** Scale factor in Y direction. */
    final double scaleY;
    /** Rotation angle in degrees (0 = no rotation). */
    final double rotation;

    /**
     * Creates a new equipment position with no rotation.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param scaleX x scale factor
     * @param scaleY y scale factor
     */
    EquipmentPosition(double x, double y, double scaleX, double scaleY) {
      this(x, y, scaleX, scaleY, 0.0);
    }

    /**
     * Creates a new equipment position with rotation.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param scaleX x scale factor
     * @param scaleY y scale factor
     * @param rotation rotation angle in degrees
     */
    EquipmentPosition(double x, double y, double scaleX, double scaleY, double rotation) {
      this.x = x;
      this.y = y;
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      this.rotation = rotation;
    }
  }

  /**
   * Appends an EquipmentBarLabel below an equipment element showing operating data.
   *
   * <p>
   * Renders a table with border lines and rows for equipment identity, operating pressure,
   * operating temperature, and flow rate. This follows the DEXPI standard pattern for equipment
   * data labels on P&amp;IDs.
   * </p>
   *
   * @param document the XML document
   * @param element the equipment element
   * @param tagName the equipment tag name
   * @param pos the equipment position
   * @param labelId the unique label ID
   * @param equipmentId the equipment element ID
   * @param pressure operating pressure in bara (NaN to omit)
   * @param temperature operating temperature in C (NaN to omit)
   * @param flowRate operating flow rate in MSm3/day (NaN to omit)
   */
  static void appendEquipmentBarLabel(Document document, Element element, String tagName,
      EquipmentPosition pos, String labelId, String equipmentId, double pressure,
      double temperature, double flowRate) {
    appendEquipmentBarLabel(document, element, tagName, pos, labelId, equipmentId, pressure,
        temperature, flowRate, null);
  }

  /**
   * Appends an equipment bar label with operating data and optional extra rows.
   *
   * @param document the XML document
   * @param element the equipment element
   * @param tagName the equipment tag name
   * @param pos the equipment position
   * @param labelId the unique label ID
   * @param equipmentId the equipment element ID
   * @param pressure operating pressure in bara (NaN to omit)
   * @param temperature operating temperature in C (NaN to omit)
   * @param flowRate operating flow rate in MSm3/day (NaN to omit)
   * @param extraRows additional label-value pairs to display (may be null)
   */
  static void appendEquipmentBarLabel(Document document, Element element, String tagName,
      EquipmentPosition pos, String labelId, String equipmentId, double pressure,
      double temperature, double flowRate, List<String[]> extraRows) {
    Element label = document.createElement("Label");
    label.setAttribute("ID", labelId);
    label.setAttribute("ComponentClass", "EquipmentBarLabel");
    label.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/EquipmentBarLabel");

    double boxLeft = pos.x - BAR_WIDTH / 2.0;
    double boxRight = pos.x + BAR_WIDTH / 2.0;
    double boxTop = pos.y + BAR_OFFSET_Y;
    double textLeft = boxLeft + 1.0;
    double valueX = boxRight - 8.0;

    // Build rows of data to display
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"Ident", tagName});
    if (!Double.isNaN(pressure)) {
      rows.add(new String[] {"Oper. Press.", formatValue(pressure) + " bara"});
    }
    if (!Double.isNaN(temperature)) {
      rows.add(new String[] {"Oper. Temp.", formatValue(temperature) + " \u00B0C"});
    }
    if (!Double.isNaN(flowRate)) {
      rows.add(new String[] {"Flow Rate", formatValue(flowRate) + " MSm3/d"});
    }
    if (extraRows != null) {
      rows.addAll(extraRows);
    }

    // Top border
    appendBarPolyLine(document, label, boxLeft, boxTop, boxRight, boxTop);

    for (int i = 0; i < rows.size(); i++) {
      double rowY = boxTop + (i + 1) * BAR_ROW_HEIGHT;
      // Row label text
      appendBarText(document, label, rows.get(i)[0], textLeft, boxTop + i * BAR_ROW_HEIGHT + 0.8,
          "LeftBottom");
      // Row value text
      appendBarText(document, label, rows.get(i)[1], valueX, boxTop + i * BAR_ROW_HEIGHT + 0.8,
          "CenterBottom");
      // Row separator line
      appendBarPolyLine(document, label, boxLeft, rowY, boxRight, rowY);
    }

    // Left and right borders
    double boxBottom = boxTop + rows.size() * BAR_ROW_HEIGHT;
    appendBarPolyLine(document, label, boxLeft, boxTop, boxLeft, boxBottom);
    appendBarPolyLine(document, label, boxRight, boxTop, boxRight, boxBottom);

    element.appendChild(label);
  }

  /**
   * Appends a Position and Scale to a Nozzle element for graphical rendering.
   *
   * @param document the XML document
   * @param nozzleElement the Nozzle element
   * @param x the x coordinate
   * @param y the y coordinate
   */
  static void appendNozzlePosition(Document document, Element nozzleElement, double x, double y) {
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);

    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);

    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);

    nozzleElement.appendChild(position);

    Element scale = document.createElement("Scale");
    scale.setAttribute("X", "0.8");
    scale.setAttribute("Y", "0.4");
    nozzleElement.appendChild(scale);
  }

  /**
   * Appends a PolyLine element used as a border in an equipment bar label.
   *
   * @param document the XML document
   * @param parent the label element
   * @param x1 start X
   * @param y1 start Y
   * @param x2 end X
   * @param y2 end Y
   */
  private static void appendBarPolyLine(Document document, Element parent, double x1, double y1,
      double x2, double y2) {
    Element poly = document.createElement("PolyLine");
    poly.setAttribute("NumPoints", "2");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", "0.3");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    poly.appendChild(pres);
    appendCoordinate(document, poly, x1, y1);
    appendCoordinate(document, poly, x2, y2);
    parent.appendChild(poly);
  }

  /**
   * Appends a Text element used in an equipment bar label row.
   *
   * @param document the XML document
   * @param parent the label element
   * @param text the text string
   * @param x the text X position
   * @param y the text Y position
   * @param justification the text justification (e.g. "LeftBottom", "CenterBottom")
   */
  private static void appendBarText(Document document, Element parent, String text, double x,
      double y, String justification) {
    Element textElement = document.createElement("Text");
    textElement.setAttribute("String", text);
    textElement.setAttribute("Font", FONT_NAME);
    textElement.setAttribute("Height", String.valueOf(BAR_FONT_HEIGHT));
    textElement.setAttribute("Width", "0");
    textElement.setAttribute("Justification", justification);

    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElement.appendChild(pres);

    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
    position.appendChild(reference);
    textElement.appendChild(position);

    parent.appendChild(textElement);
  }

  /**
   * Resolves all inlet streams for a process equipment unit. Handles both TwoPortEquipment (single
   * inlet via {@code getInletStream()}) and multi-port equipment like Separator and Mixer (via
   * {@code getInletStreams()}).
   *
   * @param unit the process equipment
   * @return list of inlet streams (never null, may be empty)
   */
  static List<StreamInterface> resolveInletStreams(ProcessEquipmentInterface unit) {
    List<StreamInterface> result = new ArrayList<>();
    if (unit instanceof TwoPortEquipment) {
      StreamInterface inlet = ((TwoPortEquipment) unit).getInletStream();
      if (inlet != null) {
        result.add(inlet);
      }
    } else {
      // Separator, Mixer, Splitter, etc. — use getInletStreams()
      List<StreamInterface> inlets = unit.getInletStreams();
      if (inlets != null) {
        result.addAll(inlets);
      }
    }
    return result;
  }

  /**
   * Formats a numeric value for display in equipment bar labels.
   *
   * @param value the value to format
   * @return formatted string
   */
  private static String formatValue(double value) {
    if (Math.abs(value) < 0.01) {
      return "0";
    }
    if (Math.abs(value - Math.round(value)) < 0.01) {
      return String.valueOf(Math.round(value));
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

  /**
   * Computes the layout position for an instrument bubble attached to a specific equipment.
   *
   * <p>
   * Instruments are placed above (higher Y) the equipment, offset by {@link #INSTRUMENT_OFFSET_Y}.
   * Multiple instruments on the same equipment are spaced horizontally.
   * </p>
   *
   * @param equipmentPos the parent equipment position
   * @param instrumentIndex 0-based index of this instrument on the equipment
   * @param totalInstruments total number of instruments on this equipment
   * @return the instrument position {x, y}
   */
  static double[] computeInstrumentPosition(EquipmentPosition equipmentPos, int instrumentIndex,
      int totalInstruments) {
    double baseX = equipmentPos.x;
    double baseY = equipmentPos.y + INSTRUMENT_OFFSET_Y;
    // Center the group of instruments horizontally over the equipment
    double totalWidth = (totalInstruments - 1) * INSTRUMENT_X_SPACING;
    double startX = baseX - totalWidth / 2.0;
    double x = startX + instrumentIndex * INSTRUMENT_X_SPACING;
    return new double[] {x, baseY};
  }

  /**
   * Appends a measuring line (CenterLine) connecting an equipment nozzle to an instrument bubble.
   *
   * <p>
   * The line runs vertically from a point on the process line up to the bottom of the instrument
   * bubble. Uses green signal-line colour per DEXPI convention.
   * </p>
   *
   * @param document the XML document
   * @param parent the InformationFlow element to append to
   * @param tapX X coordinate on the process line (tap point)
   * @param tapY Y coordinate on the process line
   * @param bubbleX X coordinate of the instrument bubble center
   * @param bubbleY Y coordinate of the instrument bubble center
   */
  static void appendMeasuringLine(Document document, Element parent, double tapX, double tapY,
      double bubbleX, double bubbleY) {
    Element centerLine = document.createElement("CenterLine");

    double bottomY = bubbleY - INSTRUMENT_BUBBLE_RADIUS;
    boolean sameX = Math.abs(tapX - bubbleX) < 0.5;

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", String.valueOf(SIGNAL_LINE_WEIGHT));
    presentation.setAttribute("R", "0");
    presentation.setAttribute("G", "0.501960784");
    presentation.setAttribute("B", "0");

    if (sameX) {
      centerLine.setAttribute("NumPoints", "2");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, tapX, tapY);
      appendCoordinate(document, centerLine, bubbleX, bottomY);
    } else {
      centerLine.setAttribute("NumPoints", "3");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, tapX, tapY);
      appendCoordinate(document, centerLine, bubbleX, tapY);
      appendCoordinate(document, centerLine, bubbleX, bottomY);
    }

    parent.appendChild(centerLine);
  }

  /**
   * Appends a signal conveying line (dashed, blue) between two instrument bubbles.
   *
   * @param document the XML document
   * @param parent the InformationFlow element
   * @param fromX source bubble center X
   * @param fromY source bubble center Y
   * @param toX target bubble center X
   * @param toY target bubble center Y
   */
  static void appendSignalLine(Document document, Element parent, double fromX, double fromY,
      double toX, double toY) {
    Element centerLine = document.createElement("CenterLine");

    double departY = fromY - INSTRUMENT_BUBBLE_RADIUS;
    double arriveY = toY + INSTRUMENT_BUBBLE_RADIUS;

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "2");
    presentation.setAttribute("LineWeight", String.valueOf(SIGNAL_LINE_WEIGHT));
    presentation.setAttribute("R", "0");
    presentation.setAttribute("G", "0");
    presentation.setAttribute("B", "1");

    if (Math.abs(fromX - toX) < 0.5) {
      centerLine.setAttribute("NumPoints", "2");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, fromX, departY);
      appendCoordinate(document, centerLine, toX, arriveY);
    } else {
      double midY = (departY + arriveY) / 2.0;
      centerLine.setAttribute("NumPoints", "4");
      centerLine.appendChild(presentation);
      appendCoordinate(document, centerLine, fromX, departY);
      appendCoordinate(document, centerLine, fromX, midY);
      appendCoordinate(document, centerLine, toX, midY);
      appendCoordinate(document, centerLine, toX, arriveY);
    }

    parent.appendChild(centerLine);
  }

  // ---- Drawing border & title block ----

  /**
   * Computes the required drawing sheet dimensions to enclose all equipment, instruments, and
   * labels with adequate margin. The result is at least A3 landscape (420 x 297 mm).
   *
   * @param positions the computed equipment layout positions
   * @return a two-element array {width, height} in mm
   */
  static double[] computeSheetSize(Map<String, EquipmentPosition> positions) {
    double maxX = 0;
    double maxY = 0;
    for (EquipmentPosition pos : positions.values()) {
      // Equipment bar extends BAR_WIDTH/2 to the right
      double rightEdge = pos.x + BAR_WIDTH / 2.0;
      // Controller bubbles may extend INSTRUMENT_X_SPACING beyond the rightmost transmitter
      double instrumentRight = pos.x + INSTRUMENT_X_SPACING + INSTRUMENT_BUBBLE_RADIUS;
      if (instrumentRight > rightEdge) {
        rightEdge = instrumentRight;
      }
      double topEdge = pos.y + INSTRUMENT_OFFSET_Y + INSTRUMENT_BUBBLE_RADIUS;
      if (rightEdge > maxX) {
        maxX = rightEdge;
      }
      if (topEdge > maxY) {
        maxY = topEdge;
      }
    }
    // Add right-side padding for nozzles, arrows, and border margin
    double width = Math.max(MIN_SHEET_WIDTH, maxX + BORDER_MARGIN + 40.0);
    double height = Math.max(MIN_SHEET_HEIGHT, maxY + BORDER_MARGIN + 40.0);
    return new double[] {width, height};
  }

  /**
   * Computes the number of drawing pages needed for a large flowsheet.
   *
   * <p>
   * When the total equipment extent exceeds an A1 landscape sheet (841 x 594 mm), the flowsheet is
   * split into multiple pages. Each page has its own Drawing element with border, title block, and
   * continuation markings.
   * </p>
   *
   * @param totalWidth the total layout width in mm
   * @param totalHeight the total layout height in mm
   * @param maxPageWidth maximum page width (default 841 mm for A1)
   * @param maxPageHeight maximum page height (default 594 mm for A1)
   * @return the number of pages as [columns, rows]
   */
  static int[] computePageGrid(double totalWidth, double totalHeight, double maxPageWidth,
      double maxPageHeight) {
    int cols = Math.max(1, (int) Math.ceil(totalWidth / maxPageWidth));
    int rows = Math.max(1, (int) Math.ceil(totalHeight / maxPageHeight));
    return new int[] {cols, rows};
  }

  /**
   * Appends a continuation arrow marking at the edge of a drawing page.
   *
   * <p>
   * Per ISO 10628 and NORSOK Z-003, when a P&amp;ID spans multiple sheets, continuation arrows
   * indicate where the process continues on adjacent sheets. The arrow is labeled with the
   * destination sheet number.
   * </p>
   *
   * @param document the XML document
   * @param parent the Drawing element
   * @param x the arrow X position
   * @param y the arrow Y position
   * @param direction "RIGHT", "LEFT", "UP", or "DOWN"
   * @param targetSheet the destination sheet identifier (e.g. "Sheet 2")
   */
  static void appendContinuationArrow(Document document, Element parent, double x, double y,
      String direction, String targetSheet) {
    double dx = 0;
    double dy = 0;
    double arrowLen = 8.0;
    if ("RIGHT".equals(direction)) {
      dx = arrowLen;
    } else if ("LEFT".equals(direction)) {
      dx = -arrowLen;
    } else if ("UP".equals(direction)) {
      dy = arrowLen;
    } else if ("DOWN".equals(direction)) {
      dy = -arrowLen;
    }

    // Arrow line
    Element polyLine = document.createElement("PolyLine");
    polyLine.setAttribute("NumPoints", "2");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", "0.35");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    polyLine.appendChild(pres);
    appendCoordinate(document, polyLine, x, y);
    appendCoordinate(document, polyLine, x + dx, y + dy);
    parent.appendChild(polyLine);

    // Arrowhead
    double headSize = 2.0;
    Element head = document.createElement("PolyLine");
    head.setAttribute("NumPoints", "3");
    Element headPres = document.createElement("Presentation");
    headPres.setAttribute("LineType", "0");
    headPres.setAttribute("LineWeight", "0.35");
    headPres.setAttribute("R", "0");
    headPres.setAttribute("G", "0");
    headPres.setAttribute("B", "0");
    head.appendChild(headPres);
    if ("RIGHT".equals(direction) || "LEFT".equals(direction)) {
      appendCoordinate(document, head, x + dx - Math.signum(dx) * headSize, y + headSize);
      appendCoordinate(document, head, x + dx, y);
      appendCoordinate(document, head, x + dx - Math.signum(dx) * headSize, y - headSize);
    } else {
      appendCoordinate(document, head, x + headSize, y + dy - Math.signum(dy) * headSize);
      appendCoordinate(document, head, x, y + dy);
      appendCoordinate(document, head, x - headSize, y + dy - Math.signum(dy) * headSize);
    }
    parent.appendChild(head);

    // Sheet reference label
    Element text = document.createElement("Text");
    text.setAttribute("String", targetSheet);
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.5");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0");
    textPres.setAttribute("G", "0");
    textPres.setAttribute("B", "0");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    double labelOffsetX = "RIGHT".equals(direction) ? 4.0 : ("LEFT".equals(direction) ? -4.0 : 0);
    double labelOffsetY = "UP".equals(direction) ? 4.0 : ("DOWN".equals(direction) ? -4.0 : 0);
    textLoc.setAttribute("X", String.valueOf(x + dx + labelOffsetX));
    textLoc.setAttribute("Y", String.valueOf(y + dy + labelOffsetY));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRefEl = document.createElement("Reference");
    textRefEl.setAttribute("X", "1");
    textRefEl.setAttribute("Y", "0");
    textRefEl.setAttribute("Z", "0");
    textPos.appendChild(textRefEl);
    text.appendChild(textPos);
    parent.appendChild(text);
  }

  /**
   * Appends a complete DEXPI {@code <Drawing>} element with border, column/row markers, and a
   * professional title block. The sheet dimensions are auto-computed to fit all content.
   *
   * @param document the XML document
   * @param parent the PlantModel root element
   * @param drawingName the drawing title (e.g. process name)
   * @param drawingNumber the drawing number (e.g. "PID-001")
   * @param revision the revision string (e.g. "A")
   * @param date the drawing date string
   * @param sheetWidth the computed sheet width in mm
   * @param sheetHeight the computed sheet height in mm
   */
  static void appendDrawing(Document document, Element parent, String drawingName,
      String drawingNumber, String revision, String date, double sheetWidth, double sheetHeight) {
    Element drawing = document.createElement("Drawing");
    drawing.setAttribute("Name", drawingName != null ? drawingName : "NeqSim P&ID");
    drawing.setAttribute("Type", "PID");

    // White background
    Element bgPres = document.createElement("Presentation");
    bgPres.setAttribute("R", "1");
    bgPres.setAttribute("G", "1");
    bgPres.setAttribute("B", "1");
    drawing.appendChild(bgPres);

    // Extent
    Element extent = document.createElement("Extent");
    Element extMin = document.createElement("Min");
    extMin.setAttribute("X", "0");
    extMin.setAttribute("Y", "0");
    extent.appendChild(extMin);
    Element extMax = document.createElement("Max");
    extMax.setAttribute("X", String.valueOf(sheetWidth));
    extMax.setAttribute("Y", String.valueOf(sheetHeight));
    extent.appendChild(extMax);
    drawing.appendChild(extent);

    // DrawingBorder
    Element drawingBorder = document.createElement("DrawingBorder");
    appendDrawingBorderLines(document, drawingBorder, sheetWidth, sheetHeight);
    appendColumnRowMarkers(document, drawingBorder, sheetWidth, sheetHeight);
    drawing.appendChild(drawingBorder);

    // Title block label
    appendTitleBlock(document, drawing, drawingName, drawingNumber, revision, date, sheetWidth);

    parent.appendChild(drawing);
  }

  /**
   * Appends the outer and inner border rectangles.
   *
   * @param document the XML document
   * @param border the DrawingBorder element
   * @param sheetWidth the sheet width in mm
   * @param sheetHeight the sheet height in mm
   */
  private static void appendDrawingBorderLines(Document document, Element border, double sheetWidth,
      double sheetHeight) {
    // Outer thin border
    appendBorderRect(document, border, 1.0, 1.0, sheetWidth - 1.0, sheetHeight - 1.0, 0.3);
    // Inner thick border
    appendBorderRect(document, border, BORDER_MARGIN, BORDER_MARGIN, sheetWidth - BORDER_MARGIN,
        sheetHeight - BORDER_MARGIN, 0.6);
  }

  /**
   * Appends a rectangle as a closed PolyLine.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param x1 left X
   * @param y1 bottom Y
   * @param x2 right X
   * @param y2 top Y
   * @param lineWeight the line weight
   */
  private static void appendBorderRect(Document document, Element parent, double x1, double y1,
      double x2, double y2, double lineWeight) {
    Element poly = document.createElement("PolyLine");
    poly.setAttribute("NumPoints", "5");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", String.valueOf(lineWeight));
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    poly.appendChild(pres);
    appendCoordinate(document, poly, x1, y1);
    appendCoordinate(document, poly, x2, y1);
    appendCoordinate(document, poly, x2, y2);
    appendCoordinate(document, poly, x1, y2);
    appendCoordinate(document, poly, x1, y1);
    parent.appendChild(poly);
  }

  /**
   * Appends column/row zone markers along the border edges (A-F vertical, 1-8 horizontal).
   *
   * @param document the XML document
   * @param border the DrawingBorder element
   * @param sheetWidth the sheet width in mm
   * @param sheetHeight the sheet height in mm
   */
  private static void appendColumnRowMarkers(Document document, Element border, double sheetWidth,
      double sheetHeight) {
    double innerLeft = BORDER_MARGIN;
    double innerRight = sheetWidth - BORDER_MARGIN;
    double innerBottom = BORDER_MARGIN;
    double innerTop = sheetHeight - BORDER_MARGIN;
    double drawableWidth = innerRight - innerLeft;
    double drawableHeight = innerTop - innerBottom;

    // Horizontal column markers — scale count to sheet width
    int cols = Math.max(8, (int) Math.ceil(sheetWidth / 52.0));
    double colWidth = drawableWidth / cols;
    for (int i = 0; i < cols; i++) {
      double cx = innerLeft + (i + 0.5) * colWidth;
      String num = String.valueOf(i + 1);
      // Top edge
      appendBorderText(document, border, num, cx, sheetHeight - 2.5);
      // Bottom edge
      appendBorderText(document, border, num, cx, 2.5);
      // Tick marks
      if (i > 0) {
        double tickX = innerLeft + i * colWidth;
        appendBorderTick(document, border, tickX, 1.0, tickX, innerBottom);
        appendBorderTick(document, border, tickX, sheetHeight - 1.0, tickX, innerTop);
      }
    }

    // Vertical row markers (lettered A-F)
    int rows = 6;
    double rowHeight = drawableHeight / rows;
    for (int i = 0; i < rows; i++) {
      double cy = innerBottom + (i + 0.5) * rowHeight;
      String letter = String.valueOf((char) ('A' + rows - 1 - i));
      // Left edge
      appendBorderText(document, border, letter, 2.5, cy);
      // Right edge
      appendBorderText(document, border, letter, sheetWidth - 2.5, cy);
      // Tick marks
      if (i > 0) {
        double tickY = innerBottom + i * rowHeight;
        appendBorderTick(document, border, 1.0, tickY, innerLeft, tickY);
        appendBorderTick(document, border, sheetWidth - 1.0, tickY, innerRight, tickY);
      }
    }
  }

  /**
   * Appends a border zone label (column number or row letter).
   *
   * @param document the XML document
   * @param parent the DrawingBorder element
   * @param text the label text
   * @param x X coordinate
   * @param y Y coordinate
   */
  private static void appendBorderText(Document document, Element parent, String text, double x,
      double y) {
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text);
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", "2.85");
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", "CenterCenter");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }

  /**
   * Appends a short tick mark line in the border margin.
   *
   * @param document the XML document
   * @param parent the DrawingBorder element
   * @param x1 start X
   * @param y1 start Y
   * @param x2 end X
   * @param y2 end Y
   */
  private static void appendBorderTick(Document document, Element parent, double x1, double y1,
      double x2, double y2) {
    Element poly = document.createElement("PolyLine");
    poly.setAttribute("NumPoints", "2");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", "0.3");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    poly.appendChild(pres);
    appendCoordinate(document, poly, x1, y1);
    appendCoordinate(document, poly, x2, y2);
    parent.appendChild(poly);
  }

  /**
   * Appends the title block label in the bottom-right corner of the drawing.
   *
   * @param document the XML document
   * @param drawing the Drawing element
   * @param drawingName the drawing title
   * @param drawingNumber the drawing number
   * @param revision the revision
   * @param date the date string
   * @param sheetWidth the width of the drawing sheet in layout units
   */
  private static void appendTitleBlock(Document document, Element drawing, String drawingName,
      String drawingNumber, String revision, String date, double sheetWidth) {
    Element label = document.createElement("Label");
    label.setAttribute("ID", "TitleBlock-1");
    label.setAttribute("ComponentClass", "Label");
    label.setAttribute("ComponentClassURI", "http://sandbox.dexpi.org/rdl/Label");

    double blockLeft = sheetWidth - BORDER_MARGIN - TITLE_BLOCK_WIDTH;
    double blockRight = sheetWidth - BORDER_MARGIN;
    double blockBottom = BORDER_MARGIN;
    double blockTop = BORDER_MARGIN + TITLE_BLOCK_HEIGHT;

    // Outer frame
    appendBorderRect(document, label, blockLeft, blockBottom, blockRight, blockTop, 0.58);

    // Row 1 (bottom): DRAWN BY | date | DRAWING NO | number
    double row1Bottom = blockBottom;
    double row1Top = row1Bottom + TITLE_ROW_HEIGHT;
    double col2 = blockLeft + 40.0;
    double col3 = blockLeft + 70.0;
    double col4 = blockLeft + 90.0;
    appendTitleCell(document, label, blockLeft, row1Bottom, col2, row1Top, "DRAWN BY");
    appendTitleCell(document, label, col2, row1Bottom, col3, row1Top, date != null ? date : "");
    appendTitleCell(document, label, col3, row1Bottom, col4, row1Top, "DRAWING NO");
    appendTitleCell(document, label, col4, row1Bottom, blockRight, row1Top,
        drawingNumber != null ? drawingNumber : "");

    // Row 2: CHECKED BY | - | REV | revision
    double row2Bottom = row1Top;
    double row2Top = row2Bottom + TITLE_ROW_HEIGHT;
    appendTitleCell(document, label, blockLeft, row2Bottom, col2, row2Top, "CHECKED BY");
    appendTitleCell(document, label, col2, row2Bottom, col3, row2Top, "-");
    appendTitleCell(document, label, col3, row2Bottom, col4, row2Top, "REV");
    appendTitleCell(document, label, col4, row2Bottom, blockRight, row2Top,
        revision != null ? revision : "0");

    // Row 3: APPROVED BY | - | STATUS | IFC
    double row3Bottom = row2Top;
    double row3Top = row3Bottom + TITLE_ROW_HEIGHT;
    appendTitleCell(document, label, blockLeft, row3Bottom, col2, row3Top, "APPROVED BY");
    appendTitleCell(document, label, col2, row3Bottom, col3, row3Top, "-");
    appendTitleCell(document, label, col3, row3Bottom, col4, row3Top, "STATUS");
    appendTitleCell(document, label, col4, row3Bottom, blockRight, row3Top, "IFC");

    // Row 4-6: Main title area (Name of drawing, large font)
    double titleBottom = row3Top;
    appendBorderRect(document, label, blockLeft, titleBottom, blockRight, blockTop, 0.28);
    // Centred title text
    double titleCenterX = (blockLeft + blockRight) / 2.0;
    double titleCenterY = (titleBottom + blockTop) / 2.0 + 3.0;
    appendTitleText(document, label,
        drawingName != null ? drawingName : "Process & Instrument Diagram", titleCenterX,
        titleCenterY, TITLE_MAIN_FONT_HEIGHT, "CenterCenter");
    // Application label
    appendTitleText(document, label, "Generated by NeqSim", titleCenterX, titleCenterY - 5.0,
        TITLE_FONT_HEIGHT, "CenterCenter");

    drawing.appendChild(label);
  }

  /**
   * Appends a cell in the title block with a border and text.
   *
   * @param document the XML document
   * @param parent the label element
   * @param x1 left X
   * @param y1 bottom Y
   * @param x2 right X
   * @param y2 top Y
   * @param text the cell text
   */
  private static void appendTitleCell(Document document, Element parent, double x1, double y1,
      double x2, double y2, String text) {
    appendBorderRect(document, parent, x1, y1, x2, y2, 0.28);
    appendTitleText(document, parent, text, x1 + 1.2, y1 + 1.5, TITLE_FONT_HEIGHT, "LeftBottom");
  }

  /**
   * Appends a text element for the title block.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param text the text string
   * @param x X position
   * @param y Y position
   * @param fontSize font height
   * @param justification text justification
   */
  private static void appendTitleText(Document document, Element parent, String text, double x,
      double y, double fontSize, String justification) {
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text);
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", String.valueOf(fontSize));
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", justification);
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }

  // ---- Flow direction arrows ----

  /**
   * Appends a solid triangular flow-direction arrow at the midpoint of a connection line.
   *
   * <p>
   * The arrow points from source to destination. For horizontal lines, the arrow points right. For
   * orthogonal routed lines, the arrow is placed on the first horizontal segment. The arrow is
   * rendered as a filled PolyLine triangle in the process line colour.
   * </p>
   *
   * @param document the XML document
   * @param parent the PipingNetworkSegment element
   * @param fromX source X coordinate
   * @param fromY source Y coordinate
   * @param toX destination X coordinate
   * @param toY destination Y coordinate
   */
  static void appendFlowArrow(Document document, Element parent, double fromX, double fromY,
      double toX, double toY) {
    // Place arrow at midpoint of the first horizontal segment
    double midX;
    double midY;
    boolean sameY = Math.abs(fromY - toY) < 0.5;
    if (sameY) {
      midX = (fromX + toX) / 2.0;
      midY = fromY;
    } else {
      double routeMidX = (fromX + toX) / 2.0;
      midX = (fromX + routeMidX) / 2.0;
      midY = fromY;
    }

    // Direction: always left-to-right for horizontal segments
    double dirX = toX > fromX ? 1.0 : -1.0;

    Element poly = document.createElement("PolyLine");
    poly.setAttribute("NumPoints", "4");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", String.valueOf(PROCESS_LINE_WEIGHT));
    pres.setAttribute("R", LINE_COLOR_R);
    pres.setAttribute("G", LINE_COLOR_G);
    pres.setAttribute("B", LINE_COLOR_B);
    poly.appendChild(pres);

    // Triangle: tip, upper corner, lower corner, back to tip
    double tipX = midX + dirX * ARROW_LENGTH;
    appendCoordinate(document, poly, tipX, midY);
    appendCoordinate(document, poly, midX, midY + ARROW_HALF_WIDTH);
    appendCoordinate(document, poly, midX, midY - ARROW_HALF_WIDTH);
    appendCoordinate(document, poly, tipX, midY);
    parent.appendChild(poly);
  }

  // ---- Stream number labels on connection lines ----

  /**
   * Appends a stream number label at the midpoint of a connection line.
   *
   * @param document the XML document
   * @param parent the PipingNetworkSegment element
   * @param streamNumber the stream/line number text to display
   * @param fromX source X coordinate
   * @param fromY source Y coordinate
   * @param toX destination X coordinate
   * @param toY destination Y coordinate
   */
  static void appendStreamLabel(Document document, Element parent, String streamNumber,
      double fromX, double fromY, double toX, double toY) {
    if (streamNumber == null || streamNumber.trim().isEmpty()) {
      return;
    }
    double midX = (fromX + toX) / 2.0;
    double midY = (fromY + toY) / 2.0;
    // Offset the label slightly above the line
    double labelY = midY + 3.5;

    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", streamNumber);
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", "2.5");
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", "CenterBottom");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0.501960784");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(midX));
    location.setAttribute("Y", String.valueOf(labelY));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }

  // ---- Stream data table ----

  /**
   * Appends a process conditions table (stream table) at the bottom of the drawing.
   *
   * <p>
   * The table shows key process data (temperature, pressure, flow, phase) for each major stream in
   * the process. This follows the standard P&amp;ID convention of including a stream data table.
   * </p>
   *
   * @param document the XML document
   * @param parent the Drawing or PlantModel element
   * @param streamData ordered list of stream data entries
   */
  static void appendStreamTable(Document document, Element parent,
      List<StreamTableEntry> streamData) {
    if (streamData == null || streamData.isEmpty()) {
      return;
    }

    Element label = document.createElement("Label");
    label.setAttribute("ID", "StreamDataTable-1");
    label.setAttribute("ComponentClass", "Label");

    double tableLeft = BORDER_MARGIN + 2.0;
    double tableTop = TABLE_TOP_Y;
    int numStreams = Math.min(streamData.size(), 12); // Limit to 12 streams for readability

    String[] rowLabels =
        {"Stream", "Temperature (\u00B0C)", "Pressure (bara)", "Flow (kg/hr)", "Phase"};
    int numRows = rowLabels.length;

    double tableRight = tableLeft + TABLE_LABEL_WIDTH + numStreams * TABLE_COL_WIDTH;
    double tableBottom = tableTop - numRows * TABLE_ROW_HEIGHT;

    // Header column (row labels)
    for (int r = 0; r < numRows; r++) {
      double rowTop = tableTop - r * TABLE_ROW_HEIGHT;
      double rowBottom = rowTop - TABLE_ROW_HEIGHT;
      appendBorderRect(document, label, tableLeft, rowBottom, tableLeft + TABLE_LABEL_WIDTH, rowTop,
          0.2);
      appendTableText(document, label, rowLabels[r], tableLeft + 1.0,
          (rowTop + rowBottom) / 2.0 - 0.5, "LeftCenter");
    }

    // Data columns
    for (int c = 0; c < numStreams; c++) {
      StreamTableEntry entry = streamData.get(c);
      double colLeft = tableLeft + TABLE_LABEL_WIDTH + c * TABLE_COL_WIDTH;
      double colRight = colLeft + TABLE_COL_WIDTH;
      String[] values =
          {entry.name, entry.temperatureC, entry.pressureBara, entry.flowKgHr, entry.phase};

      for (int r = 0; r < numRows; r++) {
        double rowTop = tableTop - r * TABLE_ROW_HEIGHT;
        double rowBottom = rowTop - TABLE_ROW_HEIGHT;
        appendBorderRect(document, label, colLeft, rowBottom, colRight, rowTop, 0.15);
        appendTableText(document, label, values[r], (colLeft + colRight) / 2.0,
            (rowTop + rowBottom) / 2.0 - 0.5, "CenterCenter");
      }
    }

    parent.appendChild(label);
  }

  /**
   * Appends a text element for the stream table.
   *
   * @param document the XML document
   * @param parent the label element
   * @param text the text string
   * @param x X position
   * @param y Y position
   * @param justification text justification
   */
  private static void appendTableText(Document document, Element parent, String text, double x,
      double y, String justification) {
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text != null ? text : "-");
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", String.valueOf(TABLE_FONT_HEIGHT));
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", justification);
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }

  /**
   * Holds stream data for a single column in the stream data table.
   */
  static final class StreamTableEntry {
    /** Stream name/number. */
    final String name;
    /** Temperature in Celsius. */
    final String temperatureC;
    /** Pressure in bara. */
    final String pressureBara;
    /** Mass flow rate in kg/hr. */
    final String flowKgHr;
    /** Phase description (Gas, Liquid, Two-phase). */
    final String phase;

    /**
     * Creates a stream table entry.
     *
     * @param name stream name
     * @param temperatureC temperature string
     * @param pressureBara pressure string
     * @param flowKgHr flow rate string
     * @param phase phase description
     */
    StreamTableEntry(String name, String temperatureC, String pressureBara, String flowKgHr,
        String phase) {
      this.name = name;
      this.temperatureC = temperatureC;
      this.pressureBara = pressureBara;
      this.flowKgHr = flowKgHr;
      this.phase = phase;
    }
  }

  // ==== Battery limit boundary (NORSOK Z-003 / ISO 10628) ====

  /**
   * Appends a dashed battery-limit boundary rectangle around all equipment.
   *
   * <p>
   * Per NORSOK Z-003 and ISO 10628, battery limits are shown as bold dash-dot lines enclosing the
   * process unit boundary. The boundary is computed from the extremes of all equipment positions
   * with padding.
   * </p>
   *
   * @param document the XML document
   * @param parent the Drawing or PlantModel element
   * @param positions all equipment positions
   * @param areaLabel the battery limit label (e.g. "AREA 100 - GAS PROCESSING")
   */
  static void appendBatteryLimitBoundary(Document document, Element parent,
      Map<String, EquipmentPosition> positions, String areaLabel) {
    if (positions == null || positions.isEmpty()) {
      return;
    }
    double minX = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (EquipmentPosition pos : positions.values()) {
      if (pos.x < minX) {
        minX = pos.x;
      }
      if (pos.x > maxX) {
        maxX = pos.x;
      }
      if (pos.y < minY) {
        minY = pos.y;
      }
      if (pos.y > maxY) {
        maxY = pos.y;
      }
    }

    double bLeft = minX - BATTERY_LIMIT_PADDING;
    double bRight = maxX + BATTERY_LIMIT_PADDING;
    double bBottom = minY - BATTERY_LIMIT_PADDING;
    double bTop = maxY + BATTERY_LIMIT_PADDING;

    Element label = document.createElement("Label");
    label.setAttribute("ID", "BatteryLimit-1");
    label.setAttribute("ComponentClass", "Label");

    // Dashed rectangle (LineType=3 = dash-dot per DEXPI schema)
    Element poly = document.createElement("PolyLine");
    poly.setAttribute("NumPoints", "5");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "3");
    pres.setAttribute("LineWeight", String.valueOf(BATTERY_LIMIT_LINE_WEIGHT));
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    poly.appendChild(pres);
    appendCoordinate(document, poly, bLeft, bBottom);
    appendCoordinate(document, poly, bRight, bBottom);
    appendCoordinate(document, poly, bRight, bTop);
    appendCoordinate(document, poly, bLeft, bTop);
    appendCoordinate(document, poly, bLeft, bBottom);
    label.appendChild(poly);

    // Area label at top-left corner
    if (areaLabel != null && !areaLabel.trim().isEmpty()) {
      Element text = document.createElement("Text");
      text.setAttribute("String", areaLabel);
      text.setAttribute("Font", FONT_NAME);
      text.setAttribute("Height", "3.5");
      text.setAttribute("Width", "0");
      text.setAttribute("Justification", "LeftBottom");
      Element textPres = document.createElement("Presentation");
      textPres.setAttribute("R", "0");
      textPres.setAttribute("G", "0");
      textPres.setAttribute("B", "0");
      text.appendChild(textPres);
      Element position = document.createElement("Position");
      Element location = document.createElement("Location");
      location.setAttribute("X", String.valueOf(bLeft + 2.0));
      location.setAttribute("Y", String.valueOf(bTop + 2.0));
      location.setAttribute("Z", "0");
      position.appendChild(location);
      Element axis = document.createElement("Axis");
      axis.setAttribute("X", "0");
      axis.setAttribute("Y", "0");
      axis.setAttribute("Z", "1");
      position.appendChild(axis);
      Element ref = document.createElement("Reference");
      ref.setAttribute("X", "1");
      ref.setAttribute("Y", "0");
      ref.setAttribute("Z", "0");
      position.appendChild(ref);
      text.appendChild(position);
      label.appendChild(text);
    }

    parent.appendChild(label);
  }

  // ==== Valve fail position marker (NORSOK Z-003) ====

  /**
   * Appends a valve fail-position marker text (FC, FO, FL) near a valve position.
   *
   * <p>
   * Per NORSOK Z-003, valve fail positions are marked adjacent to the valve symbol: FC = fail
   * closed, FO = fail open, FL = fail last position.
   * </p>
   *
   * @param document the XML document
   * @param parent the PipingComponent (valve) element
   * @param failPosition the fail position code (FC, FO, or FL)
   * @param x the valve X coordinate
   * @param y the valve Y coordinate
   */
  static void appendFailPositionMarker(Document document, Element parent, String failPosition,
      double x, double y) {
    if (failPosition == null || failPosition.trim().isEmpty()) {
      return;
    }
    String fp = failPosition.trim().toUpperCase(Locale.ROOT);
    // Color coding per NORSOK Z-003: FC=red, FO=green, FL=amber/orange
    String colorR = "0.501960784";
    String colorG = "0";
    String colorB = "0";
    if ("FC".equals(fp)) {
      colorR = "0.8";
      colorG = "0";
      colorB = "0";
    } else if ("FO".equals(fp)) {
      colorR = "0";
      colorG = "0.6";
      colorB = "0";
    } else if ("FL".equals(fp)) {
      colorR = "0.8";
      colorG = "0.5";
      colorB = "0";
    }
    Element text = document.createElement("Text");
    text.setAttribute("String", fp);
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.5");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterTop");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", colorR);
    pres.setAttribute("G", colorG);
    pres.setAttribute("B", colorB);
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y - 5.0));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== PST (Partial Stroke Test) marker (IEC 61508 / IEC 61511) ====

  /**
   * Appends a PST (Partial Stroke Test) annotation marker near a safety valve.
   *
   * <p>
   * Per IEC 61508 and IEC 61511, valves with partial stroke test capability are annotated with a
   * "PST" label and a distinctive marker to indicate online diagnostic capability.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param x the valve center X
   * @param y the valve center Y
   */
  static void appendPstMarker(Document document, Element parent, double x, double y) {
    // Small rectangle outline for PST annotation box
    Element polyLine = document.createElement("PolyLine");
    polyLine.setAttribute("NumPoints", "5");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", "0.2");
    pres.setAttribute("R", "0.6");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0.6");
    polyLine.appendChild(pres);
    double bx = x + 7.0;
    double by = y + 3.0;
    appendCoordinate(document, polyLine, bx - 5, by - 2);
    appendCoordinate(document, polyLine, bx + 5, by - 2);
    appendCoordinate(document, polyLine, bx + 5, by + 2);
    appendCoordinate(document, polyLine, bx - 5, by + 2);
    appendCoordinate(document, polyLine, bx - 5, by - 2);
    parent.appendChild(polyLine);

    // PST label text
    Element text = document.createElement("Text");
    text.setAttribute("String", "PST");
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0.6");
    textPres.setAttribute("G", "0");
    textPres.setAttribute("B", "0.6");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    textLoc.setAttribute("X", String.valueOf(bx));
    textLoc.setAttribute("Y", String.valueOf(by));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPos.appendChild(textRef);
    text.appendChild(textPos);
    parent.appendChild(text);
  }

  // ==== Heat trace indication (ISO 10628 / NORSOK Z-003) ====

  /**
   * Appends a heat trace indication on a pipe segment.
   *
   * <p>
   * Per ISO 10628 and NORSOK Z-003, heat traced lines are indicated with a zigzag marking along the
   * pipe and an "HT" or specific heat trace type code.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param traceType the heat trace type code (e.g. "ET" for electric, "ST" for steam)
   * @param fromX source X
   * @param fromY source Y
   * @param toX destination X
   * @param toY destination Y
   */
  static void appendHeatTraceMark(Document document, Element parent, String traceType, double fromX,
      double fromY, double toX, double toY) {
    if (traceType == null || traceType.trim().isEmpty()) {
      return;
    }
    // Place zigzag marks at 33% and 66% along the line
    for (double frac : new double[] {0.33, 0.66}) {
      double mx = fromX + (toX - fromX) * frac;
      double my = fromY + (toY - fromY) * frac;
      // Zigzag (3 small chevrons)
      Element zigzag = document.createElement("PolyLine");
      zigzag.setAttribute("NumPoints", "4");
      Element zPres = document.createElement("Presentation");
      zPres.setAttribute("LineType", "0");
      zPres.setAttribute("LineWeight", "0.2");
      zPres.setAttribute("R", "0.8");
      zPres.setAttribute("G", "0.4");
      zPres.setAttribute("B", "0");
      zigzag.appendChild(zPres);
      appendCoordinate(document, zigzag, mx - 2, my + 1);
      appendCoordinate(document, zigzag, mx - 1, my - 1);
      appendCoordinate(document, zigzag, mx + 1, my + 1);
      appendCoordinate(document, zigzag, mx + 2, my - 1);
      parent.appendChild(zigzag);
    }

    // Heat trace type label at midpoint
    double midX = (fromX + toX) / 2.0;
    double midY = (fromY + toY) / 2.0;
    Element text = document.createElement("Text");
    text.setAttribute("String", traceType.trim().toUpperCase(Locale.ROOT));
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterBottom");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0.8");
    textPres.setAttribute("G", "0.4");
    textPres.setAttribute("B", "0");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    textLoc.setAttribute("X", String.valueOf(midX));
    textLoc.setAttribute("Y", String.valueOf(midY + 3.0));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPos.appendChild(textRef);
    text.appendChild(textPos);
    parent.appendChild(text);
  }

  // ==== Equipment weight & COG annotation ====

  /**
   * Appends an equipment weight and center-of-gravity annotation marker.
   *
   * <p>
   * Shows the equipment dry weight and operating weight in a small annotation box below the
   * equipment symbol. Used for mechanical design and layout planning.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param dryWeightKg the equipment dry weight in kg (0 to omit)
   * @param operatingWeightKg the operating weight in kg (0 to omit)
   * @param x the equipment center X
   * @param y the equipment symbol bottom Y
   */
  static void appendWeightAnnotation(Document document, Element parent, double dryWeightKg,
      double operatingWeightKg, double x, double y) {
    if (dryWeightKg <= 0 && operatingWeightKg <= 0) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    if (dryWeightKg > 0) {
      sb.append("Dry: ").append(String.format(Locale.ROOT, "%.0f", dryWeightKg)).append(" kg");
    }
    if (operatingWeightKg > 0) {
      if (sb.length() > 0) {
        sb.append(" / ");
      }
      sb.append("Op: ").append(String.format(Locale.ROOT, "%.0f", operatingWeightKg)).append(" kg");
    }
    Element text = document.createElement("Text");
    text.setAttribute("String", sb.toString());
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "1.8");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterTop");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0.35");
    pres.setAttribute("G", "0.35");
    pres.setAttribute("B", "0.35");
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y - 18.0));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== Sample point symbol (ISO 10628) ====

  /**
   * Appends a sample point symbol on a process line.
   *
   * <p>
   * Per ISO 10628, sample points are indicated with a small circle (or filled dot) with an "SP"
   * label on the process line where grab or online samples can be taken.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param tag the sample point tag (e.g. "SP-001")
   * @param x the sample point X
   * @param y the sample point Y
   */
  static void appendSamplePoint(Document document, Element parent, String tag, double x, double y) {
    // Small filled circle on process line
    Element circle = document.createElement("Circle");
    circle.setAttribute("Radius", "1.5");
    circle.setAttribute("Filled", "Solid");
    Element circPres = document.createElement("Presentation");
    circPres.setAttribute("LineType", "0");
    circPres.setAttribute("LineWeight", "0.2");
    circPres.setAttribute("R", "0");
    circPres.setAttribute("G", "0");
    circPres.setAttribute("B", "0");
    circle.appendChild(circPres);
    Element circPos = document.createElement("Position");
    Element circLoc = document.createElement("Location");
    circLoc.setAttribute("X", String.valueOf(x));
    circLoc.setAttribute("Y", String.valueOf(y));
    circLoc.setAttribute("Z", "0");
    circPos.appendChild(circLoc);
    Element circAxis = document.createElement("Axis");
    circAxis.setAttribute("X", "0");
    circAxis.setAttribute("Y", "0");
    circAxis.setAttribute("Z", "1");
    circPos.appendChild(circAxis);
    Element circRef = document.createElement("Reference");
    circRef.setAttribute("X", "1");
    circRef.setAttribute("Y", "0");
    circRef.setAttribute("Z", "0");
    circPos.appendChild(circRef);
    circle.appendChild(circPos);
    parent.appendChild(circle);

    // Sample stem line going down
    Element stem = document.createElement("PolyLine");
    stem.setAttribute("NumPoints", "2");
    Element stemPres = document.createElement("Presentation");
    stemPres.setAttribute("LineType", "0");
    stemPres.setAttribute("LineWeight", "0.25");
    stemPres.setAttribute("R", "0");
    stemPres.setAttribute("G", "0");
    stemPres.setAttribute("B", "0");
    stem.appendChild(stemPres);
    appendCoordinate(document, stem, x, y);
    appendCoordinate(document, stem, x, y - 6.0);
    parent.appendChild(stem);

    // Tag label below
    if (tag != null && !tag.trim().isEmpty()) {
      Element text = document.createElement("Text");
      text.setAttribute("String", tag);
      text.setAttribute("Font", FONT_NAME);
      text.setAttribute("Height", "2.0");
      text.setAttribute("Width", "0");
      text.setAttribute("Justification", "CenterTop");
      Element textPres = document.createElement("Presentation");
      textPres.setAttribute("R", "0");
      textPres.setAttribute("G", "0");
      textPres.setAttribute("B", "0");
      text.appendChild(textPres);
      Element textPos = document.createElement("Position");
      Element textLoc = document.createElement("Location");
      textLoc.setAttribute("X", String.valueOf(x));
      textLoc.setAttribute("Y", String.valueOf(y - 7.5));
      textLoc.setAttribute("Z", "0");
      textPos.appendChild(textLoc);
      Element textAxis = document.createElement("Axis");
      textAxis.setAttribute("X", "0");
      textAxis.setAttribute("Y", "0");
      textAxis.setAttribute("Z", "1");
      textPos.appendChild(textAxis);
      Element textRef = document.createElement("Reference");
      textRef.setAttribute("X", "1");
      textRef.setAttribute("Y", "0");
      textRef.setAttribute("Z", "0");
      textPos.appendChild(textRef);
      text.appendChild(textPos);
      parent.appendChild(text);
    }
  }

  // ==== Gauge glass symbol (ISO 10628) ====

  /**
   * Appends a gauge glass (level gauge) symbol on a vessel.
   *
   * <p>
   * Per ISO 10628, sight/gauge glasses on vessels are represented as a narrow rectangle alongside
   * the vessel with connection stubs at top and bottom.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param x the gauge glass X position (vessel wall edge)
   * @param yTop the top connection Y
   * @param yBottom the bottom connection Y
   */
  static void appendGaugeGlass(Document document, Element parent, double x, double yTop,
      double yBottom) {
    double gx = x + 5.0;
    // Narrow rectangle for gauge glass tube
    Element rect = document.createElement("PolyLine");
    rect.setAttribute("NumPoints", "5");
    Element rectPres = document.createElement("Presentation");
    rectPres.setAttribute("LineType", "0");
    rectPres.setAttribute("LineWeight", "0.2");
    rectPres.setAttribute("R", "0");
    rectPres.setAttribute("G", "0");
    rectPres.setAttribute("B", "0");
    rect.appendChild(rectPres);
    appendCoordinate(document, rect, gx - 1, yBottom);
    appendCoordinate(document, rect, gx + 1, yBottom);
    appendCoordinate(document, rect, gx + 1, yTop);
    appendCoordinate(document, rect, gx - 1, yTop);
    appendCoordinate(document, rect, gx - 1, yBottom);
    parent.appendChild(rect);

    // Top connection stub
    Element topStub = document.createElement("PolyLine");
    topStub.setAttribute("NumPoints", "2");
    Element topPres = document.createElement("Presentation");
    topPres.setAttribute("LineType", "0");
    topPres.setAttribute("LineWeight", "0.25");
    topPres.setAttribute("R", "0");
    topPres.setAttribute("G", "0");
    topPres.setAttribute("B", "0");
    topStub.appendChild(topPres);
    appendCoordinate(document, topStub, x, yTop);
    appendCoordinate(document, topStub, gx - 1, yTop);
    parent.appendChild(topStub);

    // Bottom connection stub
    Element botStub = document.createElement("PolyLine");
    botStub.setAttribute("NumPoints", "2");
    Element botPres = document.createElement("Presentation");
    botPres.setAttribute("LineType", "0");
    botPres.setAttribute("LineWeight", "0.25");
    botPres.setAttribute("R", "0");
    botPres.setAttribute("G", "0");
    botPres.setAttribute("B", "0");
    botStub.appendChild(botPres);
    appendCoordinate(document, botStub, x, yBottom);
    appendCoordinate(document, botStub, gx - 1, yBottom);
    parent.appendChild(botStub);

    // LG label
    Element text = document.createElement("Text");
    text.setAttribute("String", "LG");
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0");
    textPres.setAttribute("G", "0");
    textPres.setAttribute("B", "0");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    textLoc.setAttribute("X", String.valueOf(gx + 4));
    textLoc.setAttribute("Y", String.valueOf((yTop + yBottom) / 2.0));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPos.appendChild(textRef);
    text.appendChild(textPos);
    parent.appendChild(text);
  }

  // ==== Insulation symbol (ISO 10628 / NORSOK Z-003) ====

  /**
   * Appends insulation marking on a process line between two points.
   *
   * <p>
   * Per ISO 10628 and NORSOK Z-003, insulation is indicated by a short perpendicular tick mark on
   * the process line with a code letter: H = hot insulation, C = cold insulation, P = personnel
   * protection, A = acoustic.
   * </p>
   *
   * @param document the XML document
   * @param parent the PipingNetworkSegment element
   * @param insulationCode the insulation code (H, C, P, A)
   * @param fromX source X
   * @param fromY source Y
   * @param toX destination X
   * @param toY destination Y
   */
  static void appendInsulationMark(Document document, Element parent, String insulationCode,
      double fromX, double fromY, double toX, double toY) {
    if (insulationCode == null || insulationCode.trim().isEmpty()) {
      return;
    }
    // Place tick marks at 25% and 75% along the line
    for (double frac : new double[] {0.25, 0.75}) {
      double mx = fromX + frac * (toX - fromX);
      double my = fromY + frac * (toY - fromY);

      // Perpendicular tick mark
      Element tick = document.createElement("PolyLine");
      tick.setAttribute("NumPoints", "2");
      Element tickPres = document.createElement("Presentation");
      tickPres.setAttribute("LineType", "0");
      tickPres.setAttribute("LineWeight", "0.3");
      tickPres.setAttribute("R", LINE_COLOR_R);
      tickPres.setAttribute("G", LINE_COLOR_G);
      tickPres.setAttribute("B", LINE_COLOR_B);
      tick.appendChild(tickPres);
      if (Math.abs(fromY - toY) < 0.5) {
        // Horizontal line: tick is vertical
        appendCoordinate(document, tick, mx, my - 2.5);
        appendCoordinate(document, tick, mx, my + 2.5);
      } else {
        // Vertical line: tick is horizontal
        appendCoordinate(document, tick, mx - 2.5, my);
        appendCoordinate(document, tick, mx + 2.5, my);
      }
      parent.appendChild(tick);
    }

    // Insulation code text at midpoint
    double midX = (fromX + toX) / 2.0;
    double midY = (fromY + toY) / 2.0;
    Element text = document.createElement("Text");
    text.setAttribute("String", insulationCode.trim().toUpperCase(Locale.ROOT));
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterBottom");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", LINE_COLOR_R);
    pres.setAttribute("G", LINE_COLOR_G);
    pres.setAttribute("B", LINE_COLOR_B);
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(midX));
    location.setAttribute("Y", String.valueOf(midY + 4.5));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== Equipment orientation marker (ISO 10628) ====

  /**
   * Appends an orientation marker (H or V) next to equipment, per ISO 10628 convention.
   *
   * @param document the XML document
   * @param parent the equipment element
   * @param orientation the orientation string ("H" or "V")
   * @param x equipment center X
   * @param y equipment center Y
   */
  static void appendOrientationMarker(Document document, Element parent, String orientation,
      double x, double y) {
    if (orientation == null || orientation.trim().isEmpty()) {
      return;
    }
    Element text = document.createElement("Text");
    text.setAttribute("String", "(" + orientation.trim().toUpperCase(Locale.ROOT) + ")");
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.5");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "LeftCenter");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0.501960784");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x + 15.0));
    location.setAttribute("Y", String.valueOf(y + 12.0));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== SIL level marker on instruments (IEC 61511 / NORSOK Z-003) ====

  /**
   * Appends a SIL level marker below an instrument bubble.
   *
   * <p>
   * Per NORSOK Z-003 and IEC 61511, safety instrumented functions are marked with their SIL level
   * (SIL 1, SIL 2, SIL 3) below the instrument bubble. The text is shown in a distinctive style.
   * </p>
   *
   * @param document the XML document
   * @param parent the ProcessInstrumentationFunction element
   * @param silLevel the SIL level (1, 2, or 3)
   * @param x the instrument bubble center X
   * @param y the instrument bubble center Y
   */
  static void appendSilMarker(Document document, Element parent, int silLevel, double x, double y) {
    if (silLevel < 1 || silLevel > 4) {
      return;
    }
    // SIL 2+ instruments get a concentric double-border circle (IEC 61511 / NORSOK Z-003)
    if (silLevel >= 2) {
      double outerRadius = INSTRUMENT_BUBBLE_RADIUS + 1.5;
      Element outerCircle = document.createElement("Circle");
      outerCircle.setAttribute("Radius", String.valueOf(outerRadius));
      Element outerPres = document.createElement("Presentation");
      outerPres.setAttribute("LineType", "0");
      outerPres.setAttribute("LineWeight", "0.25");
      outerPres.setAttribute("R", "0.8");
      outerPres.setAttribute("G", "0");
      outerPres.setAttribute("B", "0");
      outerCircle.appendChild(outerPres);
      Element outerPos = document.createElement("Position");
      Element outerLoc = document.createElement("Location");
      outerLoc.setAttribute("X", String.valueOf(x));
      outerLoc.setAttribute("Y", String.valueOf(y));
      outerLoc.setAttribute("Z", "0");
      outerPos.appendChild(outerLoc);
      Element outerAxis = document.createElement("Axis");
      outerAxis.setAttribute("X", "0");
      outerAxis.setAttribute("Y", "0");
      outerAxis.setAttribute("Z", "1");
      outerPos.appendChild(outerAxis);
      Element outerRef = document.createElement("Reference");
      outerRef.setAttribute("X", "1");
      outerRef.setAttribute("Y", "0");
      outerRef.setAttribute("Z", "0");
      outerPos.appendChild(outerRef);
      outerCircle.appendChild(outerPos);
      parent.appendChild(outerCircle);
    }
    Element text = document.createElement("Text");
    text.setAttribute("String", "SIL " + silLevel);
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterTop");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0.8");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y - INSTRUMENT_BUBBLE_RADIUS - 1.5));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== Utility line type rendering ====

  // ==== Solenoid actuator marker (ISA 5.1) ====

  /**
   * Appends a solenoid actuator diamond symbol near a valve position.
   *
   * <p>
   * Per ISA 5.1, solenoid-actuated valves (ESD, blowdown, HIPPS) are indicated with a small diamond
   * symbol on the valve actuator stem, connected to the controller by a signal line.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element to append drawing primitives to
   * @param valveX the valve center X
   * @param valveY the valve center Y
   * @param controllerX the controller bubble center X
   * @param controllerY the controller bubble center Y
   */
  static void appendSolenoidMarker(Document document, Element parent, double valveX, double valveY,
      double controllerX, double controllerY) {
    double solY = valveY + 6.0;
    double solSize = 2.5;

    // Diamond shape for solenoid
    Element polyLine = document.createElement("PolyLine");
    polyLine.setAttribute("NumPoints", "5");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("LineType", "0");
    pres.setAttribute("LineWeight", "0.2");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "1");
    polyLine.appendChild(pres);
    appendCoordinate(document, polyLine, valveX, solY + solSize);
    appendCoordinate(document, polyLine, valveX + solSize, solY);
    appendCoordinate(document, polyLine, valveX, solY - solSize);
    appendCoordinate(document, polyLine, valveX - solSize, solY);
    appendCoordinate(document, polyLine, valveX, solY + solSize);
    parent.appendChild(polyLine);

    // "S" label inside the diamond
    Element text = document.createElement("Text");
    text.setAttribute("String", "S");
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.0");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0");
    textPres.setAttribute("G", "0");
    textPres.setAttribute("B", "1");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    textLoc.setAttribute("X", String.valueOf(valveX));
    textLoc.setAttribute("Y", String.valueOf(solY));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPos.appendChild(textRef);
    text.appendChild(textPos);
    parent.appendChild(text);

    // Signal line from controller to solenoid
    Element sigLine = document.createElement("CenterLine");
    sigLine.setAttribute("NumPoints", "2");
    Element sigPres = document.createElement("Presentation");
    sigPres.setAttribute("LineType", "2");
    sigPres.setAttribute("LineWeight", String.valueOf(SIGNAL_LINE_WEIGHT));
    sigPres.setAttribute("R", "0");
    sigPres.setAttribute("G", "0");
    sigPres.setAttribute("B", "1");
    sigLine.appendChild(sigPres);
    appendCoordinate(document, sigLine, controllerX, controllerY - INSTRUMENT_BUBBLE_RADIUS);
    appendCoordinate(document, sigLine, valveX, solY + solSize);
    parent.appendChild(sigLine);
  }

  /**
   * Appends a connection line with a specific line type for utility services.
   *
   * <p>
   * Per ISO 10628 and NORSOK Z-003, different piping services use distinct line styles: solid for
   * process, dashed for steam/utility, dotted for drain, dash-dot for instrument air, etc.
   * </p>
   *
   * @param document the XML document
   * @param parent the PipingNetworkSegment element
   * @param fromX source X
   * @param fromY source Y
   * @param toX destination X
   * @param toY destination Y
   * @param lineType ISO/DEXPI line type (0=solid, 1=dashed, 2=dotted, 3=dash-dot)
   * @param colorR red component (0-1)
   * @param colorG green component (0-1)
   * @param colorB blue component (0-1)
   */
  static void appendStyledConnectionLine(Document document, Element parent, double fromX,
      double fromY, double toX, double toY, int lineType, String colorR, String colorG,
      String colorB) {
    boolean sameY = Math.abs(fromY - toY) < 0.5;
    if (sameY) {
      // Simple horizontal line
      Element poly = document.createElement("PolyLine");
      poly.setAttribute("NumPoints", "2");
      Element pres = document.createElement("Presentation");
      pres.setAttribute("LineType", String.valueOf(lineType));
      pres.setAttribute("LineWeight", String.valueOf(PROCESS_LINE_WEIGHT));
      pres.setAttribute("R", colorR);
      pres.setAttribute("G", colorG);
      pres.setAttribute("B", colorB);
      poly.appendChild(pres);
      appendCoordinate(document, poly, fromX, fromY);
      appendCoordinate(document, poly, toX, toY);
      parent.appendChild(poly);
    } else {
      // Orthogonal routing: H-V-H
      double midX = (fromX + toX) / 2.0;
      Element poly = document.createElement("PolyLine");
      poly.setAttribute("NumPoints", "4");
      Element pres = document.createElement("Presentation");
      pres.setAttribute("LineType", String.valueOf(lineType));
      pres.setAttribute("LineWeight", String.valueOf(PROCESS_LINE_WEIGHT));
      pres.setAttribute("R", colorR);
      pres.setAttribute("G", colorG);
      pres.setAttribute("B", colorB);
      poly.appendChild(pres);
      appendCoordinate(document, poly, fromX, fromY);
      appendCoordinate(document, poly, midX, fromY);
      appendCoordinate(document, poly, midX, toY);
      appendCoordinate(document, poly, toX, toY);
      parent.appendChild(poly);
    }
  }

  // ==== Utility supply connection points (ISO 10628) ====

  /**
   * Appends a utility supply connection point with label and dashed connection line.
   *
   * <p>
   * Per ISO 10628, utility connections (instrument air, steam, cooling water, nitrogen) are shown
   * as labeled connection stubs with dashed lines and a utility code identifier.
   * </p>
   *
   * @param document the XML document
   * @param parent the parent element
   * @param utilityCode short code for the utility (e.g. "IA", "STM", "CW", "N2")
   * @param x connection X coordinate
   * @param y connection Y coordinate
   * @param targetX equipment or valve X coordinate the utility connects to
   * @param targetY equipment or valve Y coordinate the utility connects to
   */
  static void appendUtilityConnectionPoint(Document document, Element parent, String utilityCode,
      double x, double y, double targetX, double targetY) {
    // Utility label text
    Element text = document.createElement("Text");
    text.setAttribute("String", utilityCode);
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.5");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterCenter");
    Element textPres = document.createElement("Presentation");
    textPres.setAttribute("R", "0.4");
    textPres.setAttribute("G", "0.4");
    textPres.setAttribute("B", "0.4");
    text.appendChild(textPres);
    Element textPos = document.createElement("Position");
    Element textLoc = document.createElement("Location");
    textLoc.setAttribute("X", String.valueOf(x));
    textLoc.setAttribute("Y", String.valueOf(y + 4.0));
    textLoc.setAttribute("Z", "0");
    textPos.appendChild(textLoc);
    Element textAxis = document.createElement("Axis");
    textAxis.setAttribute("X", "0");
    textAxis.setAttribute("Y", "0");
    textAxis.setAttribute("Z", "1");
    textPos.appendChild(textAxis);
    Element textRef = document.createElement("Reference");
    textRef.setAttribute("X", "1");
    textRef.setAttribute("Y", "0");
    textRef.setAttribute("Z", "0");
    textPos.appendChild(textRef);
    text.appendChild(textPos);
    parent.appendChild(text);

    // Dashed utility line (line type 1 = dashed)
    appendStyledConnectionLine(document, parent, x, y, targetX, targetY, 1, "0.4", "0.4", "0.4");
  }

  // ==== Line ID format label (ISO 10628 / NORSOK Z-003) ====

  /**
   * Appends a formatted line identification label per ISO 10628 / NORSOK Z-003.
   *
   * <p>
   * The standard line ID format is: SIZE"-FLUID_CODE-LINE_NUMBER-PIPING_CLASS. For example:
   * 6"-HC-001-A1B. The label is placed above the pipe midpoint.
   * </p>
   *
   * @param document the XML document
   * @param parent the PipingNetworkSegment element
   * @param lineSize pipe nominal size (e.g. "6\"")
   * @param fluidCode fluid code (e.g. "HC")
   * @param lineNumber line number (e.g. "001")
   * @param pipingClass piping class code (e.g. "A1B", "150#")
   * @param fromX source X
   * @param fromY source Y
   * @param toX destination X
   * @param toY destination Y
   */
  static void appendLineIdLabel(Document document, Element parent, String lineSize,
      String fluidCode, String lineNumber, String pipingClass, double fromX, double fromY,
      double toX, double toY) {
    StringBuilder sb = new StringBuilder();
    if (lineSize != null && !lineSize.trim().isEmpty()) {
      sb.append(lineSize.trim());
    }
    if (fluidCode != null && !fluidCode.trim().isEmpty()) {
      if (sb.length() > 0) {
        sb.append("-");
      }
      sb.append(fluidCode.trim());
    }
    if (lineNumber != null && !lineNumber.trim().isEmpty()) {
      if (sb.length() > 0) {
        sb.append("-");
      }
      sb.append(lineNumber.trim());
    }
    if (pipingClass != null && !pipingClass.trim().isEmpty()) {
      if (sb.length() > 0) {
        sb.append("-");
      }
      sb.append(pipingClass.trim());
    }
    if (sb.length() == 0) {
      return;
    }

    double midX = (fromX + toX) / 2.0;
    double midY = (fromY + toY) / 2.0;
    double labelY = midY + 6.0;

    Element text = document.createElement("Text");
    text.setAttribute("String", sb.toString());
    text.setAttribute("Font", FONT_NAME);
    text.setAttribute("Height", "2.2");
    text.setAttribute("Width", "0");
    text.setAttribute("Justification", "CenterBottom");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0.501960784");
    text.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(midX));
    location.setAttribute("Y", String.valueOf(labelY));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    text.appendChild(position);
    parent.appendChild(text);
  }

  // ==== Revision history table (NORSOK Z-003) ====

  /**
   * Appends a revision history table above the title block.
   *
   * <p>
   * Per NORSOK Z-003, the P&amp;ID title block includes a revision history showing rev number,
   * date, description, drawn by, and checked by for each revision.
   * </p>
   *
   * @param document the XML document
   * @param drawing the Drawing element
   * @param revisions list of revision entries [rev, date, description, drawnBy, checkedBy]
   * @param sheetWidth the drawing sheet width
   */
  static void appendRevisionHistory(Document document, Element drawing, List<String[]> revisions,
      double sheetWidth) {
    if (revisions == null || revisions.isEmpty()) {
      return;
    }
    Element label = document.createElement("Label");
    label.setAttribute("ID", "RevisionHistory-1");
    label.setAttribute("ComponentClass", "Label");

    double blockLeft = sheetWidth - BORDER_MARGIN - TITLE_BLOCK_WIDTH;
    double blockRight = sheetWidth - BORDER_MARGIN;
    double tableBottom = BORDER_MARGIN + TITLE_BLOCK_HEIGHT;

    // Column positions
    double colRev = blockLeft;
    double colDate = blockLeft + 12.0;
    double colDesc = blockLeft + 32.0;
    double colDrawn = blockLeft + 82.0;
    double colChecked = blockLeft + 100.0;

    // Header row
    double headerBottom = tableBottom;
    double headerTop = headerBottom + REVISION_ROW_HEIGHT;
    appendRevisionCell(document, label, colRev, headerBottom, colDate, headerTop, "REV");
    appendRevisionCell(document, label, colDate, headerBottom, colDesc, headerTop, "DATE");
    appendRevisionCell(document, label, colDesc, headerBottom, colDrawn, headerTop, "DESCRIPTION");
    appendRevisionCell(document, label, colDrawn, headerBottom, colChecked, headerTop, "BY");
    appendRevisionCell(document, label, colChecked, headerBottom, blockRight, headerTop, "CHK");

    // Revision rows
    for (int i = 0; i < revisions.size(); i++) {
      String[] rev = revisions.get(i);
      double rowBottom = headerTop + i * REVISION_ROW_HEIGHT;
      double rowTop = rowBottom + REVISION_ROW_HEIGHT;
      appendRevisionCell(document, label, colRev, rowBottom, colDate, rowTop,
          rev.length > 0 ? rev[0] : "");
      appendRevisionCell(document, label, colDate, rowBottom, colDesc, rowTop,
          rev.length > 1 ? rev[1] : "");
      appendRevisionCell(document, label, colDesc, rowBottom, colDrawn, rowTop,
          rev.length > 2 ? rev[2] : "");
      appendRevisionCell(document, label, colDrawn, rowBottom, colChecked, rowTop,
          rev.length > 3 ? rev[3] : "");
      appendRevisionCell(document, label, colChecked, rowBottom, blockRight, rowTop,
          rev.length > 4 ? rev[4] : "");
    }

    drawing.appendChild(label);
  }

  /**
   * Appends a cell for the revision history table.
   *
   * @param document the XML document
   * @param parent the label element
   * @param x1 left X
   * @param y1 bottom Y
   * @param x2 right X
   * @param y2 top Y
   * @param text the cell text
   */
  private static void appendRevisionCell(Document document, Element parent, double x1, double y1,
      double x2, double y2, String text) {
    appendBorderRect(document, parent, x1, y1, x2, y2, 0.2);
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text != null ? text : "");
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", String.valueOf(LEGEND_FONT_HEIGHT));
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", "LeftBottom");
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x1 + 0.8));
    location.setAttribute("Y", String.valueOf(y1 + 1.0));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }

  // ==== Symbol legend / key box (ISO 10628 / NORSOK Z-003) ====

  /**
   * Appends a symbol legend box listing line types, valve symbols, and abbreviations.
   *
   * <p>
   * Per ISO 10628, P&amp;IDs include a legend explaining line type conventions and abbreviations.
   * The legend is placed bottom-left to avoid overlap with the stream table and title block.
   * </p>
   *
   * @param document the XML document
   * @param parent the Drawing or PlantModel element
   * @param entries list of legend entries [lineType, description]
   */
  static void appendSymbolLegend(Document document, Element parent, List<String[]> entries) {
    if (entries == null || entries.isEmpty()) {
      return;
    }
    Element label = document.createElement("Label");
    label.setAttribute("ID", "SymbolLegend-1");
    label.setAttribute("ComponentClass", "Label");

    double legendLeft = BORDER_MARGIN + 2.0;
    double legendBottom = BORDER_MARGIN + 2.0;
    int rows = entries.size() + 1; // +1 for header
    double legendTop = legendBottom + rows * LEGEND_ROW_HEIGHT;

    // Outer box
    appendBorderRect(document, label, legendLeft, legendBottom, legendLeft + LEGEND_WIDTH,
        legendTop, 0.3);

    // Header
    double headerBottom = legendTop - LEGEND_ROW_HEIGHT;
    appendBorderRect(document, label, legendLeft, headerBottom, legendLeft + LEGEND_WIDTH,
        legendTop, 0.2);
    appendLegendText(document, label, "SYMBOL LEGEND", legendLeft + LEGEND_WIDTH / 2.0,
        headerBottom + LEGEND_ROW_HEIGHT / 2.0, "CenterCenter", "2.5");

    // Entry rows
    double sampleLeft = legendLeft + 2.0;
    double sampleRight = legendLeft + 18.0;
    double descLeft = legendLeft + 20.0;

    for (int i = 0; i < entries.size(); i++) {
      String[] entry = entries.get(i);
      double rowTop = headerBottom - i * LEGEND_ROW_HEIGHT;
      double rowBottom = rowTop - LEGEND_ROW_HEIGHT;
      double rowMidY = (rowTop + rowBottom) / 2.0;

      appendBorderRect(document, label, legendLeft, rowBottom, legendLeft + LEGEND_WIDTH, rowTop,
          0.15);

      // Sample line
      int lineType = 0;
      try {
        lineType = entry.length > 2 ? Integer.parseInt(entry[2]) : 0;
      } catch (NumberFormatException ignored) {
        // Default to solid
      }
      Element sampleLine = document.createElement("PolyLine");
      sampleLine.setAttribute("NumPoints", "2");
      Element samplePres = document.createElement("Presentation");
      samplePres.setAttribute("LineType", String.valueOf(lineType));
      samplePres.setAttribute("LineWeight", String.valueOf(PROCESS_LINE_WEIGHT));
      samplePres.setAttribute("R", LINE_COLOR_R);
      samplePres.setAttribute("G", LINE_COLOR_G);
      samplePres.setAttribute("B", LINE_COLOR_B);
      sampleLine.appendChild(samplePres);
      appendCoordinate(document, sampleLine, sampleLeft, rowMidY);
      appendCoordinate(document, sampleLine, sampleRight, rowMidY);
      label.appendChild(sampleLine);

      // Description text
      String desc = entry.length > 1 ? entry[1] : entry[0];
      appendLegendText(document, label, desc, descLeft, rowMidY - 0.5, "LeftCenter",
          String.valueOf(LEGEND_FONT_HEIGHT));
    }

    parent.appendChild(label);
  }

  /**
   * Appends text for the symbol legend box.
   *
   * @param document the XML document
   * @param parent the label element
   * @param text the text string
   * @param x X position
   * @param y Y position
   * @param justification text justification
   * @param fontSize font height as string
   */
  private static void appendLegendText(Document document, Element parent, String text, double x,
      double y, String justification, String fontSize) {
    Element textElem = document.createElement("Text");
    textElem.setAttribute("String", text);
    textElem.setAttribute("Font", FONT_NAME);
    textElem.setAttribute("Height", fontSize);
    textElem.setAttribute("Width", "0");
    textElem.setAttribute("Justification", justification);
    Element pres = document.createElement("Presentation");
    pres.setAttribute("R", "0");
    pres.setAttribute("G", "0");
    pres.setAttribute("B", "0");
    textElem.appendChild(pres);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(x));
    location.setAttribute("Y", String.valueOf(y));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    Element axis = document.createElement("Axis");
    axis.setAttribute("X", "0");
    axis.setAttribute("Y", "0");
    axis.setAttribute("Z", "1");
    position.appendChild(axis);
    Element ref = document.createElement("Reference");
    ref.setAttribute("X", "1");
    ref.setAttribute("Y", "0");
    ref.setAttribute("Z", "0");
    position.appendChild(ref);
    textElem.appendChild(position);
    parent.appendChild(textElem);
  }
}
