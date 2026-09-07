package com.ishwarx28.monsoonbattle.world

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.dynamics.*
import com.badlogic.gdx.utils.Disposable
import kotlin.math.max

/** Bullet is the authoritative collision world for movement, cover, sight and shots. */
class PhysicsWorld(layout: WorldLayout): Disposable {
    companion object { const val SCENERY=1; const val ACTOR=2; const val VEHICLE=4; const val ALL=7 }
    data class Hit(val id:Int,val point:Vector3,val fraction:Float)
    class Handle(val id:Int,val body:btRigidBody,var shape:btConvexShape,val group:Int) {
        val transform=Matrix4()
        var height=1.8f
        var prone=false
        var enabled=true
    }
    private val config:btDefaultCollisionConfiguration
    private val dispatcher:btCollisionDispatcher
    private val broadphase:btDbvtBroadphase
    private val solver:btSequentialImpulseConstraintSolver
    val world:btDiscreteDynamicsWorld
    private val handles=mutableMapOf<Int,Handle>()
    init {
        Bullet.init()
        config=btDefaultCollisionConfiguration(); dispatcher=btCollisionDispatcher(config)
        broadphase=btDbvtBroadphase(); solver=btSequentialImpulseConstraintSolver()
        world=btDiscreteDynamicsWorld(dispatcher,broadphase,solver,config)
        world.gravity=Vector3(0f,-9.81f,0f)
        layout.blocks.filter { it.solid }.forEachIndexed { i,b ->
            add(10000+i,btBoxShape(Vector3(b.w/2,b.h/2,b.d/2)),Vector3(b.x,b.y,b.z),0f,SCENERY)
        }
    }
    private fun add(id:Int,shape:btConvexShape,at:Vector3,mass:Float,group:Int):Handle {
        val inertia=Vector3(); if(mass>0f)shape.calculateLocalInertia(mass,inertia)
        val info=btRigidBody.btRigidBodyConstructionInfo(mass,null,shape,inertia)
        val body=btRigidBody(info); info.dispose(); body.userValue=id
        body.worldTransform=Matrix4().setToTranslation(at)
        body.friction=0.85f; body.restitution=0.04f
        val handle=Handle(id,body,shape,group); handles[id]=handle
        world.addRigidBody(body,group,ALL)
        return handle
    }
    fun actor(id:Int,feet:Vector3,height:Float=1.8f):Handle {
        val h=add(id,btCapsuleShape(0.28f,max(0.01f,height-0.56f)),feet.cpy().add(0f,height/2f,0f),0f,ACTOR)
        h.height=height; h.body.collisionFlags=h.body.collisionFlags or 2
        h.body.activationState=4; return h
    }
    fun car(id:Int,at:Vector3):Handle = add(id,btBoxShape(Vector3(1.02f,0.74f,2.15f)),at,1150f,VEHICLE).also {
        it.body.angularFactor=Vector3(0f,1f,0f); it.body.setDamping(0.18f,0.75f)
        it.body.activationState=4; it.body.ccdMotionThreshold=0.3f; it.body.ccdSweptSphereRadius=0.55f
    }
    fun setActor(h:Handle,feet:Vector3,yaw:Float=0f) {
        h.transform.idt().translate(feet.x,feet.y+h.height/2f,feet.z).rotate(Vector3.Y,yaw)
        if(h.prone)h.transform.rotate(Vector3.X,90f)
        h.body.worldTransform=h.transform; if(h.enabled)world.updateSingleAabb(h.body)
    }
    fun changeStance(h:Handle,feet:Vector3,height:Float,prone:Boolean,yaw:Float):Boolean {
        val candidate:btConvexShape=if(prone)btCapsuleShape(0.22f,1.10f) else btCapsuleShape(0.28f,max(0.01f,height-0.56f))
        val matrix=Matrix4().translate(feet.x,feet.y+height/2f,feet.z).rotate(Vector3.Y,yaw)
        if(prone)matrix.rotate(Vector3.X,90f)
        if(!canOccupy(candidate,matrix,h.id)){candidate.dispose();return false}
        if(h.enabled)world.removeRigidBody(h.body)
        val old=h.shape; h.body.collisionShape=candidate; h.shape=candidate; h.height=height; h.prone=prone
        if(h.enabled)world.addRigidBody(h.body,h.group,ALL); setActor(h,feet,yaw); old.dispose(); return true
    }
    private fun canOccupy(shape:btConvexShape,transform:Matrix4,ignore:Int):Boolean {
        val probe=btCollisionObject(); probe.collisionShape=shape; probe.worldTransform=transform
        var blocked=false
        val callback=object:ContactResultCallback(){
            override fun addSingleResult(cp:btManifoldPoint,a:btCollisionObjectWrapper,partA:Int,indexA:Int,
                b:btCollisionObjectWrapper,partB:Int,indexB:Int):Float {
                val other=if(a.collisionObject==probe)b.collisionObject else a.collisionObject
                if(other.userValue!=ignore && cp.distance < -0.025f)blocked=true
                return 0f
            }
        }
        callback.collisionFilterGroup=ACTOR; callback.collisionFilterMask=ALL
        world.contactTest(probe,callback); callback.dispose(); probe.dispose(); return !blocked
    }
    fun clearForActor(feet:Vector3,ignore:Int=0):Boolean {
        val shape=btCapsuleShape(0.3f,1.2f)
        val clear=canOccupy(shape,Matrix4().setToTranslation(feet.x,feet.y+0.9f,feet.z),ignore)
        shape.dispose(); return clear
    }
    /** Swept capsules prevent tunnelling. Iterative projection lets the character slide along walls. */
    fun move(h:Handle,feet:Vector3,delta:Vector3,yaw:Float=0f) {
        var remain=delta.cpy(); var position=feet.cpy()
        repeat(3) {
            if(remain.len2()<0.000001f)return@repeat
            val from=h.transform.cpy().setTranslation(position.x,position.y+h.height/2f,position.z)
            val to=from.cpy().trn(remain)
            val cb=ClosestConvexResultCallback(from.getTranslation(Vector3()),to.getTranslation(Vector3()))
            cb.collisionFilterGroup=ACTOR; cb.collisionFilterMask=SCENERY or VEHICLE
            world.convexSweepTest(h.shape,from,to,cb,0f)
            if(!cb.hasHit()) { position.add(remain); remain.setZero() }
            else {
                val fraction=(cb.closestHitFraction-0.015f).coerceIn(0f,1f)
                position.mulAdd(remain,fraction)
                val normal=Vector3(); cb.getHitNormalWorld(normal); normal.y=0f; normal.nor()
                remain.scl(1f-fraction); val into=remain.dot(normal)
                if(into<0f)remain.mulAdd(normal,-into)
                else remain.setZero()
            }
            cb.dispose()
        }
        feet.set(position); setActor(h,feet,yaw)
    }
    fun ray(from:Vector3,to:Vector3,ignore:Int=-1):Hit? {
        val cb=AllHitsRayResultCallback(from,to)
        cb.collisionFilterGroup=ALL; cb.collisionFilterMask=ALL
        world.rayTest(from,to,cb)
        var result:Hit?=null
        if(cb.hasHit())for(i in 0 until cb.collisionObjects.size()) {
            val obj=cb.collisionObjects.atConst(i)
            if(obj.userValue==ignore)continue
            val fraction=cb.hitFractions.atConst(i)
            if(result==null || fraction<result.fraction) result=Hit(obj.userValue,from.cpy().lerp(to,fraction),fraction)
        }
        cb.dispose(); return result
    }
    /** Occupants must not physically collide with the vehicle carrying them. */
    fun setActive(h:Handle,active:Boolean) {
        if(h.enabled==active)return
        if(active)world.addRigidBody(h.body,h.group,ALL)else world.removeRigidBody(h.body)
        h.enabled=active
    }
    fun step(dt:Float) { world.stepSimulation(dt,8,1f/120f) }
    fun remove(id:Int) { handles.remove(id)?.let { if(it.enabled)world.removeRigidBody(it.body);it.body.dispose();it.shape.dispose() } }
    override fun dispose() {
        handles.keys.toList().forEach(::remove)
        world.dispose(); solver.dispose(); broadphase.dispose(); dispatcher.dispose(); config.dispose()
    }
}
