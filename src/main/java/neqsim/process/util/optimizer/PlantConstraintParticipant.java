package neqsim.process.util.optimizer;

import java.io.Serializable;

/**
 * Immutable member of an aggregated plant constraint.
 *
 * <p>
 * A participant either already uses the registered target unit and basis, or declares an explicit affine conversion
 * {@code target = source * factor + offset}. The registry never guesses conversions between unlike engineering units or
 * rate/reference bases.
 * </p>
 */
public final class PlantConstraintParticipant implements Serializable, Comparable<PlantConstraintParticipant> {
  private static final long serialVersionUID = 1L;

  private final String sourceId;
  private final String unit;
  private final String basis;
  private final boolean conversionExplicit;
  private final double conversionFactor;
  private final double conversionOffset;

  private PlantConstraintParticipant(String sourceId, String unit, String basis, boolean conversionExplicit,
      double conversionFactor, double conversionOffset) {
    this.sourceId = PlantConstraintScope.requireText(sourceId, "Participant source identity");
    this.unit = PlantConstraintScope.safeText(unit);
    this.basis = PlantConstraintScope.safeText(basis);
    this.conversionExplicit = conversionExplicit;
    if (!Double.isFinite(conversionFactor) || conversionFactor == 0.0 || !Double.isFinite(conversionOffset)) {
      throw new IllegalArgumentException("Participant conversion factor must be finite and non-zero and offset finite");
    }
    this.conversionFactor = conversionFactor;
    this.conversionOffset = conversionOffset;
  }

  /** Creates a participant already expressed in the registered target unit and basis. */
  public static PlantConstraintParticipant direct(String sourceId, String unit, String basis) {
    return new PlantConstraintParticipant(sourceId, unit, basis, false, 1.0, 0.0);
  }

  /** Creates a participant with an explicit conversion to the registered target unit and basis. */
  public static PlantConstraintParticipant converted(String sourceId, String sourceUnit, String sourceBasis,
      double conversionFactor, double conversionOffset) {
    return new PlantConstraintParticipant(sourceId, sourceUnit, sourceBasis, true, conversionFactor, conversionOffset);
  }

  /** Converts a finite source value to the definition's declared target unit and basis. */
  public double convertToTarget(double sourceValue) {
    if (!Double.isFinite(sourceValue)) {
      throw new IllegalArgumentException("Participant value must be finite");
    }
    double converted = sourceValue * conversionFactor + conversionOffset;
    if (!Double.isFinite(converted)) {
      throw new IllegalArgumentException("Participant conversion produced a non-finite value");
    }
    return converted;
  }

  /** @return stable caller-owned participant identity */
  public String getSourceId() {
    return sourceId;
  }

  /** @return source engineering unit */
  public String getUnit() {
    return unit;
  }

  /** @return source measurement or reference basis */
  public String getBasis() {
    return basis;
  }

  /** @return whether a source-to-target conversion was explicitly declared */
  public boolean isConversionExplicit() {
    return conversionExplicit;
  }

  /** @return multiplier in {@code target = source * factor + offset} */
  public double getConversionFactor() {
    return conversionFactor;
  }

  /** @return offset in {@code target = source * factor + offset} */
  public double getConversionOffset() {
    return conversionOffset;
  }

  String canonicalForm() {
    StringBuilder value = new StringBuilder();
    appendCanonical(value, sourceId);
    appendCanonical(value, unit);
    appendCanonical(value, basis);
    appendCanonical(value, Boolean.toString(conversionExplicit));
    appendCanonical(value, Double.toHexString(conversionFactor));
    appendCanonical(value, Double.toHexString(conversionOffset));
    return value.toString();
  }

  private static void appendCanonical(StringBuilder target, String value) {
    target.append(value.length()).append(':').append(value);
  }

  @Override
  public int compareTo(PlantConstraintParticipant other) {
    return sourceId.compareTo(other.sourceId);
  }
}
