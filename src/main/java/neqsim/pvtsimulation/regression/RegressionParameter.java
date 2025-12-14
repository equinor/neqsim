package neqsim.pvtsimulation.regression;

import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Enumeration of regression parameters for PVT model tuning.
 *
 * <p>
 * Each parameter type includes default bounds and methods to apply the parameter to a fluid system.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public enum RegressionParameter {
  /**
   * Binary interaction parameter between methane and C7+ fraction.
   */
  BIP_METHANE_C7PLUS(0.0, 0.10, 0.03) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      String methaneCompName = null;
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String name = fluid.getPhase(0).getComponent(i).getComponentName().toLowerCase();
        if (name.equals("methane") || name.equals("c1")) {
          methaneCompName = fluid.getPhase(0).getComponent(i).getComponentName();
          break;
        }
      }
      if (methaneCompName == null) {
        return;
      }

      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()
            || fluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
          String plusCompName = fluid.getPhase(0).getComponent(i).getComponentName();
          fluid.setBinaryInteractionParameter(methaneCompName, plusCompName, value);
        }
      }
    }
  },

  /**
   * Binary interaction parameter between C2-C6 and C7+ fraction.
   */
  BIP_C2C6_C7PLUS(0.0, 0.05, 0.01) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      // Find C2-C6 components
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String name = fluid.getPhase(0).getComponent(i).getComponentName().toLowerCase();
        boolean isC2C6 = name.equals("ethane") || name.equals("c2") || name.equals("propane")
            || name.equals("c3") || name.contains("butane") || name.equals("c4")
            || name.contains("pentane") || name.equals("c5") || name.contains("hexane")
            || name.equals("c6");

        if (isC2C6) {
          String c2c6Name = fluid.getPhase(0).getComponent(i).getComponentName();
          for (int j = 0; j < fluid.getPhase(0).getNumberOfComponents(); j++) {
            if (fluid.getPhase(0).getComponent(j).isIsPlusFraction()
                || fluid.getPhase(0).getComponent(j).isIsTBPfraction()) {
              String plusName = fluid.getPhase(0).getComponent(j).getComponentName();
              fluid.setBinaryInteractionParameter(c2c6Name, plusName, value);
            }
          }
        }
      }
    }
  },

  /**
   * Binary interaction parameter between CO2 and hydrocarbons.
   */
  BIP_CO2_HC(0.08, 0.18, 0.12) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      String co2CompName = null;
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String name = fluid.getPhase(0).getComponent(i).getComponentName().toLowerCase();
        if (name.equals("co2") || name.equals("carbon dioxide")) {
          co2CompName = fluid.getPhase(0).getComponent(i).getComponentName();
          break;
        }
      }
      if (co2CompName == null) {
        return;
      }

      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String compName = fluid.getPhase(0).getComponent(i).getComponentName();
        if (!compName.equalsIgnoreCase(co2CompName)) {
          fluid.setBinaryInteractionParameter(co2CompName, compName, value);
        }
      }
    }
  },

  /**
   * Binary interaction parameter between N2 and hydrocarbons.
   */
  BIP_N2_HC(0.02, 0.12, 0.05) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      String n2CompName = null;
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String name = fluid.getPhase(0).getComponent(i).getComponentName().toLowerCase();
        if (name.equals("nitrogen") || name.equals("n2")) {
          n2CompName = fluid.getPhase(0).getComponent(i).getComponentName();
          break;
        }
      }
      if (n2CompName == null) {
        return;
      }

      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        String compName = fluid.getPhase(0).getComponent(i).getComponentName();
        if (!compName.equalsIgnoreCase(n2CompName)) {
          fluid.setBinaryInteractionParameter(n2CompName, compName, value);
        }
      }
    }
  },

  /**
   * Volume shift multiplier for C7+ pseudo-components.
   */
  VOLUME_SHIFT_C7PLUS(0.8, 1.2, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()
            || fluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
          for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
            double currentShift = fluid.getPhase(p).getComponent(i).getVolumeCorrectionConst();
            fluid.getPhase(p).getComponent(i).setVolumeCorrectionConst(currentShift * value);
          }
        }
      }
    }
  },

  /**
   * Critical temperature multiplier for C7+ pseudo-components.
   */
  TC_MULTIPLIER_C7PLUS(0.95, 1.05, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()
            || fluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
          for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
            double currentTc = fluid.getPhase(p).getComponent(i).getTC();
            fluid.getPhase(p).getComponent(i).setTC(currentTc * value);
          }
        }
      }
    }
  },

  /**
   * Critical pressure multiplier for C7+ pseudo-components.
   */
  PC_MULTIPLIER_C7PLUS(0.95, 1.05, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()
            || fluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
          for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
            double currentPc = fluid.getPhase(p).getComponent(i).getPC();
            fluid.getPhase(p).getComponent(i).setPC(currentPc * value);
          }
        }
      }
    }
  },

  /**
   * Acentric factor multiplier for C7+ pseudo-components.
   */
  OMEGA_MULTIPLIER_C7PLUS(0.90, 1.10, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()
            || fluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
          for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
            double currentOmega = fluid.getPhase(p).getComponent(i).getAcentricFactor();
            fluid.getPhase(p).getComponent(i).setAcentricFactor(currentOmega * value);
          }
        }
      }
    }
  },

  /**
   * Plus fraction molecular weight adjustment.
   */
  PLUS_MOLAR_MASS_MULTIPLIER(0.90, 1.10, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
        if (fluid.getPhase(0).getComponent(i).isIsPlusFraction()) {
          for (int p = 0; p < fluid.getMaxNumberOfPhases(); p++) {
            double currentMW = fluid.getPhase(p).getComponent(i).getMolarMass();
            fluid.getPhase(p).getComponent(i).setMolarMass(currentMW * value);
          }
        }
      }
      // Re-characterize with new MW
      fluid.getCharacterization().characterisePlusFraction();
    }
  },

  /**
   * Gamma distribution shape parameter (alpha) for Whitson characterization.
   */
  GAMMA_ALPHA(0.5, 4.0, 1.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      fluid.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
      fluid.getCharacterization().setGammaShapeParameter(value);
    }
  },

  /**
   * Minimum molecular weight (eta) for gamma distribution.
   */
  GAMMA_ETA(75.0, 95.0, 84.0) {
    @Override
    public void applyToFluid(SystemInterface fluid, double value) {
      fluid.getCharacterization().setPlusFractionModel("Whitson Gamma Model");
      fluid.getCharacterization().setGammaMinMW(value);
    }
  };

  private final double lowerBound;
  private final double upperBound;
  private final double initialGuess;

  RegressionParameter(double lowerBound, double upperBound, double initialGuess) {
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.initialGuess = initialGuess;
  }

  /**
   * Get default bounds and initial guess.
   *
   * @return array [lowerBound, upperBound, initialGuess]
   */
  public double[] getDefaultBounds() {
    return new double[] {lowerBound, upperBound, initialGuess};
  }

  /**
   * Apply this parameter value to a fluid system.
   *
   * @param fluid the fluid to modify
   * @param value the parameter value to apply
   */
  public abstract void applyToFluid(SystemInterface fluid, double value);
}
