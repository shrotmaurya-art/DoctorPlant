"""
=============================================================================
dataset.py - tf.data Pipeline for PlantVillage Dataset
=============================================================================
Handles dataset discovery, splitting, class weight computation,
and high-performance tf.data pipeline creation with GPU-optimized
prefetching, caching, and real-world augmentation integration.

Supports Oversampling and Undersampling for handling class imbalance.
=============================================================================
"""

import os
import json
import numpy as np
import tensorflow as tf

from config import (
    DATASET_DIR, IMG_SIZE, BATCH_SIZE,
    TRAIN_SPLIT, VAL_SPLIT, TEST_SPLIT,
    CLASS_NAMES_PATH, USE_OVERSAMPLING, USE_UNDERSAMPLING,
    MIN_SAMPLES_PER_CLASS, MAX_SAMPLES_PER_CLASS
)
from augmentations import (
    tf_real_world_augmentation,
    build_keras_augmentation_layer
)
from utils import (
    save_class_names, compute_class_weights, print_dataset_summary
)

AUTOTUNE = tf.data.AUTOTUNE


# =============================================================================
# DATASET DISCOVERY
# =============================================================================

def discover_dataset(dataset_dir: str):
    """
    Automatically discover classes and count images in the PlantVillage dataset.
    Returns:
        class_names: sorted list of class directory names
        file_paths: list of all image file paths
        labels: list of integer labels corresponding to file_paths
        class_counts: dict of class_name -> image_count
    """
    print("\n" + "=" * 70)
    print("  DATASET DISCOVERY")
    print("=" * 70)

    valid_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

    # Get sorted class directories
    class_names = sorted([
        d for d in os.listdir(dataset_dir)
        if os.path.isdir(os.path.join(dataset_dir, d))
    ])

    if not class_names:
        raise FileNotFoundError(
            f"No class directories found in {dataset_dir}. "
            "Expected subdirectories with images."
        )

    file_paths = []
    labels = []
    class_counts = {}

    for class_idx, class_name in enumerate(class_names):
        class_dir = os.path.join(dataset_dir, class_name)
        images = [
            os.path.join(class_dir, f)
            for f in os.listdir(class_dir)
            if os.path.splitext(f)[1].lower() in valid_extensions
        ]
        file_paths.extend(images)
        labels.extend([class_idx] * len(images))
        class_counts[class_name] = len(images)

    print(f"  [OK] Discovered {len(class_names)} classes")
    print(f"  [OK] Total images: {len(file_paths)}")

    return class_names, file_paths, labels, class_counts


# =============================================================================
# DATASET SPLITTING
# =============================================================================

def split_dataset(file_paths: list, labels: list, seed: int = 42):
    """
    Split dataset into train/validation/test sets with stratification.
    Uses sklearn for proper stratified splitting.
    """
    from sklearn.model_selection import train_test_split

    file_paths = np.array(file_paths)
    labels = np.array(labels)

    # First split: train+val vs test
    train_val_paths, test_paths, train_val_labels, test_labels = \
        train_test_split(
            file_paths, labels,
            test_size=TEST_SPLIT,
            random_state=seed,
            stratify=labels
        )

    # Second split: train vs val
    val_ratio = VAL_SPLIT / (TRAIN_SPLIT + VAL_SPLIT)
    train_paths, val_paths, train_labels, val_labels = \
        train_test_split(
            train_val_paths, train_val_labels,
            test_size=val_ratio,
            random_state=seed,
            stratify=train_val_labels
        )

    print(f"\n  Dataset Split:")
    print(f"    Train     : {len(train_paths):>6} ({TRAIN_SPLIT * 100:.0f}%)")
    print(f"    Validation: {len(val_paths):>6} ({VAL_SPLIT * 100:.0f}%)")
    print(f"    Test      : {len(test_paths):>6} ({TEST_SPLIT * 100:.0f}%)")

    return (train_paths, train_labels,
            val_paths, val_labels,
            test_paths, test_labels)


# =============================================================================
# CLASS IMBALANCE HANDLING - RESAMPLING
# =============================================================================

def balance_dataset(paths: np.ndarray, labels: np.ndarray):
    """
    Apply oversampling and/or undersampling to balance the dataset.
    Only applies to the training set.
    """
    if not USE_OVERSAMPLING and not USE_UNDERSAMPLING:
        return paths, labels

    print("\n  Applying Dataset Balancing:")
    unique_classes = np.unique(labels)
    balanced_paths = []
    balanced_labels = []

    for cls in unique_classes:
        cls_indices = np.where(labels == cls)[0]
        cls_paths = paths[cls_indices]
        count = len(cls_paths)

        # 1. Undersampling (truncate majority classes)
        if USE_UNDERSAMPLING and count > MAX_SAMPLES_PER_CLASS:
            np.random.shuffle(cls_paths)
            cls_paths = cls_paths[:MAX_SAMPLES_PER_CLASS]
            print(f"    Class {cls:2d}: Undersampled {count} -> {MAX_SAMPLES_PER_CLASS}")
            count = MAX_SAMPLES_PER_CLASS

        # 2. Oversampling (repeat minority classes)
        if USE_OVERSAMPLING and count < MIN_SAMPLES_PER_CLASS:
            # Calculate how many times to repeat
            repeats = int(np.ceil(MIN_SAMPLES_PER_CLASS / count))
            cls_paths = np.tile(cls_paths, repeats)[:MIN_SAMPLES_PER_CLASS]
            print(f"    Class {cls:2d}: Oversampled  {count} -> {MIN_SAMPLES_PER_CLASS}")
            count = MIN_SAMPLES_PER_CLASS

        balanced_paths.extend(cls_paths)
        balanced_labels.extend([cls] * count)

    # Shuffle the final balanced dataset
    indices = np.arange(len(balanced_paths))
    np.random.shuffle(indices)

    final_paths = np.array(balanced_paths)[indices]
    final_labels = np.array(balanced_labels)[indices]

    print(f"  [OK] Balanced Training Set Size: {len(final_paths)}")
    return final_paths, final_labels


