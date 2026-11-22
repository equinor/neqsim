package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Internal PVT regression and conservation checks for a characterized oil.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DifferentialLiberationTest {
  /** Verifies liberation curves after a mole- and mass-conserving Pedersen split. */
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(273.15 + 83.5, 350.0);
    tempSystem.addComponent("nitrogen", 0.39);
    tempSystem.addComponent("CO2", 0.3);
    tempSystem.addComponent("methane", 40.2);
    tempSystem.addComponent("ethane", 7.61);
    tempSystem.addComponent("propane", 7.95);
    tempSystem.addComponent("i-butane", 1.19);
    tempSystem.addComponent("n-butane", 4.08);
    tempSystem.addComponent("i-pentane", 1.39);
    tempSystem.addComponent("n-pentane", 2.15);
    tempSystem.addComponent("n-hexane", 2.79);
    tempSystem.addTBPfraction("C7", 4.28, 95 / 1000.0, 0.729);
    tempSystem.addTBPfraction("C8", 4.31, 106 / 1000.0, 0.749);
    tempSystem.addTBPfraction("C9", 3.08, 121 / 1000.0, 0.77);
    tempSystem.addTBPfraction("C10", 2.47, 135 / 1000.0, 0.786);
    tempSystem.addTBPfraction("C11", 1.91, 148 / 1000.0, 0.792);
    tempSystem.addTBPfraction("C12", 1.69, 161 / 1000.0, 0.804);
    tempSystem.addTBPfraction("C13", 1.59, 175 / 1000.0, 0.819);
    tempSystem.addTBPfraction("C14", 1.22, 196 / 1000.0, 0.833);
    tempSystem.addTBPfraction("C15", 1.25, 206 / 1000.0, 0.836);
    tempSystem.addTBPfraction("C16", 1.0, 225 / 1000.0, 0.843);
    tempSystem.addTBPfraction("C17", 0.99, 236 / 1000.0, 0.840);
    tempSystem.addTBPfraction("C18", 0.92, 245 / 1000.0, 0.846);
    tempSystem.addTBPfraction("C19", 0.6, 265 / 1000.0, 0.857);
    tempSystem.addPlusFraction("C20", 6.64, 453 / 1000.0, 0.918);
    double inputMoles = tempSystem.getTotalNumberOfMoles();
    double inputMass = componentMass(tempSystem);
    tempSystem.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(12);
    tempSystem.getCharacterization().characterisePlusFraction();
    assertEquals(inputMoles, tempSystem.getTotalNumberOfMoles(), inputMoles * 1e-11);
    assertEquals(inputMass, componentMass(tempSystem), inputMass * 1e-11);
    tempSystem.setMixingRule("classic");

    SimulationInterface satPresSim = new SaturationPressure(tempSystem);
    satPresSim.setTemperature(97.5, "C");
    satPresSim.run();
    // Internal references for the corrected plus-fraction moments, not experimental PVT data.
    assertEquals(193.28456497192383, satPresSim.getThermoSystem().getPressure(), 0.1);
    // tempSystem.prettyPrint();

    double[] pressures = new double[] {351.4, 323.2, 301.5, 275.9, 250.1, 226.1, 205.9, 179.1, 154.6, 132.1, 109.0,
        78.6, 53.6, 22.0, 1.0};
    DifferentialLiberation differentialLiberation = new DifferentialLiberation(tempSystem);
    differentialLiberation.setPressures(pressures);
    differentialLiberation.setTemperature(97.5, "C");
    differentialLiberation.runCalc();

    assertEquals(1.690017599362585, differentialLiberation.getBo()[0], 0.001);
    // Rs uses the Z = 1 standard-gas convention (Pedersen et al. 2015, Eq. 3.7), see #5195.
    assertEquals(213.65948266944534, differentialLiberation.getRs()[0], 0.001);
    assertEquals(677.684004582184, differentialLiberation.getOilDensity()[0], 0.001);
    assertEquals(1.7613484588308035, differentialLiberation.getBo()[pressures.length - 9], 0.001);
    assertEquals(1.3109612423374373, differentialLiberation.getBo()[pressures.length - 2], 0.001);
    assertEquals(55.08653610241253, differentialLiberation.getRs()[pressures.length - 2], 0.001);
    assertEquals(0.05559782268972172, differentialLiberation.getBg()[pressures.length - 2], 0.001);
    assertEquals(1.0525713881601992, differentialLiberation.getBo()[pressures.length - 1], 0.001);
    assertEquals(0.0, differentialLiberation.getRs()[pressures.length - 1], 0.001);
    assertEquals(805.7132237781785, differentialLiberation.getOilDensity()[pressures.length - 1], 0.001);
    assertTrue(differentialLiberation.validateBoMonotonicity());
    assertTrue(differentialLiberation.validateRsMonotonicity());
    assertTrue(differentialLiberation.validateOilDensityMonotonicity());
  }

  /**
   * Sums the feed component mass independently of cached mixture properties.
   *
   * @param fluid fluid whose component records carry the overall inventories
   * @return component mass in kg
   */
  private static double componentMass(SystemInterface fluid) {
    double mass = 0.0;
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      mass += fluid.getPhase(0).getComponent(component).getNumberOfmoles()
          * fluid.getPhase(0).getComponent(component).getMolarMass();
    }
    return mass;
  }

  /**
   * The saturation volume must be the volume of the saturated cell, not of a trace incipient phase left at the last
   * bisection midpoint (#5209, reported by Patrick Mockridge). Before the fix the saturation volume was 7.0e-10 m3 and
   * the relative volume at 5 bara was about 2.4e5.
   */
  @Test
  void testTracePhaseAtSaturationDoesNotDefineSaturationVolume() {
    DifferentialLiberation dle = new DifferentialLiberation(methaneDecane());
    dle.setTemperature(350.0);
    double[] pressures = {300, 250, 200, 150, 100, 50, 20, 5};
    dle.setPressures(pressures);
    dle.runCalc();

    SystemInterface saturated = methaneDecane();
    saturated.setTemperature(350.0);
    saturated.setPressure(dle.getSaturationPressure());
    new ThermodynamicOperations(saturated).TPflash();
    assertEquals(1, saturated.getNumberOfPhases(), "saturation state is single phase");
    saturated.initPhysicalProperties();
    assertEquals(saturated.getCorrectedVolume(), dle.saturationVolume, saturated.getCorrectedVolume() * 1e-6);
    assertTrue(dle.saturationVolume > 5e-5 && dle.saturationVolume < 5e-4, "saturation volume of order 1e-4 m3");
    for (int i = 0; i < pressures.length; i++) {
      double relativeVolume = dle.getRelativeVolume()[i];
      assertTrue(Double.isFinite(relativeVolume) && relativeVolume > 0.5 && relativeVolume < 5.0,
          "relative volume of order one at " + pressures[i] + " bara: " + relativeVolume);
    }
    assertEquals(1.0, dle.getRelativeVolume()[0], 0.01);
  }

  /**
   * Builds the methane/n-decane binary of issue #5209.
   *
   * @return a new SRK fluid with mixing rule 2
   */
  private static SystemInterface methaneDecane() {
    SystemInterface fluid = new SystemSrkEos(350.0, 300.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("nC10", 0.30);
    fluid.setMixingRule(2);
    return fluid;
  }
}
