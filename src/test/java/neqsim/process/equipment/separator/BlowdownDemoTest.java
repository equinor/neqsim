package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Demo: steady-state run of a separator, then dynamic blowdown.
 *
 * <p>
 * Builds an HP separator at 80 bara fed with methane / nC10, runs steady state, then closes the
 * inlet valve, opens the blowdown valve, and time-steps the separator depressurization. Prints
 * pressure vs time so the result table appears in the test output.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class BlowdownDemoTest {

  @Test
  void steadyStateThenBlowdown() {
    // ---------- 1. Build steady-state model ----------
    SystemInterface gas = new SystemSrkEos(298.15, 80.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("nC10", 0.1);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(80.0, "bara");
    feed.setTemperature(25.0, "C");

    Separator sep = new Separator("V-100", feed);
    sep.setInternalDiameter(2.0);
    sep.setSeparatorLength(6.0);
    sep.setCalculateSteadyState(true);

    ThrottlingValve bdValve = new ThrottlingValve("BDV-100", sep.getGasOutStream());
    bdValve.setOutletPressure(1.013);
    bdValve.setCv(120.0);
    bdValve.setPercentValveOpening(0.0); // closed at steady state

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(sep);
    ps.add(bdValve);

    ps.run();

    double pStart = sep.getThermoSystem().getPressure("bara");
    double tStart = sep.getThermoSystem().getTemperature("C");
    System.out.println();
    System.out.println("=== STEADY STATE ===");
    System.out.printf("Separator pressure   : %7.2f bara%n", pStart);
    System.out.printf("Separator temperature: %7.2f C%n", tStart);
    System.out.printf("Vessel volume        : %7.2f m3%n", Math.PI / 4.0 * 2.0 * 2.0 * 6.0);

    // ---------- 2. Switch to dynamic, isolate inlet, open BDV ----------
    sep.setCalculateSteadyState(false);
    feed.setFlowRate(0.0, "kg/hr");
    feed.run();
    bdValve.setPercentValveOpening(100.0); // open blowdown to atmosphere

    // ---------- 3. Time-step blowdown ----------
    System.out.println();
    System.out.println("=== BLOWDOWN ===");
    System.out.printf("%6s  %10s  %10s  %12s  %12s%n", "t [s]", "P [bara]", "T [C]", "BDV [kg/hr]",
        "n [kmol]");

    double dt = 5.0;
    int steps = 60; // 5 min total
    double pPrev = pStart;
    int decreasing = 0;

    for (int i = 0; i <= steps; i++) {
      if (i > 0) {
        ps.runTransient(dt);
      }
      double p = sep.getThermoSystem().getPressure("bara");
      double t = sep.getThermoSystem().getTemperature("C");
      double mFlow = bdValve.getOutletStream().getFlowRate("kg/hr");
      double moles = sep.getThermoSystem().getTotalNumberOfMoles() / 1000.0;
      if (i % 4 == 0) {
        System.out.printf("%6.1f  %10.2f  %10.2f  %12.1f  %12.3f%n", i * dt, p, t, mFlow, moles);
      }
      if (i > 0 && p < pPrev - 1e-6) {
        decreasing++;
      }
      pPrev = p;
    }

    double pEnd = sep.getThermoSystem().getPressure("bara");
    System.out.println();
    System.out.printf("Pressure dropped: %.2f -> %.2f bara (%.2f bar)%n", pStart, pEnd,
        pStart - pEnd);

    // Sanity: pressure must drop monotonically over the blowdown
    assertTrue(pEnd < pStart,
        "Pressure should decrease during blowdown: start=" + pStart + " end=" + pEnd);
    assertTrue(decreasing > steps / 2,
        "Pressure should be strictly decreasing for most steps; got " + decreasing + "/" + steps);
  }
}
