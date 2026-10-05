package org.potato.supervisor.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.PowerManager
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.sin

/** Bitmap character with lifecycle-bound motion. Character rights are separate from code. */
class PotatoView(context: Context) : View(context) {
    var expression: String = "idle"
        set(value) { if(field!=value) { field=value; react(); invalidate() } }
    var motionEnabled: Boolean = true
        set(value) { if(field!=value) { field=value; syncMotion() } }
    var onInteraction: (() -> Unit)? = null
    private val sprite=bitmap(context,"potato-mine.png")!!
    private val blink=bitmap(context,"potato-mine-blink.png")
    private val angry=bitmap(context,"potato-mine-angry.png")
    private val burst=bitmap(context,"potato-mine-explosion.png")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val warningLight=LightingColorFilter(Color.WHITE,Color.rgb(28,6,0))
    private val shade=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(50,76,32) }
    private val glow=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(245,78,35) }
    private val destination=RectF()
    private val shadow=RectF()
    private var phase=0f
    private var reaction=0f
    private var entrance=1f
    private var blast=0f
    private var idle: ValueAnimator?=null
    private var tap: ValueAnimator?=null
    private var arrival: ValueAnimator?=null
    private var explosion: ValueAnimator?=null
    val isMotionRunning: Boolean get()=idle?.isRunning==true
    init { contentDescription="土豆地雷，点击互动"; isClickable=true; isFocusable=true }
    private fun mayAnimate()=motionEnabled && ValueAnimator.areAnimatorsEnabled() && !context.getSystemService(PowerManager::class.java).isPowerSaveMode
    private fun syncMotion() {
        if(isAttachedToWindow && isShown && windowVisibility==VISIBLE && mayAnimate()) {
            if(idle==null) idle=ValueAnimator.ofFloat(0f,1f).apply {
                duration=4800; repeatCount=ValueAnimator.INFINITE; interpolator=LinearInterpolator()
                addUpdateListener { if(!mayAnimate()) stopMotion() else { phase=it.animatedValue as Float; invalidate() } }; start()
            }
        } else stopMotion()
    }
    fun stopMotion() {
        idle?.cancel(); idle=null; tap?.cancel(); tap=null; arrival?.cancel(); arrival=null; explosion?.cancel(); explosion=null
        phase=0f; reaction=0f; entrance=1f; blast=0f; invalidate()
    }
    /** Returns the covered animation interval; caller still validates the HOME request afterwards. */
    fun explode(): Long {
        expression="cooldown"
        if(!isAttachedToWindow || !mayAnimate()) return 0
        explosion?.cancel()
        explosion=ValueAnimator.ofFloat(0f,1f).apply {
            duration=900; interpolator=LinearInterpolator()
            addUpdateListener { blast=it.animatedValue as Float; invalidate() }; start()
        }
        return 900
    }
    private fun react() {
        if(!isAttachedToWindow || !mayAnimate()) return
        tap?.cancel()
        tap=ValueAnimator.ofFloat(0f,1f,0f).apply {
            duration=520; interpolator=DecelerateInterpolator()
            addUpdateListener { reaction=it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun performClick(): Boolean { super.performClick(); react(); onInteraction?.invoke(); return true }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); syncMotion()
        if(mayAnimate()) arrival=ValueAnimator.ofFloat(0f,1f).apply {
            duration=460; interpolator=OvershootInterpolator(1.2f)
            addUpdateListener { entrance=it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView,visibility); syncMotion() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncMotion() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit=resources.displayMetrics.density
        val wave=sin(phase*Math.PI*2).toFloat()
        val scale=minOf(width*.86f/sprite.width,height*.90f/sprite.height)
        val w=sprite.width*scale; val h=sprite.height*scale
        val left=(width-w)/2; val top=(height-h)/2 - wave*1.8f*unit - reaction*7*unit + (1f-entrance)*h*.65f
        destination.set(left,top,left+w,top+h)
        shadow.set(width*.27f,height*.87f,width*.74f,height*.95f)
        shade.alpha=(18-reaction*7).toInt(); canvas.drawOval(shadow,shade)
        canvas.save()
        canvas.clipRect(0f,0f,width.toFloat(),height*.95f)
        canvas.scale(1f+reaction*.035f,1f-reaction*.015f,width/2f,height*.85f)
        val warning=expression in setOf("cooldown","suspicious")
        if(warning) canvas.rotate(wave*1.4f + if(blast in .01f.. .4f) sin(blast*80).toFloat()*2f else 0f,width/2f,height*.84f)
        val closed=blink!=null && (phase in .78f.. .82f || reaction>.65f)
        val frame=if(warning) angry ?: sprite else if(closed) blink!! else sprite
        paint.colorFilter=if(warning && wave>0f) warningLight else null
        paint.alpha=if(blast>.35f) ((1-(blast-.35f)/.3f).coerceIn(0f,1f)*255).toInt() else 255
        canvas.drawBitmap(frame,null,destination,paint); paint.alpha=255; paint.colorFilter=null
        glow.alpha=(if(expression=="cooldown") 14+18*(wave+1)/2 else 4+8*(wave+1)/2).toInt()
        canvas.drawCircle(left+w*.445f,top+h*.164f,w*.106f,glow)
        canvas.restore()
        if(blast>.3f && burst!=null) {
            val progress=((blast-.3f)/.7f).coerceIn(0f,1f)
            val size=minOf(width.toFloat(),height.toFloat())*(.35f+progress*.75f)
            destination.set(width/2f-size/2,height*.58f-size/2,width/2f+size/2,height*.58f+size/2)
            paint.alpha=((1-progress)*255).toInt()
            canvas.drawBitmap(burst,null,destination,paint); paint.alpha=255
        }
    }
    companion object {
        private val cached=mutableMapOf<String,Bitmap>()
        private fun bitmap(context: Context,name: String): Bitmap? = cached[name] ?: runCatching {
            context.assets.open("character/$name").use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=2 }) }
                ?.also { cached[name]=it }
        }.getOrNull()
    }
}
