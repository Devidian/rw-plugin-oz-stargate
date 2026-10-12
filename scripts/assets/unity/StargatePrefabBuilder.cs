using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

// Run after build-fbx-lods.py output and gate-subtle-color.png have been copied
// to Assets/AssetBundles/StargateMilkyway. Requires an HDRP Unity project.
public static class StargatePrefabBuilder {
    [Serializable] private sealed class Manifest { public Lod[] lods; }
    [Serializable] private sealed class Lod { public Entry[] entries; }
    [Serializable] private sealed class Entry { public string part, file, material; public int triangles; }

    private const string Folder = "Assets/AssetBundles/StargateMilkyway";
    private static readonly float[] Thresholds = { .4f, .17f, .06f, .005f };
    private static readonly Dictionary<string, Material> Materials = new Dictionary<string, Material>();

    public static void Build() {
        var version = Application.unityVersion;
        if (version != "6000.3.22f1") throw new InvalidOperationException("Rising World bundle requires Unity 6000.3.22f1; found " + version);
        var json = File.ReadAllText(Folder + "/manifest.json");
        var manifest = JsonUtility.FromJson<Manifest>(json);
        if (manifest == null || manifest.lods == null || manifest.lods.Length != 4)
            throw new InvalidOperationException("Expected exactly four generated LODs");
        var root = new GameObject("StargateMilkyway");
        try {
            CreateMaterials();
            var levels = new LOD[4];
            for (var level = 0; level < 4; level++) {
                var layer = Child(root, "LOD" + level);
                var renderers = new List<Renderer>();
                var body = Child(layer, "Body");
                var ring = Child(layer, "RingPivot");
                ring.transform.localPosition = new Vector3(0, 3.075f, 0);
                var chevrons = new GameObject[9];
                var vParts = new GameObject[9];
                var rearVParts = new GameObject[9];
                for (var i = 0; i < 9; i++) {
                    chevrons[i] = Child(layer, "Chevron_" + i);
                    chevrons[i].transform.localPosition = new Vector3(0, 3.073437f, 0);
                    chevrons[i].transform.localRotation = Quaternion.Euler(0, 0, i * 40);
                    vParts[i] = Child(chevrons[i], "V");
                    rearVParts[i] = Child(chevrons[i], "RearV");
                }
                foreach (var entry in manifest.lods[level].entries) {
                    var file = Folder + "/LOD" + level + "/" + entry.file;
                    var imported = AssetDatabase.LoadAssetAtPath<GameObject>(file);
                    var source = imported == null ? null : imported.GetComponentInChildren<MeshFilter>();
                    if (source == null || source.sharedMesh == null || source.sharedMesh.triangles.Length / 3 != entry.triangles)
                        throw new InvalidOperationException("Missing or changed mesh: " + file);
                    if (entry.part == "body") Add(body, entry, source.sharedMesh, renderers);
                    else if (entry.part == "ring") Add(ring, entry, source.sharedMesh, renderers);
                    else if (entry.part == "chevron-v" || entry.part == "chevron-block") {
                        for (var i = 0; i < 9; i++) {
                            var parent = entry.part == "chevron-v" ? vParts[i] : chevrons[i];
                            Add(parent, entry, source.sharedMesh, renderers);
                            if (entry.part == "chevron-v")
                                Add(rearVParts[i], entry, MirrorRear(source.sharedMesh, level, entry.file), renderers);
                        }
                    } else throw new InvalidOperationException("Unknown gate part: " + entry.part);
                }
                levels[level] = new LOD(Thresholds[level], renderers.ToArray());
            }
            AddLights(root);
            AddCollision(root);
            var group = root.AddComponent<LODGroup>();
            group.SetLODs(levels);
            group.fadeMode = LODFadeMode.None;
            group.RecalculateBounds();
            for (var level = 0; level < 4; level++) {
                RequirePath(root, "LOD" + level + "/Body/body-00");
                RequirePath(root, "LOD" + level + "/RingPivot");
                for (var part = 0; part < 3; part++)
                    RequirePath(root, "LOD" + level + "/RingPivot/ring-0" + part);
                for (var i = 0; i < 9; i++) {
                    var prefix = "LOD" + level + "/Chevron_" + i + "/";
                    RequirePath(root, prefix + "V/chevron-v-00/VStripDark");
                    RequirePath(root, prefix + "RearV/chevron-v-00/VStripDark");
                    RequirePath(root, prefix + "chevron-block-00/FixedLightDark");
                }
            }
            for (var i = 0; i < 9; i++) {
                RequirePath(root, "Lights/Chevron_" + i + "/AmberFront");
                RequirePath(root, "Lights/Chevron_" + i + "/AmberStripFront");
            }
            RequirePath(root, "Collision/Segment_00");
            var prefab = PrefabUtility.SaveAsPrefabAsset(root, Folder + "/StargateMilkyway.prefab");
            if (prefab == null) throw new InvalidOperationException("Cannot save Stargate prefab");
            AssetDatabase.SaveAssets();
            Debug.Log("SG_BUNDLE_PREFAB_READY " + Folder + "/StargateMilkyway.prefab");
        } finally { UnityEngine.Object.DestroyImmediate(root); }
    }

