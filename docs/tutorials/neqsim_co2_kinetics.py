"""
====================================================================================================
NEQSIM CO2 IMPURITY KINETIC MODEL & MULTI-PHASE EXPERIMENT ENGINE
====================================================================================================
This module provides an illustrative rate-law engine that uses the NeqSim Java SRK EOS when the
Python package is available and a documented screening correlation otherwise.

Features:
- Guaranteed non-negative zero-floor clipping (max(0.0, val)) eliminating -0.00 formatting artifacts
- Built-in 1-hour resolution table generator via get_table_results(resolution_hours=1.0)
- Super-Visible 3-Panel Plotting Engine with Shaded Crimson Red Fill & Giant Annotation Box for H2SO4 Acid Peak
- Reaction R3b uses H2S and NO2 as co-catalysts for balanced SO2 oxidation
- Slower Reaction R4 Kinetics (2 NO + O2 -> 2 NO2) allowing NO gas persistence up to ~9.23 ppm and O2 coexistence
- NeqSim Java SRK EOS thermodynamic calculations when the Python package is available

The default kinetic parameters are uncalibrated and must not be used as qualified design data.
"""

import warnings

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

try:
    from scipy.integrate import solve_ivp
except ImportError as scipy_import_error:
    solve_ivp = None
    SCIPY_IMPORT_ERROR = scipy_import_error
else:
    SCIPY_IMPORT_ERROR = None


# ==================================================================================================
# FUNDAMENTAL PHYSICAL AND THERMODYNAMIC CONSTANTS
# ==================================================================================================
R_GAS = 8.314462618             # Universal Gas Constant [J / (mol * K)]
MW_CO2 = 44.0095                # Molar Mass of CO2 [g / mol]
MW_N2 = 28.0134                 # Molar Mass of N2 [g / mol]
P_CRIT_CO2_BAR = 73.8           # Critical Pressure of CO2 [bar]
T_CRIT_CO2_K = 304.13           # Critical Temperature of CO2 [K]


# ==================================================================================================
# ILLUSTRATIVE, UNCALIBRATED REACTION KINETIC PARAMETERS
# ==================================================================================================
DEFAULT_KINETIC_PARAMS = {
    'R1':  {'name': 'SO2 + 0.5 O2 + H2O <-> H2SO4',           'A': 5.0e5,     'Ea': 30000.0, 'units': 'm3 / (kmol * s)'},
    'R2':  {'name': 'H2S + 3 NO2 <-> SO2 + H2O + 3 NO',       'A': 1.0e10,    'Ea': 30000.0, 'units': 'm3 / (kmol * s)'},
    'R3a': {'name': 'SO2 + NO2 + H2O <-> NO + H2SO4',         'A': 1.0e5,     'Ea': 35000.0, 'units': 'm3 / (kmol * s)'},
    'R3b': {'name': 'SO2 + 0.5 O2 + H2O -> H2SO4 (H2S/NO2 co-catalysed)', 'A': 2.0e9, 'Ea': 18000.0, 'units': 'illustrative'},
    'R4':  {'name': '2 NO + O2 <-> 2 NO2',                    'A': 500.0,     'Ea': -4400.0, 'units': 'm6 / (kmol2 * s)'},
    'R5':  {'name': '3 NO2 + H2O <-> 2 HNO3 + NO',            'A': 2.4e6,     'Ea': 28000.0, 'units': 'm3 / (kmol * s)'},
    'R6':  {'name': 'H2S + 1.5 O2 <-> SO2 + H2O',             'A': 5.0e5,     'Ea': 45000.0, 'units': 'm3 / (kmol * s)'},
    'R7':  {'name': '5 H2S + 6 NO + 4 H2O -> 6 NH3 + 5 SO2',  'A': 2.0e6,     'Ea': 12000.0, 'units': 'm3 / (kmol * s)'},
    'R8_cs': {'name': 'H2S + 0.5 O2 -> 1/8 S8 + H2O (CS)',    'A': 1.5e4,     'Ea': 42000.0, 'units': 'm3 / (kmol * s)'},
    'R8_ss': {'name': 'H2S + 0.5 O2 -> 1/8 S8 + H2O (SS)',    'A': 2.0e3,     'Ea': 65000.0, 'units': 'm3 / (kmol * s)'}
}

DG_SO2_STDGIBBS = -300.1e3
DG_O2_STDGIBBS = 0.0
DG_H2O_STDGIBBS = -237.1e3
DG_H2SO4_STDGIBBS = -690.1e3
DG_H2S_STDGIBBS = -33.4e3
DG_NO2_STDGIBBS = 51.3e3
DG_NO_STDGIBBS = 86.6e3
DG_HNO3_STDGIBBS = -74.7e3

MAX_KEQ_EXPONENT = 300.0        # Exponential ceiling to prevent numerical overflow in Keq
MIN_CONCENTRATION_FLOOR = 1e-25  # Minimum concentration floor to prevent log underflow in ODEs
MOISTURE_REF_PPM = 50.0         # Reference moisture concentration scale for hydration factor [ppm]


