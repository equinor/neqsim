package neqsim.process.safety.api14c;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Walks a NeqSim {@link ProcessSystem} and emits an API RP 14C / ISO 10418 SAFE chart.
 *
 * <p>
 * Each piece of equipment is classified into an {@link Api14cEquipmentCategory} from its Java class name (Separator,
 * Compressor, Pump, HeatExchanger, ...). The required device set is then looked up from
 * {@link Api14cSafetyAnalysisTable} and compared against the user-declared "present" device set. The result lists the
 * SAFE-chart rows and the union of all missing devices.
 * </p>
 *
 * <p>
 * Because NeqSim does not currently model individual instrument tags per equipment, the "present" device set is
 * provided externally - either by the caller or, for a quick-look SAFE chart, defaulted to the standard set with
 * {@link #buildAssumingComplete(ProcessSystem)}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Api14cSafeChartBuilder implements Serializable {

  private static final long serialVersionUID = 1L;

  private final List<Api14cSafeChartItem> items = new ArrayList<Api14cSafeChartItem>();
  private final Map<String, Set<Api14cDeviceType>> presentByEquipment = new LinkedHashMap<String, Set<Api14cDeviceType>>();

  /**
   * Declares which devices are installed on a piece of equipment.
   *
   * @param equipmentName equipment tag/name
   * @param present installed devices
   * @return this builder
   */
  public Api14cSafeChartBuilder declarePresent(String equipmentName, Set<Api14cDeviceType> present) {
    if (equipmentName == null || present == null) {
      throw new IllegalArgumentException("arguments must not be null");
    }
    presentByEquipment.put(equipmentName, EnumSet.copyOf(present));
    return this;
  }

  /**
   * Builds the SAFE chart from the given {@link ProcessSystem}.
   *
   * <p>
   * Equipment whose Java class cannot be classified into an {@link Api14cEquipmentCategory} is skipped (with no
   * finding).
   * </p>
   *
   * @param process the process system
   * @return this builder (with items populated)
   */
  public Api14cSafeChartBuilder build(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    items.clear();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Api14cEquipmentCategory cat = classify(unit);
      if (cat == null) {
        continue;
      }
      Set<Api14cDeviceType> required = Api14cSafetyAnalysisTable.getRequiredDevices(cat);
      Set<Api14cDeviceType> present = presentByEquipment.get(unit.getName());
      if (present == null) {
        present = EnumSet.noneOf(Api14cDeviceType.class);
      }
      items.add(new Api14cSafeChartItem(unit.getName(), cat, required, present));
    }
    return this;
  }

  /**
   * Convenience method that builds the chart assuming every required device is present (useful for "what should this
   * facility have?" overviews).
   *
   * @param process the process system
   * @return this builder
   */
  public Api14cSafeChartBuilder buildAssumingComplete(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    items.clear();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Api14cEquipmentCategory cat = classify(unit);
      if (cat == null) {
        continue;
      }
      Set<Api14cDeviceType> required = Api14cSafetyAnalysisTable.getRequiredDevices(cat);
      items.add(new Api14cSafeChartItem(unit.getName(), cat, required, required));
    }
    return this;
  }

  /**
   * Classifies a NeqSim process unit into an API RP 14C equipment category.
   *
   * @param unit the equipment
   * @return category or null if not classifiable
   */
  public static Api14cEquipmentCategory classify(ProcessEquipmentInterface unit) {
    if (unit == null) {
      return null;
    }
    String name = unit.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    if (name.contains("scrubber") || name.contains("separator") || name.contains("flashdrum")
        || name.contains("knockout") || name.contains("flash") && name.contains("drum")) {
      return Api14cEquipmentCategory.PRESSURE_VESSEL;
    }
    if (name.contains("tank")) {
      return Api14cEquipmentCategory.ATMOSPHERIC_VESSEL;
    }
    if (name.contains("heater") || name.contains("furnace") || name.contains("reformer")) {
      return Api14cEquipmentCategory.FIRED_VESSEL;
    }
    if (name.contains("pipe") || name.contains("flowline") || name.contains("riser")) {
      return Api14cEquipmentCategory.PIPELINE_SEGMENT;
    }
    if (name.contains("compressor") || name.contains("expander")) {
      return Api14cEquipmentCategory.COMPRESSOR;
    }
    if (name.contains("pump")) {
      return Api14cEquipmentCategory.PUMP;
    }
    if (name.contains("heatexchanger") || name.contains("cooler") || name.contains("condenser")
        || name.contains("reboiler") || name.contains("aircooler")) {
      return Api14cEquipmentCategory.HEAT_EXCHANGER;
    }
    if (name.contains("well")) {
      return Api14cEquipmentCategory.WELLHEAD;
    }
    return null;
  }

  /**
   * @return chart items in build order
   */
  public List<Api14cSafeChartItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * @return union of missing devices flagged as a gap list across all equipment
   */
  public List<String> getGaps() {
    List<String> gaps = new ArrayList<String>();
    for (Api14cSafeChartItem it : items) {
      if (!it.isComplete()) {
        for (Api14cDeviceType d : it.getMissing()) {
          gaps.add(it.getEquipmentName() + ": missing " + d.name() + " (" + d.getDescription() + ")");
        }
      }
    }
    return gaps;
  }

  /**
   * @return true when every chart row is complete
   */
  public boolean isComplete() {
    for (Api14cSafeChartItem it : items) {
      if (!it.isComplete()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Renders the SAFE chart as a markdown-style table for inclusion in a report.
   *
   * @return markdown table string
   */
  public String toMarkdown() {
    StringBuilder sb = new StringBuilder();
    sb.append("| Equipment | Category | Required | Present | Missing |\n");
    sb.append("|---|---|---|---|---|\n");
    for (Api14cSafeChartItem it : items) {
      List<String> row = it.toRow();
      sb.append("| ").append(row.get(0)).append(" | ").append(row.get(1)).append(" | ").append(row.get(2)).append(" | ")
          .append(row.get(3)).append(" | ").append(row.get(4)).append(" |\n");
    }
    return sb.toString();
  }

  /**
   * @return SAFE chart as pretty JSON
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(items);
  }
}
