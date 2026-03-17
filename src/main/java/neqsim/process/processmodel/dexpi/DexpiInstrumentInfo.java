package neqsim.process.processmodel.dexpi;

import java.io.Serializable;

/**
 * Holds metadata for an instrument parsed from a DEXPI XML file. Because DEXPI instruments are not
 * directly connected to live process streams, this lightweight value object stores the tag,
 * category (P/L/T/F), functions (IC/T/CSA), and loop information so that callers can wire up real
 * transmitters and controllers later.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiInstrumentInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String id;
  private final String tagName;
  private final String category;
  private final String functions;
  private final String instrumentNumber;
  private final String loopNumber;
  private final String measurementUnit;
  private final String actuatingTag;

  /**
   * Creates a DEXPI instrument info record.
   *
   * @param id the DEXPI XML element ID
   * @param tagName the instrument tag name (e.g. "PICSA 4712.02")
   * @param category the ISA category letter (e.g. "P" for pressure)
   * @param functions the ISA function letters (e.g. "ICSA")
   * @param instrumentNumber the instrument number
   * @param loopNumber the instrumentation loop number (may be null)
   * @param measurementUnit the measurement unit (may be null)
   * @param actuatingTag the associated actuating function tag (may be null)
   */
  public DexpiInstrumentInfo(String id, String tagName, String category, String functions,
      String instrumentNumber, String loopNumber, String measurementUnit, String actuatingTag) {
    this.id = id;
    this.tagName = tagName;
    this.category = category;
    this.functions = functions;
    this.instrumentNumber = instrumentNumber;
    this.loopNumber = loopNumber;
    this.measurementUnit = measurementUnit;
    this.actuatingTag = actuatingTag;
  }

  /**
   * Returns the DEXPI XML element ID.
   *
   * @return the element ID
   */
  public String getId() {
    return id;
  }

  /**
   * Returns the instrument tag name.
   *
   * @return the tag name
   */
  public String getTagName() {
    return tagName;
  }

  /**
   * Returns the ISA category letter (e.g. "P" for pressure, "L" for level).
   *
   * @return the category
   */
  public String getCategory() {
    return category;
  }

  /**
   * Returns the ISA function letters (e.g. "ICSA" for indicating-controlling-switching-alarming).
   *
   * @return the function letters
   */
  public String getFunctions() {
    return functions;
  }

  /**
   * Returns the instrument number.
   *
   * @return the instrument number
   */
  public String getInstrumentNumber() {
    return instrumentNumber;
  }

  /**
   * Returns the instrumentation loop number.
   *
   * @return the loop number, or null if not part of a loop
   */
  public String getLoopNumber() {
    return loopNumber;
  }

  /**
   * Returns the measurement unit.
   *
   * @return the unit, or null if not specified
   */
  public String getMeasurementUnit() {
    return measurementUnit;
  }

  /**
   * Returns the associated actuating function tag.
   *
   * @return the actuating tag, or null if no actuator
   */
  public String getActuatingTag() {
    return actuatingTag;
  }

  /**
   * Checks if this instrument has a control function (contains "C" in functions string).
   *
   * @return true if the instrument has a controller function
   */
  public boolean hasControlFunction() {
    return functions != null && functions.contains("C");
  }

  /**
   * Checks if this instrument is part of a loop.
   *
   * @return true if a loop number is set
   */
  public boolean isInLoop() {
    return loopNumber != null && !loopNumber.trim().isEmpty();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("DexpiInstrumentInfo{");
    sb.append("tag='").append(tagName).append('\'');
    sb.append(", category='").append(category).append('\'');
    sb.append(", functions='").append(functions).append('\'');
    if (loopNumber != null) {
      sb.append(", loop='").append(loopNumber).append('\'');
    }
    if (actuatingTag != null) {
      sb.append(", actuator='").append(actuatingTag).append('\'');
    }
    sb.append('}');
    return sb.toString();
  }
}
