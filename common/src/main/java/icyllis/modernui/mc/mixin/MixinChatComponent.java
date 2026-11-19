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
 * You should have received a copy of the GNU Lesser General Public License
 * along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    public abstract double getScale();

    @Shadow
    private int getLineHeight() {
        throw new AssertionError();
    }

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract int getWidth();

    @Unique
    private ScrollController modernUI_MC$scroller;

    @Unique
    private double modernUI_MC$target;

    @Unique
    private float modernUI_MC$frac;

    @Unique
    private boolean modernUI_MC$scissoring;

    /**
     * @author BloCamLimb
     * @reason Smooth scrolling
     */
    @Overwrite
    public void scrollChat(int dir) {
        modernUI_MC$ensureScroller();
        int max = Math.max(trimmedMessages.size() - getLinesPerPage(), 0);
        modernUI_MC$target += dir;
        if (modernUI_MC$target > max) {
            modernUI_MC$target = max;
        }
        if (modernUI_MC$target <= 0.0) {
            modernUI_MC$target = 0.0;
            newMessageSinceScroll = false;
        }
        modernUI_MC$scroller.setMaxScroll(max);
        modernUI_MC$scroller.scrollTo((float) modernUI_MC$target);
    }

    @Inject(method = "resetChatScroll", at = @At("TAIL"))
    private void modernUI_MC$onReset(CallbackInfo ci) {
        modernUI_MC$target = 0.0;
        modernUI_MC$frac = 0.0f;
        if (modernUI_MC$scroller != null) {
            modernUI_MC$scroller.scrollTo(0.0f);
            modernUI_MC$scroller.abortAnimation();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void modernUI_MC$preRender(GuiGraphics graphics, int ticks, int mouseX, int mouseY,
                                       boolean focused, CallbackInfo ci) {
        modernUI_MC$ensureScroller();
        int max = Math.max(trimmedMessages.size() - getLinesPerPage(), 0);
        modernUI_MC$scroller.setMaxScroll(max);
        if (modernUI_MC$target > max) {
            modernUI_MC$target = max;
        }
        modernUI_MC$scissoring = false;
        modernUI_MC$scroller.update(MuiModApi.getElapsedTime());
        double smooth = Math.min(modernUI_MC$scroller.getCurrValue(), max);
        int pos = (int) Math.floor(smooth);
        if (pos < 0) {
            pos = 0;
        }
        chatScrollbarPos = pos;
        modernUI_MC$frac = (float) (smooth - pos);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void modernUI_MC$smoothShift(GuiGraphics graphics, int ticks, int mouseX, int mouseY,
                                         boolean focused, CallbackInfo ci) {
        if (modernUI_MC$frac <= 0.0f) {
            return;
        }
        graphics.pose().translate(0.0f, modernUI_MC$frac * getLineHeight(), 0.0f);

        double scale = getScale();
        int bottom = Mth.floor((graphics.guiHeight() - 40) / scale);
        int top = bottom - getLinesPerPage() * getLineHeight();
        graphics.enableScissor(0, (int) Math.floor(top * scale),
                graphics.guiWidth(), (int) Math.floor(bottom * scale));
        modernUI_MC$scissoring = true;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getChatListener()Lnet/minecraft/client/multiplayer/chat/ChatListener;"))
    private void modernUI_MC$endShift(GuiGraphics graphics, int ticks, int mouseX, int mouseY,
                                      boolean focused, CallbackInfo ci) {
        if (modernUI_MC$frac > 0.0f) {
            int lines = getLinesPerPage();
            int index = chatScrollbarPos + lines;
            if (index < trimmedMessages.size()) {
                GuiMessage.Line line = trimmedMessages.get(index);
                if (line != null) {
                    double opacity = minecraft.options.chatOpacity().get() * 0.9 + 0.1;
                    double background = minecraft.options.textBackgroundOpacity().get();
                    double spacing = minecraft.options.chatLineSpacing().get();
                    double time;
                    if (focused) {
                        time = 1.0;
                    } else {
                        time = 1.0 - (ticks - line.addedTime()) / 200.0;
                        time *= 10.0;
                        time = Mth.clamp(time, 0.0, 1.0);
                        time *= time;
                    }
                    int alpha = (int) (255.0 * time * opacity);
                    if (alpha > 3) {
                        int bgAlpha = (int) (255.0 * time * background);
                        int height = getLineHeight();
                        int bottom = Mth.floor((graphics.guiHeight() - 40) / getScale());
                        int y = bottom - lines * height;
                        int width = Mth.ceil(getWidth() / getScale());
                        int shift = (int) Math.round(-8.0 * (spacing + 1.0) + 4.0 * spacing);
                        graphics.fill(-4, y - height, width + 8, y, bgAlpha << 24);
                        graphics.drawString(minecraft.font, line.content(), 0, y + shift,
                                0xFFFFFF + (alpha << 24));
                    }
                }
            }
            graphics.pose().translate(0.0f, -modernUI_MC$frac * getLineHeight(), 0.0f);
        }
        if (modernUI_MC$scissoring) {
            modernUI_MC$scissoring = false;
            graphics.disableScissor();
        }
    }

    @Unique
    private void modernUI_MC$ensureScroller() {
        if (modernUI_MC$scroller == null) {
            modernUI_MC$scroller = new ScrollController((controller, amount) -> {});
            modernUI_MC$target = chatScrollbarPos;
            modernUI_MC$scroller.setMaxScroll(
                    Math.max(trimmedMessages.size() - getLinesPerPage(), 0));
            modernUI_MC$scroller.scrollTo(chatScrollbarPos);
            modernUI_MC$scroller.abortAnimation();
        }
    }
}
