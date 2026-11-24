package neqsim.process.fielddevelopment.hybrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.fielddevelopment.reservoir.VlpProvider;

/**
 * Tree-shaped gathering network between the wells and one fixed-pressure boundary (separator or host arrival).
 *
 * <p>
 * Nodes are manifolds, tie-in points, PLETs or a riser base; every node except the boundary has one branch leading
 * towards the boundary. A branch is a flowline, a riser or any chain of them, described by a lift model that returns
 * the branch inlet pressure for the commingled surface rates and the outlet pressure. With the well rates given,
 * {@link #solvePressures()} walks from the boundary upstream and returns the pressure of every node; the wells then
 * respond to the pressure of the node they are tied to. Each branch model is a {@link CorrectedVlpProvider}, so a fast
 * lift curve can be corrected against, or replaced by, a full multiphase pipe model without changing the network.
 * </p>
 *
 * <p>
 * Basis of every branch lift model: oil rate in Sm3/day, water cut as fraction of the liquid and gas-oil ratio in
 * Sm3/Sm3 of the commingled stream. Looped networks are outside this class; use {@code LoopedPipeNetwork} for the
 * hydraulics of a looped system.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class GatheringNetwork {
  /**
   * One branch of the network with its last solution.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class Branch {
    private final String name;
    private final String fromNode;
    private final String toNode;
    private final CorrectedVlpProvider liftModel;
    private double oilRate;
    private double waterRate;
    private double gasRate;
    private double inletPressure = Double.NaN;
    private double outletPressure = Double.NaN;

    /**
     * Creates a branch.
     *
     * @param name branch name
     * @param fromNode upstream node
     * @param toNode downstream node
     * @param liftModel corrected lift model of the branch
     */
    Branch(String name, String fromNode, String toNode, CorrectedVlpProvider liftModel) {
      this.name = name;
      this.fromNode = fromNode;
      this.toNode = toNode;
      this.liftModel = liftModel;
    }

    /**
     * Gets the branch name.
     *
     * @return branch name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the upstream node.
     *
     * @return upstream node name
     */
    public String getFromNode() {
      return fromNode;
    }

    /**
     * Gets the downstream node.
     *
     * @return downstream node name
     */
    public String getToNode() {
      return toNode;
    }

    /**
     * Gets the lift model with its correction state.
     *
     * @return corrected lift model
     */
    public CorrectedVlpProvider getLiftModel() {
      return liftModel;
    }

    /**
     * Gets the oil rate through the branch at the last solution.
     *
     * @return oil rate in Sm3/day
     */
    public double getOilRate() {
      return oilRate;
    }

    /**
     * Gets the water rate through the branch at the last solution.
     *
     * @return water rate in Sm3/day
     */
    public double getWaterRate() {
      return waterRate;
    }

    /**
     * Gets the gas rate through the branch at the last solution.
     *
     * @return gas rate in Sm3/day
     */
    public double getGasRate() {
      return gasRate;
    }

    /**
     * Gets the branch inlet pressure at the last solution.
     *
     * @return inlet pressure in bara
     */
    public double getInletPressure() {
      return inletPressure;
    }

    /**
     * Gets the branch outlet pressure at the last solution.
     *
     * @return outlet pressure in bara
     */
    public double getOutletPressure() {
      return outletPressure;
    }

    /**
     * Gets the pressure drop over the branch at the last solution.
     *
     * @return inlet minus outlet pressure in bar
     */
    public double getPressureDrop() {
      return inletPressure - outletPressure;
    }
  }

  private final String boundaryNode;
  private double boundaryPressure;
  private final Map<String, Branch> branchFromNode = new LinkedHashMap<String, Branch>();
  private final Map<HybridWell, String> wellNodes = new LinkedHashMap<HybridWell, String>();

  /**
   * Creates a network with its fixed-pressure boundary.
   *
   * @param boundaryNode name of the boundary node (separator or host arrival)
   * @param boundaryPressureBara pressure of the boundary node in bara
   */
  public GatheringNetwork(String boundaryNode, double boundaryPressureBara) {
    if (boundaryNode == null) {
      throw new IllegalArgumentException("Boundary node name must not be null");
    }
    this.boundaryNode = boundaryNode;
    this.boundaryPressure = boundaryPressureBara;
  }

  /**
   * Gets the name of the boundary node.
   *
   * @return boundary node name
   */
  public String getBoundaryNode() {
    return boundaryNode;
  }

  /**
   * Sets the pressure of the boundary node, for example a new separator pressure.
   *
   * @param pressureBara boundary pressure in bara
   */
  public void setBoundaryPressure(double pressureBara) {
    this.boundaryPressure = pressureBara;
  }

  /**
   * Gets the pressure of the boundary node.
   *
   * @return boundary pressure in bara
   */
  public double getBoundaryPressure() {
    return boundaryPressure;
  }

  /**
   * Adds a branch leading from a node towards the boundary.
   *
   * @param name branch name
   * @param fromNode upstream node; each node has one outgoing branch
   * @param toNode downstream node
   * @param liftModel branch lift model on the oil rate, water cut and gas-oil ratio basis: inlet pressure for the
   * commingled rates and an outlet pressure
   * @return the branch
   * @throws IllegalArgumentException if the upstream node already has a branch or is the boundary node
   */
  public Branch addBranch(String name, String fromNode, String toNode, VlpProvider liftModel) {
    if (name == null || fromNode == null || toNode == null || liftModel == null) {
      throw new IllegalArgumentException("Branch name, nodes and lift model must not be null");
    }
    if (fromNode.equals(boundaryNode)) {
      throw new IllegalArgumentException("The boundary node " + boundaryNode + " cannot have an outgoing branch");
    }
    if (branchFromNode.containsKey(fromNode)) {
      throw new IllegalArgumentException("Node " + fromNode + " already has an outgoing branch");
    }
    CorrectedVlpProvider corrected = liftModel instanceof CorrectedVlpProvider ? (CorrectedVlpProvider) liftModel
        : new CorrectedVlpProvider(liftModel);
    Branch branch = new Branch(name, fromNode, toNode, corrected);
    branchFromNode.put(fromNode, branch);
    return branch;
  }

  /**
   * Sets the high-fidelity reference model of a branch, used in full network mode and for hybrid corrections.
   *
   * @param branchName branch name
   * @param reference lift model on the same basis as the branch surrogate
   * @throws IllegalArgumentException if the branch does not exist
   */
  public void setReferenceModel(String branchName, VlpProvider reference) {
    getBranch(branchName).getLiftModel().setReference(reference);
  }

  /**
   * Gets a branch by name.
   *
   * @param branchName branch name
   * @return the branch
   * @throws IllegalArgumentException if the branch does not exist
   */
  public Branch getBranch(String branchName) {
    for (Branch branch : branchFromNode.values()) {
      if (branch.getName().equals(branchName)) {
        return branch;
      }
    }
    throw new IllegalArgumentException("Unknown branch " + branchName);
  }

  /**
   * Gets all branches.
   *
   * @return branches in the order they were added
   */
  public List<Branch> getBranches() {
    return new ArrayList<Branch>(branchFromNode.values());
  }

  /**
   * Ties a well to a node, or re-routes a well that is already in the network.
   *
   * @param well the well
   * @param node node name; the boundary node or a node with an outgoing branch
   * @throws IllegalArgumentException if the node is unknown
   */
  public void connectWell(HybridWell well, String node) {
    if (!node.equals(boundaryNode) && !branchFromNode.containsKey(node)) {
      throw new IllegalArgumentException("Unknown node " + node + "; add its branch before connecting wells");
    }
    wellNodes.put(well, node);
  }

  /**
   * Gets the wells of the network.
   *
   * @return wells in the order they were connected
   */
  public List<HybridWell> getWells() {
    return new ArrayList<HybridWell>(wellNodes.keySet());
  }

  /**
   * Gets the node a well is tied to.
   *
   * @param well the well
   * @return node name, or null if the well is not in the network
   */
  public String getNodeOf(HybridWell well) {
    return wellNodes.get(well);
  }

  /**
   * Checks the topology: every well node must reach the boundary node without a cycle.
   *
   * @return list of problems with a remediation hint; empty when the network is valid
   */
  public List<String> validate() {
    List<String> problems = new ArrayList<String>();
    if (wellNodes.isEmpty()) {
      problems.add("No wells connected; call connectWell for every well");
    }
    for (Branch branch : branchFromNode.values()) {
      String node = branch.getFromNode();
      int hops = 0;
      while (!node.equals(boundaryNode)) {
        Branch next = branchFromNode.get(node);
        if (next == null) {
          problems.add("Node " + node + " (downstream of branch " + branch.getName()
              + ") has no branch towards the boundary node " + boundaryNode + "; add one with addBranch");
          break;
        }
        node = next.getToNode();
        if (++hops > branchFromNode.size()) {
          problems.add("Branch " + branch.getName() + " is part of a loop; this class handles tree networks only");
          break;
        }
      }
    }
    return problems;
  }

  /**
   * Sums the well rates into the branches they flow through.
   */
  private void accumulateFlows() {
    for (Branch branch : branchFromNode.values()) {
      branch.oilRate = 0.0;
      branch.waterRate = 0.0;
      branch.gasRate = 0.0;
    }
    for (Map.Entry<HybridWell, String> entry : wellNodes.entrySet()) {
      HybridWell well = entry.getKey();
      String node = entry.getValue();
      int hops = 0;
      while (!node.equals(boundaryNode) && hops++ <= branchFromNode.size()) {
        Branch branch = branchFromNode.get(node);
        if (branch == null) {
          throw new IllegalStateException("Node " + node + " has no branch towards the boundary node");
        }
        branch.oilRate += well.getOilRate();
        branch.waterRate += well.getWaterRate();
        branch.gasRate += well.getGasRate();
        node = branch.getToNode();
      }
    }
  }

  /**
   * Calculates the node pressures for the current well rates, walking upstream from the boundary node.
   *
   * @return node pressures in bara keyed by node name, including the boundary node
   * @throws IllegalStateException if the topology is invalid or a branch cannot carry its rate
   */
  public Map<String, Double> solvePressures() {
    return walk(false);
  }

  /**
   * Corrects every branch surrogate against its reference model at the current well rates. Afterwards the corrected
   * surrogates reproduce the node pressures of the reference network at this operating point.
   *
   * @return largest change of a branch pressure offset in bar
   */
  public double calibrate() {
    Map<String, Double> change = walk(true);
    double largest = 0.0;
    for (Double value : change.values()) {
      largest = Math.max(largest, value.doubleValue());
    }
    return largest;
  }

  /**
   * Walks the tree from the boundary node and either evaluates or calibrates the branch models.
   *
   * @param calibrate true to calibrate the surrogates against the reference models
   * @return node pressures in bara, or the offset change per upstream node in bar when calibrating
   */
  private Map<String, Double> walk(boolean calibrate) {
    accumulateFlows();
    Map<String, Double> pressures = new LinkedHashMap<String, Double>();
    Map<String, Double> changes = new LinkedHashMap<String, Double>();
    pressures.put(boundaryNode, boundaryPressure);
    for (String node : branchFromNode.keySet()) {
      resolve(node, pressures, changes, calibrate, 0);
    }
    return calibrate ? changes : pressures;
  }

  /**
   * Resolves the pressure of a node recursively.
   *
   * @param node node name
   * @param pressures pressures found so far, updated in place
   * @param changes offset changes found so far, updated in place when calibrating
   * @param calibrate true to calibrate the surrogates against the reference models
   * @param depth recursion depth, to detect loops
   * @return node pressure in bara
   */
  private double resolve(String node, Map<String, Double> pressures, Map<String, Double> changes, boolean calibrate,
      int depth) {
    Double known = pressures.get(node);
    if (known != null) {
      return known.doubleValue();
    }
    Branch branch = branchFromNode.get(node);
    if (branch == null || depth > branchFromNode.size()) {
      throw new IllegalStateException("Node " + node + " does not reach the boundary node " + boundaryNode);
    }
    double outlet = resolve(branch.getToNode(), pressures, changes, calibrate, depth + 1);
    double inlet = outlet;
    if (branch.oilRate > 1.0e-9) {
      double liquid = branch.oilRate + branch.waterRate;
      double waterCut = branch.waterRate / liquid;
      double gasOilRatio = branch.gasRate / branch.oilRate;
      if (calibrate && branch.getLiftModel().hasReference()) {
        inlet = branch.getLiftModel().referencePressure(branch.oilRate, outlet, waterCut, gasOilRatio, 0.0);
        double fast = branch.getLiftModel().surrogatePressure(branch.oilRate, outlet, waterCut, gasOilRatio, 0.0);
        if (isUsable(inlet) && isUsable(fast)) {
          double offset = inlet - fast;
          changes.put(node, Math.abs(offset - branch.getLiftModel().getOffsetBar()));
          branch.getLiftModel().setOffsetBar(offset);
        } else {
          inlet = branch.getLiftModel().bottomHolePressure(branch.oilRate, outlet, waterCut, gasOilRatio, 0.0);
        }
      } else {
        inlet = branch.getLiftModel().bottomHolePressure(branch.oilRate, outlet, waterCut, gasOilRatio, 0.0);
      }
      if (!isUsable(inlet)) {
        inlet = branch.getLiftModel().surrogatePressure(branch.oilRate, outlet, waterCut, gasOilRatio, 0.0)
            + branch.getLiftModel().getOffsetBar();
      }
      if (!isUsable(inlet)) {
        throw new IllegalStateException("Branch " + branch.getName() + " cannot carry " + branch.oilRate
            + " Sm3/day oil against " + outlet + " bara; check the lift curve range or the branch capacity");
      }
    }
    branch.inletPressure = inlet;
    branch.outletPressure = outlet;
    pressures.put(node, inlet);
    return inlet;
  }

  /**
   * Tells whether a pressure is a finite number.
   *
   * @param pressure pressure in bara
   * @return true for a finite value
   */
  private static boolean isUsable(double pressure) {
    return !Double.isNaN(pressure) && !Double.isInfinite(pressure);
  }
}
