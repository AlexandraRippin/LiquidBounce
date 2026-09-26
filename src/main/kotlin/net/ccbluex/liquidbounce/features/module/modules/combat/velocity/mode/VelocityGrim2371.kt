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

package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMovementTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerDamage
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerVelocity
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult

internal object VelocityGrim2371 : VelocityMode("Grim2371") {

    // Grim flag with Teleport + velocity.
    // And Server may knockback without any damage
    // So why we should ignore velocity that may make player to fall off?
    private val onlyFlagIgnore by boolean("IgnoreOnlyFlagVelocity", false)
    // I don't know why we need swing. But let user decide
    private val shouldSwing by boolean("Swing", false)

    private var cancelNextVelocity = false
    private var delay = false
    private var needClick = false
    private var gotTeleport = false

    private var needSkipTick = false

    private var waitForPing = false
    private var waitForUpdate = false

    private var hitResult: BlockHitResult? = null
    private var shouldSkip = false

    private var freezeTicks = 0
    private const val MAX_FREEZE_TICKS = 20 // To prevent freezing

    override fun enable() {
        cancelNextVelocity = false
        delay = false
        needClick = false
        waitForUpdate = false
        hitResult = null
        shouldSkip = false
        freezeTicks = 0
    }

    override fun disable() {
        BlinkManager.flush(TransferOrigin.INCOMING)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is ServerboundInteractPacket,
            is ServerboundAttackPacket,
            is ServerboundSpectatorActionPacket,
            is ServerboundUseItemOnPacket ->
                shouldSkip = true

            is ServerboundMovePlayerPacket if packet.hasPosition() && waitForUpdate ->
                event.cancelEvent()

            is ClientboundPlayerPositionPacket ->
                gotTeleport = true
        }

        if (event.isCancelled) {
            return@handler
        }

        if (packet is ClientboundBlockUpdatePacket && waitForUpdate && packet.pos == player.blockPosition()) {
            waitForUpdate = false
            needClick = false
            return@handler
        }

        if (waitForUpdate) {
            return@handler
        }

        // Check for damage to make sure it will only cancel damage velocity (that all we need),
        // and not affect other types of velocity
        if (packet.isLocalPlayerDamage()) {
            cancelNextVelocity = true
        } else if (
            (cancelNextVelocity || (!gotTeleport && onlyFlagIgnore)) &&
            event.packet.isLocalPlayerVelocity()) {
            event.cancelEvent()
            cancelNextVelocity = false
            if (player.isFallFlying){
                needSkipTick = true
                return@handler
            }
            delay = true
            needClick = true
        }
    }

    @Suppress("unused")
    private val queuePacketHandler = handler<BlinkPacketEvent> { event ->
        if (waitForUpdate || !delay || event.origin != TransferOrigin.INCOMING) {
            return@handler
        }

        event.action = BlinkManager.Action.QUEUE
    }

    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { event ->
        // Client handle most server packets at the start of tick before player tick
        if (gotTeleport){
            gotTeleport = false
        }

        if (needSkipTick){
            // New grim velocity bypass on elytra XD
            needSkipTick = false
            event.cancelEvent()
            network.send(ServerboundMovePlayerPacket.StatusOnly(
                player.onGround(),
                player.horizontalCollision
            ))
            return@handler
        }

        if (needClick && !shouldSkip && !player.isUsingItem) {
            hitResult = traceFromPlayer(
                rotation = RotationManager.serverRotation.copy(pitch = 90F)
            ).takeIf {
                (it.blockPos.relative(it.direction) == player.blockPosition() ||
                    it.blockPos == player.blockPosition()) && player.onGround()
            }
        }

        hitResult?.let { hitResult ->
            delay = false

            BlinkManager.flush(TransferOrigin.INCOMING)

            if (interaction.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction() && shouldSwing) {
                // Is it needed? I didn't see any falses without swing
                SwingMode.DO_NOT_HIDE.swing(InteractionHand.MAIN_HAND)
            }

            if (RotationManager.serverRotation.pitch != 90f) {
                network.send(
                    ServerboundMovePlayerPacket.Rot(
                        player.yRot,
                        90f,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            } else {
                network.send(
                    ServerboundMovePlayerPacket.StatusOnly(
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            }
            event.cancelEvent()
            freezeTicks = 1
            waitForUpdate = true
            this.hitResult = null
            needClick = false
        }

        shouldSkip = false
    }

    @Suppress("Unused")
    private val playerMoveTickHandle = handler<PlayerMovementTickEvent> { event ->
        if (waitForUpdate) {
            event.cancelEvent()
            freezeTicks++
            if (freezeTicks > MAX_FREEZE_TICKS) {
                waitForUpdate = false
                waitForPing = false
                needClick = false
            }
        }
    }
}
