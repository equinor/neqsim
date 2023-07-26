package neqsim.processSimulation.processEquipment.manifold;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * Manifold class.
 * </p>
 * A manifold is a process unit that can take in any number of streams and distribute them into a
 * number of output streams. In NeqSim it is created as a combination of a mixer and a splitter.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Manifold extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(Manifold.class);

  protected Mixer localmixer = new Mixer("local mixer");
  protected Splitter localsplitter = new Splitter("local splitter");

  double[] splitFactors = new double[1];

  /**
   * <p>
   * Constructor for Splitter.
   * </p>
   */
  public Manifold() {
    super("Manifold");
  }

  /**
   * <p>
   * Constructor for Splitter with name as input.
   * </p>
   */
  public Manifold(String name) {
    super(name);
  }

  public void addStream(StreamInterface newStream) {
    localmixer.addStream(newStream);
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param splitFact an array of {@link double} objects
   */
  public void setSplitFactors(double[] splitFact) {
    splitFactors = splitFact;
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.setSplitFactors(splitFactors);
  }

  /** {@inheritDoc} */
  public StreamInterface getSplitStream(int i) {
    return localsplitter.getSplitStream(i);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    localmixer.run(id);
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.run();
  }

}
