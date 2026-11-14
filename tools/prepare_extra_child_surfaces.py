"""Blender: fit separate authored full outfits to the reviewed child silhouette.

Run with --source-root fulloutfits/staged --body reference.glb --output DIR.
Only the original child's head, face, hands and feet are reused; no hidden
mobile body or deformed mobile hand is carried into the new visual source.
"""
import argparse
import sys
from pathlib import Path
import bpy
from mathutils import Matrix

p = argparse.ArgumentParser()
p.add_argument('--source-root', type=Path, required=True)
p.add_argument('--body', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args(sys.argv[sys.argv.index('--') + 1:])
a.output.mkdir(parents=True, exist_ok=True)
for outfit in (2, 5, 6):
    source = a.source_root / f'의상_가족_{outfit}' / f'의상_가족_{outfit}.blend'
    bpy.ops.wm.open_mainfile(filepath=str(source))
    parts = [o for o in bpy.context.scene.objects if o.type in ('MESH', 'CURVE') and not o.hide_render]
    bpy.ops.object.select_all(action='DESELECT')
    for o in parts:
        o.hide_set(False)
        o.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.convert(target='MESH')
    parts = list(bpy.context.selected_objects)
    cuff_z = .473 if outfit == 5 else .553
    neck_z = .888 if outfit == 5 else .968
    lower_top = .315 if outfit == 5 else .395
    for o in parts:
        matrix = o.matrix_world.copy()
        o.parent = None
        o.matrix_world = Matrix.Identity(4)
        for v in o.data.vertices:
            q = matrix @ v.co
            if o.name.startswith('LOWER'):
                q.z = .04 + q.z / lower_top * .21
            else:
                q.z = .36 + (q.z - cuff_z) / (neck_z - cuff_z) * .345
            q.x *= .89
            q.y *= .90
            v.co = q
        o.name = 'OUTFIT|' + o.name
    bpy.ops.import_scene.gltf(filepath=str(a.body))
    imported = list(bpy.context.selected_objects)
    kept = []
    for o in imported:
        if o.type == 'MESH' and (o.name.startswith(('BODY|', 'FACE_')) or 'exposed_hand' in o.name or 'exposed_legs' in o.name):
            matrix = o.matrix_world.copy()
            o.parent = None
            o.matrix_world = matrix
            kept.append(o)
        else:
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.ops.object.select_all(action='DESELECT')
    for o in parts + kept:
        o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(a.output / f'family_{outfit}.glb'), export_format='GLB', use_selection=True, export_animations=False, export_skins=False, export_apply=True)
    print('VISUAL_READY', outfit, len(parts), len(kept), flush=True)