class CO2ImpurityKineticsModel:
    """
    Experimental simulator for impurity-reaction screening in CO2 streams.

    Uses NeqSim SRK thermodynamics when available. The fallback correlation and kinetic
    parameters are illustrative and require independent calibration and validation.
    """

    SPECIES = (
        'H2S', 'SO2', 'NO2', 'NO', 'O2', 'H2O',
        'H2SO4', 'HNO3', 'S8', 'NH3'
    )

    SUPPORTED_MATERIALS = ('carbon_steel', 'magnetite', 'stainless_steel', 'inert')

    def __init__(self, T_kelvin=298.15, P_bar=100.0, water_ppm=50.0, material='carbon_steel'):
        self.T = T_kelvin
        self.P = P_bar
        self.water_ppm = water_ppm
        self.material = material.lower().replace(' ', '_')
        if self.material not in self.SUPPORTED_MATERIALS:
            self.material = 'carbon_steel'

        self.kinetic_params = {k: v.copy() for k, v in DEFAULT_KINETIC_PARAMS.items()}

        self.diameter_cm = 6.50
        self.volume_ml = 300.0
        self.mass_flow_g_h = 50.0
        self.length_cm = self.volume_ml / (np.pi * (self.diameter_cm**2) / 4.0)

        self.molar_density, self.phase, self.phi_dict = self._calculate_srk_fugacities(T_kelvin, P_bar)

    def set_reaction_constants(self, reaction_identifier, A_forward=None, Ea_forward_kJ_mol=None):
        rxn_id = None
        clean_id = str(reaction_identifier).strip().lower()

        if clean_id.upper() in self.kinetic_params:
            rxn_id = clean_id.upper()
        elif 'r3b' in clean_id or 'so2 + h2s + no2' in clean_id:
            rxn_id = 'R3b'
        elif 'r3a' in clean_id or ('so2 + no2 + h2o' in clean_id and 'h2s' not in clean_id):
            rxn_id = 'R3a'
        elif 'r2' in clean_id or 'h2s + 3 no2' in clean_id:
            rxn_id = 'R2'
        elif 'r1' in clean_id or 'so2 + 0.5 o2' in clean_id:
            rxn_id = 'R1'
        elif 'r4' in clean_id or '2 no + o2' in clean_id:
            rxn_id = 'R4'
        elif 'r5' in clean_id or '3 no2 + h2o' in clean_id:
            rxn_id = 'R5'
        elif 'r6' in clean_id or 'h2s + 1.5 o2' in clean_id:
            rxn_id = 'R6'
        elif 'r7' in clean_id or '5 h2s + 6 no' in clean_id:
            rxn_id = 'R7'
        elif 'r8' in clean_id or 's8' in clean_id:
            rxn_id = 'R8_cs' if self.material in ['carbon_steel', 'magnetite'] else 'R8_ss'

        if rxn_id and rxn_id in self.kinetic_params:
            if A_forward is not None:
                self.kinetic_params[rxn_id]['A'] = float(A_forward)
            if Ea_forward_kJ_mol is not None:
                self.kinetic_params[rxn_id]['Ea'] = float(Ea_forward_kJ_mol) * 1000.0

    def set_reactor_geometry(self, diameter_cm=None, length_cm=None, volume_ml=None, mass_flow_g_h=None):
        if diameter_cm is not None:
            self.diameter_cm = float(diameter_cm)
        if mass_flow_g_h is not None:
            self.mass_flow_g_h = float(mass_flow_g_h)

        A_cross_cm2 = np.pi * (self.diameter_cm**2) / 4.0

        if volume_ml is not None:
            self.volume_ml = float(volume_ml)
            self.length_cm = self.volume_ml / A_cross_cm2
        elif length_cm is not None:
            self.length_cm = float(length_cm)
            self.volume_ml = A_cross_cm2 * self.length_cm

    def get_fluid_properties(self):
        return {
            'temperature_C': self.T - 273.15,
            'temperature_K': self.T,
            'pressure_bar': self.P,
            'phase': self.phase,
            'molar_density_kmol_m3': self.molar_density,
            'mass_density_kg_m3': self.molar_density * MW_CO2,
            'mass_density_g_ml': (self.molar_density * MW_CO2) * 1e-3,
            'phi_fugacities': self.phi_dict,
            'thermodynamic_backend': self.thermodynamic_backend,
        }

    def get_reaction_rates(self, moisture_ppm=10.0):
        return self._calculate_pure_physical_rate_constants(moisture_ppm)

    def get_reactor_geometry(self):
        A_cross_cm2 = np.pi * (self.diameter_cm**2) / 4.0
        rho_g_ml = (self.molar_density * MW_CO2) * 1e-3
        m_reactor_g = self.volume_ml * rho_g_ml
        tau_hours = m_reactor_g / self.mass_flow_g_h if self.mass_flow_g_h > 0 else 0.0

        return {
            'volume_ml': self.volume_ml,
            'volume_m3': self.volume_ml * 1e-6,
            'diameter_cm': self.diameter_cm,
            'diameter_m': self.diameter_cm * 1e-2,
            'cross_sectional_area_cm2': A_cross_cm2,
            'cross_sectional_area_m2': A_cross_cm2 * 1e-4,
            'length_cm': self.length_cm,
            'length_m': self.length_cm * 1e-2,
            'mass_flow_g_h': self.mass_flow_g_h,
            'inventory_mass_g': m_reactor_g,
            'residence_time_hours': tau_hours,
            'residence_time_seconds': tau_hours * 3600.0
        }

    def generate_reactor_report(self):
        geom = self.get_reactor_geometry()
        props = self.get_fluid_properties()

        report_lines = [
            "1. Reactor Geometry & Length (L) Derivation",
            f"Target Volume (V): {geom['volume_ml']:.1f} mL = {geom['volume_ml']:.1f} cm3 = {geom['volume_m3']:.1e} m3",
            f"Inner Diameter (D): {geom['diameter_cm']:.2f} cm = {geom['diameter_m']:.4f} m",
            f"Cross-Sectional Area (A_cross): A_cross = pi * D^2 / 4 = pi * ({geom['diameter_cm']:.2f} cm)^2 / 4 = {geom['cross_sectional_area_cm2']:.4f} cm2 ({geom['cross_sectional_area_m2']:.5e} m2)",
            f"Calculated Reactor Length (L): L = V / A_cross = {geom['volume_ml']:.1f} cm3 / {geom['cross_sectional_area_cm2']:.4f} cm2 = {geom['length_cm']:.4f} cm ({geom['length_m']:.6f} m)",
            "",
            f"2. Hydrodynamic Residence Time (tau) at {props['pressure_bar']:.1f} bar, {props['temperature_C']:.1f}°C",
            f"Fluid density from {props['thermodynamic_backend']}: {props['phase'].capitalize()} CO2 density rho = {props['mass_density_kg_m3']:.2f} kg/m3 (rho_m = {props['molar_density_kmol_m3']:.4f} kmol/m3).",
            f"Liquid Mass Inventory: m_reactor = {geom['volume_ml']:.1f} mL * {props['mass_density_g_ml']:.5f} g/mL = {geom['inventory_mass_g']:.2f} grams of {props['phase']} CO2.",
            f"Mass Flow Rate (m_dot): {geom['mass_flow_g_h']:.1f} g/h.",
            f"CSTR Residence Time (tau): tau = m_reactor / m_dot = {geom['inventory_mass_g']:.2f} g / {geom['mass_flow_g_h']:.1f} g/h = {geom['residence_time_hours']:.4f} HOURS ({geom['residence_time_seconds']:.1f} seconds)"
        ]

        return "\n".join(report_lines)

    def get_table_results(self, sim_results, resolution_hours=1.0):
        t_h = sim_results['time_hours']
        max_h = t_h[-1]
        target_hours = np.arange(0.0, max_h + resolution_hours/2.0, resolution_hours)

        rows = []
        for target in target_hours:
            row = {'Time (h)': round(float(target), 1)}
            for species, decimals in {
                'H2S': 2, 'SO2': 2, 'NO2': 2, 'NO': 4, 'O2': 2,
                'H2O': 2, 'H2SO4': 4, 'HNO3': 4, 'NH3': 4, 'S8': 4
            }.items():
                value = np.interp(target, t_h, sim_results['ppm'][species])
                row[f'{species} (ppm)'] = round(max(0.0, float(value)), decimals)
            rows.append(row)

        df = pd.DataFrame(rows)
        return df

    def _calculate_srk_fugacities(self, T_K, P_bar):
        try:
            from neqsim.thermo.thermoTools import TPflash, fluid

            f = fluid("srk")
            f.setTemperature(T_K)
            f.setPressure(P_bar)

            f.addComponent("CO2", 0.99995)
            f.addComponent("H2S", 10.0e-6)
            f.addComponent("oxygen", 10.0e-6)
            f.addComponent("water", 10.0e-6)
            f.addComponent("ammonia", 10.0e-6)
            f.addComponent("S8", 10.0e-6)

            f.addComponent("SO2", 10.0e-6, 430.8, 78.84, 0.2454)
            f.addComponent("NO2", 10.0e-6, 431.4, 101.0, 0.834)
            f.addComponent("NO", 10.0e-6, 180.0, 64.8, 0.588)
            f.addComponent("H2SO4", 10.0e-6, 924.0, 64.0, 0.536)
            f.addComponent("HNO3", 10.0e-6, 520.0, 68.9, 0.714)

            f.setMixingRule("classic")
            TPflash(f)

            phase = f.getPhase(0)
            phase_type = str(phase.getPhaseTypeName()).lower()
            if "gas" in phase_type or "vap" in phase_type:
                phase_name = "gas"
            else:
                phase_name = "liquid"

            density_kg_m3 = float(phase.getDensity())
            molar_mass_g_mol = float(phase.getMolarMass()) * 1000.0
            rho_m = density_kg_m3 / molar_mass_g_mol if molar_mass_g_mol > 0 else density_kg_m3 / 44.0095

            phi_dict = {}
            for i in range(phase.getNumberOfComponents()):
                comp = phase.getComponent(i)
                name = str(comp.getComponentName())
                phi = float(comp.getFugacityCoefficient())

                if name == "oxygen":
                    phi_dict["O2"] = phi
                elif name == "water":
                    phi_dict["H2O"] = phi
                elif name == "ammonia":
                    phi_dict["NH3"] = phi
                elif name in self.SPECIES:
                    phi_dict[name] = phi

            for s in self.SPECIES:
                if s not in phi_dict:
                    phi_dict[s] = 0.95 if phase_name == "liquid" else 0.65

            self.thermodynamic_backend = "NeqSim SRK EOS"
            return max(rho_m, 0.05), phase_name, phi_dict

        except (ImportError, ModuleNotFoundError) as error:
            warnings.warn(
                "NeqSim Python thermodynamics is unavailable; using the tutorial's "
                "screening density/fugacity correlation instead. "
                f"Original import error: {error}",
                RuntimeWarning,
                stacklevel=2,
            )
            phi_dict = {}
            if T_K < T_CRIT_CO2_K:
                Tr = T_K / T_CRIT_CO2_K
                tau = 1.0 - Tr
                ln_Pr = (-7.06 * tau + 1.94 * (tau**1.5) - 1.64 * (tau**3) - 2.5 * (tau**4)) / Tr
                P_sat = P_CRIT_CO2_BAR * np.exp(ln_Pr)
            else:
                P_sat = P_CRIT_CO2_BAR

            if P_bar < P_sat:
                phase_name = "gas"
                Z = 0.75 + 0.15 * (T_K / 300.0) - 0.05 * (P_bar / 40.0)
                Z = max(min(Z, 0.95), 0.60)
                rho_kg_m3 = (P_bar * 1e5 * (MW_CO2 * 1e-3)) / (Z * R_GAS * T_K)
                phi_CO2 = np.exp(min(0.0, -0.15 * (P_bar / 30.0) * (298.15 / T_K)))
                for s in self.SPECIES:
                    phi_dict[s] = phi_CO2 * 0.65
            else:
                phase_name = "liquid"
                if T_K <= 250.0:
                    rho_kg_m3 = 1060.0 - 1.2 * (T_K - 240.0) + 1.5 * (P_bar - 20.0)
                else:
                    rho_kg_m3 = 820.0 + 2.5 * (P_bar - P_CRIT_CO2_BAR) - 4.0 * (T_K - T_CRIT_CO2_K)
                for s in self.SPECIES:
                    phi_dict[s] = 0.95

            rho_m = max(rho_kg_m3 / MW_CO2, 0.05)
            self.thermodynamic_backend = "illustrative screening correlation"
            return rho_m, phase_name, phi_dict

    def _calculate_pure_physical_rate_constants(self, moisture_ppm):
        T = self.T

        dG1 = DG_H2SO4_STDGIBBS - (DG_SO2_STDGIBBS + 0.5 * DG_O2_STDGIBBS + DG_H2O_STDGIBBS)
        Keq1 = max(np.exp(min(-dG1 / (R_GAS * T), MAX_KEQ_EXPONENT)), 1e-15)

        dG2 = (DG_SO2_STDGIBBS + DG_H2O_STDGIBBS + 3.0 * DG_NO_STDGIBBS) - (DG_H2S_STDGIBBS + 3.0 * DG_NO2_STDGIBBS)
        Keq2 = max(np.exp(min(-dG2 / (R_GAS * T), MAX_KEQ_EXPONENT)), 1e-15)

        dG3 = (DG_NO_STDGIBBS + DG_H2SO4_STDGIBBS) - (DG_SO2_STDGIBBS + DG_NO2_STDGIBBS + DG_H2O_STDGIBBS)
        Keq3 = max(np.exp(min(-dG3 / (R_GAS * T), MAX_KEQ_EXPONENT)), 1e-15)

        dG4 = (2.0 * DG_NO2_STDGIBBS) - (2.0 * DG_NO_STDGIBBS + DG_O2_STDGIBBS)
        Keq4 = max(np.exp(min(-dG4 / (R_GAS * T), MAX_KEQ_EXPONENT)), 1e-15)

        dG5 = (2.0 * DG_HNO3_STDGIBBS + DG_NO_STDGIBBS) - (3.0 * DG_NO2_STDGIBBS + DG_H2O_STDGIBBS)
        Keq5 = np.exp(-dG5 / (R_GAS * T))

        dG6 = (DG_SO2_STDGIBBS + DG_H2O_STDGIBBS) - (DG_H2S_STDGIBBS + 1.5 * DG_O2_STDGIBBS)
        Keq6 = max(np.exp(min(-dG6 / (R_GAS * T), MAX_KEQ_EXPONENT)), 1e-15)

        p = self.kinetic_params
        k1_f = p['R1']['A'] * np.exp(-p['R1']['Ea'] / (R_GAS * T))
        k2_f = p['R2']['A'] * np.exp(-p['R2']['Ea'] / (R_GAS * T))
        k3a_f = p['R3a']['A'] * np.exp(-p['R3a']['Ea'] / (R_GAS * T))
        k3b_f = p['R3b']['A'] * np.exp(-p['R3b']['Ea'] / (R_GAS * T))
        k4_f = p['R4']['A'] * np.exp(-p['R4']['Ea'] / (R_GAS * T)) if p['R4']['Ea'] > 0 else p['R4']['A'] * np.exp(530.0 / T)
        k5_f = p['R5']['A'] * np.exp(-p['R5']['Ea'] / (R_GAS * T))
        k6_f = p['R6']['A'] * np.exp(-p['R6']['Ea'] / (R_GAS * T))
        k7_f = p['R7']['A'] * np.exp(-p['R7']['Ea'] / (R_GAS * T))

        r8_key = 'R8_cs' if self.material in ['carbon_steel', 'magnetite'] else 'R8_ss'
        k8_f = p[r8_key]['A'] * np.exp(-p[r8_key]['Ea'] / (R_GAS * T))
        Ea8 = p[r8_key]['Ea'] / 1000.0

        k1_r = k1_f / Keq1 if Keq1 > 1e-15 else 0.0
        k2_r = k2_f / Keq2 if Keq2 > 1e-15 else 0.0
        k3a_r = k3a_f / Keq3 if Keq3 > 1e-15 else 0.0
        k4_r = k4_f / Keq4 if Keq4 > 1e-15 else 0.0
        k5_r = k5_f / Keq5
        k6_r = k6_f / Keq6 if Keq6 > 1e-15 else 0.0
        k7_r = 0.0

        safe_moisture_ppm = max(float(moisture_ppm), 0.0)
        moisture_factor = 0.25 + 0.75 * (1.0 - np.exp(-min(safe_moisture_ppm / MOISTURE_REF_PPM, 50.0)))
        k1_f *= moisture_factor
        k3a_f *= moisture_factor

        return {
            'k1_f': k1_f, 'k1_r': k1_r, 'Keq1': Keq1,
            'k2_f': k2_f, 'k2_r': k2_r, 'Keq2': Keq2,
            'k3a_f': k3a_f, 'k3a_r': k3a_r, 'k3b_f': k3b_f, 'Keq3': Keq3,
            'k4_f': k4_f, 'k4_r': k4_r, 'Keq4': Keq4,
            'k5_f': k5_f, 'k5_r': k5_r, 'Keq5': Keq5,
            'k6_f': k6_f, 'k6_r': k6_r, 'Keq6': Keq6,
            'k7_f': k7_f, 'k7_r': k7_r,
            'k8_f': k8_f, 'Ea8': Ea8,
            'material': self.material,
            'moisture_factor': moisture_factor
        }

    def rhs(self, t, C, rates_dict, C_in=None, space_time_sec=None):
        C_raw = np.clip(C, MIN_CONCENTRATION_FLOOR, 1e5 * self.molar_density)

        phi = self.phi_dict
        C_H2S   = max(0.0, C_raw[0] * phi['H2S'])
        C_SO2   = max(0.0, C_raw[1] * phi['SO2'])
        C_NO2   = max(0.0, C_raw[2] * phi['NO2'])
        C_NO    = max(0.0, C_raw[3] * phi['NO'])
        C_O2    = max(0.0, C_raw[4] * phi['O2'])
        C_H2O   = max(0.0, C_raw[5] * phi['H2O'])
        C_H2SO4 = max(0.0, C_raw[6] * phi['H2SO4'])
        C_HNO3  = max(0.0, C_raw[7] * phi['HNO3'])

        k1_f, k1_r   = rates_dict['k1_f'], rates_dict['k1_r']
        k2_f, k2_r   = rates_dict['k2_f'], rates_dict['k2_r']
        k3a_f, k3a_r = rates_dict['k3a_f'], rates_dict['k3a_r']
        k3b_f        = rates_dict['k3b_f']
        k4_f, k4_r   = rates_dict['k4_f'], rates_dict['k4_r']
        k5_f, k5_r   = rates_dict['k5_f'], rates_dict['k5_r']
        k6_f, k6_r   = rates_dict['k6_f'], rates_dict['k6_r']
        k7_f         = rates_dict['k7_f']
        k8_f         = rates_dict['k8_f']

        r1 = k1_f * C_SO2 * (C_O2**0.5) * C_H2O - k1_r * C_H2SO4
        r2 = k2_f * C_H2S * C_NO2 - k2_r * C_SO2 * C_H2O * (C_NO**3)
        r3a = k3a_f * C_SO2 * C_NO2 * C_H2O - k3a_r * C_NO * C_H2SO4
        # R3b: H2S and NO2 are co-catalysts; its balanced net reaction is the same as R1.
        r3b = k3b_f * C_SO2 * (C_O2**0.5) * (C_H2S**0.5) * C_NO2
        r4 = k4_f * (C_NO**2) * C_O2 - k4_r * (C_NO2**2)
        r5 = k5_f * (C_NO2**3) * C_H2O - k5_r * (C_HNO3**2) * C_NO
        r6 = k6_f * C_H2S * (C_O2**1.5) - k6_r * C_SO2 * C_H2O
        r7 = k7_f * C_H2S * C_NO * C_H2O
        r8 = k8_f * C_H2S * (C_O2**0.5)

        R_H2S   = - r2 - r6 - 5.0 * r7 - r8
        R_SO2   = - r1 + r2 + r6 - r3a - r3b + 5.0 * r7
        R_NO2   = - 3.0 * r2 - r3a + 2.0 * r4 - 3.0 * r5
        R_NO    = + 3.0 * r2 + r3a - 2.0 * r4 + r5 - 6.0 * r7
        R_O2    = - 0.5 * r1 - 0.5 * r3b - 1.5 * r6 - r4 - 0.5 * r8
        R_H2O   = - r1 + r2 + r6 - r3a - r3b - r5 - 4.0 * r7 + r8
        R_H2SO4 = + r1 + r3a + r3b
        R_HNO3  = + 2.0 * r5
        R_S8    = + 0.125 * r8
        R_NH3   = + 6.0 * r7

        R_vector = np.array([
            R_H2S, R_SO2, R_NO2, R_NO, R_O2, R_H2O,
            R_H2SO4, R_HNO3, R_S8, R_NH3
        ])

        if C_in is not None and space_time_sec is not None and space_time_sec > 0.0:
            dC_dt = (C_in - C) / space_time_sec + R_vector
        else:
            dC_dt = R_vector

        return dC_dt

    def simulate(self, initial_ppm, duration_sec=100000.0, num_points=100, feed_ppm=None, space_time_sec=None):
        if solve_ivp is None:
            raise ImportError(
                "SciPy is required to run the kinetics integration. Install it with "
                "`python -m pip install scipy`."
            ) from SCIPY_IMPORT_ERROR

        t_span = (0.0, duration_sec)
        t_eval = np.linspace(0.0, duration_sec, num_points)

        C0 = np.zeros(len(self.SPECIES))
        for idx, spec in enumerate(self.SPECIES):
            if spec in initial_ppm:
                C0[idx] = (initial_ppm[spec] * 1.0e-6) * self.molar_density

        C_in = None
        if feed_ppm is not None:
            C_in = np.zeros(len(self.SPECIES))
            for idx, spec in enumerate(self.SPECIES):
                if spec in feed_ppm:
                    C_in[idx] = (feed_ppm[spec] * 1.0e-6) * self.molar_density

        moisture_basis = feed_ppm if feed_ppm is not None else initial_ppm
        moisture_ppm = moisture_basis.get('H2O', self.water_ppm)
        rates_dict = self._calculate_pure_physical_rate_constants(moisture_ppm)

        sol = solve_ivp(
            fun=lambda t, y: self.rhs(t, y, rates_dict, C_in=C_in, space_time_sec=space_time_sec),
            t_span=t_span,
            y0=C0,
            t_eval=t_eval,
            method='Radau',
            rtol=1e-6,
            atol=1e-12
        )

        if not sol.success:
            raise RuntimeError(f"Kinetics integration failed: {sol.message}")

        ppm_results = {}
        for idx, spec in enumerate(self.SPECIES):
            raw_ppm = (sol.y[idx, :] / self.molar_density) * 1.0e6
            ppm_results[spec] = np.maximum(0.0, raw_ppm)

        return {
            'time_seconds': sol.t,
            'time_hours': sol.t / 3600.0,
            'ppm': ppm_results,
            'molar_density': self.molar_density,
            'phase': self.phase,
            'phi': self.phi_dict,
            'rates': rates_dict
        }


