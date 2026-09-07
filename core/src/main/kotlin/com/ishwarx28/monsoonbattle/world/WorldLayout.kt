package com.ishwarx28.monsoonbattle.world

import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class Surface { CONCRETE, BRICK, PAINT, METAL, WOOD, GLASS, ASPHALT, GREEN, LIGHT }
data class Block(val x: Float, val y: Float, val z: Float, val w: Float, val h: Float, val d: Float,
    val surface: Surface = Surface.CONCRETE, val tint: Int = 0x76817e, val solid: Boolean = true, val rendered: Boolean = true)
data class Sign(val x: Float, val y: Float, val z: Float, val text: String, val color: Int, val width: Float = 6f, val yaw: Float = 0f)
data class Room(val id: Int, val building: Int, val index: Int, val x: Float, val z: Float, val label: String)
data class Building(val id: Int, val x: Float, val front: Float, val rows: Int, val name: String) {
    val back get() = front + rows * 8f + 4f
    val center get() = Vector3(x, 0f, (front + back) / 2f)
    fun contains(px: Float, pz: Float) = abs(px - x) < 27f && pz > front && pz < back
}

/** Deterministic, shared by navigation, collision, rendering and tests. Units are metres. */
class WorldLayout(val seed: Int = 2817) {
    val blocks = mutableListOf<Block>()
    val signs = mutableListOf<Sign>()
    val rooms = mutableListOf<Room>()
    val buildings = listOf(Building(0, -62f, 40f, 8, "SHANTI TEXTILES"), Building(1, 62f, 40f, 8, "MEGHDOOT GALLERIA"))
    val trees = mutableListOf<Vector3>()
    val lamps = mutableListOf<Vector3>()
    val stashes = mutableListOf<Vector3>()
    val spawn = Vector3(2f, 0.12f, -83f)
    val extraction = Vector3(0f, 0.12f, -92f)
    val cover = mutableListOf<Vector3>()
    val carSpawns = listOf(Vector3(-4f, 0.8f, -68f), Vector3(5f, 0.8f, -22f), Vector3(-9f, 0.8f, 29f), Vector3(15f, 0.8f, 15f))
    private val rng = Random(seed)

