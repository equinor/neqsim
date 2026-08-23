package neqsim.chemicalreactions.chemicalreaction;

/** Concentration convention used to evaluate dimensionless chemical-reaction quotients. */
public enum ChemicalReactionConcentrationBasis {
  /** Mole fraction multiplied by the model's activity coefficient. */
  MOLE_FRACTION,

  /** Solute molality divided by 1 mol/kg; solvent activity remains on the mole-fraction convention. */
  SOLUTE_MOLALITY
}
