package neqsim.process.dynamics;

import java.lang.reflect.Method;
import java.util.UUID;
import neqsim.process.ProcessElementInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.energy.EnergyConverter;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ModuleInterface;

/**
 * Transitional resolver for the dynamic capability of existing NeqSim process elements.
 *
 * <p>
 * The long-term contract allows individual element types to override
 * {@link neqsim.process.ProcessElementInterface#getDynamicCapability()}. During the capability-audit campaign, this
 * resolver provides conservative classifications for core models whose state semantics are already evident from their
 * implementations. A class that overrides the standard transient boundary but has not yet been audited is deliberately
 * returned as {@link DynamicCapability#UNCLASSIFIED_DYNAMIC}; it is never silently promoted to a professional dynamic
 * model just because a {@code runTransient} method exists.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DynamicCapabilityResolver {
  /** Utility class. */
  private DynamicCapabilityResolver() {
  }

  /**
   * Resolve the best currently audited dynamic capability for an element.
   *
   * @param element process element to classify
   * @return dynamic capability; never null
   */
  public static DynamicCapability resolve(ProcessElementInterface element) {
    if (element == null) {
      return DynamicCapability.UNSUPPORTED_DYNAMIC;
    }

    if (element instanceof ControllerDeviceInterface || element instanceof MeasurementDeviceInterface) {
      return DynamicCapability.CONTROL_DYNAMIC;
    }

    if (element instanceof ModuleInterface) {
      return DynamicCapability.ALGEBRAIC;
    }

    if (element instanceof EnergyNetworkSolver || element instanceof Orifice || element instanceof WellFlow) {
      return DynamicCapability.ALGEBRAIC;
    }

    if (element instanceof OnePhasePipeLine || element instanceof TwoFluidPipe || element instanceof TransientPipe
        || element instanceof WaterHammerPipe) {
      return DynamicCapability.DYNAMIC_DISTRIBUTED;
    }

    if (element instanceof SimpleReservoir) {
      return DynamicCapability.BOUNDARY_DYNAMIC;
    }

    if (element instanceof Separator || element instanceof Tank || element instanceof HeatExchanger
        || element instanceof Compressor || element instanceof Pump || element instanceof ThrottlingValve
        || element instanceof EnergyConverter || element instanceof BatteryStorage) {
      return DynamicCapability.DYNAMIC_LUMPED;
    }

    if (element instanceof Stream && usesStandardStreamTransientBoundary(element)) {
      return DynamicCapability.ALGEBRAIC;
    }

    if (hasCustomTransientImplementation(element)) {
      return DynamicCapability.UNCLASSIFIED_DYNAMIC;
    }

    return DynamicCapability.ALGEBRAIC;
  }

  /**
   * Whether a stream uses NeqSim's established algebraic transient boundary.
   *
   * <p>
   * {@link Stream#runTransient(double, UUID)} re-evaluates the stream and advances its execution clock, but it does not
   * integrate stored physical state. Stream subclasses that inherit that method remain algebraic; subclasses that
   * override it continue through the conservative custom-implementation audit below.
   * </p>
   *
   * @param element stream element to inspect
   * @return true when the effective transient method is declared by {@link Stream}
   */
  private static boolean usesStandardStreamTransientBoundary(ProcessElementInterface element) {
    try {
      Method method = element.getClass().getMethod("runTransient", Double.TYPE, UUID.class);
      return method.getDeclaringClass() == Stream.class;
    } catch (NoSuchMethodException ex) {
      return false;
    } catch (SecurityException ex) {
      return false;
    }
  }

  /**
   * Detects whether a simulation object overrides the default algebraic transient boundary.
   *
   * <p>
   * This is a discovery mechanism only. An override proves that custom transient code exists; it does not prove that
   * the implementation contains physically valid stored state, conserves the appropriate quantities, or has been
   * quantitatively validated.
   * </p>
   *
   * @param element process element to inspect
   * @return true if {@code runTransient(double, UUID)} is declared outside {@link SimulationInterface}
   */
  static boolean hasCustomTransientImplementation(ProcessElementInterface element) {
    if (!(element instanceof SimulationInterface)) {
      return false;
    }
    try {
      Method method = element.getClass().getMethod("runTransient", Double.TYPE, UUID.class);
      return method.getDeclaringClass() != SimulationInterface.class;
    } catch (NoSuchMethodException ex) {
      return false;
    } catch (SecurityException ex) {
      return false;
    }
  }
}
