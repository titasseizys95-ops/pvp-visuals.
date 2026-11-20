package com.example.pvpvisuals;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/** Visual-only PvP HUD. Nothing here changes gameplay or sends extra packets. */
public class PvpVisualsClient implements ClientModInitializer {

    private static int combo = 0;
    private static int bestCombo = 0;
    private static int ticksSinceHit = 999;
    private static double lastReach = 0;
    private static boolean attackWasDown = false;
    private static final Deque<Long> clicks = new ArrayDeque<>();

    private static KeyBinding openMenuKey;

    private static final Item[] TRACKED = {
            Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE,
            Items.END_CRYSTAL, Items.ENDER_PEARL, Items.RESPAWN_ANCHOR
    };

    static void resetBest() {
        bestCombo = 0;
    }

    @Override
    public void onInitializeClient() {
        Module.load();

        // Default key: Right Shift (rebind in Options > Controls > PvP Visuals)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpvisuals.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of("pvpvisuals", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(PvpVisualsClient::tick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof LivingEntity living && living != player) {
                onHit(player, living);
            }
            return ActionResult.PASS;
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("pvpvisuals", "hud"),
                (context, tickCounter) -> render(context)
        );

        registerCommands();
    }

    // ------------------------------------------------------------ logic

    private static void tick(MinecraftClient mc) {
        while (openMenuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new PvpMenuScreen());
        }

        if (mc.player == null) {
            combo = 0;
            clicks.clear();
            return;
        }

        ticksSinceHit++;
        if (ticksSinceHit > 60) combo = 0;
        if (mc.player.hurtTime > 0) combo = 0;

        boolean down = mc.options.attackKey.isPressed();
        if (down && !attackWasDown) clicks.addLast(System.currentTimeMillis());
        attackWasDown = down;

        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
    }

    private static void onHit(PlayerEntity player, LivingEntity target) {
        combo++;
        ticksSinceHit = 0;
        if (combo > bestCombo) bestCombo = combo;
        lastReach = player.distanceTo(target);

        if (Module.PARTICLES.enabled) {
            MinecraftClient mc = MinecraftClient.getInstance();
            double x = target.getX();
            double y = target.getBodyY(0.5);
            double z = target.getZ();
            for (int i = 0; i < 8; i++) {
                mc.particleManager.addParticle(ParticleTypes.CRIT, x, y, z,
                        (Math.random() - 0.5) * 0.6, Math.random() * 0.4, (Math.random() - 0.5) * 0.6);
            }
            mc.particleManager.addParticle(ParticleTypes.ENCHANTED_HIT, x, y, z, 0, 0.1, 0);
        }
    }

    // ------------------------------------------------------------ HUD

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Module.HUD.enabled || mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        // top-left stack
        int y = 6;
        if (Module.COMBO.enabled) {
            int color = combo >= 10 ? 0xFFFF5555 : combo >= 5 ? 0xFFFFAA00 : 0xFFFFFFFF;
            y = panel(ctx, tr, 6, y, "Combo: " + combo + "  (best " + bestCombo + ")", color);
        }
        if (Module.CPS.enabled) y = panel(ctx, tr, 6, y, "CPS: " + clicks.size(), 0xFFFFFFFF);
        if (Module.STATS.enabled) {
            y = panel(ctx, tr, 6, y, "FPS: " + mc.getCurrentFps() + "  Ping: " + ping(mc) + "ms", 0xFFFFFFFF);
        }
        if (Module.REACH.enabled) {
            y = panel(ctx, tr, 6, y, String.format("Last hit: %.2f blocks", lastReach), 0xFFFFFFFF);
        }

        if (Module.EFFECTS.enabled) drawEffects(ctx, tr, mc, sw);
        if (Module.ARMOR.enabled) drawArmor(ctx, tr, mc, sw, sh);
        if (Module.ITEMS.enabled) drawItemCounts(ctx, tr, mc, sw, sh);
        if (Module.KEYSTROKES.enabled) drawKeystrokes(ctx, tr, mc, sh);

        boolean onPlayer = mc.targetedEntity instanceof PlayerEntity p && p != mc.player;
        if (Module.TARGET.enabled && onPlayer) drawTarget(ctx, tr, (PlayerEntity) mc.targetedEntity, sw, sh);
        if (Module.CROSSHAIR.enabled && mc.targetedEntity instanceof LivingEntity le && le != mc.player) {
            ctx.fill(sw / 2 - 1, sh / 2 - 1, sw / 2 + 2, sh / 2 + 2, 0xFFFF3333);
        }
    }

    private static int ping(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }

    private static int panel(DrawContext ctx, TextRenderer tr, int x, int y, String text, int color) {
        int w = tr.getWidth(text);
        ctx.fill(x - 3, y - 3, x + w + 3, y + 11, 0x90000000);
        ctx.drawTextWithShadow(tr, text, x, y, color);
        return y + 18;
    }

    private static void drawTarget(DrawContext ctx, TextRenderer tr, PlayerEntity target, int sw, int sh) {
        float hp = target.getHealth() + target.getAbsorptionAmount();
        float max = Math.max(1f, target.getMaxHealth());
        float frac = Math.max(0f, Math.min(1f, target.getHealth() / max));

        String name = target.getName().getString();
        String hpText = String.format("%.1f / %.0f", hp, max);

        int boxW = Math.max(120, tr.getWidth(name) + tr.getWidth(hpText) + 20);
        int boxH = 34;
        int x = sw / 2 + 24;
        int y = sh / 2 + 16;

        ctx.fill(x, y, x + boxW, y + boxH, 0x90000000);
        ctx.drawTextWithShadow(tr, name, x + 6, y + 5, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, hpText, x + boxW - 6 - tr.getWidth(hpText), y + 5, 0xFFDDDDDD);

        int barX = x + 6;
        int barY = y + 20;
        int barW = boxW - 12;
        ctx.fill(barX, barY, barX + barW, barY + 6, 0xFF333333);
        int barColor = frac > 0.6f ? 0xFF55FF55 : frac > 0.3f ? 0xFFFFAA00 : 0xFFFF5555;
        ctx.fill(barX, barY, barX + (int) (barW * frac), barY + 6, barColor);
    }

    private static void drawEffects(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sw) {
        int y = 6;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            String time;
            if (e.isInfinite()) {
                time = "inf";
            } else {
                int s = e.getDuration() / 20;
                time = (s / 60) + ":" + String.format("%02d", s % 60);
            }
            String line = name + "  " + time;
            int w = tr.getWidth(line);
            int color = (!e.isInfinite() && e.getDuration() < 200) ? 0xFFFF5555 : 0xFFFFFFFF;
            ctx.fill(sw - w - 9, y - 3, sw - 3, y + 11, 0x90000000);
            ctx.drawTextWithShadow(tr, line, sw - w - 6, y, color);
            y += 16;
        }
    }

    private static void drawArmor(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sw, int sh) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int x = sw - 24;
        int y = sh / 2 - 84;
        for (EquipmentSlot slot : slots) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            ctx.fill(x - 2, y - 2, x + 18, y + 18, 0x90000000);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, x, y);
                ctx.drawStackOverlay(tr, stack, x, y);
            }
            y += 20;
        }
    }

    private static void drawItemCounts(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sw, int sh) {
        int x = sw - 24;
        int y = sh / 2 + 6;
        for (Item item : TRACKED) {
            int count = mc.player.getInventory().count(item);
            if (count <= 0) continue;
            ItemStack icon = new ItemStack(item);
            ctx.fill(x - 2, y - 2, x + 18, y + 18, 0x90000000);
            ctx.drawItem(icon, x, y);
            String s = String.valueOf(count);
            ctx.drawTextWithShadow(tr, s, x - 4 - tr.getWidth(s), y + 4, 0xFFFFFFFF);
            y += 20;
        }
    }

    private static void drawKeystrokes(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int sh) {
        int x = 6;
        int y = sh - 86;
        key(ctx, tr, x + 20, y, 18, 18, "W", mc.options.forwardKey.isPressed());
        key(ctx, tr, x, y + 20, 18, 18, "A", mc.options.leftKey.isPressed());
        key(ctx, tr, x + 20, y + 20, 18, 18, "S", mc.options.backKey.isPressed());
        key(ctx, tr, x + 40, y + 20, 18, 18, "D", mc.options.rightKey.isPressed());
        key(ctx, tr, x, y + 40, 28, 18, "LMB", mc.options.attackKey.isPressed());
        key(ctx, tr, x + 30, y + 40, 28, 18, "RMB", mc.options.useKey.isPressed());
        key(ctx, tr, x, y + 60, 58, 14, "___", mc.options.jumpKey.isPressed());
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        ctx.fill(x, y, x + w, y + h, down ? 0xC0FFFFFF : 0x90000000);
        ctx.drawCenteredTextWithShadow(tr, label, x + w / 2, y + (h - 8) / 2, down ? 0xFF000000 : 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ commands

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("pvpvisuals");
            for (Module m : Module.ALL) {
                root.then(ClientCommandManager.literal(m.id)
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(c -> {
                            boolean v = BoolArgumentType.getBool(c, "enabled");
                            m.set(v);
                            c.getSource().sendFeedback(Text.literal("[PvP Visuals] " + m.label + ": " + (v ? "ON" : "OFF")));
                            return 1;
                        })));
            }
            root.then(ClientCommandManager.literal("resetbest").executes(c -> {
                resetBest();
                c.getSource().sendFeedback(Text.literal("[PvP Visuals] Best combo reset."));
                return 1;
            }));
            dispatcher.register(root);
        });
    }
}
