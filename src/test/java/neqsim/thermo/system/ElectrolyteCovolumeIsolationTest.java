package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseElectrolyteCPA;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

class ElectrolyteCovolumeIsolationTest {
  private double[] originalFurst;
  private double[] originalCpa;

  @BeforeEach
  void isolateGlobalCustomization() {
    originalFurst = FurstElectrolyteConstants.furstParams;
    originalCpa = FurstElectrolyteConstants.furstParamsCPA;
    FurstElectrolyteConstants.furstParams = new double[] {1.117e-7, 5.3771e-6, 6.99219e-5, 4.3984e-6, -6.06e-8,
        -2.1795e-5};
    FurstElectrolyteConstants.furstParamsCPA = originalCpa.clone();
  }

  @AfterEach
  void restoreGlobalCustomization() {
    FurstElectrolyteConstants.furstParams = originalFurst;
    FurstElectrolyteConstants.furstParamsCPA = originalCpa;
  }

  private SystemInterface cpa(int variant) {
    if (variant == 0) {
      return new SystemElectrolyteCPA(298.15, 1.0);
    }
    if (variant == 1) {
      return new SystemElectrolyteCPAstatoil(298.15, 1.0);
    }
    return new SystemElectrolyteCPAAdvanced(298.15, 1.0);
  }

  private SystemInterface brine(SystemInterface fluid) {
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1.0);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule(fluid.getPhase(0) instanceof PhaseElectrolyteCPA ? 10 : 4);
    fluid.init(0);
    return fluid;
  }

  private double b(SystemInterface fluid) {
    return ((ComponentEosInterface) fluid.getPhase(0).getComponent("Na+")).getb();
  }

  private void reinitialize(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      ((PhaseModifiedFurstElectrolyteEos) fluid.getPhase(phase)).reInitFurstParam();
    }
  }

  @Test
  void constructingEveryCpaVariantLeavesScrkDefaultsUntouched() {
    SystemInterface first = brine(new SystemFurstElectrolyteEos());
    double expected = b(first);
    PhaseModifiedFurstElectrolyteEos firstPhase = (PhaseModifiedFurstElectrolyteEos) first.getPhase(0);
    double waterIon = firstPhase.getElectrolyteMixingRule().getWij(0, 1, 298.15);
    double ionIon = firstPhase.getElectrolyteMixingRule().getWij(1, 2, 298.15);
    double[] defaults = FurstElectrolyteConstants.furstParams;
    for (int variant = 0; variant < 3; variant++) {
      SystemInterface cpa = brine(cpa(variant));
      assertSame(defaults, FurstElectrolyteConstants.furstParams);
      assertNotEquals(expected, b(cpa));
      assertEquals(expected, b(brine(new SystemFurstElectrolyteEos())), 0.0);
      reinitialize(first);
      assertEquals(expected, b(first), 0.0);
      assertEquals(waterIon, firstPhase.getElectrolyteMixingRule().getWij(0, 1, 298.15), 0.0);
      assertEquals(ionIon, firstPhase.getElectrolyteMixingRule().getWij(1, 2, 298.15), 0.0);
    }
  }

  @Test
  void delayedIonAdditionAndReinitializationUseTheComponentModel() {
    double expectedScrk = b(brine(new SystemFurstElectrolyteEos()));
    for (int variant = 0; variant < 3; variant++) {
      SystemInterface scrk = new SystemFurstElectrolyteEos();
      SystemInterface cpa = cpa(variant);
      brine(scrk);
      brine(cpa);
      double expectedCpa = b(cpa);
      new SystemFurstElectrolyteEos();
      reinitialize(cpa);
      reinitialize(scrk);
      assertEquals(expectedScrk, b(scrk), 0.0);
      assertEquals(expectedCpa, b(cpa), 0.0);
    }
  }

  @Test
  void cpaComponentsSelectCpaCovolumeWithoutConstructingASystem() {
    ComponentElectrolyteCPA sodium = new ComponentElectrolyteCPA("Na+", 1.0, 1.0, 0);
    double expected = (FurstElectrolyteConstants.getFurstParamCPA(0) * Math.pow(sodium.getIonicDiameter(), 3.0)
        + FurstElectrolyteConstants.getFurstParamCPA(1)) * 1e5;
    assertEquals(expected, sodium.getb(), 0.0);
    sodium.initFurstParam();
    assertEquals(expected, sodium.getb(), 0.0);
  }

  @Test
  void cloneAndSerializationRetainModelIdentity() throws Exception {
    SystemInterface scrk = brine(new SystemFurstElectrolyteEos());
    double expected = b(scrk);
    for (SystemInterface original : new SystemInterface[] {scrk, brine(cpa(0)), brine(cpa(1)), brine(cpa(2))}) {
      double originalB = b(original);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
        out.writeObject(original);
      }
      SystemInterface restored;
      try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
        restored = (SystemInterface) in.readObject();
      }
      SystemInterface clone = original.clone();
      reinitialize(clone);
      reinitialize(restored);
      assertEquals(originalB, b(clone), 0.0);
      assertEquals(originalB, b(restored), 0.0);
    }
    assertEquals(expected, b(brine(new SystemFurstElectrolyteEos())), 0.0);
  }

  @Test
  void deliberateCovolumeCustomizationUsesSeparateModelTables() {
    FurstElectrolyteConstants.setFurstParam(0, 1.2e-7);
    FurstElectrolyteConstants.setFurstParamCPA(0, 2.3e-7);
    SystemInterface scrk = brine(new SystemFurstElectrolyteEos());
    double scrkB = b(scrk);
    SystemInterface cpa = brine(cpa(0));
    double cpaB = b(cpa);
    FurstElectrolyteConstants.setFurstParamCPA(0, 2.4e-7);
    reinitialize(scrk);
    reinitialize(cpa);
    assertEquals(scrkB, b(scrk), 0.0);
    assertNotEquals(cpaB, b(cpa));
    cpaB = b(cpa);
    FurstElectrolyteConstants.setFurstParam(0, 1.3e-7);
    reinitialize(cpa);
    reinitialize(scrk);
    assertEquals(cpaB, b(cpa), 0.0);
    assertNotEquals(scrkB, b(scrk));
  }
}
