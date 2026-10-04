package neqsim.mcp.runners;

import com.google.gson.JsonObject;
import neqsim.thermo.mixingrule.EosMixingRuleType;

/**
 * Shared defaults for fluids built from MCP JSON input.
 *
 * <p>
 * Every runner used to fall back to the {@code "classic"} mixing rule regardless of the equation of state. For CPA
 * systems that selects the SRK binary-interaction database and silently gives wrong aqueous-phase results (a wet-gas
 * hydrate temperature of about 0 C instead of 16 C at 100 bara). The decision lives in
 * {@link EosMixingRuleType#defaultForModel(String)}; this class adds the JSON convenience so a caller who omits
 * {@code mixingRule} gets the rule the model was parameterised with, while an explicit value is always honoured.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FluidDefaults {

  private FluidDefaults() {
  }

  /**
   * Returns the default mixing-rule name for a thermodynamic model name as used in MCP inputs.
   *
   * @param model model name such as SRK, PR, CPA, CPA-SRK, ELECTROLYTE-CPA (case-insensitive, may be null)
   * @return {@code CLASSIC_TX_CPA} for CPA-family models, otherwise {@code CLASSIC}
   */
  public static String defaultMixingRule(String model) {
    return EosMixingRuleType.defaultForModel(model).name();
  }

  /**
   * Resolves the mixing rule for an input object: the explicit {@code mixingRule} field when present, otherwise the
   * model-dependent default.
   *
   * @param input JSON input that may carry a {@code mixingRule} field
   * @param model model name the fluid is created with
   * @return mixing-rule name accepted by {@code SystemInterface.setMixingRule(String)}
   */
  public static String resolveMixingRule(JsonObject input, String model) {
    if (input != null && input.has("mixingRule") && !input.get("mixingRule").isJsonNull()) {
      String explicit = input.get("mixingRule").getAsString().trim();
      if (!explicit.isEmpty()) {
        return explicit;
      }
    }
    return defaultMixingRule(model);
  }
}
