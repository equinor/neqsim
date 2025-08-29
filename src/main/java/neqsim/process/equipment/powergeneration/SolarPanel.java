package neqsim.process.equipment.powergeneration;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Simple solar panel model converting solar irradiance to electrical power.
 *
 * @author esol
 */
public class SolarPanel extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(SolarPanel.class);

  private double irradiance; // W/m^2
  private double panelArea; // m^2
  private double efficiency = 0.2; // Fraction
  private double power = 0.0; // W

  /**
   * Default constructor.
   */
  public SolarPanel() {
    this("SolarPanel");
  }

  /**
   * Create a solar panel with a given name.
   *
   * @param name name of equipment
   */
  public SolarPanel(String name) {
    super(name);
  }

  /**
   * Create a solar panel with initial parameters.
   *
   * @param name name of equipment
   * @param irradiance solar irradiance [W/m^2]
   * @param panelArea panel area [m^2]
   * @param efficiency electrical efficiency (0-1)
   */
  public SolarPanel(String name, double irradiance, double panelArea, double efficiency) {
    this(name);
    this.irradiance = irradiance;
    this.panelArea = panelArea;
    this.efficiency = efficiency;
  }

  /**
   * Set incoming solar irradiance.
   *
   * @param irradiance solar irradiance [W/m^2]
   */
  public void setIrradiance(double irradiance) {
    this.irradiance = irradiance;
  }

  /**
   * Set panel area.
   *
   * @param panelArea panel area [m^2]
   */
  public void setPanelArea(double panelArea) {
    this.panelArea = panelArea;
  }

  /**
   * Set electrical efficiency of the panel.
   *
   * @param efficiency efficiency (0-1)
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * Get produced electrical power.
   *
   * @return power [W]
   */
  public double getPower() {
    return power;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    power = irradiance * panelArea * efficiency;
    getEnergyStream().setDuty(-power);
    setEnergyStream(true);
    setCalculationIdentifier(id);
  }
}

