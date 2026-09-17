package com.gbw.android.domain

enum class WorkflowStep(val publicLabel: String) {
    SOURCE("Fonte"),
    SEPARATION("Separação"),
    TUNING("Afinação & Pitch"),
    EXPORT("Exportação")
}

object WorkflowRules {
    fun tuningValid(config: WorkflowConfig): Boolean = when (config.pitchMode) {
        PitchMode.TUNING -> config.originalTuning.isNotBlank() &&
            config.targetTuning.isNotBlank() &&
            Tunings.delta(config.originalTuning, config.targetTuning) != null
        PitchMode.MANUAL -> config.semitones in -12..12
        PitchMode.NONE -> true
    }

    fun states(doc: ProjectDocument): Map<WorkflowStep, Boolean> {
        val source = doc.state.sourcePrepared
        val separation = source && doc.state.separationComplete
        val tuning = separation && doc.state.tuningConfirmed && tuningValid(doc.config)
        val export = tuning && doc.state.exportComplete
        return linkedMapOf(
            WorkflowStep.SOURCE to source,
            WorkflowStep.SEPARATION to separation,
            WorkflowStep.TUNING to tuning,
            WorkflowStep.EXPORT to export,
        )
    }

    fun activeStep(doc: ProjectDocument): WorkflowStep =
        states(doc).entries.firstOrNull { !it.value }?.key ?: WorkflowStep.EXPORT
}
