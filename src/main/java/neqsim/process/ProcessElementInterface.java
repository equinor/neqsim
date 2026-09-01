package neqsim.process;

import java.io.Serializable;
import neqsim.process.dynamics.DynamicCapability;
import neqsim.process.dynamics.DynamicCapabilityResolver;
import neqsim.util.NamedInterface;

/**
 * Marker interface that unifies all elements within a {@code ProcessSystem}: equipment
 * ({@link neqsim.process.equipment.ProcessEquipmentInterface}), measurement devices
 * ({@link neqsim.process.measurementdevice.MeasurementDeviceInterface}), and controllers
 * ({@link neqsim.process.controllerdevice.ControllerDeviceInterface}).
 *
 * <p>
 * This common super-type allows {@code ProcessSystem} to manage a single heterogeneous collection of all process
 * elements, simplifying topology queries, serialisation, and export to interchange formats such as DEXPI.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ProcessElementInterface extends NamedInterface, Serializable {
  /**
   * Returns the audited transient semantics of this process element.
   *
   * <p>
   * The capability is distinct from the current runtime steady-state/dynamic setting. In particular,
   * {@link DynamicCapability#ALGEBRAIC} means the element may be re-evaluated as an algebraic relation during a
   * transient study but does not expose audited stored physical state of its own. A custom class can override this
   * method once its dynamic state, equations, initialization, timestep constraints, and validation evidence have been
   * established.
   * </p>
   *
   * <p>
   * This method reports software capability only. It does not imply quantitative validation, standards conformance, or
   * suitability for accountable engineering or safety approval.
   * </p>
   *
   * @return dynamic capability; never null
   */
  default DynamicCapability getDynamicCapability() {
    return DynamicCapabilityResolver.resolve(this);
  }
}
