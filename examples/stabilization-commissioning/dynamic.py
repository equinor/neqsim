"""Integrated reduced-order inventories with NeqSim anti-surge controllers and SIS voting."""
import csv
import json
from pathlib import Path
import numpy as np
from steady import ROOT, P, J, E, jp

BASE = json.loads((ROOT/"steady.json").read_text())
S = BASE["streams"]
UUID = J("java.util.UUID")
VOTE = P.logic.sis.VotingLogic.TWO_OUT_OF_THREE
def vec(prefix, start):
    return np.array([S[prefix+str(i)]["kg_h"]/3600 for i in range(start, start+3)])

def bounded_pi(error, state, gain, ti, dt):
    new = state + gain * error * dt / ti
    trial = .5 + gain * error + new
    new = np.where(((trial > 1) & (error > 0)) | ((trial < 0) & (error < 0)), state, new)
    return np.clip(.5 + gain*error + new, 0, 1), new

def machines():
    controls = []
    for i in range(3):
        n = str(201+i)
        datum = S["suction"+n]
        # A gas flow transmitter carrier. Actual volumetric input is the model's
        # inlet flow, independent of the carrier gas composition.
        fluid = J("neqsim.thermo.system.SystemSrkEos")(308.15, datum["p_bara"])
        fluid.addComponent("methane", 1.)
        fluid.setMixingRule("classic")
        st = E.stream.Stream("FT-"+n, fluid)
        st.run()
        k = E.compressor.Compressor("K-"+n, st)
        q = datum["actual_m3_h"]
        k.getCompressorChart().getSurgeCurve().setCurve(None, [q*.76], [100.])
        valve = E.valve.ThrottlingValve("ASV-"+n)
        asc = P.controllerdevice.AntiSurgeController("ASC-"+n,k,valve)
        asc.setSurgeMarginSetPoint(.12)
        asc.setProportionalGain(360.)
        asc.setIntegralTime(3.)
        asc.setActive(True)
        controls.append((st,k,valve,asc))
    return controls

