"""Orthogonal pipe-route estimates for a placed plant layout.

For every process connection the module searches the shortest axis-parallel path
from the source equipment to the target equipment through free space: it goes
round equipment, maintenance envelopes, obstacles and reserved corridors, and it
prefers few bends and few deck penetrations.  The result is a routed-length
estimate that replaces guessing from centre-to-centre distance.

These routes are screening estimates produced by software.  They have no nozzle
positions, no pipe rack, no line sizes, no supports and no stress check, and two
routes may use the same space.  They are kept apart from the ``physical_routes``
evidence of the inventory contract and must never be entered there.
"""

from __future__ import annotations

import heapq
import math

from layout_model import equipment_bounds

SOURCE = {"kind": "software_model",
          "reference": "plant_layout_study.v1 orthogonal route estimate; not a physical route"}
METHOD = "A* search on an orthogonal grid, cost = length + bend and deck-penetration penalties"
DIRECTIONS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
DEFAULTS = {"enabled": False, "clearance_m": 0.0, "bend_penalty_m": 1.0,
            "deck_penetration_penalty_m": 5.0, "avoid_envelopes": True,
            "corridor_overhead_clearance_m": None, "headroom_m": 3.0, "max_cells": 250000}


def routing_settings(study):
    """Return the effective routing settings of a normalized study."""
    supplied = study.get("options", {}).get("routing") or {}
    settings = dict(DEFAULTS)
    settings.update({key: value for key, value in supplied.items() if key in DEFAULTS})
    settings["grid_m"] = float(supplied.get("grid_m", study["options"]["grid_m"]))
    return settings


def _expanded(bounds, margin):
    """Return bounds grown by a margin on every side."""
    return [bounds[0] - margin, bounds[1] - margin, bounds[2] - margin,
            bounds[3] + margin, bounds[4] + margin, bounds[5] + margin]


class _Grid:
    """A regular grid of cell centres over the plant volume with blocked cells."""

    def __init__(self, low, high, step):
        """Create the grid between two corner points with a cubic cell size."""
        self.low = list(low)
        self.step = step
        self.shape = [max(1, int(math.ceil((high[i] - low[i]) / step - 1.0e-9))) for i in range(3)]
        self.blocked = bytearray(self.shape[0] * self.shape[1] * self.shape[2])

    def index(self, i, j, k):
        """Return the flat index of a cell."""
        return (i * self.shape[1] + j) * self.shape[2] + k

    def inside(self, i, j, k):
        """Return whether a cell lies in the grid."""
        return 0 <= i < self.shape[0] and 0 <= j < self.shape[1] and 0 <= k < self.shape[2]

    def centre(self, cell):
        """Return the centre point of a cell in metres."""
        return [self.low[axis] + (cell[axis] + 0.5) * self.step for axis in range(3)]

    def cells_in(self, bounds):
        """Return the index ranges of the cells whose centre lies inside bounds."""
        ranges = []
        for axis in range(3):
            first = int(math.ceil((bounds[axis] - self.low[axis]) / self.step - 0.5 - 1.0e-9))
            last = int(math.floor((bounds[axis + 3] - self.low[axis]) / self.step - 0.5 + 1.0e-9))
            ranges.append((max(first, 0), min(last, self.shape[axis] - 1)))
        return ranges

    def mark(self, bounds, into=None):
        """Block every cell whose centre lies inside bounds; optionally collect the cells."""
        (i0, i1), (j0, j1), (k0, k1) = self.cells_in(bounds)
        for i in range(i0, i1 + 1):
            for j in range(j0, j1 + 1):
                for k in range(k0, k1 + 1):
                    position = self.index(i, j, k)
                    if into is not None:
                        into.add(position)
                    else:
                        self.blocked[position] = 1


def _volume(study, placements, settings):
    """Return the corner points of the routed volume and the effective grid size."""
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    width = max(d["width_m"] for d in study["decks"])
    depth = max(d["depth_m"] for d in study["decks"])
    bottom = min(d["elevation_m"] for d in study["decks"])
    # A deck with a clear-height limit ends there; headroom is added only above open levels.
    top = bottom
    limited = {d["id"] for d in study["decks"] if "height_limit_m" in d}
    for deck in study["decks"]:
        if deck["id"] in limited:
            top = max(top, deck["elevation_m"] + deck["height_limit_m"])
        else:
            top = max(top, deck["elevation_m"] + settings["headroom_m"])
    for placement in placements:
        bounds = equipment_bounds(equipment[placement["tag"]], placement, decks[placement["deck"]])
        top = max(top, bounds[5] + (0.0 if placement["deck"] in limited else settings["headroom_m"]))
    step = settings["grid_m"]
    while (math.ceil(width / step) * math.ceil(depth / step) * math.ceil((top - bottom) / step)
           > settings["max_cells"]):
        step *= 2.0
    return [0.0, 0.0, bottom], [width, depth, top], step


