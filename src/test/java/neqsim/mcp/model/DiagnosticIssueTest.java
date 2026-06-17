package neqsim.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DiagnosticIssue}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class DiagnosticIssueTest {

  @Test
  void testErrorFactory() {
    DiagnosticIssue issue = DiagnosticIssue.error("CODE", "message", "fix it");

    assertEquals("error", issue.getSeverity());
    assertEquals("CODE", issue.getCode());
    assertEquals("message", issue.getMessage());
    assertEquals("fix it", issue.getRemediation());
    assertTrue(issue.isError());
  }

  @Test
  void testWarningFactory() {
    DiagnosticIssue issue = DiagnosticIssue.warning("WARN", "a warning", "try this");

    assertEquals("warning", issue.getSeverity());
    assertEquals("WARN", issue.getCode());
    assertFalse(issue.isError());
  }

  @Test
  void testToJson() {
    DiagnosticIssue issue = DiagnosticIssue.error("TEST", "test msg", "test fix");

    JsonObject json = issue.toJson();

    assertEquals("error", json.get("severity").getAsString());
    assertEquals("TEST", json.get("code").getAsString());
    assertEquals("test msg", json.get("message").getAsString());
    assertEquals("test fix", json.get("remediation").getAsString());
  }

  @Test
  void testToJson_nullRemediation() {
    DiagnosticIssue issue = new DiagnosticIssue("error", "CODE", "msg", null);

    JsonObject json = issue.toJson();

    assertFalse(json.has("remediation"));
  }

  @Test
  void testToString() {
    DiagnosticIssue issue = DiagnosticIssue.error("CODE", "message", "fix");
    String str = issue.toString();

    assertTrue(str.contains("error"));
    assertTrue(str.contains("CODE"));
    assertTrue(str.contains("message"));
    assertTrue(str.contains("fix"));
  }
}
