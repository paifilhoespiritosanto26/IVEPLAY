package com.ive.play

import android.app.Activity
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(9, 9, 12)
    private val panel = Color.rgb(25, 25, 29)
    private val orange = Color.rgb(255, 104, 22)
    private val muted = Color.rgb(175, 175, 185)
    private lateinit var root: LinearLayout
    private var player: ExoPlayer? = null
    private val items = mutableListOf<MediaEntry>()
    private val favorites = linkedSetOf<String>()
    private val history = mutableListOf<String>()
    private var drawerOpen = false
    private var currentPage = "Início"

    data class MediaEntry(val name: String, val url: String, val group: String, val kind: String = "TV ao Vivo")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        history.addAll(getPreferences(MODE_PRIVATE).getString("history", "")!!.split("\n").filter { it.isNotBlank() })
        home()
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
        bar.addView(text("IVEPLAY", 23f, true, orange), LinearLayout.LayoutParams(0, dp(48), 1f))
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

    private fun showMenu() {
        begin("Menu", false)
        root.addView(text("IVEPLAY  •  MENU", 24f, true, orange))
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
            setImageResource(R.drawable.ive_logo)
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
        hero.addView(text("IVEPLAY", 29f, true))
        hero.addView(text("Seu entretenimento em um só lugar", 16f, false, muted))
        hero.addView(text("TV ao vivo • Filmes • Séries", 14f, false, Color.WHITE).apply { setPadding(0, dp(12), 0, dp(12)) })
        val watch = text(if (items.isEmpty()) "ADICIONAR CONTEÚDO" else "ASSISTIR AGORA", 15f, true).apply {
            gravity = Gravity.CENTER
            background = shape(orange)
            setOnClickListener { if (items.isEmpty()) settings() else play(items.first()) }
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
                        card.addView(text(if (kind == "TV ao Vivo") "▶" else "🎬", 29f, true, orange).apply { gravity = Gravity.CENTER })
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
                if (found.isEmpty()) toast("Nenhum conteúdo encontrado na lista")
                else { items.clear(); items.addAll(found); toast("${items.size} itens carregados"); home() }
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
            val password = pass.text.toString().trim()
            if (!validUrl(host) || username.isBlank() || password.isBlank()) {
                toast("Preencha servidor, usuário e senha"); return@button
            }
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
                                    "get_live_streams" -> found.add(MediaEntry(name, "$host/live/$u/$p/$id.ts", group, "TV ao Vivo"))
                                    "get_vod_streams" -> {
                                        val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                                        found.add(MediaEntry(name, "$host/movie/$u/$p/$id.$ext", group, "Filmes"))
                                    }
                                    // Series require selecting a season/episode; don't invent a playback URL.
                                    "get_series" -> found.add(MediaEntry(name, "", group, "Séries"))
                                }
                            }
                        } catch (_: Exception) { /* Some authorized servers expose only selected categories. */ }
                    }
                    runOnUiThread {
                        if (found.isEmpty()) toast("Não foi possível carregar o catálogo. Confira o acesso.")
                        else { items.clear(); items.addAll(found); toast("${items.size} itens carregados"); home() }
                    }
                } catch (e: Exception) { runOnUiThread { toast("Erro ao carregar: ${e.localizedMessage}") } }
            }
        }
        button("VOLTAR", panel) { settings() }
    }

    private fun validUrl(value: String) = value.startsWith("https://", true) || value.startsWith("http://", true)

    private fun download(link: String): String {
        val connection = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IVEPLAY/2.0")
        }
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
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
        for (line in content.lineSequence()) {
            val s = line.trim()
            if (s.startsWith("#EXTINF", true)) {
                name = s.substringAfterLast(',', "Canal").trim().ifBlank { "Canal" }
                group = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.ifBlank { "TV ao Vivo" } ?: "TV ao Vivo"
            } else if (validUrl(s)) {
                val lower = group.lowercase()
                val kind = when {
                    "filme" in lower || "movie" in lower || "vod" in lower -> "Filmes"
                    "série" in lower || "serie" in lower || "series" in lower -> "Séries"
                    else -> "TV ao Vivo"
                }
                result.add(MediaEntry(name, s, group, kind))
                name = "Canal"
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

    private fun catalog(page: String) {
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
            if (results.size > 250) list.addView(text("Mostrando 250 resultados. Refine sua busca.", 13f, false, muted))
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

    private fun play(entry: MediaEntry) {
        if (entry.url.isBlank()) {
            toast("Esta série precisa de seleção de temporada e episódio. Recurso em desenvolvimento.")
            return
        }
        begin(entry.name)
        val view = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
        }
        root.addView(view, LinearLayout.LayoutParams(-1, dp(280)))
        try {
            player = ExoPlayer.Builder(this).build().also {
                view.player = it
                it.setMediaItem(MediaItem.fromUri(entry.url))
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

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (player != null) home() else if (currentPage != "Início") home() else super.onBackPressed() }
}
