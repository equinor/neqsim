package neqsim.process.processmodel.dexpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.refrigeration.RefrigerationLevel;
import neqsim.process.equipment.refrigeration.RefrigerationLoad;
import neqsim.process.equipment.refrigeration.RefrigerationSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Read-only drawing topology for an analytically closed refrigeration cycle. Stream identities carry connectivity only.
 * The drawing neither executes equipment nor assigns operating data to an unsolved refrigerant line.
 *
 * @author NeqSim
 * @version 1.0
 */
final class RefrigerationPlantProjection {

  private final Map<ProcessEquipmentInterface, Item> exchangers = new IdentityHashMap<>();
  private final List<Item> generated = new ArrayList<>();
  private final List<ProcessEquipmentInterface> originals;

  /**
   * Captures the equipment register for an export.
   *
   * @param process simulation to project
   */
  private RefrigerationPlantProjection(ProcessSystem process) {
    originals = process.getUnitOperations();
  }

  /**
   * Expands configured packages into physical equipment, retaining live process stream and control bindings.
   *
   * @param source executable simulation
   * @return exchange-only view or the original process if there are no refrigeration packages
   */
  static ProcessSystem of(ProcessSystem source) {
    RefrigerationPlantProjection projection = new RefrigerationPlantProjection(source);
    for (ProcessEquipmentInterface unit : projection.originals) {
      if (unit instanceof RefrigerationSystem) {
        projection.exchangers.put(unit,
            projection.item(unit.getName(), unit.getName() + " condenser", "HeatExchanger"));
      }
    }
    if (projection.exchangers.isEmpty()) {
      return source;
    }
    for (ProcessEquipmentInterface unit : projection.originals) {
      if (unit instanceof RefrigerationSystem) {
        projection.addCycle((RefrigerationSystem) unit);
      }
    }
    ProcessSystem view = new ProcessSystem(source.getName());
    for (ProcessEquipmentInterface unit : projection.originals) {
      if (!(unit instanceof RefrigerationSystem)) {
        view.add(projection.exchangers.containsKey(unit) ? projection.exchangers.get(unit) : unit);
      }
    }
    for (Item item : projection.generated) {
      view.add(item);
    }
    source.getMeasurementDevices().forEach(view::add);
    source.getControllerDevices().forEach(view::add);
    return view;
  }

  /**
   * Registers a component owned by the cycle projection.
   *
   * @param packageTag source package name
   * @param name stable component tag
   * @param type physical DEXPI component class
   * @return registered component
   */
  private Item item(String packageTag, String name, String type) {
    Item node = new Item(packageTag, name, type);
    generated.add(node);
    return node;
  }

  /**
   * Projects liquid distribution from warm to cold and vapour compression from cold to warm.
   *
   * @param system configured cycle
   */
  private void addCycle(RefrigerationSystem system) {
    List<RefrigerationLevel> levels = new ArrayList<>(system.getLevels());
    levels.sort(Comparator.comparingDouble(RefrigerationLevel::getTemperature).reversed());
    if (levels.isEmpty()) {
      throw new IllegalArgumentException("A refrigeration P&ID requires at least one level: " + system.getName());
    }
    Item condenser = exchangers.get(system);
    Item dischargeTarget = condenser;
    Item liquidSource = condenser;
    boolean flash = system.getEconomizerType() == RefrigerationSystem.EconomizerType.FLASH_TANK;
    for (RefrigerationLevel level : levels) {
      String tag = system.getName() + " " + level.getName();
      Item compressor = item(system.getName(), tag + " compressor", "Compressor");
      compressor.connectTo(dischargeTarget);
      Item drum = null;
      if (flash) {
        Item valve = item(system.getName(), tag + " expansion", "OperatedValve");
        liquidSource.connectTo(valve);
        drum = item(system.getName(), tag + " flash drum", "Separator");
        valve.connectTo(drum);
        drum.connectTo(compressor);
        liquidSource = drum;
      }
      int index = 0;
      for (RefrigerationLoad load : level.getLoads()) {
        Item evaporator = evaporator(system, tag, ++index, load);
        if (flash) {
          liquidSource.connectTo(evaporator);
        } else {
          Item valve = item(system.getName(), tag + " expansion " + index, "OperatedValve");
          condenser.connectTo(valve);
          valve.connectTo(evaporator);
        }
        evaporator.connectTo(compressor);
      }
      dischargeTarget = flash
          && system.getInterstageCooling() == RefrigerationSystem.InterstageCooling.FLASH_INTERCOOLER ? drum
              : compressor;
    }
  }

  /**
   * Reuses the hot-side exchanger and its real process ports, including condensers in cascades.
   *
   * @param system source cycle
   * @param tag package and evaporation level
   * @param index one-based load position within the level
   * @param load source of the evaporation duty
   * @return physical exchanger
   */
  private Item evaporator(RefrigerationSystem system, String tag, int index, RefrigerationLoad load) {
    ProcessEquipmentInterface source = load.getSourceEquipment();
    if (exchangers.containsKey(source)) {
      return exchangers.get(source);
    }
    if (source instanceof Heater && originals.contains(source)) {
      Item bound = new Item(system.getName(), source.getName(), "HeatExchanger");
      bound.inlets.addAll(source.getInletStreams());
      bound.outlets.addAll(source.getOutletStreams());
      source.getControllers().forEach(bound::setController);
      bound.source = source;
      exchangers.put(source, bound);
      return bound;
    }
    return item(system.getName(), tag + " evaporator " + index + " " + load.getName(), "HeatExchanger");
  }

  /** A physical equipment item with separate port identities for every process and refrigerant connection. */
  static final class Item extends DexpiProcessUnit {
    private static final long serialVersionUID = 1L;
    private final List<StreamInterface> inlets = new ArrayList<>();
    private final List<StreamInterface> outlets = new ArrayList<>();
    private final String packageTag;
    private ProcessEquipmentInterface source;

    /**
     * Creates an exchange component.
     *
     * @param packageTag source refrigeration system
     * @param name engineering tag
     * @param type exchanged class
     */
    private Item(String packageTag, String name, String type) {
      super(name, type, null, null, null);
      this.packageTag = packageTag;
    }

    /**
     * Adds a directed refrigerant connection.
     *
     * @param next receiving item
     */
    private void connectTo(Item next) {
      Stream stream = new Stream(getName() + " to " + next.getName());
      outlets.add(stream);
      next.inlets.add(stream);
    }

    /** @return tag of the source refrigeration system */
    String getPackageTag() {
      return packageTag;
    }

    /** @return bound process equipment for source attributes, or null for a package-internal component */
    ProcessEquipmentInterface getSource() {
      return source;
    }

    /** @return whether the item belongs in a piping network segment */
    boolean isValve() {
      return "OperatedValve".equals(getDexpiClass());
    }

    /** {@inheritDoc} */
    @Override
    public List<StreamInterface> getInletStreams() {
      return Collections.unmodifiableList(inlets);
    }

    /** {@inheritDoc} */
    @Override
    public List<StreamInterface> getOutletStreams() {
      return Collections.unmodifiableList(outlets);
    }
  }
}
