package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentPow10KPaVaporPressureTest;
import neqsim.thermo.component.ComponentSrk;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseGEUniquac;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUEos;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;

/** Small explicit adapters that drive production calculations and inspect published state. */
final class ModelSpecFixtures {
  private ModelSpecFixtures() {
  }

  /** Explicit type binding for inventory reconciliation; never construct a discovered class. */
  static Class<?> type(ModelSpec.Fixture fixture) {
    switch (fixture) {
    case SATURATION:
    case ANTOINE_ANALYTIC:
      return ComponentSrk.class;
    case WILSON_ANALYTIC:
      return SystemGEWilson.class;
    case UNIFAC:
      return SystemUNIFAC.class;
    case PSRK:
      return SystemUNIFACpsrk.class;
    case UMR:
      return SystemUMRPRUEos.class;
    case SRK:
      return SystemSrkEos.class;
    case PR:
      return SystemPrEos.class;
    case SRK_PHASE:
      return PhaseSrkEos.class;
    case PR_PHASE:
      return PhasePrEos.class;
    case UNIQUAC:
      return PhaseGEUniquac.class;
    default:
      throw new IllegalArgumentException("unmapped fixture " + fixture);
    }
  }

  static void validate(ModelSpec s) {
    String unit = s.property == ModelSpec.Property.PSAT ? "bar"
        : s.property == ModelSpec.Property.DPSAT_DT ? "bar/K"
            : s.property == ModelSpec.Property.T_SAT ? "K" : s.property == ModelSpec.Property.HID ? "J/mol" : "1";
    ModelSpec.require(unit.equals(s.unit), "wrong unit for " + s.property);
    switch (s.fixture) {
    case ANTOINE_ANALYTIC:
      ModelSpec.require((s.property == ModelSpec.Property.DPSAT_DT || s.property == ModelSpec.Property.T_SAT)
          && s.components.size() == 1 && s.components.containsKey("i-pentane") && "pure".equals(s.phase)
          && "none".equals(s.mixingRule) && "prescribed-pow10KPa".equals(s.operation)
          && s.outcome == ModelSpec.Outcome.VALUE, "invalid analytical Antoine fixture");
      break;
    case SATURATION:
      ModelSpec.require(s.property == ModelSpec.Property.PSAT && s.components.size() == 1 && "pure".equals(s.phase)
          && "none".equals(s.mixingRule) && "correlation".equals(s.operation), "invalid saturation fixture");
      ModelSpec.require(s.outcome != ModelSpec.Outcome.UNSUPPORTED, "saturation is implemented");
      ModelSpec.require(s.outcome == ModelSpec.Outcome.VALUE || "missing".equals(s.reason) || "ion".equals(s.reason)
          || "supercritical".equals(s.reason), "unknown absence reason");
      break;
    case UNIQUAC:
      ModelSpec.require(s.property == ModelSpec.Property.GAMMA && s.outcome == ModelSpec.Outcome.UNSUPPORTED
          && "constructor".equals(s.operation) && "none".equals(s.mixingRule) && "liquid".equals(s.phase)
          && "unimplemented".equals(s.reason), "invalid unsupported fixture");
      break;
    case SRK:
    case PR:
      ModelSpec.require((s.property == ModelSpec.Property.Z || s.property == ModelSpec.Property.PHI
          || s.property == ModelSpec.Property.HID) && "gas".equals(s.phase) && "classic".equals(s.mixingRule)
          && "init1".equals(s.operation), "invalid cubic fixture");
      ModelSpec.require(s.outcome == ModelSpec.Outcome.VALUE, "cubic value expected");
      break;
    case SRK_PHASE:
    case PR_PHASE:
      ModelSpec
          .require(
              (s.property == ModelSpec.Property.Z || s.property == ModelSpec.Property.PHI) && s.components.size() == 1
                  && s.components.containsKey("methane") && "gas".equals(s.phase) && "classic".equals(s.mixingRule)
                  && "init1".equals(s.operation) && s.outcome == ModelSpec.Outcome.VALUE,
              "invalid cubic phase fixture");
      break;
    case WILSON_ANALYTIC:
      ModelSpec.require(
          s.components.size() == 2 && s.components.containsKey("methanol") && s.components.containsKey("water")
              && s.components.keySet().iterator().next().equals("methanol"),
          "Wilson fixture requires ordered methanol/water with Lambda12=2 and Lambda21=0.5");
      ModelSpec.require(s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.LN_GAMMA,
          "Wilson property not implemented by fixture");
      validateGe(s, "classic");
      break;
    case UNIFAC:
    case PSRK:
    case UMR:
      ModelSpec.require(s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.GROUP_R,
          "group property not implemented by fixture");
      validateGe(s, s.fixture == ModelSpec.Fixture.UMR ? "HV/UNIFAC_UMRPRU" : "classic");
      break;
    default:
      throw new IllegalArgumentException("unmapped fixture " + s.fixture);
    }
    if (s.outcome == ModelSpec.Outcome.VALUE && s.property != ModelSpec.Property.LN_GAMMA
        && s.property != ModelSpec.Property.HID) {
      ModelSpec.require(s.expected > 0.0, "positive-only property reference must be positive");
    }
  }

