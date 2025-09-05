
import time
import cv2
from ultralytics import YOLO
import numpy as np
from filterpy.kalman import KalmanFilter
import firebase_admin
from firebase_admin import credentials, db
# --------------------------
# CONFIGURE FIREBASE
# --------------------------
cred = credentials.Certificate(
    "C:\\Users\\Asus\\Real-Time-Vehicle-Detection-and-Traffic-Flow-Classification-System-\\mapapp-55fe3-firebase-adminsdk-fbsvc-5f4b04b458.json"
)
firebase_admin.initialize_app(cred, {
    "databaseURL": "https://mapapp-55fe3-default-rtdb.firebaseio.com/"
})
firebase_ref = db.reference("trafficLights")
# --------------------------
# List of traffic lights with lat/lng
# --------------------------
traffic_light_coords = [
    {"lat":28.7032558,"lng":77.4405371},  # light1
    {"lat":28.7032023,"lng":77.4406653},  # light2
    {"lat":28.7031664,"lng":77.440618},   # light3
    {"lat":28.7032834,"lng":77.4406605},  # light4
    {"lat":28.7005,"lng":77.4405}         # light5 (example incomplete)
]
# --------------------------
# Config
# --------------------------
# video_path = "Cars Moving On Road Stock Footage - Free Download.mp4"
video_path = "videoplayback.mp4"

weights_path = "models/best.pt"
output_path = "multi_lane_traffic.mp4"
PIXELS_PER_METER = 15
H_REF = 200  # reference vehicle height for perspective
LANES = len(traffic_light_coords)  # one lane per light
lane_bounds = [(0,640),(640,1280),(1280,1920)]  # adjust lane boundaries if needed
DIST_THRESHOLD = 60
MAX_MISSED = 5
# --------------------------
# Load YOLO
# --------------------------
model = YOLO(weights_path)
cap = cv2.VideoCapture(video_path)
frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
fps = cap.get(cv2.CAP_PROP_FPS) or 20.0
fourcc = cv2.VideoWriter_fourcc(*'mp4v')
out = cv2.VideoWriter(output_path, fourcc, fps, (frame_width, frame_height))
# --------------------------
# Vehicle tracking
# --------------------------
vehicle_id_counter = 0
tracked_vehicles = []
def dist(c1, c2):
    return np.sqrt((c1[0]-c2[0])**2 + (c1[1]-c2[1])**2)
def create_kalman(cx, cy):
    kf = KalmanFilter(dim_x=4, dim_z=2)
    kf.x = np.array([cx, cy, 0, 0])
    kf.F = np.array([[1,0,1,0],[0,1,0,1],[0,0,1,0],[0,0,0,1]])
    kf.H = np.array([[1,0,0,0],[0,1,0,0]])
    kf.P *= 1000
    kf.R = np.array([[10,0],[0,10]])
    kf.Q = np.eye(4)*0.01
    return kf
