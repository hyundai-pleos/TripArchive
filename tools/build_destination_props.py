"""Build the five small, pastel destination props used beside the home character.

Run from the repository root with:

  /Applications/Blender.app/Contents/MacOS/Blender -b --python tools/build_destination_props.py

The exported GLBs contain only the prop meshes and their solid-color materials.
Preview-only floors, cameras and lights are created after each GLB is exported.
Blender's glTF exporter writes Y-up coordinates. Prop fronts are authored toward
Blender +Y, then turned toward -Y before export so they face glTF +Z.
"""

from __future__ import annotations

import math
from pathlib import Path
import sys

import bpy
from mathutils import Matrix, Vector


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/models/destination_props"
PREVIEWS = ROOT / ".state/props26"


PALETTE = {
    "mint": (0.37, 0.76, 0.64, 1.0),
    "mint_dark": (0.20, 0.57, 0.47, 1.0),
    "mint_light": (0.64, 0.88, 0.77, 1.0),
    "cream": (0.96, 0.88, 0.71, 1.0),
    "paper": (1.00, 0.96, 0.84, 1.0),
    "blue": (0.27, 0.53, 0.81, 1.0),
    "blue_dark": (0.17, 0.36, 0.65, 1.0),
    "blue_light": (0.50, 0.70, 0.91, 1.0),
    "yellow": (0.98, 0.76, 0.25, 1.0),
    "orange": (0.91, 0.42, 0.22, 1.0),
    "orange_light": (0.98, 0.58, 0.34, 1.0),
    "bread": (0.77, 0.48, 0.24, 1.0),
    "bread_light": (0.97, 0.79, 0.51, 1.0),
    "brown": (0.34, 0.18, 0.12, 1.0),
    "brown_light": (0.56, 0.33, 0.20, 1.0),
    "white": (0.96, 0.97, 0.92, 1.0),
    "red": (0.88, 0.34, 0.32, 1.0),
    "green": (0.24, 0.61, 0.38, 1.0),
    "glass": (0.16, 0.29, 0.38, 1.0),
}


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.cameras, bpy.data.lights):
        for datablock in list(datablocks):
            if datablock.users == 0:
                datablocks.remove(datablock)


def material(name: str, color: tuple[float, float, float, float] | None = None):
    if color is None:
        color = PALETTE[name]
    mat = bpy.data.materials.new(name=f"Clay_{name}")
    mat.diffuse_color = color
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = color
    bsdf.inputs["Roughness"].default_value = 0.82
    bsdf.inputs["Metallic"].default_value = 0.0
    return mat


MATERIALS = {name: material(name, color) for name, color in PALETTE.items()}


def finish_mesh(obj, mat, smooth: bool = True):
    obj.data.materials.append(mat)
    if smooth:
        for poly in obj.data.polygons:
            poly.use_smooth = True
    obj.select_set(False)
    return obj


def bevel(obj, width: float, segments: int = 3):
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    mod = obj.modifiers.new("Soft rounded edges", "BEVEL")
    mod.width = width
    mod.segments = segments
    mod.profile = 0.5
    mod.limit_method = "ANGLE"
    mod.harden_normals = True
    bpy.ops.object.modifier_apply(modifier=mod.name)
    obj.select_set(False)


def box(name: str, center, size, mat, radius: float = 0.035, rotation_z: float = 0.0):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=center, rotation=(0.0, 0.0, rotation_z))
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = size
    bevel(obj, min(radius, min(size) * 0.46))
    return finish_mesh(obj, mat)


def sphere(name: str, center, scale, mat, segments: int = 20, rings: int = 12):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, location=center)
    obj = bpy.context.object
    obj.name = name
    obj.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return finish_mesh(obj, mat)


def cylinder(name: str, center, radius: float, depth: float, mat, axis: str = "Z", bevel_width: float = 0.012, vertices: int = 40):
    rotation = (0.0, 0.0, 0.0) if axis == "Z" else (-math.pi / 2.0, 0.0, 0.0)
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=center, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    if bevel_width > 0:
        bevel(obj, min(bevel_width, depth * 0.25), segments=3)
    return finish_mesh(obj, mat)


def cone(name: str, center, radius: float, depth: float, mat, vertices: int = 24):
    bpy.ops.mesh.primitive_cone_add(vertices=vertices, radius1=radius, radius2=0.0, depth=depth, location=center)
    obj = bpy.context.object
    obj.name = name
    return finish_mesh(obj, mat)


