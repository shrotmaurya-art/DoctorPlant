package com.plantcure.ai.domain.classifier

import android.content.Context
import android.graphics.Bitmap
import com.plantcure.ai.domain.model.DetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.nio.MappedByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device TFLite plant disease classifier.
 *
 * Loads the model once (singleton), runs inference on any Bitmap,
 * returns top-3 detection results with severity levels.
 *
 * Works 100% offline — no internet required.
 */
@Singleton
class PlantDiseaseClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var modelBuffer: MappedByteBuffer? = null

    // Image preprocessing pipeline: resize → normalize to [0, 1]
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 1f))
        .build()

    /**
     * Initialize the TFLite interpreter. Call early (e.g., Application.onCreate).
     */
    @Synchronized
    fun initialize() {
        if (interpreter != null) return

        try {
            modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer!!, options)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to load TFLite model: ${e.message}", e)
        }
    }

    /**
     * Run inference on a bitmap.
     *
     * @param bitmap Input image (any size — will be resized to 224x224)
     * @return Top 3 detection results sorted by confidence, or empty list on error
     */
    fun runInference(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) initialize()
        val interp = interpreter ?: return emptyList()

        return try {
            // Create a FLOAT32 TensorImage so the buffer has 602112 bytes instead of 150528
            val tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
            tensorImage.load(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // Allocate output buffer (38 classes)
            val output = Array(1) { FloatArray(PLANT_VILLAGE_LABELS.size) }

            // Run inference
            interp.run(processedImage.buffer, output)

            // Map outputs to labeled results, sorted by confidence descending
            val topResults = output[0]
                .mapIndexed { index, score ->
                    DetectionResult(
                        label = PLANT_VILLAGE_LABELS[index],
                        confidence = score,
                        severity = DetectionResult.computeSeverity(score)
                    )
                }
                .sortedByDescending { it.confidence }
                .take(3)

            if (topResults.isNotEmpty() && topResults[0].confidence < 0.65f) {
                listOf(DetectionResult(
                    label = "not_a_plant",
                    confidence = topResults[0].confidence,
                    severity = "None"
                ))
            } else {
                topResults
            }

        } catch (oom: OutOfMemoryError) {
            // OOM recovery: GC and retry once
            System.gc()
            try {
                val tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
                tensorImage.load(bitmap)
                val processedImage = imageProcessor.process(tensorImage)
                val output = Array(1) { FloatArray(PLANT_VILLAGE_LABELS.size) }
                interp.run(processedImage.buffer, output)
                val topResults = output[0]
                    .mapIndexed { index, score ->
                        DetectionResult(
                            label = PLANT_VILLAGE_LABELS[index],
                            confidence = score,
                            severity = DetectionResult.computeSeverity(score)
                        )
                    }
                    .sortedByDescending { it.confidence }
                    .take(3)

                if (topResults.isNotEmpty() && topResults[0].confidence < 0.65f) {
                    listOf(DetectionResult(
                        label = "not_a_plant",
                        confidence = topResults[0].confidence,
                        severity = "None"
                    ))
                } else {
                    topResults
                }
            } catch (e: Exception) {
                throw e
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Release the interpreter and model resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        modelBuffer = null
    }

    companion object {
        private const val MODEL_FILENAME = "plant_disease_model.tflite"
        private const val INPUT_SIZE = 224

        /**
         * The 18 class labels corresponding to the current TFLite model output.
         */
        val PLANT_VILLAGE_LABELS = listOf(
            "Pepper__bell___Bacterial_spot",
            "Pepper__bell___healthy",
            "Potato___Early_blight",
            "Potato___Late_blight",
            "Potato___healthy",
            "Tomato_Bacterial_spot",
            "Tomato_Early_blight",
            "Tomato_Late_blight",
            "Tomato_Leaf_Mold",
            "Tomato_Septoria_leaf_spot",
            "Tomato_Spider_mites_Two_spotted_spider_mite",
            "Tomato__Target_Spot",
            "Tomato__Tomato_YellowLeaf__Curl_Virus",
            "Tomato__Tomato_mosaic_virus",
            "Tomato_healthy",
            "rice Bacterial leaf blight",
            "rice Brown spot",
            "rice Leaf smut"
        )
    }
}
