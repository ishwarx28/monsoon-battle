package com.ishwarx28.monsoonbattle.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.*
import com.ishwarx28.monsoonbattle.*

/** Desktop is a development harness; Android is the production platform. No fake ad rewards. */
fun main(args:Array<String>) {
    val config=Lwjgl3ApplicationConfiguration().apply {
        setTitle("monsoon-battle");setWindowedMode(1280,720);setForegroundFPS(60);useVsync(true)
        setBackBufferConfig(8,8,8,8,24,0,2)
        if(args.contains("--silent"))disableAudio(true)
    }
    val services=object:PlatformServices {
        override val rewardedReady=false
        override fun showRewarded(onResult:(Boolean)->Unit){onResult(false)}
        override fun speak(hindi:String,english:String){Gdx.app.log("Dialogue",english)}
        override fun stopSpeech(){}
        override fun showPrivacyOptions(){Gdx.app.log("Privacy","No ads or tracking in the desktop harness.")}
    }
    Lwjgl3Application(MonsoonGame(services,if(args.contains("--smoke"))100 else 0,args.contains("--silent"),args.firstOrNull{it.startsWith("--scene=")}?.substringAfter("=")?:"menu"),config)
}
