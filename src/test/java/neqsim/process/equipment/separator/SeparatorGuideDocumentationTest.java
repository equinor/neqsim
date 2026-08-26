package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Executes the core examples in docs/process/equipment/separators.md. */
class SeparatorGuideDocumentationTest {

  private Stream createThreePhaseFeed() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);

    Stream feed = new Stream("Feed", fluid);
    feed.setTemperature(72.0, "C");
    feed.setPressure(10.7, "bara");
    feed.setFlowRate(720.0, "kg/hr");
    feed.run();
    return feed;
  }

  /** Prove the documented outlets and explicit produced-liquid water fraction execute. */
  @Test
  void threePhaseExampleUsesCurrentOutletAndMassFractionApis() {
    Stream feed = createThreePhaseFeed();
    assertFalse(feed.getFluid().doMultiPhaseCheck());

    ThreePhaseSeparator separator = new ThreePhaseSeparator("1st Stage Sep", feed);
    separator.run();

    StreamInterface gasOut = separator.getGasOutStream();
    StreamInterface oilOut = separator.getOilOutStream();
    StreamInterface waterOut = separator.getWaterOutStream();
    assertNotNull(gasOut);
    assertNotNull(oilOut);
    assertNotNull(waterOut);
    assertFalse(feed.getFluid().doMultiPhaseCheck());

    double waterMassFlow = waterOut.getFlowRate("kg/hr");
    double oilMassFlow = oilOut.getFlowRate("kg/hr");
    double producedLiquidMassFlow = oilMassFlow + waterMassFlow;
    assertTrue(producedLiquidMassFlow > 0.0);

    double producedLiquidWaterMassFraction = waterMassFlow / producedLiquidMassFlow;
    assertTrue(producedLiquidWaterMassFraction >= 0.0);
    assertTrue(producedLiquidWaterMassFraction <= 1.0);

    double outletMassFlow = gasOut.getFlowRate("kg/hr") + oilMassFlow + waterMassFlow;
    double inletMassFlow = feed.getFlowRate("kg/hr");
    assertEquals(inletMassFlow, outletMassFlow, inletMassFlow * 1.0e-5);
  }

  /** Prove the documented vessel and imported mechanical-design APIs execute. */
  @Test
  void mechanicalDesignExampleUsesCurrentGeometryApis() {
    ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Sep");
    separator.setInternalDiameter(2.5);
    separator.setSeparatorLength(10.0);

    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    assertEquals(0.80, design.getHHLLFraction(), 1.0e-12);
    assertEquals(0.15, design.getLLLLFraction(), 1.0e-12);
    assertEquals(0.25, design.getHILFraction(), 1.0e-12);
    assertEquals(0.20, design.getNILFraction(), 1.0e-12);
    assertEquals(0.15, design.getLILFraction(), 1.0e-12);

    design.setFromExistingDesign(2.5, 10.0, 0.025, 8.0, 9.0, 0.50, 0.45, 0.35, 0.30);
    assertEquals(4.0, separator.getSeparatorLength() / separator.getInternalDiameter(), 1.0e-12);
    assertEquals(8.0, design.getEffectiveLengthLiquid(), 1.0e-12);
    assertEquals(9.0, design.getEffectiveLengthGas(), 1.0e-12);

    design.setFromDesignSpec(2.5, 10.0, 8.0, 9.0, 0.50, 2.00, 1.75, 1.25, 0.75, 0.625, 0.60, 0.50, 0.35);
    assertEquals(2.00, design.getHHLL(), 1.0e-12);
    assertEquals(1.25, design.getNLL(), 1.0e-12);
    assertEquals(0.50, design.getNIL(), 1.0e-12);

    design.setWeirHeightAbsolute(design.getNIL() * 1.05);
    assertEquals(0.525, design.getWeirHeightAbsolute(), 1.0e-12);
    assertEquals(0.525, separator.getWeirHeight(), 1.0e-12);
  }
}
