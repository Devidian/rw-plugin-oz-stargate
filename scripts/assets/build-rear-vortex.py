#!/usr/bin/env python3
"""Small rear (+Z) return vortex, with twisted UVs and curved off-centre tip. Stdlib only."""
import hashlib,json,math,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'src/assets/models/milkyway'
sectors=64;rings=16
points=[(.12*math.cos(3.8),.12*math.sin(3.8),1)];uv=[(.5,.5)];faces=[]
for ring in range(1,rings+1):
 r=ring/rings
 for j in range(sectors):
  a=math.tau*j/sectors
  twist=3.8*(1-r)**2
  radius=1.3*r*(1+.08*math.sin(3*a+twist)*(1-r))
  bend=.12*(1-r)**2
  points.append((radius*math.cos(a)+bend*math.cos(twist),radius*math.sin(a)+bend*math.sin(twist),(1-r)**1.3))
  uv.append((.5+.5*r*math.cos(a+twist),.5+.5*r*math.sin(a+twist)))
for j in range(sectors):faces.append((0,1+j,1+(j+1)%sectors))
for ring in range(1,rings):
 i=1+(ring-1)*sectors;o=i+sectors
 for j in range(sectors):
  k=(j+1)%sectors
  faces.extend([(i+j,o+k,i+k),(i+j,o+j,o+k)])
for f in faces:
 a,b,c=[points[i] for i in f];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
 assert sum(q*q for q in (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]))>1e-16
assert min(p[2] for p in points)==0 and max(p[2] for p in points)==1
lines=['# Generated rear return vortex; +Z behind gate','o wormhole-rear-vortex']
lines+=['v %.7f %.7f %.7f'%p for p in points]
lines+=['vt %.7f %.7f'%p for p in uv]
lines+=['f '+' '.join(f'{i+1}/{i+1}' for i in face) for face in faces]
data=('\n'.join(lines)+'\n').encode()
report=json.dumps({'vertices':len(points),'triangles':len(faces),'radius':1.3,'direction':'+Z','sha256':hashlib.sha256(data).hexdigest()},indent=2)+'\n'
for name,content in [('articulated-wormhole-rear-vortex.obj',data),('rear-vortex-report.json',report.encode())]:
 if '--check' in sys.argv:assert (root/name).read_bytes()==content,name
 else:(root/name).write_bytes(content)
print(report)
