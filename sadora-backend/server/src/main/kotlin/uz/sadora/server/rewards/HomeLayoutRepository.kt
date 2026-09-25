package uz.sadora.server.rewards

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.HomeWidgets
import uz.sadora.server.core.ValidationException
import uz.sadora.server.db.HomeWidgetLayout
import uz.sadora.server.db.dbQuery

/**
 * How she arranged the home screen.
 *
 * A preference, not health data: no consent gates it and no life stage changes it. It
 * lives on the server rather than on the device for one reason — a new phone should open
 * on the home screen she built.
 *
 * An account that has never touched it has no rows at all, and gets the shipped default.
 * That is deliberate: writing eleven rows for every sign-up to store "unchanged" would
 * make a migration out of every future widget.
 */
class HomeLayoutRepository {

    suspend fun layoutOf(userId: Uuid): HomeLayout = dbQuery {
        val rows = HomeWidgetLayout.selectAll()
            .where { HomeWidgetLayout.userId eq userId }
            .orderBy(HomeWidgetLayout.position to SortOrder.ASC)
            .map {
                HomeWidget(
                    key = it[HomeWidgetLayout.widgetKey],
                    position = it[HomeWidgetLayout.position],
                    visible = it[HomeWidgetLayout.visible],
                )
            }
        if (rows.isEmpty()) HomeLayout() else HomeLayout(rows).reconciled()
    }

    /**
     * Replaces the whole arrangement.
     *
     * Wholesale rather than per-widget: reordering *is* a change to the set, and two
     * phones sending overlapping patches would interleave into a layout neither asked
     * for. Unknown keys are dropped here rather than rejected — an older app must be
     * able to save a layout that contains a widget a newer one added.
     */
    suspend fun save(userId: Uuid, widgets: List<HomeWidget>): HomeLayout = dbQuery {
        val known = widgets
            .filter { it.key in HomeWidgets.keys }
            .distinctBy { it.key }
        if (known.isEmpty()) throw ValidationException("widgets", "Bo'sh tartib saqlanmaydi")

        HomeWidgetLayout.deleteWhere { HomeWidgetLayout.userId eq userId }
        val ordered = known.sortedBy { it.position }
        HomeWidgetLayout.batchUpsert(
            ordered.withIndex().toList(),
            HomeWidgetLayout.userId,
            HomeWidgetLayout.widgetKey,
        ) { (index, widget) ->
            this[HomeWidgetLayout.userId] = userId
            this[HomeWidgetLayout.widgetKey] = widget.key
            this[HomeWidgetLayout.position] = index
            // The one card that cannot be hidden stays on whatever the request said.
            this[HomeWidgetLayout.visible] = widget.visible || widget.key in HomeWidgets.required
        }

        HomeLayout(
            ordered.mapIndexed { index, widget ->
                widget.copy(
                    position = index,
                    visible = widget.visible || widget.key in HomeWidgets.required,
                )
            },
        ).reconciled()
    }

    suspend fun reset(userId: Uuid): HomeLayout = dbQuery {
        HomeWidgetLayout.deleteWhere { HomeWidgetLayout.userId eq userId }
        HomeLayout()
    }
}
