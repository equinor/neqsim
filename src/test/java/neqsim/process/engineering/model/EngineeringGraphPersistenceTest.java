package neqsim.process.engineering.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Verifies that persisted numerical evidence retains its fingerprint and revision identity.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class EngineeringGraphPersistenceTest {
  /** Verifies exact scalar number preservation, including values beyond binary64 precision. */
  @Test
  void preservesScalarNumbersWithoutChangingTheFingerprint() {
    EngineeringGraph original = new EngineeringGraph("numeric-evidence", "A");
    original.addNode(new EngineeringNode("result:trip", EngineeringNode.Kind.SCENARIO_RESULT, "trip", "Trip result")
        .putProperty("count", Integer.valueOf(3)).putProperty("largeCounter", Long.valueOf(9007199254740993L))
        .putProperty("sequence", new BigInteger("123456789012345678901234567890"))
        .putProperty("preciseValue", new BigDecimal("0.123456789012345678901234567890"))
        .putProperty("scientificValue", new BigDecimal("1.2300E-25")).putProperty("pressureBara", Double.valueOf(6.0))
        .putProperty("negativeZero", Double.valueOf(-0.0)));

    EngineeringGraph restored = EngineeringGraph.fromJson(original.toJson());

    assertEquals(original.toJson(), restored.toJson());
    assertTrue(original.compareTo(restored).isEmpty());
    assertTrue(restored.compareTo(original).isEmpty());
    Number largeCounter = (Number) restored.getNode("result:trip").getProperties().get("largeCounter");
    assertEquals(9007199254740993L, largeCounter.longValue());
  }

  /** Verifies recursive preservation of counters in structured protection-logic evidence. */
  @Test
  void preservesNestedNumericEvidenceAcrossRepeatedRoundTrips() {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("activationCount", Integer.valueOf(1));
    evidence.put("completedSteps", Long.valueOf(12L));
    evidence.put("elapsedSeconds", Double.valueOf(1.0));
    evidence.put("samples", Arrays.asList(Integer.valueOf(0), Double.valueOf(0.5), Long.valueOf(9007199254740993L),
        null, Boolean.TRUE, "observed"));
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("logicEvidence", Arrays.asList(evidence));
    EngineeringGraph original = new EngineeringGraph("nested-evidence", "A");
    original.addNode(new EngineeringNode("result:trip", EngineeringNode.Kind.SCENARIO_RESULT, "trip", "Trip result")
        .putProperty("result", result));

    EngineeringGraph restored = EngineeringGraph.fromJson(original.toJson());
    EngineeringGraph restoredAgain = EngineeringGraph.fromJson(restored.toJson());

    assertEquals(original.toJson(), restoredAgain.toJson());
    assertTrue(original.compareTo(restoredAgain).isEmpty());
    assertTrue(restoredAgain.compareTo(original).isEmpty());
  }

  /** Verifies that preserving number representations does not disable fingerprint validation. */
  @Test
  void rejectsTamperedNestedNumericEvidence() {
    Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    evidence.put("activationCount", Integer.valueOf(1));
    EngineeringGraph original = new EngineeringGraph("tampered-evidence", "A");
    original.addNode(new EngineeringNode("result:trip", EngineeringNode.Kind.SCENARIO_RESULT, "trip", "Trip result")
        .putProperty("result", evidence));
    JsonObject tampered = JsonParser.parseString(original.toJson()).getAsJsonObject();
    tampered.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("properties").getAsJsonObject("result")
        .addProperty("activationCount", Integer.valueOf(2));

    assertThrows(IllegalArgumentException.class, () -> EngineeringGraph.fromJson(tampered.toString()));
  }
}
