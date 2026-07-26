package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyQuality;
import neqsim.process.equipment.stream.EnergyType;

/**
 * Electrical inverter or variable-frequency converter.
 *
 * @author NeqSim
 * @version 1.0
 */
public class Inverter extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  private double outputVoltage = Double.NaN;
  private double outputFrequency = Double.NaN;

  /**
   * Creates an inverter with 97 percent efficiency.
   *
   * @param name equipment name
   */
  public Inverter(String name) {
    super(name, EnergyType.ELECTRICAL, EnergyType.ELECTRICAL);
    setEfficiency(0.97);
  }

  /**
   * Sets output electrical quality.
   *
   * @param voltage output voltage in V
   * @param frequency output frequency in Hz
   */
  public void setOutputElectricalQuality(double voltage, double frequency) {
    EnergyQuality quality = new EnergyQuality();
    quality.setVoltage(voltage);
    quality.setFrequency(frequency);
    outputVoltage = voltage;
    outputFrequency = frequency;
    EnergyPort output = getEnergyPort(OUTPUT_PORT);
    if (output.isConnected()) {
      output.getEnergyStream().setQuality(quality);
    }
  }

  /**
   * Gets configured output voltage.
   *
   * @return voltage in V
   */
  public double getOutputVoltage() {
    return outputVoltage;
  }

  /**
   * Gets configured output frequency.
   *
   * @return frequency in Hz
   */
  public double getOutputFrequency() {
    return outputFrequency;
  }
}
