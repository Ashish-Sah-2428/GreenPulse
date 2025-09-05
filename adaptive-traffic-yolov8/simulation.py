import argparse
import time
from typing import Dict, List
import cv2
import numpy as np
from ultralytics import YOLO

COCO_TO_CANON = {
    'car': 'car',
    'bus': 'bus',
    'truck': 'truck',
    'motorcycle': 'bike',
    'bicycle': 'bike',
    'person': 'person',
}
CANON_TO_DISPLAY = {
    'car': 'Car',
    'bus': 'Bus',
    'truck': 'Truck',
    'bike': 'Bike',
    'rickshaw': 'Rickshaw',
}
LANES = ['up', 'right', 'down', 'left']
ROI_POLYGONS_PRESETS = {
    'cross': {
        'up':    np.array([[0.35,0.0],[0.65,0.0],[0.55,0.45],[0.45,0.45]], dtype=np.float32),
        'right': np.array([[1.0,0.35],[1.0,0.65],[0.55,0.55],[0.55,0.45]], dtype=np.float32),
        'down':  np.array([[0.35,1.0],[0.65,1.0],[0.55,0.55],[0.45,0.55]], dtype=np.float32),
        'left':  np.array([[0.0,0.35],[0.0,0.65],[0.45,0.55],[0.45,0.45]], dtype=np.float32),
    }
}
SIGNAL_IMAGES = {}

def load_signal_images():
    global SIGNAL_IMAGES
    for c in ['red','yellow','green']:
        img = cv2.imread(f'images/signals/{c}.png', cv2.IMREAD_UNCHANGED)
        SIGNAL_IMAGES[c] = img

def put_text(img, text, org, scale=0.7, thickness=2):
    cv2.putText(img, text, org, cv2.FONT_HERSHEY_SIMPLEX, scale, (0,0,0), thickness+2, cv2.LINE_AA)
    cv2.putText(img, text, org, cv2.FONT_HERSHEY_SIMPLEX, scale, (255,255,255), thickness, cv2.LINE_AA)

def center_of(box):
    x1,y1,x2,y2 = box
    return (int((x1+x2)/2), int((y1+y2)/2))

def point_in_poly(pt, poly):
    return cv2.pointPolygonTest(poly, pt, False) >= 0

def ema(prev, new, alpha):
    return alpha*new + (1-alpha)*prev

