package neqsim.process.materials;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remaining-life and inspection-interval assessment for one material review item.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class IntegrityLifeAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Verdict string for the life assessment. */
  private String verdict = "NOT_ASSESSED";

  /** Estimated remaining life in years, or -1 when not available. */
  private double remainingLifeYears = -1.0;

  /** Wall loss already consumed in millimetres. */
  private double consumedWallLossMm = -1.0;

  /** Estimated or predicted corrosion rate in millimetres per year. */
  private double corrosionRateMmPerYear = -1.0;

  /** Recommended inspection interval in years. */
  private int inspectionIntervalYears = 5;

  /** Notes supporting the assessment. */
  private final List<String> notes = new ArrayList<String>();

  /**
   * Creates a not-assessed life result.
   */
  public IntegrityLifeAssessment() {}

  /**
   * Creates a life assessment from wall-thickness and corrosion-rate data.
   *
   * @param nominalThicknessMm nominal or original wall thickness in millimetres
   * @param currentThicknessMm current measured wall thickness in millimetres
   * @param minimumRequiredThicknessMm minimum required wall thickness in millimetres
   * @param corrosionRateMmPerYear predicted corrosion rate in millimetres per year
   * @return populated life assessment
   */
  public static IntegrityLifeAssessment fromWallThickness(double nominalThicknessMm,
      double currentThicknessMm, double minimumRequiredThicknessMm, double corrosionRateMmPerYear) {
    IntegrityLifeAssessment assessment = new IntegrityLifeAssessment();
    assessment.consumedWallLossMm = Math.max(0.0, nominalThicknessMm - currentThicknessMm);
    assessment.corrosionRateMmPerYear = Math.max(0.0, corrosionRateMmPerYear);
    double availableWallMm = currentThicknessMm - minimumRequiredThicknessMm;
    if (assessment.corrosionRateMmPerYear > 1.0e-9) {
      assessment.remainingLifeYears =
          Math.max(0.0, availableWallMm / assessment.corrosionRateMmPerYear);
    } else if (availableWallMm > 0.0) {
      assessment.remainingLifeYears = 1000.0;
    } else {
      assessment.remainingLifeYears = 0.0;
    }
    assessment.assignVerdictAndInterval();
    return assessment;
  }

  /**
   * Sets the verdict.
   *
   * @param verdict verdict string
   * @return this assessment for fluent construction
   */
  public IntegrityLifeAssessment setVerdict(String verdict) {
    this.verdict = verdict == null ? "NOT_ASSESSED" : verdict;
    return this;
  }

  /**
   * Gets the verdict.
   *
   * @return verdict string
   */
  public String getVerdict() {
    return verdict;
  }

  /**
   * Gets the remaining life.
   *
   * @return remaining life in years, or -1 when not assessed
   */
  public double getRemainingLifeYears() {
    return remainingLifeYears;
  }

  /**
   * Gets the inspection interval.
   *
   * @return inspection interval in years
   */
  public int getInspectionIntervalYears() {
    return inspectionIntervalYears;
  }

  /**
   * Adds a note.
   *
   * @param note note text
   * @return this assessment for fluent construction
   */
  public IntegrityLifeAssessment addNote(String note) {
    if (note != null && !note.trim().isEmpty()) {
      notes.add(note);
    }
    return this;
  }

  /**
   * Converts this assessment to a JSON-ready map.
   *
   * @return map representation of the assessment
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("verdict", verdict);
    map.put("remainingLife_years", remainingLifeYears);
    map.put("consumedWallLoss_mm", consumedWallLossMm);
    map.put("corrosionRate_mm_per_year", corrosionRateMmPerYear);
    map.put("inspectionInterval_years", inspectionIntervalYears);
    map.put("notes", new ArrayList<String>(notes));
    return map;
  }

  /**
   * Assigns verdict and inspection interval from the remaining life result.
   */
  private void assignVerdictAndInterval() {
    if (remainingLifeYears <= 0.0) {
      verdict = "FAIL";
      inspectionIntervalYears = 1;
      notes.add("Current wall thickness is at or below the minimum required thickness.");
    } else if (remainingLifeYears < 5.0) {
      verdict = "FAIL";
      inspectionIntervalYears = 1;
      notes.add("Predicted remaining life is below five years; prioritize engineering review.");
    } else if (remainingLifeYears < 10.0) {
      verdict = "PASS_WITH_WARNINGS";
      inspectionIntervalYears = 2;
      notes.add("Remaining life is limited; shorten inspection interval.");
    } else if (remainingLifeYears < 20.0) {
      verdict = "PASS_WITH_WARNINGS";
      inspectionIntervalYears = 3;
      notes.add("Remaining life is acceptable but should be tracked.");
    } else {
      verdict = "PASS";
      inspectionIntervalYears = 5;
      notes.add("Remaining life is above 20 years for the supplied assumptions.");
    }
  }
}
