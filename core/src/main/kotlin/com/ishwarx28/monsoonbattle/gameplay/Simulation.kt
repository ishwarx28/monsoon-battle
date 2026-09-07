package com.ishwarx28.monsoonbattle.gameplay

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.ishwarx28.monsoonbattle.world.*
import kotlin.math.*
import kotlin.random.Random

enum class Stance(val height:Float,val eye:Float,val speed:Float) { STAND(1.8f,1.65f,4.6f), CROUCH(1.1f,0.94f,2.5f), PRONE(0.48f,0.35f,1.1f) }
enum class MissionStage { LOCATE, INTEL, HOSTAGE, ESCORT, COMPLETE, FAILED }
enum class PersonKind { GOON, MAN, WOMAN, BOY, GIRL, HOSTAGE }
class Person(val id:Int,val kind:PersonKind,val position:Vector3,val home:Int=-1,val style:Int=0) {
    var vitals=NpcVitals()
    val scale get()=if(kind==PersonKind.BOY || kind==PersonKind.GIRL)0.72f else 1f
    var yaw=180f; var moving=0f; var body:PhysicsWorld.Handle?=null
    var path:List<Vector3> = emptyList(); var pathIndex=0; var replan=0f
    var awareness=0f; var reaction=0f; var fireCooldown=0f; var sightTimer=0f
    var seesPlayer=false; var lastSeen=Vector3(); var memory=0f; var loot=30
    var fleeing=0f; var boarded=false; var crouching=false; var coverGoal:Vector3?=null
}
class Vehicle(val id:Int,val body:PhysicsWorld.Handle) { var speed=0f; var yaw=0f; val position=Vector3(); var steering=0f }
class Weather {
    var time=100f
    val day get()=(time%600f)<CombatRules.DAY_SECONDS
    val daylight get()=if(day) (0.38f+0.62f*sin(Math.PI*(time%300f)/300f)).toFloat() else 0.06f
    val rain get()=(0.58f+0.30f*sin(time*0.019f)+0.12f*sin(time*0.047f)).coerceIn(0.15f,1f)
    val visibility get()=78f-rain*30f
    val flash get()=if(rain>0.72f && time%43f<0.17f)1f-time%43f/0.17f else 0f
    fun step(dt:Float) {time=(time+dt)%36000f}
}
object Perception {
    /** Conservative relative to fully opaque render fog. Fog does not stop projectiles. */
    fun visible(distance:Float,fogFar:Float,lineClear:Boolean,facingDot:Float)=lineClear && distance<fogFar*0.8f && (distance<6f || facingDot> -0.18f)
}
class Mission(val building:Int,seed:Int,layout:WorldLayout) {
    var stage=MissionStage.LOCATE
    val rooms=layout.rooms.filter{it.building==building}
    val intelRoom=rooms[Random(seed).nextInt(2,7)]
    val targetRoom=rooms[Random(seed+99).nextInt(11,16)]
    val intel=Vector3(intelRoom.x-sign(intelRoom.x-layout.buildings[building].x)*3.5f,0.12f,intelRoom.z-2f)
    val target=Vector3(targetRoom.x-sign(targetRoom.x-layout.buildings[building].x)*2f,0.12f,targetRoom.z-1f)
    val title get()=if(building==0)"THE LAST SHIFT" else "AFTER THE SHUTTERS"
    val targetName get()=if(building==0)"Meera" else "Arjun"
    fun objective():String=when(stage) {
        MissionStage.LOCATE->"Locate "+if(building==0)"Shanti Textiles" else "Meghdoot Galleria"
        MissionStage.INTEL->"Search the rooms for the security log"
        MissionStage.HOSTAGE->"Find $targetName in room %02d".format(targetRoom.id+1)
        MissionStage.ESCORT->"Escort $targetName to the relief checkpoint"
        MissionStage.COMPLETE->"Civilian safely extracted"
        MissionStage.FAILED->"The rescue target was lost"
    }
}

data class Command(var forward:Float=0f,var strafe:Float=0f,var lookX:Float=0f,var lookY:Float=0f,
    var fire:Boolean=false,var sprint:Boolean=false,var aim:Boolean=false,var reload:Boolean=false,
    var interact:Boolean=false,var crouch:Boolean=false,var prone:Boolean=false,var melee:Boolean=false)

