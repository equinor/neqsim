package neqsim.mcp.server;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * MCP prompts providing guided workflows for common engineering tasks.
 *
 * <p>
 * Each prompt returns a structured instruction that guides the LLM through a multi-step engineering
 * workflow using the available NeqSim MCP tools.
 * </p>
 */
@ApplicationScoped
public class NeqSimPrompts {

  /**
   * Guide for designing a gas processing facility.
   *
   * @param feedGasDescription natural language description of the feed gas
   * @param processingGoal what the processing should achieve
   * @return prompt message with step-by-step workflow
   */
  @Prompt(name = "design_gas_processing",
      description = "Step-by-step guide for designing a gas processing facility. "
          + "Walks through feed characterization, equipment selection, simulation, "
          + "and results analysis.")
  public PromptMessage designGasProcessing(@PromptArg(name = "feedGasDescription",
      description = "Description of the feed gas (composition, conditions, flow rate)") String feedGasDescription,
      @PromptArg(name = "processingGoal",
          description = "What the processing should achieve (e.g., 'sales gas spec', "
              + "'NGL recovery', 'dehydration')") String processingGoal) {
    String text = "## Gas Processing Design Workflow\n\n" + "**Feed:** " + feedGasDescription + "\n"
        + "**Goal:** " + processingGoal + "\n\n" + "Follow these steps using NeqSim MCP tools:\n\n"
        + "### Step 1: Characterize the Feed\n"
        + "Call `runFlash` with the feed gas composition at inlet conditions to determine "
        + "phase behavior, water/hydrocarbon dew points, and fluid properties.\n\n"
        + "### Step 2: Phase Envelope\n"
        + "Call `getPhaseEnvelope` to map the full phase boundary. Identify "
        + "cricondenbar and cricondentherm for pipeline design.\n\n"
        + "### Step 3: Flow Assurance Screening\n"
        + "Call `runFlowAssurance` with analysis='hydrateRiskMap' to check hydrate "
        + "formation risk at operating conditions.\n\n" + "### Step 4: Build Process Simulation\n"
        + "Call `runProcess` with a flowsheet including:\n"
        + "- Inlet separator (remove free water/liquids)\n"
        + "- Gas treatment (dehydration, sweetening if needed)\n"
        + "- Compression stages with intercooling\n" + "- Final cooling and separation\n\n"
        + "### Step 5: Quality Check\n"
        + "Call `calculateStandard` with standard='ISO6976' on the product gas "
        + "to verify heating value and Wobbe index meet sales gas spec.\n\n"
        + "### Step 6: Sensitivity Analysis\n"
        + "Call `runParametricStudy` to sweep key operating parameters "
        + "(separator pressure, cooler temperature) and find optimal conditions.\n\n"
        + "### Step 7: Economics (optional)\n"
        + "Call `runFieldEconomics` with CAPEX/OPEX estimates and production "
        + "forecasts to evaluate project viability.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for running a PVT study.
   *
   * @param fluidDescription description of the reservoir fluid
   * @return prompt message with PVT study workflow
   */
  @Prompt(name = "pvt_study",
      description = "Step-by-step guide for a complete PVT study on a reservoir fluid. "
          + "Covers saturation pressure, CME, CVD, differential liberation, and separator tests.")
  public PromptMessage pvtStudy(@PromptArg(name = "fluidDescription",
      description = "Description of the reservoir fluid (composition, reservoir T and P, "
          + "fluid type: gas, oil, condensate)") String fluidDescription) {
    String text = "## PVT Study Workflow\n\n" + "**Fluid:** " + fluidDescription + "\n\n"
        + "Follow these steps:\n\n" + "### Step 1: Saturation Point\n"
        + "Call `runPVT` with experiment='saturationPressure' to find the bubble/dew point.\n\n"
        + "### Step 2: Phase Envelope\n"
        + "Call `getPhaseEnvelope` to visualize the full PT phase boundary.\n\n"
        + "### Step 3: Constant Mass Expansion (CME)\n"
        + "Call `runPVT` with experiment='CME' and a range of pressures from above "
        + "saturation to well below. Observe relative volume and compressibility.\n\n"
        + "### Step 4: Constant Volume Depletion (for gas condensate)\n"
        + "Call `runPVT` with experiment='CVD' to simulate reservoir depletion. "
        + "Track liquid dropout and gas composition changes.\n\n"
        + "### Step 5: Differential Liberation (for oil)\n"
        + "Call `runPVT` with experiment='differentialLiberation' to get GOR, Bo, "
        + "and oil density vs pressure.\n\n" + "### Step 6: Separator Test\n"
        + "Call `runPVT` with experiment='separatorTest' to optimize separator conditions. "
        + "Try different stage pressures to maximize stock tank oil.\n\n"
        + "### Step 7: Viscosity\n"
        + "Call `runPVT` with experiment='viscosity' to get oil and gas viscosity "
        + "as a function of pressure.\n\n" + "### Step 8: Property Table\n"
        + "Call `getPropertyTable` to generate a comprehensive property table "
        + "over the operating pressure range.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for flow assurance screening.
   *
   * @param pipelineDescription pipeline geometry and conditions
   * @return prompt message with flow assurance workflow
   */
  @Prompt(name = "flow_assurance_screening",
      description = "Step-by-step guide for flow assurance screening of a pipeline. "
          + "Covers hydrates, wax, corrosion, erosion, and cooldown analysis.")
  public PromptMessage flowAssuranceScreening(@PromptArg(name = "pipelineDescription",
      description = "Pipeline description (length, diameter, fluid type, "
          + "wellhead/arrival conditions, subsea/onshore)") String pipelineDescription) {
    String text = "## Flow Assurance Screening Workflow\n\n" + "**Pipeline:** "
        + pipelineDescription + "\n\n" + "### Step 1: Fluid Characterization\n"
        + "Call `runFlash` at wellhead and arrival conditions to check phase behavior.\n\n"
        + "### Step 2: Phase Envelope\n"
        + "Call `getPhaseEnvelope` and overlay the pipeline operating envelope.\n\n"
        + "### Step 3: Hydrate Risk\n"
        + "Call `runFlowAssurance` with analysis='hydrateRiskMap' to generate "
        + "hydrate equilibrium curve. Compare with pipeline PT profile.\n\n"
        + "### Step 4: Wax Appearance\n"
        + "Call `runFlowAssurance` with analysis='waxAppearance' to find WAT.\n\n"
        + "### Step 5: CO2 Corrosion\n"
        + "Call `runFlowAssurance` with analysis='CO2Corrosion' with CO2 partial "
        + "pressure and water chemistry to estimate corrosion rate.\n\n" + "### Step 6: Erosion\n"
        + "Call `runFlowAssurance` with analysis='erosion' to check erosional velocity.\n\n"
        + "### Step 7: Pipeline Hydraulics\n"
        + "Call `runPipeline` with pipe geometry and flow conditions to get "
        + "pressure drop, outlet temperature, and flow regime.\n\n"
        + "### Step 8: Cooldown Analysis\n"
        + "Call `runFlowAssurance` with analysis='pipelineCooldown' to estimate "
        + "time to reach hydrate or wax temperature during shutdown.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for field development screening.
   *
   * @param fieldDescription field characteristics
   * @return prompt message with field development workflow
   */
  @Prompt(name = "field_development_screening",
      description = "Step-by-step guide for screening a field development concept. "
          + "Covers resource estimation, production forecasting, economics, and risk.")
  public PromptMessage fieldDevelopmentScreening(@PromptArg(name = "fieldDescription",
      description = "Field description (reservoir fluid, depth, size, location, "
          + "infrastructure options)") String fieldDescription) {
    String text = "## Field Development Screening Workflow\n\n" + "**Field:** " + fieldDescription
        + "\n\n" + "### Step 1: Fluid Properties\n"
        + "Call `runFlash` to characterize the reservoir fluid at reservoir conditions. "
        + "Determine GOR, API gravity, water cut.\n\n" + "### Step 2: Phase Envelope\n"
        + "Call `getPhaseEnvelope` to understand phase behavior across the "
        + "expected operating envelope (reservoir to surface).\n\n"
        + "### Step 3: Reservoir Simulation\n"
        + "Call `runReservoir` with estimated volumes and well rates to forecast "
        + "pressure decline and recovery factor.\n\n" + "### Step 4: Production Profile\n"
        + "Call `runFieldEconomics` with mode='productionProfile' and a "
        + "suitable decline model to generate an annual production forecast.\n\n"
        + "### Step 5: Process Simulation\n"
        + "Call `runProcess` with a representative processing flowsheet "
        + "to size equipment and estimate utility requirements.\n\n" + "### Step 6: Economics\n"
        + "Call `runFieldEconomics` with mode='cashflow', the production profile, "
        + "CAPEX/OPEX estimates, and commodity prices. Try different fiscal "
        + "regimes if comparing jurisdictions.\n\n" + "### Step 7: Flow Assurance\n"
        + "Call `runFlowAssurance` with hydrate and wax checks for the "
        + "tieback/export pipeline.\n\n" + "### Step 8: Sensitivity\n"
        + "Repeat step 6 with different oil/gas prices, CAPEX multipliers, "
        + "and recovery factors to build a tornado diagram.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for CO2 transport and injection (CCS) analysis.
   *
   * @param ccsDescription CCS chain description
   * @return prompt message with CCS workflow
   */
  @Prompt(name = "co2_ccs_chain",
      description = "Step-by-step guide for analyzing a CO2 capture, transport, "
          + "and storage (CCS) chain. Covers phase behavior, pipeline design, "
          + "impurity effects, and injection well analysis.")
  public PromptMessage co2CcsChain(@PromptArg(name = "ccsDescription",
      description = "CCS chain description (CO2 source, purity, impurities, "
          + "pipeline length, injection depth)") String ccsDescription) {
    String text = "## CO2 CCS Chain Analysis Workflow\n\n" + "**System:** " + ccsDescription
        + "\n\n" + "### Step 1: CO2 Phase Behavior\n"
        + "Call `runFlash` with the CO2 stream composition (include impurities: "
        + "N2, H2, O2, H2S, CH4) at transport conditions. Use SRK or PR EOS.\n\n"
        + "### Step 2: Phase Envelope\n"
        + "Call `getPhaseEnvelope` to map the two-phase region. Impurities shift "
        + "the phase boundary — critical for pipeline operating pressure.\n\n"
        + "### Step 3: Property Table\n"
        + "Call `getPropertyTable` sweeping pressure at pipeline temperature to "
        + "find density, viscosity, and compressibility for hydraulic design.\n\n"
        + "### Step 4: Pipeline Sizing\n"
        + "Call `runPipeline` with the CO2 mixture to calculate pressure drop "
        + "and ensure single-phase dense phase flow throughout.\n\n"
        + "### Step 5: Hydrate Check (if water present)\n"
        + "Call `runFlowAssurance` with analysis='hydrateRiskMap' if any free "
        + "water is present in the CO2 stream.\n\n" + "### Step 6: Corrosion Assessment\n"
        + "Call `runFlowAssurance` with analysis='CO2Corrosion' to estimate "
        + "corrosion rate for material selection.\n\n" + "### Step 7: Standards Compliance\n"
        + "Call `calculateStandard` with standard='ISO14687' to check CO2 "
        + "purity against injection specification.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for TEG dehydration design.
   *
   * @param gasDescription wet gas description
   * @return prompt message with TEG dehydration workflow
   */
  @Prompt(name = "teg_dehydration_design",
      description = "Step-by-step guide for designing a TEG (triethylene glycol) "
          + "dehydration unit for natural gas.")
  public PromptMessage tegDehydrationDesign(@PromptArg(name = "gasDescription",
      description = "Wet gas description (composition, T, P, flow rate, "
          + "target water dew point)") String gasDescription) {
    String text = "## TEG Dehydration Design Workflow\n\n" + "**Gas:** " + gasDescription + "\n\n"
        + "### Step 1: Feed Gas Analysis\n"
        + "Call `runFlash` with CPA EOS (required for water/glycol) to determine "
        + "water content of the saturated gas.\n\n" + "### Step 2: Water Dew Point Requirement\n"
        + "Call `runFlash` with flashType='dewPointT' at pipeline pressure "
        + "to find the current water dew point.\n\n" + "### Step 3: Hydrate Check\n"
        + "Call `runFlowAssurance` with analysis='hydrateRiskMap' to confirm "
        + "the target dew point is below hydrate formation temperature.\n\n"
        + "### Step 4: Process Simulation\n"
        + "Call `runProcess` with a TEG contactor/absorber column, TEG "
        + "regenerator, and associated equipment. Use CPA EOS.\n\n" + "### Step 5: Optimization\n"
        + "Call `runParametricStudy` sweeping:\n" + "- TEG circulation rate\n"
        + "- Contactor number of stages\n" + "- Reboiler temperature\n"
        + "Record outlet water dew point and TEG losses.\n\n" + "### Step 6: Quality Verification\n"
        + "Call `calculateStandard` with ISO6976 on the treated gas to verify "
        + "it meets sales gas specification.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for biorefinery analysis.
   *
   * @param feedstockDescription biomass feedstock description
   * @return prompt message with biorefinery workflow
   */
  @Prompt(name = "biorefinery_analysis",
      description = "Step-by-step guide for analyzing a biorefinery process "
          + "(anaerobic digestion, gasification, or pyrolysis).")
  public PromptMessage biorefineryAnalysis(@PromptArg(name = "feedstockDescription",
      description = "Biomass feedstock description (type, composition, "
          + "moisture, flow rate, desired products)") String feedstockDescription) {
    String text = "## Biorefinery Analysis Workflow\n\n" + "**Feedstock:** " + feedstockDescription
        + "\n\n" + "### Step 1: Feedstock Selection\n" + "Choose the appropriate reactor type:\n"
        + "- **Anaerobic Digester**: wet biomass (food waste, manure, sewage) → biogas\n"
        + "- **Gasifier**: dry biomass → syngas (H2 + CO)\n"
        + "- **Pyrolysis**: dry biomass → bio-oil + biochar + gas\n\n"
        + "### Step 2: Reactor Simulation\n"
        + "Call `runBioprocess` with the appropriate reactor type and "
        + "feedstock parameters. Obtain product yields and energy balance.\n\n"
        + "### Step 3: Product Gas Analysis\n"
        + "Call `runFlash` on the product gas composition to determine "
        + "properties at downstream conditions.\n\n" + "### Step 4: Gas Upgrading (if needed)\n"
        + "For biogas: Call `runProcess` with a CO2 removal step to upgrade "
        + "to biomethane quality.\n"
        + "For syngas: Call `runProcess` with a water-gas shift reactor "
        + "to adjust H2/CO ratio.\n\n" + "### Step 5: Quality Check\n"
        + "Call `calculateStandard` with ISO6976 to verify biogas/biomethane "
        + "meets grid injection specification.\n\n" + "### Step 6: Economics\n"
        + "Call `runFieldEconomics` with CAPEX/OPEX for the biorefinery "
        + "and revenue from products to evaluate viability.";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for dynamic simulation and control.
   *
   * @param processDescription process to simulate dynamically
   * @return prompt message with dynamic simulation workflow
   */
  @Prompt(name = "dynamic_simulation",
      description = "Step-by-step guide for running a dynamic (transient) process "
          + "simulation with automatic controller instrumentation.")
  public PromptMessage dynamicSimulation(@PromptArg(name = "processDescription",
      description = "Process to simulate dynamically (equipment, disturbance scenario, "
          + "control objective)") String processDescription) {
    String text = "## Dynamic Simulation Workflow\n\n" + "**Process:** " + processDescription
        + "\n\n" + "### Step 1: Steady-State Baseline\n"
        + "Call `runProcess` to establish the steady-state operating point "
        + "before introducing dynamics.\n\n" + "### Step 2: Run Dynamic Simulation\n"
        + "Call `runDynamic` with the same process JSON. The tool "
        + "automatically instruments the process with:\n"
        + "- Pressure transmitters (PT) on separators and pipes\n"
        + "- Level transmitters (LT) on vessels\n" + "- Temperature transmitters (TT) on streams\n"
        + "- Flow transmitters (FT) on feed streams\n"
        + "- PID controllers connected to control valves\n\n" + "### Step 3: Analyze Response\n"
        + "Examine the time-series data from transmitters to assess:\n"
        + "- Settling time after disturbance\n" + "- Overshoot/undershoot magnitude\n"
        + "- Oscillation damping\n\n" + "### Step 4: Tune Controllers (if needed)\n"
        + "Re-run `runDynamic` with custom tuning parameters in the "
        + "'tuning' field to adjust Kp and Ti for pressure, level, "
        + "flow, and temperature loops.\n\n" + "### Step 5: Scenario Testing\n"
        + "Modify the process JSON to simulate disturbances:\n"
        + "- Change feed flow rate (simulate startup/shutdown)\n"
        + "- Change feed composition (simulate slug arrival)\n"
        + "- Change outlet pressure (simulate pipeline pressure change)";
    return PromptMessage.withUserRole(new TextContent(text));
  }

  /**
   * Guide for pipeline sizing and design.
   *
   * @param pipelineRequirements pipeline design requirements
   * @return prompt message with pipeline design workflow
   */
  @Prompt(name = "pipeline_sizing",
      description = "Step-by-step guide for sizing and designing a multiphase pipeline.")
  public PromptMessage pipelineSizing(@PromptArg(name = "pipelineRequirements",
      description = "Pipeline requirements (fluid, flow rate, length, elevation, "
          + "arrival pressure, subsea/onshore)") String pipelineRequirements) {
    String text =
        "## Pipeline Sizing Workflow\n\n" + "**Requirements:** " + pipelineRequirements + "\n\n"
            + "### Step 1: Fluid Properties\n" + "Call `runFlash` at pipeline inlet conditions.\n"
            + "Call `getPropertyTable` sweeping pressure from inlet to outlet.\n\n"
            + "### Step 2: Initial Pipeline Sizing\n"
            + "Start with an estimated diameter. Call `runPipeline` with pipe "
            + "geometry and flow conditions.\n\n" + "### Step 3: Parametric Diameter Study\n"
            + "Try 3-4 pipe diameters (e.g., 8\", 10\", 12\", 14\"). For each:\n"
            + "- Call `runPipeline` and record pressure drop and arrival pressure\n"
            + "- Check that arrival pressure meets specification\n"
            + "- Check that velocity is within erosional limits\n\n"
            + "### Step 4: Flow Assurance\n" + "For the selected diameter:\n"
            + "- Call `runFlowAssurance` with analysis='hydrateRiskMap'\n"
            + "- Call `runFlowAssurance` with analysis='waxAppearance'\n"
            + "- Call `runFlowAssurance` with analysis='CO2Corrosion'\n"
            + "- Call `runFlowAssurance` with analysis='erosion'\n\n"
            + "### Step 5: Steady-State Turndown\n"
            + "Call `runPipeline` at minimum flow rate (turndown) to check "
            + "liquid holdup and slugging risk.\n\n" + "### Step 6: Cooldown\n"
            + "Call `runFlowAssurance` with analysis='pipelineCooldown' "
            + "to determine no-touch time before hydrate risk.";
    return PromptMessage.withUserRole(new TextContent(text));
  }
}