  private static void validateGe(ModelSpec s, String mixingRule) {
    ModelSpec.require(s.outcome == ModelSpec.Outcome.VALUE && "liquid".equals(s.phase)
        && mixingRule.equals(s.mixingRule) && "init-ge".equals(s.operation), "invalid GE fixture");
  }

  static double evaluate(ModelSpec s) {
    if (s.fixture == ModelSpec.Fixture.ANTOINE_ANALYTIC) {
      ComponentInterface c = ComponentPow10KPaVaporPressureTest.correlation(0.0, 2.0);
      assertEquals(type(s.fixture), c.getClass(), s.toString());
      assertTrue(c.hasAntoineVaporPressureCorrelation(), s.toString());
      return s.property == ModelSpec.Property.DPSAT_DT ? c.getAntoineVaporPressuredT(s.temperature)
          : c.getAntoineVaporTemperature(s.pressure);
    }
    if (s.fixture == ModelSpec.Fixture.UNIQUAC) {
      new PhaseGEUniquac();
      throw new AssertionError("unsupported UNIQUAC unexpectedly constructed");
    }
    if (s.fixture == ModelSpec.Fixture.SATURATION) {
      ComponentInterface c = new ComponentSrk(s.components.keySet().iterator().next(), 1.0, 1.0, 0);
      assertEquals(type(s.fixture), c.getClass(), s.toString());
      if (s.outcome == ModelSpec.Outcome.UNAVAILABLE) {
        if ("supercritical".equals(s.reason)) {
          assertTrue(c.hasAntoineVaporPressureCorrelation(), s.toString());
          assertTrue(s.temperature > c.getTC(), s.toString());
        } else {
          assertFalse(c.hasAntoineVaporPressureCorrelation(), s.toString());
          if ("ion".equals(s.reason)) {
            assertTrue(c.getIonicCharge() != 0.0, s.toString());
          } else {
            assertEquals(0.0, c.getIonicCharge(), s.toString());
          }
        }
        assertTrue(Double.isNaN(c.getAntoineVaporPressuredT(s.temperature)), s.toString());
        return c.getAntoineVaporPressure(s.temperature);
      }
      assertTrue(c.hasAntoineVaporPressureCorrelation(), s.toString());
      double value = c.getAntoineVaporPressure(s.temperature);
      positive(value, s.toString());
      assertTrue(s.temperature <= c.getTC() && value <= c.getPC() * (1.0 + 1e-8), s.toString());
      return value;
    }
    SystemInterface system = create(s);
    if (s.fixture != ModelSpec.Fixture.SRK_PHASE && s.fixture != ModelSpec.Fixture.PR_PHASE) {
      assertEquals(type(s.fixture), system.getClass(), s.toString());
    }
    system.init(0);
    if (s.fixture == ModelSpec.Fixture.SRK || s.fixture == ModelSpec.Fixture.PR
        || s.fixture == ModelSpec.Fixture.SRK_PHASE || s.fixture == ModelSpec.Fixture.PR_PHASE) {
      system.init(1);
      PhaseInterface phase = system.getPhase(0);
      if (s.fixture == ModelSpec.Fixture.SRK_PHASE || s.fixture == ModelSpec.Fixture.PR_PHASE) {
        assertEquals(type(s.fixture), phase.getClass(), s.toString());
      }
      double phi = phase.getComponent(s.componentIndex).getFugacityCoefficient();
      positive(phi, s.toString());
      if (s.property == ModelSpec.Property.Z) {
        return phase.getZ();
      }
      if (s.property == ModelSpec.Property.PHI) {
        return phi;
      }
      return phase.getComponent(s.componentIndex).getHID(s.temperature);
    }
    PhaseInterface liquid = system.getPhase(1);
    if (s.fixture == ModelSpec.Fixture.UMR) {
      system.init(1);
      positive(liquid.getComponent(s.componentIndex).getFugacityCoefficient(), s.toString());
      liquid = ((EosMixingRulesInterface) ((PhaseEosInterface) liquid).getMixingRule()).getGEPhase();
    }
    if (s.fixture == ModelSpec.Fixture.WILSON_ANALYTIC) {
      for (int i = 0; i < 2; i++) {
        ComponentInterface original = liquid.getComponent(i);
        liquid.getcomponentArray()[i] = new ComponentGEWilson(original.getName(), original.getz(), original.getz(), i) {
          private static final long serialVersionUID = 1L;

          @Override
          public double getCharEnergyParamter(PhaseInterface phase, int first, int second) {
            return first == second ? 1.0 : first == 0 ? 2.0 : 0.5;
          }
        };
        liquid.getComponent(i).setx(original.getz());
      }
    }
    double result = readGe(s, liquid);
    // Read again from the same state; do not merely check that init did not throw.
    assertEquals(result, readGe(s, liquid), 1e-11, s.toString());
    return result;
  }

