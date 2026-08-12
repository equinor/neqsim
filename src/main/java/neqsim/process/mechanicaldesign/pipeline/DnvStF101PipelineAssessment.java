package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable aggregate returned by the DNV-ST-F101 pipeline screening calculator. */
public final class DnvStF101PipelineAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String standardBasis;
  private final double characteristicWallThicknessM;
  private final double deratedSmysMPa;
  private final double deratedSmtsMPa;
  private final double burstResistanceMPa;
  private final double collapseResistanceMPa;
  private final double propagationResistanceMPa;
  private final double fatigueDamage;
  private final double installationStrainFraction;
  private final List<DnvStF101LimitStateCheck> checks;

  DnvStF101PipelineAssessment(String standardBasis, double characteristicWallThicknessM, double deratedSmysMPa,
      double deratedSmtsMPa, double burstResistanceMPa, double collapseResistanceMPa, double propagationResistanceMPa,
      double fatigueDamage, double installationStrainFraction, List<DnvStF101LimitStateCheck> checks) {
    this.standardBasis = standardBasis;
    this.characteristicWallThicknessM = characteristicWallThicknessM;
    this.deratedSmysMPa = deratedSmysMPa;
    this.deratedSmtsMPa = deratedSmtsMPa;
    this.burstResistanceMPa = burstResistanceMPa;
    this.collapseResistanceMPa = collapseResistanceMPa;
    this.propagationResistanceMPa = propagationResistanceMPa;
    this.fatigueDamage = fatigueDamage;
    this.installationStrainFraction = installationStrainFraction;
    this.checks = Collections.unmodifiableList(new ArrayList<DnvStF101LimitStateCheck>(checks));
  }

  /** @return explicit standard basis */
  public String getStandardBasis() {
    return standardBasis;
  }

  /** @return characteristic corroded wall thickness in metres */
  public double getCharacteristicWallThicknessM() {
    return characteristicWallThicknessM;
  }

  /** @return de-rated SMYS in MPa */
  public double getDeratedSmysMPa() {
    return deratedSmysMPa;
  }

  /** @return de-rated SMTS in MPa */
  public double getDeratedSmtsMPa() {
    return deratedSmtsMPa;
  }

  /** @return design burst resistance in MPa */
  public double getBurstResistanceMPa() {
    return burstResistanceMPa;
  }

  /** @return design collapse resistance in MPa */
  public double getCollapseResistanceMPa() {
    return collapseResistanceMPa;
  }

  /** @return design propagation-buckling resistance in MPa */
  public double getPropagationResistanceMPa() {
    return propagationResistanceMPa;
  }

  /** @return cumulative Miner damage before the design fatigue factor */
  public double getFatigueDamage() {
    return fatigueDamage;
  }

  /** @return accumulated screening installation strain */
  public double getInstallationStrainFraction() {
    return installationStrainFraction;
  }

  /** @return immutable ordered screening checks */
  public List<DnvStF101LimitStateCheck> getChecks() {
    return checks;
  }

  /** @return {@code true} only when every implemented screening check passes */
  public boolean areAllScreeningChecksPassing() {
    for (DnvStF101LimitStateCheck check : checks) {
      if (check.getStatus() != DnvStF101LimitStateCheck.Status.PASS) {
        return false;
      }
    }
    return true;
  }

  /** @return check with the largest utilization */
  public DnvStF101LimitStateCheck getGoverningCheck() {
    DnvStF101LimitStateCheck governing = null;
    for (DnvStF101LimitStateCheck check : checks) {
      if (governing == null || check.getUtilization() > governing.getUtilization()) {
        governing = check;
      }
    }
    return governing;
  }

  /** @return deterministic, review-gated result map */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardBasis", standardBasis);
    result.put("characteristicWallThicknessM", Double.valueOf(characteristicWallThicknessM));
    result.put("deratedSmysMPa", Double.valueOf(deratedSmysMPa));
    result.put("deratedSmtsMPa", Double.valueOf(deratedSmtsMPa));
    result.put("burstResistanceMPa", Double.valueOf(burstResistanceMPa));
    result.put("collapseResistanceMPa", Double.valueOf(collapseResistanceMPa));
    result.put("propagationResistanceMPa", Double.valueOf(propagationResistanceMPa));
    result.put("fatigueDamage", Double.valueOf(fatigueDamage));
    result.put("installationStrainFraction", Double.valueOf(installationStrainFraction));
    List<Map<String, Object>> checkMaps = new ArrayList<Map<String, Object>>();
    for (DnvStF101LimitStateCheck check : checks) {
      checkMaps.add(check.toMap());
    }
    result.put("checks", checkMaps);
    result.put("allScreeningChecksPassing", Boolean.valueOf(areAllScreeningChecksPassing()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