/** No renderer dependencies: this simulation is also exercised by native Bullet tests. */
class Simulation(val layout:WorldLayout,val missionNumber:Int=0,val seed:Int=2817) {
    val physics=PhysicsWorld(layout); val nav=Navigation(layout); val weather=Weather()
    val player=layout.spawn.cpy(); val playerBody=physics.actor(0,player)
    var vitals=PlayerVitals(); var stance=Stance.STAND; var yaw=180f; var pitch=0f
    var magazine=30; var reserve=120; var reloadTime=0f; var fireTime=0f; var recoil=0f; var hurt=0f
    var vehicle:Vehicle?=null
    val vehicles=layout.carSpawns.mapIndexed {i,p->Vehicle(2000+i,physics.car(2000+i,p.cpy())) }
    val people=mutableListOf<Person>(); val collected=mutableSetOf<Int>()
    var mission=Mission(missionNumber,seed,layout)
    val target:Person
    val rng=Random(seed)
    var onSound:(String,Float)->Unit={_,_->}; var onDialogue:(String,String)->Unit={_,_->}
    var onCheckpoint:()->Unit={}; var message=""; var messageTime=0f; var shots=0
    val traces=mutableListOf<Pair<Vector3,Vector3>>(); var traceTime=0f
    val eye get()=vehicle?.let { Vector3(-.40f,.58f,-.22f).rot(it.body.transform).add(it.position) } ?: player.cpy().add(0f,stance.eye,0f)
    val forward get()=Vector3(-MathUtils.sinDeg(yaw)*MathUtils.cosDeg(pitch),MathUtils.sinDeg(pitch),-MathUtils.cosDeg(yaw)*MathUtils.cosDeg(pitch)).nor()
    init {
        var id=1
        layout.rooms.forEach {r ->
            if(r.index%2==0 || r.building==missionNumber) {
                people+=Person(id++,PersonKind.GOON,Vector3(r.x,0.12f,r.z-1.8f),r.building,r.id%4)
            }
        }
        for(i in 0..11) {
            val kinds=listOf(PersonKind.MAN,PersonKind.WOMAN,PersonKind.BOY,PersonKind.GIRL)
            people+=Person(id++,kinds[i%4],Vector3(70f+(i%3)*3f,0.12f,-83f+(i/3)*12f),-1,i%4)
        }
        target=Person(1000,PersonKind.HOSTAGE,mission.target.cpy(),missionNumber,missionNumber); target.crouching=true
        people+=target
        people.forEach {
            it.body=physics.actor(it.id,it.position,if(it.crouching)1.1f else 1.8f*it.scale)
            it.yaw=if(it.kind==PersonKind.GOON && it.id%3!=0)0f else 180f
            physics.setActor(it.body!!,it.position,it.yaw)
        }
        vehicles.forEach {it.body.body.getWorldTransform(it.body.transform);it.body.transform.getTranslation(it.position)}
    }
    fun say(hindi:String,english:String) {message=english;messageTime=5f;onDialogue(hindi,english)}
    fun notice(text:String) {message=text;messageTime=3f}
    fun update(dt:Float,c:Command) {
        weather.step(dt); messageTime-=dt; recoil=max(0f,recoil-dt*5f);hurt=max(0f,hurt-dt)
        traceTime-=dt;if(traceTime<=0f)traces.clear()
        if(vitals.state!=LifeState.ACTIVE || mission.stage==MissionStage.COMPLETE || mission.stage==MissionStage.FAILED)return
        fireTime-=dt
        if(reloadTime>0f){reloadTime-=dt;if(reloadTime<=0f){val n=min(30-magazine,reserve);magazine+=n;reserve-=n;onCheckpoint()}}
        yaw=(yaw-c.lookX)%360f; pitch=(pitch-c.lookY).coerceIn(-79f,79f)
        if(c.crouch)changeStance(if(stance==Stance.CROUCH)Stance.STAND else Stance.CROUCH)
        if(c.prone)changeStance(if(stance==Stance.PRONE)Stance.STAND else Stance.PRONE)
        val car=vehicle
        if(car==null) {
            val f=Vector3(-MathUtils.sinDeg(yaw),0f,-MathUtils.cosDeg(yaw))
            val right=Vector3(-f.z,0f,f.x)
            val delta=f.scl(c.forward).mulAdd(right,c.strafe)
            if(delta.len2()>1f)delta.nor()
            val speed=stance.speed*(if(c.sprint && stance==Stance.STAND && !c.aim)1.5f else 1f)*(if(c.aim)0.65f else 1f)
            physics.move(playerBody,player,delta.scl(speed*dt),yaw)
            separatePlayer()
        } else drive(car,c,dt)
        if(c.reload)reload()
        if(c.fire && car==null && fireTime<=0f && reloadTime<=0f){if(magazine>0)shoot() else if(reserve>0)reload() else melee()}
        if(c.melee && car==null && fireTime<=0f)melee()
        if(c.interact)interact()
        people.forEach {updatePerson(it,dt)}
        physics.step(dt)
        vehicles.forEach {
            it.body.body.getWorldTransform(it.body.transform);it.body.transform.getTranslation(it.position)
            val f=Vector3(0f,0f,-1f).rot(it.body.transform);it.yaw=MathUtils.atan2(-f.x,-f.z)*MathUtils.radiansToDegrees
        }
        vehicle?.let {carNow ->
            player.set(carNow.position.x,carNow.position.y+0.12f,carNow.position.z)
            physics.setActor(playerBody,player,yaw)
            if(target.boarded){target.position.set(carNow.position);target.body?.let{physics.setActor(it,target.position)}}
        }
        if(mission.stage==MissionStage.LOCATE && layout.buildings[mission.building].contains(player.x,player.z)) {
            mission.stage=MissionStage.INTEL;say("सुरक्षा रिकॉर्ड ढूँढना होगा।", "Find the security log. Check each room.");onCheckpoint()
        }
        if(!target.vitals.alive && mission.stage!=MissionStage.COMPLETE){mission.stage=MissionStage.FAILED;onCheckpoint()}
        if(mission.stage==MissionStage.ESCORT && player.dst(layout.extraction)<9f && target.position.dst(layout.extraction)<10f) {
            mission.stage=MissionStage.COMPLETE;say("हम सुरक्षित हैं। मिशन पूरा हुआ।", "We are safe. Mission complete.");onCheckpoint()
        }
    }
    private fun separatePlayer() {
        for(p in people)if(p.vitals.alive && !p.boarded && p.position.dst2(player)<0.65f*0.65f) {
            val push=player.cpy().sub(p.position);push.y=0f
            if(push.len2()>0.001f)physics.move(playerBody,player,push.nor().scl(0.03f),yaw)
        }
    }
    private fun changeStance(next:Stance) {
        if(vehicle!=null)return
        if(physics.changeStance(playerBody,player,next.height,next==Stance.PRONE,yaw))stance=next
        else notice("Not enough room to change stance")
    }
    fun reload() {
        if(reloadTime<=0f && reserve>0 && magazine<30){reloadTime=1.65f;onSound("reload",1f)}
    }
    private fun shoot() {
        magazine--;shots++;fireTime=0.16f;recoil=1f
        val start=eye;val direction=forward
        direction.add(rng.nextFloat()*0.012f-0.006f,rng.nextFloat()*0.012f-0.006f,rng.nextFloat()*0.012f-0.006f).nor()
        val end=start.cpy().mulAdd(direction,220f);val hit=physics.ray(start,end,0)
        hit?.let {damage(it.id,CombatRules.BULLET_DAMAGE)}
        traces+=start.cpy().mulAdd(direction,0.65f) to (hit?.point?:end);traceTime=0.055f
        onSound("shot",1f)
        people.filter{it.position.dst2(player)<900f && it.kind!=PersonKind.GOON}.forEach{it.fleeing=8f}
        if(magazine==0)onCheckpoint()
    }
    private fun melee() {
        fireTime=0.6f;recoil=0.7f;onSound("melee",1f)
        physics.ray(eye,eye.mulAdd(forward,2.1f),0)?.let{damage(it.id,CombatRules.MELEE_DAMAGE)}
    }
    private fun damage(id:Int,amount:Int) {
        if(id==0) {
            if(vehicle!=null)return
            if(vitals.hit(amount)){hurt=0.6f;onSound("hurt",0.6f);onCheckpoint()}
        } else people.firstOrNull{it.id==id}?.let {
            if(!it.boarded && it.vitals.hit(amount)) {
                if(!it.vitals.alive){physics.remove(it.id);it.body=null;it.path=emptyList();onCheckpoint()}
                else if(it.kind==PersonKind.GOON){it.lastSeen.set(player);it.memory=7f}
            }
        }
    }
    private fun drive(car:Vehicle,c:Command,dt:Float) {
        val f=Vector3(0f,0f,-1f).rot(car.body.transform).nor()
        val velocity=car.body.body.linearVelocity
        car.speed=velocity.dot(f)
        val targetSpeed=if(c.sprint)21f else 14f
        val acceleration=c.forward*7f-car.speed*0.22f
        val desired=(car.speed+acceleration*dt).coerceIn(-7f,targetSpeed)
        car.body.body.linearVelocity=Vector3(f.x*desired,velocity.y,f.z*desired)
        car.steering=MathUtils.lerp(car.steering,-c.strafe,dt*6f)
        car.body.body.angularVelocity=Vector3(0f,car.steering*desired/9f,0f)
        yaw=car.yaw;pitch=pitch.coerceIn(-25f,35f)
    }
    private fun canReach(from:Vector3,to:Vector3,allowedId:Int=-1,ignore:Int=0):Boolean {
        val hit=physics.ray(from,to,ignore)
        return hit==null || hit.id==allowedId
    }
    private fun nearby(to:Vector3,distance:Float,id:Int=-1,height:Float=.5f):Boolean =
        player.dst(to)<distance && canReach(eye,to.cpy().add(0f,height,0f),id)
    fun context():String {
        if(vehicle!=null)return "EXIT VEHICLE"
        if(mission.stage==MissionStage.INTEL && nearby(mission.intel,3f,height=.55f))return "READ SECURITY LOG"
        if(mission.stage==MissionStage.HOSTAGE && nearby(target.position,3f,target.id,1f))return "FREE "+mission.targetName.uppercase()
        if(layout.stashes.indices.any{it !in collected && nearby(layout.stashes[it],2.5f)})return "COLLECT AMMUNITION"
        if(people.any{!it.vitals.alive && it.loot>0 && nearby(it.position,2.5f,height=.3f)})return "SEARCH AMMUNITION"
        if(vehicles.any{nearby(it.position,3.8f,it.id,0f)})return "ENTER VEHICLE"
        return ""
    }
    private fun boardTarget() {
        target.boarded=true
        target.body?.let{physics.setActive(it,false)}
    }
    fun interact() {
        vehicle?.let {car ->
            for(offset in listOf(Vector3(-2.8f,0f,0f),Vector3(2.8f,0f,0f),Vector3(0f,0f,3.2f))) {
                val pos=offset.rot(car.body.transform).add(car.position);pos.y=0.12f
                if(physics.clearForActor(pos,0)) {
                    val companionExit=if(target.boarded)listOf(Vector3(0f,0f,-1.5f),Vector3(0f,0f,1.5f),Vector3(-1.5f,0f,0f),Vector3(1.5f,0f,0f))
                        .map{it.add(pos)}.firstOrNull{physics.clearForActor(it,target.id)} else null
                    if(target.boarded && companionExit==null)continue
                    vehicle=null;player.set(pos);stance=Stance.STAND
                    physics.changeStance(playerBody,player,stance.height,false,yaw);physics.setActor(playerBody,player,yaw);physics.setActive(playerBody,true)
                    if(target.boarded){target.boarded=false;target.position.set(companionExit!!);target.body?.let{physics.setActor(it,target.position);physics.setActive(it,true)}}
                    onCheckpoint();return
                }
            }
            notice("Both doors are blocked. Move the vehicle.");return
        }
        if(mission.stage==MissionStage.INTEL && nearby(mission.intel,3f,height=.55f)) {
            mission.stage=MissionStage.HOSTAGE;say("रिकॉर्ड मिल गया। अब बंधक को ढूँढना है।", "Security log found. Check room %02d.".format(mission.targetRoom.id+1));onCheckpoint();return
        }
        if(mission.stage==MissionStage.HOSTAGE && nearby(target.position,3f,target.id,1f)) {
            mission.stage=MissionStage.ESCORT;target.crouching=false
            target.body?.let{physics.changeStance(it,target.position,1.8f,false,target.yaw)}
            say("मेरे पीछे रहिए। मैं आपको सुरक्षित ले जाऊँगा।", "Stay behind me. We are getting you out.");onCheckpoint();return
        }
        layout.stashes.forEachIndexed {i,p ->if(i !in collected && nearby(p,2.5f)){collected+=i;reserve+=60;notice("+60 rounds");onSound("reload",0.5f);onCheckpoint();return}}
        people.firstOrNull{!it.vitals.alive && it.loot>0 && nearby(it.position,2.5f,height=.3f)}?.let{reserve+=it.loot;it.loot=0;notice("+30 rounds");onCheckpoint();return}
        vehicles.firstOrNull{nearby(it.position,3.8f,it.id,0f)}?.let {
            vehicle=it;physics.setActive(playerBody,false);reloadTime=0f;pitch=0f
            if(mission.stage==MissionStage.ESCORT && target.position.dst(player)<7f && physics.ray(target.position.cpy().add(0f,1.5f,0f),eye,target.id)?.id==0)boardTarget()
            say("गाड़ी मिल गई। निकलते हैं।", "Vehicle secured. Let's move.");onCheckpoint()
        }
    }
    private fun updatePerson(p:Person,dt:Float) {
        if(!p.vitals.alive)return
        p.fireCooldown-=dt;p.replan-=dt;p.sightTimer-=dt;p.fleeing-=dt;p.memory-=dt;p.moving=0f
        if(p.boarded)return
        if(p==target) {
            if(mission.stage==MissionStage.ESCORT) {
                if(vehicle!=null && p.position.dst(player)<5f && canReach(p.position.cpy().add(0f,1.4f,0f),vehicle!!.position,vehicle!!.id,p.id)){boardTarget();return}
                if(p.position.dst(player)>2.8f)follow(p,player,3.8f,dt)
            }
            return
        }
        if(p.kind!=PersonKind.GOON) {
            if(p.fleeing>0f)follow(p,Vector3(73f,0.12f,-98f),3.4f*p.scale,dt)
            else if((weather.time+p.id*7f)%28f<12f)follow(p,Vector3(72f+(p.id%3)*2f,0.12f,-83f+(p.id%4)*12f),0.85f,dt)
            return
        }
        if(p.position.dst2(player)>120f*120f)return
        val from=p.position.cpy().add(0f,1.55f,0f); val to=eye
        if(p.sightTimer<=0f) {
            p.sightTimer=0.18f+rng.nextFloat()*0.05f
            val distance=from.dst(to);val direction=to.cpy().sub(from).nor()
            val facing=Vector3(-MathUtils.sinDeg(p.yaw),0f,-MathUtils.cosDeg(p.yaw))
            val hit=if(distance<weather.visibility*0.8f)physics.ray(from,to,p.id) else null
            val visible=Perception.visible(distance,weather.visibility,hit?.id==0 || (vehicle!=null && hit?.id==vehicle!!.id),facing.dot(direction))
            if(visible && !p.seesPlayer)p.reaction=0.65f+rng.nextFloat()*0.8f
            p.seesPlayer=visible
            if(visible){p.lastSeen.set(player);p.memory=7f}
        }
        if(p.seesPlayer) {
            val d=to.cpy().sub(from);p.yaw=MathUtils.atan2(-d.x,-d.z)*MathUtils.radiansToDegrees
            p.reaction-=dt
            if(p.reaction<=0f && p.fireCooldown<=0f) {
                p.fireCooldown=0.7f+rng.nextFloat()*0.65f
                // Human error is applied to aim, not by bypassing collision or silently inflicting damage.
                val error=0.12f+from.dst(to)*0.025f
                val end=to.cpy().add((rng.nextFloat()-0.5f)*error*2f,(rng.nextFloat()-0.5f)*error*1.7f,0f)
                val hit=physics.ray(from,end.cpy().sub(from).nor().scl(160f).add(from),p.id)
                if(hit!=null)damage(hit.id,10)
                traces+=from to (hit?.point?:end);traceTime=0.07f;onSound("shot",(1f-from.dst(to)/80f).coerceIn(0.08f,0.65f))
            }
            if(p.vitals.health<=50 && p.coverGoal==null) {
                p.coverGoal=layout.cover.asSequence().filter{it.dst2(p.position)<144f && nav.clear(it.x,it.z)}
                    .sortedBy{it.dst2(p.position)}.firstOrNull {
                        val coverHit=physics.ray(to,it.cpy().add(0f,.8f,0f),0)
                        coverHit!=null && coverHit.id>=2000 && coverHit.fraction<.97f
                    }?.cpy()
            }
            val cover=p.coverGoal
            if(cover!=null && p.position.dst2(cover)>.6f)follow(p,cover,2.8f,dt)
            else if(from.dst(to)>22f)follow(p,p.lastSeen,2.4f,dt)
        } else if(p.coverGoal!=null && p.position.dst2(p.coverGoal!!)>.6f)follow(p,p.coverGoal!!,2.8f,dt)
        else if(p.memory>0f)follow(p,p.lastSeen,2f,dt)
    }
    private fun follow(p:Person,destination:Vector3,speed:Float,dt:Float) {
        if(p.replan<=0f){p.path=nav.path(p.position,destination);p.pathIndex=0;p.replan=1f+rng.nextFloat()*0.7f}
        while(p.pathIndex<p.path.size && p.position.dst2(p.path[p.pathIndex])<0.4f)p.pathIndex++
        if(p.pathIndex>=p.path.size)return
        val delta=p.path[p.pathIndex].cpy().sub(p.position);delta.y=0f
        if(delta.len2()<0.001f)return
        p.yaw=MathUtils.atan2(-delta.x,-delta.z)*MathUtils.radiansToDegrees
        p.body?.let{physics.move(it,p.position,delta.nor().scl(speed*dt),p.yaw)};p.moving=speed
    }
    fun dispose()=physics.dispose()
}
