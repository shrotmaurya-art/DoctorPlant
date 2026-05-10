"""
=============================================================================
train.py - Production Training Pipeline for Plant Disease Detection
=============================================================================
Two-phase training strategy:
  Phase 1: Transfer learning with frozen EfficientNetB0 base
  Phase 2: Fine-tuning with unfrozen top layers

GPU-ONLY execution with mixed precision, XLA, and optimized data loading.
=============================================================================
"""

import os
import sys
import json
import time

# ============================================================================
# GPU MUST BE CONFIGURED BEFORE ANY TF IMPORT IN SUBMODULES
# ============================================================================
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "1"  # Suppress INFO logs from TF

# Register CUDA DLLs before TF initialization
from utils import register_cuda_dlls
register_cuda_dlls()

import tensorflow as tf

from config import (
    IMG_SIZE, IMG_SHAPE, BATCH_SIZE,
    PHASE1_EPOCHS, PHASE1_LEARNING_RATE, PHASE1_MIN_LR,
    PHASE2_EPOCHS, PHASE2_LEARNING_RATE, PHASE2_MIN_LR,
    FINE_TUNE_AT_LAYER, DROPOUT_RATE, LABEL_SMOOTHING,
    EARLY_STOPPING_PATIENCE, REDUCE_LR_PATIENCE, REDUCE_LR_FACTOR,
    BEST_MODEL_PATH, FINAL_MODEL_PATH, PHASE1_MODEL_PATH,
    TRAINING_HISTORY_PATH, CSV_LOG_PATH,
    TENSORBOARD_DIR, CHECKPOINT_DIR, LOG_DIR,
    ENABLE_MIXED_PRECISION
)
from utils import enforce_gpu, setup_logging, save_training_history
from dataset import prepare_datasets


# =============================================================================
# ENFORCE GPU - EXITS IF NOT AVAILABLE
# =============================================================================
gpus = enforce_gpu()
logger = setup_logging(LOG_DIR, "training")


# =============================================================================
# MODEL ARCHITECTURE
# =============================================================================

def build_model(num_classes: int, fine_tune: bool = False,
                fine_tune_at: int = FINE_TUNE_AT_LAYER):
    """
    Build EfficientNetB0 with custom classification head.

    Architecture:
        EfficientNetB0 (pretrained ImageNet) -> GlobalAveragePooling
        -> Dense(512) + BN + ReLU + Dropout
        -> Dense(256) + BN + ReLU + Dropout
        -> Dense(num_classes, float32 softmax)  # float32 for mixed precision stability
    """
    logger.info("Building EfficientNetB0 model...")

    # Base model - pretrained on ImageNet
    base_model = tf.keras.applications.EfficientNetB0(
        include_top=False,
        weights="imagenet",
        input_shape=IMG_SHAPE,
        pooling=None
    )

    # Freeze/unfreeze base layers
    if fine_tune:
        base_model.trainable = True
        for layer in base_model.layers[:fine_tune_at]:
            layer.trainable = False
        trainable_count = sum(
            1 for l in base_model.layers if l.trainable
        )
        logger.info(
            f"  Fine-tuning mode: {trainable_count} layers trainable "
            f"(unfrozen from layer {fine_tune_at})"
        )
    else:
        base_model.trainable = False
        logger.info("  Transfer learning mode: base model FROZEN")

    # Custom classification head
    inputs = tf.keras.Input(shape=IMG_SHAPE, name="input_image")
    x = base_model(inputs, training=fine_tune)
    x = tf.keras.layers.GlobalAveragePooling2D(name="global_pool")(x)

    x = tf.keras.layers.Dense(512, name="fc1")(x)
    x = tf.keras.layers.BatchNormalization(name="bn1")(x)
    x = tf.keras.layers.Activation("relu", name="relu1")(x)
    x = tf.keras.layers.Dropout(DROPOUT_RATE, name="dropout1")(x)

    x = tf.keras.layers.Dense(256, name="fc2")(x)
    x = tf.keras.layers.BatchNormalization(name="bn2")(x)
    x = tf.keras.layers.Activation("relu", name="relu2")(x)
    x = tf.keras.layers.Dropout(DROPOUT_RATE, name="dropout2")(x)

    # Output layer - use float32 explicitly for numerical stability
    # with mixed precision training
    x = tf.keras.layers.Dense(num_classes, name="logits")(x)
    outputs = tf.keras.layers.Activation(
        "softmax", dtype="float32", name="predictions"
    )(x)

    model = tf.keras.Model(inputs, outputs, name="PlantDiseaseDetector")

    logger.info(f"  Total parameters   : {model.count_params():,}")
    trainable_params = sum(
        tf.keras.backend.count_params(w) for w in model.trainable_weights
    )
    logger.info(f"  Trainable parameters: {trainable_params:,}")
    # Fix TF 2.10 EagerTensor serialization bug in Keras applications (like EfficientNet)
    # Patch get_config instead of mutating properties to avoid float16 dtype conflicts
    def patch_get_config(layer):
        orig_get_config = getattr(layer, 'get_config')
        def new_get_config():
            config = orig_get_config()
            for k, v in config.items():
                if isinstance(v, tf.Tensor):
                    config[k] = v.numpy().tolist()
                elif type(v).__name__ == 'ndarray':
                    config[k] = v.tolist()
            return config
        layer.get_config = new_get_config

    def _apply_patch(m):
        for layer in m.layers:
            patch_get_config(layer)
            if hasattr(layer, 'layers'):
                _apply_patch(layer)

    _apply_patch(model)

    return model, base_model

