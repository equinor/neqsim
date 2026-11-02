package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies unchanged pure-term requests and portable baseline values without timing assertions.
 *
 * <p>
 * The local baseline/candidate probe separately requires exact bit parity. This regression uses a fixed 1e-12 mixed
 * absolute/relative band for platform math-library differences.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalRowPureRequestTest {
  /**
   * Preserves legacy values for canonical, distinct and padded raw selector strings.
   *
   * @throws Exception if the baseline resource cannot be read
   */
  @Test
  void rawSelectorsPreserveLegacyActivityAndDerivativeValues() throws Exception {
    StringWriter captured = new StringWriter();
    try (PrintWriter out = new PrintWriter(captured)) {
      out.println("eos,init,selector,component,gamma,lnGamma,dlnT,dlnTT,dlnN0,dlnN1");
      for (boolean pr : new boolean[] {false, true}) {
        for (int type : new int[] {1, 3}) {
          for (String label : new String[] {"HV", "paddedHV", "Classic", "paddedClassic", "distinctHV"}) {
            SystemInterface fluid = pr ? new SystemPrEos(350.0, 10.0) : new SystemSrkEos(350.0, 10.0);
            fluid.addComponent("water", .5);
            fluid.addComponent("methane", .5);
            fluid.setMixingRule(2);
            fluid.setForceSinglePhase(PhaseType.GAS);
            fluid.init(3);
            PhaseInterface phase = fluid.getPhase(0);
            phase.setInitType(type);
            String selector = label.equals("paddedHV") ? " HV "
                : label.equals("paddedClassic") ? " Classic "
                    : label.equals("distinctHV") ? new String(new char[] {'H', 'V'}) : label;
            String[][] rules = new String[2][2];
            for (String[] row : rules) {
              Arrays.fill(row, selector);
            }
            double[][] zero = new double[2][2];
            for (int i = 0; i < 2; i++) {
              ComponentGENRTLmodifiedHV activity = new ComponentGENRTLmodifiedHV(i == 0 ? "water" : "methane", .5, .5,
                  i);
              double gamma = activity.getGamma(phase, 2, 350, 10, PhaseType.GAS, zero, zero, zero, zero, rules);
              out.printf("%s,%d,%s,%d,%s,%s,%s,%s,%s,%s%n", pr ? "PR" : "SRK", type, label, i,
                  Double.toHexString(gamma), Double.toHexString(activity.getLnGamma()),
                  Double.toHexString(activity.getLnGammadt()), Double.toHexString(activity.getLnGammadtdt()),
                  Double.toHexString(activity.getLnGammadn(0)), Double.toHexString(activity.getLnGammadn(1)));
            }
          }
        }
      }
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/thermo/mixingrule/hv-row-pure/baseline-selector-probe.csv"),
        StandardCharsets.UTF_8))) {
      List<String> expected = reader.lines().collect(Collectors.toList());
      String[] actual = captured.toString().split("\\R");
      assertEquals(41, expected.size());
      assertEquals(expected.size(), actual.length);
      assertEquals(expected.get(0), actual[0]);
      for (int row = 1; row < actual.length; row++) {
        String[] before = expected.get(row).split(",");
        String[] after = actual[row].split(",");
        assertEquals(before.length, after.length);
        for (int column = 0; column < before.length; column++) {
          if (column < 4) {
            assertEquals(before[column], after[column]);
          } else {
            double expectedValue = Double.parseDouble(before[column]);
            double actualValue = Double.parseDouble(after[column]);
            assertTrue(Double.isFinite(expectedValue) && Double.isFinite(actualValue));
            assertEquals(expectedValue, actualValue, 1e-12 * Math.max(1.0, Math.abs(expectedValue)),
                "Legacy selector row " + row + " field " + column);
          }
        }
      }
    }
  }

  /**
   * Runs ordinary SRK/PR forced-phase probes with complete numeric fingerprints.
   *
   * @throws Exception if the baseline resource cannot be read
   */
  @Test
  void pureRequestsAndBaselineValuesRemainStable() throws Exception {
    StringWriter captured = new StringWriter();
    try (PrintWriter out = new PrintWriter(captured)) {
      out.println("eos,n,init,selection,component,aT,dT,dTT,gamma,lnGamma,dlnT,dlnTT,dlnN");
      for (boolean pr : new boolean[] {false, true}) {
        for (int n : new int[] {5, 15}) {
          for (int type : new int[] {1, 3}) {
            for (String selection : new String[] {"pair", "classic", "hv"}) {
              SystemInterface fluid = pr ? new SystemPrEos(350.0, 10.0) : new SystemSrkEos(350.0, 10.0);
              String[] names = {"water", "CO2", "methane", "methanol", "MEG", "ethane", "propane", "i-butane",
                  "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "nitrogen"};
              for (int i = 0; i < n; i++) {
                fluid.addComponent(names[i], 1.0 / n);
              }
              fluid.setMixingRule(2);
              fluid.setForceSinglePhase(PhaseType.GAS);
              fluid.init(3);
              PhaseInterface phase = fluid.getPhase(0);
              phase.setInitType(type);
              ComponentEosInterface[] wrapped = new ComponentEosInterface[phase.getcomponentArray().length];
              long[][] count = new long[n][3];
              for (int i = 0; i < n; i++) {
                final int index = i;
                final ComponentInterface original = phase.getComponent(i);
                wrapped[i] = (ComponentEosInterface) Proxy.newProxyInstance(
                    HuronVidalRowPureRequestTest.class.getClassLoader(), new Class<?>[] {ComponentEosInterface.class},
                    (proxy, method, arguments) -> {
                      if (method.getName().equals("aT"))
                        count[index][0]++;
                      if (method.getName().equals("diffaT"))
                        count[index][1]++;
                      if (method.getName().equals("diffdiffaT"))
                        count[index][2]++;
                      return method.invoke(original, arguments);
                    });
              }
              phase.setComponentArray(wrapped);
              double[][] zero = new double[n][n];
              String[][] rules = new String[n][n];
              for (int i = 0; i < n; i++) {
                Arrays.fill(rules[i], selection.equals("hv") ? "HV" : "Classic");
              }
              if (selection.equals("pair")) {
                rules[0][1] = "HV";
                rules[1][0] = "HV";
              }
              for (int i = 0; i < n; i++) {
                for (long[] row : count)
                  Arrays.fill(row, 0);
                ComponentGENRTLmodifiedHV activity = new ComponentGENRTLmodifiedHV(names[i], 1.0 / n, 1.0 / n, i);
                double gamma = activity.getGamma(phase, n, 350, 10, PhaseType.GAS, zero, zero, zero, zero, rules);
                long a = 0, dt = 0, dtt = 0;
                for (long[] row : count) {
                  a += row[0];
                  dt += row[1];
                  dtt += row[2];
                }
                if (selection.equals("hv")) {
                  assertEquals(0L, a + dt + dtt, "HV-only paths must remain lazy");
                }
                StringBuilder dn = new StringBuilder();
                for (int j = 0; j < n; j++) {
                  if (j > 0)
                    dn.append(';');
                  dn.append(Double.toHexString(activity.getLnGammadn(j)));
                }
                out.printf("%s,%d,%d,%s,%d,%d,%d,%d,%s,%s,%s,%s,%s%n", pr ? "PR" : "SRK", n, type, selection, i, a, dt,
                    dtt, Double.toHexString(gamma), Double.toHexString(activity.getLnGamma()),
                    Double.toHexString(activity.getLnGammadt()), Double.toHexString(activity.getLnGammadtdt()), dn);
              }
            }
          }
        }
      }
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        getClass().getResourceAsStream("/neqsim/thermo/mixingrule/hv-row-pure/baseline-probe.csv"),
        StandardCharsets.UTF_8))) {
      List<String> expected = reader.lines().collect(Collectors.toList());
      String[] actual = captured.toString().split("\\R");
      assertEquals(241, expected.size());
      assertEquals(expected.size(), actual.length);
      assertEquals(expected.get(0), actual[0]);
      for (int i = 1; i < actual.length; i++) {
        String[] before = expected.get(i).split(",");
        String[] after = actual[i].split(",");
        assertEquals(before.length, after.length);
        for (int k = 0; k < before.length; k++) {
          if (k < 8) {
            assertEquals(before[k], after[k], "Baseline identity/request row " + i + " field " + k);
          } else if (k > 7) {
            String[] expectedValues = before[k].split(";");
            String[] actualValues = after[k].split(";");
            assertEquals(expectedValues.length, actualValues.length);
            for (int value = 0; value < expectedValues.length; value++) {
              double expectedValue = Double.parseDouble(expectedValues[value]);
              double actualValue = Double.parseDouble(actualValues[value]);
              assertTrue(Double.isFinite(expectedValue) && Double.isFinite(actualValue));
              assertEquals(expectedValue, actualValue, 1e-12 * Math.max(1.0, Math.abs(expectedValue)),
                  "Baseline numeric row " + i + " field " + k + " value " + value);
            }
          }
        }
      }
    }
  }
}
