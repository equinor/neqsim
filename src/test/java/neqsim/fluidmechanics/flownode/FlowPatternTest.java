package neqsim.fluidmechanics.flownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FlowPattern enum.
 */
class FlowPatternTest {

  @Test
  void testFlowPatternNames() {
    assertEquals("stratified", FlowPattern.STRATIFIED.getName());
    assertEquals("annular", FlowPattern.ANNULAR.getName());
    assertEquals("slug", FlowPattern.SLUG.getName());
    assertEquals("bubble", FlowPattern.BUBBLE.getName());
    assertEquals("droplet", FlowPattern.DROPLET.getName());
    assertEquals("churn", FlowPattern.CHURN.getName());
  }

  @Test
  void testFromString() {
    assertEquals(FlowPattern.STRATIFIED, FlowPattern.fromString("stratified"));
    assertEquals(FlowPattern.STRATIFIED, FlowPattern.fromString("STRATIFIED"));
    assertEquals(FlowPattern.ANNULAR, FlowPattern.fromString("annular"));
    assertEquals(FlowPattern.SLUG, FlowPattern.fromString("slug"));
    assertEquals(FlowPattern.BUBBLE, FlowPattern.fromString("bubble"));
    assertEquals(FlowPattern.DROPLET, FlowPattern.fromString("droplet"));
    assertEquals(FlowPattern.DROPLET, FlowPattern.fromString("mist")); // alias
    assertEquals(FlowPattern.STRATIFIED_WAVY, FlowPattern.fromString("wavy")); // alias
  }

  @Test
  void testFromStringNull() {
    assertEquals(FlowPattern.STRATIFIED, FlowPattern.fromString(null));
  }

  @Test
  void testFromStringInvalid() {
    assertThrows(IllegalArgumentException.class, () -> FlowPattern.fromString("invalid_pattern"));
  }

  @Test
  void testToString() {
    assertEquals("stratified", FlowPattern.STRATIFIED.toString());
    assertEquals("annular", FlowPattern.ANNULAR.toString());
  }
}
