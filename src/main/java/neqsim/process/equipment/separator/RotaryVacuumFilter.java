package neqsim.process.equipment.separator;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Rotary vacuum filter for bio-processing solid-liquid separation.
 *
 * <p>
 * A continuous vacuum filtration unit for separating solids from liquids. Commonly used in sugar
 * processing, starch recovery, and biomass dewatering. Lower energy consumption than centrifuges
 * but generally produces wetter cake.
 * </p>
 *
 * <p>
 * Typical parameters:
 * </p>
 * <ul>
 * <li>Solids recovery: 90-98%</li>
 * <li>Cake moisture: 50-70%</li>
 * <li>Specific energy: 1-3 kWh/m3</li>
 * <li>Vacuum: 0.3-0.7 bar absolute</li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class RotaryVacuumFilter extends SolidsSeparator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Vacuum pressure in bar absolute. */
  private double vacuumPressure = 0.5;

  /** Filter area in m2. */
  private double filterArea = 10.0;

  /** Specific cake resistance in m/kg. */
  private double specificCakeResistance = 1.0e10;

  /**
   * Constructor for RotaryVacuumFilter.
   *
   * @param name name of the filter
   */
  public RotaryVacuumFilter(String name) {
    super(name);
    this.equipmentType = "RotaryVacuumFilter";
    setSpecificEnergy(2.0);
    setMoistureContent(0.60);
  }

  /**
   * Constructor for RotaryVacuumFilter with inlet stream.
   *
   * @param name name of the filter
   * @param inletStream the feed stream
   */
  public RotaryVacuumFilter(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.equipmentType = "RotaryVacuumFilter";
    setSpecificEnergy(2.0);
    setMoistureContent(0.60);
  }

  /**
   * Set the vacuum pressure.
   *
   * @param pressureBara vacuum pressure in bar absolute
   */
  public void setVacuumPressure(double pressureBara) {
    this.vacuumPressure = pressureBara;
  }

  /**
   * Get the vacuum pressure.
   *
   * @return vacuum pressure in bar absolute
   */
  public double getVacuumPressure() {
    return vacuumPressure;
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

  /**
   * Set the specific cake resistance.
   *
   * @param resistance specific resistance in m/kg
   */
  public void setSpecificCakeResistance(double resistance) {
    this.specificCakeResistance = resistance;
  }

  /**
   * Get the specific cake resistance.
   *
   * @return resistance in m/kg
   */
  public double getSpecificCakeResistance() {
    return specificCakeResistance;
  }
}
