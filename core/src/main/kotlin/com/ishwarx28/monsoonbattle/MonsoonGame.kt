package com.ishwarx28.monsoonbattle

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.ishwarx28.monsoonbattle.audio.Soundscape
import com.ishwarx28.monsoonbattle.gameplay.*
import com.ishwarx28.monsoonbattle.render.WorldRenderer
import com.ishwarx28.monsoonbattle.ui.*
import com.ishwarx28.monsoonbattle.world.WorldLayout

class MonsoonGame(private val platform:PlatformServices,private val smokeFrames:Int=0,private val silent:Boolean=false,private val smokeScene:String="menu"):ApplicationAdapter() {
    private lateinit var layout:WorldLayout;private lateinit var simulation:Simulation
    private lateinit var renderer:WorldRenderer;private lateinit var audio:Soundscape;private lateinit var hud:Hud
    private val save=SaveStore();private var hasSave=false;private var playerName="Officer"
    private var elapsedSave=0f;private var footsteps=0f;private var frames=0;private var lightningWasActive=false
    private var externalPause=false;private var lifecyclePaused=false;private var disposed=false
    private val prefs get()=Gdx.app.getPreferences("monsoon-battle-settings")
    override fun create() {
        playerName=prefs.getString("playerName","Officer")
        hud=Hud(::action);Gdx.input.inputProcessor=hud;Gdx.input.setCatchKey(Input.Keys.BACK,true)
        layout=WorldLayout();simulation=Simulation(layout);renderer=WorldRenderer(layout)
        renderer.highQuality=prefs.getBoolean("highQuality",true)
        audio=Soundscape();audio.enabled=prefs.getBoolean("sound",true) && !silent
        hasSave=save.load()!=null;attach()
        if(smokeFrames>0 && smokeScene!="menu") {
            hud.mode=Mode.PLAY
            if(smokeScene=="factory") {
                simulation.player.set(-62f,.12f,44f);simulation.yaw=180f;simulation.mission.stage=MissionStage.INTEL
            }else if(smokeScene=="car") {
                val car=simulation.vehicles.first();simulation.player.set(car.position.x-2.8f,.12f,car.position.z)
            }else simulation.player.set(2f,.12f,-64f)
            simulation.physics.setActor(simulation.playerBody,simulation.player,simulation.yaw)
            if(smokeScene=="car")simulation.interact()
        }
        Gdx.app.log("Monsoon","Loaded ${layout.rooms.size} rooms, ${layout.blocks.size} blocks and ${simulation.people.size} people")
    }
    private fun attach() {
        simulation.onSound={name,volume->audio.effect(name,volume)}
        simulation.onDialogue={hi,en->if(audio.enabled)platform.speak(hi,en)}
        simulation.onCheckpoint={checkpoint()}
    }
    private fun checkpoint(){if(smokeFrames==0){save.save(simulation);hasSave=true};elapsedSave=0f}
    private fun start(data:SavedRun?=null) {
        hud.clearInput();simulation.dispose()
        simulation=Simulation(layout,data?.missionNumber?:hud.selectedMission,data?.seed?:((System.currentTimeMillis()%1000000).toInt()))
        if(data!=null)save.restore(simulation,data)
        attach();hud.mode=Mode.PLAY;hud.mapOpen=false
        Gdx.input.isCursorCatched=Gdx.app.type==Application.ApplicationType.Desktop
        audio.resume();checkpoint()
        if(data==null)simulation.say("शहर में एक व्यक्ति फँसा हुआ है। उसे सुरक्षित निकालना है।", "Someone is still inside. Find them and bring them home.")
    }
    private fun action(id:String) {
        when(id) {
            "begin"->start()
            "continue"->save.load()?.let{start(it)}
            "factory"->hud.selectedMission=0
            "mall"->hud.selectedMission=1
            "pause"->{hud.mode=Mode.PAUSE;hud.clearInput();Gdx.input.isCursorCatched=false;audio.pause();platform.stopSpeech();checkpoint()}
            "resume"->{hud.mode=Mode.PLAY;hud.clearInput();Gdx.input.isCursorCatched=Gdx.app.type==Application.ApplicationType.Desktop;audio.resume()}
            "menu"->{checkpoint();hud.mode=Mode.MENU;hud.clearInput();hud.mapOpen=false;Gdx.input.isCursorCatched=false;audio.resume();platform.stopSpeech()}
            "end"->{simulation.vitals.abandonRun();checkpoint();action("menu")}
            "map"->{hud.mapOpen=!hud.mapOpen;hud.clearInput();Gdx.input.isCursorCatched=!hud.mapOpen && Gdx.app.type==Application.ApplicationType.Desktop}
            "sound"->{audio.enabled=!audio.enabled;prefs.putBoolean("sound",audio.enabled).flush();if(!audio.enabled)platform.stopSpeech()}
            "quality"->{renderer.highQuality=!renderer.highQuality;prefs.putBoolean("highQuality",renderer.highQuality).flush();simulation.notice(if(renderer.highQuality)"High-quality reflections"else"Battery-friendly reflections")}
            "privacy"->platform.showPrivacyOptions()
            "name"->Gdx.input.getTextInput(object:Input.TextInputListener {
                override fun input(text:String){Gdx.app.postRunnable{playerName=text.filter{!it.isISOControl()}.trim().take(20).ifBlank{"Officer"};prefs.putString("playerName",playerName).flush()}}
                override fun canceled(){}
            },"Officer name",playerName,"Enter your name")
            "reward"->{
                val run=simulation;val ticket=run.vitals.beginRevive()?:return
                hud.clearInput();platform.showRewarded {earned->Gdx.app.postRunnable {
                    if(disposed || simulation!==run)return@postRunnable
                    if(run.vitals.finishRevive(ticket,earned)) {
                        run.people.forEach{it.reaction=1.25f;it.fireCooldown=1.25f};run.notice("Revived. 100 health restored.")
                        hud.mode=Mode.PLAY;Gdx.input.isCursorCatched=Gdx.app.type==Application.ApplicationType.Desktop
                    } else run.notice("Reward not earned or ad unavailable. You can retry without losing a revive.")
                    checkpoint()
                }}
            }
        }
    }
    fun setExternalPause(value:Boolean) {externalPause=value;if(::audio.isInitialized){if(value)audio.pause()else if(!lifecyclePaused)audio.resume()};if(::hud.isInitialized)hud.clearInput()}
    override fun render() {
        if(disposed)return
        val dt=Gdx.graphics.deltaTime.coerceIn(0f,.05f);frames++
        if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            if(hud.mapOpen)action("map")else if(hud.mode==Mode.PLAY)action("pause")else if(hud.mode==Mode.PAUSE)action("resume")
        }
        if(Gdx.input.isKeyJustPressed(Input.Keys.M) && hud.mode==Mode.PLAY)action("map")
        val command=if(hud.mode==Mode.PLAY && !hud.mapOpen && !externalPause)hud.command()else Command()
        if(!lifecyclePaused && !externalPause) {
            if(hud.mode==Mode.MENU)simulation.weather.step(dt)
            else if(hud.mode==Mode.PLAY && !hud.mapOpen) {
                simulation.update(dt,command);elapsedSave+=dt
                if(elapsedSave>5f)checkpoint()
                if(simulation.vitals.state!=LifeState.ACTIVE || simulation.mission.stage==MissionStage.COMPLETE || simulation.mission.stage==MissionStage.FAILED) {
                    Gdx.input.isCursorCatched=false;hud.clearInput()
                }
                footsteps-=dt
                if(simulation.vehicle==null && simulation.vitals.state==LifeState.ACTIVE && (command.forward!=0f || command.strafe!=0f) && footsteps<=0f) {
                    footsteps=if(command.sprint).28f else .43f;audio.effect("step",if(simulation.stance==Stance.PRONE).10f else .23f)
                }
            }
        }
        if(!lifecyclePaused && !externalPause && hud.mode!=Mode.PAUSE)audio.update(simulation.weather.rain,layout.roofAt(simulation.player.x,simulation.player.z)!=null,
            simulation.vehicle!=null,simulation.vehicle?.speed?:0f,hud.mode==Mode.MENU)
        val lightning=simulation.weather.flash>0f
        if(lightning && !lightningWasActive)audio.effect("thunder",.55f)
        lightningWasActive=lightning
        renderer.render(simulation,hud.mode==Mode.MENU,command.aim)
        hud.draw(simulation,playerName,audio.enabled,hasSave,platform.rewardedReady)
        if(smokeFrames>0 && frames==smokeFrames) {
            val image=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.backBufferWidth,Gdx.graphics.backBufferHeight)
            val f=Gdx.files.local(".build-tools/$smokeScene-smoke.png");val png=PixmapIO.PNG();png.setFlipY(true);png.write(f,image);png.dispose();image.dispose()
            Gdx.app.log("Smoke","Saved ${f.file().absolutePath}; frames=$frames; fps=${Gdx.graphics.framesPerSecond}")
            Gdx.app.exit()
        }
    }
    override fun resize(width:Int,height:Int){if(::renderer.isInitialized)renderer.resize(width,height);if(::hud.isInitialized)hud.resize(width,height)}
    override fun pause(){lifecyclePaused=true;if(::simulation.isInitialized && hud.mode!=Mode.MENU)checkpoint();if(::audio.isInitialized)audio.pause();if(::hud.isInitialized)hud.clearInput();platform.stopSpeech()}
    override fun resume(){lifecyclePaused=false;if(::audio.isInitialized && !externalPause)audio.resume()}
    override fun dispose(){disposed=true;if(::simulation.isInitialized){if(hud.mode!=Mode.MENU)checkpoint();simulation.dispose()};if(::renderer.isInitialized)renderer.dispose();if(::audio.isInitialized)audio.dispose();if(::hud.isInitialized)hud.dispose();platform.stopSpeech()}
}
