/**
 * Lifecycle management for ProcessSystem models (Digital Twins).
 *
 * <p>
 * This package provides infrastructure for managing process models throughout their entire
 * lifecycle, from concept design through operation to decommissioning. Key capabilities:
 * </p>
 *
 * <ul>
 * <li><b>State Serialization:</b> Export/import ProcessSystem state as JSON for versioning</li>
 * <li><b>Model Metadata:</b> Track lifecycle phase, validation history, responsible engineers</li>
 * <li><b>Audit Trail:</b> Record all model modifications for compliance and knowledge transfer</li>
 * <li><b>Checkpointing:</b> Save and restore simulation state for reproducibility</li>
 * </ul>
 *
 * <h2>Digital Twin Lifecycle Phases:</h2>
 * <ol>
 * <li><b>Concept:</b> Early screening studies, feasibility analysis</li>
 * <li><b>Design:</b> FEED, detailed engineering design</li>
 * <li><b>Commissioning:</b> Model tuning during plant startup</li>
 * <li><b>Operation:</b> Live digital twin, continuous calibration</li>
 * <li><b>Late-life:</b> Decommissioning planning, knowledge preservation</li>
 * </ol>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * // Create and configure process
 * ProcessSystem process = new ProcessSystem("Offshore Platform Train 1");
 * // ... add equipment ...
 *
 * // Create state snapshot with metadata
 * ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
 * state.setVersion("2.1.0");
 * state.setDescription("Post-turnaround update with new compressor curves");
 *
 * ModelMetadata metadata = state.getMetadata();
 * metadata.setAssetId("PLATFORM-A-TRAIN-1");
 * metadata.setLifecyclePhase(ModelMetadata.LifecyclePhase.OPERATION);
 * metadata.setResponsibleEngineer("jane.doe@company.com");
 * metadata.recordValidation("Matched separator P/T within 1%", "WELL-TEST-2024-05");
 *
 * // Save for version control
 * state.saveToFile("models/platform_a_train1_v2.1.0.json");
 *
 * // Later: load and restore
 * ProcessSystemState loaded =
 *     ProcessSystemState.loadFromFile("models/platform_a_train1_v2.1.0.json");
 * System.out.println("Model last validated: " + loaded.getMetadata().getLastValidated());
 * </pre>
 *
 * @see neqsim.process.processmodel.lifecycle.ProcessSystemState
 * @see neqsim.process.processmodel.lifecycle.ModelMetadata
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.processmodel.lifecycle;
