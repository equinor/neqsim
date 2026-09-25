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
import neqsim.thermo.phase.PhaseLeachmanEos;
import neqsim.thermo.phase.PhaseVegaEos;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemAmmoniaEos;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemIdealGas;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermo.system.SystemVegaEos;
import neqsim.thermo.system.SystemNRTL;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUEos;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermo.util.gerg.NeqSimGERG2008;
import neqsim.thermo.util.leachman.NeqSimLeachman;
import neqsim.thermo.util.Vega.NeqSimVega;

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
    case LEACHMAN:
      return SystemLeachmanEos.class;
    case LEACHMAN_PHASE:
      return PhaseLeachmanEos.class;
    case VEGA:
      return SystemVegaEos.class;
    case VEGA_PHASE:
      return PhaseVegaEos.class;
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
    case LEACHMAN:
    case LEACHMAN_PHASE:
      ModelSpec.require(isLeachmanProperty(s.property) && s.components.size() == 1
          && s.components.containsKey("hydrogen") && ("gas".equals(s.phase) || "liquid".equals(s.phase))
          && "none".equals(s.mixingRule) && "coolprop-7.2.0-leachman-2009".equals(s.operation)
          && s.outcome == ModelSpec.Outcome.VALUE && s.componentIndex == 0,
          "invalid normal-hydrogen Leachman reference fixture");
      break;
    case VEGA:
    case VEGA_PHASE:
      ModelSpec.require(isVegaProperty(s.property) && s.components.size() == 1 && s.components.containsKey("helium")
          && "gas".equals(s.phase) && "none".equals(s.mixingRule)
          && "coolprop-7.2.0-ortiz-vega-2019".equals(s.operation) && s.outcome == ModelSpec.Outcome.VALUE
          && s.componentIndex == 0, "invalid helium Vega reference fixture");
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

  private static boolean isLeachmanProperty(ModelSpec.Property property) {
    return isAmmoniaProperty(property) || property == ModelSpec.Property.GIBBS_ENERGY;
  }

  private static boolean isVegaProperty(ModelSpec.Property property) {
    return isAmmoniaProperty(property) || property == ModelSpec.Property.GIBBS_ENERGY;
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
      asse