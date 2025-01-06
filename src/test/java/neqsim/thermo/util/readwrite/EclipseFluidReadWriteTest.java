package neqsim.thermo.util.readwrite;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class EclipseFluidReadWriteTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  File file = new File("src/test/java/neqsim/thermo/util/readwrite");
  String fileFluid1 = file.getAbsolutePath() + "/fluid1.e300";
  String fileA1 = file.getAbsolutePath() + "/A-1.E300";
  String fileA13 = file.getAbsolutePath() + "/A-13.E300";
  String fileA17 = file.getAbsolutePath() + "/A-17.E300";
  String fileA19 = file.getAbsolutePath() + "/A-19.E300";
  String filer = file.getAbsolutePath() + "/fluid-r.E300";

  /**
   * Test method for
   * {@link neqsim.thermo.util.readwrite.EclipseFluidReadWrite#read(java.lang.String)}.
   *
   * @throws IOException
   */
  @Test
  void testRead() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A13";
    testSystem = EclipseFluidReadWrite.read(fileA13);
  }

  @Test
  void testReadAndAddFluids() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A19";
    testSystem = EclipseFluidReadWrite.read(fileA19);

    EclipseFluidReadWrite.pseudoName = "_A17";
    neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
    testSystem.addFluid(testSystem2);

    EclipseFluidReadWrite.pseudoName = "_A13";
    neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
    testSystem.addFluid(testSystem3);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testReadAndAddFluids2() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A1";
    testSystem = EclipseFluidReadWrite.read(fileA1);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(100.0, "bara");
    testSystem.setTemperature(25.0, "C");
    testOps.TPflash();

    // neqsim.thermo.util.readwrite.TablePrinter.printTable((((PhaseEos
    // )testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters()));
  }

  @Test
  void testSetComposition() throws IOException {
    EclipseFluidReadWrite.pseudoName = "_A19";
    testSystem = EclipseFluidReadWrite.read(fileA19);

    EclipseFluidReadWrite.pseudoName = "_A17";
    neqsim.thermo.system.SystemInterface testSystem2 = EclipseFluidReadWrite.read(fileA17);
    testSystem.addFluid(testSystem2);

    EclipseFluidReadWrite.pseudoName = "_A13";
    neqsim.thermo.system.SystemInterface testSystem3 = EclipseFluidReadWrite.read(fileA13);
    testSystem.addFluid(testSystem3);

    EclipseFluidReadWrite.pseudoName = "_A13";
    EclipseFluidReadWrite.setComposition(testSystem, fileA13);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  @Test
  void testReadFluid1() throws IOException {
    testSystem = EclipseFluidReadWrite.read(fileFluid1);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(50.0, "bara");

    // neqsim.thermo.util.readwrite.TablePrinter.printTable(
    // (((PhaseEos) testSystem.getPhase(0)).getMixingRule().getBinaryInteractionParameters()));

    // for (int i = 0; i < testSystem.getNumberOfComponents(); i++) {
    // System.out.println(testSystem.getComponentName(i) + " TC "
    // + (testSystem.getComponent(i).getVolumeCorrectionConst()));
    // }
  }

  @Test
  void testReadFluidR() throws IOException {
    testSystem = EclipseFluidReadWrite.read(filer);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(530.97, "bara");
    testSystem.setTemperature(105.0, "C");
    testOps.TPflash();
    // testSystem.prettyPrint();
    // Assertions.assertEquals(0.9270363530255, testSystem.getBeta(0), 1e-6);

    testSystem = EclipseFluidReadWrite.read(filer);
    testSystem.setMultiPhaseCheck(true);
    testOps = new ThermodynamicOperations(testSystem);
    testSystem.setPressure(520.0, "bara");
    testSystem.setTemperature(105.0, "C");
    testOps.TPflash();
    // testSystem.prettyPrint();
    // Assertions.assertEquals(0.9270363530, testSystem.getBeta(0), 1e-6);
  }
}