    private static void Add(GameObject parent, Entry entry, Mesh mesh, List<Renderer> renderers) {
        var role = Role(entry.part, entry.material);
        var name = Path.GetFileNameWithoutExtension(entry.file);
        if (role == "VStrip" || role == "FixedLight") {
            var holder = Child(parent, name);
            foreach (var state in new[] { "Dark", "Amber", "Open" }) {
                var variant = Child(holder, role + state);
                variant.SetActive(state == "Dark");
                var renderer = Attach(variant, mesh, Materials[state]);
                renderers.Add(renderer);
            }
        } else {
            var part = Child(parent, name);
            renderers.Add(Attach(part, mesh, Materials[role]));
        }
    }

    private static string Role(string part, string sourceMaterial) {
        if (part == "chevron-v") return sourceMaterial == "Chevron Light" ? "VStrip" : "Housing";
        if (part == "chevron-block") return sourceMaterial == "Chevron Block Light" ? "FixedLight" : "Housing";
        if (sourceMaterial == "Inner Ring Glyph Face") return "Glyph";
        if (sourceMaterial.Contains("Groove") || sourceMaterial.Contains("Recess")) return "Groove";
        return "Stone";
    }

    private static MeshRenderer Attach(GameObject obj, Mesh mesh, Material material) {
        obj.AddComponent<MeshFilter>().sharedMesh = mesh;
        var renderer = obj.AddComponent<MeshRenderer>();
        renderer.sharedMaterial = material;
        renderer.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.On;
        return renderer;
    }

    private static void CreateMaterials() {
        var shader = Shader.Find("HDRP/Lit");
        if (shader == null) throw new InvalidOperationException("HDRP/Lit shader unavailable; create a High Definition 3D project");
        var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(Folder + "/gate-subtle-color.png");
        if (texture == null) throw new InvalidOperationException("Missing accepted gate stone texture");
        Make("Stone", shader, new Color(.85f, .85f, .85f), .08f, .18f, texture);
        Make("Housing", shader, new Color(.82f, .82f, .82f), .12f, .22f, texture);
        Make("Groove", shader, new Color(.54f, .54f, .54f), .08f, .12f, texture);
        Make("Glyph", shader, new Color(.56f, .57f, .58f), .08f, .12f, null);
        Make("Dark", shader, new Color(.12f, .035f, .025f), .04f, .22f, null);
        Make("Amber", shader, new Color(1f, .28f, .02f), .04f, .35f, null, new Color(2f, .65f, .05f));
        Make("Open", shader, new Color(1f, .55f, .12f), .04f, .35f, null, new Color(3f, 1.2f, .2f));
    }

    private static void Make(string name, Shader shader, Color color, float metallic, float smoothness,
            Texture texture, Color? emission = null) {
        var path = Folder + "/Mat" + name + ".mat";
        var material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (material == null) {
            material = new Material(shader);
            AssetDatabase.CreateAsset(material, path);
        }
        material.shader = shader;
        material.SetColor("_BaseColor", color);
        material.SetFloat("_Metallic", metallic);
        material.SetFloat("_Smoothness", smoothness);
        if (texture != null) material.SetTexture("_BaseColorMap", texture);
        if (emission.HasValue) {
            material.SetColor("_EmissiveColor", emission.Value);
            material.EnableKeyword("_EMISSION");
        }
        Materials[name] = material;
    }

