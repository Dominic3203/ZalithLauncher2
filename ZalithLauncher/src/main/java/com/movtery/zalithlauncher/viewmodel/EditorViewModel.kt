/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.cloneNew
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableJoystickData
import com.movtery.layer_controller.observable.ObservableJoystickStyle
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTextData
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.cloneJoystick
import com.movtery.layer_controller.observable.cloneNormal
import com.movtery.layer_controller.observable.cloneText
import com.movtery.layer_controller.utils.saveToFile
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.control.CabinetStyleItem
import com.movtery.zalithlauncher.game.control.StyleCabinetState
import com.movtery.zalithlauncher.ui.components.MenuState
import com.movtery.zalithlauncher.ui.screens.main.control_editor.EditorOperation
import com.movtery.zalithlauncher.ui.screens.main.control_editor.EditorWarningOperation
import com.movtery.zalithlauncher.ui.screens.main.control_editor.EditorWidgetOperation
import com.movtery.zalithlauncher.ui.screens.main.control_editor.PreviewScenario
import com.movtery.zalithlauncher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import com.movtery.zalithlauncher.ui.theme.showThemed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 控制布局编辑器
 */
class EditorViewModel : ViewModel() {
    lateinit var observableLayout: ObservableControlLayout
        private set

    /**
     * 样式收藏柜
     */
    val styleCabinet = StyleCabinetState()

    /**
     * 收藏柜是否从摇杆样式列表打开（关闭时返回来源列表）
     */
    var styleCabinetFromJoystick by mutableStateOf(false)

