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
                        else { items.clear(); items.addAll(found); toast("${items.size} itens carregados"); onSuccess() }
                    }
                } catch (e: Exception) { runOnUiThread { toast("Erro ao carregar: ${e.localizedMessage}") } }
            }
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
