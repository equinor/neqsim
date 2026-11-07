package neqsim.process.logic.action;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.ShutdownProfile;
import neqsim.process.logic.LogicAction;

/**
 * Commands a native compressor shutdown and verifies actual shaft speed rather than command bookkeeping.
 *
 * <p>
 * The process runner must advance {@link Compressor#updateDynamicState(double)} once per physical step. The
 * compressor's configured shutdown profile and driver or rate-limit policy determine the speed response. This action
 * does not infer an inertia model, open a recycle valve, isolate a process boundary or establish restart permissives.
 * Those actions require separate actual targets in the cause-and-effect sequence.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class TripCompressorAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final Compressor compressor;
  private final ShutdownProfile.ShutdownType shutdownType;
  private final double stoppedSpeedRpm;
  private boolean commandAvailable = true;
  private boolean executed;

  /**
   * Creates an emergency-profile command with a 1 rpm observed stop threshold.
   *
   * @param compressor actual native compressor target
   */
  public TripCompressorAction(Compressor compressor) {
    this(compressor, ShutdownProfile.ShutdownType.EMERGENCY, 1.0);
  }

  /**
   * Creates a shutdown action with an explicit native policy and completion threshold.
   *
   * @param compressor actual native compressor target
   * @param shutdownType native shutdown profile type
   * @param stoppedSpeedRpm finite nonnegative observed speed threshold in rpm
   * @throws IllegalArgumentException if the threshold is not finite and nonnegative
   */
  public TripCompressorAction(Compressor compressor, ShutdownProfile.ShutdownType shutdownType,
      double stoppedSpeedRpm) {
    this.compressor = Objects.requireNonNull(compressor, "compressor");
    this.shutdownType = Objects.requireNonNull(shutdownType, "shutdownType");
    if (!Double.isFinite(stoppedSpeedRpm) || stoppedSpeedRpm < 0.0) {
      throw new IllegalArgumentException("Stopped speed threshold must be finite and nonnegative");
    }
    this.stoppedSpeedRpm = stoppedSpeedRpm;
  }

  /** {@inheritDoc} */
  @Override
  public void execute() {
    if (!executed && commandAvailable) {
      // emergencyShutdown() marks TRIPPED, whose native state updater does not integrate the shutdown profile.
      // stopCompressor(EMERGENCY) intentionally retains the native SHUTDOWN integration state.
      compressor.stopCompressor(shutdownType);
      executed = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isComplete() {
    return executed && Double.isFinite(compressor.getSpeed()) && compressor.getSpeed() <= stoppedSpeedRpm;
  }

  /** {@inheritDoc} */
  @Override
  public void reset() {
    executed = false;
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Trip compressor " + compressor.getName() + " using " + shutdownType;
  }

  /** {@inheritDoc} */
  @Override
  public String getTargetName() {
    return compressor.getName();
  }

  /** {@inheritDoc} */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return Collections.<ProcessEquipmentInterface>singletonList(compressor);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTargetEquipmentIdentityComplete() {
    return true;
  }

  /** @return actual native target, never a tag-resolved replacement */
  public Compressor getCompressor() {
    return compressor;
  }

  /** @return the configured native shutdown profile type */
  public ShutdownProfile.ShutdownType getShutdownType() {
    return shutdownType;
  }

  /** @return actual speed completion threshold in rpm */
  public double getStoppedSpeedRpm() {
    return stoppedSpeedRpm;
  }

  /** @return whether the simulated trip command path is available */
  public boolean isCommandAvailable() {
    return commandAvailable;
  }

  /**
   * Configures an explicit command-path failure for a simulation case.
   *
   * @param available true when the native shutdown command can be issued
   */
  public void setCommandAvailable(boolean available) {
    commandAvailable = available;
  }
}