class PhaseController:
    def __init__(self, lanes: List[str], cycle=60, min_green=7, max_green=30, yellow=3, smooth=0.6):
        self.lanes = lanes
        self.cycle = cycle
        self.min_green = min_green
        self.max_green = max_green
        self.yellow = yellow
        self.smooth = smooth
        self.counts_ema = {l: 0.0 for l in lanes}
        self.phase = lanes[0]
        self.state = 'GREEN'
        self.t0 = time.time()

    def update_counts(self, raw_counts: Dict[str, int]):
        for l in self.lanes:
            self.counts_ema[l] = ema(self.counts_ema[l], raw_counts.get(l,0), self.smooth)

    def green_allocation(self) -> Dict[str, float]:
        counts = np.array([max(0.1, self.counts_ema[l]) for l in self.lanes], dtype=float)
        total = counts.sum()
        if total <= 0:
            return {l: self.min_green for l in self.lanes}
        secs = counts/total * (self.cycle - len(self.lanes)*self.yellow)
        secs = np.clip(secs, self.min_green, self.max_green)
        return {l: float(s) for l,s in zip(self.lanes, secs)}

    def policy_next_phase(self, alloc: Dict[str,float]):
        now = time.time()
        elapsed = now - self.t0
        if self.state == 'GREEN':
            if elapsed >= alloc[self.phase]:
                self.state = 'YELLOW'
                self.t0 = now
        elif self.state == 'YELLOW':
            if elapsed >= self.yellow:
                i = (self.lanes.index(self.phase) + 1) % len(self.lanes)
                self.phase = self.lanes[i]
                self.state = 'GREEN'
                self.t0 = now

    def get_signal_for(self, lane: str) -> str:
        if self.state == 'YELLOW' and lane == self.phase:
            return 'yellow'
        if self.state == 'GREEN' and lane == self.phase:
            return 'green'
        return 'red'

    def time_left(self, alloc: Dict[str,float]) -> float:
        now = time.time()
        elapsed = now - self.t0
        if self.state == 'GREEN':
            return max(0.0, alloc[self.phase] - elapsed)
        else:
            return max(0.0, self.yellow - elapsed)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=str, default='0', help='0 for webcam or path to video')
    parser.add_argument('--model', type=str, default='yolov8n.pt')
    parser.add_argument('--conf', type=float, default=0.25)
    parser.add_argument('--layout', type=str, default='cross', choices=list(ROI_POLYGONS_PRESETS.keys()))
    parser.add_argument('--cycle', type=int, default=60)
    parser.add_argument('--min-green', type=int, default=7)
    parser.add_argument('--max-green', type=int, default=30)
    parser.add_argument('--yellow', type=int, default=3)
    parser.add_argument('--smooth', type=float, default=0.6)
    args = parser.parse_args()

    src = int(args.source) if str(args.source).isdigit() else args.source
    model = YOLO(args.model)

    cap = cv2.VideoCapture(src)
    if not cap.isOpened():
        raise RuntimeError(f'Cannot open source: {args.source}')
    ret, frame = cap.read()
    if not ret:
        raise RuntimeError('Could not read first frame')

    H, W = frame.shape[:2]
    polys_norm = ROI_POLYGONS_PRESETS[args.layout]
    polys_px = {k: (v * np.array([W,H], dtype=np.float32)).astype(np.int32) for k,v in polys_norm.items()}

    controller = PhaseController(LANES, cycle=args.cycle, min_green=args.min_green,
                                 max_green=args.max_green, yellow=args.yellow, smooth=args.smooth)

    load_signal_images()
    t_prev = time.time()
    fps = 0.0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        results = model.predict(source=frame, conf=args.conf, verbose=False)
        det = results[0]

        raw_counts = {l: 0 for l in LANES}
        if det.boxes is not None and len(det.boxes) > 0:
            for b, cls_id in zip(det.boxes.xyxy.cpu().numpy(), det.boxes.cls.cpu().numpy().astype(int)):
                x1, y1, x2, y2 = map(int, b)
                name = det.names.get(int(cls_id), str(cls_id))
                name = COCO_TO_CANON.get(name, None)
                if name is None:
                    continue
                cx, cy = center_of((x1,y1,x2,y2))
                lane_hit = None
                for lane, poly in polys_px.items():
                    if point_in_poly((cx,cy), poly):
                        lane_hit = lane
                        break
                if lane_hit:
                    raw_counts[lane_hit] += 1
                cv2.rectangle(frame, (x1,y1), (x2,y2), (0,255,0), 2)
                cv2.circle(frame, (cx,cy), 3, (255,255,255), -1)

        controller.update_counts(raw_counts)
        alloc = controller.green_allocation()
        controller.policy_next_phase(alloc)

        overlay = frame.copy()
        for lane, poly in polys_px.items():
            color = (0,255,0) if controller.get_signal_for(lane)=='green' else (0,0,255)
            cv2.polylines(overlay, [poly], True, color, 2)
        frame = overlay

        panel_w = 280
        panel = np.zeros((H, panel_w, 3), dtype=np.uint8)
        put_text(panel, 'Adaptive Traffic', (10, 30), 0.9, 2)
        y = 70
        for lane in LANES:
            sig = controller.get_signal_for(lane)
            put_text(panel, f'{lane.upper():5}  {sig:>6}  cnt≈{controller.counts_ema[lane]:4.1f}', (10, y))
            y += 28
        y += 10
        put_text(panel, f'PHASE: {controller.phase.upper()}  {controller.state}', (10, y)); y+=28
        put_text(panel, f'Time Left: {controller.time_left(alloc):.1f}s', (10, y)); y+=28
        for lane in LANES:
            put_text(panel, f'Alloc {lane[:1].upper()}: {alloc[lane]:.1f}s', (10, y)); y+=24

        sig = controller.get_signal_for(controller.phase)
        icon = SIGNAL_IMAGES.get(sig)
        if icon is not None:
            scale = 0.4
            ih = int(icon.shape[0]*scale)
            iw = int(icon.shape[1]*scale)
            icon_rs = cv2.resize(icon, (iw, ih), interpolation=cv2.INTER_AREA)
            x0, y0 = panel_w - iw - 10, 10
            if icon_rs.shape[2]==4:
                alpha = icon_rs[:,:,3]/255.0
                for c in range(3):
                    panel[y0:y0+ih, x0:x0+iw, c] = (alpha*icon_rs[:,:,c] + (1-alpha)*panel[y0:y0+ih, x0:x0+iw, c]).astype(np.uint8)
            else:
                panel[y0:y0+ih, x0:x0+iw] = icon_rs

        frame_out = np.hstack([frame, panel])

        t = time.time()
        fps = ema(fps, 1.0/max(1e-6, t - t_prev), 0.9)
        t_prev = t
        put_text(frame_out, f'FPS: {fps:4.1f}', (10, 25))

        cv2.imshow('Adaptive Traffic – YOLOv8', frame_out)
        if cv2.waitKey(1) & 0xFF == 27:
            break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == '__main__':
    main()
