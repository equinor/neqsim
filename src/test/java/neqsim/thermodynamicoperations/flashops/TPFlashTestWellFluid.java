package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class TPFlashTestWellFluid {
  static neqsim.thermo.system.SystemInterface wellFluid = null;
  static ThermodynamicOperations testOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp() throws Exception {
    wellFluid = new neqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 30.0, 65.00);
    wellFluid.addComponent("oxygen", 0.0);
    wellFluid.addComponent("H2S", 0.00008);
    wellFluid.addComponent("nitrogen", 0.08);
    wellFluid.addComponent("CO2", 3.56);
    wellFluid.addComponent("methane", 87.36);
    wellFluid.addComponent("ethane", 4.02);
    wellFluid.addComponent("propane", 1.54);
    wellFluid.addComponent("i-butane", 0.2);
    wellFluid.addComponent("n-butane", 0.42);
    wellFluid.addComponent("i-pentane", 0.15);
    wellFluid.addComponent("n-pentane", 0.20);

    wellFluid.addTBPfraction("C6_Frigg", 0.24, 84.99 / 1000.0, 695.0 / 1000.0);
    wellFluid.addTBPfraction("C7_Frigg", 0.34, 97.87 / 1000.0, 718.0 / 1000.0);
    wellFluid.addTBPfraction("C8_Frigg", 0.33, 111.54 / 1000.0, 729.0 / 1000.0);
    wellFluid.addTBPfraction("C9_Frigg", 0.19, 126.1 / 1000.0, 749.0 / 1000.0);
    wellFluid.addTBPfraction("C10_Frigg", 0.15, 140.14 / 1000.0, 760.0 / 1000.0);
    wellFluid.addTBPfraction("C11_Frigg", 0.69, 175.0 / 1000.0, 830.0 / 1000.0);
    wellFluid.addTBPfraction("C12_Frigg", 0.5, 280.0 / 1000.0, 914.0 / 1000.0);
    wellFluid.addTBPfraction("C13_Frigg", 0.103, 560.0 / 1000.0, 980.0 / 1000.0);

    wellFluid.addTBPfraction("C6_ML_WestCtrl", 0.0, 84.0 / 1000.0, 684.0 / 1000.0);
    wellFluid.addTBPfraction("C7_ML_WestCtrl", 0.0, 97.9 / 1000.0, 742.0 / 1000.0);
    wellFluid.addTBPfraction("C8_ML_WestCtrl", 0.0, 111.5 / 1000.0, 770.0 / 1000.0);
    wellFluid.addTBPfraction("C9_ML_WestCtrl", 0.0, 126.1 / 1000.0, 790.0 / 1000.0);
    wellFluid.addTBPfraction("C10_ML_WestCtrl", 0.0, 140.14 / 1000.0, 805.0 / 1000.0);
    wellFluid.addTBPfraction("C11_ML_WestCtrl", 0.0, 175.0 / 1000.0, 815.0 / 1000.0);
    wellFluid.addTBPfraction("C12_ML_WestCtrl", 0.0, 280.0 / 1000.0, 835.0 / 1000.0);
    wellFluid.addTBPfraction("C13_ML_WestCtrl", 0.0, 450.0 / 1000.0, 850.0 / 1000.0);
    wellFluid.addComponent("water", 12.01);
    wellFluid.setMixingRule(10);
    wellFluid.init(0);
    wellFluid.setMultiPhaseCheck(true);
  }

  @Test
  void testTPflashComp1() {
    testOps = new ThermodynamicOperations(wellFluid);
    testOps.TPflash();
  }

  @Test
  void testTPflashComp2() {
    wellFluid.setTemperature(339.04);
    wellFluid.setPressure(1.5);
    wellFluid.setMolarComposition(new double[] {0.0, 4.76579e-6, 1.21459e-5, 1.3409e-3, 3.30439e-2,
        5.06e-3, 7.34e-3, 1.53e-3, 4.11e-3, 1.58e-3, 2.255e-3, 2.8779e-4, 8.58e-4, 8.73e-4, 8.5e-4,
        3.88e-3, 7.36e-2, 1.47e-1, 6.176e-2, 3.69e-2, 7.735e-3, 1.023e-2, 6.19e-3, 4.3e-3, 1.2e-2,
        8.96e-3, 1.539e-3, 5.9921e-1});
    testOps = new ThermodynamicOperations(wellFluid);
    testOps.TPflash();
    assertEquals(1.432253736300898
    , wellFluid.getPhase(0).getDensity(), 1e-5);
  }
}
