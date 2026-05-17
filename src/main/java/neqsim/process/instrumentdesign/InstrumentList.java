package neqsim.process.instrumentdesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of instrument specifications for a piece of process equipment.
 *
 * <p>
 * Acts as an instrument index (instrument list) for a single equipment item. Provides convenience
 * methods for I/O count summaries, cost aggregation, and tag number generation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class InstrumentList implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Equipment tag prefix used for tag number generation (e.g. "V-101"). */
  private String equipmentTag;

  /** The list of instrument specifications. */
  private List<InstrumentSpecification> instruments = new ArrayList<InstrumentSpecification>();

  /** Running counter for tag number generation per ISA prefix. */
  private int tagCounter = 1;

  /**
   * Constructor for InstrumentList.
   *
   * @param equipmentTag the equipment tag prefix for instrument tag generation
   */
  public InstrumentList(String equipmentTag) {
    this.equipmentTag = equipmentTag;
  }

  /**
   * Add an instrument specification and auto-generate its tag number.
   *
   * @param spec the instrument specification to add
   */
  public void add(InstrumentSpecification spec) {
    if (spec.getTagNumber() == null || spec.getTagNumber().trim().isEmpty()) {
      spec.setTagNumber(generateTagNumber(spec.getIsaSymbol()));
    }
    instruments.add(spec);
  }

  /**
   * Get all instrument specifications.
   *
   * @return list of instrument specifications
   */
  public List<InstrumentSpecification> getAll() {
    return instruments;
  }

  /**
   * Get instrument count.
   *
   * @return total number of instruments
   */
  public int size() {
    return instruments.size();
  }

  /**
   * Count analog inputs.
   *
   * @return number of AI channels
   */
  public int getAnalogInputCount() {
    int count = 0;
    for (InstrumentSpecification spec : instruments) {
      if ("AI".equals(spec.getIoType())) {
        count++;
      }
    }
    return count;
  }

  /**
   * Count analog outputs.
   *
   * @return number of AO channels
   */
  public int getAnalogOutputCount() {
    int count = 0;
    for (InstrumentSpecification spec : instruments) {
      if ("AO".equals(spec.getIoType())) {
        count++;
      }
    }
    return count;
  }

  /**
   * Count digital inputs.
   *
   * @return number of DI channels
   */
  public int getDigitalInputCount() {
    int count = 0;
    for (InstrumentSpecification spec : instruments) {
      if ("DI".equals(spec.getIoType())) {
        count++;
      }
    }
    return count;
  }

  /**
   * Count digital outputs.
   *
   * @return number of DO channels
   */
  public int getDigitalOutputCount() {
    int count = 0;
    for (InstrumentSpecification spec : instruments) {
      if ("DO".equals(spec.getIoType())) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get total I/O count (AI + AO + DI + DO).
   *
   * @return total I/O count
   */
  public int getTotalIOCount() {
    return instruments.size();
  }

  /**
   * Count safety-related (SIS) instruments.
   *
   * @return number of safety instruments
   */
  public int getSafetyInstrumentCount() {
    int count = 0;
    for (InstrumentSpecification spec : instruments) {
      if (spec.isSafetyRelated()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get total estimated cost in USD for all instruments.
   *
   * @return total cost in USD
   */
  public double getTotalCostUSD() {
    double total = 0.0;
    for (InstrumentSpecification spec : instruments) {
      total += spec.getEstimatedCostUSD();
    }
    return total;
  }

  /**
   * Generate a tag number from ISA symbol and running counter.
   *
   * @param isaSymbol ISA functional identification
   * @return generated tag number (e.g. "PT-1001")
   */
  private String generateTagNumber(String isaSymbol) {
    String tag = isaSymbol + "-" + (1000 + tagCounter);
    tagCounter++;
    return tag;
  }

  /**
   * Get the equipment tag prefix.
   *
   * @return the equipment tag
   */
  public String getEquipmentTag() {
    return equipmentTag;
  }

  /**
   * Set the equipment tag prefix.
   *
   * @param equipmentTag the equipment tag
   */
  public void setEquipmentTag(String equipmentTag) {
    this.equipmentTag = equipmentTag;
  }
}
