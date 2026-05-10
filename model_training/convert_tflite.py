"""
=============================================================================
convert_tflite.py - TensorFlow Lite Conversion & Benchmarking
=============================================================================
Converts the trained Keras model to:
  - Float32 TFLite model (maximum accuracy)
  - Float16 TFLite model (50% smaller, minimal accuracy loss)

Then benchmarks:
  - Inference speed comparison
  - Prediction consistency between original and TFLite models
  - Accuracy loss detection
=============================================================================
"""

import os
import sys
import time
import json
import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "1"

# Register CUDA DLLs before TF initialization
from utils import register_cuda_dlls
if sys.platform == "win32":
    register_cuda_dlls()

import tensorflow as tf

from config import (
    BEST_MODEL_PATH, FINAL_MODEL_PATH,
    TFLITE_FP32_PATH, TFLITE_FP16_PATH,
    CLASS_NAMES_PATH, DATASET_DIR, IMG_SIZE,
    LOG_DIR, TFLITE_DIR
)
from utils import enforce_gpu, setup_logging, load_class_names
from dataset import discover_dataset, split_dataset, build_eval_dataset


# =============================================================================
# GPU SETUP
# =============================================================================
gpus = enforce_gpu()
logger = setup_logging(LOG_DIR, "tflite_conversion")


# =============================================================================
# CONVERT TO TFLITE
# =============================================================================

def load_model_as_float32(model_path: str):
    """Load a model and force its compute dtype to float32 for conversion."""
    tf.keras.mixed_precision.set_global_policy("float32")
    model = tf.keras.models.load_model(model_path)
    
    config = model.get_config()
    config_str = json.dumps(config)
    config_str = config_str.replace('"mixed_float16"', '"float32"')
    config_str = config_str.replace('"float16"', '"float32"')
    config = json.loads(config_str)
    
    new_model = tf.keras.Model.from_config(config)
    new_model.set_weights(model.get_weights())
    return new_model


def convert_to_tflite_fp32(model_path: str, output_path: str):
    """Convert Keras model to Float32 TFLite format."""
    logger.info("\n  Converting to Float32 TFLite...")

    if model_path.endswith(".pb"):
        converter = tf.lite.TFLiteConverter.from_saved_model(model_path)
    else:
        model = load_model_as_float32(model_path)
        converter = tf.lite.TFLiteConverter.from_keras_model(model)

    converter.optimizations = []
    converter.target_spec.supported_types = [tf.float32]

    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    logger.info(f"  [OK] Float32 TFLite saved: {output_path}")
    logger.info(f"    Size: {size_mb:.2f} MB")

    return output_path


def convert_to_tflite_fp16(model_path: str, output_path: str):
    """Convert Keras model to Float16 TFLite format (50% smaller)."""
    logger.info("\n  Converting to Float16 TFLite...")

    model = load_model_as_float32(model_path)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    logger.info(f"  [OK] Float16 TFLite saved: {output_path}")
    logger.info(f"    Size: {size_mb:.2f} MB")

    return output_path


# =============================================================================
# TFLITE INFERENCE HELPER
# =============================================================================

class TFLitePredictor:
    """Wrapper for TFLite model inference."""

    def __init__(self, model_path: str):
        self.interpreter = tf.lite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        self.input_shape = self.input_details[0]["shape"]
        self.input_dtype = self.input_details[0]["dtype"]

    def predict(self, image: np.ndarray) -> np.ndarray:
        """Run inference on a single preprocessed image."""
        # Ensure correct shape: (1, 224, 224, 3)
        if image.ndim == 3:
            image = np.expand_dims(image, axis=0)

        # Cast to expected dtype
        image = image.astype(self.input_dtype)

        self.interpreter.set_tensor(
            self.input_details[0]["index"], image
        )
        self.interpreter.invoke()

        output = self.interpreter.get_tensor(
            self.output_details[0]["index"]
        )
        return output

    def predict_batch(self, images: np.ndarray) -> list:
        """Run inference on a batch of images (one at a time for TFLite)."""
        results = []
        for img in images:
            results.append(self.predict(img))
        return np.vstack(results)


# =============================================================================
# BENCHMARK INFERENCE SPEED
# =============================================================================

