"""Tests for devtools/sodir_wellbore_stats.py."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import sodir_wellbore_stats as sws  # noqa: E402

DAY = 86400000


def _well(name, td, hc1=None, typ="EXPLORATION", purpose="WILDCAT", days=50, year=2010, field=None):
    return {"wlbWellboreName": name, "wlbFormationAtTd": td, "wlbFormationWithHc1": hc1, "wlbWellType": typ, "wlbPurpose": purpose,
            "wlbEntryDate": 0, "wlbCompletionDate": int(days * DAY), "wlbCompletionYear": year, "wlbField": field}


def test_hit_rate_counts_penetrations_and_hits():
    wells = [_well("a", "TILJE FM", "TILJE FM"), _well("b", "\u00c5RE FM", None), _well("c", "GARN FM", "GARN FM"),
             _well("d", "TILJE FM", None, typ="DEVELOPMENT", purpose="PRODUCTION")]
    res = sws.hit_rate(wells, ("TILJE", "ARE"), "TILJE FM")
    assert res["n"] == 2
    assert res["k"] == 1
    assert res["hits"] == ["a"]
    assert abs(res["mean"] - 0.5) < 1e-9


def test_norwegian_a_ring_is_normalised():
    assert sws.normalise("\u00c5re Fm") == "ARE FM"
    assert sws.penetrated({"wlbFormationAtTd": "\u00c5RE FM"}, ("ARE",))


def test_percentiles_use_p90_low_convention():
    p = sws.percentiles([10, 20, 30, 40, 50])
    assert p["P90"] < p["P50"] < p["P10"]
    assert p["P50"] == 30


def test_pilot_hole_days_filters_by_field():
    wells = [_well("p1", None, typ="DEVELOPMENT", purpose="OBSERVATION", days=30, field="TYRIHANS"),
             _well("p2", None, typ="DEVELOPMENT", purpose="OBSERVATION", days=70, field="OTHER")]
    assert sws.pilot_hole_days(wells, "TYRIHANS") == [30.0]
    assert sorted(sws.pilot_hole_days(wells)) == [30.0, 70.0]


def test_missing_dates_give_none():
    assert sws.days({"wlbEntryDate": None, "wlbCompletionDate": 5}) is None
    assert sws.percentiles([]) is None
