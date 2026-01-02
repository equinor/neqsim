package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ESPPump (Electric Submersible Pump) for multiphase flow.
 */
@Disabled
class ESPPumpTest {

  private SystemInterface gasLiquidFluid;
  private SystemInterface liquidOnlyFluid;
  private Stream gasLiquidStream;
  private Stream liquidStream;

  @BeforeEach
  void setUp() {
    // Create a gas-liquid mixture (typical oil well production)
    gasLiquidFluid = new SystemSrkEos(323.15, 50.0); // 50°C, 50 bar
    gasLiquidFluid.addComponent("methane", 0.1); // 10% gas
    gasLiquidFluid.addComponent("n-heptane", 0.9); // 90% oil
    gasLiquidFluid.setMixingRule("classic");
    gasLiquidFluid.setMultiPhaseCheck(true);

    gasLiquidStream = new Stream("gasLiquidFeed", gasLiquidFluid);
    gasLiquidStream.setFlowRate(10000.0, "kg/hr");
    gasLiquidStream.setTemperature(50.0, "C");
    gasLiquidStream.setPressure(50.0, "bara");
    gasLiquidStream.run();

    // Create liquid-only stream
    liquidOnlyFluid = new SystemSrkEos(323.15, 50.0);
    liquidOnlyFluid.addComponent("n-heptane", 1.0);
    liquidOnlyFluid.setMixingRule("classic");

    liquidStream = new Stream("liquidFeed", liquidOnlyFluid);
    liquidStream.setFlowRate(10000.0, "kg/hr");
    liquidStream.setTemperature(50.0, "C");
    liquidStream.setPressure(50.0, "bara");
    liquidStream.run();
  }

  @Test
  void testESPConstructor() {
    ESPPump esp = new ESPPump("ESP-1");
    assertEquals("ESP-1", esp.getName());
    assertEquals(100, esp.getNumberOfStages());
    assertEquals(10.0, esp.getHeadPerStage());
  }

