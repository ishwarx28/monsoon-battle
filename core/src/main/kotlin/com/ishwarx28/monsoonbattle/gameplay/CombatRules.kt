package com.ishwarx28.monsoonbattle.gameplay

/** Shared, platform-independent rules. Health is integral so ten hits are exact. */
object CombatRules {
    const val MAX_HEALTH = 100
    const val BULLET_DAMAGE = 10
    const val MELEE_DAMAGE = 10
    const val MAX_REVIVES = 3
    const val DAY_SECONDS = 300f
    const val NIGHT_SECONDS = 300f
}

enum class LifeState { ACTIVE, INCAPACITATED, DEAD }

data class LifeSnapshot(val health: Int, val revivesUsed: Int)

/** NPC health and damage accounting. Dead NPCs cannot take further damage. */
class NpcVitals(initialHealth: Int = CombatRules.MAX_HEALTH) {
    var health: Int = initialHealth.coerceIn(0, CombatRules.MAX_HEALTH)
        private set
    val alive: Boolean get() = health > 0

    fun hit(damage: Int = CombatRules.BULLET_DAMAGE): Boolean {
        require(damage >= 0) { "Damage must not be negative" }
        if (!alive || damage == 0) return false
        health = (health - damage).coerceAtLeast(0)
        return true
    }
}

/**
 * A player is incapacitated on the first three lethal encounters and permanently
 * dead on the fourth, provided each earlier incapacitation earned a revive.
 * Ad failure never grants health or consumes a revive. A ticket is single-use
 * and belongs to this run, preventing stale/duplicate callbacks from rewarding
 * another run. Call mutations on the game/render thread.
 */
class PlayerVitals(snapshot: LifeSnapshot = LifeSnapshot(CombatRules.MAX_HEALTH, 0)) {
    var health: Int = snapshot.health.coerceIn(0, CombatRules.MAX_HEALTH)
        private set
    var revivesUsed: Int = snapshot.revivesUsed.coerceIn(0, CombatRules.MAX_REVIVES)
        private set
    var state: LifeState = deriveState()
        private set
    private var pendingTicket: ReviveTicket? = null

    class ReviveTicket internal constructor()

    val canRequestRevive: Boolean
        get() = state == LifeState.INCAPACITATED &&
            revivesUsed < CombatRules.MAX_REVIVES && pendingTicket == null
    val rewardPending: Boolean get() = pendingTicket != null

    fun hit(damage: Int = CombatRules.BULLET_DAMAGE): Boolean {
        require(damage >= 0) { "Damage must not be negative" }
        if (state != LifeState.ACTIVE || damage == 0) return false
        health = (health - damage).coerceAtLeast(0)
        state = deriveState()
        return true
    }

    fun beginRevive(): ReviveTicket? {
        if (!canRequestRevive) return null
        return ReviveTicket().also { pendingTicket = it }
    }

    /** Invoke only after the platform has verified the reward callback. */
    fun finishRevive(ticket: ReviveTicket, rewardEarned: Boolean): Boolean {
        if (pendingTicket !== ticket) return false
        pendingTicket = null
        if (!rewardEarned || state != LifeState.INCAPACITATED ||
            revivesUsed >= CombatRules.MAX_REVIVES) return false
        revivesUsed += 1
        health = CombatRules.MAX_HEALTH
        state = LifeState.ACTIVE
        return true
    }

    fun abandonRun() {
        pendingTicket = null
        health = 0
        revivesUsed = CombatRules.MAX_REVIVES
        state = LifeState.DEAD
    }

    fun snapshot(): LifeSnapshot = LifeSnapshot(health, revivesUsed)

    private fun deriveState(): LifeState = when {
        health > 0 -> LifeState.ACTIVE
        revivesUsed < CombatRules.MAX_REVIVES -> LifeState.INCAPACITATED
        else -> LifeState.DEAD
    }
}
