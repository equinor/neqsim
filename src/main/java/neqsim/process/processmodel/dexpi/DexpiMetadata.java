package neqsim.process.processmodel.dexpi;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared constants describing the recommended DEXPI metadata handled by the reader and writer.
 */
public final class DexpiMetadata {
  private DexpiMetadata() {}

  /** Generic attribute containing the tag name of an equipment item. */
  public static final String TAG_NAME = "TagNameAssignmentClass";

  /** Generic attribute containing a line number reference. */
  public static final String LINE_NUMBER = "LineNumberAssignmentClass";

  /** Generic attribute containing a fluid code reference. */
  public static final String FLUID_CODE = "FluidCodeAssignmentClass";

  /** Generic attribute containing the segment number of a piping network segment. */
  public static final String SEGMENT_NUMBER = "SegmentNumberAssignmentClass";

  /** Generic attribute containing the operating pressure value of a segment. */
  public static final String OPERATING_PRESSURE_VALUE = "OperatingPressureValue";

  /** Generic attribute containing the unit of the operating pressure value of a segment. */
  public static final String OPERATING_PRESSURE_UNIT = "OperatingPressureUnit";

  /** Generic attribute containing the operating temperature value of a segment. */
  public static final String OPERATING_TEMPERATURE_VALUE = "OperatingTemperatureValue";

  /** Generic attribute containing the unit of the operating temperature value of a segment. */
  public static final String OPERATING_TEMPERATURE_UNIT = "OperatingTemperatureUnit";

  /** Generic attribute containing the operating flow value of a segment. */
  public static final String OPERATING_FLOW_VALUE = "OperatingFlowValue";

  /** Generic attribute containing the unit of the operating flow value of a segment. */
  public static final String OPERATING_FLOW_UNIT = "OperatingFlowUnit";

  // ---- Instrumentation attributes (DEXPI P&ID) ----

  /**
   * Generic attribute for the ISA-5.1 category letter (e.g. "P" for pressure, "L" for level).
   */
  public static final String INSTRUMENTATION_CATEGORY =
      "ProcessInstrumentationFunctionCategoryAssignmentClass";

  /**
   * Generic attribute for the ISA-5.1 function letters (e.g. "IC" for indicating controller, "T"
   * for transmitter).
   */
  public static final String INSTRUMENTATION_FUNCTIONS =
      "ProcessInstrumentationFunctionsAssignmentClass";

  /** Generic attribute for the instrumentation function number (e.g. "4712.02"). */
  public static final String INSTRUMENTATION_NUMBER =
      "ProcessInstrumentationFunctionNumberAssignmentClass";

  /** Generic attribute for the instrumentation loop number. */
  public static final String LOOP_NUMBER = "InstrumentationLoopFunctionNumberAssignmentClass";

  /** Generic attribute for a process signal generating function number (sensor tag). */
  public static final String SIGNAL_GENERATING_NUMBER =
      "ProcessSignalGeneratingFunctionNumberAssignmentClass";

  /** Generic attribute for an actuating function number (e.g. "PV4712.02"). */
  public static final String ACTUATING_FUNCTION_NUMBER = "ActuatingFunctionNumberAssignmentClass";

  // ---- Equipment sizing attributes (DEXPI GenericAttributes) ----

  /** Generic attribute for equipment inside diameter (metres). */
  public static final String INSIDE_DIAMETER = "InsideDiameter";

  /** Generic attribute for equipment nominal diameter (e.g. "DN 80"). */
  public static final String NOMINAL_DIAMETER = "NominalDiameter";

  /** Generic attribute for tangent-to-tangent length of vessels (metres). */
  public static final String TANGENT_TO_TANGENT_LENGTH = "TangentToTangentLength";

  /** Generic attribute for equipment design pressure (bara). */
  public static final String DESIGN_PRESSURE = "DesignPressure";

