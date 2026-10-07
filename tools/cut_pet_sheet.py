#!/usr/bin/env python3
"""Cuts a 2x2 AI-pet pose sheet (design/pets/<pet>_poses.png) into four 512px transparent PNGs.
Unlike cut_icon_sheet.py: grey floor shadows become translucent black (clean on dark mode),
and all four poses share one scale and one baseline so swapping poses does not jump.
usage: python3 tools/cut_pet_sheet.py design/pets/laylo_poses.png design/pets/cut laylo_idle laylo_happy laylo_think laylo_sleep
"""
import sys, numpy as np
from PIL import Image
SIZE=512; INSET=10; BG=12; SH=70
def dil(m):
    o=m.copy(); o[1:]|=m[:-1]; o[:-1]|=m[1:]; o[:,1:]|=m[:,:-1]; o[:,:-1]|=m[:,1:]; return o
def flood(seed, allow):
    r=seed.copy()
    while True:
        n=dil(r)&allow
        if (n==r).all(): return r
        r=n
def cut(cell):
    a=np.asarray(cell.convert("RGB")).astype(np.float32)
    d=(255-a).max(2); sat=a.max(2)-a.min(2)
    light=d<BG
    seed=np.zeros_like(light); seed[0]=seed[-1]=True; seed[:,0]=seed[:,-1]=True
    reach=flood(seed&light, light)
    h=a.shape[0]; low=np.zeros_like(light); low[int(h*.45):]=True
    shadow=flood(reach, (reach|((sat<=10)&(d<SH)&low)))&~reach
    edge=dil(dil(reach|shadow))&~(reach|shadow)
    alpha=np.ones(d.shape,np.float32); rgb=a.copy()
    alpha[reach]=0
    # shadow: grey on white -> translucent black
    alpha[shadow]=np.clip(d[shadow]/255*1.6,0,.45); rgb[shadow]=0
    am=np.clip((d[edge]-3)/(30-3),0,1); alpha[edge]=np.maximum(am, alpha[edge]*0)
    m=np.maximum(alpha[edge],1e-3)[:,None]; rgb[edge]=np.clip((a[edge]-255*(1-m))/m,0,255)
    return np.dstack([rgb,alpha*255]).astype(np.uint8)
sheet=Image.open(sys.argv[1]); outdir=sys.argv[2]; names=sys.argv[3:]
W,H=sheet.size; cw,ch=W//2,H//2; arrs=[]
for i in range(4):
    r,c=divmod(i,2)
    arrs.append(cut(sheet.crop((c*cw+INSET,r*ch+INSET,(c+1)*cw-INSET,(r+1)*ch-INSET))))
boxes=[]
for x in arrs:
    ys,xs=np.where(x[...,3]>40); boxes.append((ys.min(),ys.max()+1,xs.min(),xs.max()+1))
side=int(max(max(b[1]-b[0],b[3]-b[2]) for b in boxes)*1.06)
for x,b,n in zip(arrs,boxes,names):
    im=Image.fromarray(x[b[0]:b[1],b[2]:b[3]],"RGBA")
    cv=Image.new("RGBA",(side,side),(0,0,0,0))
    cv.paste(im,((side-im.width)//2, side-im.height-int(side*.03)))  # common baseline
    cv.resize((SIZE,SIZE),Image.LANCZOS).save(f"{outdir}/{n}.png",optimize=True)
