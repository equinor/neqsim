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
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
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
  private static final double INSTRUMENT_OFFSET_Y = 45.0;
  /** Horizontal spacing between instrument bubbles on the same equipment. */
  private static final double INSTRUMENT_X_SPACING = 15.0;
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

    // Build edges
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof Stream || unit instanceof DexpiStream) {
        continue;
      }
      if (unit instanceof TwoPortEquipment) {
        StreamInterface inletStream = ((TwoPortEquipment) unit).getInletStream();
        if (inletStream != null) {
          String upstream = outletStreamToEquipment.get(System.identityHashCode(inletStream));
          if (upstream != null && adjacency.containsKey(upstream)) {
            adjacency.get(upstream).add(unit.getName());
          }
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

    // Assign positions: each unit gets its own column, all on Y_BASE (horizontal alignment).
    // Only liquid/water branch equipment is shifted downward for visual separation.
    int col = 0;
    for (String name : topoOrder) {
      double x = X_START + col * X_SPACING;
      double y = Y_BASE;
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

    Element reference = document.createElement("Reference");
    reference.setAttribute("X", "1");
    reference.setAttribute("Y", "0");
    reference.setAttribute("Z", "0");
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

    /**
     * Creates a new equipment position.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param scaleX x scale factor
     * @param scaleY y scale factor
     */
    EquipmentPosition(double x, double y, double scaleX, double scaleY) {
      this.x = x;
      this.y = y;
      this.scaleX = scaleX;
      this.scaleY = scaleY;
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
      return String.valueOf((long) Math.round(value));
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
}
