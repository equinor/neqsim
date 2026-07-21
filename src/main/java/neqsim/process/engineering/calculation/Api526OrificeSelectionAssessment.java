package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot of an API 526 standard-orifice screening selection. */
public final class Api526OrificeSelectionAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String standardEdition;
  private final double requiredAreaM2;
  private final double requiredAreaIn2;
  private final String selectedOrifice;
  private final double selectedAreaM2;
  private final double selectedAreaIn2;
  private final double areaMarginFraction;
  private final boolean adequate;

  Api526OrificeSelectionAssessment(String standardEdition, double requiredAreaM2, double requiredAreaIn2,
      String selectedOrifice, double selectedAreaM2, double selectedAreaIn2, boolean adequate) {
    this.standardEdition = standardEdition;
    this.requiredAreaM2 = requiredAreaM2;
    this.requiredAreaIn2 = requiredAreaIn2;
    this.selectedOrifice = selectedOrifice;
    this.selectedAreaM2 = selectedAreaM2;
    this.selectedAreaIn2 = selectedAreaIn2;
    this.areaMarginFraction = selectedAreaIn2 / requiredAreaIn2 - 1.0;
    this.adequate = adequate;
  }

  /** @return explicit edition used for selection */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return required effective area in square metres */
  public double getRequiredAreaM2() {
    return requiredAreaM2;
  }

  /** @return required effective area in square inches */
  public double getRequiredAreaIn2() {
    return requiredAreaIn2;
  }

  /** @return selected standard orifice letter */
  public String getSelectedOrifice() {
    return selectedOrifice;
  }

  /** @return selected standard area in square metres */
  public double getSelectedAreaM2() {
    return selectedAreaM2;
  }

  /** @return selected standard area in square inches */
  public double getSelectedAreaIn2() {
    return selectedAreaIn2;
  }

  /** @return selected-area margin divided by required area; negative when inadequate */
  public double getAreaMarginFraction() {
    return areaMarginFraction;
  }

  /** @return whether the selected standard orifice meets the required area */
  public boolean isAdequate() {
    return adequate;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("requiredAreaM2", Double.valueOf(requiredAreaM2));
    result.put("requiredAreaIn2", Double.valueOf(requiredAreaIn2));
    result.put("selectedOrifice", selectedOrifice);
    result.put("selectedAreaM2", Double.valueOf(selectedAreaM2));
    result.put("selectedAreaIn2", Double.valueOf(selectedAreaIn2));
    result.put("areaMarginFraction", Double.valueOf(areaMarginFraction));
    result.put("adequate", Boolean.valueOf(adequate));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