    init {
        block(0f, -0.4f, 0f, 256f, 0.8f, 256f, tint = 0x3d504c)
        // Perimeter, continuous roads and a wide boulevard connecting both mission sites.
        block(-128f, 2f, 0f, 1f, 4f, 256f); block(128f, 2f, 0f, 1f, 4f, 256f)
        block(0f, 2f, -128f, 256f, 4f, 1f); block(0f, 2f, 128f, 256f, 4f, 1f)
        for (side in listOf(-1, 1)) {
            for (z in -68..20 step 22) {
                val x = side * 25f
                val h = rng.nextInt(9, 20).toFloat()
                block(x, h/2f, z.toFloat(), 21f, h, 17f, Surface.PAINT,
                    listOf(0x718185, 0x988875, 0x688b84, 0x9a7865)[rng.nextInt(4)])
                block(x, h + 0.25f, z.toFloat(), 22f, 0.5f, 18f, Surface.CONCRETE, 0x39494c)
                block(x+3f, h+1f, z+2f, 3f, 1.5f, 3f, Surface.METAL, 0x414946, false)
                // Storefronts face the boulevard; signs are physically placed, never floating HUD labels.
                signs += Sign(x, 3.5f, z-8.58f, listOf("SHARMA CHAI", "NAYA MEDICAL", "PATEL GENERAL STORE", "MONSOON CAFE", "CITY ELECTRICALS")[(z+68)/22], 0xdab478, 14f)
                for (floor in 1..((h/3f).toInt()-1)) for (col in -2..2) {
                    block(x+col*3.4f, floor*3f+1f, z-8.57f, 1.5f, 1.7f, 0.08f, Surface.GLASS, if(rng.nextBoolean()) 0x476675 else 0xd8af6e, false)
                    block(x+col*3.4f, floor*3f-0.05f, z-8.8f, 1.9f, 0.12f, 0.65f, Surface.METAL, 0x3d494a, false)
                }
                block(x, 2.5f, z-9.5f, 20f, 0.16f, 2.5f, Surface.METAL, 0x263e43, false)
                // The boulevard-facing facade is detailed too, not a blank side wall.
                signs += Sign(side*14.3f, 3.5f, z.toFloat(), listOf("SHARMA CHAI", "NAYA MEDICAL", "PATEL GENERAL STORE", "MONSOON CAFE", "CITY ELECTRICALS")[(z+68)/22], 0xe0c08a, 15f,side*90f)
                block(side*13.7f, 2.6f, z.toFloat(), 1.8f,.13f,16.5f,Surface.METAL,0x3a6564,false)
                for(col in -1..1) {
                    block(side*14.38f,1.25f,z+col*5f,.09f,2.3f,4.1f,Surface.METAL,0x47645f,false)
                    for(floor in 1..((h/3f).toInt()-1)) {
                        block(side*14.38f,floor*3f+1f,z+col*5f,.08f,1.7f,2f,Surface.GLASS,if((floor+col)%2==0)0x7b8f87 else 0x546f78,false)
                        block(side*14.15f,floor*3f-.05f,z+col*5f,.65f,.12f,2.4f,Surface.METAL,0x354d50,false)
                    }
                }
                signs += Sign(side*14.24f,1.4f,z+6.8f,"LOCAL JOBS / ENQUIRE INSIDE",0xc19a68,2.4f,side*90f)
                if(z== -24)signs += Sign(side*14.2f,h+1.6f,z.toFloat(),"MONSOON RELIEF  /  HELP YOUR NEIGHBOUR",0xd9ac62,16f,side*90f)

                for (c in -2..2) block(x+c*3.9f, 1.15f, z-8.65f, 3.4f, 2.25f, 0.12f, Surface.METAL, 0x405960, false)
                signs += Sign(x-6f, 1.5f, z-8.8f, "FRESH CHAI  /  10", 0xa7c6ac, 3.3f)
            }
            for (z in -110..110 step 22) lamps += Vector3(side*10.5f, 0f, z.toFloat())
        }
        // Quiet garden: canopy, paths, benches, a water basin and small pavilion.
        block(73f, 0.02f, -61f, 66f, 0.04f, 69f, Surface.GREEN, 0x335c45, false)
        block(73f, 0.045f, -61f, 5f, 0.05f, 68f, Surface.CONCRETE, 0x81918a, false)
        block(73f, 0.045f, -58f, 64f, 0.05f, 4f, Surface.CONCRETE, 0x81918a, false)
        for (i in 0..27) {
            val x = 46f+rng.nextFloat()*60f; val z = -91f+rng.nextFloat()*59f
            if(abs(x-73f)>5f && abs(z+58f)>4f) trees += Vector3(x, 0f, z)
        }
        for (z in -88..-30 step 14) {
            block(68f, 0.48f, z.toFloat(), 2.8f, 0.2f, 0.7f, Surface.WOOD, 0x806149)
            block(67.9f, 0.85f, z+0.4f, 2.8f, 0.6f, 0.13f, Surface.WOOD, 0x806149)
        }
        for (x in listOf(64f, 82f)) for (z in listOf(-103f, -95f)) block(x, 2f, z, 0.3f, 4f, 0.3f, Surface.WOOD, 0x554839)
        block(73f, 4.1f, -99f, 20f, 0.35f, 11f, Surface.WOOD, 0x5d4e3f)
        signs += Sign(73f, 3.2f, -93.4f, "RAIN GARDEN  /  WALK SLOWLY", 0xb3c4a0, 15f)
        // Warehouse district and contextual street furniture.
        for (i in 0..5) {
            val x = -61f-(i%2)*30f; val z = -72f+(i/2)*28f
            block(x, 5f, z, 23f, 10f, 21f, Surface.BRICK, 0x796856)
            block(x, 10.25f, z, 24f, 0.5f, 22f, Surface.METAL, 0x394e54)
        }
        for (x in -112..112 step 16) if(abs(x)>16) lamps += Vector3(x.toFloat(), 0f, 32f)
        for (i in 0..15) {
            val x = if(i%2==0) -11.7f else 11.7f; val z = -100f+i*12.4f
            block(x, 0.5f, z, 0.6f, 1f, 0.7f, Surface.METAL, 0x3d6667)
        }
        buildings.forEach(::buildInterior)
        for (t in trees) block(t.x, 2f, t.z, 0.65f, 4f, 0.65f, Surface.WOOD, 0x564938)
        // Collision-only proxies match the detailed lamp and auto-rickshaw meshes.
        for (l in lamps) blocks += Block(l.x, 3.1f, l.z, .16f, 6.2f, .16f, rendered=false)
        for (v in listOf(Vector3(-6f,.72f,-48f),Vector3(7f,.72f,55f)))
            blocks += Block(v.x, .73f, v.z, 1.52f, 1.45f, 2.65f, rendered=false)
        // The player's safe pickup point is accessible from the entire road network.
        signs += Sign(0f, 3f, -99f, "NAYA NAGAR  /  RELIEF CHECKPOINT", 0xe1bc7e, 18f, 180f)
        for(x in listOf(-8f, 8f)) block(x, 1f, -97f, 5f, 2f, 1.5f, Surface.CONCRETE, 0x8b9284)
    }

