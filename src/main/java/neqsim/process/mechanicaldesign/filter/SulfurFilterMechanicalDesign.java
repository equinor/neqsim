package neqsim.process.mechanicaldesign.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.filter.SulfurFilter;

/**
 * Mechanical design for sulfur filter vessels per ASME VIII Div 1.
 *
 * <p>
 * Extends the general {@link FilterMechanicalDesign} with sulfur-specific behaviour: reading gas
 * flow rate and element count from {@link SulfurFilter}, providing sulfur removal data in the JSON
 * report, and calculating maintenance intervals based on solid S8 loading rates.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * SulfurFilter filter = new SulfurFilter("S8 Filter", stream);
 * filter.run();
 * filter.initMechanicalDesign();
 * SulfurFilterMechanicalDesign design =
 *     (SulfurFilterMechanicalDesign) filter.getMechanicalDesign();
 * design.setMaxOperationPressure(20.0);
 * design.setMaxOperationTemperature(273.15 + 50.0);
 * design.calcDesign();
 * System.out.println(design.toJson());
 * }
 * </pre>
 *
 * @author esol
 * @version 1.1
 */
public class SulfurFilterMechanicalDesign extends FilterMechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * Creates a new SulfurFilterMechanicalDesign.
   *
   * @param equipment the SulfurFilter equipment to design
   */
  public SulfurFilterMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  protected double getGasFlowRateKgHr() {
    SulfurFilter filter = (SulfurFilter) getProcessEquipment();
    double flow = filter.getGasFlowRate();
    if (flow <= 0 && filter.getInletStream() != null) {
      flow = filter.getInletStream().getFlowRate("kg/hr");
    }
    return flow;
  }

  /** {@inheritDoc} */
  @Override
  protected int getInitialElementCount() {
    return ((SulfurFilter) getProcessEquipment()).getNumberOfElements();
  }

  /** {@inheritDoc} */
  @Override
  protected void updateEquipmentElementCount(int count) {
    ((SulfurFilter) getProcessEquipment()).setNumberOfElements(count);
  }

  /** {@inheritDoc} */
  @Override
  protected double getFilterChangeIntervalHours() {
    return ((SulfurFilter) getProcessEquipment()).getChangeIntervalHours();
  }

  /** {@inheritDoc} */
  @Override
  protected String getEquipmentTypeName() {
    return "Sulfur Filter Vessel";
  }

  /** {@inheritDoc} */
  @Override
  protected void addEquipmentSpecificJson(Map<String, Object> result) {
    SulfurFilter filter = (SulfurFilter) getProcessEquipment();

    // Enrich filter elements section with sulfur-specific data
    @SuppressWarnings("unchecked")
    Map<String, Object> elemData = (Map<String, Object>) result.get("filterElements");
    if (elemData == null) {
      elemData = new LinkedHashMap<String, Object>();
      result.put("filterElements", elemData);
    }
    elemData.put("filterType", filter.getFilterType());
    elemData.put("filtrationRating_um", filter.getFiltrationRating());
    elemData.put("elementCapacity_kg", filter.getFilterElementCapacity());
    elemData.put("totalCapacity_kg", filter.getFilterElementCapacity() * getRequiredElements());

    // Sulfur removal section
    Map<String, Object> s8data = new LinkedHashMap<String, Object>();
    s8data.put("solidS8Detected", filter.isSolidS8Detected());
    s8data.put("removalEfficiency", filter.getRemovalEfficiency());
    s8data.put("removalRate_kghr", filter.getSolidSulfurRemovalRate());
    s8data.put("removalRate_kgday", filter.getSolidSulfurRemovalRate("kg/day"));
    s8data.put("changeInterval_hours", filter.getChangeIntervalHours());
    s8data.put("changeInterval_days", filter.getChangeIntervalDays());
    result.put("sulfurRemoval", s8data);
  }
}
