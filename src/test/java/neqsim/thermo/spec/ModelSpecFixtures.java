package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.thermo.component.ComponentGeNRTL;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentPow10KPaVaporPressureTest;
import neqsim.thermo.component.ComponentSrk;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseAmmoniaEos;
import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseGEUnifacPSRK;
import neqsim.thermo.phase.PhaseGEUnifacUMRPRU;
import neqsim.thermo.phase.PhaseGEUniquac;
import neqsim.thermo.phase.PhaseGEWilson;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseIdealGas;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemNRTL;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUEos;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermo.util.gerg.NeqSimGERG2008;

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
    case WILSON_PHASE:
      return PhaseGEWilson.class;
    case NRTL_ANALYTIC:
      return SystemNRTL.class;
    case NRTL_PHASE:
      return PhaseGENRTL.class;
    case UNIFAC:
      return SystemUNIFAC.class;
    case PSRK:
      return SystemUNIFACpsrk.class;
    case UMR:
      return SystemUMRPRUEos.class;
    case UNIFAC_PHASE:
      return PhaseGEUnifac.class;
    case PSRK_PHASE:
      return PhaseGEUnifacPSRK.class;
    case UMR_PHASE:
      return PhaseGEUnifacUMRPRU.class;
    case SRK:
      return SystemSrkEos.class;
    case PR:
      return SystemPrEos.class;
    case SRK_PHASE:
      return PhaseSrkEos.class;
    case PR_PHASE:
      return PhasePrEos.class;
    case GERG:
      return SystemGERG2008Eos.class;
    case GERG_PHASE:
      return PhaseGERG2008Eos.class;
    case IDEAL_GAS:
      return SystemIdealGas.class;
    case IDEAL_GAS_PHASE:
      return PhaseIdealGas.class;
    case AMMONIA:
      return SystemAmmoniaEos.class;
    case AMMONIA_PHASE:
      return PhaseAmmoniaEos.class;
    case UNIQUAC:
      return PhaseGEUniquac.class;
    default:
      throw new IllegalArgumentException("unmapped fixture " + fixture);
    }
  }

  static void validate(ModelSpec s) {
    ModelSpec.require(unit(s.property).equals(s.unit), "wrong unit for " + s.property);
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
    case GERG:
    case GERG_PHASE:
      ModelSpec.require(isGergProperty(s.property) && s.components.size() == 21 && "gas".equals(s.phase)
          && "none".equals(s.mixingRule) && "nist-aga8-gerg2008".equals(s.operation)
          && s.outcome == ModelSpec.Outcome.VALUE && s.componentIndex == 0, "invalid GERG-2008 reference fixture");
      for (String component : new String[] {"methane", "nitrogen", "CO2", "ethane", "propane", "i-butane", "n-butane",
          "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "hydrogen", "oxygen", "CO",
          "water", "H2S", "helium", "argon"}) {
        ModelSpec.require(s.components.containsKey(component), "GERG-2008 reference composition missing " + component);
      }
      break;
    case IDEAL_GAS:
    case IDEAL_GAS_PHASE:
      ModelSpec.require(isIdealGasProperty(s.property) && s.components.size() == 1 && s.components.containsKey("argon")
          && "gas".equals(s.phase) && "none".equals(s.mixingRule) && "nist-argon-ideal-gas".equals(s.operation)
          && s.outcome == ModelSpec.Outcome.VALUE && s.componentIndex == 0, "invalid ideal-gas reference fixture");
      break;
    case AMMONIA:
    case AMMONIA_PHASE:
      ModelSpec.require(isAmmoniaProperty(s.property) && s.components.size() == 1 && s.components.containsKey("ammonia")
          && ("gas".equals(s.phase) || "liquid".equals(s.phase)) && "none".equals(s.mixingRule)
          && "coolprop-7.2.0-gao-2020".equals(s.operation) && s.outcome == ModelSpec.Outcome.VALUE
          && s.componentIndex == 0, "invalid ammonia reference fixture");
      break;
    case WILSON_ANALYTIC:
    case WILSON_PHASE:
      ModelSpec.require(
          s.components.size() == 2 && s.components.containsKey("methanol") && s.components.containsKey("water")
              && s.components.keySet().iterator().next().equals("methanol"),
          "Wilson fixture requires ordered methanol/water with Lambda12=2 and Lambda21=0.5");
      ModelSpec.require(s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.LN_GAMMA,
          "Wilson property not implemented by fixture");
      validateGe(s, "classic", "init-ge");
      break;
    case NRTL_ANALYTIC:
    case NRTL_PHASE:
      ModelSpec.require(
          s.components.size() == 2 && s.components.containsKey("methanol") && s.components.containsKey("water")
              && s.components.keySet().iterator().next().equals("methanol"),
          "NRTL fixture requires ordered methanol/water with prescribed alpha and tau parameters");
      ModelSpec.require(s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.LN_GAMMA
          || s.property == ModelSpec.Property.GEX, "NRTL property not implemented by fixture");
      validateGe(s, "classic", "prescribed-nrtl");
      break;
    case UNIFAC:
    case PSRK:
    case UMR:
    case UNIFAC_PHASE:
    case PSRK_PHASE:
    case UMR_PHASE:
      ModelSpec.require(
          s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.GROUP_R
              || s.property == ModelSpec.Property.GROUP_Q || s.property == ModelSpec.Property.INTERACTION_A
              || s.property == ModelSpec.Property.LN_GAMMA || s.property == ModelSpec.Property.GEX,
          "group property not implemented by fixture");
      boolean umr = s.fixture == ModelSpec.Fixture.UMR || s.fixture == ModelSpec.Fixture.UMR_PHASE;
      boolean classicNonideal = (s.fixture == ModelSpec.Fixture.UNIFAC || s.fixture == ModelSpec.Fixture.UNIFAC_PHASE)
          && s.components.size() == 2 && s.property != ModelSpec.Property.INTERACTION_A;
      if (s.property == ModelSpec.Property.LN_GAMMA || s.property == ModelSpec.Property.GEX) {
        ModelSpec.require(classicNonideal, "nonideal log/gex qualification is limited to original UNIFAC");
      }
      validateGe(s, umr ? "HV/UNIFAC_UMRPRU" : "classic", classicNonideal ? "published-original-unifac" : "init-ge");
      if (s.property == ModelSpec.Property.INTERACTION_A) {
        ModelSpec.require(
            s.components.size() == 2 && s.components.containsKey("methanol") && s.components.containsKey("water")
                && s.components.keySet().iterator().next().equals("methanol"),
            "interaction fixture requires ordered methanol/water");
      } else if (classicNonideal) {
        ModelSpec.require(
            (s.property == ModelSpec.Property.GAMMA || s.property == ModelSpec.Property.LN_GAMMA
                || s.property == ModelSpec.Property.GEX) && s.components.containsKey("methanol")
                && s.components.containsKey("water") && s.components.keySet().iterator().next().equals("methanol"),
            "published original UNIFAC fixture requires ordered methanol/water");
      } else {
        ModelSpec.require(s.components.size() == 1 && s.components.containsKey("methanol"),
            "group fixture requires pure methanol unless testing an interaction");
      }
      break;
    default:
      throw new IllegalArgumentException("unmapped fixture " + s.fixture);
    }
    if (s.outcome == ModelSpec.Outcome.VALUE && isPositiveOnly(s.property)) {
      ModelSpec.require(s.expected > 0.0, "positive-only property reference must be positive");
    }
  }

  private static String unit(ModelSpec.Property property) {
    switch (property) {
    case PSAT:
      return "bar";
    case DPSAT_DT:
      return "bar/K";
    case T_SAT:
      return "K";
    case HID:
    case GEX:
    case INTERNAL_ENERGY:
    case ENTHALPY:
    case GIBBS_ENERGY:
      return "J/mol";
    case INTERACTION_A:
      return "K";
    case MOLAR_MASS:
      return "g/mol";
    case MOLAR_DENSITY:
      return "mol/L";
    case MASS_DENSITY:
      return "kg/m3";
    case DPD_DENSITY:
      return "kPa/(mol/L)";
    case D2PD_DENSITY2:
      return "kPa/(mol/L)^2";
    case DPD_T:
      return "kPa/K";
    case ENTROPY:
    case CV:
    case CP:
      return "J/(mol*K)";
    case SOUND_SPEED:
      return "m/s";
    case JT:
      return "K/kPa";
    default:
      return "1";
    }
  }

  static boolean isPositiveOnly(ModelSpec.Property property) {
    return property != ModelSpec.Property.LN_GAMMA && property != ModelSpec.Property.HID
        && property != ModelSpec.Property.GEX && property != ModelSpec.Property.INTERACTION_A
        && property != ModelSpec.Property.D2PD_DENSITY2 && property != ModelSpec.Property.DPD_T
        && property != ModelSpec.Property.INTERNAL_ENERGY && property != ModelSpec.Property.ENTHALPY
        && property != ModelSpec.Property.ENTROPY && property != ModelSpec.Property.GIBBS_ENERGY
        && property != ModelSpec.Property.JT;
  }

  private static boolean isGergProperty(ModelSpec.Property property) {
    switch (property) {
    case MOLAR_MASS:
    case MOLAR_DENSITY:
    case Z:
    case DPD_DENSITY:
    case D2PD_DENSITY2:
    case DPD_T:
    case INTERNAL_ENERGY:
    case ENTHALPY:
    case ENTROPY:
    case CV:
    case CP:
    case SOUND_SPEED:
    case GIBBS_ENERGY:
    case JT:
    case KAPPA:
      return true;
    default:
      return false;
    }
  }

  private static boolean isIdealGasProperty(ModelSpec.Property property) {
    switch (property) {
    case MOLAR_MASS:
    case MOLAR_DENSITY:
    case Z:
    case PHI:
    case CP:
    case CV:
    case SOUND_SPEED:
    case JT:
      return true;
    default:
      return false;
    }
  }

  private static boolean isAmmoniaProperty(ModelSpec.Property property) {
    switch (property) {
    case MOLAR_MASS:
    case MOLAR_DENSITY:
    case MASS_DENSITY:
    case Z:
    case INTERNAL_ENERGY:
    case ENTHALPY:
    case ENTROPY:
    case CV:
    case CP:
    case SOUND_SPEED:
    case JT:
    case KAPPA:
      return true;
    default:
      return false;
    }
  }

  private static void validateGe(ModelSpec s, String mixingRule, String operation) {
    ModelSpec.require(s.outcome == ModelSpec.Outcome.VALUE && "liquid".equals(s.phase)
        && mixingRule.equals(s.mixingRule) && operation.equals(s.operation), "invalid GE fixture");
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
    if (!isPhaseFixture(s.fixture)) {
      assertEquals(type(s.fixture), system.getClass(), s.toString());
    }
    system.init(0);
    if (s.fixture == ModelSpec.Fixture.GERG || s.fixture == ModelSpec.Fixture.GERG_PHASE) {
      system.init(1);
      PhaseInterface phase = system.getPhase(0);
      if (s.fixture == ModelSpec.Fixture.GERG_PHASE) {
        assertEquals(type(s.fixture), phase.getClass(), s.toString());
      }
      NeqSimGERG2008 gerg = new NeqSimGERG2008(phase, GERG2008Type.STANDARD);
      double[] properties = s.fixture == ModelSpec.Fixture.GERG_PHASE ? phase.getProperties_GERG2008()
          : gerg.propertiesGERG();
      assertEquals(15, properties.length, s.toString());
      for (double property : properties) {
        assertTrue(Double.isFinite(property), s.toString());
      }
      double result = readGerg(s.property, gerg, properties);
      double[] repeated = s.fixture == ModelSpec.Fixture.GERG_PHASE ? phase.getProperties_GERG2008()
          : gerg.propertiesGERG();
      assertEquals(result, readGerg(s.property, gerg, repeated), 0.0, s + " repeat read");
      return result;
    }
    if (s.fixture == ModelSpec.Fixture.IDEAL_GAS || s.fixture == ModelSpec.Fixture.IDEAL_GAS_PHASE) {
      system.init(1);
      PhaseInterface phase = system.getPhase(0);
      if (s.fixture == ModelSpec.Fixture.IDEAL_GAS_PHASE) {
        assertEquals(type(s.fixture), phase.getClass(), s.toString());
      }
      double result = readIdealGas(s.property, phase, s.componentIndex);
      assertEquals(result, readIdealGas(s.property, phase, s.componentIndex), 0.0, s + " repeat read");
      return result;
    }
    if (s.fixture == ModelSpec.Fixture.AMMONIA || s.fixture == ModelSpec.Fixture.AMMONIA_PHASE) {
      system.setNumberOfPhases(1);
      system.setMaxNumberOfPhases(1);
      system.setForcePhaseTypes(true);
      system.setPhaseType(0, "liquid".equals(s.phase) ? PhaseType.LIQUID : PhaseType.GAS);
      system.init(3);
      PhaseInterface phase = system.getPhase(0);
      if (s.fixture == ModelSpec.Fixture.AMMONIA_PHASE) {
        assertEquals(type(s.fixture), phase.getClass(), s.toString());
      }
      double result = readAmmonia(s.property, phase);
      assertEquals(result, readAmmonia(s.property, phase), 0.0, s + " repeat read");
      return result;
    }
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
    if (s.fixture == ModelSpec.Fixture.UMR || s.fixture == ModelSpec.Fixture.UMR_PHASE) {
      system.init(1);
      positive(liquid.getComponent(s.componentIndex).getFugacityCoefficient(), s.toString());
      liquid = ((EosMixingRulesInterface) ((PhaseEosInterface) liquid).getMixingRule()).getGEPhase();
    }
    if (s.fixture == ModelSpec.Fixture.WILSON_ANALYTIC || s.fixture == ModelSpec.Fixture.WILSON_PHASE) {
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
    if (s.fixture == ModelSpec.Fixture.NRTL_ANALYTIC || s.fixture == ModelSpec.Fixture.NRTL_PHASE) {
      PhaseGENRTL nrtl = (PhaseGENRTL) liquid;
      nrtl.setAlpha(new double[][] {{0.0, 0.3}, {0.3, 0.0}});
      nrtl.setDij(new double[][] {{0.0, 200.0}, {-100.0, 0.0}});
    }
    if (isPhaseFixture(s.fixture)) {
      assertEquals(type(s.fixture), liquid.getClass(), s.toString());
    }
    double result = readGe(s, liquid);
    // Read again from the same state; do not merely check that init did not throw.
    assertEquals(result, readGe(s, liquid), 1e-11, s.toString());
    return result;
  }

  private static double readGerg(ModelSpec.Property property, NeqSimGERG2008 gerg, double[] values) {
    switch (property) {
    case MOLAR_MASS:
      return gerg.getMolarMass() * 1000.0;
    case MOLAR_DENSITY:
      return gerg.getMolarDensity();
    case Z:
      return values[1];
    case DPD_DENSITY:
      return values[2];
    case D2PD_DENSITY2:
      return values[3];
    case DPD_T:
      return values[5];
    case INTERNAL_ENERGY:
      return values[6];
    case ENTHALPY:
      return values[7];
    case ENTROPY:
      return values[8];
    case CV:
      return values[9];
    case CP:
      return values[10];
    case SOUND_SPEED:
      return values[11];
    case GIBBS_ENERGY:
      return values[12];
    case JT:
      return values[13];
    case KAPPA:
      return values[14];
    default:
      throw new IllegalArgumentException("unmapped GERG-2008 property " + property);
    }
  }

  private static double readIdealGas(ModelSpec.Property property, PhaseInterface phase, int componentIndex) {
    double phi = phase.getComponent(componentIndex).fugcoef(phase);
    positive(phi, "ideal-gas fugacity coefficient");
    switch (property) {
    case MOLAR_MASS:
      return phase.getMolarMass() * 1000.0;
    case MOLAR_DENSITY:
      return phase.getDensity("mol/m3") / 1000.0;
    case Z:
      return phase.getZ();
    case PHI:
      return phi;
    case CP:
      return phase.getCp("J/molK");
    case CV:
      return phase.getCv("J/molK");
    case SOUND_SPEED:
      return phase.getSoundSpeed();
    case JT:
      return phase.getJouleThomsonCoefficient();
    default:
      throw new IllegalArgumentException("unmapped ideal-gas property " + property);
    }
  }

  private static double readAmmonia(ModelSpec.Property property, PhaseInterface phase) {
    switch (property) {
    case MOLAR_MASS:
      return phase.getMolarMass() * 1000.0;
    case MOLAR_DENSITY:
      return 1.0e5 / phase.getMolarVolume() / 1000.0;
    case MASS_DENSITY:
      return phase.getDensity();
    case Z:
      return phase.getZ();
    case INTERNAL_ENERGY:
      return phase.getInternalEnergy("J/mol");
    case ENTHALPY:
      return phase.getEnthalpy("J/mol");
    case ENTROPY:
      return phase.getEntropy("J/molK");
    case CV:
      return phase.getCv("J/molK");
    case CP:
      return phase.getCp("J/molK");
    case SOUND_SPEED:
      return phase.getSoundSpeed();
    case JT:
      return phase.getJouleThomsonCoefficient() / 100.0;
    case KAPPA:
      return phase.getIsothermalCompressibility();
    default:
      throw new IllegalArgumentException("unmapped ammonia property " + property);
    }
  }

  private static double readGe(ModelSpec s, PhaseInterface liquid) {
    ComponentGEInterface c = (ComponentGEInterface) liquid.getComponent(s.componentIndex);
    if (s.fixture == ModelSpec.Fixture.NRTL_ANALYTIC || s.fixture == ModelSpec.Fixture.NRTL_PHASE) {
      double total = ((PhaseGENRTL) liquid).getExcessGibbsEnergy(liquid, liquid.getNumberOfComponents(), s.temperature,
          s.pressure, PhaseType.LIQUID);
      assertTrue(Double.isFinite(total), s.toString());
      for (int i = 0; i < liquid.getNumberOfComponents(); i++) {
        ComponentGEInterface stored = (ComponentGEInterface) liquid.getComponent(i);
        positive(stored.getGamma(), s.toString());
        double phi = liquid.getComponent(i).fugcoef(liquid);
        positive(phi, s.toString());
        assertEquals(phi, liquid.getComponent(i).getFugacityCoefficient(), 0.0, s.toString());
      }
      if (s.property == ModelSpec.Property.GEX) {
        return total / liquid.getNumberOfMolesInPhase();
      }
    }
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
      if (s.property == ModelSpec.Property.GROUP_Q) {
        return group.getQ();
      }
      if (s.property == ModelSpec.Property.INTERACTION_A) {
        PhaseGEUnifac phase = (PhaseGEUnifac) liquid;
        assertEquals(2, group.getNumberOfUNIFACgroups(), s.toString());
        assertEquals(6, group.getUnifacGroup(0).getMainGroup(), s.toString());
        assertEquals(7, group.getUnifacGroup(1).getMainGroup(), s.toString());
        return phase.getAij(s.componentIndex, 1 - s.componentIndex);
      }
      if (s.property == ModelSpec.Property.GEX) {
        PhaseGEUnifac phase = (PhaseGEUnifac) liquid;
        double total = phase.getExcessGibbsEnergy(phase, liquid.getNumberOfComponents(), s.temperature, s.pressure,
            PhaseType.LIQUID);
        assertTrue(Double.isFinite(total), s.toString());
        for (int i = 0; i < liquid.getNumberOfComponents(); i++) {
          ComponentGEInterface stored = (ComponentGEInterface) liquid.getComponent(i);
          positive(stored.getGamma(), s.toString());
          assertEquals(Math.log(stored.getGamma()), stored.getLnGamma(), 1e-12, s.toString());
        }
        return total / liquid.getNumberOfMolesInPhase();
      }
    }
    double gamma = c instanceof ComponentGEWilson
        ? ((ComponentGEWilson) c).getGamma(liquid, liquid.getNumberOfComponents(), s.temperature, s.pressure,
            PhaseType.LIQUID)
        : c instanceof ComponentGEUnifac
            ? ((ComponentGEUnifac) c).getGamma(liquid, liquid.getNumberOfComponents(), s.temperature, s.pressure,
                PhaseType.LIQUID)
            : ((ComponentGeNRTL) c).getGamma();
    positive(gamma, s.toString());
    assertEquals(gamma, c.getGamma(), 1e-12, s + " returned/stored gamma");
    assertEquals(Math.log(gamma), c.getLnGamma(), 1e-12, s + " stored ln-gamma");
    if (s.fixture == ModelSpec.Fixture.WILSON_ANALYTIC || s.fixture == ModelSpec.Fixture.WILSON_PHASE
        || s.fixture == ModelSpec.Fixture.NRTL_ANALYTIC || s.fixture == ModelSpec.Fixture.NRTL_PHASE) {
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
    case WILSON_PHASE:
      system = new SystemGEWilson(s.temperature, s.pressure);
      break;
    case NRTL_ANALYTIC:
    case NRTL_PHASE:
      system = new SystemNRTL(s.temperature, s.pressure);
      break;
    case UNIFAC:
    case UNIFAC_PHASE:
      system = new SystemUNIFAC(s.temperature, s.pressure);
      break;
    case PSRK:
    case PSRK_PHASE:
      system = new SystemUNIFACpsrk(s.temperature, s.pressure);
      break;
    case UMR:
    case UMR_PHASE:
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
    case GERG:
    case GERG_PHASE:
      system = new SystemGERG2008Eos(s.temperature, s.pressure);
      break;
    case IDEAL_GAS:
    case IDEAL_GAS_PHASE:
      system = new SystemIdealGas(s.temperature, s.pressure);
      break;
    case AMMONIA:
    case AMMONIA_PHASE:
      system = new SystemAmmoniaEos(s.temperature, s.pressure);
      break;
    default:
      throw new IllegalArgumentException("no system factory for " + s.fixture);
    }
    if (s.fixture != ModelSpec.Fixture.AMMONIA && s.fixture != ModelSpec.Fixture.AMMONIA_PHASE) {
      for (Map.Entry<String, Double> entry : s.components.entrySet()) {
        system.addComponent(entry.getKey(), entry.getValue());
      }
    }
    if (s.fixture == ModelSpec.Fixture.UMR || s.fixture == ModelSpec.Fixture.UMR_PHASE) {
      system.setMixingRule("HV", "UNIFAC_UMRPRU");
    } else if (s.fixture != ModelSpec.Fixture.GERG && s.fixture != ModelSpec.Fixture.GERG_PHASE
        && s.fixture != ModelSpec.Fixture.IDEAL_GAS && s.fixture != ModelSpec.Fixture.IDEAL_GAS_PHASE
        && s.fixture != ModelSpec.Fixture.AMMONIA && s.fixture != ModelSpec.Fixture.AMMONIA_PHASE) {
      system.setMixingRule("classic");
    }
    return system;
  }

  private static boolean isPhaseFixture(ModelSpec.Fixture fixture) {
    return fixture == ModelSpec.Fixture.SRK_PHASE || fixture == ModelSpec.Fixture.PR_PHASE
        || fixture == ModelSpec.Fixture.WILSON_PHASE || fixture == ModelSpec.Fixture.NRTL_PHASE
        || fixture == ModelSpec.Fixture.UNIFAC_PHASE || fixture == ModelSpec.Fixture.PSRK_PHASE
        || fixture == ModelSpec.Fixture.UMR_PHASE || fixture == ModelSpec.Fixture.GERG_PHASE
        || fixture == ModelSpec.Fixture.IDEAL_GAS_PHASE || fixture == ModelSpec.Fixture.AMMONIA_PHASE;
  }

  static void positive(double value, String context) {
    assertTrue(Double.isFinite(value) && value > 0.0, context + ": expected finite positive value, got " + value);
  }
}
