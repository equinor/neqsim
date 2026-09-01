package neqsim.process.equipment;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
import neqsim.process.equipment.separator.ThreePhaseGasScrubber;
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

  /** Root Java package containing process equipment implementations. */
  private static final String EQUIPMENT_PACKAGE = "neqsim.process.equipment";

  /** Classpath form of {@link #EQUIPMENT_PACKAGE}. */
  private static final String EQUIPMENT_PACKAGE_PATH = EQUIPMENT_PACKAGE.replace('.', '/');

  /**
   * Concrete equipment classes with a public {@code (String name)} constructor, discovered recursively from the
   * classpath. The normalized simple class name is the key.
   */
  private static final Map<String, Class<? extends ProcessEquipmentInterface>> DISCOVERED_EQUIPMENT = discoverEquipmentClasses();

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
    case "threephasegasscrubber":
    case "threephasescrubber":
    case "threephase_scrubber":
    case "gasscrubber_3phase":
      return createEquipment(name, EquipmentEnum.ThreePhaseGasScrubber);
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
   * Reports whether a type name is recognized by this factory without constructing equipment.
   *
   * @param equipmentType equipment type or alias to inspect
   * @return {@code true} when the type is an alias, enum value, or discovered concrete class with a public
   * {@code (String name)} constructor
   */
  public static boolean supportsEquipmentType(String equipmentType) {
    if (equipmentType == null || equipmentType.trim().isEmpty()) {
      return false;
    }
    EquipmentEnum enumType = resolveEquipmentEnum(equipmentType);
    return isEquipmentAlias(equipmentType.trim().toLowerCase(Locale.ROOT))
        || enumType != null && isNameOnlyConstructible(enumType) || findEquipmentClass(equipmentType) != null;
  }

  /**
   * Lists concrete equipment class names that can be constructed through the generic {@code (String name)} factory
   * path.
   *
   * <p>
   * The list is discovered recursively from the runtime classpath, so newly added equipment is included without
   * changing this factory. Specialized aliases and enum-only factory entries are not included because they do not
   * identify additional concrete classes.
   * </p>
   *
   * @return immutable alphabetically sorted list of constructible simple class names
   */
  public static List<String> getConstructibleEquipmentTypes() {
    List<String> types = new ArrayList<String>();
    for (Class<? extends ProcessEquipmentInterface> equipmentClass : DISCOVERED_EQUIPMENT.values()) {
      types.add(equipmentClass.getSimpleName());
    }
    Collections.sort(types);
    return Collections.unmodifiableList(types);
  }

  /**
   * Lists canonical equipment type names supported by {@link #createEquipment(String, String)} without additional
   * constructor context.
   *
   * <p>
   * This combines recursively discovered classes with enum-backed types such as {@link DistillationColumn} that use
   * specialized construction. Context-dependent equipment, such as an {@link Ejector} requiring two inlet streams, is
   * excluded.
   * </p>
   *
   * @return immutable alphabetically sorted list of name-only factory-supported types
   */
  public static List<String> getSupportedEquipmentTypes() {
    List<String> types = new ArrayList<String>(getConstructibleEquipmentTypes());
    for (EquipmentEnum equipmentType : EquipmentEnum.values()) {
      if (isNameOnlyConstructible(equipmentType) && !types.contains(equipmentType.name())) {
        types.add(equipmentType.name());
      }
    }
    Collections.sort(types);
    return Collections.unmodifiableList(types);
  }

  /**
   * Checks whether an enum-backed type can be constructed from only a name.
   *
   * @param equipmentType enum type to inspect
   * @return {@code false} when extra streams or a reservoir fluid are required
   */
  private static boolean isNameOnlyConstructible(EquipmentEnum equipmentType) {
    switch (equipmentType) {
    case Ejector:
    case GORfitter:
    case ReservoirCVDsim:
    case ReservoirDiffLibsim:
    case ReservoirTPsim:
      return false;
    default:
      return true;
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
    case ThreePhaseGasScrubber:
      return new ThreePhaseGasScrubber(name);
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
   * Checks names handled as aliases by {@link #createEquipment(String, String)}.
   *
   * @param normalizedType trimmed lowercase equipment type
   * @return {@code true} when the type is a factory alias
   */
  private static boolean isEquipmentAlias(String normalizedType) {
    switch (normalizedType) {
    case "valve":
    case "separator_3phase":
    case "separator3phase":
    case "threephaseseparator":
    case "gasscrubber":
    case "gas_scrubber":
    case "scrubber":
    case "threephasegasscrubber":
    case "threephasescrubber":
    case "threephase_scrubber":
    case "gasscrubber_3phase":
    case "co₂electrolyzer":
    case "co2electrolyser":
    case "co2electrolyzer":
    case "haberbosch":
    case "equilibriumreactor":
    case "reactor":
    case "pfr":
    case "tubereformer":
    case "smrtubereformer":
    case "smrfurnace":
    case "firedreformer":
    case "poxburnerzone":
    case "atrburnerzone":
    case "atr":
    case "pox":
    case "syngasquench":
    case "watergasshift":
    case "wgsreactor":
    case "wgs":
    case "componentcapture":
    case "co2capture":
    case "h2dryer":
    case "cstr":
    case "powercable":
    case "pipe":
    case "pipeline":
    case "beggsandbrills":
    case "waterhammer":
    case "liquidhammer":
    case "hydraulictransientpipe":
    case "saturator":
    case "spreadsheet":
    case "unisim_calculator":
    case "unisimcalculatorblock":
    case "virtualstreamop":
    case "balanceop":
    case "subflowsheet":
    case "column":
      return true;
    default:
      return false;
    }
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
    Class<? extends ProcessEquipmentInterface> equipmentClass = findEquipmentClass(equipmentType);
    if (equipmentClass == null) {
      return null;
    }
    return tryInstantiate(equipmentClass, name);
  }

  /**
   * Finds a discovered equipment class by normalized simple name.
   *
   * @param equipmentType simple equipment class name or separator-insensitive spelling
   * @return matching class, or {@code null} when none is registered
   */
  private static Class<? extends ProcessEquipmentInterface> findEquipmentClass(String equipmentType) {
    return DISCOVERED_EQUIPMENT.get(normalizeEquipmentClassName(equipmentType));
  }

  /**
   * Attempts to instantiate a discovered class through its public {@code (String name)} constructor.
   *
   * @param equipmentClass concrete equipment class to instantiate
   * @param name name to pass to the constructor
   * @return created equipment, or {@code null} when reflective construction fails
   */
  private static ProcessEquipmentInterface tryInstantiate(Class<? extends ProcessEquipmentInterface> equipmentClass,
      String name) {
    try {
      Constructor<? extends ProcessEquipmentInterface> constructor = equipmentClass.getConstructor(String.class);
      return (ProcessEquipmentInterface) constructor.newInstance(name);
    } catch (ReflectiveOperationException e) {
      logger.debug("Reflection instantiation failed for {}: {}", equipmentClass.getName(), e.getMessage());
      return null;
    }
  }

  /**
   * Discovers all eligible equipment classes recursively below {@link #EQUIPMENT_PACKAGE} in class directories and JAR
   * files.
   *
   * @return immutable registry keyed by normalized simple class name
   */
  private static Map<String, Class<? extends ProcessEquipmentInterface>> discoverEquipmentClasses() {
    Map<String, Class<? extends ProcessEquipmentInterface>> classes = new LinkedHashMap<String, Class<? extends ProcessEquipmentInterface>>();
    List<String> classNames = new ArrayList<String>();
    ClassLoader classLoader = EquipmentFactory.class.getClassLoader();
    try {
      Enumeration<URL> resources = classLoader.getResources(EQUIPMENT_PACKAGE_PATH);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        if ("file".equals(resource.getProtocol())) {
          collectDirectoryClassNames(new File(new URI(resource.toString())), EQUIPMENT_PACKAGE, classNames);
        } else if ("jar".equals(resource.getProtocol())) {
          collectJarClassNames(resource, classNames);
        }
      }
    } catch (IOException | URISyntaxException e) {
      logger.warn("Could not scan process equipment classes: {}", e.getMessage());
    }

    Collections.sort(classNames);
    for (String className : classNames) {
      registerEquipmentClass(classLoader, className, classes);
    }
    return Collections.unmodifiableMap(classes);
  }

  /**
   * Collects class names recursively from an exploded class directory.
   *
   * @param directory current package directory
   * @param packageName Java package represented by the directory
   * @param classNames destination list
   */
  private static void collectDirectoryClassNames(File directory, String packageName, List<String> classNames) {
    File[] files = directory.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        collectDirectoryClassNames(file, packageName + "." + file.getName(), classNames);
      } else if (file.getName().endsWith(".class") && file.getName().indexOf('$') < 0) {
        classNames.add(packageName + "." + file.getName().substring(0, file.getName().length() - 6));
      }
    }
  }

  /**
   * Collects class names recursively from a JAR classpath resource.
   *
   * @param resource equipment package resource in a JAR
   * @param classNames destination list
   * @throws IOException if the JAR cannot be opened or read
   */
  private static void collectJarClassNames(URL resource, List<String> classNames) throws IOException {
    JarURLConnection connection = (JarURLConnection) resource.openConnection();
    JarFile jarFile = connection.getJarFile();
    Enumeration<JarEntry> entries = jarFile.entries();
    while (entries.hasMoreElements()) {
      String entryName = entries.nextElement().getName();
      if (entryName.startsWith(EQUIPMENT_PACKAGE_PATH + "/") && entryName.endsWith(".class")
          && entryName.indexOf('$') < 0) {
        classNames.add(entryName.substring(0, entryName.length() - 6).replace('/', '.'));
      }
    }
  }

  /**
   * Registers a class when it is concrete process equipment with a public {@code (String name)} constructor.
   *
   * @param classLoader loader used for non-initializing class resolution
   * @param className fully qualified candidate class name
   * @param classes destination registry
   */
  @SuppressWarnings("unchecked")
  private static void registerEquipmentClass(ClassLoader classLoader, String className,
      Map<String, Class<? extends ProcessEquipmentInterface>> classes) {
    try {
      Class<?> candidate = Class.forName(className, false, classLoader);
      if (!ProcessEquipmentInterface.class.isAssignableFrom(candidate) || candidate.isInterface()
          || Modifier.isAbstract(candidate.getModifiers())) {
        return;
      }
      candidate.getConstructor(String.class);
      Class<? extends ProcessEquipmentInterface> equipmentClass = (Class<? extends ProcessEquipmentInterface>) candidate;
      String key = normalizeEquipmentClassName(candidate.getSimpleName());
      if (!classes.containsKey(key)) {
        classes.put(key, equipmentClass);
      } else {
        logger.warn("Duplicate process equipment simple name {}: keeping {}, ignoring {}", candidate.getSimpleName(),
            classes.get(key).getName(), className);
      }
    } catch (ClassNotFoundException | NoSuchMethodException | LinkageError | SecurityException e) {
      logger.debug("Skipping process equipment class {}: {}", className, e.getMessage());
    }
  }

  /**
   * Normalizes a class-style equipment type for case- and separator-insensitive lookup.
   *
   * @param equipmentType equipment type spelling
   * @return lowercase type without whitespace, underscores, or dashes
   */
  private static String normalizeEquipmentClassName(String equipmentType) {
    return equipmentType.trim().replaceAll("[\\s_-]", "").toLowerCase(Locale.ROOT);
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
