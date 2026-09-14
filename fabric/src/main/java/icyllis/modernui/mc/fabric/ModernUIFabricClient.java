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

package icyllis.modernui.mc.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.Handler;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.*;
import icyllis.modernui.mc.text.MuiTextCommand;
import icyllis.modernui.mc.text.TextLayoutEngine;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.config.ModConfig;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static icyllis.modernui.mc.ModernUIMod.LOGGER;
import static icyllis.modernui.mc.ModernUIMod.MARKER;

@Environment(EnvType.CLIENT)
public class ModernUIFabricClient extends ModernUIClient implements ClientModInitializer {

    public static final Event<Runnable> START_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    public static final Event<Runnable> END_RENDER_TICK = EventFactory.createArrayBacked(Runnable.class,
            callbacks -> () -> {
                for (Runnable runnable : callbacks) {
                    runnable.run();
                }
            });

    private String mSelectedLanguageCode;
    private Locale mSelectedJavaLocale;

    public ModernUIFabricClient() {
        super();
    }

    @Override
    public void onInitializeClient() {
        START_RENDER_TICK.register(EventHandler.Client::onRenderTick);
        END_RENDER_TICK.register(EventHandler.Client::onRenderTick);

        KeyBindingHelper.registerKeyBinding(UIManagerFabric.OPEN_CENTER_KEY);

        Image.setLegacyFactory(ImageStore.getInstance());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ModernUIMod.location("resources");
            }

            @Nonnull
            @Override
            public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                                  @Nonnull ResourceManager resourceManager,
                                                  @Nonnull ProfilerFiller preparationProfiler,
                                                  @Nonnull ProfilerFiller reloadProfiler,
                                                  @Nonnull Executor preparationExecutor,
                                                  @Nonnull Executor reloadExecutor) {
                return ResourcesStore.getInstance().reload(preparationBarrier, resourceManager, preparationProfiler, reloadProfiler, preparationExecutor, reloadExecutor);
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ModernUIMod.location("client");
            }

            @Override
            public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
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
            }
        });

        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                        ModernUIMod.location("rendertype_modern_tooltip"),
                        DefaultVertexFormat.POSITION,
                        GuiRenderType::setShaderTooltip);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad tooltip shader", e);
            }
            try {
                context.register(
                        ModernUIMod.location("rendertype_round_rect"),
                        DefaultVertexFormat.POSITION_COLOR,
                        GuiRenderType::setShaderRoundRect);
            } catch (IOException e) {
                LOGGER.error(MARKER, "Bad round rect shader", e);
            }
        });

        NeoForgeModConfigEvents.loading(ID).register(ConfigImpl::reloadAnyClient);
        NeoForgeModConfigEvents.reloading(ID).register(ConfigImpl::reloadAnyClient);

        ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
            UIManagerFabric.initializeRenderer();
        });

        NeoForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, ConfigImpl.CLIENT_SPEC,
                ModernUI.NAME_CPT + "/client.toml");
        NeoForgeConfigRegistry.INSTANCE.register(ID, ModConfig.Type.CLIENT, ConfigImpl.TEXT_SPEC,
                ModernUI.NAME_CPT + "/text.toml");

        FontResourceManager.getInstance();
        if (ModernUIMod.isTextEngineEnabled()) {
            ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
                MuiModApi.addOnWindowResizeListener(TextLayoutEngine.getInstance());
            });

            ClientCommandRegistrationCallback.EVENT.register(MuiTextCommand::register);

            MuiModApi.addOnDebugDumpListener(TextLayoutEngine.getInstance());

            ClientTickEvents.END_CLIENT_TICK.register((mc) -> TextLayoutEngine.getInstance().onEndClientTick());

            LOGGER.info(MARKER, "Initialized Modern UI text engine");
        } else {
            // see MixinFontManager in another case
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ModernUIMod.location("font");
                }

                @Nonnull
                @Override
                public CompletableFuture<Void> reload(@Nonnull PreparationBarrier preparationBarrier,
                                                      @Nonnull ResourceManager resourceManager,
                                                      @Nonnull ProfilerFiller preparationProfiler,
                                                      @Nonnull ProfilerFiller reloadProfiler,
                                                      @Nonnull Executor preparationExecutor,
                                                      @Nonnull Executor reloadExecutor) {
                    return FontResourceManager.getInstance().reload(
                            preparationBarrier,
                            resourceManager,
                            preparationProfiler,
                            reloadProfiler,
                            preparationExecutor,
                            reloadExecutor
                    );
                }
            });
        }
        LOGGER.info(MARKER, "Initialized Modern UI client");
    }

    @SuppressWarnings("ConstantValue")
    @Nonnull
    @Override
    protected Locale onGetSelectedLocale() {
        // Minecraft can be null if we're running DataGen
        // LanguageManager can be null if this method is being called too early
        Minecraft minecraft;
        LanguageManager languageManager;
        if ((minecraft = Minecraft.getInstance()) != null &&
                (languageManager = minecraft.getLanguageManager()) != null) {
            String languageCode = languageManager.getSelected();
            if (!languageCode.equals(mSelectedLanguageCode)) {
                mSelectedLanguageCode = languageCode;
                String[] langSplit = languageCode.split("_", 2);
                mSelectedJavaLocale = langSplit.length == 1
                        ? new Locale(langSplit[0])
                        : new Locale(langSplit[0], langSplit[1]);
            }
            return mSelectedJavaLocale;
        }
        return super.onGetSelectedLocale();
    }
}
