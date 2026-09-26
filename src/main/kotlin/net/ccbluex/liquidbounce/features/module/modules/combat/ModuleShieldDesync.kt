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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.InteractionTracker
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket

object ModuleShieldDesync : ClientModule("ShieldDesync", ModuleCategories.COMBAT) {

    private var ticks = 0

    @Suppress("Unused")
    private val repeatable = tickHandler {
        val hand = InteractionTracker.blockingHand ?: return@tickHandler
        ticks++
        if (ticks >= 4){
            // TODO: Fix Grim PacketOrderI false.
            //  Maybe delay release for a tick?
            InteractionTracker.untracked {
                network.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN
                    ))
                interaction.startPrediction(world){ sequence ->
                    ServerboundUseItemPacket(hand, sequence, player.yRot, player.xRot)
                }
            }
            ticks = 0
        }
    }
}