def _search(grid, sources, targets, target_box, settings, deck_levels):
    """Run A* from a set of start cells to a set of goal cells.

    The state is a cell with the direction it was entered from, so a change of
    direction can be charged a bend penalty.  Returns the cell path or ``None``.
    """
    step = grid.step
    bend = settings["bend_penalty_m"]
    penetration = settings["deck_penetration_penalty_m"]
    shape = grid.shape

    def heuristic(cell):
        """Admissible remaining length: axis distances to the target box."""
        point = grid.centre(cell)
        return sum(max(target_box[axis] - point[axis], 0.0, point[axis] - target_box[axis + 3])
                   for axis in range(3))

    queue = []
    best = {}
    parents = {}
    for cell in sorted(sources):
        state = (cell, -1)
        best[state] = 0.0
        heapq.heappush(queue, (heuristic(cell), 0.0, cell, -1))
    while queue:
        _estimate, cost, cell, direction = heapq.heappop(queue)
        if cost > best.get((cell, direction), math.inf) + 1.0e-12:
            continue
        if grid.index(*cell) in targets:
            path = [cell]
            state = (cell, direction)
            while state in parents:
                state = parents[state]
                path.append(state[0])
            return list(reversed(path))
        for new_direction, (di, dj, dk) in enumerate(DIRECTIONS):
            i, j, k = cell[0] + di, cell[1] + dj, cell[2] + dk
            if not (0 <= i < shape[0] and 0 <= j < shape[1] and 0 <= k < shape[2]):
                continue
            if grid.blocked[grid.index(i, j, k)]:
                continue
            new_cost = cost + step
            if direction not in (-1, new_direction):
                new_cost += bend
            if dk:
                lower = grid.low[2] + (min(cell[2], k) + 0.5) * step
                if any(lower < level <= lower + step for level in deck_levels):
                    new_cost += penetration
            state = ((i, j, k), new_direction)
            if new_cost < best.get(state, math.inf) - 1.0e-12:
                best[state] = new_cost
                parents[state] = (cell, direction)
                heapq.heappush(queue, (new_cost + heuristic((i, j, k)), new_cost, (i, j, k), new_direction))
    return None


def _surface_cells(grid, box, free):
    """Return the free cells that touch a blocked box through a face."""
    inside = set()
    grid.mark(box, into=inside)
    (i0, i1), (j0, j1), (k0, k1) = grid.cells_in(box)
    cells = set()
    for i in range(i0 - 1, i1 + 2):
        for j in range(j0 - 1, j1 + 2):
            for k in range(k0 - 1, k1 + 2):
                if not grid.inside(i, j, k):
                    continue
                position = grid.index(i, j, k)
                if position in inside or not free(position):
                    continue
                if any(grid.inside(i + di, j + dj, k + dk) and grid.index(i + di, j + dj, k + dk) in inside
                       for di, dj, dk in DIRECTIONS):
                    cells.add((i, j, k))
    return cells


def _corners(points):
    """Drop the points of a polyline that lie on a straight run."""
    kept = [points[0]]
    for before, point, after in zip(points[:-2], points[1:-1], points[2:]):
        first = [point[i] - before[i] for i in range(3)]
        second = [after[i] - point[i] for i in range(3)]
        cross = [first[1] * second[2] - first[2] * second[1], first[2] * second[0] - first[0] * second[2],
                 first[0] * second[1] - first[1] * second[0]]
        if any(abs(value) > 1.0e-9 for value in cross):
            kept.append(point)
    if len(points) > 1:
        kept.append(points[-1])
    return kept


def _face_point(point, box):
    """Return the point of a box nearest to an outside point."""
    return [min(max(point[axis], box[axis]), box[axis + 3]) for axis in range(3)]


