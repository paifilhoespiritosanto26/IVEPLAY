package com.ive.play

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val bg = Color.rgb(9, 13, 25)
    private val accent = Color.rgb(0, 190, 245)
    private lateinit var root: LinearLayout
    private var player: ExoPlayer? = null
    private val channels = mutableListOf<Channel>()
    data class Channel(val name: String, val url: String, val group: String)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); home() }
    private fun init(title: String) {
        player?.release(); player = null
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(24, 18, 24, 18) }
        setContentView(ScrollView(this).apply {
    isFillViewport = true
    addView(root)
})

        val heading = TextView(this).apply { text = title; textSize = 26f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 10, 0, 22) }
        root.addView(heading)
    }
    private fun label(s: String) = TextView(this).apply { text = s; textSize = 16f; setTextColor(Color.WHITE); setPadding(4, 12, 4, 8) }
    private fun field(hint: String): EditText = EditText(this).apply { this.hint = hint; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent)); root.addView(this) }
    private fun button(s: String, action: () -> Unit) { val b=Button(this).apply { text=s; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(17, 87, 139))); setOnClickListener { action() } }; root.addView(b) }
    private fun message(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private fun home() {
        init("IVE PLAY")
        val logo=ImageView(this).apply { setImageResource(R.drawable.ive_logo); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1, 260) }
        root.addView(logo)
        root.addView(label("IMAGEM • VÍDEO • ENTRETENIMENTO"))
        root.addView(label("TV POR APLICATIVO — utilize apenas listas e servidores autorizados."))
        button("ENTRAR COM LISTA M3U") { m3uLogin() }
        button("ENTRAR COM XTREAM CODES") { xtreamLogin() }
        if(channels.isNotEmpty()) button("VER CANAIS CARREGADOS") { catalog() }
    }
    private fun m3uLogin() {
        init("Adicionar lista M3U")
        val url=field("URL HTTPS da lista M3U")
        button("CARREGAR CANAIS") {
            val link=url.text.toString().trim()
            if(!link.startsWith("https://") && !link.startsWith("http://")) { message("Informe uma URL válida"); return@button }
            fetch(link) { body ->
                val result=parseM3u(body)
                if(result.isEmpty()) message("Nenhum canal encontrado na lista") else { channels.clear();channels.addAll(result);catalog() }
            }
        }
        button("VOLTAR") { home() }
    }
    private fun xtreamLogin() {
        init("Xtream Codes")
        val server=field("Servidor: https://exemplo.com")
        val user=field("Usuário")
        val pass=field("Senha")
        button("CONECTAR") {
            val host=server.text.toString().trim().trimEnd('/')
            val u=user.text.toString().trim();val pw=pass.text.toString().trim()
            if(!host.startsWith("https://") && !host.startsWith("http://") || u.isBlank() || pw.isBlank()) { message("Preencha servidor, usuário e senha");return@button }
            val encU=URLEncoder.encode(u,"UTF-8");val encP=URLEncoder.encode(pw,"UTF-8")
            val api="$host/player_api.php?username=$encU&password=$encP&action=get_live_streams"
            fetch(api) { body ->
                try {
                    val arr=JSONArray(body); val found=mutableListOf<Channel>()
                    for(i in 0 until arr.length()) { val x=arr.getJSONObject(i);val id=x.optString("stream_id");if(id.isNotBlank()) found.add(Channel(x.optString("name","Canal"),"$host/live/$encU/$encP/$id.ts",x.optString("category_id","TV ao vivo"))) }
                    if(found.isEmpty()) message("Nenhum canal disponível") else { channels.clear();channels.addAll(found);catalog() }
                } catch(e:Exception) { message("Resposta Xtream inválida ou acesso não autorizado") }
            }
        }
        button("VOLTAR") { home() }
    }
    private fun fetch(link:String, done:(String)->Unit) {
        message("Conectando...")
        thread {
            try {
                val connection=(URL(link).openConnection() as HttpURLConnection).apply { connectTimeout=12000;readTimeout=20000;instanceFollowRedirects=true;setRequestProperty("User-Agent","IVEPlay/0.1") }
                val data=connection.inputStream.bufferedReader().use { it.readText() };connection.disconnect()
                runOnUiThread { done(data) }
            } catch(e:Exception) { runOnUiThread { message("Falha na conexão: ${e.localizedMessage ?: "verifique o servidor"}") } }
        }
    }
    private fun parseM3u(content:String):List<Channel> {
        val result=mutableListOf<Channel>();var name="";var group="TV ao vivo"
        for(line in content.lines()) { val s=line.trim()
            if(s.startsWith("#EXTINF",true)) { name=s.substringAfterLast(',',"Canal").trim().ifBlank { "Canal" };group=Regex("group-title=\"([^\"]*)\"").find(s)?.groupValues?.get(1)?.ifBlank { "TV ao vivo" } ?: "TV ao vivo" }
            else if(s.startsWith("http://") || s.startsWith("https://")) { result.add(Channel(name.ifBlank { "Canal" },s,group));name="" }
        }
        return result
    }
    private fun catalog() {
        init("IVE PLAY • Canais (${channels.size})")
        val search=field("Buscar canal ou categoria")
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };root.addView(list)
        fun update(query:String) {
            list.removeAllViews()
            channels.filter { it.name.contains(query,true)||it.group.contains(query,true) }.take(200).forEach { ch ->
                val b=Button(this).apply { text="${ch.name}  •  ${ch.group}";isAllCaps=false;setTextColor(Color.WHITE);setOnClickListener { play(ch) } }
                list.addView(b)
            }
        }
        search.addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){update(s.toString())};override fun afterTextChanged(s:android.text.Editable?){} })
        update("")
        button("INÍCIO") { home() }
    }
    private fun play(ch:Channel) {
        init(ch.name)
        val view=PlayerView(this).apply { layoutParams=LinearLayout.LayoutParams(-1, 500);useController=true }
        root.addView(view)
        try { player=ExoPlayer.Builder(this).build().also { view.player=it;it.setMediaItem(MediaItem.fromUri(ch.url));it.prepare();it.playWhenReady=true } }
        catch(e:Exception) { message("Não foi possível iniciar a reprodução") }
        button("VOLTAR AOS CANAIS") { catalog() }
    }
    override fun onDestroy() { player?.release();super.onDestroy() }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { if(player!=null) catalog() else home() }
}
