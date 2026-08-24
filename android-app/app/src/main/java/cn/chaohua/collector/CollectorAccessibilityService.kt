package cn.chaohua.collector

import android.accessibilityservice.*
import android.content.Intent
import android.graphics.Rect
import android.graphics.Path
import android.net.Uri
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.*
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class RunRequest(val topics:List<Topic>,val upload:Boolean,val showResult:Boolean,val useRemote:Boolean)

class CollectorAccessibilityService:AccessibilityService(){
    companion object{
        private var instance:CollectorAccessibilityService?=null
        private var queued:RunRequest?=null
        fun requestRun(topics:List<Topic>,upload:Boolean,showResult:Boolean,useRemote:Boolean){val req=RunRequest(topics,upload,showResult,useRemote);instance?.run(req)?:run{queued=req}}
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var activeJob:Job?=null
    override fun onServiceConnected(){instance=this;queued?.also{run(it);queued=null}}
    override fun onDestroy(){instance=null;scope.cancel();super.onDestroy()}
    override fun onAccessibilityEvent(e:AccessibilityEvent?){}
    override fun onInterrupt(){}
    private fun run(req:RunRequest){
        if(activeJob?.isActive==true){toast("已有采集任务正在运行，请勿重复点击");return}
        activeJob=scope.launch{
            try{
                val activeTopics=if(req.useRemote)RepositoryClient(applicationContext).fetchTopics().ifEmpty{req.topics}else req.topics
                val results=mutableListOf<CaptureRow>();val failed=mutableListOf<String>()
                activeTopics.forEachIndexed{i,topic->
                    toast("正在采集 ${i+1}/${activeTopics.size}：${topic.name}")
                    val row=withTimeoutOrNull(90000){capture(topic)}
                    performGlobalAction(GLOBAL_ACTION_BACK);delay(2600)
                    if(row!=null){results.add(row);toast("${topic.name} 采集成功")}else{failed.add(topic.name);toast("${topic.name} 采集失败，继续下一个")}
                }
                var uploaded=false;if(req.upload&&results.size==activeTopics.size){RepositoryClient(applicationContext).upload(results);uploaded=true}
                if(req.showResult){val intent=Intent(applicationContext,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP;putExtra("result_json",JSONArray().apply{results.forEach{put(it.json())}}.toString());putExtra("failed",failed.joinToString("、"));putExtra("uploaded",uploaded)};startActivity(intent)}
            }finally{activeJob=null}
        }
    }
    private suspend fun capture(topic:Topic):CaptureRow?{
        val direct=Intent(Intent.ACTION_VIEW,Uri.parse("sinaweibo://pageinfo?containerid=${Uri.encode(topic.id)}")).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK;setPackage("com.sina.weibo")}
        runCatching{startActivity(direct)}.getOrElse{return null}
        var homeReady=false
        repeat(24){if(!homeReady){delay(700);val page=allText();if(page.contains(topic.name)&&(page.contains("今日签到")||page.contains("帖子")))homeReady=true}}
        if(!homeReady)return null
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
        repeat(10){
            if(superLike==null){
                superLike=match(allText(),Regex("超\\s*LIKE\\s*([0-9.]+\\s*[万亿]?人?)",RegexOption.IGNORE_CASE))
                if(superLike==null){scrollHeaderForward();delay(250);gesture(.93f,.15f,.05f,.15f);delay(1200)}
            }
        }
        if(superLike==null)return null
        if(!openTopicDetail(topic.name))return null
        gesture(.50f,.84f,.50f,.24f);delay(1800)
        var detail=allText()
        repeat(6){
            if(!detail.contains("今日新帖")||!detail.contains("今日新增互动")){gesture(.50f,.84f,.50f,.24f);delay(1400);detail=allText()}
        }
        val posts=match(detail,Regex("今日新帖\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val interactions=match(detail,Regex("今日新增互动\\s*([0-9.]+\\s*[万亿]?)"))?:return null;val reads=match(detail,Regex("([0-9.]+\\s*[万亿]?)\\s*阅读"))?:return null
        delay(2200)
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
    private fun isDetailPage():Boolean{val page=allText();return page.contains("公开超话")||page.contains("钻超等级")||page.contains("今日新帖")||page.contains("今日新增互动")}
    private suspend fun openTopicDetail(name:String):Boolean{
        val dm=resources.displayMetrics
        repeat(4){attempt->
            if(isDetailPage())return true
            when(attempt){
                0,3->clickTopicHeader(name)
                1->gesturePixels(dm.widthPixels*.31f,dm.heightPixels*.115f)
                else->gesturePixels(dm.widthPixels*.36f,dm.heightPixels*.135f)
            }
            repeat(5){delay(700);if(isDetailPage())return true}
        }
        return false
    }
    private fun match(s:String,r:Regex)=r.find(s)?.groupValues?.get(1)?.replace(" ","")
    private fun toast(s:String)=Toast.makeText(applicationContext,s,Toast.LENGTH_LONG).show()
    private fun scrollHeaderForward():Boolean{
        val dm=resources.displayMetrics;val candidates=mutableListOf<Pair<AccessibilityNodeInfo,Rect>>()
        fun walk(n:AccessibilityNodeInfo?){if(n==null)return;val r=Rect();n.getBoundsInScreen(r);if(n.isScrollable&&r.width()>dm.widthPixels*.45f&&r.height()<dm.heightPixels*.18f&&r.centerY()<dm.heightPixels*.35f)candidates.add(n to r);for(i in 0 until n.childCount)walk(n.getChild(i))}
        walk(rootInActiveWindow);return candidates.sortedBy{it.second.height()}.any{it.first.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}
    }
    private suspend fun gesture(x1:Float,y1:Float,x2:Float,y2:Float)=suspendCancellableCoroutine<Unit>{c->val dm=resources.displayMetrics;val p=Path().apply{moveTo(x1*dm.widthPixels,y1*dm.heightPixels);lineTo(x2*dm.widthPixels,y2*dm.heightPixels)};val cb=object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}};if(!dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,650)).build(),cb,null)&&c.isActive)c.resume(Unit){}}
    private suspend fun gesturePixels(x:Float,y:Float)=suspendCancellableCoroutine<Unit>{c->val p=Path().apply{moveTo(x,y);lineTo(x+1,y+1)};val cb=object:GestureResultCallback(){override fun onCompleted(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}override fun onCancelled(g:GestureDescription?){if(c.isActive)c.resume(Unit){}}};if(!dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,160)).build(),cb,null)&&c.isActive)c.resume(Unit){}}
}

data class CaptureRow(val topic:String,val superLike:String,val posts:String,val interactions:String,val checkins:String,val reads:String){fun json()=org.json.JSONObject().put("topic",topic).put("superLike",superLike).put("posts",posts).put("interactions",interactions).put("checkins",checkins).put("reads",reads)}
