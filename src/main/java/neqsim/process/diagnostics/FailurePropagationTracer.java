package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Traces how a failure propagates through a process system by following stream connectivity.
 *
 * <p>
 * Starting from the initiating equipment, the tracer walks downstream and upstream through
 * connected streams to build a propagation chain showing which equipment is affected and in what
 * order.
 * </p>
 *
 * <p>
 * This is used by the {@link RootCauseAnalyzer} to understand the cascade of events from the root
 * cause to the observed trip.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class FailurePropagationTracer implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final ProcessSystem processSystem;

  /**
   * Constructs a tracer for a process system.
   *
   * @param processSystem the process system to trace through
   */
  public FailurePropagationTracer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Traces the propagation path downstream from the initiating equipment.
   *
   * @param initiatingEquipmentName name of the equipment where the failure started
   * @return ordered list of propagation steps
   */
  public PropagationChain traceDownstream(String initiatingEquipmentName) {
    return trace(initiatingEquipmentName, true);
  }

  /**
   * Traces the propagation path upstream from the tripped equipment to find the root cause.
   *
   * @param trippedEquipmentName name of the equipment that tripped
   * @return ordered list of propagation steps (from upstream cause to the trip)
   */
  public PropagationChain traceUpstream(String trippedEquipmentName) {
    PropagationChain chain = trace(trippedEquipmentName, false);
    // Reverse so the chain reads from root cause to effect
    chain.reverse();
    return chain;
  }

  /**
   * Traces the full propagation path both upstream and downstream from the initiating equipment.
   *
   * @param equipmentName name of the central equipment
   * @return combined propagation chain
   */
  public PropagationChain traceBidirectional(String equipmentName) {
    PropagationChain upstream = trace(equipmentName, false);
    PropagationChain downstream = trace(equipmentName, true);

    upstream.reverse();

    PropagationChain combined = new PropagationChain();
    // Add upstream steps (root to center)
    for (PropagationStep step : upstream.getSteps()) {
      combined.addStep(step);
    }
    // Add downstream steps (center to effects), skip first if it duplicates the center
    List<PropagationStep> downstreamSteps = downstream.getSteps();
    int startIdx = downstreamSteps.size() > 0
        && downstreamSteps.get(0).getEquipmentName().equals(equipmentName) ? 1 : 0;
    for (int i = startIdx; i < downstreamSteps.size(); i++) {
      combined.addStep(downstreamSteps.get(i));
    }
    return combined;
  }

  /**
   * Internal trace implementation.
   *
   * @param startEquipmentName starting equipment name
   * @param downstream true for downstream, false for upstream
   * @return propagation chain
   */
  private PropagationChain trace(String startEquipmentName, boolean downstream) {
    PropagationChain chain = new PropagationChain();
    Set<String> visited = new LinkedHashSet<>();

    ProcessEquipmentInterface startEquip = findEquipment(startEquipmentName);
    if (startEquip == null) {
      return chain;
    }

    chain.addStep(new PropagationStep(startEquipmentName, startEquip.getClass().getSimpleName(), 0,
        "Initiating equipment"));
    visited.add(startEquipmentName);

    traceRecursive(startEquip, downstream, visited, chain, 1);
    return chain;
  }

  /**
   * Recursive trace through connected equipment.
   *
   * @param equipment current equipment
   * @param downstream direction of trace
   * @param visited set of already-visited equipment names
   * @param chain the chain to append to
   * @param depth current depth
   */
  private void traceRecursive(ProcessEquipmentInterface equipment, boolean downstream,
      Set<String> visited, PropagationChain chain, int depth) {
    if (depth > 50) {
      return; // Safety limit
    }

    List<StreamInterface> streams =
        downstream ? equipment.getOutletStreams() : equipment.getInletStreams();

    if (streams == null) {
      return;
    }

    for (StreamInterface stream : streams) {
      // Find equipment connected to the other end of this stream
      for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
        if (visited.contains(eq.getName())) {
          continue;
        }

        List<StreamInterface> eqStreams = downstream ? eq.getInletStreams() : eq.getOutletStreams();
        if (eqStreams != null && containsStream(eqStreams, stream)) {
          visited.add(eq.getName());
          String connection = downstream ? stream.getName() + " -> " + eq.getName()
              : eq.getName() + " -> " + stream.getName();
          chain.addStep(new PropagationStep(eq.getName(), eq.getClass().getSimpleName(), depth,
              "Connected via " + connection));
          traceRecursive(eq, downstream, visited, chain, depth + 1);
        }
      }
    }
  }

  /**
   * Finds equipment by name in the process system.
   *
   * @param name equipment name
   * @return the equipment, or null
   */
  private ProcessEquipmentInterface findEquipment(String name) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq.getName().equals(name)) {
        return eq;
      }
    }
    return null;
  }

  /**
   * Checks if a list of streams contains a specific stream (by identity or name).
   *
   * @param streams list to search
   * @param target stream to find
   * @return true if the stream is in the list
   */
  private boolean containsStream(List<StreamInterface> streams, StreamInterface target) {
    for (StreamInterface s : streams) {
      if (s == target || (s != null && s.getName().equals(target.getName()))) {
        return true;
      }
    }
    return false;
  }

  /**
   * A single step in a failure propagation chain.
   *
   * @author esol
   * @version 1.0
   */
  public static class PropagationStep implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String equipmentName;
    private final String equipmentType;
    private final int depth;
    private final String connection;

    /**
     * Constructs a propagation step.
     *
     * @param equipmentName name of the equipment at this step
     * @param equipmentType class name of the equipment
     * @param depth distance from the initiating equipment
     * @param connection description of the connecting stream
     */
    public PropagationStep(String equipmentName, String equipmentType, int depth,
        String connection) {
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.depth = depth;
      this.connection = connection;
    }

    /**
     * Returns the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Returns the equipment type.
     *
     * @return equipment class name
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Returns the depth from the initiating equipment.
     *
     * @return depth (0 = initiating equipment)
     */
    public int getDepth() {
      return depth;
    }

    /**
     * Returns the connection description.
     *
     * @return connection text
     */
    public String getConnection() {
      return connection;
    }

    @Override
    public String toString() {
      return String.format("[depth=%d] %s (%s) - %s", depth, equipmentName, equipmentType,
          connection);
    }
  }

  /**
   * An ordered chain of propagation steps from root cause to effects.
   *
   * @author esol
   * @version 1.0
   */
  public static class PropagationChain implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<PropagationStep> steps = new ArrayList<>();

    /**
     * Constructs an empty chain.
     */
    public PropagationChain() {}

    /**
     * Adds a step to the chain.
     *
     * @param step the step to add
     */
    public void addStep(PropagationStep step) {
      steps.add(step);
    }

    /**
     * Returns the ordered list of steps.
     *
     * @return unmodifiable list of steps
     */
    public List<PropagationStep> getSteps() {
      return Collections.unmodifiableList(steps);
    }

    /**
     * Returns the number of steps in the chain.
     *
     * @return step count
     */
    public int size() {
      return steps.size();
    }

    /**
     * Reverses the order of steps (e.g. to convert upstream trace to root-cause-first order).
     */
    public void reverse() {
      Collections.reverse(steps);
    }

    /**
     * Returns the names of all equipment in the chain.
     *
     * @return ordered list of equipment names
     */
    public List<String> getEquipmentNames() {
      List<String> names = new ArrayList<>();
      for (PropagationStep step : steps) {
        names.add(step.getEquipmentName());
      }
      return names;
    }

    /**
     * Serialises the chain to JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      List<Map<String, Object>> list = new ArrayList<>();
      for (PropagationStep step : steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("equipment", step.getEquipmentName());
        map.put("type", step.getEquipmentType());
        map.put("depth", step.getDepth());
        map.put("connection", step.getConnection());
        list.add(map);
      }
      return GSON.toJson(list);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("PropagationChain[");
      for (int i = 0; i < steps.size(); i++) {
        if (i > 0) {
          sb.append(" -> ");
        }
        sb.append(steps.get(i).getEquipmentName());
      }
      sb.append("]");
      return sb.toString();
    }
  }
}
