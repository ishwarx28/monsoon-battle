# Architecture

## Ownership and threading

`MonsoonGame` owns the simulation, renderer, HUD, soundscape and save store. Android-only services implement `PlatformServices`. Ad callbacks are marshalled back onto libGDX's render thread before touching the run. Each rewarded attempt has an identity-based, single-use ticket; the callback also checks that the same simulation is still active.

`WorldLayout` is deterministic geometry and semantic data. Static collision proxies, navigation occupancy, rooms, stashes and roof queries originate here. `PhysicsWorld` owns and disposes all native Bullet resources. Collision-only proxies cover detailed props whose visual geometry differs from a simple box.

## Gameplay

`CombatRules` and vitals are platform-independent integral state machines. `Simulation` applies input, bullet/melee traces, AI, cars and the rescue state machine. Mission progression is `LOCATE -> INTEL -> HOSTAGE -> ESCORT -> COMPLETE`; losing the target gives `FAILED`. The run, mission phase, positions, health, revive count, ammunition, bodies, vehicles and collected stashes are persisted.

Walking uses swept capsules with iterative wall sliding. Crouch changes capsule height and prone uses a horizontal capsule. Cars are dynamic rigid bodies with acceleration and steering; occupants' bodies are removed from active collision until they exit. Navigation is a one-metre four-neighbour A* grid derived from solid layout bounds. Dynamic obstacles still use Bullet sweeps; the grid itself is static.

A ray query returns the nearest collision across scenery, people and cars after excluding the shooter. Bullets do not stop at the fog boundary, but enemy acquisition does. NPC sight stops at 80 percent of the shared fog-far value, before the renderer becomes completely opaque, and also requires an unobstructed ray. Enemies react after a delay and aim with spatial error instead of secretly applying probabilistic damage through scenery.

## Rendering

Static models are merged with `ModelCache`. Dynamic people use articulated nodes; nearby actors are animated on the CPU. Shadow and reflected-scene passes are refreshed less often than the main view. Road shading uses a separate GLSL planar-reflection pass with procedural wetness and ripples. The main framebuffer is capped to a 1440-pixel width and color graded before the HUD. Rain segments are culled below roofs.

This is not a full physically based renderer or a photogrammetric asset pipeline. Current humans are original procedural meshes, not production scanned/rigged characters. The geometry and shader code intentionally remain inspectable and replaceable.

## Persistence and lifecycle

A temporary JSON file is written before rotating the previous save to a backup. The loader validates the schema version and important bounds and can recover the backup if the primary file is corrupt. Ads never persist a pending ticket. App pause saves the current run and stops audio/TTS. Scene and native resources are disposed when a new run replaces the old one.

## Extension points and known limits

Replace procedural model factories with properly licensed, rigged assets without changing health or mission rules. For more elaborate multi-floor missions, replace the flat A* grid and walking controller together. Dynamic car avoidance, richer enemy squad tactics, tyre/suspension simulation, save tamper resistance and long-running mobile profiling are not production-grade in this milestone.

No network gameplay, analytics backend, account system, server-side reward verification or asset streaming service is implemented. These are not silently simulated.
