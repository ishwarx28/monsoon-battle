package com.ishwarx28.monsoonbattle

import com.badlogic.gdx.math.Vector3
import com.ishwarx28.monsoonbattle.world.*
import com.ishwarx28.monsoonbattle.gameplay.*
import org.junit.Test
import kotlin.test.*

class WorldTest {
    @Test fun allThirtyTwoRoomsAreReachableFromTheStreet(){val l=WorldLayout();val nav=Navigation(l);assertEquals(32,l.rooms.size);for(r in l.rooms)assertTrue(nav.path(l.spawn,Vector3(r.x,.12f,r.z-1f)).isNotEmpty(),"No route to room ${r.id}")}
    @Test fun everyObjectiveHasAValidRoute(){val l=WorldLayout();val nav=Navigation(l);for(building in 0..1)for(seed in 0..15){val m=Mission(building,seed,l);assertTrue(nav.path(l.spawn,m.intel).isNotEmpty());assertTrue(nav.path(m.intel,m.target).isNotEmpty());assertTrue(nav.path(m.target,l.extraction).isNotEmpty())}}
    @Test fun bulletHitsNearestSolidAndCannotSeeThroughWalls(){val l=WorldLayout();val p=PhysicsWorld(l);try {
        val wall=l.blocks.first{it.solid && it.x==25f && it.h>8f && it.z== -68f}
        val start=Vector3(wall.x,1.5f,wall.z-14f);val end=Vector3(wall.x,1.5f,wall.z+14f)
        val hit=p.ray(start,end);assertNotNull(hit);assertTrue(hit.id>=10000);assertTrue(hit.point.z<wall.z)
    }finally{p.dispose()}}
    @Test fun sweptActorCannotWalkThroughAFactoryWall(){val l=WorldLayout();val p=PhysicsWorld(l);try {
        val at=Vector3(-93f,.12f,80f);val actor=p.actor(0,at);p.setActor(actor,at)
        p.move(actor,at,Vector3(14f,0f,0f));assertTrue(at.x< -89f,"Actor tunneled through the exterior wall: $at")
    }finally{p.dispose()}}
    @Test fun carsBlockProjectilesBeforeTheOccupant(){val l=WorldLayout();val p=PhysicsWorld(l);try {
        val car=p.car(2000,Vector3(0f,.8f,-110f));val actor=p.actor(0,Vector3(0f,.12f,-110f));p.setActor(actor,Vector3(0f,.12f,-110f))
        assertEquals(2000,p.ray(Vector3(0f,1f,-120f),Vector3(0f,1f,-110f))?.id)
    }finally{p.dispose()}}
    @Test fun stanceChangesTheActualBulletCollider(){val l=WorldLayout();val p=PhysicsWorld(l);try {
        val at=Vector3(0f,.12f,-110f);val a=p.actor(0,at);p.setActor(a,at)
        assertEquals(0,p.ray(Vector3(-3f,1.5f,-110f),Vector3(3f,1.5f,-110f))?.id)
        assertTrue(p.changeStance(a,at,.48f,true,0f))
        assertNull(p.ray(Vector3(-3f,1.5f,-110f),Vector3(3f,1.5f,-110f)))
    }finally{p.dispose()}}
}
