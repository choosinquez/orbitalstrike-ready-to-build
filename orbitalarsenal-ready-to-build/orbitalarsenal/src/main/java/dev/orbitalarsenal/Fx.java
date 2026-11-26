package dev.orbitalarsenal;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Small helpers so the rest of the code reads like the Bukkit version. */
final class Fx {
    private Fx() {}

    /** Plays a vanilla sound by id, e.g. "entity.generic.explode". */
    static void sound(ServerWorld w, double x, double y, double z, String vanillaId, float vol, float pitch) {
        w.playSound(null, x, y, z, SoundEvent.of(Identifier.ofVanilla(vanillaId)), SoundCategory.MASTER, vol, pitch);
    }

    static void particles(ServerWorld w, ParticleEffect p, double x, double y, double z,
                          int count, double dx, double dy, double dz, double speed) {
        w.spawnParticles(p, x, y, z, count, dx, dy, dz, speed);
    }

    static void lightning(ServerWorld w, double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w, SpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setPosition(x, y, z);
        w.spawnEntity(bolt);
    }

    /** world.createExplosion(loc, power, setFire=false, breakBlocks=true) */
    static void explode(ServerWorld w, double x, double y, double z, float power) {
        w.createExplosion(null, x, y, z, power, false, World.ExplosionSourceType.TNT);
    }
}
