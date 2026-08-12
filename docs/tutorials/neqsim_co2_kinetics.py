"""
NeqSim CO2 Impurity Chemical Reaction Kinetics Framework.
Models trace impurity kinetics (H2S, SO2, NO2, NO, O2, H2O, H2SO4, HNO3, S8, NH3)
for CO2 transport pipelines and ship transport over carbon steel / magnetite / stainless steel.

Thiyl/Hydroperoxyl Radical Chain Co-Catalysis Engine:
- Calibrated R3a: SO2 + NO2 + H2O <-> NO + H2SO4 (Ea3a = 26.0 kJ/mol, k3a = 1.40e6 * exp(-26000/RT)).
  Reduced by 2.5x per experimental observations, making base SO2 oxidation controlled and slow without H2S.
- R3b: Radical chain accelerated oxidation when BOTH H2S & NO2 are present (Ea3b = 15.0 kJ/mol).
- R8: Heterogeneous magnetite/carbon-steel surface catalysis for S8 formation (Ea8 = 42.0 kJ/mol).
"""

import numpy as np
from scipy.integrate import solve_ivp
try:
    from neqsim.thermo import fluid
    HAS_NEQSIM = True
except Exception:
    HAS_NEQSIM = False


# Universal Gas Constant (J / mol K)
R_GAS = 8.314462618


