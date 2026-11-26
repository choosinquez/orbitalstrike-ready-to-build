package dev.orbitalarsenal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Port of the DogOrbital plugin. Dogs are ordinary wolves marked with entity command tags, so the
 * marking is saved with the entity and survives restarts (Bukkit's PersistentDataContainer equivalent).
 */
final class DogOrbital {
    private static final String OWNER_TAG = "orbitalarsenal_dog_owner_";
    private static final String BORN_TAG = "orbitalarsenal_dog_born_";

    private static final class Dog {
        final WolfEntity wolf;
        final ServerWorld world;
        final UUID owner;

        Dog(WolfEntity wolf, ServerWorld world, UUID owner) {
            this.wolf = wolf;
            this.world = world;
            this.owner = owner;
        }
    }

    private static final Map<UUID, Dog> DOGS = new HashMap<>();

    private DogOrbital() {}

    // ------------------------------------------------------------ releasing

    static void release(ServerPlayerEntity owner, ServerWorld w) {
        int count = OrbitalArsenal.config.dogsPerRod;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double bx = owner.getX(), by = owner.getY(), bz = owner.getZ();
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            double x = bx + rnd.nextDouble(-8.0, 8.0);
            double y = by + 0.5;
            double z = bz + rnd.nextDouble(-8.0, 8.0);
            if (!passable(w, x, y, z) || !passable(w, x, y + 1, z)) {
                x = bx;
                y = by;
                z = bz;
            }
            WolfEntity wolf = EntityType.WOLF.create(w, SpawnReason.TRIGGERED);
            if (wolf == null) continue;
            wolf.refreshPositionAndAngles(x, y, z, rnd.nextFloat() * 360f, 0f);
            wolf.setTamed(true, true);
            wolf.setOwner(owner);
            wolf.addCommandTag(OWNER_TAG + owner.getUuid());
            wolf.addCommandTag(BORN_TAG + now);
            wolf.setPersistent(); // never despawn
            // permanent Strength II + Speed II (no armour: the dogs can be killed)
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, 1));
            wolf.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, 1));
            w.spawnEntity(wolf);
            // ENTITY_LOAD also registers it, but be explicit in case the event fires later
            DOGS.putIfAbsent(wolf.getUuid(), new Dog(wolf, w, owner.getUuid()));
        }
        Fx.sound(w, bx, by, bz, "entity.wolf.howl", 3.0f, 1.0f);
        owner.sendMessage(Text.literal("DOG ORBITAL! ").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal(count + " dogs are yours - they hunt everyone else.").formatted(Formatting.GRAY)), false);
    }

    private static boolean passable(ServerWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState s = w.getBlockState(bp);
        return s.getCollisionShape(w, bp).isEmpty();
    }

    // ------------------------------------------------------------ tracking (load/unload)

    /** Called for every wolf that loads (including freshly spawned ones and after a restart). */
    static void onWolfLoad(WolfEntity wolf, ServerWorld world) {
        UUID owner = null;
        long born = 0L;
        for (String tag : wolf.getCommandTags()) {
            try {
                if (tag.startsWith(OWNER_TAG)) owner = UUID.fromString(tag.substring(OWNER_TAG.length()));
                else if (tag.startsWith(BORN_TAG)) born = Long.parseLong(tag.substring(BORN_TAG.length()));
            } catch (IllegalArgumentException ignored) {
                // bad tag: ignore
            }
        }
        if (owner == null) return;
        // released before the last /cleardogs (sat in an unloaded chunk): remove it now
        if (born <= OrbitalArsenal.config.clearedAt) {
            Scheduler.later(1, wolf::discard);
            return;
        }
        DOGS.put(wolf.getUuid(), new Dog(wolf, world, owner));
    }

    static void onWolfUnload(WolfEntity wolf) {
        DOGS.remove(wolf.getUuid());
    }

    // ------------------------------------------------------------ hunting (call every 10 ticks)

    static void tick() {
        Iterator<Dog> it = DOGS.values().iterator();
        while (it.hasNext()) {
            Dog d = it.next();
            if (d.wolf.isRemoved() || !d.wolf.isAlive()) {
                it.remove();
                continue;
            }
            ServerPlayerEntity best = null;
            double bestD = 128.0 * 128.0;
            for (ServerPlayerEntity p : d.world.getPlayers()) {
                if (p.getUuid().equals(d.owner) || !p.isAlive()) continue;
                if (p.isSpectator() || p.isCreative()) continue;
                double dist = d.wolf.squaredDistanceTo(p);
                if (dist < bestD) {
                    bestD = dist;
                    best = p;
                }
            }
            if (best != null) d.wolf.setTarget(best);
        }
    }

    // ------------------------------------------------------------ clearing

    /** Removes every loaded dog now; dogs in unloaded chunks vanish when their chunk loads. */
    static int clearAll() {
        int n = 0;
        for (Dog d : DOGS.values()) {
            if (!d.wolf.isRemoved()) {
                d.wolf.discard();
                n++;
            }
        }
        DOGS.clear();
        OrbitalArsenal.config.clearedAt = System.currentTimeMillis();
        OrbitalArsenal.config.save();
        return n;
    }

    static void reset() {
        DOGS.clear();
    }
}
