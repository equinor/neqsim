package neqsim.process.equipment;

import java.lang.reflect.Constructor;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.absorber.SimpleAbsorber;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.absorber.WaterStripperColumn;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.flare.FlareStack;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.powergeneration.FuelCell;
import neqsim.process.equipment.powergeneration.OffshoreEnergySystem;
import neqsim.process.equipment.powergeneration.SolarPanel;
import neqsim.process.equipment.powergeneration.WindFarm;
import neqsim.process.equipment.powergeneration.WindTurbine;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reactor.AmmoniaSynthesisReactor;
import neqsim.process.equipment.reactor.AutothermalReformer;
import neqsim.process.equipment.reactor.CatalyticTubeReformer;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.reactor.PartialOxidationReactor;
import neqsim.process.equipment.reactor.PlugFlowReactor;
import neqsim.process.equipment.reactor.QuenchSection;
import neqsim.process.equipment.reactor.ReformerFurnace;
import neqsim.process.equipment.reactor.StirredTankReactor;
import neqsim.process.equipment.reactor.SyngasBurnerZone;
import neqsim.process.equipment.reactor.WaterGasShiftReactor;
import neqsim.process.equipment.reservoir.ReservoirCVDsim;
import neqsim.process.equipment.reservoir.ReservoirDiffLibsim;
import neqsim.process.equipment.reservoir.ReservoirTPsim;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentCaptureUnit;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.stream.VirtualStream;
import neqsim.process.equipment.subsea.SubseaPowerCable;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.process.equipment.util.GORfitter;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.util.SpreadsheetBlock;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.process.equipment.util.UnisimCalculator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Factory for creating process equipment.
 */
public final class EquipmentFactory {

  /** Logger instance for the factory. */
  private static final Logger logger = LogManager.getLogger(EquipmentFactory.class);

  /**
   * Equipment sub-packages probed by the reflection fallback when an equipment type is not handled by the curated
   * {@link EquipmentEnum} switch. Searching these packages lets every concrete process equipment class that exposes a
   * {@code (String name)} constructor be created by its class name, so all current and future equipment is reachable by
   * name and through {@code JsonProcessBuilder} without a dedicated enum entry.
   */
  private static final String[] EQUIPMENT_PACKAGES = new String[] { "neqsim.process.equipment.absorber",
      "neqsim.process.equipment.adsorber", "neqsim.process.equipment.battery", "neqsim.process.equipment.blackoil",
      "neqsim.process.equipment.compressor", "neqsim.process.equipment.diffpressure",
      "neqsim.process.equipment.distillation", "neqsim.process.equipment.ejector",
      "neqsim.process.equipment.electrolyzer", "neqsim.process.equipment.expander", "neqsim.process.equipment.filter",
      "neqsim.process.equipment.flare", "neqsim.process.equipment.heatexchanger", "neqsim.process.equipment.lng",
      "neqsim.process.equipment.manifold", "neqsim.process.equipment.membrane", "neqsim.process.equipment.mixer",
      "neqsim.process.equipment.network", "neqsim.process.equipment.pipeline",
      "neqsim.process.equipment.pipeline.twophasepipe", "neqsim.process.equipment.powergeneration",
      "neqsim.process.equipment.powergeneration.gasturbine", "neqsim.process.equipment.pump",
      "neqsim.process.equipment.reactor", "neqsim.process.equipment.reservoir", "neqsim.process.equipment.separator",
      "neqsim.process.equipment.splitter", "neqsim.process.equipment.stream", "neqsim.process.equipment.subsea",
      "neqsim.process.equipment.tank", "neqsim.process.equipment.util", "neqsim.process.equipment.valve",
      "neqsim.process.equipment.watertreatment", "neqsim.process.equipment.well" };

