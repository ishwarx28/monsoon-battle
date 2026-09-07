package com.ishwarx28.monsoonbattle

import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Toast
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.*
import com.google.android.ump.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Ads and speech never touch simulation state directly. Debug builds use Google's test IDs. */
class AndroidLauncher:AndroidApplication(),PlatformServices {
    private lateinit var game:MonsoonGame
    private lateinit var consent:ConsentInformation
    private var adsInitialized=false;private var ad:RewardedAd?=null;private var loading=false;private var loadedAt=0L
    @Volatile private var ready=false
    private var tts:TextToSpeech?=null;private var ttsReady=false;private var hindi=false;private var speechId=0
    override val rewardedReady get()=ready
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        game=MonsoonGame(this)
        val config=AndroidApplicationConfiguration().apply {
            useImmersiveMode=true;useAccelerometer=false;useCompass=false;useGyroscope=false
            r=8;g=8;b=8;a=8;depth=24;stencil=0;numSamples=2
        }
        initialize(game,config)
        tts=TextToSpeech(this){status->runOnUiThread{
            val engine=tts
            if(status==TextToSpeech.SUCCESS && engine!=null) {
                val locale=Locale.forLanguageTag("hi-IN")
                hindi=engine.isLanguageAvailable(locale)>=TextToSpeech.LANG_AVAILABLE
                engine.language=if(hindi)locale else Locale.US
                engine.voices?.firstOrNull{it.locale.language==(if(hindi)"hi"else"en") && !it.isNetworkConnectionRequired}?.let{engine.voice=it}
                engine.setSpeechRate(.93f);ttsReady=true
            }
        }}
        consent=UserMessagingPlatform.getConsentInformation(this)
        consent.requestConsentInfoUpdate(this,ConsentRequestParameters.Builder().build(),{
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this){if(consent.canRequestAds())initializeAds()}
        },{if(consent.canRequestAds())initializeAds()})
        if(consent.canRequestAds())initializeAds()
    }
    private fun initializeAds() {
        if(adsInitialized || !consent.canRequestAds())return
        adsInitialized=true
        MobileAds.initialize(this){runOnUiThread{loadRewarded()}}
    }
    private fun loadRewarded() {
        if(!adsInitialized || !consent.canRequestAds() || loading || isFinishing)return
        ready=false;loading=true
        RewardedAd.load(this,BuildConfig.REWARDED_AD_UNIT,AdRequest.Builder().build(),object:RewardedAdLoadCallback(){
            override fun onAdLoaded(value:RewardedAd){ad=value;loadedAt=SystemClock.elapsedRealtime();ready=true;loading=false}
            override fun onAdFailedToLoad(error:LoadAdError){ad=null;ready=false;loading=false}
        })
    }
    override fun showRewarded(onResult:(Boolean)->Unit) = runOnUiThread {
        val value=ad
        if(value==null || SystemClock.elapsedRealtime()-loadedAt>55*60*1000L || isFinishing) {
            ad=null;ready=false;loadRewarded();onResult(false);return@runOnUiThread
        }
        ad=null;ready=false
        val delivered=AtomicBoolean(false)
        fun result(earned:Boolean){if(delivered.compareAndSet(false,true))onResult(earned)}
        Gdx.app.postRunnable{game.setExternalPause(true)}
        value.fullScreenContentCallback=object:FullScreenContentCallback(){
            override fun onAdDismissedFullScreenContent(){result(false);Gdx.app.postRunnable{game.setExternalPause(false)};loadRewarded()}
            override fun onAdFailedToShowFullScreenContent(error:AdError){result(false);Gdx.app.postRunnable{game.setExternalPause(false)};loadRewarded()}
        }
        // Only this SDK-earned callback can grant a revive. Dismissal, failure and retry cannot.
        value.show(this){result(true)}
    }
    override fun speak(hindi:String,english:String)=runOnUiThread {
        if(ttsReady && !isFinishing)tts?.speak(if(this.hindi)hindi else english,TextToSpeech.QUEUE_FLUSH,null,"mission-${speechId++}")
    }
    override fun stopSpeech(){runOnUiThread{tts?.stop()}}
    override fun showPrivacyOptions()=runOnUiThread {
        if(consent.privacyOptionsRequirementStatus==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Gdx.app.postRunnable{game.setExternalPause(true)}
            UserMessagingPlatform.showPrivacyOptionsForm(this){
                if(!consent.canRequestAds()){ready=false;ad=null}else loadRewarded()
                Gdx.app.postRunnable{game.setExternalPause(false)}
            }
        } else Toast.makeText(this,"No additional privacy choices are required in this session.",Toast.LENGTH_LONG).show()
    }
    override fun onPause(){tts?.stop();super.onPause()}
    override fun onDestroy(){ready=false;ad=null;ttsReady=false;tts?.stop();tts?.shutdown();tts=null;super.onDestroy()}
}
