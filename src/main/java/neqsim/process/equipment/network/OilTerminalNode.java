package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Oil terminal containing named tanks/caverns and route availability.
 */
public class OilTerminalNode implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final Map<String, OilTerminalTank> tanks = new LinkedHashMap<String, OilTerminalTank>();
  private final Map<String, Boolean> routeAvailability = new LinkedHashMap<String, Boolean>();

  /**
   * Create a terminal.
   *
   * @param name terminal name
   */
  public OilTerminalNode(String name) {
    this.name = name;
  }

  /** @return terminal name */
  public String getName() {
    return name;
  }

  /**
   * Add a tank or cavern.
   *
   * @param tank tank
   */
  public void addTank(OilTerminalTank tank) {
    tanks.put(tank.getName(), tank);
  }

  /**
   * Get a tank.
   *
   * @param tankName tank name
   * @return tank
   */
  public OilTerminalTank getTank(String tankName) {
    OilTerminalTank tank = tanks.get(tankName);
    if (tank == null) {
      throw new IllegalArgumentException("Unknown terminal tank: " + tankName);
    }
    return tank;
  }

  /** @return immutable tanks */
  public Map<String, OilTerminalTank> getTanks() {
    return Collections.unmodifiableMap(tanks);
  }

  /**
   * Set route availability.
   *
   * @param route route
   * @param available availability
   */
  public void setRouteAvailable(String route, boolean available) {
    routeAvailability.put(route, available);
  }

  /**
   * Check route availability.
   *
   * @param route route
   * @return true when available or not explicitly disabled
   */
  public boolean isRouteAvailable(String route) {
    Boolean available = routeAvailability.get(route);
    return available == null || available;
  }

  /**
   * Create an operational copy.
   *
   * @return copied terminal
   */
  public OilTerminalNode copy() {
    OilTerminalNode copied = new OilTerminalNode(name);
    for (OilTerminalTank tank : tanks.values()) {
      copied.addTank(tank.copy());
    }
    copied.routeAvailability.putAll(routeAvailability);
    return copied;
  }
}
