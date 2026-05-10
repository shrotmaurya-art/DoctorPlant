"""
=============================================================================
augmentations.py - Real-World Mobile Camera Augmentation Pipeline
=============================================================================
Custom OpenCV augmentation functions simulating real mobile camera conditions:
  - Gaussian blur, motion blur
  - Random noise, JPEG compression artifacts
  - Shadow overlay, low-light simulation
  - Contrast/brightness variation, color temperature shifts
  - Perspective distortion, random cropping

These augmentations are applied ON TOP of standard Keras augmentation to
ensure the model generalizes to blurry, noisy, poorly-lit mobile photos.
=============================================================================
"""

import cv2
import numpy as np
import tensorflow as tf


# =============================================================================
# INDIVIDUAL AUGMENTATION FUNCTIONS (OpenCV-based)
# =============================================================================

def gaussian_blur(image: np.ndarray, kernel_max: int = 7) -> np.ndarray:
    """Apply random Gaussian blur simulating out-of-focus mobile photos."""
    k = np.random.choice([3, 5, kernel_max])
    return cv2.GaussianBlur(image, (k, k), 0)


def motion_blur(image: np.ndarray, kernel_size: int = 15) -> np.ndarray:
    """Apply directional motion blur simulating hand shake."""
    k = np.random.randint(5, kernel_size + 1)
    kernel = np.zeros((k, k), dtype=np.float32)

    # Random direction: horizontal, vertical, or diagonal
    direction = np.random.choice(["h", "v", "d"])
    if direction == "h":
        kernel[k // 2, :] = 1.0
    elif direction == "v":
        kernel[:, k // 2] = 1.0
    else:
        np.fill_diagonal(kernel, 1.0)

    kernel /= k
    return cv2.filter2D(image, -1, kernel)


def random_noise(image: np.ndarray, std_max: float = 25.0) -> np.ndarray:
    """Add Gaussian noise simulating low-quality mobile sensors."""
    std = np.random.uniform(5.0, std_max)
    noise = np.random.normal(0, std, image.shape).astype(np.float32)
    noisy = image.astype(np.float32) + noise
    return np.clip(noisy, 0, 255).astype(np.uint8)


def dark_image_simulation(image: np.ndarray) -> np.ndarray:
    """Simulate low-light conditions by reducing brightness."""
    factor = np.random.uniform(0.3, 0.7)
    dark = image.astype(np.float32) * factor
    return np.clip(dark, 0, 255).astype(np.uint8)


def jpeg_compression_simulation(image: np.ndarray,
                                 quality_min: int = 30) -> np.ndarray:
    """Simulate JPEG compression artifacts from mobile camera saves."""
    quality = np.random.randint(quality_min, 80)
    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), quality]
    _, encoded = cv2.imencode(".jpg", image, encode_param)
    return cv2.imdecode(encoded, cv2.IMREAD_COLOR)


