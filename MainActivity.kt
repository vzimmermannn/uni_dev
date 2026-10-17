package br.com.eventoperto

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
 private val prefs by lazy { getSharedPreferences("eventoperto", MODE_PRIVATE) }
 private val client by lazy { Nearby.getConnectionsClient(this) }
 private val device by lazy { prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() } }
 private var event = ""
 private var running = false
 private val peers = mutableSetOf<String>()
 private val pending = mutableSetOf<String>()
 private val notices = linkedMapOf<String, JSONObject>()
 private lateinit var feed: LinearLayout
 private lateinit var status: TextView
 private lateinit var eventInput: EditText
 private lateinit var toggle: Button
 private val handler = Handler(Looper.getMainLooper())
 private val refresh = object : Runnable { override fun run() { render(); handler.postDelayed(this, 30_000) } }
 private fun now() = System.currentTimeMillis()
 private fun live(o: JSONObject) = o.optLong("expires") > now()
 private fun message(s: String) { if (::status.isInitialized) status.text = s }
 private fun field(hint: String) = EditText(this).apply { this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); textSize = 16f }
 private fun label(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setTextColor(Color.WHITE); setPadding(0,12,0,12) }
 private fun button(s: String, action: () -> Unit) = Button(this).apply { text = s; setOnClickListener { action() } }
 override fun onCreate(state: Bundle?) {
  super.onCreate(state)
  window.statusBarColor = Color.rgb(13,22,37)
  val root = LinearLayout(this).apply { orientation = 1; fitsSystemWindows = true; setPadding(32,30,32,12); setBackgroundColor(Color.rgb(13,22,37)) }
  root.addView(label("EVENTO PERTO",28f).apply { setTypeface(null,Typeface.BOLD) })
  root.addView(label("A internet caiu. A informação continua circulando.",17f))
  root.addView(label("PROTÓTIPO • mensagens não verificadas",13f).apply { setTextColor(Color.rgb(255,199,100)) })
  eventInput = field("Código do evento (igual nos aparelhos)")
  eventInput.setText(prefs.getString("event", "DEMO-2026")); root.addView(eventInput)
  toggle = button("Ativar proximidade") { if(running) stop() else startWithPermissions() }; root.addView(toggle)
  status = label("Proximidade desligada • avisos continuam salvos",14f); root.addView(status)
  root.addView(button("Criar aviso de teste") { compose() })
  root.addView(label("Mural do evento",22f))
  val scroll = ScrollView(this)
  feed = LinearLayout(this).apply { orientation = 1 }
  scroll.addView(feed); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(label("Mantenha o app aberto para trocar avisos. Sem garantia de entrega.",12f))
  setContentView(root)
  event = eventInput.text.toString().trim().uppercase(Locale.ROOT)
  load(); handler.post(refresh)
 }
 private fun permissions(): Array<String> {
  val list = mutableListOf<String>()
  if(Build.VERSION.SDK_INT >= 31) list.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE))
  if(Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
  else list.add(Manifest.permission.ACCESS_FINE_LOCATION)
  return list.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
 }
 private fun startWithPermissions() {
  val code = eventInput.text.toString().trim().uppercase(Locale.ROOT)
  if(!code.matches(Regex("[A-Z0-9-]{3,32}"))) { message("Use um código com 3 a 32 letras, números ou hífen."); return }
  val needed = permissions()
  if(needed.isNotEmpty()) { requestPermissions(needed,10); return }
  if(event != code) { event = code; load() }
  prefs.edit().putString("event",event).apply()
  running = true; eventInput.isEnabled = false; toggle.text = "Desligar proximidade"
  val hash = MessageDigest.getInstance("SHA-256").digest(event.toByteArray()).joinToString("") { "%02x".format(it) }
  val service = "br.com.eventoperto.$hash"
  message("Procurando participantes • Bluetooth e Wi-Fi devem estar ligados")
  client.startAdvertising(device, service, lifecycle, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
   .addOnFailureListener { if(running) { stop(); message("Não foi possível anunciar: ${it.localizedMessage}") } }
  client.startDiscovery(service, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
   .addOnFailureListener { if(running) { stop(); message("Não foi possível procurar: ${it.localizedMessage}") } }
 }
 override fun onRequestPermissionsResult(code: Int, p: Array<out String>, results: IntArray) {
  super.onRequestPermissionsResult(code,p,results)
  if(code == 10 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startWithPermissions()
  else message("Permissões necessárias negadas. Você ainda pode ler os avisos salvos.")
 }
 private fun stop() {
  running = false; client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints(); peers.clear(); pending.clear()
  eventInput.isEnabled = true; toggle.text = "Ativar proximidade"; message("Proximidade desligada • avisos salvos")
 }
 private val discovery = object : EndpointDiscoveryCallback() {
  override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
   // Only one side initiates to avoid two simultaneous requests.
   if(running && device < info.endpointName && id !in peers && pending.add(id)) {
    client.requestConnection(device,id,lifecycle).addOnFailureListener { pending.remove(id); message("Conexão não iniciada. Desligue e reative para tentar novamente.") }
   }
  }
  override fun onEndpointLost(id: String) { pending.remove(id) }
 }
 private val lifecycle = object : ConnectionLifecycleCallback() {
  override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
   if(!running) { client.rejectConnection(id); return }
   AlertDialog.Builder(this@MainActivity).setTitle("Conectar participante?")
    .setMessage("Compare o código nos dois celulares: ${info.authenticationDigits}\nAceite somente se os códigos forem iguais. Isso confirma o par, não a autoria dos avisos.")
    .setPositiveButton("Códigos iguais") { _,_ -> if(running) client.acceptConnection(id,payloads).addOnFailureListener { message("Falha ao aceitar conexão") } }
    .setNegativeButton("Recusar") { _,_ -> client.rejectConnection(id); pending.remove(id) }
    .setCancelable(false).show()
  }
  override fun onConnectionResult(id: String, result: ConnectionResolution) {
   pending.remove(id)
   if(!running) return
   if(result.status.isSuccess) { peers.add(id); message("${peers.size} participante(s) conectado(s) • trocando avisos"); notices.values.filter { live(it) }.forEach { send(id,it) } }
   else message("Conexão recusada ou falhou. Reative a proximidade para tentar novamente.")
  }
  override fun onDisconnected(id: String) { peers.remove(id); pending.remove(id); if(running) message("${peers.size} participante(s) conectado(s) • buscando novos encontros") }
 }
 private val payloads = object : PayloadCallback() {
  override fun onPayloadReceived(from: String, payload: Payload) {
   val bytes = payload.asBytes() ?: return
   if(bytes.size > 4096) return
   try {
    val o = JSONObject(String(bytes,Charsets.UTF_8))
    if(!valid(o) || notices.containsKey(o.getString("id"))) return
    notices[o.getString("id")] = o; save(); render()
    peers.filter { it != from }.forEach { send(it,o) }
    message("Novo aviso recebido por proximidade • ${peers.size} conectado(s)")
   } catch (_: Exception) { message("Mensagem incompatível ignorada") }
  }
  override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
   if(update.status == PayloadTransferUpdate.Status.FAILURE) message("Um envio falhou. O aviso permanece salvo para o próximo encontro.")
  }
 }
 private fun valid(o: JSONObject): Boolean {
  val created = o.optLong("created"); val expires = o.optLong("expires")
  return o.optInt("v") == 1 && o.optString("event") == event &&
   runCatching { UUID.fromString(o.getString("id")) }.isSuccess &&
   o.optString("text").length in 1..280 && o.optString("sector").length in 1..40 &&
   created > 0 && created <= now()+300_000 && expires > now() && expires > created && expires-created <= 86_400_000
 }
 private fun send(id: String, o: JSONObject) {
  client.sendPayload(id,Payload.fromBytes(o.toString().toByteArray(Charsets.UTF_8)))
   .addOnFailureListener { message("Envio não concluído. Aviso salvo para um próximo encontro.") }
 }
 private fun load() {
  notices.clear()
  runCatching { val a=JSONArray(prefs.getString("notices:$event","[]")); for(i in 0 until a.length()) { val o=a.getJSONObject(i); notices[o.getString("id")]=o } }
  render()
 }
 private fun save() {
  val keep=notices.values.sortedByDescending { it.optLong("created") }.take(200)
  notices.clear(); keep.forEach { notices[it.getString("id")]=it }
  prefs.edit().putString("notices:$event",JSONArray(keep).toString()).apply()
 }
 private fun compose() {
  if(eventInput.text.toString().trim().uppercase(Locale.ROOT) != event) { message("Ative a proximidade para confirmar a troca de evento."); return }
  val form=LinearLayout(this).apply { orientation=1; setPadding(30,10,30,10) }
  val sector=EditText(this).apply { hint="Setor (ex.: Portão 3)"; setText("Geral") }
  val text=EditText(this).apply { hint="Aviso curto, até 280 caracteres"; filters=arrayOf(android.text.InputFilter.LengthFilter(280)) }
  sector.filters=arrayOf(android.text.InputFilter.LengthFilter(40))
  form.addView(sector);form.addView(text)
  val dialog=AlertDialog.Builder(this).setTitle("Aviso de teste • validade de 1 hora").setView(form)
   .setMessage("Autoria não verificada. Não use para orientar emergências reais.")
   .setPositiveButton("Publicar",null).setNegativeButton("Cancelar",null).create()
  dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
   if(text.text.isBlank() || sector.text.isBlank()) { text.error="Preencha o setor e o aviso"; return@setOnClickListener }
   val t=now(); val o=JSONObject().put("v",1).put("id",UUID.randomUUID().toString()).put("event",event)
    .put("created",t).put("expires",t+3_600_000).put("sector",sector.text.toString().trim()).put("text",text.text.toString().trim())
   notices[o.getString("id")]=o;save();render();peers.forEach { send(it,o) }
   message(if(peers.isEmpty()) "Aviso salvo • aguardando encontro com outro participante" else "Aviso salvo • envio iniciado aos participantes conectados")
   dialog.dismiss()
  } }; dialog.show()
 }
 private fun render() {
  if(!::feed.isInitialized) return
  feed.removeAllViews()
  if(notices.isEmpty()) feed.addView(label("Nenhum aviso recebido. Conecte-se a alguém do mesmo evento ou crie um aviso de teste."))
  notices.values.sortedByDescending { it.optLong("created") }.forEach { o ->
   val card=LinearLayout(this).apply { orientation=1;setPadding(24,10,24,16);setBackgroundColor(Color.rgb(25,40,59)) }
   card.addView(label(o.optString("sector"),18f).apply { setTypeface(null,Typeface.BOLD) })
   card.addView(label(o.optString("text"),18f))
   val fmt=SimpleDateFormat("HH:mm",Locale("pt","BR"))
   card.addView(label("${fmt.format(Date(o.optLong("created")))} • ${if(live(o)) "válido até "+fmt.format(Date(o.optLong("expires"))) else "VENCIDO"} • autoria não verificada",12f))
   val params=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=18 };feed.addView(card,params)
  }
 }
 override fun onDestroy() { handler.removeCallbacks(refresh); if(running) stop(); super.onDestroy() }
}
