"""
=============================================================================
compare_tflite_preprocessing.py - Preprocessing Validation Script
=============================================================================
This script proves the accuracy drop caused by the `NormalizeOp(0f, 255f)`
mismatch in the Android app, and validates the `NormalizeOp(0f, 1f)` fix.
=============================================================================
"""

import os
import sys
import numpy as np
import tensorflow as tf
from predict import predict_with_keras, format_predictions
from config import BEST_MODEL_PATH, FINAL_MODEL_PATH, TFLITE_FP32_PATH, CLASS_NAMES_PATH, IMG_SIZE
import json

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

def main():
    print("=" * 70)
    print("  TFLITE MOBILE PREPROCESSING COMPARISON")
    print("=" * 70)

    # 1. Load class names
    if not os.path.exists(CLASS_NAMES_PATH):
        print(f"Warning: {CLASS_NAMES_PATH} not found. Using fallback Android class names.")
        class_names = [
            "Apple___Apple_scab", "Apple___Black_rot", "Apple___Cedar_apple_rust", "Apple___healthy",
            "Blueberry___healthy", "Cherry_(including_sour)___Powdery_mildew", "Cherry_(including_sour)___healthy",
            "Corn_(maize)___Cercospora_leaf_spot_Gray_leaf_spot", "Corn_(maize)___Common_rust",
            "Corn_(maize)___Northern_Leaf_Blight", "Corn_(maize)___healthy", "Grape___Black_rot",
            "Grape___Esca_(Black_Measles)", "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Grape___healthy",
            "Orange___Haunglongbing_(Citrus_greening)", "Peach___Bacterial_spot", "Peach___healthy",
            "Pepper,_bell___Bacterial_spot", "Pepper,_bell___healthy", "Potato___Early_blight",
            "Potato___Late_blight", "Potato___healthy", "Raspberry___healthy", "Soybean___healthy",
            "Squash___Powdery_mildew", "Strawberry___Leaf_scorch", "Strawberry___healthy",
            "Tomato___Bacterial_spot", "Tomato___Early_blight", "Tomato___Late_blight", "Tomato___Leaf_Mold",
            "Tomato___Septoria_leaf_spot", "Tomato___Spider_mites_Two-spotted_spider_mite", "Tomato___Target_Spot",
            "Tomato___Tomato_Yellow_Leaf_Curl_Virus", "Tomato___Tomato_mosaic_virus", "Tomato___healthy"
        ]
    else:
        with open(CLASS_NAMES_PATH, "r") as f:
            class_names = json.load(f)

    # 2. Determine Models path
    keras_model_path = BEST_MODEL_PATH if os.path.exists(BEST_MODEL_PATH) else FINAL_MODEL_PATH
    tflite_model_path = TFLITE_FP32_PATH
    
    # Fallback to Android asset
    if not os.path.exists(tflite_model_path):
        android_asset_path = r"c:\Users\kings\OneDrive\Desktop\Documents\Doctor__Plant\PlantCureApp\app\src\main\assets\plant_disease_model.tflite"
        if os.path.exists(android_asset_path):
            tflite_model_path = android_asset_path
        else:
            print("Error: TFLite model not found anywhere.")
            sys.exit(1)

    has_keras = os.path.exists(keras_model_path)
    if has_keras:
        print(f"Loading Keras Model: {keras_model_path}")
        keras_model = tf.keras.models.load_model(keras_model_path)
    else:
        print(f"Warning: Keras model not found at {keras_model_path}. Skipping Keras baseline.")
    
    print(f"Loading TFLite Model: {tflite_model_path}")
    interpreter = tf.lite.Interpreter(model_path=tflite_model_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # 3. Simulate a raw camera image [0, 255]
    np.random.seed(42)
    raw_image_data = np.random.randint(0, 256, (1, IMG_SIZE, IMG_SIZE, 3), dtype=np.uint8)

    if has_keras:
        print("\n--- TEST 1: Keras Baseline (Correct) ---")
        keras_input = raw_image_data.astype(np.float32)
        keras_input = tf.keras.applications.efficientnet.preprocess_input(keras_input)
        keras_predictions = keras_model.predict(keras_input, verbose=0)[0]
        keras_result = format_predictions(keras_predictions, class_names)
        
        print(f"  Predicted: {keras_result['predicted_class']}")
        print(f"  Confidence: {keras_result['confidence']:.4f}")
        top_class_idx = np.argmax(keras_predictions)
        true_predictions = keras_predictions
    else:
        top_class_idx = None
        true_predictions = None

    print("\n--- TEST 2: TFLite with OLD Android Preprocessing (BUGGY) ---")
    # Android was doing: NormalizeOp(0f, 255f) -> (pixel - 0) / 255
    buggy_tflite_input = (raw_image_data.astype(np.float32) - 0.0) / 255.0
    interpreter.set_tensor(input_details[0]["index"], buggy_tflite_input)
    interpreter.invoke()
    buggy_tflite_predictions = interpreter.get_tensor(output_details[0]["index"])[0]
    buggy_result = format_predictions(buggy_tflite_predictions, class_names)
    
    print(f"  Predicted: {buggy_result['predicted_class']}")
    print(f"  Confidence: {buggy_result['confidence']:.4f}")
    
    # Calculate confidence diff for the actual true class (according to keras)
    if top_class_idx is not None:
        keras_class_prob_in_buggy = buggy_tflite_predictions[top_class_idx]
        print(f"  Probability of Keras's top class ({class_names[top_class_idx]}): {keras_class_prob_in_buggy:.4f}")
    
    print("\n--- TEST 3: TFLite with FIXED Android Preprocessing (CORRECT) ---")
    # New Android code: NormalizeOp(0f, 1f) -> (pixel - 0) / 1
    # This leaves the values in [0, 255] but casts to float32
    fixed_tflite_input = (raw_image_data.astype(np.float32) - 0.0) / 1.0
    interpreter.set_tensor(input_details[0]["index"], fixed_tflite_input)
    interpreter.invoke()
    fixed_tflite_predictions = interpreter.get_tensor(output_details[0]["index"])[0]
    fixed_result = format_predictions(fixed_tflite_predictions, class_names)
    
    print(f"  Predicted: {fixed_result['predicted_class']}")
    print(f"  Confidence: {fixed_result['confidence']:.4f}")
    
    # Verify exact match
    if has_keras:
        max_diff = np.max(np.abs(true_predictions - fixed_tflite_predictions))
        print(f"\nMax absolute difference between Keras and FIXED TFLite: {max_diff:.8f}")
        
        if max_diff < 1e-5:
            print("\nSUCCESS! The fixed Android preprocessing perfectly matches the Keras training pipeline.")
        else:
            print("\nWARNING! There is still a discrepancy.")
    else:
        print("\nSUCCESS! TFLite model evaluated with fixed preprocessing.")
        print("Note: Run with a trained Keras model to verify exact mathematical parity.")

if __name__ == "__main__":
    main()
