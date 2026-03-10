package neqsim.process.instrumentdesign.separator;

import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;

/**
 * Instrument design for separators.
 *
 * <p>
 * Determines the required instrumentation for a separator vessel following typical oil and gas
 * practice. A separator typically requires:
 * </p>
 * <ul>
 * <li>Pressure: 2x PT (voted/redundant), 1x PSH (high pressure switch, SIL-rated)</li>
 * <li>Temperature: 1x TT (process temperature)</li>
 * <li>Level: 2x LT (voted/redundant), 1x LSH (high level switch), 1x LSLL (low-low level switch,
 * SIL-rated)</li>
 * <li>Control outputs: 1x LCV (level control valve), 1x PCV (pressure control valve)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SeparatorInstrumentDesign extends InstrumentDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Whether this is a three-phase separator (adds water level instruments). */
  private boolean threePhase = false;

  /**
   * Constructor for SeparatorInstrumentDesign.
   *
   * @param processEquipment the separator equipment
   */
  public SeparatorInstrumentDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    if (processEquipment instanceof neqsim.process.equipment.separator.ThreePhaseSeparator) {
      this.threePhase = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    double maxPressure = getMaxPressure();
    double maxTemp = getMaxTemperature();

    // === Pressure instrumentation ===
    // PT-x001: Process pressure (control)
    getInstrumentList().add(new InstrumentSpecification("PT", "Process Pressure", 0.0,
        maxPressure * 1.2, "bara", "AI"));
    // PT-x002: Process pressure (redundant for voting)
    getInstrumentList().add(new InstrumentSpecification("PT", "Process Pressure (Redundant)", 0.0,
        maxPressure * 1.2, "bara", "AI"));
    // PSH: High pressure switch (safety)
    if (isIncludeSafetyInstruments()) {
      InstrumentSpecification psh =
          new InstrumentSpecification("PSH", "High Pressure Switch", "DI", getDefaultSilLevel());
      getInstrumentList().add(psh);
    }

    // === Temperature instrumentation ===
    // TT: Process temperature
    getInstrumentList().add(new InstrumentSpecification("TT", "Process Temperature", -50.0,
        maxTemp + 50.0, "degC", "AI"));

    // === Level instrumentation ===
    // LT-x001: Liquid level (control)
    getInstrumentList()
        .add(new InstrumentSpecification("LT", "Liquid Level", 0.0, 100.0, "%", "AI"));
    // LT-x002: Liquid level (redundant)
    getInstrumentList()
        .add(new InstrumentSpecification("LT", "Liquid Level (Redundant)", 0.0, 100.0, "%", "AI"));
    // LSH: High level switch (safety)
    if (isIncludeSafetyInstruments()) {
      getInstrumentList()
          .add(new InstrumentSpecification("LSH", "High Level Switch", "DI", getDefaultSilLevel()));
    }
    // LSLL: Low-low level switch (safety - pump protection)
    if (isIncludeSafetyInstruments()) {
      getInstrumentList().add(
          new InstrumentSpecification("LSLL", "Low-Low Level Switch", "DI", getDefaultSilLevel()));
    }

    // === Three-phase separator additions ===
    if (threePhase) {
      // LT: Water/oil interface level
      getInstrumentList().add(
          new InstrumentSpecification("LT", "Water/Oil Interface Level", 0.0, 100.0, "%", "AI"));
      // LCV: Water dump valve feedback
      getInstrumentList().add(
          new InstrumentSpecification("ZT", "Water Dump Valve Position", 0.0, 100.0, "%", "AI"));
    }

    // === Control valve position feedback ===
    // ZT: Level control valve position
    getInstrumentList().add(
        new InstrumentSpecification("ZT", "Level Control Valve Position", 0.0, 100.0, "%", "AI"));
    // ZT: Pressure control valve position
    getInstrumentList().add(new InstrumentSpecification("ZT", "Pressure Control Valve Position",
        0.0, 100.0, "%", "AI"));
  }

  /**
   * Get maximum operating pressure from mechanical design or default.
   *
   * @return maximum pressure in bara
   */
  private double getMaxPressure() {
    try {
      return getProcessEquipment().getMechanicalDesign().getMaxOperationPressure();
    } catch (Exception e) {
      return 100.0;
    }
  }

  /**
   * Get maximum operating temperature from mechanical design or default.
   *
   * @return maximum temperature in degC
   */
  private double getMaxTemperature() {
    try {
      double tempK = getProcessEquipment().getMechanicalDesign().getMaxOperationTemperature();
      return tempK > 0 ? tempK - 273.15 : 200.0;
    } catch (Exception e) {
      return 200.0;
    }
  }

  /**
   * Check if this is a three-phase separator.
   *
   * @return true if three-phase
   */
  public boolean isThreePhase() {
    return threePhase;
  }

  /**
   * Set whether this is a three-phase separator.
   *
   * @param threePhase true for three-phase
   */
  public void setThreePhase(boolean threePhase) {
    this.threePhase = threePhase;
  }
}
