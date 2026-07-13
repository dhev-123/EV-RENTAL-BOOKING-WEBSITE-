# AI-Based Helmet Detection System Using YOLOv8

This project builds a computer-vision application that detects riders and checks whether a helmet is present in an image, video, or webcam feed.

## Features
- Uses YOLOv8 for object detection
- Supports webcam, image, and video input
- Draws bounding boxes and shows a helmet-status warning
- Saves annotated output to the output folder

## Folder Structure
- models/ - place your YOLO weights here, such as best.pt
- output/ - generated annotated outputs
- docs/ - project notes and screenshots

## Quick Start
1. Install requirements:
   ```bash
   pip install -r requirements.txt
   ```
2. Run the detector on a sample image:
   ```bash
   python main.py --source sample.jpg --show
   ```
3. Run webcam mode:
   ```bash
   python main.py --source 0 --show
   ```

## Notes
- If a helmet-specific weights file is not available, the app falls back to a generic YOLOv8 model so the pipeline can still run.
- For the best results, place a trained helmet-detection model at models/best.pt.
