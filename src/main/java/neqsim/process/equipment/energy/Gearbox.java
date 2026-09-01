package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyQuality;
import neqsim.process.equipment.stream.EnergyType;

/**
 * Mechanical gearbox connecting shaft-work networks with an efficiency and speed ratio.
 *
 * @author NeqSim
 * @version 1.0
 */
public class Gearbox extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  private double speedRatio = 1.0;

  /**
   * Creates a gearbox with 98 percent efficiency.
   *
   * @param name equipment name
   */
  public Gearbox(String name) {
    super(name, EnergyType.SHAFT_WORK, EnergyType.SHAFT_WORK);
    setEfficiency(0.98);
  }

  /**
   * Gets output-to-input speed ratio.
   *
   * @return speed ratio
   */
  public double getSpeedRatio() {
    return speedRatio;
  }

  /**
   * Sets output-to-input speed ratio and updates connected output quality.
   *
   * @param speedRatio positive speed ratio
   */
  public void setSpeedRatio(double speedRatio) {
    if (!Double.isFinite(speedRatio) || speedRatio <= 0.0) {
      throw new IllegalArgumentException("Speed ratio must be positive and finite");
    }
    this.speedRatio = speedRatio;
    updateOutputSpeedQuality();
  }

  /** Updates output shaft-speed quality when both stream qualities are available. */
  private void updateOutputSpeedQuality() {
    EnergyPort input = getEnergyPort(INPUT_PORT);
    EnergyPort output = getEnergyPort(OUTPUT_PORT);
    if (!input.isConnected() || !output.isConnected()) {
      return;
    }
    double inputSpeed = input.getEnergyStream().getQuality().getShaftSpeed();
    if (Double.isFinite(inputSpeed)) {
      EnergyQuality quality = output.getEnergyStream().getQuality();
      quality.setShaftSpeed(inputSpeed * speedRatio);
    }
  }
}
