package neqsim.process.equipment.refrigeration;

import java.io.Serializable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Heater;

/**
 * A heat load served by one temperature level of a {@link RefrigerationSystem}.
 *
 * <p>
 * A load is read every time the refrigeration system runs, so a load bound to process equipment follows the flowsheet.
 * Use the static factories for the common cases: a fixed duty, a process cooler, or the condenser of a colder
 * refrigeration system in a cascade.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public interface RefrigerationLoad extends Serializable {
  /**
   * Gets the load name.
   *
   * @return name
   */
  String getName();

  /**
   * Gets the heat removed from the process.
   *
   * @return duty in W, positive when heat is removed from the process
   */
  double getDuty();

  /**
   * Gets the coldest process temperature the refrigerant has to reach.
   *
   * @return process outlet temperature in K, or NaN when it is not known
   */
  double getProcessOutletTemperature();

  /**
   * Gets the process equipment this load is read from.
   *
   * <p>
   * The refrigeration system reports it as a signal source so that graph-based flowsheet execution solves it first.
   * </p>
   *
   * @return source equipment, or null when the load does not depend on other equipment
   */
  default ProcessEquipmentInterface getSourceEquipment() {
    return null;
  }

  /**
   * Creates a load with a fixed duty and no process temperature.
   *
   * @param name load name
   * @param dutyW duty in W, must not be negative
   * @return fixed load
   */
  static RefrigerationLoad fixed(final String name, final double dutyW) {
    return fixed(name, dutyW, Double.NaN);
  }

  /**
   * Creates a load with a fixed duty and a known process outlet temperature.
   *
   * @param name load name
   * @param dutyW duty in W, must not be negative
   * @param processOutletTemperatureK coldest process temperature in K, or NaN when it is not known
   * @return fixed load
   */
  static RefrigerationLoad fixed(final String name, final double dutyW, final double processOutletTemperatureK) {
    if (dutyW < 0.0 || Double.isNaN(dutyW)) {
      throw new IllegalArgumentException("refrigeration duty must be non-negative, got " + dutyW);
    }
    return new RefrigerationLoad() {
      private static final long serialVersionUID = 1000L;

      @Override
      public String getName() {
        return name;
      }

      @Override
      public double getDuty() {
        return dutyW;
      }

      @Override
      public double getProcessOutletTemperature() {
        return processOutletTemperatureK;
      }
    };
  }

  /**
   * Binds a process cooler to a refrigeration level.
   *
   * <p>
   * The load is the heat the cooler removes from its process stream, and the process outlet temperature is the cooler
   * outlet temperature. A unit that heats its stream contributes zero duty.
   * </p>
   *
   * @param cooler process cooler, or any {@link Heater} that cools its stream
   * @return load that follows the cooler
   */
  static RefrigerationLoad of(final Heater cooler) {
    if (cooler == null) {
      throw new IllegalArgumentException("cooler must not be null");
    }
    return new RefrigerationLoad() {
      private static final long serialVersionUID = 1000L;

      @Override
      public String getName() {
        return cooler.getName();
      }

      @Override
      public double getDuty() {
        double duty = -cooler.getDuty();
        return duty > 0.0 ? duty : 0.0;
      }

      @Override
      public double getProcessOutletTemperature() {
        return cooler.getOutletStream() == null ? Double.NaN : cooler.getOutletStream().getTemperature();
      }

      @Override
      @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Graph ordering and utility accounting require the same live cooler registered with the flowsheet")
      public ProcessEquipmentInterface getSourceEquipment() {
        return cooler;
      }
    };
  }

  /**
   * Binds the condenser of a colder refrigeration system to a level of a warmer one, forming a cascade.
   *
   * <p>
   * The load is the heat rejected by the colder system and the process outlet temperature is its condensing
   * temperature. Add the colder system to the flowsheet before the warmer one.
   * </p>
   *
   * @param colderSystem refrigeration system whose condenser is cooled
   * @return load that follows the colder system's condenser
   */
  static RefrigerationLoad cascadeCondenser(final RefrigerationSystem colderSystem) {
    if (colderSystem == null) {
      throw new IllegalArgumentException("colder refrigeration system must not be null");
    }
    return new RefrigerationLoad() {
      private static final long serialVersionUID = 1000L;

      @Override
      public String getName() {
        return colderSystem.getName() + " condenser";
      }

      @Override
      public double getDuty() {
        return colderSystem.getCondenserDuty();
      }

      @Override
      public double getProcessOutletTemperature() {
        return colderSystem.getCondensingTemperature();
      }

      @Override
      @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "A cascade reads the live condenser of the exact upstream refrigeration system")
      public ProcessEquipmentInterface getSourceEquipment() {
        return colderSystem;
      }
    };
  }
}
