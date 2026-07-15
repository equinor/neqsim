package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Project line-list data used to complement simulation geometry with mechanical-design inputs.
 *
 * <p>
 * Hydraulic dimensions remain on the associated {@code PipeLineInterface}. This class records the information normally
 * supplied by a controlled line list: nominal dimensions, material, corrosion allowance, design conditions, insulation
 * and layout allowances. Calculated results remain review-required until checked against the project piping class,
 * stress analysis and fabrication specification.
 * </p>
 */
public final class LineDesignInput implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String lineTag;
  private final String equipmentTag;
  private String nominalPipeSize = "";
  private String schedule = "";
  private String materialGrade = "";
  private String pipingClass = "";
  private String insulationType = "";
  private double outerDiameterM = Double.NaN;
  private double nominalWallThicknessM = Double.NaN;
  private double corrosionAllowanceM = Double.NaN;
  private double designPressureBara = Double.NaN;
  private double designTemperatureC = Double.NaN;
  private double installationTemperatureC = 20.0;
  private double equivalentFittingsLengthM = 0.0;
  private double proposedSupportSpacingM = Double.NaN;
  private final List<String> evidenceReferences = new ArrayList<String>();

  /**
   * Creates a controlled line-list row.
   *
   * @param lineTag line number or tag
   * @param equipmentTag matching NeqSim pipeline equipment tag
   */
  public LineDesignInput(String lineTag, String equipmentTag) {
    this.lineTag = requireText(lineTag, "lineTag");
    this.equipmentTag = requireText(equipmentTag, "equipmentTag");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public LineDesignInput setNominalPipeSize(String value) {
    nominalPipeSize = requireText(value, "nominalPipeSize");
    return this;
  }

  public LineDesignInput setSchedule(String value) {
    schedule = requireText(value, "schedule");
    return this;
  }

  public LineDesignInput setMaterialGrade(String value) {
    materialGrade = requireText(value, "materialGrade");
    return this;
  }

  public LineDesignInput setPipingClass(String value) {
    pipingClass = requireText(value, "pipingClass");
    return this;
  }

  public LineDesignInput setInsulationType(String value) {
    insulationType = requireText(value, "insulationType");
    return this;
  }

  public LineDesignInput setOuterDiameter(double value, String unit) {
    outerDiameterM = lengthToMetres(value, unit);
    return this;
  }

  public LineDesignInput setNominalWallThickness(double value, String unit) {
    nominalWallThicknessM = lengthToMetres(value, unit);
    return this;
  }

  public LineDesignInput setCorrosionAllowance(double value, String unit) {
    corrosionAllowanceM = lengthToMetres(value, unit);
    return this;
  }

  public LineDesignInput setDesignPressureBara(double value) {
    designPressureBara = requirePositive(value, "designPressureBara");
    return this;
  }

  public LineDesignInput setDesignTemperatureC(double value) {
    designTemperatureC = value;
    return this;
  }

  public LineDesignInput setInstallationTemperatureC(double value) {
    installationTemperatureC = value;
    return this;
  }

  public LineDesignInput setEquivalentFittingsLengthM(double value) {
    equivalentFittingsLengthM = requireNonNegative(value, "equivalentFittingsLengthM");
    return this;
  }

  public LineDesignInput setProposedSupportSpacingM(double value) {
    proposedSupportSpacingM = requirePositive(value, "proposedSupportSpacingM");
    return this;
  }

  public LineDesignInput addEvidenceReference(String value) {
    if (value != null && !value.trim().isEmpty() && !evidenceReferences.contains(value.trim())) {
      evidenceReferences.add(value.trim());
    }
    return this;
  }

  private static double lengthToMetres(double value, String unit) {
    requirePositive(value, "length");
    String normalized = requireText(unit, "unit").toLowerCase();
    if ("m".equals(normalized)) {
      return value;
    }
    if ("mm".equals(normalized)) {
      return value / 1000.0;
    }
    if ("in".equals(normalized) || "inch".equals(normalized)) {
      return value * 0.0254;
    }
    throw new IllegalArgumentException("unsupported length unit " + unit);
  }

  private static double requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static double requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
    return value;
  }

  /** @return missing controlled line-list fields needed for mechanical screening */
  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (nominalPipeSize.isEmpty()) {
      missing.add("nominalPipeSize");
    }
    if (schedule.isEmpty()) {
      missing.add("schedule");
    }
    if (materialGrade.isEmpty()) {
      missing.add("materialGrade");
    }
    if (pipingClass.isEmpty()) {
      missing.add("pipingClass");
    }
    if (!Double.isFinite(outerDiameterM)) {
      missing.add("outerDiameter");
    }
    if (!Double.isFinite(nominalWallThicknessM)) {
      missing.add("nominalWallThickness");
    }
    if (!Double.isFinite(corrosionAllowanceM)) {
      missing.add("corrosionAllowance");
    }
    if (!Double.isFinite(designPressureBara)) {
      missing.add("designPressure");
    }
    if (!Double.isFinite(designTemperatureC)) {
      missing.add("designTemperature");
    }
    if (evidenceReferences.isEmpty()) {
      missing.add("lineListEvidenceReference");
    }
    return missing;
  }

  public String getLineTag() {
    return lineTag;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public String getNominalPipeSize() {
    return nominalPipeSize;
  }

  public String getSchedule() {
    return schedule;
  }

  public String getMaterialGrade() {
    return materialGrade;
  }

  public String getPipingClass() {
    return pipingClass;
  }

  public String getInsulationType() {
    return insulationType;
  }

  public double getOuterDiameterM() {
    return outerDiameterM;
  }

  public double getNominalWallThicknessM() {
    return nominalWallThicknessM;
  }

  public double getCorrosionAllowanceM() {
    return corrosionAllowanceM;
  }

  public double getDesignPressureBara() {
    return designPressureBara;
  }

  public double getDesignTemperatureC() {
    return designTemperatureC;
  }

  public double getInstallationTemperatureC() {
    return installationTemperatureC;
  }

  public double getEquivalentFittingsLengthM() {
    return equivalentFittingsLengthM;
  }

  public double getProposedSupportSpacingM() {
    return proposedSupportSpacingM;
  }

  public List<String> getEvidenceReferences() {
    return Collections.unmodifiableList(evidenceReferences);
  }
}
