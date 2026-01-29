package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for SubseaJumper equipment.
 *
 * @author ESOL
 */
public class SubseaJumperTest {

  private SystemInterface testFluid;
  private Stream inletStream;

  @BeforeEach
  public void setUp() {
    testFluid = new SystemSrkEos(288.15, 150.0);
    testFluid.addComponent("methane", 0.85);
    testFluid.addComponent("ethane", 0.10);
    testFluid.addComponent("propane", 0.05);
    testFluid.setMixingRule("classic");

    inletStream = new Stream("Jumper Inlet", testFluid);
    inletStream.setFlowRate(50000, "kg/hr");
    inletStream.run();
  }

  @Test
  public void testJumperCreation() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    assertNotNull(jumper);
    assertEquals("Test Jumper", jumper.getName());
  }

  @Test
  public void testRigidJumperConfiguration() {
    SubseaJumper jumper = new SubseaJumper("Rigid Jumper", inletStream);

    jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
    jumper.setLength(50.0);
    jumper.setNominalBoreInches(6.0);
    jumper.setOuterDiameterInches(6.625);
    jumper.setWallThicknessMm(12.7);
    jumper.setDesignPressure(200.0);
    jumper.setMaterialGrade("X65");
    jumper.setMinimumBendRadius(1.5);

    assertEquals(SubseaJumper.JumperType.RIGID_M_SHAPE, jumper.getJumperType());
    assertEquals(50.0, jumper.getLength(), 0.001);
    assertEquals(6.0, jumper.getNominalBoreInches(), 0.001);
    assertEquals(6.625, jumper.getOuterDiameterInches(), 0.001);
    assertEquals(12.7, jumper.getWallThicknessMm(), 0.001);
    assertEquals(200.0, jumper.getDesignPressure(), 0.001);
    assertEquals("X65", jumper.getMaterialGrade());
    assertTrue(jumper.isRigid());
  }

  @Test
  public void testFlexibleJumperConfiguration() {
    SubseaJumper jumper = new SubseaJumper("Flexible Jumper", inletStream);

    jumper.setJumperType(SubseaJumper.JumperType.FLEXIBLE_DYNAMIC);
    jumper.setLength(80.0);
    jumper.setNominalBoreInches(4.0);
    jumper.setDesignPressure(150.0);
    jumper.setFlexibleMinBendRadius(2.0);

    assertEquals(SubseaJumper.JumperType.FLEXIBLE_DYNAMIC, jumper.getJumperType());
    assertEquals(80.0, jumper.getLength(), 0.001);
    assertEquals(2.0, jumper.getFlexibleMinBendRadius(), 0.001);
    assertTrue(!jumper.isRigid());
  }

  @Test
  public void testJumperPressureDrop() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
    jumper.setLength(50.0);
    jumper.setNominalBoreInches(6.0);
    jumper.setOuterDiameterInches(6.625);
    jumper.setWallThicknessMm(12.7);
    jumper.setDesignPressure(200.0);
    jumper.setNumberOfBends(3);
    jumper.run();

    // Check pressure drop occurred
    double inletPressure = inletStream.getPressure("bara");
    double outletPressure = jumper.getOutletStream().getPressure("bara");
    assertTrue(outletPressure <= inletPressure);
  }

  @Test
  public void testJumperRun() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    jumper.setLength(50.0);
    jumper.run();

    assertNotNull(jumper.getOutletStream());
    assertTrue(jumper.getOutletStream().getFlowRate("kg/hr") > 0);
  }

  @Test
  public void testJumperMechanicalDesign() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
    jumper.setLength(50.0);
    jumper.setNominalBoreInches(6.0);
    jumper.setOuterDiameterInches(6.625);
    jumper.setDesignPressure(200.0);
    jumper.setMaterialGrade("X65");
    jumper.run();

    jumper.initMechanicalDesign();
    assertNotNull(jumper.getMechanicalDesign());

    jumper.getMechanicalDesign().calcDesign();

    String json = jumper.getMechanicalDesign().toJson();
    assertNotNull(json);
    assertTrue(json.contains("SubseaJumper"));
  }

  @Test
  public void testJumperWeight() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
    jumper.setLength(50.0);
    jumper.setOuterDiameterInches(6.625);
    jumper.setWallThicknessMm(12.7);
    jumper.run();

    jumper.initMechanicalDesign();
    jumper.getMechanicalDesign().calcDesign();

    assertTrue(jumper.getDryWeight() > 0);
    assertTrue(jumper.getSubmergedWeight() > 0);
    assertTrue(jumper.getSubmergedWeight() < jumper.getDryWeight());
  }

  @Test
  public void testJumperToJson() {
    SubseaJumper jumper = new SubseaJumper("Test Jumper", inletStream);
    jumper.setJumperType(SubseaJumper.JumperType.RIGID_INVERTED_U);
    jumper.setLength(60.0);
    jumper.run();

    String json = jumper.toJson();
    assertNotNull(json);
    assertTrue(json.contains("Test Jumper"));
    assertTrue(json.contains("RIGID_INVERTED_U"));
  }
}
