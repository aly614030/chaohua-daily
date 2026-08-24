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
        val direct=Intent(Intent.ACTION_VIEW,Uri.parse("sinaweibo://pageinfo?containerid=${Uri.encode(topic.id)}")).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK;setPackage("com.sina.weibo")}
        runCatching{startActivity(direct)}.getOrElse{return null};delay(7000)
        if(!allText().contains("今日签到")){
            val search=Intent(Intent.ACTION_VIEW,Uri.parse("sinaweibo://searchall?q=${Uri.encode(topic.name+"超话")}" )).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK;setPackage("com.sina.weibo")}
            runCatching{startActivity(search)}.getOrElse{return null};delay(5000)
            var entered=clickAnyText(listOf(topic.name+"超话","#${topic.name}超话#","进入超话"),8)
            if(!entered){clickAnyText(listOf("超话"),3);delay(1800);entered=clickAnyText(listOf(topic.name+"超话","#${topic.name}超话#","进入超话"),8)}
            if(!entered)return null
            delay(6000)
        }
        var checkin:String?=null
        repeat(10){
            if(checkin==null){
                val first=allText()
                checkin=match(first,Regex("今日\\s*签到[^0-9]{0,12}([0-9.]+\\s*[万亿]?\\s*人?)"))
                    ?:match(first,Regex("签到[^0-9]{0,12}([0-9.]+\\s*[万亿]?\\s*人?)"))
                if(checkin==null)delay(700)
            }
        }
        var superLike:String?=null
        repeat(5){
            if(superLike==null){
                superLike=match(allText(),Regex("超\\s*LIKE\\s*([0-9.]+\\s*[万亿]?人?)",RegexOption.IGNORE_CASE))
                if(superLike==null){if(!scrollHeaderForward())gesture(.90f,.15f,.08f,.15f);delay(1400)}
            }
        }
        if(superLike==null)return null
        if(!clickTopicHeader(topic.name))return null
        var detailReady=false
        repeat(8){delay(900);val page=allText();if(page.contains("公开超话")||page.contains("钻超等级")||page.contains("今日新帖")||page.contains("今日新增互动")){detailReady=true;return@repeat}}
        if(!detailReady)return null
        var detail=allText()
        repeat(5){
            if(!detail.contains("今日新帖")||!detail.contains("今日新增互动")){gesture(.50f,.84f,.50f,.24f);delay(1400);detail=allText()}
        }
        val posts=match(detail,Regex("今日新帖\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val interactions=match(detail,Regex("今日新增互动\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val reads=match(detail,Regex("([0-9.]+\\s*[万亿]?)\\s*阅读"))?:return null
        performGlobalAction(GLOBAL_ACTION_BACK);delay(1200)
        if(checkin==null)return null
        return CaptureRow(topic.name,superLike!!,posts,interactions,checkin!!,reads)
    }
    private fun allText():String{val out=mutableListOf<String>();fun walk(n:AccessibilityNodeInfo?){if(n==null)return;n.text?.toString()?.takeIf{it.isNotBlank()}?.let(out::add);n.contentDescription?.toString()?.takeIf{it.isNotBlank()}?.let(out::add);for(i in 0 until n.childCount)walk(n.getChild(i))};walk(rootInActiveWindow);return out.joinToString("\n")}
    private fun findExact(s:String)=rootInActiveWindow?.findAccessibilityNodeInfosByText(s)?.firstOrNull{it.text?.toString()==s}
    private fun findContains(s:String)=rootInActiveWindow?.findAccessibilityNodeInfosByText(s)?.firstOrNull()
    private suspend fun clickAnyText(labels:List<String>,tries:Int):Boolean{
        repeat(tries){
            for(label in labels){
                val nodes=rootInActiveWindow?.findAccessibilityNodeInfosByText(label).orEmpty()
                for(node in nodes){
                    val shown=listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).map{it.trim()}
                    val exactEnough=shown.any{it==label||it=="#$label#"||it.startsWith("$label ")||it.startsWith("$label\n")}
                    if(!exactEnough)continue
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
    private suspend fun clickTopicHeader(name:String):Boolean{
        repeat(8){
            val nodes=rootInActiveWindow?.findAccessibilityNodeInfosByText(name).orEmpty()
            val limit=(resources.displayMetrics.heightPixels*.38f).toInt()
            for(node in nodes){
                val value=node.text?.toString()?.trim().orEmpty()
                val r=Rect();node.getBoundsInScreen(r)
                if(value!=name||r.isEmpty||r.centerY()>limit)continue
                var target:AccessibilityNodeInfo?=node
                while(target!=null&&!target.isClickable)target=target.parent
                if(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)return true
                if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true
                gesturePixels(r.centerX().toFloat(),r.centerY().toFloat());return true
            }
            delay(900)
        }
        return false
    }
    private fun match(s:String,r:Regex)=r.find(s)?.groupValues?.get(1)?.replace(" ","")
    private fun scrollHeaderForward():Boolean{
        val dm=resources.displayMetrics;val candidates=mutableListOf<Pair<AccessibilityNodeInfo,Rect>>()
        fun walk(n:AccessibilityNodeInfo?){if(n==null)return;val r=Rect();n.getBoundsInScreen(r);if(n.isScrollable&&r.width()>dm.widthPixels*.45f&&r.height()<dm.heightPixels*.18f&&r.centerY()<dm.heightPixels*.35f)candidates.add(n to r);for(i in 0 until n.childCount)walk(n.getChild(i))}
        walk(rootInActiveWindow);return candidates.sortedBy{it.second.height()}.any{it.first.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}
    }
    private suspend fun gesture(x1:Float,y1:Float,x2:Float,y2:Float)=suspendCancellableCoroutine<Unit>{c->val dm=resources.displayMetrics;val p=Path().apply{moveTo(x1*dm.widthPixels,y1*dm.heightPixels);lineTo(x2*dm.widthPixels,y2*dm.heightPixels)};val cb=object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}};if(!dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,650)).build(),cb,null)&&c.isActive)c.resume(Unit){}}
    private suspend fun gesturePixels(x:Float,y:Float)=suspendCancellableCoroutine<Unit>{c->val p=Path().apply{moveTo(x,y);lineTo(x+1,y+1)};val cb=object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}};if(!dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,160)).build(),cb,null)&&c.isActive)c.resume(Unit){}}
}

data class CaptureRow(val topic:String,val superLike:String,val posts:String,val interactions:String,val checkins:String,val reads:String){fun json()=org.json.JSONObject().put("topic",topic).put("superLike",superLike).put("posts",posts).put("interactions",interactions).put("checkins",checkins).put("reads",reads)}
