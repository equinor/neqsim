package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Checks the separate Born and hard-sphere ion diameters in Solbraa (2002), equation (C.10).
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteBornDiameterTest extends neqsim.NeqSimTest implements ThermodynamicConstantsInterface {
  /**
   * Compares the implemented Born energy and mole-size factors with the published characteristic-diameter formula.
   *
   * @param association whether to use electrolyte CPA
   * @param cation cation name
   */
  @ParameterizedTest
  @CsvSource({"false,Na+", "true,Na+", "false,K+", "true,K+", "false,Ca++", "true,Ca++"})
  void bornUsesCharacteristicDiameterSeparatelyFromHardSphereDiameter(boolean association, String cation) {
    SystemInterface fluid = association ? new SystemElectrolyteCPAstatoil(298.15, 50.0)
        : new SystemFurstElectrolyteEos(298.15, 50.0);
    fluid.addComponent("methane", 0.002);
    fluid.addComponent("water", 1.0);
    fluid.addComponent(cation, 0.018);
    double charge = fluid.getComponent(cation).getIonicCharge();
    fluid.addComponent("Cl-", 0.018 * charge);
    fluid.setMixingRule(association ? 10 : 2);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(3);
    PhaseModifiedFurstElectrolyteEos phase = (PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0);
    double expectedChargeSize = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentModifiedFurstElectrolyteEos component = (ComponentModifiedFurstElectrolyteEos) phase.getComponent(i);
      double z = component.getIonicCharge();
      double diameter = z > 0.0 ? component.getStokesCationicDiameter() : component.getPaulingAnionicDiameter();
      double expectedDerivative = z == 0.0 ? 0.0 : z * z / (diameter * 1e-10);
      assertEquals(expectedDerivative, component.getXBorni(), Math.max(1.0, expectedDerivative) * 1e-12);
      expectedChargeSize += component.getNumberOfMolesInPhase() * expectedDerivative;
      if (z != 0.0) {
        double hardSphere = Math.cbrt(6.0 * component.getb() * 1e-5 / (pi * avagadroNumber)) * 1e10;
        assertEquals(hardSphere, component.getLennardJonesMolecularDiameter(), hardSphere * 1e-12);
      }
    }
    assertEquals(expectedChargeSize, phase.calcBornX(), expectedChargeSize * 1e-12);
    double expectedEnergy = avagadroNumber * electronCharge * electronCharge
        / (4.0 * pi * vacumPermittivity * R * phase.getTemperature())
        * (1.0 / phase.getSolventDiElectricConstant() - 1.0) * expectedChargeSize;
    assertEquals(expectedEnergy, phase.FBorn(), Math.abs(expectedEnergy) * 1e-12);
  }
}
