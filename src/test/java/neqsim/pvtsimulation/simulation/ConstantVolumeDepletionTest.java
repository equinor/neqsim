package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    CVDsim.setPressures(new double[] {400, 300.0, 200.0, 150.0, 100.0, 50.0});
    CVDsim.runCalc();
    CVDsim.setTemperaturesAndPressures(new double[] {313, 313, 313, 313},
        new double[] {400, 300.0, 200.0, 100.0});
    double[][] expData = {{0.95, 0.99, 1.0, 1.1}};
    CVDsim.setExperimentalData(expData);
    assertEquals(2.198101313307075, CVDsim.getRelativeVolume()[4], 0.001);
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
    CVDsim.setPressures(
        new double[] {200.0, 154.0, 139.0, 127.0, 117.0, 108.0, 91.0, 82.0, 62.0, 38.0});
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
