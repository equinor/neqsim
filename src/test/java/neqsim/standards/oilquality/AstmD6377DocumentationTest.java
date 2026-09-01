package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpMethod;
import neqsim.standards.oilquality.Standard_ASTM_D6377.RvpResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executes and verifies the ASTM D6377 vapor-pressure documentation workflow.
 *
 * @author ESOL
 * @version 1.0
 */
class AstmD6377DocumentationTest extends NeqSimTest {
  @Test
  void documentedTypedWorkflowProducesTraceableResultsAndPreservesSourceState() {
    SystemInterface sourceOil = createOil();
    double sourceTemperatureK = sourceOil.getTemperature();
    double sourcePressureBara = sourceOil.getPressure();

    SystemInterface workingFluid = sourceOil.clone();
    Standard_ASTM_D6377 vaporPressure = new Standard_ASTM_D6377(workingFluid);
    vaporPressure.setReferenceTemperature(37.8, "C");
    vaporPressure.setMethodRVP(RvpMethod.RVP_ASTM_D6377);
    vaporPressure.calculate();

    RvpResult selected = vaporPressure.getRvpResult();
    RvpResult vpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4);
    RvpResult dryVpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER);
    double tvpBara = vaporPressure.getValue("TVP", "bara");
    double selectedRvpKPa = vaporPressure.getValue("RVP", "kPa");

    assertTrue(selected.isValid());
    assertTrue(vpcr4.isValid());
    assertTrue(dryVpcr4.isValid());
    assertEquals(RvpMethod.RVP_ASTM_D6377, selected.getMethod());
    assertEquals("RVP_ASTM_D6377", vaporPressure.getMethodRVP());
    assertEquals(37.8, selected.getReferenceTemperatureC(), 1.0e-12);
    assertEquals(0.9653068384, selected.getValue(), 1.0e-3);
    assertEquals(1.1574422523, vpcr4.getValue(), 1.0e-3);
    assertEquals(vpcr4.getValue(), dryVpcr4.getValue(), 1.0e-6);
    assertEquals(1.6662983670, tvpBara, 1.0e-3);
    assertEquals(selected.getValue() * 100.0, selectedRvpKPa, 1.0e-6);
    assertTrue(tvpBara > vpcr4.getValue());
    assertTrue(vpcr4.getValue() > selected.getValue());

    JsonObject json = JsonParser.parseString(selected.toJson()).getAsJsonObject();
    assertEquals("RVP_ASTM_D6377", json.get("method").getAsString());
    assertEquals("bara", json.get("unit").getAsString());
    assertTrue(json.get("valid").getAsBoolean());

    assertEquals(sourceTemperatureK, sourceOil.getTemperature(), 0.0);
    assertEquals(sourcePressureBara, sourceOil.getPressure(), 0.0);
  }

  @Test
  void documentedWaterFreeComparisonRespondsToWater() {
    SystemInterface wetOil = createOil();
    wetOil.addComponent("water", 0.00545);
    wetOil.init(0);

    Standard_ASTM_D6377 vaporPressure = new Standard_ASTM_D6377(wetOil.clone());
    vaporPressure.setReferenceTemperature(37.8, "C");
    vaporPressure.setMethodRVP(RvpMethod.VPCR4);
    vaporPressure.calculate();

    RvpResult wetVpcr4 = vaporPressure.getRvpResult();
    RvpResult dryVpcr4 = vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER);

    assertTrue(wetVpcr4.isValid());
    assertTrue(dryVpcr4.isValid());
    assertNotEquals(wetVpcr4.getValue(), dryVpcr4.getValue(), 1.0e-6);
  }

  private SystemInterface createOil() {
    SystemInterface oil = new SystemSrkEos(275.15, 1.0);
    oil.addComponent("methane", 0.0006538);
    oil.addComponent("ethane", 0.006538);
    oil.addComponent("propane", 0.065380);
    oil.addComponent("n-pentane", 0.154500);
    oil.addComponent("nC10", 0.545000);
    oil.setMixingRule(2);
    oil.init(0);
    return oil;
  }
}
