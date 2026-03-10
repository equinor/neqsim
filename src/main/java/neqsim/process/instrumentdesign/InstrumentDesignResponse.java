package neqsim.process.instrumentdesign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Response class for instrument design JSON export.
 *
 * <p>
 * Provides a structured representation of instrument design data for JSON serialization. Includes
 * equipment identification, instrument index, I/O summary, and cost summary.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class InstrumentDesignResponse implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private String equipmentName;
  private String equipmentType;
  private int totalInstruments;
  private Map<String, Integer> ioSummary;
  private int safetyInstrumentCount;
  private double totalCostUSD;
  private List<Map<String, Object>> instrumentIndex;

  /**
   * Default constructor.
   */
  public InstrumentDesignResponse() {}

  /**
   * Constructor from InstrumentDesign.
   *
   * @param design the instrument design object
   */
  public InstrumentDesignResponse(InstrumentDesign design) {
    populateFromDesign(design);
  }

  /**
   * Populate from an InstrumentDesign instance.
   *
   * @param design the instrument design object
   */
  public void populateFromDesign(InstrumentDesign design) {
    if (design == null) {
      return;
    }

    if (design.getProcessEquipment() != null) {
      this.equipmentName = design.getProcessEquipment().getName();
      this.equipmentType = design.getProcessEquipment().getClass().getSimpleName();
    }

    InstrumentList list = design.getInstrumentList();
    if (list != null) {
      this.totalInstruments = list.size();
      this.safetyInstrumentCount = list.getSafetyInstrumentCount();
      this.totalCostUSD = list.getTotalCostUSD();

      this.ioSummary = new LinkedHashMap<String, Integer>();
      this.ioSummary.put("AI", list.getAnalogInputCount());
      this.ioSummary.put("AO", list.getAnalogOutputCount());
      this.ioSummary.put("DI", list.getDigitalInputCount());
      this.ioSummary.put("DO", list.getDigitalOutputCount());
      this.ioSummary.put("Total", list.getTotalIOCount());

      this.instrumentIndex = new ArrayList<Map<String, Object>>();
      for (InstrumentSpecification spec : list.getAll()) {
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("tagNumber", spec.getTagNumber());
        entry.put("isaSymbol", spec.getIsaSymbol());
        entry.put("service", spec.getService());
        entry.put("instrumentType", spec.getInstrumentType());
        entry.put("ioType", spec.getIoType());
        if (spec.isAnalog()) {
          entry.put("rangeMin", spec.getRangeMin());
          entry.put("rangeMax", spec.getRangeMax());
          entry.put("rangeUnit", spec.getRangeUnit());
        }
        entry.put("outputSignal", spec.getOutputSignal());
        entry.put("material", spec.getMaterial());
        entry.put("hazardousAreaZone", spec.getHazardousAreaZone());
        entry.put("exProtection", spec.getExProtection());
        if (spec.getSilRating() > 0) {
          entry.put("silRating", spec.getSilRating());
        }
        if (spec.isSafetyRelated()) {
          entry.put("safetyRelated", true);
        }
        entry.put("estimatedCostUSD", spec.getEstimatedCostUSD());
        this.instrumentIndex.add(entry);
      }
    }
  }

  /**
   * Serialize to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(this);
  }
}
