package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Scenario tests (exercise only) for GibbsReactorCO2 using the tables provided in the attachments.
 * These tests run the reactor and print outlet mole-fractions (ppm) for manual verification. They
 * assert basic sanity (outlet present and components exist).
 */
public class GibbsReactorCO2ScenariosTest {

  /**
   * Run reactor and return the outlet thermo system.
   */
  private SystemInterface runReactor(SystemInterface system) {
    Stream inlet = new Stream("Inlet Stream", system);
    inlet.run();

    GibbsReactorCO2 reactor = new GibbsReactorCO2("GibbsReactorCO2", inlet);
    reactor.run();

    Stream outlet = (Stream) reactor.getOutletStream();
    return outlet != null ? outlet.getThermoSystem() : null;
  }

  /** Print composition (ppm) of a thermo system for diagnostics. */
  private void printComposition(SystemInterface outSys, String label) {
    System.out.println("\n--- Scenario: " + label + " ---");
    if (outSys == null) {
      System.out.println("Outlet system is null");
      return;
    }
    for (int i = 0; i < outSys.getNumberOfComponents(); i++) {
      double ppm = outSys.getComponent(i).getz() * 1e6;
      if (ppm > 1e-6) {
        System.out.printf("%s: %.6f ppm\n", outSys.getComponent(i).getComponentName(), ppm);
      }
    }
  }

  /** Assert selected component ppm values against expected with given tolerance. */
  private void assertSelectedPpm(SystemInterface outSys, String[] names, double[] expectedPpm,
      double tol, String label) {
    Assertions.assertNotNull(outSys, "Outlet thermo system should not be null for " + label);
    for (int idx = 0; idx < names.length; idx++) {
      String compName = names[idx];
      double expect = expectedPpm[idx];
      int compIndex = -1;
      for (int i = 0; i < outSys.getNumberOfComponents(); i++) {
        if (outSys.getComponent(i).getComponentName().equalsIgnoreCase(compName)) {
          compIndex = i;
          break;
        }
      }
      if (compIndex < 0) {
        Assertions.fail("Component " + compName + " not found in outlet for scenario " + label);
      }
      double actualPpm = outSys.getComponent(compIndex).getz() * 1e6;
      Assertions.assertEquals(expect, actualPpm, tol,
          "Component " + compName + " in scenario " + label + " differs from expected ppm");
    }
  }

  /**
   * Create a base SystemSrkEos with CO2 = 1e6 moles/sec and other components initialized to 0.
   * Temperature in K and pressure in bara.
   */
  private SystemSrkEos createBaseSystem(double temperatureK, double pressureBara) {
    SystemSrkEos sys = new SystemSrkEos(temperatureK, pressureBara);
    // CO2 must be 1e6 moles/sec in all cases
    sys.addComponent("CO2", 1e6, "mole/sec");
    // Initialize other common components to 0 explicitly
    String[] comps = new String[] {"SO2", "SO3", "NO2", "NO", "water", "H2S", "oxygen",
        "sulfuric acid", "nitric acid", "NH4NO3", "NH4HSO4", "formic acid", "acetic acid",
        "methanol", "ethanol", "CO", "NH2OH", "S8", "HNO2"};
    for (String c : comps) {
      try {
        sys.addComponent(c, 0.0, "mole/sec");
      } catch (Exception ignored) {
        // ignore components not present in database
      }
    }
    sys.setMixingRule(2);
    return sys;
  }

  // Scenario 1
  @Test
  public void scenario1() {
    SystemSrkEos sys = createBaseSystem(275.15, 100.0);
    // overlay scenario-specific non-zero components
    sys.addComponent("water", 50.0);
    sys.addComponent("NO2", 10.0);
    sys.addComponent("oxygen", 30.0);
    double[] expectedPpm = new double[] {48.6, 0.0, 7.3, 30.0, 0.0, 0.0, 0.82, 1.9};
    String[] expectedNames =
        new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO", "nitric acid", "HNO2"};
    runAndPrintWithAssertions(sys, "1", expectedNames, expectedPpm);
  }

  // Scenario 2
  @Test
  public void scenario2() {
    SystemSrkEos sys = createBaseSystem(275.15, 100.0);
    sys.addComponent("water", 50.0);
    sys.addComponent("SO2", 1.0);
    sys.addComponent("NO2", 10.0);
    sys.addComponent("oxygen", 30.0);
    double[] expectedPpm = new double[] {48.6, 1.0, 7.3, 30.0, 0.0, 0.0, 0.82, 1.9};
    String[] expectedNames =
        new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO", "nitric acid", "HNO2"};
    runAndPrintWithAssertions(sys, "2", expectedNames, expectedPpm);
  }

