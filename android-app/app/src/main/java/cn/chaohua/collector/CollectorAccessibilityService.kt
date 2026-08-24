package cn.chaohua.collector

import android.accessibilityservice.*
import android.content.Intent
import android.graphics.Rect
import android.graphics.Path
import android.net.Uri
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CollectorAccessibilityService:AccessibilityService(){
    companion object{
        private var instance:CollectorAccessibilityService?=null
        private var queued:Pair<List<Topic>,Boolean>?=null
        fun requestRun(topics:List<Topic>,test:Boolean){instance?.run(topics,test)?:run{queued=topics to test}}
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onServiceConnected(){instance=this;queued?.also{run(it.first,it.second);queued=null}}
    override fun onDestroy(){instance=null;scope.cancel();super.onDestroy()}
    override fun onAccessibilityEvent(e:AccessibilityEvent?){}
    override fun onInterrupt(){}
    private fun run(topics:List<Topic>,test:Boolean)=scope.launch{
        val activeTopics=if(test)topics else RepositoryClient(applicationContext).fetchTopics().ifEmpty{topics}
        val results=mutableListOf<CaptureRow>()
        for(topic in activeTopics){capture(topic)?.let(results::add)}
        if(results.size==activeTopics.size&&!test)RepositoryClient(applicationContext).upload(results)
    }
    private suspend fun capture(topic:Topic):CaptureRow?{
        val intent=Intent(Intent.ACTION_VIEW,Uri.parse("sinaweibo://searchall?q=${Uri.encode(topic.name+"超话")}" )).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK;setPackage("com.sina.weibo")}
        runCatching{startActivity(intent)}.getOrElse{return null};delay(5000)
        var entered=clickAnyText(listOf(topic.name+"超话",topic.name),8)
        if(!entered){clickAnyText(listOf("超话"),3);delay(1800);entered=clickAnyText(listOf(topic.name+"超话",topic.name,"进入超话"),8)}
        if(!entered)return null
        delay(6000)
        val first=allText();val checkin=match(first,Regex("今日签到\\s*([0-9.]+\\s*[万亿]?人?)"))?:return null
        gesture(.78f,.18f,.30f,.18f);delay(1800)
        val second=allText();val superLike=match(second,Regex("超\\s*LIKE\\s*([0-9.]+\\s*[万亿]?人?)",RegexOption.IGNORE_CASE))?:return null
        (findExact(topic.name)?:findContains(topic.name))?.performAction(AccessibilityNodeInfo.ACTION_CLICK);delay(4500)
        gesture(.50f,.78f,.50f,.35f);delay(1800)
        val detail=allText();val posts=match(detail,Regex("今日新帖\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val interactions=match(detail,Regex("今日新增互动\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val reads=match(detail,Regex("([0-9.]+\\s*[万亿]?)\\s*阅读"))?:return null
        performGlobalAction(GLOBAL_ACTION_BACK);delay(1200)
        return CaptureRow(topic.name,superLike,posts,interactions,checkin,reads)
    }
    private fun allText():String{val out=mutableListOf<String>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;n.text?.toString()?.takeIf{it.isNotBlank()}?.let(out::add);n.contentDescription?.toString()?.takeIf{it.isNotBlank()}?.let(out::add);for(i in 0 until n.childCount)walk(n.getChild(i))};walk(rootInActiveWindow);return out.joinToString("\n")}
    private fun findExact(s:String)=rootInActiveWindow?.findAccessibilityNodeInfosByText(s)?.firstOrNull{it.text?.toString()==s}
    private fun findContains(s:String)=rootInActiveWindow?.findAccessibilityNodeInfosByText(s)?.firstOrNull()
    private suspend fun clickAnyText(labels:List<String>,tries:Int):Boolean{
        repeat(tries){
            for(label in labels){
                val nodes=rootInActiveWindow?.findAccessibilityNodeInfosByText(label).orEmpty()
                for(node in nodes){
                    var target:AccessibilityNodeInfo?=node
                    while(target!=null&&!target.isClickable)target=target.parent
                    if(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)return true
                    if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true
                    val r=Rect();node.getBoundsInScreen(r)
                    if(!r.isEmpty){gesturePixels(r.centerX().toFloat(),r.centerY().toFloat());return true}
                }
            }
            delay(900)
        }
        return false
    }
    private fun match(s:String,r:Regex)=r.find(s)?.groupValues?.get(1)?.replace(" ","")
    private suspend fun gesture(x1:Float,y1:Float,x2:Float,y2:Float)=suspendCancellableCoroutine<Unit>{c->val dm=resources.displayMetrics;val p=Path().apply{moveTo(x1*dm.widthPixels,y1*dm.heightPixels);lineTo(x2*dm.widthPixels,y2*dm.heightPixels)};dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,500)).build(),object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){c.resume(Unit){}}},null)}
    private suspend fun gesturePixels(x:Float,y:Float)=suspendCancellableCoroutine<Unit>{c->val p=Path().apply{moveTo(x,y);lineTo(x+1,y+1)};dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,120)).build(),object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){c.resume(Unit){}}},null)}
}

data class CaptureRow(val topic:String,val superLike:String,val posts:String,val interactions:String,val checkins:String,val reads:String){fun json()=org.json.JSONObject().put("topic",topic).put("superLike",superLike).put("posts",posts).put("interactions",interactions).put("checkins",checkins).put("reads",reads)}
