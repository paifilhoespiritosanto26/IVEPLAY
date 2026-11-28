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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
    private val imageLoader = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(48), ThreadPoolExecutor.DiscardOldestPolicy())
    private var drawerOpen = false
    private var currentPage = "Início"
    private var playingKind: String? = null
    private var playbackOrigin: String? = null
    private var sportsChannelPageIndex = 0
    private var sportsChannelQuery = ""
    private var sportsChannelSelectedUrl: String? = null
    private var resumePlaybackOnStart = false
    private val sportsRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sportsRefreshTask: Runnable? = null

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

    private fun begin(page: String, menu: Boolean = true, scrollable: Boolean = true) {
        sportsRefreshTask?.let { sportsRefreshHandler.removeCallbacks(it) }
        sportsRefreshTask = null
        player?.release()
        player = null
        resumePlaybackOnStart = false
        currentPage = page
        if (page == "Início" || page == "TV ao Vivo" || page == "Menu") playingKind = null
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Preserve screen space for the four-pane live-TV media center.
            if (page == "TV ao Vivo") {
                setPadding(dp(6), dp(4), dp(6), dp(4))
            } else {
                setPadding(dp(16), dp(12), dp(16), dp(30))
            }
            setBackgroundColor(bg)
        }
        if (scrollable) {
            val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
            setContentView(scroll)
        } else {
            setContentView(root)
        }
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
        // Keep the entire IVEPLAY experience landscape, including sign-in.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        begin("Login", false)
        root.gravity = Gravity.CENTER_HORIZONTAL
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ive_brand_mark)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val shortLandscape = resources.configuration.screenHeightDp < 420
        // A two-column sign-in keeps the form usable on short landscape phones.
        val loginColumns = LinearLayout(this).apply {
            orientation = if (shortLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(loginColumns, LinearLayout.LayoutParams(-1, -2))
        val branding = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        loginColumns.addView(branding, if (shortLandscape) {
            LinearLayout.LayoutParams(0, -1, 0.9f)
        } else {
            LinearLayout.LayoutParams(-1, -2)
        })
        branding.addView(logo, LinearLayout.LayoutParams(-1, dp(if (shortLandscape) 80 else 110)))
        branding.addView(text("IVEPLAY", if (shortLandscape) 24f else 32f, true, gold).apply {
            gravity = Gravity.CENTER
        })
        branding.addView(text("Seu entretenimento em um só lugar", 13f, false, muted).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        val loginFields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (shortLandscape) setPadding(dp(12), dp(4), dp(4), dp(4))
            else setPadding(0, dp(20), 0, 0)
        }
        val formScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(loginFields)
        }
        // Use the actual landscape height instead of a fixed 320dp form.
        // This leaves the sign-in button reachable on smaller Android phones.
        val availableFormHeight = (resources.configuration.screenHeightDp - 44).coerceAtLeast(180)
        loginColumns.addView(formScroll, if (shortLandscape) {
            LinearLayout.LayoutParams(0, dp(availableFormHeight), 1.5f)
        } else {
            LinearLayout.LayoutParams(-1, -2)
        })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loginFields.addView(tabs)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        loginFields.addView(form)
        var xtream = true
        fun renderForm() {
            tabs.removeAllViews()
            form.removeAllViews()
            listOf("Xtream Codes" to true, "Lista M3U" to false).forEach { (label, mode) ->
                tabs.addView(text(label, 15f, true, if (xtream == mode) Color.BLACK else Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = shape(if (xtream == mode) orange else panel)
                    isFocusable = true
                    isClickable = true
                    contentDescription = "Entrar com $label"
                    setOnFocusChangeListener { view, focused ->
                        view.background = shape(if (focused) gold else if (xtream == mode) orange else panel)
                        setTextColor(if (focused || xtream == mode) Color.BLACK else Color.WHITE)
                    }
                    setOnClickListener {
                        if (xtream != mode) {
                            xtream = mode
                            renderForm()
                            formScroll.scrollTo(0, 0)
                        }
                    }
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
                    inputType = when {
                        secret -> android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        hint.startsWith("URL") -> android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_URI
                        else -> android.text.InputType.TYPE_CLASS_TEXT
                    }
                    imeOptions = if (secret || hint == "URL da lista M3U") {
                        android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    } else {
                        android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                    }
                    isFocusable = true
                    contentDescription = hint
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
                isFocusable = true
                isClickable = true
                contentDescription = "Conectar à lista de conteúdo"
                setOnFocusChangeListener { view, focused ->
                    view.background = shape(if (focused) gold else orange)
                }
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
        // IVEPLAY uses a landscape media-center layout on phones, TV and TV Box.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        home()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // AndroidManifest handles orientation changes, so rebuild responsive columns
        // after the screen has actually rotated.
        if (items.isNotEmpty()) {
            when (currentPage) {
                "Início" -> home()
                "TV ao Vivo" -> liveCatalog()
            }
        }
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
            "TV ao Vivo" -> liveCatalog()
            "Esportes" -> sportsSchedule()
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
        // Sports-first hero: no arbitrary movie artwork or unrelated promotions.
        val sportsCount = items.count {
            it.kind == "Esportes" || (it.kind == "TV ao Vivo" &&
                listOf("esporte", "sport", "futebol").any { word ->
                    it.group.contains(word, ignoreCase = true)
                })
        }
        hero.addView(text("⚡  PROGRAMAÇÃO ESPORTIVA EM DESTAQUE", 19f, true, gold).apply {
            setPadding(0, dp(14), 0, dp(6))
        })
        hero.addView(text(
            if (sportsCount > 0) "$sportsCount opções esportivas disponíveis na sua lista"
            else "Acompanhe os canais e conteúdos esportivos da sua lista",
            14f, false, Color.WHITE
        ).apply { setPadding(0, 0, 0, dp(12)) })
        val watch = text(if (items.isEmpty()) "ADICIONAR CONTEÚDO" else "EXPLORAR ESPORTES", 15f, true).apply {
            gravity = Gravity.CENTER
            background = shape(orange)
            isFocusable = true
            setOnClickListener { navigate("Esportes") }
        }
        hero.addView(watch, LinearLayout.LayoutParams(-1, dp(46)))
        root.addView(hero)
        gap(22)
        root.addView(text("Categorias", 21f, true))
        gap(10)
        // Circular neon category buttons, inspired by the approved IVEPLAY preview.
        // Horizontal scrolling keeps all five buttons accessible on smaller phones.
        val categories = listOf(
            Triple("TV ao Vivo", R.drawable.ive_category_tv, Color.rgb(255, 109, 48)),
            Triple("Filmes", R.drawable.ive_category_film, Color.rgb(67, 152, 255)),
            Triple("Séries", R.drawable.ive_category_series, Color.rgb(198, 79, 255)),
            Triple("Esportes", R.drawable.ive_category_sports, Color.rgb(66, 231, 93)),
            Triple("Infantil", R.drawable.ive_category_kids, Color.rgb(255, 67, 158))
        )
        val circleSize = if (resources.configuration.screenHeightDp < 420) 112 else 148
        val categoryStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        categories.forEach { (page, iconRes, accent) ->
            fun circleBackground(focused: Boolean): GradientDrawable =
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                    Color.argb(if (focused) 190 else 120, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(245, 9, 9, 16)
                )).apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(if (focused) 4 else 2), accent)
                }
            val circle = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = circleBackground(false)
                isFocusable = true
                isClickable = true
                contentDescription = "Abrir $page"
                elevation = dp(5).toFloat()
                setOnFocusChangeListener { view, focused ->
                    view.background = circleBackground(focused)
                    view.scaleX = if (focused) 1.06f else 1f
                    view.scaleY = if (focused) 1.06f else 1f
                }
                setOnClickListener { navigate(page) }
            }
            circle.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                contentDescription = null
                isFocusable = false
            }, LinearLayout.LayoutParams(dp(circleSize / 3), dp(circleSize / 3)))
            circle.addView(text(page, if (circleSize < 130) 13f else 16f, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), 0)
            })
            categoryStrip.addView(circle, LinearLayout.LayoutParams(dp(circleSize), dp(circleSize)).apply {
                setMargins(dp(7), dp(5), dp(7), dp(5))
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(categoryStrip)
        }, LinearLayout.LayoutParams(-1, -2))
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

    private fun sportsChannelsPage() {
        begin("Canais esportivos")
        root.addView(text("📺  CANAIS ESPORTIVOS", 22f, true, gold))
        root.addView(text("Canais disponíveis na sua própria lista. A presença de um canal não confirma a transmissão de um jogo.", 13f, false, muted))
        gap(12)
        val channels = filtered("Esportes")
            .filter { it.kind == "TV ao Vivo" && it.url.isNotBlank() }
            .sortedBy { it.name.trim().replace(Regex("""^\\d+[\\s._:|\\-]*"""), "").lowercase(java.util.Locale.ROOT) }
        val search = field("Buscar canal esportivo")
        root.addView(text("Use a busca para encontrar canais pelo nome ou grupo.", 12f, false, muted))
        gap(8)
        val channelRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(channelRows)
        var pageIndex = sportsChannelPageIndex
        fun showChannels(query: String) {
            channelRows.removeAllViews()
            val matching = channels.filter {
                it.name.contains(query.trim(), ignoreCase = true) ||
                    it.group.contains(query.trim(), ignoreCase = true)
            }
            if (matching.isEmpty()) {
                channelRows.addView(text("Nenhum canal encontrado.", 14f, false, muted))
                return
            }
            val pageSize = 40
            val totalPages = ((matching.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            pageIndex = pageIndex.coerceIn(0, totalPages - 1)
            sportsChannelPageIndex = pageIndex
            channelRows.addView(text("Canais ${pageIndex * pageSize + 1}–${minOf((pageIndex + 1) * pageSize, matching.size)} de ${matching.size} • Página ${pageIndex + 1}/${totalPages}", 12f, false, gold))
            matching.drop(pageIndex * pageSize).take(pageSize).forEach { entry ->
                val item = text("▶  ${entry.name}\n${entry.group}", 15f, true).apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = shape(panel, 12, Color.DKGRAY)
                    isFocusable = true
                    isClickable = true
                    setOnClickListener {
                        sportsChannelQuery = search.text.toString()
                        sportsChannelPageIndex = pageIndex
                        sportsChannelSelectedUrl = entry.url
                        play(entry)
                    }
                    setOnFocusChangeListener { view, focused ->
                        view.background = shape(if (focused) Color.rgb(58, 38, 24) else panel,
                            12, if (focused) orange else Color.DKGRAY)
                        view.scaleX = if (focused) 1.015f else 1f
                        view.scaleY = if (focused) 1.015f else 1f
                    }
                }
                channelRows.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                if (entry.url == sportsChannelSelectedUrl) item.post { item.requestFocus() }
            }
            if (totalPages > 1) {
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val previous = text("◀ ANTERIOR", 14f, true, gold).apply {
                    gravity = Gravity.CENTER
                    isFocusable = pageIndex > 0
                    isClickable = pageIndex > 0
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    background = shape(panel, 10, gold)
                    alpha = if (pageIndex > 0) 1f else 0.4f
                    setOnClickListener {
                        if (pageIndex > 0) { pageIndex--; sportsChannelPageIndex = pageIndex; showChannels(search.text.toString()) }
                    }
                }
                val next = text("PRÓXIMA ▶", 14f, true, gold).apply {
                    gravity = Gravity.CENTER
                    isFocusable = pageIndex < totalPages - 1
                    isClickable = pageIndex < totalPages - 1
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    background = shape(panel, 10, gold)
                    alpha = if (pageIndex < totalPages - 1) 1f else 0.4f
                    setOnClickListener {
                        if (pageIndex < totalPages - 1) { pageIndex++; sportsChannelPageIndex = pageIndex; showChannels(search.text.toString()) }
                    }
                }
                controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
                controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
                channelRows.addView(controls)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pageIndex = 0
                sportsChannelPageIndex = 0
                sportsChannelQuery = s?.toString() ?: ""
                showChannels(sportsChannelQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val restoredQuery = sportsChannelQuery
        if (restoredQuery.isNotEmpty()) {
            search.setText(restoredQuery)
            pageIndex = sportsChannelPageIndex
            showChannels(restoredQuery)
        } else showChannels("")

        gap(12)
        button("VOLTAR À PROGRAMAÇÃO", panel) { sportsSchedule() }
    }

    private fun sportsSchedule() {
        begin("Esportes")
        root.addView(text("⚽  JOGOS E PROGRAMAÇÃO", 22f, true, gold))
        root.addView(text("Brasileirão • Champions League • principais ligas", 13f, false, muted))
        gap(10)
        val sportsChannels = filtered("Esportes").filter { it.kind == "TV ao Vivo" && it.url.isNotBlank() }
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        if (sportsChannels.isNotEmpty()) {
            button("📺  CANAIS ESPORTIVOS DA MINHA LISTA (${sportsChannels.size})", panel) {
                sportsChannelsPage()
            }
            gap(8)
        }
        val token = getPreferences(MODE_PRIVATE).getString("football_data_token", "") ?: ""
        val status = text("Consultando programação...", 13f, false, muted)
        root.addView(status)
        val liveTitle = text("🔴  AO VIVO AGORA", 18f, true, orange)
        val liveRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val upcomingTitle = text("📅  PRÓXIMOS JOGOS", 18f, true, gold)
        val upcomingRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gap(12)
        root.addView(liveTitle)
        root.addView(liveRows)
        gap(14)
        root.addView(upcomingTitle)
        root.addView(upcomingRows)
        gap(10)
        val finishedTitle = text("🏁  RESULTADOS DE HOJE", 18f, true, gold)
        val finishedRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(finishedTitle)
        root.addView(finishedRows)
        gap(10)
        val zone = java.time.ZoneId.of("America/Sao_Paulo")
        fun renderMatch(match: JSONObject, live: Boolean, finished: Boolean = false) {
            val homeTeam = match.optJSONObject("homeTeam")?.optString("shortName")?.takeIf { it.isNotBlank() }
                ?: match.optJSONObject("homeTeam")?.optString("name") ?: "Mandante"
            val awayTeam = match.optJSONObject("awayTeam")?.optString("shortName")?.takeIf { it.isNotBlank() }
                ?: match.optJSONObject("awayTeam")?.optString("name") ?: "Visitante"
            val competition = match.optJSONObject("competition")?.optString("name") ?: "Campeonato"
            val kickoff = try {
                val utc = java.time.Instant.parse(match.getString("utcDate"))
                java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .withZone(zone).format(utc)
            } catch (_: Exception) { "Horário indisponível" }
            val score = match.optJSONObject("score")?.optJSONObject("fullTime")
            val goalsHome = score?.opt("home")
            val goalsAway = score?.opt("away")
            val scoreText = if ((live || finished) && goalsHome is Number && goalsAway is Number)
                "${goalsHome.toInt()}  ×  ${goalsAway.toInt()}" else "×"
            val accent = if (live) Color.rgb(255, 77, 67) else if (finished) muted else gold
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent)), panel)).apply {
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent)))
                }
            }
            val tag = if (live) "● AO VIVO" else if (finished) "ENCERRADO" else "AGENDADO"
            card.addView(text("$tag  •  $competition", 12f, true, accent))
            val confrontation = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            confrontation.addView(text(homeTeam, 15f, true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            confrontation.addView(text(scoreText, 20f, true, accent).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(84), -2))
            confrontation.addView(text(awayTeam, 15f, true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(confrontation, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            })
            card.addView(text("🕒  $kickoff • Horário de Brasília", 12f, false, muted))
            (if (live) liveRows else if (finished) finishedRows else upcomingRows).addView(card,
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        if (token.isBlank()) {
            status.text = "Para exibir jogos reais, configure sua chave gratuita da API esportiva."
            liveRows.addView(text("Sem dados ao vivo configurados.", 13f, false, muted))
            upcomingRows.addView(text("Os horários aparecerão após configurar a API.", 13f, false, muted))
            finishedRows.addView(text("Resultados indisponíveis sem configuração.", 13f, false, muted))
            button("CONFIGURAR API ESPORTIVA") { settings() }
            return
        }
        button("ATUALIZAR JOGOS") { sportsSchedule() }
        // Refresh only while the sports page remains visible.
        sportsRefreshTask = Runnable {
            if (currentPage == "Esportes" && !isFinishing && !isDestroyed) sportsSchedule()
        }
        sportsRefreshHandler.postDelayed(sportsRefreshTask!!, 120_000L)
        thread {
            try {
                val today = java.time.LocalDate.now(zone)
                val start = today.toString()
                val end = today.plusDays(7).toString()
                val url = "https://api.football-data.org/v4/matches?dateFrom=$start&dateTo=$end&competitions=BSA,CL,PL,PD,SA,BL1,FL1"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("X-Auth-Token", token)
                    setRequestProperty("Accept", "application/json")
                }
                val body = try {
                    if (connection.responseCode !in 200..299)
                        throw java.io.IOException("API respondeu HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val matches = JSONObject(body).optJSONArray("matches") ?: JSONArray()
                val live = mutableListOf<JSONObject>()
                val upcoming = mutableListOf<JSONObject>()
                val finished = mutableListOf<JSONObject>()
                for (i in 0 until matches.length()) {
                    val match = matches.optJSONObject(i) ?: continue
                    when (match.optString("status")) {
                        "LIVE", "IN_PLAY", "PAUSED" -> live.add(match)
                        "SCHEDULED", "TIMED" -> upcoming.add(match)
                        "FINISHED" -> {
                            val matchDate = try {
                                java.time.Instant.parse(match.getString("utcDate")).atZone(zone).toLocalDate()
                            } catch (_: Exception) { null }
                            if (matchDate == today) finished.add(match)
                        }
                    }
                }
                upcoming.sortBy { it.optString("utcDate") }
                finished.sortByDescending { it.optString("utcDate") }
                runOnUiThread {
                    if (currentPage != "Esportes") return@runOnUiThread
                    status.text = "Dados: football-data.org • Brasília • Atualização automática a cada 2 minutos"
                    if (live.isEmpty()) liveRows.addView(text("Nenhum jogo ao vivo informado pela API.", 13f, false, muted))
                    else live.forEach { renderMatch(it, true) }
                    if (upcoming.isEmpty()) upcomingRows.addView(text("Nenhum próximo jogo informado pela API.", 13f, false, muted))
                    else upcoming.take(35).forEach { renderMatch(it, false) }
                    if (finished.isEmpty()) finishedRows.addView(text("Nenhum resultado de hoje informado pela API.", 13f, false, muted))
                    else finished.take(20).forEach { renderMatch(it, false, true) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (currentPage == "Esportes") {
                        liveRows.removeAllViews()
                        upcomingRows.removeAllViews()
                        finishedRows.removeAllViews()
                        status.text = "Não foi possível consultar jogos: ${e.localizedMessage ?: "verifique a conexão e a chave"}"
                    }
                }
            }
        }
    }

    private fun settings() {
        begin("Configurações")
        root.addView(text("Adicionar sua fonte de conteúdo autorizada", 16f, false, muted))
        gap(14)
        root.addView(text("PROGRAMAÇÃO ESPORTIVA", 17f, true, gold))
        root.addView(text("Chave da API football-data.org (plano gratuito, obtida pelo usuário).", 12f, false, muted))
        val sportsToken = field("Chave da API esportiva")
        sportsToken.setText(getPreferences(MODE_PRIVATE).getString("football_data_token", ""))
        button("SALVAR CHAVE ESPORTIVA") {
            getPreferences(MODE_PRIVATE).edit().putString("football_data_token", sportsToken.text.toString().trim()).apply()
            toast("Chave salva neste aparelho")
        }
        button("VER PROGRAMAÇÃO ESPORTIVA", panel) { sportsSchedule() }

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
            toast("Baixando e organizando lista M3U...")
            thread {
                try {
                    // Both network and parsing can be expensive for very large playlists.
                    val found = parseM3u(download(link))
                    runOnUiThread {
                        if (found.isEmpty()) {
                            toast("A URL respondeu, mas não trouxe canais M3U válidos. Confira o formato da lista.")
                        } else {
                            xtreamSession = null
                            items.clear()
                            items.addAll(found)
                            toast("${items.size} itens carregados")
                            enterCatalog()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        toast("Falha ao carregar M3U: ${e.localizedMessage ?: "verifique o servidor"}")
                    }
                }
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
            val failures = mutableListOf<String>()
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
                        } catch (e: Exception) {
                            failures.add("$action: ${e.localizedMessage ?: "resposta inválida"}")
                        }
                    }
                    runOnUiThread {
                        if (found.isEmpty()) toast("Catálogo vazio. ${failures.firstOrNull() ?: "Verifique usuário, senha e permissões da lista."}")
                        else { xtreamSession = Triple(host, username, password); items.clear(); items.addAll(found); toast("${items.size} itens carregados" + if (failures.isNotEmpty()) " • Algumas categorias falharam" else ""); onSuccess() }
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

    // EXTINF titles start after the first comma outside quoted attribute values.
    private fun extinfSeparator(line: String): Int {
        var quote: Char? = null
        for (index in line.indices) {
            val char = line[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '"' || char == '\'') {
                quote = char
            } else if (char == ',') {
                return index
            }
        }
        return -1
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
                    val separator = extinfSeparator(line)
                    val metadata = if (separator >= 0) line.substring(0, separator) else line
                    val attributes = attr.findAll(metadata).associate {
                        it.groupValues[1].lowercase() to (it.groupValues[2].ifBlank { it.groupValues[3].ifBlank { it.groupValues[4] } })
                    }
                    name = (if (separator >= 0) line.substring(separator + 1).trim() else "")
                        .ifBlank { attributes["tvg-name"].orEmpty().ifBlank { "Canal" } }
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
        // Full-screen layout keeps the channel lists independently scrollable.
        begin("TV ao Vivo", false, false)
        val channelSearch = field("Buscar canal pelo nome")
        // Keep search and its clear action in one compact row above the channel browser.
        root.removeView(channelSearch)
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(44)).apply {
            bottomMargin = dp(4)
        })
        searchRow.addView(channelSearch, LinearLayout.LayoutParams(0, -1, 1f))
        searchRow.addView(text("LIMPAR  ×", 12f, true, orange).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            contentDescription = "Limpar busca de canais"
            background = shape(panel, 8, orange)
            setOnFocusChangeListener { view, focused ->
                view.background = shape(if (focused) orange else panel, 8, orange)
                setTextColor(if (focused) Color.BLACK else orange)
            }
            setOnClickListener {
                channelSearch.text.clear()
                channelSearch.clearFocus()
            }
        }, LinearLayout.LayoutParams(dp(88), -1).apply { leftMargin = dp(6) })
        val compact = resources.configuration.screenWidthDp < 480
        val screen = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        root.addView(screen, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), 0); background = shape(panel, 10) }
        val groupsList = ListView(this).apply {
            dividerHeight = dp(3)
            setBackgroundColor(bg)
            isFocusable = true
            choiceMode = ListView.CHOICE_MODE_SINGLE
            selector = shape(Color.rgb(87, 43, 19), 8, orange)
        }
        val channelsList = ListView(this).apply {
            dividerHeight = dp(3)
            setBackgroundColor(bg)
            isFocusable = true
            choiceMode = ListView.CHOICE_MODE_SINGLE
            selector = shape(Color.rgb(87, 43, 19), 8, orange)
        }
        val preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), 0) }
        // Explicit D-pad navigation prevents TV remotes from jumping between columns.
        groupsList.id = View.generateViewId()
        channelsList.id = View.generateViewId()
        groupsList.nextFocusRightId = channelsList.id
        channelsList.nextFocusLeftId = groupsList.id
        if (compact) {
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val homeLink = text("♛ IVEPLAY   •   INÍCIO", 17f, true, gold).apply {
                isFocusable = true
                setOnClickListener { home() }
            }
            root.addView(homeLink, LinearLayout.LayoutParams(-1, dp(44)))
            // Split the remaining height proportionally so both channel lists and
            // video preview remain usable on short landscape phone screens.
            screen.addView(top, LinearLayout.LayoutParams(-1, 0, 1.15f))
            top.addView(groupsList, LinearLayout.LayoutParams(0, -1, 1.1f))
            top.addView(channelsList, LinearLayout.LayoutParams(0, -1, 1.9f))
            screen.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            screen.addView(ScrollView(this).apply { addView(nav) }, LinearLayout.LayoutParams(0, -1, 0.85f))
            screen.addView(groupsList, LinearLayout.LayoutParams(0, -1, 1.5f))
            screen.addView(channelsList, LinearLayout.LayoutParams(0, -1, 2.1f))
            screen.addView(preview, LinearLayout.LayoutParams(0, -1, 4.1f))
        }
        // Column headings remain visible as users scroll through categories and channels.
        if (!compact) {
            groupsList.addHeaderView(text("CATEGORIAS", 12f, true, orange).apply {
                setPadding(dp(8), dp(8), dp(4), dp(8))
            }, null, false)
            channelsList.addHeaderView(text("CANAIS", 12f, true, orange).apply {
                setPadding(dp(8), dp(8), dp(4), dp(8))
            }, null, false)
        }
        nav.addView(text("♛ IVEPLAY", 17f, true, gold))
        listOf("Início", "TV ao Vivo", "Filmes", "Séries", "Esportes", "Infantil", "Favoritos", "Histórico", "Configurações").forEach { page ->
            nav.addView(text(page, 12f, page == "TV ao Vivo", if (page == "TV ao Vivo") orange else Color.WHITE).apply {
                setPadding(dp(6), dp(10), 0, dp(10))
                isFocusable = true
                isClickable = true
                background = shape(if (page == "TV ao Vivo") Color.rgb(63, 33, 18) else panel, 8)
                setOnFocusChangeListener { view, focused ->
                    view.background = shape(
                        if (focused) orange else if (page == "TV ao Vivo") Color.rgb(63, 33, 18) else panel,
                        8
                    )
                    setTextColor(if (focused) Color.BLACK else if (page == "TV ao Vivo") orange else Color.WHITE)
                }
                setOnClickListener { navigate(page) }
            })
        }
        fun cleanChannelName(name: String): String =
            name.trim().replace(Regex("""^\\d+[\\s._:|\\-]*"""), "")
                .trim().lowercase(java.util.Locale.ROOT)
                .replace(Regex("""[\\s._:|\\-]+"""), " ")
                .trim()
        fun featuredRank(name: String): Int {
            val clean = cleanChannelName(name)
            // Match exact broadcaster names and common stream-quality suffixes,
            // without accidentally promoting unrelated channels (e.g. GloboNews).
            val broadcaster = clean.replace(Regex("""[!]+"""), "").trim()
            val names = listOf("globo", "record", "sbt", "band", "rede tv", "redetv")
            val suffixes = setOf("", "hd", "fhd", "full hd", "sd", "4k", "uhd", "h265", "hevc", "1080p", "720p")
            val rank = names.indexOfFirst { namePart ->
                broadcaster == namePart || (
                    broadcaster.startsWith("$namePart ") &&
                    broadcaster.removePrefix("$namePart ").trim() in suffixes
                )
            }
            return when (rank) {
                0 -> 0
                1 -> 1
                2 -> 2
                3 -> 3
                4, 5 -> 4
                else -> Int.MAX_VALUE
            }
        }
        val channels = items.filter { it.kind == "TV ao Vivo" }.sortedWith(
            compareBy<MediaEntry> { featuredRank(it.name) }
                .thenBy { cleanChannelName(it.name) }
                .thenBy { it.name.lowercase(java.util.Locale.ROOT) }
        )
                val groups = channels.map { it.group.trim().ifBlank { "Outros" } }
            .distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val groupCounts = channels.groupingBy { it.group.trim().ifBlank { "Outros" }.lowercase() }.eachCount()
        var selectedGroup: String? = null
        var selected: MediaEntry? = null
        val playerView = PlayerView(this).apply { useController = true; setBackgroundColor(Color.BLACK) }
        val playerTitle = text("Selecione um canal", 15f, true, orange)
        val playbackStatus = text("Escolha um canal para começar", 11f, false, muted)
        val channelCount = text("", 11f, false, muted)
        preview.addView(text("PRÉ-VISUALIZAÇÃO", 14f, true, orange))
        preview.addView(channelCount)
        preview.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6); bottomMargin = dp(6) })
        preview.addView(playerTitle)
        preview.addView(playbackStatus)
        val fullscreenButton = text("TELA CHEIA  ▶", 13f, true, orange).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = shape(panel, 8, orange)
            isFocusable = true
            isClickable = true
            contentDescription = "Abrir canal selecionado em tela cheia"
            setOnFocusChangeListener { view, focused ->
                view.background = shape(if (focused) orange else panel, 8, orange)
                setTextColor(if (focused) Color.BLACK else orange)
            }
            setOnClickListener {
                selected?.let { play(it) } ?: toast("Selecione um canal primeiro")
            }
        }
        fullscreenButton.id = View.generateViewId()
        channelsList.nextFocusRightId = fullscreenButton.id
        fullscreenButton.nextFocusLeftId = channelsList.id
        preview.addView(fullscreenButton)
        fun showPreview(entry: MediaEntry) {
            selected = entry
            playerTitle.text = entry.name
            playbackStatus.text = "Conectando ao canal..."
            player?.release()
            player = null
            playerView.player = null
            if (entry.url.isBlank()) {
                playbackStatus.text = "Canal sem endereço de reprodução"
                toast("Canal sem endereço de reprodução")
                return
            }
            try {
                player = ExoPlayer.Builder(this).build().also { instance ->
                    playerView.player = instance
                    attachPlaybackErrors(instance)
                    instance.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (player !== instance) return
                            playbackStatus.text = when (state) {
                                Player.STATE_BUFFERING -> "Carregando transmissão..."
                                Player.STATE_READY -> "Transmissão pronta"
                                Player.STATE_ENDED -> "Transmissão encerrada"
                                else -> "Aguardando transmissão..."
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            if (player === instance) playbackStatus.text =
                                "Não foi possível reproduzir este canal"
                        }
                    })
                    instance.setMediaItem(mediaItem(entry.url))
                    instance.prepare()
                    instance.playWhenReady = true
                }
            } catch (_: Exception) {
                playbackStatus.text = "Falha ao abrir a pré-visualização"
                toast("Não foi possível abrir a pré-visualização")
            }
        }
        fun refreshChannels() {
            val query = channelSearch.text.toString().trim()
            val matches = channels.filter {
                (selectedGroup == null || it.group.trim().ifBlank { "Outros" }
                    .equals(selectedGroup, ignoreCase = true)) &&
                    (query.isBlank() || it.name.contains(query, ignoreCase = true))
            }
            channelCount.text = "${matches.size} de ${channels.size} canais"
            val labels = if (matches.isEmpty()) {
                listOf(if (channels.isEmpty()) "Nenhum canal carregado" else "Nenhum canal encontrado")
            } else {
                matches.map { "▶  " + it.name }
            }
            channelsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
            // Preserve the selected channel when searching or switching categories.
            // Do not leave a placeholder row selected when there are no matches.
            val selectedIndex = matches.indexOfFirst { it.url == selected?.url && it.name == selected?.name }
            if (selectedIndex >= 0) {
                channelsList.setItemChecked(selectedIndex + if (compact) 0 else 1, true)
            } else {
                channelsList.clearChoices()
            }
            channelsList.setOnItemClickListener { _, _, position, _ ->
                val index = if (compact) position else position - 1
                matches.getOrNull(index)?.let { entry ->
                    channelsList.setItemChecked(position, true)
                    showPreview(entry)
                }
            }
            channelsList.setSelection(if (selectedIndex >= 0) {
                selectedIndex + if (compact) 0 else 1
            } else if (compact) 0 else 1)
        }
        val choices = listOf("Todos os Canais" to null) + groups.map { it to it }
        groupsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, choices.map { (name, group) ->
            if (group == null) "$name (${channels.size})" else "$name (${groupCounts[group.lowercase()] ?: 0})"
        })
        groupsList.setOnItemClickListener { _, _, position, _ ->
            val index = if (compact) position else position - 1
            val choice = choices.getOrNull(index) ?: return@setOnItemClickListener
            selectedGroup = choice.second
            groupsList.setItemChecked(position, true)
            refreshChannels()
        }
        // Start with "Todos os Canais" highlighted; TV remotes can navigate lists with DPAD.
        groupsList.setItemChecked(if (compact) 0 else 1, true)
        channelSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshChannels()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        try { imageLoader.execute {
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
        } } catch (_: java.util.concurrent.RejectedExecutionException) { /* Activity is closing. */ }
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
        // Remember the originating page so Back returns to the sports channel list.
        val origin = currentPage
        begin(entry.name, menu = false, scrollable = false)
        playbackOrigin = origin
        root.setPadding(dp(6), dp(4), dp(6), dp(6))
        playingKind = entry.kind
        val view = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isFocusable = true
            contentDescription = "Reprodutor de ${entry.name}"
        }
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
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
        val playbackActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(playbackActions, LinearLayout.LayoutParams(-1, dp(48)))
        val backAction = text("‹  VOLTAR", 13f, true, orange).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            contentDescription = "Voltar para ${entry.kind}"
            background = shape(panel, 8, orange)
            setOnFocusChangeListener { focusedView, focused ->
                focusedView.background = shape(if (focused) orange else panel, 8, orange)
                setTextColor(if (focused) Color.BLACK else orange)
            }
            setOnClickListener {
                if (playbackOrigin == "Canais esportivos") sportsChannelsPage()
                else if (entry.kind == "TV ao Vivo") liveCatalog() else catalog(entry.kind)
            }
        }
        playbackActions.addView(backAction, LinearLayout.LayoutParams(dp(115), dp(42)))
        playbackActions.addView(text(entry.name, 14f, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(10), 0)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        val homeAction = text("INÍCIO", 13f, true, gold).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            contentDescription = "Voltar à tela inicial"
            background = shape(panel, 8, gold)
            setOnFocusChangeListener { focusedView, focused ->
                focusedView.background = shape(if (focused) gold else panel, 8, gold)
                setTextColor(if (focused) Color.BLACK else gold)
            }
            setOnClickListener { home() }
        }
        playbackActions.addView(homeAction, LinearLayout.LayoutParams(dp(85), dp(42)))
        backAction.id = View.generateViewId()
        homeAction.id = View.generateViewId()
        backAction.nextFocusRightId = homeAction.id
        homeAction.nextFocusLeftId = backAction.id
    }

    override fun onStart() {
        super.onStart()
        if (resumePlaybackOnStart) {
            player?.play()
            resumePlaybackOnStart = false
        }
    }

    override fun onStop() {
        // Pause background streams; resume only if they were playing beforehand.
        resumePlaybackOnStart = player?.isPlaying == true
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        sportsRefreshTask?.let { sportsRefreshHandler.removeCallbacks(it) }
        sportsRefreshTask = null
        player?.release(); player = null
        imageLoader.shutdownNow()
        super.onDestroy()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            currentPage == "TV ao Vivo" -> home()
            player != null && currentPage != "Início" -> liveCatalogIfChannelOrHome()
            currentPage != "Início" -> home()
            else -> super.onBackPressed()
        }
    }

    private fun liveCatalogIfChannelOrHome() {
        if (playbackOrigin == "Canais esportivos") sportsChannelsPage()
        else if (playingKind == "TV ao Vivo") liveCatalog() else home()
    }
}