# =============================================================================
# COMPILE MODEL
# =============================================================================

def compile_model(model, learning_rate: float):
    """Compile with Adam optimizer, categorical crossentropy + label smoothing."""
    optimizer = tf.keras.optimizers.Adam(learning_rate=learning_rate)

    model.compile(
        optimizer=optimizer,
        loss=tf.keras.losses.CategoricalCrossentropy(
            label_smoothing=LABEL_SMOOTHING
        ),
        metrics=[
            tf.keras.metrics.CategoricalAccuracy(name="accuracy"),
            tf.keras.metrics.TopKCategoricalAccuracy(k=3, name="top3_accuracy"),
        ]
    )
    logger.info(f"  Compiled with lr={learning_rate}, "
                f"label_smoothing={LABEL_SMOOTHING}")
    return model


# =============================================================================
# CALLBACKS
# =============================================================================

def get_callbacks(phase: str):
    """Build callback list for training phase."""
    callbacks = [
        # Save best model by validation accuracy
        tf.keras.callbacks.ModelCheckpoint(
            filepath=BEST_MODEL_PATH,
            monitor="val_accuracy",
            save_best_only=True,
            mode="max",
            verbose=1
        ),

        # Early stopping to prevent overfitting
        tf.keras.callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=EARLY_STOPPING_PATIENCE,
            restore_best_weights=True,
            verbose=1,
            mode="max"
        ),

        # Reduce learning rate on plateau
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=REDUCE_LR_FACTOR,
            patience=REDUCE_LR_PATIENCE,
            min_lr=PHASE1_MIN_LR if phase == "phase1" else PHASE2_MIN_LR,
            verbose=1
        ),

        # TensorBoard logging
        tf.keras.callbacks.TensorBoard(
            log_dir=os.path.join(TENSORBOARD_DIR, phase),
            histogram_freq=1,
            write_graph=True,
            update_freq="epoch"
        ),

        # CSV logger for training metrics
        tf.keras.callbacks.CSVLogger(
            CSV_LOG_PATH,
            append=(phase == "phase2")
        ),

        # Checkpoint every epoch
        tf.keras.callbacks.ModelCheckpoint(
            filepath=os.path.join(
                CHECKPOINT_DIR,
                f"{phase}_epoch_{{epoch:02d}}_val_acc_{{val_accuracy:.4f}}.keras"
            ),
            save_freq="epoch",
            verbose=0
        ),
    ]
    return callbacks


# =============================================================================
# TRAINING PHASES
# =============================================================================

