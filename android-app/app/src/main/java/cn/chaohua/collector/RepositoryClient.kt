package cn.chaohua.collector

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Base64

class RepositoryClient(private val context:Context){
    private val client=OkHttpClient();private val api="https://api.github.com/repos/aly614030/chaohua-daily/contents/data/history.json"
    private fun token():String{val key=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();return EncryptedSharedPreferences.create(context,"secure",key,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).getString("token","").orEmpty()}
    suspend fun fetchTopics():List<Topic> = withContext(Dispatchers.IO){runCatching{val req=Request.Builder().url("https://raw.githubusercontent.com/aly614030/chaohua-daily/main/config/topics.json").build();val json=client.newCall(req).execute().use{JSONObject(it.body!!.string())}.getJSONArray("topics");buildList{for(i in 0 until json.length()){val item=json.getJSONObject(i);if(item.optBoolean("enabled",true))add(Topic(item.getString("name"),item.getString("id")))}}}.getOrDefault(emptyList())}
    suspend fun upload(rows:List<CaptureRow>)=withContext(Dispatchers.IO){
        val t=token();if(t.isBlank())return@withContext
        val get=Request.Builder().url(api).header("Authorization","Bearer $t").header("Accept","application/vnd.github+json").build();val current=client.newCall(get).execute().use{JSONObject(it.body!!.string())};val sha=current.getString("sha");val decoded=String(Base64.getMimeDecoder().decode(current.getString("content")));val history=JSONObject(decoded);val now=ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));val snapshot=JSONObject().put("date",now.toLocalDate().toString()).put("collectedAt",now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).put("source","android-accessibility").put("rows",JSONArray().apply{rows.forEach{put(it.json())}});val old=history.optJSONArray("snapshots")?:JSONArray();val next=JSONArray().put(snapshot);for(i in 0 until old.length())if(old.getJSONObject(i).optString("date")!=now.toLocalDate().toString())next.put(old.getJSONObject(i));history.put("snapshots",next)
        val body=JSONObject().put("message","data: update from Android collector").put("content",Base64.getEncoder().encodeToString((history.toString(2)+"\n").toByteArray())).put("sha",sha).put("branch","main").toString().toRequestBody("application/json".toMediaType());val put=Request.Builder().url(api).put(body).header("Authorization","Bearer $t").header("Accept","application/vnd.github+json").build();client.newCall(put).execute().close()
    }
}
