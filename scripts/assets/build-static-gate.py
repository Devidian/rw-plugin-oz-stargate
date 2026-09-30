#!/usr/bin/env python3
"""Offline build of the CC0 Milky Way preview. Originals are never modified."""
import argparse
import hashlib
import json
import subprocess
import sys
from importlib.metadata import version
from pathlib import Path
from zipfile import ZipFile
import numpy as np
import pymeshlab
from triangulate_obj import read_obj

SOURCE_SHA = '3a976d4dfd234bc80024a11e3ade84710b4d917946decd9de6ec182bc7a5b641'
PARTS = [('SG_MW_Main', 120000), ('SG_MW_InnerRing_Earth_Giza', 22000),
         ('SG_MW_Chevron', 800), ('SG_MW_ChevronBlock', 800)]

def write_obj(path, parts):
    offset = 1
    with path.open('w') as out:
        out.write('# CC0 source: David Gian-Cursio, Stargate Milky Way 2021. Optimized static preview.\n')
        for name, points, triangles in parts:
            out.write(f'o {name}\n')
            for x,y,z in points: out.write(f'v {x:.6f} {y:.6f} {z:.6f}\n')
            for a,b,c in triangles+offset: out.write(f'f {a} {b} {c}\n')
            offset += len(points)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('archive',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--cache',type=Path,required=True)
    args=parser.parse_args()
    with args.archive.open('rb') as source:
        digest=hashlib.file_digest(source,'sha256').hexdigest()
    if digest!=SOURCE_SHA: raise ValueError('Unexpected source archive; review source before rebuilding')
    args.output.mkdir(parents=True,exist_ok=True);args.cache.mkdir(parents=True,exist_ok=True)
    pieces=[];metrics=[]
    with ZipFile(args.archive) as archive:
        for name,target in PARTS:
            print('Processing',name,flush=True)
            cached=args.cache/(name+'.npz')
            if cached.exists():
                with np.load(cached) as saved:
                    points=saved['points'];triangles=saved['triangles'];original=int(saved['original'])
            else:
                with archive.open('SG_MW_OBJ/'+name+'.obj') as stream:
                    points,triangles,_=read_obj(stream)
                original=len(triangles)
                print('Triangulated',original,flush=True)
                meshes=pymeshlab.MeshSet()
                meshes.add_mesh(pymeshlab.Mesh(vertex_matrix=points,face_matrix=triangles))
                meshes.meshing_remove_duplicate_vertices();meshes.meshing_remove_duplicate_faces();meshes.meshing_remove_null_faces()
                meshes.meshing_decimation_quadric_edge_collapse(targetfacenum=target,preserveboundary=(name != "SG_MW_Main"),
                    preservenormal=True,optimalplacement=False,planarquadric=True,qualitythr=0.3)
                mesh=meshes.current_mesh();points=mesh.vertex_matrix();triangles=mesh.face_matrix()
                np.savez_compressed(cached,points=points,triangles=triangles,original=original)
            if not np.isfinite(points).all() or triangles.min()<0 or triangles.max()>=len(points):
                raise ValueError('Invalid mesh output')
            metrics.append(dict(part=name,source_triangles=original,requested_triangles=target,triangles=len(triangles),vertices=len(points)))
            pieces.append((name,points,triangles));print(metrics[-1],flush=True)
        notice=next(n for n in archive.namelist() if n.endswith('.txt') and not n.startswith('__MACOSX/'))
        notice_data=archive.read(notice)
    combined=[]
    for index,(name,points,triangles) in enumerate(pieces):
        for instance in range(9 if index>=2 else 1):
            angle=np.deg2rad(instance*40)
            rotation=np.array([[np.cos(angle),-np.sin(angle),0],[np.sin(angle),np.cos(angle),0],[0,0,1]])
            combined.append((name+'_'+str(instance),points@rotation.T*0.9,triangles))
    floor=min(p[:,1].min() for _,p,_ in combined)
    combined=[(n,p-np.array([0,floor,0]),t) for n,p,t in combined]
    # Remove zero-area slivers after final decimal quantization, not just before export.
    cleaned=[];removed=0
    for name,p,t in combined:
        p=np.round(p,6);xyz=p[t]
        valid=np.linalg.norm(np.cross(xyz[:,1]-xyz[:,0],xyz[:,2]-xyz[:,0]),axis=1)>1e-12
        removed+=int(np.count_nonzero(~valid));cleaned.append((name,p,t[valid]))
    combined=cleaned
    points=np.concatenate([p for _,p,_ in combined]);count=sum(len(t) for _,_,t in combined)
    if count>250000: raise ValueError('Triangle budget exceeded')
    path=args.output/'milkyway-preview.obj';write_obj(path,combined)
    if path.stat().st_size>10*1024*1024: raise ValueError('Payload budget exceeded')
    with path.open('rb') as source: output_hash=hashlib.file_digest(source,'sha256').hexdigest()
    report=dict(removed_degenerate_triangles=removed,source_sha256=digest,tool='pymeshlab '+version('pymeshlab'),parts=metrics,triangles=count,
        bounds_min=points.min(axis=0).tolist(),bounds_max=points.max(axis=0).tolist(),bytes=path.stat().st_size,
        obj_sha256=output_hash)
    (args.output/'SOURCE-NOTICE.txt').write_bytes(notice_data)
    (args.output/'build-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report),flush=True)
    subprocess.run([sys.executable,str(Path(__file__).with_name("verify-static-gate.py")),str(args.output)],check=True)

if __name__=='__main__': main()
