/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.ext_config.ui.fw_upgrade

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.models.Boards
import com.st.blue_sdk.services.ota.FirmwareType
import com.st.blue_sdk.services.ota.FwFileDescriptor
import com.st.blue_sdk.services.ota.FwUpdateListener
import com.st.blue_sdk.services.ota.FwUploadError
import com.st.blue_sdk.services.ota.UpgradeStrategy
import com.st.blue_sdk.services.ota.characteristic.CharacteristicFwUpgrade
import com.st.blue_sdk.utils.WbOTAUtils
import com.st.ext_config.download.DownloadAPI
import com.st.ext_config.model.FwUpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class FwUpgradeViewModel
@Inject internal constructor(
    private val blueManager: BlueManager,
    private val downloadAPI: DownloadAPI,
    @ApplicationContext context: Context
) : ViewModel() {

    companion object {
        const val TAG = "FwDownloadViewModel"
        private const val SEC_IN_MILLIS = 1000f
        private const val BUFFER_ARRAY_SIZE = 4 * 1024
    }

    private val contentResolver = context.contentResolver
    private val filesDir = context.filesDir

    private val _fwUpdateState: MutableStateFlow<FwUpdateState> = MutableStateFlow(FwUpdateState())
    val fwUpdateState: StateFlow<FwUpdateState> = _fwUpdateState.asStateFlow()

    private val otaListener = object : FwUpdateListener {
        private var time: Long? = null

        override fun onUpdate(progress: Float) {
            Log.d(TAG, "update progress $progress")
            if (time == null) {
                time = System.currentTimeMillis()
            }
            _fwUpdateState.value =
                _fwUpdateState.value.copy(isInProgress = true, progress = progress)
        }

        override fun onComplete() {
            Log.d(TAG, "COMPLETE")

            val duration = time?.let {
                (System.currentTimeMillis() - it) / SEC_IN_MILLIS
            } ?: 0f

            _fwUpdateState.value =
                _fwUpdateState.value.copy(
                    isComplete = true,
                    duration = duration
                )
        }

        override fun onError(error: FwUploadError) {
            _fwUpdateState.value =
                _fwUpdateState.value.copy(isInProgress = false, progress = null, error = error)
        }
    }

    fun changeFile(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()

            _fwUpdateState.value = _fwUpdateState.value.copy(
                fwUri = uri,
                downloadFinished = true,
                fwName = cursor.getString(nameIndex),
                fwSize = cursor.getLong(sizeIndex).toString()
            )
        }
    }

    fun startUpgradeFW(
        nodeId: String,
        boardType: WbOTAUtils.WBBoardType?,
        fwType: FirmwareType?
    ) {
        viewModelScope.launch {
            _fwUpdateState.value = _fwUpdateState.value.copy(isInProgress = true)

            _fwUpdateState.value.fwUri?.let {
                val fileDescriptor =
                    FwFileDescriptor(fileUri = it, resolver = contentResolver)

                val params = if (boardType != null && fwType != null) {
                    CharacteristicFwUpgrade.buildFwUpgradeParams(
                        firmwareType = fwType,
                        boardType = boardType,
                        fileDescriptor = fileDescriptor
                    )
                } else {
                    null
                }

                blueManager.upgradeFw(nodeId = nodeId)?.launchFirmwareUpgrade(
                    nodeId = nodeId,
                    fwType = FirmwareType.BOARD_FW,
                    fileDescriptor = fileDescriptor,
                    params = params,
                    fwUpdateListener = otaListener
                )
            }
        }
    }

    fun clearFwUpdateState() {
        _fwUpdateState.value = FwUpdateState()
    }

    fun startDemo(nodeId: String, fwUrl: String) {
        viewModelScope.launch {
            if (_fwUpdateState.value.fwUri == null) {
                _fwUpdateState.value = _fwUpdateState.value.copy(downloadFinished = false)
                try {
                    val responseBody = downloadAPI.downloadFile(fwUrl).body()
                    val fileName: String = fwUrl.substring(fwUrl.lastIndexOf("/") + 1)
                    val filePath = filesDir.absolutePath + fileName
                    val downloaded = saveFile(responseBody, filePath)
                    Uri.fromFile(File(filePath))?.let {
                        _fwUpdateState.value = _fwUpdateState.value.copy(
                            fwUri = it,
                            downloadFinished = downloaded,
                            fwName = fileName,
                            boardInfo = blueManager.getFwVersion(nodeId = nodeId)
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
            }
        }
    }

    private fun saveFile(body: ResponseBody?, path: String): Boolean {
        if (body != null) {
            var input: InputStream? = null
            try {
                input = body.byteStream()
                // val file = File(getCacheDir(), "cacheFileAppeal.srl")
                val fos = FileOutputStream(path)
                fos.use { output ->
                    val buffer = ByteArray(BUFFER_ARRAY_SIZE) // or other buffer size
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            } finally {
                input?.close()
            }
        }
        return false
    }

    fun stopDemo(nodeId: String) {
        Log.d(TAG, "stop demo for node $nodeId")
        /** NOOP **/
    }

    fun isWbOta(nodeId: String): Boolean =
        blueManager.getFwUpdateStrategy(nodeId = nodeId) == UpgradeStrategy.CHARACTERISTIC

    fun isWbaBoard(nodeId: String): Boolean =
        blueManager.getNode(nodeId = nodeId)?.boardType == Boards.Model.WBA_BOARD
}
