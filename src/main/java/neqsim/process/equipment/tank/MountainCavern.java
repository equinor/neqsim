package neqsim.process.equipment.tank;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Underground rock ("mountain") cavern storage vessel with a non-equilibrium drop-line gas migration model.
 *
 * <p>
 * Represents a large, fixed-total-volume rock cavern used for crude oil storage, fed through a vertical "drop line"
 * from the surface. Two effects, both grounded in how such caverns and their piping are typically configured, combine
 * to shape the vessel pressure response to a downstream process stop:
 * </p>
 *
 * <ol>
 * <li><b>Fixed-volume compression.</b> As liquid keeps arriving and the withdrawal is reduced or stopped, the gas cap
 * shrinks and any light ends still evolving from the crude are compressed into a smaller volume, so the vessel pressure
 * that a fixed-volume flash would predict rises - potentially quickly at first, since the incoming liquid is furthest
 * from equilibrium with the (initially low) cavern pressure.</li>
 * <li><b>Non-equilibrium relaxation ("gas mixing into the drop line").</b> The measured pressure does not track that
 * instantaneous fixed-volume-equilibrium value directly: gas from the cavern gas cap has to migrate against the
 * incoming liquid in the drop line (and be swept back into solution, or vice versa) before the vessel reaches the new
 * equilibrium, which is approximated here as a first-order relaxation of the actual pressure toward the instantaneous
 * equilibrium target, with a configurable time constant ({@link #setDropLineRelaxationTime}). A large time constant
 * means slow, incomplete gas-liquid contact in the drop line and a pressure response that visibly lags (and therefore
 * rises more slowly than) the underlying equilibrium trend.</li>
 * </ol>
 *
 * <p>
 * A simple pressure-relief characteristic (a proportional vent law above {@link #enableVent}, representing a mechanical
 * pressure-control valve venting to a flare/vent stack) can additionally cap the equilibrium target itself, so that
 * both a gradually decelerating rise (from the relaxation) and an eventual flattening (from the vent) are captured with
 * the one model.
 * </p>
 *
 * <p>
 * The cavern is modelled isothermally (a large rock mass is a very large, near-constant- temperature thermal reservoir
 * compared to the timescale of a downstream process stop), using
 * {@link ThermodynamicOperations#TVflash(double, String)} rather than the internal-energy based approach used by
 * {@link Tank}. Component moles are mutated only through
 * {@link neqsim.thermo.component.ComponentInterface#addMolesChemReac(double)} on the existing feed composition, so -
 * unlike {@link Tank#run} / {@link Tank#runTransient} - characterised (TBP/plus-fraction) feed fluids are supported
 * without requiring their pseudo-components to be registered in the shared component database.
 * </p>
 *
 * <p>
 * This is a screening-level model: the drop-line contact itself is not solved as a coupled hydraulic/mass-transfer
 * problem, only approximated by the single relaxation time constant above.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MountainCavern extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;
  private static final Logger logger = LogManager.getLogger(MountainCavern.class);

  /**
   * {@link neqsim.thermo.component.ComponentInterface#addMolesChemReac(double)} updates a component's own per-phase and
   * per-component-total mole bookkeeping, but not the SYSTEM-level total ({@code SystemThermo} field
   * {@code totalNumberOfMoles} is a plain scalar). This resynchronises that scalar by summing every phase/component
   * after any direct mole manipulation touching a SINGLE phase (feed addition into one phase, withdrawal from one
   * phase, venting from one phase), so a subsequent flash sees the correct total. Do not reuse this pattern across
   * MULTIPLE phases of the same logical component in one call - see {@link #scaleAllMoles} for why.
   *
   * @param system system to resynchronise (mutated in place)
   */
  private static void resyncTotalMoles(SystemInterface system) {
    double sum = 0.0;
    for (int j = 0; j < system.getNumberOfPhases(); j++) {
      for (int i = 0; i < system.getPhase(j).getNumberOfComponents(); i++) {
        sum += system.getPhase(j).getComponent(i).getNumberOfMolesInPhase();
      }
    }
    // setTotalNumberOfMolesRaw (unlike the public setTotalNumberOfMoles) does not rescale the
    // per-component mole numbers: it must not, since those are already correct here (updated via
    // addMolesChemReac alongside this call) - rescaling them again against the OLD, pre-mutation
    // total would silently redistribute moles toward a stale composition and compound further
    // on every subsequent call.
    system.setTotalNumberOfMolesRaw(sum);
  }

  /**
   * Scale the whole inventory by the same factor (in place), preserving composition and phase split. Delegates directly
   * to {@link SystemInterface#setTotalNumberOfMoles(double)}, which already rescales every component's mole numbers
   * proportionally against the system's own (self-consistent) existing composition - looping over phases and calling
   * {@link neqsim.thermo.component.ComponentInterface#addMolesChemReac(double)} once PER PHASE a logical component
   * appears in would double (or N-times) count that component's per-component mole total, since each phase holds its
   * own component object/bookkeeping.
   *
   * @param system system to rescale (mutated in place)
   * @param factor multiplier applied to the whole inventory
   */
  private static void scaleAllMoles(SystemInterface system, double factor) {
    system.setTotalNumberOfMoles(system.getTotalNumberOfMoles() * factor);
  }

  /**
   * Flash a clone of the given system at the configured fixed cavern volume and report the resulting liquid volume
   * fraction. Does not mutate the input system.
   *
   * @param system system to flash (will be cloned internally)
   * @return liquid volume fraction (liquid volume / cavern volume), 0-1
   */
  private double liquidVolumeFractionAtFixedVolume(SystemInterface system) {
    SystemInterface clone = system.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(clone);
    try {
      ops.TVflash(totalVolume, "m3");
    } catch (RuntimeException ex) {
      // Treat a failed (near-degenerate/over-compressed) trial as "too much liquid" so the
      // bisection backs off toward a lower scale factor rather than propagating the failure.
      return 1.0;
    }
    if (!clone.hasPhaseType("oil")) {
      return 0.0;
    }
    double liquidVolume = clone.getPhase(clone.getPhaseNumberOfPhase("oil")).getVolume() * 1.0e-5;
    return Math.min(1.0, liquidVolume / totalVolume);
  }

  private StreamInterface inletStream;
  private Stream gasOutStream;
  private Stream liquidOutStream;

  /** Persistent cavern inventory (all moles ever received, minus vented gas and withdrawn liquid). */
  private SystemInterface accumulatedSystem;

  private double totalVolume = 100000.0; // m3
  private double cavernTemperature = 288.15; // K, isothermal assumption

  /** Reported (lagged, non-equilibrium) cavern pressure, bara. */
  private double actualPressure = 1.5;

  /** Relaxation time constant for the drop-line non-equilibrium gas migration, in hours. */
  private double dropLineRelaxationTimeHours = 6.0;

  /** Liquid product withdrawal rate (e.g. the downstream feed-pump/SCUP rate), kg/hr. */
  private double liquidWithdrawalRate = 0.0;

  private boolean ventEnabled = false;
  private double ventSetpointPressure = 1.5; // bara
  private double ventMaxMolarRatePerHour = 1.0e6; // mol/hr, effective vent capacity

  private double lastVentedMolesPerHour = 0.0;

  /** Assumed starting liquid level fraction (0-1) used to seed the initial inventory in {@link #run}. */
  private double initialLiquidLevelFraction = 0.5;

  /** Pressure used to seed the initial inventory in {@link #run}, bara. */
  private double initialPressureGuess = 1.5;

  /**
   * Constructor for MountainCavern.
   *
   * @param name name of the cavern
   */
  public MountainCavern(String name) {
    super(name);
  }

  /**
   * Constructor for MountainCavern.
   *
   * @param name name of the cavern
   * @param inletStream the drop-line feed stream
   */
  public MountainCavern(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * Set the drop-line feed stream and (re-)initialise the persistent cavern inventory from its composition.
   *
   * @param inletStream drop-line feed stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    accumulatedSystem = inletStream.getThermoSystem().clone();
    cavernTemperature = inletStream.getTemperature("K");
    gasOutStream = new Stream("gasOutStream", accumulatedSystem.getEmptySystemClone());
    liquidOutStream = new Stream("liquidOutStream", accumulatedSystem.getEmptySystemClone());
  }

  /**
   * Set the assumed starting liquid level fraction and pressure used to seed the initial inventory in
   * {@link #run(UUID)} (a real cavern does not start empty).
   *
   * @param liquidLevelFraction starting liquid level fraction, 0-1
   * @param pressureGuessBara starting pressure guess, bara
   */
  public void setInitialCondition(double liquidLevelFraction, double pressureGuessBara) {
    if (liquidLevelFraction < 0.0 || liquidLevelFraction > 1.0) {
      throw new IllegalArgumentException("liquidLevelFraction must be between 0 and 1");
    }
    this.initialLiquidLevelFraction = liquidLevelFraction;
    this.initialPressureGuess = pressureGuessBara;
  }

  /**
   * Getter for the field <code>gasOutStream</code>.
   *
   * @return the vented gas stream
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * Getter for the field <code>liquidOutStream</code>.
   *
   * @return the withdrawn liquid product stream
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * Copy another cavern's persistent inventory and reported pressure into this one (a deep clone, not a shared
   * reference), e.g. to fork a scenario from a common, already-seeded starting state - useful for comparing two
   * configurations (different relaxation time constants, vent settings, etc.) from an identical mass/composition basis
   * instead of two independently-seeded ones, which need not converge to bit-identical states.
   *
   * @param other cavern whose inventory and reported pressure are copied
   */
  public void copyInventoryFrom(MountainCavern other) {
    this.accumulatedSystem = other.accumulatedSystem.clone();
    this.actualPressure = other.actualPressure;
  }

  /**
   * Configure the fixed total cavern volume.
   *
   * @param totalVolume total volume in cubic meter
   */
  public void setTotalVolume(double totalVolume) {
    if (totalVolume <= 0.0) {
      throw new IllegalArgumentException("totalVolume must be positive");
    }
    this.totalVolume = totalVolume;
  }

  /**
   * Getter for the field <code>totalVolume</code>.
   *
   * @return total volume, m3
   */
  public double getTotalVolume() {
    return totalVolume;
  }

  /**
   * Set the isothermal cavern (rock) temperature.
   *
   * @param temperature temperature value
   * @param unit temperature unit, e.g. "C" or "K"
   */
  public void setCavernTemperature(double temperature, String unit) {
    this.cavernTemperature = "C".equalsIgnoreCase(unit) ? temperature + 273.15 : temperature;
  }

  /**
   * Set the drop-line non-equilibrium relaxation time constant. Larger values mean slower, more incomplete gas-liquid
   * contact in the drop line, and a more strongly damped pressure response relative to the instantaneous fixed-volume
   * equilibrium.
   *
   * @param hours relaxation time constant, hours
   */
  public void setDropLineRelaxationTime(double hours) {
    if (hours < 0.0) {
      throw new IllegalArgumentException("relaxation time must be non-negative");
    }
    this.dropLineRelaxationTimeHours = hours;
  }

  /**
   * Getter for the field <code>dropLineRelaxationTimeHours</code>.
   *
   * @return relaxation time constant, hours
   */
  public double getDropLineRelaxationTime() {
    return dropLineRelaxationTimeHours;
  }

  /**
   * Set the liquid product withdrawal rate (e.g. the SCUP feed-pump rate). Set to zero to represent a downstream stop.
   *
   * @param kgPerHour withdrawal rate, kg/hr
   */
  public void setLiquidWithdrawalRate(double kgPerHour) {
    this.liquidWithdrawalRate = kgPerHour;
  }

  /**
   * Enable the pressure-relief vent characteristic (a simplified stand-in for a mechanical pressure-control valve
   * venting to a flare/vent stack).
   *
   * @param setpointPressureBara pressure above which the vent starts removing gas, bara
   * @param maxMolarRatePerHour maximum vent capacity, mol/hr
   */
  public void enableVent(double setpointPressureBara, double maxMolarRatePerHour) {
    this.ventEnabled = true;
    this.ventSetpointPressure = setpointPressureBara;
    this.ventMaxMolarRatePerHour = maxMolarRatePerHour;
  }

  /**
   * Getter for the last-computed vent rate.
   *
   * @return vented amount over the last time step, expressed as an equivalent mol/hr rate
   */
  public double getLastVentedMolesPerHour() {
    return lastVentedMolesPerHour;
  }

  /**
   * Getter for the (lagged, non-equilibrium) cavern pressure.
   *
   * @return pressure, bara
   */
  public double getCavernPressure() {
    return actualPressure;
  }

  /**
   * Compute the instantaneous fixed-volume-equilibrium pressure of a clone of the given system, applying the vent
   * relief if enabled. Does not mutate the input system.
   *
   * @param system system to flash (will be cloned internally)
   * @param dtHours elapsed time since the previous call, used to rate-limit the vent removal to at most
   * {@code ventMaxMolarRatePerHour * dtHours}; pass a large value (e.g. for the one-off initial seeding in
   * {@link #run}) to treat the vent as unconstrained by time
   * @return equilibrium pressure after any vent relief, bara
   */
  private double equilibriumPressureAfterVent(SystemInterface system, double dtHours) {
    SystemInterface clone = system.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(clone);
    // Deliberately NOT warm-started from the caller's lagged, reported actualPressure: this
    // instantaneous equilibrium target must depend only on the persistent inventory's own
    // moles/composition (system), not on the relaxation history of whichever caller happens to
    // invoke it - otherwise two instances with identical inventories but different relaxation
    // time constants (and therefore different actualPressure histories) would see different
    // equilibrium targets purely as a warm-start artifact.
    clone.setTemperature(cavernTemperature);
    double pressure;
    try {
      ops.TVflash(totalVolume, "m3");
      pressure = clone.getPressure();
    } catch (RuntimeException ex) {
      logger.warn("MountainCavern fixed-volume flash failed; retaining previous pressure", ex);
      return actualPressure;
    }

    lastVentedMolesPerHour = 0.0;
    if (ventEnabled && pressure > ventSetpointPressure && clone.hasPhaseType("gas")) {
      int gasPhaseIndex = clone.getPhaseNumberOfPhase("gas");
      double remainingCapacity = ventMaxMolarRatePerHour * Math.max(dtHours, 1.0e-6);
      for (int iter = 0; iter < 20 && pressure > ventSetpointPressure && remainingCapacity > 0.0; iter++) {
        double gasMoles = clone.getPhase(gasPhaseIndex).getNumberOfMolesInPhase();
        if (gasMoles <= 0.0) {
          break;
        }
        // Remove as much as the vent capacity allows (up to 90% of the current gas phase per
        // iteration, leaving a small margin to avoid a degenerate near-zero-gas edge case).
        double removed = Math.min(0.9 * gasMoles, remainingCapacity);
        if (removed <= 0.0) {
          break;
        }
        for (int i = 0; i < clone.getPhase(gasPhaseIndex).getNumberOfComponents(); i++) {
          double xi = clone.getPhase(gasPhaseIndex).getComponent(i).getx();
          clone.getPhase(gasPhaseIndex).getComponent(i).addMolesChemReac(-removed * xi);
        }
        resyncTotalMoles(clone);
        lastVentedMolesPerHour += removed;
        remainingCapacity -= removed;
        try {
          ops.TVflash(totalVolume, "m3");
          pressure = clone.getPressure();
        } catch (RuntimeException ex) {
          logger.warn("MountainCavern vent-relief flash failed; stopping vent iteration", ex);
          break;
        }
      }
    }
    return pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    inletStream.run(id);

    // Seed a physically sensible starting inventory (a real cavern does not start empty): flash
    // the feed's own composition at the initial pressure guess, then scale ALL moles uniformly
    // (same factor in every phase/component) so the resulting equilibrium volume equals the
    // configured cavern volume. Scaling every component by the same factor at fixed temperature
    // leaves the equilibrium pressure and phase split unchanged (volume is extensive), so the
    // seeded inventory reproduces close to initialPressureGuess without any double counting of
    // dissolved-vs-free light ends.
    SystemInterface seed = inletStream.getThermoSystem().clone();
    seed.setTemperature(cavernTemperature);
    seed.setPressure(initialPressureGuess);
    ThermodynamicOperations seedOps = new ThermodynamicOperations(seed);
    seedOps.TPflash();

    double seedVolume = seed.getVolume("m3");
    double scaleFactor = totalVolume / Math.max(seedVolume, 1e-12);

    // Bisect on the scale factor so the fixed-volume equilibrium liquid level matches the
    // configured initial liquid level fraction as closely as this feed composition allows
    // (more total moles compressed into the same fixed cavern volume shifts the equilibrium
    // toward more liquid, so the fraction is monotonic in the scale factor for a
    // liquid+vapour system near saturation).
    double loScale = scaleFactor * 0.05;
    double hiScale = scaleFactor * 20.0;
    for (int iter = 0; iter < 25; iter++) {
      double trialScale = 0.5 * (loScale + hiScale);
      SystemInterface trial = seed.clone();
      scaleAllMoles(trial, trialScale);
      double liquidFrac = liquidVolumeFractionAtFixedVolume(trial);
      if (liquidFrac < initialLiquidLevelFraction) {
        loScale = trialScale;
      } else {
        hiScale = trialScale;
      }
    }
    scaleFactor = 0.5 * (loScale + hiScale);

    accumulatedSystem = seed.clone();
    scaleAllMoles(accumulatedSystem, scaleFactor);

    actualPressure = equilibriumPressureAfterVent(accumulatedSystem, 1.0e9);
    updateOutletStreams(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    inletStream.run(id);
    SystemInterface feed = inletStream.getThermoSystem();
    // SystemInterface.getTotalNumberOfMoles() is a molar RATE in mol/sec (consistent with
    // getFlowRate("kg/sec") = totalNumberOfMoles * molarMass); convert to mol/hr.
    double feedMolarFlow = feed.getTotalNumberOfMoles() * 3600.0;

    // Add the moles that arrived over dt to the persistent inventory.
    for (int i = 0; i < accumulatedSystem.getPhase(0).getNumberOfComponents(); i++) {
      double zi = feed.getPhase(0).getComponent(i).getz();
      accumulatedSystem.getPhase(0).getComponent(i).addMolesChemReac(feedMolarFlow * zi * dt);
    }
    resyncTotalMoles(accumulatedSystem);

    // Withdraw liquid product, if any, at the CURRENT (lagged) apparent liquid composition.
    if (liquidWithdrawalRate > 0.0) {
      SystemInterface reportingClone = accumulatedSystem.clone();
      ThermodynamicOperations reportOps = new ThermodynamicOperations(reportingClone);
      reportingClone.setTemperature(cavernTemperature);
      reportingClone.setPressure(actualPressure);
      try {
        reportOps.TPflash();
      } catch (RuntimeException ex) {
        logger.warn("MountainCavern withdrawal flash failed; skipping withdrawal this step", ex);
        reportingClone = null;
      }
      if (reportingClone != null && reportingClone.hasPhaseType("oil")) {
        int oilIdx = reportingClone.getPhaseNumberOfPhase("oil");
        double oilMolarMass = reportingClone.getPhase(oilIdx).getMolarMass();
        double withdrawMolesPerHour = liquidWithdrawalRate / Math.max(oilMolarMass, 1e-6);
        double oilMolesAvailable = reportingClone.getPhase(oilIdx).getNumberOfMolesInPhase();
        double withdrawMoles = Math.min(withdrawMolesPerHour * dt, 0.9 * oilMolesAvailable);
        for (int i = 0; i < reportingClone.getPhase(oilIdx).getNumberOfComponents(); i++) {
          double xi = reportingClone.getPhase(oilIdx).getComponent(i).getx();
          accumulatedSystem.getPhase(0).getComponent(i).addMolesChemReac(-withdrawMoles * xi);
        }
        resyncTotalMoles(accumulatedSystem);
      }
    }

    // Instantaneous fixed-volume-equilibrium target (after any vent relief).
    double equilibriumPressure = equilibriumPressureAfterVent(accumulatedSystem, dt);

    // Non-equilibrium relaxation of the reported pressure toward that target ("gas mixing
    // into the drop line" lag).
    if (dropLineRelaxationTimeHours <= 1.0e-9) {
      actualPressure = equilibriumPressure;
    } else {
      double relax = 1.0 - Math.exp(-dt / dropLineRelaxationTimeHours);
      actualPressure += (equilibriumPressure - actualPressure) * relax;
    }

    updateOutletStreams(id);
    setCalculationIdentifier(id);
    increaseTime(dt);
  }

  /**
   * Refresh the reporting streams (level/composition) from a flash of the persistent inventory at the current (lagged)
   * pressure - a derived/reporting quantity, not part of the mass balance itself. Failures here are non-fatal (e.g. an
   * extreme, near-degenerate state at the edge of the model's validity): the previous reporting streams are simply left
   * unchanged.
   *
   * @param id calculation id
   */
  private void updateOutletStreams(UUID id) {
    SystemInterface reportingClone = accumulatedSystem.clone();
    reportingClone.setTemperature(cavernTemperature);
    reportingClone.setPressure(actualPressure);
    ThermodynamicOperations reportOps = new ThermodynamicOperations(reportingClone);
    try {
      reportOps.TPflash();
    } catch (RuntimeException ex) {
      logger.warn("MountainCavern reporting flash failed; keeping previous outlet streams", ex);
      return;
    }
    if (reportingClone.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(reportingClone, "gas");
    } else {
      gasOutStream.setThermoSystemFromPhase(reportingClone.getEmptySystemClone(), "gas");
    }
    if (reportingClone.hasPhaseType("oil")) {
      liquidOutStream.setThermoSystemFromPhase(reportingClone, "oil");
    } else {
      liquidOutStream.setThermoSystemFromPhase(reportingClone.getEmptySystemClone(), "oil");
    }
    gasOutStream.run(id);
    liquidOutStream.run(id);
  }

  /**
   * Get the current apparent liquid level fraction (liquid volume / total volume), derived from a flash of the
   * persistent inventory at the current (lagged) pressure.
   *
   * @return liquid level fraction, 0-1
   */
  public double getLiquidLevelFraction() {
    SystemInterface reportingClone = accumulatedSystem.clone();
    reportingClone.setTemperature(cavernTemperature);
    reportingClone.setPressure(actualPressure);
    ThermodynamicOperations reportOps = new ThermodynamicOperations(reportingClone);
    try {
      reportOps.TPflash();
      reportingClone.initPhysicalProperties();
    } catch (RuntimeException ex) {
      logger.warn("MountainCavern liquid-level flash failed; reporting last known level as full", ex);
      return 1.0;
    }
    if (!reportingClone.hasPhaseType("oil")) {
      return 0.0;
    }
    double liquidVolume = reportingClone.getPhase(reportingClone.getPhaseNumberOfPhase("oil")).getVolume() * 1.0e-5;
    return Math.min(1.0, liquidVolume / totalVolume);
  }
}
