package com.donzilzenovka.villagemechanics;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;

import com.donzilzenovka.villagemechanics.combat.VillagerCombatHandler;
import com.donzilzenovka.villagemechanics.command.VillageMechanicsCommand;
import com.donzilzenovka.villagemechanics.config.ModConfiguration;
import com.donzilzenovka.villagemechanics.trade.DiagnosticTradeOverrideHandler;
import com.donzilzenovka.villagemechanics.trade.TradeManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = VillageMechanics.MOD_ID,
    name = VillageMechanics.MOD_NAME,
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:Forge@[10.13.4.1614,);after:VillageNames;after:SpecialAI")
public final class VillageMechanics {

    public static final String MOD_ID = "villagemechanics";
    public static final String MOD_NAME = "Village Mechanics";

    private static ModConfiguration configuration;
    private static TradeManager tradeManager;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File root = new File(event.getModConfigurationDirectory(), "VillageMechanics");
        configuration = new ModConfiguration(root, event.getModLog());
        configuration.load();

        VillagerCombatHandler combatHandler = new VillagerCombatHandler(configuration, event.getModLog());
        MinecraftForge.EVENT_BUS.register(combatHandler);
        MinecraftForge.EVENT_BUS.register(new DiagnosticTradeOverrideHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(combatHandler);

        event.getModLog()
            .info(
                "Compatibility: VillageNames={}, SpecialAI={}",
                Loader.isModLoaded("VillageNames"),
                Loader.isModLoaded("SpecialAI"));
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        tradeManager = new TradeManager(configuration, LogManager.getLogger(MOD_ID));
        LogManager.getLogger(MOD_ID)
            .warn(
                "Diagnostic trade override enabled: every EntityVillager is forced to trade one dirt for one diamond");
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new VillageMechanicsCommand(configuration, tradeManager));
    }
}
