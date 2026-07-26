package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyQuality;
import neqsim.process.equipment.stream.EnergyType;

/**
 * Electrical transformer with power efficiency and voltage ratio.
 *
 * @author NeqSim
 * @version 1.0
 */
public class Transformer extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  private double voltageRatio = 1.0;

  /**
   * Creates a transformer with 99 percent efficiency.
   *
   * @param name equipment name
   */
  public Transformer(String name) {
    super(name, EnergyType.ELECTRICAL, EnergyType.ELECTRICAL);
    setEfficiency(0.99);
  }

  /**
   * Gets secondary-to-primary voltage ratio.
   *
   * @return voltage ratio
   */
  public double getVoltageRatio() {
    return voltageRatio;
  }

  /**
   * Sets secondary-to-primary voltage ratio and updates connected output quality.
   *
   * @param voltageRatio positive voltage ratio
   */
  public void setVoltageRatio(double voltageRatio) {
    if (!Double.isFinite(voltageRatio) || voltageRatio <= 0.0) {
      throw new IllegalArgumentException("Voltage ratio must be positive and finite");
    }
    this.voltageRatio = voltageRatio;
    updateOutputQuality();
  }

  /** Updates connected output voltage and preserves input frequency. */
  private void updateOutputQuality() {
    EnergyPort input = getEnergyPort(INPUT_PORT);
    EnergyPort output = getEnergyPort(OUTPUT_PORT);
    if (!input.isConnected() || !output.isConnected()) {
      return;
    }
    EnergyQuality inputQuality = input.getEnergyStream().getQuality();
    EnergyQuality outputQuality = output.getEnergyStream().getQuality();
    if (Double.isFinite(inputQuality.getVoltage())) {
      outputQuality.setVoltage(inputQuality.getVoltage() * voltageRatio);
    }
    if (Double.isFinite(inputQuality.getFrequency())) {
      outputQuality.setFrequency(inputQuality.getFrequency());
    }
  }
}
