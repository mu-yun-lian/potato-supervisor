package org.potato.supervisor.overlay

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.PowerManager
import android.os.SystemClock
import org.potato.supervisor.R
import org.potato.supervisor.rules.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Cue { APPEAR, DISAPPEAR, WARNING, EXPLOSION }
class SoundEffects(private val context: Context) {
    // Keep playback and the media-volume check on the same channel.
    internal val audioAttributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build()
    private val mutableStatus=MutableStateFlow("")
    val status: StateFlow<String> = mutableStatus
    private val sounds=mutableMapOf<Cue,Int>()
    private val ready=mutableSetOf<Int>()
    private val streams=mutableMapOf<Cue,Int>()
    private val failed=mutableSetOf<Int>()
    private var pending: Triple<Cue,Config,Long>?=null
    private var closed=false
    init {
        pool.setOnLoadCompleteListener { _,id,status ->
            if(status!=0) {
                failed.add(id)
                if(pending?.first?.let { sounds[it]==id }==true) { pending=null; reject("音效加载失败，请重新打开应用后试听。") }
            }
            if(status==0 && !closed) {
                ready.add(id)
                pending?.let { (cue,c,at) -> if(sounds[cue]==id) {
                    pending=null; if(SystemClock.elapsedRealtime()-at<400) play(cue,c)
                } }
            }
        }
        listOf(Cue.APPEAR to R.raw.appear,Cue.DISAPPEAR to R.raw.disappear,
            Cue.WARNING to R.raw.warning,Cue.EXPLOSION to R.raw.explosion).forEach { (cue,res) -> sounds[cue]=pool.load(context,res,1) }
    }
    private fun reject(message: String): Int { mutableStatus.value=message; return 0 }
    fun play(cue: Cue, c: Config): Int = try { playReady(cue,c) }
        catch (_: Exception) { reject("系统暂时无法播放音效，请重新试听。") }
    private fun playReady(cue: Cue, c: Config): Int {
        if(closed) return reject("音效已停止，请重新打开应用。")
        if(!c.sound) return reject("动作音效未开启，请打开开关并保存。")
        if(c.soundVolume==0) return reject("应用音效音量为 0，请调高后保存。")
        val audio=context.getSystemService(AudioManager::class.java)
        if(audio.ringerMode==AudioManager.RINGER_MODE_SILENT) return reject("手机处于静音模式，音效已暂停。")
        if(audio.ringerMode==AudioManager.RINGER_MODE_VIBRATE) return reject("手机处于振动模式，音效已暂停。")
        if(audio.mode!=AudioManager.MODE_NORMAL) return reject("通话或语音通信占用了音频，音效已暂停。")
        if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0) return reject("手机媒体音量为 0，请调高媒体音量。")
        val filter=context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        if(filter==NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return reject("无法确认勿扰状态，音效已暂停。")
        if(filter!=NotificationManager.INTERRUPTION_FILTER_ALL) return reject("手机开启了勿扰模式，音效已暂停。")
        if(context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !context.getSystemService(PowerManager::class.java).isInteractive) return reject("手机锁屏或熄屏，音效已暂停。")
        val id=sounds[cue] ?: return reject("找不到这段音效。")
        if(id==0 || id in failed) return reject("音效加载失败，请重新打开应用后试听。")
        if(id !in ready) { pending=Triple(cue,c,SystemClock.elapsedRealtime()); return reject("音效正在加载，请稍后再试听。") }
        streams.remove(cue)?.let { pool.stop(it) }
        val volume=c.soundVolume/100f
        return pool.play(id,volume,volume,1,0,1f).also {
            if(it>0) {
                streams[cue]=it
                val name=when(cue) { Cue.APPEAR -> "出现"; Cue.DISAPPEAR -> "收起"; Cue.WARNING -> "警告"; Cue.EXPLOSION -> "爆炸" }
                mutableStatus.value="已请求播放「$name」；仍听不到请检查媒体音量和耳机输出。"
            } else reject("系统未接受播放，请稍后重试。")
        }
    }
    fun stop(includeExit: Boolean = true, includeExplosion: Boolean = true) {
        pending=null
        streams.keys.toList().filter { (includeExit || it!=Cue.DISAPPEAR) && (includeExplosion || it!=Cue.EXPLOSION) }.forEach { cue -> streams.remove(cue)?.let { pool.stop(it) } }
    }
    fun close() { if(!closed) { stop(); closed=true; pool.release() } }
}