  // Scenario 3
  @Test
  public void scenario3() {
    SystemSrkEos sys = createBaseSystem(275.15, 100.0);
    sys.addComponent("water", 50.0);
    sys.addComponent("SO2", 1.0);
    sys.addComponent("NO2", 10.0);
    sys.addComponent("oxygen", 30.0);
    sys.addComponent("NO", 10.0);
    double[] expectedPpm = new double[] {47.3, 1.0, 14.5, 25.5, 0.0, 0.0, 1.5, 3.86};
    String[] expectedNames =
        new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO", "nitric acid", "HNO2"};
    runAndPrintWithAssertions(sys, "3", expectedNames, expectedPpm);
  }

  // Scenario 4
  @Test
  public void scenario4() {
    SystemSrkEos sys = createBaseSystem(275.15, 100.0);
    sys.addComponent("water", 50.0);
    sys.addComponent("SO2", 1.0);
    sys.addComponent("NO2", 10.0);
    sys.addComponent("oxygen", 80.0);
    sys.addComponent("NO", 10.0);
    double[] expectedPpm = new double[] {47.0, 1.0, 14.8, 75.0, 0.0, 0.0, 2.11, 3.02};
    String[] expectedNames =
        new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO", "nitric acid", "HNO2"};
    runAndPrintWithAssertions(sys, "4", expectedNames, expectedPpm);
  }

  // Scenario 5
  @Test
  public void scenario5() {
    SystemSrkEos sys = createBaseSystem(248.15, 70.0);
    sys.addComponent("water", 92.0);
    sys.addComponent("SO2", 37.0);
    sys.addComponent("oxygen", 133.0);
    sys.addComponent("H2S", 28.0);
    double[] expectedPpm = new double[] {92.0, 37.0, 0.0, 133.0, 28.0};
    String[] expectedNames = new String[] {"water", "SO2", "NO2", "oxygen", "H2S"};
    runAndPrintWithAssertions(sys, "5", expectedNames, expectedPpm);
  }

  // Scenario 6
  @Test
  public void scenario6() {
    SystemSrkEos sys = createBaseSystem(248.15, 20.0);
    sys.addComponent("water", 10.0);
    sys.addComponent("SO2", 10.0);
    sys.addComponent("H2S", 19.0);
    double[] expectedPpm = new double[] {10.0, 10.0, 0.0, 0.0, 19.0};
    String[] expectedNames = new String[] {"water", "SO2", "NO2", "oxygen", "H2S"};
    runAndPrintWithAssertions(sys, "6", expectedNames, expectedPpm);
  }

  // Scenario 7
  @Test
  public void scenario7() {
    SystemSrkEos sys = createBaseSystem(248.15, 20.0);
    sys.addComponent("water", 9.5);
    sys.addComponent("SO2", 10.0);
    sys.addComponent("NO2", 10.0);
    sys.addComponent("oxygen", 10.0);
    sys.addComponent("H2S", 10.0);
    double[] expectedPpm =
        new double[] {13.7, 16.4, 0.0, 0.0, 0.0, 0.0, 8.55, 0.0, 2.16, 1.44, 0.0};
    String[] expectedNames = new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO",
        "nitric acid", "HNO2", "sulfuric acid", "NH4HSO4", "NH4NO3"};
    runAndPrintWithAssertions(sys, "7", expectedNames, expectedPpm);
  }

  // Scenario 8
  @Test
  public void scenario8() {
    SystemSrkEos sys = createBaseSystem(298.15, 100.0);
    sys.addComponent("water", 130.0);
    sys.addComponent("SO2", 300.0);
    sys.addComponent("oxygen", 275.0);
    double[] expectedPpm = new double[] {130.0, 300.0, 0.0, 275.0};
    String[] expectedNames = new String[] {"water", "SO2", "NO2", "oxygen"};
    runAndPrintWithAssertions(sys, "8", expectedNames, expectedPpm);
  }


  // Scenario 9
  @Test
  public void scenario9() {
    SystemSrkEos sys = createBaseSystem(25 + 273.15, 100.0);
    sys.addComponent("water", 100.0);
    sys.addComponent("SO2", 0.0);
    sys.addComponent("NO2", 100.0);
    sys.addComponent("oxygen", 300.0);
    double[] expectedPpm = new double[] {48.6, 1.0, 7.3, 30.0, 0.0, 0.0, 0.82, 1.9};
    String[] expectedNames =
        new String[] {"water", "SO2", "NO2", "oxygen", "H2S", "NO", "nitric acid", "HNO2"};
    runAndPrintWithAssertions(sys, "2", expectedNames, expectedPpm);
  }

  /**
   * Run reactor and assert selected component ppm values against expected with tolerance.
   */
  private void runAndPrintWithAssertions(SystemInterface system, String label, String[] names,
      double[] expectedPpm) {
    SystemInterface outSys = runReactor(system);
    printComposition(outSys, label);
    assertSelectedPpm(outSys, names, expectedPpm, 0.5, label);
  }
}
