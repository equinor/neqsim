/**
 * Produced water treatment equipment for offshore oil and gas facilities.
 *
 * <p>
 * This package provides equipment models for treating produced water to meet environmental
 * discharge specifications, particularly the Norwegian Continental Shelf (NCS) limit of 30 mg/L
 * oil-in-water.
 * </p>
 *
 * <h2>Equipment Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain} - Complete
 * treatment train with multiple stages</li>
 * <li>{@link neqsim.process.equipment.watertreatment.Hydrocyclone} - Centrifugal oil/water
 * separation</li>
 * </ul>
 *
 * <h2>Typical Treatment Train</h2>
 * 
 * <pre>
 * Production Separator → Hydrocyclone → Flotation → Skim Tank → Discharge
 *       1000 mg/L           50 mg/L      10 mg/L     &lt;30 mg/L
 * </pre>
 *
 * <h2>Regulatory Context</h2>
 * <ul>
 * <li><b>NCS:</b> 30 mg/L monthly weighted average</li>
 * <li><b>OSPAR:</b> 30 mg/L for North Sea operators</li>
 * <li><b>Zero Discharge:</b> Ultimate goal for sensitive areas</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.equipment.watertreatment;