def hemisphere(name: str, base_z: float, radius: float, height: float, mat, segments: int = 48, rings: int = 10):
    vertices = []
    for i in range(rings):
        theta = math.pi / 2.0 * (1.0 - i / rings)
        ring_radius = radius * math.sin(theta)
        z = base_z + height * math.cos(theta)
        for j in range(segments):
            phi = 2.0 * math.pi * j / segments
            vertices.append((ring_radius * math.cos(phi), ring_radius * math.sin(phi), z))
    pole = len(vertices)
    vertices.append((0.0, 0.0, base_z + height))
    faces = []
    for i in range(rings - 1):
        lower = i * segments
        upper = (i + 1) * segments
        for j in range(segments):
            nxt = (j + 1) % segments
            faces.append((lower + j, upper + j, upper + nxt, lower + nxt))
    last = (rings - 1) * segments
    for j in range(segments):
        faces.append((last + j, pole, last + (j + 1) % segments))
    mesh = bpy.data.meshes.new(f"{name} mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    return finish_mesh(obj, mat)


def ellipsoid(name: str, center, scale, mat, rotation_z: float = 0.0):
    obj = sphere(name, center, scale, mat)
    obj.rotation_euler[2] = rotation_z
    return obj


def add_mart():
    # Basket base, separated slats and a soft lip leave the produce visible.
    box("Basket base", (0.0, 0.0, 0.075), (0.78, 0.47, 0.15), MATERIALS["mint_dark"], 0.06)
    for side_y, label in ((0.205, "front"), (-0.205, "back")):
        for i, x in enumerate((-0.31, -0.105, 0.105, 0.31)):
            box(f"Basket {label} slat {i + 1}", (x, side_y, 0.205), (0.065, 0.065, 0.25), MATERIALS["mint"], 0.028)
    for side_x, label in ((-0.34, "left"), (0.34, "right")):
        for i, y in enumerate((-0.105, 0.105)):
            box(f"Basket {label} slat {i + 1}", (side_x, y, 0.205), (0.065, 0.065, 0.25), MATERIALS["mint"], 0.028)
    box("Basket lip front", (0.0, 0.234, 0.335), (0.82, 0.07, 0.065), MATERIALS["mint_light"], 0.026)
    box("Basket lip back", (0.0, -0.234, 0.335), (0.82, 0.07, 0.065), MATERIALS["mint_light"], 0.026)
    box("Basket lip left", (-0.38, 0.0, 0.335), (0.07, 0.40, 0.065), MATERIALS["mint_light"], 0.026)
    box("Basket lip right", (0.38, 0.0, 0.335), (0.07, 0.40, 0.065), MATERIALS["mint_light"], 0.026)
    # The loaf sits diagonally in the basket; its lower half is embedded.
    baguette = ellipsoid("Baguette", (-0.12, -0.055, 0.45), (0.105, 0.09, 0.34), MATERIALS["bread"])
    baguette.rotation_euler[1] = -0.16
    for i, z in enumerate((0.47, 0.57, 0.67)):
        x = -0.12 - 0.16 * (z - 0.45)
        slash = box(f"Baguette score {i + 1}", (x, 0.03, z), (0.11, 0.025, 0.022), MATERIALS["bread_light"], 0.007)
        slash.rotation_euler[1] = 0.16
    cone("Carrot", (0.22, 0.015, 0.49), 0.067, 0.30, MATERIALS["orange"])
    # Tilt carrot toward the right and bring its tip forward.
    bpy.data.objects["Carrot"].rotation_euler[1] = -0.22
    ellipsoid("Carrot leaf left", (0.19, 0.015, 0.645), (0.035, 0.026, 0.09), MATERIALS["green"], -0.28)
    ellipsoid("Carrot leaf right", (0.25, 0.015, 0.645), (0.035, 0.026, 0.08), MATERIALS["mint_dark"], 0.26)


def add_school():
    # Two visibly separate books with page blocks, stacked low and centered.
    box("Lower book pages", (0.0, 0.0, 0.115), (0.72, 0.48, 0.16), MATERIALS["paper"], 0.035)
    box("Lower blue cover", (0.0, 0.0, 0.155), (0.78, 0.52, 0.085), MATERIALS["blue_dark"], 0.045)
    box("Upper book pages", (0.035, -0.005, 0.282), (0.63, 0.43, 0.14), MATERIALS["paper"], 0.03)
    box("Upper blue cover", (0.035, -0.005, 0.323), (0.69, 0.47, 0.075), MATERIALS["blue"], 0.04)
    # The narrow spine bands read from the front and side.
    box("Lower book spine", (-0.365, 0.0, 0.158), (0.045, 0.50, 0.09), MATERIALS["blue_light"], 0.02)
    box("Upper book spine", (-0.285, -0.005, 0.325), (0.04, 0.45, 0.08), MATERIALS["blue_light"], 0.018)
    box("Upper cover mark", (0.08, 0.238, 0.366), (0.24, 0.018, 0.018), MATERIALS["yellow"], 0.008)
    # Pencil lies across the top book: rounded barrel, eraser, ferrule and tip.
    box("Pencil barrel", (0.045, 0.055, 0.435), (0.69, 0.065, 0.055), MATERIALS["yellow"], 0.025, -0.06)
    box("Pencil eraser", (0.405, 0.055, 0.435), (0.10, 0.07, 0.062), MATERIALS["red"], 0.026, -0.06)
    box("Pencil ferrule", (0.338, 0.055, 0.435), (0.045, 0.071, 0.063), MATERIALS["cream"], 0.014, -0.06)
    cone("Pencil graphite tip", (-0.337, 0.055, 0.435), 0.036, 0.10, MATERIALS["brown"])
    bpy.data.objects["Pencil graphite tip"].rotation_euler[1] = math.pi / 2.0


def add_tourism():
    # Cream compact camera, with a large forward-facing chocolate lens.
    box("Camera body", (0.0, 0.0, 0.34), (0.79, 0.39, 0.47), MATERIALS["cream"], 0.09)
    box("Camera top housing", (-0.16, -0.005, 0.61), (0.30, 0.34, 0.12), MATERIALS["paper"], 0.045)
    box("Camera grip", (0.30, 0.06, 0.34), (0.15, 0.32, 0.35), MATERIALS["cream"], 0.055)
    # Rings protrude toward the +Y front; the dark central disk stays inset.
    cylinder("Lens outer rim", (-0.015, 0.238, 0.35), 0.225, 0.11, MATERIALS["brown_light"], axis="Y", bevel_width=0.014)
    cylinder("Lens inner rim", (-0.015, 0.304, 0.35), 0.176, 0.045, MATERIALS["brown"], axis="Y", bevel_width=0.009)
    cylinder("Lens glass", (-0.015, 0.333, 0.35), 0.132, 0.018, MATERIALS["glass"], axis="Y", bevel_width=0.004)
    ellipsoid("Lens glint", (-0.062, 0.346, 0.398), (0.027, 0.012, 0.042), MATERIALS["blue_light"], 0.14)
    cylinder("Shutter button", (0.27, 0.04, 0.62), 0.055, 0.05, MATERIALS["orange"], axis="Z", bevel_width=0.012)
    box("Viewfinder", (-0.275, 0.205, 0.49), (0.13, 0.07, 0.11), MATERIALS["brown_light"], 0.025)


def add_restaurant():
    # A round orange serving plate with a domed cream cloche and top knob.
    cylinder("Plate foot", (0.0, 0.0, 0.075), 0.27, 0.13, MATERIALS["orange_light"], bevel_width=0.028, vertices=48)
    cylinder("Plate", (0.0, 0.0, 0.14), 0.43, 0.11, MATERIALS["orange"], bevel_width=0.035, vertices=56)
    cylinder("Plate inner well", (0.0, 0.0, 0.205), 0.32, 0.025, MATERIALS["orange_light"], bevel_width=0.012, vertices=48)
    # A true upper hemisphere sits flat on its cream rim instead of reading as a ball.
    cylinder("Cloche lower lip", (0.0, 0.0, 0.265), 0.268, 0.045, MATERIALS["paper"], bevel_width=0.012, vertices=48)
    hemisphere("Cream cloche", base_z=0.2875, radius=0.255, height=0.235, mat=MATERIALS["cream"])
    ellipsoid("Cloche knob", (0.0, 0.0, 0.548), (0.075, 0.075, 0.048), MATERIALS["paper"])


def add_hospital():
    # Medical kit with a soft arch handle and a raised mint cross on the front.
    box("Medical bag body", (0.0, 0.0, 0.30), (0.76, 0.40, 0.55), MATERIALS["white"], 0.09)
    box("Bag top seam", (0.0, 0.0, 0.585), (0.60, 0.39, 0.07), MATERIALS["paper"], 0.03)
    # Handle is a rounded U made from two uprights and a top bar.
    box("Handle left post", (-0.16, -0.01, 0.70), (0.075, 0.075, 0.22), MATERIALS["mint_dark"], 0.034)
    box("Handle right post", (0.16, -0.01, 0.70), (0.075, 0.075, 0.22), MATERIALS["mint_dark"], 0.034)
    box("Handle arch", (0.0, -0.01, 0.795), (0.39, 0.075, 0.085), MATERIALS["mint_dark"], 0.04)
    # The sign sits a little ahead of the white face so it cannot z-fight.
    box("Medical cross vertical", (0.0, 0.218, 0.31), (0.13, 0.045, 0.33), MATERIALS["mint"], 0.038)
    box("Medical cross horizontal", (0.0, 0.221, 0.31), (0.34, 0.05, 0.13), MATERIALS["mint"], 0.038)
    box("Bag clasp", (0.0, 0.222, 0.555), (0.13, 0.045, 0.055), MATERIALS["mint_light"], 0.018)


BUILDERS = {
    "mart": add_mart,
    "school": add_school,
    "tourism": add_tourism,
    "restaurant": add_restaurant,
    "hospital": add_hospital,
}


def mesh_stats(objects):
    bpy.context.view_layer.update()
    points = [obj.matrix_world @ Vector(corner) for obj in objects for corner in obj.bound_box]
    mins = tuple(min(p[i] for p in points) for i in range(3))
    maxs = tuple(max(p[i] for p in points) for i in range(3))
    triangles = sum(max(0, len(poly.vertices) - 2) for obj in objects for poly in obj.data.polygons)
    return triangles, mins, maxs


def orient_front_for_gltf(objects):
    """Bake the authored Blender +Y face toward Blender -Y / glTF +Z.

    The glTF exporter swizzles Blender coordinates as (x, z, -y) when
    export_yup is enabled. Applying a half-turn around the grounded up axis
    preserves the layout while making the authored front face glTF +Z.
    """
    bpy.context.view_layer.update()
    half_turn = Matrix.Rotation(math.pi, 4, "Z")
    for obj in objects:
        transform = half_turn @ obj.matrix_world.copy()
        obj.data.transform(transform)
        obj.matrix_world = Matrix.Identity(4)
    bpy.context.view_layer.update()
    points = [Vector(corner) for obj in objects for corner in obj.bound_box]
    min_z = min(point.z for point in points)
    center_x = (min(point.x for point in points) + max(point.x for point in points)) / 2.0
    center_y = (min(point.y for point in points) + max(point.y for point in points)) / 2.0
    placement = Matrix.Translation(Vector((-center_x, -center_y, -min_z)))
    for obj in objects:
        obj.data.transform(placement)
    bpy.context.view_layer.update()


def add_preview_setup(prop_name: str):
    # Preview-only ground, orthographic camera and soft studio lighting.
    ground_mat = bpy.data.materials.new("Preview background")
    ground_mat.diffuse_color = (0.89, 0.92, 0.91, 1.0)
    ground_mat.use_nodes = True
    ground_mat.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = ground_mat.diffuse_color
    ground_mat.node_tree.nodes.get("Principled BSDF").inputs["Roughness"].default_value = 1.0
    bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, -0.012))
    floor = bpy.context.object
    floor.name = "PREVIEW ONLY ground"
    floor.data.materials.append(ground_mat)

    world = bpy.data.worlds.new("PREVIEW ONLY world")
    bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.83, 0.88, 0.88, 1.0)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.42

    bpy.ops.object.camera_add(location=(2.35, -3.25, 2.15))
    camera = bpy.context.object
    camera.name = "PREVIEW ONLY camera"
    target = Vector((0.0, 0.0, 0.39))
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = 1.67
    bpy.context.scene.camera = camera

    def area_light(name, location, energy, size):
        bpy.ops.object.light_add(type="AREA", location=location)
        light = bpy.context.object
        light.name = f"PREVIEW ONLY {name}"
        light.data.energy = energy
        light.data.shape = "DISK"
        light.data.size = size
        light.rotation_euler = (target - light.location).to_track_quat("-Z", "Y").to_euler()

    area_light("key", (1.0, -2.0, 3.4), 180, 3.0)
    area_light("fill", (-2.6, -1.4, 1.9), 75, 2.7)
    area_light("rim", (0.3, 2.1, 2.8), 110, 2.3)

    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = 48
    scene.render.resolution_x = 900
    scene.render.resolution_y = 900
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.camera.data.lens = 50
    scene.render.filepath = str(PREVIEWS / f"{prop_name}.png")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    for prop_name, build in BUILDERS.items():
        clear_scene()
        build()
        prop_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        orient_front_for_gltf(prop_meshes)
        triangles, mins, maxs = mesh_stats(prop_meshes)
        bpy.ops.object.select_all(action="DESELECT")
        for obj in prop_meshes:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = prop_meshes[0]
        out_path = OUTPUT / f"{prop_name}.glb"
        bpy.ops.export_scene.gltf(
            filepath=str(out_path),
            export_format="GLB",
            use_selection=True,
            export_animations=False,
            export_skins=False,
            export_apply=True,
            export_yup=True,
        )
        add_preview_setup(prop_name)
        bpy.ops.render.render(write_still=True)
        print(
            f"PROP_READY name={prop_name} objects={len(prop_meshes)} triangles={triangles} "
            f"bounds_min={tuple(round(v, 4) for v in mins)} "
            f"bounds_max={tuple(round(v, 4) for v in maxs)} "
            f"bytes={out_path.stat().st_size} preview={PREVIEWS / (prop_name + '.png')}",
            flush=True,
        )


if __name__ == "__main__":
    main()
