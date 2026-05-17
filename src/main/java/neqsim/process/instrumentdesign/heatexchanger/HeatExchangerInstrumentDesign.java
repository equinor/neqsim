package neqsim.process.instrumentdesign.heatexchanger;

import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Instrument design for heat exchangers.
 *
 * <p>
 * Determines the required instrumentation for heat exchangers (shell-and-tube, air coolers,
 * electric heaters). A heat exchanger typically requires:
 * </p>
 * <ul>
 * <li>Temperature: inlet TT, outlet TT (both sides for shell-and-tube)</li>
 * <li>Pressure: inlet PT, outlet PT (both sides for S&amp;T), differential pressure</li>
 * <li>Flow: outlet FT (optional, for duty calculation)</li>
 * <li>Safety: TSHH (overtemperature), TSLL (undertemperature for hydrate/freeze protection)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class HeatExchangerInstrumentDesign extends InstrumentDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Heat exchanger type enumeration.
   */
  public enum HeatExchangerType {
    /** Shell and tube heat exchanger. */
    SHELL_AND_TUBE,
    /** Air cooler (fin-fan). */
    AIR_COOLER,
    /** Electric heater. */
    ELECTRIC_HEATER
  }

  /** Type of heat exchanger. */
  private HeatExchangerType heatExchangerType = HeatExchangerType.SHELL_AND_TUBE;

  /**
   * Constructor for HeatExchangerInstrumentDesign.
   *
   * @param processEquipment the heat exchanger equipment
   */
  public HeatExchangerInstrumentDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    autoDetectType(processEquipment);
  }

  /**
   * Auto-detect heat exchanger type from equipment class.
   *
   * @param equipment the process equipment
   */
  private void autoDetectType(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName();
    if ("Cooler".equals(className)) {
      this.heatExchangerType = HeatExchangerType.AIR_COOLER;
    } else if ("Heater".equals(className)) {
      this.heatExchangerType = HeatExchangerType.ELECTRIC_HEATER;
    } else {
      this.heatExchangerType = HeatExchangerType.SHELL_AND_TUBE;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    // === Process side temperature ===
    getInstrumentList().add(
        new InstrumentSpecification("TT", "Process Inlet Temperature", -50.0, 400.0, "degC", "AI"));
    getInstrumentList().add(new InstrumentSpecification("TT", "Process Outlet Temperature", -50.0,
        400.0, "degC", "AI"));

    // === Process side pressure ===
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Process Inlet Pressure", 0.0, 200.0, "bara", "AI"));
    getInstrumentList().add(
        new InstrumentSpecification("PT", "Process Outlet Pressure", 0.0, 200.0, "bara", "AI"));

    // === Differential pressure (fouling / blockage detection) ===
    getInstrumentList()
        .add(new InstrumentSpecification("PDT", "Process Side dP", 0.0, 10.0, "bar", "AI"));

    // === Safety: overtemperature trip ===
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(
          new InstrumentSpecification("TSHH", "Overtemperature Trip", "DI", getDefaultSilLevel()));
    }

    // === Type-specific instruments ===
    switch (heatExchangerType) {
      case SHELL_AND_TUBE:
        addShellAndTubeInstruments();
        break;
      case AIR_COOLER:
        addAirCoolerInstruments();
        break;
      case ELECTRIC_HEATER:
        addElectricHeaterInstruments();
        break;
      default:
        break;
    }
  }

  /**
   * Add shell-and-tube-specific instruments (utility side T and P).
   */
  private void addShellAndTubeInstruments() {
    getInstrumentList().add(
        new InstrumentSpecification("TT", "Utility Inlet Temperature", -50.0, 400.0, "degC", "AI"));
    getInstrumentList().add(new InstrumentSpecification("TT", "Utility Outlet Temperature", -50.0,
        400.0, "degC", "AI"));
    getInstrumentList()
        .add(new InstrumentSpecification("PT", "Utility Inlet Pressure", 0.0, 50.0, "bara", "AI"));
  }

  /**
   * Add air cooler-specific instruments (fan and air-side).
   */
  private void addAirCoolerInstruments() {
    getInstrumentList().add(
        new InstrumentSpecification("TT", "Ambient Air Temperature", -40.0, 60.0, "degC", "AI"));
    // Fan speed/status
    getInstrumentList()
        .add(new InstrumentSpecification("ST", "Fan Speed", 0.0, 1800.0, "rpm", "AI"));
    // Fan vibration
    getInstrumentList()
        .add(new InstrumentSpecification("VT", "Fan Vibration", 0.0, 100.0, "mm/s", "AI"));
  }

  /**
   * Add electric heater-specific instruments (element monitoring).
   */
  private void addElectricHeaterInstruments() {
    // Element temperature monitoring
    getInstrumentList().add(
        new InstrumentSpecification("TT", "Heating Element Temperature", 0.0, 600.0, "degC", "AI"));
    // Overtemperature trip on element
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(new InstrumentSpecification("TSHH", "Element Overtemperature Trip",
          "DI", getDefaultSilLevel()));
    }
  }

  /**
   * Get the heat exchanger type.
   *
   * @return heat exchanger type
   */
  public HeatExchangerType getHeatExchangerType() {
    return heatExchangerType;
  }

  /**
   * Set the heat exchanger type.
   *
   * @param heatExchangerType heat exchanger type
   */
  public void setHeatExchangerType(HeatExchangerType heatExchangerType) {
    this.heatExchangerType = heatExchangerType;
  }
}
