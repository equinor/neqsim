package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyQuality;
import neqsim.process.equipment.stream.EnergyType;

/** Electrical transformer with voltage ratio and optional part-load efficiency curve. */
public class Transformer extends LoadMappedEnergyConverter {
  private static final long serialVersionUID = 1000L;
  private double voltageRatio = 1.0;

  public Transformer(String name) {
    super(name, EnergyType.ELECTRICAL, EnergyType.ELECTRICAL);
    setEfficiency(0.99);
  }

  public double getVoltageRatio() {
    return voltageRatio;
  }

  public void setVoltageRatio(double voltageRatio) {
    if (!Double.isFinite(voltageRatio) || voltageRatio <= 0.0) {
      throw new IllegalArgumentException("Voltage ratio must be positive and finite");
    }
    this.voltageRatio = voltageRatio;
    updateOutputQuality();
  }

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
