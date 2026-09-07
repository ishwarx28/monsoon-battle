package com.ishwarx28.monsoonbattle

import com.badlogic.gdx.math.Vector3
import com.ishwarx28.monsoonbattle.gameplay.*
import com.ishwarx28.monsoonbattle.world.WorldLayout
import org.junit.Test
import kotlin.test.*

class SimulationTest {
    private fun isolate(s:Simulation,keep:Int=-1) {
        for(p in s.people)if(p.id!=keep && p!=s.target) {p.vitals.hit(100);s.physics.remove(p.id);p.body=null}
    }
    private fun playerAt(s:Simulation,p:Vector3) {s.player.set(p);s.physics.setActor(s.playerBody,p,s.yaw)}
    @Test fun bothRescuesCanProgressFromEntranceToExtraction() {
        for(building in 0..1) {
            val s=Simulation(WorldLayout(),building,123)
            try {
                isolate(s)
                playerAt(s,Vector3(s.layout.buildings[building].x,.12f,42f));s.update(.016f,Command())
                assertEquals(MissionStage.INTEL,s.mission.stage)
                playerAt(s,s.mission.intel.cpy().add(0f,0f,-1f));s.interact()
                assertEquals(MissionStage.HOSTAGE,s.mission.stage)
                playerAt(s,s.target.position.cpy().add(0f,0f,-1.2f));s.interact()
                assertEquals(MissionStage.ESCORT,s.mission.stage)
                assertEquals(1.8f,s.target.body!!.height)
                playerAt(s,s.layout.extraction);s.target.position.set(s.layout.extraction).add(2f,0f,0f)
                s.physics.setActor(s.target.body!!,s.target.position);s.update(.016f,Command())
                assertEquals(MissionStage.COMPLETE,s.mission.stage)
            }finally{s.dispose()}
        }
    }
    @Test fun actualGunRaysApplyTenDamageAndConsumeOneRound() {
        val s=Simulation(WorldLayout());val victim=s.people.first{it.kind==PersonKind.GOON}
        try {
            isolate(s,victim.id);victim.seesPlayer=true;victim.reaction=999f;victim.yaw=0f;victim.position.set(0f,.12f,-109f);s.physics.setActor(victim.body!!,victim.position)
            playerAt(s,Vector3(0f,.12f,-119f));s.yaw=180f
            repeat(9){s.update(.17f,Command(fire=true))}
            assertEquals(10,victim.vitals.health);assertEquals(21,s.magazine)
            s.update(.17f,Command(fire=true));assertFalse(victim.vitals.alive);assertEquals(20,s.magazine)
            playerAt(s,victim.position.cpy().add(0f,0f,-1f));s.interact()
            assertEquals(150,s.reserve);assertEquals(0,victim.loot)
        }finally{s.dispose()}
    }
    @Test fun emptyAmmunitionFallsBackToPhysicalMelee() {
        val s=Simulation(WorldLayout());val victim=s.people.first{it.kind==PersonKind.MAN}
        try {
            isolate(s,victim.id);victim.position.set(0f,.12f,-110f);s.physics.setActor(victim.body!!,victim.position)
            playerAt(s,Vector3(0f,.12f,-111.5f));s.yaw=180f;s.magazine=0;s.reserve=0
            s.update(.02f,Command(fire=true));assertEquals(90,victim.vitals.health);assertEquals(0,s.magazine)
        }finally{s.dispose()}
    }
    @Test fun enemiesReactAfterADelayAndThenShoot() {
        val s=Simulation(WorldLayout());val goon=s.people.first{it.kind==PersonKind.GOON}
        try {
            isolate(s,goon.id);goon.position.set(0f,.12f,-110f);goon.yaw=0f;s.physics.setActor(goon.body!!,goon.position)
            playerAt(s,Vector3(0f,.12f,-120f));repeat(5){s.update(.05f,Command())}
            assertTrue(goon.seesPlayer);assertEquals(100,s.vitals.health)
            repeat(70){s.update(.05f,Command())};assertTrue(s.vitals.health<100)
        }finally{s.dispose()}
    }
    @Test fun buildingWallsAndFogPreventEnemyAcquisition() {
        val s=Simulation(WorldLayout());val goon=s.people.first{it.kind==PersonKind.GOON}
        try {
            isolate(s,goon.id);goon.position.set(-77.5f,.12f,78f);goon.yaw=90f;s.physics.setActor(goon.body!!,goon.position)
            playerAt(s,Vector3(-94f,.12f,78f));repeat(15){s.update(.05f,Command())}
            assertFalse(goon.seesPlayer);assertEquals(100,s.vitals.health)
            goon.position.set(0f,.12f,0f);s.physics.setActor(goon.body!!,goon.position);playerAt(s,Vector3(0f,.12f,-120f))
            repeat(15){s.update(.05f,Command())};assertFalse(goon.seesPlayer)
        }finally{s.dispose()}
    }
    @Test fun vehicleBoardingDisablesOccupantColliderAndExitRestoresIt() {
        val s=Simulation(WorldLayout())
        try {
            isolate(s);val car=s.vehicles.first();playerAt(s,Vector3(car.position.x-2.8f,.12f,car.position.z))
            s.interact();assertEquals(car,s.vehicle);assertFalse(s.playerBody.enabled)
            repeat(60){s.update(1f/60f,Command())}
            assertTrue(car.position.y in .6f..1f,"Vehicle unstable with occupant: ${car.position}")
            assertTrue(s.eye.y<car.position.y+.8f)
            s.interact();assertNull(s.vehicle);assertTrue(s.playerBody.enabled)
        }finally{s.dispose()}
    }
    @Test fun targetCannotBeReleasedThroughRoomWall() {
        val s=Simulation(WorldLayout())
        try {
            isolate(s);s.mission.stage=MissionStage.HOSTAGE
            val b=s.layout.buildings[s.missionNumber]
            s.target.position.set(b.x+5.1f,.12f,b.front+3.2f);s.physics.setActor(s.target.body!!,s.target.position)
            playerAt(s,Vector3(b.x+3.5f,.12f,b.front+3.2f));s.interact()
            assertEquals(MissionStage.HOSTAGE,s.mission.stage)
        }finally{s.dispose()}
    }
    @Test fun reloadingConservesTotalAmmunition() {
        val s=Simulation(WorldLayout())
        try {
            isolate(s);s.magazine=7;s.reserve=13;s.reload();repeat(120){s.update(1f/60f,Command())}
            assertEquals(20,s.magazine);assertEquals(0,s.reserve)
        }finally{s.dispose()}
    }
}
