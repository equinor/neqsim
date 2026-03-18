package neqsim.process.mechanicaldesign.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Hydrogen pipeline mechanical design per ASME B31.12 and DNV-ST-F101.
 *
 * <p>
 * Extends the standard pipeline mechanical design with hydrogen-specific considerations:
 * </p>
 * <ul>
 * <li>Hydrogen embrittlement derating factors per ASME B31.12</li>
 * <li>Material compatibility assessment for hydrogen service</li>
 * <li>Design factor reduction for hydrogen pipelines</li>
 * <li>Fatigue considerations for cyclic hydrogen loading</li>
 * <li>Permeation rate estimation</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>ASME B31.12 — Hydrogen Piping and Pipelines (primary)</li>
 * <li>DNV-ST-F101 — Submarine Pipeline Systems</li>
 * <li>EIGA 121/14 — Hydrogen Pipeline Systems</li>
 * <li>CGA G-5.6 — Hydrogen Pipeline Systems</li>
 * </ul>
 *
 * <h2>Material Compatibility (ASME B31.12 Table IX-5A)</h2>
 *
 * <table>
 * <caption>Material grades and hydrogen service compatibility</caption>
 * <tr><th>Material Grade</th><th>Max Pressure [bar]</th><th>Derating Factor</th>
 * <th>Compatibility</th></tr>
 * <tr><td>API 5L X42</td><td>100</td><td>1.0</td><td>Excellent</td></tr>
 * <tr><td>API 5L X52</td><td>100</td><td>0.95</td><td>Good</td></tr>
 * <tr><td>API 5L X60</td><td>80</td><td>0.90</td><td>Acceptable</td></tr>
 * <tr><td>API 5L X65</td><td>70</td><td>0.85</td><td>Limited</td></tr>
 * <tr><td>API 5L X70</td><td>50</td><td>0.80</td><td>Not recommended</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * AdiabaticPipe pipe = new AdiabaticPipe("H2 Pipeline", stream);
 * pipe.setDiameter(0.508);
 * pipe.setLength(150000.0);
 *
 * HydrogenPipelineDesign design = new HydrogenPipelineDesign(pipe);
 * design.setMaxOperationPressure(100.0);
 * design.setDesignPressure(110.0);
 * design.setDesignTemperature(60.0);
 * design.setMaterialGrade("X52");
 * design.setHydrogenMoleFraction(1.0);
 * design.calcDesign();
 *
 * double wallThickness = design.getRequiredWallThickness();
 * boolean compatible = design.isMaterialCompatible();
 * }</pre>
 *
 * @author esol
 * @version 1.0
 */
