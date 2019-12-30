package autoswitch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.aeonbits.owner.ConfigFactory;
import org.lwjgl.glfw.GLFW;

import java.io.FileOutputStream;
import java.io.IOException;


public class AutoSwitch implements ClientModInitializer {

    //Keybinding
    private static FabricKeyBinding autoswitchToggleKeybinding;
    private static FabricKeyBinding mowingWhenFightingToggleKeybinding;

    private boolean doAS = true;

    private boolean onMP = false;

    private boolean mowing = true;

    @SuppressWarnings("ConstantConditions") //removes warnings about chat message potentially have a null player
    @Override
    public void onInitializeClient() {

        //configuration
        String config = FabricLoader.getInstance().getConfigDirectory().toString() + "/autoswitch.cfg";
        String configMats = FabricLoader.getInstance().getConfigDirectory().toString() + "/autoswitchMaterials.cfg";
        ConfigFactory.setProperty("configDir", config);
        ConfigFactory.setProperty("configDirMats", configMats);
        AutoSwitchConfig cfg = ConfigFactory.create(AutoSwitchConfig.class);
        AutoSwitchMaterialConfig matCfg = ConfigFactory.create(AutoSwitchMaterialConfig.class);

        //generate config file; removes incorrect values from existing one as well
        try {
            cfg.store(new FileOutputStream(config), "AutoSwitch Configuration File" +
                    "\nSee https://github.com/dexman545/Fabric-Autoswitch/wiki/Configuration for more details");
            matCfg.store(new FileOutputStream(configMats), "AutoSwitch Material Configuration File" +
                    "\nControls which block material the tool will target" + "" +
                    "\nSee https://github.com/dexman545/Fabric-Autoswitch/wiki/Materials-Configuration for details");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Keybindings
        autoswitchToggleKeybinding = FabricKeyBinding.Builder.create(
                new Identifier("autoswitch", "toggle"),
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "AutoSwitch"
        ).build();

        mowingWhenFightingToggleKeybinding = FabricKeyBinding.Builder.create(
                new Identifier("autoswitch", "toggle_mowing"),
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "AutoSwitch"
        ).build();

        KeyBindingRegistry.INSTANCE.addCategory("AutoSwitch");
        KeyBindingRegistry.INSTANCE.register(autoswitchToggleKeybinding);
        KeyBindingRegistry.INSTANCE.register(mowingWhenFightingToggleKeybinding);

        //create object to store player state
        SwitchDataStorage data = new SwitchDataStorage();

        System.out.println("AutoSwitch Loaded");

        ClientTickCallback.EVENT.register(e ->
        {

            //keybinding implementation
            if(autoswitchToggleKeybinding.wasPressed()) {
                //The toggle
                doAS = !doAS;

                if (cfg.displayToggleMsg()) {
                    //Toggle message
                    TranslatableText msg = new TranslatableText(doAS && !onMP ? "msg.autoswitch.toggle_true" : "msg.autoswitch.toggle_false");
                    //Display msg above hotbar, set false to display in text chat
                    e.player.addChatMessage(msg, cfg.toggleMsgOverHotbar());
                }

            }

            if (mowingWhenFightingToggleKeybinding.wasPressed()) {
                mowing = !mowing;

                if (cfg.displayToggleMsg()) {
                    //Toggle message
                    TranslatableText msg = new TranslatableText(mowing || !cfg.controlMowingWhenFighting() ? "msg.autoswitch.mow_true" : "msg.autoswitch.mow_false");
                    //Display msg above hotbar, set false to display in text chat
                    e.player.addChatMessage(msg, cfg.toggleMsgOverHotbar());
                }
            }

            //Checks for implementing switchback feature
            if (e.player != null) {
                if (data.getHasSwitched() && !e.player.isHandSwinging) {
                    data.setHasSwitched(false);
                    Targetable.of(data.getPrevSlot(), e.player).changeTool();

                }
            }

            //check if client is on a server or not
            if (!cfg.switchInMP()) {
                if (e.getGame().getCurrentSession() != null) {
                    onMP = e.getGame().getCurrentSession().isRemoteServer();
                }
            }

        });

        //Check if the client in on a multiplayer server
        //This is only called when starting a SP world, not on server join
        //ServerStartCallback.EVENT.register((minecraftServer -> onMP = !minecraftServer.isSinglePlayer()));

        //Block Swap
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
        {

            //Mowing control
            //disable block breaking iff mowing is disabled and there's an entity to hit
            EntityHitResult entityResult = EmptyCollisionBoxAttack.rayTraceEntity(player, 1.0F, 4.5D);
            if (entityResult != null && cfg.controlMowingWhenFighting() && !mowing) {
                player.isHandSwinging = !cfg.disableHandSwingWhenMowing();
                return ActionResult.FAIL;
            }

            //AutoSwitch handling
            int m; //Initialize variable used to track if a switch has been made
            if (!player.isCreative() || cfg.switchInCreative()) {
                if (doAS && cfg.switchForBlocks() && !onMP) {
                    if (!data.getHasSwitched()) {data.setPrevSlot(player.inventory.selectedSlot);}
                    m = Targetable.of(world.getBlockState(pos), player, cfg, matCfg).changeTool();
                    if (m == 1 && cfg.switchbackBlocks()){
                        data.setHasSwitched(true);
                    }

                }
            }

            return ActionResult.PASS;
        });

        //Entity Swap
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
        {

            //AutoSwitch handling
            int m; //Initialize variable used to track if a switch has been made
            if (!player.isCreative() || cfg.switchInCreative()) {
                if (doAS && cfg.switchForMobs() && !onMP) {
                    if (!data.getHasSwitched()) {data.setPrevSlot(player.inventory.selectedSlot);}
                    m = Targetable.of(entity, player, cfg, matCfg).changeTool();
                    if (m == 1 && cfg.switchbackMobs()){
                        data.setHasSwitched(true);
                    }

                }
            }

            return ActionResult.PASS;
        });


    }
}

