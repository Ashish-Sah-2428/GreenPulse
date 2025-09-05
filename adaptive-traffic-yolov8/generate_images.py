import os
import random
from pathlib import Path
import cv2
import numpy as np

BG_PATH = 'images/mod_int.png'
ICON_ROOT = 'images'
OUT_DIR = 'demo_frames'

LANES = ['up','right','down','left']
CLASSES = ['car','bus','truck','bike','rickshaw']

def main(n=100):
    os.makedirs(OUT_DIR, exist_ok=True)
    bg = cv2.imread(BG_PATH)
    if bg is None:
        raise SystemExit('Missing images/mod_int.png background')
    H, W = bg.shape[:2]

    for i in range(n):
        img = bg.copy()
        for lane in LANES:
            lane_dir = Path(ICON_ROOT)/lane
            if not lane_dir.exists():
                continue
            for _ in range(random.randint(1,4)):
                cls = random.choice(CLASSES)
                icon_p = lane_dir/f'{cls}.png'
                if not icon_p.exists():
                    continue
                icon = cv2.imread(str(icon_p), cv2.IMREAD_UNCHANGED)
                if icon is None:
                    continue
                scale = random.uniform(0.3, 0.6)
                h = int(icon.shape[0]*scale)
                w = int(icon.shape[1]*scale)
                icon = cv2.resize(icon, (w,h), interpolation=cv2.INTER_AREA)
                x = random.randint(0, W-w-1)
                y = random.randint(0, H-h-1)
                if icon.shape[2]==4:
                    a = icon[:,:,3]/255.0
                    for c in range(3):
                        img[y:y+h, x:x+w, c] = (a*icon[:,:,c] + (1-a)*img[y:y+h, x:x+w, c]).astype(np.uint8)
                else:
                    img[y:y+h, x:x+w] = icon

        cv2.imwrite(f'{OUT_DIR}/frame_{i:04d}.png', img)

if __name__ == '__main__':
    main(n=60)
