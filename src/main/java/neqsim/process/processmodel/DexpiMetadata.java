package neqsim.process.processmodel;

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

  /** Default pressure unit written to DEXPI documents. */
  public static final String DEFAULT_PRESSURE_UNIT = "bara";

  /** Default temperature unit written to DEXPI documents. */
  public static final String DEFAULT_TEMPERATURE_UNIT = "C";

  /** Default volumetric flow unit written to DEXPI documents. */
  public static final String DEFAULT_FLOW_UNIT = "MSm3/day";

  private static final Set<String> RECOMMENDED_STREAM_ATTRIBUTES =
      Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(LINE_NUMBER, FLUID_CODE,
          SEGMENT_NUMBER, OPERATING_PRESSURE_VALUE, OPERATING_PRESSURE_UNIT,
          OPERATING_TEMPERATURE_VALUE, OPERATING_TEMPERATURE_UNIT, OPERATING_FLOW_VALUE,
          OPERATING_FLOW_UNIT)));

  private static final Set<String> RECOMMENDED_EQUIPMENT_ATTRIBUTES = Collections
      .unmodifiableSet(new LinkedHashSet<>(Arrays.asList(TAG_NAME, LINE_NUMBER, FLUID_CODE)));

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
}
