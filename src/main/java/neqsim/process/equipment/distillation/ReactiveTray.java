package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphasePHflash;
import neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphaseTPflash;

/**
 * A distillation tray that performs reactive equilibrium (simultaneous chemical equilibrium + phase
 * equilibrium) instead of standard VLE. Uses the Modified RAND method via
 * {@link ReactiveMultiphasePHflash} for enthalpy-specified trays and
 * {@link ReactiveMultiphaseTPflash} for temperature-specified trays.
 *
 * <p>
 * This enables reactive distillation, where chemical reactions occur simultaneously with separation
 * on each tray. The non-stoichiometric approach automatically discovers reactions from the
 * elemental composition — no explicit reaction specification is needed.
 * </p>
 *
 * <p>
 * Usage: Set reactive trays on a {@link DistillationColumn} via
 * {@code column.setReactive(true, startTray, endTray)} to enable reactions on a range of trays (the
 * reactive section), while keeping the rest as standard VLE trays.
 * </p>
 *
 * @author copilot
 * @version 1.0
 * @see SimpleTray
 * @see ReactiveMultiphasePHflash
 * @see ReactiveMultiphaseTPflash
 */
public class ReactiveTray extends SimpleTray {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ReactiveTray.class);

  /**
   * Constructor for ReactiveTray.
   *
   * @param name name of the tray
   */
  public ReactiveTray(String name) {
    super(name);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Overrides the standard tray flash to use reactive equilibrium. The method follows the same
   * structure as {@link SimpleTray#run(UUID)} but replaces {@code PHflash} with
   * {@link ReactiveMultiphasePHflash} and {@code TPflash} with {@link ReactiveMultiphaseTPflash}.
   * </p>
   */
  @Override
  public void run(UUID id) {
    invalidateOutStreamCache();
    double enthalpy = 0.0;
    boolean changeTo2Phase = false;
    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    if (thermoSystem2.doMultiPhaseCheck()) {
      changeTo2Phase = true;
      thermoSystem2.setMultiPhaseCheck(false);
    }

    if (trayPressure > 0) {
      thermoSystem2.setPressure(trayPressure);
    }
    mixedStream.setThermoSystem(thermoSystem2);

    if (streams.size() > 0) {
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);

      mixStream();
      if (trayPressure > 0) {
        mixedStream.setPressure(trayPressure, "bara");
      }
      enthalpy = calcMixStreamEnthalpy();

      if (isSetOutTemperature()) {
        mixedStream.setTemperature(getOutTemperature(), "K");
      }
    }

    if (isSetOutTemperature()) {
      // Temperature-specified tray: use reactive TP flash
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.getThermoSystem().setTemperature(getOutTemperature());
      }
      runReactiveTPflash(thermoSystem2);
    } else {
      // Standard tray: use reactive PH flash (enthalpy-specified)
      try {
        runReactivePHflash(thermoSystem2, enthalpy);
      } catch (Exception ex) {
        logger.warn("ReactivePHflash failed on tray " + getName()
            + ", falling back to reactive TP flash: " + ex.getMessage());
        try {
          if (!Double.isNaN(getOutTemperature())) {
            mixedStream.getThermoSystem().setTemperature(getOutTemperature());
          }
          runReactiveTPflash(thermoSystem2);
        } catch (Exception ex2) {
          logger.warn("Reactive TPflash fallback also failed on tray " + getName(), ex2);
          // Last resort: standard non-reactive TP flash
          ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
          testOps.TPflash();
        }
      }
    }

    if (Double.isNaN(mixedStream.getTemperature())) {
      if (!Double.isNaN(getOutTemperature())) {
        mixedStream.setTemperature(getOutTemperature());
      }
    }

    setTemperature(mixedStream.getTemperature());
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);

    if (changeTo2Phase) {
      thermoSystem2.setMultiPhaseCheck(true);
    }
  }

  /**
   * Run a reactive TP flash on the tray fluid.
   *
   * @param system the thermodynamic system
   */
  private void runReactiveTPflash(SystemInterface system) {
    ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
    tpFlash.setMaxNumberOfPhases(2);
    tpFlash.run();
    system.init(2);
  }

  /**
   * Run a reactive PH flash on the tray fluid.
   *
   * @param system the thermodynamic system
   * @param enthalpySpec the target enthalpy in J
   */
  private void runReactivePHflash(SystemInterface system, double enthalpySpec) {
    ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, enthalpySpec);
    phFlash.setMaxNumberOfPhases(2);
    phFlash.run();

    if (!phFlash.isConverged()) {
      logger.warn("Reactive PH flash did not converge on tray " + getName() + " after "
          + phFlash.getOuterIterations() + " iterations");
    }
  }

}
