package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;

public class GERG2008Test {
  private GERG2008 gerg;
  static Logger logger = LogManager.getLogger(GERG2008Test.class);

  @BeforeEach
  public void setUp() {
    gerg = new GERG2008();
    gerg.SetupGERG();
  }

  @Test
  public void testMolarMassGERG() {
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW Mm = new doubleW(0.0);
    gerg.MolarMassGERG(x, Mm);
    assertEquals(16.04246, Mm.val, 1e-5);
  }

  @Test
  public void testPressureGERG() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW P = new doubleW(0.0);
    doubleW Z = new doubleW(0.0);
    gerg.PressureGERG(T, D, x, P, Z);
    assertTrue(P.val > 0);
    assertTrue(Z.val > 0);
  }

  @Test
  public void testDensityGERG() {
    int iFlag = 0;
    double T = 300.0;
    double P = 1000.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    gerg.DensityGERG(iFlag, T, P, x, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.4077469060672, D.val, 1e-5);
  }

  @Test
  public void testPropertiesGERG() {
    double T = 300.0;
    double D = 10.0;
    double[] x = new double[22];
    x[1] = 1.0; // Pure methane
    doubleW P = new doubleW(0.0);
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
    gerg.PropertiesGERG(T, D, x, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT, Kappa,
        A);
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
    assertEquals(0.83232372466, Z.val, 1e-5);
  }

  @Test
  void testThermoGERG2008() {
    SystemInterface GERG2008fluid = new neqsim.thermo.system.SystemGERG2008Eos(298.15, 10.0);
    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);

    GERG2008fluid.addComponent("methane", 0.85);
    GERG2008fluid.addComponent("hydrogen", 0.15);
    GERG2008fluid.init(0);
    GERG2008fluid.init(1);
    GERG2008fluid.init(2);
    GERG2008fluid.init(3);

    SRKfluid.addComponent("methane", 0.85);
    SRKfluid.addComponent("hydrogen", 0.15);
    SRKfluid.init(0);
    SRKfluid.init(1);
    SRKfluid.init(2);
    SRKfluid.init(3);
    // ThermodynamicOperations ops = new ThermodynamicOperations(GERG2008fluid);
    // ops.TPflash();
    // double densitygas = GERG2008fluid.getPhase("gas").getDensity("kg/m3");
    // assertEquals(1.607, densitygas, 1e-3);
    // GERG2008fluid.init(2);

    // GERG2008fluid.setP

    double enthalpgas = GERG2008fluid.getPhase("gas").getEnthalpy("J/mol");
    assertEquals(GERG2008fluid.getPhase("gas").getDensity(),
        SRKfluid.getPhase("gas").getDensity_GERG2008(), 1e-5);
    assertEquals(SRKfluid.getPhase("gas").getProperties_GERG2008()[7], enthalpgas, 1e-9);

    GERG2008fluid.setNumberOfPhases(1);
    GERG2008fluid.setMaxNumberOfPhases(1);
    GERG2008fluid.setForcePhaseTypes(true);
    GERG2008fluid.setPhaseType(0, "GAS");
    GERG2008fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    GERG2008fluid.getPhase("gas").initPhysicalProperties();
    // GERG2008fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");
    // GERG2008fluid.getPhase("gas").initPhysicalProperties();

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");
    SRKfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    SRKfluid.getPhase("gas").initPhysicalProperties();

    neqsim.process.equipment.stream.Stream gasstream =
        new neqsim.process.equipment.stream.Stream("gas", GERG2008fluid);
    gasstream.setFlowRate(10000.0, "kg/hr");
    gasstream.run();

    neqsim.process.equipment.stream.Stream gasstream_SKR =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
    gasstream_SKR.setFlowRate(10000.0, "kg/hr");
    gasstream_SKR.run();

    neqsim.process.equipment.compressor.Compressor compressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream);
    // compressor.setUseGERG2008(true);
    compressor.setOutletPressure(20.0);
    compressor.setPolytropicMethod("schultz");
    compressor.run();

    neqsim.process.equipment.compressor.Compressor compressor_SRK =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_SKR);
    compressor_SRK.setUseGERG2008(true);
    compressor_SRK.setOutletPressure(20.0);
    compressor_SRK.run();

    assertEquals(compressor_SRK.getOutletStream().getTemperature("C"),
        compressor.getOutletStream().getTemperature("C"), 1e-5);

    neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipeline =
        new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("pipe 1",
            compressor.getOutletStream());
    pipeline.setLength(5000.0);
    pipeline.setDiameter(0.2);
    pipeline.setElevation(0);
    pipeline.run();

    assertEquals(pipeline.getOutletPressure(), 18.55863368801788, 0.2);
  }

  @Test
  void testGERG2008Compressor() {
    SystemInterface GERG2008fluid = new neqsim.thermo.system.SystemGERG2008Eos(298.15, 10.0);

    GERG2008fluid.addComponent("methane", 0.85);
    GERG2008fluid.addComponent("hydrogen", 0.15);
    GERG2008fluid.init(0);
    GERG2008fluid.init(1);

    GERG2008fluid.setNumberOfPhases(1);
    GERG2008fluid.setMaxNumberOfPhases(1);
    GERG2008fluid.setForcePhaseTypes(true);
    GERG2008fluid.setPhaseType(0, "GAS");
    GERG2008fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");

    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);

    SRKfluid.addComponent("methane", 0.85);
    SRKfluid.addComponent("hydrogen", 0.15);
    SRKfluid.init(0);
    SRKfluid.init(1);

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");
    SRKfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");

    neqsim.process.equipment.stream.Stream GERG2008stream =
        new neqsim.process.equipment.stream.Stream("gas", GERG2008fluid);
    GERG2008stream.setFlowRate(130.0, "MSm3/day");
    GERG2008stream.run();

    neqsim.process.equipment.stream.Stream SRKstream =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
    SRKstream.setFlowRate(130.0, "MSm3/day");
    SRKstream.run();

    neqsim.process.equipment.compressor.Compressor GERG2008compressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", GERG2008stream);
    GERG2008compressor.setOutletPressure(20.0, "bara");
    GERG2008compressor.setPolytropicEfficiency(0.75);
    GERG2008compressor.run();

    neqsim.process.equipment.compressor.Compressor SRKcompressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", SRKstream);
    SRKcompressor.setOutletPressure(20.0, "bara");
    SRKcompressor.setPolytropicEfficiency(0.75);
    SRKcompressor.setUseGERG2008(true);
    SRKcompressor.run();

    assertEquals(GERG2008compressor.getOutletStream().getTemperature("C"),
        SRKcompressor.getOutletStream().getTemperature("C"), 1e-8);
    assertEquals(GERG2008compressor.getPower("MW"), SRKcompressor.getPower("MW"), 1e-8);
    assertEquals(GERG2008compressor.getPolytropicHead(), SRKcompressor.getPolytropicHead(), 1e-5);
  }

  @Test
  void CompressorSchultz() {
    SystemInterface GERG2008fluid = new neqsim.thermo.system.SystemGERG2008Eos(298.15, 90.0);

    GERG2008fluid.addComponent("methane", 0.85);
    GERG2008fluid.addComponent("hydrogen", 0.15);
    GERG2008fluid.init(0);
    GERG2008fluid.init(1);
    GERG2008fluid.init(2);
    GERG2008fluid.init(3);

    GERG2008fluid.setNumberOfPhases(1);
    GERG2008fluid.setMaxNumberOfPhases(1);
    GERG2008fluid.setForcePhaseTypes(true);
    GERG2008fluid.setPhaseType(0, "GAS");
    // Leachmanfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    GERG2008fluid.getPhase("gas").initPhysicalProperties();

    neqsim.process.equipment.stream.Stream gasstream_GERG2008 =
        new neqsim.process.equipment.stream.Stream("gas", GERG2008fluid);
    gasstream_GERG2008.setFlowRate(60.0, "MSm3/day");
    gasstream_GERG2008.run();

    neqsim.process.equipment.compressor.Compressor compressor_GERG2008 =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream_GERG2008);
    compressor_GERG2008.setOutletPressure(120.0);
    compressor_GERG2008.setPolytropicEfficiency(0.77);
    compressor_GERG2008.run();

    neqsim.process.equipment.compressor.Compressor compressor_Schultz =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_GERG2008);
    compressor_Schultz.setOutletPressure(120.0);
    compressor_Schultz.setPolytropicEfficiency(0.77);
    compressor_Schultz.setUsePolytropicCalc(true);
    compressor_Schultz.setPolytropicMethod("schultz");
    compressor_Schultz.run();

    logger.debug("Density before compressor " + GERG2008fluid.getDensity("kg/m3"));
    logger.debug("-----------------Normal-----------------");
    logger.debug(
        "Temperature out of Compr." + compressor_GERG2008.getOutletStream().getTemperature("C"));
    logger.debug("Power out of Compr." + compressor_GERG2008.getPower("MW"));
    logger.debug("Polytropic Head out of Compr." + compressor_GERG2008.getPolytropicHead("kJ/kg"));

    logger.debug("-----------------Schultz-----------------");
    logger.debug(
        "Temperature out of Compr." + compressor_Schultz.getOutletStream().getTemperature("C"));
    logger.debug("Power out of Compr." + compressor_Schultz.getPower("MW"));
    logger.debug("Polytropic Head out of Compr." + compressor_Schultz.getPolytropicHead("kJ/kg"));
  }
}