  /** Generic attribute for equipment design temperature (C). */
  public static final String DESIGN_TEMPERATURE = "DesignTemperature";

  /** Generic attribute for vessel orientation (Horizontal or Vertical). */
  public static final String ORIENTATION = "Orientation";

  /** Generic attribute for valve flow coefficient (Cv). */
  public static final String VALVE_CV = "Cv";

  /** Generic attribute for wall thickness (metres). */
  public static final String WALL_THICKNESS = "WallThickness";

  /** Generic attribute for equipment weight (kg). */
  public static final String WEIGHT = "Weight";

  /** Generic attribute for the piping class code (e.g. "2500#"). */
  public static final String PIPING_CLASS_CODE = "PipingClassCode";

  /** Generic attribute for the number of trays in a distillation column. */
  public static final String NUMBER_OF_TRAYS = "NumberOfTrays";

  /** Generic attribute for the feed tray number in a distillation column. */
  public static final String FEED_TRAY = "FeedTray";

  /** DEXPI URI prefix for RDL references. */
  public static final String DEXPI_RDL_PREFIX = "http://sandbox.dexpi.org/rdl/";

  /** Default pressure unit written to DEXPI documents. */
  public static final String DEFAULT_PRESSURE_UNIT = "bara";

  /** Default temperature unit written to DEXPI documents. */
  public static final String DEFAULT_TEMPERATURE_UNIT = "C";

  /** Default volumetric flow unit written to DEXPI documents. */
  public static final String DEFAULT_FLOW_UNIT = "MSm3/day";

  private static final Set<String> RECOMMENDED_STREAM_ATTRIBUTES = Collections
      .unmodifiableSet(new LinkedHashSet<>(Arrays.asList(LINE_NUMBER, FLUID_CODE, SEGMENT_NUMBER,
          OPERATING_PRESSURE_VALUE, OPERATING_PRESSURE_UNIT, OPERATING_TEMPERATURE_VALUE,
          OPERATING_TEMPERATURE_UNIT, OPERATING_FLOW_VALUE, OPERATING_FLOW_UNIT)));

  private static final Set<String> RECOMMENDED_EQUIPMENT_ATTRIBUTES =
      Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(TAG_NAME, LINE_NUMBER,
          FLUID_CODE, INSIDE_DIAMETER, NOMINAL_DIAMETER, TANGENT_TO_TANGENT_LENGTH, DESIGN_PRESSURE,
          DESIGN_TEMPERATURE, ORIENTATION, VALVE_CV, WALL_THICKNESS, WEIGHT)));

  private static final Set<String> SIZING_ATTRIBUTES = Collections
      .unmodifiableSet(new LinkedHashSet<>(Arrays.asList(INSIDE_DIAMETER, NOMINAL_DIAMETER,
          TANGENT_TO_TANGENT_LENGTH, DESIGN_PRESSURE, DESIGN_TEMPERATURE, ORIENTATION, VALVE_CV,
          WALL_THICKNESS, WEIGHT, PIPING_CLASS_CODE, NUMBER_OF_TRAYS, FEED_TRAY)));

  /**
   * Returns the recommended generic attributes that should accompany DEXPI piping segments.
   *
   * @return immutable set of attribute names
   */
  public static Set<String> recommendedStreamAttributes() {
    return RECOMMENDED_STREAM_ATTRIBUTES;
  }

  /**
   * Returns the recommended generic attributes that should accompany DEXPI equipment items.
   *
   * @return immutable set of attribute names
   */
  public static Set<String> recommendedEquipmentAttributes() {
    return RECOMMENDED_EQUIPMENT_ATTRIBUTES;
  }

  /**
   * Returns the set of sizing-related generic attributes that the reader should extract from DEXPI
   * equipment and piping component elements.
   *
   * @return immutable set of sizing attribute names
   */
  public static Set<String> sizingAttributes() {
    return SIZING_ATTRIBUTES;
  }
}