def train_phase1(model, train_ds, val_ds, class_weights):
    """
    Phase 1: Transfer Learning
    Train only the custom head with frozen EfficientNetB0 base.
    """
    logger.info("\n" + "=" * 70)
    logger.info("  PHASE 1: TRANSFER LEARNING (Base Frozen)")
    logger.info("=" * 70)

    model = compile_model(model, PHASE1_LEARNING_RATE)
    callbacks = get_callbacks("phase1")

    start_time = time.time()

    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=PHASE1_EPOCHS,
        callbacks=callbacks,
        class_weight=class_weights,
        verbose=1
    )

    elapsed = time.time() - start_time
    logger.info(f"\n  Phase 1 completed in {elapsed / 60:.1f} minutes")

    # Save phase 1 model
    model.save(PHASE1_MODEL_PATH)
    logger.info(f"  Phase 1 model saved to {PHASE1_MODEL_PATH}")

    return model, history.history


def train_phase2(model, base_model, train_ds, val_ds, class_weights):
    """
    Phase 2: Fine-Tuning
    Unfreeze top layers of EfficientNetB0 and train with lower learning rate.
    """
    logger.info("\n" + "=" * 70)
    logger.info("  PHASE 2: FINE-TUNING (Partial Unfreeze)")
    logger.info("=" * 70)

    # Unfreeze base model from specified layer
    base_model.trainable = True
    for layer in base_model.layers[:FINE_TUNE_AT_LAYER]:
        layer.trainable = False

    trainable_count = sum(1 for l in model.layers if l.trainable)
    logger.info(f"  Unfrozen layers from index {FINE_TUNE_AT_LAYER}")
    logger.info(f"  Total trainable layers: {trainable_count}")

    # Recompile with lower learning rate
    model = compile_model(model, PHASE2_LEARNING_RATE)
    callbacks = get_callbacks("phase2")

    start_time = time.time()

    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=PHASE2_EPOCHS,
        callbacks=callbacks,
        class_weight=class_weights,
        verbose=1
    )

    elapsed = time.time() - start_time
    logger.info(f"\n  Phase 2 completed in {elapsed / 60:.1f} minutes")

    return model, history.history


# =============================================================================
# MAIN EXECUTION
# =============================================================================

def main():
    """Full training pipeline execution."""
    logger.info("\n" + "#" * 70)
    logger.info("  PLANT DISEASE DETECTION - TRAINING PIPELINE")
    logger.info("#" * 70)

    total_start = time.time()

    # ------ Prepare Dataset ------
    (train_ds, val_ds, test_ds,
     class_names, class_weights, num_classes,
     test_paths, test_labels) = prepare_datasets()

    logger.info(f"\n  Number of classes: {num_classes}")
    logger.info(f"  Classes: {class_names}")

    # ------ Build Model (Phase 1: Frozen) ------
    model, base_model = build_model(num_classes, fine_tune=False)
    model.summary(print_fn=logger.info)

    # ------ Phase 1: Transfer Learning ------
    model, phase1_history = train_phase1(
        model, train_ds, val_ds, class_weights
    )

    # ------ Phase 2: Fine-Tuning ------
    model, phase2_history = train_phase2(
        model, base_model, train_ds, val_ds, class_weights
    )

    # ------ Save Final Model ------
    model.save(FINAL_MODEL_PATH)
    logger.info(f"\n  [OK] Final model saved to {FINAL_MODEL_PATH}")

    # ------ Save Combined Training History ------
    combined_history = {}
    for key in phase1_history:
        combined_history[f"phase1_{key}"] = phase1_history[key]
    for key in phase2_history:
        combined_history[f"phase2_{key}"] = phase2_history[key]

    save_training_history(combined_history, TRAINING_HISTORY_PATH)

    # ------ Quick Test Evaluation ------
    logger.info("\n  Running test set evaluation...")
    test_results = model.evaluate(test_ds, verbose=1)
    logger.info(f"  Test Loss    : {test_results[0]:.4f}")
    logger.info(f"  Test Accuracy: {test_results[1]:.4f}")
    logger.info(f"  Test Top-3   : {test_results[2]:.4f}")

    total_elapsed = time.time() - total_start
    logger.info(f"\n  Total training time: {total_elapsed / 60:.1f} minutes")

    logger.info("\n" + "#" * 70)
    logger.info("  TRAINING COMPLETE - Run evaluate.py for full analysis")
    logger.info("#" * 70 + "\n")


if __name__ == "__main__":
    main()