def benchmark_tflite(tflite_path: str, num_runs: int = 100):
    """Benchmark TFLite inference speed."""
    logger.info(f"\n  Benchmarking {os.path.basename(tflite_path)}...")

    predictor = TFLitePredictor(tflite_path)

    # Generate dummy input
    dummy_input = np.random.rand(1, IMG_SIZE, IMG_SIZE, 3).astype(np.float32)

    # Warmup
    for _ in range(10):
        predictor.predict(dummy_input)

    # Timed runs
    times = []
    for _ in range(num_runs):
        start = time.perf_counter()
        predictor.predict(dummy_input)
        times.append(time.perf_counter() - start)

    avg_ms = np.mean(times) * 1000
    std_ms = np.std(times) * 1000
    min_ms = np.min(times) * 1000
    max_ms = np.max(times) * 1000

    logger.info(f"    Average: {avg_ms:.2f} ms")
    logger.info(f"    Std Dev: {std_ms:.2f} ms")
    logger.info(f"    Min    : {min_ms:.2f} ms")
    logger.info(f"    Max    : {max_ms:.2f} ms")
    logger.info(f"    FPS    : {1000 / avg_ms:.1f}")

    return avg_ms


# =============================================================================
# COMPARE PREDICTIONS: ORIGINAL vs TFLITE
# =============================================================================

def compare_predictions(keras_model_path: str, tflite_path: str,
                        test_images: np.ndarray, test_labels: np.ndarray,
                        class_names: list, num_samples: int = 200):
    """
    Compare predictions between original Keras model and TFLite model.
    Detects accuracy loss after conversion.
    """
    logger.info(f"\n  Comparing Keras vs TFLite predictions ({num_samples} samples)...")

    # Load Keras model
    keras_model = tf.keras.models.load_model(keras_model_path)

    # Load TFLite model
    tflite_predictor = TFLitePredictor(tflite_path)

    # Use subset for comparison
    indices = np.random.choice(len(test_images), min(num_samples, len(test_images)),
                                replace=False)
    sample_images = test_images[indices]
    sample_labels = test_labels[indices]

    # Keras predictions
    keras_preds = keras_model.predict(sample_images, verbose=0)
    keras_classes = np.argmax(keras_preds, axis=1)

    # TFLite predictions
    tflite_preds = tflite_predictor.predict_batch(sample_images)
    tflite_classes = np.argmax(tflite_preds, axis=1)

    # Accuracy comparison
    keras_accuracy = np.mean(keras_classes == sample_labels)
    tflite_accuracy = np.mean(tflite_classes == sample_labels)
    agreement = np.mean(keras_classes == tflite_classes)
    accuracy_drop = keras_accuracy - tflite_accuracy

    logger.info(f"\n  Results:")
    logger.info(f"    Keras Accuracy  : {keras_accuracy:.4f}")
    logger.info(f"    TFLite Accuracy : {tflite_accuracy:.4f}")
    logger.info(f"    Accuracy Drop   : {accuracy_drop:.4f}")
    logger.info(f"    Agreement Rate  : {agreement:.4f}")

    if accuracy_drop > 0.02:
        logger.warning(
            f"  [WARN] Significant accuracy loss detected ({accuracy_drop:.4f})! "
            "Consider using Float32 TFLite or re-evaluating quantization."
        )
    else:
        logger.info("  [OK] Accuracy loss within acceptable range (<2%)")

    # Confidence distribution comparison
    keras_conf = np.max(keras_preds, axis=1)
    tflite_conf = np.max(tflite_preds, axis=1)
    logger.info(f"\n    Keras Avg Confidence  : {np.mean(keras_conf):.4f}")
    logger.info(f"    TFLite Avg Confidence : {np.mean(tflite_conf):.4f}")

    return {
        "keras_accuracy": float(keras_accuracy),
        "tflite_accuracy": float(tflite_accuracy),
        "accuracy_drop": float(accuracy_drop),
        "agreement_rate": float(agreement),
    }


# =============================================================================
# MAIN
# =============================================================================

