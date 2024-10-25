package neqsim.physicalproperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class PhysicalPropertyTypeTest {
  @Test
  void testByName() {
    assertEquals(PhysicalPropertyType.byName("DENSITY"), PhysicalPropertyType.MASS_DENSITY);
    assertEquals(PhysicalPropertyType.byName("density"), PhysicalPropertyType.MASS_DENSITY);
    assertEquals(PhysicalPropertyType.byName("mass_density"), PhysicalPropertyType.MASS_DENSITY);

    assertEquals(PhysicalPropertyType.byName("VISCOSITY"), PhysicalPropertyType.DYNAMIC_VISCOSITY);
    assertEquals(PhysicalPropertyType.byName("dynamic_VISCOSITY"),
        PhysicalPropertyType.DYNAMIC_VISCOSITY);

    assertEquals(PhysicalPropertyType.byName("CONDUCTIVITY"),
        PhysicalPropertyType.THERMAL_CONDUCTIVITY);
    assertEquals(PhysicalPropertyType.byName("thermal_CONDUCTIVITY"),
        PhysicalPropertyType.THERMAL_CONDUCTIVITY);
    assertEquals(PhysicalPropertyType.byName("conductivity"),
        PhysicalPropertyType.THERMAL_CONDUCTIVITY);
    assertEquals(PhysicalPropertyType.byName("viscosity"), PhysicalPropertyType.DYNAMIC_VISCOSITY);
  }

  @Test
  void testValueOf() {
    assertEquals(PhysicalPropertyType.MASS_DENSITY, PhysicalPropertyType.valueOf("MASS_DENSITY"));
    assertEquals(PhysicalPropertyType.MASS_DENSITY,
        PhysicalPropertyType.valueOf("mass_density".toUpperCase()));

    SystemInterface thermoSystem = new SystemPrEos(318.0, 20.01325);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.init(0);
    thermoSystem.init(1);
    thermoSystem.initPhysicalProperties("DENSITY");
    thermoSystem.getPhase(PhaseType.GAS).initPhysicalProperties("viscosity");
  }
}
