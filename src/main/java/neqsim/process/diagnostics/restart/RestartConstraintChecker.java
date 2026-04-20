package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import neqsim.process.diagnostics.ProcessStateSnapshot;
import neqsim.process.diagnostics.TripEvent;
import neqsim.process.diagnostics.TripType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorState;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Checks whether a process system satisfies the constraints required for a safe restart.
 *
 * <p>
 * Inspects pressures, temperatures, levels, compressor states, and valve positions to determine if
 * the process is in a safe state for restart. Produces a {@link RestartReadiness} assessment.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartConstraintChecker implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;

  /** Maximum allowed pressure fraction vs design for restart (default 80%). */
  private double maxPressureFraction = 0.80;

  /** Minimum required separator level fraction for restart (default 20%). */
  private double minLevelFraction = 0.20;

  /** Maximum allowed separator level fraction for restart (default 80%). */
  private double maxLevelFraction = 0.80;

  /**
   * Constructs a constraint checker for a process system.
   *
   * @param processSystem the process system to check
   */
  public RestartConstraintChecker(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
  }

  /**
   * Sets the maximum allowed pressure fraction (vs design) for restart.
   *
   * @param fraction value between 0 and 1 (default 0.80)
   */
  public void setMaxPressureFraction(double fraction) {
    this.maxPressureFraction = fraction;
  }

  /**
   * Sets the acceptable separator level range for restart.
   *
   * @param minFraction minimum level fraction (default 0.20)
   * @param maxFraction maximum level fraction (default 0.80)
   */
  public void setLevelFractionRange(double minFraction, double maxFraction) {
    this.minLevelFraction = minFraction;
    this.maxLevelFraction = maxFraction;
  }

  /**
   * Evaluates all restart constraints for the current process state.
   *
   * @param tripEvent the trip event (provides context for constraint selection)
   * @param currentTime the current simulation time
   * @return a readiness assessment
   */
  public RestartReadiness check(TripEvent tripEvent, double currentTime) {
    List<RestartReadiness.ConstraintResult> results = new ArrayList<>();

    checkCompressorStates(results);
    checkSeparatorLevels(results);
    checkValvePositions(results);
    checkActiveAlarms(results, tripEvent);

    return new RestartReadiness(results, currentTime);
  }

  /**
   * Checks that all compressors are in a state suitable for startup.
   *
   * @param results list to append results to
   */
  private void checkCompressorStates(List<RestartReadiness.ConstraintResult> results) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor) {
        Compressor comp = (Compressor) eq;
        CompressorState state = comp.getOperatingState();

        if (state == CompressorState.TRIPPED) {
          results.add(new RestartReadiness.ConstraintResult(
              "Compressor " + comp.getName() + " state", RestartReadiness.ConstraintSeverity.FAIL,
              "Compressor is in TRIPPED state — requires operator acknowledgement",
              "Acknowledge compressor trip alarm and reset to STOPPED before restarting"));
        } else if (state == CompressorState.DEPRESSURIZING) {
          results.add(new RestartReadiness.ConstraintResult(
              "Compressor " + comp.getName() + " state", RestartReadiness.ConstraintSeverity.FAIL,
              "Compressor system is depressurising — wait for completion",
              "Wait for depressurisation to complete and settle"));
        } else if (state == CompressorState.STOPPED || state == CompressorState.STANDBY) {
          results.add(new RestartReadiness.ConstraintResult(
              "Compressor " + comp.getName() + " state", RestartReadiness.ConstraintSeverity.PASS,
              "Compressor is " + state.name() + " — ready for startup", "No action required"));
        } else if (state == CompressorState.RUNNING) {
          results.add(new RestartReadiness.ConstraintResult(
              "Compressor " + comp.getName() + " state", RestartReadiness.ConstraintSeverity.PASS,
              "Compressor is already running", "No action required"));
        } else {
          results
              .add(new RestartReadiness.ConstraintResult("Compressor " + comp.getName() + " state",
                  RestartReadiness.ConstraintSeverity.WARNING,
                  "Compressor in state " + state.name() + " — may need manual intervention",
                  "Verify compressor status before proceeding"));
        }
      }
    }
  }

  /**
   * Checks separator liquid levels are within safe range for restart.
   *
   * @param results list to append results to
   */
  private void checkSeparatorLevels(List<RestartReadiness.ConstraintResult> results) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Separator) {
        Separator sep = (Separator) eq;
        double level = sep.getLiquidLevel();

        if (level < minLevelFraction) {
          results.add(new RestartReadiness.ConstraintResult("Separator " + sep.getName() + " level",
              RestartReadiness.ConstraintSeverity.WARNING,
              String.format("Level (%.0f%%) below minimum (%.0f%%)", level * 100.0,
                  minLevelFraction * 100.0),
              "Increase liquid level before restarting to avoid gas breakthrough"));
        } else if (level > maxLevelFraction) {
          results.add(new RestartReadiness.ConstraintResult("Separator " + sep.getName() + " level",
              RestartReadiness.ConstraintSeverity.FAIL,
              String.format("Level (%.0f%%) above maximum (%.0f%%)", level * 100.0,
                  maxLevelFraction * 100.0),
              "Drain separator to below " + (int) (maxLevelFraction * 100) + "% before restart"));
        } else {
          results
              .add(new RestartReadiness.ConstraintResult("Separator " + sep.getName() + " level",
                  RestartReadiness.ConstraintSeverity.PASS,
                  String.format("Level (%.0f%%) within acceptable range (%.0f-%.0f%%)",
                      level * 100.0, minLevelFraction * 100.0, maxLevelFraction * 100.0),
                  "No action required"));
        }
      }
    }
  }

  /**
   * Checks that critical valves are in appropriate positions for restart.
   *
   * @param results list to append results to
   */
  private void checkValvePositions(List<RestartReadiness.ConstraintResult> results) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof ThrottlingValve) {
        ThrottlingValve valve = (ThrottlingValve) eq;
        double opening = valve.getPercentValveOpening();

        if (opening < 1.0) {
          results.add(new RestartReadiness.ConstraintResult(
              "Valve " + valve.getName() + " position", RestartReadiness.ConstraintSeverity.WARNING,
              "Valve is closed (opening=" + String.format("%.0f%%", opening) + ")",
              "Verify valve should be open for restart sequence"));
        } else {
          results.add(new RestartReadiness.ConstraintResult(
              "Valve " + valve.getName() + " position", RestartReadiness.ConstraintSeverity.PASS,
              "Valve opening at " + String.format("%.0f%%", opening), "No action required"));
        }
      }
    }
  }

  /**
   * Checks for active alarms that might block restart.
   *
   * @param results list to append results to
   * @param tripEvent the trip event for context
   */
  private void checkActiveAlarms(List<RestartReadiness.ConstraintResult> results,
      TripEvent tripEvent) {
    try {
      if (processSystem.getAlarmManager() != null) {
        int activeCount = processSystem.getAlarmManager().getActiveAlarms().size();
        if (activeCount > 0) {
          results.add(new RestartReadiness.ConstraintResult("Active alarms",
              RestartReadiness.ConstraintSeverity.WARNING, activeCount + " alarm(s) still active",
              "Review and acknowledge all alarms before restarting"));
        } else {
          results.add(new RestartReadiness.ConstraintResult("Active alarms",
              RestartReadiness.ConstraintSeverity.PASS, "No active alarms", "No action required"));
        }
      }
    } catch (Exception e) {
      // Alarm manager may not be initialised
      results.add(new RestartReadiness.ConstraintResult("Active alarms",
          RestartReadiness.ConstraintSeverity.WARNING,
          "Could not check alarm status: " + e.getMessage(),
          "Manually verify alarm panel before restarting"));
    }
  }
}