def main():
    """Full TFLite conversion and benchmarking pipeline."""
    logger.info("\n" + "#" * 70)
    logger.info("  TFLITE CONVERSION & BENCHMARKING")
    logger.info("#" * 70)

    # Determine source model
    model_path = BEST_MODEL_PATH if os.path.exists(BEST_MODEL_PATH) \
        else FINAL_MODEL_PATH

    if not os.path.exists(model_path):
        logger.error(f"  No trained model found at {model_path}")
        logger.error("  Run train.py first!")
        sys.exit(1)

    logger.info(f"\n  Source model: {model_path}")
    original_size = os.path.getsize(model_path) / (1024 * 1024)
    logger.info(f"  Original model size: {original_size:.2f} MB")

    # ------ Convert to Float32 ------
    convert_to_tflite_fp32(model_path, TFLITE_FP32_PATH)

    # ------ Convert to Float16 ------
    convert_to_tflite_fp16(model_path, TFLITE_FP16_PATH)

    # ------ Size Comparison ------
    fp32_size = os.path.getsize(TFLITE_FP32_PATH) / (1024 * 1024)
    fp16_size = os.path.getsize(TFLITE_FP16_PATH) / (1024 * 1024)

    logger.info("\n" + "=" * 70)
    logger.info("  MODEL SIZE COMPARISON")
    logger.info("=" * 70)
    logger.info(f"  Original (.keras): {original_size:.2f} MB")
    logger.info(f"  TFLite Float32   : {fp32_size:.2f} MB ({fp32_size/original_size*100:.0f}%)")
    logger.info(f"  TFLite Float16   : {fp16_size:.2f} MB ({fp16_size/original_size*100:.0f}%)")

    # ------ Benchmark Speed ------
    logger.info("\n" + "=" * 70)
    logger.info("  INFERENCE SPEED BENCHMARK")
    logger.info("=" * 70)
    fp32_ms = benchmark_tflite(TFLITE_FP32_PATH)
    fp16_ms = benchmark_tflite(TFLITE_FP16_PATH)

    logger.info(f"\n  Speed comparison:")
    logger.info(f"    Float32: {fp32_ms:.2f} ms/inference")
    logger.info(f"    Float16: {fp16_ms:.2f} ms/inference")
    if fp32_ms > 0:
        logger.info(f"    Float16 speedup: {fp32_ms / fp16_ms:.2f}x")

    # ------ Prediction Comparison (if test data available) ------
    try:
        class_names = load_class_names(CLASS_NAMES_PATH)
        _, file_paths, labels, _ = discover_dataset(DATASET_DIR)
        (_, _, _, _, test_paths, test_labels) = split_dataset(file_paths, labels)

        # Load a subset of test images for comparison
        logger.info("\n  Loading test images for prediction comparison...")
        test_images = []
        num_compare = min(200, len(test_paths))
        for path in test_paths[:num_compare]:
            img = tf.keras.utils.load_img(path, target_size=(IMG_SIZE, IMG_SIZE))
            img_array = tf.keras.utils.img_to_array(img)
            img_array = tf.keras.applications.efficientnet.preprocess_input(img_array)
            test_images.append(img_array)

        test_images = np.array(test_images)
        test_labels_subset = np.array(test_labels[:num_compare])

        # Compare FP32
        logger.info("\n  --- Float32 TFLite vs Keras ---")
        fp32_results = compare_predictions(
            model_path, TFLITE_FP32_PATH,
            test_images, test_labels_subset, class_names
        )

        # Compare FP16
        logger.info("\n  --- Float16 TFLite vs Keras ---")
        fp16_results = compare_predictions(
            model_path, TFLITE_FP16_PATH,
            test_images, test_labels_subset, class_names
        )

        # Save comparison results
        comparison = {
            "model_sizes": {
                "original_mb": original_size,
                "fp32_mb": fp32_size,
                "fp16_mb": fp16_size
            },
            "inference_speed": {
                "fp32_ms": fp32_ms,
                "fp16_ms": fp16_ms
            },
            "fp32_comparison": fp32_results,
            "fp16_comparison": fp16_results
        }
        comparison_path = os.path.join(TFLITE_DIR, "conversion_report.json")
        with open(comparison_path, "w") as f:
            json.dump(comparison, f, indent=2)
        logger.info(f"\n  [OK] Conversion report saved to {comparison_path}")

    except Exception as e:
        logger.warning(f"  Prediction comparison skipped: {e}")

    logger.info("\n" + "#" * 70)
    logger.info("  TFLITE CONVERSION COMPLETE")
    logger.info(f"  Float32: {TFLITE_FP32_PATH}")
    logger.info(f"  Float16: {TFLITE_FP16_PATH}")
    logger.info("#" * 70 + "\n")


if __name__ == "__main__":
    main()
