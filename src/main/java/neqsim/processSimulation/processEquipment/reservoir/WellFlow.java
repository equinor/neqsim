package neqsim.processSimulation.processEquipment.reservoir;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * WellFlow class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WellFlow extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for WellFlow.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public WellFlow(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface stream) {
    super.setInletStream(stream);
    StreamInterface outStream = stream.clone();
    outStream.setName("outStream");
    super.setOutletStream(outStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {



  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {

  }

}
