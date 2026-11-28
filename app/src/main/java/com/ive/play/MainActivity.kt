package com.ive.play

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.BitmapFactory
import java.util.concurrent.Executors
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(9, 9, 12)
    private val panel = Color.rgb(25, 25, 29)
    private val orange = Color.rgb(255, 104, 22)
    private val gold = Color.rgb(255, 179, 52)
    private val muted = Color.rgb(175, 175, 185)
    private lateinit var root: LinearLayout
    private var player: ExoPlayer? = null
    private val items = mutableListOf<MediaEntry>()
    private val favorites = linkedSetOf<String>()
    private val history = mutableListOf<String>()
    private var xtreamSession: Triple<String, String, String>? = null
    private val imageLoader = Executors.newFixedThreadPool(3)
    private var drawerOpen = false
    private var currentPage = "Início"

    data class MediaEntry(val name: String, val url: String, val group: String, val kind: String = "TV ao Vivo", val image: String = "", val seriesId: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        history.addAll(getPreferences(MODE_PRIVATE).getString("history", "")!!.split("\n").filter { it.isNotBlank() })
        loginScreen()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 14, border: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != null) setStroke(dp(1), border)
        }

    private fun text(value: String, size: Float = 16f, bold: Boolean = false, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun gap(height: Int) { root.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    private fun begin(page: String, menu: Boolean = true) {
        player?.release()
        player = null
        currentPage = page
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(30))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
        if (menu) header(page)
    }

    private fun header(page: String) {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuButton = text("☰", 28f, true, orange).apply {
            gravity = Gravity.CENTER
            setOnClickListener { drawerOpen = !drawerOpen; if (drawerOpen) showMenu() else navigate(currentPage) }
        }
        bar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(text("♛  IVEPLAY", 23f, true, gold), LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(text("⌕", 30f, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { catalog("Buscar") }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar)
        if (page != "Início") {
            root.addView(text(page, 24f, true).apply { setPadding(0, dp(10), 0, dp(12)) })
        }
    }

    private fun button(title: String, color: Int = orange, action: () -> Unit) {
        val view = text(title, 15f, true).apply {
            gravity = Gravity.CENTER
            background = shape(color)
            setOnClickListener { action() }
        }
        root.addView(view, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
    }

    private fun field(hint: String, password: Boolean = false): EditText {
        val input = EditText(this).apply {
            this.hint = hint
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = shape(panel, 10, Color.DKGRAY)
            if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        return input
    }

    private fun loginScreen() {
        val isTelevision = packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.hardware.type.television")
        requestedOrientation = if (isTelevision) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        begin("Login", false)
        root.gravity = Gravity.CENTER_HORIZONTAL
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ive_brand_mark)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(110)))
        root.addView(text("IVEPLAY", 32f, true, gold).apply { gravity = Gravity.CENTER })
        root.addView(text("Seu entretenimento em um só lugar", 13f, false, muted).apply { gravity = Gravity.CENTER })
        gap(24)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabs)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(form)
        var xtream = true
        fun renderForm() {
            tabs.removeAllViews()
            form.removeAllViews()
            listOf("Xtream Codes" to true, "Lista M3U" to false).forEach { (label, mode) ->
                tabs.addView(text(label, 15f, true, if (xtream == mode) Color.BLACK else Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = shape(if (xtream == mode) orange else panel)
                    setOnClickListener { xtream = mode; renderForm() }
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            fun input(hint: String, secret: Boolean = false): EditText {
                val v = EditText(this).apply {
                    this.hint = hint
                    setTextColor(Color.WHITE)
                    setHintTextColor(muted)
                    setSingleLine(true)
                    setPadding(dp(12), 0, dp(12), 0)
                    background = shape(panel, 10, orange)
                    if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                form.addView(v, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
                return v
            }
            val server = input(if (xtream) "URL do servidor" else "URL da lista M3U")
            val user = if (xtream) input("Usuário") else null
            val pass = if (xtream) input("Senha", true) else null
            val keep = CheckBox(this).apply {
                text = "Manter conectado"
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(orange)
                isEnabled = false
                alpha = 0.55f
            }
            form.addView(keep)
            form.addView(text("Salvar o acesso com segurança estará disponível em uma próxima versão.", 12f, false, muted))
            form.addView(text("ENTRAR", 17f, true, Color.BLACK).apply {
                gravity = Gravity.CENTER
                background = shape(orange)
                setOnClickListener {
                    if (xtream) {
                        val host = server.text.toString().trim().trimEnd('/')
                        val username = user!!.text.toString().trim()
                        val password = pass!!.text.toString()
                        if (!validUrl(host) || username.isBlank() || password.isBlank()) {
                            toast("Preencha URL, usuário e senha")
                        } else {
                            loadXtream(host, username, password) { enterCatalog() }
                        }
                    } else {
                        val url = server.text.toString().trim()
                        if (!validUrl(url)) toast("Informe uma URL válida")
                        else fetch(url) { body ->
                            val found = parseM3u(body)
                            if (found.isEmpty()) toast("A URL respondeu, mas não trouxe canais M3U válidos. Confira o formato da lista.")
                            else { xtreamSession = null; items.clear(); items.addAll(found); enterCatalog() }
                        }
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        }
        renderForm()
    }

    private fun enterCatalog() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        home()
    }

    private fun showMenu() {
        begin("Menu", false)
        root.addView(text("♛ IVEPLAY  •  MENU", 24f, true, gold))
        gap(16)
        listOf("Início", "TV ao Vivo", "Filmes", "Séries", "Esportes", "Infantil", "Favoritos", "Histórico", "Buscar", "Configurações").forEach { page ->
            button(page, if (page == currentPage) orange else panel) { drawerOpen = false; navigate(page) }
        }
    }

    private fun navigate(page: String) {
        when (page) {
            "Início" -> home()
            "Configurações" -> settings()
            else -> catalog(page)
        }
    }

    private fun home() {
        begin("Início")
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ive_brand_mark)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(105)))
        gap(8)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(90, 26, 9), Color.rgb(25, 12, 15), Color.rgb(7, 7, 9))).apply { cornerRadius = dp(20).toFloat() }
        }
        hero.addView(text("♛  IVEPLAY", 29f, true, gold))
        hero.addView(text("Seu entretenimento em um só lugar", 16f, false, muted))
        val featured = items.firstOrNull { it.kind == "Filmes" && validUrl(it.image) }
            ?: items.firstOrNull { it.kind == "Séries" && validUrl(it.image) }
        if (featured != null) {
            val artwork = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(panel, 12) }
            hero.addView(artwork, LinearLayout.LayoutParams(-1, dp(145)).apply { topMargin = dp(10); bottomMargin = dp(8) })
            loadPoster(artwork, featured.image)
            hero.addView(text("EM DESTAQUE  •  " + featured.name, 15f, true, gold).apply { setOnClickListener { play(featured) } })
        }
        hero.addView(text("TV ao vivo • Filmes • Séries", 14f, false, Color.WHITE).apply { setPadding(0, dp(12), 0, dp(12)) })
        val watch = text(if (items.isEmpty()) "ADICIONAR CONTEÚDO" else "EXPLORAR CATÁLOGO", 15f, true).apply {
            gravity = Gravity.CENTER
            background = shape(orange)
            setOnClickListener { if (items.isEmpty()) settings() else catalog("Filmes") }
        }
        hero.addView(watch, LinearLayout.LayoutParams(-1, dp(46)))
        root.addView(hero)
        gap(22)
        root.addView(text("Categorias", 21f, true))
        gap(10)
        val categories = listOf(
            Triple("📺 TV ao Vivo", "TV ao Vivo", Color.rgb(155, 45, 18)),
            Triple("🎬 Filmes", "Filmes", Color.rgb(20, 76, 145)),
            Triple("🎞 Séries", "Séries", Color.rgb(96, 43, 138)),
            Triple("⚽ Esportes", "Esportes", Color.rgb(27, 112, 64)),
            Triple("🧸 Infantil", "Infantil", Color.rgb(158, 83, 27))
        )
        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, page, color) ->
                val tile = text(label, 15f, true).apply {
                    gravity = Gravity.CENTER
                    background = shape(color)
                    setOnClickListener { catalog(page) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(88), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(88), 1f))
            root.addView(row)
        }
        gap(18)
        if (items.isEmpty()) {
            root.addView(text("Conecte sua lista autorizada para mostrar canais, filmes e séries.", 15f, false, muted))
            gap(12)
            button("ENTRAR COM LISTA M3U") { m3uLogin() }
            button("ENTRAR COM XTREAM CODES", panel) { xtreamLogin() }
        } else {
            listOf("TV ao Vivo", "Filmes", "Séries").forEach { kind ->
                val matching = items.filter { it.kind == kind }.take(8)
                if (matching.isNotEmpty()) {
                    gap(14)
                    root.addView(text(if (kind == "TV ao Vivo") "Canais ao Vivo" else "$kind em Destaque", 20f, true))
                    gap(8)
                    val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    matching.forEach { entry ->
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(8), dp(8), dp(8), dp(8))
                            background = shape(panel, 12, Color.rgb(69, 43, 34))
                            setOnClickListener { play(entry) }
                        }
                        val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.rgb(40, 35, 35)) }
                        card.addView(poster, LinearLayout.LayoutParams(-1, dp(82)))
                        loadPoster(poster, entry.image)
                        card.addView(text(entry.name, 12f, true).apply { gravity = Gravity.CENTER; maxLines = 2 })
                        strip.addView(card, LinearLayout.LayoutParams(dp(130), dp(135)).apply { rightMargin = dp(9) })
                    }
                    root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) })
                }
            }
        }
        gap(20)
        root.addView(text("Assista no celular, Android TV ou TV Box", 13f, false, muted).apply { gravity = Gravity.CENTER })
    }

    private fun settings() {
        begin("Configurações")
        root.addView(text("Adicionar sua fonte de conteúdo autorizada", 16f, false, muted))
        gap(14)
        button("ENTRAR COM LISTA M3U") { m3uLogin() }
        button("ENTRAR COM XTREAM CODES", panel) { xtreamLogin() }
        gap(14)
        root.addView(text("Itens carregados: ${items.size}", 15f))
        gap(12)
        button("TROCAR CONTA / SAIR", panel) { items.clear(); xtreamSession = null; loginScreen() }
        button("VOLTAR AO INÍCIO", panel) { home() }
    }

    private fun m3uLogin() {
        begin("Adicionar lista M3U")
        val input = field("URL da lista M3U (https://...)")
        button("CARREGAR CONTEÚDO") {
            val link = input.text.toString().trim()
            if (!validUrl(link)) { toast("Informe uma URL HTTP ou HTTPS válida"); return@button }
            fetch(link) { body ->
                val found = parseM3u(body)
                if (found.isEmpty()) toast("A URL respondeu, mas não trouxe canais M3U válidos. Confira o formato da lista.")
                else { xtreamSession = null; items.clear(); items.addAll(found); toast("${items.size} itens carregados"); home() }
            }
        }
        button("VOLTAR", panel) { settings() }
    }

    private fun xtreamLogin() {
        begin("Xtream Codes")
        val server = field("Servidor: https://exemplo.com")
        val user = field("Usuário")
        val pass = field("Senha", true)
        button("CONECTAR") {
            val host = server.text.toString().trim().trimEnd('/')
            val username = user.text.toString().trim()
            val password = pass.text.toString()
            if (!validUrl(host) || username.isBlank() || password.isBlank()) {
                toast("Preencha servidor, usuário e senha")
            } else {
                loadXtream(host, username, password) { enterCatalog() }
            }
        }
        button("VOLTAR", panel) { settings() }
    }

    private fun loadXtream(host: String, username: String, password: String, onSuccess: () -> Unit) {
            val u = URLEncoder.encode(username, "UTF-8")
            val p = URLEncoder.encode(password, "UTF-8")
            val found = mutableListOf<MediaEntry>()
            val actions = listOf("get_live_streams", "get_vod_streams", "get_series")
            toast("Carregando catálogo...")
            thread {
                try {
                    // Resolve Xtream category IDs to the real group names.
                    val categoryNames = mutableMapOf<String, String>()
                    listOf("get_live_categories", "get_vod_categories", "get_series_categories").forEach { categoryAction ->
                        try {
                            val categories = JSONArray(download("$host/player_api.php?username=$u&password=$p&action=$categoryAction"))
                            for (i in 0 until categories.length()) {
                                val category = categories.optJSONObject(i) ?: continue
                                val id = category.optString("category_id")
                                val label = category.optString("category_name")
                                if (id.isNotBlank() && label.isNotBlank()) categoryNames["$categoryAction:$id"] = label
                            }
                        } catch (_: Exception) { /* Category endpoints may be unavailable. */ }
                    }
                    actions.forEach { action ->
                        try {
                            val body = download("$host/player_api.php?username=$u&password=$p&action=$action")
                            val array = JSONArray(body)
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                val name = obj.optString("name", "Conteúdo")
                                val categoryId = obj.optString("category_id")
                                val categoryAction = when (action) {
                                    "get_live_streams" -> "get_live_categories"
                                    "get_vod_streams" -> "get_vod_categories"
                                    else -> "get_series_categories"
                                }
                                val group = categoryNames["$categoryAction:$categoryId"] ?: "Outros"
                                val id = if (action == "get_series") obj.optString("series_id") else obj.optString("stream_id")
                                if (id.isBlank()) continue
                                when (action) {
                                    "get_live_streams" -> {
                                        val extension = if (obj.optString("container_extension").equals("m3u8", true)) "m3u8" else "ts"
                                        found.add(MediaEntry(name, "$host/live/$u/$p/$id.$extension", group, "TV ao Vivo", obj.optString("stream_icon")))
                                    }
                                    "get_vod_streams" -> {
                                        val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                                        found.add(MediaEntry(name, "$host/movie/$u/$p/$id.$ext", group, "Filmes", obj.optString("stream_icon")) )
                                    }
                                    // Series require selecting a season/episode; don't invent a playback URL.
                                    "get_series" -> found.add(MediaEntry(name, "", group, "Séries", obj.optString("cover"), id))
                                }
                            }
                        } catch (_: Exception) { /* Some authorized servers expose only selected categories. */ }
                    }
                    runOnUiThread {
                        if (found.isEmpty()) toast("Não foi possível carregar o catálogo. Confira o acesso.")
                        else { xtreamSession = Triple(host, username, password); items.clear(); items.addAll(found); toast("${items.size} itens carregados"); onSuccess() }
                    }
                } catch (e: Exception) { runOnUiThread { toast("Erro ao carregar: ${e.localizedMessage}") } }
            }
    }

    private fun mediaItem(link: String): MediaItem {
        val clean = link.substringBefore('?').lowercase()
        val builder = MediaItem.Builder().setUri(link)
        if (clean.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        return builder.build()
    }

    private fun attachPlaybackErrors(instance: ExoPlayer) {
        instance.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toast("Falha ao reproduzir: ${error.errorCodeName}. Confira a transmissão e o acesso.")
            }
        })
    }

    private fun validUrl(value: String) = value.startsWith("https://", true) || value.startsWith("http://", true)

    private fun download(link: String): String {
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain, */*")
            setRequestProperty("User-Agent", "IVEPLAY/2.0")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw java.io.IOException("Servidor respondeu HTTP $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        finally { connection.disconnect() }
    }

    private fun fetch(link: String, done: (String) -> Unit) {
        toast("Conectando...")
        thread {
            try { val result = download(link); runOnUiThread { done(result) } }
            catch (e: Exception) { runOnUiThread { toast("Falha na conexão: ${e.localizedMessage ?: "verifique o servidor"}") } }
        }
    }

    private fun parseM3u(content: String): List<MediaEntry> {
        val result = mutableListOf<MediaEntry>()
        var name = "Canal"
        var group = "TV ao Vivo"
        var image = ""
        val attr = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
        for (raw in content.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.startsWith("#EXTINF", true) -> {
                    name = line.substringAfter(',', "Canal").trim().ifBlank { "Canal" }
                    val attributes = attr.findAll(line.substringBeforeLast(',', line)).associate {
                        it.groupValues[1].lowercase() to (it.groupValues[2].ifBlank { it.groupValues[3].ifBlank { it.groupValues[4] } })
                    }
                    image = attributes["tvg-logo"].orEmpty()
                    group = attributes["group-title"].orEmpty().ifBlank { "TV ao Vivo" }
                }
                line.startsWith("#EXTGRP:", true) -> {
                    group = line.substringAfter(':').trim().ifBlank { "TV ao Vivo" }
                }
                validUrl(line) -> {
                    val lower = group.lowercase()
                    val kind = when {
                        listOf("filme", "movie", "vod", "cinema").any { it in lower } -> "Filmes"
                        listOf("série", "serie", "series", "temporada").any { it in lower } -> "Séries"
                        else -> "TV ao Vivo"
                    }
                    result.add(MediaEntry(name, line, group, kind, image))
                    name = "Canal"
                    group = "TV ao Vivo"
                    image = ""
                }
            }
        }
        return result
    }

    private fun filtered(page: String): List<MediaEntry> = when (page) {
        "Início", "Buscar" -> items
        "Favoritos" -> items.filter { it.url.isNotBlank() && it.url in favorites }
        "Histórico" -> history.mapNotNull { key -> items.find { it.url == key && key.isNotBlank() } }
        "Esportes" -> items.filter { listOf("sport", "esport", "futebol", "football", "espn").any { word -> word in it.group.lowercase() || word in it.name.lowercase() } }
        "Infantil" -> items.filter { listOf("infantil", "kids", "desenho", "cartoon").any { word -> word in it.group.lowercase() || word in it.name.lowercase() } }
        else -> items.filter { it.kind == page }
    }

    // Four-column live-TV view: navigation, groups, channels, preview.
    // Independently scrolling channel/category lists: no nested ScrollView gesture conflicts.
    private fun liveCatalog() {
        begin("TV ao Vivo", false)
        // Avoid a parent ScrollView intercepting vertical swipes and TV remote navigation.
        root.layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        setContentView(root)
        val screen = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(screen, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), 0); background = shape(panel, 10) }
        val groupsList = ListView(this).apply { dividerHeight = dp(4); setBackgroundColor(bg); isFocusable = true; isFocusableInTouchMode = true }
        val channelsList = ListView(this).apply { dividerHeight = dp(4); setBackgroundColor(bg); isFocusable = true; isFocusableInTouchMode = true }
        val preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), 0) }
        screen.addView(ScrollView(this).apply { addView(nav) }, LinearLayout.LayoutParams(0, -1, 1.2f))
        screen.addView(groupsList, LinearLayout.LayoutParams(0, -1, 1.6f))
        screen.addView(channelsList, LinearLayout.LayoutParams(0, -1, 2.2f))
        screen.addView(preview, LinearLayout.LayoutParams(0, -1, 3.2f))
        nav.addView(text("♛ IVEPLAY", 17f, true, gold))
        listOf("Início", "TV ao Vivo", "Filmes", "Séries", "Esportes", "Infantil", "Favoritos", "Histórico", "Configurações").forEach { page ->
            nav.addView(text(page, 12f, page == "TV ao Vivo", if (page == "TV ao Vivo") orange else Color.WHITE).apply {
                setPadding(dp(6), dp(10), 0, dp(10))
                isFocusable = true
                setOnClickListener { navigate(page) }
            })
        }
        val channels = items.filter { it.kind == "TV ao Vivo" }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val groups = channels.map { it.group.ifBlank { "Outros" } }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        var selectedGroup: String? = null
        var selected: MediaEntry? = null
        val playerView = PlayerView(this).apply { useController = true; setBackgroundColor(Color.BLACK) }
        val playerTitle = text("Selecione um canal", 15f, true, orange)
        preview.addView(text("PRÉ-VISUALIZAÇÃO", 14f, true, orange))
        preview.addView(text("${channels.size} canais carregados", 11f, false, muted))
        preview.addView(playerView, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(10) })
        preview.addView(playerTitle)
        preview.addView(text("Programação EPG não disponível nesta versão", 12f, false, muted))
        preview.addView(text("TELA CHEIA  ▶", 13f, true, orange).apply {
            setPadding(0, dp(14), 0, dp(10))
            isFocusable = true
            setOnClickListener { selected?.let { play(it) } }
        })
        fun showPreview(entry: MediaEntry) {
            selected = entry
            playerTitle.text = entry.name
            player?.release()
            player = null
            playerView.player = null
            if (entry.url.isBlank()) { toast("Canal sem endereço de reprodução"); return }
            try {
                player = ExoPlayer.Builder(this).build().also {
                    playerView.player = it
                    attachPlaybackErrors(it)
                    it.setMediaItem(mediaItem(entry.url))
                    it.prepare()
                    it.playWhenReady = true
                }
            } catch (_: Exception) { toast("Não foi possível abrir a pré-visualização") }
        }
        fun refreshChannels() {
            val matches = channels.filter { selectedGroup == null || it.group.ifBlank { "Outros" } == selectedGroup }
            channelsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, matches.map { "▶  " + it.name })
            channelsList.setOnItemClickListener { _, _, position, _ -> showPreview(matches[position]) }
            channelsList.setSelection(0)
        }
        val choices = listOf("Todos os Canais" to null) + groups.map { it to it }
        groupsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, choices.map { it.first })
        groupsList.setOnItemClickListener { _, _, position, _ ->
            selectedGroup = choices[position].second
            refreshChannels()
        }
        refreshChannels()
    }

    private fun catalog(page: String) {
        if (page == "TV ao Vivo") { liveCatalog(); return }
        begin(page)
        val search = field("Buscar por nome ou categoria")
        val source = filtered(page)
        val groups = source.map { it.group.ifBlank { "Outros" } }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        var selectedGroup: String? = null
        val groupBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val groupScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(groupBar)
        }
        root.addView(groupScroll)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val results = source.filter {
                (selectedGroup == null || it.group.ifBlank { "Outros" } == selectedGroup) &&
                (it.name.contains(query, true) || it.group.contains(query, true))
            }
            if (results.isEmpty()) {
                list.addView(text(if (items.isEmpty()) "Adicione uma lista em Configurações." else "Nenhum item encontrado.", 15f, false, muted))
            }
            results.take(250).forEach { entry ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(panel) }
                val info = text("▶  ${entry.name}\n     ${entry.group}", 14f, true).apply {
                    setPadding(dp(12), dp(9), dp(4), dp(9))
                    maxLines = 3
                    setOnClickListener { play(entry) }
                }
                if (validUrl(entry.image)) {
                    val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    row.addView(poster, LinearLayout.LayoutParams(dp(56), dp(72)))
                    loadPoster(poster, entry.image)
                }
                row.addView(info, LinearLayout.LayoutParams(0, dp(72), 1f))
                val fav = text(if (entry.url in favorites && entry.url.isNotBlank()) "★" else "☆", 26f, true, orange).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        if (entry.url.isBlank()) { toast("Selecione um episódio para favoritar"); return@setOnClickListener }
                        if (!favorites.add(entry.url)) favorites.remove(entry.url)
                        getPreferences(MODE_PRIVATE).edit().putStringSet("favorites", favorites.toSet()).apply()
                        render(query)
                    }
                }
                row.addView(fav, LinearLayout.LayoutParams(dp(48), dp(72)))
                list.addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(9) })
            }
            if (results.size > 250) list.addView(text("Mostrando 250 resultados. Use a busca ou escolha uma categoria.", 13f, false, muted))
        }
        // A horizontal group selector keeps networks and genres separate.
        fun addGroupChip(label: String, group: String?) {
            val chip = text(label, 13f, true, if (selectedGroup == group) Color.BLACK else Color.WHITE).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = shape(if (selectedGroup == group) orange else panel, 9, orange)
                setOnClickListener {
                    selectedGroup = group
                    groupBar.removeAllViews()
                    addGroupChip("Todos", null)
                    groups.forEach { addGroupChip(it, it) }
                    render(search.text.toString())
                }
            }
            groupBar.addView(chip, LinearLayout.LayoutParams(-2, dp(42)).apply {
                rightMargin = dp(8)
                bottomMargin = dp(10)
            })
        }
        addGroupChip("Todos", null)
        groups.forEach { addGroupChip(it, it) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
    }

    private fun loadPoster(target: ImageView, link: String) {
        if (!validUrl(link)) return
        target.tag = link
        imageLoader.execute {
            try {
                val conn = URL(link).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                val bytes = try {
                    conn.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            total += read
                            if (total > 4 * 1024 * 1024) return@execute
                            output.write(chunk, 0, read)
                        }
                        output.toByteArray()
                    }
                } finally { conn.disconnect() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample })
                if (bitmap != null) runOnUiThread {
                    if (target.tag == link && target.isAttachedToWindow) target.setImageBitmap(bitmap)
                }
            } catch (_: Exception) { /* Unavailable artwork must not interrupt browsing. */ }
        }
    }

    private fun seriesEpisodes(entry: MediaEntry) {
        val session = xtreamSession
        if (session == null) { toast("Esta lista não fornece detalhes de episódios Xtream"); return }
        begin(entry.name)
        root.addView(text("Carregando temporadas e episódios...", 16f, false, muted))
        val (host, username, password) = session
        thread {
            try {
                val u = URLEncoder.encode(username, "UTF-8")
                val p = URLEncoder.encode(password, "UTF-8")
                val response = JSONObject(download("$host/player_api.php?username=$u&password=$p&action=get_series_info&series_id=${entry.seriesId}"))
                val episodes = response.optJSONObject("episodes") ?: JSONObject()
                val seasons = episodes.keys().asSequence().toList().sortedWith(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
                runOnUiThread {
                    begin(entry.name)
                    if (seasons.isEmpty()) { root.addView(text("Nenhum episódio disponível nesta lista.", 15f)); return@runOnUiThread }
                    seasons.forEach { season ->
                        root.addView(text("TEMPORADA $season", 18f, true, orange))
                        val list = episodes.optJSONArray(season) ?: JSONArray()
                        for (i in 0 until list.length()) {
                            val ep = list.optJSONObject(i) ?: continue
                            val id = ep.optString("id")
                            if (id.isBlank()) continue
                            val title = ep.optString("title", "Episódio ${i + 1}")
                            val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                            val url = "$host/series/$u/$p/$id.$ext"
                            button(title, panel) { play(MediaEntry(title, url, entry.group, "Séries", entry.image)) }
                        }
                    }
                }
            } catch (_: Exception) { runOnUiThread { toast("Não foi possível carregar os episódios desta série") } }
        }
    }

    private fun play(entry: MediaEntry) {
        if (entry.seriesId.isNotBlank() && entry.kind == "Séries") {
            seriesEpisodes(entry)
            return
        }
        if (entry.url.isBlank()) { toast("Conteúdo sem endereço de reprodução"); return }
        begin(entry.name)
        val view = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
        }
        root.addView(view, LinearLayout.LayoutParams(-1, dp(280)))
        try {
            player = ExoPlayer.Builder(this).build().also {
                view.player = it
                attachPlaybackErrors(it)
                it.setMediaItem(mediaItem(entry.url))
                it.prepare()
                it.playWhenReady = true
            }
            history.remove(entry.url)
            history.add(0, entry.url)
            while (history.size > 100) history.removeAt(history.lastIndex)
            getPreferences(MODE_PRIVATE).edit().putString("history", history.joinToString("\n")).apply()
        } catch (e: Exception) { toast("Não foi possível iniciar a reprodução") }
        gap(16)
        root.addView(text("${entry.kind}  •  ${entry.group}", 15f, false, muted))
        gap(18)
        button("VOLTAR PARA ${entry.kind.uppercase()}") { catalog(entry.kind) }
        button("INÍCIO", panel) { home() }
    }

    override fun onDestroy() { player?.release(); player = null; imageLoader.shutdownNow(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (player != null) home() else if (currentPage != "Início") home() else super.onBackPressed() }
}
