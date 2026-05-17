package neqsim.process;

import java.io.Serializable;
import neqsim.util.NamedInterface;

/**
 * Marker interface that unifies all elements within a {@code ProcessSystem}: equipment
 * ({@link neqsim.process.equipment.ProcessEquipmentInterface}), measurement devices
 * ({@link neqsim.process.measurementdevice.MeasurementDeviceInterface}), and controllers
 * ({@link neqsim.process.controllerdevice.ControllerDeviceInterface}).
 *
 * <p>
 * This common super-type allows {@code ProcessSystem} to manage a single heterogeneous collection
 * of all process elements, simplifying topology queries, serialisation, and export to interchange
 * formats such as DEXPI.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ProcessElementInterface extends NamedInterface, Serializable {
}
