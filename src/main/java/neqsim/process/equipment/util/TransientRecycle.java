package neqsim.process.equipment.util;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * One-pass recycle transport element for dynamic process simulations.
 *
 * <p>
 * This unit deliberately does not perform steady-state tear-stream convergence. When it is placed after the recycle
 * take-off and before the destination mixer, the sequential dynamic process execution provides one accepted-time-step
 * of transport delay around the loop. This avoids the non-physical within-step back substitution that can occur when a
 * steady-state {@link Recycle} is used in a transient network.
 */
public class TransientRecycle extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Whether material is currently transported through the recycle. */
  private boolean enabled = false;

  /**
   * Creates a transient recycle between two explicit streams.
   *
   * @param name equipment name
   * @param inletStream recycle take-off stream
   * @param outletStream delayed recycle stream connected to the destination mixer
   */
  public TransientRecycle(String name, StreamInterface inletStream, StreamInterface outletStream) {
    super(name);
    this.inStream = inletStream;
    this.outStream = outletStream;
  }

  /**
   * Enables or isolates recycle transport.
   *
   * @param enabled true to copy the inlet state to the outlet on the next execution
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns whether recycle transport is enabled.
   *
   * @return true when recycle material is transported
   */
  public boolean isEnabled() {
    return enabled;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null || outStream == null) {
      throw new IllegalStateException("Transient recycle streams must be connected before running " + getName());
    }
    SystemInterface transportedState = inStream.getThermoSystem().clone();
    if (!enabled) {
      transportedState.setTotalFlowRate(0.0, "kg/hr");
    }
    outStream.setThermoSystem(transportedState);
    outStream.run(id);
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
