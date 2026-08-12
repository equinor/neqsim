package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thermodynamic crude blend plus component mass-closure diagnostics.
 */
public class CrudeBlendResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final CrudeAssay assay;
  private final double totalMassKg;
  private final Map<String, Double> componentMassKg;
  private final double massBalanceResidualKg;

  /**
   * Create a blend result.
   *
   * @param assay blended assay
   * @param totalMassKg mass
   * @param componentMassKg component masses
   * @param massBalanceResidualKg closure residual
   */
  public CrudeBlendResult(CrudeAssay assay, double totalMassKg, Map<String, Double> componentMassKg,
      double massBalanceResidualKg) {
    this.assay = assay;
    this.totalMassKg = totalMassKg;
    this.componentMassKg = new LinkedHashMap<String, Double>(componentMassKg);
    this.massBalanceResidualKg = massBalanceResidualKg;
  }

  /** @return blended assay */
  public CrudeAssay getAssay() {
    return assay;
  }

  /** @return total mass in kg */
  public double getTotalMassKg() {
    return totalMassKg;
  }

  /** @return immutable component masses */
  public Map<String, Double> getComponentMassKg() {
    return Collections.unmodifiableMap(componentMassKg);
  }

  /** @return closure residual in kg */
  public double getMassBalanceResidualKg() {
    return massBalanceResidualKg;
  }
}
