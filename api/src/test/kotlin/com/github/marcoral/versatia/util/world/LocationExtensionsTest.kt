package com.github.marcoral.versatia.util.world

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World

class LocationExtensionsTest {
    /** A world that is only itself; locations keep a weak reference, so tests hold it in a variable. */
    private fun world() = Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args[0]
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "World"
            else -> throw UnsupportedOperationException(method.name)
        }
    } as World

    @Test
    fun `spawnParticle needs no type argument and does nothing without a world`() {
        Location(null, 0.0, 0.0, 0.0).spawnParticle(Particle.FLAME)
        Location(null, 0.0, 0.0, 0.0).spawnParticle(Particle.DUST, count = 3, data = null)
    }

    @Test
    fun `isWithin includes the boundary`() {
        val world = world()
        val origin = Location(world, 0.0, 64.0, 0.0)
        assertTrue(Location(world, 3.0, 64.0, 4.0).isWithin(5.0, origin))
        assertTrue(origin.isWithin(0.0, origin))
        assertFalse(Location(world, 3.0, 64.1, 4.0).isWithin(5.0, origin))
    }

    @Test
    fun `isWithin is false across worlds and without a world, never an exception`() {
        val world = world()
        val other = world()
        val here = Location(world, 0.0, 64.0, 0.0)
        assertFalse(Location(other, 0.0, 64.0, 0.0).isWithin(10.0, here))
        assertFalse(Location(null, 0.0, 64.0, 0.0).isWithin(10.0, here))
        assertFalse(here.isWithin(10.0, Location(null, 0.0, 64.0, 0.0)))
        assertFalse(Location(null, 0.0, 64.0, 0.0).isWithin(10.0, Location(null, 0.0, 64.0, 0.0)))
    }
}
