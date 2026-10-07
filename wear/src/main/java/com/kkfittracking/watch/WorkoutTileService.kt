package com.kkfittracking.watch

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kkfittracking.wear.WatchState

/**
 * A tile in the watch's tile carousel: the workout at a glance (the exercise and set, how much is
 * done, paused or all done) with a button that opens the app. Updated whenever the phone sends news.
 */
class WorkoutTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val layout = layout(WatchStore.loadState(this), requestParams.deviceConfiguration)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(5 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        ResolvableFuture.create<ResourceBuilders.Resources>().apply {
            set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        }

    private fun layout(state: WatchState?, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val (label, title, detail) = when {
            state == null || !state.active -> Triple(
                "FitTraKKing",
                "No workout",
                state?.exercisesToday?.takeIf { it > 0 }?.let { "Today: $it exercises" } ?: "Nothing planned today",
            )
            state.paused -> Triple("${state.percent}% done", "Paused", "Next: ${state.exerciseName}")
            state.allDone -> Triple("${state.percent}% done", "All done", "Finish on the watch or phone")
            else -> Triple("${state.position}/${state.of} · ${state.percent}%", state.exerciseName, state.step)
        }
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setColor(argb(WHITE))
                    .setMaxLines(2)
                    .build(),
            )
            .addContent(
                Text.Builder(this, detail)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(ACCENT))
                    .setMaxLines(2)
                    .build(),
            )
            .build()
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(
                Text.Builder(this, label).setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(GREY)).build(),
            )
            .setContent(content)
            .setPrimaryChipContent(
                CompactChip.Builder(this, if (state?.active == true) "Open" else "Start", open, device).build(),
            )
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFFB0B0B0.toInt()
        const val ACCENT = 0xFF4DD0C8.toInt()
    }
}
