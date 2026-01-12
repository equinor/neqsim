package neqsim.integration;

import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.integration.ValidationFramework.*;

/**
 * Validators for process equipment (Separator, DistillationColumn, Heater, Cooler, etc.).
 * 
 * <p>
 * Checks:
 * <ul>
 * <li>Feed stream(s) properly connected</li>
 * <li>Operating parameters (pressure, temperature) in valid ranges</li>
 * <li>Equipment configuration (tray count, solver type, etc.)</li>
 * <li>No conflicting settings (e.g., outlet temp higher than inlet in cooler)</li>
 * </ul>
 */
public class EquipmentValidator {

  /**
   * Validate any ProcessEquipmentBaseClass before execution.
   * 
   * @param equipment The equipment to validate
   * @return ValidationResult with errors and warnings
   */
  public static ValidationResult validateEquipment(ProcessEquipmentBaseClass equipment) {
    String equipmentType = equipment.getClass().getSimpleName();
    ValidationBuilder builder = new ValidationBuilder(equipmentType);

    // Check: Equipment has a valid name
    if (equipment.getName() == null || equipment.getName().isEmpty()) {
      builder.checkTrue(false, "Equipment has no name",
          "Set equipment name: new " + equipmentType + "(\"myName\", ...)");
    }

    // Equipment-specific validation
    if (equipment instanceof Separator) {
      validateSeparator(builder, (Separator) equipment);
    } else if (equipment instanceof DistillationColumn) {
      validateDistillationColumn(builder, (DistillationColumn) equipment);
    } else if (equipment instanceof Heater) {
      validateHeater(builder, (Heater) equipment);
    } else if (equipment instanceof Cooler) {
      validateCooler(builder, (Cooler) equipment);
    } else {
      builder.addWarning("equipment", "Equipment type not specifically validated: " + equipmentType,
          "Ensure feed streams are connected and parameters are physically reasonable");
    }

    return builder.build();
  }

  /**
   * Separator-specific validation.
   *
   * @param builder the validation builder to add results to
   * @param separator the separator to validate
   */
  private static void validateSeparator(ValidationBuilder builder, Separator separator) {
    // Check: Inlet present
    try {
      StreamInterface feedStream = separator.getFeedStream();
      if (feedStream == null) {
        builder.checkTrue(false, CommonErrors.FEED_STREAM_NOT_SET,
            "Call separator.setInletStream(stream) to connect inlet");
      }
    } catch (Exception e) {
      builder.addWarning("separator", "Could not verify inlet stream",
          "Ensure inlet stream is connected before running");
    }

    // Check: Pressure valid
    try {
      double pressure = separator.getPressure();
      if (pressure <= 0) {
        builder.checkTrue(false, CommonErrors.INVALID_PRESSURE,
            CommonErrors.REMEDIATION_INVALID_PRESSURE);
      }
    } catch (Exception e) {
      builder.addWarning("separator", "Could not verify pressure setting",
          "Set separator pressure: separator.setPressure(value)");
    }

    // Check: Temperature valid
    try {
      double temperature = separator.getTemperature();
      if (temperature < 200.0) {
        builder.addWarning("separator", "Separator temperature very low (<200 K)",
            "Verify this is intentional; most separators operate above 250 K");
      }
    } catch (Exception e) {
      builder.addWarning("separator", "Could not verify temperature setting",
          "Set separator temperature: separator.setTemperature(value)");
    }
  }

  /**
   * Distillation column-specific validation.
   *
   * @param builder the validation builder to add results to
   * @param column the distillation column to validate
   */
  private static void validateDistillationColumn(ValidationBuilder builder,
      DistillationColumn column) {
    // Check: Number of trays reasonable (use getNumerOfTrays())
    try {
      int trayCount = column.getNumerOfTrays();
      if (trayCount < 2) {
        builder.checkTrue(false, "Distillation column has fewer than 2 stages",
            "Column must have at least 2 stages (condenser + reboiler)");
      }
      if (trayCount > 500) {
        builder.addWarning("distillation", "Distillation column has > 500 stages",
            "Large columns may take very long to converge. Consider staged distillation.");
      }
    } catch (Exception e) {
      builder.addWarning("distillation", "Could not verify stage count",
          "Ensure column is initialized with proper tray specification");
    }

    // Check: Feed tray position valid
    try {
      // Feed tray should be between reboiler (0) and condenser (last)
      builder.addWarning("distillation", "Feed tray position not automatically verified",
          "Ensure feed tray is not at condenser or reboiler. Typical: middle section");
    } catch (Exception e) {
      // Skip if not accessible
    }

    // Check: Pressure profile
    try {
      // Just warn about pressure settings
      builder.addWarning("distillation", "Pressure profile not automatically verified",
          "Set column pressure: setTopPressure() and setBottomPressure()");
    } catch (Exception e) {
      // Skip
    }

    // Check: Solver type specified
    try {
      builder.addWarning("distillation", "Solver type not automatically verified",
          "Specify solver: column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT or SEQUENTIAL or DAMPED)");
    } catch (Exception e) {
      // Skip
    }
  }

