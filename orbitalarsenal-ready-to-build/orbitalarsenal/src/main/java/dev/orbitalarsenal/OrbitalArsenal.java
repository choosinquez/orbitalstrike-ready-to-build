package dev.orbitalarsenal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orbital Arsenal = OrbitalCannon 1.4.1 + DogOrbital 1.3.0, ported from Bukkit/Paper plugins to a
 * server-side Fabric mod for Minecraft 1.21.11. Vanilla clients can connect.
 *
 * Commands
 *   /orbitalstrike [player]   (op)   instant orbital strike
 *   /orbitalnuke [player]     (op)   orbital nuke falling from the sky
 *   /orbitalrod [strike|nuke] (all)  one-use TNT orbital fishing rod
 *   /dogorbital [player|clear] (op)  Dog Orbital rod / clear all dogs   (alias /dogrod)
 *   /cleardogs                (op)   remove every Dog Orbital dog
 */
public final class OrbitalArsenal implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("orbitalarsenal");

    static final String ROD_KEY = "orbitalarsenal_rod";        // value: "strike" | "nuke" | "dog"

    static ArsenalConfig config = new ArsenalConfig();
    static volatile MinecraftServer server;

    private static int dogTickCounter = 0;
    private static boolean inBuff = false;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            config = ArsenalConfig.load();
            DogOrbital.reset();
            Scheduler.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            DogOrbital.reset();
            Scheduler.clear();
        });

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Scheduler.tick();
            if (++dogTickCounter >= 10) { // every half second
                dogTickCounter = 0;
                DogOrbital.tick();
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof WolfEntity wolf) DogOrbital.onWolfLoad(wolf, world);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof WolfEntity wolf) DogOrbital.onWolfUnload(wolf);
        });

        UseItemCallback.EVENT.register(OrbitalArsenal::onUse);

        // TNT minecart buff: anything hit by a TNT-minecart explosion takes at least tntMinecartDamage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (inBuff) return true;
            if (!(source.getSource() instanceof TntMinecartEntity)) return true;
            double min = config.tntMinecartDamage;
            if (amount >= min) return true;
            if (!(entity.getEntityWorld() instanceof ServerWorld sw)) return true;
            inBuff = true;
            try {
                entity.damage(sw, source, (float) min);
            } finally {
                inBuff = false;
            }
            return false; // the original, weaker hit is replaced by the buffed one
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        LOGGER.info("Orbital Arsenal loaded (OrbitalCannon + DogOrbital).");
    }

    // ================================================================== rods

    private static ItemStack makeRod(String mode) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        Text name;
        List<Text> lore;
        switch (mode) {
            case "dog" -> {
                name = Text.literal("Dog Orbital").formatted(Formatting.RED, Formatting.BOLD);
                lore = List.of(
                        Text.literal("Right-click to unleash " + config.dogsPerRod + " dogs.").formatted(Formatting.GRAY),
                        Text.literal("They become yours and hunt everyone else.").formatted(Formatting.GRAY),
                        Text.literal("One use only.").formatted(Formatting.DARK_GRAY));
                rod.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            case "nuke" -> {
                name = Text.literal("Orbital Nuke Rod (TNT)");
                lore = List.of();
            }
            default -> {
                name = Text.literal("Orbital Strike Rod (TNT)");
                lore = List.of();
            }
        }
        rod.set(DataComponentTypes.CUSTOM_NAME, name.copy().styled(s -> s.withItalic(false)));
        if (!lore.isEmpty()) {
            rod.set(DataComponentTypes.LORE, new LoreComponent(lore.stream().map(t -> (Text) t.copy().styled(s -> s.withItalic(false))).toList()));
        }
        rod.set(DataComponentTypes.MAX_DAMAGE, 1); // one use
        rod.set(DataComponentTypes.DAMAGE, 0);
        NbtCompound tag = new NbtCompound();
        tag.putString(ROD_KEY, mode);
        rod.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return rod;
    }

    /** @return "strike", "nuke", "dog" or null when the stack is not one of our rods. */
    private static String rodMode(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FISHING_ROD)) return null;
        NbtComponent nc = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nc == null) return null;
        return nc.copyNbt().getString(ROD_KEY).orElse(null);
    }

    private static ActionResult onUse(net.minecraft.entity.player.PlayerEntity pe, net.minecraft.world.World world, Hand hand) {
        if (world.isClient() || !(pe instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld sw)) {
            return ActionResult.PASS;
        }
        String mode = rodMode(player.getStackInHand(hand));
        if (mode == null) return ActionResult.PASS;

        if (mode.equals("dog")) {
            player.setStackInHand(hand, ItemStack.EMPTY); // one durability: it breaks on use
            DogOrbital.release(player, sw);
            return ActionResult.FAIL;
        }

        // orbital rods
        if (player.interactionManager.getGameMode() == GameMode.ADVENTURE) {
            player.sendMessage(Text.literal("Orbital rods cannot be used in Adventure mode."), false);
            return ActionResult.FAIL;
        }
        HitResult hit = player.raycast(256.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("No block in range to target."), false);
            return ActionResult.FAIL;
        }
        BlockPos bp = ((BlockHitResult) hit).getBlockPos();
        double tx = bp.getX() + 0.5, ty = bp.getY() + 1.0, tz = bp.getZ() + 0.5;
        player.setStackInHand(hand, ItemStack.EMPTY); // 1 use
        if (mode.equals("nuke")) OrbitalCannon.nukeTnt(sw, tx, ty, tz);
        else OrbitalCannon.strikeTnt(sw, tx, ty, tz);
        return ActionResult.FAIL;
    }

    // ================================================================== commands

    private static Predicate<ServerCommandSource> op() {
        return CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);
    }

    private static final SuggestionProvider<ServerCommandSource> ROD_MODES =
            (ctx, b) -> CommandSource.suggestMatching(new String[]{"strike", "nuke"}, b);

    private static void registerCommands(CommandDispatcher<ServerCommandSource> d) {
        // ---- /orbitalstrike [player]
        d.register(CommandManager.literal("orbitalstrike").requires(op())
                .executes(ctx -> fire(ctx.getSource(), null, false))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> fire(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), false))));

        // ---- /orbitalnuke [player]
        d.register(CommandManager.literal("orbitalnuke").requires(op())
                .executes(ctx -> fire(ctx.getSource(), null, true))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> fire(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), true))));

        // ---- /orbitalrod [strike|nuke]   (everyone, like the original plugin)
        d.register(CommandManager.literal("orbitalrod")
                .executes(ctx -> giveOrbitalRod(ctx.getSource(), "strike"))
                .then(CommandManager.argument("mode", StringArgumentType.word()).suggests(ROD_MODES)
                        .executes(ctx -> giveOrbitalRod(ctx.getSource(),
                                StringArgumentType.getString(ctx, "mode").equalsIgnoreCase("nuke") ? "nuke" : "strike"))));

        // ---- /dogorbital [player|clear]  (alias /dogrod)
        LiteralArgumentBuilder<ServerCommandSource> dog = CommandManager.literal("dogorbital").requires(op())
                .executes(ctx -> giveDogRod(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
                .then(CommandManager.literal("clear").executes(ctx -> clearDogs(ctx.getSource())))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> giveDogRod(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))));
        var dogNode = d.register(dog);
        d.register(CommandManager.literal("dogrod").requires(op()).redirect(dogNode));

        // ---- /cleardogs
        d.register(CommandManager.literal("cleardogs").requires(op()).executes(ctx -> clearDogs(ctx.getSource())));
    }

    /** Shared by /orbitalstrike and /orbitalnuke. Target = named player, else the block you look at, else you. */
    private static int fire(ServerCommandSource src, ServerPlayerEntity named, boolean nuke) {
        ServerWorld w;
        double x, y, z;
        if (named != null) {
            w = (ServerWorld) named.getEntityWorld();
            x = named.getX();
            y = named.getY();
            z = named.getZ();
        } else {
            ServerPlayerEntity self = src.getPlayer();
            if (self == null) {
                src.sendError(Text.literal("Console usage: specify a player name."));
                return 0;
            }
            w = (ServerWorld) self.getEntityWorld();
            HitResult hit = self.raycast(256.0, 1.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = ((BlockHitResult) hit).getBlockPos();
                x = bp.getX() + 0.5;
                y = bp.getY() + 1.0;
                z = bp.getZ() + 0.5;
            } else {
                x = self.getX();
                y = self.getY();
                z = self.getZ();
            }
        }
        if (nuke) OrbitalCannon.nuke(w, x, y, z);
        else OrbitalCannon.strike(w, x, y, z);
        return 1;
    }

    private static int giveOrbitalRod(ServerCommandSource src, String mode) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Only players can receive the rod."));
            return 0;
        }
        p.getInventory().offerOrDrop(makeRod(mode));
        src.sendFeedback(() -> Text.literal("Given: Orbital " + mode + " rod (1 use). Right-click to fire where you look."), false);
        return 1;
    }

    private static int giveDogRod(ServerCommandSource src, ServerPlayerEntity target) {
        target.getInventory().offerOrDrop(makeRod("dog"));
        src.sendFeedback(() -> Text.literal("Gave a Dog Orbital to " + target.getName().getString() + ".").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int clearDogs(ServerCommandSource src) {
        int n = DogOrbital.clearAll();
        src.sendFeedback(() -> Text.literal("Cleared " + n + " dogs. Any in unloaded chunks vanish when those chunks load.").formatted(Formatting.GREEN), true);
        return n;
    }
}