  private EquipmentFactory() {
  }

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
    case "gasscrubber":
    case "gas_scrubber":
    case "scrubber":
      return createEquipment(name, EquipmentEnum.GasScrubber);
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
    case "windfarm":
      return createEquipment(name, EquipmentEnum.WindFarm);
    case "offshoreenergysystem":
      return createEquipment(name, EquipmentEnum.OffshoreEnergySystem);
    case "ammoniasynthesisreactor":
    case "haberbosch":
      return createEquipment(name, EquipmentEnum.AmmoniaSynthesisReactor);
    case "gibbsreactor":
    case "equilibriumreactor":
    case "reactor":
      return createEquipment(name, EquipmentEnum.GibbsReactor);
    case "plugflowreactor":
    case "pfr":
      return createEquipment(name, EquipmentEnum.PlugFlowReactor);
    case "catalytictubereformer":
    case "tubereformer":
    case "smrtubereformer":
      return createEquipment(name, EquipmentEnum.CatalyticTubeReformer);
    case "reformerfurnace":
    case "smrfurnace":
    case "firedreformer":
      return createEquipment(name, EquipmentEnum.ReformerFurnace);
    case "syngasburnerzone":
    case "poxburnerzone":
    case "atrburnerzone":
      return createEquipment(name, EquipmentEnum.SyngasBurnerZone);
    case "autothermalreformer":
    case "atr":
      return createEquipment(name, EquipmentEnum.AutothermalReformer);
    case "partialoxidationreactor":
    case "pox":
      return createEquipment(name, EquipmentEnum.PartialOxidationReactor);
    case "quenchsection":
    case "syngasquench":
      return createEquipment(name, EquipmentEnum.QuenchSection);
    case "watergasshiftreactor":
    case "watergasshift":
    case "wgsreactor":
    case "wgs":
      return createEquipment(name, EquipmentEnum.WaterGasShiftReactor);
    case "componentcaptureunit":
    case "componentcapture":
    case "co2capture":
    case "h2dryer":
      return createEquipment(name, EquipmentEnum.ComponentCaptureUnit);
    case "stirredtankreactor":
    case "cstr":
      return createEquipment(name, EquipmentEnum.StirredTankReactor);
    case "subseapowercable":
    case "powercable":
      return createEquipment(name, EquipmentEnum.SubseaPowerCable);
    case "adiabaticpipe":
    case "pipe":
    case "pipeline":
      return createEquipment(name, EquipmentEnum.AdiabaticPipe);
    case "pipebeggsandbrills":
    case "beggsandbrills":
      return createEquipment(name, EquipmentEnum.PipeBeggsAndBrills);
    case "waterhammerpipe":
    case "waterhammer":
    case "liquidhammer":
    case "hydraulictransientpipe":
      return createEquipment(name, EquipmentEnum.WaterHammerPipe);
    case "streamsaturatorutil":
    case "saturator":
      return createEquipment(name, EquipmentEnum.StreamSaturatorUtil);
    case "spreadsheet":
    case "spreadsheetblock":
      return createEquipment(name, EquipmentEnum.SpreadsheetBlock);
    case "unisimcalculator":
    case "unisim_calculator":
    case "unisimcalculatorblock":
    case "virtualstreamop":
    case "balanceop":
    case "subflowsheet":
      return createEquipment(name, EquipmentEnum.UnisimCalculator);
    case "distillationcolumn":
    case "column":
      return createEquipment(name, EquipmentEnum.DistillationColumn);
    default:
      EquipmentEnum enumType = resolveEquipmentEnum(equipmentType);
      if (enumType != null) {
        return createEquipment(name, enumType);
      }
      ProcessEquipmentInterface reflected = createByClassName(name, equipmentType);
      if (reflected != null) {
        return reflected;
      }
      throw new IllegalArgumentException("Unknown equipment type: " + equipmentType);
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
    case GasScrubber:
      return new GasScrubber(name);
    case HeatExchanger:
      return new HeatExchanger(name);
    case Mixer:
      return new Mixer(name);
    case Splitter:
      return new Splitter(name);
    case Reactor:
    case GibbsReactor:
      return new GibbsReactor(name);
    case PlugFlowReactor:
      return new PlugFlowReactor(name);
    case CatalyticTubeReformer:
      return new CatalyticTubeReformer(name);
    case ReformerFurnace:
      return new ReformerFurnace(name);
    case SyngasBurnerZone:
      return new SyngasBurnerZone(name);
    case AutothermalReformer:
      return new AutothermalReformer(name);
    case PartialOxidationReactor:
      return new PartialOxidationReactor(name);
    case QuenchSection:
      return new QuenchSection(name);
    case WaterGasShiftReactor:
      return new WaterGasShiftReactor(name);
    case StirredTankReactor:
      return new StirredTankReactor(name);
    case Cooler:
      return new Cooler(name);
    case Heater:
      return new Heater(name);
    case Recycle:
      return new Recycle(name);
    case ThreePhaseSeparator:
      return new ThreePhaseSeparator(name);
    case Ejector:
      throw new IllegalArgumentException("Ejector requires motive and suction streams. Use createEjector instead.");
    case GORfitter:
      throw new IllegalArgumentException("GORfitter requires an inlet stream. Use createGORfitter instead.");
    case Adjuster:
      return new Adjuster(name);
    case SetPoint:
      return new SetPoint(name);
    case FlowRateAdjuster:
      return new FlowRateAdjuster(name);
    case Calculator:
      return new Calculator(name);
    case SpreadsheetBlock:
      return new SpreadsheetBlock(name);
    case UnisimCalculator:
      return new UnisimCalculator(name);
    case Expander:
      return new Expander(name);
    case SimpleTEGAbsorber:
      return new SimpleTEGAbsorber(name);
    case WaterStripperColumn:
      return new WaterStripperColumn(name);
    case SimpleAbsorber:
      return new SimpleAbsorber(name);
    case Filter:
      return new Filter(name);
    case Tank:
      return new Tank(name);
    case ComponentSplitter:
      return new ComponentSplitter(name);
    case ComponentCaptureUnit:
      return new ComponentCaptureUnit(name);
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
    case WindFarm:
      return new WindFarm(name);
    case OffshoreEnergySystem:
      return new OffshoreEnergySystem(name);
    case AmmoniaSynthesisReactor:
      return new AmmoniaSynthesisReactor(name);
    case SubseaPowerCable:
      return new SubseaPowerCable(name);
    case AdiabaticPipe:
      return new AdiabaticPipe(name);
    case PipeBeggsAndBrills:
      return new PipeBeggsAndBrills(name);
    case WaterHammerPipe:
      return new WaterHammerPipe(name);
    case StreamSaturatorUtil:
      return new StreamSaturatorUtil(name);
    case Column:
    case DistillationColumn:
      return new DistillationColumn(name, 5, true, true);
    default:
      ProcessEquipmentInterface reflected = createByClassName(name, equipmentType.name());
      if (reflected != null) {
        return reflected;
      }
      throw new IllegalArgumentException("Unsupported equipment type: " + equipmentType.name());
    }
  }

  /**
   * Resolves an {@link EquipmentEnum} from a free-form type string, ignoring case and any whitespace, underscore, or
   * dash separators.
   *
   * @param equipmentType the equipment type identifier
   * @return the matching {@link EquipmentEnum}, or {@code null} if no enum constant matches
   */
  private static EquipmentEnum resolveEquipmentEnum(String equipmentType) {
    String sanitized = equipmentType.replaceAll("[\\s_-]", "");
    for (EquipmentEnum value : EquipmentEnum.values()) {
      if (value.name().equalsIgnoreCase(equipmentType) || value.name().equalsIgnoreCase(sanitized)) {
        return value;
      }
    }
    return null;
  }

  /**
   * Reflection fallback that creates any concrete process equipment class exposing a {@code (String name)} constructor,
   * located by its class name within the known equipment sub-packages. This guarantees that every current and future
   * equipment class is constructible by name (and therefore through {@code JsonProcessBuilder}) without an explicit
   * {@link EquipmentEnum} entry. The simple class name is matched case-sensitively; whitespace, underscore, and dash
   * separators are stripped before probing.
   *
   * @param name the name to assign to the created equipment
   * @param equipmentType the equipment class name, e.g. {@code "ControlValve"}
   * @return the created equipment, or {@code null} if no matching class with a {@code (String)} constructor was found
   */
  private static ProcessEquipmentInterface createByClassName(String name, String equipmentType) {
    String trimmed = equipmentType.trim();
    String sanitized = trimmed.replaceAll("[\\s_-]", "");
    for (String pkg : EQUIPMENT_PACKAGES) {
      ProcessEquipmentInterface eq = tryInstantiate(pkg + "." + trimmed, name);
      if (eq != null) {
        return eq;
      }
      if (!sanitized.equals(trimmed)) {
        eq = tryInstantiate(pkg + "." + sanitized, name);
        if (eq != null) {
          return eq;
        }
      }
    }
    return null;
  }

  /**
   * Attempts to instantiate the named class through its {@code (String name)} constructor, provided it is a concrete
   * {@link ProcessEquipmentInterface} implementation.
   *
   * @param className the fully qualified class name to instantiate
   * @param name the name to pass to the {@code (String)} constructor
   * @return the created equipment, or {@code null} if the class does not exist, is abstract, lacks a {@code (String)}
   * constructor, or is not a {@link ProcessEquipmentInterface}
   */
  private static ProcessEquipmentInterface tryInstantiate(String className, String name) {
    try {
      Class<?> clazz = Class.forName(className);
      if (!ProcessEquipmentInterface.class.isAssignableFrom(clazz)
          || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
        return null;
      }
      Constructor<?> constructor = clazz.getConstructor(String.class);
      return (ProcessEquipmentInterface) constructor.newInstance(name);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      return null;
    } catch (ReflectiveOperationException e) {
      logger.debug("Reflection instantiation failed for {}: {}", className, e.getMessage());
      return null;
    }
  }

  public static Ejector createEjector(String name, StreamInterface motiveStream, StreamInterface suctionStream) {
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

  public static ReservoirDiffLibsim createReservoirDiffLibsim(String name, SystemInterface reservoirFluid) {
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

  // ============================================================
  // Convenience factory methods (eliminate Python wrapper boilerplate)
  // ============================================================

  /**
   * Creates a configured Stream with flow, pressure, and temperature.
   *
   * @param name stream name
   * @param fluid thermodynamic system
   * @param flowRate mass flow rate
   * @param flowUnit flow unit, e.g. "kg/hr"
   * @param pressure stream pressure
   * @param pressureUnit pressure unit, e.g. "bara"
   * @param temperature stream temperature
   * @param temperatureUnit temperature unit, e.g. "C"
   * @return configured Stream
   */
  public static Stream createStream(String name, SystemInterface fluid, double flowRate, String flowUnit,
      double pressure, String pressureUnit, double temperature, String temperatureUnit) {
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(flowRate, flowUnit);
    stream.setPressure(pressure, pressureUnit);
    stream.setTemperature(temperature, temperatureUnit);
    return stream;
  }

  /**
   * Creates a Compressor with outlet pressure and isentropic efficiency.
   *
   * @param name compressor name
   * @param inletStream inlet stream
   * @param outletPressure discharge pressure in bara
   * @param isentropicEfficiency isentropic efficiency (0.0 to 1.0)
   * @return configured Compressor
   */
  public static Compressor createCompressor(String name, StreamInterface inletStream, double outletPressure,
      double isentropicEfficiency) {
    Compressor compressor = new Compressor(name, inletStream);
    compressor.setOutletPressure(outletPressure);
    compressor.setIsentropicEfficiency(isentropicEfficiency);
    return compressor;
  }

  /**
   * Creates a Cooler with specified outlet temperature.
   *
   * @param name cooler name
   * @param inletStream inlet stream
   * @param outletTemperature desired outlet temperature
   * @param temperatureUnit temperature unit, e.g. "C"
   * @return configured Cooler
   */
  public static Cooler createCooler(String name, StreamInterface inletStream, double outletTemperature,
      String temperatureUnit) {
    Cooler cooler = new Cooler(name, inletStream);
    cooler.setOutTemperature(outletTemperature, temperatureUnit);
    return cooler;
  }

  /**
   * Creates a Heater with specified outlet temperature.
   *
   * @param name heater name
   * @param inletStream inlet stream
   * @param outletTemperature desired outlet temperature
   * @param temperatureUnit temperature unit, e.g. "C"
   * @return configured Heater
   */
  public static Heater createHeater(String name, StreamInterface inletStream, double outletTemperature,
      String temperatureUnit) {
    Heater heater = new Heater(name, inletStream);
    heater.setOutTemperature(outletTemperature, temperatureUnit);
    return heater;
  }

  /**
   * Creates a ThrottlingValve with outlet pressure and valve opening.
   *
   * @param name valve name
   * @param inletStream inlet stream
   * @param outletPressure downstream pressure in bara
   * @param percentValveOpening valve opening percentage (0-100)
   * @return configured ThrottlingValve
   */
  public static ThrottlingValve createValve(String name, StreamInterface inletStream, double outletPressure,
      double percentValveOpening) {
    ThrottlingValve valve = new ThrottlingValve(name, inletStream);
    valve.setOutletPressure(outletPressure);
    valve.setPercentValveOpening(percentValveOpening);
    return valve;
  }

  /**
   * Creates a Pump with specified outlet pressure.
   *
   * @param name pump name
   * @param inletStream inlet stream
   * @param outletPressure discharge pressure in bara
   * @return configured Pump
   */
  public static Pump createPump(String name, StreamInterface inletStream, double outletPressure) {
    Pump pump = new Pump(name, inletStream);
    pump.setOutletPressure(outletPressure);
    return pump;
  }

  /**
   * Creates a Separator from an inlet stream.
   *
   * @param name separator name
   * @param inletStream inlet stream
   * @return configured Separator
   */
  public static Separator createSeparator(String name, StreamInterface inletStream) {
    return new Separator(name, inletStream);
  }

  /**
   * Creates a ThreePhaseSeparator from an inlet stream.
   *
   * @param name separator name
   * @param inletStream inlet stream
   * @return configured ThreePhaseSeparator
   */
  public static ThreePhaseSeparator createThreePhaseSeparator(String name, StreamInterface inletStream) {
    return new ThreePhaseSeparator(name, inletStream);
  }

  /**
   * Creates a Mixer with multiple inlet streams.
   *
   * @param name mixer name
   * @param inletStreams inlet streams to combine
   * @return configured Mixer
   */
  public static Mixer createMixer(String name, StreamInterface... inletStreams) {
    Mixer mixer = new Mixer(name);
    for (StreamInterface s : inletStreams) {
      mixer.addStream(s);
    }
    return mixer;
  }

  /**
   * Creates an Expander with specified outlet pressure.
   *
   * @param name expander name
   * @param inletStream inlet stream
   * @param outletPressure discharge pressure in bara
   * @return configured Expander
   */
  public static Expander createExpander(String name, StreamInterface inletStream, double outletPressure) {
    Expander expander = new Expander(name, inletStream);
    expander.setOutletPressure(outletPressure);
    return expander;
  }
}
