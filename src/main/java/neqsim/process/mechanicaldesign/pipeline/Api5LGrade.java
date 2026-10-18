package neqsim.process.mechanicaldesign.pipeline;

import java.util.Locale;

/**
 * Canonical API 5L grade names, including the equivalent ISO metric designations.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Api5LGrade {
  /** Prevent construction of this utility class. */
  private Api5LGrade() {
  }

  /**
   * Normalizes a grade spelling without substituting a different material.
   *
   * @param grade API 5L or ISO grade designation
   * @return trimmed uppercase grade, with known metric aliases mapped to API 5L names
   * @throws IllegalArgumentException if the grade is null or empty
   */
  public static String normalize(String grade) {
    if (grade == null || grade.trim().isEmpty()) {
      throw new IllegalArgumentException("Pipe material grade must not be empty");
    }
    String key = grade.trim().toUpperCase(Locale.ROOT);
    if (key.startsWith("GRADE ")) {
      key = key.substring(6).trim();
    }
    String[] metric = {"L175", "L210", "L245", "L290", "L320", "L360", "L390", "L415", "L450", "L485", "L555", "L625",
        "L690", "L830"};
    String[] imperial = {"A25", "A", "B", "X42", "X46", "X52", "X56", "X60", "X65", "X70", "X80", "X90", "X100",
        "X120"};
    for (int i = 0; i < metric.length; i++) {
      if (metric[i].equals(key)) {
        return imperial[i];
      }
    }
    return key;
  }
}
