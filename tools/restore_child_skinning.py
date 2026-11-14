#!/usr/bin/env python3
"""Restore the reviewed child surfaces and bind them to the existing child rig.

The visual source is the pre-motion catalog at 42a51eb. This adds only skin
attributes and skeletal clips; vertex positions, normals, UVs, materials and
textures remain byte-for-byte as authored. Run from the repository root.
"""
from __future__ import annotations

import argparse
import copy
import heapq
import json
import math
import struct
import subprocess
import shutil
from pathlib import Path

from build_character_gaits import ASSET_ROOT, Glb, ROOT, add_parent_run, mat_mul, point, world_matrices

SOURCE_REVISION = "42a51eb"


def inverse_matrix(matrix):
    rows = [[matrix[c * 4 + r] for c in range(4)] + [float(r == c) for c in range(4)] for r in range(4)]
    for col in range(4):
        pivot = max(range(col, 4), key=lambda r: abs(rows[r][col]))
        if abs(rows[pivot][col]) < 1e-10:
            raise ValueError("Singular bind transform")
        rows[col], rows[pivot] = rows[pivot], rows[col]
        divisor = rows[col][col]
        rows[col] = [value / divisor for value in rows[col]]
        for r in range(4):
            if r != col:
                factor = rows[r][col]
                rows[r] = [a - factor * b for a, b in zip(rows[r], rows[col])]
    return [rows[r][4 + c] for c in range(4) for r in range(4)]