  /**
   * Heater-specific validation.
   *
   * @param builder the validation builder to add results to
   * @param heater the heater to validate
   */
  private static void validateHeater(ValidationBuilder builder, Heater heater) {
    // Check: Inlet present (Heater extends TwoPortEquipment)
    try {
      StreamInterface inletStream = heater.getInletStream();
      if (inletStream == null) {
        builder.checkTrue(false, CommonErrors.FEED_STREAM_NOT_SET,
            "Call heater.setInletStream(stream) to connect inlet");
      }
    } catch (Exception e) {
      builder.addWarning("heater", "Could not verify inlet stream",
          "Ensure inlet stream is connected");
    }

    // Check: Outlet temperature set
    try {
      StreamInterface outletStream = heater.getOutletStream();
      StreamInterface inletStream = heater.getInletStream();
      if (outletStream != null && inletStream != null) {
        // Outlet temperature should be higher than inlet for a heater
        if (outletStream.getFluid() != null && inletStream.getFluid() != null) {
          double outTemp = outletStream.getFluid().getTemperature();
          double inTemp = inletStream.getFluid().getTemperature();
          if (outTemp > 0 && inTemp > 0 && outTemp <= inTemp) {
            builder.addWarning("heater", "Outlet temperature not higher than inlet",
                "Heater should increase temperature. Set: heater.setOutTemperature(T > inlet)");
          }
        }
      }
    } catch (Exception e) {
      builder.addWarning("heater", "Could not verify heater configuration",
          "Ensure inlet and outlet parameters are set correctly");
    }
  }

  /**
   * Cooler-specific validation.
   *
   * @param builder the validation builder to add results to
   * @param cooler the cooler to validate
   */
  private static void validateCooler(ValidationBuilder builder, Cooler cooler) {
    // Check: Inlet present (Cooler extends TwoPortEquipment)
    try {
      StreamInterface inletStream = cooler.getInletStream();
      if (inletStream == null) {
        builder.checkTrue(false, CommonErrors.FEED_STREAM_NOT_SET,
            "Call cooler.setInletStream(stream) to connect inlet");
      }
    } catch (Exception e) {
      builder.addWarning("cooler", "Could not verify inlet stream",
          "Ensure inlet stream is connected");
    }

    // Check: Outlet temperature set
    try {
      StreamInterface outletStream = cooler.getOutletStream();
      StreamInterface inletStream = cooler.getInletStream();
      if (outletStream != null && inletStream != null) {
        // Outlet temperature should be lower than inlet for a cooler
        if (outletStream.getFluid() != null && inletStream.getFluid() != null) {
          double outTemp = outletStream.getFluid().getTemperature();
          double inTemp = inletStream.getFluid().getTemperature();
          if (outTemp > 0 && inTemp > 0 && outTemp >= inTemp) {
            builder.addWarning("cooler", "Outlet temperature not lower than inlet",
                "Cooler should decrease temperature. Set: cooler.setOutTemperature(T < inlet)");
          }
        }
      }
    } catch (Exception e) {
      builder.addWarning("cooler", "Could not verify cooler configuration",
          "Ensure inlet and outlet parameters are set correctly");
    }
  }

  /**
   * Helper: Check if equipment appears ready.
   *
   * @param equipment the equipment to check
   * @return true if equipment is ready
   */
  public static boolean isEquipmentReady(ProcessEquipmentBaseClass equipment) {
    ValidationResult result = validateEquipment(equipment);
    return result.isReady();
  }

  /**
   * Validate a sequence of equipment (checks inter-dependencies).
   *
   * @param sequence the sequence of equipment to validate
   * @return validation result with any errors or warnings
   */
  public static ValidationResult validateSequence(ProcessEquipmentBaseClass... sequence) {
    ValidationBuilder builder = new ValidationBuilder("Equipment Sequence");

    for (int i = 0; i < sequence.length; i++) {
      ProcessEquipmentBaseClass equipment = sequence[i];
      ValidationResult result = validateEquipment(equipment);

      if (!result.isReady()) {
        builder.checkTrue(false, "Equipment [" + i + "] (" + equipment.getName() + ") not ready",
            "Fix errors in " + equipment.getClass().getSimpleName() + ": "
                + result.getErrors().get(0).getMessage());
      }

      // Check: Output of (i) could be input to (i+1)
      if (i < sequence.length - 1) {
        try {
          // Only check if equipment is TwoPortEquipment (has outlet stream)
          if (equipment instanceof TwoPortEquipment) {
            StreamInterface outlet = ((TwoPortEquipment) equipment).getOutletStream();
            if (outlet == null) {
              builder.addWarning("sequence", "Equipment [" + i + "] has no outlet stream",
                  "Verify equipment outputs correctly");
            }
          }
        } catch (Exception e) {
          // Skip if outlet access fails
        }
      }
    }

    return builder.build();
  }
}
