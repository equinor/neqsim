package neqsim.process.mechanicaldesign.separator.pressuredrop;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated gas-side pressure-drop result for a separator or scrubber.
 *
 * <p>
 * Sums the individual {@link PressureDropContribution} items produced by
 * {@link SeparatorPressureDropCalculator} and exposes both the total in Pa and the total in bar
 * suitable for {@code Stream.setPressure}-style consumers.
 * </p>
 *
 * <p>
 * Currently modelled contributions:
 * </p>
 *
 * <ul>
 * <li>Sudden expansion at the inlet nozzle into the vessel (Borda–Carnot or user K).</li>
 * <li>Inlet device velocity-head loss (deflector / vane / cyclone / Schoepentoeter).</li>
 * <li>Mist eliminator (mesh pad) velocity-head loss.</li>
 * <li>Demisting cyclone bank loss (gas scrubbers only).</li>
 * <li>Sudden contraction into the gas outlet nozzle (sharp-edged default K = 0.5).</li>
 * </ul>
 *
 * <p>
 * Contributions that are absent (e.g. a vessel without a mesh pad) are not added to the breakdown
 * — the list is populated only with mechanisms that actually applied.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PressureDropBreakdown implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<PressureDropContribution> contributions;

  /**
   * Creates a new breakdown wrapping the given contribution list. The list is defensively copied
   * and made immutable.
   *
   * @param contributions ordered list of contributions; must not be {@code null}
   */
  public PressureDropBreakdown(List<PressureDropContribution> contributions) {
    this.contributions =
        Collections.unmodifiableList(new ArrayList<PressureDropContribution>(contributions));
  }

  /**
   * Gets the immutable list of contributions in the order they were applied.
   *
   * @return list of contributions
   */
  public List<PressureDropContribution> getContributions() {
    return contributions;
  }

  /**
   * Returns the contribution with the given name, or {@code null} if absent.
   *
   * @param name contribution identifier (e.g. {@code "mesh"})
   * @return contribution, or {@code null} when not present
   */
  public PressureDropContribution find(String name) {
    for (PressureDropContribution c : contributions) {
      if (c.getName().equals(name)) {
        return c;
      }
    }
    return null;
  }

  /**
   * Sum of all contributions in Pa.
   *
   * @return total pressure drop in Pa (always non-negative)
   */
  public double getTotalPa() {
    double sum = 0.0;
    for (PressureDropContribution c : contributions) {
      sum += c.getDpPa();
    }
    return sum;
  }

  /**
   * Sum of all contributions in bar.
   *
   * @return total pressure drop in bar (always non-negative)
   */
  public double getTotalBar() {
    return getTotalPa() * 1.0e-5;
  }

  /**
   * Convenience map view: {@code name -> dpPa}.
   *
   * @return map of contribution name to pressure drop in Pa
   */
  public Map<String, Double> toDpMap() {
    Map<String, Double> map = new LinkedHashMap<String, Double>();
    for (PressureDropContribution c : contributions) {
      map.put(c.getName(), c.getDpPa());
    }
    map.put("total", getTotalPa());
    return map;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("PressureDropBreakdown(total=");
    sb.append(String.format("%.1f Pa", getTotalPa()));
    sb.append(", n=").append(contributions.size()).append(")");
    return sb.toString();
  }
}
