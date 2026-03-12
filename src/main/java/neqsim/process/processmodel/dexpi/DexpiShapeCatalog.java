package neqsim.process.processmodel.dexpi;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Generates the DEXPI {@code <ShapeCatalogue>} section with ISO 10628:2012 standard P&amp;ID
 * symbols for process equipment.
 *
 * <p>
 * Each shape is drawn using DEXPI graphical primitives (Circle, PolyLine, TrimmedCurve) in a local
 * coordinate system centered at (0,0). Equipment elements reference these shapes via the
 * {@code ComponentName} attribute. The {@code SymbolRegistrationNumberAssignmentClass} generic
 * attribute records the ISO 10628 registration number for each shape.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class DexpiShapeCatalog {

  /** Shape name for vertical separators / vessels with dished heads. */
  static final String SEPARATOR_SHAPE = "VERTICAL_SEPARATOR_SHAPE";
  /** Shape name for three-phase separators. */
  static final String THREE_PHASE_SEPARATOR_SHAPE = "THREE_PHASE_SEPARATOR_SHAPE";
  /** Shape name for centrifugal compressors. */
  static final String COMPRESSOR_SHAPE = "CENTRIFUGAL_COMPRESSOR_SHAPE";
  /** Shape name for centrifugal pumps. */
  static final String PUMP_SHAPE = "CENTRIFUGAL_PUMP_SHAPE";
  /** Shape name for air coolers. */
  static final String COOLER_SHAPE = "AIR_COOLED_HEAT_EXCHANGER_SHAPE";
  /** Shape name for fired heaters. */
  static final String HEATER_SHAPE = "FIRED_HEATER_SHAPE";
  /** Shape name for shell-and-tube heat exchangers. */
  static final String HEAT_EXCHANGER_SHAPE = "SHELL_AND_TUBE_HEAT_EXCHANGER_SHAPE";
  /** Shape name for globe valves. */
  static final String GLOBE_VALVE_SHAPE = "GLOBE_VALVE_SHAPE";
  /** Shape name for gate valves (ISO 10628:2012-X8062-A). */
  static final String GATE_VALVE_SHAPE = "GATE_VALVE_SHAPE";
  /** Shape name for ball valves (ISO 10628:2012-X8038-A). */
  static final String BALL_VALVE_SHAPE = "BALL_VALVE_SHAPE";
  /** Shape name for check/non-return valves (ISO 10628:2012-X8072-A). */
  static final String CHECK_VALVE_SHAPE = "CHECK_VALVE_SHAPE";
  /** Shape name for butterfly valves (ISO 10628:2012-X8042-A). */
  static final String BUTTERFLY_VALVE_SHAPE = "BUTTERFLY_VALVE_SHAPE";
  /** Shape name for expanders / turbines. */
  static final String EXPANDER_SHAPE = "EXPANDER_TURBINE_SHAPE";
  /** Shape name for mixers. */
  static final String MIXER_SHAPE = "MIXER_SHAPE";
  /** Shape name for splitters. */
  static final String SPLITTER_SHAPE = "SPLITTER_SHAPE";
  /** Shape name for nozzles. */
  static final String NOZZLE_SHAPE = "NOZZLE_SHAPE";
  /** Shape name for generic equipment. */
  static final String GENERIC_EQUIPMENT_SHAPE = "GENERIC_EQUIPMENT_SHAPE";
  /** Shape name for distillation/tray columns. */
  static final String DISTILLATION_COLUMN_SHAPE = "DISTILLATION_COLUMN_SHAPE";
  /** Shape name for safety/relief valves (ISO 10628:2012-X8088-A). */
  static final String RELIEF_VALVE_SHAPE = "RELIEF_VALVE_SHAPE";
  /** Shape name for solenoid valve actuator symbol (ISA 5.1). */
  static final String SOLENOID_SHAPE = "SOLENOID_VALVE_SHAPE";
  /** Shape name for utility connection point (instrument air, steam, etc.). */
  static final String UTILITY_SUPPLY_SHAPE = "UTILITY_SUPPLY_SHAPE";
  /** Shape name for field-mounted instrument bubble (ISA 5.1). */
  static final String INSTRUMENT_BUBBLE_FIELD_SHAPE = "INSTRUMENTATION_BUBBLE_SHAPE_FIELD";
  /** Shape name for central/panel-mounted instrument bubble (ISA 5.1). */
  static final String INSTRUMENT_BUBBLE_CENTRAL_SHAPE = "INSTRUMENTATION_BUBBLE_SHAPE_CENTRAL";

  /** Standard line weight for instrument outlines. */
  private static final String INSTRUMENT_LINE_WEIGHT = "0.2";
  /** Instrument outline colour R (green). */
  private static final String INSTRUMENT_COLOR_R = "0";
  /** Instrument outline colour G (green). */
  private static final String INSTRUMENT_COLOR_G = "0.501960784";
  /** Instrument outline colour B. */
  private static final String INSTRUMENT_COLOR_B = "0";

  /** Standard line weight for equipment outlines. */
  private static final String LINE_WEIGHT = "0.3";
  /** Standard line color R component (dark red per DEXPI convention). */
  private static final String COLOR_R = "0.501960784";
  /** Standard line color G component. */
  private static final String COLOR_G = "0";
  /** Standard line color B component. */
  private static final String COLOR_B = "0";

  private DexpiShapeCatalog() {}

  /**
   * Appends a complete {@code <ShapeCatalogue>} element to the given parent.
   *
   * @param document the XML document
   * @param parent the parent element (PlantModel root)
   */
  static void appendShapeCatalogue(Document document, Element parent) {
    Element catalogue = document.createElement("ShapeCatalogue");
    catalogue.setAttribute("Name", "Shapes");

    appendSeparatorShape(document, catalogue);
    appendThreePhaseSeparatorShape(document, catalogue);
    appendCompressorShape(document, catalogue);
    appendPumpShape(document, catalogue);
    appendCoolerShape(document, catalogue);
    appendHeaterShape(document, catalogue);
    appendHeatExchangerShape(document, catalogue);
    appendGlobeValveShape(document, catalogue);
    appendGateValveShape(document, catalogue);
    appendBallValveShape(document, catalogue);
    appendCheckValveShape(document, catalogue);
    appendButterflyValveShape(document, catalogue);
    appendExpanderShape(document, catalogue);
    appendMixerShape(document, catalogue);
    appendSplitterShape(document, catalogue);
    appendNozzleShape(document, catalogue);
    appendDistillationColumnShape(document, catalogue);
    appendReliefValveShape(document, catalogue);
    appendSolenoidShape(document, catalogue);
    appendUtilitySupplyShape(document, catalogue);
    appendGenericEquipmentShape(document, catalogue);
    appendInstrumentBubbleFieldShape(document, catalogue);
    appendInstrumentBubbleCentralShape(document, catalogue);

    parent.appendChild(catalogue);
  }

  /**
   * Returns the DEXPI ComponentName for a given NeqSim DEXPI component class.
   *
   * @param componentClass the DEXPI ComponentClass string
   * @return the shape ComponentName, or null if no specific shape is defined
   */
  static String getShapeName(String componentClass) {
    if (componentClass == null) {
      return GENERIC_EQUIPMENT_SHAPE;
    }
    switch (componentClass) {
      case "Separator":
        return SEPARATOR_SHAPE;
      case "ThreePhaseSeparator":
        return THREE_PHASE_SEPARATOR_SHAPE;
      case "CentrifugalCompressor":
        return COMPRESSOR_SHAPE;
      case "CentrifugalPump":
        return PUMP_SHAPE;
      case "AirCoolingSystem":
        return COOLER_SHAPE;
      case "FiredHeater":
        return HEATER_SHAPE;
      case "ShellAndTubeHeatExchanger":
        return HEAT_EXCHANGER_SHAPE;
      case "GlobeValve":
        return GLOBE_VALVE_SHAPE;
      case "GateValve":
        return GATE_VALVE_SHAPE;
      case "BallValve":
        return BALL_VALVE_SHAPE;
      case "CheckValve":
        return CHECK_VALVE_SHAPE;
      case "ButterflyValve":
        return BUTTERFLY_VALVE_SHAPE;
      case "Expander":
        return EXPANDER_SHAPE;
      case "DistillationColumn":
        return DISTILLATION_COLUMN_SHAPE;
      case "Mixer":
        return MIXER_SHAPE;
      case "Splitter":
        return SPLITTER_SHAPE;
      default:
        return GENERIC_EQUIPMENT_SHAPE;
    }
  }

  // --- Vertical separator / vessel with dished heads (ISO 10628:2012-2062-A) ---

  private static void appendSeparatorShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Sep",
        SEPARATOR_SHAPE, "ISO10628:2012-2062-A");
    // Vertical vessel: two straight sides + two dished head arcs
    appendPolyLine(document, shape, new double[][] {{10, -12.5}, {10, 12.5}});
    appendTrimmedCurve(document, shape, 61.93, 118.07, 21.25, 0, -6.25);
    appendPolyLine(document, shape, new double[][] {{-10, 12.5}, {-10, -12.5}});
    appendTrimmedCurve(document, shape, 241.93, 298.07, 21.25, 0, 6.25);
    catalogue.appendChild(shape);
  }

  // --- Three-phase separator (vessel with internal divider) ---

  private static void appendThreePhaseSeparatorShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-3PSep",
        THREE_PHASE_SEPARATOR_SHAPE, "ISO10628:2012-2062-A");
    // Same vessel outline as separator
    appendPolyLine(document, shape, new double[][] {{10, -12.5}, {10, 12.5}});
    appendTrimmedCurve(document, shape, 61.93, 118.07, 21.25, 0, -6.25);
    appendPolyLine(document, shape, new double[][] {{-10, 12.5}, {-10, -12.5}});
    appendTrimmedCurve(document, shape, 241.93, 298.07, 21.25, 0, 6.25);
    // Internal weir / divider line
    appendPolyLine(document, shape, new double[][] {{0, -8}, {0, 8}});
    catalogue.appendChild(shape);
  }

  // --- Centrifugal compressor (ISO 10628:2012-2332-A: circle + triangle) ---

  private static void appendCompressorShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Comp",
        COMPRESSOR_SHAPE, "ISO10628:2012-2332-A");
    appendCircle(document, shape, 7.5, 0, 0, false);
    // Horizontal diameter line
    appendPolyLine(document, shape, new double[][] {{-7.5, 0}, {7.5, 0}});
    // Arrow triangle pointing right (discharge direction)
    appendPolyLine(document, shape, new double[][] {{0, -7.5}, {7.5, 0}, {0, 7.5}});
    catalogue.appendChild(shape);
  }

  // --- Centrifugal pump (ISO 10628:2012-2322-A: circle + triangle) ---

  private static void appendPumpShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Pump",
        PUMP_SHAPE, "ISO10628:2012-2322-A");
    appendCircle(document, shape, 7.5, 0, 0, false);
    appendPolyLine(document, shape, new double[][] {{-7.5, 0}, {7.5, 0}});
    appendPolyLine(document, shape, new double[][] {{0, -7.5}, {7.5, 0}, {0, 7.5}});
    catalogue.appendChild(shape);
  }

  // --- Air-cooled heat exchanger (ISO 10628:2012-2514-A: rectangle + fan) ---

  private static void appendCoolerShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Cooler",
        COOLER_SHAPE, "ISO10628:2012-2514-A");
    // Rectangle body
    appendPolyLine(document, shape,
        new double[][] {{-15, 5}, {15, 5}, {15, -5}, {-15, -5}, {-15, 5}});
    // Fan symbol: X across the top
    appendPolyLine(document, shape, new double[][] {{-10, 5}, {-10, 10}});
    appendPolyLine(document, shape, new double[][] {{10, 5}, {10, 10}});
    appendPolyLine(document, shape, new double[][] {{-10, 10}, {10, 10}});
    // Fan blades
    appendPolyLine(document, shape, new double[][] {{-5, 10}, {0, 13}});
    appendPolyLine(document, shape, new double[][] {{5, 10}, {0, 13}});
    catalogue.appendChild(shape);
  }

  // --- Fired heater (ISO 10628:2012-2502-A: rectangle with flame) ---

  private static void appendHeaterShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Heater",
        HEATER_SHAPE, "ISO10628:2012-2502-A");
    // Rectangle
    appendPolyLine(document, shape,
        new double[][] {{-12, 8}, {12, 8}, {12, -8}, {-12, -8}, {-12, 8}});
    // Flame / heat symbol (zigzag)
    appendPolyLine(document, shape, new double[][] {{-6, -8}, {-3, -3}, {0, -8}, {3, -3}, {6, -8}});
    catalogue.appendChild(shape);
  }

  // --- Shell-and-tube heat exchanger (ISO 10628:2012-2512-A) ---

  private static void appendHeatExchangerShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-HX",
        HEAT_EXCHANGER_SHAPE, "ISO10628:2012-2512-A");
    // Outer rectangle (shell)
    appendPolyLine(document, shape,
        new double[][] {{-17.5, 5}, {17.5, 5}, {17.5, -5}, {-17.5, -5}, {-17.5, 5}});
    // Channel partition
    appendPolyLine(document, shape, new double[][] {{-12.5, 5}, {-12.5, -5}});
    appendPolyLine(document, shape, new double[][] {{-17.5, 0}, {-12.5, 0}});
    // Tube bundle lines
    appendPolyLine(document, shape, new double[][] {{-12.5, 2.5}, {10, 2.5}});
    appendPolyLine(document, shape, new double[][] {{-12.5, -2.5}, {10, -2.5}});
    // Floating head
    appendPolyLine(document, shape,
        new double[][] {{10, 3.75}, {12.5, 3.75}, {12.5, -3.75}, {10, -3.75}, {10, 3.75}});
    catalogue.appendChild(shape);
  }

  // --- Globe valve (ISO 10628:2012-X8068-A: bowtie + filled center dot) ---

  private static void appendGlobeValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-GV",
        GLOBE_VALVE_SHAPE, "ISO10628:2012-X8068-A");
    // Bowtie
    appendPolyLine(document, shape,
        new double[][] {{-5, 2.5}, {5, -2.5}, {5, 2.5}, {-5, -2.5}, {-5, 2.5}});
    appendCircle(document, shape, 1.25, 0, 0, true);
    catalogue.appendChild(shape);
  }

  // --- Gate valve (ISO 10628:2012-X8062-A: bowtie without center mark) ---

  private static void appendGateValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-GtV",
        GATE_VALVE_SHAPE, "ISO10628:2012-X8062-A");
    // Bowtie only (no center dot distinguishes gate from globe)
    appendPolyLine(document, shape,
        new double[][] {{-5, 2.5}, {5, -2.5}, {5, 2.5}, {-5, -2.5}, {-5, 2.5}});
    catalogue.appendChild(shape);
  }

  // --- Ball valve (ISO 10628:2012-X8038-A: bowtie with center line) ---

  private static void appendBallValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-BV",
        BALL_VALVE_SHAPE, "ISO10628:2012-X8038-A");
    // Bowtie
    appendPolyLine(document, shape,
        new double[][] {{-5, 2.5}, {5, -2.5}, {5, 2.5}, {-5, -2.5}, {-5, 2.5}});
    // Center stem line (distinguishes ball valve)
    appendPolyLine(document, shape, new double[][] {{0, -2.5}, {0, 2.5}});
    catalogue.appendChild(shape);
  }

  // --- Check / non-return valve (ISO 10628:2012-X8072-A: triangle + bar) ---

  private static void appendCheckValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-CV",
        CHECK_VALVE_SHAPE, "ISO10628:2012-X8072-A");
    // Triangle pointing in flow direction (right)
    appendPolyLine(document, shape, new double[][] {{-5, 2.5}, {5, 0}, {-5, -2.5}, {-5, 2.5}});
    // Vertical bar at outlet side (prevents backflow)
    appendPolyLine(document, shape, new double[][] {{5, 2.5}, {5, -2.5}});
    catalogue.appendChild(shape);
  }

  // --- Butterfly valve (ISO 10628:2012-X8042-A: bowtie with open circle) ---

  private static void appendButterflyValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-BtV",
        BUTTERFLY_VALVE_SHAPE, "ISO10628:2012-X8042-A");
    // Bowtie
    appendPolyLine(document, shape,
        new double[][] {{-5, 2.5}, {5, -2.5}, {5, 2.5}, {-5, -2.5}, {-5, 2.5}});
    // Open circle in center (disc indicator)
    appendCircle(document, shape, 1.25, 0, 0, false);
    catalogue.appendChild(shape);
  }

  // --- Expander / turbine (ISO 10628:2012-2342: opposing triangles) ---

  private static void appendExpanderShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Exp",
        EXPANDER_SHAPE, "ISO10628:2012-2342-A");
    // Expanding trapezoid shape
    appendPolyLine(document, shape,
        new double[][] {{-7.5, 3}, {7.5, 7.5}, {7.5, -7.5}, {-7.5, -3}, {-7.5, 3}});
    catalogue.appendChild(shape);
  }

  // --- Mixer (junction/tee shape) ---

  private static void appendMixerShape(Document document, Element catalogue) {
    Element shape =
        createShapeElement(document, "Equipment", "TaggedPlantItemShape-Mix", MIXER_SHAPE, "");
    // Converging triangle (two inlets to one outlet)
    appendPolyLine(document, shape, new double[][] {{-7.5, 5}, {7.5, 0}, {-7.5, -5}, {-7.5, 5}});
    catalogue.appendChild(shape);
  }

  // --- Splitter (diverging junction) ---

  private static void appendSplitterShape(Document document, Element catalogue) {
    Element shape =
        createShapeElement(document, "Equipment", "TaggedPlantItemShape-Spl", SPLITTER_SHAPE, "");
    // Diverging triangle (one inlet to two outlets)
    appendPolyLine(document, shape, new double[][] {{-7.5, 0}, {7.5, 5}, {7.5, -5}, {-7.5, 0}});
    catalogue.appendChild(shape);
  }

  // --- Nozzle (ISO 10628:2012-X8160-A) ---

  private static void appendNozzleShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Nozzle", "NozzleShape-1", NOZZLE_SHAPE,
        "ISO10628:2012-X8160-A");
    appendPolyLine(document, shape, new double[][] {{5, 2.5}, {5, -2.5}});
    appendPolyLine(document, shape, new double[][] {{5, 0}, {0, 0}});
    catalogue.appendChild(shape);
  }

  // --- Distillation / tray column (ISO 10628:2012-2092-A: tall vessel with tray lines) ---

  private static void appendDistillationColumnShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Col",
        DISTILLATION_COLUMN_SHAPE, "ISO10628:2012-2092-A");
    // Tall vertical vessel outline (taller than separator, aspect ~3:1)
    appendPolyLine(document, shape, new double[][] {{10, -20}, {10, 20}});
    appendTrimmedCurve(document, shape, 61.93, 118.07, 21.25, 0, -13.75);
    appendPolyLine(document, shape, new double[][] {{-10, 20}, {-10, -20}});
    appendTrimmedCurve(document, shape, 241.93, 298.07, 21.25, 0, 13.75);
    // Internal tray lines (horizontal lines representing trays)
    appendPolyLine(document, shape, new double[][] {{-8, 12}, {8, 12}});
    appendPolyLine(document, shape, new double[][] {{-8, 6}, {8, 6}});
    appendPolyLine(document, shape, new double[][] {{-8, 0}, {8, 0}});
    appendPolyLine(document, shape, new double[][] {{-8, -6}, {8, -6}});
    appendPolyLine(document, shape, new double[][] {{-8, -12}, {8, -12}});
    catalogue.appendChild(shape);
  }

  // --- Safety / relief valve (ISO 10628:2012-X8088-A: angle body with spring) ---

  private static void appendReliefValveShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-RV",
        RELIEF_VALVE_SHAPE, "ISO10628:2012-X8088-A");
    // Valve body (triangle pointing up = discharge direction)
    appendPolyLine(document, shape, new double[][] {{-5, -2.5}, {0, 2.5}, {5, -2.5}, {-5, -2.5}});
    // Spring / bonnet line on top
    appendPolyLine(document, shape, new double[][] {{0, 2.5}, {0, 5}});
    // Arrow head (set pressure indicator)
    appendPolyLine(document, shape, new double[][] {{-1.5, 4}, {0, 5}, {1.5, 4}});
    catalogue.appendChild(shape);
  }

  // --- Solenoid valve actuator (ISA 5.1: diamond with 'S') ---

  private static void appendSolenoidShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "PipingComponent", "PipingComponentShape-SOV",
        SOLENOID_SHAPE, "");
    // Diamond outline representing solenoid actuator
    appendPolyLine(document, shape, new double[][] {{0, 3}, {3, 0}, {0, -3}, {-3, 0}, {0, 3}});
    // Vertical line inside diamond (coil symbol)
    appendPolyLine(document, shape, new double[][] {{0, -1.5}, {0, 1.5}});
    catalogue.appendChild(shape);
  }

  // --- Utility supply connection point (ISO 10628:2012: circle with arrow) ---

  private static void appendUtilitySupplyShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Util",
        UTILITY_SUPPLY_SHAPE, "");
    // Small circle with incoming arrow representing utility connection
    appendCircle(document, shape, 3.0, 0, 0, false);
    // Arrow entering from left
    appendPolyLine(document, shape, new double[][] {{-7, 0}, {-3, 0}});
    appendPolyLine(document, shape, new double[][] {{-5, 1.5}, {-3, 0}, {-5, -1.5}});
    catalogue.appendChild(shape);
  }

  // --- Generic equipment (simple rectangle) ---

  private static void appendGenericEquipmentShape(Document document, Element catalogue) {
    Element shape = createShapeElement(document, "Equipment", "TaggedPlantItemShape-Gen",
        GENERIC_EQUIPMENT_SHAPE, "");
    appendPolyLine(document, shape,
        new double[][] {{-10, 7.5}, {10, 7.5}, {10, -7.5}, {-10, -7.5}, {-10, 7.5}});
    catalogue.appendChild(shape);
  }

  // --- Instrument bubble (ISA 5.1, field-mounted: stadium/pill shape, no centre line) ---

  private static void appendInstrumentBubbleFieldShape(Document document, Element catalogue) {
    Element shape = document.createElement("ProcessInstrumentationFunction");
    shape.setAttribute("ID", "ProcessInstrumentationFunctionShape-Field");
    shape.setAttribute("ComponentName", INSTRUMENT_BUBBLE_FIELD_SHAPE);

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    genericAttributes.setAttribute("Number", "1");
    Element attr = document.createElement("GenericAttribute");
    attr.setAttribute("Name", "SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("AttributeURI",
        "http://sandbox.dexpi.org/rdl/SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("Format", "string");
    genericAttributes.appendChild(attr);
    shape.appendChild(genericAttributes);

    // Top horizontal line
    appendInstrumentPolyLine(document, shape, new double[][] {{-3, 3.75}, {3, 3.75}});
    // Left semicircle (90-270)
    appendInstrumentTrimmedCurve(document, shape, 90, 270, 3.75, -3, 0);
    // Bottom horizontal line
    appendInstrumentPolyLine(document, shape, new double[][] {{-3, -3.75}, {3, -3.75}});
    // Right semicircle (270-90)
    appendInstrumentTrimmedCurve(document, shape, 270, 90, 3.75, 3, 0);

    catalogue.appendChild(shape);
  }

  // --- Instrument bubble (ISA 5.1, central/panel-mounted: stadium with centre line) ---

  private static void appendInstrumentBubbleCentralShape(Document document, Element catalogue) {
    Element shape = document.createElement("ProcessInstrumentationFunction");
    shape.setAttribute("ID", "ProcessInstrumentationFunctionShape-Central");
    shape.setAttribute("ComponentName", INSTRUMENT_BUBBLE_CENTRAL_SHAPE);

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    genericAttributes.setAttribute("Number", "1");
    Element attr = document.createElement("GenericAttribute");
    attr.setAttribute("Name", "SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("AttributeURI",
        "http://sandbox.dexpi.org/rdl/SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("Format", "string");
    genericAttributes.appendChild(attr);
    shape.appendChild(genericAttributes);

    // Top horizontal line
    appendInstrumentPolyLine(document, shape, new double[][] {{-3, 3.75}, {3, 3.75}});
    // Left semicircle (90-270)
    appendInstrumentTrimmedCurve(document, shape, 90, 270, 3.75, -3, 0);
    // Bottom horizontal line
    appendInstrumentPolyLine(document, shape, new double[][] {{-3, -3.75}, {3, -3.75}});
    // Right semicircle (270-90)
    appendInstrumentTrimmedCurve(document, shape, 270, 90, 3.75, 3, 0);
    // Centre divider line (distinguishes central from field)
    appendInstrumentPolyLine(document, shape, new double[][] {{-6.75, 0}, {6.75, 0}});

    catalogue.appendChild(shape);
  }

  /**
   * Appends a PolyLine with instrument presentation (green, 0.2 weight).
   *
   * @param document the XML document
   * @param parent the parent element
   * @param coords array of [x, y] coordinate pairs
   */
  private static void appendInstrumentPolyLine(Document document, Element parent,
      double[][] coords) {
    Element polyLine = document.createElement("PolyLine");
    polyLine.setAttribute("NumPoints", String.valueOf(coords.length));
    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", INSTRUMENT_LINE_WEIGHT);
    presentation.setAttribute("R", INSTRUMENT_COLOR_R);
    presentation.setAttribute("G", INSTRUMENT_COLOR_G);
    presentation.setAttribute("B", INSTRUMENT_COLOR_B);
    polyLine.appendChild(presentation);
    for (double[] coord : coords) {
      Element coordinate = document.createElement("Coordinate");
      coordinate.setAttribute("X", String.valueOf(coord[0]));
      coordinate.setAttribute("Y", String.valueOf(coord[1]));
      polyLine.appendChild(coordinate);
    }
    parent.appendChild(polyLine);
  }

  /**
   * Appends a TrimmedCurve (arc) with instrument presentation (green, 0.2 weight).
   *
   * @param document the XML document
   * @param parent the parent element
   * @param startAngle arc start angle in degrees
   * @param endAngle arc end angle in degrees
   * @param radius the circle radius
   * @param cx center X
   * @param cy center Y
   */
  private static void appendInstrumentTrimmedCurve(Document document, Element parent,
      double startAngle, double endAngle, double radius, double cx, double cy) {
    Element trimmedCurve = document.createElement("TrimmedCurve");
    trimmedCurve.setAttribute("StartAngle", String.valueOf(startAngle));
    trimmedCurve.setAttribute("EndAngle", String.valueOf(endAngle));
    Element circle = document.createElement("Circle");
    circle.setAttribute("Radius", String.valueOf(radius));
    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", INSTRUMENT_LINE_WEIGHT);
    presentation.setAttribute("R", INSTRUMENT_COLOR_R);
    presentation.setAttribute("G", INSTRUMENT_COLOR_G);
    presentation.setAttribute("B", INSTRUMENT_COLOR_B);
    circle.appendChild(presentation);
    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(cx));
    location.setAttribute("Y", String.valueOf(cy));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    appendAxisAndReference(document, position);
    circle.appendChild(position);
    trimmedCurve.appendChild(circle);
    parent.appendChild(trimmedCurve);
  }

  // ========================= Helper methods =========================

  /**
   * Creates a shape element with ComponentName and ISO registration number.
   *
   * @param document the XML document
   * @param tagName the XML element tag (Equipment, PipingComponent, Nozzle)
   * @param id the shape element ID
   * @param componentName the ComponentName attribute
   * @param isoNumber the ISO 10628 registration number (may be empty)
   * @return the created element
   */
  private static Element createShapeElement(Document document, String tagName, String id,
      String componentName, String isoNumber) {
    Element shape = document.createElement(tagName);
    shape.setAttribute("ID", id);
    shape.setAttribute("ComponentName", componentName);

    Element genericAttributes = document.createElement("GenericAttributes");
    genericAttributes.setAttribute("Set", "DexpiAttributes");
    genericAttributes.setAttribute("Number", "1");
    Element attr = document.createElement("GenericAttribute");
    attr.setAttribute("Name", "SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("AttributeURI",
        "http://sandbox.dexpi.org/rdl/SymbolRegistrationNumberAssignmentClass");
    attr.setAttribute("Format", "string");
    attr.setAttribute("Value", isoNumber);
    genericAttributes.appendChild(attr);
    shape.appendChild(genericAttributes);

    return shape;
  }

  /**
   * Appends a PolyLine element with the given coordinate pairs.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param coords array of [x, y] coordinate pairs
   */
  private static void appendPolyLine(Document document, Element parent, double[][] coords) {
    Element polyLine = document.createElement("PolyLine");
    polyLine.setAttribute("NumPoints", String.valueOf(coords.length));

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", LINE_WEIGHT);
    presentation.setAttribute("R", COLOR_R);
    presentation.setAttribute("G", COLOR_G);
    presentation.setAttribute("B", COLOR_B);
    polyLine.appendChild(presentation);

    for (double[] coord : coords) {
      Element coordinate = document.createElement("Coordinate");
      coordinate.setAttribute("X", String.valueOf(coord[0]));
      coordinate.setAttribute("Y", String.valueOf(coord[1]));
      polyLine.appendChild(coordinate);
    }
    parent.appendChild(polyLine);
  }

  /**
   * Appends a Circle element.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param radius the circle radius
   * @param cx center X coordinate
   * @param cy center Y coordinate
   * @param filled whether the circle is filled solid
   */
  private static void appendCircle(Document document, Element parent, double radius, double cx,
      double cy, boolean filled) {
    Element circle = document.createElement("Circle");
    circle.setAttribute("Radius", String.valueOf(radius));
    if (filled) {
      circle.setAttribute("Filled", "Solid");
    }

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", LINE_WEIGHT);
    presentation.setAttribute("R", COLOR_R);
    presentation.setAttribute("G", COLOR_G);
    presentation.setAttribute("B", COLOR_B);
    circle.appendChild(presentation);

    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(cx));
    location.setAttribute("Y", String.valueOf(cy));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    appendAxisAndReference(document, position);
    circle.appendChild(position);

    parent.appendChild(circle);
  }

  /**
   * Appends a TrimmedCurve (arc) element.
   *
   * @param document the XML document
   * @param parent the parent element
   * @param startAngle arc start angle in degrees
   * @param endAngle arc end angle in degrees
   * @param radius the circle radius
   * @param cx center X coordinate
   * @param cy center Y coordinate
   */
  private static void appendTrimmedCurve(Document document, Element parent, double startAngle,
      double endAngle, double radius, double cx, double cy) {
    Element trimmedCurve = document.createElement("TrimmedCurve");
    trimmedCurve.setAttribute("StartAngle", String.valueOf(startAngle));
    trimmedCurve.setAttribute("EndAngle", String.valueOf(endAngle));

    Element circle = document.createElement("Circle");
    circle.setAttribute("Radius", String.valueOf(radius));

    Element presentation = document.createElement("Presentation");
    presentation.setAttribute("LineType", "0");
    presentation.setAttribute("LineWeight", LINE_WEIGHT);
    presentation.setAttribute("R", COLOR_R);
    presentation.setAttribute("G", COLOR_G);
    presentation.setAttribute("B", COLOR_B);
    circle.appendChild(presentation);

    Element position = document.createElement("Position");
    Element location = document.createElement("Location");
    location.setAttribute("X", String.valueOf(cx));
    location.setAttribute("Y", String.valueOf(cy));
    location.setAttribute("Z", "0");
    position.appendChild(location);
    appendAxisAndReference(document, position);
    circle.appendChild(position);

    trimmedCurve.appendChild(circle);
    parent.appendChild(trimmedCurve);
  }

  /**
   * Appends standard Axis and Reference elements to a Position element.
   *
   * @param document the XML document
   * @param position the Position element
   */
  private static void appendAxisAndReference(Document document, Element position) {
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
  }
}
