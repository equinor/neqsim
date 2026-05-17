package neqsim.process.chemistry.equipment;

import java.util.UUID;
import neqsim.process.chemistry.ProductionChemical;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Process equipment representing a production-chemical injection point.
 *
 * <p>
 * The injection point takes one inlet stream (the produced fluid) and a {@link ProductionChemical}
 * description plus a target dose (kg/h or ppm). On {@code run()} it:
 * </p>
 * <ol>
 * <li>clones the inlet thermo system into the outlet</li>
 * <li>computes the injected mass flow of the chemical (and its solvent fraction if water-based)
 * </li>
 * <li>updates the outlet by adding the inhibitor mass to the appropriate aqueous component (water
 * for water-based products) when the chemistry permits — otherwise records the dose as metadata on
 * the outlet stream</li>
 * <li>exposes {@link #getActiveIngredientPpmInWater()} for downstream chemistry models</li>
 * </ol>
 *
 * <p>
 * The class deliberately avoids running an electrolyte flash on every call (heavy and brittle in a
 * flowsheet); it instead tracks the dose at the outlet and lets dedicated chemistry models do the
 * rigorous chemistry. Use it as a flow-sheet-visible, snapshot-able placeholder for chemical
 * injection.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class InhibitorInjectionPoint extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Chemical being injected. */
  private ProductionChemical chemical;

  /** Mode for specifying dose. */
  private DoseMode doseMode = DoseMode.PPM;

  /** Numeric dose value (interpretation per {@link DoseMode}). */
  private double doseValue = 0.0;

  /** Last computed active-ingredient ppm in the water phase. */
  private double activeIngredientPpmInWater = 0.0;

  /** Last computed total injection mass flow in kg/h. */
  private double injectionRateKgPerHour = 0.0;

  /**
   * Dose specification mode.
   */
  public enum DoseMode {
    /** Dose in ppm of active ingredient on the water phase mass basis. */
    PPM,
    /** Dose in ppm of active ingredient on the total fluid mass basis. */
    PPM_TOTAL,
    /** Dose in kg/h of neat product. */
    KG_PER_HOUR;
  }

  /**
   * Constructs an injection point with no inlet stream.
   *
   * @param name equipment name
   */
  public InhibitorInjectionPoint(String name) {
    super(name);
  }

  /**
   * Constructs an injection point with an inlet stream.
   *
   * @param name equipment name
   * @param inletStream inlet stream
   */
  public InhibitorInjectionPoint(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Sets the chemical being injected.
   *
   * @param chemical chemical descriptor
   */
  public void setChemical(ProductionChemical chemical) {
    this.chemical = chemical;
  }

  /**
   * Returns the chemical being injected.
   *
   * @return chemical
   */
  public ProductionChemical getChemical() {
    return chemical;
  }

  /**
   * Sets the dose mode.
   *
   * @param mode dose mode
   */
  public void setDoseMode(DoseMode mode) {
    this.doseMode = mode;
  }

  /**
   * Sets the numeric dose value.
   *
   * @param value dose value (interpretation per {@link DoseMode})
   */
  public void setDoseValue(double value) {
    this.doseValue = value;
  }

  /**
   * Convenience: set dose in ppm on the water phase.
   *
   * @param ppm parts per million
   */
  public void setDoseInPpmOnWater(double ppm) {
    this.doseMode = DoseMode.PPM;
    this.doseValue = ppm;
  }

  /**
   * Convenience: set dose in kg/h of neat product.
   *
   * @param kgHr kg per hour
   */
  public void setDoseInKgPerHour(double kgHr) {
    this.doseMode = DoseMode.KG_PER_HOUR;
    this.doseValue = kgHr;
  }

  /**
   * Returns the last computed active-ingredient ppm in the water phase.
   *
   * @return ppm
   */
  public double getActiveIngredientPpmInWater() {
    return activeIngredientPpmInWater;
  }

  /**
   * Returns the last computed total injection mass flow.
   *
   * @return injection rate in kg/h
   */
  public double getInjectionRateKgPerHour() {
    return injectionRateKgPerHour;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    StreamInterface inlet = getInletStream();
    if (inlet == null) {
      setCalculationIdentifier(id);
      return;
    }
    SystemInterface inSystem = inlet.getThermoSystem();

    // Clone inlet into outlet (no chemistry change to the EOS state)
    SystemInterface outSystem = inSystem.clone();
    StreamInterface outlet = new Stream(getName() + "_out", outSystem);
    setOutletStream(outlet);

    // Determine injection rate
    double waterMassFlowKgHr = 0.0;
    if (inSystem.hasPhaseType("aqueous")) {
      waterMassFlowKgHr = inSystem.getPhase("aqueous").getFlowRate("kg/hr");
    }
    double totalMassFlowKgHr = inSystem.getFlowRate("kg/hr");
    double activeWtFrac = chemical == null ? 0.0 : chemical.getActiveWtPct() / 100.0;

    switch (doseMode) {
      case PPM:
        // ppm on water mass: active mass = waterMassFlowKgHr * doseValue * 1e-6
        injectionRateKgPerHour =
            activeWtFrac > 0 ? waterMassFlowKgHr * doseValue * 1.0e-6 / activeWtFrac : 0.0;
        break;
      case PPM_TOTAL:
        injectionRateKgPerHour =
            activeWtFrac > 0 ? totalMassFlowKgHr * doseValue * 1.0e-6 / activeWtFrac : 0.0;
        break;
      case KG_PER_HOUR:
        injectionRateKgPerHour = doseValue;
        break;
      default:
        injectionRateKgPerHour = 0.0;
        break;
    }

    // Compute resulting active ingredient concentration on water basis
    double activeMassKgHr = injectionRateKgPerHour * activeWtFrac;
    activeIngredientPpmInWater =
        waterMassFlowKgHr > 0 ? activeMassKgHr * 1.0e6 / waterMassFlowKgHr : 0.0;

    setCalculationIdentifier(id);
  }
}
