package com.gbw.android.project

import org.json.JSONArray
import org.json.JSONObject

internal object ProjectJson {
    fun encode(project: ProjectManifest): String {
        val json = JSONObject()
            .put("schemaVersion", ProjectManifest.CURRENT_SCHEMA_VERSION)
            .put("projectId", project.projectId)
            .put("name", project.name)
            .put("artist", project.artist)
            .put("song", project.song)
            .put("createdAtEpochMs", project.createdAtEpochMs)
            .put("updatedAtEpochMs", project.updatedAtEpochMs)
            .put("workflowStage", project.workflowStage)
            .put("inventory", JSONArray().also { array ->
                project.inventory.sortedBy { it.relativePath }.forEach { artifact ->
                    array.put(
                        JSONObject()
                            .put("relativePath", artifact.relativePath)
                            .put("size", artifact.size)
                            .put("sha256", artifact.sha256)
                            .put("modifiedAtEpochMs", artifact.modifiedAtEpochMs)
                    )
                }
            })

        project.lastSyncedRevisionId?.let { json.put("lastSyncedRevisionId", it) }

        project.source?.let { source ->
            json.put(
                "source",
                JSONObject()
                    .put("originalRelativePath", source.originalRelativePath)
                    .put("preparedRelativePath", source.preparedRelativePath)
                    .put("provenanceUri", source.provenanceUri)
                    .put("sourceUrl", source.sourceUrl)
                    .put("title", source.title)
                    .put("formatId", source.formatId)
                    .put("durationSeconds", source.durationSeconds)
            )
        }

        project.separation?.let { separation ->
            json.put(
                "separation",
                JSONObject()
                    .put("engine", separation.engine)
                    .put("jobId", separation.jobId)
                    .put("completedAtEpochMs", separation.completedAtEpochMs)
                    .put("frames", separation.frames)
                    .put("elapsedMillis", separation.elapsedMillis)
                    .put("runtimeIdentity", separation.runtimeIdentity)
                    .put("blasThreads", separation.blasThreads)
                    .put(
                        "stems",
                        JSONObject().also { stems ->
                            separation.stems.toSortedMap().forEach { (role, path) ->
                                stems.put(role, path)
                            }
                        },
                    )
            )
        }

        project.export?.let { export ->
            json.put(
                "export",
                JSONObject()
                    .put("exportId", export.exportId)
                    .put("revision", export.revision)
                    .put("outputFormat", export.outputFormat)
                    .put("manifestRelativePath", export.manifestRelativePath)
                    .put(
                        "artifacts",
                        JSONArray().also { array ->
                            export.artifacts.forEach { artifact ->
                                array.put(
                                    JSONObject()
                                        .put("role", artifact.role)
                                        .put("variant", artifact.variant)
                                        .put("relativePath", artifact.relativePath)
                                        .put("format", artifact.format)
                                        .put("sampleRate", artifact.sampleRate)
                                        .put("channels", artifact.channels)
                                        .put("durationFrames", artifact.durationFrames)
                                        .put("size", artifact.size)
                                        .put("sha256", artifact.sha256)
                                )
                            }
                        },
                    )
            )
        }

        return json.toString(2)
    }

    fun decode(text: String): ProjectManifest {
        val json = JSONObject(text)
        val rawSchemaVersion = json.optInt("schemaVersion", 1)
        require(rawSchemaVersion in 1..ProjectManifest.CURRENT_SCHEMA_VERSION) {
            "Schema de projeto não suportado: $rawSchemaVersion"
        }

        val source = json.optJSONObject("source")?.let { sourceJson ->
            ManagedSource(
                originalRelativePath = normalizeRelativePath(sourceJson.getString("originalRelativePath")),
                preparedRelativePath =
                    sourceJson.optStringOrNull("preparedRelativePath")?.let(::normalizeRelativePath),
                provenanceUri = sourceJson.optStringOrNull("provenanceUri"),
                sourceUrl = sourceJson.optStringOrNull("sourceUrl"),
                title = sourceJson.optString("title", ""),
                formatId = sourceJson.optString("formatId", ""),
                durationSeconds = sourceJson.optDouble("durationSeconds", 0.0),
            )
        }

        val separation = json.optJSONObject("separation")?.let { separationJson ->
            val stemsJson = separationJson.optJSONObject("stems") ?: JSONObject()
            val stems = buildMap {
                stemsJson.keys().forEach { role ->
                    put(role, normalizeRelativePath(stemsJson.getString(role)))
                }
            }
            SeparationState(
                engine = separationJson.optString("engine", "htdemucs_6s"),
                jobId = separationJson.getString("jobId"),
                completedAtEpochMs = separationJson.optLong("completedAtEpochMs", 0L),
                frames = separationJson.optLong("frames", 0L),
                elapsedMillis = separationJson.optLong("elapsedMillis", 0L),
                runtimeIdentity = separationJson.optString("runtimeIdentity", ""),
                blasThreads = separationJson.optInt("blasThreads", 1),
                stems = stems,
            )
        }

        val export =
            if (rawSchemaVersion >= 2) {
                json.optJSONObject("export")?.let { exportJson ->
                    val artifacts = mutableListOf<ExportArtifact>()
                    val array = exportJson.optJSONArray("artifacts") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val artifactJson = array.getJSONObject(index)
                        artifacts += ExportArtifact(
                            role = artifactJson.getString("role"),
                            variant = artifactJson.optString("variant", "original"),
                            relativePath = normalizeRelativePath(artifactJson.getString("relativePath")),
                            format = artifactJson.getString("format"),
                            sampleRate = artifactJson.getInt("sampleRate"),
                            channels = artifactJson.getInt("channels"),
                            durationFrames = artifactJson.getLong("durationFrames"),
                            size = artifactJson.getLong("size"),
                            sha256 = artifactJson.getString("sha256"),
                        )
                    }
                    ExportState(
                        exportId = exportJson.getString("exportId"),
                        revision = exportJson.optLong("revision", 1L),
                        outputFormat = exportJson.optString("outputFormat", "FLAC_24"),
                        artifacts = artifacts,
                        manifestRelativePath =
                            normalizeRelativePath(exportJson.getString("manifestRelativePath")),
                    )
                }
            } else {
                null
            }

        val inventory = mutableListOf<DurableArtifact>()
        val inventoryJson = json.optJSONArray("inventory") ?: JSONArray()
        for (index in 0 until inventoryJson.length()) {
            val artifactJson = inventoryJson.getJSONObject(index)
            inventory += DurableArtifact(
                relativePath = normalizeRelativePath(artifactJson.getString("relativePath")),
                size = artifactJson.getLong("size"),
                sha256 = artifactJson.getString("sha256"),
                modifiedAtEpochMs = artifactJson.optLong("modifiedAtEpochMs", 0L),
            )
        }

        val storedStage = json.optString("workflowStage", "SOURCE")
        val migratedStage =
            if (rawSchemaVersion < 2) {
                if (separation != null) "SEPARATION" else "SOURCE"
            } else {
                storedStage
            }

        return ProjectManifest(
            schemaVersion = rawSchemaVersion,
            projectId = json.getString("projectId").lowercase(),
            name = sanitizeProjectName(json.optString("name", "Projeto sem nome")),
            artist = json.optString("artist", ""),
            song = json.optString("song", ""),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
            workflowStage = migratedStage,
            source = source,
            separation = separation,
            export = export,
            inventory = inventory,
            lastSyncedRevisionId =
                if (rawSchemaVersion < 2) null else json.optStringOrNull("lastSyncedRevisionId"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