# =============================================================================
# IMAGE LOADING & PREPROCESSING
# =============================================================================

def load_and_preprocess_image(file_path: tf.Tensor,
                               label: tf.Tensor,
                               num_classes: int):
    """Load image from disk, resize, and one-hot encode label."""
    # Read and decode image
    img_bytes = tf.io.read_file(file_path)
    image = tf.image.decode_image(img_bytes, channels=3, expand_animations=False)
    image = tf.image.resize(image, [IMG_SIZE, IMG_SIZE])
    image = tf.cast(image, tf.float32)  # Keep in [0, 255] range

    # One-hot encode label for categorical crossentropy
    label = tf.one_hot(label, num_classes)

    return image, label


# =============================================================================
# TF.DATA PIPELINE BUILDERS
# =============================================================================

def build_train_dataset(file_paths, labels, num_classes: int,
                        apply_realworld_aug: bool = True):
    """
    Build optimized training tf.data pipeline.
    Applies both Keras GPU augmentation and OpenCV real-world augmentation.
    """
    dataset = tf.data.Dataset.from_tensor_slices(
        (file_paths.tolist(), labels.tolist())
    )

    # Shuffle the full dataset
    dataset = dataset.shuffle(
        buffer_size=len(file_paths),
        reshuffle_each_iteration=True
    )

    # Load and preprocess images (parallel)
    dataset = dataset.map(
        lambda fp, lbl: load_and_preprocess_image(fp, lbl, num_classes),
        num_parallel_calls=AUTOTUNE
    )

    # Apply real-world augmentation (OpenCV, runs on CPU via py_function)
    if apply_realworld_aug:
        dataset = dataset.map(
            lambda img, lbl: (tf_real_world_augmentation(img, IMG_SIZE), lbl),
            num_parallel_calls=AUTOTUNE
        )

    # Apply EfficientNet preprocessing
    dataset = dataset.map(
        lambda img, lbl: (
            tf.keras.applications.efficientnet.preprocess_input(img), lbl
        ),
        num_parallel_calls=AUTOTUNE
    )

    # Batch, prefetch for GPU throughput
    dataset = dataset.batch(BATCH_SIZE, drop_remainder=True)

    # Apply Keras augmentation layer (GPU-accelerated) on batched data
    keras_aug = build_keras_augmentation_layer(IMG_SIZE)
    dataset = dataset.map(
        lambda img, lbl: (keras_aug(img, training=True), lbl),
        num_parallel_calls=AUTOTUNE
    )

    dataset = dataset.prefetch(AUTOTUNE)

    return dataset


def build_eval_dataset(file_paths, labels, num_classes: int):
    """
    Build evaluation tf.data pipeline (no augmentation).
    Used for validation and test sets.
    """
    dataset = tf.data.Dataset.from_tensor_slices(
        (file_paths.tolist(), labels.tolist())
    )

    dataset = dataset.map(
        lambda fp, lbl: load_and_preprocess_image(fp, lbl, num_classes),
        num_parallel_calls=AUTOTUNE
    )

    # Apply EfficientNet preprocessing
    dataset = dataset.map(
        lambda img, lbl: (
            tf.keras.applications.efficientnet.preprocess_input(img), lbl
        ),
        num_parallel_calls=AUTOTUNE
    )

    dataset = dataset.batch(BATCH_SIZE)
    dataset = dataset.prefetch(AUTOTUNE)

    return dataset


# =============================================================================
# MAIN DATASET PREPARATION FUNCTION
# =============================================================================

def prepare_datasets():
    """
    Full dataset preparation pipeline:
    1. Discover dataset structure
    2. Split into train/val/test
    3. Apply balancing (oversampling/undersampling) to train set
    4. Compute class weights
    5. Build tf.data pipelines
    6. Save class names
    """
    # Discover
    class_names, file_paths, labels, class_counts = \
        discover_dataset(DATASET_DIR)
    num_classes = len(class_names)

    # Split (stratified)
    (train_paths, train_labels,
     val_paths, val_labels,
     test_paths, test_labels) = split_dataset(file_paths, labels)

    # Print summary & Imbalance Analysis
    print_dataset_summary(
        class_names, class_counts,
        len(train_paths), len(val_paths), len(test_paths)
    )

    # Handle Imbalance - Balance only the training set
    train_paths, train_labels = balance_dataset(train_paths, train_labels)

    # Compute class weights for training
    class_weights = compute_class_weights(train_labels)

    # Save class names for inference consistency
    save_class_names(class_names, CLASS_NAMES_PATH)

    # Build tf.data pipelines
    print("\n  Building tf.data pipelines...")
    train_ds = build_train_dataset(train_paths, train_labels, num_classes)
    val_ds = build_eval_dataset(val_paths, val_labels, num_classes)
    test_ds = build_eval_dataset(test_paths, test_labels, num_classes)
    print("  [OK] All pipelines ready")

    return (train_ds, val_ds, test_ds,
            class_names, class_weights, num_classes,
            test_paths, test_labels)
