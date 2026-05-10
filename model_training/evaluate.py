"""
=============================================================================
evaluate.py - Comprehensive Model Evaluation & Visualization
=============================================================================
Generates:
  - Accuracy/Loss training curves
  - Confusion matrix heatmap
  - Classification report (precision, recall, F1)
  - Per-class accuracy breakdown
  - Top-k accuracy analysis
=============================================================================
"""

import os
import sys
import json
import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "1"

# Register CUDA DLLs before TF initialization
from utils import register_cuda_dlls
if sys.platform == "win32":
    register_cuda_dlls()

import tensorflow as tf
import matplotlib
matplotlib.use("Agg")  # Non-interactive backend for server/headless
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import (
    classification_report, confusion_matrix,
    precision_recall_fscore_support, accuracy_score
)

from config import (
    BEST_MODEL_PATH, FINAL_MODEL_PATH, CLASS_NAMES_PATH,
    TRAINING_HISTORY_PATH, PLOTS_DIR, LOG_DIR, IMG_SIZE, BATCH_SIZE,
    DATASET_DIR
)
from utils import enforce_gpu, setup_logging, load_class_names, load_training_history
from dataset import discover_dataset, split_dataset, build_eval_dataset


# =============================================================================
# GPU SETUP
# =============================================================================
gpus = enforce_gpu()
logger = setup_logging(LOG_DIR, "evaluation")


# =============================================================================
# PLOT TRAINING HISTORY
# =============================================================================

def plot_training_curves(history: dict, save_dir: str):
    """Plot accuracy and loss curves for both training phases."""
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle("Plant Disease Detection - Training History",
                 fontsize=16, fontweight="bold")

    # ------ Phase 1: Accuracy ------
    if "phase1_accuracy" in history:
        ax = axes[0, 0]
        ax.plot(history["phase1_accuracy"], label="Train Accuracy", linewidth=2)
        ax.plot(history["phase1_val_accuracy"], label="Val Accuracy", linewidth=2)
        ax.set_title("Phase 1: Transfer Learning - Accuracy")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Accuracy")
        ax.legend()
        ax.grid(True, alpha=0.3)

    # ------ Phase 1: Loss ------
    if "phase1_loss" in history:
        ax = axes[0, 1]
        ax.plot(history["phase1_loss"], label="Train Loss", linewidth=2)
        ax.plot(history["phase1_val_loss"], label="Val Loss", linewidth=2)
        ax.set_title("Phase 1: Transfer Learning - Loss")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Loss")
        ax.legend()
        ax.grid(True, alpha=0.3)

    # ------ Phase 2: Accuracy ------
    if "phase2_accuracy" in history:
        ax = axes[1, 0]
        ax.plot(history["phase2_accuracy"], label="Train Accuracy", linewidth=2)
        ax.plot(history["phase2_val_accuracy"], label="Val Accuracy", linewidth=2)
        ax.set_title("Phase 2: Fine-Tuning - Accuracy")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Accuracy")
        ax.legend()
        ax.grid(True, alpha=0.3)

    # ------ Phase 2: Loss ------
    if "phase2_loss" in history:
        ax = axes[1, 1]
        ax.plot(history["phase2_loss"], label="Train Loss", linewidth=2)
        ax.plot(history["phase2_val_loss"], label="Val Loss", linewidth=2)
        ax.set_title("Phase 2: Fine-Tuning - Loss")
        ax.set_xlabel("Epoch")
        ax.set_ylabel("Loss")
        ax.legend()
        ax.grid(True, alpha=0.3)

    plt.tight_layout()
    path = os.path.join(save_dir, "training_curves.png")
    plt.savefig(path, dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"  [OK] Training curves saved to {path}")


# =============================================================================
# CONFUSION MATRIX
# =============================================================================

def plot_confusion_matrix(y_true, y_pred, class_names, save_dir):
    """Generate and save confusion matrix heatmap."""
    cm = confusion_matrix(y_true, y_pred)

    # Normalize for better visualization
    cm_normalized = cm.astype("float") / cm.sum(axis=1)[:, np.newaxis]

    fig, axes = plt.subplots(1, 2, figsize=(24, 10))

    # Raw counts
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues",
                xticklabels=class_names, yticklabels=class_names,
                ax=axes[0])
    axes[0].set_title("Confusion Matrix (Counts)", fontsize=14)
    axes[0].set_xlabel("Predicted")
    axes[0].set_ylabel("True")
    axes[0].tick_params(axis="x", rotation=45)
    axes[0].tick_params(axis="y", rotation=0)

    # Normalized (percentage)
    sns.heatmap(cm_normalized, annot=True, fmt=".2f", cmap="YlOrRd",
                xticklabels=class_names, yticklabels=class_names,
                ax=axes[1])
    axes[1].set_title("Confusion Matrix (Normalized)", fontsize=14)
    axes[1].set_xlabel("Predicted")
    axes[1].set_ylabel("True")
    axes[1].tick_params(axis="x", rotation=45)
    axes[1].tick_params(axis="y", rotation=0)

    plt.tight_layout()
    path = os.path.join(save_dir, "confusion_matrix.png")
    plt.savefig(path, dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"  [OK] Confusion matrix saved to {path}")


# =============================================================================
# PER-CLASS ACCURACY BAR CHART
# =============================================================================

