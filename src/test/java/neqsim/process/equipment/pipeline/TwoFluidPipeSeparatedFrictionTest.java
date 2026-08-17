package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the separated friction model against the mixture correlation it replaces.
 *
 * <p>
 * The pressure march charges friction with a hold-up weighted mixture density over the whole perimeter. That is a
 * dispersed-flow description; in a separated flow it applies a liquid-dominated density to a bore that is mostly gas.
 * At high liquid hold-up it over-predicts the pressure drop severely, which is why the separated form exists.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TwoFluidPipeSeparatedFrictionTest {
  /**
   * Builds a horizontal liquid-loaded pipe.
   *
   * @param separated true to select the separated friction model
   * @return a pipe ready to run
   */
  private TwoFluidPipe buildPipe(boolean separated) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 57.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50.0 * 3600.0, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(57.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(20);
    pipe.setElevationProfile(new double[20]);
    pipe.setSeparatedFrictionModel(separated);
    return pipe;
  }

  /**
   * The mixture correlation must remain the default so existing results do not move.
   */
  @Test
  @DisplayName("Separated friction model is off by default")
  void testSeparatedFrictionIsOffByDefault() {
    Assertions.assertFalse(buildPipe(false).isSeparatedFrictionModel(),
        "the separated friction model must stay opt-in until the hold-up deficit is closed");
    Assertions.assertTrue(buildPipe(true).isSeparatedFrictionModel(), "the selection must be honoured");
  }

  /**
   * On a liquid-loaded line the mixture correlation must predict the larger pressure drop.
   *
   * <p>
   * This is the signature of the defect the separated model addresses: charging the whole perimeter with a
   * liquid-weighted density inflates the friction once the liquid hold-up is appreciable.
   * </p>
   */
  @Test
  @DisplayName("Mixture friction over-predicts pressure drop against the separated form")
  void testMixtureFrictionExceedsSeparatedOnLiquidLoadedLine() {
    TwoFluidPipe mixture = buildPipe(false);
    mixture.run();
    double mixtureDrop = mixture.getInletPressure() - mixture.getOutletPressure();

    TwoFluidPipe separated = buildPipe(true);
    separated.run();
    double separatedDrop = separated.getInletPressure() - separated.getOutletPressure();

    Assertions.assertTrue(mixtureDrop > 0.0 && separatedDrop > 0.0,
        "both models must produce a positive pressure drop, got " + mixtureDrop + " and " + separatedDrop);
    Assertions.assertTrue(separatedDrop < mixtureDrop,
        "the mixture correlation must be the larger of the two on a liquid-loaded line, but got separated "
            + separatedDrop + " bar against mixture " + mixtureDrop + " bar");
  }
}
