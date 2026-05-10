"""
=============================================================================
utils.py - Utility Functions for GPU Setup, Logging, and Common Operations
=============================================================================
Handles GPU detection/enforcement, mixed precision, XLA, logging setup,
and shared helper functions used across the pipeline.
=============================================================================
"""

import os
import sys
import json
import logging
from datetime import datetime

import numpy as np


# =============================================================================
# LOGGING SETUP
# =============================================================================
def setup_logging(log_dir: str, name: str = "PlantDisease") -> logging.Logger:
    """Configure logging to both console and file."""
    os.makedirs(log_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_file = os.path.join(log_dir, f"{name}_{timestamp}.log")

    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)

    # Avoid duplicate handlers on repeated calls
    if logger.handlers:
        return logger

    # File handler
    fh = logging.FileHandler(log_file, encoding="utf-8")
    fh.setLevel(logging.INFO)

    # Console handler
    ch = logging.StreamHandler(sys.stdout)
    ch.setLevel(logging.INFO)

    formatter = logging.Formatter(
        "[%(asctime)s] %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    fh.setFormatter(formatter)
    ch.setFormatter(formatter)

    logger.addHandler(fh)
    logger.addHandler(ch)

    return logger


# =============================================================================
# GPU ENFORCEMENT - MUST HAVE NVIDIA GPU OR EXIT
# =============================================================================
def enforce_gpu():
    """
    Detect, configure, and enforce NVIDIA GPU usage.
    Exits with error if no GPU is found - NEVER falls back to CPU.
    """
    import tensorflow as tf

    # Windows-specific: Attempt to register CUDA DLLs if they are missing from PATH
    if sys.platform == "win32":
        register_cuda_dlls()

    print("=" * 70)
    print("  GPU DETECTION & CONFIGURATION")
    print("=" * 70)

    # ------ Detect physical GPUs ------
    gpus = tf.config.list_physical_devices("GPU")

    if not gpus:
        print("\n" + "!" * 70)
        print("  FATAL: No NVIDIA GPU detected!")
        print("  This pipeline requires CUDA-capable GPU hardware.")
        print("  Possible fixes for Windows:")
        print("  1. Ensure NVIDIA Drivers are updated.")
        print("  2. Ensure CUDA Toolkit 11.2 and cuDNN 8.1 are installed.")
        print("  3. Add CUDA 'bin' and 'libnvvp' folders to your System PATH.")
        print("  4. For TF 2.10, you MUST use CUDA 11.2.")
        print("!" * 70 + "\n")
        sys.exit(1)

    # ------ Print GPU details ------
    print(f"\n  [OK] Found {len(gpus)} GPU(s):")
    for i, gpu in enumerate(gpus):
        print(f"    [{i}] {gpu.name}  -  {gpu.device_type}")

    # ------ Enable memory growth ------
    for gpu in gpus:
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
            print(f"  [OK] Memory growth enabled for {gpu.name}")
        except RuntimeError as e:
            print(f"  [WARN] Memory growth setting failed: {e}")

    # ------ Enable XLA JIT compilation ------
    from config import ENABLE_XLA
    if ENABLE_XLA:
        tf.config.optimizer.set_jit(True)
        print("  [OK] XLA JIT compilation enabled")

    # ------ Enable mixed precision ------
    from config import ENABLE_MIXED_PRECISION
    if ENABLE_MIXED_PRECISION:
        tf.keras.mixed_precision.set_global_policy("mixed_float16")
        print("  [OK] Mixed precision (float16) enabled")
        print(f"    Compute dtype : {tf.keras.mixed_precision.global_policy().compute_dtype}")
        print(f"    Variable dtype: {tf.keras.mixed_precision.global_policy().variable_dtype}")

    # ------ Final verification ------
    logical_gpus = tf.config.list_logical_devices("GPU")
    print(f"\n  [OK] Logical GPUs available: {len(logical_gpus)}")

    # Quick GPU operation test
    try:
        with tf.device("/GPU:0"):
            a = tf.constant([[1.0, 2.0], [3.0, 4.0]])
            b = tf.constant([[5.0, 6.0], [7.0, 8.0]])
            c = tf.matmul(a, b)
        print(f"  [OK] GPU computation test passed: {c.device}")
    except Exception as e:
        print(f"  [WARN] GPU test failed: {e}")

    print("\n" + "=" * 70)
    print("  GPU READY - All training will execute on GPU")
    print("=" * 70 + "\n")

    return gpus


def register_cuda_dlls():
    """
    Attempt to find and register CUDA/cuDNN DLLs on Windows.
    This helps when libraries are installed but not in the PATH.
    """
    if sys.platform != "win32":
        return

    # Common CUDA 11.2 paths on Windows
    cuda_paths = [
        r"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v11.2\bin",
        r"C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v11.2\libnvvp",
        r"C:\tools\cuda\bin"
    ]

    # Dynamically find pip-installed nvidia packages in sys.path
    try:
        import glob
        for p in sys.path:
            if not p: continue
            nvidia_path = os.path.join(p, "nvidia")
            if os.path.isdir(nvidia_path):
                # Add bin directories to PATH for DLL loading
                for bin_dir in glob.glob(os.path.join(nvidia_path, "*", "bin")):
                    cuda_paths.append(bin_dir)
                
                # Setup XLA compiler paths if cuda_nvcc is installed
                nvcc_path = os.path.join(nvidia_path, "cuda_nvcc")
                if os.path.isdir(nvcc_path):
                    # XLA requires the base CUDA directory containing nvvm/libdevice and bin/ptxas
                    os.environ["XLA_FLAGS"] = f"--xla_gpu_cuda_data_dir={nvcc_path}"
    except Exception:
        pass

    added_count = 0
    for path in cuda_paths:
        if os.path.exists(path):
            try:
                os.add_dll_directory(path)
                os.environ["PATH"] = path + os.pathsep + os.environ["PATH"]
                added_count += 1
            except Exception:
                pass

    if added_count > 0:
        print(f"  [INFO] Registered {added_count} CUDA DLL directories from common locations.")


# =============================================================================
# CLASS NAME UTILITIES
# =============================================================================
def save_class_names(class_names: list, path: str):
    """Save class names to JSON for consistent inference."""
    with open(path, "w") as f:
        json.dump(class_names, f, indent=2)
    print(f"  [OK] Class names saved to {path}")


def load_class_names(path: str) -> list:
    """Load class names from JSON."""
    with open(path, "r") as f:
        return json.load(f)


# =============================================================================
# TRAINING HISTORY I/O
# =============================================================================
def save_training_history(history_dict: dict, path: str):
    """Save training history to JSON (handles numpy types)."""
    serializable = {}
    for key, values in history_dict.items():
        serializable[key] = [
            float(v) if isinstance(v, (np.floating, float)) else v
            for v in values
        ]
    with open(path, "w") as f:
        json.dump(serializable, f, indent=2)
    print(f"  [OK] Training history saved to {path}")


def load_training_history(path: str) -> dict:
    """Load training history from JSON."""
    with open(path, "r") as f:
        return json.load(f)


# =============================================================================
# COMPUTE CLASS WEIGHTS FOR IMBALANCED DATASET
# =============================================================================
def compute_class_weights(labels: np.ndarray) -> dict:
    """
    Compute balanced class weights inversely proportional to class frequency.
    Critical for this dataset where rice has ~40 images vs tomato with 3000+.
    """
    from sklearn.utils.class_weight import compute_class_weight

    unique_classes = np.unique(labels)
    weights = compute_class_weight(
        class_weight="balanced",
        classes=unique_classes,
        y=labels
    )
    class_weight_dict = {int(cls): float(w) for cls, w in zip(unique_classes, weights)}

    return class_weight_dict


# =============================================================================
# FORMATTED SUMMARY & IMBALANCE ANALYSIS
# =============================================================================
def print_dataset_summary(class_names: list, class_counts: dict,
                          total_train: int, total_val: int, total_test: int):
    """
    Print a formatted summary of the dataset and perform imbalance analysis.
    Warns if the dataset is severely imbalanced.
    """
    from config import IMBALANCE_RATIO_THRESHOLD

    counts = list(class_counts.values())
    max_count = max(counts)
    min_count = min(counts)
    imbalance_ratio = max_count / min_count if min_count > 0 else float("inf")

    print("\n" + "=" * 70)
    print("  DATASET SUMMARY & IMBALANCE ANALYSIS")
    print("=" * 70)
    print(f"  Total classes: {len(class_names)}")
    print(f"  Training samples  : {total_train}")
    print(f"  Validation samples: {total_val}")
    print(f"  Test samples      : {total_test}")
    print(f"  Total samples     : {total_train + total_val + total_test}")
    print(f"  Imbalance Ratio   : {imbalance_ratio:.2f}x (Max/Min)")
    print("-" * 70)
    print(f"  {'Class Name':<50} {'Count':>8} {'Weight':>8}")
    print("-" * 70)

    # Compute weights for display purpose
    labels_dummy = []
    for i, name in enumerate(class_names):
        labels_dummy.extend([i] * class_counts[name])
    weights_dict = compute_class_weights(np.array(labels_dummy))

    for i, name in enumerate(class_names):
        count = class_counts.get(name, 0)
        weight = weights_dict.get(i, 1.0)
        print(f"  {name:<50} {count:>8} {weight:>8.4f}")

    print("-" * 70)
    if imbalance_ratio > IMBALANCE_RATIO_THRESHOLD:
        print(f"  [WARN] SEVERE IMBALANCE DETECTED (Ratio > {IMBALANCE_RATIO_THRESHOLD}x)")
        print("  Pipeline will automatically apply class weights and/or resampling.")
    else:
        print("  [OK] Class distribution is relatively balanced.")
    print("=" * 70 + "\n")
