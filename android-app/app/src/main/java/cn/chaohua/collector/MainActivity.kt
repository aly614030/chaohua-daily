package cn.chaohua.collector

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.*

class MainActivity : AppCompatActivity() {
    private val prefs by lazy {
        val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(this,"secure",key,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,60,40,40)}
        val title=TextView(this).apply{text="超话采集助手";textSize=28f;setTextColor(0xff216e39.toInt())}
        val note=TextView(this).apply{text="版本 0.2.4\n每天 23:30 自动读取微博页面。运行时请保持手机解锁、微博已登录。\n\n首次设置：开启无障碍权限，填写 GitHub Token，然后先测试张真源超话。";textSize=16f;setPadding(0,24,0,24)}
        val token=EditText(this).apply{hint="GitHub Fine-grained Token";setText(prefs.getString("token",""));inputType=129}
        val save=Button(this).apply{text="保存 Token（系统加密）";setOnClickListener{prefs.edit().putString("token",token.text.toString().trim()).apply();toast("已保存")}}
        val access=Button(this).apply{text="打开无障碍设置";setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}}
        val test=Button(this).apply{text="测试采集张真源";setOnClickListener{CollectorAccessibilityService.requestRun(listOf(Topic("张真源","1008081631871d44bb53c07db75a5ef288af6a")),true);toast("已发出测试指令")}}
        val schedule=Button(this).apply{text="启用每天 23:30";setOnClickListener{scheduleDaily();toast("已设置每天 23:30")}}
        listOf(title,note,token,save,access,test,schedule).forEach{box.addView(it,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=18})};setContentView(box)
    }
    private fun scheduleDaily(){
        val alarm=getSystemService(AlarmManager::class.java);val pi=PendingIntent.getBroadcast(this,2330,Intent(this,DailyReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        var next=ZonedDateTime.now().withHour(23).withMinute(30).withSecond(0).withNano(0);if(!next.isAfter(ZonedDateTime.now()))next=next.plusDays(1)
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pi)
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}

class DailyReceiver:android.content.BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){CollectorAccessibilityService.requestRun(Topics.defaults,false);val alarm=c.getSystemService(AlarmManager::class.java);val pi=PendingIntent.getBroadcast(c,2330,Intent(c,DailyReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+24*60*60*1000,pi)}}

data class Topic(val name:String,val id:String)
object Topics{val defaults=listOf(Topic("时代少年团","10080892b1f367dec8ee89a39109d0b5fe9c0d"),Topic("马嘉祺","100808d084599a8d651890c335344604dc6441"),Topic("丁程鑫","10080893fb74e5ad334664e9ed75257a99cb12"),Topic("宋亚轩","100808f2892dd79cfb5ef048fd0f0926d643b0"),Topic("刘耀文","100808b8be41fe83ba990678f3e11a63fbe041"),Topic("张真源","1008081631871d44bb53c07db75a5ef288af6a"),Topic("严浩翔","100808b160efbd4e3821e749d578259ab59157"),Topic("贺峻霖","100808da1f664a39066684045432530979265a"))}
