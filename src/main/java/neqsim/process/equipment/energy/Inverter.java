package neqsim.process.equipment.energy;

import java.util.UUID;
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
   * <p>
   * Voltage and frequency are updated on the existing output quality object so temperature, pressure, utility level,
   * and future metadata are preserved.
   * </p>
   *
   * @param voltage output voltage in V
   * @param frequency output frequency in Hz
   */
  public void setOutputElectricalQuality(double voltage, double frequency) {
    if (!Double.isFinite(voltage) || voltage <= 0.0) {
      throw new IllegalArgumentException("Output voltage must be positive and finite");
    }
    if (!Double.isFinite(frequency) || frequency <= 0.0) {
      throw new IllegalArgumentException("Output frequency must be positive and finite");
    }
    outputVoltage = voltage;
    outputFrequency = frequency;
    publishOutputElectricalQuality();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    super.run(id);
    publishOutputElectricalQuality();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    super.runTransient(dt, id);
    publishOutputElectricalQuality();
  }

  /** Publishes configured voltage and frequency without replacing other output quality metadata. */
  private void publishOutputElectricalQuality() {
    EnergyPort output = getEnergyPort(OUTPUT_PORT);
    if (!output.isConnected()) {
      return;
    }
    EnergyQuality quality = output.getEnergyStream().getQuality();
    if (quality == null) {
      quality = new EnergyQuality();
      output.getEnergyStream().setQuality(quality);
    }
    if (Double.isFinite(outputVoltage)) {
      quality.setVoltage(outputVoltage);
    }
    if (Double.isFinite(outputFrequency)) {
      quality.setFrequency(outputFrequency);
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
