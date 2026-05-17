package neqsim.process.equipment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Abstract base class for process equipment with multiple inlet and/or outlet streams. Subclasses
 * such as heat exchanger networks, manifolds, or custom multi-port units can extend this instead of
 * wiring their own stream lists.
 *
 * <p>
 * Existing equipment (Separator, Mixer, Splitter) is <em>not</em> refactored to extend this class
 * to preserve backward compatibility. They already override {@code getInletStreams()} and
 * {@code getOutletStreams()} individually.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public abstract class MultiPortEquipment extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /** Inlet streams connected to this equipment. */
  protected final List<StreamInterface> inletStreams = new ArrayList<StreamInterface>();

  /** Outlet streams connected to this equipment. */
  protected final List<StreamInterface> outletStreams = new ArrayList<StreamInterface>();

  /**
   * Creates a multi-port equipment with the given name.
   *
   * @param name equipment name
   */
  public MultiPortEquipment(String name) {
    super(name);
  }

  /**
   * Adds an inlet stream.
   *
   * @param stream the inlet stream to add
   */
  public void addInletStream(StreamInterface stream) {
    inletStreams.add(stream);
  }

  /**
   * Adds an outlet stream.
   *
   * @param stream the outlet stream to add
   */
  public void addOutletStream(StreamInterface stream) {
    outletStreams.add(stream);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(inletStreams);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    return Collections.unmodifiableList(outletStreams);
  }
}
