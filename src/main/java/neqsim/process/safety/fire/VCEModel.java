package neqsim.process.safety.fire;

import java.io.Serializable;

/**
 * TNO Multi-Energy method for vapor cloud explosion (VCE) overpressure prediction.
 *
 * <p>
 * Implements the workbook approach from TNO Yellow Book (Berg, 1985):
 * <ol>
 * <li>Compute combustion energy: E = m_fuel · ΔHc</li>
 * <li>Scale distance R̅ = R / (E/p₀)^(1/3)</li>
 * <li>Look up dimensionless overpressure ΔP̅ from the strength-class curve (1–10)</li>
 * <li>Recover overpressure ΔP = ΔP̅ · p₀</li>
 * </ol>
 *
 * <p>
 * Strength classes:
 * <ul>
 * <li>1 — Very weak (open, unconfined)</li>
 * <li>4 — Moderate confinement (outdoor process plant, average obstruction)</li>
 * <li>7 — Strong (highly confined / congested)</li>
 * <li>10 — Detonation</li>
 * </ul>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>Berg A.C. (1985) — The multi-energy method, J. Hazardous Materials 12, 1–10</li>
 * <li>TNO Green Book — CPR 14E, Methods for the calculation of physical effects</li>
 * <li>CCPS — Guidelines for Vapor Cloud Explosion, Pressure Vessel Burst, BLEVE</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class VCEModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double cloudFuelMassKg;
  private final double heatOfCombustionJPerKg;
  private final int strengthClass;
  private double ambientPressurePa = 101325.0;

  /**
   * Construct a TNO multi-energy VCE model.
   *
   * @param cloudFuelMassKg mass of fuel in the flammable portion of the cloud, kg
   * @param heatOfCombustionJPerKg lower heating value, J/kg
   * @param strengthClass TNO strength class 1..10 (4 typical for petrochemical plants)
   */
  public VCEModel(double cloudFuelMassKg, double heatOfCombustionJPerKg, int strengthClass) {
    if (cloudFuelMassKg < 0.0 || heatOfCombustionJPerKg <= 0.0) {
      throw new IllegalArgumentException("cloudFuelMass >= 0 and heatOfCombustion > 0 required");
    }
    if (strengthClass < 1 || strengthClass > 10) {
      throw new IllegalArgumentException("strengthClass must be in [1, 10]");
    }
    this.cloudFuelMassKg = cloudFuelMassKg;
    this.heatOfCombustionJPerKg = heatOfCombustionJPerKg;
    this.strengthClass = strengthClass;
  }

  /**
   * Set ambient atmospheric pressure (default 101325 Pa).
   *
   * @param pAmbPa ambient pressure in Pa
   * @return this model for chaining
   */
  public VCEModel setAmbientPressure(double pAmbPa) {
    this.ambientPressurePa = pAmbPa;
    return this;
  }

  /**
   * Total combustion energy E = m · ΔHc.
   *
   * @return combustion energy in J
   */
  public double combustionEnergyJ() {
    return cloudFuelMassKg * heatOfCombustionJPerKg;
  }

  /**
   * Sachs-scaled distance R̅ = R / (E/p₀)^(1/3).
   *
   * @param distanceM physical distance from cloud centre in m
   * @return dimensionless scaled distance
   */
  public double scaledDistance(double distanceM) {
    double E = combustionEnergyJ();
    double scale = Math.cbrt(E / ambientPressurePa);
    return distanceM / scale;
  }

  /**
   * Dimensionless overpressure ΔP̅ from strength-class curve.
   *
   * <p>
   * Implements an analytic fit to TNO Fig. 4.10 curves:
   * far-field (R̅ &gt; 1) ΔP̅ = ΔP̅_near · (1/R̅)^β with class-dependent constants.
   *
   * @param distanceM physical distance from cloud centre in m
   * @return dimensionless overpressure ΔP/p₀
   */
  public double dimensionlessOverpressure(double distanceM) {
    double Rbar = scaledDistance(distanceM);
    if (Rbar <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    // Near-field plateau values (TNO Fig 4.10 at R̅ → 1)
    double[] plateau = {0.01, 0.02, 0.05, 0.10, 0.20, 0.50, 1.00, 2.00, 5.00, 10.0};
    double dpNear = plateau[strengthClass - 1];
    if (Rbar <= 1.0) {
      return dpNear;
    }
    // Far field: power-law decay
    double beta = (strengthClass <= 5) ? 1.0 : 1.2;
    return dpNear * Math.pow(Rbar, -beta);
  }

  /**
   * Side-on overpressure ΔP at the receiver.
   *
   * @param distanceM physical distance from cloud centre in m
   * @return ΔP in Pa
   */
  public double overpressurePa(double distanceM) {
    return dimensionlessOverpressure(distanceM) * ambientPressurePa;
  }

  /**
   * Distance to a target side-on overpressure threshold (e.g. 6.9 kPa window damage,
   * 20.7 kPa structural damage, 100 kPa fatality).
   *
   * @param targetDpPa target side-on overpressure in Pa
   * @return distance in m
   */
  public double distanceToOverpressure(double targetDpPa) {
    if (targetDpPa <= 0.0) {
      throw new IllegalArgumentException("targetDp must be positive");
    }
    double lo = 0.1;
    double hi = 50000.0;
    if (overpressurePa(lo) < targetDpPa) {
      return lo;
    }
    for (int i = 0; i < 80; i++) {
      double mid = 0.5 * (lo + hi);
      if (overpressurePa(mid) > targetDpPa) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return 0.5 * (lo + hi);
  }
}
