using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

// Build after build-dhd-bundle-input.py has populated Assets/AssetBundles/StargateDhd.
public static class StargateDhdPrefabBuilder {
    [Serializable] private sealed class Manifest { public Lod[] lods; }
    [Serializable] private sealed class Lod { public int level, triangles; public Entry[] entries; }
    [Serializable] private sealed class Entry { public string part, file; public int triangles; }

    private const string Folder = "Assets/AssetBundles/StargateDhd";
    private static readonly Dictionary<string, Material> Materials = new Dictionary<string, Material>();
    private static readonly Vector3[] SymbolLights = {
        new Vector3(.3296f, .9652f, .0040f), new Vector3(-.3416f, .9251f, -.0572f),
        new Vector3(.0473f, .7598f, -.3429f), new Vector3(.0310f, 1.1043f, .2447f),
        new Vector3(-.0987f, 1.0630f, .0908f), new Vector3(-.1585f, .9146f, -.1840f),
        new Vector3(.1645f, .9376f, -.1506f)
    };

    public static void Build() {
        if (Application.unityVersion != "6000.3.22f1")
            throw new InvalidOperationException("DHD bundle requires Unity 6000.3.22f1");
        var manifest = JsonUtility.FromJson<Manifest>(File.ReadAllText(Folder + "/manifest.json"));
        if (manifest == null || manifest.lods == null || manifest.lods.Length != 2)
            throw new InvalidOperationException("Expected full and reduced DHD LODs");
        var root = new GameObject("StargateDhd");
        try {
            CreateMaterials();
            var lods = new LOD[2];
            foreach (var level in manifest.lods) {
                if (level.level < 0 || level.level > 1 || level.entries == null || level.entries.Length != 19)
                    throw new InvalidOperationException("Invalid DHD LOD manifest");
                var holder = Child(root, "LOD" + level.level);
                var renderers = new List<Renderer>();
                var triangles = 0;
                foreach (var entry in level.entries) {
                    var path = Folder + "/LOD" + level.level + "/" + entry.file;
                    var imported = AssetDatabase.LoadAssetAtPath<GameObject>(path);
                    var filter = imported == null ? null : imported.GetComponentInChildren<MeshFilter>();
                    if (filter == null || filter.sharedMesh == null ||
                            filter.sharedMesh.triangles.Length / 3 != entry.triangles)
                        throw new InvalidOperationException("Missing or changed DHD mesh: " + path);
                    var piece = Child(holder, entry.part.Replace('-', '_'));
                    piece.AddComponent<MeshFilter>().sharedMesh = filter.sharedMesh;
                    var renderer = piece.AddComponent<MeshRenderer>();
                    renderer.sharedMaterial = Materials[MaterialRole(entry.part)];
                    renderers.Add(renderer);
                    triangles += entry.triangles;
                }
                if (triangles != level.triangles)
                    throw new InvalidOperationException("DHD LOD triangle total changed");
                lods[level.level] = new LOD(level.level == 0 ? .22f : .005f, renderers.ToArray());
            }
            var group = root.AddComponent<LODGroup>();
            group.SetLODs(lods);
            group.fadeMode = LODFadeMode.None;
            group.RecalculateBounds();
            AddCollision(root);
            AddLights(root);
            for (var i = 0; i < 7; i++) {
                Require(root, "LOD0/keys_" + i);
                Require(root, "LOD1/symbols_" + i);
                Require(root, "Lights/Symbol_" + i);
            }
            Require(root, "Collision/Column");
            Require(root, "Lights/Activation");
            var prefab = PrefabUtility.SaveAsPrefabAsset(root, Folder + "/StargateDhd.prefab");
            if (prefab == null) throw new InvalidOperationException("Cannot save DHD prefab");
            AssetDatabase.SaveAssets();
            Debug.Log("SG_DHD_PREFAB_READY triangles=" + manifest.lods[0].triangles + "/" +
                manifest.lods[1].triangles);
        } finally { UnityEngine.Object.DestroyImmediate(root); }
    }

