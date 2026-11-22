"""Deterministic improvement of a feasible plant-layout candidate.

The placement backend in ``layout_engine`` returns the first arrangement that
satisfies the hard constraints of a scan.  This module takes such a candidate
and shortens its connections: equipment is moved, turned and exchanged on the
declared grid while every hard constraint used by the placement search keeps
holding.  The objective is the weighted centre-to-centre Manhattan distance of
the process connections - a proxy for piping, not a routed length or a cost.

The search is a steepest-descent local search with a fixed order and no random
numbers, so equal input gives an identical result.  It stops at a layout that no
single relocation, pair exchange or shift of a connected group improves.  That is a local optimum on the
grid; global optimality is not established and is never reported.
"""

from __future__ import annotations

import math

from layout_model import TOLERANCE_M, equipment_bounds

OBJECTIVE = "weighted_connection_manhattan_m"
METHOD = "deterministic steepest-descent relocation, pair exchange and connected-group shift on the placement grid"
DEFAULT_MAX_PASSES = 20
DEFAULT_MAX_EVALUATIONS = 200000
IMPROVEMENT_M = 1.0e-9


def optimization_settings(study):
    """Return the effective optimisation settings of a normalized study."""
    supplied = study.get("options", {}).get("optimize") or {}
    return {"enabled": bool(supplied.get("enabled", False)),
            "max_passes": int(supplied.get("max_passes", DEFAULT_MAX_PASSES)),
            "max_evaluations": int(supplied.get("max_evaluations", DEFAULT_MAX_EVALUATIONS))}


def connection_weight(connection):
    """Return the relative weight of one connection; an unweighted connection counts as one."""
    return float(connection.get("weight", 1.0))


def _centre(item, placement, deck):
    """Return the centre of the physical bounding box of a placed item."""
    bounds = equipment_bounds(item, placement, deck)
    return [(bounds[i] + bounds[i + 3]) / 2.0 for i in range(3)]


def _manhattan(first, second):
    """Return the Manhattan distance between two points in metres."""
    return sum(abs(first[i] - second[i]) for i in range(3))


def weighted_connection_length(study, placed, equipment, decks):
    """Return the objective for a set of placements.

    Connections with an unplaced end are left out, exactly as in the reported
    connection proxy, so a partial layout is never credited with a shorter total.
    """
    centres = {tag: _centre(equipment[tag], placement, decks[placement["deck"]])
               for tag, placement in placed.items()}
    return sum(connection_weight(c) * _manhattan(centres[c["from_tag"]], centres[c["to_tag"]])
               for c in study["connections"] if c["from_tag"] in centres and c["to_tag"] in centres)


def lower_bound(study, equipment, decks):
    """Return a simple valid lower bound of the objective.

    Two boxes that may not overlap cannot bring their centres closer than half
    the sum of their shortest allowed horizontal sides when they stand on one
    level, or than the smallest centre-height difference of two different decks
    they are both allowed on.  The bound ignores every other item, corridor and
    rule, so it is usually far below what is achievable; it bounds the gap, it
    does not locate the optimum.
    """
    total = 0.0
    for connection in study["connections"]:
        first, second = equipment[connection["from_tag"]], equipment[connection["to_tag"]]
        if first["tag"] == second["tag"]:
            continue

        best = (min(first["size_m"][:2]) + min(second["size_m"][:2])) / 2.0
        for deck_a in first["allowed_decks"]:
            for deck_b in second["allowed_decks"]:
                if deck_a == deck_b:
                    continue
                height_a = decks[deck_a]["elevation_m"] + first["size_m"][2] / 2.0
                height_b = decks[deck_b]["elevation_m"] + second["size_m"][2] / 2.0
                best = min(best, abs(height_a - height_b))
        total += connection_weight(connection) * best
    return total


