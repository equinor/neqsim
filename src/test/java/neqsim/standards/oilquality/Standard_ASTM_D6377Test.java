package neqsim.standards.oilquality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class Standard_ASTM_D6377Test {
  @Test
  void testCalculate() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.06538);
    testSystem.addComponent("n-pentane", 0.1545);
    testSystem.addComponent("nC10", 0.545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(1.10445689545, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(1.666298367, standard.getValue("TVP", "bara"), 1e-3);
  }

  @Test
  void testCalculate2() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.026538);
    testSystem.addComponent("ethane", 0.16538);
    testSystem.addComponent("propane", 0.26538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.addComponent("nC10", 0.545);
    testSystem.addTBPfraction("C11", 0.545, 145.0 / 1000.0, 0.82);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.setPressure(100.0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setMethodRVP("VPCR4");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(3.61452722, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(7.867696779327479, standard.getValue("TVP", "bara"), 1e-3);

    standard.setMethodRVP("RVP_ASTM_D6377");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(3.01451570813, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(7.867696779327479, standard.getValue("TVP", "bara"), 1e-3);
  }

  @Test
  void testCalculate3() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.addComponent("nC10", 0.545);
    testSystem.addComponent("nC12", 0.545);
    testSystem.addTBPfraction("C11", 0.545, 145.0 / 1000.0, 0.82);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.setPressure(100.0);
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setMethodRVP("VPCR4");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(0.25505060, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(0.261765909821, standard.getValue("TVP", "bara"), 1e-3);

    standard.setMethodRVP("RVP_ASTM_D6377");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(0.2127122042, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(0.2617659098, standard.getValue("TVP", "bara"), 1e-3);
  }
  @Test
  void testWaterFreeRvpUsesWaterFreeFluid() throws Exception {
    SystemInterface testSystem = createWaterBearingOil();
    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();

    standard.setMethodRVP("VPCR4");
    double vpcr4WithWater = standard.getValue("RVP", "bara");
    standard.setMethodRVP("VPCR4_no_water");
    double vpcr4NoWater = standard.getValue("RVP", "bara");

    Assertions.assertNotEquals(vpcr4WithWater, vpcr4NoWater, 1e-8);
    Assertions.assertEquals(calculateIndependentWaterFreeVpcr4(createWaterBearingOil()), vpcr4NoWater,
        1e-8);
  }

  @Test
  void testWaterFreeRvpMatchesVpcr4WhenFluidHasNoWater() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.006538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    Standard_ASTM_D6377 standard = new Standard_ASTM_D6377(testSystem);
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();

    standard.setMethodRVP("VPCR4");
    double vpcr4 = standard.getValue("RVP", "bara");
    standard.setMethodRVP("VPCR4_no_water");
    double vpcr4NoWater = standard.getValue("RVP", "bara");

    Assertions.assertEquals(vpcr4, vpcr4NoWater, 1e-8);
  }

  private SystemInterface createWaterBearingOil() {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 2.0, 1.0);
    testSystem.addComponent("methane", 0.0006538);
    testSystem.addComponent("ethane", 0.006538);
    testSystem.addComponent("propane", 0.006538);
    testSystem.addComponent("n-pentane", 0.545);
    testSystem.addComponent("water", 0.00545);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    return testSystem;
  }

  private double calculateIndependentWaterFreeVpcr4(SystemInterface fluid) throws Exception {
    SystemInterface waterFreeFluid = fluid.clone();
    waterFreeFluid.removeComponent("water");
    waterFreeFluid.setTemperature(37.8, "C");
    waterFreeFluid.setPressure(ThermodynamicConstantsInterface.referencePressure);
    waterFreeFluid.init(0);

    ThermodynamicOperations waterFreeOps = new ThermodynamicOperations(waterFreeFluid);
    waterFreeOps.bubblePointPressureFlash(false);
    waterFreeFluid.setPressure(waterFreeFluid.getPressure() * 0.9);
    waterFreeOps.TVfractionFlash(0.8);
    return waterFreeFluid.getPressure();
  }

}
