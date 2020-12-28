package autoswitch.mixin.impl;

import autoswitch.AutoSwitch;
import autoswitch.config.AutoSwitchConfig;
import autoswitch.events.SwitchEvent;
import autoswitch.util.EventUtil;
import autoswitch.util.SwitchData;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;


/**
 * Implementation of the logic for the MinecraftClient mixin
 */
public class SwitchEventTriggerImpl {

    /**
     * Logic for handling ATTACK type actions.
     *
     * Duplicates short-circuit conditions from {@link net.minecraft.client.MinecraftClient#doAttack()}
     *
     * @param attackCooldown  the attack cooldown
     * @param player          the player
     * @param crosshairTarget the crosshair target
     */
    public static void attack(int attackCooldown, ClientPlayerEntity player, HitResult crosshairTarget) {
        if (attackCooldown > 0 || player.isRiding() || crosshairTarget == null) return;

        triggerSwitch(DesiredType.ACTION, crosshairTarget, player);

    }

    /**
     * Logic for handling USE actions.
     *
     * Duplicates short-circuit conditions from {@link net.minecraft.client.MinecraftClient#doItemUse()}
     *
     * @param interactionManager the interaction manager
     * @param player             the player
     * @param crosshairTarget    the crosshair target
     */
    public static void interact(ClientPlayerInteractionManager interactionManager, ClientPlayerEntity player, HitResult crosshairTarget) {
        if (interactionManager.isBreakingBlock() || player.isRiding() || crosshairTarget == null) return;

        triggerSwitch(DesiredType.USE, crosshairTarget, player);

    }

    /**
     * Process type of action made and desired switch action.
     * <p>Tick scheduler clock to ensure immediate-mode actions are taken on time.</p>
     *
     * @param desiredType     type of action to process for switching.
     * @param crosshairTarget target that the player is looking at.
     * @param player          the player
     */
    private static void triggerSwitch(DesiredType desiredType, HitResult crosshairTarget, ClientPlayerEntity player) {
        SwitchEvent event;
        boolean doSwitchType;

        // Set event and doSwitchType
        switch (desiredType) {
            case USE:
                event = SwitchEvent.USE;
                doSwitchType = AutoSwitch.featureCfg.switchUseActions();
                break;
            case ACTION:
                event = SwitchEvent.ATTACK;
                doSwitchType = AutoSwitch.featureCfg.switchAllowed() == AutoSwitchConfig.TargetType.BOTH ||
                        (crosshairTarget.getType() == HitResult.Type.ENTITY ?
                        AutoSwitch.featureCfg.switchAllowed() == AutoSwitchConfig.TargetType.MOBS :
                        AutoSwitch.featureCfg.switchAllowed() == AutoSwitchConfig.TargetType.BLOCKS);
                break;
            default:
                throw new IllegalStateException("AutoSwitch encountered an unexpected enum value: " + desiredType
                        + "\nSome mod has fiddled with AS's internals!");
        }

        // Trigger switch
        switch (crosshairTarget.getType()) {
            case MISS:
                if (desiredType != DesiredType.USE) break;
                if (AutoSwitch.useActionCfg.bow_action().length == 0) {
                    return; // guard to help prevent lag when rclicking into empty space
                }
                EventUtil.scheduleEvent(event, AutoSwitch.doAS, player, doSwitchType, SwitchData.itemTarget);
                break;
            case ENTITY:
                EntityHitResult entityHitResult = (EntityHitResult) crosshairTarget;
                Entity entity = entityHitResult.getEntity();
                EventUtil.scheduleEvent(event, AutoSwitch.doAS, player, doSwitchType, entity);
                break;
            case BLOCK:
                BlockHitResult blockHitResult = ((BlockHitResult) crosshairTarget);
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockState blockState = player.clientWorld.getBlockState(blockPos);
                if (blockState.isAir()) break;
                EventUtil.scheduleEvent(event, AutoSwitch.doAS, player, doSwitchType, blockState);
                break;
        }

        // Run scheduler here as well as in the clock to ensure immediate-eval switches occur
        AutoSwitch.scheduler.execute(AutoSwitch.tickTime);

    }


    /**
     * Type used to control processing of user action for switching in a unified manor.
     */
    enum DesiredType {
        /**
         * Player "interact" or "use" actions.
         */
        USE,
        /**
         * Player "attack" actions.
         */
        ACTION
    }

}