  private static double readGe(ModelSpec s, PhaseInterface liquid) {
    ComponentGEInterface c = (ComponentGEInterface) liquid.getComponent(s.componentIndex);
    if (c instanceof ComponentGEUnifac) {
      ComponentGEUnifac group = (ComponentGEUnifac) c;
      assertTrue(group.getNumberOfUNIFACgroups() > 0, s.toString());
      assertEquals(group.getNumberOfUNIFACgroups(), group.getUnifacGroups().length, s.toString());
      int actualGroups = 0;
      for (int i = 0; i < group.getNumberOfUNIFACgroups(); i++) {
        assertEquals(group.getUnifacGroups2().get(i).getSubGroup(), group.getUnifacGroup(i).getSubGroup());
        actualGroups += group.getUnifacGroup(i).getN();
      }
      assertTrue(actualGroups > 0, s.toString());
      if ("methanol".equals(group.getName())) {
        assertEquals(15, group.getUnifacGroup(0).getSubGroup(), s.toString());
        assertEquals(1, group.getUnifacGroup(0).getN(), s.toString());
      }
      if (s.property == ModelSpec.Property.GROUP_R) {
        return group.getR();
      }
    }
    double gamma = c instanceof ComponentGEWilson
        ? ((ComponentGEWilson) c).getGamma(liquid, liquid.getNumberOfComponents(), s.temperature, s.pressure,
            PhaseType.LIQUID)
        : ((ComponentGEUnifac) c).getGamma(liquid, liquid.getNumberOfComponents(), s.temperature, s.pressure,
            PhaseType.LIQUID);
    positive(gamma, s.toString());
    assertEquals(gamma, c.getGamma(), 1e-12, s + " returned/stored gamma");
    assertEquals(Math.log(gamma), c.getLnGamma(), 1e-12, s + " stored ln-gamma");
    if (s.fixture == ModelSpec.Fixture.WILSON_ANALYTIC) {
      double phi = liquid.getComponent(s.componentIndex).fugcoef(liquid);
      positive(phi, s.toString());
      assertEquals(phi, liquid.getComponent(s.componentIndex).getFugacityCoefficient(), 0.0, s.toString());
    }
    return s.property == ModelSpec.Property.LN_GAMMA ? c.getLnGamma() : gamma;
  }

  private static SystemInterface create(ModelSpec s) {
    SystemInterface system;
    switch (s.fixture) {
    case WILSON_ANALYTIC:
      system = new SystemGEWilson(s.temperature, s.pressure);
      break;
    case UNIFAC:
      system = new SystemUNIFAC(s.temperature, s.pressure);
      break;
    case PSRK:
      system = new SystemUNIFACpsrk(s.temperature, s.pressure);
      break;
    case UMR:
      system = new SystemUMRPRUEos(s.temperature, s.pressure);
      break;
    case SRK:
    case SRK_PHASE:
      system = new SystemSrkEos(s.temperature, s.pressure);
      break;
    case PR:
    case PR_PHASE:
      system = new SystemPrEos(s.temperature, s.pressure);
      break;
    default:
      throw new IllegalArgumentException("no system factory for " + s.fixture);
    }
    for (Map.Entry<String, Double> entry : s.components.entrySet()) {
      system.addComponent(entry.getKey(), entry.getValue());
    }
    if (s.fixture == ModelSpec.Fixture.UMR) {
      system.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else {
      system.setMixingRule("classic");
    }
    return system;
  }

  static void positive(double value, String context) {
    assertTrue(Double.isFinite(value) && value > 0.0, context + ": expected finite positive value, got " + value);
  }
}
