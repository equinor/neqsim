package neqsim.process.equipment.network;

import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.CouplingResult;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Process-system adapter for one live {@link FieldWellNetworkCoupler}.
 *
 * <p>
 * The adapter owns no additional graph or pressure-flow calculation. Its {@link #run(UUID)} method executes the bound
 * live wells against the canonical {@link LoopedPipeNetwork}, exposes the network's stable sink streams to downstream
 * separators and compressors, and reports the process unit unsolved unless the complete coupled result is converged and
 * finite. {@link neqsim.process.automation.ProcessAutomation} exposes the wrapped network's existing source, choke,
 * route-availability, regulator, compressor and pump addresses through this unit name.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class FieldWellNetworkProcessUnit extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Existing live well/network coupling calculation. */
  private final FieldWellNetworkCoupler coupler;

  /**
   * Create a process-system unit over one existing coupler.
   *
   * @param name process equipment name used by automation addresses
   * @param coupler configured live well/network coupler
   */
  public FieldWellNetworkProcessUnit(String name, FieldWellNetworkCoupler coupler) {
    super(requireName(name));
    if (coupler == null) {
      throw new IllegalArgumentException("Field well/network coupler cannot be null");
    }
    this.coupler = coupler;
  }

  /**
   * Get the wrapped coupling calculation.
   *
   * @return configured live well/network coupler
   */
  public FieldWellNetworkCoupler getCoupler() {
    return coupler;
  }

  /**
   * Get the canonical field topology.
   *
   * @return topology used by the wrapped coupler
   */
  public FieldNetworkTopology getTopology() {
    return coupler.getTopology();
  }

  /**
   * Get the sole hydraulic network.
   *
   * @return canonical hydraulic graph used by the wrapped coupler
   */
  public LoopedPipeNetwork getHydraulicNetwork() {
    return getTopology().getHydraulicNetwork();
  }

  /**
   * Get the most recent coupled solve evidence.
   *
   * @return immutable result, or {@code null} before the first run
   */
  public CouplingResult getLastCouplingResult() {
    return coupler.getLastResult();
  }

  /** {@inheritDoc} */
  @Override
  public ProcessEquipmentInterface copy() {
    return new FieldWellNetworkProcessUnit(getName(), coupler.copy());
  }

  /**
   * Get one stable solved sink stream for downstream processing.
   *
   * @param sinkNodeName canonical sink-node identity
   * @return sink stream, or {@code null} before the network has produced it
   */
  public StreamInterface getOutletStream(String sinkNodeName) {
    return getHydraulicNetwork().getOutletStream(sinkNodeName);
  }

  /**
   * Get the first solved sink stream.
   *
   * @return first sink stream, or {@code null} before the network has produced one
   */
  public StreamInterface getOutletStream() {
    return getHydraulicNetwork().getOutletStream();
  }

  /**
   * Get all stable solved sink streams.
   *
   * @return immutable sink-stream list
   */
  @Override
  public List<StreamInterface> getOutletStreams() {
    return getHydraulicNetwork().getOutletStreams();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    CouplingResult result = coupler.run(id);
    isSolved = isCompleteFiniteResult(result);
    setCalculationIdentifier(id);
  }

  /**
   * Check the complete coupled result before allowing the surrounding process model to converge.
   *
   * @param result candidate coupling evidence
   * @return true only for converged finite network and per-well evidence
   */
  private boolean isCompleteFiniteResult(CouplingResult result) {
    if (result == null || !result.isConverged() || !Double.isFinite(result.getMaximumRateResidualKgS())
        || !Double.isFinite(result.getMaximumPressureResidualBar()) || !Double.isFinite(result.getNetworkResidualPa())
        || !Double.isFinite(result.getMassBalanceResidualKgS())) {
      return false;
    }
    for (WellResult well : result.getWellResults()) {
      if (!Double.isFinite(well.getNodePressureBara()) || !Double.isFinite(well.getTargetRateKgS())
          || !Double.isFinite(well.getAppliedRateKgS()) || !Double.isFinite(well.getRateResidualKgS())
          || !Double.isFinite(well.getInnerPressureResidualBar()) || !well.isInnerConverged()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validate and normalize a process-unit name.
   *
   * @param name requested name
   * @return trimmed non-empty name
   */
  private static String requireName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field well/network process-unit name cannot be empty");
    }
    return name.trim();
  }
}
