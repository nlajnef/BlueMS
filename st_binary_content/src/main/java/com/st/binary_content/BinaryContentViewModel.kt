/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.binary_content

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.board_catalog.models.DtmiContent
import com.st.blue_sdk.features.extended.binary_content.BinaryContent
import com.st.blue_sdk.features.extended.binary_content.BinaryContentCommand
import com.st.blue_sdk.features.extended.binary_content.RawData
import com.st.blue_sdk.features.extended.pnpl.PnPL
import com.st.blue_sdk.features.extended.pnpl.PnPLConfig
import com.st.blue_sdk.features.extended.pnpl.request.PnPLCmd
import com.st.blue_sdk.features.extended.pnpl.request.PnPLCommand
import com.st.ui.composables.CommandRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.io.FileNotFoundException
import java.io.IOError
import javax.inject.Inject

@HiltViewModel
class BinaryContentViewModel @Inject constructor(
    private val blueManager: BlueManager,
    private val coroutineScope: CoroutineScope,
    private val application: Application
) : ViewModel() {

    private var observeFeaturePnPLJob: Job? = null

    private val _modelUpdates =
        mutableStateOf<List<Pair<DtmiContent.DtmiComponentContent, DtmiContent.DtmiInterfaceContent>>>(
            emptyList()
        )
    val modelUpdates: State<List<Pair<DtmiContent.DtmiComponentContent, DtmiContent.DtmiInterfaceContent>>>
        get() = _modelUpdates

    private val _componentStatusUpdates = mutableStateOf<List<JsonObject>>(emptyList())
    val componentStatusUpdates: State<List<JsonObject>>
        get() = _componentStatusUpdates

    private val _isLoading = mutableStateOf(value = false)
    val isLoading: State<Boolean>
        get() = _isLoading

    private val _enableCollapse = mutableStateOf(value = false)
    val enableCollapse: State<Boolean>
        get() = _enableCollapse

    private val _lastStatusUpdatedAt = mutableStateOf(value = 0L)
    val lastStatusUpdatedAt: State<Long>
        get() = _lastStatusUpdatedAt

    private var byteArrayContentToBoard: ByteArray? = null
    private var byteArrayContentFromBoard: ByteArray? = null

    private val _binaryContentReceived = mutableStateOf(value = false)
    val binaryContentReceived: State<Boolean>
        get() = _binaryContentReceived

    private val _binaryContentReadyForSending = mutableStateOf(value = false)
    val binaryContentReadyForSending: State<Boolean>
        get() = _binaryContentReadyForSending

    private val _fileOperationResultVisible = mutableStateOf(value = false)
    val fileOperationResultVisible: State<Boolean>
        get() = _fileOperationResultVisible

    var fileOperationResult: String? = null

    private fun sendGetAllCommand(nodeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val feature =
                blueManager.nodeFeatures(nodeId = nodeId).find { it.name == PnPL.NAME }
                    ?: return@launch

            if (feature is PnPL) {
                blueManager.writeFeatureCommand(
                    nodeId = nodeId,
                    featureCommand = PnPLCommand(feature = feature, cmd = PnPLCmd.ALL)
                )
            }
        }
    }

    fun setDtmiModel(nodeId: String, fileUri: Uri) {
        viewModelScope.launch {
            _modelUpdates.value = blueManager.setDtmiModel(
                nodeId = nodeId,
                fileUri = fileUri,
                contentResolver = application.contentResolver
            )?.extractComponent(compName = "control") ?: emptyList()
        }
    }

    fun getModel(nodeId: String, compName: String) {
        viewModelScope.launch {
            _isLoading.value = true

            _modelUpdates.value =
                blueManager.getDtmiModel(nodeId = nodeId)?.extractComponent(compName = compName)
                    ?: emptyList()

            _enableCollapse.value = false

            _isLoading.value = false

            sendGetAllCommand(nodeId = nodeId)
        }
    }

    fun sendCommand(nodeId: String, name: String, value: CommandRequest?) {
        viewModelScope.launch {
            _isLoading.value = true

            val feature =
                blueManager.nodeFeatures(nodeId = nodeId).find { it.name == PnPL.NAME }
                    ?: return@launch

            if (feature is PnPL) {
                value?.let {
                    blueManager.writeFeatureCommand(
                        nodeId = nodeId,
                        featureCommand = PnPLCommand(
                            feature = feature,
                            cmd = PnPLCmd(
                                component = name,
                                command = it.commandName,
                                fields = it.request
                            )
                        )
                    )

                    sendGetAllCommand(nodeId = nodeId)
                }
            }
        }
    }

    fun sendChange(nodeId: String, name: String, value: Pair<String, Any>) {
        viewModelScope.launch {
            _isLoading.value = true

            val feature =
                blueManager.nodeFeatures(nodeId = nodeId).find { it.name == PnPL.NAME }
                    ?: return@launch

            if (feature is PnPL) {
                value.let {
                    val featureCommand = PnPLCommand(
                        feature = feature,
                        cmd = PnPLCmd(
                            command = name,
                            fields = mapOf(it)
                        )
                    )

                    blueManager.writeFeatureCommand(
                        nodeId = nodeId,
                        featureCommand = featureCommand
                    )

                    sendGetAllCommand(nodeId = nodeId)
                }
            }
        }
    }

    fun hideFileOperation() {
        _fileOperationResultVisible.value = false
    }

    fun readBinaryContent(file: Uri?) {
        val contentResolver = application.contentResolver
        _binaryContentReadyForSending.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = file?.let { contentResolver.openInputStream(it) } ?: return@launch
                // val stream = file?.let { contentResolver.openInputStream(it) }
                // stream?.let {
                byteArrayContentToBoard = stream.readBytes()
                stream.close()
                fileOperationResult = "File read correctly"
                _fileOperationResultVisible.value = true
                _binaryContentReadyForSending.value = true
                // }
                return@launch
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                fileOperationResult = "File not Found"
                _fileOperationResultVisible.value = true
                return@launch
            } catch (e: IOError) {
                e.printStackTrace()
                fileOperationResult = "File I/O error"
                _fileOperationResultVisible.value = true
                return@launch
            }
        }
    }

    fun saveBinaryContent(file: Uri?) {
        val contentResolver = application.contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = file?.let { contentResolver.openOutputStream(it) } ?: return@launch

                stream.write(byteArrayContentFromBoard)
                stream.close()
                fileOperationResult = "File written correctly"
                _binaryContentReceived.value = false
                _fileOperationResultVisible.value = true
                return@launch
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                fileOperationResult = "File not found"
                _fileOperationResultVisible.value = true
                return@launch
            } catch (e: IOError) {
                e.printStackTrace()
                fileOperationResult = "File I/O error"
                _fileOperationResultVisible.value = true
                return@launch
            }
        }
    }

    fun writeBinaryContentToNode(nodeId: String) {
        if (byteArrayContentToBoard != null) {
            blueManager.nodeFeatures(nodeId = nodeId).find {
                it.name == BinaryContent.NAME
            }?.let {
                val feature = it as BinaryContent
                viewModelScope.launch(Dispatchers.IO) {
                    blueManager.writeFeatureCommand(
                        nodeId = nodeId,
                        featureCommand = BinaryContentCommand(
                            feature = feature,
                            data = byteArrayContentToBoard!!
                        )
                    )
                    byteArrayContentToBoard = null
                }
            }
        }
    }

    fun startDemo(nodeId: String) {
        observeFeaturePnPLJob?.cancel()

        blueManager.nodeFeatures(nodeId)
            .filter { it.name == PnPL.NAME || it.name == BinaryContent.NAME }.let { feature ->

                observeFeaturePnPLJob = blueManager.getFeatureUpdates(
                    nodeId = nodeId,
                    features = feature,
                    onFeaturesEnabled = {
                        launch {
                            // //getModel(nodeId = nodeId, demoName = null)
                            _isLoading.value = true

                            _modelUpdates.value =
                                blueManager.getDtmiModel(nodeId = nodeId)
                                    ?.extractComponent(compName = "control") ?: emptyList()

                            _enableCollapse.value = false
                            // //

                            // //Send
//                            if (byteArrayContentToBoard != null) {
//                                writeBinaryContentToNode(nodeId, byteArrayContentToBoard!!)
//                                byteArrayContentToBoard = null
//                            }
                            // //

                            // //sendGetAllCommand(nodeId = nodeId)
                            val featurePnPL =
                                blueManager.nodeFeatures(nodeId).find { it.name == PnPL.NAME }

                            if (featurePnPL is PnPL) {
                                blueManager.writeFeatureCommand(
                                    nodeId = nodeId,
                                    featureCommand = PnPLCommand(
                                        feature = featurePnPL,
                                        cmd = PnPLCmd.ALL
                                    )
                                )
                            }
                            // //
                        }
                    }
                ).flowOn(Dispatchers.IO).onEach { featureUpdate ->

                    val data = featureUpdate.data

                    if (data is PnPLConfig) {
                        data.deviceStatus.value?.components?.let { json ->
                            _lastStatusUpdatedAt.value = System.currentTimeMillis()
                            _componentStatusUpdates.value = json
                            _isLoading.value = false
                        }
                    } else if (data is RawData) {
                        byteArrayContentFromBoard = data.data.value
                        _binaryContentReceived.value = true
                    }
                }.launchIn(viewModelScope)
            }
    }

    fun stopDemo(nodeId: String) {
        observeFeaturePnPLJob?.cancel()

        _componentStatusUpdates.value = emptyList()

        coroutineScope.launch {
            val features = blueManager.nodeFeatures(nodeId)
                .filter { it.name == BinaryContent.NAME || it.name == PnPL.NAME }

            blueManager.disableFeatures(
                nodeId = nodeId,
                features = features
            )
        }
    }
}
