package neqsim.thermo.util.leachman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;

/**
 * @author victorigi99
 */
public class LeachmanTest {
  private Leachman Leachman;

  @BeforeEach
  public void setUp() {
    Leachman = new Leachman();
  }

  @Test
  void testDensityLeachman_normal() {
    Leachman.SetupLeachman("normal");
    int iFlag = 0;
    double T = 300.0;
    double P = 1000.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    Leachman.DensityLeachman(iFlag, T, P, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.39857173642364424, D.val, 1e-5);
  }

  @Test
  void testDensityLeachman_para() {
    Leachman.SetupLeachman("para");
    int iFlag = 0;
    double T = 300.0;
    double P = 1000.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    Leachman.DensityLeachman(iFlag, T, P, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.39857317089888866, D.val, 1e-5);
  }

  @Test
  void testDensityLeachman_ortho() {
    Leachman.SetupLeachman("ortho");
    int iFlag = 0;
    double T = 300.0;
    double P = 1000.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    Leachman.DensityLeachman(iFlag, T, P, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.39858158583647507, D.val, 1e-5);
  }

  @Test
  void testPropertiesLeachman() {
    Leachman.SetupLeachman("normal");

    double T = 300.0;
    int iFlag = 0;
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    doubleW D = new doubleW(30);
    doubleW P = new doubleW(1000.0d);
    doubleW Z = new doubleW(0.0);
    doubleW dPdD = new doubleW(0.0);
    doubleW d2PdD2 = new doubleW(0.0);
    doubleW d2PdTD = new doubleW(0.0);
    doubleW dPdT = new doubleW(0.0);
    doubleW U = new doubleW(0.0);
    doubleW H = new doubleW(0.0);
    doubleW S = new doubleW(0.0);
    doubleW Cv = new doubleW(0.0);
    doubleW Cp = new doubleW(0.0);
    doubleW W = new doubleW(0.0);
    doubleW G = new doubleW(0.0);
    doubleW JT = new doubleW(0.0);
    doubleW Kappa = new doubleW(0.0);
    doubleW A = new doubleW(0.0);

    Leachman.DensityLeachman(iFlag, T, P.val, D, ierr, herr);

    Leachman.propertiesLeachman(T, D.val, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G,
        JT, Kappa, A);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
    assertTrue(U.val != 0);
    assertTrue(H.val != 0);
    assertTrue(S.val != 0);
    assertTrue(Cv.val != 0);
    assertTrue(Cp.val != 0);
    assertTrue(W.val != 0);
    assertTrue(G.val != 0);
    assertTrue(JT.val != 0);
    assertTrue(Kappa.val != 0);
    assertTrue(A.val != 0);
    assertEquals(1.0058554805933522, Z.val, 1e-5);
  }

    @Test
  void testThermoLeachman() {

    SystemInterface Leachmanfluid = new neqsim.thermo.system.SystemLeachmanEos(298.15, 10.0);
    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);

    Leachmanfluid.addComponent("hydrogen", 1.0);
    Leachmanfluid.init(0);
    Leachmanfluid.init(1);

    SRKfluid.addComponent("hydrogen", 1.0);
    SRKfluid.init(0);
    SRKfluid.init(1);
    // ThermodynamicOperations ops = new ThermodynamicOperations(Leachmanfluid);
    // ops.TPflash();
    // double densitygas = Leachmanfluid.getPhase("gas").getDensity("kg/m3");
    // assertEquals(1.607, densitygas, 1e-3);
    // Leachmanfluid.init(2);

    // Leachmanfluid.setP

    // double enthalpgas = Leachmanfluid.getPhase("gas").getEnthalpy("J/mol");
    // assertEquals(6230.66, enthalpgas, 1e-2);


    Leachmanfluid.setNumberOfPhases(1);
    Leachmanfluid.setMaxNumberOfPhases(1);
    Leachmanfluid.setForcePhaseTypes(true);
    Leachmanfluid.setPhaseType(0, "GAS");
    Leachmanfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    Leachmanfluid.getPhase("gas").initPhysicalProperties();

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");

    neqsim.process.equipment.stream.Stream gasstream_leachman =
        new neqsim.process.equipment.stream.Stream("gas", Leachmanfluid);
      gasstream_leachman.setFlowRate(10000.0, "kg/hr");
      gasstream_leachman.run();

