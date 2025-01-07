package neqsim.process.equipment.manifold;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;

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
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Manifold.class);

  protected Mixer localmixer = new Mixer("tmpName");
  protected Splitter localsplitter = new Splitter("tmpName");

  double[] splitFactors = new double[1];

  /**
   * <p>
   * Constructor for Manifold with name as input.
   * </p>
   *
   * @param name name of manifold
   */
  public Manifold(String name) {
    super(name);
    setName(name);
  }

  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream) {
    localmixer.addStream(newStream);
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param splitFact an array of type double
   */
  public void setSplitFactors(double[] splitFact) {
    splitFactors = splitFact;
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.setSplitFactors(splitFactors);
  }

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSplitStream(int i) {
    return localsplitter.getSplitStream(i);
  }

  /**
   * <p>
   * getMixedStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface}
   */
  public StreamInterface getMixedStream() {
    return localmixer.getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    localmixer.run(id);
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.run();
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    localmixer.setName(name + "local mixer");
    localsplitter.setName(name + "local splitter");
  }
}
