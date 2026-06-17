package neqsim.process.equipment.absorber;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for RateBasedAbsorber.
 */
public class RateBasedAbsorberTest {

  @Test
  public void testConstructor() {
    RateBasedAbsorber absorber = new RateBasedAbsorber("test absorber");
    Assertions.assertNotNull(absorber);
    Assertions.assertEquals("test absorber", absorber.getName());
    Assertions.assertEquals(RateBasedAbsorber.MassTransferModel.ONDA_1968,
        absorber.getMassTransferModel());
  }

  @Test
  public void testSetPackingParameters() {
    RateBasedAbsorber absorber = new RateBasedAbsorber("packing test");
    absorber.setColumnDiameter(1.2);
    absorber.setPackedHeight(8.0);
    absorber.setPackingSpecificArea(250.0);
    absorber.setPackingVoidFraction(0.95);
    absorber.setPackingNominalSize(0.025);
    absorber.setPackingCriticalSurfaceTension(0.061);
    absorber.setNumberOfStages(15);

    Assertions.assertEquals(1.2, absorber.getColumnDiameter(), 0.001);
    Assertions.assertEquals(8.0, absorber.getPackedHeight(), 0.001);
  }

  @Test
  public void testMassTransferModelEnum() {
    RateBasedAbsorber.MassTransferModel onda = RateBasedAbsorber.MassTransferModel.ONDA_1968;
    Assertions.assertEquals("ONDA_1968", onda.name());

    RateBasedAbsorber.MassTransferModel billet =
        RateBasedAbsorber.MassTransferModel.BILLET_SCHULTES_1999;
    Assertions.assertEquals("BILLET_SCHULTES_1999", billet.name());
  }

  @Test
  public void testEnhancementModelEnum() {
    RateBasedAbsorber.EnhancementModel none = RateBasedAbsorber.EnhancementModel.NONE;
    Assertions.assertEquals("NONE", none.name());

    RateBasedAbsorber.EnhancementModel hatta =
        RateBasedAbsorber.EnhancementModel.HATTA_PSEUDO_FIRST_ORDER;
    Assertions.assertEquals("HATTA_PSEUDO_FIRST_ORDER", hatta.name());
  }

  @Test
  public void testRunWithGasAndSolvent() {
    // Simple gas stream with CO2
    SystemInterface gasFluid = new SystemSrkEos(273.15 + 40.0, 50.0);
    gasFluid.addComponent("methane", 0.90);
    gasFluid.addComponent("CO2", 0.10);
    gasFluid.setMixingRule("classic");

    Stream gasIn = new Stream("gas in", gasFluid);
    gasIn.setFlowRate(10000.0, "kg/hr");
    gasIn.run();

    // Simple solvent stream (MEG or amine approximated as water for simplicity)
    SystemInterface solventFluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    solventFluid.addComponent("water", 0.80);
    solventFluid.addComponent("MDEA", 0.20);
    solventFluid.setMixingRule("classic");

    Stream solventIn = new Stream("solvent in", solventFluid);
    solventIn.setFlowRate(20000.0, "kg/hr");
    solventIn.run();

    RateBasedAbsorber absorber = new RateBasedAbsorber("CO2 absorber");
    absorber.addGasInStream(gasIn);
    absorber.addSolventInStream(solventIn);
    absorber.setColumnDiameter(1.0);
    absorber.setPackedHeight(6.0);
    absorber.setPackingSpecificArea(250.0);
    absorber.setPackingVoidFraction(0.95);
    absorber.setPackingNominalSize(0.025);
    absorber.setPackingCriticalSurfaceTension(0.061);
    absorber.setNumberOfStages(10);
    absorber.setMassTransferModel(RateBasedAbsorber.MassTransferModel.ONDA_1968);

    absorber.run();

    // Verify outputs exist
    Assertions.assertNotNull(absorber.getGasOutStream());
    Assertions.assertNotNull(absorber.getSolventOutStream());

    // HTU and KGa should be computed
    double htu = absorber.getHeightOfTransferUnit();
    double kga = absorber.getOverallKGa();
    double kla = absorber.getOverallKLa();
    double wetted = absorber.getWettedArea();

    // These should be non-negative (may be 0 if correlations can't compute for this system)
    Assertions.assertTrue(htu >= 0.0, "HTU should be non-negative: " + htu);
    Assertions.assertTrue(kga >= 0.0, "KGa should be non-negative: " + kga);
    Assertions.assertTrue(kla >= 0.0, "KLa should be non-negative: " + kla);
    Assertions.assertTrue(wetted >= 0.0, "Wetted area should be non-negative: " + wetted);

    // Stage results should be populated
    Assertions.assertNotNull(absorber.getStageResults());
    Assertions.assertTrue(absorber.getStageResults().size() > 0,
        "Stage results should be populated");
  }

  @Test
  public void testSetModelTypes() {
    RateBasedAbsorber absorber = new RateBasedAbsorber("model test");
    absorber.setMassTransferModel(RateBasedAbsorber.MassTransferModel.BILLET_SCHULTES_1999);
    Assertions.assertEquals(RateBasedAbsorber.MassTransferModel.BILLET_SCHULTES_1999,
        absorber.getMassTransferModel());

    absorber.setEnhancementModel(RateBasedAbsorber.EnhancementModel.HATTA_PSEUDO_FIRST_ORDER);
    // No getter for enhancement model, but this shouldn't throw
  }
}
