package autoswitch.mixin.mixins;

import autoswitch.mixin.impl.ConnectionHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    /**
     * Reset keybinding params and switch state when leaving a world.
     *
     * @see MinecraftClient#disconnect(Screen)
     */
    @Inject(at = @At("HEAD"), method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V")
    private void autoswitch$disconnectEvent(Screen screen, CallbackInfo ci) {
        ConnectionHandler.reset();

    }


}
