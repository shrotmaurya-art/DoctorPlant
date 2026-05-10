"""
=============================================================================
predict.py - Single Image Prediction with Confidence Scores
=============================================================================
Supports:
  - Keras (.keras) model inference
  - TFLite (.tflite) model inference
  - Top-K predictions with confidence
  - Image display with prediction overlay
  - Webcam/mobile camera image support
  - Same preprocessing as training for consistency
=============================================================================
"""

import os
import sys
import argparse
import json
import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

# Register CUDA DLLs before TF initialization
from utils import register_cuda_dlls
if sys.platform == "win32":
    register_cuda_dlls()

import tensorflow as tf
import cv2

from config import (
    BEST_MODEL_PATH, FINAL_MODEL_PATH,
    TFLITE_FP16_PATH, TFLITE_FP32_PATH,
    CLASS_NAMES_PATH, IMG_SIZE,
    TOP_K_PREDICTIONS, CONFIDENCE_THRESHOLD
)


# =============================================================================
# PREPROCESSING - MUST MATCH TRAINING EXACTLY
# =============================================================================

def preprocess_image(image_path: str) -> np.ndarray:
    """
    Load and preprocess image identically to training pipeline.
    This ensures consistent predictions between training and inference.
    """
    # Load with OpenCV (handles various formats)
    image = cv2.imread(image_path)
    if image is None:
        raise FileNotFoundError(f"Cannot load image: {image_path}")

    # Convert BGR -> RGB
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

    # Resize to model input size
    image = cv2.resize(image, (IMG_SIZE, IMG_SIZE),
                       interpolation=cv2.INTER_LINEAR)

    # Convert to float32
    image = image.astype(np.float32)

    # Apply EfficientNet preprocessing (same as training)
    image = tf.keras.applications.efficientnet.preprocess_input(image)

    # Add batch dimension
    image = np.expand_dims(image, axis=0)

    return image


def preprocess_image_from_array(image_array: np.ndarray) -> np.ndarray:
    """Preprocess a numpy array (e.g., from webcam capture)."""
    if image_array.shape[2] == 4:  # BGRA
        image_array = cv2.cvtColor(image_array, cv2.COLOR_BGRA2RGB)
    elif len(image_array.shape) == 3 and image_array.shape[2] == 3:
        image_array = cv2.cvtColor(image_array, cv2.COLOR_BGR2RGB)

    image = cv2.resize(image_array, (IMG_SIZE, IMG_SIZE),
                       interpolation=cv2.INTER_LINEAR)
    image = image.astype(np.float32)
    image = tf.keras.applications.efficientnet.preprocess_input(image)
    image = np.expand_dims(image, axis=0)
    return image


# =============================================================================
# PREDICTION FUNCTIONS
# =============================================================================

def predict_with_keras(model_path: str, image: np.ndarray,
                       class_names: list) -> dict:
    """Run prediction using Keras model."""
    model = tf.keras.models.load_model(model_path)
    predictions = model.predict(image, verbose=0)[0]
    return format_predictions(predictions, class_names)


def predict_with_tflite(tflite_path: str, image: np.ndarray,
                        class_names: list) -> dict:
    """Run prediction using TFLite model."""
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Cast to expected dtype
    input_dtype = input_details[0]["dtype"]
    image = image.astype(input_dtype)

    interpreter.set_tensor(input_details[0]["index"], image)
    interpreter.invoke()

    predictions = interpreter.get_tensor(output_details[0]["index"])[0]
    return format_predictions(predictions, class_names)


def format_predictions(predictions: np.ndarray,
                       class_names: list) -> dict:
    """Format raw predictions into structured result."""
    top_k_indices = np.argsort(predictions)[::-1][:TOP_K_PREDICTIONS]

    result = {
        "predicted_class": class_names[top_k_indices[0]],
        "confidence": float(predictions[top_k_indices[0]]),
        "top_predictions": [],
        "all_probabilities": {
            name: float(prob)
            for name, prob in zip(class_names, predictions)
        }
    }

    for idx in top_k_indices:
        result["top_predictions"].append({
            "class": class_names[idx],
            "confidence": float(predictions[idx]),
            "percentage": f"{predictions[idx] * 100:.2f}%"
        })

    return result


# =============================================================================
# DISPLAY RESULTS
# =============================================================================