def optimize_placements(study, placements, candidates, candidate_valid, settings=None):
    """Shorten the connections of a complete feasible layout.

    ``placements`` is the list of a complete candidate.  ``candidates(item)``
    yields the finite grid placements of one item and ``candidate_valid(item,
    placement, placed)`` applies the hard constraints of the placement search;
    both are supplied by ``layout_engine`` so this module adds no constraint
    logic of its own.  Returns the new placement list and a record of the search.
    """
    settings = dict(optimization_settings(study), **(settings or {}))
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    placed = {p["tag"]: dict(p) for p in placements}
    initial = weighted_connection_length(study, placed, equipment, decks)
    bound = lower_bound(study, equipment, decks)

    links = {tag: [] for tag in placed}
    for connection in study["connections"]:
        first, second = connection["from_tag"], connection["to_tag"]
        if first in links and second in links and first != second:
            links[first].append((second, connection_weight(connection)))
            links[second].append((first, connection_weight(connection)))
    movable = sorted((tag for tag in placed if equipment[tag].get("fixed") is None and links[tag]),
                     key=lambda tag: (-sum(weight for _other, weight in links[tag]), tag))
    centres = {tag: _centre(equipment[tag], placed[tag], decks[placed[tag]["deck"]]) for tag in placed}

    def cost(tag, centre, ignore=None):
        """Weighted distance from one item to everything it is connected to."""
        return sum(weight * _manhattan(centre, centres[other])
                   for other, weight in links[tag] if other != ignore)

    # Blocks of free items that are connected to each other: each item with its free
    # neighbours, and each whole connected component of free items.
    free = set(movable)
    groups = []
    for tag in movable:
        star = frozenset([tag] + [other for other, _weight in links[tag] if other in free])
        component, frontier = {tag}, [tag]
        while frontier:
            for other, _weight in links[frontier.pop()]:
                if other in free and other not in component:
                    component.add(other)
                    frontier.append(other)
        for group in (star, frozenset(component)):
            if len(group) > 1 and group not in groups:
                groups.append(group)

    grid_cache = {}

    def grid(tag):
        """Every grid placement of an item with its centre, computed once."""
        if tag not in grid_cache:
            item = equipment[tag]
            grid_cache[tag] = [(candidate, _centre(item, candidate, decks[candidate["deck"]]))
                               for candidate in candidates(item)]
        return grid_cache[tag]

    evaluations = moves = passes = 0
    limit_reached = False
    local_optimum = False
    while passes < settings["max_passes"] and not limit_reached:
        passes += 1
        improved = False
        for tag in movable:
            current = cost(tag, centres[tag])
            others = {other: placement for other, placement in placed.items() if other != tag}
            better = sorted(((cost(tag, centre), index) for index, (_candidate, centre) in enumerate(grid(tag))
                             if cost(tag, centre) < current - IMPROVEMENT_M))
            for _value, index in better:
                if evaluations >= settings["max_evaluations"]:
                    limit_reached = True
                    break
                evaluations += 1
                candidate, centre = grid(tag)[index]
                if candidate_valid(equipment[tag], candidate, others):
                    placed[tag], centres[tag] = dict(candidate), centre
                    moves += 1
                    improved = True
                    break
            if limit_reached:
                break
        # Pair exchange: two items trade anchor positions, each in any allowed orientation.
        for index, first in enumerate(movable):
            if limit_reached:
                break
            for second in movable[index + 1:]:
                if limit_reached:
                    break
                before = cost(first, centres[first], second) + cost(second, centres[second], first)
                pair_weight = sum(weight for other, weight in links[first] if other == second)
                before += pair_weight * _manhattan(centres[first], centres[second])
                rest = {tag: placement for tag, placement in placed.items() if tag not in (first, second)}
                best = None
                for rotation_first in equipment[first]["rotations_deg"]:
                    for rotation_second in equipment[second]["rotations_deg"]:
                        new_first = dict(placed[second], tag=first, rotation_deg=rotation_first)
                        new_second = dict(placed[first], tag=second, rotation_deg=rotation_second)
                        if (new_first["deck"] not in equipment[first]["allowed_decks"]
                                or new_second["deck"] not in equipment[second]["allowed_decks"]):
                            continue
                        centre_first = _centre(equipment[first], new_first, decks[new_first["deck"]])
                        centre_second = _centre(equipment[second], new_second, decks[new_second["deck"]])
                        after = (sum(weight * _manhattan(centre_first, centres[other])
                                     for other, weight in links[first] if other != second)
                                 + sum(weight * _manhattan(centre_second, centres[other])
                                       for other, weight in links[second] if other != first)
                                 + pair_weight * _manhattan(centre_first, centre_second))
                        if after < before - IMPROVEMENT_M and (best is None or after < best[0]):
                            best = (after, new_first, new_second, centre_first, centre_second)
                if best is None:
                    continue
                if evaluations >= settings["max_evaluations"]:
                    limit_reached = True
                    break
                evaluations += 1
                _after, new_first, new_second, centre_first, centre_second = best
                if (candidate_valid(equipment[first], new_first, rest)
                        and candidate_valid(equipment[second], new_second, dict(rest, **{first: new_first}))):
                    placed[first], placed[second] = new_first, new_second
                    centres[first], centres[second] = centre_first, centre_second
                    moves += 1
                    improved = True
        # Group shift: connected free items move together as a rigid block.  Without it a
        # chain stalls, because moving one link towards one neighbour lengthens the other.
        for group in groups:
            if limit_reached:
                break
            members = sorted(group)
            rest = {tag: placement for tag, placement in placed.items() if tag not in group}
            external = [(tag, other, weight) for tag in members for other, weight in links[tag]
                        if other not in group]
            if not external:
                continue
            deck = decks[placed[members[0]]["deck"]]
            if any(placed[tag]["deck"] != deck["id"] for tag in members):
                continue
            spans = [equipment_bounds(equipment[tag], placed[tag], deck) for tag in members]
            low_x, low_y = min(b[0] for b in spans), min(b[1] for b in spans)
            high_x, high_y = max(b[3] for b in spans), max(b[4] for b in spans)
            step = study["options"]["grid_m"]
            shifts = []
            current = sum(weight * _manhattan(centres[tag], centres[other]) for tag, other, weight in external)
            for i in range(-int(math.floor(low_x / step + 1.0e-9)),
                           int(math.floor((deck["width_m"] - high_x) / step + 1.0e-9)) + 1):
                for j in range(-int(math.floor(low_y / step + 1.0e-9)),
                               int(math.floor((deck["depth_m"] - high_y) / step + 1.0e-9)) + 1):
                    if i == 0 and j == 0:
                        continue
                    dx, dy = i * step, j * step
                    value = sum(weight * _manhattan([centres[tag][0] + dx, centres[tag][1] + dy, centres[tag][2]],
                                                    centres[other]) for tag, other, weight in external)
                    if value < current - IMPROVEMENT_M:
                        shifts.append((value, abs(i) + abs(j), i, j))
            for _value, _size, i, j in sorted(shifts):
                if evaluations >= settings["max_evaluations"]:
                    limit_reached = True
                    break
                evaluations += 1
                moved = {}
                valid = True
                for tag in members:
                    candidate = dict(placed[tag], x_m=round(placed[tag]["x_m"] + i * step, 10),
                                     y_m=round(placed[tag]["y_m"] + j * step, 10))
                    if not candidate_valid(equipment[tag], candidate, dict(rest, **moved)):
                        valid = False
                        break
                    moved[tag] = candidate
                if valid:
                    for tag, candidate in moved.items():
                        placed[tag] = candidate
                        centres[tag] = _centre(equipment[tag], candidate, decks[candidate["deck"]])
                    moves += 1
                    improved = True
                    break
        if not improved and not limit_reached:
            local_optimum = True
            break

    final = weighted_connection_length(study, placed, equipment, decks)
    if final > initial + TOLERANCE_M:  # defensive: never hand back a longer layout
        placed = {p["tag"]: dict(p) for p in placements}
        final, moves, local_optimum = initial, 0, False
    record = {
        "method": METHOD,
        "objective": OBJECTIVE,
        "status": "IMPROVED" if final < initial - IMPROVEMENT_M else "NO_IMPROVEMENT",
        "stopped_by": "SEARCH_LIMIT" if limit_reached else "LOCAL_OPTIMUM" if local_optimum else "PASS_LIMIT",
        "objective_initial_m": round(initial, 6),
        "objective_final_m": round(final, 6),
        "reduction_percent": round(100.0 * (initial - final) / initial, 3) if initial > 0 else 0.0,
        "lower_bound_m": round(bound, 6),
        "gap_to_lower_bound_percent": (round(100.0 * (final - bound) / final, 3) if final > 0 else 0.0),
        "moves_accepted": moves,
        "candidates_evaluated": evaluations,
        "passes": passes,
        "settings": {"max_passes": settings["max_passes"], "max_evaluations": settings["max_evaluations"]},
        "local_optimum": local_optimum,
        "optimality_proven": False,
    }
    if not math.isfinite(final):
        raise ValueError("Layout objective is not finite")
    return [placed[tag] for tag in sorted(placed)], record
