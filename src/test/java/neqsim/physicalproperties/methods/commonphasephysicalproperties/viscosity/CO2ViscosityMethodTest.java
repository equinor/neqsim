package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemSpanWagnerEos;

/**
 * Tests for the CO2 viscosity model coupled with the Span–Wagner EOS over a
 * wide range of conditions.
 */
public class CO2ViscosityMethodTest {

  @Test
  void testViscosityAcrossConditions() {
    double[][] states = {
        {300.0, 1.0, 1.5003069868799645E-5},
        {300.0, 10.0, 1.51080385834066E-5},
        {400.0, 50.0, 2.059452549395974E-5},
        {220.0, 20.0, 2.4218888131055303E-4},
        {250.0, 50.0, 1.5336195913344397E-4},
        {260.0, 100.0, 1.401607589154972E-4},
    };
    for (double[] st : states) {
      SystemSpanWagnerEos system = new SystemSpanWagnerEos(st[0], st[1]);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initPhysicalProperties();
      assertTrue(system.getPhase(0).getPhysicalProperties().getViscosityModel()
          instanceof CO2ViscosityMethod);
      assertEquals(st[2],
          system.getPhase(0).getPhysicalProperties().getViscosity(), 1e-5);
    }
  }
}
