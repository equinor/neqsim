package neqsim.process.equipment.iec81346;

import java.io.Serializable;

/**
 * Represents an IEC 81346 reference designation for a process element.
 *
 * <p>
 * IEC 81346 defines three orthogonal aspects for identifying objects in industrial plants:
 * </p>
 * <ul>
 * <li><strong>Function aspect</strong> (prefix {@code =}): What the system <em>does</em> (e.g.
 * {@code =K1} for cooling function 1)</li>
 * <li><strong>Product aspect</strong> (prefix {@code -}): What the physical equipment <em>is</em>
 * (e.g. {@code -B1} for heat exchanger 1)</li>
 * <li><strong>Location aspect</strong> (prefix {@code +}): Where the equipment <em>is
 * installed</em> (e.g. {@code +R1.L2} for room 1, level 2)</li>
 * </ul>
 *
 * <p>
 * This class stores the three aspects independently and composes the full reference designation
 * string on demand. It also stores the IEC 81346-2 letter code that classifies the equipment.
 * </p>
 *
 * <p>
 * <strong>Examples of composed reference designations:</strong>
 * </p>
 * <ul>
 * <li>{@code =A1.K1-B1+P1.M1} — Separation function, heat exchanger 1, platform module 1</li>
 * <li>{@code =A1.K2-K1+P1.M2} — Compression function, compressor 1, platform module 2</li>
 * <li>{@code =A1.K1-S1+P1.M1} — Separation function, sensor 1, platform module 1</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ReferenceDesignation implements Serializable {

  private static final long serialVersionUID = 1001L;

  /** The function aspect designation without prefix (e.g. "A1.K1"). */
  private String functionDesignation = "";

  /** The product aspect designation without prefix (e.g. "B1"). */
  private String productDesignation = "";

  /** The location aspect designation without prefix (e.g. "P1.M1"). */
  private String locationDesignation = "";

  /** The IEC 81346-2 letter code classifying this equipment. */
  private IEC81346LetterCode letterCode = IEC81346LetterCode.A;

  /** The sequence number within the letter code category. */
  private int sequenceNumber = 0;

  /**
   * Creates an empty reference designation.
   */
  public ReferenceDesignation() {}

  /**
   * Parses an IEC 81346 reference designation string into its three aspects.
   *
   * <p>
   * Accepted formats (order of aspects does not matter):
   * </p>
   * <ul>
   * <li>{@code "=A1-B1+P1"} — all three aspects</li>
   * <li>{@code "=A1-B1"} — function + product</li>
   * <li>{@code "-B1+P1"} — product + location</li>
   * <li>{@code "-B1"} — product only</li>
   * </ul>
   *
   * @param designationString the reference designation string to parse
   * @return a new {@link ReferenceDesignation} instance, or an empty instance if the input is null
   *         or empty
   */
  public static ReferenceDesignation parse(String designationString) {
    if (designationString == null || designationString.trim().isEmpty()) {
      return new ReferenceDesignation();
    }
    String s = designationString.trim();
    String func = "";
    String prod = "";
    String loc = "";

    // Extract function aspect (=...)
    int eqIdx = s.indexOf('=');
    if (eqIdx >= 0) {
      int endFunc = findNextAspectStart(s, eqIdx + 1);
      func = s.substring(eqIdx + 1, endFunc);
    }

    // Extract product aspect (-...)
    int dashIdx = s.indexOf('-');
    if (dashIdx >= 0) {
      int endProd = findNextAspectStart(s, dashIdx + 1);
      prod = s.substring(dashIdx + 1, endProd);
    }

    // Extract location aspect (+...)
    int plusIdx = s.indexOf('+');
    if (plusIdx >= 0) {
      int endLoc = findNextAspectStart(s, plusIdx + 1);
      loc = s.substring(plusIdx + 1, endLoc);
    }

    // Try to extract letter code from the product aspect (first letter)
    IEC81346LetterCode letterCode = IEC81346LetterCode.A;
    int seqNum = 0;
    if (!prod.isEmpty()) {
      char firstChar = Character.toUpperCase(prod.charAt(0));
      try {
        letterCode = IEC81346LetterCode.valueOf(String.valueOf(firstChar));
      } catch (IllegalArgumentException ignored) {
        // Not a valid letter code — keep default A
      }
      // Extract sequence number from remaining digits
      StringBuilder digits = new StringBuilder();
      for (int i = 1; i < prod.length(); i++) {
        if (Character.isDigit(prod.charAt(i))) {
          digits.append(prod.charAt(i));
        } else {
          break;
        }
      }
      if (digits.length() > 0) {
        seqNum = Integer.parseInt(digits.toString());
      }
    }

    return new ReferenceDesignation(func, prod, loc, letterCode, seqNum);
  }

  /**
   * Finds the index of the next aspect prefix character ({@code =}, {@code -}, or {@code +}) after
   * the given start position.
   *
   * @param s the string to search
   * @param from the starting index (exclusive of the current prefix)
   * @return the index of the next prefix, or the end of string if none found
   */
  private static int findNextAspectStart(String s, int from) {
    for (int i = from; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '=' || c == '-' || c == '+') {
        return i;
      }
    }
    return s.length();
  }

  /**
   * Creates a reference designation with the specified aspects and letter code.
   *
   * @param functionDesignation the function aspect (without {@code =} prefix), e.g. "A1.K1"
   * @param productDesignation the product aspect (without {@code -} prefix), e.g. "B1"
   * @param locationDesignation the location aspect (without {@code +} prefix), e.g. "P1.M1"
   * @param letterCode the IEC 81346-2 letter code classifying this equipment
   * @param sequenceNumber the sequence number within the letter code category (1-based)
   */
  public ReferenceDesignation(String functionDesignation, String productDesignation,
      String locationDesignation, IEC81346LetterCode letterCode, int sequenceNumber) {
    this.functionDesignation = functionDesignation != null ? functionDesignation : "";
    this.productDesignation = productDesignation != null ? productDesignation : "";
    this.locationDesignation = locationDesignation != null ? locationDesignation : "";
    this.letterCode = letterCode != null ? letterCode : IEC81346LetterCode.A;
    this.sequenceNumber = sequenceNumber;
  }

  /**
   * Returns the function aspect designation without the {@code =} prefix.
   *
   * @return the function designation, e.g. "A1.K1"
   */
  public String getFunctionDesignation() {
    return functionDesignation;
  }

  /**
   * Sets the function aspect designation.
   *
   * @param functionDesignation the function designation without the {@code =} prefix
   */
  public void setFunctionDesignation(String functionDesignation) {
    this.functionDesignation = functionDesignation != null ? functionDesignation : "";
  }

  /**
   * Returns the product aspect designation without the {@code -} prefix.
   *
   * @return the product designation, e.g. "B1"
   */
  public String getProductDesignation() {
    return productDesignation;
  }

  /**
   * Sets the product aspect designation.
   *
   * @param productDesignation the product designation without the {@code -} prefix
   */
  public void setProductDesignation(String productDesignation) {
    this.productDesignation = productDesignation != null ? productDesignation : "";
  }

  /**
   * Returns the location aspect designation without the {@code +} prefix.
   *
   * @return the location designation, e.g. "P1.M1"
   */
  public String getLocationDesignation() {
    return locationDesignation;
  }

  /**
   * Sets the location aspect designation.
   *
   * @param locationDesignation the location designation without the {@code +} prefix
   */
  public void setLocationDesignation(String locationDesignation) {
    this.locationDesignation = locationDesignation != null ? locationDesignation : "";
  }

  /**
   * Returns the IEC 81346-2 letter code classifying this equipment.
   *
   * @return the letter code
   */
  public IEC81346LetterCode getLetterCode() {
    return letterCode;
  }

  /**
   * Sets the IEC 81346-2 letter code classifying this equipment.
   *
   * @param letterCode the letter code to set
   */
  public void setLetterCode(IEC81346LetterCode letterCode) {
    this.letterCode = letterCode != null ? letterCode : IEC81346LetterCode.A;
  }

  /**
   * Returns the sequence number within the letter code category.
   *
   * @return the sequence number (1-based)
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  /**
   * Sets the sequence number within the letter code category.
   *
   * @param sequenceNumber the sequence number (1-based)
   */
  public void setSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  /**
   * Returns the full function aspect string including the {@code =} prefix.
   *
   * @return the formatted function designation, e.g. "=A1.K1", or empty string if not set
   */
  public String getFormattedFunctionDesignation() {
    if (functionDesignation.trim().isEmpty()) {
      return "";
    }
    return "=" + functionDesignation;
  }

  /**
   * Returns the full product aspect string including the {@code -} prefix.
   *
   * @return the formatted product designation, e.g. "-B1", or empty string if not set
   */
  public String getFormattedProductDesignation() {
    if (productDesignation.trim().isEmpty()) {
      return "";
    }
    return "-" + productDesignation;
  }

  /**
   * Returns the full location aspect string including the {@code +} prefix.
   *
   * @return the formatted location designation, e.g. "+P1.M1", or empty string if not set
   */
  public String getFormattedLocationDesignation() {
    if (locationDesignation.trim().isEmpty()) {
      return "";
    }
    return "+" + locationDesignation;
  }

  /**
   * Composes the full IEC 81346 reference designation from all three aspects.
   *
   * <p>
   * The format is: {@code =function-product+location}, e.g. {@code =A1.K1-B1+P1.M1}. Empty aspects
   * are omitted.
   * </p>
   *
   * @return the full reference designation string, or empty string if no aspects are set
   */
  public String toReferenceDesignationString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getFormattedFunctionDesignation());
    sb.append(getFormattedProductDesignation());
    sb.append(getFormattedLocationDesignation());
    return sb.toString();
  }

  /**
   * Returns the product code string composed from the letter code and sequence number.
   *
   * <p>
   * Example: for letter code {@link IEC81346LetterCode#B} and sequence 3, returns "B3".
   * </p>
   *
   * @return the product code, e.g. "B3"
   */
  public String getProductCode() {
    return letterCode.name() + sequenceNumber;
  }

  /**
   * Checks if this reference designation has any aspects set.
   *
   * @return true if at least one aspect (function, product, or location) is non-empty
   */
  public boolean isSet() {
    return !functionDesignation.trim().isEmpty() || !productDesignation.trim().isEmpty()
        || !locationDesignation.trim().isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    String refDes = toReferenceDesignationString();
    return refDes.isEmpty() ? "(no designation)" : refDes;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ReferenceDesignation)) {
      return false;
    }
    ReferenceDesignation other = (ReferenceDesignation) obj;
    return functionDesignation.equals(other.functionDesignation)
        && productDesignation.equals(other.productDesignation)
        && locationDesignation.equals(other.locationDesignation);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = functionDesignation.hashCode();
    result = 31 * result + productDesignation.hashCode();
    result = 31 * result + locationDesignation.hashCode();
    return result;
  }
}
