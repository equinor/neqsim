package neqsim.process.instrumentdesign.valve;

import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Instrument design for control and safety valves.
 *
 * <p>
 * Determines the required instrumentation for a valve. Typical instrumentation includes:
 * </p>
 * <ul>
 * <li>Position: ZT (valve position transmitter)</li>
 * <li>Positioner: ZC (I/P converter or digital positioner, AO)</li>
 * <li>Solenoid: XV (trip solenoid for ESD valves, DO)</li>
 * <li>Limit switches: ZSO (open), ZSC (closed)</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ValveInstrumentDesign extends InstrumentDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Whether this is a safety/ESD valve (adds solenoid and limit switches). */
  private boolean safetyValve = false;

  /**
   * Constructor for ValveInstrumentDesign.
   *
   * @param processEquipment the valve equipment
   */
  public ValveInstrumentDesign(ProcessEquipmentInterface processEquipment) {
    super(processEquipment);
    autoDetectSafetyValve(processEquipment);
  }

  /**
   * Auto-detect if valve is an ESD/safety valve.
   *
   * @param equipment the equipment
   */
  private void autoDetectSafetyValve(ProcessEquipmentInterface equipment) {
    String className = equipment.getClass().getSimpleName();
    if ("ESDValve".equals(className) || "HIPPSValve".equals(className)
        || "BlowdownValve".equals(className)) {
      this.safetyValve = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    // === Position feedback ===
    getInstrumentList()
        .add(new InstrumentSpecification("ZT", "Valve Position", 0.0, 100.0, "%", "AI"));

    // === Positioner output (control signal to valve) ===
    getInstrumentList()
        .add(new InstrumentSpecification("ZC", "Valve Positioner Output", 0.0, 100.0, "%", "AO"));

    if (safetyValve) {
      // === Solenoid valve (trip) ===
      getInstrumentList()
          .add(new InstrumentSpecification("XV", "Trip Solenoid", "DO", getDefaultSilLevel()));

      // === Limit switches ===
      getInstrumentList().add(new InstrumentSpecification("ZSO", "Open Limit Switch", "DI", 0));
      getInstrumentList().add(new InstrumentSpecification("ZSC", "Closed Limit Switch", "DI", 0));
    }
  }

  /**
   * Check if this is a safety valve.
   *
   * @return true if safety/ESD valve
   */
  public boolean isSafetyValve() {
    return safetyValve;
  }

  /**
   * Set whether this is a safety valve.
   *
   * @param safetyValve true for safety/ESD valve
   */
  public void setSafetyValve(boolean safetyValve) {
    this.safetyValve = safetyValve;
  }
}
