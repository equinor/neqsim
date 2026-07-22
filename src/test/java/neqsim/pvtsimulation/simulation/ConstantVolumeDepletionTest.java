package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ConstantVolumeDepletionTest {
  @Test
  void testRunCalc() {
    SystemInterface tempSystem = new SystemSrkEos(298.0, 211.0);
    tempSystem.addComponent("nitrogen", 0.34);
    tempSystem.addComponent("CO2", 3.59);
    tempSystem.addComponent("methane", 67.42);
    tempSystem.addComponent("ethane", 9.02);
    tempSystem.addComponent("propane", 4.31);
    tempSystem.addComponent("i-butane", 0.93);
    tempSystem.addComponent("n-butane", 1.71);
    tempSystem.addComponent("i-pentane", 0.74);
    tempSystem.addComponent("n-pentane", 0.85);
    tempSystem.addComponent("n-hexane", 1.38);
    tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
    tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
    tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
    tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
    tempSystem.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398);
    // tempSystem.getCharacterization().characterisePlusFraction();
    tempSystem.createDatabase(true);
    tempSystem.setMixingRule(2);
    tempSystem.init(0);
    tempSystem.init(1);

    ConstantVolumeDepletion CVDsim = new ConstantVolumeDepletion(tempSystem);
    CVDsim.setTemperature(315.0);
    CVDsim.setPressures(new double[] { 400, 300.0, 200.0, 150.0, 100.0, 50.0 });
    CVDsim.runCalc();
    CVDsim.setTemperaturesAndPressures(new double[] { 313, 313, 313, 313 }, new double[] { 400, 300.0, 200.0, 100.0 });
    double[][] expData = { { 0.95, 0.99, 1.0, 1.1 } };
    CVDsim.setExperimentalData(expData);
    assertEquals(1.0, CVDsim.getRelativeVolume()[4], 1.0e-6);
    assertTrue(CVDsim.validateMaterialBalance(0.02));
  }

  @Test
  void testCondensateMaintainsCellVolumeAndMaterialBalance() {
    SystemInterface condensate = new SystemSrkEos(353.15, 200.0);
    condensate.addComponent("nitrogen", 0.010);
    condensate.addComponent("CO2", 0.020);
    condensate.addComponent("methane", 0.760);
    condensate.addComponent("ethane", 0.080);
    condensate.addComponent("propane", 0.050);
    condensate.addComponent("i-butane", 0.015);
    condensate.addComponent("n-butane", 0.020);
    condensate.addComponent("i-pentane", 0.010);
    condensate.addComponent("n-pentane", 0.010);
    condensate.addComponent("n-hexane", 0.010);
    condensate.addComponent("n-heptane", 0.008);
    condensate.addComponent("n-octane", 0.007);
    condensate.setMixingRule("classic");
    condensate.setMultiPhaseCheck(true);
    condensate.init(0);
    condensate.init(1);

    SaturationPressure saturationReference = new SaturationPressure(condensate.clone());
    saturationReference.setTemperature(80.0, "C");
    saturationReference.run();

    ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(condensate);
    cvd.setTemperature(80.0, "C");
    cvd.setPressures(new double[] { 120.0, 100.0, 80.0, 60.0, 40.0, 20.0 });
    cvd.runCalc();

    assertEquals(saturationReference.getSaturationPressure(), cvd.getSaturationPressure(), 0.05);

    double[] depletion = cvd.getCummulativeMolePercDepleted();
    double[] relativeVolume = cvd.getRelativeVolume();
    double maximumLiquidDropout = 0.0;
    for (int i = 0; i < depletion.length; i++) {
      assertTrue(depletion[i] >= -1.0e-8);
      assertTrue(depletion[i] < 99.0);
      if (i > 0) {
        assertTrue(depletion[i] >= depletion[i - 1] - 1.0e-8);
      }
      if (depletion[i] > 1.0e-8) {
        assertEquals(1.0, relativeVolume[i], 1.0e-6);
      }
      maximumLiquidDropout = Math.max(maximumLiquidDropout, cvd.getLiquidRelativeVolume()[i]);
    }
    assertTrue(maximumLiquidDropout > 0.5);
  }

  @Test
  void testRunEclipseInput() {
    File file = new File("src/test/java/neqsim/pvtsimulation/simulation");
    String fileFluid1 = file.getAbsolutePath() + "/EclipseModel.e300";
    SystemInterface fluid1 = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);
    // TODO: check why not working with multiphase
    // fluid1.setMultiPhaseCheck(true);
    fluid1.setTemperature(90.0, "C");

    SaturationPressure satPres = new SaturationPressure(fluid1);
    satPres.run();
    assertEquals(199.45807075, fluid1.getPressure("bara"), 0.01);

    ConstantVolumeDepletion CVDsim = new ConstantVolumeDepletion(fluid1);
    CVDsim.setTemperature(90.0, "C");
    CVDsim.setPressures(new double[] { 200.0, 154.0, 139.0, 127.0, 117.0, 108.0, 91.0, 82.0, 62.0, 38.0 });
    CVDsim.runCalc();
    CVDsim.getThermoSystem().initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double gasdens = CVDsim.getThermoSystem().getPhase("gas").getDensity("kg/m3");
    double oildens = CVDsim.getThermoSystem().getPhase("oil").getDensity("kg/m3");

    SystemInterface gasFluid = CVDsim.getThermoSystem().phaseToSystem("gas");
    gasFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    assertEquals(gasdens, gasFluid.getDensity("kg/m3"), 0.01);

    SystemInterface oilFluid = CVDsim.getThermoSystem().phaseToSystem("oil");
    ThermodynamicOperations ops = new ThermodynamicOperations(oilFluid);
    ops.TPflash();
    oilFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    assertEquals(oildens, oilFluid.getDensity("kg/m3"), 0.1);
  }
}
