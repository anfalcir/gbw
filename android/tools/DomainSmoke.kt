import com.gbw.android.domain.*

fun main() {
    check(TextNormalization.titleCase("WOLVES AT THE GATE") == "Wolves At The Gate")
    check(TextNormalization.searchKey("Árvore") == "arvore")

    val ideal = AudioQualityRules.classify(
        displayName = "guitar.wav", format = "WAV", codec = "pcm_f32le",
        sampleRate = 48_000, channels = 1, bitDepth = 32,
        isFloat = true, isLossless = true, peakDbfs = -4.0,
    )
    check(ideal.status == QualityStatus.IDEAL)

    val adequate = AudioQualityRules.classify(
        displayName = "guitar.flac", format = "FLAC", codec = "flac",
        sampleRate = 48_000, channels = 1, bitDepth = 24,
        isFloat = false, isLossless = true, peakDbfs = -4.0,
    )
    check(adequate.status == QualityStatus.ADEQUATE)

    val caution = AudioQualityRules.classify(
        displayName = "guitar.mp3", format = "MP3", codec = "mp3",
        sampleRate = 44_100, channels = 2, bitDepth = null,
        isFloat = false, isLossless = false, peakDbfs = -2.0,
    )
    check(caution.status == QualityStatus.CAUTION)
    check(caution.issues.any { it.title == "Formato com perdas" })

    val doc = ProjectDocument(
        state = ProjectState(sourcePrepared = true, separationComplete = true)
    )
    check(WorkflowRules.states(doc)[WorkflowStep.SEPARATION] == true)
    check(WorkflowRules.activeStep(doc) == WorkflowStep.EXPORT)

    println("DOMAIN_SMOKE_OK")
}
