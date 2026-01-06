/**
 * Technical Requirements Document (TORG) framework for process design.
 *
 * <p>
 * This package provides a structured approach to managing Technical Requirements Documents (TORG)
 * that specify design standards and methods to be used in process design. A TORG typically defines:
 * </p>
 * <ul>
 * <li>Project identification and metadata</li>
 * <li>Applicable design standards per equipment category (NORSOK, ASME, API, DNV, ISO, etc.)</li>
 * <li>Company-specific design requirements and guidelines</li>
 * <li>Environmental design conditions (temperature, seismic zone, etc.)</li>
 * <li>Safety factors and margins</li>
 * <li>Material specifications</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 *
 * <h3>{@link neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument}</h3>
 * <p>
 * The main data class representing a TORG. Built using the builder pattern for flexible
 * construction. Supports defining standards per category or per equipment type.
 * </p>
 *
 * <h3>{@link neqsim.process.mechanicaldesign.torg.TorgDataSource}</h3>
 * <p>
 * Interface for loading TORG documents from various sources (CSV, database, API).
 * </p>
 *
 * <h3>{@link neqsim.process.mechanicaldesign.torg.CsvTorgDataSource}</h3>
 * <p>
 * CSV-based implementation supporting both standards-focused and master file formats.
 * </p>
 *
 * <h3>{@link neqsim.process.mechanicaldesign.torg.DatabaseTorgDataSource}</h3>
 * <p>
 * Database-based implementation using the NeqSim process design database.
 * </p>
 *
 * <h3>{@link neqsim.process.mechanicaldesign.torg.TorgManager}</h3>
 * <p>
 * Manager class for loading and applying TORG to process systems.
 * </p>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * // Create TORG manually
 * TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
 *     .projectId("PROJECT-001").projectName("Offshore Gas Platform").companyIdentifier("Equinor")
 *     .addStandard("pressure vessel design code", StandardType.ASME_VIII_DIV1)
 *     .addStandard("separator process design", StandardType.API_12J)
 *     .addStandard("pipeline design codes", StandardType.NORSOK_L_001)
 *     .environmentalConditions(-40.0, 45.0).build();
 *
 * // Apply to process system
 * TorgManager manager = new TorgManager();
 * manager.apply(torg, processSystem);
 *
 * // Or load from CSV file
 * manager.addDataSource(CsvTorgDataSource.fromResource("designdata/torg/projects.csv"));
 * manager.loadAndApply("PROJECT-001", processSystem);
 * </pre>
 *
 * <h2>CSV File Format</h2>
 * <p>
 * The CSV data source supports a standards-focused format where each row defines one standard
 * assignment:
 * </p>
 * 
 * <pre>
 * PROJECT_ID,PROJECT_NAME,COMPANY,DESIGN_CATEGORY,STANDARD_CODE,VERSION
 * PROJ-001,Platform A,Equinor,pressure vessel design code,ASME-VIII-Div1,2021
 * PROJ-001,Platform A,Equinor,separator process design,API-12J,8th Ed
 * </pre>
 *
 * @author esol
 * @version 1.0
 * @see neqsim.process.mechanicaldesign.designstandards.StandardType
 * @see neqsim.process.mechanicaldesign.designstandards.StandardRegistry
 */
package neqsim.process.mechanicaldesign.torg;
