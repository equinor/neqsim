package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Pressure filter for bio-processing solid-liquid separation.
 *
 * <p>
 * A batch or semi-continuous pressure filtration unit. Uses applied pressure to force liquid
 * through a filter medium, retaining solids. Used for fine particle separations, clarification, and
 * product recovery in pharmaceutical and food processing.
 * </p>
 *
 * <p>
 * Typical parameters:
 * </p>
 * <ul>
 * <li>Solids recovery: 95-99%</li>
 * <li>Cake moisture: 35-55%</li>
 * <li>Operating pressure: 2-10 bar</li>
 * <li>Specific energy: 2-5 kWh/m3</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class PressureFilter extends SolidsSeparator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Operating pressure in bar gauge. */
  private double operatingPressure = 5.0;

  /** Filter area in m2. */
  private double filterArea = 10.0;

  /**
   * Constructor for PressureFilter.
   *
   * @param name name of the filter
   */
  public PressureFilter(String name) {
    super(name);
    this.equipmentType = "PressureFilter";
    setSpecificEnergy(3.0);
    setMoistureContent(0.45);
  }

  /**
   * Constructor for PressureFilter with inlet stream.
   *
   * @param name name of the filter
   * @param inletStream the feed stream
   */
  public PressureFilter(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.equipmentType = "PressureFilter";
    setSpecificEnergy(3.0);
    setMoistureContent(0.45);
  }

  /**
   * Set the operating pressure.
   *
   * @param pressure operating pressure in bar gauge
   */
  public void setOperatingPressure(double pressure) {
    this.operatingPressure = pressure;
  }

  /**
   * Get the operating pressure.
   *
   * @return pressure in bar gauge
   */
  public double getOperatingPressure() {
    return operatingPressure;
  }

  /**
   * Set the filter area.
   *
   * @param area filter area in m2
   */
  public void setFilterArea(double area) {
    this.filterArea = area;
  }

  /**
   * Get the filter area.
   *
   * @return filter area in m2
   */
  public double getFilterArea() {
    return filterArea;
  }
}
