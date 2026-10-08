// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.models

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qtekfun.ultimatekeys.voicemodels.DownloadFailure
import com.qtekfun.ultimatekeys.voicemodels.DownloadOutcome
import com.qtekfun.ultimatekeys.voicemodels.DownloadState
import com.qtekfun.ultimatekeys.voicemodels.ModelDownloader
import com.qtekfun.ultimatekeys.voicemodels.ModelProvisioner
import com.qtekfun.ultimatekeys.voicemodels.ModelSpec
import com.qtekfun.ultimatekeys.voicemodels.modelCatalog
import com.qtekfun.ultimatekeys.voicemodels.modelStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * The `lite` flavor: nothing is bundled; models come from the pinned URLs in `models.json`
 * through WorkManager. This source set also holds the INTERNET permission (its own manifest).
 */

/** Nothing ships inside the lite APK. */
fun installBundledModels(context: Context) = Unit

fun createProvisioner(context: Context): ModelProvisioner =
    WorkManagerProvisioner(context.applicationContext)

private const val TAG = "uk-model-download"
private const val MODEL_TAG = "uk-model:"
private const val KEY_MODEL = "model"
private const val KEY_BYTES = "bytes"
private const val KEY_TOTAL = "total"
private const val KEY_REASON = "reason"
private const val MAX_ATTEMPTS = 5
private const val BACKOFF_SECONDS = 30L

/** Schedules [ModelDownloadWorker]s: resumable, only with the network the person allowed. */
private class WorkManagerProvisioner(private val context: Context) : ModelProvisioner {
    private val work get() = WorkManager.getInstance(context)

    override val usesNetwork = true

    override val states: Flow<Map<String, DownloadState>> = work.getWorkInfosByTagFlow(
        TAG
    ).map { infos ->
        infos.mapNotNull { info ->
            info.tags.firstOrNull { it.startsWith(MODEL_TAG) }?.removePrefix(MODEL_TAG)?.let {
                it to
                    stateOf(info)
            }
        }.toMap()
    }

    override fun canProvide(spec: ModelSpec) = true

    override fun start(spec: ModelSpec, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(KEY_MODEL to spec.id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(MODEL_TAG + spec.id)
            .build()
        work.enqueueUniqueWork(uniqueName(spec), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(spec: ModelSpec) {
        work.cancelUniqueWork(uniqueName(spec))
        context.modelStore().deletePartial(spec)
    }

    private fun uniqueName(spec: ModelSpec) = "model-download-${spec.id}"

    private fun stateOf(info: WorkInfo): DownloadState = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued

        WorkInfo.State.RUNNING -> DownloadState.Downloading(
            info.progress.getLong(KEY_BYTES, 0),
            info.progress.getLong(KEY_TOTAL, 0)
        )

        WorkInfo.State.SUCCEEDED -> DownloadState.Completed

        WorkInfo.State.FAILED -> DownloadState.Failed(failureOf(info.outputData))

        WorkInfo.State.CANCELLED -> DownloadState.Idle
    }

    private fun failureOf(data: Data): DownloadFailure = data.getString(KEY_REASON)?.let { name ->
        DownloadFailure.entries.firstOrNull {
            it.name ==
                name
        }
    }
        ?: DownloadFailure.NETWORK
}

/**
 * Runs the download state machine of `:voice-models` over `HttpURLConnection`. Interrupted
 * transfers keep their partial file, so every retry and every new run resumes where it stopped.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val spec = inputData.getString(KEY_MODEL)?.let(applicationContext.modelCatalog()::find)
            ?: return Result.failure(workDataOf(KEY_REASON to DownloadFailure.SERVER_REFUSED.name))
        val downloader = ModelDownloader(applicationContext.modelStore(), HttpUrlConnectionSource())
        val outcome = downloader.download(spec) { state ->
            if (state is DownloadState.Downloading) {
                setProgressAsync(workDataOf(KEY_BYTES to state.bytes, KEY_TOTAL to state.total))
            }
        }
        return when (outcome) {
            DownloadOutcome.Success -> Result.success()

            is DownloadOutcome.Failure ->
                if (outcome.retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_REASON to outcome.reason.name))
                }
        }
    }
}