# --------------------------
# Process video
# --------------------------
try:
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        results = model.predict(frame, verbose=False)
        current_centroids = []
        boxes = []
        for box in results[0].boxes.xyxy:
            x1,y1,x2,y2 = map(int, box)
            cx,cy = int((x1+x2)/2), int((y1+y2)/2)
            current_centroids.append((cx,cy))
            boxes.append((x1,y1,x2,y2))
        # Predict positions
        for vehicle in tracked_vehicles:
            vehicle["kf"].predict()
            vehicle["centroid_prev"] = vehicle["centroid"]
            vehicle["centroid"] = (int(vehicle["kf"].x[0]), int(vehicle["kf"].x[1]))
        # Match detections
        new_tracked = []
        assigned_detections = set()
        for vehicle in tracked_vehicles:
            min_dist = float('inf')
            matched_idx = -1
            for i,c in enumerate(current_centroids):
                if i in assigned_detections:
                    continue
                d = dist(c, vehicle["centroid"])
                if d<min_dist:
                    min_dist=d
                    matched_idx=i
            if matched_idx!=-1 and min_dist<DIST_THRESHOLD:
                vehicle["kf"].update(np.array(current_centroids[matched_idx]))
                vehicle["centroid"] = current_centroids[matched_idx]
                vehicle["bbox"] = boxes[matched_idx]
                vehicle["missed"]=0
                assigned_detections.add(matched_idx)
            else:
                vehicle["missed"] +=1
            if vehicle["missed"] <= MAX_MISSED:
                new_tracked.append(vehicle)
        # Add new vehicles
        for i,c in enumerate(current_centroids):
            if i not in assigned_detections:
                vehicle_id_counter+=1
                kf = create_kalman(c[0],c[1])
                new_tracked.append({"id":vehicle_id_counter,"kf":kf,"centroid":c,
                                    "centroid_prev":c,"missed":0,"bbox":boxes[i]})
        tracked_vehicles = new_tracked
        # --------------------------
        # Lane-wise metrics
        # --------------------------
        lane_metrics = []
        for idx,(lx1,lx2) in enumerate(lane_bounds):
            lane_area = (lx2-lx1)*frame_height
            total_vehicle_area = 0
            speeds_kmph = []
            for vehicle in tracked_vehicles:
                cx,_ = vehicle["centroid"]
                if lx1 <= cx < lx2:
                    dx = vehicle["centroid"][0]-vehicle["centroid_prev"][0]
                    dy = vehicle["centroid"][1]-vehicle["centroid_prev"][1]
                    speed_pixels = np.sqrt(dx**2+dy**2)
                    speed_m_s = (speed_pixels/PIXELS_PER_METER)*fps
                    speeds_kmph.append(speed_m_s*3.6)
                    if "bbox" in vehicle:
                        x1,y1,x2,y2 = vehicle["bbox"]
                    else:
                        cx,cy=vehicle["centroid"]
                        w,h=50,100
                        x1,y1,x2,y2 = cx-w//2,cy-h//2,cx+w//2,cy+h//2
                    H_box=y2-y1
                    scale = (H_REF/H_box)**2 if H_box>0 else 1
                    area=(x2-x1)*(y2-y1)*scale
                    total_vehicle_area += area
            occupancy = min(total_vehicle_area/lane_area*100,100)
            avg_speed = np.mean(speeds_kmph) if speeds_kmph else 0
            lane_metrics.append({"occupancy":occupancy,"avg_speed":avg_speed})
        # --------------------------
        # Traffic light logic based on occupancy
        # --------------------------
        max_occ = max([lm["occupancy"] for lm in lane_metrics])
        traffic_lights = []
        for lm in lane_metrics:
            if lm["occupancy"]>=max_occ:
                traffic_lights.append("GREEN")
            elif lm["occupancy"]>0.3*max_occ:
                traffic_lights.append("YELLOW")
            else:
                traffic_lights.append("RED")
        # --------------------------
        # Update Firebase for all lights
        # --------------------------
        lights = firebase_ref.get()
        for idx, coord in enumerate(traffic_light_coords):
            for light_id, light_data in lights.items():
                if light_data.get("lat") == coord["lat"] and light_data.get("lng") == coord["lng"]:
                    # Map each lane to corresponding light
                    light_status = traffic_lights[idx] if idx < len(traffic_lights) else "RED"
                    firebase_ref.child(light_id).update({"status": light_status})
        # --------------------------
        # Draw bounding boxes and IDs
        # --------------------------
        for vehicle in tracked_vehicles:
            if "bbox" in vehicle:
                x1,y1,x2,y2=vehicle["bbox"]
                cv2.rectangle(frame,(x1,y1),(x2,y2),(0,255,0),2)
                cv2.putText(frame,str(vehicle["id"]),(x1,y1-5),cv2.FONT_HERSHEY_SIMPLEX,0.8,(0,255,0),2)
        # --------------------------
        # Overlay lane info
        # --------------------------
        for idx,(lx1,lx2) in enumerate(lane_bounds):
            lm = lane_metrics[idx]
            light=traffic_lights[idx] if idx < len(traffic_lights) else "RED"
            color={'GREEN':(0,255,0),'YELLOW':(0,255,255),'RED':(0,0,255)}[light]
            x_text=lx1+10
            y_text=30
            cv2.putText(frame,f"Lane {idx+1}: Occ {lm['occupancy']:.1f}% Speed {lm['avg_speed']:.1f} km/h Light {light}", 
                        (x_text,y_text),cv2.FONT_HERSHEY_SIMPLEX,0.6,(255,255,255),2)
            cv2.rectangle(frame,(lx2-40,5),(lx2-5,25),color,-1)
        out.write(frame)
        cv2.imshow("Multi-Lane Traffic System",frame)
        if cv2.waitKey(1) & 0xFF==ord('q'):
            break
except KeyboardInterrupt:
    print("Stopped processing.")
cap.release()
out.release()
cv2.destroyAllWindows()
print(f"Processed video saved at: {output_path}")
