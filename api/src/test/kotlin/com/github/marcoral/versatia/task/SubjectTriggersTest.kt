package com.github.marcoral.versatia.task

import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldSaveEvent

class SubjectTriggersTest {
    private inline fun <reified T : Any> fake(id: UUID) = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "getUniqueId", "getUID" -> id
            "equals" -> proxy === args[0]
            "hashCode" -> System.identityHashCode(proxy)
            else -> null
        }
    } as T

    @Test
    fun `a player trigger accepts only that player's events, also after a rejoin`() {
        val id = UUID.randomUUID()
        val trigger = recheckOn<PlayerMoveEvent>(fake<Player>(id))
        val here = Location(null, 0.0, 0.0, 0.0)
        assertEquals(PlayerMoveEvent::class.java, trigger.event)
        assertTrue(trigger.filter(PlayerMoveEvent(fake<Player>(id), here, here)), "a new Player object of the same player")
        assertFalse(trigger.filter(PlayerMoveEvent(fake<Player>(UUID.randomUUID()), here, here)))
    }

    @Test
    fun `the event type picks the subject, also among the entity and world kinds`() {
        val player = fake<Player>(UUID.randomUUID())
        val damage = recheckOn<EntityDamageEvent>(player, immediately = true)
        assertEquals(EntityDamageEvent::class.java, damage.event)
        assertTrue(damage.immediately)
        assertEquals(PlayerQuitEvent::class.java, recheckOn<PlayerQuitEvent>(player).event)
        val world = fake<World>(UUID.randomUUID())
        assertEquals(WorldSaveEvent::class.java, recheckOn<WorldSaveEvent>(world).event)
    }
}
