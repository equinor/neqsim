package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * No-fit characterization of the MM hybrid against primary aqueous NaCl data.
 *
 * <p>
 * Hamer and Wu, J. Phys. Chem. Ref. Data 1 (1972), doi:10.1063/1.3253108, report molality-scale mean ionic activity
 * coefficients and osmotic coefficients at 298.15 K. The 5% activity and 2% osmotic envelopes below are engineering
 * acceptance bounds for NaCl from 0.001 to 3 mol/kg at 1 bar, not experimental uncertainties. They do not establish
 * comparable accuracy for other salts or for a full published MM e-CPA model. No model parameters were fitted to these
 * points in the common-permittivity correction.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMPrimaryDataTest {
  /** Checks the declared NaCl scope with explicit molality normalization and pressure closure. */
  @Test
  void sodiumChlorideMolalityDataStayWithinDeclaredEngineeringEnvelope() {
    double[] molality = {0.001, 0.01, 0.1, 0.5, 1, 2, 3};
    double[] gamma = {0.965, 0.903, 0.779, 0.681, 0.657, 0.668, 0.714};
    double[] osmotic = {0.988, 0.968, 0.933, 0.921, 0.936, 0.984, 1.045};
    for (int i = 0; i < molality.length; i++) {
      SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(298.15, 1);
      fluid.addComponent("water", 55.508);
      fluid.addComponent("Na+", molality[i]);
      fluid.addComponent("Cl-", molality[i]);
      fluid.setMixingRule(10);
      fluid.init(0);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(3);
      assertEquals(1, fluid.getNumberOfPhases(), "NaCl molality " + molality[i]);
      PhaseInterface phase = fluid.getPhase(0);
      double predictedGamma = phase.getMolalMeanIonicActivity(1, 2);
      double predictedOsmotic = phase.getOsmoticCoefficientOfWater();
      assertTrue(Math.abs(predictedGamma / gamma[i] - 1) <= 0.05,
          "Declared 5% gamma_m engineering envelope at molality " + molality[i]);
      assertTrue(Math.abs(predictedOsmotic / osmotic[i] - 1) <= 0.02,
          "Declared 2% osmotic engineering envelope at molality " + molality[i]);
      assertEquals(1, ((PhaseEos) phase).calcPressure(), 1e-8);
    }
  }
}
