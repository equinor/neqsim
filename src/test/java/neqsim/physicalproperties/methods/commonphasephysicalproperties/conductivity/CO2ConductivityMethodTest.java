package neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.system.SystemSpanWagnerEos;

/**
 * Tests for the CO2 thermal conductivity model coupled with the Span–Wagner EOS over a wide range
 * of conditions.
 */
public class CO2ConductivityMethodTest {

  @Test
  void testConductivityAcrossConditions() {
    // Expected values validated against NIST WebBook reference data
    // NIST uses Huber et al. (JPCRD 2016) thermal conductivity correlation
    double[][] states = {{300.0, 1.0, 0.016773682981674743}, {300.0, 10.0, 0.017334537751875614},
        {400.0, 50.0, 0.027437895523946265}, {220.0, 20.0, 0.17401034581268648}, // NIST: 0.17407
                                                                                 // W/m.K (liquid)
        {250.0, 50.0, 0.14025257694726223}, {260.0, 100.0, 0.13423766706020518},}; // NIST: 0.14026
                                                                                   // W/m.K (liquid)
    for (double[] st : states) {
      SystemSpanWagnerEos system = new SystemSpanWagnerEos(st[0], st[1]);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();
      system.initPhysicalProperties();
      assertTrue(system.getPhase(0).getPhysicalProperties()
          .getConductivityModel() instanceof CO2ConductivityMethod);
      assertEquals(st[2], system.getPhase(0).getPhysicalProperties().getConductivity(), 5e-4);
    }
  }
}
