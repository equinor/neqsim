package neqsim.process.dynamics;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import neqsim.process.ProcessElementInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.adsorber.AdsorptionBed;
import neqsim.process.equipment.adsorber.MercuryRemovalBed;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.energy.CommittedEnergyGenerator;
import neqsim.process.equipment.energy.EnergyConverter;
import neqsim.process.equipment.energy.EnergyNetworkSolver;
import neqsim.process.equipment.energy.Inverter;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.membrane.MembraneSeparator;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.network.PipeFlowNetwork;
import neqsim.process.equipment.network.WellFlowlineNetwork;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.MultiphasePipe;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reactor.IronSulfideOxidationSource;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.tank.VesselDepressurization;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.PSDValve;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyValve;
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
 * @version 1.1
 */
public final class DynamicCapabilityResolver {
  /**
   * Explicit ADR exemptions for built-in transient implementations that intentionally remain unclassified.
   *
   * <p>
   * Entries must map a declaring class to an existing repository-relative Markdown ADR. The source-inventory test
   * rejects absent files. There are no exemptions after the WS6 audit closure.
   * </p>
   */
  private static final Map<Class<?>, String> UNCLASSIFIED_BUILT_IN_ADRS = Collections.emptyMap();

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

    Class<?> transientOwner = getTransientImplementationClass(element);
    DynamicCapability audited = resolveAuditedBuiltInClass(transientOwner);
    if (audited != null) {
      return audited;
    }

    if (transientOwner != null && transientOwner != SimulationInterface.class) {
      return DynamicCapability.UNCLASSIFIED_DYNAMIC;
    }

    return DynamicCapability.ALGEBRAIC;
  }

  /**
   * Resolve one built-in class that declares the standard two-argument transient boundary.
   *
   * <p>
   * Classification is based on the class that declares the effective method, not merely the runtime subtype. A custom
   * subclass that inherits an audited built-in implementation keeps that implementation's category. A subclass that
   * overrides the method becomes {@link DynamicCapability#UNCLASSIFIED_DYNAMIC} until its own implementation is
   * audited.
   * </p>
   *
   * @param type class declaring {@code runTransient(double, UUID)}
   * @return audited capability, or null when the declaring class has no audit mapping
   */
  static DynamicCapability resolveAuditedBuiltInClass(Class<?> type) {
    if (type == null) {
      return null;
    }

    if (ControllerDeviceInterface.class.isAssignableFrom(type)
        || MeasurementDeviceInterface.class.isAssignableFrom(type)) {
      return DynamicCapability.CONTROL_DYNAMIC;
    }

    if (ModuleInterface.class.isAssignableFrom(type)) {
      return DynamicCapability.ALGEBRAIC;
    }

    if (isOneOf(type, EnergyNetworkSolver.class, Orifice.class, WellFlow.class, Stream.class, Heater.class, Mixer.class,
        Splitter.class, MembraneSeparator.class, AdiabaticPipe.class)) {
      return DynamicCapability.ALGEBRAIC;
    }

    if (isOneOf(type, Separator.class, ThreePhaseSeparator.class, Tank.class, VesselDepressurization.class,
        HeatExchanger.class, Compressor.class, Expander.class, Pump.class, ThrottlingValve.class, BlowdownValve.class,
        ESDValve.class, HIPPSValve.class, PSDValve.class, RuptureDisk.class, SafetyValve.class, EnergyConverter.class,
        Inverter.class, BatteryStorage.class, Filter.class, CommittedEnergyGenerator.class, Electrolyzer.class)) {
      return DynamicCapability.DYNAMIC_LUMPED;
    }

    if (isOneOf(type, OnePhasePipeLine.class, TwoFluidPipe.class, TransientPipe.class, WaterHammerPipe.class,
        Pipeline.class, MultiphasePipe.class, PipeBeggsAndBrills.class, DistillationColumn.class, AdsorptionBed.class,
        MercuryRemovalBed.class, PipeFlowNetwork.class, WellFlowlineNetwork.class)) {
      return DynamicCapability.DYNAMIC_DISTRIBUTED;
    }

    if (isOneOf(type, SimpleReservoir.class, IronSulfideOxidationSource.class)) {
      return DynamicCapability.BOUNDARY_DYNAMIC;
    }

    return null;
  }

  /**
   * Repository ADR for an intentionally unclassified built-in transient implementation.
   *
   * @param type class declaring the transient implementation
   * @return repository-relative Markdown path, or null when no exemption is recorded
   */
  static String getUnclassifiedBuiltInAdr(Class<?> type) {
    return UNCLASSIFIED_BUILT_IN_ADRS.get(type);
  }

  /** Compare a class against a compact exact-class inventory. */
  private static boolean isOneOf(Class<?> type, Class<?>... candidates) {
    for (Class<?> candidate : candidates) {
      if (type == candidate) {
        return true;
      }
    }
    return false;
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
    Class<?> transientOwner = getTransientImplementationClass(element);
    return transientOwner != null && transientOwner != SimulationInterface.class;
  }

  /**
   * Class that declares the effective standard transient boundary.
   *
   * @param element process element to inspect
   * @return declaring class, or null when reflection cannot resolve the boundary
   */
  private static Class<?> getTransientImplementationClass(ProcessElementInterface element) {
    try {
      Method method = element.getClass().getMethod("runTransient", Double.TYPE, UUID.class);
      return method.getDeclaringClass();
    } catch (NoSuchMethodException ex) {
      return null;
    } catch (SecurityException ex) {
      return null;
    }
  }
}
