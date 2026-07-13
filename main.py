import argparse
import os
from pathlib import Path

import cv2

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}
VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".m4v"}

try:
    from ultralytics import YOLO
except ImportError as exc:
    raise SystemExit(
        "ultralytics is required. Install dependencies with: pip install -r requirements.txt"
    ) from exc


DEFAULT_WEIGHTS = "yolov8n.pt"
DEFAULT_FAST_MODEL = "yolov8n.pt"


def parse_args():
    parser = argparse.ArgumentParser(description="Helmet detection using YOLOv8")
    parser.add_argument("--weights", default=DEFAULT_WEIGHTS, help="Path to YOLO weights file")
    parser.add_argument(
        "--source",
        default="0",
        help="Camera index, image path, or video file. Default is webcam (0)",
    )
    parser.add_argument("--conf", type=float, default=0.45, help="Detection confidence threshold")
    parser.add_argument("--imgsz", type=int, default=320, help="Input image size for faster inference")
    parser.add_argument("--process-every", type=int, default=2, help="Process every Nth frame for faster webcam/video runs")
    parser.add_argument("--save-dir", default="output", help="Directory to save output media")
    parser.add_argument("--show", action="store_true", help="Display the annotated frame")
    return parser.parse_args()


def resolve_weights(weights: str):
    path = Path(weights)
    if path.exists():
        return str(path)

    fallback = Path(DEFAULT_FAST_MODEL)
    print(f"Weights file not found at {weights}. Falling back to {fallback}.")
    return str(fallback)


def draw_status(frame, status, helmet_count, rider_count):
    height, width = frame.shape[:2]
    color = (0, 255, 0) if status == "helmet detected" else (0, 0, 255)
    cv2.rectangle(frame, (10, 10), (width - 10, 70), (0, 0, 0), -1)
    cv2.putText(
        frame,
        f"Status: {status}",
        (20, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        color,
        2,
    )
    cv2.putText(
        frame,
        f"Helmets: {helmet_count}  Riders: {rider_count}",
        (20, 60),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        (255, 255, 255),
        2,
    )


def annotate_frame(frame, results, names):
    helmet_count = 0
    rider_count = 0

    for box in results.boxes:
        x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
        cls_id = int(box.cls[0])
        label = names.get(cls_id, str(cls_id)).lower()
        conf = float(box.conf[0])

        if label == "helmet":
            helmet_count += 1
            color = (0, 255, 0)
            label_text = f"helmet {conf:.2f}"
        elif label in {"person", "motorcycle", "rider"}:
            rider_count += 1
            color = (0, 0, 255)
            label_text = f"{label} {conf:.2f}"
        else:
            color = (255, 255, 0)
            label_text = f"{label} {conf:.2f}"

        cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
        cv2.putText(frame, label_text, (x1, max(0, y1 - 5)), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)

    if helmet_count > 0:
        status = "helmet detected"
    elif rider_count > 0:
        status = "no helmet detected"
    else:
        status = "no rider detected"

    draw_status(frame, status, helmet_count, rider_count)
    return frame


def run_inference(model, source, conf, imgsz, process_every, show, save_dir):
    save_dir = Path(save_dir)
    save_dir.mkdir(parents=True, exist_ok=True)
    root_dir = Path(__file__).resolve().parent
    source_path = Path(source)
    if not source_path.is_absolute():
        source_path = root_dir / source_path

    if source.isdigit():
        cap = cv2.VideoCapture(int(source))
        if not cap.isOpened():
            raise RuntimeError(f"Unable to open webcam device {source}")
        output_path = save_dir / "webcam_output.mp4"
        writer = None
        frame_count = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frame_count += 1
            if frame_count % process_every != 0:
                continue
            results = model(frame, imgsz=imgsz, conf=conf, stream=False)[0]
            annotated = annotate_frame(frame.copy(), results, model.names)
            if show:
                cv2.imshow("Helmet Detection", annotated)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break
            if writer is None:
                fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                writer = cv2.VideoWriter(str(output_path), fourcc, 20.0, (frame.shape[1], frame.shape[0]))
            writer.write(annotated)
        cap.release()
        if writer is not None:
            writer.release()
        if show:
            cv2.destroyAllWindows()
        print(f"Saved webcam output to {output_path}")
        return

    if source_path.exists() and source_path.suffix.lower() in IMAGE_EXTENSIONS:
        frame = cv2.imread(str(source_path))
        if frame is None:
            raise FileNotFoundError(f"Image not found: {source}")
        results = model(frame, imgsz=imgsz, conf=conf, stream=False)[0]
        annotated = annotate_frame(frame.copy(), results, model.names)
        output_path = save_dir / f"{source_path.stem}_annotated.jpg"
        cv2.imwrite(str(output_path), annotated)
        print(f"Saved annotated image to {output_path}")
        if show:
            cv2.imshow("Helmet Detection", annotated)
            cv2.waitKey(0)
            cv2.destroyAllWindows()
        return

    if source_path.exists() and source_path.suffix.lower() in VIDEO_EXTENSIONS:
        cap = cv2.VideoCapture(str(source_path))
        if not cap.isOpened():
            raise RuntimeError(f"Unable to open video file {source}")
        output_path = save_dir / f"{source_path.stem}_annotated.mp4"
        writer = None
        frame_count = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frame_count += 1
            if frame_count % process_every != 0:
                continue
            results = model(frame, imgsz=imgsz, conf=conf, stream=False)[0]
            annotated = annotate_frame(frame.copy(), results, model.names)
            if show:
                cv2.imshow("Helmet Detection", annotated)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break
            if writer is None:
                fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                writer = cv2.VideoWriter(str(output_path), fourcc, 20.0, (frame.shape[1], frame.shape[0]))
            writer.write(annotated)
        cap.release()
        if writer is not None:
            writer.release()
        if show:
            cv2.destroyAllWindows()
        print(f"Saved annotated video to {output_path}")
        return

    raise FileNotFoundError(f"Source path not found: {source}")


def main():
    args = parse_args()
    weights = resolve_weights(args.weights)
    print(f"Loading YOLOv8 model from {weights}...")
    model = YOLO(weights)
    model.to("cpu")
    print("Model loaded successfully.")
    run_inference(model, args.source, args.conf, args.imgsz, args.process_every, args.show, args.save_dir)


if __name__ == "__main__":
    main()
