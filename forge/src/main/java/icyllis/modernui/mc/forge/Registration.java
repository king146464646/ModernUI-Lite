/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.forge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.testforge.TestContainerMenu;
import icyllis.modernui.mc.testforge.TestPauseFragment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.commons.io.output.StringBuilderWriter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static icyllis.modernui.mc.ModernUIMod.*;

/**
 * This class handles mod loading events, all registry entries are only available under the development mode.
 */
@Mod.EventBusSubscriber(modid = ModernUI.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
final class Registration {

    private Registration() {
    }

    @SubscribeEvent
    static void register(@Nonnull RegisterEvent event) {
        if (ModernUIMod.sDevelopment) {
            event.register(ForgeRegistries.MENU_TYPES.getRegistryKey(), Registration::registerMenus);
            event.register(ForgeRegistries.ITEMS.getRegistryKey(), Registration::registerItems);
        }
    }

    static void registerMenus(@Nonnull RegisterEvent.RegisterHelper<MenuType<?>> helper) {
        helper.register(MuiRegistries.TEST_MENU_KEY, IForgeMenuType.create(TestContainerMenu::new));
    }

    static void registerItems(@Nonnull RegisterEvent.RegisterHelper<Item> helper) {
        Item.Properties properties = new Item.Properties().stacksTo(1);
        helper.register(MuiRegistries.PROJECT_BUILDER_ITEM_KEY, new ProjectBuilderItem(properties));
    }

    @SubscribeEvent
    static void setupCommon(@Nonnull FMLCommonSetupEvent event) {
        /*byte[] bytes = null;
        try (InputStream stream = ModernUIForge.class.getClassLoader().getResourceAsStream(
                "icyllis/modernui/forge/NetworkMessages.class")) {
            Objects.requireNonNull(stream, "Mod file is broken");
            bytes = IOUtils.toByteArray(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream stream = ModernUIForge.class.getClassLoader().getResourceAsStream(
                "icyllis/modernui/forge/NetworkMessages$C.class")) {
            Objects.requireNonNull(stream, "Mod file is broken");
            bytes = ArrayUtils.addAll(bytes, IOUtils.toByteArray(stream));
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        if (ModList.get().getModContainerById(new String(new byte[]{0x1f ^ 0x74, (0x4 << 0x1) | 0x41,
                ~-0x78, 0xd2 >> 0x1}, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT)).isPresent()) {
            event.enqueueWork(() -> LOGGER.fatal("OK"));
        }
        /*bytes = ArrayUtils.addAll(bytes, ModList.get().getModFileById(ModernUI.ID).getLicense()
                .getBytes(StandardCharsets.UTF_8));
        if (bytes == null) {
            throw new IllegalStateException();
        }*/

        MinecraftForge.EVENT_BUS.register(ServerHandler.INSTANCE);
    }

    /*@Nonnull
    private static String digest(@Nonnull byte[] in) {
        try {
            in = MessageDigest.getInstance("MD5").digest(in);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i += 3) {
            int c = (in[i] & 0xFF) | (in[i + 1] & 0xFF) << 8 | (in[i + 2] & 0xFF) << 16;
            for (int k = 0; k < 4; k++) {
                final int m = c & 0x3f;
                final char t;
                if (m < 26)
                    t = (char) ('A' + m);
                else if (m < 52)
                    t = (char) ('a' + m - 26);
                else if (m < 62)
                    t = (char) ('0' + m - 52);
                else if (m == 62)
                    t = '+';
                else // m == 63
                    t = '/';
                sb.append(t);
                c >>= 6;
            }
        }
        sb.append(Integer.toHexString(in[15] & 0xFF));
        return sb.toString();
    }*/

    @Mod.EventBusSubscriber(modid = ModernUI.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ModClient {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private ModClient() {
        }

        /*@SubscribeEvent
        static void loadingClient(RegisterParticleProvidersEvent event) {
            // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
            // this event fired before RegisterClientReloadListenersEvent
            UIManagerForge.initialize();
        }*/

        @SubscribeEvent
        static void registerResourceListener(@Nonnull RegisterClientReloadListenersEvent event) {
            // this event fired after LOAD_REGISTRIES and before COMMON_SETUP on client main thread (render thread)
            // this event fired after ParticleFactoryRegisterEvent
            Image.setLegacyFactory(ImageStore.getInstance());
            event.registerReloadListener(ResourcesStore.getInstance());
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                Handler handler = Core.getUiHandlerAsync();
                // FML may throw ex, so it can be null
                if (handler != null) {
                    // Call in lambda, not in creating the lambda
                    handler.post(() -> {
                        ImageStore.getInstance().clear();
                        UIManager.getInstance().updateLayoutDir(ConfigImpl.CLIENT.mForceRtl.get());
                    });
                }
                BlurHandler.INSTANCE.loadEffect();
            });
            if (!ModernUIMod.isTextEngineEnabled()) {
                event.registerReloadListener(FontResourceManager.getInstance());
            }
            // else injected by MixinFontManager

            LOGGER.debug(MARKER, "Registered resource reload listener");
        }

        @SubscribeEvent
        static void registerKeyMapping(@Nonnull RegisterKeyMappingsEvent event) {
            event.register(UIManagerForge.OPEN_CENTER_KEY);
        }

        /*@SubscribeEvent
        static void registerCapabilities(@Nonnull RegisterCapabilitiesEvent event) {
            event.register(ScreenCallback.class);
        }*/

        @SubscribeEvent
        static void setupClient(@Nonnull FMLClientSetupEvent event) {
            //SettingsManager.INSTANCE.buildAllSettings();
            //UIManager.getInstance().registerMenuScreen(Registration.TEST_MENU, menu -> new TestUI());

            event.enqueueWork(() -> {
                //ModernUI.getSelectedTypeface();
                UIManagerForge.initializeRenderer();
                if (ModernUIMod.sDevelopment) {
                    MenuScreens.register(MuiRegistries.TEST_MENU.get(), MenuScreenFactory.create(menu ->
                            new TestPauseFragment()));
                }
            });

            CrashReportCallables.registerCrashCallable("Fragments", () -> {
                var fragments = UIManager.getInstance().getFragmentController();
                var builder = new StringBuilder();
                if (fragments != null) {
                    try (var pw = new PrintWriter(new StringBuilderWriter(builder))) {
                        fragments.getFragmentManager().dump("", null, pw);
                    }
                }
                return builder.toString();
            }, () -> UIManager.getInstance().getFragmentController() != null);
        }

        @SubscribeEvent
        static void onMenuOpen(@Nonnull OpenMenuEvent event) {
            if (ModernUIMod.sDevelopment) {
                if (event.getMenu() instanceof TestContainerMenu c) {
                    event.set(new TestPauseFragment());
                }
            }
        }

        @SubscribeEvent
        static void onRegisterShaders(@Nonnull RegisterShadersEvent event) {
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(),
                                ModernUIMod.location("rendertype_modern_tooltip"),
                                DefaultVertexFormat.POSITION),
                        GuiRenderType::setShaderTooltip);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad tooltip shader", e);
            }
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(),
                                ModernUIMod.location("rendertype_round_rect"),
                                DefaultVertexFormat.POSITION_COLOR),
                        GuiRenderType::setShaderRoundRect);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad round rect shader", e);
            }
        }
    }

    static class ModClientDev {

        static {
            assert (FMLEnvironment.dist.isClient());
        }

        private ModClientDev() {
        }

        @SubscribeEvent
        static void onRegistryModel(@Nonnull ModelEvent.RegisterAdditional event) {
            event.register(new ModelResourceLocation(ModernUIMod.location("item/project_builder_main"), "standalone"));
            event.register(new ModelResourceLocation(ModernUIMod.location("item/project_builder_cube"), "standalone"));
        }

        @SubscribeEvent
        static void onBakeModel(@Nonnull ModelEvent.ModifyBakingResult event) {
            Map<ModelResourceLocation, BakedModel> registry = event.getModels();
            replaceModel(registry, ModelResourceLocation.inventory(ModernUIMod.location("project_builder")),
                    baseModel -> new ProjectBuilderModel(baseModel, event.getModels()));
        }

        private static void replaceModel(@Nonnull Map<ModelResourceLocation, BakedModel> modelRegistry,
                                         @Nonnull ModelResourceLocation location,
                                         @Nonnull Function<BakedModel, BakedModel> replacer) {
            modelRegistry.put(location, replacer.apply(modelRegistry.get(location)));
        }
    }
}
