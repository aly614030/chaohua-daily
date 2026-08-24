package cn.chaohua.collector

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.time.*

class MainActivity : AppCompatActivity() {
    private val securePrefs by lazy { val key=MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();EncryptedSharedPreferences.create(this,"secure",key,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM) }
    private val configPrefs by lazy { getSharedPreferences("config",Context.MODE_PRIVATE) }
    private val checks=linkedMapOf<Topic,CheckBox>()
    private lateinit var resultView:TextView
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,54,36,40)}
        val title=TextView(this).apply{text="超话采集助手";textSize=28f;setTextColor(0xff216e39.toInt())}
        val note=TextView(this).apply{text="版本 0.3.0｜全线测试版\n请保持手机解锁、微博已登录。八个超话默认全部勾选，完成后自动返回并显示排名。";textSize=16f;setPadding(0,18,0,18)}
        val token=EditText(this).apply{hint="GitHub Fine-grained Token";setText(securePrefs.getString("token",""));inputType=129}
        val save=Button(this).apply{text="保存 Token（系统加密）";setOnClickListener{securePrefs.edit().putString("token",token.text.toString().trim()).apply();toast("Token 已保存")}}
        val access=Button(this).apply{text="打开无障碍设置";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}}
        val chooseTitle=TextView(this).apply{text="选择本次要采集和排名的超话";textSize=19f;setTypeface(null,Typeface.BOLD);setPadding(0,18,0,8)}
        box.addView(title);box.addView(note);box.addView(token);box.addView(save);box.addView(access);box.addView(chooseTitle)
        val saved=configPrefs.getStringSet("selected",Topics.defaults.map{it.name}.toSet()).orEmpty()
        Topics.defaults.forEach{topic->val cb=CheckBox(this).apply{text=topic.name;textSize=17f;isChecked=saved.contains(topic.name)};checks[topic]=cb;box.addView(cb)}
        val all=Button(this).apply{text="全选／取消全选";setOnClickListener{val select=checks.values.any{!it.isChecked};checks.values.forEach{it.isChecked=select}}}
        val runSelected=Button(this).apply{text="开始采集所选超话并生成排名";setOnClickListener{val selected=checks.filterValues{it.isChecked}.keys.toList();if(selected.isEmpty()){toast("请至少勾选一个超话");return@setOnClickListener};configPrefs.edit().putStringSet("selected",selected.map{it.name}.toSet()).apply();CollectorAccessibilityService.requestRun(selected,true,true,false);toast("开始采集 ${selected.size} 个超话，请不要操作手机")}}
        val test=Button(this).apply{text="仅测试张真源";setOnClickListener{CollectorAccessibilityService.requestRun(listOf(Topics.defaults.first{it.name=="张真源"}),false,true,false);toast("开始单项测试")}}
        val schedule=Button(this).apply{text="启用每天 23:30 自动采集全部八个";setOnClickListener{scheduleDaily();toast("已设置每天 23:30")}}
        val web=Button(this).apply{text="查看公开网页排名";setOnClickListener{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://aly614030.github.io/chaohua-daily/")))}}
        resultView=TextView(this).apply{textSize=16f;setTextColor(0xff173b24.toInt());setPadding(18,24,18,36);setBackgroundColor(0xffedf8ef.toInt())}
        listOf(all,runSelected,test,schedule,web,resultView).forEach{box.addView(it,LinearLayout.LayoutParams(-1,-2).apply{topMargin=12})}
        setContentView(ScrollView(this).apply{addView(box)});showIncomingResult(intent)
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);showIncomingResult(intent)}
    private fun showIncomingResult(intent:Intent){val json=intent.getStringExtra("result_json")?:return;val failed=intent.getStringExtra("failed").orEmpty();val uploaded=intent.getBooleanExtra("uploaded",false);resultView.text=formatRankings(json)+if(failed.isNotBlank())"\n\n未成功：$failed" else if(uploaded)"\n\n数据已同步到 GitHub，网页稍后自动更新。" else "\n\n本次为测试，未写入 GitHub。"}
    private fun formatRankings(json:String):String{
        val rows=JSONArray(json);if(rows.length()==0)return "本次没有成功读取到数据。"
        val metrics=listOf("superLike" to "超 LIKE","posts" to "今日发帖量","interactions" to "今日互动值","checkins" to "今日签到数","reads" to "阅读量");val out=StringBuilder("采集完成：${rows.length()} 个超话\n")
        metrics.forEach{(key,name)->out.append("\n【$name 排名】\n");val items=(0 until rows.length()).map{rows.getJSONObject(it)}.sortedByDescending{number(it.optString(key))};items.forEachIndexed{i,row->out.append("${i+1}. ${row.optString("topic")}  ${row.optString(key)}\n")}}
        return out.toString().trim()
    }
    private fun number(raw:String):Double{val n=Regex("[0-9.]+").find(raw.replace(",",""))?.value?.toDoubleOrNull()?:0.0;return when{raw.contains("亿")->n*100000000;raw.contains("万")->n*10000;else->n}}
    private fun scheduleDaily(){val alarm=getSystemService(AlarmManager::class.java);val pi=PendingIntent.getBroadcast(this,2330,Intent(this,DailyReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);var next=ZonedDateTime.now().withHour(23).withMinute(30).withSecond(0).withNano(0);if(!next.isAfter(ZonedDateTime.now()))next=next.plusDays(1);runCatching{alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pi)}.onFailure{toast("请在系统中允许精确闹钟权限")}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}

class DailyReceiver:android.content.BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){CollectorAccessibilityService.requestRun(Topics.defaults,true,false,true);val alarm=c.getSystemService(AlarmManager::class.java);val pi=PendingIntent.getBroadcast(c,2330,Intent(c,DailyReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);runCatching{alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+24*60*60*1000,pi)}}}
data class Topic(val name:String,val id:String)
object Topics{val defaults=listOf(Topic("时代少年团","10080892b1f367dec8ee89a39109d0b5fe9c0d"),Topic("马嘉祺","100808d084599a8d651890c335344604dc6441"),Topic("丁程鑫","10080893fb74e5ad334664e9ed75257a99cb12"),Topic("宋亚轩","100808f2892dd79cfb5ef048fd0f0926d643b0"),Topic("刘耀文","100808b8be41fe83ba990678f3e11a63fbe041"),Topic("张真源","1008081631871d44bb53c07db75a5ef288af6a"),Topic("严浩翔","100808b160efbd4e3821e749d578259ab59157"),Topic("贺峻霖","100808da1f664a39066684045432530979265a"))}
