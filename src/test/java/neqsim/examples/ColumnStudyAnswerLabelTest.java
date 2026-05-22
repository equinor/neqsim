package neqsim.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Verifies user-facing wording in column_study.py has been updated from UniSim to answer.
 *
 * @author Copilot
 * @version 1.0
 */
public class ColumnStudyAnswerLabelTest {
  /**
   * Ensures key console headers and table labels use answer wording.
   *
   * @throws IOException if the script file cannot be read.
   */
  @Test
  public void shouldUseAnswerWordingInKeyHeaders() throws IOException {
    Path scriptPath = Paths.get("examples", "notebooks", "column_study.py");
    String text = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

    assertTrue(text.contains("Comparison with answer reference"),
        "Expected comparison header to use 'answer'.");
    assertTrue(text.contains("Per-component mass balance (Inlet, TOP, BOTTOM — answer vs NeqSim)"),
        "Expected per-component mass balance header to use 'answer'.");
    assertTrue(text.contains("TOP answer"), "Expected TOP column label to use 'answer'.");
    assertTrue(text.contains("BOT answer"), "Expected BOTTOM column label to use 'answer'.");

    assertFalse(text.contains("Comparison with UniSim reference"),
        "Legacy UniSim comparison header should be removed.");
    assertFalse(text.contains("Per-component mass balance (Inlet, TOP, BOTTOM — UniSim vs NeqSim)"),
        "Legacy UniSim per-component header should be removed.");
    assertFalse(text.contains("TOP UniSim"), "Legacy TOP UniSim label should be removed.");
    assertFalse(text.contains("BOT UniSim"), "Legacy BOTTOM UniSim label should be removed.");
  }

  /**
   * Ensures mass target values for overhead (mass out) and bottoms are preserved.
   *
   * @throws IOException if the script file cannot be read.
   */
  @Test
  public void shouldKeepMassOutAndBottomTargets() throws IOException {
    Path scriptPath = Paths.get("examples", "notebooks", "column_study.py");
    String text = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);

    double massOutTarget = extractMassFlowTarget(text, "overhead");
    double massBottomTarget = extractMassFlowTarget(text, "bottoms");

    assertEquals(23745.275834065487, massOutTarget, 1.0e-9,
        "Unexpected overhead mass_flow_kg_hr target.");
    assertEquals(83561.38485823716, massBottomTarget, 1.0e-9,
        "Unexpected bottoms mass_flow_kg_hr target.");
  }

  /**
   * Extracts a stream's mass_flow_kg_hr value from UNISIM_TARGET in column_study.py.
   *
   * @param scriptText full contents of column_study.py.
   * @param streamName stream name key (for example, overhead or bottoms).
   * @return parsed mass_flow_kg_hr value.
   */
  private double extractMassFlowTarget(String scriptText, String streamName) {
    String pattern = "(?s)\"" + Pattern.quote(streamName) + "\"\\s*:\\s*\\{.*?"
        + "\"mass_flow_kg_hr\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)";
    Matcher matcher = Pattern.compile(pattern).matcher(scriptText);
    assertTrue(matcher.find(), "Could not find mass_flow_kg_hr for stream: " + streamName);
    return Double.parseDouble(matcher.group(1));
  }
}
