package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link WriteValidatorRegistry} dispatch behaviour and the bundled
 * {@link DefaultWriteValidators}.
 */
class WriteValidatorRegistryTest {

  private Stream makeFeed(double pBara, double tC) {
    SystemInterface fluid = new SystemSrkEos(273.15 + tC, pBara);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(tC, "C");
    feed.setPressure(pBara, "bara");
    feed.run();
    return feed;
  }

  @Test
  void okIsShared() {
    assertSame(WriteValidationResult.ok(), WriteValidationResult.ok());
  }

  @Test
  void warningIsAllowedErrorIsNot() {
    assertTrue(WriteValidationResult.ok().isAllowed());
    assertTrue(WriteValidationResult.warn("X", "warn").isAllowed());
    assertFalse(WriteValidationResult.fail("E", "err").isAllowed());
  }

  @Test
  void defaultRegistryRegistersSixClasses() {
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    assertEquals(6, reg.getRegisteredClasses().size());
    assertTrue(reg.getRegisteredClasses().containsKey(Compressor.class));
    assertTrue(reg.getRegisteredClasses().containsKey(Pump.class));
    assertTrue(reg.getRegisteredClasses().containsKey(ThrottlingValve.class));
    assertTrue(reg.getRegisteredClasses().containsKey(Heater.class));
    assertTrue(reg.getRegisteredClasses().containsKey(Cooler.class));
    assertTrue(reg.getRegisteredClasses().containsKey(Separator.class));
  }

  @Test
  void compressorRejectsNegativePressure() {
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(c, "outletPressure", -10.0, "bara");
    assertFalse(r.isAllowed());
    assertEquals("OUTLET_PRESSURE_NOT_POSITIVE", r.getCode());
  }

  @Test
  void compressorRejectsOutletBelowInlet() {
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(c, "outletPressure", 40.0, "bara");
    assertFalse(r.isAllowed());
    assertEquals("OUTLET_PRESSURE_BELOW_INLET", r.getCode());
  }

  @Test
  void compressorAcceptsValidOutlet() {
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(c, "outletPressure", 120.0, "bara");
    assertTrue(r.isAllowed());
    assertEquals(WriteValidationResult.Severity.OK, r.getSeverity());
  }

  @Test
  void compressorWarnsOnVeryHighRatio() {
    Compressor c = new Compressor("K-1", makeFeed(2.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(c, "outletPressure", 200.0, "bara");
    assertTrue(r.isAllowed());
    assertEquals(WriteValidationResult.Severity.WARNING, r.getSeverity());
    assertEquals("PRESSURE_RATIO_VERY_HIGH", r.getCode());
  }

  @Test
  void compressorRejectsEfficiencyOutOfRange() {
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    assertFalse(reg.validate(c, "polytropicEfficiency", 1.5, null).isAllowed());
    assertFalse(reg.validate(c, "isentropicEfficiency", 0.0, null).isAllowed());
    assertTrue(reg.validate(c, "polytropicEfficiency", 0.75, null).isAllowed());
  }

  @Test
  void heaterRejectsOutletBelowInlet() {
    Heater h = new Heater("H-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(h, "outletTemperature", 10.0, "C");
    assertFalse(r.isAllowed());
    assertEquals("HEATER_COOLS_FLUID", r.getCode());
  }

  @Test
  void coolerRejectsOutletAboveInlet() {
    Cooler c = new Cooler("C-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(c, "outletTemperature", 80.0, "C");
    assertFalse(r.isAllowed());
    assertEquals("COOLER_HEATS_FLUID", r.getCode());
  }

  @Test
  void heaterValidatorDoesNotFireOnCooler() {
    // Cooler extends Heater; ensure the HeaterWriteValidator delegates correctly.
    Cooler c = new Cooler("C-1", makeFeed(50.0, 30.0));
    DefaultWriteValidators.HeaterWriteValidator hv =
        new DefaultWriteValidators.HeaterWriteValidator();
    WriteValidationResult r = hv.validate(c, "outletTemperature", 10.0, "C");
    assertTrue(r.isAllowed(), "HeaterWriteValidator must not reject Cooler writes");
  }

  @Test
  void valveRejectsOutletAboveInlet() {
    ThrottlingValve v = new ThrottlingValve("V-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    WriteValidationResult r = reg.validate(v, "outletPressure", 80.0, "bara");
    assertFalse(r.isAllowed());
    assertEquals("OUTLET_PRESSURE_ABOVE_INLET", r.getCode());
  }

  @Test
  void valveRejectsBadOpening() {
    ThrottlingValve v = new ThrottlingValve("V-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    assertFalse(reg.validate(v, "percentValveOpening", -1.0, null).isAllowed());
    assertFalse(reg.validate(v, "percentValveOpening", 110.0, null).isAllowed());
    assertTrue(reg.validate(v, "percentValveOpening", 50.0, null).isAllowed());
  }

  @Test
  void errorTakesPrecedenceOverWarning() {
    WriteValidatorRegistry reg = new WriteValidatorRegistry();
    reg.register(new WriteValidator() {
      @Override
      public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
        return Compressor.class;
      }

      @Override
      public WriteValidationResult validate(ProcessEquipmentInterface eq, String pp, double v,
          String u) {
        return WriteValidationResult.warn("W1", "warn");
      }
    });
    reg.register(new WriteValidator() {
      @Override
      public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
        return Compressor.class;
      }

      @Override
      public WriteValidationResult validate(ProcessEquipmentInterface eq, String pp, double v,
          String u) {
        return WriteValidationResult.fail("E1", "err");
      }
    });
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidationResult r = reg.validate(c, "anything", 1.0, null);
    assertEquals(WriteValidationResult.Severity.ERROR, r.getSeverity());
    assertEquals("E1", r.getCode());
  }

  @Test
  void hierarchyWalkFindsParentValidator() {
    // Register against Heater; check that a Cooler (extends Heater) sees the validator.
    WriteValidatorRegistry reg = new WriteValidatorRegistry();
    final List<String> seen = new ArrayList<String>();
    reg.register(new WriteValidator() {
      @Override
      public Class<? extends ProcessEquipmentInterface> getEquipmentClass() {
        return Heater.class;
      }

      @Override
      public WriteValidationResult validate(ProcessEquipmentInterface eq, String pp, double v,
          String u) {
        seen.add(eq.getName());
        return WriteValidationResult.ok();
      }
    });
    Cooler c = new Cooler("C-1", makeFeed(50.0, 30.0));
    reg.validate(c, "outletTemperature", 20.0, "C");
    assertEquals(1, seen.size());
  }

  @Test
  void unregisterRemovesValidator() {
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    assertTrue(reg.getRegisteredClasses().containsKey(Compressor.class));
    reg.unregister(Compressor.class);
    assertFalse(reg.getRegisteredClasses().containsKey(Compressor.class));
  }

  @Test
  void unitConversionBaraVsPa() {
    // 12_000_000 Pa = 120 bara — should pass for a 50 bara inlet
    Compressor c = new Compressor("K-1", makeFeed(50.0, 30.0));
    WriteValidatorRegistry reg = WriteValidatorRegistry.createDefault();
    assertTrue(reg.validate(c, "outletPressure", 1.2e7, "Pa").isAllowed());
  }
}
