package com.gbw.android.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object BackupOperationLock {
    private val mutex = Mutex()
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
