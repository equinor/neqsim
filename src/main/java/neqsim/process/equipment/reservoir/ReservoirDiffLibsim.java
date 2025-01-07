package neqsim.process.equipment.reservoir;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ReservoirDiffLibsim class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: ReservoirDiffLibsim.java 1234 2024-05-31 10:00:00Z esolbraa $
 */
public class ReservoirDiffLibsim extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ReservoirDiffLibsim.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param reservoirFluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ReservoirDiffLibsim(String name, SystemInterface reservoirFluid) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {}
}
