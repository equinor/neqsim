package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.thermo.system.SystemInterface;

/**
 * Multi-compartment reservoir model for tracking pressure evolution across connected zones.
 *
 * <p>
 * Models multiple reservoir compartments connected by inter-zone transmissibilities. Each
 * compartment has its own fluid (SystemInterface), pore volume, and pressure. Injectors and
 * producers can be placed in specific compartments.
 * </p>
 *
 * <p>
 * The material balance equation for each compartment per timestep is:
 * </p>
 * <p>
 * V_i * ct_i * dP_i/dt = q_inj,i - q_prod,i + SUM_j(T_ij * (P_j - P_i))
 * </p>
 * <p>
 * where ct_i is total compressibility, T_ij is the transmissibility between compartments i and j.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * MultiCompartmentReservoir reservoir = new MultiCompartmentReservoir("Field");
 *
 * // Add zones
 * reservoir.addCompartment("Target Sand", targetFluid, 1.0e7, 250.0);
 * reservoir.addCompartment("Thief Zone", thiefFluid, 5.0e6, 230.0);
 * reservoir.addCompartment("Aquifer", aquiferFluid, 1.0e9, 260.0);
 *
 * // Set inter-zone transmissibilities
 * reservoir.setTransmissibility("Target Sand", "Thief Zone", 0.5);
 * reservoir.setTransmissibility("Target Sand", "Aquifer", 0.1);
 *
 * // Add wells
 * reservoir.addInjectionRate("INJ-1", "Target Sand", 5000.0); // Sm3/day
 * reservoir.addProductionRate("PROD-1", "Target Sand", 3000.0);
 *
 * // Time-step the reservoir
 * for (int step = 0; step &lt; 365; step++) {
 *   reservoir.runTimeStep(86400.0); // 1 day
 *   double p = reservoir.getCompartmentPressure("Target Sand", "bara");
 *   double flow = reservoir.getInterZoneFlowRate("Target Sand", "Thief Zone", "Sm3/day");
 * }
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiCompartmentReservoir extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(MultiCompartmentReservoir.class);

  /**
   * Represents a single reservoir compartment.
   */
  public static class Compartment implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Compartment name. */
    public String name;
    /** Fluid in this compartment. */
    public transient SystemInterface fluid;
    /** Pore volume (m³). */
    public double poreVolume;
    /** Current pressure (bara). */
    public double pressure;
    /** Initial pressure (bara). */
    public double initialPressure;
    /** Total compressibility (1/bar). */
    public double totalCompressibility;
    /** Net injection rate into compartment (Sm3/day). */
    public double netInjectionRate;
    /** Net production rate from compartment (Sm3/day). */
    public double netProductionRate;

    /**
     * Creates a reservoir compartment.
     *
     * @param name compartment name
     * @param fluid fluid system
     * @param poreVolume pore volume (m³)
     * @param pressure initial pressure (bara)
     */
    public Compartment(String name, SystemInterface fluid, double poreVolume, double pressure) {
      this.name = name;
      this.fluid = fluid;
      this.poreVolume = poreVolume;
      this.pressure = pressure;
      this.initialPressure = pressure;
      this.totalCompressibility = 1e-4; // Default: 1e-4 1/bar (typical oil reservoir)
    }
  }

  /**
   * Represents a transmissibility connection between two compartments.
   */
  public static class TransmissibilityConnection implements Serializable {
    private static final long serialVersionUID = 1L;

    /** First compartment name. */
    public String compartment1;
    /** Second compartment name. */
    public String compartment2;
    /** Transmissibility (Sm3/day/bar). */
    public double transmissibility;
    /** Current inter-zone flow rate (Sm3/day), positive = from comp1 to comp2. */
    public double currentFlowRate;

    /**
     * Creates a transmissibility connection.
     *
     * @param comp1 first compartment name
     * @param comp2 second compartment name
     * @param transmissibility transmissibility value (Sm3/day/bar)
     */
    public TransmissibilityConnection(String comp1, String comp2, double transmissibility) {
      this.compartment1 = comp1;
      this.compartment2 = comp2;
      this.transmissibility = transmissibility;
    }
  }

  private List<Compartment> compartments = new ArrayList<>();
  private List<TransmissibilityConnection> connections = new ArrayList<>();
  private Map<String, Integer> compartmentIndex = new HashMap<>();
  private double simulationTime = 0.0; // Cumulative time (seconds)

  /**
   * Creates a multi-compartment reservoir model.
   *
   * @param name reservoir name
   */
  public MultiCompartmentReservoir(String name) {
    super(name);
  }

  /**
   * Add a reservoir compartment.
   *
   * @param name compartment identifier
   * @param fluid NeqSim fluid system for this compartment
   * @param poreVolume pore volume (m³)
   * @param pressure initial pressure (bara)
   */
  public void addCompartment(String name, SystemInterface fluid, double poreVolume,
      double pressure) {
    compartmentIndex.put(name, compartments.size());
    compartments.add(new Compartment(name, fluid, poreVolume, pressure));
  }

  /**
   * Set total compressibility for a compartment.
   *
   * @param compartmentName compartment name
   * @param compressibility total compressibility (1/bar)
   */
  public void setCompressibility(String compartmentName, double compressibility) {
    Compartment comp = getCompartment(compartmentName);
    if (comp != null) {
      comp.totalCompressibility = compressibility;
    }
  }

  /**
   * Set transmissibility between two compartments.
   *
   * @param name1 first compartment name
   * @param name2 second compartment name
   * @param transmissibility inter-zone transmissibility (Sm3/day/bar)
   */
  public void setTransmissibility(String name1, String name2, double transmissibility) {
    // Check if connection already exists and update
    for (TransmissibilityConnection conn : connections) {
      if ((conn.compartment1.equals(name1) && conn.compartment2.equals(name2))
          || (conn.compartment1.equals(name2) && conn.compartment2.equals(name1))) {
        conn.transmissibility = transmissibility;
        return;
      }
    }
    connections.add(new TransmissibilityConnection(name1, name2, transmissibility));
  }

  /**
   * Add injection rate to a compartment.
   *
   * @param wellName injector well name (for tracking)
   * @param compartmentName compartment receiving injection
   * @param rateSm3day injection rate (Sm3/day)
   */
  public void addInjectionRate(String wellName, String compartmentName, double rateSm3day) {
    Compartment comp = getCompartment(compartmentName);
    if (comp != null) {
      comp.netInjectionRate += rateSm3day;
    }
  }

  /**
   * Add production rate from a compartment.
   *
   * @param wellName producer well name (for tracking)
   * @param compartmentName compartment being produced
   * @param rateSm3day production rate (Sm3/day)
   */
  public void addProductionRate(String wellName, String compartmentName, double rateSm3day) {
    Compartment comp = getCompartment(compartmentName);
    if (comp != null) {
      comp.netProductionRate += rateSm3day;
    }
  }

  /**
   * Set injection rate for a compartment (replaces any previous rate).
   *
   * @param compartmentName compartment name
   * @param rateSm3day injection rate (Sm3/day)
   */
  public void setInjectionRate(String compartmentName, double rateSm3day) {
    Compartment comp = getCompartment(compartmentName);
    if (comp != null) {
      comp.netInjectionRate = rateSm3day;
    }
  }

  /**
   * Set production rate for a compartment (replaces any previous rate).
   *
   * @param compartmentName compartment name
   * @param rateSm3day production rate (Sm3/day)
   */
  public void setProductionRate(String compartmentName, double rateSm3day) {
    Compartment comp = getCompartment(compartmentName);
    if (comp != null) {
      comp.netProductionRate = rateSm3day;
    }
  }

  /**
   * Advance the reservoir state by one timestep using explicit Euler integration.
   *
   * <p>
   * For each compartment the pressure update is: dP_i = dt / (V_i * ct_i) * (q_inj,i - q_prod,i +
   * SUM(T_ij * (P_j - P_i)))
   * </p>
   *
   * @param dtSeconds timestep size (seconds)
   */
  public void runTimeStep(double dtSeconds) {
    double dtDays = dtSeconds / 86400.0;
    int n = compartments.size();

    // Calculate inter-zone flows
    for (TransmissibilityConnection conn : connections) {
      Integer idx1 = compartmentIndex.get(conn.compartment1);
      Integer idx2 = compartmentIndex.get(conn.compartment2);
      if (idx1 != null && idx2 != null) {
        double p1 = compartments.get(idx1).pressure;
        double p2 = compartments.get(idx2).pressure;
        conn.currentFlowRate = conn.transmissibility * (p1 - p2); // Sm3/day
      }
    }

    // Compute pressure changes for each compartment
    double[] dpdt = new double[n];
    for (int i = 0; i < n; i++) {
      Compartment comp = compartments.get(i);
      double netFlow = comp.netInjectionRate - comp.netProductionRate; // Sm3/day

      // Add inter-zone flows
      for (TransmissibilityConnection conn : connections) {
        Integer idx1 = compartmentIndex.get(conn.compartment1);
        Integer idx2 = compartmentIndex.get(conn.compartment2);
        if (idx1 != null && idx2 != null) {
          if (idx1 == i) {
            // Flow leaving compartment1 into compartment2 (positive reduces pressure)
            netFlow -= conn.currentFlowRate;
          } else if (idx2 == i) {
            // Flow entering compartment2 from compartment1 (positive increases pressure)
            netFlow += conn.currentFlowRate;
          }
        }
      }

      // dP/dt = netFlow / (V * ct) [bar/day]
      if (comp.poreVolume > 0 && comp.totalCompressibility > 0) {
        dpdt[i] = netFlow / (comp.poreVolume * comp.totalCompressibility);
      }
    }

    // Update pressures (explicit Euler)
    for (int i = 0; i < n; i++) {
      compartments.get(i).pressure += dpdt[i] * dtDays;
    }

    simulationTime += dtSeconds;
  }

  /**
   * Get the current pressure of a compartment.
   *
   * @param name compartment name
   * @param unit pressure unit ("bara", "psia")
   * @return current pressure
   */
  public double getCompartmentPressure(String name, String unit) {
    Compartment comp = getCompartment(name);
    if (comp == null) {
      return 0.0;
    }
    if ("psia".equalsIgnoreCase(unit)) {
      return comp.pressure / 0.0689476;
    }
    return comp.pressure;
  }

  /**
   * Get the inter-zone flow rate between two compartments.
   *
   * @param name1 first compartment name
   * @param name2 second compartment name
   * @param unit flow rate unit ("Sm3/day")
   * @return flow rate (positive = from name1 to name2)
   */
  public double getInterZoneFlowRate(String name1, String name2, String unit) {
    for (TransmissibilityConnection conn : connections) {
      if (conn.compartment1.equals(name1) && conn.compartment2.equals(name2)) {
        return conn.currentFlowRate;
      } else if (conn.compartment1.equals(name2) && conn.compartment2.equals(name1)) {
        return -conn.currentFlowRate;
      }
    }
    return 0.0;
  }

  /**
   * Get the number of compartments.
   *
   * @return number of compartments
   */
  public int getNumberOfCompartments() {
    return compartments.size();
  }

  /**
   * Get the cumulative simulation time.
   *
   * @param unit time unit ("s", "days", "years")
   * @return cumulative simulation time
   */
  public double getSimulationTime(String unit) {
    if ("days".equalsIgnoreCase(unit)) {
      return simulationTime / 86400.0;
    } else if ("years".equalsIgnoreCase(unit)) {
      return simulationTime / (86400.0 * 365.25);
    }
    return simulationTime; // seconds
  }

  /**
   * Get the fluid in a compartment.
   *
   * @param name compartment name
   * @return SystemInterface for the compartment, or null
   */
  public SystemInterface getCompartmentFluid(String name) {
    Compartment comp = getCompartment(name);
    return comp != null ? comp.fluid : null;
  }

  /**
   * Get a compartment by name.
   *
   * @param name compartment name
   * @return Compartment or null if not found
   */
  private Compartment getCompartment(String name) {
    Integer idx = compartmentIndex.get(name);
    if (idx != null) {
      return compartments.get(idx);
    }
    logger.warn("Compartment not found: " + name);
    return null;
  }

  /**
   * Reset all compartment pressures to initial values.
   */
  public void reset() {
    for (Compartment comp : compartments) {
      comp.pressure = comp.initialPressure;
    }
    for (TransmissibilityConnection conn : connections) {
      conn.currentFlowRate = 0.0;
    }
    simulationTime = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Single steady-state run: just compute inter-zone flows at current pressures
    for (TransmissibilityConnection conn : connections) {
      Integer idx1 = compartmentIndex.get(conn.compartment1);
      Integer idx2 = compartmentIndex.get(conn.compartment2);
      if (idx1 != null && idx2 != null) {
        double p1 = compartments.get(idx1).pressure;
        double p2 = compartments.get(idx2).pressure;
        conn.currentFlowRate = conn.transmissibility * (p1 - p2);
      }
    }
  }
}