def simulate(case, dt=.25, duration=420, record=True):
    controls = machines()
    p0 = np.array([26.,8.,1.6])
    s0 = np.array([1.45,7.,24.])
    d0 = np.array([7.,24.,60.])
    gas, oil, water = [vec(prefix,101) for prefix in ("gas","oil","water")]
    fresh = gas + oil + water
    feed0 = S["feed"]["kg_h"]/3600
    suction, su_liq, cond = [vec(prefix,201) for prefix in ("suction","suction_liq","cond")]
    su_water,dis_water = [vec(prefix,201) for prefix in ("suction_water","cond_water")]
    rho_g = np.array([S["gas"+str(i)]["density"] for i in range(101,104)])
    rho_o = np.array([S["oil"+str(i)]["density"] for i in range(101,104)])
    rho_s = np.array([S["suction"+str(i)]["density"] for i in range(201,204)])
    rho_l = np.array([S["suction_liq"+str(i)]["density"] for i in range(201,204)])
    vessel_v = np.array([45.,35.,30.])
    m_o = .45*vessel_v*rho_o
    m_w = np.array([.12*45*961.1,0.,0.])
    m_g = (vessel_v - m_o/rho_o - m_w/961.1)*rho_g
    scrub_v = np.array([6.,6.,9.])
    m_l = .20*scrub_v*rho_l
    m_s = .8*scrub_v*rho_s
    p_int, o_int, w_int, l_int = np.zeros((4,3))
    p_valve, o_valve, w_valve, drain = np.ones((4,3))*.5
    openness = np.zeros(3)
    speed = 1.
    inlet_sdv = 1.
    export_sdv = 1.
    transfer_sdv = 1.
    pump_run = 1.
    overall_residual = 0.
    initial_mass = sum(np.sum(m) for m in [m_g,m_o,m_w,m_s,m_l])
    cumulative_net = 0.
    rows, log = [], []
    firstout = None
    demand_since = np.full(9,np.nan)
    for step in range(int(round(duration/dt))+1):
        t = step*dt
        op = m_o/rho_o/vessel_v
        wp = m_w/961.1/vessel_v
        p = m_g/rho_g/(vessel_v*(1-op-wp))*p0
        sl = m_l/rho_l/scrub_v
        sp = m_s/rho_s/(scrub_v*(1-sl))*s0
        dp = np.r_[sp[1:],60.]
        demand = 1.
        if case in ("turndown","no_asc"):
            demand = 1 - .45*np.clip((t-30.)/60.,0.,1.)
        if case == "feed_step" and t >= 30.:
            demand = 1.15
        # Independent HH transmitters share the same physical pressure; small
        # deterministic offsets and one-channel fault are explicit test inputs.
        offsets = np.array([[-.03,0.,.03],[-.01,0.,.01],[-.005,0.,.005]])
        signals = p[:,None] + offsets
        if case == "single_channel" and t >= 30.:
            signals[0,0] = 40.
        votes = (signals >= np.array([29.,9.2,2.05])[:,None]).sum(axis=1)
        stage_votes = [VOTE.evaluate(int(v)) for v in votes]
        # Loop-test injections exercise machinery-package shutdown wiring.
        # These channels do not represent thermal, bearing or lube hydraulics.
        vibration = 2.0 + (7.0 if case == "vibration_test" and t >= 30. else 0.)
        lube = 3.0 - (2.0 if case == "lube_test" and t >= 30. else 0.)
        discharge_t = S["discharge203"]["t_C"] + (55. if case == "temperature_test" and t >= 30. else 0.)
        channels = np.r_[stage_votes,sl >= .70,vibration>=8.,lube<=1.5,discharge_t>=145.]
        demand_since = np.where(channels,np.where(np.isnan(demand_since),t,demand_since),np.nan)
        on_timer = np.where(channels,t-demand_since,0.)
        if firstout is None and max(on_timer) >= 1.:
            idx = np.flatnonzero(on_timer >= 1.)[0]
            tags = ["PSHH-101","PSHH-102","PSHH-103","LSHH-201","LSHH-202","LSHH-203",
                    "VSHH-203","PSLL-203L","TSHH-203"]
            firstout = tags[idx]
            log.append([t,firstout])
        trip = firstout is not None
        # Quasi-steady fixed-speed characteristic through design flow, with
        # surge at 0.76 design volume. Fixed gas properties and effective head.
        h_ratio = np.log(np.maximum(dp/sp,1.000001))/np.log(d0/s0)
        q_ratio = np.sqrt(np.maximum(0., (1.45*speed**2-h_ratio)/.45))
        gross = suction * q_ratio * sp/s0
        margins, targets = [], []
        for i,(st,k,v,asc) in enumerate(controls):
            q = S["suction"+str(201+i)]["actual_m3_h"]*q_ratio[i]
            st.setFlowRate(float(max(q,1.e-8)), "m3/hr")
            margin = k.getDistanceToSurge()
            margins.append(margin)
            asc.runTransient(float(openness[i]*100),dt,UUID(0,step+1))
            targets.append(asc.getResponse()/100)
        targets = np.array(targets)
        if case == "no_asc":
            targets[:] = 0.
        if trip:
            targets[:] = 1.
        # Choked recycle capacity proxy, calibrated to 100% of nominal gas
        # flow at design discharge pressure; limit withdrawal to available gas.
        recycled = np.minimum(gross, suction*openness*dp/d0)
        net = gross-recycled
        if trip:
            net[-1] *= export_sdv
        condensed = net * cond/suction
        discharge_water = net * dis_water/suction
        passed = net-condensed-discharge_water
        gasout = gas*2*p_valve*np.sqrt(np.maximum(0.,(p-sp[::-1])/(p0-s0[::-1])))
        if case == "blocked_gas" and t >= 30.:
            gasout[0] = 0.
        if case == "blocked_mp" and t>=30.:
            gasout[1] = 0.
        if case == "blocked_lp" and t>=30.:
            gasout[2] = 0.
        oilout = oil*2*o_valve*np.array([transfer_sdv,transfer_sdv,pump_run])
        waterout = water*2*w_valve
        feed = feed0*demand*inlet_sdv
        sep_feed = np.r_[feed, oilout[:-1]]
        header_in = gasout[::-1] + np.r_[0, passed[:-1]]
        knockout = header_in * su_liq/(suction+su_liq+su_water)
        suction_water = header_in * su_water/(suction+su_liq+su_water)
        liquid_injection = 9.0 if case == "scrubber_slug" and 30. <= t < 300. and not trip else 0.
        removed = su_liq*2*drain
        if case == "scrubber_slug" and t >= 30.:
            removed[0] = 0.
        # V-213 to HP; V-203 and V-212 to MP; the other three to LP.
        sep_feed += np.array([condensed[2],removed[2]+condensed[1],
                              removed[0]+removed[1]+condensed[0]])
        inventory = sum(np.sum(m) for m in [m_g,m_o,m_w,m_s,m_l])
        overall_residual = max(overall_residual,abs(inventory-initial_mass-cumulative_net))
        row = dict(t_s=t,feed_pct=100*feed/feed0,trip=int(trip),votes=int(votes[0]),votes_mp=int(votes[1]),votes_lp=int(votes[2]),
                   transfer_sdv_pct=100*transfer_sdv,pump_pct=100*pump_run,
                   inlet_sdv_pct=100*inlet_sdv,export_sdv_pct=100*export_sdv,
                   vib203_mm_s=vibration,lube203_barg=lube,Td203_C=discharge_t,
                   speed_pct=100*speed,oil_export_kg_s=oilout[-1],gas_export_kg_s=passed[-1])
        for i in range(3):
            n, ktag = 101+i,201+i
            row.update({f"P{n}_bara":p[i],f"oil{n}_volpct":op[i]*100,
                        f"water{n}_volpct":wp[i]*100,f"PV{n}_pct":p_valve[i]*100,
                        f"PS{ktag}_bara":sp[i],f"scrub{ktag}_volpct":sl[i]*100,
                        f"margin{ktag}_pct":margins[i]*100,f"ASV{ktag}_pct":openness[i]*100,
                        f"recycle{ktag}_kg_s":recycled[i],f"gross{ktag}_kg_s":gross[i]})
        rows.append(row)
        if step == int(round(duration/dt)):
            break
        p_cmd,p_int = bounded_pi((p-p0)/p0,p_int,4.,12.,dt)
        # Oil outlet is driven by total liquid level; water outlet by interface.
        o_cmd,o_int = bounded_pi(op+wp-np.array([.57,.45,.45]),o_int,3.,65.,dt)
        w_cmd,w_int = bounded_pi(wp-np.array([.12,0,0]),w_int,4.,55.,dt)
        l_cmd,l_int = bounded_pi(sl-.20,l_int,4.,50.,dt)
        for pos,cmd in [(p_valve,p_cmd),(o_valve,o_cmd),(w_valve,w_cmd),(drain,l_cmd)]:
            pos += np.clip(cmd-pos,-dt/8,dt/8)
        openness += np.clip(targets-openness,-dt/12,dt/2)
        if trip:
            inlet_sdv = max(0.,inlet_sdv-dt/4.)
            export_sdv = max(0.,export_sdv-dt/3.)
            transfer_sdv = max(0.,transfer_sdv-dt/3.)
            pump_run = max(0.,pump_run-dt/3.)
            speed *= np.exp(-dt/7.)
        m_g += dt*(sep_feed*gas/fresh-gasout)
        m_o += dt*(sep_feed*oil/fresh-oilout)
        m_w += dt*(sep_feed*water/fresh-waterout)
        m_s += dt*(header_in-knockout-suction_water-net)
        m_l += dt*(knockout-removed+np.array([liquid_injection,0.,0.]))
        cumulative_net += dt*(feed+liquid_injection-oilout[-1]-sum(waterout)-passed[-1]-sum(discharge_water)-sum(suction_water))
        assert min(m_o)>0 and min(m_s)>0 and max(op+wp)<1 and max(sl)<1
    if record:
        with (ROOT/(case+".csv")).open("w") as f:
            writer=csv.DictWriter(f,fieldnames=rows[0])
            writer.writeheader()
            writer.writerows(rows)
    return rows, log, overall_residual

