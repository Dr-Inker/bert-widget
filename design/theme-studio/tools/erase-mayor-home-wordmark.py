"""Erase the BERT wordmark from the Mayor Purple home master (841x1870) by rebuilding gradient + halftone."""
import sys, colorsys
import numpy as np
from PIL import Image, ImageFilter
src_path, out_path = sys.argv[1], sys.argv[2]
im = Image.open(src_path).convert('RGB'); a = np.asarray(im).astype(np.float64); H, W, _ = a.shape
BOX = (180, 1550, 548, 1800)  # x0, y0, x1, y1 around the wordmark (source px)

def gauss1d(s):
    r = int(3*s)+1; x = np.arange(-r, r+1); k = np.exp(-x*x/(2*s*s)); return k/k.sum()
def blur(img, s):
    k = gauss1d(s); out = img.copy()
    for ax in (0, 1):
        out = np.apply_along_axis(lambda v: np.convolve(np.pad(v, len(k)//2, mode='reflect'), k, 'valid'), ax, out)
    return out

lum = a.mean(axis=2)
r, g, b = (a[..., i]/255 for i in range(3))
hue = np.vectorize(lambda R, G, B: colorsys.rgb_to_hsv(R, G, B)[0]*360)(r, g, b)
purpleish = (hue > 235) & (hue < 305)
white = lum > 140
sat = (a.max(axis=2) - a.min(axis=2))/np.maximum(a.max(axis=2), 1)
# anti-aliased letter edges against Bert's black outline: light, unsaturated greys (his outline is near-black)
white = white | ((sat < 0.15) & (lum > 80))
inbox = np.zeros((H, W), bool); inbox[BOX[1]:BOX[3], BOX[0]:BOX[2]] = True
core = white & inbox
coreimg = Image.fromarray((core*255).astype(np.uint8))
dil = np.asarray(coreimg.filter(ImageFilter.MaxFilter(25))) > 0  # ~12px
# the letters' soft drop shadow falls below and to either side: sweep shifted copies
shadow = np.zeros((H, W), bool)
for dy in range(0, 29, 4):
    for dx in range(-12, 13, 6):
        shadow |= np.asarray(coreimg.transform((W, H), Image.AFFINE, (1, 0, -dx, 0, 1, -dy))) > 0
shadow = np.asarray(Image.fromarray((shadow*255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))) > 0
M = (dil | shadow) & inbox & (purpleish | white)
print('mask px', int(M.sum()))

# 1) smooth background by normalized convolution over unmasked pixels
S = 16
def background(M):
    # multi-scale normalized convolution: fine where enough samples are near, wider where the hole is deep
    w = ((~M) & purpleish).astype(float)
    out = None
    for s in (120, 60, 30, S):
        num = np.stack([blur(a[..., c]*w, s) for c in range(3)], -1); den = blur(w, s)
        est = num/np.maximum(den, 1e-9)[..., None]
        if out is None: out = est; continue
        conf = np.clip((den - 0.15)/0.25, 0, 1)[..., None]  # trust the finer scale only with enough coverage
        out = est*conf + out*(1-conf)
    return out
Bg = background(M)
# second pass: anything in the box still clearly darker than the expected background is leftover shadow
smooth = np.stack([blur(a[..., c], 3.0) for c in range(3)], -1)
dark = inbox & purpleish & ~M & ((Bg.mean(axis=2) - smooth.mean(axis=2)) > 4)
dark = np.asarray(Image.fromarray((dark*255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))) > 0
M = M | (dark & inbox & purpleish)
print('mask px after shadow pass', int(M.sum()))
Bg = background(M)

# 2) halftone residual and its periodic tile (lattice period P on both axes)
hp = a - np.stack([blur(a[..., c], 3.0) for c in range(3)], -1)
ys, xs = np.mgrid[0:H, 0:W]
donor = (~M) & purpleish & (ys > 1450) & (xs < 560) & ~((xs > 520) & (ys > 1450) & ~purpleish)
dys, dxs = ys[donor], xs[donor]
def tile_for(P, nb=32):
    Px, Py = P
    bx = ((xs % Px)/Px*nb).astype(int) % nb; by = ((ys % Py)/Py*nb).astype(int) % nb
    t = np.zeros((nb, nb, 3)); n = np.zeros((nb, nb))
    np.add.at(t, (by[donor], bx[donor]), hp[donor]); np.add.at(n, (by[donor], bx[donor]), 1)
    return t/np.maximum(n, 1)[..., None], bx, by
def contrast(Px, Py, nb=16):
    bx = ((dxs % Px)/Px*nb).astype(int) % nb; by = ((dys % Py)/Py*nb).astype(int) % nb
    t = np.zeros((nb, nb, 3)); n = np.zeros((nb, nb))
    np.add.at(t, (by, bx), hp[donor]); np.add.at(n, (by, bx), 1)
    return np.abs(t/np.maximum(n, 1)[..., None]).mean()
# Lattice measured on this master (it was stretched vertically): picked by eye against untouched texture,
# a free search aliases to cross-shaped dots.
best = (None, 12.43, 13.45)
P = (best[1], best[2]); tile, bx, by = tile_for(P); print('period x/y', round(P[0], 3), round(P[1], 3), 'tile contrast', round(np.abs(tile).mean(), 3))
amp_local = blur(np.abs(hp).mean(axis=2)*donor, 30)/np.maximum(blur(donor.astype(float), 30), 1e-6)
tex = tile[by, bx] * (amp_local/np.abs(tile).mean())[..., None]

# 3) composite with a feathered mask
soft = np.asarray(Image.fromarray((M*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5))).astype(float)[..., None]/255
soft = np.maximum(soft, M[..., None].astype(float))  # masked pixels are always fully replaced; feather only outward
# never let the feather touch Bert: only purple background and the white letters may change
soft = soft * (purpleish | white)[..., None]
out = a*(1-soft) + (Bg+tex)*soft
Image.fromarray(np.clip(out, 0, 255).round().astype(np.uint8)).save(out_path)
Image.fromarray((M*255).astype(np.uint8)).save(out_path.replace('.png', '-mask.png'))
