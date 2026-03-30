package neqsim.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApiEnvelope}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ApiEnvelopeTest {

  @Test
  void testSuccessEnvelope() {
    ApiEnvelope<String> env = ApiEnvelope.success("hello");

    assertTrue(env.isSuccess());
    assertEquals("success", env.getStatus());
    assertEquals("hello", env.getData());
    assertTrue(env.getErrors().isEmpty());
    assertTrue(env.getWarnings().isEmpty());
  }

  @Test
  void testSuccessWithWarnings() {
    List<String> warnings = new ArrayList<String>();
    warnings.add("watch out");

    ApiEnvelope<String> env = ApiEnvelope.success("data", warnings);

    assertTrue(env.isSuccess());
    assertEquals(1, env.getWarnings().size());
    assertEquals("watch out", env.getWarnings().get(0));
  }

  @Test
  void testErrorEnvelope() {
    ApiEnvelope<String> env = ApiEnvelope.error("ERR_CODE", "something broke", "try again");

    assertFalse(env.isSuccess());
    assertEquals("error", env.getStatus());
    assertNull(env.getData());
    assertEquals(1, env.getErrors().size());
    assertEquals("ERR_CODE", env.getErrors().get(0).getCode());
  }

  @Test
  void testErrorsListEnvelope() {
    List<DiagnosticIssue> issues = new ArrayList<DiagnosticIssue>();
    issues.add(DiagnosticIssue.error("E1", "first", "fix1"));
    issues.add(DiagnosticIssue.error("E2", "second", "fix2"));

    ApiEnvelope<String> env = ApiEnvelope.errors(issues);

    assertFalse(env.isSuccess());
    assertEquals(2, env.getErrors().size());
  }

  @Test
  void testAddWarning() {
    ApiEnvelope<String> env = ApiEnvelope.success("data");
    env.addWarning("new warning");

    assertEquals(1, env.getWarnings().size());
  }

  @Test
  void testToJson_success() {
    ApiEnvelope<String> env = ApiEnvelope.success("test-data");

    String json = env.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("success", root.get("status").getAsString());
    assertTrue(root.has("data"));
    assertFalse(root.has("errors"));
  }

  @Test
  void testToJson_error() {
    ApiEnvelope<Object> env = ApiEnvelope.error("ERR", "msg", "fix");

    String json = env.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertEquals("error", root.get("status").getAsString());
    assertTrue(root.has("errors"));
    assertEquals(1, root.getAsJsonArray("errors").size());

    JsonObject err = root.getAsJsonArray("errors").get(0).getAsJsonObject();
    assertEquals("ERR", err.get("code").getAsString());
    assertEquals("msg", err.get("message").getAsString());
  }

  @Test
  void testToJson_successWithWarnings() {
    List<String> warnings = new ArrayList<String>();
    warnings.add("w1");
    warnings.add("w2");

    ApiEnvelope<String> env = ApiEnvelope.success("data", warnings);
    String json = env.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    assertTrue(root.has("warnings"));
    assertEquals(2, root.getAsJsonArray("warnings").size());
  }
}
