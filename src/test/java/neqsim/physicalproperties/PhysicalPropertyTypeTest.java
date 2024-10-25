package neqsim.physicalproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

public class PhysicalPropertyTypeTest {
  @Test
  void testByName() {
    assertEquals(PhysicalPropertyType.byName("DENSITY"), PhysicalPropertyType.DENSITY);
    assertEquals(PhysicalPropertyType.byName("density"), PhysicalPropertyType.DENSITY);
    assertEquals(PhysicalPropertyType.byName("VISCOSITY"), PhysicalPropertyType.VISCOSITY);
    assertEquals(PhysicalPropertyType.byName("CONDUCTIVITY"), PhysicalPropertyType.CONDUCTIVITY);
  }

  @Test
  void testValueOf() {
    assertEquals(PhysicalPropertyType.DENSITY, PhysicalPropertyType.valueOf("DENSITY"));
    assertEquals(PhysicalPropertyType.DENSITY,
        PhysicalPropertyType.valueOf("density".toUpperCase()));

    SystemInterface thermoSystem = new SystemPrEos(318.0, 20.01325);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.init(0);
    thermoSystem.init(1);
    thermoSystem.initPhysicalProperties("DENSITY");
    thermoSystem.getPhase(PhaseType.GAS).initPhysicalProperties("viscosity");
  }
}
