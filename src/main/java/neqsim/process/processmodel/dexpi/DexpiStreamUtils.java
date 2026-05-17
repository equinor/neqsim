package neqsim.process.processmodel.dexpi;

import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Shared utilities for resolving outlet streams from NeqSim process equipment.
 *
 * <p>
 * This class centralizes the logic for extracting the primary outlet stream from any process
 * equipment instance. It is used by both {@link DexpiSimulationBuilder} and {@link DexpiXmlWriter}
 * to avoid code duplication.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DexpiStreamUtils {

  private DexpiStreamUtils() {}

  /**
   * Gets the primary (gas) outlet stream from a process equipment instance.
   *
   * <p>
   * For separators, this returns the gas outlet stream. For splitters, it returns the first split
   * stream. For streams, it returns the stream itself. For all other TwoPortEquipment (compressor,
   * pump, valve, heater, cooler, expander, heat exchanger), it returns the outlet stream directly.
   * </p>
   *
   * @param equipment the process equipment
   * @return the primary outlet stream, or null if not available
   */
  public static StreamInterface getGasOutletStream(ProcessEquipmentInterface equipment) {
    if (equipment instanceof ThreePhaseSeparator) {
      return ((ThreePhaseSeparator) equipment).getGasOutStream();
    }
    if (equipment instanceof Separator) {
      return ((Separator) equipment).getGasOutStream();
    }
    if (equipment instanceof Splitter) {
      return ((Splitter) equipment).getSplitStream(0);
    }
    if (equipment instanceof Stream) {
      return (StreamInterface) equipment;
    }
    if (equipment instanceof TwoPortEquipment) {
      return ((TwoPortEquipment) equipment).getOutletStream();
    }
    return null;
  }

  /**
   * Gets the liquid outlet stream from a separator. For a ThreePhaseSeparator, this returns the oil
   * outlet. For a standard Separator, this returns the liquid outlet.
   *
   * @param equipment the process equipment
   * @return the liquid outlet stream, or null if equipment is not a separator
   */
  public static StreamInterface getLiquidOutletStream(ProcessEquipmentInterface equipment) {
    if (equipment instanceof ThreePhaseSeparator) {
      return ((ThreePhaseSeparator) equipment).getOilOutStream();
    }
    if (equipment instanceof Separator) {
      return ((Separator) equipment).getLiquidOutStream();
    }
    return null;
  }

  /**
   * Gets the water outlet stream from a ThreePhaseSeparator.
   *
   * @param equipment the process equipment
   * @return the water outlet stream, or null if equipment is not a ThreePhaseSeparator
   */
  public static StreamInterface getWaterOutletStream(ProcessEquipmentInterface equipment) {
    if (equipment instanceof ThreePhaseSeparator) {
      return ((ThreePhaseSeparator) equipment).getWaterOutStream();
    }
    return null;
  }

  /**
   * Checks whether the given equipment is a multi-outlet type (separator or splitter). Uses the
   * {@code getOutletStreams()} API when available, falling back to instanceof checks.
   *
   * @param equipment the process equipment
   * @return true if the equipment has multiple outlet streams
   */
  public static boolean isMultiOutlet(ProcessEquipmentInterface equipment) {
    List<StreamInterface> outlets = equipment.getOutletStreams();
    if (!outlets.isEmpty()) {
      return outlets.size() > 1;
    }
    // Fallback for equipment that has not yet overridden getOutletStreams()
    return equipment instanceof Separator || equipment instanceof Splitter;
  }
}
