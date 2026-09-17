package com.gbw.android.separation

import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.pytorch.executorch.DType
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

/**
 * Thin ExecuTorch boundary for the exact BS-RoFormer mask core.
 *
 * The module remains open across chunks. Input is backed by the same direct
 * buffer filled by JNI STFT; output is copied into one reusable direct mask
 * buffer to avoid a 100+ MiB FloatArray allocation per model call.
 */
internal class BsRoformerExecuTorch private constructor(
    private val module: Module,
) : Closeable {
    fun forwardMasks(stft: ByteBuffer, masks: ByteBuffer) {
        require(stft.isDirect && stft.order() == ByteOrder.nativeOrder())
        require(masks.isDirect && masks.order() == ByteOrder.nativeOrder())
        require(stft.capacity() >= BsRoformerSpectralNative.stftBytes)
        require(masks.capacity() >= BsRoformerSpectralNative.maskBytes)

        stft.clear()
        val inputFloats = stft.asFloatBuffer()
        inputFloats.clear()
        val input = Tensor.fromBlob(inputFloats, INPUT_SHAPE)

        val outputs = module.forward(EValue.from(input))
        check(outputs.size == 1) {
            "BS-RoFormer ExecuTorch returned " + outputs.size + " outputs; expected 1"
        }
        val output = outputs[0].toTensor()
        check(output.dtype() == DType.FLOAT) {
            "BS-RoFormer output dtype changed: " + output.dtype()
        }
        check(output.shape().contentEquals(OUTPUT_SHAPE)) {
            "BS-RoFormer output shape changed: " + output.shape().contentToString()
        }
        check(output.numel() == BsRoformerContract.MASK_FLOATS.toLong()) {
            "BS-RoFormer output element count changed: " + output.numel()
        }

        masks.clear()
        val maskFloats = masks.asFloatBuffer()
        maskFloats.clear()
        output.copyDataInto(maskFloats)
        masks.clear()
    }

    override fun close() {
        module.close()
    }

    companion object {
        private val INPUT_SHAPE = longArrayOf(
            1,
            BsRoformerContract.CHANNELS.toLong(),
            BsRoformerContract.STFT_BINS.toLong(),
            BsRoformerContract.PRODUCTION_STFT_TIME_FRAMES.toLong(),
            BsRoformerContract.COMPLEX_COMPONENTS.toLong(),
        )

        private val OUTPUT_SHAPE = longArrayOf(
            1,
            BsRoformerContract.STEM_COUNT.toLong(),
            (BsRoformerContract.STFT_BINS * BsRoformerContract.CHANNELS).toLong(),
            BsRoformerContract.PRODUCTION_STFT_TIME_FRAMES.toLong(),
            BsRoformerContract.COMPLEX_COMPONENTS.toLong(),
        )

        fun open(pteFile: File): BsRoformerExecuTorch {
            require(pteFile.isFile) { "BS-RoFormer .pte file not found" }
            require(pteFile.length() > 0L) { "BS-RoFormer .pte file is empty" }
            return BsRoformerExecuTorch(Module.load(pteFile.absolutePath))
        }
    }
}
