package com.ishwarx28.monsoonbattle.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.ishwarx28.monsoonbattle.gameplay.Weather

/** Half-resolution projective planar reflections, with procedural roughness and rain ripples. */
class WetRoad:Disposable {
    private val shader=ShaderProgram(VERTEX,FRAGMENT)
    private val mesh:Mesh
    init {
        require(shader.isCompiled){shader.log}
        val vertices=ArrayList<Float>();val indices=ArrayList<Short>()
        fun rect(x0:Float,z0:Float,x1:Float,z1:Float) {
            val i=vertices.size/3
            for(p in listOf(floatArrayOf(x0,0.065f,z0),floatArrayOf(x1,0.065f,z0),floatArrayOf(x1,0.065f,z1),floatArrayOf(x0,0.065f,z1)))vertices.addAll(p.toList())
            indices.addAll(listOf(i,i+2,i+1,i,i+3,i+2).map{it.toShort()})
        }
        rect(-8.6f,-127f,8.6f,127f);rect(-124f,22f,-8.6f,36f);rect(8.6f,22f,124f,36f)
        rect(-16f,-105f,16f,-87f);rect(9f,-60f,75f,-56f)
        mesh=Mesh(true,vertices.size/3,indices.size,VertexAttribute.Position())
        mesh.setVertices(vertices.toFloatArray());mesh.setIndices(indices.toShortArray())
    }
    fun render(camera:Camera,reflection:Texture,reflectionMatrix:Matrix4,weather:Weather,fog:Color) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        reflection.bind(0);shader.bind();shader.setUniformMatrix("u_projTrans",camera.combined)
        shader.setUniformMatrix("u_reflect",reflectionMatrix);shader.setUniformi("u_reflection",0)
        shader.setUniformf("u_camera",camera.position);shader.setUniformf("u_time",weather.time)
        shader.setUniformf("u_fog",fog);shader.setUniformf("u_far",camera.far)
        shader.setUniformf("u_day",weather.daylight);shader.setUniformf("u_rain",weather.rain)
        mesh.render(shader,GL20.GL_TRIANGLES)
    }
    override fun dispose(){mesh.dispose();shader.dispose()}
    companion object {
        private val VERTEX="""
            attribute vec3 a_position;
            uniform mat4 u_projTrans; uniform mat4 u_reflect;
            varying vec3 v_world; varying vec4 v_reflect;
            void main(){v_world=a_position;v_reflect=u_reflect*vec4(a_position,1.0);gl_Position=u_projTrans*vec4(a_position,1.0);}
        """.trimIndent()
        private val FRAGMENT="""
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec3 v_world; varying vec4 v_reflect;
            uniform sampler2D u_reflection;
            uniform vec3 u_camera; uniform vec4 u_fog;
            uniform float u_time,u_far,u_day,u_rain;
            float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
            float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+1.),f.x),f.y);}
            void main(){
                vec2 p=v_world.xz;
                float puddle=smoothstep(0.36,0.70,noise(p*.22)+noise(p*.61)*.18);
                float ripple=sin(p.x*16.+u_time*7.)*sin(p.y*14.-u_time*5.);
                vec2 uv=v_reflect.xy/max(v_reflect.w,.01)*.5+.5;
                uv+=vec2(ripple,sin(p.x*11.+p.y*13.+u_time*8.))*.0016*u_rain;
                vec3 reflectColor=texture2D(u_reflection,clamp(uv,.003,.997)).rgb*.4;
                reflectColor+=texture2D(u_reflection,clamp(uv+vec2(.0025,0.),.003,.997)).rgb*.15;
                reflectColor+=texture2D(u_reflection,clamp(uv-vec2(.0025,0.),.003,.997)).rgb*.15;
                reflectColor+=texture2D(u_reflection,clamp(uv+vec2(0.,.003),.003,.997)).rgb*.15;
                reflectColor+=texture2D(u_reflection,clamp(uv-vec2(0.,.003),.003,.997)).rgb*.15;
                float rough=.95+noise(p*3.)*.05;
                vec3 asphalt=vec3(.075,.115,.126)*rough*(.6+u_day*.9);
                float stripe=(1.-smoothstep(.075,.11,abs(p.x)))*step(.38,fract(p.y*.12));
                float edge=(1.-smoothstep(.07,.13,abs(abs(p.x)-7.5)))*step(abs(p.x),8.6);
                asphalt=mix(asphalt,vec3(.68,.59,.38)*(.3+.7*u_day),max(stripe,edge*.65)*.76);
                float fresnel=.10+.55*pow(1.-abs(normalize(u_camera-v_world).y),3.);
                vec3 c=mix(asphalt,reflectColor,clamp(fresnel*(.4+puddle*.8),0.,.75));
                c+=vec3(.005,.008,.009)*ripple*puddle*u_rain;
                float fog=clamp(dot(u_camera-v_world,u_camera-v_world)*1.1881/(u_far*u_far),0.,1.);
                gl_FragColor=vec4(mix(c,u_fog.rgb,fog),1.);
            }
        """.trimIndent()
    }
}
