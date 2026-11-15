package com.glass.launcher.appdrawer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.*

/**
 * The capsule starts as a small pill at the top and springs open into a result sheet;
 * progress drives both the corner radius and the content reveal so it reads as one surface growing.
 */
@Composable
fun LiquidSearch(vm: LauncherViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val t = LocalGlass.current
    val s by vm.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(0.78f, 360f * t.speed)); runCatching { focus.requestFocus() } }

    val results = remember(query, s.hidden, vm.apps.collectAsState().value) { vm.search(query) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK)
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }
    val canVoice = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }

    fun webSearch() {
        vm.rememberSearch(query)
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onClose()
    }

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * open.value))
        .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 14.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    val p = open.value
                    scaleX = 0.86f + 0.14f * p; scaleY = 0.9f + 0.1f * p; alpha = p.coerceIn(0f, 1f)
                }
        ) {
            // ---- capsule ----
            Row(
                Modifier.fillMaxWidth().height(58.dp).glass(GlassMaterial.LIQUID, Dp.Infinity, 16.dp)
                    .pointerInput(Unit) { detectTapGestures { } }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = t.textDim, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) GText("Search apps, web…", 17, dim = true)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = t.text, fontSize = 17.sp),
                        cursorBrush = SolidColor(t.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val first = results.firstOrNull()
                            if (first != null) { vm.rememberSearch(query); vm.launch(first); onClose() }
                            else if (query.isNotBlank()) webSearch()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                if (query.isNotEmpty()) IconBtn(Icons.Default.Close, "Clear") { query = "" }
                else if (canVoice) IconBtn(Icons.Default.Mic, "Voice search") {
                    runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- results sheet ----
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).glass(GlassMaterial.FROSTED, 28.dp, 16.dp, touchReactive = false)
                .pointerInput(Unit) { detectTapGestures { } }) {
                LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
                    if (query.isBlank()) {
                        val sugg = vm.suggestions()
                        if (s.recentSearches.isNotEmpty()) {
                            item { Label("Recent searches") }
                            items(s.recentSearches, key = { "rs_$it" }) { r ->
                                Row(Modifier.fillMaxWidth().pointerInput(r) { detectTapGestures { query = r } }.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null, tint = t.textDim, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(14.dp)); GText(r, 15)
                                }
                            }
                        }
                        if (sugg.isNotEmpty()) {
                            item { Label("Suggested") }
                            items(sugg, key = { "sg_" + it.key }) { a -> AppRow(a, vm, onClose) }
                        }
                        if (sugg.isEmpty() && s.recentSearches.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { GText("Start typing to search", 14, dim = true) } }
                    } else {
                        items(results, key = { "res_" + it.key }) { a -> AppRow(a, vm, onClose) { vm.rememberSearch(query) } }
                        item {
                            Row(Modifier.fillMaxWidth().pointerInput(query) { detectTapGestures { webSearch() } }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, null, tint = t.accent, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(14.dp)); GText("Search the web for “$query”", 15, FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Label(text: String) =
    GText(text.uppercase(), 11, FontWeight.Medium, dim = true, spacing = 1.8f, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))

@Composable
private fun AppRow(app: com.glass.launcher.data.AppInfo, vm: LauncherViewModel, onClose: () -> Unit, before: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().pointerInput(app.key) { detectTapGestures { before(); vm.launch(app); onClose() } }
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, vm, 40.dp, showLabel = false, tile = false)
        Spacer(Modifier.width(16.dp))
        Column { GText(app.label, 16, FontWeight.Medium); GText(app.category, 12, dim = true) }
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).pointerInput(Unit) { detectTapGestures { onClick() } }, contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = LocalGlass.current.textDim, modifier = Modifier.size(20.dp))
    }
}
