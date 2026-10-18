package com.github.marcoral.versatia.task

import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Hanging
import org.bukkit.entity.Player
import org.bukkit.entity.Vehicle
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.hanging.HangingEvent
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.vehicle.VehicleEvent
import org.bukkit.event.world.WorldEvent
import org.bukkit.inventory.Inventory

// Shorthands for recheckOn limited to the events of one subject; see Condition.kt for the trigger itself

/** [recheckOn] for the events of [player] only, matched by UUID, so a player who rejoined still counts. */
inline fun <reified E : PlayerEvent> recheckOn(
    player: Player,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger {
    val id = player.uniqueId
    return recheckOn<E>(priority, ignoreCancelled, immediately) { it.player.uniqueId == id }
}

/** [recheckOn] for the events of [entity] only, such as `EntityDamageEvent` of a player, matched by UUID. */
inline fun <reified E : EntityEvent> recheckOn(
    entity: Entity,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger {
    val id = entity.uniqueId
    return recheckOn<E>(priority, ignoreCancelled, immediately) { it.entity.uniqueId == id }
}

/** [recheckOn] for the events of [vehicle] only, matched by UUID. */
inline fun <reified E : VehicleEvent> recheckOn(
    vehicle: Vehicle,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger {
    val id = vehicle.uniqueId
    return recheckOn<E>(priority, ignoreCancelled, immediately) { it.vehicle.uniqueId == id }
}

/** [recheckOn] for the events of [hanging] (an item frame, a painting) only, matched by UUID. */
inline fun <reified E : HangingEvent> recheckOn(
    hanging: Hanging,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger {
    val id = hanging.uniqueId
    return recheckOn<E>(priority, ignoreCancelled, immediately) { it.entity.uniqueId == id }
}

/** [recheckOn] for the events of [block] only: the same world and position. */
inline fun <reified E : BlockEvent> recheckOn(
    block: Block,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger = recheckOn<E>(priority, ignoreCancelled, immediately) { it.block == block }

/** [recheckOn] for the events of [inventory] only, such as clicks in and the closing of one menu. */
inline fun <reified E : InventoryEvent> recheckOn(
    inventory: Inventory,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger = recheckOn<E>(priority, ignoreCancelled, immediately) { it.inventory == inventory }

/** [recheckOn] for the events of [world] only, matched by UUID. */
inline fun <reified E : WorldEvent> recheckOn(
    world: World,
    priority: EventPriority = EventPriority.MONITOR,
    ignoreCancelled: Boolean = true,
    immediately: Boolean = false,
): RecheckTrigger {
    val id = world.uid
    return recheckOn<E>(priority, ignoreCancelled, immediately) { it.world.uid == id }
}
