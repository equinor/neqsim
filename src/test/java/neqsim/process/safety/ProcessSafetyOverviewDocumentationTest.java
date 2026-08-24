package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.RuptureDisk;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.hipps.HIPPSLogic;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class ProcessSafetyOverviewDocumentationTest {
  @Test
  void documentedSafetyEquipmentUsesStreamInputsAndExplicitUnits() throws Exception {
    Stream feed = createFeed();

    SafetyValve safetyValve = new SafetyValve("PSV-100", feed);
    safetyValve.setPressureSpec(75.0);
    safetyValve.setFullOpenPressure(82.5);
    safetyValve.setBlowdown(7.0);
    safetyValve.setOutletPressure(1.5, "bara");

    RuptureDisk ruptureDisk = new RuptureDisk("RD-100", feed);
    ruptureDisk.setBurstPressure(85.0);
    ruptureDisk.setFullOpenPressure(89.25);
    ruptureDisk.setOutletPressure(1.5, "bara");

    assertSame(feed, safetyValve.getInletStream());
    assertSame(feed, ruptureDisk.getInletStream());
    assertEquals(69.75, safetyValve.getBlowdownPressure(), 1.0e-9);
    assertEquals(85.0, ruptureDisk.getBurstPressure(), 0.0);
    assertSame(StreamInterface.class,
        SafetyValve.class.getConstructor(String.class, StreamInterface.class).getParameterTypes()[1]);
    assertSame(StreamInterface.class,
        RuptureDisk.class.getConstructor(String.class, StreamInterface.class).getParameterTypes()[1]);
  }

  @Test
  void documentedEsdSequenceClosesIsolationAndOpensBlowdown() {
    Stream feed = createFeed();

    ESDValve inletIsolation = new ESDValve("ESD-XV-100", feed);
    inletIsolation.setStrokeTime(2.0);
    inletIsolation.setCv(500.0);
    inletIsolation.setOutletPressure(75.0, "bara");
    inletIsolation.setCalculateSteadyState(false);
    inletIsolation.energize();

    BlowdownValve blowdownValve = new BlowdownValve("BDV-100", feed);
    blowdownValve.setOpeningTime(2.0);
    blowdownValve.setCv(100.0);
    blowdownValve.setOutletPressure(1.5, "bara");
    blowdownValve.setCalculateSteadyState(false);

    ESDLogic esdLogic = new ESDLogic("ESD level 1");
    esdLogic.addAction(new TripValveAction(inletIsolation), 0.0);
    esdLogic.addAction(new ActivateBlowdownAction(blowdownValve), 0.0);
    esdLogic.activate();

    UUID calculationId = UUID.randomUUID();
    double timeStepSeconds = 0.5;
    for (int step = 0; step < 20 && !esdLogic.isComplete(); step++) {
      esdLogic.execute(timeStepSeconds);
      inletIsolation.runTransient(timeStepSeconds, calculationId);
      blowdownValve.runTransient(timeStepSeconds, calculationId);
    }

    assertEquals(LogicState.COMPLETED, esdLogic.getState());
    assertTrue(inletIsolation.hasTripCompleted());
    assertTrue(inletIsolation.getPercentValveOpening() <= 1.0);
    assertTrue(blowdownValve.isActivated());
    assertTrue(blowdownValve.getPercentValveOpening() > 90.0);
  }

  @Test
  void hippsDocumentationNamesTheCurrentLogicTypes() throws Exception {
    assertSame(VotingLogic.class,
        HIPPSLogic.class.getConstructor(String.class, VotingLogic.class).getParameterTypes()[1]);
    assertSame(void.class, HIPPSLogic.class.getMethod("setIsolationValve", ThrottlingValve.class).getReturnType());
    assertSame(void.class, HIPPSLogic.class.getMethod("update", double[].class).getReturnType());
  }

  private static Stream createFeed() {
    SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("safety feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }
}
