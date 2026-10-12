using System;
using System.Linq;
using UnityEditor;
using UnityEngine;

// Run with -executeMethod StargateFbxInspector.Dump after importing SG_MilkyWay.fbx.
public static class StargateFbxInspector {
    public static void Dump() {
        const string path = "Assets/StargateSource/SG_MilkyWay.fbx";
        var root = AssetDatabase.LoadAssetAtPath<GameObject>(path);
        if (root == null) throw new InvalidOperationException("Missing source FBX: " + path);
        long total = 0;
        foreach (var filter in root.GetComponentsInChildren<MeshFilter>(true)) {
            var mesh = filter.sharedMesh;
            if (mesh == null) continue;
            long triangles = 0;
            for (var i = 0; i < mesh.subMeshCount; i++) triangles += mesh.GetIndexCount(i) / 3;
            total += triangles;
            var renderer = filter.GetComponent<MeshRenderer>();
            var materials = renderer == null ? "" : string.Join(",", renderer.sharedMaterials.Select(m => m == null ? "null" : m.name));
            Debug.Log($"SG_FBX_MESH path={Path(filter.transform, root.transform)} triangles={triangles} vertices={mesh.vertexCount} uv={mesh.uv.Length} localPosition={filter.transform.localPosition} localRotation={filter.transform.localEulerAngles} localScale={filter.transform.localScale} materials={materials}");
            if (filter.name == "SG_MW_Main" || filter.name == "SG_MW_InnerRing_Earth_Giza") {
                for (var i = 0; i < mesh.subMeshCount; i++) {
                    Debug.Log($"SG_FBX_SUBMESH path={filter.name} material={renderer.sharedMaterials[i].name} triangles={mesh.GetIndexCount(i) / 3}");
                }
            }
        }
        Debug.Log($"SG_FBX_TOTAL triangles={total}");
    }

    private static string Path(Transform child, Transform root) {
        var path = child.name;
        while (child != root && child.parent != null) {
            child = child.parent;
            if (child != root) path = child.name + "/" + path;
        }
        return path;
    }
}
