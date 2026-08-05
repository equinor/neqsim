package neqsim.process.equipment.mixer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Mixes streams without flashing the combined inventory back to equilibrium.
 *
 * <p>
 * Active gas, hydrocarbon-liquid, aqueous, and solid phases are transferred by component identity into deterministic
 * output phase slots. This preserves the separated phase inventories while allowing the result to be used as a normal
 * NeqSim stream.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class StaticPhaseMixer extends StaticMixer {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for StaticPhaseMixer.
   *
   * @param name a {@link java.lang.String} object
   */
  public StaticPhaseMixer(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void mixStream() {
    SystemInterface outputSystem = mixedStream.getThermoSystem();
    List<PhaseType> outputPhaseTypes = collectOutputPhaseTypes();
    if (outputPhaseTypes.isEmpty()) {
      clearInventory(outputSystem);
      return;
    }

    outputSystem.setNumberOfPhases(outputPhaseTypes.size());
    ensureComponentSlate(outputSystem);
    clearInventory(outputSystem);
    for (int phaseIndex = 0; phaseIndex < outputPhaseTypes.size(); phaseIndex++) {
      outputSystem.setPhaseType(phaseIndex, outputPhaseTypes.get(phaseIndex));
    }

    double[] phaseTemperatures = new double[outputPhaseTypes.size()];
    for (int phaseIndex = 0; phaseIndex < phaseTemperatures.length; phaseIndex++) {
      phaseTemperatures[phaseIndex] = Double.NaN;
    }

    for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
      if (streams.get(streamIndex).getFlowRate("kg/hr") <= getMinimumFlow()) {
        continue;
      }
      SystemInterface inputSystem = streams.get(streamIndex).getThermoSystem();
      for (int inputPhaseIndex = 0; inputPhaseIndex < inputSystem.getNumberOfPhases(); inputPhaseIndex++) {
        if (inputSystem.getPhase(inputPhaseIndex).getNumberOfMolesInPhase() <= 0.0) {
          continue;
        }
        int outputPhaseIndex = outputPhaseTypes
            .indexOf(canonicalPhaseType(inputSystem.getPhase(inputPhaseIndex).getType()));
        if (Double.isNaN(phaseTemperatures[outputPhaseIndex])) {
          phaseTemperatures[outputPhaseIndex] = inputSystem.getPhase(inputPhaseIndex).getTemperature();
        }
        transferPhaseInventory(inputSystem, inputPhaseIndex, outputSystem, outputPhaseIndex);
      }
    }

    for (int phaseIndex = 0; phaseIndex < phaseTemperatures.length; phaseIndex++) {
      if (Double.isFinite(phaseTemperatures[phaseIndex])) {
        outputSystem.getPhase(phaseIndex).setTemperature(phaseTemperatures[phaseIndex]);
      }
    }
    outputSystem.init_x_y();
    outputSystem.initBeta();
    outputSystem.init(2);
  }

  /**
   * Collect the distinct active inlet phase types in deterministic output order.
   *
   * @return ordered phase types for the mixed stream
   */
  private List<PhaseType> collectOutputPhaseTypes() {
    List<PhaseType> phaseTypes = new ArrayList<PhaseType>();
    for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
      if (streams.get(streamIndex).getFlowRate("kg/hr") <= getMinimumFlow()) {
        continue;
      }
      SystemInterface inputSystem = streams.get(streamIndex).getThermoSystem();
      for (int phaseIndex = 0; phaseIndex < inputSystem.getNumberOfPhases(); phaseIndex++) {
        if (inputSystem.getPhase(phaseIndex).getNumberOfMolesInPhase() <= 0.0) {
          continue;
        }
        PhaseType phaseType = canonicalPhaseType(inputSystem.getPhase(phaseIndex).getType());
        if (!phaseTypes.contains(phaseType)) {
          phaseTypes.add(phaseType);
        }
      }
    }
    Collections.sort(phaseTypes, new Comparator<PhaseType>() {
      @Override
      public int compare(PhaseType first, PhaseType second) {
        return Integer.compare(phaseRank(first), phaseRank(second));
      }
    });
    return phaseTypes;
  }

  /**
   * Treat the generic and oil phase labels as one hydrocarbon-liquid inventory.
   *
   * @param phaseType inlet phase type
   * @return canonical phase type used in the mixed stream
   */
  private PhaseType canonicalPhaseType(PhaseType phaseType) {
    if (phaseType == PhaseType.OIL || phaseType == PhaseType.LIQUID) {
      return PhaseType.LIQUID;
    }
    return phaseType;
  }

  /**
   * Rank phase types so active-phase creation order cannot change the mixed result.
   *
   * @param phaseType phase type to rank
   * @return deterministic phase rank
   */
  private int phaseRank(PhaseType phaseType) {
    if (phaseType == PhaseType.GAS) {
      return 0;
    }
    if (phaseType == PhaseType.LIQUID) {
      return 1;
    }
    if (phaseType == PhaseType.AQUEOUS) {
      return 2;
    }
    return 3 + phaseType.ordinal();
  }

  /**
   * Add components that are absent from the cloned template without adding inventory.
   *
   * @param outputSystem mixed-stream thermodynamic system
   */
  private void ensureComponentSlate(SystemInterface outputSystem) {
    boolean componentAdded = false;
    for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
      if (streams.get(streamIndex).getFlowRate("kg/hr") <= getMinimumFlow()) {
        continue;
      }
      SystemInterface inputSystem = streams.get(streamIndex).getThermoSystem();
      for (int componentIndex = 0; componentIndex < inputSystem.getPhase(0).getNumberOfComponents(); componentIndex++) {
        ComponentInterface sourceComponent = inputSystem.getPhase(0).getComponent(componentIndex);
        if (outputSystem.getPhase(0).hasComponent(sourceComponent.getName())) {
          continue;
        }
        if (sourceComponent.isIsPlusFraction()) {
          String rawName = sourceComponent.getName().replaceFirst("_PC$", "");
          outputSystem.addPlusFraction(rawName, 0.0, sourceComponent.getMolarMass(),
              sourceComponent.getNormalLiquidDensity());
        } else if (sourceComponent.isIsTBPfraction()) {
          String rawName = sourceComponent.getName().replaceFirst("_PC$", "");
          outputSystem.addTBPfraction(rawName, 0.0, sourceComponent.getMolarMass(),
              sourceComponent.getNormalLiquidDensity());
        } else {
          outputSystem.addComponent(sourceComponent.getName(), 0.0);
        }
        componentAdded = true;
      }
    }
    if (componentAdded) {
      outputSystem.setMixingRule(outputSystem.getMixingRule());
    }
  }

  /**
   * Clear all component inventory while retaining the cloned model and component parameters.
   *
   * @param outputSystem mixed-stream thermodynamic system
   */
  private void clearInventory(SystemInterface outputSystem) {
    for (PhaseInterface phase : outputSystem.getPhases()) {
      if (phase == null) {
        continue;
      }
      phase.setEmptyFluid();
    }
    outputSystem.setTotalNumberOfMoles(0.0);
  }

  /**
   * Transfer one active inlet phase into the matching output phase by component name.
   *
   * @param inputSystem inlet thermodynamic system
   * @param inputPhaseIndex active inlet phase index
   * @param outputSystem mixed-stream thermodynamic system
   * @param outputPhaseIndex matching output phase index
   */
  private void transferPhaseInventory(SystemInterface inputSystem, int inputPhaseIndex, SystemInterface outputSystem,
      int outputPhaseIndex) {
    for (int componentIndex = 0; componentIndex < inputSystem.getPhase(inputPhaseIndex)
        .getNumberOfComponents(); componentIndex++) {
      ComponentInterface sourceComponent = inputSystem.getPhase(inputPhaseIndex).getComponent(componentIndex);
      double sourceMoles = sourceComponent.getNumberOfMolesInPhase();
      if (sourceMoles == 0.0) {
        continue;
      }
      ComponentInterface destinationComponent = outputSystem.getPhase(0).getComponent(sourceComponent.getName());
      double destinationMoles = sourceMoles;
      if (destinationComponent.getMolarMass() > 0.0) {
        destinationMoles *= sourceComponent.getMolarMass() / destinationComponent.getMolarMass();
      }
      outputSystem.addComponent(destinationComponent.getComponentNumber(), destinationMoles, outputPhaseIndex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    int templateIndex = -1;
    for (int k = 0; k < streams.size(); k++) {
      if (streams.get(k).getFlowRate("kg/hr") <= getMinimumFlow()) {
        continue;
      }
      streams.get(k).getThermoSystem().init(3);
      if (templateIndex < 0) {
        templateIndex = k;
      }
    }
    if (templateIndex < 0) {
      SystemInterface outputSystem = streams.get(0).getThermoSystem().clone();
      clearInventory(outputSystem);
      mixedStream.setThermoSystem(outputSystem);
      isActive(false);
      mixedStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      return;
    }

    isActive(true);
    mixedStream.setThermoSystem((streams.get(templateIndex).getThermoSystem().clone()));

    mixStream();

    if (mixedStream.getThermoSystem().getTotalNumberOfMoles() > 0.0) {
      mixedStream.getThermoSystem().initProperties();
    }
    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
