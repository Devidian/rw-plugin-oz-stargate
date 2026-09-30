#!/usr/bin/env python3
"""Extract authored CC0 V/stripe groups, preserving source geometry/material boundaries.
Offline only: same numpy/mapbox-earcut environment as build-static-gate.py.
"""
import argparse,hashlib,json
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import numpy as np
from triangulate_obj import read_obj

parser=argparse.ArgumentParser()
parser.add_argument('archive',type=Path)
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
assert hashlib.sha256(args.archive.read_bytes()).hexdigest()=='3a976d4dfd234bc80024a11e3ade84710b4d917946decd9de6ec182bc7a5b641'
root=Path(__file__).resolve().parents[2]/'src/assets/models/milkyway'
report={}
with ZipFile(args.archive) as archive:
 for source,selections in [('SG_MW_Chevron',{'chevron-v-frame':['Chevron_A'],'chevron-v-strips':['Chevron_Light']}),
                           ('SG_MW_ChevronBlock',{'chevron-fixed-frame':['Chevron_Block_Arm'],'chevron-fixed-light':['Chevron_Block_Light']})]:
  lines=archive.read('SG_MW_OBJ/'+source+'.obj').splitlines(keepends=True)
  vertices=[l for l in lines if l.startswith(b'v ')]
  groups={};material=''
  for line in lines:
   if line.startswith(b'usemtl '):material=line.split()[1].decode()
   elif line.startswith(b'f '):groups.setdefault(material,[]).append(line)
  assert set(groups)=={g for selected in selections.values() for g in selected}
  for name,selected in selections.items():
   points,faces,_=read_obj(BytesIO(b''.join(vertices+[f for group in selected for f in groups[group]])))
   points=np.round(points*.9,6) # source centre origin; parent adds accepted floor offset 3.073437
   xyz=points[faces]
   faces=faces[np.linalg.norm(np.cross(xyz[:,1]-xyz[:,0],xyz[:,2]-xyz[:,0]),axis=1)>1e-12]
   indices=sorted(set(faces.ravel().tolist()));mapping={v:i+1 for i,v in enumerate(indices)}
   output=['# CC0 David Gian-Cursio 2021; original authored material surfaces.', 'o '+name]
   output+=['v %.6f %.6f %.6f'%tuple(points[i]) for i in indices]
   output+=['f '+' '.join(str(mapping[int(i)]) for i in face) for face in faces]
   data=('\n'.join(output)+'\n').encode();path=root/('articulated-'+name+'.obj')
   if args.check:assert path.read_bytes()==data,path
   else:path.write_bytes(data)
   report[name]={'materials':selected,'triangles':len(faces),'vertices':len(indices),'sha256':hashlib.sha256(data).hexdigest()}
report['active_triangles_per_gate']=119999+22000+9*sum(x['triangles'] for x in report.values())
assert report['active_triangles_per_gate']<250000
out=json.dumps(report,indent=2)+'\n'
if args.check:assert (root/'chevron-material-report.json').read_text()==out
else:(root/'chevron-material-report.json').write_text(out)
print(out)
