import com.gbw.android.domain.*

fun main() {
    check(Tunings.names.size == 18)
    check(Tunings.delta("Drop B", "Drop D") == 3)
    check(Tunings.delta("Drop D", "Drop B") == -3)
    check(Tunings.delta("E Standard", "Drop D") == null)
    check(TextNormalization.titleCase("WOLVES AT THE GATE") == "Wolves At The Gate")
    check(TextNormalization.searchKey("Árvore") == "arvore")
    check(SeparationMode.androidDefault == SeparationMode.QUICK)

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
        config = WorkflowConfig(originalTuning = "Drop B", targetTuning = "Drop D"),
        state = ProjectState(sourcePrepared = true, separationComplete = true, tuningConfirmed = true)
    )
    check(WorkflowRules.states(doc)[WorkflowStep.TUNING] == true)
    check(WorkflowRules.activeStep(doc) == WorkflowStep.EXPORT)

    check(FilePitchRules.suggestedName(
        "guitarra.wav", "Drop D", "Drop B", -3, OutputFormat.WAV_FLOAT32
    ) == "guitarra_DropD_para_DropB.wav")

    println("DOMAIN_SMOKE_OK")
}
