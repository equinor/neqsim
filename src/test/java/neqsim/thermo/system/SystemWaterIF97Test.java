package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import neqsim.thermo.util.steam.Iapws_if97;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.junit.jupiter.api.Test;

/**
 * Basic regression test for the water IAPWS-IF97 system implementation.
 */
public class SystemWaterIF97Test {

  @Test
  public void testPureWaterProperties() {
    SystemInterface sys = new SystemWaterIF97(373.15, 1.0);
    sys.init(0);
    sys.init(1);
    sys.init(2);
    new ThermodynamicOperations(sys).TPflash();

    double pMPa = 0.1; // 1 bar
    double molarMass = sys.getPhase(0).getComponent(0).getMolarMass();

    double expectedVm = Iapws_if97.v_pt(pMPa, 373.15) * molarMass;
    assertEquals(expectedVm, sys.getPhase(0).getMolarVolume(), 1e-8, "molar volume");

    double expectedH = Iapws_if97.h_pt(pMPa, 373.15) * molarMass * 1e3;
    assertEquals(expectedH, sys.getPhase(0).getEnthalpy(), 1e-4, "enthalpy");
  }

  @Test
  public void testTpFlashPhaseAndProperties() {
    SystemInterface gas = new SystemWaterIF97(298.15, 1.0);
    gas.init(0);
    gas.init(1);
    gas.init(2);
    gas.setTemperature(400.0);
    gas.setPressure(1.0);
    ThermodynamicOperations opsGas = new ThermodynamicOperations(gas);
    opsGas.TPflash();
    assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
    double molarMassG = gas.getPhase(0).getComponent(0).getMolarMass();
    double expectedVmG = Iapws_if97.v_pt(0.1, 400.0) * molarMassG;
    assertEquals(expectedVmG, gas.getPhase(0).getMolarVolume(), 1e-8, "molar volume");
    double expectedHG = Iapws_if97.h_pt(0.1, 400.0) * molarMassG * 1e3;
    assertEquals(expectedHG, gas.getPhase(0).getEnthalpy(), 1e-4, "enthalpy");
    double expectedSG = Iapws_if97.s_pt(0.1, 400.0) * molarMassG * 1e3;
    assertEquals(expectedSG, gas.getPhase(0).getEntropy(), 1e-4, "entropy");
    double expectedCpG = Iapws_if97.cp_pt(0.1, 400.0) * molarMassG * 1e3;
    assertEquals(expectedCpG, gas.getPhase(0).getCp(), 1e-4, "cp");

    SystemInterface liq = new SystemWaterIF97(298.15, 1.0);
    liq.init(0);
    liq.init(1);
    liq.init(2);
    liq.setTemperature(300.0);
    liq.setPressure(1.0);
    ThermodynamicOperations opsLiq = new ThermodynamicOperations(liq);
    opsLiq.TPflash();
    assertEquals(PhaseType.AQUEOUS, liq.getPhase(0).getType());
    double molarMassL = liq.getPhase(0).getComponent(0).getMolarMass();
    double expectedVmL = Iapws_if97.v_pt(0.1, 300.0) * molarMassL;
    assertEquals(expectedVmL, liq.getPhase(0).getMolarVolume(), 1e-8, "molar volume");
    double expectedHL = Iapws_if97.h_pt(0.1, 300.0) * molarMassL * 1e3;
    assertEquals(expectedHL, liq.getPhase(0).getEnthalpy(), 1e-4, "enthalpy");
    double expectedSL = Iapws_if97.s_pt(0.1, 300.0) * molarMassL * 1e3;
    assertEquals(expectedSL, liq.getPhase(0).getEntropy(), 1e-4, "entropy");
    double expectedCpL = Iapws_if97.cp_pt(0.1, 300.0) * molarMassL * 1e3;
    assertEquals(expectedCpL, liq.getPhase(0).getCp(), 1e-4, "cp");
  }

  @Test
  public void testPhFlashPhase() {
    SystemInterface gas = new SystemWaterIF97(400.0, 1.0);
    gas.init(0);
    gas.init(1);
    gas.init(2);
    double molarMassG = gas.getPhase(0).getComponent(0).getMolarMass();
    double hGas = Iapws_if97.h_pt(0.1, 400.0) * molarMassG * 1e3;

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.PHflash2(hGas, 0);
    assertEquals(PhaseType.GAS, gas.getPhase(0).getType());
    assertEquals(400.0, gas.getTemperature(), 1e-6);

    SystemInterface liq = new SystemWaterIF97(300.0, 1.0);
    liq.init(0);
    liq.init(1);
    liq.init(2);
    double molarMassL = liq.getPhase(0).getComponent(0).getMolarMass();
    double hLiq = Iapws_if97.h_pt(0.1, 300.0) * molarMassL * 1e3;

    ThermodynamicOperations ops2 = new ThermodynamicOperations(liq);
    ops2.PHflash2(hLiq, 0);
    assertEquals(PhaseType.AQUEOUS, liq.getPhase(0).getType());
    assertEquals(300.0, liq.getTemperature(), 1e-6);
  }
}
