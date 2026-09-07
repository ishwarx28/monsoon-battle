package com.ishwarx28.monsoonbattle.world

import com.badlogic.gdx.math.Vector3
import java.util.PriorityQueue
import kotlin.math.*

/** One-metre A*, no diagonal corner cutting. Built from the same geometry as Bullet. */
class Navigation(private val layout: WorldLayout) {
    private val n=257
    private val walkable=BooleanArray(n*n) { i -> !layout.blocked((i%n-128).toFloat(),(i/n-128).toFloat()) }
    fun clear(x:Float,z:Float)=x in -126f..126f && z in -126f..126f && walkable[index(x,z)]
    private fun index(x:Float,z:Float)=(z.roundToInt()+128).coerceIn(0,n-1)*n+(x.roundToInt()+128).coerceIn(0,n-1)
    private fun point(i:Int)=Vector3((i%n-128).toFloat(),0.12f,(i/n-128).toFloat())
    private fun nearest(i:Int):Int? {
        if(walkable[i]) return i
        val x=i%n; val z=i/n
        for(r in 1..6) for(dz in -r..r) for(dx in -r..r) {
            val a=x+dx; val b=z+dz
            if(a in 1 until n-1 && b in 1 until n-1 && walkable[b*n+a]) return b*n+a
        }
        return null
    }
    fun path(from:Vector3,to:Vector3):List<Vector3> {
        val start=nearest(index(from.x,from.z))?:return emptyList()
        val goal=nearest(index(to.x,to.z))?:return emptyList()
        if(start==goal) return listOf(point(goal))
        val prev=IntArray(n*n){-1}; val cost=IntArray(n*n){Int.MAX_VALUE}; val closed=BooleanArray(n*n)
        data class Entry(val id:Int,val score:Int)
        val open=PriorityQueue<Entry>(compareBy { it.score })
        fun h(i:Int)=10*(abs(i%n-goal%n)+abs(i/n-goal/n))
        cost[start]=0; open.add(Entry(start,h(start)))
        var visits=0
        while(open.isNotEmpty() && visits++<45000) {
            val cur=open.remove().id
            if(closed[cur]) continue
            if(cur==goal) {
                val out=ArrayList<Vector3>(); var at=goal
                while(at!=start) { out+=point(at); at=prev[at]; if(at<0)return emptyList() }
                out.reverse(); return out
            }
            closed[cur]=true
            val x=cur%n; val z=cur/n
            for((dx,dz) in arrayOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                val a=x+dx; val b=z+dz
                if(a !in 1 until n-1 || b !in 1 until n-1)continue
                val next=b*n+a
                if(!walkable[next] || closed[next])continue
                val c=cost[cur]+10
                if(c<cost[next]) { cost[next]=c; prev[next]=cur; open.add(Entry(next,c+h(next))) }
            }
        }
        return emptyList()
    }
}
