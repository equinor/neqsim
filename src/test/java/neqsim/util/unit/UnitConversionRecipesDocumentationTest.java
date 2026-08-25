package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executable contracts for the unit-conversion cookbook examples. */
class UnitConversionRecipesDocumentationTest {
  @Test
  void convertsTemperaturePressureAndFlowThroughPublicApis() {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("methane", 1.0);
    fluid.setTemperature(77.0, "F");
    fluid.setPressure(5.0, "MPa");

    assertEquals(25.0, fluid.getTemperature("C"), 1.0e-10);
    assertEquals(50.0, fluid.getPressure("bara"), 1.0e-12);

    Stream stream = new Stream("unit conversion feed", fluid);
    stream.setFlowRate(3600.0, "kg/hr");
    assertEquals(1.0, stream.getFlowRate("kg/sec"), 1.0e-12);
  }

  @Test
  void convertsScalarLengthAndPowerWithCaseSensitiveUnits() {
    assertEquals(3.280839895013123, new LengthUnit(1.0, "m").getValue("ft"), 1.0e-12);
    assertEquals(0.745699872, new PowerUnit(1.0, "hp").getValue("kW"), 1.0e-12);
  }

  @Test
  void treatsReportUnitProfilesAsGlobalMutableState() {
    try {
      Units.activateFieldUnits();
      assertEquals("psia", Units.getSymbol("pressure"));
      assertEquals("hp", Units.getSymbol("power"));
    } finally {
      Units.activateDefaultUnits();
    }
    assertEquals("bara", Units.getSymbol("pressure"));
    assertEquals("W", Units.getSymbol("power"));
  }
}
