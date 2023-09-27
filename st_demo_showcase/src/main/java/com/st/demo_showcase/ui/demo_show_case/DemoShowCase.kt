/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.demo_showcase.ui.demo_show_case

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.st.core.ARG_NODE_ID
import com.st.demo_showcase.DemoShowCaseConfig
import com.st.demo_showcase.DemoShowcaseNavGraphDirections
import com.st.demo_showcase.R
import com.st.demo_showcase.databinding.DemoShowcaseBinding
import com.st.demo_showcase.models.Demo
import com.st.demo_showcase.models.LOG_SETTINGS
import com.st.demo_showcase.models.SERIAL_CONSOLE
import com.st.demo_showcase.ui.DemoShowCaseViewModel
import com.st.demo_showcase.ui.composable.DemoShowCaseTopBar
import com.st.demo_showcase.ui.composable.UpdateAvailableDialog
import com.st.demo_showcase.ui.demo_list.DemoListFragmentDirections
import com.st.demo_showcase.utils.toActions
import com.st.plot.PlotFragmentDirections
import com.st.plot.utils.PLOT_SETTINGS
import com.st.ui.composables.ActionItem
import com.st.ui.composables.JSON_FILE_TYPE
import com.st.ui.theme.BlueMSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DemoShowCase : Fragment() {
    companion object {
        const val TAG = "DemoShowCase"
    }

    private lateinit var nodeId: String
    var navController: NavController? = null
    private lateinit var binding: DemoShowcaseBinding
    private val viewModel: DemoShowCaseViewModel by activityViewModels()

    private val onDestinationChangedListener: (controller: NavController, destination: NavDestination, arguments: Bundle?) -> Unit =
        { controller, destination, _ ->
            viewModel.onDestinationChanged(
                controller.previousBackStackEntry?.destination,
                destination
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DemoShowcaseBinding.inflate(inflater, container, false)
        nodeId = arguments?.getString(ARG_NODE_ID)
            ?: throw IllegalArgumentException("Missing string $ARG_NODE_ID arguments")

        viewModel.setNodeId(nodeId)

        binding.composeViewDialog.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BlueMSTheme {
                    val showUpdateDialog by viewModel.showFwUpdate.collectAsStateWithLifecycle()
                    val currentFw by viewModel.currentFw.collectAsStateWithLifecycle()
                    val updateFw by viewModel.updateFw.collectAsStateWithLifecycle()
                    val updateUrl by viewModel.updateUrl.collectAsStateWithLifecycle()
                    val updateChangeLog by viewModel.updateChangeLog.collectAsStateWithLifecycle()

                    UpdateAvailableDialog(show = showUpdateDialog,
                        currentFw = currentFw,
                        updateFw = updateFw,
                        changeLog = updateChangeLog,
                        onInstall = {
                            navController?.navigate(
                                DemoListFragmentDirections.actionDemoListToFwUpgrade(
                                    nodeId,
                                    updateUrl
                                )
                            )
                        },
                        dismissUpdateDialog = { viewModel.dismissUpdateDialog(it) })
                }
            }
        }

        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BlueMSTheme {
                    BackHandler {
                        viewModel.disconnect(nodeId)

                        DemoShowCaseConfig.onClose()
                    }

                    val showSettingsMenu by viewModel.showSettingsMenu.collectAsStateWithLifecycle()
                    val currentDemoSelected by viewModel.currentDemo.collectAsStateWithLifecycle()
                    val hasPnplSettings by viewModel.hasPnplSettings.collectAsStateWithLifecycle()
                    val isExpert by viewModel.isExpert.collectAsStateWithLifecycle()

                    val demoActions = currentDemoSelected?.settings.toActions(
                        navController = navController,
                        demo = currentDemoSelected,
                        nodeId = nodeId,
                    ) + if (hasPnplSettings) {
                        listOf(
                            ActionItem(
                                label = context.getString(R.string.st_demoShowcase_menuAction_demoSettings),
                                imageVector = Icons.Default.Settings,
                                action = {
                                    currentDemoSelected?.navigateToPnplSettings(
                                        navController,
                                        nodeId
                                    )
                                })
                        )
                    } else {
                        emptyList()
                    } + if (isExpert && (currentDemoSelected!=Demo.TextualMonitor))  {
                        listOf(
                            ActionItem(
                                label = SERIAL_CONSOLE,
                                imageVector = Icons.Default.Terminal,
                                action = {
                                    val direction =
                                        DemoShowcaseNavGraphDirections.globalActionToSerialConsole(
                                            nodeId
                                        )
                                    direction.let { dir ->
                                        navController?.navigate(dir)
                                    }
                                }
                            )
                        )
                    } else {
                        emptyList()
                    }

                    val pickFileLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { fileUri ->
                        if (fileUri != null) {
                            viewModel.setDtmiModel(nodeId, fileUri)
                        }
                    }

                    val boardActions by remember (key1 = isExpert) {
                        derivedStateOf {
                            if(isExpert) {
                                listOf(
                                    ActionItem(
                                        label = context.getString(R.string.st_demoShowcase_menuAction_addDtmi),
                                        action = {
                                            pickFileLauncher.launch(arrayOf(JSON_FILE_TYPE))
                                        }),
                                    ActionItem(
                                        label = context.getString(R.string.st_demoShowcase_menuAction_debug),
                                        action = {
                                            navController?.navigate(
                                                DemoShowcaseNavGraphDirections.globalActionToDebugConsole()
                                            )
                                        }),
                                    ActionItem(
                                        label =
                                        context.getString(R.string.st_demoShowcase_menuAction_logSettings),
                                        action = {
                                            navController?.navigate(
                                                DemoShowcaseNavGraphDirections.globalActionToLogSettings()
                                            )
                                        }
                                    ),
                                    ActionItem(
                                        label = context.getString(R.string.st_demoShowcase_menuAction_fwUpdate),
                                        action = {
                                            navController?.navigate(
                                                DemoListFragmentDirections.actionDemoListToFwUpgrade(
                                                    nodeId
                                                )
                                            )
                                        })
                                )
                            } else {
                                listOf(
                                    ActionItem(
                                        label =
                                        context.getString(R.string.st_demoShowcase_menuAction_logSettings),
                                        action = {
                                            navController?.navigate(
                                                DemoShowcaseNavGraphDirections.globalActionToLogSettings()
                                            )
                                        }
                                    )
                                )
                            }
                        }
                    }


                    DemoShowCaseTopBar(
                        demoName = currentDemoSelected?.displayName,
                        boardActions = boardActions,
                        demoActions = demoActions,
                        showSettingsMenu = showSettingsMenu
                    )
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.nav_host_fragment_demo_showcase) as NavHostFragment

        navController = navHostFragment.navController

        navController?.addOnDestinationChangedListener(onDestinationChangedListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        navController?.removeOnDestinationChangedListener(onDestinationChangedListener)
    }
}
