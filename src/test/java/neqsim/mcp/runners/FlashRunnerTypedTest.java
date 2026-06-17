package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.FlashRequest;
import neqsim.mcp.model.FlashResult;
import neqsim.mcp.model.ValueWithUnit;

/**
 * Tests for {@link FlashRunner#runTyped(FlashRequest)}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlashRunnerTypedTest {

  @Test
  void testRunTyped_simpleGas() {
    FlashRequest request = new FlashRequest().setModel("SRK")
        .setTemperature(new ValueWithUnit(25.0, "C")).setPressure(new ValueWithUnit(50.0, "bara"))
        .setFlashType("TP").addComponent("methane", 0.85).addComponent("ethane", 0.10)
        .addComponent("propane", 0.05);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertEquals("SRK", result.getData().getModel());
    assertEquals("TP", result.getData().getFlashType());
    assertTrue(result.getData().getNumberOfPhases() >= 1);
    assertNotNull(result.getData().getFluidResponse());
  }

  @Test
  void testRunTyped_twoPhase() {
    FlashRequest request = new FlashRequest().setModel("SRK")
        .setTemperature(new ValueWithUnit(-20.0, "C")).setPressure(new ValueWithUnit(10.0, "bara"))
        .addComponent("methane", 0.50).addComponent("propane", 0.50);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertTrue(result.isSuccess());
    assertTrue(result.getData().getNumberOfPhases() >= 2);
    assertTrue(result.getData().getPhases().size() >= 2);
  }

  @Test
  void testRunTyped_prModel() {
    FlashRequest request = new FlashRequest().setModel("PR")
        .setTemperature(new ValueWithUnit(300.0, "K")).setPressure(new ValueWithUnit(10.0, "bara"))
        .addComponent("methane", 0.7).addComponent("propane", 0.3);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertTrue(result.isSuccess());
    assertEquals("PR", result.getData().getModel());
  }

  @Test
  void testRunTyped_unknownModel() {
    FlashRequest request = new FlashRequest().setModel("INVALID").addComponent("methane", 1.0);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertFalse(result.isSuccess());
    assertEquals("UNKNOWN_MODEL", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_unknownComponent() {
    FlashRequest request = new FlashRequest().addComponent("methan", 1.0); // misspelled

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertFalse(result.isSuccess());
    assertEquals("UNKNOWN_COMPONENT", result.getErrors().get(0).getCode());
    assertTrue(result.getErrors().get(0).getMessage().contains("Did you mean"));
  }

  @Test
  void testRunTyped_missingComponents() {
    FlashRequest request = new FlashRequest();

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertFalse(result.isSuccess());
    assertEquals("MISSING_COMPONENTS", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_nullInput() {
    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(null);

    assertFalse(result.isSuccess());
    assertEquals("INPUT_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_dewPointT() {
    FlashRequest request = new FlashRequest().setFlashType("dewPointT")
        .setPressure(new ValueWithUnit(50.0, "bara")).addComponent("methane", 0.80)
        .addComponent("ethane", 0.10).addComponent("propane", 0.10);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertTrue(result.isSuccess());
    assertEquals("dewPointT", result.getData().getFlashType());
  }

  @Test
  void testRunTyped_unknownFlashType() {
    FlashRequest request = new FlashRequest().setFlashType("INVALID").addComponent("methane", 1.0);

    ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);

    assertFalse(result.isSuccess());
    assertEquals("UNKNOWN_FLASH_TYPE", result.getErrors().get(0).getCode());
  }

  @Test
  void testConvertTemperatureToKelvin() {
    assertEquals(298.15, FlashRunner.convertTemperatureToKelvin(new ValueWithUnit(25.0, "C")),
        0.01);
    assertEquals(300.0, FlashRunner.convertTemperatureToKelvin(new ValueWithUnit(300.0, "K")),
        0.01);
    assertEquals(373.15, FlashRunner.convertTemperatureToKelvin(new ValueWithUnit(212.0, "F")),
        0.1);
    assertEquals(288.15, FlashRunner.convertTemperatureToKelvin(null), 0.01);
    assertTrue(Double.isNaN(FlashRunner.convertTemperatureToKelvin(new ValueWithUnit(25.0, "X"))));
  }

  @Test
  void testConvertPressureToBara() {
    assertEquals(50.0, FlashRunner.convertPressureToBara(new ValueWithUnit(50.0, "bara")), 0.01);
    assertEquals(10.0, FlashRunner.convertPressureToBara(new ValueWithUnit(1.0, "MPa")), 0.01);
    assertEquals(1.01325, FlashRunner.convertPressureToBara(null), 0.01);
    assertTrue(Double.isNaN(FlashRunner.convertPressureToBara(new ValueWithUnit(50.0, "INVALID"))));
  }
}