def plot_per_class_accuracy(y_true, y_pred, class_names, save_dir):
    """Generate per-class accuracy bar chart."""
    cm = confusion_matrix(y_true, y_pred)
    per_class_acc = cm.diagonal() / cm.sum(axis=1)

    # Sort by accuracy for better visualization
    sorted_indices = np.argsort(per_class_acc)
    sorted_names = [class_names[i] for i in sorted_indices]
    sorted_acc = per_class_acc[sorted_indices]

    fig, ax = plt.subplots(figsize=(12, 8))
    colors = plt.cm.RdYlGn(sorted_acc)
    bars = ax.barh(range(len(sorted_names)), sorted_acc, color=colors)

    ax.set_yticks(range(len(sorted_names)))
    ax.set_yticklabels(sorted_names, fontsize=9)
    ax.set_xlabel("Accuracy", fontsize=12)
    ax.set_title("Per-Class Accuracy", fontsize=14, fontweight="bold")
    ax.set_xlim(0, 1.0)
    ax.axvline(x=0.9, color="green", linestyle="--", alpha=0.5, label="90%")
    ax.axvline(x=0.8, color="orange", linestyle="--", alpha=0.5, label="80%")
    ax.legend()

    # Add percentage labels
    for i, (acc, bar) in enumerate(zip(sorted_acc, bars)):
        ax.text(acc + 0.01, i, f"{acc:.1%}", va="center", fontsize=8)

    plt.tight_layout()
    path = os.path.join(save_dir, "per_class_accuracy.png")
    plt.savefig(path, dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"  [OK] Per-class accuracy chart saved to {path}")


# =============================================================================
# FULL EVALUATION
# =============================================================================

def run_evaluation():
    """Run comprehensive model evaluation."""
    logger.info("\n" + "#" * 70)
    logger.info("  PLANT DISEASE DETECTION - FULL EVALUATION")
    logger.info("#" * 70)

    # ------ Load model ------
    model_path = BEST_MODEL_PATH if os.path.exists(BEST_MODEL_PATH) \
        else FINAL_MODEL_PATH
    logger.info(f"\n  Loading model from {model_path}")
    model = tf.keras.models.load_model(model_path)

    # ------ Load class names ------
    class_names = load_class_names(CLASS_NAMES_PATH)
    num_classes = len(class_names)
    logger.info(f"  Classes: {num_classes}")

    # ------ Prepare test dataset ------
    _, file_paths, labels, _ = discover_dataset(DATASET_DIR)
    (_, _, _, _, test_paths, test_labels) = split_dataset(file_paths, labels)
    test_ds = build_eval_dataset(test_paths, test_labels, num_classes)

    # ------ Model evaluation ------
    logger.info("\n  Evaluating on test set...")
    test_results = model.evaluate(test_ds, verbose=1)
    logger.info(f"  Test Loss     : {test_results[0]:.4f}")
    logger.info(f"  Test Accuracy : {test_results[1]:.4f}")
    if len(test_results) > 2:
        logger.info(f"  Test Top-3 Acc: {test_results[2]:.4f}")

    # ------ Generate predictions ------
    logger.info("\n  Generating predictions...")
    y_pred_probs = model.predict(test_ds, verbose=1)
    y_pred = np.argmax(y_pred_probs, axis=1)
    y_true = np.array(test_labels)

    # ------ Classification Report ------
    logger.info("\n" + "=" * 70)
    logger.info("  CLASSIFICATION REPORT")
    logger.info("=" * 70)
    report = classification_report(
        y_true, y_pred,
        target_names=class_names,
        digits=4
    )
    logger.info(f"\n{report}")

    # Save report to file
    report_path = os.path.join(PLOTS_DIR, "classification_report.txt")
    with open(report_path, "w") as f:
        f.write(report)
    logger.info(f"  [OK] Classification report saved to {report_path}")

    # ------ Precision, Recall, F1 ------
    precision, recall, f1, support = precision_recall_fscore_support(
        y_true, y_pred, average="weighted"
    )
    logger.info(f"\n  Weighted Precision: {precision:.4f}")
    logger.info(f"  Weighted Recall   : {recall:.4f}")
    logger.info(f"  Weighted F1-Score : {f1:.4f}")
    logger.info(f"  Overall Accuracy  : {accuracy_score(y_true, y_pred):.4f}")

    # ------ Per-class metrics ------
    precision_per, recall_per, f1_per, _ = precision_recall_fscore_support(
        y_true, y_pred, average=None
    )
    logger.info("\n  Per-Class Metrics:")
    logger.info(f"  {'Class':<50} {'Prec':>6} {'Rec':>6} {'F1':>6}")
    logger.info("-" * 70)
    for i, name in enumerate(class_names):
        logger.info(
            f"  {name:<50} {precision_per[i]:>6.4f} "
            f"{recall_per[i]:>6.4f} {f1_per[i]:>6.4f}"
        )

    # ------ Plot Training History ------
    if os.path.exists(TRAINING_HISTORY_PATH):
        history = load_training_history(TRAINING_HISTORY_PATH)
        plot_training_curves(history, PLOTS_DIR)
    else:
        logger.warning("  Training history not found, skipping curves")

    # ------ Plot Confusion Matrix ------
    plot_confusion_matrix(y_true, y_pred, class_names, PLOTS_DIR)

    # ------ Plot Per-Class Accuracy ------
    plot_per_class_accuracy(y_true, y_pred, class_names, PLOTS_DIR)

    # ------ Summary ------
    logger.info("\n" + "#" * 70)
    logger.info("  EVALUATION COMPLETE")
    logger.info(f"  All plots saved to {PLOTS_DIR}")
    logger.info("#" * 70 + "\n")


if __name__ == "__main__":
    run_evaluation()
