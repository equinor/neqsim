/**
 * Provides mapping utilities for translating gas-chromatography analyser labels to NeqSim
 * component names.
 *
 * <p>
 * The primary class {@link neqsim.thermo.util.componentmapping.GcComponentMap} loads a dictionary
 * of known GC aliases from a CSV resource and supports case-insensitive, whitespace-tolerant
 * resolution. It also identifies co-elution groups (multiple GC peaks that map to the same
 * NeqSim component) and provides PNA class information.
 * </p>
 *
 * @since 3.7.0
 */
package neqsim.thermo.util.componentmapping;
