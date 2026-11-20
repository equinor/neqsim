package neqsim.process.util.mercury;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.mercury.MercuryPhaseEquilibrium;

/**
 * Tracks mercury through a calculated process.
 *
 * <p>
 * Use it with a fluid that holds the component mercury, normally {@code SystemUMRPRUMCEos}. The process calculation
 * itself distributes mercury between gas, oil and water in every separation with the equation of state. This class adds
 * what the process calculation does not give:
 * </p>
 *
 * <ul>
 * <li>{@link #analyze(ProcessSystem)} reports the mercury flow, the mercury concentration in each phase, the degree of
 * mercury saturation and the free liquid or solid mercury of every stream, and the mercury balance of every unit
 * operation and of the process.</li>
 * <li>{@link #setFreeMercuryAccumulation(ProcessSystem, boolean)} makes the separators hold back free mercury, so that
 * the streams after a separator carry no more mercury than they dissolve.</li>
 * </ul>
 *
 * <p>
 * Streams are found from the inlet and outlet streams that the unit operations report. A stream that no unit operation
 * produces is a feed, and a stream that no unit operation uses is a product.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryProcessAnalyzer {
  /** Default molar mass of mercury in kg/mol, used when no stream holds mercury. */
  private static final double DEFAULT_MOLAR_MASS = 0.20059;

  /**
   * Utility class; not instantiated.
   */
  private MercuryProcessAnalyzer() {
  }

  /**
   * Track mercury through a calculated process.
   *
   * @param process process that has been run
   * @return mercury in every stream and unit operation
   * @throws IllegalArgumentException if the process is null
   */
  public static MercuryProcessReport analyze(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process can not be null");
    }
    return analyzeUnits(process.getUnitOperations());
  }

  /**
   * Track mercury through a calculated plant model with several process areas.
   *
   * @param model plant model that has been run
   * @return mercury in every stream and unit operation of all process areas
   * @throws IllegalArgumentException if the model is null
   */
  public static MercuryProcessReport analyze(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model can not be null");
    }
    List<ProcessEquipmentInterface> units = new ArrayList<ProcessEquipmentInterface>();
    for (ProcessSystem process : model.getAllProcesses()) {
      units.addAll(process.getUnitOperations());
    }
    return analyzeUnits(units);
  }

  /**
   * Choose whether the separators of a process hold back free mercury.
   *
   * <p>
   * Sets {@link Separator#setFreeMercuryAccumulation(boolean)} on every separator. Run the process again afterwards.
   * </p>
   *
   * @param process process to change
   * @param enabled true to hold back free mercury in the separators
   * @return number of separators changed
   * @throws IllegalArgumentException if the process is null
   */
  public static int setFreeMercuryAccumulation(ProcessSystem process, boolean enabled) {
    if (process == null) {
      throw new IllegalArgumentException("process can not be null");
    }
    int separators = 0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof Separator) {
        ((Separator) unit).setFreeMercuryAccumulation(enabled);
        separators++;
      }
    }
    return separators;
  }

  /**
   * Choose whether the separators of a plant model hold back free mercury.
   *
   * @param model plant model to change
   * @param enabled true to hold back free mercury in the separators
   * @return number of separators changed
   * @throws IllegalArgumentException if the model is null
   */
  public static int setFreeMercuryAccumulation(ProcessModel model, boolean enabled) {
    if (model == null) {
      throw new IllegalArgumentException("model can not be null");
    }
    int separators = 0;
    for (ProcessSystem process : model.getAllProcesses()) {
      separators += setFreeMercuryAccumulation(process, enabled);
    }
    return separators;
  }

  /**
   * Track mercury through a list of unit operations.
   *
   * @param units unit operations in calculation order
   * @return mercury in every stream and unit operation
   */
  private static MercuryProcessReport analyzeUnits(List<ProcessEquipmentInterface> units) {
    Map<StreamInterface, StreamNode> nodes = new IdentityHashMap<StreamInterface, StreamNode>();
    List<StreamNode> order = new ArrayList<StreamNode>();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        StreamNode node = node((StreamInterface) unit, nodes, order);
        for (StreamInterface inlet : unit.getInletStreams()) {
          if (inlet != null && inlet != unit) {
            node(inlet, nodes, order).used = true;
            if (node.sourceUnit == null) {
              node.sourceUnit = inlet.getName();
            }
          }
        }
      } else {
        for (StreamInterface inlet : unit.getInletStreams()) {
          if (inlet != null) {
            node(inlet, nodes, order).used = true;
          }
        }
        for (StreamInterface outlet : unit.getOutletStreams()) {
          if (outlet != null) {
            StreamNode node = node(outlet, nodes, order);
            if (node.sourceUnit == null) {
              node.sourceUnit = unit.getName();
            }
          }
        }
      }
    }

    List<MercuryStreamResult> streamResults = createStreamResults(order);
    double molarMass = DEFAULT_MOLAR_MASS;
    for (StreamNode node : order) {
      SystemInterface fluid = node.stream.getFluid();
      if (fluid != null && fluid.getPhase(0).hasComponent(MercuryPhaseEquilibrium.MERCURY)) {
        molarMass = fluid.getPhase(0).getComponent(MercuryPhaseEquilibrium.MERCURY).getMolarMass();
        break;
      }
    }

    List<MercuryUnitResult> unitResults = new ArrayList<MercuryUnitResult>();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        continue;
      }
      List<StreamInterface> inlets = unit.getInletStreams();
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (inlets.isEmpty() && outlets.isEmpty()) {
        continue;
      }
      double[] flows = new double[3];
      flows[0] = mercuryMoles(inlets, nodes);
      flows[1] = mercuryMoles(outlets, nodes);
      String state = "none";
      if (unit instanceof Separator) {
        flows[2] = ((Separator) unit).getFreeMercuryAccumulationRate("mole/sec");
        state = ((Separator) unit).getFreeMercuryState();
      }
      unitResults.add(new MercuryUnitResult(unit.getName(), unit.getClass().getSimpleName(), molarMass, flows, state));
    }
    return new MercuryProcessReport(streamResults, unitResults, molarMass);
  }

  /**
   * Find or register the node of a stream.
   *
   * @param stream process stream
   * @param nodes nodes by stream identity
   * @param order nodes in the order they are found
   * @return node of the stream
   */
  private static StreamNode node(StreamInterface stream, Map<StreamInterface, StreamNode> nodes,
      List<StreamNode> order) {
    StreamNode node = nodes.get(stream);
    if (node == null) {
      node = new StreamNode(stream);
      nodes.put(stream, node);
      order.add(node);
    }
    return node;
  }

  /**
   * Give every stream a unique label and calculate its mercury distribution.
   *
   * @param order stream nodes in the order they are found
   * @return mercury in each stream, in the same order
   */
  private static List<MercuryStreamResult> createStreamResults(List<StreamNode> order) {
    Map<String, Integer> nameCount = new HashMap<String, Integer>();
    for (StreamNode node : order) {
      Integer count = nameCount.get(node.stream.getName());
      nameCount.put(node.stream.getName(), count == null ? 1 : count + 1);
    }
    Set<String> labels = new HashSet<String>();
    List<MercuryStreamResult> results = new ArrayList<MercuryStreamResult>();
    for (StreamNode node : order) {
      String name = node.stream.getName();
      String base = nameCount.get(name) > 1 && node.sourceUnit != null ? node.sourceUnit + "." + name : name;
      String label = base;
      int number = 2;
      while (!labels.add(label)) {
        label = base + " (" + number + ")";
        number++;
      }
      node.result = new MercuryStreamResult(label, node.sourceUnit == null ? "" : node.sourceUnit,
          node.sourceUnit == null, !node.used, node.stream);
      results.add(node.result);
    }
    return results;
  }

  /**
   * Sum the mercury flow of a list of streams.
   *
   * @param streams streams of a unit operation
   * @param nodes nodes by stream identity, with calculated results
   * @return mercury flow in mole/sec
   */
  private static double mercuryMoles(List<StreamInterface> streams, Map<StreamInterface, StreamNode> nodes) {
    double moles = 0.0;
    for (StreamInterface stream : streams) {
      StreamNode node = stream == null ? null : nodes.get(stream);
      if (node != null && node.result != null) {
        moles += node.result.getMercuryFlowRate("mole/sec");
      }
    }
    return moles;
  }

  /**
   * A stream with its place in the process.
   */
  private static final class StreamNode {
    /** Process stream. */
    private final StreamInterface stream;

    /** Name of the unit operation that produces the stream, null for a feed stream. */
    private String sourceUnit = null;

    /** True when a unit operation uses the stream as inlet. */
    private boolean used = false;

    /** Mercury in the stream. */
    private MercuryStreamResult result = null;

    /**
     * Create a node.
     *
     * @param stream process stream
     */
    private StreamNode(StreamInterface stream) {
      this.stream = stream;
    }
  }
}
