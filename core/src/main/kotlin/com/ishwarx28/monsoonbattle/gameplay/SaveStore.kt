package com.ishwarx28.monsoonbattle.gameplay

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json

class SavedPerson {var id=0;var health=100;var x=0f;var z=0f;var loot=30;var boarded=false}
class SavedCar {var id=0;var x=0f;var y=0.8f;var z=0f;var yaw=0f}
class SavedRun {
    var version=1;var seed=2817;var missionNumber=0;var stage="LOCATE"
    var health=100;var revives=0;var x=2f;var z=-83f;var yaw=180f;var pitch=0f;var stance="STAND"
    var magazine=30;var reserve=120;var time=100f;var vehicle=-1
    var people=ArrayList<SavedPerson>();var cars=ArrayList<SavedCar>();var collected=IntArray(0)
}
/** Rotating backup protects against interrupted writes; ad tickets are deliberately not persisted. */
class SaveStore {
    private val json=Json().apply{setIgnoreUnknownFields(true)}
    private val file get()=Gdx.files.local("monsoon-battle/run.json")
    private val backup get()=Gdx.files.local("monsoon-battle/run.backup.json")
    fun load():SavedRun? {
        for(f in listOf(file,backup))if(f.exists())try {
            val s=json.fromJson(SavedRun::class.java,f)
            if(s.version==1 && s.missionNumber in 0..1 && s.x.isFinite() && s.z.isFinite() && s.time.isFinite() && s.people.size<=100)return s
        }catch(_:Exception){}
        return null
    }
    fun save(s:Simulation) {
        try {
            val data=SavedRun().apply {
                seed=s.seed;missionNumber=s.missionNumber;stage=s.mission.stage.name
                health=s.vitals.health;revives=s.vitals.revivesUsed;x=s.player.x;z=s.player.z;yaw=s.yaw;pitch=s.pitch;stance=s.stance.name
                magazine=s.magazine;reserve=s.reserve;time=s.weather.time;vehicle=s.vehicle?.id?:-1
                collected=s.collected.toIntArray()
                s.people.forEach{p->people+=SavedPerson().apply{id=p.id;health=p.vitals.health;x=p.position.x;z=p.position.z;loot=p.loot;boarded=p.boarded}}
                s.vehicles.forEach{c->cars+=SavedCar().apply{id=c.id;x=c.position.x;y=c.position.y;z=c.position.z;yaw=c.yaw}}
            }
            val tmp=Gdx.files.local("monsoon-battle/run.tmp.json");tmp.writeString(json.toJson(data),false,"UTF-8")
            if(file.exists()){backup.delete();file.moveTo(backup)}
            tmp.moveTo(file)
        }catch(e:Exception){Gdx.app.error("SaveStore","Could not save the operation",e)}
    }
    fun restore(s:Simulation,data:SavedRun) {
        s.vitals=PlayerVitals(LifeSnapshot(data.health,data.revives))
        s.player.set(data.x.coerceIn(-125f,125f),0.12f,data.z.coerceIn(-125f,125f))
        s.yaw=if(data.yaw.isFinite())data.yaw else 180f;s.pitch=if(data.pitch.isFinite())data.pitch.coerceIn(-79f,79f)else 0f
        s.magazine=data.magazine.coerceIn(0,30);s.reserve=data.reserve.coerceIn(0,10000);s.weather.time=data.time.coerceIn(0f,36000f)
        s.mission.stage=runCatching{MissionStage.valueOf(data.stage)}.getOrDefault(MissionStage.LOCATE)
        s.collected+=data.collected.filter{it in s.layout.stashes.indices}
        for(saved in data.people)s.people.firstOrNull{it.id==saved.id}?.let {p->
            p.vitals=NpcVitals(saved.health);p.loot=saved.loot.coerceIn(0,30)
            if(saved.x.isFinite() && saved.z.isFinite())p.position.set(saved.x,0.12f,saved.z)
            p.boarded=saved.boarded && p==s.target
            if(!p.vitals.alive){s.physics.remove(p.id);p.body=null}else p.body?.let{s.physics.setActor(it,p.position,p.yaw)}
        }
        for(saved in data.cars)s.vehicles.firstOrNull{it.id==saved.id}?.let {c->
            if(saved.x.isFinite() && saved.y.isFinite() && saved.z.isFinite() && saved.yaw.isFinite()) {
                c.body.transform.idt().translate(saved.x,saved.y,saved.z).rotate(Vector3.Y,saved.yaw)
                c.body.body.worldTransform=c.body.transform
                c.position.set(saved.x,saved.y,saved.z);c.yaw=saved.yaw;s.physics.world.updateSingleAabb(c.body.body)
            }
        }
        s.vehicle=s.vehicles.firstOrNull{it.id==data.vehicle}
        if(s.vehicle!=null)s.physics.setActive(s.playerBody,false)
        if(s.target.boarded && s.vehicle!=null)s.target.body?.let{s.physics.setActive(it,false)}else s.target.boarded=false
        val stance=runCatching{Stance.valueOf(data.stance)}.getOrDefault(Stance.STAND)
        if(s.physics.changeStance(s.playerBody,s.player,stance.height,stance==Stance.PRONE,s.yaw))s.stance=stance
        s.physics.setActor(s.playerBody,s.player,s.yaw)
        if(s.mission.stage==MissionStage.ESCORT){s.target.crouching=false;s.target.body?.let{s.physics.changeStance(it,s.target.position,1.8f,false,s.target.yaw)}}
    }
}