# ==================================================================================================
# HIGH-LEVEL MULTI-PHASE CSTR EXPERIMENT MANAGER CLASS
# ==================================================================================================
class CO2ImpurityReactorExperiment:
    """
    High-level Manager for Setting Up, Configuring, and Executing Multi-Phase CSTR Experiments.
    Uses NeqSim Java SRK EOS when available, with an explicit screening fallback.
    """

    def __init__(self, target_pressure_bar=25.0, target_temp_C=-25.0, diameter_cm=6.5, volume_ml=300.0, mass_flow_g_h=50.0, material='carbon_steel'):
        self.target_P = float(target_pressure_bar)
        self.target_T_C = float(target_temp_C)
        self.target_T_K = self.target_T_C + 273.15
        self.diameter_cm = float(diameter_cm)
        self.volume_ml = float(volume_ml)
        self.mass_flow_g_h = float(mass_flow_g_h)
        self.material = material

        self.initial_gas = 'N2'
        self.initial_P_bar = 1.0
        self.initial_T_C = 25.0

        self.model = CO2ImpurityKineticsModel(
            T_kelvin=self.target_T_K,
            P_bar=self.target_P,
            material=self.material
        )
        self.model.set_reactor_geometry(
            diameter_cm=self.diameter_cm,
            volume_ml=self.volume_ml,
            mass_flow_g_h=self.mass_flow_g_h
        )

        self.phases = []
        self.simulation_results = None

    def set_initial_vessel_charge(self, gas_name='N2', pressure_bar=1.0, temp_C=25.0):
        """Record initial-charge metadata.

        The tutorial kinetics state tracks only the impurity species in ``SPECIES``. The initial
        inert-gas inventory is therefore reported as metadata and is not included in the species
        ODE balance.
        """
        self.initial_gas = str(gas_name).upper()
        self.initial_P_bar = float(pressure_bar)
        self.initial_T_C = float(temp_C)

    def set_reactor_geometry(self, diameter_cm=None, length_cm=None, volume_ml=None, mass_flow_g_h=None):
        self.model.set_reactor_geometry(
            diameter_cm=diameter_cm,
            length_cm=length_cm,
            volume_ml=volume_ml,
            mass_flow_g_h=mass_flow_g_h
        )
        geom = self.model.get_reactor_geometry()
        self.diameter_cm = geom['diameter_cm']
        self.volume_ml = geom['volume_ml']
        self.mass_flow_g_h = geom['mass_flow_g_h']

    def set_reaction_constants(self, reaction_identifier, A_forward=None, Ea_forward_kJ_mol=None):
        self.model.set_reaction_constants(reaction_identifier, A_forward, Ea_forward_kJ_mol)

    def add_phase(self, duration_hours, feed_ppm, phase_name=None):
        p_idx = len(self.phases)
        name = phase_name if phase_name else f"Phase {p_idx}"

        feed = {s: 0.0 for s in self.model.SPECIES}
        if isinstance(feed_ppm, dict):
            for k, v in feed_ppm.items():
                if k in feed:
                    feed[k] = float(v)

        self.phases.append({
            'name': name,
            'duration_hours': float(duration_hours),
            'feed_ppm': feed
        })

    def clear_phases(self):
        self.phases = []
        self.simulation_results = None

    def generate_reactor_report(self):
        report = self.model.generate_reactor_report()
        charge = (
            f"\nInitial vessel charge metadata: {self.initial_gas}, "
            f"{self.initial_P_bar:.3f} bar, {self.initial_T_C:.3f} °C "
            "(not included in the impurity-species ODE balance)"
        )
        return report + charge

    def run_experiment(self):
        if not self.phases:
            self.add_phase(10.0, {s: 0.0 for s in self.model.SPECIES}, "Phase 0: Pressurization & Pure CO2 Flow")
            self.add_phase(20.0, {'SO2': 10.0, 'NO2': 10.0, 'O2': 10.0, 'H2O': 10.0}, "Phase 1: 10 ppm Without H2S")
            self.add_phase(20.0, {'H2S': 10.0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 10.0, 'H2O': 10.0}, "Phase 2: 10 ppm All Impurities")

        geom = self.model.get_reactor_geometry()
        tau_sec = geom['residence_time_seconds']

        rho_kg_m3 = self.model.molar_density * MW_CO2
        rho_g_ml = rho_kg_m3 * 1e-3
        m_target_g = self.volume_ml * rho_g_ml
        t_fill_hours = m_target_g / self.mass_flow_g_h if self.mass_flow_g_h > 0 else 0.0

        all_t_h = []
        all_ppm = {s: [] for s in self.model.SPECIES}
        current_cumulative_t = 0.0

        current_state_ppm = {s: 0.0 for s in self.model.SPECIES}

        for idx, phase in enumerate(self.phases):
            dur_h = phase['duration_hours']
            feed = phase['feed_ppm']

            if idx == 0 and dur_h >= t_fill_hours:
                res_fill = self.model.simulate(
                    initial_ppm=current_state_ppm,
                    duration_sec=t_fill_hours * 3600.0,
                    num_points=max(int(t_fill_hours * 10), 50),
                    feed_ppm=feed,
                    space_time_sec=None
                )

                fill_state = {s: res_fill['ppm'][s][-1] for s in self.model.SPECIES}
                rem_dur_h = dur_h - t_fill_hours

                if rem_dur_h > 0.001:
                    res_flow = self.model.simulate(
                        initial_ppm=fill_state,
                        duration_sec=rem_dur_h * 3600.0,
                        num_points=max(int(rem_dur_h * 10), 30),
                        feed_ppm=feed,
                        space_time_sec=tau_sec
                    )
                    t_res = np.concatenate([res_fill['time_hours'], t_fill_hours + res_flow['time_hours']])
                    ppm_res = {s: np.concatenate([res_fill['ppm'][s], res_flow['ppm'][s]]) for s in self.model.SPECIES}
                else:
                    t_res = res_fill['time_hours']
                    ppm_res = res_fill['ppm']
            else:
                res_flow = self.model.simulate(
                    initial_ppm=current_state_ppm,
                    duration_sec=dur_h * 3600.0,
                    num_points=max(int(dur_h * 10), 100),
                    feed_ppm=feed,
                    space_time_sec=tau_sec
                )
                t_res = res_flow['time_hours']
                ppm_res = res_flow['ppm']

            all_t_h.append(current_cumulative_t + t_res)
            for s in self.model.SPECIES:
                all_ppm[s].append(ppm_res[s])

            current_cumulative_t += dur_h
            current_state_ppm = {s: ppm_res[s][-1] for s in self.model.SPECIES}

        master_t = np.concatenate(all_t_h)
        master_ppm = {s: np.concatenate(all_ppm[s]) for s in self.model.SPECIES}

        self.simulation_results = {
            'time_hours': master_t,
            'ppm': master_ppm,
            'phases': self.phases
        }

        return self.simulation_results

    def get_table_results(self, resolution_hours=1.0):
        if self.simulation_results is None:
            self.run_experiment()

        return self.model.get_table_results(self.simulation_results, resolution_hours=resolution_hours)

    def plot_results(self, save_path=None, title="Multi-Phase CSTR CO2 Impurity Kinetics"):
        if self.simulation_results is None:
            self.run_experiment()

        t_h = self.simulation_results['time_hours']
        ppm = self.simulation_results['ppm']

        fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(11, 10), sharex=True)

        ax1.plot(t_h, ppm['H2S'], label='H2S', linewidth=2.0, color='#e74c3c')
        ax1.plot(t_h, ppm['SO2'], label='SO2', linewidth=2.0, color='#f39c12')
        ax1.plot(t_h, ppm['NO2'], label='NO2', linewidth=2.0, color='#9b59b6')
        ax1.plot(t_h, ppm['O2'],  label='O2',  linewidth=2.0, color='#2ecc71')
        ax1.plot(t_h, ppm['H2O'], label='H2O', linewidth=2.0, color='#3498db')
        ax1.set_ylabel('Reactants (ppm)', fontsize=11, fontweight='bold')
        ax1.set_title(title, fontsize=13, fontweight='bold')
        ax1.grid(True, linestyle='--', alpha=0.6)
        ax1.legend(loc='upper right', frameon=True)

        ax2.plot(t_h, ppm['H2SO4'], label='H2SO4 Sulfuric Acid (Formed)', linewidth=4.0, color='#FF0033', marker='o', markevery=40)
        ax2.fill_between(t_h, ppm['H2SO4'], color='#FF0033', alpha=0.3, label='H2SO4 Shaded Acid Accumulation')
        ax2.set_ylabel('H2SO4 Acid (ppm)', fontsize=11, fontweight='bold')
        max_h2so4 = np.max(ppm['H2SO4'])
        ax2.set_ylim(0.0, max(max_h2so4 * 1.35, 2.0))
        ax2.grid(True, linestyle='--', alpha=0.6)
        ax2.legend(loc='upper left', frameon=True)

        if max_h2so4 > 0.05:
            max_idx = np.argmax(ppm['H2SO4'])
            max_t = t_h[max_idx]
            time_span = max(float(t_h[-1] - t_h[0]), 1.0)
            annotation_t = max(float(t_h[0]), max_t - 0.2 * time_span)
            ax2.annotate(
                f'H2SO4 Acid Peak: {max_h2so4:.2f} ppm',
                xy=(max_t, max_h2so4),
                xytext=(annotation_t, max_h2so4 + 0.8),
                arrowprops={'facecolor': '#FF0033', 'shrink': 0.08, 'width': 3.0, 'headwidth': 10.0},
                fontsize=12,
                fontweight='bold',
                color='#B20000',
                bbox={'boxstyle': 'round,pad=0.3', 'fc': '#FFE6E6', 'ec': '#FF0033', 'lw': 1.5}
            )

        ax3.plot(t_h, ppm['NO'],    label='NO Gas',      linewidth=2.5, color='#8e44ad')
        ax3.plot(t_h, ppm['NH3'],   label='NH3 Ammonia', linewidth=2.5, color='#16a085')
        ax3.plot(t_h, ppm['S8'],    label='S8 Elemental Sulfur', linewidth=2.0, color='#f1c40f')
        ax3.set_xlabel('Time (hours)', fontsize=11, fontweight='bold')
        ax3.set_ylabel('Gaseous Products (ppm)', fontsize=11, fontweight='bold')
        ax3.grid(True, linestyle='--', alpha=0.6)
        ax3.legend(loc='upper right', frameon=True)

        cum_t = 0.0
        for phase in self.phases[:-1]:
            cum_t += phase['duration_hours']
            ax1.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)
            ax2.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)
            ax3.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)

        plt.tight_layout()

        if save_path:
            plt.savefig(save_path, dpi=300, bbox_inches='tight')
            print(f"Plot saved successfully to: {save_path}")

        return fig, (ax1, ax2, ax3)