    /**
     * 当前选中的控件层
     */
    var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)

    /**
     * 当前选中的组件（仅用于编辑组件对话框）
     */
    var selectedWidget by mutableStateOf<SelectedWidgetData?>(null)

    /**
     * 当前选中的控件样式（仅用于样式编辑对话框）
     */
    var selectedStyle by mutableStateOf<ObservableButtonStyle?>(null)

    /**
     * 当前选中的摇杆样式（仅用于摇杆样式编辑对话框）
     */
    var selectedJoystickStyle by mutableStateOf<ObservableJoystickStyle?>(null)

    /**
     * 编辑器菜单状态
     */
    var editorMenu by mutableStateOf(MenuState.HIDE)

    /** 编辑器菜单悬浮球当前的位置 */
    var editorBallPosition by mutableStateOf(Offset.Zero)

    /**
     * 编辑器各种操作项
     */
    var editorOperation by mutableStateOf<EditorOperation>(EditorOperation.None)

    /**
     * 编辑器对于控件的操作项
     */
    var editorWidgetOperation by mutableStateOf<EditorWidgetOperation>(EditorWidgetOperation.None)

    /**
     * 编辑器的一些警告状态项
     */
    var editorWarningOperation by mutableStateOf<EditorWarningOperation>(EditorWarningOperation.None)

    /**
     * 是否开启控件层聚焦模式
     */
    var isLayerFocus by mutableStateOf(false)

    /**
     * 是否为预览控制布局模式
     */
    var isPreviewMode by mutableStateOf(false)

    /**
     * 预览控制布局的场景
     */
    var previewScenario by mutableStateOf(PreviewScenario.InMenu)

    /**
     * 预览控制布局时根据设备隐藏控制层
     */
    var previewHideLayerWhen by mutableStateOf(HideLayerWhen.None)



    fun initLayout(layout: ControlLayout) {
        if (!::observableLayout.isInitialized) {
            this.observableLayout = ObservableControlLayout(layout)
        }
    }



    /**
     * 切换编辑器菜单
     */
    fun switchMenu() {
        editorMenu = editorMenu.next()
    }

    /**
     * 移除控件层
     */
    fun removeLayer(layer: ObservableControlLayer) {
        if (layer == selectedLayer) selectedLayer = null
        observableLayout.removeLayer(layer.uuid)
    }

    /**
     * 为控件层添加控件
     */
    fun addWidget(layers: List<ObservableControlLayer>, addToLayer: (ObservableControlLayer) -> Unit) {
        val layer = selectedLayer
        if (layers.isEmpty()) {
            editorWarningOperation = EditorWarningOperation.WarningNoLayers
        } else if (layer == null) {
            editorWarningOperation = EditorWarningOperation.WarningNoSelectLayer
        } else {
            addToLayer(layer)
        }
    }

    /**
     * 在控件层移除控件
     */
    fun removeWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
            is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
        }
    }

    /**
     * 将控件复制到控件层
     */
    fun cloneWidgetToLayers(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        when (widget) {
            is ObservableNormalData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneNormal()
                    layer.addNormalButton(newData)
                }
            }
            is ObservableTextData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneText()
                    layer.addTextBox(newData)
                }
            }
            is ObservableJoystickData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneJoystick()
                    layer.addJoystickButton(newData)
                }
            }
        }
    }

    /**
     * 创建一个新的控件外观
     */
    fun createNewStyle(name: String) {
        observableLayout.addStyle(
            com.movtery.layer_controller.data.createNewButtonStyle(name)
        )
    }

    /**
     * 复制控件外观
     */
    fun cloneStyle(style: ObservableButtonStyle) {
        observableLayout.cloneStyle(style)
    }

    /**
     * 删除一个控件外观
     */
    fun removeStyle(style: ObservableButtonStyle) {
        observableLayout.removeStyle(style.uuid)
    }

    /**
     * 移除一个摇杆样式
     */
    fun removeJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.removeJoystickStyle(style.uuid)
    }

    /**
     * 创建一个新的摇杆样式
     */
    fun createNewJoystickStyle(name: String) {
        observableLayout.addJoystickStyle(
            com.movtery.layer_controller.data.createNewJoystickStyle(name)
        )
    }

    /**
     * 复制摇杆样式
     */
    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.cloneJoystickStyle(style)
    }

    /**
     * 将控件外观样式收藏到样式收藏柜
     * @param independent 为 true 时生成新 uuid 作为独立收藏，否则同 uuid 覆盖
     * @param onResult 收藏结果回调
     */
    fun favoriteButtonStyleToCabinet(
        style: ObservableButtonStyle,
        independent: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        val data = if (independent) style.pack().cloneNew() else style.pack()
        styleCabinet.favorite(data, onResult)
    }

    /**
     * 将摇杆样式收藏到样式收藏柜
     * @param independent 为 true 时生成新 uuid 作为独立收藏，否则同 uuid 覆盖
     * @param onResult 收藏结果回调
     */
    fun favoriteJoystickStyleToCabinet(
        style: ObservableJoystickStyle,
        independent: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        val data = if (independent) style.pack().cloneNew() else style.pack()
        styleCabinet.favorite(data, onResult)
    }

    /**
     * 关闭样式收藏柜，返回打开它的样式列表
     */
    fun closeStyleCabinet() {
        editorOperation = if (styleCabinetFromJoystick) {
            EditorOperation.OpenJoystickStyleList
        } else {
            EditorOperation.OpenStyleList
        }
    }

    /**
     * 关闭柜内样式编辑器，返回样式收藏柜，并立即保存编辑结果
     */
    fun closeCabinetEdit(item: CabinetStyleItem) {
        styleCabinet.save(item)
        editorOperation = EditorOperation.OpenStyleCabinet
    }

    /**
     * 将柜内样式加入当前控制布局
     * @return 是否加入成功，布局内已存在同 uuid 样式时返回 false
     */
    fun addCabinetStyleToLayout(item: CabinetStyleItem): Boolean {
        return when (item) {
            is CabinetStyleItem.Button -> {
                if (observableLayout.styles.value.any { it.uuid == item.style.uuid }) {
                    false
                } else {
                    observableLayout.addStyle(item.style.pack())
                    true
                }
            }
            is CabinetStyleItem.Joystick -> {
                if (observableLayout.joystickStyles.value.any { it.uuid == item.style.uuid }) {
                    false
                } else {
                    observableLayout.addJoystickStyle(item.style.pack())
                    true
                }
            }
        }
    }

    /**
     * 将编辑器内层级隐藏状态同步到实际隐藏状态
     * 供预览模式下使用正确的隐藏状态
     */
    fun applyEditorHide() {
        observableLayout.applyEditorHide()
    }

    /**
     * 保存控制布局
     */
    fun save(
        targetFile: File,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            editorOperation = EditorOperation.Saving
            val layout = observableLayout.pack()
            runCatching {
                layout.saveToFile(targetFile)
            }.onFailure { e ->
                editorOperation = EditorOperation.SaveFailed(e)
            }.onSuccess {
                editorOperation = EditorOperation.None
                onSaved()
            }
        }
    }

    fun onBackPressed(
        context: Context,
        onExit: () -> Unit
    ) {
        //检查并退出控件对话框、编辑控件样式对话框
        when (val operation = editorOperation) {
            is EditorOperation.SelectButton,
            is EditorOperation.EditButtonStyle,
            is EditorOperation.OpenStyleCabinet -> {
                editorOperation = EditorOperation.None
            }
            //柜内样式编辑器，保存编辑结果并返回样式收藏柜
            is EditorOperation.EditCabinetButtonStyle -> closeCabinetEdit(operation.item)
            is EditorOperation.EditCabinetJoystickStyle -> closeCabinetEdit(operation.item)
            else -> {
                showExitEditorDialog(
                    context = context,
                    onExit = onExit
                )
            }
        }
    }

    /**
     * 用于检查控制布局是否被修改过
     */
    private val checkModified = Mutex()

    /**
     * 弹出退出控制布局编辑器的对话框
     * @param onExit 用户点击确认，退出编辑器
     */
    fun showExitEditorDialog(
        context: Context,
        onExit: () -> Unit
    ) {
        viewModelScope.launch {
            val isModified = checkModified.withLock {
                observableLayout.isModified()
            }
            if (isModified) {
                showExitEditorDialogSuspend(
                    context = context,
                    onExit = onExit
                )
            } else {
                //未被修改，可以直接退出
                onExit()
            }
        }
    }

    private suspend fun showExitEditorDialogSuspend(
        context: Context,
        onExit: () -> Unit
    ) = withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.generic_warning)
            .setMessage(R.string.control_editor_exit_message)
            .setPositiveButton(R.string.generic_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.control_editor_exit_confirm) { dialog, _ ->
                dialog.dismiss()
                onExit()
            }
            .showThemed()
    }
}