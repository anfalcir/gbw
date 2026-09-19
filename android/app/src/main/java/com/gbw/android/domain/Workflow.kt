package com.gbw.android.domain

enum class WorkflowStep(val publicLabel: String) {
    SOURCE("Fonte"),
    SEPARATION("Separação"),
    EXPORT("Exportação")
}

object WorkflowRules {
    fun states(doc: ProjectDocument): Map<WorkflowStep, Boolean> {
        val source = doc.state.sourcePrepared
        val separation = source && doc.state.separationComplete
        val export = separation && doc.state.exportComplete
        return linkedMapOf(
            WorkflowStep.SOURCE to source,
            WorkflowStep.SEPARATION to separation,
            WorkflowStep.EXPORT to export,
        )
    }

    fun activeStep(doc: ProjectDocument): WorkflowStep =
        states(doc).entries.firstOrNull { !it.value }?.key ?: WorkflowStep.EXPORT
}
