package neqsim.process.processmodel.dexpi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Factory that converts {@link DexpiProcessUnit} placeholders into runnable NeqSim process
 * equipment by matching the {@link neqsim.process.equipment.EquipmentEnum} type and applying sizing
 * attributes from DEXPI GenericAttributes.
 *
 * <p>
 * This factory is used by {@link DexpiSimulationBuilder} during the topology walk to instantiate
 * real equipment objects (Separator, Compressor, Valve, etc.) from the DEXPI import placeholders.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiEquipmentFactory {
  private static final Logger logger = LogManager.getLogger(DexpiEquipmentFactory.class);

  private DexpiEquipmentFactory() {}

  /**
   * Creates a runnable NeqSim equipment instance from a DEXPI process unit and its inlet stream.
   *
   * <p>
   * The method selects the correct NeqSim equipment class based on
   * {@link DexpiProcessUnit#getMappedEquipment()}, wires the inlet stream, and applies any sizing
   * attributes (diameter, length, Cv, etc.) extracted from the DEXPI XML.
   * </p>
   *
   * @param unit the DEXPI process unit placeholder
   * @param inletStream the inlet stream to wire to the equipment (may be null for Mixer)
   * @return a fully configured NeqSim process equipment instance, or a pass-through Stream if the
   *         equipment type is not supported
   */
  public static ProcessEquipmentInterface create(DexpiProcessUnit unit,
      StreamInterface inletStream) {
    if (unit == null) {
      throw new IllegalArgumentException("DexpiProcessUnit must not be null");
    }

    String name = unit.getName();

    switch (unit.getMappedEquipment()) {
      case Separator:
        return createSeparator(name, inletStream, unit);
      case ThreePhaseSeparator:
        return createThreePhaseSeparator(name, inletStream, unit);
      case Compressor:
        return createCompressor(name, inletStream, unit);
      case Pump:
        return createPump(name, inletStream, unit);
      case HeatExchanger:
        return createHeatExchanger(name, inletStream, unit);
      case Heater:
        return createHeater(name, inletStream, unit);
      case Cooler:
        return createCooler(name, inletStream, unit);
      case ThrottlingValve:
        return createValve(name, inletStream, unit);
      case Expander:
        return createExpander(name, inletStream, unit);
      case Mixer:
        return createMixer(name, inletStream);
      case Splitter:
        return createSplitter(name, inletStream);
      case Column:
      case Reactor:
      case Tank:
      case Calculator:
      case Flare:
      case FlareStack:
      case Ejector:
      default:
        logger.info("No factory mapping for {}; creating pass-through stream '{}'",
            unit.getMappedEquipment(), name);
        return createPassThrough(name, inletStream);
    }
  }

  /**
   * Creates a Separator and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Separator
   */
  private static Separator createSeparator(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    Separator separator = new Separator(name, inletStream);

    double diameter = unit.getSizingAttributeAsDouble(DexpiMetadata.INSIDE_DIAMETER, -1.0);
    if (diameter > 0) {
      separator.setInternalDiameter(diameter);
    }

    double length = unit.getSizingAttributeAsDouble(DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, -1.0);
    if (length > 0) {
      separator.setSeparatorLength(length);
    }

    String orientation = unit.getSizingAttribute(DexpiMetadata.ORIENTATION);
    if (orientation != null && orientation.trim().toLowerCase().startsWith("vert")) {
      separator.setOrientation("vertical");
    }

    logger.debug("Created Separator '{}' (D={}, L={})", name, diameter, length);
    return separator;
  }

  /**
   * Creates a ThreePhaseSeparator and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured ThreePhaseSeparator
   */
  private static ThreePhaseSeparator createThreePhaseSeparator(String name,
      StreamInterface inletStream, DexpiProcessUnit unit) {
    ThreePhaseSeparator separator = new ThreePhaseSeparator(name, inletStream);

    double diameter = unit.getSizingAttributeAsDouble(DexpiMetadata.INSIDE_DIAMETER, -1.0);
    if (diameter > 0) {
      separator.setInternalDiameter(diameter);
    }

    double length = unit.getSizingAttributeAsDouble(DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, -1.0);
    if (length > 0) {
      separator.setSeparatorLength(length);
    }

    logger.debug("Created ThreePhaseSeparator '{}' (D={}, L={})", name, diameter, length);
    return separator;
  }

  /**
   * Creates a Compressor and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Compressor
   */
  private static Compressor createCompressor(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    Compressor compressor = new Compressor(name, inletStream);

    double designPressure = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_PRESSURE, -1.0);
    if (designPressure > 0) {
      compressor.setOutletPressure(designPressure);
    }

    logger.debug("Created Compressor '{}' (designP={})", name, designPressure);
    return compressor;
  }

  /**
   * Creates a Pump and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Pump
   */
  private static Pump createPump(String name, StreamInterface inletStream, DexpiProcessUnit unit) {
    Pump pump = new Pump(name, inletStream);

    double designPressure = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_PRESSURE, -1.0);
    if (designPressure > 0) {
      pump.setOutletPressure(designPressure);
    }

    logger.debug("Created Pump '{}' (designP={})", name, designPressure);
    return pump;
  }

  /**
   * Creates a HeatExchanger and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured HeatExchanger
   */
  private static HeatExchanger createHeatExchanger(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    HeatExchanger hx = new HeatExchanger(name, inletStream);

    double designTemp = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_TEMPERATURE, -1.0);
    if (designTemp > 0) {
      hx.setOutTemperature(273.15 + designTemp);
    }

    logger.debug("Created HeatExchanger '{}' (designT={})", name, designTemp);
    return hx;
  }

  /**
   * Creates a Heater and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Heater
   */
  private static Heater createHeater(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    Heater heater = new Heater(name, inletStream);

    double designTemp = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_TEMPERATURE, -1.0);
    if (designTemp > 0) {
      heater.setOutTemperature(273.15 + designTemp);
    }

    logger.debug("Created Heater '{}' (designT={})", name, designTemp);
    return heater;
  }

  /**
   * Creates a Cooler and applies sizing attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Cooler
   */
  private static Cooler createCooler(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    Cooler cooler = new Cooler(name, inletStream);

    double designTemp = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_TEMPERATURE, -1.0);
    if (designTemp > 0) {
      cooler.setOutTemperature(273.15 + designTemp);
    }

    logger.debug("Created Cooler '{}' (designT={})", name, designTemp);
    return cooler;
  }

  /**
   * Creates a ThrottlingValve and applies Cv and pressure attributes.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured ThrottlingValve
   */
  private static ThrottlingValve createValve(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    ThrottlingValve valve = new ThrottlingValve(name, inletStream);

    double cv = unit.getSizingAttributeAsDouble(DexpiMetadata.VALVE_CV, -1.0);
    if (cv > 0) {
      valve.setCv(cv);
    }

    double designPressure = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_PRESSURE, -1.0);
    if (designPressure > 0) {
      valve.setOutletPressure(designPressure);
    }

    logger.debug("Created ThrottlingValve '{}' (Cv={}, designP={})", name, cv, designPressure);
    return valve;
  }

  /**
   * Creates an Expander.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @param unit the DEXPI process unit with sizing data
   * @return the configured Expander
   */
  private static Expander createExpander(String name, StreamInterface inletStream,
      DexpiProcessUnit unit) {
    Expander expander = new Expander(name, inletStream);

    double designPressure = unit.getSizingAttributeAsDouble(DexpiMetadata.DESIGN_PRESSURE, -1.0);
    if (designPressure > 0) {
      expander.setOutletPressure(designPressure);
    }

    logger.debug("Created Expander '{}' (designP={})", name, designPressure);
    return expander;
  }

  /**
   * Creates a Mixer and adds the inlet stream if provided.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream (may be null)
   * @return the configured Mixer
   */
  private static Mixer createMixer(String name, StreamInterface inletStream) {
    Mixer mixer = new Mixer(name);
    if (inletStream != null) {
      mixer.addStream(inletStream);
    }
    logger.debug("Created Mixer '{}'", name);
    return mixer;
  }

  /**
   * Creates a Splitter with 2 output streams by default.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream
   * @return the configured Splitter
   */
  private static Splitter createSplitter(String name, StreamInterface inletStream) {
    Splitter splitter = new Splitter(name, inletStream);
    splitter.setSplitNumber(2);
    logger.debug("Created Splitter '{}'", name);
    return splitter;
  }

  /**
   * Creates a pass-through Stream for unsupported equipment types (Column, Reactor, Tank, etc.).
   * This preserves the unit in the process flowsheet even if it cannot be simulated.
   *
   * @param name the equipment name
   * @param inletStream the inlet stream to clone through
   * @return a simple Stream acting as a pass-through
   */
  private static Stream createPassThrough(String name, StreamInterface inletStream) {
    if (inletStream != null && inletStream.getThermoSystem() != null) {
      return new Stream(name, inletStream.getThermoSystem().clone());
    }
    return new Stream(name);
  }
}