def shadow_overlay(image: np.ndarray,
                   intensity: float = 0.5) -> np.ndarray:
    """Simulate random shadow patches (tree shadows, hand shadows)."""
    h, w = image.shape[:2]
    shadow_mask = np.ones((h, w), dtype=np.float32)

    # Create 1-3 random shadow rectangles
    num_shadows = np.random.randint(1, 4)
    for _ in range(num_shadows):
        x1 = np.random.randint(0, w // 2)
        y1 = np.random.randint(0, h // 2)
        x2 = np.random.randint(w // 2, w)
        y2 = np.random.randint(h // 2, h)
        shadow_val = np.random.uniform(1.0 - intensity, 1.0)
        shadow_mask[y1:y2, x1:x2] = shadow_val

    # Blur shadow edges for realism
    shadow_mask = cv2.GaussianBlur(shadow_mask, (21, 21), 0)
    shadowed = image.astype(np.float32) * shadow_mask[:, :, np.newaxis]
    return np.clip(shadowed, 0, 255).astype(np.uint8)


def contrast_reduction(image: np.ndarray) -> np.ndarray:
    """Reduce contrast simulating hazy/foggy conditions."""
    factor = np.random.uniform(0.5, 0.8)
    mean = np.mean(image)
    reduced = image.astype(np.float32) * factor + mean * (1.0 - factor)
    return np.clip(reduced, 0, 255).astype(np.uint8)


def brightness_variation(image: np.ndarray) -> np.ndarray:
    """Random brightness adjustment simulating varying light conditions."""
    factor = np.random.uniform(0.6, 1.4)
    bright = image.astype(np.float32) * factor
    return np.clip(bright, 0, 255).astype(np.uint8)


def contrast_variation(image: np.ndarray) -> np.ndarray:
    """Random contrast adjustment."""
    factor = np.random.uniform(0.7, 1.3)
    mean = np.mean(image, axis=(0, 1), keepdims=True)
    adjusted = (image.astype(np.float32) - mean) * factor + mean
    return np.clip(adjusted, 0, 255).astype(np.uint8)


def color_temperature_shift(image: np.ndarray) -> np.ndarray:
    """Subtle color temperature shift (warm/cool) simulating different lighting."""
    shift = np.random.uniform(-15, 15)
    result = image.astype(np.float32).copy()

    if shift > 0:
        # Warm (increase red, decrease blue)
        result[:, :, 2] = np.clip(result[:, :, 2] + shift, 0, 255)  # R
        result[:, :, 0] = np.clip(result[:, :, 0] - shift * 0.5, 0, 255)  # B
    else:
        # Cool (increase blue, decrease red)
        result[:, :, 0] = np.clip(result[:, :, 0] - shift, 0, 255)  # B
        result[:, :, 2] = np.clip(result[:, :, 2] + shift * 0.5, 0, 255)  # R

    return result.astype(np.uint8)


def perspective_distortion(image: np.ndarray) -> np.ndarray:
    """Simulate slight perspective changes from different camera angles."""
    h, w = image.shape[:2]
    max_shift = int(min(h, w) * 0.08)

    src_pts = np.float32([[0, 0], [w, 0], [0, h], [w, h]])
    dst_pts = np.float32([
        [np.random.randint(0, max_shift), np.random.randint(0, max_shift)],
        [w - np.random.randint(0, max_shift), np.random.randint(0, max_shift)],
        [np.random.randint(0, max_shift), h - np.random.randint(0, max_shift)],
        [w - np.random.randint(0, max_shift), h - np.random.randint(0, max_shift)]
    ])

    matrix = cv2.getPerspectiveTransform(src_pts, dst_pts)
    warped = cv2.warpPerspective(image, matrix, (w, h),
                                  borderMode=cv2.BORDER_REFLECT_101)
    return warped


def random_crop_and_resize(image: np.ndarray, target_size: int = 224) -> np.ndarray:
    """Random crop followed by resize to target size."""
    h, w = image.shape[:2]
    crop_ratio = np.random.uniform(0.75, 0.95)
    crop_h = int(h * crop_ratio)
    crop_w = int(w * crop_ratio)

    y = np.random.randint(0, h - crop_h + 1)
    x = np.random.randint(0, w - crop_w + 1)

    cropped = image[y:y + crop_h, x:x + crop_w]
    resized = cv2.resize(cropped, (target_size, target_size),
                         interpolation=cv2.INTER_LINEAR)
    return resized


# =============================================================================
# COMPOSITE AUGMENTATION PIPELINE
# =============================================================================

def apply_real_world_augmentation(image: np.ndarray,
                                  target_size: int = 224) -> np.ndarray:
    """
    Apply a random subset of real-world augmentations.
    Each augmentation is applied with independent probability to create
    diverse training samples mimicking actual mobile camera conditions.
    """
    # Ensure uint8 for OpenCV operations
    if image.dtype != np.uint8:
        image = np.clip(image * 255.0, 0, 255).astype(np.uint8) \
            if image.max() <= 1.0 else image.astype(np.uint8)

    # Apply augmentations with random probability
    if np.random.random() < 0.3:
        image = gaussian_blur(image)

    if np.random.random() < 0.15:
        image = motion_blur(image)

    if np.random.random() < 0.3:
        image = random_noise(image)

    if np.random.random() < 0.2:
        image = dark_image_simulation(image)

    if np.random.random() < 0.25:
        image = jpeg_compression_simulation(image)

    if np.random.random() < 0.25:
        image = shadow_overlay(image)

    if np.random.random() < 0.2:
        image = contrast_reduction(image)

    if np.random.random() < 0.3:
        image = brightness_variation(image)

    if np.random.random() < 0.3:
        image = contrast_variation(image)

    if np.random.random() < 0.15:
        image = color_temperature_shift(image)

    if np.random.random() < 0.15:
        image = perspective_distortion(image)

    if np.random.random() < 0.2:
        image = random_crop_and_resize(image, target_size)

    return image


# =============================================================================
# TF.DATA-COMPATIBLE WRAPPER
# =============================================================================

def tf_real_world_augmentation(image: tf.Tensor,
                                target_size: int = 224) -> tf.Tensor:
    """
    Wraps the OpenCV augmentation pipeline for use inside tf.data.
    Uses tf.py_function to bridge numpy operations.
    """
    def _augment(img_tensor):
        img_np = img_tensor.numpy()
        # Convert from float [0,1] to uint8 if needed
        if img_np.max() <= 1.0:
            img_np = (img_np * 255.0).astype(np.uint8)
        augmented = apply_real_world_augmentation(img_np, target_size)
        # Return as float32 [0, 255] - preprocessing happens later
        return augmented.astype(np.float32)

    augmented = tf.py_function(
        func=_augment,
        inp=[image],
        Tout=tf.float32
    )
    augmented.set_shape([target_size, target_size, 3])
    return augmented


# =============================================================================
# STANDARD KERAS AUGMENTATION LAYER (GPU-accelerated)
# =============================================================================

def build_keras_augmentation_layer(img_size: int = 224):
    """
    Build a Keras Sequential augmentation model that runs on GPU.
    These are standard geometric/color transforms accelerated by GPU.
    """
    from tensorflow.keras import layers, Sequential

    return Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.08),       # ~30 degrees
        layers.RandomZoom((-0.25, 0.0)),
        layers.RandomTranslation(0.2, 0.2),
        layers.RandomContrast(0.2),
        layers.RandomBrightness(0.2),
    ], name="keras_augmentation")