def read_attribute(glb, index):
    acc = glb.doc["accessors"][index]
    width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4, "MAT4": 16}[acc["type"]]
    code = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}[acc["componentType"]]
    fmt = "<" + code * width
    view = glb.doc["bufferViews"][acc["bufferView"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride", struct.calcsize(fmt))
    return [struct.unpack_from(fmt, glb.binary, start + i * stride) for i in range(acc["count"])]


class WeightTree:
    def __init__(self, samples):
        self.samples = samples
        self.root = self.build(list(range(len(samples))))

    def build(self, ids, depth=0):
        if len(ids) <= 12:
            return ids
        axis = depth % 3
        ids.sort(key=lambda i: self.samples[i][0][axis])
        mid = len(ids) // 2
        return axis, self.samples[ids[mid]][0][axis], self.build(ids[:mid], depth + 1), self.build(ids[mid:], depth + 1)

    def weights(self, position):
        best = []

        def visit(node):
            if isinstance(node, list):
                for idx in node:
                    p = self.samples[idx][0]
                    distance = sum((a - b) ** 2 for a, b in zip(p, position))
                    item = (-distance, idx)
                    if len(best) < 4:
                        heapq.heappush(best, item)
                    elif distance < -best[0][0]:
                        heapq.heapreplace(best, item)
                return
            axis, split, low, high = node
            delta = position[axis] - split
            first, second = (low, high) if delta < 0 else (high, low)
            visit(first)
            if len(best) < 4 or delta * delta < -best[0][0]:
                visit(second)

        visit(self.root)
        combined = {}
        for negative_distance, idx in best:
            factor = 1.0 / max(-negative_distance, 1e-8)
            for joint, weight in self.samples[idx][1].items():
                combined[joint] = combined.get(joint, 0.0) + factor * weight
        return normalized(combined)


def normalized(weights):
    top = sorted(weights.items(), key=lambda item: -item[1])[:4]
    total = sum(w for _, w in top)
    return {j: w / total for j, w in top if w > 0}


def source_tree(rig, node):
    samples = []
    world = world_matrices(rig.doc)[rig.doc["nodes"].index(node)]
    for primitive in rig.doc["meshes"][node["mesh"]]["primitives"]:
        attrs = primitive["attributes"]
        positions = read_attribute(rig, attrs["POSITION"])
        joints = read_attribute(rig, attrs["JOINTS_0"])
        weights = read_attribute(rig, attrs["WEIGHTS_0"])
        for p, js, ws in zip(positions, joints, weights):
            samples.append((point(world, p), {j: w for j, w in zip(js, ws) if w > 0}))
    return WeightTree(samples)


def build(outfit, rig_root, visual_root=None):
    destination = ASSET_ROOT / f"catalog/child/outfits/family_{outfit}.glb"
    relative = destination.relative_to(ROOT).as_posix()
    original_path = ROOT / f".state/rig-transfer/original_{outfit}.glb"
    original_path.parent.mkdir(parents=True, exist_ok=True)
    if visual_root is None:
        original_path.write_bytes(subprocess.check_output(["git", "show", f"{SOURCE_REVISION}:{relative}"], cwd=ROOT))
    else:
        shutil.copyfile(visual_root / f"family_{outfit}.glb", original_path)
    original = Glb(original_path)
    target = Glb(original_path)
    # Derive motion from the existing child rig, never from a modified mesh.
    rig_path = original_path.with_name(f"rig_{outfit}.glb")
    shutil.copyfile(rig_root / f"의상_가족_{outfit}/LOD0.glb", rig_path)
    add_parent_run(rig_path, preserve_walk_arms=True)
    rig = Glb(rig_path)
    original_binary = bytes(target.binary)
    original_node_count = len(target.doc["nodes"])
    mesh_worlds = world_matrices(target.doc)
    rig_worlds = world_matrices(rig.doc)
    old_skin = rig.doc["skins"][0]
    joint_names = [rig.doc["nodes"][i]["name"] for i in old_skin["joints"]]
    joint_by_name = {name: i for i, name in enumerate(joint_names)}
    node_map = {}
    for i, node in enumerate(rig.doc["nodes"]):
        if "mesh" not in node:
            node_map[i] = len(target.doc["nodes"])
            target.doc["nodes"].append(copy.deepcopy(node))
    for old, new in node_map.items():
        n = target.doc["nodes"][new]
        if "children" in n:
            n["children"] = [node_map[c] for c in n["children"] if c in node_map]
    for root in rig.doc["scenes"][rig.doc.get("scene", 0)]["nodes"]:
        if root in node_map:
            target.doc["scenes"][target.doc.get("scene", 0)]["nodes"].append(node_map[root])

    imported = {}

    def import_accessor(index):
        if index not in imported:
            acc = copy.deepcopy(rig.doc["accessors"][index])
            view = rig.doc["bufferViews"][acc["bufferView"]]
            start = view.get("byteOffset", 0)
            new_view = target.append_view(bytes(rig.binary[start : start + view["byteLength"]]))
            if "byteStride" in view:
                target.doc["bufferViews"][new_view]["byteStride"] = view["byteStride"]
            acc["bufferView"] = new_view
            imported[index] = len(target.doc["accessors"])
            target.doc["accessors"].append(acc)
        return imported[index]

    target.doc["animations"] = []
    for animation in rig.doc["animations"]:
        if animation["name"] not in {"Idle", "Walk", "Run"}:
            continue
        clone = copy.deepcopy(animation)
        for channel in clone["channels"]:
            channel["target"]["node"] = node_map[channel["target"]["node"]]
        for sampler in clone["samplers"]:
            sampler["input"] = import_accessor(sampler["input"])
            sampler["output"] = import_accessor(sampler["output"])
        target.doc["animations"].append(clone)

    body_tree = source_tree(rig, next(n for n in rig.doc["nodes"] if n.get("name") == "BODY|child"))
    outfit_tree = source_tree(rig, next(n for n in rig.doc["nodes"] if n.get("name", "").startswith("OUTFIT|")))
    target.doc["skins"] = []
    skin_cache = {}
    vertex_count = 0
    for node_index in range(original_node_count):
        node = target.doc["nodes"][node_index]
        if "mesh" not in node:
            continue
        name = node.get("name", "")
        is_head = name.startswith(("BODY|head", "BODY|tuft", "FACE_"))
        # AccessoryRigBinding must resolve the animated joint named 'head'.
        if name == "BODY|head":
            node["name"] = "BODY|head_surface"
        mesh_world = mesh_worlds[node_index]
        key = tuple(mesh_world)
        if key not in skin_cache:
            binds = [v for joint in old_skin["joints"] for v in mat_mul(inverse_matrix(rig_worlds[joint]), mesh_world)]
            bind_index = target.append_accessor(binds, "MAT4", 16)
            skin_cache[key] = len(target.doc["skins"])
            target.doc["skins"].append({"name": f"ChildRigMeshBind_{len(skin_cache)}", "joints": [node_map[j] for j in old_skin["joints"]], "inverseBindMatrices": bind_index})
        node["skin"] = skin_cache[key]
        is_skin = "exposed_" in name or name == "child_body_unified"
        tree = body_tree if is_skin else outfit_tree
        for primitive in target.doc["meshes"][node["mesh"]]["primitives"]:
            js, ws = [], []
            for local in target.read_float_vec3(primitive["attributes"]["POSITION"]):
                world = point(mesh_world, local)
                if is_head:
                    weights = {joint_by_name["head"]: 1.0}
                elif "exposed_hand" in name or "sleeve_cuff" in name:
                    weights = {joint_by_name["forearm.L" if world[0] > 0 else "forearm.R"]: 1.0}
                else:
                    # The mobile body omits skin hidden beneath the outfit.
                    # For the intact original overall body, transfer those
                    # covered vertices from the clothing surface instead of
                    # a distant surviving hand/leg sample.
                    if outfit == 3 and name == "child_body_unified" and abs(world[0]) < 0.23 and 0.17 < world[1] < 0.64:
                        weights = outfit_tree.weights(world)
                    else:
                        weights = tree.weights(world)
                    # The authored cuff and hand are separate meshes. Match the
                    # distal sleeve/body region to their forearm transform.
                    x, y, _ = world
                    if abs(x) > 0.26 and y < 0.455:
                        blend = min(1.0, max(0.0, (abs(x) - 0.26) / 0.035)) * min(1.0, max(0.0, (0.455 - y) / 0.025))
                        joint = joint_by_name["forearm.L" if x > 0 else "forearm.R"]
                        weights = {j: w * (1.0 - blend) for j, w in weights.items()}
                        weights[joint] = weights.get(joint, 0.0) + blend
                        weights = normalized(weights)
                pairs = list(weights.items())
                js.extend([j for j, _ in pairs] + [0] * (4 - len(pairs)))
                ws.extend([w for _, w in pairs] + [0.0] * (4 - len(pairs)))
                vertex_count += 1
            view = target.append_view(bytes(js))
            joint_accessor = len(target.doc["accessors"])
            target.doc["accessors"].append({"bufferView": view, "componentType": 5121, "count": len(js) // 4, "type": "VEC4"})
            primitive["attributes"]["JOINTS_0"] = joint_accessor
            primitive["attributes"]["WEIGHTS_0"] = target.append_accessor(ws, "VEC4", 4)
    target.doc.setdefault("asset", {}).setdefault("extras", {})["childSurfaceSkinning"] = {"visualSource": str(visual_root) if visual_root else SOURCE_REVISION, "rigSource": "original child mobile rig", "method": "four nearest source skin samples; shared distal forearm for authored hand/cuff"}
    # No existing bytes were altered. This covers all original geometry,
    # normals, UVs, material images, indices and their exact accessor bounds.
    assert bytes(target.binary[:len(original_binary)]) == original_binary
    assert target.doc.get("materials") == original.doc.get("materials")
    assert target.doc.get("textures") == original.doc.get("textures")
    for original_mesh, skinned_mesh in zip(original.doc["meshes"], target.doc["meshes"]):
        for a, b in zip(original_mesh["primitives"], skinned_mesh["primitives"]):
            assert {k: v for k, v in b["attributes"].items() if k not in {"JOINTS_0", "WEIGHTS_0"}} == a["attributes"]
    target.path = destination
    target.write()
    print(f"family_{outfit}: preserved {len(original_binary)} source bytes, skinned {vertex_count} vertices, {len(target.doc['skins'])} mesh bind spaces", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rig-root", type=Path, required=True, help="Original HYDAI_FINAL/mobile/child directory")
    parser.add_argument("--visual-root", type=Path, help="Prepared unskinned family_N.glb surfaces")
    parser.add_argument("--outfits", type=int, nargs="+", default=[1, 3, 4])
    args = parser.parse_args()
    for outfit in args.outfits:
        build(outfit, args.rig_root, args.visual_root)