    neqsim.process.equipment.stream.Stream gasstream_SRK =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
      gasstream_SRK.setFlowRate(10000.0, "kg/hr");
      gasstream_SRK.run();

    neqsim.process.equipment.compressor.Compressor compressor_leachman =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream_leachman);
    // compressor.setUseLeachman(true);
    compressor_leachman.setOutletPressure(20.0);
    compressor_leachman.run();

    System.out.println("Temperature out of Compr." + compressor_leachman.getOutletStream().getTemperature("C"));

    neqsim.process.equipment.compressor.Compressor compressor_SRK =
    new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_SRK);
    compressor_SRK.setUseLeachman(true);
    compressor_SRK.setOutletPressure(20.0);
    compressor_SRK.run();

    assertEquals(compressor_leachman.getOutletStream().getTemperature("C"), compressor_SRK.getOutletStream().getTemperature("C"), 1e-5);
    assertEquals(compressor_leachman.getPower(), compressor_SRK.getPower(), 1e-2);
    assertEquals(compressor_leachman.getPolytropicHead("kJ/kg"), compressor_SRK.getPolytropicHead("kJ/kg"), 1e-5);

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipeline =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("pipe 1",
        compressor_leachman.getOutletStream());
    pipeline.setLength(1000.0);
    pipeline.setDiameter(0.2);
    pipeline.setElevation(0);
    pipeline.run();

    System.out.println("pressure " + pipeline.getOutletStream().getPressure());
    System.out.println("temperature out of pipe " + pipeline.getOutletStream().getTemperature("C"));
    assertEquals(17.91015516796876, pipeline.getOutletStream().getPressure(), 1e-5);


    // assertEquals(0.43802, D.val, 1e-5);
  }

  @Test 
  void CompressorSchultz() {
    SystemInterface Leachmanfluid = new neqsim.thermo.system.SystemLeachmanEos(298.15, 90.0);
    
    
    Leachmanfluid.addComponent("hydrogen", 1.0);
    Leachmanfluid.init(0);
    Leachmanfluid.init(1);
    Leachmanfluid.init(2);
    Leachmanfluid.init(3);

    Leachmanfluid.setNumberOfPhases(1);
    Leachmanfluid.setMaxNumberOfPhases(1);
    Leachmanfluid.setForcePhaseTypes(true);
    Leachmanfluid.setPhaseType(0, "GAS");
    //Leachmanfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    Leachmanfluid.getPhase("gas").initPhysicalProperties();

    neqsim.process.equipment.stream.Stream gasstream_leachman =
        new neqsim.process.equipment.stream.Stream("gas", Leachmanfluid);
      gasstream_leachman.setFlowRate(60.0, "MSm3/day");
      gasstream_leachman.run();

    neqsim.process.equipment.compressor.Compressor compressor_leachman =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream_leachman);
    compressor_leachman.setOutletPressure(120.0);
    compressor_leachman.setPolytropicEfficiency(0.77);
    compressor_leachman.run();

    neqsim.process.equipment.compressor.Compressor compressor_Schultz = 
        new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_leachman);
    compressor_Schultz.setOutletPressure(120.0);
    compressor_Schultz.setPolytropicEfficiency(0.77);
    compressor_Schultz.setUsePolytropicCalc(true);
    compressor_Schultz.setPolytropicMethod("schultz");
    compressor_Schultz.run();

    System.out.println("Density before compressor " + Leachmanfluid.getDensity("kg/m3"));
    System.out.println("-----------------Normal-----------------");
    System.out.println("Temperature out of Compr." + compressor_leachman.getOutletStream().getTemperature("C"));
    System.out.println("Power out of Compr." + compressor_leachman.getPower("MW"));
    System.out.println("Polytropic Head out of Compr." + compressor_leachman.getPolytropicHead("kJ/kg"));

    System.out.println("-----------------Schultz-----------------");
    System.out.println("Temperature out of Compr." + compressor_Schultz.getOutletStream().getTemperature("C"));
    System.out.println("Power out of Compr." + compressor_Schultz.getPower("MW"));
    System.out.println("Polytropic Head out of Compr." + compressor_Schultz.getPolytropicHead("kJ/kg"));

  }


}
