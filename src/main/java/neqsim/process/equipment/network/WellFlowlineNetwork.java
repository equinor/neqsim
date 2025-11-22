package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.util.unit.PressureUnit;

/**
 * Network wrapper that links {@link WellFlow} inflow models with {@link PipeBeggsAndBrills}
 * hydraulics and gathers the outlets into configurable manifolds.
 */
public class WellFlowlineNetwork extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** A branch represents one well + flowline path to a manifold. */
  public static class Branch {
    private final String name;
    private final WellFlow well;
    private final PipeBeggsAndBrills pipeline;
    private ThrottlingValve choke;

    Branch(String name, WellFlow well, PipeBeggsAndBrills pipeline, ThrottlingValve choke) {
      this.name = name;
      this.well = well;
      this.pipeline = pipeline;
      this.choke = choke;
      updatePipelineInlet();
    }

    private void updatePipelineInlet() {
      if (choke != null) {
        choke.setInletStream(well.getOutletStream());
        pipeline.setInletStream(choke.getOutletStream());
      } else {
        pipeline.setInletStream(well.getOutletStream());
      }
    }

    /** Run steady-state inflow and hydraulics for this branch. */
    void run(UUID id) {
      well.run(id);
      if (choke != null) {
        choke.run(id);
      }
      pipeline.run(id);
    }

    /** Run transient inflow and hydraulics for this branch. */
    void runTransient(double dt, UUID id) {
      well.runTransient(dt, id);
      if (choke != null) {
        choke.runTransient(dt, id);
      }
      pipeline.runTransient(dt, id);
    }

    public String getName() {
      return name;
    }

    public WellFlow getWell() {
      return well;
    }

    public PipeBeggsAndBrills getPipeline() {
      return pipeline;
    }

    public ThrottlingValve getChoke() {
      return choke;
    }

    public void setChoke(ThrottlingValve choke) {
      this.choke = choke;
      updatePipelineInlet();
    }
  }

  /**
   * Represents a manifold that mixes incoming branches (and optional upstream pipeline) before
   * optionally sending flow further downstream through a connecting pipeline.
   */
  public static class ManifoldNode {
    private final String name;
    private final Mixer mixer;
    private final List<Branch> branches = new ArrayList<>();
    private final List<PipeBeggsAndBrills> inboundPipelines = new ArrayList<>();
    private PipeBeggsAndBrills pipelineToNext;

    ManifoldNode(String name) {
      this.name = name;
      this.mixer = new Mixer(name + " mixer");
    }

    public String getName() {
      return name;
    }

    public Mixer getMixer() {
      return mixer;
    }

    public List<Branch> getBranches() {
      return Collections.unmodifiableList(branches);
    }

    void addBranch(Branch branch) {
      branches.add(branch);
      mixer.addStream(branch.getPipeline().getOutletStream());
    }

    void addInboundPipeline(PipeBeggsAndBrills pipeline) {
      if (pipeline != null) {
        inboundPipelines.add(pipeline);
        // Note: Stream is added to mixer in runManifold() after pipeline has been wired and run
      }
    }

    List<PipeBeggsAndBrills> getInboundPipelines() {
      return Collections.unmodifiableList(inboundPipelines);
    }

    void setPipelineToNext(PipeBeggsAndBrills pipelineToNext) {
      this.pipelineToNext = pipelineToNext;
    }

    PipeBeggsAndBrills getPipelineToNext() {
      return pipelineToNext;
    }
  }

  private final List<ManifoldNode> manifolds = new ArrayList<>();
  private PipeBeggsAndBrills facilityPipeline;
  private boolean propagateArrivalPressureToWells = true;
  private boolean forceFlowFromPressureSolve = true;
  private Double targetEndpointPressure;
  private String targetEndpointPressureUnit = "bara";
  private double iterationTolerance = 1.0e-4;
  private int maxIterations = 20;
  private Double lastTerminalManifoldPressure;

  /**
   * Create a new network wrapper.
   *
   * @param name network name
   */
  public WellFlowlineNetwork(String name) {
    super(name);
    manifolds.add(new ManifoldNode(name + " arrival manifold"));
  }

  /**
   * Create an unconnected manifold node that can later be linked to other manifolds via pipelines.
   *
   * @param name manifold name
   * @return the created {@link ManifoldNode}
   */
  public ManifoldNode createManifold(String name) {
    ManifoldNode node = new ManifoldNode(name);
    manifolds.add(node);
    return node;
  }

  /**
   * Add a branch to the network using preconfigured well and pipeline units. The pipeline inlet is
   * forced to the well outlet and its outlet is attached to the terminal manifold by default.
   *
   * @param branchName branch identifier
   * @param well configured {@link WellFlow}
   * @param pipeline configured {@link PipeBeggsAndBrills}
   * @return the added {@link Branch}
   */
  public Branch addBranch(String branchName, WellFlow well, PipeBeggsAndBrills pipeline) {
    return addBranch(branchName, well, pipeline, null, getTailManifold());
  }

  /**
   * Add a branch to a specific manifold in the network.
   *
   * @param branchName branch identifier
   * @param well configured {@link WellFlow}
   * @param pipeline configured {@link PipeBeggsAndBrills}
   * @param manifold manifold that should receive the branch outlet
   * @return the added {@link Branch}
   */
  public Branch addBranch(String branchName, WellFlow well, PipeBeggsAndBrills pipeline,
      ManifoldNode manifold) {
    return addBranch(branchName, well, pipeline, null, manifold);
  }

  /**
   * Add a branch with an optional production choke to a specific manifold.
   *
   * @param branchName branch identifier
   * @param well configured {@link WellFlow}
   * @param pipeline configured {@link PipeBeggsAndBrills}
   * @param choke optional {@link ThrottlingValve} placed between the well outlet and pipeline inlet
   * @param manifold manifold that should receive the branch outlet
   * @return the added {@link Branch}
   */
  public Branch addBranch(String branchName, WellFlow well, PipeBeggsAndBrills pipeline,
      ThrottlingValve choke, ManifoldNode manifold) {
    Objects.requireNonNull(well, "well cannot be null");
    Objects.requireNonNull(pipeline, "pipeline cannot be null");
    Objects.requireNonNull(manifold, "manifold cannot be null");
    Branch branch = new Branch(branchName, well, pipeline, choke);
    manifold.addBranch(branch);
    return branch;
  }

  /**
   * Convenience helper that creates a branch from a reservoir stream by instantiating a
   * {@link WellFlow} and {@link PipeBeggsAndBrills} pair and attaching it to the terminal manifold.
   *
   * @param branchName branch identifier
   * @param reservoirStream stream representing reservoir inflow
   * @return the created {@link Branch}
   */
  public Branch addBranch(String branchName, StreamInterface reservoirStream) {
    return addBranch(branchName, reservoirStream, getTailManifold());
  }

  /**
   * Convenience helper that creates a branch from a reservoir stream for a specific manifold.
   *
   * @param branchName branch identifier
   * @param reservoirStream stream representing reservoir inflow
   * @param manifold manifold that should receive the branch outlet
   * @return the created {@link Branch}
   */
  public Branch addBranch(String branchName, StreamInterface reservoirStream,
      ManifoldNode manifold) {
    WellFlow well = new WellFlow(branchName + " well");
    well.setInletStream(reservoirStream);
    PipeBeggsAndBrills pipeline =
        new PipeBeggsAndBrills(branchName + " pipeline", well.getOutletStream());
    return addBranch(branchName, well, pipeline, null, manifold);
  }

  /**
   * Get an immutable view of network branches for optimization or tuning.
   *
   * @return list of branches
   */
  public List<Branch> getBranches() {
    List<Branch> allBranches = new ArrayList<>();
    for (ManifoldNode manifold : manifolds) {
      allBranches.addAll(manifold.getBranches());
    }
    return Collections.unmodifiableList(allBranches);
  }

  /**
   * Get the configured manifolds in upstream-to-downstream order.
   *
   * @return manifolds
   */
  public List<ManifoldNode> getManifolds() {
    return Collections.unmodifiableList(manifolds);
  }

  /**
   * Get the arrival mixer that gathers all branch outlets (useful for reporting or re-routing).
   *
   * @return arrival mixer
   */
  public Mixer getArrivalMixer() {
    return getTailManifold().getMixer();
  }

  /**
   * Attach a common pipeline from the arrival manifold to the receiving facility. The pipeline
   * inlet is automatically wired to the terminal manifold outlet.
   *
   * @param pipeline configured {@link PipeBeggsAndBrills} representing the shared pipeline
   */
  public void setFacilityPipeline(PipeBeggsAndBrills pipeline) {
    this.facilityPipeline = pipeline;
    if (pipeline != null) {
      pipeline.setInletStream(getTailManifold().getMixer().getOutletStream());
    }
  }

  /**
   * Add an additional manifold downstream of the current endpoint, connecting it with the supplied
   * pipeline. The pipeline inlet is automatically wired to the current terminal manifold outlet.
   *
   * @param name manifold name
   * @param connectionPipeline pipeline from the current endpoint manifold to the new manifold
   * @return the created {@link ManifoldNode}
   */
  public ManifoldNode addManifold(String name, PipeBeggsAndBrills connectionPipeline) {
    Objects.requireNonNull(connectionPipeline, "connectionPipeline cannot be null");
    ManifoldNode previousTail = getTailManifold();
    // Note: inlet stream connection is deferred until run() when outlet stream is guaranteed to
    // exist
    previousTail.setPipelineToNext(connectionPipeline);

    ManifoldNode newNode = new ManifoldNode(name);
    newNode.addInboundPipeline(connectionPipeline);
    manifolds.add(newNode);
    return newNode;
  }

  /**
   * Connect an upstream manifold to a downstream manifold using the provided pipeline.
   *
   * @param upstream upstream manifold
   * @param downstream downstream manifold that should receive the pipeline outlet
   * @param connectionPipeline pipeline from upstream to downstream
   */
  public void connectManifolds(ManifoldNode upstream, ManifoldNode downstream,
      PipeBeggsAndBrills connectionPipeline) {
    Objects.requireNonNull(upstream, "upstream manifold cannot be null");
    Objects.requireNonNull(downstream, "downstream manifold cannot be null");
    Objects.requireNonNull(connectionPipeline, "connectionPipeline cannot be null");
    // Note: inlet stream connection is deferred until run() when outlet stream is guaranteed to
    // exist
    upstream.setPipelineToNext(connectionPipeline);
    downstream.addInboundPipeline(connectionPipeline);
  }

  /**
   * Control whether the arrival pressure should be pushed back to wells that are configured to
   * compute flow from outlet pressure (production-index, Vogel, or Fetkovich modes with
   * {@code solveFlowFromOutletPressure(true)}).
   *
   * @param propagate true to push arrival pressure back to wells
   */
  public void setPropagateArrivalPressureToWells(boolean propagate) {
    this.propagateArrivalPressureToWells = propagate;
  }

  /**
   * Automatically switch wells to solve for flow from outlet pressure when running with a specified
   * target endpoint pressure.
   *
   * @param enable true to force wells to solve for flow from pressure
   */
  public void setForceFlowFromPressureSolve(boolean enable) {
    this.forceFlowFromPressureSolve = enable;
  }

  /**
   * Specify the desired pressure at the network endpoint (arrival manifold if no facility pipeline,
   * otherwise the facility pipeline outlet). The network will iterate manifold pressure to match
   * this target while solving individual well rates from backpressure.
   *
   * @param pressure target endpoint pressure
   * @param unit pressure unit
   */
  public void setTargetEndpointPressure(double pressure, String unit) {
    this.targetEndpointPressure = new PressureUnit(pressure, unit).getValue("bara");
    this.targetEndpointPressureUnit = unit;
  }

  /**
   * Configure convergence tolerance when iterating manifold pressure to reach the target endpoint
   * pressure.
   *
   * @param tolerance absolute pressure tolerance in bara
   */
  public void setIterationTolerance(double tolerance) {
    this.iterationTolerance = tolerance;
  }

  /**
   * Configure maximum number of iterations when matching target endpoint pressure.
   *
   * @param maxIterations iteration cap
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Stream representing combined arrival conditions at the platform inlet.
   *
   * @return arrival stream
   */
  public StreamInterface getArrivalStream() {
    ManifoldNode tail = getTailManifold();
    if (tail == null) {
      return null;
    }
    Mixer mixer = tail.getMixer();
    if (mixer == null) {
      return null;
    }
    if (mixer.getNumberOfInputStreams() == 0) {
      // Tail manifold has no input streams, cannot produce outlet stream
      return null;
    }
    return mixer.getOutletStream();
  }

  /**
   * Report the most recent pressure enforced at the terminal manifold while solving toward a target
   * endpoint pressure.
   *
   * @param unit requested unit
   * @return terminal manifold pressure, or {@code null} if the network has not been run
   */
  public Double getTerminalManifoldPressure(String unit) {
    if (lastTerminalManifoldPressure == null) {
      return null;
    }
    return new PressureUnit(lastTerminalManifoldPressure, "bara").getValue(unit);
  }

  @Override
  public void run(UUID id) {
    if (getBranches().isEmpty()) {
      return;
    }
    if (targetEndpointPressure != null) {
      iterateForEndpointPressure(id);
    } else {
      runManifolds(id, false, null);
      if (facilityPipeline != null) {
        facilityPipeline.setInletStream(getTailManifold().getMixer().getOutletStream());
        facilityPipeline.run(id);
      }
      enforceManifoldPressures(id);
    }
  }

  @Override
  public void runTransient(double dt, UUID id) {
    if (getBranches().isEmpty()) {
      return;
    }
    if (targetEndpointPressure != null) {
      iterateForEndpointPressure(id);
    } else {
      for (ManifoldNode manifold : manifolds) {
        for (Branch branch : manifold.getBranches()) {
          branch.runTransient(dt, id);
        }
        // Mixer has no dedicated transient API; recompute arrival conditions after transient steps.
        manifold.getMixer().run(id);
        if (manifold.getPipelineToNext() != null) {
          manifold.getPipelineToNext().setInletStream(manifold.getMixer().getOutletStream());
          manifold.getPipelineToNext().runTransient(dt, id);
        }
      }
      if (facilityPipeline != null) {
        facilityPipeline.setInletStream(getTailManifold().getMixer().getOutletStream());
        facilityPipeline.runTransient(dt, id);
      }
      enforceManifoldPressures(id);
    }
  }

  @Override
  public void setName(String name) {
    super.setName(name);
    if (!manifolds.isEmpty()) {
      manifolds.get(manifolds.size() - 1).getMixer().setName(name + " arrival mixer");
    }
  }

  @Override
  public String toJson() {
    if (getBranches().isEmpty()) {
      return null;
    }
    // Defer to the arrival mixer JSON for a concise snapshot of network outlet conditions.
    return getTailManifold().getMixer().toJson();
  }

  /**
   * Force a single pressure at each manifold outlet and optionally push that backpressure to wells
   * that solve for flow from outlet pressure.
   */
  private void enforceManifoldPressures(UUID id) {
    if (getBranches().isEmpty()) {
      return;
    }

    for (ManifoldNode manifold : manifolds) {
      double manifoldPressure = manifold.getMixer().getOutletStream().getPressure("bara");

      for (Branch branch : manifold.getBranches()) {
        branch.getPipeline().getOutletStream().setPressure(manifoldPressure, "bara");
        if (branch.getChoke() != null) {
          branch.getChoke().getOutletStream().setPressure(manifoldPressure, "bara");
        }
        if (propagateArrivalPressureToWells && !branch.getWell().isCalculatingOutletPressure()) {
          branch.getWell().setOutletPressure(manifoldPressure, "bara");
        }
      }

      for (PipeBeggsAndBrills inbound : manifold.getInboundPipelines()) {
        inbound.getOutletStream().setPressure(manifoldPressure, "bara");
      }

      // Recompute combined arrival with harmonized pressures.
      manifold.getMixer().run(id);
    }

    if (facilityPipeline != null) {
      facilityPipeline.run(id);
    }
  }

  /**
   * Iteratively adjust terminal manifold pressure so that the endpoint (arrival or facility outlet)
   * pressure matches the configured target.
   */
  private void iterateForEndpointPressure(UUID id) {
    double manifoldGuess = targetEndpointPressure;
    double achievedPressure = runWithEndpointPressure(manifoldGuess, id);

    for (int i = 0; i < maxIterations
        && Math.abs(achievedPressure - targetEndpointPressure) > iterationTolerance; i++) {
      double error = achievedPressure - targetEndpointPressure;
      manifoldGuess = Math.max(0.1, manifoldGuess - error);
      achievedPressure = runWithEndpointPressure(manifoldGuess, id);
    }

    // Harmonize pressures after the final iteration to keep branch and manifold streams aligned
    // with the terminal pressure used to hit the endpoint target.
    enforceManifoldPressures(id);
  }

  /**
   * Execute branch runs and facility pipeline while enforcing a specified terminal manifold
   * pressure.
   *
   * @param manifoldPressure pressure in bara at the terminal manifold
   * @param id calculation identifier
   * @return pressure achieved at the endpoint (arrival if no facility pipeline, otherwise facility
   *         pipeline outlet)
   */
  private double runWithEndpointPressure(double manifoldPressure, UUID id) {
    lastTerminalManifoldPressure = manifoldPressure;
    runManifolds(id, true, manifoldPressure);

    if (facilityPipeline != null) {
      facilityPipeline.setInletStream(getTailManifold().getMixer().getOutletStream());
      facilityPipeline.run(id);
      return facilityPipeline.getOutletStream().getPressure("bara");
    }

    return getTailManifold().getMixer().getOutletStream().getPressure("bara");
  }

  /**
   * Run through all manifolds and branches in upstream-to-downstream order.
   */
  private void runManifolds(UUID id, boolean forceEndpointPressure, Double endpointPressure) {
    for (int i = 0; i < manifolds.size(); i++) {
      ManifoldNode manifold = manifolds.get(i);
      boolean isTerminal = i == manifolds.size() - 1;
      runManifold(manifold, id, forceEndpointPressure && isTerminal, endpointPressure);
    }
  }

  private void runManifold(ManifoldNode manifold, UUID id, boolean overridePressure,
      Double forcedPressure) {
    // Step 1: Run all inbound pipelines and add their outlet streams to the mixer
    for (PipeBeggsAndBrills inbound : manifold.getInboundPipelines()) {
      // Wire inlet stream if it's a pipelineToNext from an upstream manifold
      if (inbound.getInletStream() == null) {
        // Find the upstream manifold that has this pipeline as its pipelineToNext
        for (ManifoldNode upstream : manifolds) {
          if (upstream.getPipelineToNext() == inbound) {
            inbound.setInletStream(upstream.getMixer().getOutletStream());
            break;
          }
        }
      }
      inbound.run(id);

      // Add outlet stream to mixer if not already added
      StreamInterface inboundStream = inbound.getOutletStream();
      if (inboundStream != null) {
        boolean alreadyAdded = false;
        for (int i = 0; i < manifold.getMixer().getNumberOfInputStreams(); i++) {
          if (manifold.getMixer().getStream(i) == inboundStream) {
            alreadyAdded = true;
            break;
          }
        }
        if (!alreadyAdded) {
          manifold.getMixer().addStream(inboundStream);
        }
      }
    }

    // Step 2: Run all branches
    for (Branch branch : manifold.getBranches()) {
      if (forceFlowFromPressureSolve && branch.getWell().isCalculatingOutletPressure()) {
        branch.getWell().solveFlowFromOutletPressure(true);
      }
      if (overridePressure && forcedPressure != null) {
        if (branch.getChoke() != null) {
          branch.getChoke().setOutletPressure(forcedPressure, "bara");
        }
        branch.getWell().setOutletPressure(forcedPressure, "bara");
      }
      branch.run(id);
    }

    // Step 3: Run mixer to combine all inbound and branch streams
    if (manifold.getMixer().getNumberOfInputStreams() > 0) {
      manifold.getMixer().run(id);
    }

    // Step 4: Propagate manifold pressure back to all outlets
    double manifoldPressure = manifold.getMixer().getOutletStream().getPressure("bara");
    if (overridePressure && forcedPressure != null) {
      manifoldPressure = forcedPressure;
      manifold.getMixer().getOutletStream().setPressure(manifoldPressure, "bara");
    }

    for (Branch branch : manifold.getBranches()) {
      branch.getPipeline().getOutletStream().setPressure(manifoldPressure, "bara");
      if (branch.getChoke() != null) {
        branch.getChoke().getOutletStream().setPressure(manifoldPressure, "bara");
      }
      if (propagateArrivalPressureToWells && !branch.getWell().isCalculatingOutletPressure()) {
        branch.getWell().setOutletPressure(manifoldPressure, "bara");
      }
    }

    for (PipeBeggsAndBrills inbound : manifold.getInboundPipelines()) {
      inbound.getOutletStream().setPressure(manifoldPressure, "bara");
    }

    // Step 5: Re-run mixer to ensure all pressures are consistent
    if (manifold.getMixer().getNumberOfInputStreams() > 0) {
      manifold.getMixer().run(id);
    }

    // Step 6: Run pipelineToNext if it exists
    if (manifold.getPipelineToNext() != null) {
      manifold.getPipelineToNext().setInletStream(manifold.getMixer().getOutletStream());
      manifold.getPipelineToNext().run(id);
    }
  }

  private ManifoldNode getTailManifold() {
    return manifolds.get(manifolds.size() - 1);
  }
}
