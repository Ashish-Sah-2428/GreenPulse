# Adaptive Traffic Controller (YOLOv8 + OpenCV)

Yeh repo aapke purane Darkflow/TensorFlow waale project ko **Ultralytics YOLOv8 + OpenCV** par migrate karta hai.

> ✅ Goals
> - Webcam/Video feed par vehicle detection (car/bus/truck/bike etc.).
> - 4-direction intersection (up / down / left / right) par **per-direction counts**.
> - Counts ke hisaab se **adaptive green time**.
> - UI overlay: current signal (red/yellow/green) + per-lane stats.

## Quick Start

```bash
# 1) Python 3.10+ recommended
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# 2) Install deps
pip install -r requirements.txt

# 3) Run simulation on webcam
python simulation.py --source 0

# or on a video file
python simulation.py --source path/to/traffic.mp4

# (optional) custom trained model
python simulation.py --model weights/best.pt
```

## Repo Layout

```
adaptive-traffic-yolov8/
├── images/
│   ├── mod_int.png
│   ├── signals/
│   │   ├── red.png
│   │   ├── yellow.png
│   │   └── green.png
│   ├── right/   # car.png, bus.png, truck.png, rickshaw.png, bike.png
│   ├── left/    # copy right/
│   ├── up/      # copy right/
│   └── down/    # copy right/
├── simulation.py
├── generate_images.py
├── requirements.txt
└── README.md
```

## Parameters

```bash
python simulation.py \  --source 0 \  --model yolov8n.pt \  --conf 0.25 \  --layout cross \  --cycle 60 \  --min-green 7 \  --max-green 30 \  --yellow 3 \  --smooth 0.6
```

- `--cycle`: total cycle length (sec)
- `--min-green` / `--max-green`: per-direction bounds
- `--yellow`: yellow duration (sec) between greens
- `--smooth`: EMA smoothing for counts (0..1)

## Notes
- COCO default model me `rickshaw` nahi hota; custom trained weights dena best hai.
- Purana Darkflow/TensorFlow URL milte hi classes/config ko map karke port kiya jaa sakta hai.
