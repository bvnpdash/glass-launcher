package com.glass.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glass.launcher.data.*
import com.glass.launcher.glass.Backdrop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

const val EMPTY = "-"

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    val icons = IconCache(app)
    val backdrop = Backdrop()
    val dash = com.glass.launcher.dashboard.DashboardRepository(app)
    private val store = SettingsRepository(app)

    private val _settings = MutableStateFlow(GlassSettings())
    val settings: StateFlow<GlassSettings> = _settings
    val ready = MutableStateFlow(false)
    val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val iconEpoch = MutableStateFlow(0)           // bumps when icon pack changes so icons reload

    private var persistJob: Job? = null
    private val launcherApps = app.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) { refreshApps() }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) { refreshApps() }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) { refreshApps() }
        override fun onPackagesAvailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) { refreshApps() }
        override fun onPackagesUnavailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) { refreshApps() }
    }

    init {
        launcherApps.registerCallback(callback)
        viewModelScope.launch {
            _settings.value = store.flow.first()
            refreshAppsNow()
            if (!_settings.value.seeded) seed()
            iconPacks.value = withContext(Dispatchers.IO) { icons.installedPacks() }
            icons.selectPack(_settings.value.iconPack); iconEpoch.value++
            ready.value = true
        }
        // wallpaper + blur reload (debounced so slider drags stay smooth)
        viewModelScope.launch {
            ready.filter { it }.first()
            _settings.map { Triple(it.wallpaperUri, it.useSystemWallpaper, it.blur) }
                .distinctUntilChanged().debounce(90)
                .collect { (uri, sys, blur) -> backdrop.load(getApplication(), uri, sys, 0.35f + blur * 0.9f) }
        }
        viewModelScope.launch {
            ready.filter { it }.first()
            _settings.map { it.iconPack }.distinctUntilChanged().drop(1).collect {
                icons.selectPack(it); iconEpoch.value++
            }
        }
    }

    override fun onCleared() { launcherApps.unregisterCallback(callback) }

    // ---------- settings ----------
    fun edit(block: (GlassSettings) -> GlassSettings) {
        val n = block(_settings.value)
        _settings.value = n
        persistJob?.cancel()
        persistJob = viewModelScope.launch { delay(250); store.update { n } }
    }

    fun flushNow() { persistJob?.cancel(); viewModelScope.launch(NonCancellable) { store.update { _settings.value } } }

    // ---------- apps ----------
    fun refreshApps() { viewModelScope.launch { refreshAppsNow() } }
    private suspend fun refreshAppsNow() {
        val list = repo.loadApps()
        apps.value = list
        // drop uninstalled apps from layouts
        val keys = list.map { it.key }.toSet()
        edit { s -> s.copy(
            homeApps = s.homeApps.map { if (it == EMPTY || it in keys) it else EMPTY },
            dockApps = s.dockApps.filter { it in keys },
            favorites = s.favorites.filter { it in keys },
            recentLaunches = s.recentLaunches.filter { it in keys },
        ) }
    }

    fun byKey(key: String): AppInfo? = apps.value.firstOrNull { it.key == key }

    fun launch(app: AppInfo) {
        if (repo.launch(app)) edit { s -> s.copy(recentLaunches = (listOf(app.key) + s.recentLaunches.filter { it != app.key }).take(12)) }
    }

    private suspend fun seed() {
        val ctx = getApplication<Application>()
        val pm = ctx.packageManager
        val list = apps.value
        fun find(i: Intent): String? = pm.queryIntentActivities(i, 0)
            .firstNotNullOfOrNull { ri -> list.firstOrNull { it.packageName == ri.activityInfo.packageName }?.key }
        fun cat(c: String) = find(Intent(Intent.ACTION_MAIN).addCategory(c))
        val dock = listOfNotNull(
            find(Intent(Intent.ACTION_DIAL)), cat(Intent.CATEGORY_APP_MESSAGING),
            cat(Intent.CATEGORY_APP_BROWSER), find(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            cat(Intent.CATEGORY_APP_GALLERY),
        ).distinct()
        val home = listOfNotNull(
            cat(Intent.CATEGORY_APP_GALLERY), cat(Intent.CATEGORY_APP_MAPS), cat(Intent.CATEGORY_APP_EMAIL),
            cat(Intent.CATEGORY_APP_CALENDAR), cat(Intent.CATEGORY_APP_MUSIC), cat(Intent.CATEGORY_APP_CONTACTS),
            cat(Intent.CATEGORY_APP_CALCULATOR), find(Intent(Settings.ACTION_SETTINGS)),
        ).distinct().filter { it !in dock.take(4) }
        val fallback = list.map { it.key }.filter { it !in dock && it !in home }
        val finalDock = (dock + fallback).distinct().take(4)
        val finalHome = (home + fallback.filter { it !in finalDock }).distinct().take(8)
        edit { it.copy(dockApps = finalDock, homeApps = finalHome, favorites = finalHome.take(4), seeded = true) }
        flushNow()
    }

    // ---------- home / dock mutations ----------
    fun placeOnHome(key: String, cell: Int) = edit { s ->
        val cells = (s.homeApps + List(maxOf(0, s.gridCols * s.gridRows - s.homeApps.size)) { EMPTY }).toMutableList()
        while (cells.size <= cell) cells += EMPTY
        val old = cells.indexOf(key)
        val displaced = cells[cell]
        if (old >= 0) cells[old] = if (displaced == key) EMPTY else displaced
        cells[cell] = key
        s.copy(homeApps = cells, dockApps = s.dockApps - key)
    }

    fun addToHome(key: String) = edit { s ->
        if (key in s.homeApps) return@edit s
        val cells = s.homeApps.toMutableList()
        val free = cells.indexOf(EMPTY)
        if (free >= 0) cells[free] = key else if (cells.size < s.gridCols * s.gridRows) cells += key
        s.copy(homeApps = cells)
    }

    fun removeFromHome(key: String) = edit { s -> s.copy(homeApps = s.homeApps.map { if (it == key) EMPTY else it }) }

    fun placeOnDock(key: String, index: Int) = edit { s ->
        val d = (s.dockApps - key).toMutableList()
        d.add(index.coerceIn(0, d.size), key)
        s.copy(dockApps = d.take(s.dockCount), homeApps = s.homeApps.map { if (it == key) EMPTY else it })
    }

    fun removeFromDock(key: String) = edit { it.copy(dockApps = it.dockApps - key) }
    fun toggleFavorite(key: String) = edit { s -> s.copy(favorites = if (key in s.favorites) s.favorites - key else s.favorites + key) }
    fun hide(key: String) = edit { s -> s.copy(hidden = s.hidden + key, homeApps = s.homeApps.map { if (it == key) EMPTY else it }, dockApps = s.dockApps - key) }
    fun unhide(key: String) = edit { it.copy(hidden = it.hidden - key) }

    // ---------- search ----------
    fun search(q: String): List<AppInfo> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        val hidden = _settings.value.hidden
        return apps.value.asSequence().filter { it.key !in hidden }.mapNotNull { a ->
            val l = a.sortKey
            val score = when {
                l.startsWith(t) -> 0
                l.split(' ', '-', '.').any { it.startsWith(t) } -> 1
                l.contains(t) -> 2
                a.packageName.contains(t) -> 3
                else -> return@mapNotNull null
            }
            score to a
        }.sortedWith(compareBy({ it.first }, { it.second.sortKey })).map { it.second }.take(24).toList()
    }

    fun rememberSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        edit { s -> s.copy(recentSearches = (listOf(t) + s.recentSearches.filter { it != t }).take(6)) }
    }

    fun suggestions(): List<AppInfo> {
        val s = _settings.value
        return (s.recentLaunches.mapNotNull { byKey(it) } + s.favorites.mapNotNull { byKey(it) }).distinct().take(6)
    }

    fun visibleApps(): List<AppInfo> = apps.value.filter { it.key !in _settings.value.hidden }
}
