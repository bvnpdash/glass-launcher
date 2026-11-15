package com.glass.launcher.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    vm: LauncherViewModel,
    onMenu: (MenuRequest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by vm.settings.collectAsState()
    val all by vm.apps.collectAsState()
    val t = LocalGlass.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val visible = remember(all, s.hidden, showHidden) { if (showHidden) all else all.filter { it.key !in s.hidden } }
    val categories = remember(visible) { visible.map { it.category }.distinct().sorted() }
    val filtered = remember(visible, query, category) {
        val q = query.trim().lowercase()
        visible.filter { (category == null || it.category == category) && (q.isEmpty() || it.sortKey.contains(q)) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.letter }.toSortedMap(compareBy<Char> { it == '#' }.thenBy { it }) }
    val recents = remember(s.recentLaunches, all) { s.recentLaunches.mapNotNull { vm.byKey(it) }.take(8) }
    val favs = remember(s.favorites, all) { s.favorites.mapNotNull { vm.byKey(it) } }
    val browsing = query.isEmpty() && category == null

    // pull-down at the top of the list closes the drawer
    var overscroll by remember { mutableFloatStateOf(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    overscroll += available.y
                    if (overscroll > 220f) { overscroll = 0f; onClose() }
                } else overscroll = 0f
                return Offset.Zero
            }
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
            .glass(GlassMaterial.CLEAR, Dp.Infinity, 0.dp, touchReactive = false)
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d > 6f) onClose() } })
        Spacer(Modifier.height(14.dp))

        // ---- search field ----
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp)
                .glass(GlassMaterial.FROSTED, Dp.Infinity, 12.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = t.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) GText("Search apps", 16, dim = true)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = t.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(t.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        filtered.firstOrNull()?.let { vm.rememberSearch(query); vm.launch(it); onClose() }
                    }),
                    modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) Box(Modifier.size(28.dp).pointerInput(Unit) { detectTapGestures { query = "" } }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Clear", tint = t.textDim, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- category chips ----
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GlassPill("All", selected = category == null) { category = null } }
            items(categories) { c -> GlassPill(c, selected = category == c) { category = if (category == c) null else c } }
            item { GlassPill(if (showHidden) "Hiding off" else "Hidden (${s.hidden.size})", selected = showHidden) { showHidden = !showHidden } }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), state = gridState,
                modifier = Modifier.fillMaxSize().nestedScroll(nested),
                contentPadding = PaddingValues(start = 12.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (browsing && recents.isNotEmpty()) {
                    item(span = { GridItemSpan(4) }, key = "h_recent") { SectionLabel("Recent") }
                    items(recents, key = { "r_" + it.key }, span = { GridItemSpan(1) }) { a -> DrawerIcon(a, vm, s.hidden, onMenu, onClose) }
                }
                if (browsing && favs.isNotEmpty()) {
                    item(span = { GridItemSpan(4) }, key = "h_fav") { SectionLabel("Favourites") }
                    items(favs, key = { "f_" + it.key }, span = { GridItemSpan(1) }) { a -> DrawerIcon(a, vm, s.hidden, onMenu, onClose) }
                }
                grouped.forEach { (letter, list) ->
                    item(span = { GridItemSpan(4) }, key = "l_$letter") { SectionLabel(letter.toString()) }
                    items(list, key = { "a_" + it.key }, span = { GridItemSpan(1) }) { a -> DrawerIcon(a, vm, s.hidden, onMenu, onClose) }
                }
            }
            // letter index
            if (browsing || query.isEmpty()) AlphaIndex(
                letters = grouped.keys.toList(), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                onPick = { letter ->
                    // find the grid index of the header
                    var idx = 0
                    if (browsing && recents.isNotEmpty()) idx += 1 + recents.size
                    if (browsing && favs.isNotEmpty()) idx += 1 + favs.size
                    for ((l, list) in grouped) { if (l == letter) break; idx += 1 + list.size }
                    scope.launch { gridState.scrollToItem(idx) }
                })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    GText(text, 12, FontWeight.Medium, dim = true, spacing = 2f, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
}

@Composable
private fun DrawerIcon(app: AppInfo, vm: LauncherViewModel, hidden: Set<String>, onMenu: (MenuRequest) -> Unit, onClose: () -> Unit) {
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { center = it.boundsInRoot().center }, contentAlignment = Alignment.Center) {
        AppIcon(app, vm, 54.dp, hidden = app.key in hidden,
            onClick = { vm.launch(app); onClose() },
            onLongClick = { onMenu(MenuRequest(app, center, MenuSource.DRAWER)) })
    }
}

@Composable
private fun AlphaIndex(letters: List<Char>, modifier: Modifier, onPick: (Char) -> Unit) {
    if (letters.size < 2) return
    var h by remember { mutableFloatStateOf(1f) }
    Column(
        modifier.onGloballyPositioned { h = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull() ?: continue
                        if (c.pressed) {
                            val i = ((c.position.y / h) * letters.size).toInt().coerceIn(0, letters.size - 1)
                            onPick(letters[i]); c.consume()
                        }
                    }
                }
            }.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { letters.forEach { GText(it.toString(), 10, dim = true, modifier = Modifier.padding(vertical = 1.dp)) } }
}
