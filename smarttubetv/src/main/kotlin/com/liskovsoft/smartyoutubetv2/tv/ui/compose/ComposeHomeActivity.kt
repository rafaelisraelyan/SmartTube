package com.liskovsoft.smartyoutubetv2.tv.ui.compose

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity

/**
 * GRTubeYou: first Compose for TV screen.
 *
 * The rest of the app is still Leanback Views + XML layouts. This screen is an
 * additional entry point on the TV launcher and hands navigation back to the
 * existing screens through the app's own [ViewManager], so nothing in the
 * Leanback flow changes.
 */
class ComposeHomeActivity : MotherActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeView = ComposeView(this)
        setContentView(composeView)

        composeView.setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ACCENT,
                    background = BACKDROP,
                    surface = BACKDROP
                )
            ) {
                ComposeHomeScreen(
                    onOpenSection = { openSection(it) },
                    onOpenSearch = { openView(SearchView::class.java) },
                    onOpenSignIn = { openView(SignInView::class.java) }
                )
            }
        }
    }

    /** Selects a browse section, then hands off to the existing Leanback browse screen. */
    private fun openSection(sectionId: Int) {
        BrowsePresenter.instance(this).selectSection(sectionId)
        openView(BrowseView::class.java)
    }

    /**
     * forceStart = true because this activity is not registered in the ViewManager
     * stack, so the "already on top" short-circuit would otherwise swallow the tap.
     */
    private fun openView(viewClass: Class<*>) {
        ViewManager.instance(this).startView(viewClass, true)
    }

    private data class TileSpec(
        val title: String,
        val color: Color,
        val action: () -> Unit
    )

    @Composable
    private fun ComposeHomeScreen(
        onOpenSection: (Int) -> Unit,
        onOpenSearch: () -> Unit,
        onOpenSignIn: () -> Unit
    ) {
        val mainRow = remember {
            listOf(
                TileSpec("В тренде", CATEGORY[0]) { onOpenSection(MediaGroup.TYPE_TRENDING) },
                TileSpec("Подписки", CATEGORY[1]) { onOpenSection(MediaGroup.TYPE_SUBSCRIPTIONS) },
                TileSpec("Плейлисты", CATEGORY[2]) { onOpenSection(MediaGroup.TYPE_USER_PLAYLISTS) },
                TileSpec("История", CATEGORY[3]) { onOpenSection(MediaGroup.TYPE_HISTORY) },
                TileSpec("Поиск", CATEGORY[4], onOpenSearch),
                TileSpec("Настройки", CATEGORY[5]) { onOpenSection(MediaGroup.TYPE_SETTINGS) },
                TileSpec("Вход", CATEGORY[6], onOpenSignIn)
            )
        }

        val genreRow = remember {
            listOf(
                TileSpec("Музыка", CATEGORY[1]) { onOpenSection(MediaGroup.TYPE_MUSIC) },
                TileSpec("Новости", CATEGORY[2]) { onOpenSection(MediaGroup.TYPE_NEWS) },
                TileSpec("Игры", CATEGORY[3]) { onOpenSection(MediaGroup.TYPE_GAMING) },
                TileSpec("Кино", CATEGORY[4]) { onOpenSection(MediaGroup.TYPE_MOVIES) },
                TileSpec("Спорт", CATEGORY[5]) { onOpenSection(MediaGroup.TYPE_SPORTS) },
                TileSpec("Live", CATEGORY[6]) { onOpenSection(MediaGroup.TYPE_LIVE) },
                TileSpec("Shorts", CATEGORY[0]) { onOpenSection(MediaGroup.TYPE_SHORTS) },
                TileSpec("Рекомендации", CATEGORY[1]) { onOpenSection(MediaGroup.TYPE_RECOMMENDED) },
                TileSpec("Главная", CATEGORY[2]) { onOpenSection(MediaGroup.TYPE_HOME) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BACKDROP)
                .padding(horizontal = 48.dp, vertical = 40.dp)
        ) {
            Text(
                text = "GRTubeYou",
                color = Color.White,
                fontSize = 44.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "YouTube для Android TV",
                color = MUTED,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            RowHeader(title = "Основное")
            Spacer(modifier = Modifier.height(12.dp))
            TileRow(mainRow)

            Spacer(modifier = Modifier.height(32.dp))

            RowHeader(title = "Жанры")
            Spacer(modifier = Modifier.height(12.dp))
            TileRow(genreRow)
        }
    }

    @Composable
    private fun RowHeader(title: String) {
        Text(
            text = title,
            color = MUTED,
            fontSize = 20.sp
        )
    }

    @Composable
    private fun TileRow(tiles: List<TileSpec>) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tiles) { tile ->
                Tile(
                    title = tile.title,
                    color = tile.color,
                    onClick = tile.action
                )
            }
        }
    }

    @Composable
    private fun Tile(title: String, color: Color, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .width(210.dp)
                .height(120.dp)
                .scale(if (focused) 1.06f else 1f)
                .background(
                    if (focused) color.copy(alpha = 0.85f) else color.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { event ->
                    val isActivate = event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.DirectionCenter
                    if (event.type == KeyEventType.KeyDown && isActivate) {
                        onClick()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }

    private companion object {
        val BACKDROP = Color(0xFF0B0F14)
        val MUTED = Color(0xFF9AA6B2)
        val ACCENT = Color(0xFFFF3D3D)

        val CATEGORY = listOf(
            Color(0xFFC2410C), // orange
            Color(0xFF1D4ED8), // blue
            Color(0xFF15803D), // green
            Color(0xFF6D28D9), // violet
            Color(0xFF0E7490), // cyan
            Color(0xFF9D174D), // pink
            Color(0xFFB45309)  // amber
        )
    }
}
