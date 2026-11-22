package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhasePrEosvolcor;

/** A translated liquid root must satisfy the same cubic as its untranslated PR counterpart. */
class PrTranslatedLiquidRootTest {
  @Test
  void liquidRootBelowCovolumeRemainsOnTheLiquidBranch() {
    SystemInterface reference = new SystemPrEos(280.0, 1.0);
    SystemInterface translated = new SystemPrEosvolcor(280.0, 1.0);
    for (SystemInterface fluid : new SystemInterface[] {reference, translated}) {
      fluid.addComponent("cumene", 1.0);
      fluid.setMixingRule("classic");
      fluid.init(0);
      fluid.setBeta(0.5);
      fluid.init(3);
    }
    PhasePrEosvolcor liquid = (PhasePrEosvolcor) translated.getPhase(1);
    double volume = liquid.getMolarVolume();
    double covolume = liquid.getB() / liquid.getNumberOfMolesInPhase();
    assertTrue(volume > 0.0 && volume < covolume, "physical translated liquid root lies below B/n");
    assertEquals(reference.getPhase(1).getMolarVolume(), volume + liquid.getc(), 1.0e-7);
    assertTrue(liquid.dFdVdV() > 0.0, "liquid root must be mechanically stable");
  }
}
