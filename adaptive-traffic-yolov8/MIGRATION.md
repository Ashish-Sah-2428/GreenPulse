# Migration: Darkflow/TensorFlow ➜ Ultralytics YOLOv8 (PyTorch) + OpenCV

- ✅ **Removed**: Any dependency on Darkflow / TensorFlow.
- ✅ **Detector**: Uses `ultralytics` YOLOv8 (PyTorch backend).
- ✅ **Vision**: OpenCV-only pipeline for I/O, drawing, UI.
- ✅ **Classes**: Cars, buses, trucks, bikes (rickshaw optionally with custom weights).
- ✅ **Timing Logic**: Adaptive per-lane green allocation using EMA-smooth counts.

## Run
```bash
pip install -r requirements.txt
python simulation.py --source 0
# or
python simulation.py --source path/to/video.mp4
```
