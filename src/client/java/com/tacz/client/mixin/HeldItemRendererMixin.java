package com.tacz.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.client.config.ClientItemOverrideRegistry;
import com.tacz.client.render.TaczGunRenderer;
import com.tacz.client.input.ClientActionController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the vanilla item submission only for mapped items. A call-site
 * redirect avoids cancelling an injection callback. The replacement uses the same
 * client render queue, so the server and other players keep the ordinary item.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    abstract void submitArmWithItem(AbstractClientPlayer player,
                                            float partialTick,
                                            float pitch,
                                            InteractionHand hand,
                                            float swing,
                                            ItemStack stack,
                                            float equipProgress,
                                            PoseStack poseStack,
                                            SubmitNodeCollector collector,
                                            int light);

    @Redirect(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(" +
                    "Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;" +
                    "FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void tacz$applyClientItemTransform(ItemInHandRenderer renderer,
                                                AbstractClientPlayer player,
                                                float partialTick,
                                                float pitch,
                                                InteractionHand hand,
                                                float swing,
                                                ItemStack stack,
                                                float equipProgress,
                                                PoseStack poseStack,
                                                SubmitNodeCollector collector,
                                                int light) {
        // The timeline belongs to the main hand; the offhand keeps vanilla behavior.
        ItemStack visualStack = hand == InteractionHand.MAIN_HAND
                ? ClientActionController.visualItem(stack) : stack;
        if (hand == InteractionHand.MAIN_HAND && ClientItemOverrideRegistry.INSTANCE.get(visualStack).isPresent()
                && TaczGunRenderer.render(visualStack, poseStack, collector, light, partialTick)) {
            return;
        }
        ItemStack fallback = hand == InteractionHand.MAIN_HAND
                && ClientItemOverrideRegistry.INSTANCE.get(stack).isPresent()
                && ClientItemOverrideRegistry.INSTANCE.get(visualStack).isEmpty() ? visualStack : stack;
        submitArmWithItem(player, partialTick, pitch, hand, swing, fallback,
                equipProgress, poseStack, collector, light);
    }
}
