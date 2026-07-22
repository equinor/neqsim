package neqsim.process.safety.api14c;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single row in an API RP 14C / ISO 10418 SAFE chart: equipment, category, required devices, present devices and
 * missing devices.
 *
 * @author ESOL
 * @version 1.0
 */
public class Api14cSafeChartItem implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String equipmentName;
  private final Api14cEquipmentCategory category;
  private final Set<Api14cDeviceType> required;
  private final Set<Api14cDeviceType> present;
  private final Set<Api14cDeviceType> missing;

  /**
   * Creates a SAFE-chart row.
   *
   * @param equipmentName process equipment name (e.g. tag number)
   * @param category equipment category
   * @param required required devices per the SAFE table
   * @param present devices declared as installed
   */
  public Api14cSafeChartItem(String equipmentName, Api14cEquipmentCategory category, Set<Api14cDeviceType> required,
      Set<Api14cDeviceType> present) {
    if (equipmentName == null || category == null || required == null || present == null) {
      throw new IllegalArgumentException("arguments must not be null");
    }
    this.equipmentName = equipmentName;
    this.category = category;
    this.required = Collections.unmodifiableSet(new LinkedHashSet<Api14cDeviceType>(required));
    this.present = Collections.unmodifiableSet(new LinkedHashSet<Api14cDeviceType>(present));
    Set<Api14cDeviceType> miss = new LinkedHashSet<Api14cDeviceType>(required);
    miss.removeAll(present);
    this.missing = Collections.unmodifiableSet(miss);
  }

  /**
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * @return equipment category
   */
  public Api14cEquipmentCategory getCategory() {
    return category;
  }

  /**
   * @return required device set
   */
  public Set<Api14cDeviceType> getRequired() {
    return required;
  }

  /**
   * @return device set declared as present
   */
  public Set<Api14cDeviceType> getPresent() {
    return present;
  }

  /**
   * @return required \ present (the SAFE-chart gaps)
   */
  public Set<Api14cDeviceType> getMissing() {
    return missing;
  }

  /**
   * @return true when no required device is missing
   */
  public boolean isComplete() {
    return missing.isEmpty();
  }

  /**
   * @return rendered table row for printing in a report
   */
  public List<String> toRow() {
    List<String> row = new ArrayList<String>();
    row.add(equipmentName);
    row.add(category.name());
    row.add(joined(required));
    row.add(joined(present));
    row.add(joined(missing));
    return row;
  }

  private static String joined(Set<Api14cDeviceType> s) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Api14cDeviceType d : s) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(d.name());
      first = false;
    }
    return sb.toString();
  }
}
