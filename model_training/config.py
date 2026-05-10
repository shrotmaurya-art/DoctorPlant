"""
=============================================================================
config.py - Central Configuration for Plant Disease Detection Pipeline
=============================================================================
All paths, hyperparameters, and pipeline settings in one place.
Modify this file to adapt the pipeline to different datasets or hardware.
=============================================================================
"""

import os

# =============================================================================
# PROJECT PATHS
# =============================================================================
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR = os.path.join(PROJECT_ROOT, "Dataset")
OUTPUT_DIR = os.path.join(PROJECT_ROOT, "output")
MODEL_DIR = os.path.join(OUTPUT_DIR, "models")
LOG_DIR = os.path.join(OUTPUT_DIR, "logs")
TFLITE_DIR = os.path.join(OUTPUT_DIR, "tflite")
PLOTS_DIR = os.path.join(OUTPUT_DIR, "plots")
TENSORBOARD_DIR = os.path.join(OUTPUT_DIR, "tensorboard")
CHECKPOINT_DIR = os.path.join(OUTPUT_DIR, "checkpoints")

# Create all output directories
for d in [OUTPUT_DIR, MODEL_DIR, LOG_DIR, TFLITE_DIR, PLOTS_DIR,
          TENSORBOARD_DIR, CHECKPOINT_DIR]:
    os.makedirs(d, exist_ok=True)

# =============================================================================
# IMAGE & MODEL PARAMETERS
# =============================================================================
IMG_SIZE = 224                  # EfficientNetB0 optimal input size
IMG_SHAPE = (IMG_SIZE, IMG_SIZE, 3)
BATCH_SIZE = 32                 # Optimal for RTX 3050 4GB VRAM
NUM_CHANNELS = 3

# =============================================================================
# DATASET SPLIT RATIOS
# =============================================================================
TRAIN_SPLIT = 0.70
VAL_SPLIT = 0.15
TEST_SPLIT = 0.15

# =============================================================================
# TRAINING HYPERPARAMETERS - PHASE 1 (TRANSFER LEARNING)
# =============================================================================
PHASE1_EPOCHS = 30
PHASE1_LEARNING_RATE = 1e-3
PHASE1_MIN_LR = 1e-6

# =============================================================================
# TRAINING HYPERPARAMETERS - PHASE 2 (FINE-TUNING)
# =============================================================================
PHASE2_EPOCHS = 50
PHASE2_LEARNING_RATE = 1e-5
PHASE2_MIN_LR = 1e-8
FINE_TUNE_AT_LAYER = 100        # Unfreeze from this layer onward

# =============================================================================
# REGULARIZATION
# =============================================================================
DROPOUT_RATE = 0.4
LABEL_SMOOTHING = 0.1

# =============================================================================
# CALLBACKS
# =============================================================================
EARLY_STOPPING_PATIENCE = 10
REDUCE_LR_PATIENCE = 5
REDUCE_LR_FACTOR = 0.5

# =============================================================================
# AUGMENTATION INTENSITIES (for real-world mobile robustness)
# =============================================================================
AUG_ROTATION_RANGE = 30
AUG_ZOOM_RANGE = 0.25
AUG_WIDTH_SHIFT = 0.2
AUG_HEIGHT_SHIFT = 0.2
AUG_BRIGHTNESS_RANGE = (0.6, 1.4)
AUG_CONTRAST_RANGE = (0.7, 1.3)
AUG_BLUR_KERNEL_MAX = 7
AUG_NOISE_STD_MAX = 25.0
AUG_JPEG_QUALITY_MIN = 30
AUG_SHADOW_INTENSITY = 0.5

# =============================================================================
# FILE NAMES
# =============================================================================
BEST_MODEL_PATH = os.path.join(MODEL_DIR, "best_model.keras")
FINAL_MODEL_PATH = os.path.join(MODEL_DIR, "final_model.keras")
PHASE1_MODEL_PATH = os.path.join(MODEL_DIR, "phase1_model.keras")
CLASS_NAMES_PATH = os.path.join(MODEL_DIR, "class_names.json")
TRAINING_HISTORY_PATH = os.path.join(LOG_DIR, "training_history.json")
CSV_LOG_PATH = os.path.join(LOG_DIR, "training_log.csv")
TFLITE_FP32_PATH = os.path.join(TFLITE_DIR, "model_float32.tflite")
TFLITE_FP16_PATH = os.path.join(TFLITE_DIR, "model_float16.tflite")

# =============================================================================
# GPU SETTINGS
# =============================================================================
ENABLE_MIXED_PRECISION = True
ENABLE_XLA = True
GPU_MEMORY_GROWTH = True

# =============================================================================
# INFERENCE
# =============================================================================
TOP_K_PREDICTIONS = 3
CONFIDENCE_THRESHOLD = 0.5

# =============================================================================
# CLASS IMBALANCE SETTINGS
# =============================================================================
USE_CLASS_WEIGHTS = True
USE_OVERSAMPLING = True         # Repeat minority class images
USE_UNDERSAMPLING = False       # Truncate majority class images
MIN_SAMPLES_PER_CLASS = 800     # Target for oversampling
MAX_SAMPLES_PER_CLASS = 2000    # Target for undersampling
IMBALANCE_RATIO_THRESHOLD = 5.0 # Warn if max/min ratio exceeds this
