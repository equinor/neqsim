package neqsim.process.processmodel.dexpi;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Validates instrument tag identifiers against the ISA-5.1 (ANSI/ISA-5.1-2009)
 * identification-letter table.
 *
 * <p>
 * An ISA instrument tag is built from a letter group followed by a loop number, for example
 * {@code PT-101}, {@code FIC-204} or {@code LSHH-330}. Within the letter group:
 * </p>
 *
 * <ul>
 * <li>the <b>first letter</b> identifies the measured or initiating variable (P = pressure, T =
 * temperature, F = flow, L = level, A = analysis, etc.);</li>
 * <li>an optional <b>modifier letter</b> may follow the first letter (e.g. the D in PDT for
 * <i>differential</i> pressure);</li>
 * <li>the <b>succeeding letters</b> identify the readout/passive and output functions (I =
 * indicate, R = record, C = control, T = transmit, V = valve, S = switch, etc.).</li>
 * </ul>
 *
 * <p>
 * This validator checks that the first letter is a recognised measured-variable letter and that
 * every succeeding letter is a recognised function letter, and that a numeric loop number is
 * present. It is intentionally permissive about ordering, matching the screening level expected for
 * automatically generated P&amp;ID tags.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class IsaTagValidator {

  /** First (measured / initiating variable) letters per ISA-5.1. */
  private static final Map<Character, String> FIRST_LETTERS = new HashMap<>();
  /** Succeeding (readout / output / function) letters per ISA-5.1. */
  private static final Map<Character, String> SUCCEEDING_LETTERS = new HashMap<>();
  /** Modifier letters that may immediately follow the first letter. */
  private static final String MODIFIER_LETTERS = "DFKMQ";

  static {
    FIRST_LETTERS.put('A', "Analysis");
    FIRST_LETTERS.put('B', "Burner/Combustion");
    FIRST_LETTERS.put('C', "User's choice (conductivity)");
    FIRST_LETTERS.put('D', "User's choice (density)");
    FIRST_LETTERS.put('E', "Voltage");
    FIRST_LETTERS.put('F', "Flow rate");
    FIRST_LETTERS.put('G', "User's choice (gauging)");
    FIRST_LETTERS.put('H', "Hand (manual)");
    FIRST_LETTERS.put('I', "Current");
    FIRST_LETTERS.put('J', "Power");
    FIRST_LETTERS.put('K', "Time/schedule");
    FIRST_LETTERS.put('L', "Level");
    FIRST_LETTERS.put('M', "User's choice (moisture)");
    FIRST_LETTERS.put('N', "User's choice");
    FIRST_LETTERS.put('O', "User's choice");
    FIRST_LETTERS.put('P', "Pressure/vacuum");
    FIRST_LETTERS.put('Q', "Quantity");
    FIRST_LETTERS.put('R', "Radiation");
    FIRST_LETTERS.put('S', "Speed/frequency");
    FIRST_LETTERS.put('T', "Temperature");
    FIRST_LETTERS.put('U', "Multivariable");
    FIRST_LETTERS.put('V', "Vibration/mechanical");
    FIRST_LETTERS.put('W', "Weight/force");
    FIRST_LETTERS.put('X', "Unclassified");
    FIRST_LETTERS.put('Y', "Event/state");
    FIRST_LETTERS.put('Z', "Position/dimension");

    SUCCEEDING_LETTERS.put('A', "Alarm");
    SUCCEEDING_LETTERS.put('B', "User's choice");
    SUCCEEDING_LETTERS.put('C', "Control");
    SUCCEEDING_LETTERS.put('D', "Differential (modifier)");
    SUCCEEDING_LETTERS.put('E', "Sensor/primary element");
    SUCCEEDING_LETTERS.put('F', "Ratio (modifier)");
    SUCCEEDING_LETTERS.put('G', "Glass/gauge");
    SUCCEEDING_LETTERS.put('H', "High");
    SUCCEEDING_LETTERS.put('I', "Indicate");
    SUCCEEDING_LETTERS.put('K', "Control station");
    SUCCEEDING_LETTERS.put('L', "Low/light");
    SUCCEEDING_LETTERS.put('M', "Middle/intermediate");
    SUCCEEDING_LETTERS.put('N', "User's choice");
    SUCCEEDING_LETTERS.put('O', "Orifice/restriction");
    SUCCEEDING_LETTERS.put('P', "Point/test connection");
    SUCCEEDING_LETTERS.put('Q', "Integrate/totalize");
    SUCCEEDING_LETTERS.put('R', "Record");
    SUCCEEDING_LETTERS.put('S', "Switch");
    SUCCEEDING_LETTERS.put('T', "Transmit");
    SUCCEEDING_LETTERS.put('U', "Multifunction");
    SUCCEEDING_LETTERS.put('V', "Valve/damper");
    SUCCEEDING_LETTERS.put('W', "Well/probe");
    SUCCEEDING_LETTERS.put('X', "Unclassified");
    SUCCEEDING_LETTERS.put('Y', "Relay/compute/convert");
    SUCCEEDING_LETTERS.put('Z', "Driver/actuator");
  }

  /** Private constructor — utility class. */
  private IsaTagValidator() {}

  /**
   * Outcome of validating an instrument tag against the ISA-5.1 letter table.
   */
  static final class ValidationResult {
    /** Whether the tag conforms to the ISA-5.1 letter scheme. */
    private final boolean valid;
    /** Human-readable description of the measured variable and functions, or the error. */
    private final String message;

    /**
     * Creates a validation result.
     *
     * @param valid whether the tag is valid
     * @param message description or error message
     */
    ValidationResult(boolean valid, String message) {
      this.valid = valid;
      this.message = message;
    }

    /**
     * Returns whether the tag is valid.
     *
     * @return true if valid
     */
    boolean isValid() {
      return valid;
    }

    /**
     * Returns the description or error message.
     *
     * @return the message
     */
    String getMessage() {
      return message;
    }
  }

  /**
   * Validates an instrument tag.
   *
   * @param tag the instrument tag, e.g. {@code "PT-101"} or {@code "FIC 204"} (may be null)
   * @return a {@link ValidationResult} indicating conformance and a description
   */
  static ValidationResult validate(String tag) {
    if (tag == null || tag.trim().isEmpty()) {
      return new ValidationResult(false, "Empty instrument tag");
    }
    String trimmed = tag.trim().toUpperCase(Locale.ROOT);
    // Split letters from the loop number; allow '-' or space between.
    StringBuilder letters = new StringBuilder();
    StringBuilder number = new StringBuilder();
    boolean inNumber = false;
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c == '-' || c == ' ' || c == '_') {
        continue;
      }
      if (Character.isLetter(c) && !inNumber) {
        letters.append(c);
      } else if (Character.isDigit(c)) {
        inNumber = true;
        number.append(c);
      } else if (Character.isLetter(c) && inNumber) {
        // Trailing suffix letter after the loop number (e.g. 101A) — accept.
        number.append(c);
      } else {
        return new ValidationResult(false, "Unexpected character '" + c + "' in tag");
      }
    }
    if (letters.length() == 0) {
      return new ValidationResult(false, "No identification letters in tag");
    }
    if (number.length() == 0) {
      return new ValidationResult(false, "No loop number in tag");
    }

    char first = letters.charAt(0);
    if (!FIRST_LETTERS.containsKey(first)) {
      return new ValidationResult(false,
          "First letter '" + first + "' is not a valid ISA-5.1 measured variable");
    }
    StringBuilder desc = new StringBuilder();
    desc.append(FIRST_LETTERS.get(first));
    int idx = 1;
    // Optional modifier directly after the first letter.
    if (letters.length() > 1 && MODIFIER_LETTERS.indexOf(letters.charAt(1)) >= 0) {
      desc.append(" (").append(SUCCEEDING_LETTERS.get(letters.charAt(1))).append(")");
      idx = 2;
    }
    for (int i = idx; i < letters.length(); i++) {
      char c = letters.charAt(i);
      if (!SUCCEEDING_LETTERS.containsKey(c)) {
        return new ValidationResult(false,
            "Succeeding letter '" + c + "' is not a valid ISA-5.1 function");
      }
      desc.append(" / ").append(SUCCEEDING_LETTERS.get(c));
    }
    return new ValidationResult(true, letters + "-" + number + ": " + desc.toString());
  }

  /**
   * Convenience predicate returning only whether a tag is valid.
   *
   * @param tag the instrument tag (may be null)
   * @return true if the tag conforms to the ISA-5.1 letter scheme
   */
  static boolean isValid(String tag) {
    return validate(tag).isValid();
  }
}
