package com.ishwarx28.monsoonbattle.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.environment.*
import com.badlogic.gdx.graphics.g3d.utils.*
import com.badlogic.gdx.graphics.glutils.*
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.Disposable
import com.ishwarx28.monsoonbattle.gameplay.*
import com.ishwarx28.monsoonbattle.world.*
import kotlin.math.*
import kotlin.random.Random

class WorldRenderer(val layout:WorldLayout):Disposable {
    val art=Art();val camera=PerspectiveCamera(74f,1280f,720f)
    private val batch=ModelBatch(DefaultShaderProvider(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config().apply{numDirectionalLights=1;numPointLights=6}))
    private val depth=ModelBatch(DepthShaderProvider())
    private val environment=Environment()
    private val shadow=DirectionalShadowLight(1024,1024,70f,70f,1f,140f)
    private val points=List(6){PointLight()}
    private val scenery=ModelCache();private val ground=ModelCache()
    private val road=WetRoad();private val reflection=FrameBuffer(Pixmap.Format.RGBA8888,640,360,true)
    private val reflectedCamera=PerspectiveCamera(74f,1280f,720f);private val reflectedMatrix=Matrix4()
    private var scene:FrameBuffer?=null;private var sceneRegion:TextureRegion?=null
    private val screen=SpriteBatch();private val post=ShaderProgram(SPRITE_VERTEX,POST_FRAGMENT)
    private val lines=ShapeRenderer();private val fog=Color()
    private val humanModels=mutableMapOf<String,Model>();private val humans=mutableMapOf<String,ModelInstance>()
    private val playerModel=ModelInstance(art.human(PersonKind.GOON,0));private val playerShadow=ModelInstance(playerModel.model)
    private val weapon=ModelInstance(art.weapon());private val enemyWeaponModel=art.weapon(false);private val enemyWeapons=mutableMapOf<Int,ModelInstance>();private val carModels=mutableMapOf<Int,ModelInstance>()
    private val ammo=ModelInstance(art.cube);private val marker=ModelInstance(art.cube)
    private val rain=List(650){val r=Random(581+it);Vector3(r.nextFloat()*48f-24f,r.nextFloat()*18f,r.nextFloat()*48f-24f)}
    private val roofs=layout.blocks.filter{it.y-it.h/2f>3f && it.w>3f && it.d>3f}
    private var frame=0;var highQuality=true
    init {
        ShaderProgram.pedantic=false
        require(post.isCompiled){post.log}
        camera.near=0.065f;camera.far=70f
        environment.add(shadow);environment.shadowMap=shadow
        points.forEach{environment.add(it)}
        val objects=ArrayList<ModelInstance>();val floors=ArrayList<ModelInstance>()
        for(b in layout.blocks.filter{it.rendered}){if(b.y+b.h/2f<0.2f)floors+=art.box(b)else objects+=art.box(b)}
        for(s in layout.signs)objects+=art.sign(s)
        for(t in layout.trees) {
            for(i in 0..5) {
                val a=i*60f;val center=t.cpy().add(MathUtils.cosDeg(a)*1.25f,4.6f+(i%3)*0.4f,MathUtils.sinDeg(a)*1.25f)
                objects+=art.instance(art.sphere,center,Vector3(3.8f,2.8f,3.4f),art.material(Surface.GREEN,if(i%2==0)0x315b48 else 0x426b51))
            }
        }
        for(l in layout.lamps) {
            objects+=art.instance(art.cylinder,l.cpy().add(0f,3.1f,0f),Vector3(.13f,6.2f,.13f),art.material(Surface.METAL,0x344749))
            objects+=art.instance(art.cube,l.cpy().add(0.65f,6.15f,0f),Vector3(1.4f,.10f,.10f),art.material(Surface.METAL,0x344749))
            objects+=art.instance(art.sphere,l.cpy().add(1.3f,6.04f,0f),Vector3(.4f,.13f,.30f),art.material(Surface.LIGHT,0xe2bd7b))
        }
        val auto=art.car(0x497e56,true)
        for(p in listOf(Vector3(-6f,.72f,-48f),Vector3(7f,.72f,55f)))objects+=ModelInstance(auto).apply{transform.setToTranslation(p)}
        // Grates, road reflectors, electric wiring and fine rain gutters.
        for(z in -116..116 step 8) {
            for(x in listOf(-8.3f,8.3f))objects+=art.instance(art.cube,Vector3(x,.04f,z.toFloat()),Vector3(.34f,.06f,1.2f),art.material(Surface.METAL,0x253f43))
        }
        for(z in -80..20 step 22)for(i in 0..2)objects+=art.instance(art.cylinder,Vector3(0f,7.5f-i*.13f,z.toFloat()),Vector3(.025f,28f,.025f),art.material(Surface.METAL,0x273c3d)).apply{transform.rotate(Vector3.Z,90f)}
        scenery.begin();objects.forEach{scenery.add(it)};scenery.end()
        ground.begin();floors.forEach{ground.add(it)};ground.end()
        resize(Gdx.graphics.width,Gdx.graphics.height)
    }
    fun resize(width:Int,height:Int) {
        if(width<=0 || height<=0)return
        camera.viewportWidth=width.toFloat();camera.viewportHeight=height.toFloat()
        reflectedCamera.viewportWidth=width.toFloat();reflectedCamera.viewportHeight=height.toFloat()
        scene?.dispose();val scale=min(1f,1440f/width)
        scene=FrameBuffer(Pixmap.Format.RGBA8888,max(1,(width*scale).toInt()),max(1,(height*scale).toInt()),true)
        sceneRegion=TextureRegion(scene!!.colorBufferTexture).apply{flip(false,true)}
        screen.projectionMatrix=Matrix4().setToOrtho2D(0f,0f,width.toFloat(),height.toFloat())
    }
    fun render(s:Simulation,menu:Boolean,aim:Boolean=false) {
        frame++
        val w=s.weather
        fog.set(0.13f+0.22f*w.daylight,0.22f+0.25f*w.daylight,0.25f+0.26f*w.daylight,1f)
        fog.lerp(Color(0.75f,0.86f,0.87f,1f),w.flash*.65f)
        if(menu) {
            camera.position.set(5f+sin(w.time*.025f)*1.5f,2.4f,-59f)
            camera.lookAt(-6f,5f,24f);camera.up.set(Vector3.Y);camera.fieldOfView=69f
        } else {
            camera.position.set(s.eye);camera.direction.set(s.forward);camera.up.set(Vector3.Y);camera.fieldOfView=if(aim && s.vehicle==null)52f else 74f
        }
        camera.far=w.visibility;camera.update()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight,0.23f+w.daylight*.26f,0.30f+w.daylight*.27f,0.32f+w.daylight*.28f,1f))
        environment.set(ColorAttribute(ColorAttribute.Fog,fog))
        shadow.set(0.25f+w.daylight*.60f,0.27f+w.daylight*.53f,0.27f+w.daylight*.43f,-.35f,-.82f,.3f)
        val lightPositions=ArrayList<Vector3>()
        layout.lamps.forEach{lightPositions+=it.cpy().add(1.3f,5.7f,0f)}
        layout.rooms.forEach{lightPositions+=Vector3(it.x,4.5f,it.z)}
        layout.buildings.forEach{b->for(i in 0..7)lightPositions+=Vector3(b.x,5f,b.front+6f+i*8f)}
        val nearest=lightPositions.sortedBy{it.dst2(camera.position)}
        points.forEachIndexed{i,p->p.set(1f,.73f,.42f,nearest[i],if(w.day)24f else 32f)}
        if(frame%2==0 || frame==1) {
            shadow.begin(Vector3(camera.position.x,0f,camera.position.z),camera.direction)
            depth.begin(shadow.camera);depth.render(scenery);renderActors(depth,s,shadow.camera,true);depth.end();shadow.end()
        }
        if(frame%(if(highQuality)2 else 5)==0 || frame==1) {
            reflectedCamera.fieldOfView=camera.fieldOfView;reflectedCamera.near=camera.near;reflectedCamera.far=camera.far
            reflectedCamera.position.set(camera.position.x,-camera.position.y,camera.position.z)
            reflectedCamera.direction.set(camera.direction.x,-camera.direction.y,camera.direction.z)
            reflectedCamera.up.set(camera.up.x,-camera.up.y,camera.up.z);reflectedCamera.update();reflectedMatrix.set(reflectedCamera.combined)
            reflection.begin();clear();batch.begin(reflectedCamera);batch.render(scenery,environment);renderActors(batch,s,reflectedCamera,false);batch.end();reflection.end()
        }
        scene!!.begin();clear()
        batch.begin(camera);batch.render(ground,environment);batch.render(scenery,environment);renderActors(batch,s,camera,false);renderItems(s);batch.end()
        road.render(camera,reflection.colorBufferTexture,reflectedMatrix,w,fog)
        drawRain(s)
        if(!menu && s.vehicle==null && s.vitals.state==LifeState.ACTIVE) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
            val basis=Matrix4().setToLookAt(Vector3.Zero,camera.direction,camera.up).inv()
            val right=Vector3(1f,0f,0f).rot(basis)
            val offset=camera.position.cpy().mulAdd(camera.direction,0.61f-s.recoil*0.05f).mulAdd(right,if(aim)0.02f else 0.20f).add(0f,if(aim)-0.12f else -0.22f,0f)
            weapon.transform.set(basis).setTranslation(offset).rotate(Vector3.X,s.recoil*5f+(if(s.reloadTime>0f)-35f else 0f))
            batch.begin(camera);batch.render(weapon,environment);batch.end()
        }
        scene!!.end();Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        screen.shader=post;screen.begin();post.setUniformf("u_time",w.time);post.setUniformf("u_hurt",s.hurt)
        screen.color=Color.WHITE;screen.draw(sceneRegion,0f,0f,Gdx.graphics.width.toFloat(),Gdx.graphics.height.toFloat());screen.end();screen.shader=null
    }
    private fun clear(){Gdx.gl.glClearColor(fog.r,fog.g,fog.b,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT);Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)}
    private fun renderActors(b:ModelBatch,s:Simulation,cam:Camera,shadowPass:Boolean) {
        for(v in s.vehicles) {
            if(cam.position.dst2(v.position)>cam.far*cam.far)continue
            val model=carModels.getOrPut(v.id){ModelInstance(art.car(listOf(0xa4b5b3,0x48727b,0x89754e,0x465957)[v.id-2000]))}
            model.transform.set(v.body.transform)
            if(shadowPass)b.render(model)else b.render(model,environment)
        }
        for(p in s.people) {
            if(p.boarded || cam.position.dst2(p.position)>cam.far*cam.far)continue
            val model=humans.getOrPut("${p.id}:${p.kind}:${p.style}") {
                val key="${p.kind}:${p.style}"
                ModelInstance(humanModels.getOrPut(key){art.human(p.kind,p.style)})
            }
            pose(model,p.position,p.yaw,p.scale,p.moving,s.weather.time+p.id,!p.vitals.alive,p.crouching,p.kind==PersonKind.GOON && p.seesPlayer)
            if(shadowPass)b.render(model)else b.render(model,environment)
            if(p.kind==PersonKind.GOON && p.vitals.alive) {
                val gun=enemyWeapons.getOrPut(p.id){ModelInstance(enemyWeaponModel)}
                gun.transform.idt().translate(p.position).rotate(Vector3.Y,p.yaw).translate(.15f,1.15f,-.25f).scale(.82f,.82f,.82f)
                if(shadowPass)b.render(gun)else b.render(gun,environment)
            }
        }
        if(s.vehicle==null) {
            val model=if(shadowPass)playerShadow else playerModel
            pose(model,s.player,s.yaw,1f,0f,s.weather.time,false,s.stance==Stance.CROUCH,false)
            if(s.stance==Stance.PRONE)model.transform.rotate(Vector3.X,-85f).scale(1f,.9f,1f)
            for(n in model.nodes)for(part in n.parts)part.enabled=shadowPass || n.id.startsWith("leg")
            if(shadowPass)b.render(model)else b.render(model,environment)
        }
    }
    private fun pose(m:ModelInstance,p:Vector3,yaw:Float,scale:Float,speed:Float,time:Float,dead:Boolean,crouch:Boolean,combat:Boolean) {
        m.transform.idt().translate(p).rotate(Vector3.Y,yaw)
        if(dead)m.transform.translate(0f,.25f,0f).rotate(Vector3.Z,89f)
        m.transform.scale(scale,scale*(if(crouch).65f else 1f),scale)
        val walk=sin(time*9f)*min(speed,2f)*15f
        for(name in listOf("armL","armR","legL","legR")) {
            val node=m.getNode(name)
            val angle=if(name.startsWith("arm") && combat)57f else walk*(if(name.endsWith("L"))1f else -1f)*(if(name.startsWith("arm"))-0.7f else 1f)
            node.rotation.set(Vector3.X,angle)
        }
        m.calculateTransforms()
    }
    private fun renderItems(s:Simulation) {
        for((i,p) in layout.stashes.withIndex())if(i !in s.collected && p.dst2(camera.position)<camera.far*camera.far) {
            ammo.transform.setToTranslation(p.x,.38f,p.z).scale(.7f,.5f,.45f)
            ammo.materials.first().clear();ammo.materials.first().set(art.material(Surface.METAL,0x73865b));batch.render(ammo,environment)
        }
        if(s.mission.stage==MissionStage.INTEL) {
            marker.transform.setToTranslation(s.mission.intel.x,.52f,s.mission.intel.z).scale(.42f,.08f,.58f)
            marker.materials.first().clear();marker.materials.first().set(art.material(Surface.LIGHT,0xc9bc86));batch.render(marker,environment)
        }
    }
    private fun drawRain(s:Simulation) {
        Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false);lines.projectionMatrix=camera.combined;lines.begin(ShapeRenderer.ShapeType.Line)
        lines.color=Color(.65f,.81f,.84f,.22f*s.weather.rain)
        for((i,p) in rain.withIndex()) {
            if(!highQuality && i%2==0)continue
            val x=camera.position.x+p.x;val z=camera.position.z+p.z
            val y=((p.y-s.weather.time*15f)%18f+18f)%18f
            val roof=roofs.filter{abs(x-it.x)<it.w/2f && abs(z-it.z)<it.d/2f}.maxOfOrNull{it.y+it.h/2f}?:0f
            if(y<roof)continue
            lines.line(x,y,z,x-.07f,max(roof,y-.7f),z+.04f)
        }
        lines.color=Color(1f,.76f,.38f,.65f)
        for(t in s.traces)lines.line(t.first,t.second)
        lines.end();Gdx.gl.glDepthMask(true);Gdx.gl.glDisable(GL20.GL_BLEND)
    }
    override fun dispose(){scene?.dispose();reflection.dispose();scenery.dispose();ground.dispose();road.dispose();batch.dispose();depth.dispose();shadow.dispose();screen.dispose();post.dispose();lines.dispose();art.dispose()}
    companion object {
        private val SPRITE_VERTEX="""
            attribute vec4 a_position;attribute vec4 a_color;attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;varying vec4 v_color;varying vec2 v_texCoords;
            void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;gl_Position=u_projTrans*a_position;}
        """.trimIndent()
        private val POST_FRAGMENT="""
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;varying vec2 v_texCoords;uniform sampler2D u_texture;uniform float u_time,u_hurt;
            void main(){vec2 uv=v_texCoords;vec3 c=texture2D(u_texture,uv).rgb;
                float l=dot(c,vec3(.2126,.7152,.0722));c=mix(vec3(l),c,1.06);
                c+=vec3(-.008,.009,.012)*(1.-l)+vec3(.016,.007,-.004)*l;
                float vignette=1.-.31*dot((uv-.5)*1.35,(uv-.5)*1.35);
                c*=vignette;c+=((fract(sin(dot(uv,vec2(12.9898,78.233))+u_time)*43758.5453))-.5)*.009;
                c=mix(c,vec3(.43,.10,.055),clamp(u_hurt*.55*(1.-smoothstep(.0,.7,length(uv-.5))),0.,.25));
                gl_FragColor=vec4(pow(max(c,vec3(0.)),vec3(.96)),1.)*v_color;
            }
        """.trimIndent()
    }
}
