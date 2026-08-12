package neqsim.process.dynamics;

import neqsim.process.ProcessElementInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.HeatExchanger;

/**
 * Conservative resolver for the runtime activation of audited dynamic implementations.
 *
 * <p>
 * This resolver is intentionally type-specific. A generic {@code calculateSteadyState == false} flag records requested
 * execution mode but does not prove that a subclass actually enters a stateful path; individual equipment families can
 * have additional enable flags and physical-parameter prerequisites. Types without an explicit activation audit remain
 * {@link DynamicActivationStatus#UNVERIFIED} rather than being promoted from API presence alone.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DynamicActivationResolver {
  /** Utility class. */
  private DynamicActivationResolver() {
  }

  /**
   * Resolve runtime activation status for one process element.
   *
   * @param element process element to inspect
   * @return activation status; never null
   */
  public static DynamicActivationStatus resolve(ProcessElementInterface element) {
    if (element == null) {
      return DynamicActivationStatus.UNVERIFIED;
    }

    DynamicCapability capability = element.getDynamicCapability();
    if (capability == DynamicCapability.ALGEBRAIC) {
      return DynamicActivationStatus.NOT_APPLICABLE;
    }

    if (element instanceof HeatExchanger) {
      return resolveHeatExchanger((HeatExchanger) element);
    }
    if (element instanceof BatteryStorage) {
      return resolveBatteryStorage((BatteryStorage) element);
    }
    if (element instanceof Expander) {
      return resolveExpander((Expander) element);
    }

    return DynamicActivationStatus.UNVERIFIED;
  }

  /**
   * Human-readable reason for the resolved activation status.
   *
   * @param element process element to inspect
   * @return diagnostic reason suitable for capability reports
   */
  public static String diagnostic(ProcessElementInterface element) {
    if (element == null) {
      return "element is null";
    }

    DynamicCapability capability = element.getDynamicCapability();
    if (capability == DynamicCapability.ALGEBRAIC) {
      return "algebraic relation has no independent stateful path";
    }

    if (element instanceof HeatExchanger) {
      return heatExchangerDiagnostic((HeatExchanger) element);
    }
    if (element instanceof BatteryStorage) {
      return batteryStorageDiagnostic((BatteryStorage) element);
    }
    if (element instanceof Expander) {
      return expanderDiagnostic((Expander) element);
    }

    if (element instanceof SimulationInterface) {
      boolean requested = !((SimulationInterface) element).getCalculateSteadyState();
      return requested ? "dynamic mode is requested, but type-specific runtime activation has not been audited"
          : "dynamic mode is not requested by calculateSteadyState, but type-specific activation has not been audited";
    }
    return "type-specific runtime activation has not been audited";
  }

  private static DynamicActivationStatus resolveHeatExchanger(HeatExchanger exchanger) {
    if (!exchanger.isDynamicModelEnabled()) {
      if (isDynamicModeRequested(exchanger)) {
        return DynamicActivationStatus.INCOMPLETE_CONFIGURATION;
      }
      return DynamicActivationStatus.INACTIVE;
    }
    if (exchanger.getWallMass() <= 0.0 || exchanger.getHeatTransferArea() <= 0.0) {
      return DynamicActivationStatus.INCOMPLETE_CONFIGURATION;
    }
    return DynamicActivationStatus.ACTIVE;
  }

  private static String heatExchangerDiagnostic(HeatExchanger exchanger) {
    if (!exchanger.isDynamicModelEnabled()) {
      return isDynamicModeRequested(exchanger)
          ? "calculateSteadyState requests dynamic mode but dynamicModelEnabled is false"
          : "dynamicModelEnabled is false";
    }
    if (exchanger.getWallMass() <= 0.0 && exchanger.getHeatTransferArea() <= 0.0) {
      return "dynamicModelEnabled is true but wallMass and heatTransferArea are not positive";
    }
    if (exchanger.getWallMass() <= 0.0) {
      return "dynamicModelEnabled is true but wallMass is not positive";
    }
    if (exchanger.getHeatTransferArea() <= 0.0) {
      return "dynamicModelEnabled is true but heatTransferArea is not positive";
    }
    return "dynamic heat-exchanger wall-energy path is active";
  }

  private static DynamicActivationStatus resolveBatteryStorage(BatteryStorage battery) {
    if (battery.getCapacity() <= 0.0) {
      return DynamicActivationStatus.INCOMPLETE_CONFIGURATION;
    }
    return DynamicActivationStatus.ACTIVE;
  }

  private static String batteryStorageDiagnostic(BatteryStorage battery) {
    if (battery.getCapacity() <= 0.0) {
      return "battery transient state is always evaluated by runTransient but storage capacity is not positive";
    }
    return "battery stored-energy and ramped-power state is active independently of calculateSteadyState";
  }

  private static DynamicActivationStatus resolveExpander(Expander expander) {
    return isDynamicModeRequested(expander) ? DynamicActivationStatus.ACTIVE : DynamicActivationStatus.INACTIVE;
  }

  private static String expanderDiagnostic(Expander expander) {
    return isDynamicModeRequested(expander) ? "expander nozzle, recovered-power and shaft-speed state is active"
        : "calculateSteadyState selects algebraic expander thermodynamics without nozzle/power/speed integration";
  }

  private static boolean isDynamicModeRequested(SimulationInterface element) {
    return !element.getCalculateSteadyState();
  }
}
