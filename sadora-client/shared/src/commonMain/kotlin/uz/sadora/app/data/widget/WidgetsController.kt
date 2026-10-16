package uz.sadora.app.data.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.widget.WidgetBlock
import uz.sadora.widget.WidgetConfig
import uz.sadora.widget.WidgetContent
import uz.sadora.widget.WidgetSize
import uz.sadora.widget.WidgetStore

/**
 * One set of widget settings and its preview: the app's default for a new widget on the
 * "Widgets and watch" page, or one placed widget's own on Android's configure screen.
 */
class WidgetsController(
    initial: WidgetConfig,
    private val save: (WidgetConfig) -> Unit,
    private val previewOf: (WidgetConfig) -> WidgetContent?,
    private val pinner: ((WidgetConfig) -> Boolean)? = null,
    val watchPaired: Boolean = false,
) {
    /** The settings page, over the app's bridge; inert without one (previews, tests). */
    constructor(bridge: WidgetBridge?) : this(
        initial = bridge?.defaultConfig() ?: WidgetConfig(WidgetSize.MEDIUM, listOf(WidgetBlock.WATER, WidgetBlock.DOSES)),
        save = { bridge?.saveDefaultConfig(it) },
        previewOf = { bridge?.preview(it) },
        pinner = bridge?.takeIf { it.canPin }?.let { b -> { config: WidgetConfig -> b.pin(config) } },
        watchPaired = bridge?.watchPaired == true,
    )

    var config by mutableStateOf(initial)
        private set

    val canPin: Boolean get() = pinner != null

    fun preview(): WidgetContent? = previewOf(config)

    fun update(next: WidgetConfig) {
        config = next
        save(next)
    }

    fun toggle(block: WidgetBlock) =
        update(config.copy(blocks = if (block in config.blocks) config.blocks - block else config.blocks + block))

    fun pin(): Boolean = pinner?.invoke(config) == true

    companion object {
        /** One placed widget's settings, kept in [store] under [widgetId]. */
        fun forWidget(store: WidgetStore, widgetId: String, fallback: WidgetConfig, onSaved: () -> Unit) = WidgetsController(
            initial = store.config(widgetId) ?: fallback,
            save = {
                store.putConfig(widgetId, it)
                onSaved()
            },
            previewOf = { config ->
                store.snapshot()?.let {
                    WidgetContent.build(it, config, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                }
            },
        )
    }
}
