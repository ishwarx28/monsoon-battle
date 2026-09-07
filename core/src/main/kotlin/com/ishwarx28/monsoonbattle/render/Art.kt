package com.ishwarx28.monsoonbattle.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.utils.*
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.Disposable
import com.ishwarx28.monsoonbattle.gameplay.PersonKind
import com.ishwarx28.monsoonbattle.world.*
import kotlin.math.min
import kotlin.random.Random

/** Original procedural art. No downloaded character, music or texture licenses are required. */
class Art:Disposable {
    private val usage=VertexAttributes.Usage.Position.toLong() or VertexAttributes.Usage.Normal.toLong() or VertexAttributes.Usage.TextureCoordinates.toLong()
    private val models=mutableListOf<Model>(); private val textures=mutableListOf<Texture>();private val signs=mutableListOf<FrameBuffer>()
    private val materials=mutableMapOf<String,Material>()
    val cube=keep(ModelBuilder().createBox(1f,1f,1f,Material(ColorAttribute.createDiffuse(Color.WHITE)),usage))
    val sphere=keep(ModelBuilder().createSphere(1f,1f,1f,16,12,Material(ColorAttribute.createDiffuse(Color.WHITE)),usage))
    val cylinder=keep(ModelBuilder().createCylinder(1f,1f,1f,16,Material(ColorAttribute.createDiffuse(Color.WHITE)),usage))
    val pixel:Texture
    init {val p=Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(Color.WHITE);p.fill();pixel=Texture(p);p.dispose();textures+=pixel}
    fun color(hex:Int,alpha:Float=1f)=Color(((hex shr 16)and 255)/255f,((hex shr 8)and 255)/255f,(hex and 255)/255f,alpha)
    private fun keep(model:Model)=model.also{models+=it}
    fun material(surface:Surface,hex:Int):Material=materials.getOrPut("$surface:$hex") {
        val c=color(hex)
        val m=Material(ColorAttribute.createDiffuse(c))
        when(surface) {
            Surface.METAL,Surface.ASPHALT,Surface.GLASS->{m.set(ColorAttribute.createSpecular(color(0x7c9a9e)));m.set(FloatAttribute.createShininess(if(surface==Surface.GLASS)70f else 28f))}
            Surface.LIGHT->{m.set(ColorAttribute.createEmissive(c));m.set(ColorAttribute.createDiffuse(c))}
            else->{m.set(ColorAttribute.createSpecular(color(0x202725)));m.set(FloatAttribute.createShininess(6f))}
        }
        if(surface==Surface.CONCRETE || surface==Surface.BRICK || surface==Surface.WOOD || surface==Surface.PAINT) {
            val pix=Pixmap(64,64,Pixmap.Format.RGBA8888);val random=Random(hex)
            for(y in 0..63)for(x in 0..63){var v=0.82f+random.nextFloat()*0.18f
                if(surface==Surface.BRICK && (y%12==0 || (x+(y/12%2)*16)%32==0))v*=0.7f
                if(surface==Surface.WOOD && x%9==0)v*=0.8f
                pix.setColor(v,v,v,1f);pix.drawPixel(x,y)
            }
            val texture=Texture(pix,true);pix.dispose();texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear)
            texture.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);textures+=texture
            m.set(TextureAttribute.createDiffuse(texture))
        }
        m
    }
    fun instance(model:Model,at:Vector3,scale:Vector3,material:Material?=null):ModelInstance=ModelInstance(model).also {
        it.transform.setToTranslation(at).scale(scale.x,scale.y,scale.z)
        if(material!=null)it.materials.forEach {old->old.clear();old.set(material)}
    }
    fun box(b:Block):ModelInstance {
        val mat=material(b.surface,b.tint).copy()
        (mat.get(TextureAttribute.Diffuse) as? TextureAttribute)?.let {
            it.scaleU=kotlin.math.max(1f,kotlin.math.max(b.w,b.d)*.8f)
            it.scaleV=kotlin.math.max(1f,b.h*.8f)
        }
        return instance(cube,Vector3(b.x,b.y,b.z),Vector3(b.w,b.h,b.d),mat)
    }
    fun sign(s:Sign):ModelInstance {
        val fb=FrameBuffer(Pixmap.Format.RGBA8888,768,112,false);signs+=fb
        val batch=SpriteBatch();val font=Fonts.create();val glyph=GlyphLayout()
        fb.begin();Gdx.gl.glClearColor(0.035f,0.085f,0.095f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix=Matrix4().setToOrtho2D(0f,0f,768f,112f)
        glyph.setText(font,s.text);font.data.setScale(min(58f/font.data.capHeight,720f/glyph.width))
        glyph.setText(font,s.text);batch.begin();batch.color=color(s.color);batch.draw(pixel,15f,9f,738f,3f)
        font.color=color(s.color);font.draw(batch,s.text,(768f-glyph.width)/2f,81f)
        Fonts.scale(font,0.85f);font.color=color(0x899e99);font.draw(batch,"NAYA NAGAR     /     MONSOON DISTRICT",23f,24f)
        batch.end();fb.end();batch.dispose();font.dispose()
        fb.colorBufferTexture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear)
        val mat=Material(TextureAttribute.createDiffuse(fb.colorBufferTexture).apply{scaleV=-1f;offsetV=1f},ColorAttribute.createDiffuse(Color.WHITE),ColorAttribute.createEmissive(color(0x22221b)),IntAttribute.createCullFace(GL20.GL_NONE))
        val model=keep(ModelBuilder().createRect(0.5f,-0.5f,0f,-0.5f,-0.5f,0f,-0.5f,0.5f,0f,0.5f,0.5f,0f,0f,0f,-1f,mat,usage))
        return ModelInstance(model).also{it.transform.setToTranslation(s.x,s.y,s.z).rotate(Vector3.Y,s.yaw).scale(s.width,s.width*112f/768f,1f)}
    }
    private fun part(mb:ModelBuilder,name:String,mat:Material)=mb.part(name,GL20.GL_TRIANGLES,usage,mat)
    private fun ellipsoid(p:MeshPartBuilder,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float) {
        p.setVertexTransform(Matrix4().translate(x,y,z).scale(w,h,d));p.sphere(1f,1f,1f,16,12)
    }
    private fun box(p:MeshPartBuilder,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float) {
        p.setVertexTransform(Matrix4().translate(x,y,z).scale(w,h,d));p.box(1f,1f,1f)
    }
    fun human(kind:PersonKind,style:Int):Model {
        val mb=ModelBuilder();mb.begin()
        val skin=material(Surface.PAINT,listOf(0x9f6b49,0xc18b63,0x82583f,0xae7855)[style%4])
        val shirt=material(Surface.PAINT,if(kind==PersonKind.GOON)listOf(0x384943,0x4a4e46,0x5b5144,0x345054)[style%4]
            else listOf(0x9a6255,0xb7a978,0x597b83,0x997d9b)[style%4])
        val pants=material(Surface.PAINT,if(kind==PersonKind.GOON)0x303b3a else 0x3e555f)
        val hair=material(Surface.PAINT,0x231f1c);val boots=material(Surface.PAINT,0x282c29)
        mb.node().id="torso"
        val torso=part(mb,"clothing",shirt)
        ellipsoid(torso,0f,1.12f,0f,0.49f,0.65f,0.30f)
        box(torso,0f,0.86f,0f,0.43f,0.16f,0.26f)
        if(kind==PersonKind.WOMAN || kind==PersonKind.GIRL || (kind==PersonKind.HOSTAGE && style==0))ellipsoid(torso,0f,0.83f,0f,0.48f,0.65f,0.31f)
        val belt=part(mb,"belt",boots);box(belt,0f,0.82f,0f,0.44f,0.055f,0.285f)
        if(kind==PersonKind.GOON){val vest=part(mb,"vest",material(Surface.PAINT,0x283831));box(vest,0f,1.12f,-0.04f,0.43f,0.43f,0.29f)
            for(x in listOf(-0.13f,0f,0.13f))box(vest,x,0.99f,-0.20f,0.095f,0.14f,0.06f)}
        mb.node().id="head"
        val head=part(mb,"skin",skin)
        ellipsoid(head,0f,1.58f,-0.012f,0.32f,0.43f,0.31f)
        ellipsoid(head,0f,1.39f,0f,0.15f,0.20f,0.15f)
        ellipsoid(head,0f,1.56f,-0.167f,0.055f,0.11f,0.10f)
        for(x in listOf(-0.168f,0.168f))ellipsoid(head,x,1.58f,0f,0.04f,0.10f,0.065f)
        val white=part(mb,"eyes",material(Surface.PAINT,0xd6cfbb))
        for(x in listOf(-0.067f,0.067f))ellipsoid(white,x,1.624f,-0.154f,0.054f,0.025f,0.025f)
        val dark=part(mb,"iris",hair)
        for(x in listOf(-0.067f,0.067f)) {
            ellipsoid(dark,x,1.623f,-0.168f,0.019f,0.022f,0.01f)
            box(dark,x,1.655f,-0.15f,0.064f,0.012f,0.025f)
        }
        val lip=part(mb,"mouth",material(Surface.PAINT,0x684438));box(lip,0f,1.488f,-0.15f,0.077f,0.014f,0.021f)
        val hairPart=part(mb,"hair",hair);ellipsoid(hairPart,0f,1.755f,0.025f,0.335f,0.16f,0.325f)
        if(kind==PersonKind.WOMAN || kind==PersonKind.GIRL || (kind==PersonKind.HOSTAGE && style==0)) {
            ellipsoid(hairPart,0f,1.65f,0.12f,0.33f,0.31f,0.18f);ellipsoid(hairPart,0f,1.49f,0.20f,0.13f,0.23f,0.13f)
        }
        for(side in listOf(-1,1)) {
            val armNode=mb.node();armNode.id=if(side<0)"armL" else "armR";armNode.translation.set(side*0.30f,1.28f,0f)
            val sleeve=part(mb,"sleeve$side",shirt);ellipsoid(sleeve,0f,-0.17f,0f,0.17f,0.40f,0.18f)
            val arm=part(mb,"forearm$side",skin);ellipsoid(arm,0f,-0.47f,-0.01f,0.12f,0.32f,0.12f)
            ellipsoid(arm,0f,-0.65f,-0.02f,0.105f,0.15f,0.075f)
            for(f in -1..1)box(arm,f*0.023f,-0.72f,-0.02f,0.019f,0.07f,0.027f)
            val legNode=mb.node();legNode.id=if(side<0)"legL" else "legR";legNode.translation.set(side*0.12f,0.77f,0f)
            val leg=part(mb,"leg$side",pants);ellipsoid(leg,0f,-0.18f,0f,0.195f,0.45f,0.21f);ellipsoid(leg,0f,-0.51f,0.015f,0.16f,0.38f,0.18f)
            val shoe=part(mb,"shoe$side",boots);box(shoe,0f,-0.70f,-0.07f,0.18f,0.13f,0.32f)
        }
        return keep(mb.end())
    }
    fun weapon(hands:Boolean=true):Model {
        val mb=ModelBuilder();mb.begin();val metal=part(mb,"gun",material(Surface.METAL,0x27343a))
        box(metal,0f,0f,-0.10f,0.11f,0.135f,0.48f);box(metal,0f,-0.16f,-0.17f,0.075f,0.24f,0.14f)
        box(metal,0f,-0.13f,0.10f,0.07f,0.20f,0.10f);box(metal,0f,0.015f,0.26f,0.105f,0.105f,0.31f)
        metal.setVertexTransform(Matrix4().translate(0f,0.01f,-0.52f).rotate(Vector3.X,90f));metal.cylinder(0.035f,0.48f,0.035f,16)
        for(i in 0..11)box(metal,0f,0.085f,-0.32f+i*0.03f,0.12f,0.019f,0.012f)
        for(x in listOf(-0.03f,0.03f))box(metal,x,0.12f,-0.25f,0.01f,0.06f,0.022f)
        if(hands) {
        val skin=part(mb,"hands",material(Surface.PAINT,0xae7855));ellipsoid(skin,0.045f,-0.135f,0.12f,0.115f,0.16f,0.13f)
        ellipsoid(skin,-0.025f,-0.09f,-0.31f,0.12f,0.09f,0.18f)
        val sleeves=part(mb,"sleeves",material(Surface.PAINT,0x425952));ellipsoid(sleeves,0.15f,-0.25f,0.29f,0.16f,0.18f,0.48f)
        ellipsoid(sleeves,-0.24f,-0.24f,0.13f,0.16f,0.16f,0.62f)
        }
        return keep(mb.end())
    }
    fun car(hex:Int,auto:Boolean=false):Model {
        val mb=ModelBuilder();mb.begin()
        // Finish each mesh part before opening the next: libGDX reuses MeshPartBuilder instances.
        val paint=part(mb,"paint",material(Surface.METAL,hex))
        if(!auto) {
            box(paint,0f,0f,0f,1.94f,0.50f,4.1f);box(paint,0f,.83f,.15f,1.75f,.12f,2.1f)
            for(x in listOf(-.88f,.88f))for(z in listOf(-.86f,1.16f))box(paint,x,.5f,z,.10f,.73f,.09f)
            for(x in listOf(-.95f,.95f))box(paint,x,.31f,.15f,.10f,.20f,2.1f)
        }else {box(paint,0f,-.03f,0f,1.48f,.55f,2.6f);box(paint,0f,.65f,0f,1.6f,.14f,2.5f)}
        val trim=part(mb,"trim",material(Surface.METAL,0x28383b))
        if(!auto) {
            box(trim,0f,.30f,-.70f,1.65f,.16f,.35f)
            for(x in listOf(-.46f,.46f)){box(trim,x,.10f,.48f,.63f,.17f,.66f);box(trim,x,.43f,.85f,.63f,.72f,.17f)}
            for(z in listOf(-2.07f,2.07f))box(trim,0f,-.10f,z,1.94f,.18f,.08f)
            for(x in listOf(-1f,1f))for(z in listOf(-1.34f,1.34f)) {
                trim.setVertexTransform(Matrix4().translate(x,-.39f,z).rotate(Vector3.Z,90f));trim.cylinder(.64f,.20f,.64f,20)
            }
        }else {
            for(x in listOf(-.71f,.71f))for(z in listOf(-1.1f,1.1f))box(trim,x,.35f,z,.06f,.8f,.06f)
            box(trim,0f,.38f,.65f,1.2f,.15f,.70f);box(trim,0f,0f,-.8f,.65f,.35f,.6f)
            for(v in listOf(Vector3(-.72f,-.36f,.7f),Vector3(.72f,-.36f,.7f),Vector3(0f,-.36f,-1f))) {
                trim.setVertexTransform(Matrix4().translate(v).rotate(Vector3.Z,90f));trim.cylinder(.55f,.16f,.55f,16)
            }
        }
        if(auto) {val yellow=part(mb,"yellow",material(Surface.PAINT,0xd2aa4e));box(yellow,0f,.08f,-1.31f,1.4f,.35f,.08f)}
        else {
            val lights=part(mb,"lights",material(Surface.LIGHT,0xe4dbb8))
            for(x in listOf(-.66f,.66f))box(lights,x,.04f,-2.062f,.42f,.17f,.025f)
            val tail=part(mb,"tail",material(Surface.LIGHT,0xa95536))
            for(x in listOf(-.72f,.72f))box(tail,x,.05f,2.062f,.23f,.17f,.025f)
        }
        return keep(mb.end())
    }
    override fun dispose(){models.forEach{it.dispose()};signs.forEach{it.dispose()};textures.forEach{it.dispose()}}
}
