package com.ishwarx28.monsoonbattle.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.Disposable
import com.ishwarx28.monsoonbattle.gameplay.*
import kotlin.math.*
import com.ishwarx28.monsoonbattle.render.Fonts

enum class Mode { MENU, PLAY, PAUSE }
class Hud(private val action:(String)->Unit):InputAdapter(),Disposable {
    data class Button(val id:String,val text:String,val rect:Rectangle,val primary:Boolean=false)
    val height=720f;var width=1280f;private val camera=OrthographicCamera()
    private val batch=SpriteBatch();private val shapes=ShapeRenderer();private val font=Fonts.create();private val glyph=GlyphLayout()
    private val buttons=mutableListOf<Button>();private val held=mutableMapOf<Int,String>();private val taps=mutableSetOf<String>()
    private val cream=Color(.89f,.89f,.81f,1f);private val gold=Color(.84f,.68f,.40f,1f);private val muted=Color(.53f,.66f,.65f,1f)
    private var movePointer=-1;private var lookPointer=-1;private var lookX=0f;private var lookY=0f
    private var previous=Vector2();val anchor=Vector2(115f,140f);val stick=Vector2();var mapOpen=false
    var mode=Mode.MENU;var selectedMission=0;private var skipMouse=2
    private val desktop get()=Gdx.app.type==com.badlogic.gdx.Application.ApplicationType.Desktop
    init{font.region.texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);resize(Gdx.graphics.width,Gdx.graphics.height)}
    fun resize(w:Int,h:Int){width=height*w/max(1,h);camera.setToOrtho(false,width,height);batch.projectionMatrix=camera.combined;shapes.projectionMatrix=camera.combined}
    fun clearInput(){held.clear();taps.clear();movePointer=-1;lookPointer=-1;stick.setZero();lookX=0f;lookY=0f;skipMouse=2}
    fun command():Command {
        val mouse=desktop && Gdx.input.isCursorCatched && skipMouse--<=0
        val c=Command(
            forward=(if(Gdx.input.isKeyPressed(Input.Keys.W))1f else 0f)-(if(Gdx.input.isKeyPressed(Input.Keys.S))1f else 0f)+stick.y,
            strafe=(if(Gdx.input.isKeyPressed(Input.Keys.D))1f else 0f)-(if(Gdx.input.isKeyPressed(Input.Keys.A))1f else 0f)+stick.x,
            lookX=lookX+(if(mouse)Gdx.input.deltaX*.14f else 0f),lookY=lookY+(if(mouse)Gdx.input.deltaY*.14f else 0f),
            fire=held.containsValue("fire") || (mouse && Gdx.input.isButtonPressed(Input.Buttons.LEFT)),
            sprint=held.containsValue("sprint") || Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT),
            aim=held.containsValue("aim") || (mouse && Gdx.input.isButtonPressed(Input.Buttons.RIGHT)),
            reload=taps.contains("reload") || Gdx.input.isKeyJustPressed(Input.Keys.R),interact=taps.contains("interact") || Gdx.input.isKeyJustPressed(Input.Keys.E),
            crouch=taps.contains("crouch") || Gdx.input.isKeyJustPressed(Input.Keys.C),prone=taps.contains("prone") || Gdx.input.isKeyJustPressed(Input.Keys.Z),
            melee=taps.contains("melee") || Gdx.input.isKeyJustPressed(Input.Keys.F))
        lookX=0f;lookY=0f;taps.clear();return c
    }
    private fun button(id:String,text:String,x:Float,y:Float,w:Float,h:Float,primary:Boolean=false){buttons+=Button(id,text,Rectangle(x,y,w,h),primary)}
    private fun text(value:String,x:Float,y:Float,scale:Float=1f,color:Color=cream,center:Boolean=false) {
        Fonts.scale(font,scale);font.color=color
        if(center){glyph.setText(font,value);font.draw(batch,value,x-glyph.width/2f,y)}else font.draw(batch,value,x,y)
    }
    private fun panel(x:Float,y:Float,w:Float,h:Float,color:Color){shapes.color=color;shapes.rect(x,y,w,h)}
    fun draw(s:Simulation,playerName:String,sound:Boolean,hasSave:Boolean,rewardReady:Boolean) {
        buttons.clear();Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA)
        val down=s.vitals.state!=LifeState.ACTIVE;val finished=s.mission.stage==MissionStage.COMPLETE || s.mission.stage==MissionStage.FAILED
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if(mode==Mode.MENU) {
            panel(0f,0f,width*.53f,height,Color(.025f,.07f,.075f,.85f))
            panel(65f,509f,68f,4f,gold)
            button("begin",if(hasSave)"NEW OPERATION" else "BEGIN OPERATION",65f,166f,350f,59f,true)
            if(hasSave)button("continue","CONTINUE OPERATION",65f,96f,350f,54f)
            button("name","OFFICER  /  $playerName",65f,257f,350f,47f)
            button("factory","FACTORY",65f,329f,165f,42f,selectedMission==0)
            button("mall","MALL",246f,329f,169f,42f,selectedMission==1)
            button("sound",if(sound)"SOUND ON" else "SOUND OFF",width-180f,height-68f,140f,38f)
            button("privacy","PRIVACY",width-166f,28f,128f,34f)
        } else if(mode==Mode.PAUSE || down || finished) {
            panel(0f,0f,width,height,Color(.015f,.05f,.055f,.88f))
            if(down && s.vitals.state==LifeState.INCAPACITATED) {
                button("reward",if(s.vitals.rewardPending)"REWARD IN PROGRESS" else if(rewardReady)"REVIVE WITH REWARDED AD" else "RETRY REWARDED AD",width/2-230f,263f,460f,62f,true)
                button("end","END THIS RUN",width/2-230f,181f,460f,50f)
            } else if(down || finished)button("menu","RETURN TO MENU",width/2-220f,230f,440f,60f,true)
            else {
                button("resume","RESUME OPERATION",width/2-220f,327f,440f,60f,true)
                button("sound",if(sound)"SOUND ON" else "SOUND OFF",width/2-220f,260f,211f,46f)
                button("quality", "REFLECTION QUALITY",width/2+9f,260f,211f,46f)
                button("menu","SAVE AND RETURN TO MENU",width/2-220f,188f,440f,50f)
                button("privacy","PRIVACY OPTIONS",width/2-220f,122f,440f,44f)
            }
        } else {
            panel(25f,height-94f,410f,76f,Color(.02f,.06f,.065f,.62f))
            button("pause","II",width-85f,height-69f,53f,46f)
            button("map","MAP",width-162f,height-69f,65f,46f)
            panel(34f,41f,154f,5f,Color(.15f,.22f,.23f,.8f));panel(34f,41f,154f*s.vitals.health/100f,5f,gold)
            if(!desktop) {
                shapes.color=Color(.50f,.67f,.66f,.12f);shapes.circle(anchor.x,anchor.y,73f,48)
                shapes.color=Color(.70f,.80f,.75f,.35f);shapes.circle(anchor.x+stick.x*56f,anchor.y+stick.y*56f,27f,32)
                button("fire","FIRE",width-150f,122f,116f,96f,true)
                button("aim","AIM",width-148f,238f,112f,51f)
                button("reload","RELOAD",width-285f,96f,110f,51f)
                button("crouch","CROUCH",width-395f,30f,97f,46f)
                button("prone","PRONE",width-285f,30f,97f,46f)
                button("melee","MELEE",width-175f,30f,139f,46f)
                button("interact","USE",width-280f,165f,105f,52f)
                button("sprint","SPRINT",37f,260f,103f,48f)
            }
            if(s.vehicle==null) {
                shapes.color=cream;val x=width/2;val y=height/2
                shapes.rect(x-10f,y-1f,6f,2f);shapes.rect(x+4f,y-1f,6f,2f);shapes.rect(x-1f,y-10f,2f,6f);shapes.rect(x-1f,y+4f,2f,6f)
            }
        }
        for(b in buttons)panel(b.rect.x,b.rect.y,b.rect.width,b.rect.height,if(b.primary)Color(.80f,.65f,.40f,.93f)else Color(.055f,.13f,.14f,.85f))
        shapes.end();batch.begin()
        if(mode==Mode.MENU) {
            text("A NAYA NAGAR OPERATION",66f,637f,1.1f,gold)
            text("MONSOON",59f,591f,4.5f);text("B A T T L E",66f,483f,1.6f,muted)
            text("The rain hides everything. Not everyone.",66f,427f,1.12f)
            text("SELECT OPERATION",66f,395f,.85f,muted)
            text("16 connected rooms. One person to bring home.",66f,75f,.92f,muted)
            text("WASD / mouse   E use   R reload   C crouch   Z prone   F melee",66f,30f,.78f,muted)
        } else if(mode==Mode.PAUSE || down || finished) {
            val headline=when{ s.vitals.state==LifeState.DEAD->"END OF THE LINE";down->"INCAPACITATED";s.mission.stage==MissionStage.COMPLETE->"BROUGHT HOME";finished->"OPERATION LOST";else->"TAKE A BREATH" }
            text(headline,width/2,528f,2.8f,cream,true)
            text(s.mission.title,width/2,458f,1.05f,gold,true)
            if(down && s.vitals.state==LifeState.INCAPACITATED) {
                text("Earn the reward to return with 100 health.",width/2,403f,1.15f,cream,true)
                text("${s.vitals.revivesUsed} / 3 revives used. No reward means no revive is consumed.",width/2,373f,.98f,muted,true)
            } else if(s.vitals.state==LifeState.DEAD)text("All three revives have been used. This run is over.",width/2,385f,1.12f,muted,true)
            else if(finished)text(s.mission.objective(),width/2,384f,1.1f,muted,true)
            else text("Your operation is saved locally.",width/2,414f,1.05f,muted,true)
        } else {
            text("0${s.missionNumber+1}  /  ${s.mission.title}",40f,height-35f,.86f,gold)
            text(s.mission.objective(),40f,height-62f,1.04f)
            text("${s.vitals.health}",34f,90f,2.0f);text("HEALTH",96f,72f,.77f,muted)
            text(if(s.reloadTime>0f)"RELOADING" else "${s.magazine}  /  ${s.reserve}",width-348f,if(desktop)71f else 350f,1.6f)
            text(if(s.vehicle!=null)"VEHICLE  /  ${abs(s.vehicle!!.speed*3.6f).roundToInt()} km/h" else s.stance.name,width/2,32f,.83f,muted,true)
            val goal=when(s.mission.stage){MissionStage.ESCORT->s.layout.extraction;MissionStage.HOSTAGE->s.mission.target;else->s.layout.buildings[s.missionNumber].center}
            val delta=goal.cpy().sub(s.player);val bearing=MathUtils.atan2(-delta.x,-delta.z)*MathUtils.radiansToDegrees
            val relative=((bearing-s.yaw+540f)%360f)-180f
            text("${if(relative>30)"<  " else ""}${delta.len().roundToInt()} m${if(relative< -30)"  >" else ""}",width/2,height-44f,.95f,gold,true)
            val prompt=s.context();if(prompt.isNotBlank())text((if(desktop)"[ E ]  "else "USE  /  ")+prompt,width/2,216f,1.05f,cream,true)
        }
        for(b in buttons)text(b.text,b.rect.x+b.rect.width/2,b.rect.y+b.rect.height/2+7f,if(b.text.length>24).94f else 1.08f,if(b.primary)Color(.035f,.095f,.095f,1f)else cream,true)
        if(s.messageTime>0f)text(s.message,width/2,130f,1.02f,cream,true)
        batch.end()
        if(mapOpen && mode==Mode.PLAY && !down && !finished)drawMap(s)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }
    private fun drawMap(s:Simulation) {
        val size=540f;val x=width/2-size/2;val y=90f;val scale=size/256f
        shapes.begin(ShapeRenderer.ShapeType.Filled);panel(0f,0f,width,height,Color(.015f,.04f,.045f,.94f))
        panel(x,y,size,size,Color(.08f,.15f,.15f,1f));panel(x+119.4f*scale,y,17.2f*scale,size,Color(.20f,.29f,.28f,1f))
        panel(x,y+(128f+22f)*scale,size,14f*scale,Color(.20f,.29f,.28f,1f))
        for(b in s.layout.buildings)panel(x+(b.x-27f+128f)*scale,y+(b.front+128f)*scale,54f*scale,(b.back-b.front)*scale,Color(.55f,.49f,.34f,1f))
        shapes.color=gold;shapes.circle(x+(s.player.x+128f)*scale,y+(s.player.z+128f)*scale,5f)
        shapes.color=Color(.5f,.85f,.73f,1f);shapes.circle(x+(s.layout.extraction.x+128f)*scale,y+(s.layout.extraction.z+128f)*scale,5f)
        shapes.end();batch.begin();text("NAYA NAGAR  /  FIELD MAP",width/2,677f,1.5f,cream,true)
        text("SHANTI TEXTILES",x+40f,610f,.87f);text("MEGHDOOT GALLERIA",x+310f,610f,.87f)
        text("Gold: you    Green: extraction    Tap or press M to close",width/2,53f,.95f,muted,true);batch.end()
    }
    private fun point(x:Int,y:Int)=Vector2(x.toFloat()/Gdx.graphics.width*width,(1f-y.toFloat()/Gdx.graphics.height)*height)
    override fun touchDown(screenX:Int,screenY:Int,pointer:Int,button:Int):Boolean {
        if(mapOpen){action("map");return true}
        val p=point(screenX,screenY)
        val b=buttons.lastOrNull{it.rect.contains(p)}
        if(b!=null) {
            if(b.id in listOf("fire","aim","sprint"))held[pointer]=b.id
            else if(b.id in listOf("reload","interact","crouch","prone","melee"))taps+=b.id
            else action(b.id)
            return true
        }
        if(mode!=Mode.PLAY || desktop)return false
        if(p.x<width*.43f && movePointer<0){movePointer=pointer;anchor.set(p);stick.setZero()}
        else if(lookPointer<0){lookPointer=pointer;previous.set(p)}
        return true
    }
    override fun touchDragged(screenX:Int,screenY:Int,pointer:Int):Boolean {
        val p=point(screenX,screenY)
        if(pointer==movePointer){stick.set(p).sub(anchor).scl(1f/65f);if(stick.len2()>1f)stick.nor()}
        if(pointer==lookPointer){lookX+=(p.x-previous.x)*.20f;lookY-=(p.y-previous.y)*.20f;previous.set(p)}
        return true
    }
    override fun touchUp(screenX:Int,screenY:Int,pointer:Int,button:Int):Boolean {
        held.remove(pointer);if(pointer==movePointer){movePointer=-1;stick.setZero()};if(pointer==lookPointer)lookPointer=-1;return true
    }
    override fun touchCancelled(screenX:Int,screenY:Int,pointer:Int,button:Int)=touchUp(screenX,screenY,pointer,button)
    override fun dispose(){batch.dispose();shapes.dispose();font.dispose()}
}
