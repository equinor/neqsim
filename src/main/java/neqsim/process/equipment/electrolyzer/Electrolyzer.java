package neqsim.process.equipment.electrolyzer;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Electrolyzer unit converting water to hydrogen and oxygen using electrical energy.
 * </p>
 *
 * @author esol
 */
public class Electrolyzer extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Electrolyzer.class);

  private StreamInterface waterInlet;
  private Stream hydrogenOutStream;
  private Stream oxygenOutStream;

  private double cellVoltage = 1.23; // V
  private static final double FARADAY_CONSTANT = 96485.3329; // C/mol e-

  /** Molar mass of hydrogen (kg/mol). */
  private static final double MW_H2 = 0.002016;

  /** Selected technology. Null preserves the original (1.23 V) behaviour. */
  private ElectrolyzerTechnology technology = null;

  /** Optional polarisation model. When set, drives {@link #cellVoltage} from current density. */
  private ElectrolyzerIVCharacteristic ivCharacteristic = null;

  /** Current density (A/cm2), used when {@link #ivCharacteristic} is non-null. */
  private double currentDensity = 0.0;

  /** Faradaic (current) efficiency, fraction of electrons that split water (0..1). */
  private double faradaicEfficiency = 1.0;

  /** Stack power computed by the most recent {@link #run(java.util.UUID)} (W). */
  private double stackPower = 0.0;

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   */
  public Electrolyzer(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Electrolyzer.
   * </p>
   *
   * @param name name of unit
   * @param inletStream water inlet stream
   */
  public Electrolyzer(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   *
   * @param inletStream water inlet stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.waterInlet = inletStream;
    SystemInterface h2System =
        new Fluid().create2(new String[] {"hydrogen"}, new double[] {1.0}, "mole/sec");
    hydrogenOutStream = new Stream("hydrogenOutStream", h2System);
    SystemInterface o2System =
        new Fluid().create2(new String[] {"oxygen"}, new double[] {1.0}, "mole/sec");
    oxygenOutStream = new Stream("oxygenOutStream", o2System);

    double pressure = inletStream.getPressure("bara");
    double temperature = inletStream.getTemperature("K");
    hydrogenOutStream.setPressure(pressure, "bara");
    hydrogenOutStream.setTemperature(temperature, "K");
    oxygenOutStream.setPressure(pressure, "bara");
    oxygenOutStream.setTemperature(temperature, "K");
  }

  /**
   * <p>
   * Getter for the field <code>hydrogenOutStream</code>.
   * </p>
   *
   * @return hydrogen product stream
   */
  public StreamInterface getHydrogenOutStream() {
    return hydrogenOutStream;
  }

  /**
   * Gets the cell voltage.
   *
   * @return cell voltage in V
   */
  public double getCellVoltage() {
    return cellVoltage;
  }

  /**
   * Sets the cell voltage.
   *
   * @param cellVoltage cell voltage in V
   */
  public void setCellVoltage(double cellVoltage) {
    this.cellVoltage = cellVoltage;
  }

  /**
   * <p>
   * Getter for the field <code>oxygenOutStream</code>.
   * </p>
   *
   * @return oxygen product stream
   */
  public StreamInterface getOxygenOutStream() {
    return oxygenOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = waterInlet.getThermoSystem().getFlowRate(unit);
    double outletFlow = hydrogenOutStream.getThermoSystem().getFlowRate(unit)
        + oxygenOutStream.getThermoSystem().getFlowRate(unit);
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double waterFlow = waterInlet.getFlowRate("mole/sec");
    // Faradaic efficiency reduces the fraction of water electrolysed per unit current.
    double hydrogenFlow = waterFlow * faradaicEfficiency;
    double oxygenFlow = hydrogenFlow / 2.0;

    hydrogenOutStream.setFlowRate(hydrogenFlow, "mole/sec");
    hydrogenOutStream.setPressure(waterInlet.getPressure("bara"), "bara");
    hydrogenOutStream.setTemperature(waterInlet.getTemperature("K"), "K");
    hydrogenOutStream.run(id);

    oxygenOutStream.setFlowRate(oxygenFlow, "mole/sec");
    oxygenOutStream.setPressure(waterInlet.getPressure("bara"), "bara");
    oxygenOutStream.setTemperature(waterInlet.getTemperature("K"), "K");
    oxygenOutStream.run(id);

    // If an I-V model is set, recompute the operating cell voltage from current density and T.
    if (ivCharacteristic != null) {
      double temperatureK = waterInlet.getTemperature("K");
      cellVoltage = ivCharacteristic.getCellVoltage(currentDensity, temperatureK);
    }

    // Total current is computed from the actual H2 produced (faradaic efficiency already applied).
    double current = hydrogenFlow * 2.0 * FARADAY_CONSTANT;
    stackPower = current * cellVoltage;
    energyStream.setDuty(stackPower);
    setEnergyStream(true);
  }

  /**
   * Set the electrolyzer technology. Applies the technology's default cell voltage, current
   * density, and faradaic efficiency unless they have already been set explicitly. Does not
   * override an already-attached I-V characteristic.
   *
   * @param technology technology selector
   */
  public void setTechnology(ElectrolyzerTechnology technology) {
    if (technology == null) {
      throw new IllegalArgumentException("technology must not be null");
    }
    this.technology = technology;
    this.cellVoltage = technology.getDefaultCellVoltage();
    this.currentDensity = technology.getDefaultCurrentDensity();
    this.faradaicEfficiency = technology.getDefaultFaradaicEfficiency();
  }

  /**
   * Get the electrolyzer technology, or {@code null} if not set.
   *
   * @return technology, or null
   */
  public ElectrolyzerTechnology getTechnology() {
    return technology;
  }

  /**
   * Attach an I-V characteristic. When set, the cell voltage is recomputed during
   * {@link #run(UUID)} from the current density and feed temperature.
   *
   * @param ivCharacteristic polarisation model, or {@code null} to disable
   */
  public void setIVCharacteristic(ElectrolyzerIVCharacteristic ivCharacteristic) {
    this.ivCharacteristic = ivCharacteristic;
  }

  /**
   * Get the attached I-V characteristic.
   *
   * @return polarisation model, or {@code null} if none
   */
  public ElectrolyzerIVCharacteristic getIVCharacteristic() {
    return ivCharacteristic;
  }

  /**
   * Set the operating current density (A/cm2). Used together with an attached I-V characteristic to
   * compute the cell voltage during {@link #run(UUID)}.
   *
   * @param currentDensity current density (A/cm2), must be non-negative
   */
  public void setCurrentDensity(double currentDensity) {
    if (currentDensity < 0.0) {
      throw new IllegalArgumentException("currentDensity must be non-negative");
    }
    this.currentDensity = currentDensity;
  }

  /**
   * Get the operating current density.
   *
   * @return current density (A/cm2)
   */
  public double getCurrentDensity() {
    return currentDensity;
  }

  /**
   * Set the faradaic (current) efficiency.
   *
   * @param faradaicEfficiency fraction of stack current that actually splits water, 0..1
   */
  public void setFaradaicEfficiency(double faradaicEfficiency) {
    if (faradaicEfficiency <= 0.0 || faradaicEfficiency > 1.0) {
      throw new IllegalArgumentException(
          "faradaicEfficiency must be in (0,1], got " + faradaicEfficiency);
    }
    this.faradaicEfficiency = faradaicEfficiency;
  }

  /**
   * Get the faradaic (current) efficiency.
   *
   * @return faradaic efficiency, 0..1
   */
  public double getFaradaicEfficiency() {
    return faradaicEfficiency;
  }

  /**
   * Get the stack electrical power computed by the last {@link #run(UUID)}.
   *
   * @return stack power (W)
   */
  public double getStackPower() {
    return stackPower;
  }

  /**
   * Get the specific energy consumption in kWh per kg of hydrogen produced, based on the most
   * recent {@link #run(UUID)}. Returns {@code 0.0} if no hydrogen has been produced.
   *
   * @return specific energy consumption (kWh / kg H2)
   */
  public double getSpecificEnergyConsumption_kWh_per_kg_H2() {
    if (hydrogenOutStream == null) {
      return 0.0;
    }
    double h2MoleFlow = hydrogenOutStream.getFlowRate("mole/sec");
    if (h2MoleFlow <= 0.0) {
      return 0.0;
    }
    double h2MassFlowKgPerHour = h2MoleFlow * MW_H2 * 3600.0;
    if (h2MassFlowKgPerHour <= 0.0) {
      return 0.0;
    }
    double powerKW = stackPower / 1000.0;
    return powerKW / h2MassFlowKgPerHour;
  }
}