class CO2ImpurityKineticsModel:
    """
    Rigorous Pure Physical Kinetic & Thermodynamic Simulator for Impurity Reactions in CO2 Streams.
    Supports material-dependent heterogeneous surface kinetics (carbon_steel, magnetite, stainless_steel, inert).
    """

    SPECIES = [
        'H2S', 'SO2', 'NO2', 'NO', 'O2', 'H2O',
        'H2SO4', 'HNO3', 'S8', 'NH3'
    ]

    SUPPORTED_MATERIALS = ['carbon_steel', 'magnetite', 'stainless_steel', 'inert']

    def __init__(self, T_kelvin=298.15, P_bar=100.0, water_ppm=50.0, material='carbon_steel'):
        self.T = T_kelvin
        self.P = P_bar
        self.water_ppm = water_ppm
        self.material = material.lower().replace(' ', '_')
        if self.material not in self.SUPPORTED_MATERIALS:
            self.material = 'carbon_steel'
        self.molar_density = self._calculate_molar_density(T_kelvin, P_bar)

    def _calculate_molar_density(self, T_K, P_bar):
        """
        Calculates fluid molar density (kmol/m3) using NeqSim SRK EOS or EOS density correlation.
        """
        if HAS_NEQSIM:
            try:
                test_fluid = fluid('srk')
                test_fluid.addComponent('CO2', 0.999)
                test_fluid.addComponent('water', 0.001)
                test_fluid.setTemperature(T_K)
                test_fluid.setPressure(P_bar)
                test_fluid.init(0)
                test_fluid.init(3)
                density_kg_m3 = test_fluid.getDensity() # kg/m3
                return density_kg_m3 / 44.0095 # kmol/m3
            except Exception:
                pass

        if P_bar > 60.0 and T_K < 305.15:
            if T_K <= 250.0:
                rho_kg_m3 = 1060.0 - 1.2 * (T_K - 240.0) + 1.5 * (P_bar - 20.0)
            else:
                rho_kg_m3 = 820.0 + 2.5 * (P_bar - 73.8) - 4.0 * (T_K - 304.13)
        else:
            Z = 0.85
            rho_kg_m3 = (P_bar * 1e5 * 44.01e-3) / (Z * R_GAS * T_K)

        return max(rho_kg_m3 / 44.0095, 0.1)

    def _calculate_pure_physical_rate_constants(self, moisture_ppm):
        """
        Pure Arrhenius rate constants k(T) = A * exp(-Ea / RT) and Gibbs Equilibrium Constants Keq(T).
        Includes material-dependent surface catalytic acceleration for carbon steel / magnetite.
        """
        T = self.T

        # Standard Gibbs Free Energy of Reactions Delta G°rxn (J / mol)
        # R1: SO2 + 0.5 O2 + H2O <-> H2SO4
        dG1 = (-690.1 - (-300.1 + 0.0 + -237.1)) * 1000.0 # -152.9 kJ/mol
        Keq1 = max(np.exp(min(-dG1 / (R_GAS * T), 300.0)), 1e-15)

        # R2: H2S + 3 NO2 <-> SO2 + H2O + 3 NO
        dG2 = ((-300.1 + -237.1 + 3.0*86.6) - (-33.4 + 3.0*51.3)) * 1000.0 # -396.7 kJ/mol
        Keq2 = max(np.exp(min(-dG2 / (R_GAS * T), 300.0)), 1e-15)

        # R3: SO2 + NO2 + H2O <-> NO + H2SO4
        dG3 = ((86.6 + -690.1) - (-300.1 + 51.3 + -237.1)) * 1000.0 # -117.6 kJ/mol
        Keq3 = max(np.exp(min(-dG3 / (R_GAS * T), 300.0)), 1e-15)

        # R4: 2 NO + O2 <-> 2 NO2
        dG4 = (2.0*51.3 - 2.0*86.6) * 1000.0 # -70.6 kJ/mol
        Keq4 = max(np.exp(min(-dG4 / (R_GAS * T), 300.0)), 1e-15)

        # R5: 3 NO2 + H2O <-> 2 HNO3 + NO (Thermodynamic Equilibrium Ceiling: Delta G° = +14.5 kJ/mol)
        dG5 = ((2.0 * -74.7 + 86.6) - (3.0 * 51.3 + -237.1)) * 1000.0 # +14.5 kJ/mol
        Keq5 = np.exp(-dG5 / (R_GAS * T))

        # R6: H2S + 1.5 O2 <-> SO2 + H2O
        dG6 = ((-300.1 + -237.1) - (-33.4 + 0.0)) * 1000.0 # -503.8 kJ/mol
        Keq6 = max(np.exp(min(-dG6 / (R_GAS * T), 300.0)), 1e-15)

        # 1. Forward Rate Constants k_forward(T) = A * exp(-Ea / RT)
        k1_f = 1.0e4 * np.exp(-45000.0 / (R_GAS * T)) # Direct SO2 oxidation
        k2_f = 5.0e7 * np.exp(-28000.0 / (R_GAS * T)) # H2S + NO2 oxidation
        
        # R3a: Base NO2-catalyzed SO2 oxidation without H2S (Calibrated 2.5x slower: A3a = 1.40e6, Ea3a = 26.0 kJ/mol)
        k3a_f = 1.4e6 * np.exp(-26000.0 / (R_GAS * T))
        
        # R3b: Radical chain accelerated SO2 oxidation when BOTH H2S & NO2 are present (Ea3b = 15.0 kJ/mol)
        k3b_f = 2.13e8 * np.exp(-15000.0 / (R_GAS * T))

        k4_f = 1.0e5 * np.exp(530.0 / T)               # Termolecular NO oxidation
        k5_f = 2.4e6 * np.exp(-28000.0 / (R_GAS * T)) # Reversible NO2 hydrolysis
        k6_f = 2.0e3 * np.exp(-65000.0 / (R_GAS * T)) # Uncatalyzed direct H2S oxidation (Ea6 = 65.0 kJ/mol)
        k7_f = 5.0e5 * np.exp(-15000.0 / (R_GAS * T)) # Fast Ammonia Reactions

        # 2. Material-Dependent Heterogeneous Catalysis for Elemental Sulfur Formation (R8: H2S + 0.5 O2 -> 1/8 S8 + H2O)
        if self.material in ['carbon_steel', 'magnetite']:
            # Carbon Steel / Magnetite surface catalytic kinetics: Ea8 = 42.0 kJ/mol
            k8_f = 1.5e4 * np.exp(-42000.0 / (R_GAS * T))
            Ea8 = 42.0
        else:
            # Stainless Steel / Inert: Uncatalyzed gas-phase reaction (Ea8 = 65.0 kJ/mol)
            k8_f = 2.0e3 * np.exp(-65000.0 / (R_GAS * T))
            Ea8 = 65.0

        # Reverse Rate Constants k_reverse(T) = k_forward(T) / Keq(T)
        k1_r = k1_f / Keq1 if Keq1 > 1e-15 else 0.0
        k2_r = k2_f / Keq2 if Keq2 > 1e-15 else 0.0
        k3a_r = k3a_f / Keq3 if Keq3 > 1e-15 else 0.0
        k4_r = k4_f / Keq4 if Keq4 > 1e-15 else 0.0
        k5_r = k5_f / Keq5
        k6_r = k6_f / Keq6 if Keq6 > 1e-15 else 0.0
        k7_r = 0.0
        k8_r = 0.0

        # Continuous Moisture Scaling Factor based on Trace Water Activity
        moisture_factor = 0.25 + 0.75 * (1.0 - np.exp(-moisture_ppm / 50.0))
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

    def rhs(self, t, C, rates_dict):
        """
        Single Coupled ODE System: dC_i / dt = sum(r_j,i)
        Includes H2S thiyl/hydroperoxyl radical chain co-catalytic acceleration (R3b)
        and Material-dependent Heterogeneous Catalytic Elemental Sulfur Formation (R8).
        """
        C_H2S, C_SO2, C_NO2, C_NO, C_O2, C_H2O, C_H2SO4, C_HNO3, C_S8, C_NH3 = np.maximum(C, 1e-25)

        k1_f, k1_r   = rates_dict['k1_f'], rates_dict['k1_r']
        k2_f, k2_r   = rates_dict['k2_f'], rates_dict['k2_r']
        k3a_f, k3a_r = rates_dict['k3a_f'], rates_dict['k3a_r']
        k3b_f        = rates_dict['k3b_f']
        k4_f, k4_r   = rates_dict['k4_f'], rates_dict['k4_r']
        k5_f, k5_r   = rates_dict['k5_f'], rates_dict['k5_r']
        k6_f, k6_r   = rates_dict['k6_f'], rates_dict['k6_r']
        k7_f         = rates_dict['k7_f']
        k8_f         = rates_dict['k8_f']

        # Reversible Net Reaction Rates r_net = r_forward - r_reverse (kmol / m3 s)
        # R1: SO2 + 0.5 O2 + H2O <-> H2SO4
        r1 = k1_f * C_SO2 * (C_O2**0.5) * C_H2O - k1_r * C_H2SO4

        # R2: H2S + 3 NO2 <-> SO2 + H2O + 3 NO
        r2 = k2_f * C_H2S * C_NO2 - k2_r * C_SO2 * C_H2O * (C_NO**3)

        # R3a: Base SO2 + NO2 + H2O <-> NO + H2SO4
        r3a = k3a_f * C_SO2 * C_NO2 * C_H2O - k3a_r * C_NO * C_H2SO4

        # R3b: Radical Chain Accelerated SO2 Oxidation (Fast Radical Chain Rate Law)
        r3b = k3b_f * C_SO2 * (C_H2S**0.5) * C_NO2 * (C_O2**0.5)

        # R4: 2 NO + O2 <-> 2 NO2
        r4 = k4_f * (C_NO**2) * C_O2 - k4_r * (C_NO2**2)

        # R5: 3 NO2 + H2O <-> 2 HNO3 + NO (Keq Driven Reversible Hydrolysis)
        r5 = k5_f * (C_NO2**3) * C_H2O - k5_r * (C_HNO3**2) * C_NO

        # R6: H2S + 1.5 O2 <-> SO2 + H2O (Uncatalyzed Direct Oxidation to SO2)
        r6 = k6_f * C_H2S * (C_O2**0.5) - k6_r * C_SO2 * C_H2O

        # R7: Fast Ammonia Reactions (5 H2S + 6 NO + 4 H2O -> 6 NH3 + 5 SO2)
        r7 = k7_f * C_H2S * C_NO * C_H2O

        # R8: Heterogeneous Catalytic Elemental Sulfur Formation (H2S + 0.5 O2 -> 1/8 S8 + H2O)
        r8 = k8_f * C_H2S * (C_O2**0.5)

        # Species ODE rates of change dC_i / dt
        dC_H2S   = - r2 - r6 - 5.0 * r7 - r8
        dC_SO2   = - r1 + r2 + r6 - r3a - r3b + 5.0 * r7
        dC_NO2   = - 3.0 * r2 - r3a + r4 - 3.0 * r5
        dC_NO    = + 3.0 * r2 + r3a - r4 + r5 - 6.0 * r7
        dC_O2    = - 0.5 * r1 - 1.5 * r6 - 0.5 * r4 - 0.5 * r3b - 0.5 * r8
        dC_H2O   = - r1 + r2 + r6 - r3a - r3b - r5 - 4.0 * r7 + r8
        dC_H2SO4 = + r1 + r3a + r3b
        dC_HNO3  = + 2.0 * r5
        dC_S8    = + 0.125 * r8
        dC_NH3   = + 6.0 * r7

        return [
            dC_H2S, dC_SO2, dC_NO2, dC_NO, dC_O2, dC_H2O,
            dC_H2SO4, dC_HNO3, dC_S8, dC_NH3
        ]

    def simulate(self, initial_ppm, duration_sec=100000.0, num_points=100):
        """
        Executes single coupled ODE integration using Radau/RK45 adaptive time-step solver.
        Dynamic time step dt is managed automatically by local error tolerance control (rtol=1e-6, atol=1e-12).
        """
        t_span = (0.0, duration_sec)
        t_eval = np.linspace(0.0, duration_sec, num_points)

        C0 = np.zeros(len(self.SPECIES))
        for idx, spec in enumerate(self.SPECIES):
            if spec in initial_ppm:
                C0[idx] = (initial_ppm[spec] * 1.0e-6) * self.molar_density

        moisture_ppm = initial_ppm.get('H2O', self.water_ppm)
        rates_dict = self._calculate_pure_physical_rate_constants(moisture_ppm)

        sol = solve_ivp(
            fun=lambda t, y: self.rhs(t, y, rates_dict),
            t_span=t_span,
            y0=C0,
            t_eval=t_eval,
            method='Radau',
            rtol=1e-6,
            atol=1e-12
        )

        ppm_results = {}
        for idx, spec in enumerate(self.SPECIES):
            ppm_results[spec] = (sol.y[idx, :] / self.molar_density) * 1.0e6

        return {
            'time_seconds': sol.t,
            'time_hours': sol.t / 3600.0,
            'ppm': ppm_results,
            'molar_density': self.molar_density,
            'rates': rates_dict
        }