def estimate_routes(study, alternative):
    """Route every connection of a placed alternative and total the lengths.

    Returns ``{"status", "method", "source", "settings", "grid_m", "routes",
    "total_length_m", "routed", "unrouted"}``.  Each route has the corner points
    of its centreline in plant metres, its length, bend count, deck penetrations
    and a status: ``ROUTED``, ``ROUTED_THROUGH_ENVELOPE`` when no path clear of
    maintenance envelopes exists, or ``NO_ROUTE_FOUND``.  A connection whose
    equipment is not placed is ``NOT_PLACED``.
    """
    settings = routing_settings(study)
    decks = {d["id"]: d for d in study["decks"]}
    equipment = {e["tag"]: e for e in study["equipment"]}
    placements = alternative.get("placements", [])
    placed = {p["tag"]: p for p in placements}
    low, high, step = _volume(study, placements, settings)
    margin = settings["clearance_m"]
    deck_levels = sorted(d["elevation_m"] for d in study["decks"] if d["elevation_m"] > low[2] + 1.0e-9)

    boxes = {tag: equipment_bounds(equipment[tag], p, decks[p["deck"]]) for tag, p in placed.items()}
    envelopes = {tag: [equipment_bounds(equipment[tag], p, decks[p["deck"]], e)
                       for e in equipment[tag]["envelopes"]] for tag, p in placed.items()}

    def build(with_envelopes, skip):
        """Block equipment, obstacles and corridors; envelopes of the two ends stay open."""
        grid = _Grid(low, high, step)
        for tag, box in boxes.items():
            grid.mark(_expanded(box, margin))
            if with_envelopes and tag not in skip:
                for envelope in envelopes[tag]:
                    grid.mark(envelope)
        for obstacle in study["obstacles"]:
            bounds = list(obstacle["bounds_m"])
            elevation = decks[obstacle["deck"]]["elevation_m"]
            bounds[2] += elevation
            bounds[5] += elevation
            grid.mark(_expanded(bounds, margin))
        for corridor in study["corridors"]:
            deck = decks[corridor["deck"]]
            bounds = corridor["bounds_m"]
            clear = settings["corridor_overhead_clearance_m"]
            ceiling = (deck["elevation_m"] + clear if clear is not None
                       else deck["elevation_m"] + deck.get("height_limit_m", high[2] - deck["elevation_m"]))
            grid.mark([bounds[0], bounds[1], deck["elevation_m"], bounds[2], bounds[3], ceiling])
        return grid

    routes = []
    for connection in study["connections"]:
        first, second = connection["from_tag"], connection["to_tag"]
        record = {"connection_id": connection["id"], "from_tag": first, "to_tag": second,
                  "points_m": [], "length_m": None, "bends": None, "deck_penetrations": None}
        if first not in placed or second not in placed:
            record["status"] = "NOT_PLACED"
            routes.append(record)
            continue
        if first == second:
            record.update(status="ROUTED", length_m=0.0, bends=0, deck_penetrations=0)
            routes.append(record)
            continue
        path = grid = None
        status = "NO_ROUTE_FOUND"
        attempts = [(True, "ROUTED"), (False, "ROUTED_THROUGH_ENVELOPE")] if settings["avoid_envelopes"] \
            else [(False, "ROUTED")]
        for with_envelopes, label in attempts:
            grid = build(with_envelopes, (first, second))
            free = lambda position, g=grid: not g.blocked[position]  # noqa: E731
            sources = _surface_cells(grid, _expanded(boxes[first], margin), free)
            goals = _surface_cells(grid, _expanded(boxes[second], margin), free)
            if not sources or not goals:
                continue
            targets = {grid.index(*cell) for cell in goals}
            path = _search(grid, sources, targets, boxes[second], settings, deck_levels)
            if path is not None:
                status = label
                break
        record["status"] = status
        if path is not None:
            centres = [grid.centre(cell) for cell in path]
            points = [_face_point(centres[0], boxes[first])] + centres + [_face_point(centres[-1], boxes[second])]
            points = _corners([point for index, point in enumerate(points)
                               if index == 0 or point != points[index - 1]])
            length = sum(math.dist(a, b) for a, b in zip(points[:-1], points[1:]))
            crossings = sum(1 for a, b in zip(points[:-1], points[1:]) for level in deck_levels
                            if min(a[2], b[2]) < level <= max(a[2], b[2]) and abs(a[2] - b[2]) > 1.0e-9)
            record.update(points_m=[[round(v, 6) for v in point] for point in points],
                          length_m=round(length, 6), bends=max(len(points) - 2, 0),
                          deck_penetrations=crossings)
        routes.append(record)

    routed = [r for r in routes if r["length_m"] is not None]
    unrouted = [r["connection_id"] for r in routes if r["length_m"] is None]
    return {
        "status": "COMPLETE" if not unrouted else "PARTIAL" if routed else "NONE",
        "method": METHOD,
        "source": dict(SOURCE),
        "evidence_class": "SCREENING_ESTIMATE",
        "settings": {key: settings[key] for key in sorted(settings) if key != "enabled"},
        "grid_m": step,
        "routes": routes,
        "total_length_m": round(sum(r["length_m"] for r in routed), 6),
        "routed": len(routed),
        "unrouted": unrouted,
        "limitations": ["No nozzle positions: a route runs from face to face of the equipment boxes.",
                        "Routes do not avoid each other and no pipe rack is allocated.",
                        "No line size, slope, support, flexibility or stress check.",
                        "Not physical-route evidence; never enter these in physical_routes."],
    }
