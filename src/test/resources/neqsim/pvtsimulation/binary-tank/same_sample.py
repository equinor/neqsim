"""Same-composition synthetic sample handoff using the independent binary oracle."""
import json
import reference as ref


def initial_moles():
    """Scale one mole of the frozen0.8/0.2 sample to100 reservoir m3."""
    molar_volume, beta, _, _ = ref.equilibrium(323.15,80e5,[0.8,0.2])
    return [100*0.8/molar_volume,100*0.2/molar_volume]


def generate(substeps=4):
    """Return output-complete independent same-sample tank and laboratory data."""
    result = ref.replay(initial_moles(),substeps)
    _,beta,_,_ = ref.equilibrium(323.15,80e5,result['initial_moles'])
    _,_,vl,vg = ref.tie(323.15,80e5)
    gas_fraction = beta*vg/((1-beta)*vl+beta*vg)
    result['initial_phase_volume_fractions'] = [gas_fraction,1-gas_fraction,0.0]
    lab = ref.laboratory()
    lab['two_phase_holdout_temperature_k'] = 323.15
    lab['two_phase_holdout'] = []
    for pressure in [80e5,70e5,60e5,50e5]:
        volume,beta,x,y = ref.equilibrium(323.15,pressure,[0.8,0.2])
        lab['two_phase_holdout'].append(dict(pressure_bara=pressure/1e5,
            molar_volume_m3=volume,gas_mole_fraction=beta,
            liquid_methane_fraction=x,vapor_methane_fraction=y))
    result['laboratory'] = lab
    return result


if __name__ == '__main__':
    print(json.dumps(generate(),indent=2,allow_nan=False))
