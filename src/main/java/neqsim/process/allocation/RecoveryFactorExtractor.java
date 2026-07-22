package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Extracts frozen per-unit per-component split (recovery) factors from a rigorous base-case {@link ProcessSystem} run.
 *
 * <p>
 * After the process has been solved once with a single common component slate, this extractor walks every conservative
 * process unit and computes, for each outlet stream and component, the fraction of that component leaving through the
 * outlet:
 * </p>
 *
 * <p>
 * $$f_u(s,k) = \frac{\dot{n}_{s,k}^{\,out}}{\sum_{i\in inlets} \dot{n}_{i,k}^{\,in}}$$
 * </p>
 *
 * <p>
 * The result is a {@link UnitSplit} per unit and a master component slate (the union of all component names found
 * across the process). These frozen factors are the basis for the linear allocation network — see
 * {@link AllocationNetwork} and {@link SourceAllocator}.
 * </p>
 *
 * <p>
 * Stream objects are skipped as nodes (they are edges in the proxy network). Reactive or contacting units (reactors,
 * absorbers) are still captured as black boxes; their factors reflect whatever component redistribution the base case
 * produced, but for strictly mass-conserving allocation the scope is conservative separation / scrubber / column /
 * valve / cooler / mixer / splitter networks.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RecoveryFactorExtractor implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(RecoveryFactorExtractor.class);

  /** Molar-flow basis used internally for all factor extraction. */
  static final String MOLAR_FLOW_UNIT = "mole/sec";

  /** The base-case process the factors are extracted from. */
  private final ProcessSystem process;

  /** Master component slate (union of all component names across the process). */
  private List<String> componentNames;

  /** Molar mass per component (kg/mol), keyed by component name. */
  private Map<String, Double> molarMass;

  /** Frozen split factors per unit, keyed by unit name (insertion-ordered). */
  private Map<String, UnitSplit> unitSplits;

  /** Node units (non-stream units participating in the network), in process order. */
  private List<ProcessEquipmentInterface> nodeUnits;

  /**
   * Creates an extractor for a base-case process.
   *
   * @param process the base-case process system; must already have been run; must be non-null
   */
  public RecoveryFactorExtractor(ProcessSystem process) {
    this.process = process;
  }

  /**
   * Extracts frozen split factors and the master component slate from the base-case run.
   *
   * @return this extractor (fluent), with results available via the getters
   */
  public RecoveryFactorExtractor extract() {
    buildMasterSlate();
    buildUnitSplits();
    logger.info("Extracted recovery factors: {} components, {} node units", componentNames.size(), unitSplits.size());
    return this;
  }

  /**
   * Determines whether a unit acts as a node (mixing/splitting point) in the proxy network. Streams are edges, not
   * nodes; a node must expose at least one outlet stream.
   *
   * @param unit the unit to test; must be non-null
   * @return {@code true} if the unit is a network node
   */
  private boolean isNodeUnit(ProcessEquipmentInterface unit) {
    if (unit instanceof StreamInterface) {
      return false;
    }
    List<StreamInterface> outlets = safeOutlets(unit);
    return !outlets.isEmpty();
  }

  /**
   * Builds the master component slate (union of component names) and per-component molar masses by scanning every node
   * unit's inlet and outlet streams.
   */
  private void buildMasterSlate() {
    componentNames = new ArrayList<>();
    molarMass = new LinkedHashMap<>();
    nodeUnits = new ArrayList<>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!isNodeUnit(unit)) {
        continue;
      }
      nodeUnits.add(unit);
      collectComponents(safeInlets(unit));
      collectComponents(safeOutlets(unit));
    }
  }

  /**
   * Registers all component names (and molar masses) found in a list of streams into the master slate, preserving
   * first-seen order.
   *
   * @param streams the streams to scan; must be non-null
   */
  private void collectComponents(List<StreamInterface> streams) {
    for (StreamInterface stream : streams) {
      SystemInterface fluid = stream.getFluid();
      if (fluid == null) {
        continue;
      }
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        String name = fluid.getComponent(i).getComponentName();
        if (!molarMass.containsKey(name)) {
          componentNames.add(name);
          molarMass.put(name, fluid.getComponent(i).getMolarMass());
        }
      }
    }
  }

  /**
   * Builds a {@link UnitSplit} for every node unit using the master component slate.
   */
  private void buildUnitSplits() {
    unitSplits = new LinkedHashMap<>();
    String[] slate = componentNames.toArray(new String[0]);
    for (ProcessEquipmentInterface unit : nodeUnits) {
      List<StreamInterface> inlets = safeInlets(unit);
      List<StreamInterface> outlets = safeOutlets(unit);

      double[] inletFlow = new double[slate.length];
      for (int k = 0; k < slate.length; k++) {
        double sum = 0.0;
        for (StreamInterface in : inlets) {
          sum += componentFlow(in, slate[k]);
        }
        inletFlow[k] = sum;
      }

      double[][] factors = new double[outlets.size()][slate.length];
      for (int k = 0; k < slate.length; k++) {
        if (inletFlow[k] > 0.0) {
          for (int s = 0; s < outlets.size(); s++) {
            factors[s][k] = componentFlow(outlets.get(s), slate[k]) / inletFlow[k];
          }
        } else if (outlets.size() == 1) {
          // No inlet of this component: a single-outlet pass-through is the identity map.
          factors[0][k] = 1.0;
        } else {
          // No inlet flow and multiple outlets: nothing to route, leave factors at zero.
          for (int s = 0; s < outlets.size(); s++) {
            factors[s][k] = 0.0;
          }
        }
      }

      UnitSplit split = new UnitSplit(unit.getName(), unit.getClass().getSimpleName(), inlets, outlets, slate, factors,
          inletFlow);
      unitSplits.put(unit.getName(), split);
    }
  }

  /**
   * Gets the total molar flow of a named component in a stream (mole/sec), or zero if the component is not present in
   * that stream.
   *
   * @param stream the stream to read; must be non-null
   * @param componentName the component name to look up; must be non-null
   * @return the component molar flow in mole/sec, or {@code 0.0} if absent
   */
  static double componentFlow(StreamInterface stream, String componentName) {
    SystemInterface fluid = stream.getFluid();
    if (fluid == null) {
      return 0.0;
    }
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      if (fluid.getComponent(i).getComponentName().equals(componentName)) {
        return fluid.getComponent(i).getTotalFlowRate(MOLAR_FLOW_UNIT);
      }
    }
    return 0.0;
  }

  /**
   * Returns the inlet streams of a unit, never throwing.
   *
   * @param unit the unit; must be non-null
   * @return the inlet streams, or an empty list if not available
   */
  private static List<StreamInterface> safeInlets(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> inlets = unit.getInletStreams();
      return inlets == null ? Collections.<StreamInterface>emptyList() : inlets;
    } catch (Exception e) {
      return Collections.<StreamInterface>emptyList();
    }
  }

  /**
   * Returns the outlet streams of a unit, never throwing.
   *
   * @param unit the unit; must be non-null
   * @return the outlet streams, or an empty list if not available
   */
  private static List<StreamInterface> safeOutlets(ProcessEquipmentInterface unit) {
    try {
      List<StreamInterface> outlets = unit.getOutletStreams();
      return outlets == null ? Collections.<StreamInterface>emptyList() : outlets;
    } catch (Exception e) {
      return Collections.<StreamInterface>emptyList();
    }
  }

  /**
   * Gets the base-case process system.
   *
   * @return the process system
   */
  public ProcessSystem getProcess() {
    return process;
  }

  /**
   * Gets the master component slate (union of all component names across the process).
   *
   * @return an unmodifiable list of component names
   */
  public List<String> getComponentNames() {
    return Collections.unmodifiableList(componentNames);
  }

  /**
   * Gets the molar mass (kg/mol) of a component on the master slate.
   *
   * @param componentName the component name; must be on the master slate
   * @return the molar mass in kg/mol, or {@code 0.0} if unknown
   */
  public double getMolarMass(String componentName) {
    Double value = molarMass.get(componentName);
    return value == null ? 0.0 : value;
  }

  /**
   * Gets the frozen split factors per unit, keyed by unit name.
   *
   * @return an unmodifiable, insertion-ordered map of unit splits
   */
  public Map<String, UnitSplit> getUnitSplits() {
    return Collections.unmodifiableMap(unitSplits);
  }

  /**
   * Gets the node units participating in the proxy network, in process order.
   *
   * @return an unmodifiable list of node units
   */
  public List<ProcessEquipmentInterface> getNodeUnits() {
    return Collections.unmodifiableList(nodeUnits);
  }
}
