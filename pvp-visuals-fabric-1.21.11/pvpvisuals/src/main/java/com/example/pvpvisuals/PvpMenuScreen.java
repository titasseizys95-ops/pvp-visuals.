package com.example.pvpvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class PvpMenuScreen extends Screen {

    public PvpMenuScreen() {
        super(Text.literal("PvP Visuals"));
    }

    @Override
    protected void init() {
        int w = 150;
        int h = 20;
        int gapX = 8;
        int gapY = 24;
        int rows = (Module.ALL.size() + 1) / 2;
        int totalW = w * 2 + gapX;
        int left = this.width / 2 - totalW / 2;
        int top = this.height / 2 - (rows * gapY) / 2 - 10;

        for (int i = 0; i < Module.ALL.size(); i++) {
            Module m = Module.ALL.get(i);
            int x = left + (i % 2) * (w + gapX);
            int y = top + (i / 2) * gapY;
            ButtonWidget[] ref = new ButtonWidget[1];
            ref[0] = ButtonWidget.builder(label(m), b -> {
                m.set(!m.enabled);
                ref[0].setMessage(label(m));
            }).dimensions(x, y, w, h).build();
            addDrawableChild(ref[0]);
        }

        int bottom = top + rows * gapY + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset best combo"), b -> PvpVisualsClient.resetBest())
                .dimensions(left, bottom, w, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left + w + gapX, bottom, w, h).build());
    }

    private static Text label(Module m) {
        return Text.literal(m.label + ": " + (m.enabled ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int rows = (Module.ALL.size() + 1) / 2;
        int top = this.height / 2 - (rows * 24) / 2 - 10;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, top - 16, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
