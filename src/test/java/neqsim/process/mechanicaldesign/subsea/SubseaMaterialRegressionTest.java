package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.subsea.PLET;
import neqsim.process.equipment.subsea.SubseaJumper;
import neqsim.process.equipment.subsea.SubseaTree;

/**
 * Regression checks for table-backed subsea material strengths.
 *
 * @author ESOL
 * @version 1.0
 */
class SubseaMaterialRegressionTest {
  /** Checks the assumed F22 Class 3 tree body rather than a 75 ksi grade. */
  @Test
  void treeUsesF22Class3Strength() {
    SubseaTree tree = new SubseaTree("F22 tree");
    tree.setDesignPressure(1034.0);
    tree.setBoreSizeInches(7.0);
    SubseaTreeMechanicalDesign design = tree.getMechanicalDesign();
    design.calcDesign();
    assertEquals((1034.0 / 10.0 * 7.0 * 25.4) / (2 * 310.0 * 0.67), design.getBoreWallThickness(), 1e-10);
  }

  /** Checks that Inconel cladding does not supply hub structural strength. */
  @Test
  void pletUsesF22Class3Strength() {
    PLET plet = new PLET("Clad F22 hub");
    plet.setDesignPressure(500.0);
    plet.setNominalBoreInches(12.0);
    plet.setDesignTemperature(20.0);
    PLETMechanicalDesign design = plet.getMechanicalDesign();
    design.calcDesign();
    assertEquals((50.0 * 12.0 * 25.4 * 1.5) / (2 * 310.0 * 0.72) + 3.0, design.getHubWallThickness(), 1e-10);
  }

  /**
   * Checks distinct table grades at a pressure above the wall floor.
   *
   * @param grade qualified material grade
   * @param smys yield strength in MPa
   */
  @ParameterizedTest
  @CsvSource({"316L,170", "316,205", "Duplex-2205,450", "SuperDuplex-2507,550", "6Mo,300", "X65,448"})
  void jumperUsesGradeSpecificTableStrength(String grade, double smys) {
    assertJumperWall(grade, smys);
  }

  /** Checks explicit F22 Class 1 selection cannot retain Class 3 strength. */
  @Test
  void explicitF22Class1UsesOwnStrength() {
    SubseaTree tree = new SubseaTree("Class 1 tree");
    tree.setDesignPressure(1034.0);
    tree.setBoreSizeInches(7.0);
    SubseaTreeMechanicalDesign treeDesign = tree.getMechanicalDesign();
    treeDesign.setMaterialGrade("F22-Class1");
    treeDesign.calcDesign();
    assertEquals(103.4 * 7.0 * 25.4 / (2 * 205.0 * 0.67), treeDesign.getBoreWallThickness(), 1e-10);
    PLET plet = new PLET("Class 1 hub");
    plet.setDesignPressure(500.0);
    plet.setDesignTemperature(20.0);
    PLETMechanicalDesign hubDesign = plet.getMechanicalDesign();
    hubDesign.setHubMaterialGrade("F22-Class1");
    hubDesign.calcDesign();
    assertEquals(50.0 * plet.getNominalBoreInches() * 25.4 * 1.5 / (2 * 205.0 * 0.72) + 3.0,
        hubDesign.getHubWallThickness(), 1e-10);
  }

  /** Checks that unknown alloys cannot silently acquire X65 strength. */
  @Test
  void unsupportedJumperMaterialIsRejected() {
    SubseaJumper jumper = new SubseaJumper("Unknown alloy");
    jumper.setMaterialGrade("unqualified alloy");
    assertThrows(IllegalArgumentException.class, () -> jumper.getMechanicalDesign().calcDesign());
  }

  /**
   * Compares calculated wall with the unchanged screening formula using table strength.
   *
   * @param grade table material grade
   * @param smys minimum yield strength in MPa
   */
  private void assertJumperWall(String grade, double smys) {
    SubseaJumper jumper = new SubseaJumper("Material check");
    jumper.setMaterialGrade(grade);
    jumper.setDesignPressure(350.0);
    SubseaJumperMechanicalDesign design = jumper.getMechanicalDesign();
    design.calcDesign();
    double allowable = smys / (1.26 * 1.15);
    double expected = (35.0 * jumper.getOuterDiameterInches() * 25.4 / (2 * allowable) + 3.0) * 1.125;
    assertEquals(Math.max(expected, 9.52), design.getRequiredWallThickness(), 1e-10, grade);
  }
}