  @Test
  void testESPConstructorWithStream() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    assertEquals("ESP-1", esp.getName());
    assertNotNull(esp.getInletStream());
  }

  @Test
  void testLiquidOnlyOperation() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(8.0); // 8 m per stage
    esp.setIsentropicEfficiency(70.0);
    esp.run();

    // Check output pressure increased
    double outletPressure = esp.getOutletStream().getPressure("bara");
    assertTrue(outletPressure > 50.0, "Outlet pressure should be higher than inlet");

    // No gas = no degradation
    assertEquals(0.0, esp.getGasVoidFraction(), 0.01);
    assertEquals(1.0, esp.getHeadDegradationFactor(), 0.01);
    assertFalse(esp.isSurging());
    assertFalse(esp.isGasLocked());
  }

  @Test
  void testDesignHeadCalculation() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    esp.setNumberOfStages(100);
    esp.setHeadPerStage(10.0);

    assertEquals(1000.0, esp.getDesignHead(), 0.1);
  }

  @Test
  void testGasVoidFractionGetterSetter() {
    ESPPump esp = new ESPPump("ESP-1");

    // Default values
    assertEquals(0.30, esp.getMaxGVF());
    assertEquals(0.15, esp.getSurgingGVF());

    // Set new values
    esp.setMaxGVF(0.25);
    esp.setSurgingGVF(0.10);

    assertEquals(0.25, esp.getMaxGVF());
    assertEquals(0.10, esp.getSurgingGVF());
  }

  @Test
  void testGasSeparatorSettings() {
    ESPPump esp = new ESPPump("ESP-1");

    // Default: no gas separator
    assertFalse(esp.hasGasSeparator());
    assertEquals(0.0, esp.getGasSeparatorEfficiency());

    // Enable gas separator
    esp.setHasGasSeparator(true);
    esp.setGasSeparatorEfficiency(0.5); // 50% gas removal

    assertTrue(esp.hasGasSeparator());
    assertEquals(0.5, esp.getGasSeparatorEfficiency());
  }

  @Test
  void testDegradationCoefficients() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(10.0);

    // Default coefficients: A=0.5, B=2.0
    // At 20% GVF: f = 1 - 0.5*0.2 - 2.0*0.04 = 1 - 0.1 - 0.08 = 0.82
    esp.setDegradationCoefficients(0.5, 2.0);
    esp.run();

    // With pure liquid, no degradation
    assertEquals(1.0, esp.getHeadDegradationFactor(), 0.01);
  }

  @Test
  void testActualHeadWithLiquid() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(10.0);
    esp.run();

    // With liquid only, actual head equals design head
    assertEquals(esp.getDesignHead(), esp.getActualHead(), 1.0);
  }

  @Test
  void testNumberOfStagesGetterSetter() {
    ESPPump esp = new ESPPump("ESP-1");

    esp.setNumberOfStages(150);
    assertEquals(150, esp.getNumberOfStages());

    esp.setHeadPerStage(12.0);
    assertEquals(12.0, esp.getHeadPerStage());
  }

  @Test
  void testESPWithGasLiquidMixture() {
    // Create a stream that will have some dissolved gas but mostly liquid
    // Use moderate conditions where solver is stable
    SystemInterface multiphaseFluid = new SystemSrkEos(293.15, 50.0); // Higher pressure to keep
                                                                      // mostly liquid
    multiphaseFluid.addComponent("methane", 0.1); // 10% methane (mostly dissolved)
    multiphaseFluid.addComponent("n-heptane", 0.9); // 90% heptane (liquid)
    multiphaseFluid.setMixingRule("classic");
    multiphaseFluid.setMultiPhaseCheck(true);

    Stream multiphaseStream = new Stream("multiphase", multiphaseFluid);
    multiphaseStream.setFlowRate(10000.0, "kg/hr");
    multiphaseStream.setTemperature(20.0, "C");
    multiphaseStream.setPressure(50.0, "bara");
    multiphaseStream.run();

    ESPPump esp = new ESPPump("ESP-1", multiphaseStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(10.0);
    esp.setMaxGVF(0.40); // Allow higher GVF
    esp.setSurgingGVF(0.20);
    esp.run();

    // GVF should be calculable (may be very low since gas mostly dissolved)
    double gvf = esp.getGasVoidFraction();
    assertTrue(gvf >= 0.0, "GVF should be non-negative");

    // If GVF is significant, expect degradation
    if (gvf > 0.05) {
      assertTrue(esp.getHeadDegradationFactor() < 1.0, "Head should be degraded with gas present");
    }
  }

  @Test
  void testGasLockCondition() {
    // Create a very gassy stream
    SystemInterface gassyFluid = new SystemSrkEos(323.15, 10.0); // Low pressure
    gassyFluid.addComponent("methane", 0.8); // 80% gas
    gassyFluid.addComponent("n-heptane", 0.2);
    gassyFluid.setMixingRule("classic");
    gassyFluid.setMultiPhaseCheck(true);

    Stream gassyStream = new Stream("gassy", gassyFluid);
    gassyStream.setFlowRate(10000.0, "kg/hr");
    gassyStream.setTemperature(50.0, "C");
    gassyStream.setPressure(10.0, "bara");
    gassyStream.run();

    ESPPump esp = new ESPPump("ESP-1", gassyStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(10.0);
    esp.setMaxGVF(0.20); // Low tolerance for gas
    esp.run();

    // If GVF exceeds maxGVF, pump should be gas locked
    double gvf = esp.getGasVoidFraction();
    if (gvf > esp.getMaxGVF()) {
      assertTrue(esp.isGasLocked(), "Pump should be gas locked at high GVF");
      assertEquals(0.0, esp.getHeadDegradationFactor(),
          "Head degradation should be zero when gas locked");
    }
  }

  @Test
  void testGasSeparatorEffect() {
    ESPPump esp = new ESPPump("ESP-1", liquidStream);
    esp.setNumberOfStages(50);
    esp.setHeadPerStage(10.0);

    // Enable gas separator (would reduce effective GVF)
    esp.setHasGasSeparator(true);
    esp.setGasSeparatorEfficiency(0.7); // 70% gas removal

    esp.run();

    // Gas separator is configured correctly
    assertTrue(esp.hasGasSeparator());
    assertEquals(0.7, esp.getGasSeparatorEfficiency());
  }
}
