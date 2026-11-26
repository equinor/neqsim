"""Independent two-phase PR laboratory/tank qualification against workspace Java."""
import importlib.util
import json
import math
import os
import sys
from pathlib import Path
from time import perf_counter

import pytest

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / 'src/test/resources/neqsim/pvtsimulation/binary-tank'
pytestmark = pytest.mark.skipif(os.environ.get('NEQSIM_TEST_RESERVOIR_JAVA') != '1',
                               reason='Dedicated compiled-workspace JVM qualification')


def require_finite(values):
    """Reject invalid numerical outputs before aggregation can hide NaNs."""
    if not all(math.isfinite(float(value)) for value in values):
        raise ValueError('Nonfinite native comparison output')


@pytest.mark.parametrize('bad',[float('nan'),float('inf'),-float('inf')])
def test_nonfinite_admission_control(bad):
    with pytest.raises(ValueError):
        require_finite([1.0,bad])


def oracle(same_sample=False):
    spec = importlib.util.spec_from_file_location('binary_reference',FOLDER / 'reference.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if same_sample:
        previous = sys.modules.get('reference')
        sys.modules['reference'] = module
        try:
            spec = importlib.util.spec_from_file_location('binary_same_sample',FOLDER / 'same_sample.py')
            same = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(same)
            return same
        finally:
            if previous is None:
                del sys.modules['reference']
            else:
                sys.modules['reference'] = previous
    return module


@pytest.fixture(scope='module')
def java():
    import jpype
    from neqsim_dev_setup import neqsim_init
    assert not jpype.isJVMStarted()
    ns = neqsim_init(project_root=ROOT,verbose=False)
    try:
        cls = ns.JClass('neqsim.pvtsimulation.util.CompositionalReservoirHistory')
        location = Path(str(cls.class_.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()))
        assert location.resolve() == (ROOT / 'target/classes').resolve()
        yield ns
    finally:
        jpype.shutdownJVM()


def fluid(java,kij):
    ref = oracle()
    f = java.JClass('neqsim.thermo.system.SystemPrEos')(323.15,80)
    f.addComponent('methane',0.8)
    f.addComponent('n-heptane',0.2)
    f.setMixingRule('classic')
    f.useVolumeCorrection(False)
    for phase in range(f.getNumberOfPhases()):
        ph = f.getPhase(phase)
        for i in range(2):
            ph.getComponent(i).setTC(ref.TC[i])
            ph.getComponent(i).setPC(ref.PC[i]/1e5)
            ph.getComponent(i).setAcentricFactor(ref.OMEGA[i])
        ph.getEosMixingRule().setBinaryInteractionParameter(0,1,kij)
        ph.getEosMixingRule().setBinaryInteractionParameter(1,0,kij)
    f.init(0)
    return f


def history(java,model,values,shift=0):
    Step = java.JClass('neqsim.pvtsimulation.util.CompositionalReservoirHistory$Step')
    History = java.JClass('neqsim.pvtsimulation.util.CompositionalReservoirHistory')
    steps = [Step.phaseWithdrawal(86400,[8000,2000,0],[200,10],
                                 row['pressure_bara']+(shift if i==3 else 0),0.1,i==3,values['substeps'])
             for i,row in enumerate(values['rows'])]
    if 'initial_phase_volume_fractions' in values:
        return History.fromSampleComposition(model,'Independent same-sample binary protocol',
                   ['methane','n-heptane'],323.15,80,steps)
    return History(model,'Independent binary PR synthetic fixed-substep protocol',
                   ['methane','n-heptane'],323.15,80,[0.7,0.3,0],steps)


@pytest.mark.parametrize('same_sample',[False,True])
def test_laboratory_and_independent_two_phase_tank(java,same_sample):
    ref = oracle()
    values = oracle(True).generate() if same_sample else ref.generate()
    assert len(values['rows']) == 4
    lab = values['laboratory'] if same_sample else ref.laboratory()
    assert abs(lab['identified_kij']-0.02) < 1e-10
    tuned = fluid(java,lab['identified_kij'])
    original = fluid(java,0.0)
    two_phase_errors = []
    if same_sample:
        # Execute the actual whole closed-mass CCE, retaining every phase/component.
        cce = java.JClass('neqsim.pvtsimulation.simulation.ConstantMassExpansion')(tuned.clone())
        cce.setTemperature(lab['two_phase_holdout_temperature_k'])
        cce.setPressures([row['pressure_bara'] for row in lab['two_phase_holdout']])
        snapshots = cce.runNativeExpansion()
        assert len(snapshots) == len(lab['two_phase_holdout']) == 4
        for snapshot,row in zip(snapshots,lab['two_phase_holdout']):
            sample = snapshot.getEquilibriumFluid()
            require_finite([sample.getVolume('m3'),sample.getBeta(sample.getPhaseNumberOfPhase('gas'))])
            two_phase_errors.append(abs(sample.getVolume('m3')/row['molar_volume_m3']-1))
            assert abs(sample.getBeta(sample.getPhaseNumberOfPhase('gas'))-row['gas_mole_fraction']) <= 1e-6
            assert abs(sample.getPhase('oil').getComponent('methane').getx()-row['liquid_methane_fraction']) <= 1e-6
            assert abs(sample.getPhase('gas').getComponent('methane').getx()-row['vapor_methane_fraction']) <= 1e-6
            assert abs(sample.getComponent('methane').getNumberOfmoles()-0.8) <= 1e-9
            assert abs(sample.getComponent('n-heptane').getNumberOfmoles()-0.2) <= 1e-9
        assert max(two_phase_errors) <= 1e-7
    lab_errors = []
    original_errors = []
    for f,target in ((original,original_errors),(tuned,lab_errors)):
        for row in lab['holdout']:
            sample = f.clone()
            sample.setTemperature(lab['holdout_temperature_k'])
            sample.setPressure(row['pressure_pa']/1e5)
            java.JClass('neqsim.thermodynamicoperations.ThermodynamicOperations')(sample).TPflash()
            require_finite([sample.getVolume('m3')])
            target.append(abs(sample.getVolume('m3')/row['molar_volume_m3']-1))
    assert max(lab_errors) < 1e-7
    Model = java.JClass('neqsim.pvtsimulation.util.CalibratedPvtModel')
    model = Model(original,tuned,'synthetic binary PR sample','kij0','independent laboratory kij',
                  'independent600K200bar occupied volume; no reservoir data',['laboratory600K'])
    serializer = java.JClass('org.apache.commons.lang3.SerializationUtils')
    before = bytes(serializer.serialize(model))
    initialization_started = perf_counter()
    tank = history(java,model,values)
    initialization_seconds = perf_counter()-initialization_started
    if same_sample:
        for volume in [80,100,120]:
            initial = list(tank.replay(volume).getComponentMoles()[0])
            require_finite(initial)
            assert abs(initial[0]/sum(initial)-0.8) <= 1e-10
            assert max(abs(initial[i]/values['initial_moles'][i]-volume/100) for i in range(2)) <= 1e-9
        assert max(abs(a-b) for a,b in zip(tank.getInitialPhaseVolumeFractions(),
                                         values['initial_phase_volume_fractions'])) <= 1e-9
    started = perf_counter()
    result = tank.replay(100)
    replay_seconds = perf_counter()-started
    separator = java.JClass('neqsim.pvtsimulation.util.PvtSeparatorPath')(
        [1.01325],[288.15],'single equilibrium surface flash; gas/oil m3 at288.15K/1.01325bara')
    errors = {'pressure_bar':0.0,'composition':0.0,'gor_relative':0.0,'component_mol':0.0}
    predictions = []
    for i,row in enumerate(values['rows']):
        p = float(result.getPressuresBara()[i+1])
        produced = list(result.getProducedComponentMoles()[i])
        x = produced[0]/sum(produced)
        gor = float(separator.separate(result.getProducedFluid(i)).getGasOilRatio())
        assert len(produced) == 2
        require_finite([p,x,gor]+produced+list(result.getComponentMoles()[i+1]))
        errors['pressure_bar'] = max(errors['pressure_bar'],abs(p-row['pressure_bara']))
        errors['composition'] = max(errors['composition'],abs(x-row['produced_methane_fraction']))
        errors['gor_relative'] = max(errors['gor_relative'],abs(gor/row['gor']-1))
        for j,n in enumerate(result.getComponentMoles()[i+1]):
            errors['component_mol'] = max(errors['component_mol'],abs(n-row['retained_moles'][j]))
        predictions.append(dict(pressure_bara=p,produced_methane_fraction=x,gor=gor))
    assert errors['component_mol']/sum(values['initial_moles']) <= 1e-9
    assert errors['pressure_bar'] <= 1e-4
    assert errors['composition'] <= 1e-6
    assert errors['gor_relative'] <= 1e-4
    fit_started = perf_counter()
    fit = tank.fitVolume(80,120)
    fit_seconds = perf_counter()-fit_started
    assert abs(fit.getResult().getInitialVolumeM3()-100) <= 0.01
    assert not fit.isAtBound()
    heldout = fit.getResult()
    require_finite(list(heldout.getPressuresBara())+[heldout.getInitialVolumeM3(),
                    heldout.getCalibrationChiSquare(),heldout.getHoldoutChiSquare()])
    heldout_produced = list(heldout.getProducedComponentMoles()[3])
    heldout_gor = separator.separate(heldout.getProducedFluid(3)).getGasOilRatio()
    assert abs(heldout.getPressuresBara()[4]-values['rows'][3]['pressure_bara']) <= 1e-4
    assert abs(heldout_produced[0]/sum(heldout_produced)-values['rows'][3]['produced_methane_fraction']) <= 1e-6
    assert abs(heldout_gor/values['rows'][3]['gor']-1) <= 1e-4
    # Volume sensitivity is independent of the excluded observations.
    assert abs(tank.replay(80).getPressuresBara()[3]-tank.replay(120).getPressuresBara()[3]) > 0.01
    shifted = history(java,model,values,5).fitVolume(80,120).getResult()
    assert shifted.getInitialVolumeM3() == fit.getResult().getInitialVolumeM3()
    assert shifted.getHoldoutChiSquare() > fit.getResult().getHoldoutChiSquare()
    rejection_started = perf_counter()
    with pytest.raises(Exception) as failure:
        tank.replay(1)
    rejection_seconds = perf_counter()-rejection_started
    assert 'substep 0' in str(failure.value)
    assert 'Phase interval cannot retain' in str(failure.value)
    assert list(tank.replay(100).getPressuresBara()) == list(result.getPressuresBara())
    assert bytes(serializer.serialize(model)) == before
    print(json.dumps(dict(case='same_sample' if same_sample else 'phase_volume',errors=errors,predictions=predictions,
                          two_phase_lab_holdout_relative_errors=two_phase_errors,
                          initialization_seconds=initialization_seconds,
                          truth_replay_seconds=replay_seconds,
                          volume_fit_seconds=fit_seconds,
                          infeasible_trial_seconds=rejection_seconds,
                          infeasible_trial_message=str(failure.value),
                          fitted_volume_m3=fit.getResult().getInitialVolumeM3(),
                          fitted_pressures_bara=list(fit.getResult().getPressuresBara()),
                          fitted_holdout_gor=float(heldout_gor),
                          calibration_chi_square=fit.getResult().getCalibrationChiSquare(),
                          holdout_chi_square=fit.getResult().getHoldoutChiSquare(),
                          lab_holdout_relative_errors=lab_errors,
                          original_holdout_relative_errors=original_errors,
                          elapsed_seconds=perf_counter()-started)))


@pytest.mark.parametrize('same_sample',[False,True])
def test_independent_finite_protocol_refinement(java,same_sample):
    ref = oracle()
    Model = java.JClass('neqsim.pvtsimulation.util.CalibratedPvtModel')
    model = Model(fluid(java,0.0),fluid(java,ref.laboratory()['identified_kij']),
                  'synthetic binary PR sample','kij0','laboratory kij',
                  'independent600K200bar calibration',['laboratory600K'])
    diagnostics = []
    for count in [1,2,4,8,16]:
        values = oracle(True).generate(count) if same_sample else ref.generate(count)
        native = history(java,model,values).replay(100)
        separator = java.JClass('neqsim.pvtsimulation.util.PvtSeparatorPath')(
            [1.01325],[288.15],'fixed synthetic surface basis')
        last = values['rows'][-1]
        produced = list(native.getProducedComponentMoles()[3])
        gor = separator.separate(native.getProducedFluid(3)).getGasOilRatio()
        require_finite([native.getPressuresBara()[4],gor]+produced)
        assert abs(native.getPressuresBara()[4]-last['pressure_bara']) <= 1e-4
        assert abs(produced[0]/sum(produced)-last['produced_methane_fraction']) <= 1e-6
        assert abs(gor/last['gor']-1) <= 1e-4
        diagnostics.append(dict(substeps=count,reference_pressure_bara=last['pressure_bara'],
                                native_pressure_bara=float(native.getPressuresBara()[4]),
                                reference_gor=last['gor'],native_gor=float(gor),
                                reference_methane_fraction=last['produced_methane_fraction'],
                                native_methane_fraction=produced[0]/sum(produced)))
    print(json.dumps(dict(case='same_sample' if same_sample else 'phase_volume',finite_protocol_refinement=diagnostics)))
