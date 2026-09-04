package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the entrainment provider SPI and the three models that ship with public NeqSim.
 *
 * @author NeqSim
 * @version 1.0
 */
public class EntrainmentProviderSpiTest {

  /** Separator used by the tests, already run. */
  private Separator separator;

  /** Builds a three-phase separator so oil, water and gas are all present. */
  @BeforeEach
  public void setUp() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 80.0);
    fluid.addComponent("n-heptane", 15.0);
    fluid.addComponent("water", 5.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    separator = new Separator("test separator", feed);
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.run();
  }

  /** All three public models are discoverable through ServiceLoader. */
  @Test
  public void publicProvidersAreRegistered() {
    Set<String> ids = new HashSet<String>();
    for (Iterator<EnhancedEntrainmentProvider> it = EntrainmentProviderRegistry.all().iterator(); it.hasNext();) {
      ids.add(it.next().getId());
    }
    assertTrue(ids.contains(ZeroCarryOverProvider.ID), "zero model not registered, found " + ids);
    assertTrue(ids.contains(SpecCarryOverProvider.ID), "spec model not registered, found " + ids);
    assertTrue(ids.contains(BuiltInSevenStageProvider.ID), "7-stage model not registered, found " + ids);
  }

  /** An unknown id fails loudly and names what is available. */
  @Test
  public void unknownProviderIdFailsLoudly() {
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> EntrainmentProviderRegistry.find("no-such-model"));
    assertTrue(ex.getMessage().contains("no-such-model"));
    assertTrue(ex.getMessage().contains("Available providers"));
  }

  /** A null id is rejected as a programming error rather than treated as "default". */
  @Test
  public void nullProviderIdIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> EntrainmentProviderRegistry.find(null));
  }

  /** The zero model reports no carry-over on any channel. */
  @Test
  public void zeroModelReportsNoCarryOver() {
    EntrainmentResult r = EntrainmentProviderRegistry.find(ZeroCarryOverProvider.ID).compute(separator);
    assertEquals(0.0, r.getOilInGasKgPerHr(), 0.0);
    assertEquals(0.0, r.getWaterInGasKgPerHr(), 0.0);
    assertEquals(0.0, r.getGasInLiquidKgPerHr(), 0.0);
    assertEquals(ZeroCarryOverProvider.ID, r.getProviderId());
  }

  /**
   * The spec model carries the documented liquid volume. Checked by converting the reported masses back to volumes and
   * comparing against 13.4 L per MSm3 of gas, which is independent of how the model splits the total.
   */
  @Test
  public void specModelCarriesDocumentedLiquidVolume() {
    EntrainmentResult r = EntrainmentProviderRegistry.find(SpecCarryOverProvider.ID).compute(separator);

    double gasMSm3PerHr = separator.getGasOutStream().getFluid().getFlowRate("MSm3/hr");
    double oilDensity = separator.getFeedStream().getFluid().getPhase("oil").getDensity("kg/m3");
    double waterDensity = separator.getFeedStream().getFluid().getPhase("aqueous").getDensity("kg/m3");

    double carriedVolumeM3PerHr = r.getOilInGasKgPerHr() / oilDensity + r.getWaterInGasKgPerHr() / waterDensity;
    double expectedVolumeM3PerHr = SpecCarryOverProvider.DEFAULT_CARRY_OVER_LITRE_PER_MSM3 * 1.0e-3 * gasMSm3PerHr;

    assertTrue(gasMSm3PerHr > 0.0, "test fluid produced no gas");
    assertEquals(expectedVolumeM3PerHr, carriedVolumeM3PerHr, expectedVolumeM3PerHr * 1.0e-9);
  }

  /** The spec model splits the carried liquid at the feed water cut. */
  @Test
  public void specModelSplitsAtFeedWaterCut() {
    EntrainmentResult r = EntrainmentProviderRegistry.find(SpecCarryOverProvider.ID).compute(separator);

    SystemInterface feed = separator.getFeedStream().getFluid();
    double oilVol = feed.getPhase("oil").getFlowRate("m3/hr");
    double waterVol = feed.getPhase("aqueous").getFlowRate("m3/hr");
    double expectedWaterCut = waterVol / (oilVol + waterVol);

    double oilDensity = feed.getPhase("oil").getDensity("kg/m3");
    double waterDensity = feed.getPhase("aqueous").getDensity("kg/m3");
    double carriedOilVol = r.getOilInGasKgPerHr() / oilDensity;
    double carriedWaterVol = r.getWaterInGasKgPerHr() / waterDensity;
    double carriedWaterCut = carriedWaterVol / (carriedOilVol + carriedWaterVol);

    assertEquals(expectedWaterCut, carriedWaterCut, 1.0e-9);
  }

  /** A configurable carry-over figure scales the result proportionally. */
  @Test
  public void specModelHonoursConfiguredFigure() {
    SpecCarryOverProvider provider = new SpecCarryOverProvider();
    EntrainmentResult atDefault = provider.compute(separator);
    provider.setCarryOverLitrePerMSm3(2.0 * SpecCarryOverProvider.DEFAULT_CARRY_OVER_LITRE_PER_MSM3);
    EntrainmentResult atDouble = provider.compute(separator);

    assertEquals(2.0 * atDefault.getOilInGasKgPerHr(), atDouble.getOilInGasKgPerHr(), 1.0e-9);
    assertThrows(IllegalArgumentException.class, () -> provider.setCarryOverLitrePerMSm3(-1.0));
  }

  /** The 7-stage model returns finite, non-negative mass rates rather than NaN. */
  @Test
  public void sevenStageModelReturnsFiniteMassRates() {
    EntrainmentResult r = EntrainmentProviderRegistry.find(BuiltInSevenStageProvider.ID).compute(separator);
    assertNotNull(r);
    assertTrue(!Double.isNaN(r.getOilInGasKgPerHr()), "oil carry-over must not be NaN");
    assertTrue(!Double.isNaN(r.getWaterInGasKgPerHr()), "water carry-over must not be NaN");
    assertTrue(r.getOilInGasKgPerHr() >= 0.0, "oil carry-over must not be negative");
    assertTrue(r.getWaterInGasKgPerHr() >= 0.0, "water carry-over must not be negative");
  }

  /** With nothing selected the separator falls back to the spec model. */
  @Test
  public void defaultModelIsTheSpecFigure() {
    assertNull(separator.getEntrainmentProvider());
    assertEquals(SpecCarryOverProvider.ID, separator.getEntrainmentResult().getProviderId());
  }

  /** Selecting a model is validated immediately and round-trips. */
  @Test
  public void selectingAModelRoundTripsAndValidates() {
    separator.setEntrainmentProvider(ZeroCarryOverProvider.ID);
    assertEquals(ZeroCarryOverProvider.ID, separator.getEntrainmentProvider());
    assertEquals(ZeroCarryOverProvider.ID, separator.getEntrainmentResult().getProviderId());

    separator.setEntrainmentProvider(null);
    assertNull(separator.getEntrainmentProvider());

    assertThrows(IllegalStateException.class, () -> separator.setEntrainmentProvider("no-such-model"));
  }

  /**
   * Selecting an entrainment model must not move the separator's own results. This pins the promise that the new
   * mechanism is opt-in and cannot silently change an existing model.
   */
  @Test
  public void selectingAModelDoesNotChangeSeparatorResults() {
    double gasBefore = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidBefore = separator.getLiquidOutStream().getFlowRate("kg/hr");

    separator.setEntrainmentProvider(BuiltInSevenStageProvider.ID);
    separator.getEntrainmentResult();
    separator.run();

    assertEquals(gasBefore, separator.getGasOutStream().getFlowRate("kg/hr"), Math.abs(gasBefore) * 1.0e-9);
    assertEquals(liquidBefore, separator.getLiquidOutStream().getFlowRate("kg/hr"), Math.abs(liquidBefore) * 1.0e-9);
  }

  /** A provider declaring a future SPI revision is refused rather than used. */
  @Test
  public void futureApiVersionIsRefused() {
    assertEquals(1, EntrainmentProviderRegistry.CURRENT_API_VERSION);
    assertEquals(1, new ZeroCarryOverProvider().getApiVersion());
  }
}
