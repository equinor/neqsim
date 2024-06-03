package neqsim.processSimulation.processEquipment.reservoir;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ReservoirCVDsim class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: ReservoirCVDsim.java 1234 2024-05-31 10:00:00Z esolbraa $
 */
public class ReservoirCVDsim extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;

  public ReservoirCVDsim(String name, SystemInterface reservoirFluid) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {

  }

}
