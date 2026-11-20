package neqsim.thermo.util.mercury;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;

/**
 * Converts a fluid to the UMR-PRU-MC model for mercury studies.
 *
 * <p>
 * Process studies are normally made with a characterized fluid from an Eclipse E300 file, read with
 * {@code EclipseFluidReadWrite} as a Peng-Robinson or SRK fluid. That model has no mercury parameters. This class
 * builds a {@link SystemUMRPRUMCEos} fluid with the same components and amounts, and optionally a given mercury
 * content, so that the same process can be calculated with mercury.
 * </p>
 *
 * <ul>
 * <li>Components from the NeqSim database are added by name and use the database parameters of UMR-PRU-MC.</li>
 * <li>Pseudo-components (TBP and plus fractions) keep the critical temperature, critical pressure, acentric factor,
 * molar mass, density and volume correction of the source fluid. They have no Mathias-Copeman parameters and therefore
 * use the Peng-Robinson alpha function, and their UNIFAC groups are CH2 groups from the molar mass.</li>
 * <li>Binary interaction parameters of the source fluid are not used; UMR-PRU-MC uses its UNIFAC group parameters.</li>
 * </ul>
 *
 * <p>
 * The phase behaviour of the converted fluid is therefore close to, but not the same as, that of the source fluid.
 * Compare the main stream flows of the two models before using the mercury results. Glycols are outside the range of
 * the mercury parameters.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class MercuryFluidConverter {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MercuryFluidConverter.class);

  /** Suffix that NeqSim gives the name of a pseudo-component. */
  private static final String PSEUDO_SUFFIX = "_PC";

  /** Provisional molar mass of mercury in kg/mol, replaced by the database value once mercury is added. */
  private static final double PROVISIONAL_MOLAR_MASS = 0.2006;

  /**
   * Utility class; not instantiated.
   */
  private MercuryFluidConverter() {
  }

  /**
   * Convert a fluid to UMR-PRU-MC, keeping its mercury content.
   *
   * @param fluid fluid with any equation of state, for example read from an E300 file
   * @return new UMR-PRU-MC fluid with the same components, amounts, temperature and pressure, with the UMR-PRU mixing
   * rule set and multiphase check enabled
   * @throws IllegalArgumentException if the fluid is null or has a component that UMR-PRU-MC does not support
   */
  public static SystemInterface toUmrPruMc(SystemInterface fluid) {
    return convert(fluid, false, 0.0, "mole fraction");
  }

  /**
   * Convert a fluid to UMR-PRU-MC and give it a mercury content.
   *
   * <p>
   * The content refers to the whole fluid. To match a mercury content measured in one phase of a process stream, for
   * example in the gas from a separator, run the process, read the calculated content of that phase and scale the
   * content given here by measured over calculated; mercury below saturation distributes in proportion to the feed
   * content.
   * </p>
   *
   * @param fluid fluid with any equation of state, for example read from an E300 file
   * @param mercuryContent mercury content of the whole fluid; mercury already in the fluid is replaced. Zero gives a
   * fluid without mercury.
   * @param unit "mole fraction", "ppb" (mole fraction times 1e9), "ug/Sm3" (per standard cubic metre of the fluid as
   * ideal gas at 15 C and 1.01325 bara), "ug/Nm3" (0 C and 1.01325 bara) or "ug/kg" (per kilogram of the fluid)
   * @return new UMR-PRU-MC fluid with the same components, amounts, temperature and pressure, with the UMR-PRU mixing
   * rule set and multiphase check enabled
   * @throws IllegalArgumentException if the fluid is null, the content is negative or not finite, the unit is not
   * supported or the fluid has a component that UMR-PRU-MC does not support
   */
  public static SystemInterface toUmrPruMc(SystemInterface fluid, double mercuryContent, String unit) {
    if (Double.isNaN(mercuryContent) || Double.isInfinite(mercuryContent) || mercuryContent < 0.0) {
      throw new IllegalArgumentException("mercury content must be zero or positive, but is " + mercuryContent);
    }
    return convert(fluid, true, mercuryContent, unit);
  }

  /**
   * Build the UMR-PRU-MC fluid.
   *
   * @param fluid source fluid
   * @param replaceMercury true to replace the mercury of the source fluid by the given content
   * @param mercuryContent mercury content of the whole fluid
   * @param unit unit of the mercury content
   * @return new UMR-PRU-MC fluid
   * @throws IllegalArgumentException if the fluid is null, the unit is not supported or the fluid has a component that
   * UMR-PRU-MC does not support
   */
  private static SystemInterface convert(SystemInterface fluid, boolean replaceMercury, double mercuryContent,
      String unit) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid can not be null");
    }
    SystemInterface target = new SystemUMRPRUMCEos(fluid.getTemperature(), fluid.getPressure());
    double otherMoles = 0.0;
    double otherMass = 0.0;
    try {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        ComponentInterface source = fluid.getPhase(0).getComponent(i);
        String name = source.getComponentName();
        boolean mercury = MercuryPhaseEquilibrium.MERCURY.equals(name);
        if (mercury && replaceMercury) {
          continue;
        }
        if (!mercury) {
          otherMoles += source.getNumberOfmoles();
          otherMass += source.getNumberOfmoles() * source.getMolarMass();
        }
        if (source.isIsTBPfraction() || source.isIsPlusFraction() || name.endsWith(PSEUDO_SUFFIX)) {
          addPseudoComponent(target, source);
        } else {
          if ("MEG".equals(name) || "DEG".equals(name) || "TEG".equals(name)) {
            logger.warn("{} is outside the range of the UMR-PRU-MC mercury parameters", name);
          }
          target.addComponent(name, source.getNumberOfmoles());
        }
      }
      if (replaceMercury && mercuryContent > 0.0) {
        addMercury(target, otherMoles, otherMass, mercuryContent, unit);
      }
      target.setMixingRule("HV", "UNIFAC_UMRPRU");
      target.setMultiPhaseCheck(true);
      target.useVolumeCorrection(fluid.getPhase(0).useVolumeCorrection());
      target.init(0);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("could not convert the fluid to UMR-PRU-MC: " + ex.getMessage(), ex);
    }
    return target;
  }

  /**
   * Add a pseudo-component with the properties of the source fluid.
   *
   * @param target UMR-PRU-MC fluid without mixing rule
   * @param source pseudo-component of the source fluid
   */
  private static void addPseudoComponent(SystemInterface target, ComponentInterface source) {
    String name = source.getComponentName();
    String baseName = name.endsWith(PSEUDO_SUFFIX) ? name.substring(0, name.length() - PSEUDO_SUFFIX.length()) : name;
    target.addTBPfraction(baseName, source.getNumberOfmoles(), source.getMolarMass(), source.getNormalLiquidDensity());
    String targetName = baseName + PSEUDO_SUFFIX;
    for (int p = 0; p < target.getMaxNumberOfPhases(); p++) {
      if (target.getPhase(p) == null) {
        continue;
      }
      ComponentInterface component = target.getPhase(p).getComponent(targetName);
      component.setTC(source.getTC());
      component.setPC(source.getPC());
      component.setAcentricFactor(source.getAcentricFactor());
      component.setMolarMass(source.getMolarMass());
      component.setNormalBoilingPoint(source.getNormalBoilingPoint());
      component.setCriticalVolume(source.getCriticalVolume());
      component.setParachorParameter(source.getParachorParameter());
      component.setRacketZ(source.getRacketZ());
      component.setVolumeCorrectionConst(source.getVolumeCorrectionConst());
    }
  }

  /**
   * Add mercury to a fluid without mixing rule.
   *
   * @param target UMR-PRU-MC fluid holding all other components
   * @param otherMoles moles of the other components
   * @param otherMass mass of the other components in kg
   * @param content mercury content of the whole fluid, above zero
   * @param unit unit of the mercury content
   * @throws IllegalArgumentException if the unit is not supported or the content is not below pure mercury
   */
  private static void addMercury(SystemInterface target, double otherMoles, double otherMass, double content,
      String unit) {
    if (!(otherMoles > 0.0)) {
      throw new IllegalArgumentException("the fluid needs at least one component in addition to mercury");
    }
    double provisional = mercuryMoles(otherMoles, otherMass, PROVISIONAL_MOLAR_MASS, content, unit);
    target.addComponent(MercuryPhaseEquilibrium.MERCURY, provisional);
    double molarMass = target.getPhase(0).getComponent(MercuryPhaseEquilibrium.MERCURY).getMolarMass();
    double moles = mercuryMoles(otherMoles, otherMass, molarMass, content, unit);
    target.addComponent(MercuryPhaseEquilibrium.MERCURY, moles - provisional);
  }

  /**
   * Calculate the moles of mercury that give a fluid a mercury content.
   *
   * @param otherMoles moles of the other components
   * @param otherMass mass of the other components in kg
   * @param molarMass molar mass of mercury in kg/mol
   * @param content mercury content of the whole fluid
   * @param unit unit of the mercury content
   * @return moles of mercury
   * @throws IllegalArgumentException if the unit is not supported or the content is not below pure mercury
   */
  private static double mercuryMoles(double otherMoles, double otherMass, double molarMass, double content,
      String unit) {
    if ("ug/kg".equals(unit)) {
      double massFraction = content * 1.0e-9;
      if (massFraction >= 1.0) {
        throw new IllegalArgumentException("mercury content " + content + " ug/kg is not below pure mercury");
      }
      return massFraction / (1.0 - massFraction) * otherMass / molarMass;
    }
    double moleFraction;
    if ("mole fraction".equals(unit)) {
      moleFraction = content;
    } else if ("ppb".equals(unit) || "ug/Sm3".equals(unit) || "ug/Nm3".equals(unit)) {
      moleFraction = content / MercuryPhaseEquilibrium.convertMercuryConcentration(1.0, molarMass, 1.0, unit);
    } else {
      throw new IllegalArgumentException(
          "unit " + unit + " is not supported for a mercury content; use mole fraction, ppb, ug/Sm3, ug/Nm3 or ug/kg");
    }
    if (moleFraction >= 1.0) {
      throw new IllegalArgumentException("mercury content " + content + " " + unit + " is not below pure mercury");
    }
    return moleFraction / (1.0 - moleFraction) * otherMoles;
  }
}