    private fun buildInterior(b: Building) {
        val centerZ = (b.front+b.back)/2f
        val tint = if(b.id==0) 0x778b86 else 0xbaa98e
        block(b.x, 3.3f, b.back, 54f, 6.6f, 0.45f, tint=tint)
        for(s in listOf(-1,1)) {
            block(b.x+s*27f, 3.3f, centerZ, 0.45f, 6.6f, b.back-b.front, tint=tint)
            block(b.x+s*16f, 3.3f, b.front, 22f, 6.6f, 0.45f, tint=tint)
        }
        block(b.x, 5.4f, b.front, 10f, 2.4f, 0.45f, tint=tint)
        block(b.x, 6.7f, centerZ, 54.4f, 0.35f, b.back-b.front+0.4f, Surface.METAL, 0x384c52)
        block(b.x, 0.035f, centerZ, 53f, 0.07f, b.back-b.front-0.5f, Surface.CONCRETE, if(b.id==0) 0x637771 else 0x8b918c, false)
        signs += Sign(b.x, 4.8f, b.front-0.35f, b.name, 0xebc480, 26f)
        for(row in 0 until b.rows) for(s in listOf(-1,1)) {
            val z = b.front+6f+row*8f; val x=b.x+s*15.5f
            val label = if(b.id==0) listOf("CUTTING", "WEAVING", "PACKING", "QUALITY", "STORES", "OFFICE", "ARCHIVE", "DISPATCH")[row]
                else listOf("ATRIUM", "FASHION", "BOOKSHOP", "ELECTRONICS", "FOOD COURT", "SERVICE", "SECURITY", "MANAGEMENT")[row]
            val room = Room(rooms.size,b.id,row,x,z,label); rooms += room
            for(dz in listOf(-2.85f,2.85f)) block(b.x+s*4.5f, 3f, z+dz, 0.3f, 6f, 2.3f, tint=tint)
            block(b.x+s*4.5f, 4.5f, z, 0.3f, 3f, 3.4f, tint=tint)
            block(x, 3f, z+4f, 22f, 6f, 0.3f, tint=tint)
            // Furnishings create useful cover without sealing the route to the door.
            block(x+s*4f, 0.6f, z+0.4f, 6f, 1.2f, 2f, if(b.id==0) Surface.METAL else Surface.WOOD, if(b.id==0) 0x416d6c else 0x947254)
            block(x+s*8f, 1.5f, z-2.5f, 2f, 3f, 1.5f, Surface.WOOD, 0x826b4e)
            cover += Vector3(x+s*4f, 0.12f, z+2.2f)
            cover += Vector3(x+s*4f, 0.12f, z-1.5f)
            block(x, 5.85f, z, 4f, 0.10f, 0.35f, Surface.LIGHT, 0xf0ddb4, false)
            signs += Sign(x, 2.6f, z+3.78f, "%02d  /  %s".format(room.id+1,label), 0xdac498, 7f)
            if(row%2==0) stashes += Vector3(x-s*4f, 0.12f, z-2.1f)
        }
        for(row in 0 until b.rows) {
            block(b.x, 6.1f, b.front+6f+row*8f, 2f, 0.12f, 0.4f, Surface.LIGHT, 0xf4d7a3, false)
            block(b.x, 6.0f, b.front+3f+row*8f, 8.6f, 0.32f, 0.3f, Surface.METAL, 0x40525a, false)
        }
    }

    fun block(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,surface:Surface=Surface.CONCRETE,tint:Int=0x76817e,solid:Boolean=true) {
        blocks += Block(x,y,z,w,h,d,surface,tint,solid)
    }
    fun roofAt(x:Float,z:Float):Float? = blocks.asSequence().filter { it.y-it.h/2f>3f && it.w>3f && it.d>3f && abs(x-it.x)<it.w/2f && abs(z-it.z)<it.d/2f }.minOfOrNull { it.y-it.h/2f }
    fun blocked(x:Float,z:Float,radius:Float=0.36f,height:Float=1.8f):Boolean = blocks.any {
        it.solid && it.y+it.h/2f>0.22f && it.y-it.h/2f<height && abs(x-it.x)<it.w/2f+radius && abs(z-it.z)<it.d/2f+radius
    }
}
