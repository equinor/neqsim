package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;

/**
 * Verifies explicit scaling, immutable policy and deterministic numerical error evidence.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TransientErrorStateVectorTest {
  /** Identity ordering and the declared scales determine the norm independently of provider order. */
  @Test
  public void normalizedErrorUsesDeclaredUnitsAndTolerances() {
    TransientErrorStateVector full = new TransientErrorStateVector(
        Arrays.asList(variable("vessel", "temperature", "K", 300.0, 0.1, 0.01),
            variable("vessel", "inventory", "kg", 10.0, 0.25, 0.0)));
    TransientErrorStateVector half = new TransientErrorStateVector(
        Arrays.asList(variable("vessel", "inventory", "kg", 10.5, 0.25, 0.0),
            variable("vessel", "temperature", "K", 301.0, 0.1, 0.01)));

    TransientErrorStateVector.ErrorEstimate estimate = full.compareTo(half);
    assertEquals(2.0, estimate.getNormalizedError(), 0.0);
    assertEquals("vessel::inventory", estimate.getLimitingVariable());
    assertEquals("inventory", full.getVariables().get(0).getVariableIdentity());
  }

  /** Equal maxima are resolved by stable owner and variable ordering. */
  @Test
  public void tiesHaveDeterministicLimitingIdentity() {
    TransientErrorStateVector full = new TransientErrorStateVector(
        Arrays.asList(variable("b", "mass", "kg", 0.0, 1.0, 0.0), variable("a", "mass", "kg", 0.0, 1.0, 0.0)));
    TransientErrorStateVector half = new TransientErrorStateVector(
        Arrays.asList(variable("a", "mass", "kg", 1.0, 1.0, 0.0), variable("b", "mass", "kg", -1.0, 1.0, 0.0)));
    assertEquals("a::mass", full.compareTo(half).getLimitingVariable());
  }

  /** Value objects reject incomplete identities, nonfinite values and implicit or invalid error scales. */
  @Test
  public void invalidScalarContractsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> variable(" ", "mass", "kg", 0.0, 1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> variable("tank", null, "kg", 0.0, 1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "", 0.0, 1.0, 0.0));
    for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", invalid, 1.0, 0.0));
      assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", 0.0, invalid, 0.0));
      assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", 0.0, 1.0, invalid));
    }
    assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", 0.0, 0.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", 0.0, -1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> variable("tank", "mass", "kg", 0.0, 1.0, -0.1));
  }

  /** Changed state identity, unit, tolerance policy or coverage cannot be accepted as numerical convergence. */
  @Test
  public void comparisonRejectsChangedMetadataAndCoverage() {
    TransientErrorStateVector baseline = vector(variable("tank", "mass", "kg", 0.0, 1.0, 0.0));
    List<TransientErrorStateVariable> changed = Arrays.asList(variable("other", "mass", "kg", 0.0, 1.0, 0.0),
        variable("tank", "other", "kg", 0.0, 1.0, 0.0), variable("tank", "mass", "g", 0.0, 1.0, 0.0),
        variable("tank", "mass", "kg", 0.0, 2.0, 0.0), variable("tank", "mass", "kg", 0.0, 1.0, 0.1));
    for (TransientErrorStateVariable value : changed) {
      assertThrows(IllegalArgumentException.class, () -> baseline.compareTo(vector(value)));
    }
    assertThrows(IllegalArgumentException.class, () -> baseline.compareTo(null));
    TransientErrorStateVector expanded = new TransientErrorStateVector(Arrays
        .asList(variable("tank", "mass", "kg", 0.0, 1.0, 0.0), variable("tank", "temperature", "K", 300.0, 1.0, 0.0)));
    assertThrows(IllegalArgumentException.class, () -> baseline.compareTo(expanded));
  }

  /** Pairwise identity checks avoid delimiter collisions and defend the captured vector against mutation. */
  @Test
  public void vectorCaptureIsDefensiveAndRejectsDuplicateIdentities() {
    TransientErrorStateVariable value = variable("a::b", "c", "kg", 0.0, 1.0, 0.0);
    List<TransientErrorStateVariable> source = new ArrayList<TransientErrorStateVariable>();
    source.add(value);
    source.add(variable("a", "b::c", "kg", 0.0, 1.0, 0.0));
    TransientErrorStateVector captured = new TransientErrorStateVector(source);
    source.clear();
    assertEquals(2, captured.getVariables().size());
    assertThrows(UnsupportedOperationException.class, () -> captured.getVariables().clear());
    assertThrows(IllegalArgumentException.class, () -> new TransientErrorStateVector(Arrays.asList(value, value)));
    assertThrows(IllegalArgumentException.class,
        () -> new TransientErrorStateVector(Collections.<TransientErrorStateVariable>emptyList()));
    assertThrows(IllegalArgumentException.class, () -> new TransientErrorStateVector(Arrays.asList(value, null)));
  }

  /** Extreme arithmetic fails conservatively rather than manufacturing zero or NaN error. */
  @Test
  public void arithmeticOverflowProducesExplicitInfiniteError() {
    TransientErrorStateVector positive = vector(
        variable("tank", "mass", "kg", Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE));
    TransientErrorStateVector negative = vector(
        variable("tank", "mass", "kg", -Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE));
    assertEquals(Double.POSITIVE_INFINITY, positive.compareTo(negative).getNormalizedError());
    assertEquals(0.0, vector(variable("tank", "mass", "kg", 0.0, Double.MIN_VALUE, 0.0))
        .compareTo(vector(variable("tank", "mass", "kg", 0.0, Double.MIN_VALUE, 0.0))).getNormalizedError(), 0.0);
  }

  /** Explicit options are immutable and serialized state retains the same numerical evidence. */
  @Test
  public void policyAndEvidenceSurviveSerialization() throws Exception {
    ControllerDeviceBaseClass participant = new ControllerDeviceBaseClass("PC-100");
    TransientErrorStateBinding binding = new TransientErrorStateBinding(participant,
        () -> Collections.singletonList(variable("PC-100", "integral", "1", 0.0, 0.01, 0.0)));
    ArrayList<TransientErrorStateBinding> input = new ArrayList<TransientErrorStateBinding>();
    input.add(binding);
    AdaptiveTransientOptions options = new AdaptiveTransientOptions(0.001, 1.0, 10, 1, input);
    input.clear();
    assertEquals(1, options.getBindings().size());
    assertThrows(UnsupportedOperationException.class, () -> options.getBindings().clear());
    AdaptiveTransientOptions restoredOptions = roundTrip(options);
    assertEquals(0.001, restoredOptions.getMinimumStepSeconds(), 0.0);
    assertEquals(1, restoredOptions.getBindings().get(0).getProvider().getTransientErrorState().size());

    TransientErrorStateVector original = vector(variable("tank", "mass", "kg", 10.0, 0.1, 0.01));
    TransientErrorStateVector restored = roundTrip(original);
    assertEquals(0.0, original.compareTo(restored).getNormalizedError(), 0.0);
    UUID parent = UUID.nameUUIDFromBytes(new byte[] {1});
    UUID first = UUID.nameUUIDFromBytes(new byte[] {2});
    UUID second = UUID.nameUUIDFromBytes(new byte[] {3});
    AdaptiveTransientResult result = roundTrip(
        new AdaptiveTransientResult(parent, 0.1, 0.15, 0.5, "tank::mass", 3, 2, first, second));
    assertEquals(parent, result.getPhysicalStepIdentifier());
    assertEquals(first, result.getFirstHalfStepIdentifier());
    assertEquals(second, result.getSecondHalfStepIdentifier());
    assertEquals(0.1, result.getAcceptedStepSeconds(), 0.0);
    assertEquals(2, result.getRejectedAttempts());
  }

  /** Invalid policies or incomplete associations fail before any trial may start. */
  @Test
  public void invalidPoliciesAndBindingsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new TransientErrorStateBinding(null, null));
    ControllerDeviceBaseClass participant = new ControllerDeviceBaseClass("PC-100");
    TransientErrorStateBinding binding = new TransientErrorStateBinding(participant,
        () -> Collections.singletonList(variable("PC-100", "integral", "1", 0.0, 0.01, 0.0)));
    List<TransientErrorStateBinding> bindings = Collections.singletonList(binding);
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(0.0, 1.0, 1, 1, bindings));
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(1.0, 0.1, 1, 1, bindings));
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(0.1, Double.NaN, 1, 1, bindings));
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(0.1, 1.0, 0, 1, bindings));
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(0.1, 1.0, 1, 0, bindings));
    assertThrows(IllegalArgumentException.class, () -> new AdaptiveTransientOptions(0.1, 1.0, 1, 9, bindings));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientOptions(0.1, 1.0, 1, 1, Collections.<TransientErrorStateBinding>emptyList()));
    assertTrue(binding.getParticipant() == participant);
  }

  /** Accepted evidence cannot describe excessive error, aliased step identities or uncounted rejections. */
  @Test
  public void acceptedEvidenceEnforcesNumericalAndIdentityConsistency() {
    UUID parent = UUID.nameUUIDFromBytes(new byte[] {1});
    UUID first = UUID.nameUUIDFromBytes(new byte[] {2});
    UUID second = UUID.nameUUIDFromBytes(new byte[] {3});
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientResult(parent, 0.1, 0.1, 1.01, "tank::mass", 1, 0, first, second));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientResult(parent, 0.1, 0.1, 0.5, "tank::mass", 1, 0, parent, second));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientResult(parent, 0.1, 0.1, 0.5, "tank::mass", 1, 0, first, parent));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientResult(parent, 0.1, 0.1, 0.5, "tank::mass", 3, 1, first, second));
    assertEquals(1.0,
        new AdaptiveTransientResult(parent, 0.1, 0.1, 1.0, "tank::mass", 3, 2, first, second).getNormalizedError(),
        0.0);
  }

  /** Numerical termination survives serialization and retains explicit unavailable and infinite errors. */
  @Test
  public void failureEvidenceRetainsReasonAndNumericalBudget() throws Exception {
    AdaptiveTransientException exhausted = roundTrip(
        new AdaptiveTransientException(AdaptiveTransientException.Reason.ATTEMPT_LIMIT, "Attempt budget exhausted", 3,
            0.01, Double.POSITIVE_INFINITY, "tank::mass"));
    assertEquals(AdaptiveTransientException.Reason.ATTEMPT_LIMIT, exhausted.getReason());
    assertEquals(3, exhausted.getAttempts());
    assertEquals(0.01, exhausted.getAttemptedStepSeconds(), 0.0);
    assertEquals(Double.POSITIVE_INFINITY, exhausted.getNormalizedError());
    assertEquals("tank::mass", exhausted.getLimitingVariable());
    assertEquals("Attempt budget exhausted", exhausted.getMessage());
    AdaptiveTransientException unavailable = new AdaptiveTransientException(
        AdaptiveTransientException.Reason.UNREPRESENTABLE_INTERVAL, "Clock cannot advance", 0, Double.MIN_VALUE,
        Double.NaN, null);
    assertTrue(Double.isNaN(unavailable.getNormalizedError()));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientException(null, "Invalid reason", 0, 0.01, Double.NaN, null));
    assertThrows(IllegalArgumentException.class,
        () -> new AdaptiveTransientException(AdaptiveTransientException.Reason.MINIMUM_STEP, "Invalid error", 1, 0.01,
            -1.0, null));
  }

  /**
   * Creates one explicitly scaled test variable.
   *
   * @param owner stable state owner
   * @param variable stable variable identity
   * @param unit physical unit
   * @param value state value
   * @param absolute absolute tolerance
   * @param relative relative tolerance
   * @return test variable
   */
  private static TransientErrorStateVariable variable(String owner, String variable, String unit, double value,
      double absolute, double relative) {
    return new TransientErrorStateVariable(owner, variable, unit, value, absolute, relative);
  }

  /**
   * Creates a one-variable vector.
   *
   * @param value numerical state value
   * @return single-variable vector
   */
  private static TransientErrorStateVector vector(TransientErrorStateVariable value) {
    return new TransientErrorStateVector(Collections.singletonList(value));
  }

  /**
   * Serializes and restores a value or explicit option policy.
   *
   * @param original serializable input
   * @param <T> restored value type
   * @return deserialized copy
   * @throws Exception if serialization or deserialization fails
   */
  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T roundTrip(T original) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) input.readObject();
    }
  }
}
