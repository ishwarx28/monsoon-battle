package com.ishwarx28.monsoonbattle

import com.ishwarx28.monsoonbattle.gameplay.*
import org.junit.Test
import kotlin.test.*

class RulesTest {
    @Test fun tenBulletsIncapacitateExactly(){val p=PlayerVitals();repeat(9){p.hit()};assertEquals(10,p.health);assertEquals(LifeState.ACTIVE,p.state);p.hit();assertEquals(0,p.health);assertEquals(LifeState.INCAPACITATED,p.state)}
    @Test fun threeRewardsThenPermanentDeath(){val p=PlayerVitals();repeat(3){repeat(10){p.hit()};assertEquals(LifeState.INCAPACITATED,p.state);val t=p.beginRevive()!!;assertTrue(p.finishRevive(t,true));assertEquals(100,p.health)};repeat(10){p.hit()};assertEquals(LifeState.DEAD,p.state);assertNull(p.beginRevive())}
    @Test fun failedAndDuplicateAdsNeverReward(){val p=PlayerVitals();repeat(10){p.hit()};val t=p.beginRevive()!!;assertNull(p.beginRevive());assertFalse(p.finishRevive(t,false));assertEquals(0,p.revivesUsed);assertFalse(p.finishRevive(t,true));val t2=p.beginRevive()!!;assertTrue(p.finishRevive(t2,true));assertFalse(p.finishRevive(t2,true));assertEquals(1,p.revivesUsed)}
    @Test fun ticketsCannotCrossRuns(){val a=PlayerVitals();val b=PlayerVitals();repeat(10){a.hit();b.hit()};val ta=a.beginRevive()!!;val tb=b.beginRevive()!!;assertFalse(b.finishRevive(ta,true));assertEquals(0,b.health);assertTrue(b.finishRevive(tb,true))}
    @Test fun savedIncapacitationDoesNotRestoreHealth(){val p=PlayerVitals(LifeSnapshot(0,2));assertEquals(LifeState.INCAPACITATED,p.state);val restored=PlayerVitals(p.snapshot());assertEquals(0,restored.health);assertEquals(2,restored.revivesUsed)}
    @Test fun invalidHealthAndDamageAreRejectedOrClamped(){val p=PlayerVitals(LifeSnapshot(-3,88));assertEquals(0,p.health);assertEquals(3,p.revivesUsed);assertEquals(LifeState.DEAD,p.state);assertFailsWith<IllegalArgumentException>{p.hit(-10)}}
    @Test fun everyNpcHasExactHealth(){for(i in 0..20){val p=NpcVitals();repeat(9){p.hit()};assertTrue(p.alive);p.hit();assertFalse(p.alive);assertFalse(p.hit());assertEquals(0,p.health)}}
    @Test fun weatherUsesFiveMinutePhases(){val w=Weather();w.time=0f;assertTrue(w.day);w.step(299f);assertTrue(w.day);w.step(1f);assertFalse(w.day);w.step(299f);assertFalse(w.day);w.step(1f);assertTrue(w.day)}
    @Test fun fogAndWallsBothLimitSight(){assertTrue(Perception.visible(20f,60f,true,1f));assertFalse(Perception.visible(50f,60f,true,1f));assertFalse(Perception.visible(10f,60f,false,1f));assertFalse(Perception.visible(20f,60f,true,-1f));assertTrue(Perception.visible(3f,60f,true,-1f))}
}
