/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.sneaking

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.math.copy
import kotlin.math.abs
import kotlin.math.max

class NoSlowGrimClamp(override val parent: ModeValueGroup<*>) : Mode("GrimClamp") {
    @Suppress("Unused")
    private val playerVel = handler<PlayerMoveEvent> { event ->
        //TODO: Do something for no groud
        if (player.onGround()){
            val origMovement = player.deltaMovement
            val absMax = max(
                abs(origMovement.x),
                abs(origMovement.z)
            )
            if (absMax > 0.0846){
                val clampK = 0.0846 / absMax
                player.deltaMovement = origMovement.copy(
                    x = origMovement.x * clampK,
                    z = origMovement.z * clampK
                )
            }
        }
    }
}
