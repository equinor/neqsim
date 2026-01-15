package neqsim.thermo.util.Vega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;

public class VegaTest {
  private Vega Vega;

  @BeforeEach
  void setUp() {
    Vega = new Vega();
    Vega.SetupVega();
  }

  @Test
  void testDensityVega() {
    int iFlag = 0;
    double T = 273.15;
    double P = 1000.0;
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    Vega.DensityVega(iFlag, T, P, D, ierr, herr);
    assertEquals(0, ierr.val);
    assertTrue(D.val > 0);
    assertEquals(0.43802, D.val, 1e-5);
  }

  @Test
  void testPropertiesVega() {
    double T = 273.15;
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

    Vega.DensityVega(iFlag, T, P.val, D, ierr, herr);

    Vega.propertiesVega(T, D.val, P, Z, dPdD, d2PdD2, d2PdTD, dPdT, U, H, S, Cv, Cp, W, G, JT,
        Kappa, A);
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
    assertEquals(1.005253888311987, Z.val, 1e-5);
  }

  @Test
  void testThermoVega() {
    SystemInterface vegafluid = new neqsim.thermo.system.SystemVegaEos(298.15, 10.0);
    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);
    vegafluid.init(0);
    vegafluid.init(1);
    vegafluid.init(2);
    vegafluid.init(3);

    SRKfluid.addComponent("helium", 1.0);
    SRKfluid.init(0);
    SRKfluid.init(1);
    SRKfluid.init(2);
    SRKfluid.init(3);
    // ThermodynamicOperations ops = new ThermodynamicOperations(vegafluid);
    // ops.TPflash();
    // double densitygas = vegafluid.getPhase("gas").getDensity("kg/m3");
    // assertEquals(1.607, densitygas, 1e-3);
    // vegafluid.init(2);

    // vegafluid.setP

    double enthalpgas = vegafluid.getPhase("gas").getEnthalpy("J/mol");
    assertEquals(vegafluid.getPhase("gas").getDensity(), SRKfluid.getPhase("gas").getDensity_Vega(),
        1e-8);
    assertEquals(SRKfluid.getPhase("gas").getProperties_Vega()[7], enthalpgas, 1e-9);

    vegafluid.setNumberOfPhases(1);
    vegafluid.setMaxNumberOfPhases(1);
    vegafluid.setForcePhaseTypes(true);
    vegafluid.setPhaseType(0, "GAS");
    vegafluid.getPhase("gas").getPhysicalProperties().setViscosityModel("FT");
    vegafluid.getPhase("gas").initPhysicalProperties();
    // vegafluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");
    // vegafluid.getPhase("gas").initPhysicalProperties();

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");
    SRKfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");
    SRKfluid.getPhase("gas").initPhysicalProperties();

    neqsim.process.equipment.stream.Stream gasstream =
        new neqsim.process.equipment.stream.Stream("gas", vegafluid);
    gasstream.setFlowRate(10000.0, "kg/hr");
    gasstream.run();

    neqsim.process.equipment.stream.Stream gasstream_SKR =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
    gasstream_SKR.setFlowRate(10000.0, "kg/hr");
    gasstream_SKR.run();

