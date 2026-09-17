package com.gbw.android.separation

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native spectral seam around the ExecuTorch BS-RoFormer core.
 *
 * Large tensors use direct ByteBuffers so JNI and ExecuTorch can share native
 * storage without creating 100+ MiB FloatArray copies for every model chunk.
 */
internal object BsRoformerSpectralNative {
    val stftBytes: Int = BsRoformerContract.STFT_BYTES
    val maskBytes: Int = BsRoformerContract.MASK_BYTES
    val outputFloats: Int = BsRoformerContract.OUTPUT_FLOATS
    val outputBytes: Int = BsRoformerContract.OUTPUT_BYTES

    init {
        System.loadLibrary("gbw_bsroformer_spectral")
    }

    fun allocateStftBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(stftBytes).order(ByteOrder.nativeOrder())

    fun allocateMaskBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(maskBytes).order(ByteOrder.nativeOrder())

    fun allocateOutputBuffer(): ByteBuffer =
        ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())

    fun stft(interleavedStereo: FloatArray, destination: ByteBuffer) {
        require(interleavedStereo.size == BsRoformerContract.CHUNK_FRAMES * BsRoformerContract.CHANNELS) {
            "BS-RoFormer STFT requires one fixed 588800-frame stereo chunk"
        }
        requireDirect(destination, stftBytes, "STFT")
        destination.clear()
        nativeStft(interleavedStereo, destination)
        destination.clear()
    }

    fun applyMasksAndIstft(
        stft: ByteBuffer,
        masks: ByteBuffer,
        output: ByteBuffer,
    ) {
        requireDirect(stft, stftBytes, "STFT")
        requireDirect(masks, maskBytes, "mask")
        requireDirect(output, outputBytes, "output")
        stft.clear()
        masks.clear()
        output.clear()
        nativeApplyMasksAndIstft(stft, masks, output)
        output.clear()
    }

    fun identity(): String = nativeIdentity()

    private fun requireDirect(buffer: ByteBuffer, minimumBytes: Int, label: String) {
        require(buffer.isDirect) { label + " must be a direct ByteBuffer" }
        require(buffer.capacity() >= minimumBytes) {
            label + " buffer has " + buffer.capacity() +
                " bytes; expected at least " + minimumBytes
        }
        require(buffer.order() == ByteOrder.nativeOrder()) {
            label + " buffer must use native byte order"
        }
    }

    private external fun nativeStft(
        interleavedStereo: FloatArray,
        destination: ByteBuffer,
    )

    private external fun nativeApplyMasksAndIstft(
        stft: ByteBuffer,
        masks: ByteBuffer,
        output: ByteBuffer,
    )

    private external fun nativeIdentity(): String
}
