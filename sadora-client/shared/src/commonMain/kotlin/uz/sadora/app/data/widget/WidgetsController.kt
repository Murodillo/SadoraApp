package uz.sadora.app.data.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.widget.WidgetBlock
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetContent
import uz.sadora.widget.WidgetSize

/** The "Widgets and watch" page: the default a new widget takes, and its preview. */
class WidgetsController(private val bridge: WidgetBridge?) {
    var config by mutableStateOf(
        bridge?.defaultConfig() ?: WidgetConfig(WidgetSize.MEDIUM, listOf(WidgetBlock.WATER, WidgetBlock.DOSES)),
    )
        private set

    val canPin: Boolean get() = bridge?.canPin == true
    val watchPaired: Boolean get() = bridge?.watchPaired == true

    fun preview(): WidgetContent? = bridge?.preview(config)

    fun update(next: WidgetConfig) {
        config = next
        bridge?.saveDefaultConfig(next)
    }

    fun toggle(block: WidgetBlock) =
        update(config.copy(blocks = if (block in config.blocks) config.blocks - block else config.blocks + block))

    fun pin(): Boolean = bridge?.pin(config) == true
}
