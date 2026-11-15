package neqsim.process.safety.release;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Configures the two independent pipe inventories created by a full-bore rupture.
 *
 * <p>
 * The rupture plane divides an initially uniform, rigid, constant-area gas pipe into a left and a right inventory. Each
 * inventory is advanced by {@link RealGasPipeDecompression} from its remote closed end toward the common rupture plane.
 * The two resulting jets point in opposite axial directions and are therefore retained as separate
 * {@link CoupledReleaseSource} equipment and separate source-term frames. Their rates must not be collapsed into one
 * thermodynamic station.
 * </p>
 *
 * <p>
 * This topology is adiabatic and single-gas. It does not couple pressure across the open rupture plane, model pipe
 * elasticity, infer crack opening, or add non-equilibrium multiphase transport. The caller owns native
 * {@link ProcessSystem} or process-model execution and can sum the exposed branch inventories for whole-pipe mass and
 * energy accounting.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TwoSidedRealGasPipeRupture {
  private static final int MINIMUM_BRANCH_CELLS = 8;
  private static final int MAXIMUM_BRANCH_CELLS = 400;
  private final String name;
  private final double totalLengthM;
  private final double ruptureDistanceFromLeftM;
  private final Branch leftBranch;
  private final Branch rightBranch;

  /** Axial direction of a branch jet relative to the caller's left-to-right pipe axis. */
  public enum AxialDirection {
    /** Jet directed toward decreasing pipe-axis coordinate. */
    NEGATIVE_PIPE_AXIS,
    /** Jet directed toward increasing pipe-axis coordinate. */
    POSITIVE_PIPE_AXIS
  }

  /**
   * Creates an adiabatic two-sided real-gas rupture topology.
   *
   * @param name stable topology name used to derive equipment and source identities
   * @param initialGas initial uniform equilibrium gas and selected EOS
   * @param totalLengthM total pipe length in m
   * @param ruptureDistanceFromLeftM rupture distance from the left closed end in m
   * @param diameterM internal pipe diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure at the rupture plane in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param totalCells total finite-volume cells shared by the two branches, from 16 through 800
   * @param cfl Courant number in (0,0.7]
   * @throws IllegalArgumentException for an invalid name, rupture position or cell allocation
   */
  public TwoSidedRealGasPipeRupture(String name, SystemInterface initialGas, double totalLengthM,
      double ruptureDistanceFromLeftM, double diameterM, double dischargeCoefficient, double backPressurePa,
      double darcyFrictionFactor, int totalCells, double cfl) {
    Objects.requireNonNull(initialGas, "initialGas");
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Rupture topology name is required");
    }
    this.totalLengthM = ReleaseFlowRequest.positive(totalLengthM, "totalLengthM");
    this.ruptureDistanceFromLeftM = ReleaseFlowRequest.positive(ruptureDistanceFromLeftM, "ruptureDistanceFromLeftM");
    if (ruptureDistanceFromLeftM >= totalLengthM) {
      throw new IllegalArgumentException("Rupture position must be strictly inside the pipe");
    }
    if (totalCells < 2 * MINIMUM_BRANCH_CELLS || totalCells > 800) {
      throw new IllegalArgumentException("Two-sided rupture requires 16 through 800 total cells");
    }
    this.name = name;
    int leftCells = allocateLeftCells(totalCells, ruptureDistanceFromLeftM / totalLengthM);
    int rightCells = totalCells - leftCells;
    double rightLengthM = totalLengthM - ruptureDistanceFromLeftM;
    RealGasPipeDecompression left = new RealGasPipeDecompression(name + "-left-branch", initialGas,
        ruptureDistanceFromLeftM, diameterM, dischargeCoefficient, backPressurePa, darcyFrictionFactor, leftCells, cfl);
    RealGasPipeDecompression right = new RealGasPipeDecompression(name + "-right-branch", initialGas, rightLengthM,
        diameterM, dischargeCoefficient, backPressurePa, darcyFrictionFactor, rightCells, cfl);
    leftBranch = new Branch(left, "LEFT", AxialDirection.POSITIVE_PIPE_AXIS, totalLengthM, ruptureDistanceFromLeftM,
        ruptureDistanceFromLeftM);
    rightBranch = new Branch(right, "RIGHT", AxialDirection.NEGATIVE_PIPE_AXIS, totalLengthM, ruptureDistanceFromLeftM,
        rightLengthM);
  }

  /**
   * Allocates cells in proportion to branch length while retaining each solver's minimum.
   *
   * @param totalCells total number of cells
   * @param leftFraction fraction of total pipe length on the left branch
   * @return number of cells assigned to the left branch
   */
  private static int allocateLeftCells(int totalCells, double leftFraction) {
    int proportional = (int) Math.round(totalCells * leftFraction);
    int lowerBound = Math.max(MINIMUM_BRANCH_CELLS, totalCells - MAXIMUM_BRANCH_CELLS);
    int upperBound = Math.min(MAXIMUM_BRANCH_CELLS, totalCells - MINIMUM_BRANCH_CELLS);
    return Math.max(lowerBound, Math.min(upperBound, proportional));
  }

  /**
   * Adds both physical branch units to a process in deterministic left-then-right order.
   *
   * @param process process that will own native transient execution
   */
  public void addTo(ProcessSystem process) {
    Objects.requireNonNull(process, "process");
    process.add(leftBranch);
    process.add(rightBranch);
  }

  /**
   * Registers both branch frames in a session backed by a single {@link ProcessSystem}.
   *
   * @param session live source-term session
   */
  public void registerSources(SourceTermSession session) {
    Objects.requireNonNull(session, "session");
    session.addInventorySource(getLeftSourceId(), leftBranch.getName());
    session.addInventorySource(getRightSourceId(), rightBranch.getName());
  }

  /**
   * Registers both branch frames in a session backed by a process model.
   *
   * @param session live source-term session
   * @param areaName process-model area containing both branch units
   */
  public void registerSources(SourceTermSession session, String areaName) {
    Objects.requireNonNull(session, "session");
    session.addInventorySource(getLeftSourceId(), areaName, leftBranch.getName());
    session.addInventorySource(getRightSourceId(), areaName, rightBranch.getName());
  }

  /** @return left-branch process equipment */
  public Branch getLeftBranch() {
    return leftBranch;
  }

  /** @return right-branch process equipment */
  public Branch getRightBranch() {
    return rightBranch;
  }

  /** @return stable source identity for the left-branch jet */
  public String getLeftSourceId() {
    return name + "-left-jet";
  }

  /** @return stable source identity for the right-branch jet */
  public String getRightSourceId() {
    return name + "-right-jet";
  }

  /** @return total configured pipe length in m */
  public double getTotalLengthM() {
    return totalLengthM;
  }

  /** @return rupture distance from the left closed end in m */
  public double getRuptureDistanceFromLeftM() {
    return ruptureDistanceFromLeftM;
  }

  /** @return combined initial branch mass in kg */
  public double getInitialMassKg() {
    return leftBranch.segment.getInitialMassKg() + rightBranch.segment.getInitialMassKg();
  }

  /** @return combined current branch mass in kg */
  public double getRemainingMassKg() {
    return leftBranch.segment.getRemainingMassKg() + rightBranch.segment.getRemainingMassKg();
  }

  /** @return combined cumulative released mass in kg */
  public double getReleasedMassKg() {
    return leftBranch.segment.getReleasedMassKg() + rightBranch.segment.getReleasedMassKg();
  }

  /** @return combined initial branch total energy in J */
  public double getInitialEnergyJ() {
    return leftBranch.segment.getInitialEnergyJ() + rightBranch.segment.getInitialEnergyJ();
  }

  /** @return combined current branch total energy in J */
  public double getRemainingEnergyJ() {
    return leftBranch.segment.getRemainingEnergyJ() + rightBranch.segment.getRemainingEnergyJ();
  }

  /** @return combined cumulative released total energy in J */
  public double getReleasedEnergyJ() {
    return leftBranch.segment.getReleasedEnergyJ() + rightBranch.segment.getReleasedEnergyJ();
  }

  /** @return sum of the two current outward mass-flow rates in kg/s */
  public double getTotalMassFlowRateKgS() {
    return leftBranch.getReleaseResult().getMassFlowRateKgS() + rightBranch.getReleaseResult().getMassFlowRateKgS();
  }

  /**
   * Directional process-equipment adapter for one side of the rupture.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Branch extends ProcessEquipmentBaseClass implements CoupledReleaseSource {
    private static final long serialVersionUID = 1L;
    private final RealGasPipeDecompression segment;
    private final String branchId;
    private final AxialDirection axialDirection;
    private final double totalPipeLengthM;
    private final double ruptureDistanceFromLeftM;
    private final double branchLengthM;

    /**
     * Creates a process adapter around one conservative branch solver.
     *
     * @param segment branch finite-volume solver
     * @param branchId stable branch label
     * @param axialDirection outward jet direction
     * @param totalPipeLengthM total pre-rupture pipe length in m
     * @param ruptureDistanceFromLeftM rupture position from the left end in m
     * @param branchLengthM branch inventory length in m
     */
    private Branch(RealGasPipeDecompression segment, String branchId, AxialDirection axialDirection,
        double totalPipeLengthM, double ruptureDistanceFromLeftM, double branchLengthM) {
      super(segment.getName());
      this.segment = segment;
      this.branchId = branchId;
      this.axialDirection = axialDirection;
      this.totalPipeLengthM = totalPipeLengthM;
      this.ruptureDistanceFromLeftM = ruptureDistanceFromLeftM;
      this.branchLengthM = branchLengthM;
      setCalculateSteadyState(false);
    }

    /**
     * Commits a stationary directional branch snapshot.
     *
     * @param id calculation identity
     */
    @Override
    public synchronized void run(UUID id) {
      segment.run(id);
      setTime(segment.getTime());
      setCalculationIdentifier(id);
    }

    /**
     * Advances this branch through native transient process execution.
     *
     * @param dt caller timestep in s
     * @param id calculation identity
     */
    @Override
    public synchronized void runTransient(double dt, UUID id) {
      segment.runTransient(dt, id);
      setTime(segment.getTime());
      setCalculationIdentifier(id);
    }

    /**
     * Opens or closes this branch at the rupture plane before the next calculation.
     *
     * @param enabled true to open the branch release boundary
     */
    public synchronized void setReleaseEnabled(boolean enabled) {
      segment.setReleaseEnabled(enabled);
      setCalculationIdentifier(null);
    }

    /** @return immutable request describing this branch release boundary */
    @Override
    public synchronized ReleaseFlowRequest getReleaseRequest() {
      return segment.getReleaseRequest();
    }

    /** @return model identity associated with this branch */
    @Override
    public ReleaseFlowModel getReleaseModel() {
      return segment.getReleaseModel();
    }

    /** @return current committed branch result */
    @Override
    public synchronized ReleaseFlowResult getReleaseResult() {
      return segment.getReleaseResult();
    }

    /** @return whether this branch rupture boundary is open */
    @Override
    public synchronized boolean isReleaseEnabled() {
      return segment.isReleaseEnabled();
    }

    /** @return stable two-sided rupture branch basis code */
    @Override
    public String getReleaseBasis() {
      return "COUPLED_TWO_SIDED_REAL_GAS_PIPE_RUPTURE_BRANCH";
    }

    /** @return immutable branch, direction, accounting and numerical provenance */
    @Override
    public synchronized Map<String, String> getReleaseProvenance() {
      Map<String, String> provenance = new LinkedHashMap<String, String>(segment.getReleaseProvenance());
      provenance.put("ruptureTopology", "TWO_SIDED_FULL_BORE_PIPE_RUPTURE");
      provenance.put("ruptureBranch", branchId);
      provenance.put("ruptureJetAxialDirection", axialDirection.name());
      provenance.put("preRuptureTotalPipeLengthM", Double.toString(totalPipeLengthM));
      provenance.put("ruptureDistanceFromLeftM", Double.toString(ruptureDistanceFromLeftM));
      provenance.put("ruptureBranchLengthM", Double.toString(branchLengthM));
      provenance.put("oppositeBranchRepresentedSeparately", "true");
      return Collections.unmodifiableMap(provenance);
    }

    /** @return outward jet direction relative to the caller's pipe axis */
    public AxialDirection getAxialDirection() {
      return axialDirection;
    }

    /** @return branch inventory length in m */
    public double getBranchLengthM() {
      return branchLengthM;
    }

    /** @return underlying conservative branch solver for detailed diagnostics */
    public RealGasPipeDecompression getSegment() {
      return segment;
    }
  }
}
