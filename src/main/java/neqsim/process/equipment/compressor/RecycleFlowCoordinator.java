package neqsim.process.equipment.compressor;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * Reconciles a valve-calculated anti-surge recycle flow with a compressor discharge split.
 *
 * <p>
 * Dynamic process systems execute equipment sequentially, so the recycle valve can calculate a requested flow after its
 * discharge splitter has already run. This coordinator writes the accepted, bounded flow back to both branches and
 * updates the splitter specification for the next step. The current accepted state is therefore mass conserving while
 * retaining an explicit transport delay when used with {@link neqsim.process.equipment.util.TransientRecycle}.
 */
public class RecycleFlowCoordinator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Compressor discharge stream upstream of the split. */
  private final StreamInterface compressorDischarge;

  /** Two-outlet discharge splitter; outlet zero is main flow and outlet one is recycle. */
  private final Splitter dischargeSplitter;

  /** Valve used to calculate the pressure-driven recycle request. */
  private final ThrottlingValve recycleValve;

  /** Minimum positive branch specification used to keep the recycle valve initialized. */
  private double recycleSeedFlow = 1.0;

  /** Maximum fraction of compressor discharge that may be recycled. */
  private double maximumRecycleFraction = 0.85;

  /** Last accepted recycle flow in kg/hr. */
  private double lastRecycleFlow = 0.0;

  /** Last accepted main discharge flow in kg/hr. */
  private double lastMainFlow = 0.0;

  /**
   * Creates a two-branch anti-surge recycle flow coordinator.
   *
   * @param name equipment name
   * @param compressorDischarge compressor discharge upstream of the split
   * @param dischargeSplitter two-outlet splitter with main flow at index zero and recycle at index one
   * @param recycleValve pressure-driven valve in the recycle branch
   */
  public RecycleFlowCoordinator(String name, StreamInterface compressorDischarge, Splitter dischargeSplitter,
      ThrottlingValve recycleValve) {
    super(name);
    if (compressorDischarge == null || dischargeSplitter == null || recycleValve == null) {
      throw new IllegalArgumentException("Recycle flow coordinator connections cannot be null");
    }
    if (dischargeSplitter.getSplitNumber() != 2) {
      throw new IllegalArgumentException("Recycle flow coordinator requires a two-outlet splitter");
    }
    this.compressorDischarge = compressorDischarge;
    this.dischargeSplitter = dischargeSplitter;
    this.recycleValve = recycleValve;
  }

  /**
   * Sets the maximum permitted recycle fraction.
   *
   * @param fraction fraction in the inclusive range zero to one
   */
  public void setMaximumRecycleFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Maximum recycle fraction must be in [0, 1]");
    }
    maximumRecycleFraction = fraction;
  }

  /**
   * Sets the positive flow used to keep the recycle branch initialized.
   *
   * @param flowKgPerHour seed flow in kg/hr, non-negative
   */
  public void setRecycleSeedFlow(double flowKgPerHour) {
    if (!Double.isFinite(flowKgPerHour) || flowKgPerHour < 0.0) {
      throw new IllegalArgumentException("Recycle seed flow must be finite and non-negative");
    }
    recycleSeedFlow = flowKgPerHour;
  }

  /**
   * Returns the last accepted recycle flow.
   *
   * @return recycle flow in kg/hr
   */
  public double getLastRecycleFlow() {
    return lastRecycleFlow;
  }

  /**
   * Returns the last accepted main discharge flow.
   *
   * @return main discharge flow in kg/hr
   */
  public double getLastMainFlow() {
    return lastMainFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double totalFlow = Math.max(0.0, compressorDischarge.getFlowRate("kg/hr"));
    double requestedRecycle = Math.max(0.0, recycleValve.getOutletStream().getFlowRate("kg/hr"));
    lastRecycleFlow = Math.min(requestedRecycle, maximumRecycleFraction * totalFlow);
    lastMainFlow = Math.max(0.0, totalFlow - lastRecycleFlow);

    dischargeSplitter.getSplitStream(0).setFlowRate(lastMainFlow, "kg/hr");
    dischargeSplitter.getSplitStream(1).setFlowRate(lastRecycleFlow, "kg/hr");
    recycleValve.getInletStream().setFlowRate(lastRecycleFlow, "kg/hr");
    recycleValve.getOutletStream().setFlowRate(lastRecycleFlow, "kg/hr");
    dischargeSplitter.setFlowRates(new double[] { Splitter.REMAINDER, Math.max(lastRecycleFlow, recycleSeedFlow) },
        "kg/hr");
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    boolean alreadyEvaluatedForStep = id != null && id.equals(getCalculationIdentifier());
    run(id);
    if (!alreadyEvaluatedForStep) {
      increaseTime(dt);
    }
  }
}
