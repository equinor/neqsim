package neqsim.standards.oilquality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
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
    Assertions.assertEquals(1.1574422523131047, standard.getValue("RVP", "bara"), 1e-3);
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
    Assertions.assertEquals(3.8285556883808334, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(7.867696779327479, standard.getValue("TVP", "bara"), 1e-3);

    standard.setMethodRVP("RVP_ASTM_D6377");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(3.1930154441, standard.getValue("RVP", "bara"), 1e-3);
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
    Assertions.assertEquals(0.25830597077793843, standard.getValue("RVP", "bara"), 1e-3);
    Assertions.assertEquals(0.261765909821, standard.getValue("TVP", "bara"), 1e-3);

    standard.setMethodRVP("RVP_ASTM_D6377");
    standard.setReferenceTemperature(37.8, "C");
    standard.calculate();
    Assertions.assertEquals(0.2154271776, standard.getValue("RVP", "bara"), 1e-3);
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

    Assertions.assertNotEquals(vpcr4WithWater, vpcr4NoWater, 1e-6);
    Assertions.assertEquals(calculateIndependentWaterFreeVpcr4(createWaterBearingOil()), vpcr4NoWater, 1e-6);
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

    Assertions.assertEquals(vpcr4, vpcr4NoWater, 1e-6);
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

  @Test
  void testRvpMethodEnumMatchesStringApi() {
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

    // The enum setter and the legacy string setter must agree.
    standard.setMethodRVP(Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377);
    double viaEnum = standard.getValue("RVP", "bara");
    standard.setMethodRVP("RVP_ASTM_D6377");
    double viaString = standard.getValue("RVP", "bara");
    Assertions.assertEquals(viaString, viaEnum, 1e-9);

    // fromLabel resolves both the legacy label and the enum name.
    Assertions.assertEquals(Standard_ASTM_D6377.RvpMethod.VPCR4, Standard_ASTM_D6377.RvpMethod.fromLabel("VPCR4"));
    Assertions.assertEquals(Standard_ASTM_D6377.RvpMethod.VPCR4_NO_WATER,
        Standard_ASTM_D6377.RvpMethod.fromLabel("VPCR4_no_water"));
    Assertions.assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        Standard_ASTM_D6377.RvpMethod.fromLabel("not_a_method");
      }
    });
  }

  @Test
  void testStructuredRvpResult() {
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
    standard.setMethodRVP(Standard_ASTM_D6377.RvpMethod.VPCR4);
    standard.calculate();

    Standard_ASTM_D6377.RvpResult result = standard.getRvpResult();
    Assertions.assertTrue(result.isValid(), "Result should be valid for a normal oil");
    Assertions.assertEquals(Standard_ASTM_D6377.RvpMethod.VPCR4, result.getMethod());
    Assertions.assertEquals(37.8, result.getReferenceTemperatureC(), 1e-9);
    Assertions.assertEquals(standard.getValue("RVP", "bara"), result.getValue(), 1e-9);

    // JSON serializes and carries the expected fields.
    String json = result.toJson();
    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    Assertions.assertEquals("VPCR4", obj.get("method").getAsString());
    Assertions.assertEquals("bara", obj.get("unit").getAsString());
    Assertions.assertTrue(obj.get("valid").getAsBoolean());

    // Specific-method overload returns each populated value without recalculating.
    Standard_ASTM_D6377.RvpResult d6377 = standard.getRvpResult(Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377);
    Assertions.assertEquals(Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377, d6377.getMethod());
  }

}
