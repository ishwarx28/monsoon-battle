package com.ishwarx28.monsoonbattle.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

/** Locally synthesised PCM: filtered rain, wind, engines, foley and an original restrained score. */
class Soundscape:Disposable {
    private val effects=mutableMapOf<String,Sound>();private val loops=mutableMapOf<String,Music>()
    var enabled=true;private var paused=false;private var engineActive=false
    init {
        try {
            val r=Random(2831)
            fun noise()=r.nextFloat()*2f-1f
            var filtered=0f
            create("rain",7f,true){t->filtered=filtered*.85f+noise()*.15f;(filtered*.58f+noise()*.075f)*(0.86f+0.14f*sin(t*2.1f))}
            var wind=0f
            create("wind",8f,true){t->wind=wind*.975f+noise()*.025f;wind*(0.7f+sin(t*.8f)*.25f)}
            create("engine",4f,true){t->(sin(t*2f*PI.toFloat()*43f)*.33f+sin(t*2f*PI.toFloat()*86f)*.12f+noise()*.018f)}
            create("shot",0.65f){t->(noise()*.88f*exp(-t*32f)+sin(2f*PI.toFloat()*(90f*t-35f*t*t))*.58f*exp(-t*16f)+noise()*.15f*exp(-t*5f)).coerceIn(-1f,1f)}
            create("reload",0.6f){t->noise()*(exp(-abs(t-.05f)*100f)*.45f+exp(-abs(t-.35f)*95f)*.5f+exp(-abs(t-.52f)*100f)*.38f)}
            create("melee",0.38f){t->noise()*sin(PI.toFloat()*t/.38f)*exp(-t*7f)*.5f}
            create("hurt",0.3f){t->(sin(t*PI.toFloat()*135f)*.3f+noise()*.2f)*exp(-t*14f)}
            create("step",0.3f){t->noise()*exp(-t*22f)*.32f+sin(t*PI.toFloat()*95f)*exp(-t*30f)*.2f}
            create("thunder",3.5f){t->noise()*(.1f+.16f*sin(t*17f))*exp(-t*.9f)+sin(t*2f*PI.toFloat()*29f)*exp(-t*1.3f)*.22f}
            val chordRoots=floatArrayOf(110f,87.307f,130.813f,97.999f)
            val melody=intArrayOf(0,7,12,10,7,3,5,7,0,7,15,12,10,7,5,3)
            create("score",32f,true){t->
                val root=chordRoots[(t/8).toInt().coerceIn(0,3)]
                val swell=(0.5f-0.5f*cos((t%8f)/8f*2f*PI.toFloat()))
                var value=0f
                for(interval in floatArrayOf(1f,1.189207f,1.498307f,2f))value+=sin(t*2f*PI.toFloat()*root*interval)*.045f*swell
                val step=(t/2).toInt().coerceIn(0,15);val frequency=220f*2f.pow(melody[step]/12f)
                value+=sin(t*2f*PI.toFloat()*frequency)*exp(-(t%2f)*2f)*.045f
                value
            }
        }catch(e:Exception){Gdx.app.error("Audio","Audio is unavailable; continuing silently",e)}
    }
    private fun create(name:String,seconds:Float,loop:Boolean=false,sample:(Float)->Float) {
        val file=Gdx.files.local("monsoon-battle/audio-v1/$name.wav")
        if(!file.exists()) {
            val rate=22050;val count=(seconds*rate).toInt();val bytes=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray());bytes.putInt(36+count*2);bytes.put("WAVEfmt ".toByteArray());bytes.putInt(16)
            bytes.putShort(1);bytes.putShort(1);bytes.putInt(rate);bytes.putInt(rate*2);bytes.putShort(2);bytes.putShort(16)
            bytes.put("data".toByteArray());bytes.putInt(count*2)
            for(i in 0 until count){val t=i.toFloat()/rate;val fade=min(1f,min(i/220f,(count-i)/220f));bytes.putShort((sample(t).coerceIn(-1f,1f)*fade*30000).toInt().toShort())}
            file.writeBytes(bytes.array(),false)
        }
        if(loop)loops[name]=Gdx.audio.newMusic(file).apply{isLooping=true;volume=0f}
        else effects[name]=Gdx.audio.newSound(file)
    }
    fun effect(name:String,volume:Float=1f){if(enabled && !paused)effects[name]?.play(volume.coerceIn(0f,1f))}
    fun update(rain:Float,indoors:Boolean,driving:Boolean,speed:Float,menu:Boolean) {
        engineActive=driving
        if(!enabled || paused){loops.values.forEach{if(it.isPlaying)it.pause()};return}
        loops.forEach{(name,music)->
            val volume=when(name){"rain"->rain*(if(indoors).13f else .42f);"wind"->if(indoors).04f else .16f;"score"->if(menu).45f else .24f;"engine"->if(driving)(.10f+abs(speed)/80f).coerceAtMost(.4f)else 0f;else->0f}
            music.volume=volume
            if(volume>0f && !music.isPlaying)music.play()
            if(volume<=0f && music.isPlaying)music.pause()
        }
    }
    fun pause(){paused=true;loops.values.forEach{it.pause()};effects.values.forEach{it.stop()}}
    fun resume(){paused=false}
    override fun dispose(){loops.values.forEach{it.dispose()};effects.values.forEach{it.dispose()}}
}