    neqsim.process.equipment.compressor.Compressor compressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream);
    // compressor.setUseVega(true);
    compressor.setOutletPressure(20.0);
    compressor.setPolytropicMethod("schultz");
    compressor.run();

    neqsim.process.equipment.compressor.Compressor compressor_SRK =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_SKR);
    compressor_SRK.setUseVega(true);
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

    assertEquals(14.092057183942142, pipeline.getOutletPressure(), 1e-5);
  }

  @Test
  void testVegaCompressor() {
    SystemInterface vegafluid = new neqsim.thermo.system.SystemVegaEos(298.15, 10.0);
    vegafluid.init(0);
    vegafluid.init(1);

    vegafluid.setNumberOfPhases(1);
    vegafluid.setMaxNumberOfPhases(1);
    vegafluid.setForcePhaseTypes(true);
    vegafluid.setPhaseType(0, "GAS");
    vegafluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");

    SystemInterface SRKfluid = new neqsim.thermo.system.SystemSrkEos(298.15, 10.0);

    SRKfluid.addComponent("helium", 1.0);
    SRKfluid.init(0);
    SRKfluid.init(1);

    SRKfluid.setNumberOfPhases(1);
    SRKfluid.setMaxNumberOfPhases(1);
    SRKfluid.setForcePhaseTypes(true);
    SRKfluid.setPhaseType(0, "GAS");
    SRKfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("KTA_mod");

    neqsim.process.equipment.stream.Stream Vegastream =
        new neqsim.process.equipment.stream.Stream("gas", vegafluid);
    Vegastream.setFlowRate(130.0, "MSm3/day");
    Vegastream.run();

    neqsim.process.equipment.stream.Stream SRKstream =
        new neqsim.process.equipment.stream.Stream("gas", SRKfluid);
    SRKstream.setFlowRate(130.0, "MSm3/day");
    SRKstream.run();

    neqsim.process.equipment.compressor.Compressor Vegacompressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", Vegastream);
    Vegacompressor.setOutletPressure(20.0, "bara");
    Vegacompressor.setPolytropicEfficiency(0.75);
    Vegacompressor.run();

    neqsim.process.equipment.compressor.Compressor SRKcompressor =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", SRKstream);
    SRKcompressor.setOutletPressure(20.0, "bara");
    SRKcompressor.setPolytropicEfficiency(0.75);
    SRKcompressor.setUseVega(true);
    SRKcompressor.run();

    assertEquals(Vegacompressor.getOutletStream().getTemperature("C"),
        SRKcompressor.getOutletStream().getTemperature("C"), 1e-5);
    assertEquals(Vegacompressor.getPower("MW"), SRKcompressor.getPower("MW"), 1e-8);
    assertEquals(Vegacompressor.getPolytropicHead(), SRKcompressor.getPolytropicHead(), 1e-5);
  }

  @Test
  void CompressorSchultz() {
    SystemInterface Vegafluid = new neqsim.thermo.system.SystemVegaEos(298.15, 90.0);

    Vegafluid.addComponent("helium", 1.0);
    Vegafluid.init(0);
    Vegafluid.init(1);
    Vegafluid.init(2);
    Vegafluid.init(3);

    Vegafluid.setNumberOfPhases(1);
    Vegafluid.setMaxNumberOfPhases(1);
    Vegafluid.setForcePhaseTypes(true);
    Vegafluid.setPhaseType(0, "GAS");
    // Leachmanfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    Vegafluid.getPhase("gas").initPhysicalProperties();

    neqsim.process.equipment.stream.Stream gasstream_Vega =
        new neqsim.process.equipment.stream.Stream("gas", Vegafluid);
    gasstream_Vega.setFlowRate(60.0, "MSm3/day");
    gasstream_Vega.run();

    neqsim.process.equipment.compressor.Compressor compressor_Vega =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream_Vega);
    compressor_Vega.setOutletPressure(120.0);
    compressor_Vega.setPolytropicEfficiency(0.77);
    compressor_Vega.run();

    neqsim.process.equipment.compressor.Compressor compressor_Schultz =
        new neqsim.process.equipment.compressor.Compressor("compressor 2", gasstream_Vega);
    compressor_Schultz.setOutletPressure(120.0);
    compressor_Schultz.setPolytropicEfficiency(0.77);
    compressor_Schultz.setUsePolytropicCalc(true);
    compressor_Schultz.setPolytropicMethod("schultz");
    compressor_Schultz.run();

    /*
     * System.out.println("Density before compressor " + Vegafluid.getDensity("kg/m3"));
     * System.out.println("-----------------Normal-----------------"); System.out.println(
     * "Temperature out of Compr." + compressor_Vega.getOutletStream().getTemperature("C"));
     * System.out.println("Power out of Compr." + compressor_Vega.getPower("MW")); System.out
     * .println("Polytropic Head out of Compr." + compressor_Vega.getPolytropicHead("kJ/kg"));
     * 
     * System.out.println("-----------------Schultz-----------------"); System.out.println(
     * "Temperature out of Compr." + compressor_Schultz.getOutletStream().getTemperature("C"));
     * System.out.println("Power out of Compr." + compressor_Schultz.getPower("MW")); System.out
     * .println("Polytropic Head out of Compr." + compressor_Schultz.getPolytropicHead("kJ/kg"));
     */
  }
}
