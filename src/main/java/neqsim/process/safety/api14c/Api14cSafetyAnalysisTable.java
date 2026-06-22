package neqsim.process.safety.api14c;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API RP 14C / ISO 10418 Safety Analysis Table - the canonical mapping from {@link Api14cEquipmentCategory} to the
 * mandatory set of {@link Api14cDeviceType}s.
 *
 * <p>
 * The default table reflects the prescriptive minimum-device list of API RP 14C, Appendix A. Project deviations should
 * be documented separately; this class is intentionally read only after construction.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class Api14cSafetyAnalysisTable implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final Map<Api14cEquipmentCategory, Set<Api14cDeviceType>> TABLE;

  static {
    EnumMap<Api14cEquipmentCategory, Set<Api14cDeviceType>> t = new EnumMap<Api14cEquipmentCategory, Set<Api14cDeviceType>>(
	Api14cEquipmentCategory.class);
    t.put(Api14cEquipmentCategory.PRESSURE_VESSEL, toSet(Api14cDeviceType.PSH, Api14cDeviceType.PSL,
	Api14cDeviceType.LSH, Api14cDeviceType.LSL, Api14cDeviceType.PSV, Api14cDeviceType.SDV));
    t.put(Api14cEquipmentCategory.ATMOSPHERIC_VESSEL,
	toSet(Api14cDeviceType.LSH, Api14cDeviceType.PSV, Api14cDeviceType.FSV));
    t.put(Api14cEquipmentCategory.FIRED_VESSEL, toSet(Api14cDeviceType.PSH, Api14cDeviceType.TSH, Api14cDeviceType.LSL,
	Api14cDeviceType.PSV, Api14cDeviceType.SDV, Api14cDeviceType.BDV, Api14cDeviceType.FIRE));
    t.put(Api14cEquipmentCategory.PIPELINE_SEGMENT,
	toSet(Api14cDeviceType.PSH, Api14cDeviceType.PSL, Api14cDeviceType.SDV, Api14cDeviceType.FSV));
    t.put(Api14cEquipmentCategory.COMPRESSOR, toSet(Api14cDeviceType.PSH, Api14cDeviceType.PSL, Api14cDeviceType.TSH,
	Api14cDeviceType.SDV, Api14cDeviceType.BDV, Api14cDeviceType.PSV));
    t.put(Api14cEquipmentCategory.PUMP,
	toSet(Api14cDeviceType.PSH, Api14cDeviceType.SDV, Api14cDeviceType.PSV, Api14cDeviceType.FSV));
    t.put(Api14cEquipmentCategory.HEAT_EXCHANGER,
	toSet(Api14cDeviceType.PSH, Api14cDeviceType.PSV, Api14cDeviceType.TSH));
    t.put(Api14cEquipmentCategory.WELLHEAD, toSet(Api14cDeviceType.PSH, Api14cDeviceType.PSL, Api14cDeviceType.USV,
	Api14cDeviceType.SDV, Api14cDeviceType.PSV));
    TABLE = Collections.unmodifiableMap(t);
  }

  private Api14cSafetyAnalysisTable() {
    // utility class
  }

  private static Set<Api14cDeviceType> toSet(Api14cDeviceType... items) {
    return Collections.unmodifiableSet(new HashSet<Api14cDeviceType>(Arrays.asList(items)));
  }

  /**
   * Returns the required device set for an equipment category.
   *
   * @param category equipment category
   * @return immutable required device set
   */
  public static Set<Api14cDeviceType> getRequiredDevices(Api14cEquipmentCategory category) {
    if (category == null) {
      throw new IllegalArgumentException("category must not be null");
    }
    Set<Api14cDeviceType> s = TABLE.get(category);
    return s == null ? Collections.<Api14cDeviceType>emptySet() : s;
  }

  /**
   * Returns the full standard table.
   *
   * @return immutable mapping
   */
  public static Map<Api14cEquipmentCategory, Set<Api14cDeviceType>> getTable() {
    return TABLE;
  }

  /**
   * Returns the canonical list of all device types (in printing order).
   *
   * @return list of device types
   */
  public static List<Api14cDeviceType> deviceOrder() {
    return Collections.unmodifiableList(Arrays.asList(Api14cDeviceType.values()));
  }
}