def display_prediction(image_path: str, result: dict):
    """Display image with prediction overlay."""
    # Load original image for display
    image = cv2.imread(image_path)
    if image is None:
        print("  Cannot display image")
        return

    # Resize for display
    display_h = 500
    aspect = image.shape[1] / image.shape[0]
    display_w = int(display_h * aspect)
    display_img = cv2.resize(image, (display_w, display_h))

    # Add prediction text overlay
    predicted = result["predicted_class"]
    confidence = result["confidence"]

    # Background rectangle for text
    cv2.rectangle(display_img, (0, 0), (display_w, 120), (0, 0, 0), -1)

    # Main prediction
    color = (0, 255, 0) if confidence >= CONFIDENCE_THRESHOLD else (0, 165, 255)
    cv2.putText(
        display_img, f"{predicted}",
        (10, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2
    )
    cv2.putText(
        display_img, f"Confidence: {confidence:.2%}",
        (10, 65), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1
    )

    # Top-3 predictions
    top3_text = " | ".join([
        f"{p['class']}: {p['percentage']}"
        for p in result["top_predictions"]
    ])
    cv2.putText(
        display_img, f"Top 3: {top3_text[:80]}",
        (10, 100), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (200, 200, 200), 1
    )

    cv2.imshow("Plant Disease Prediction", display_img)
    print("\n  Press any key to close the window...")
    cv2.waitKey(0)
    cv2.destroyAllWindows()


def print_prediction(result: dict):
    """Print formatted prediction results to console."""
    print("\n" + "=" * 60)
    print("  PREDICTION RESULT")
    print("=" * 60)
    print(f"  Predicted Disease : {result['predicted_class']}")
    print(f"  Confidence        : {result['confidence']:.4f} "
          f"({result['confidence'] * 100:.2f}%)")

    if result["confidence"] < CONFIDENCE_THRESHOLD:
        print(f"\n  [WARN] LOW CONFIDENCE - prediction may be unreliable")
        print(f"    Threshold: {CONFIDENCE_THRESHOLD:.0%}")

    print(f"\n  Top-{TOP_K_PREDICTIONS} Predictions:")
    print("-" * 60)
    for i, pred in enumerate(result["top_predictions"], 1):
        bar_length = int(pred["confidence"] * 40)
        # Use ASCII-compatible characters for the progress bar to avoid encoding errors on some systems
        bar = "#" * bar_length + "-" * (40 - bar_length)
        print(f"  {i}. {pred['class']:<40} {pred['percentage']:>7}")
        print(f"     [{bar}]")

    print("=" * 60 + "\n")


# =============================================================================
# WEBCAM CAPTURE
# =============================================================================

def predict_from_webcam(model_path: str, class_names: list,
                        use_tflite: bool = False):
    """Capture from webcam and predict in real-time."""
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("  Error: Cannot open webcam")
        return

    print("\n  Webcam active - Press 'q' to quit, 'c' to capture & predict")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        # Display live feed
        display = frame.copy()
        cv2.putText(display, "Press 'c' to capture, 'q' to quit",
                     (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7,
                     (0, 255, 0), 2)
        cv2.imshow("Plant Disease Detection - Live", display)

        key = cv2.waitKey(1) & 0xFF
        if key == ord("q"):
            break
        elif key == ord("c"):
            # Process captured frame
            processed = preprocess_image_from_array(frame)

            if use_tflite:
                result = predict_with_tflite(model_path, processed, class_names)
            else:
                result = predict_with_keras(model_path, processed, class_names)

            print_prediction(result)

            # Show prediction on frame
            cv2.rectangle(frame, (0, 0), (frame.shape[1], 80), (0, 0, 0), -1)
            cv2.putText(
                frame,
                f"{result['predicted_class']}: {result['confidence']:.2%}",
                (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 255, 0), 2
            )
            cv2.imshow("Prediction", frame)
            cv2.waitKey(0)

    cap.release()
    cv2.destroyAllWindows()


# =============================================================================
# CLI INTERFACE
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Plant Disease Detection - Predict disease from plant leaf image"
    )
    parser.add_argument(
        "image", nargs="?",
        help="Path to plant leaf image (omit for webcam mode)"
    )
    parser.add_argument(
        "--model", type=str, default=None,
        help="Path to model (.keras or .tflite)"
    )
    parser.add_argument(
        "--tflite", action="store_true",
        help="Use TFLite model for inference"
    )
    parser.add_argument(
        "--fp16", action="store_true",
        help="Use Float16 TFLite model (smaller, slightly faster)"
    )
    parser.add_argument(
        "--webcam", action="store_true",
        help="Use webcam for live prediction"
    )
    parser.add_argument(
        "--no-display", action="store_true",
        help="Skip image display (headless mode)"
    )

    args = parser.parse_args()

    # Load class names
    if not os.path.exists(CLASS_NAMES_PATH):
        print(f"  Error: Class names not found at {CLASS_NAMES_PATH}")
        print("  Run train.py first to generate class names.")
        sys.exit(1)

    with open(CLASS_NAMES_PATH, "r") as f:
        class_names = json.load(f)

    # Determine model path
    if args.model:
        model_path = args.model
    elif args.tflite or args.fp16:
        model_path = TFLITE_FP16_PATH if args.fp16 else TFLITE_FP32_PATH
    else:
        model_path = BEST_MODEL_PATH if os.path.exists(BEST_MODEL_PATH) \
            else FINAL_MODEL_PATH

    if not os.path.exists(model_path):
        print(f"  Error: Model not found at {model_path}")
        print("  Run train.py first!")
        sys.exit(1)

    use_tflite = args.tflite or args.fp16 or model_path.endswith(".tflite")

    print(f"\n  Using model: {model_path}")
    print(f"  Model type : {'TFLite' if use_tflite else 'Keras'}")
    print(f"  Classes    : {len(class_names)}")

    # Webcam mode
    if args.webcam or args.image is None:
        if args.image is None and not args.webcam:
            print("\n  No image provided - launching webcam mode")
        predict_from_webcam(model_path, class_names, use_tflite)
        return

    # Single image prediction
    if not os.path.exists(args.image):
        print(f"  Error: Image not found: {args.image}")
        sys.exit(1)

    # Preprocess
    image = preprocess_image(args.image)

    # Predict
    if use_tflite:
        result = predict_with_tflite(model_path, image, class_names)
    else:
        result = predict_with_keras(model_path, image, class_names)

    # Display results
    print_prediction(result)

    if not args.no_display:
        display_prediction(args.image, result)


if __name__ == "__main__":
    main()