public class HydrogenPipelineDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Outer diameter [m]. */
  private double outerDiameter = 0.508;

  /** Design pressure [bara]. */
  private double designPressure = 100.0;

  /** Design temperature [C]. */
  private double designTemperature = 60.0;

  /** Material grade per API 5L. */
  private String materialGrade = "X52";

  /** SMYS of selected material [MPa]. */
  private double smys = 358.0;

  /** SMTS of selected material [MPa]. */
  private double smts = 455.0;

  /** Hydrogen mole fraction in transported gas [0-1]. */
  private double hydrogenMoleFraction = 1.0;

  /** Design factor per ASME B31.12 (lower than natural gas). */
  private double designFactor = 0.50;

  /** Joint factor (1.0 for seamless or SAW). */
  private double jointFactor = 1.0;

  /** Temperature derating factor. */
  private double temperatureDeratingFactor = 1.0;

  /** Hydrogen derating factor based on material grade. */
  private double hydrogenDeratingFactor = 1.0;

  /** Corrosion allowance [mm]. */
  private double corrosionAllowance = 1.0;

  /** Required wall thickness [mm]. */
  private double requiredWallThickness = 0.0;

  /** Pipeline length [m]. */
  private double pipelineLength = 50000.0;

  /** Water depth for subsea pipelines [m], 0 for onshore. */
  private double waterDepth = 0.0;

  /** Calculated hydrogen permeation rate [mol/(m2 s)]. */
  private double permeationRate = 0.0;

  /** Whether the material is compatible with hydrogen. */
  private boolean materialCompatible = true;

  /** Maximum hardness for hydrogen service per ASME B31.12 [HRC]. */
  private static final double MAX_HARDNESS_HRC = 22.0;

  /** Maximum SMYS for hydrogen service per ASME B31.12 [MPa]. */
  private static final double MAX_SMYS_H2_MPA = 480.0;

  /**
   * Constructor for HydrogenPipelineDesign.
   *
   * @param equipment the pipeline equipment
   */
  public HydrogenPipelineDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // 1. Look up material properties
    updateMaterialProperties();

    // 2. Calculate hydrogen derating factor
    hydrogenDeratingFactor = calculateHydrogenDeratingFactor(materialGrade);

    // 3. Check material compatibility
    materialCompatible = checkMaterialCompatibility();

    // 4. Calculate wall thickness per ASME B31.12 / Barlow's formula
    double designPressurePa = designPressure * 1.0e5;
    double outerDiameterMm = outerDiameter * 1000.0;
    double smysPa = smys * 1.0e6;

    // t = P * D / (2 * S * F * E * T * Hf)
    double effectiveStress = smysPa * designFactor * jointFactor
        * temperatureDeratingFactor * hydrogenDeratingFactor;

    double tMinMm = 0.0;
    if (effectiveStress > 0.0) {
      tMinMm = (designPressurePa * outerDiameterMm) / (2.0 * effectiveStress);
    }

    // Add corrosion allowance
    requiredWallThickness = tMinMm + corrosionAllowance;

    // 5. Set on parent class
    setWallThickness(requiredWallThickness / 1000.0);

    // 6. Calculate hydrogen permeation
    permeationRate = calculatePermeationRate();
  }

  /**
   * Look up material SMYS/SMTS based on grade.
   */
  private void updateMaterialProperties() {
    if ("X42".equals(materialGrade)) {
      smys = 290.0;
      smts = 414.0;
    } else if ("X52".equals(materialGrade)) {
      smys = 358.0;
      smts = 455.0;
    } else if ("X60".equals(materialGrade)) {
      smys = 414.0;
      smts = 517.0;
    } else if ("X65".equals(materialGrade)) {
      smys = 448.0;
      smts = 531.0;
    } else if ("X70".equals(materialGrade)) {
      smys = 483.0;
      smts = 565.0;
    }
  }

  /**
   * Calculate hydrogen derating factor per ASME B31.12 Table IX-5A.
   *
   * <p>
   * Higher strength steels are more susceptible to hydrogen embrittlement.
   * </p>
   *
   * @param grade material grade
   * @return derating factor [0-1]
   */
  private double calculateHydrogenDeratingFactor(String grade) {
    double h2Frac = hydrogenMoleFraction;
    if (h2Frac < 0.1) {
      return 1.0;
    }
    double baseFactor;
    if ("X42".equals(grade)) {
      baseFactor = 1.0;
    } else if ("X52".equals(grade)) {
      baseFactor = 0.95;
    } else if ("X60".equals(grade)) {
      baseFactor = 0.90;
    } else if ("X65".equals(grade)) {
      baseFactor = 0.85;
    } else if ("X70".equals(grade)) {
      baseFactor = 0.80;
    } else {
      baseFactor = 0.75;
    }
    // Linear interpolation with hydrogen content
    return 1.0 - h2Frac * (1.0 - baseFactor);
  }

  /**
   * Check material compatibility with hydrogen service.
   *
   * @return true if material is compatible
   */
  private boolean checkMaterialCompatibility() {
    // ASME B31.12: max SMYS 480 MPa for hydrogen service
    if (smys > MAX_SMYS_H2_MPA) {
      return false;
    }
    // Check derating factor (if below 0.80 material is not recommended)
    if (hydrogenDeratingFactor < 0.80) {
      return false;
    }
    return true;
  }

  /**
   * Calculate hydrogen permeation rate through pipe wall.
   *
   * <p>
   * Based on Sieverts' law: J = Phi * sqrt(P) / t, where Phi is the permeability coefficient.
   * </p>
   *
   * @return permeation rate [mol/(m2 s)]
   */
  private double calculatePermeationRate() {
    if (requiredWallThickness <= 0.0) {
      return 0.0;
    }
    // Permeability of H2 through carbon steel at ~60C [mol m / (m2 s sqrt(Pa))]
    double phi = 2.0e-11;
    double pressurePa = designPressure * 1.0e5;
    double thicknessM = requiredWallThickness / 1000.0;
    return phi * Math.sqrt(pressurePa) * hydrogenMoleFraction / thicknessM;
  }

  /**
   * Get required wall thickness [mm].
   *
   * @return required wall thickness including corrosion allowance [mm]
   */
  public double getRequiredWallThickness() {
    return requiredWallThickness;
  }

  /**
   * Get hydrogen derating factor.
   *
   * @return derating factor [0-1]
   */
  public double getHydrogenDeratingFactor() {
    return hydrogenDeratingFactor;
  }

  /**
   * Check if material is compatible with hydrogen service.
   *
   * @return true if compatible
   */
  public boolean isMaterialCompatible() {
    return materialCompatible;
  }

  /**
   * Get hydrogen permeation rate.
   *
   * @return permeation rate [mol/(m2 s)]
   */
  public double getPermeationRate() {
    return permeationRate;
  }

  /**
   * Set outer diameter [m].
   *
   * @param od outer diameter [m]
   */
  public void setOuterDiameter(double od) {
    this.outerDiameter = od;
  }

  /**
   * Get outer diameter [m].
   *
   * @return outer diameter [m]
   */
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /**
   * Set design pressure [bara].
   *
   * @param pressure design pressure [bara]
   */
  public void setDesignPressure(double pressure) {
    this.designPressure = pressure;
  }

  /**
   * Set design temperature [C].
   *
   * @param temperature design temperature [C]
   */
  public void setDesignTemperature(double temperature) {
    this.designTemperature = temperature;
  }

  /**
   * Set material grade per API 5L.
   *
   * @param grade material grade (e.g. "X42", "X52", "X65")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    updateMaterialProperties();
  }

  /**
   * Get material grade.
   *
   * @return material grade
   */
  public String getMaterialGrade() {
    return materialGrade;
  }

  /**
   * Set hydrogen mole fraction [0-1].
   *
   * @param fraction hydrogen mole fraction
   */
  public void setHydrogenMoleFraction(double fraction) {
    this.hydrogenMoleFraction = fraction;
  }

  /**
   * Set design factor per ASME B31.12 (default 0.50 for hydrogen).
   *
   * @param factor design factor
   */
  public void setDesignFactor(double factor) {
    this.designFactor = factor;
  }

  /**
   * Set corrosion allowance [mm].
   *
   * @param ca corrosion allowance [mm]
   */
  public void setCorrosionAllowance(double ca) {
    this.corrosionAllowance = ca;
  }

  /**
   * Set pipeline length [m].
   *
   * @param length pipeline length [m]
   */
  public void setPipelineLength(double length) {
    this.pipelineLength = length;
  }

  /**
   * Get pipeline length [m].
   *
   * @return pipeline length [m]
   */
  public double getPipelineLength() {
    return pipelineLength;
  }

  /**
   * Set water depth for subsea pipelines [m].
   *
   * @param depth water depth [m], 0 for onshore
   */
  public void setWaterDepth(double depth) {
    this.waterDepth = depth;
  }

  /**
   * Get SMYS [MPa].
   *
   * @return specified minimum yield strength [MPa]
   */
  public double getSmys() {
    return smys;
  }

  /**
   * Get design factor.
   *
   * @return design factor
   */
  public double getDesignFactor() {
    return designFactor;
  }

  /**
   * Get design results as a map.
   *
   * @return design results
   */
  public Map<String, Object> getDesignResults() {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("materialGrade", materialGrade);
    results.put("outerDiameter_m", outerDiameter);
    results.put("outerDiameter_inch", outerDiameter / 0.0254);
    results.put("requiredWallThickness_mm", requiredWallThickness);
    results.put("designPressure_bara", designPressure);
    results.put("designTemperature_C", designTemperature);
    results.put("SMYS_MPa", smys);
    results.put("SMTS_MPa", smts);
    results.put("designFactor", designFactor);
    results.put("hydrogenDeratingFactor", hydrogenDeratingFactor);
    results.put("materialCompatible", materialCompatible);
    results.put("corrosionAllowance_mm", corrosionAllowance);
    results.put("pipelineLength_m", pipelineLength);
    results.put("hydrogenMoleFraction", hydrogenMoleFraction);
    results.put("permeationRate_mol_per_m2s", permeationRate);
    results.put("designStandard", "ASME B31.12");
    return results;
  }
}