if __name__ == "__main__":
    summary={}
    for case,duration in [("baseline",60),("feed_step",1200),("turndown",1200),("no_asc",1200),
                          ("blocked_gas",160),("blocked_mp",160),("blocked_lp",160),("single_channel",160),("scrubber_slug",350),
                          ("vibration_test",65),("lube_test",65),("temperature_test",65)]:
        rows,log,res=simulate(case,duration=duration)
        summary[case]=dict(events=log,mass_error_kg=res,final=rows[-1],
            min_margin=[min(r[f"margin{i}_pct"] for r in rows if r['speed_pct']>99.9) for i in range(201,204)],
            max_asv=[max(r[f"ASV{i}_pct"] for r in rows) for i in range(201,204)],
            peak_HP_bara=max(r['P101_bara'] for r in rows))
        print(case,log,summary[case]['min_margin'],summary[case]['max_asv'],res,flush=True)
    fine,log,res=simulate("turndown",dt=.125,duration=1200,record=False)
    summary["refinement"]=dict(dt_s=.125,min_margin=[min(r[f"margin{i}_pct"] for r in fine) for i in range(201,204)],
        max_asv=[max(r[f"ASV{i}_pct"] for r in fine) for i in range(201,204)])
    (ROOT/"summary.json").write_text(json.dumps(summary,indent=2))
