package neqsim.process.equipment;

import java.util.Objects;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.flare.FlareStack;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.powergeneration.FuelCell;
import neqsim.process.equipment.powergeneration.SolarPanel;
import neqsim.process.equipment.powergeneration.WindTurbine;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.ReservoirCVDsim;
import neqsim.process.equipment.reservoir.ReservoirDiffLibsim;
import neqsim.process.equipment.reservoir.ReservoirTPsim;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.stream.VirtualStream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.process.equipment.util.GORfitter;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Factory for creating process equipment.
 */
public final class EquipmentFactory {

  private EquipmentFactory() {}

  /**
   * Creates a piece of equipment based on the provided type.
   *
   * @param name name to assign to the equipment
   * @param equipmentType equipment type identifier
   * @return the created equipment instance
   */
  public static ProcessEquipmentInterface createEquipment(String name, String equipmentType) {
    if (equipmentType == null || equipmentType.trim().isEmpty()) {
      throw new IllegalArgumentException("Equipment type cannot be null or empty");
    }

    String normalized = equipmentType.trim().toLowerCase();
    switch (normalized) {
      case "valve":
        return createEquipment(name, EquipmentEnum.ThrottlingValve);
      case "separator_3phase":
      case "separator3phase":
      case "threephaseseparator":
        return createEquipment(name, EquipmentEnum.ThreePhaseSeparator);
      case "co₂electrolyzer":
      case "co2electrolyser":
      case "co2electrolyzer":
        return createEquipment(name, EquipmentEnum.CO2Electrolyzer);
      case "windturbine":
        return createEquipment(name, EquipmentEnum.WindTurbine);
      case "batterystorage":
        return createEquipment(name, EquipmentEnum.BatteryStorage);
      case "solarpanel":
        return createEquipment(name, EquipmentEnum.SolarPanel);
      default:
        EquipmentEnum enumType = resolveEquipmentEnum(equipmentType);
        return createEquipment(name, enumType);
    }
  }

  /**
   * Creates a piece of equipment based on {@link EquipmentEnum}.
   *
   * @param name name to assign
   * @param equipmentType {@link EquipmentEnum}
   * @return the created equipment
   */
  public static ProcessEquipmentInterface createEquipment(String name, EquipmentEnum equipmentType) {
    Objects.requireNonNull(equipmentType, "equipmentType");

    switch (equipmentType) {
      case ThrottlingValve:
        return new ThrottlingValve(name);
      case Stream:
        return new Stream(name);
      case Compressor:
        return new Compressor(name);
      case Pump:
        return new Pump(name);
      case Separator:
        return new Separator(name);
      case HeatExchanger:
        return new HeatExchanger(name);
      case Mixer:
        return new Mixer(name);
      case Splitter:
        return new Splitter(name);
      case Cooler:
        return new Cooler(name);
      case Heater:
        return new Heater(name);
      case Recycle:
        return new Recycle(name);
      case ThreePhaseSeparator:
        return new ThreePhaseSeparator(name);
      case Ejector:
        throw new IllegalArgumentException(
            "Ejector requires motive and suction streams. Use createEjector instead.");
      case GORfitter:
        throw new IllegalArgumentException(
            "GORfitter requires an inlet stream. Use createGORfitter instead.");
      case Adjuster:
        return new Adjuster(name);
      case SetPoint:
        return new SetPoint(name);
      case FlowRateAdjuster:
        return new FlowRateAdjuster(name);
      case Calculator:
        return new Calculator(name);
      case Expander:
        return new Expander(name);
      case SimpleTEGAbsorber:
        return new SimpleTEGAbsorber(name);
      case Tank:
        return new Tank(name);
      case ComponentSplitter:
        return new ComponentSplitter(name);
      case ReservoirCVDsim:
        throw new IllegalArgumentException(
            "ReservoirCVDsim requires a reservoir fluid. Use createReservoirCVDsim instead.");
      case ReservoirDiffLibsim:
        throw new IllegalArgumentException(
            "ReservoirDiffLibsim requires a reservoir fluid. Use createReservoirDiffLibsim instead.");
      case VirtualStream:
        return new VirtualStream(name);
      case ReservoirTPsim:
        throw new IllegalArgumentException(
            "ReservoirTPsim requires a reservoir fluid. Use createReservoirTPsim instead.");
      case SimpleReservoir:
        return new SimpleReservoir(name);
      case Manifold:
        return new Manifold(name);
      case Flare:
        return new Flare(name);
      case FlareStack:
        return new FlareStack(name);
      case FuelCell:
        return new FuelCell(name);
      case CO2Electrolyzer:
        return new CO2Electrolyzer(name);
      case Electrolyzer:
        return new Electrolyzer(name);
      case WindTurbine:
        return new WindTurbine(name);
      case BatteryStorage:
        return new BatteryStorage(name);
      case SolarPanel:
        return new SolarPanel(name);
      default:
        throw new IllegalArgumentException(
            "Unsupported equipment type: " + equipmentType.name());
    }
  }

  private static EquipmentEnum resolveEquipmentEnum(String equipmentType) {
    String sanitized = equipmentType.replaceAll("[\\s_-]", "");
    for (EquipmentEnum value : EquipmentEnum.values()) {
      if (value.name().equalsIgnoreCase(equipmentType)
          || value.name().equalsIgnoreCase(sanitized)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown equipment type: " + equipmentType);
  }

  public static Ejector createEjector(String name, StreamInterface motiveStream,
      StreamInterface suctionStream) {
    if (motiveStream == null || suctionStream == null) {
      throw new IllegalArgumentException("Ejector requires both motive and suction streams");
    }
    return new Ejector(name, motiveStream, suctionStream);
  }

  public static GORfitter createGORfitter(String name, StreamInterface stream) {
    if (stream == null) {
      throw new IllegalArgumentException("GORfitter requires a non-null inlet stream");
    }
    return new GORfitter(name, stream);
  }

  public static ReservoirCVDsim createReservoirCVDsim(String name, SystemInterface reservoirFluid) {
    if (reservoirFluid == null) {
      throw new IllegalArgumentException("ReservoirCVDsim requires a reservoir fluid");
    }
    return new ReservoirCVDsim(name, reservoirFluid);
  }

  public static ReservoirDiffLibsim createReservoirDiffLibsim(String name,
      SystemInterface reservoirFluid) {
    if (reservoirFluid == null) {
      throw new IllegalArgumentException("ReservoirDiffLibsim requires a reservoir fluid");
    }
    return new ReservoirDiffLibsim(name, reservoirFluid);
  }

  public static ReservoirTPsim createReservoirTPsim(String name, SystemInterface reservoirFluid) {
    if (reservoirFluid == null) {
      throw new IllegalArgumentException("ReservoirTPsim requires a reservoir fluid");
    }
    return new ReservoirTPsim(name, reservoirFluid);
  }
}