    private static string MaterialRole(string part) {
        if (part == "body") return "Stone";
        if (part == "accents") return "Accents";
        if (part.StartsWith("keys")) return "Keys";
        if (part.StartsWith("symbols")) return "Symbols";
        return "Activation";
    }

    private static void CreateMaterials() {
        var shader = Shader.Find("HDRP/Lit");
        if (shader == null) throw new InvalidOperationException("HDRP/Lit unavailable");
        var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(Folder + "/gate-subtle-color.png");
        if (texture == null) throw new InvalidOperationException("DHD stone texture missing");
        MaterialFor("Stone", shader, new Color(.74f, .76f, .78f), texture);
        MaterialFor("Accents", shader, new Color(.58f, .59f, .60f), texture);
        MaterialFor("Keys", shader, new Color(.51f, .49f, .44f), null);
        MaterialFor("Symbols", shader, new Color(.66f, .64f, .59f), null);
        MaterialFor("Activation", shader, new Color(.68f, .13f, .06f), null);
    }

    private static void MaterialFor(string name, Shader shader, Color color, Texture texture) {
        var path = Folder + "/Mat" + name + ".mat";
        var material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (material == null) {
            material = new Material(shader);
            AssetDatabase.CreateAsset(material, path);
        }
        material.shader = shader;
        material.SetColor("_BaseColor", color);
        if (texture != null) material.SetTexture("_BaseColorMap", texture);
        Materials[name] = material;
    }

    private static void AddLights(GameObject root) {
        var holder = Child(root, "Lights");
        // Client bloom remained clipped at intensity 0.0025; scale RGB as well.
        for (var i = 0; i < SymbolLights.Length; i++)
            Light(holder, "Symbol_" + i, SymbolLights[i], new Color(.03f, .0189f, .0057f),
                .0025f, .4f);
        Light(holder, "Activation", new Vector3(0f, 1.22f, 0f),
            new Color(.03f, .0036f, .0012f), .00125f, 1.2f);
    }

    private static void Light(GameObject parent, string name, Vector3 position,
            Color color, float intensity, float range) {
        var piece = Child(parent, name);
        piece.transform.localPosition = position;
        var light = piece.AddComponent<Light>();
        light.type = LightType.Point;
        light.color = color;
        light.intensity = intensity;
        light.range = range;
        light.shadows = LightShadows.None;
        piece.SetActive(false);
    }

    private static void AddCollision(GameObject root) {
        var holder = Child(root, "Collision");
        Box(holder, "Column", new Vector3(0f, .43f, 0f), new Vector3(.62f, .86f, .62f));
        Disk(holder, "Lower", .20f, .47f, .40f);
        Disk(holder, "Middle", .52f, .66f, .38f);
        Disk(holder, "Top", .87f, .76f, .55f);
    }

    private static void Disk(GameObject parent, string name, float height,
            float radius, float thickness) {
        const int strips = 12;
        var depth = radius * 2f / strips;
        for (var i = 0; i < strips; i++) {
            var low = -radius + i * depth;
            var high = low + depth;
            var nearest = low <= 0f && high >= 0f ? 0f : Mathf.Min(Mathf.Abs(low), Mathf.Abs(high));
            var width = 2f * Mathf.Sqrt(radius * radius - nearest * nearest);
            Box(parent, name + "_" + i, new Vector3(0f, height, (low + high) / 2f),
                new Vector3(width, thickness, depth));
        }
    }

    private static void Box(GameObject parent, string name, Vector3 position, Vector3 size) {
        var piece = Child(parent, name);
        piece.transform.localPosition = position;
        piece.AddComponent<BoxCollider>().size = size;
    }

    private static GameObject Child(GameObject parent, string name) {
        var piece = new GameObject(name);
        piece.transform.SetParent(parent.transform, false);
        return piece;
    }

    private static void Require(GameObject root, string path) {
        if (root.transform.Find(path) == null) throw new InvalidOperationException("Missing " + path);
    }
}