    private static void AddLights(GameObject root) {
        var holder = Child(root, "Lights");
        for (var i = 0; i < 9; i++) {
            var chevron = Child(holder, "Chevron_" + i);
            chevron.transform.localPosition = new Vector3(0, 3.073437f, 0);
            chevron.transform.localRotation = Quaternion.Euler(0, 0, i * 40);
            AddLight(chevron, "AmberFront", new Vector3(0, 2.88f, -.3f), new Color(1f, .48f, .1f), 3.6f, 1.8f);
            AddLight(chevron, "AmberRear", new Vector3(0, 2.88f, .3f), new Color(1f, .48f, .1f), 3.6f, 1.8f);
            AddLight(chevron, "OpenFront", new Vector3(0, 2.88f, -.3f), new Color(1f, .78f, .39f), 3f, 1.8f);
            AddLight(chevron, "OpenRear", new Vector3(0, 2.88f, .3f), new Color(1f, .78f, .39f), 3f, 1.8f);
            AddLight(chevron, "AmberStripFront", new Vector3(0, 3.15f, -.23f), new Color(1f, .48f, .1f), 2.5f, .55f);
            AddLight(chevron, "AmberStripRear", new Vector3(0, 3.15f, .23f), new Color(1f, .48f, .1f), 2.5f, .55f);
            AddLight(chevron, "OpenStripFront", new Vector3(0, 3.15f, -.23f), new Color(1f, .78f, .39f), 2.5f, .55f);
            AddLight(chevron, "OpenStripRear", new Vector3(0, 3.15f, .23f), new Color(1f, .78f, .39f), 2.5f, .55f);
        }
    }

    private static void AddCollision(GameObject root) {
        // Stable annular collision belongs to the prefab itself. API children
        // attached to a loaded prefab did not block the player in Development.
        const int segments = 48;
        const float inner = 2.30f, outer = 3.075f, centreY = 3.075f;
        var holder = Child(root, "Collision");
        for (var i = 0; i < segments; i++) {
            var angle = i * Mathf.PI * 2f / segments;
            var segment = Child(holder, "Segment_" + i.ToString("00"));
            var radius = (inner + outer) / 2f;
            segment.transform.localPosition = new Vector3(Mathf.Cos(angle) * radius,
                centreY + Mathf.Sin(angle) * radius, 0f);
            segment.transform.localRotation = Quaternion.Euler(0f, 0f, angle * Mathf.Rad2Deg - 90f);
            var collider = segment.AddComponent<BoxCollider>();
            collider.size = new Vector3(2f * outer * Mathf.Tan(Mathf.PI / segments) + .01f,
                outer - inner, .48f);
            collider.isTrigger = false;
        }
    }

    private static Mesh MirrorRear(Mesh source, int level, string filename) {
        // The source FBX has only front Chevron V geometry. Mirror that part
        // across the gate plane so the rear housings have matching backs.
        var path = Folder + "/RearV-LOD" + level + "-" + Path.GetFileNameWithoutExtension(filename) + ".asset";
        var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(path);
        if (mesh == null) {
            mesh = new Mesh { name = "RearV" };
            AssetDatabase.CreateAsset(mesh, path);
        }
        mesh.Clear();
        var vertices = source.vertices;
        for (var i = 0; i < vertices.Length; i++) vertices[i].z = -vertices[i].z;
        var triangles = source.triangles;
        for (var i = 0; i < triangles.Length; i += 3) {
            var swap = triangles[i + 1];
            triangles[i + 1] = triangles[i + 2];
            triangles[i + 2] = swap;
        }
        mesh.vertices = vertices;
        mesh.uv = source.uv;
        mesh.triangles = triangles;
        mesh.RecalculateNormals();
        mesh.RecalculateBounds();
        EditorUtility.SetDirty(mesh);
        return mesh;
    }

    private static void AddLight(GameObject parent, string name, Vector3 position, Color color,
            float intensity, float range) {
        var obj = Child(parent, name);
        obj.transform.localPosition = position;
        var light = obj.AddComponent<Light>();
        light.type = LightType.Point;
        light.color = color;
        light.range = range;
        light.intensity = intensity;
        light.shadows = LightShadows.None;
        obj.SetActive(false);
    }

    private static GameObject Child(GameObject parent, string name) {
        var child = new GameObject(name);
        child.transform.SetParent(parent.transform, false);
        return child;
    }

    private static void RequirePath(GameObject root, string path) {
        if (root.transform.Find(path) == null)
            throw new InvalidOperationException("Runtime animation path absent: " + path);
    }
}
