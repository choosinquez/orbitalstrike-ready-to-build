package dev.orbitalarsenal;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Port of the OrbitalCannon plugin (com.orbital.OrbitalCannon and its 4 inner classes). */
final class OrbitalCannon {
    private static final Random RNG = new Random();

    private OrbitalCannon() {}

    // ------------------------------------------------------------ command versions

    /** /orbitalstrike: instant strike straight down. */
    static void strike(ServerWorld w, double x, double y, double z) {
        beam(w, x, y, z);
        tripleLightning(w, x, y, z);
        Fx.sound(w, x, y, z, "entity.generic.explode", 10.0f, 0.6f);
        Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 3, 1, 1, 1, 0);
        Fx.explode(w, x, y, z, 12.0f);
    }

    /** /orbitalnuke */
    static void nuke(ServerWorld w, double x, double y, double z) {
        nukeTnt(w, x, y, z);
    }

    // ------------------------------------------------------------ rod versions

    /** Rod "strike": a TNT drills a 3-radius shaft from the target to the bottom of the world. */
    static void strikeTnt(ServerWorld w, double tx, double ty, double tz) {
        final int minY = w.getBottomY();
        final int cx = (int) Math.floor(tx);
        final int cz = (int) Math.floor(tz);
        beam(w, tx, ty, tz);
        Fx.sound(w, tx, ty, tz, "entity.firework_rocket.launch", 10.0f, 0.6f);

        final TntEntity tnt = new TntEntity(w, tx, ty + 1.0, tz, null);
        tnt.setFuse(100000);
        tnt.setNoGravity(true);
        w.spawnEntity(tnt);

        final int[] y = {(int) Math.floor(ty)};
        Scheduler.repeat(0, 1, () -> {
            if (y[0] < minY) {
                tnt.discard();
                double ey = minY + 1;
                Fx.sound(w, tx, ey, tz, "entity.generic.explode", 12.0f, 0.5f);
                Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, tx, ey, tz, 6, 2, 2, 2, 0);
                Fx.explode(w, tx, ey, tz, 16.0f);
                return true;
            }
            int low = Math.max(y[0] - 8 + 1, minY);
            double midY = (y[0] + low) / 2.0;
            double halfH = (y[0] - low) / 2.0 + 2.0;

            // 100 damage to everything in the shaft slice
            Box box = new Box(tx - 3, midY - halfH, tz - 3, tx + 3, midY + halfH, tz + 3);
            for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class, box, e -> true)) {
                le.damage(w, w.getDamageSources().generic(), 100.0f);
            }
            // carve a radius-3 cylinder (no physics, no drops, bedrock stays)
            for (int yy = y[0]; yy >= low; yy--) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > 9) continue;
                        BlockPos bp = new BlockPos(cx + dx, yy, cz + dz);
                        BlockState bs = w.getBlockState(bp);
                        if (bs.isAir() || bs.isOf(Blocks.BEDROCK)) continue;
                        w.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            tnt.requestTeleport(tx, midY, tz);
            Fx.explode(w, tx, midY, tz, 4.0f);
            Fx.particles(w, ParticleTypes.FLAME, tx, midY, tz, 8, 0.4, 2.0, 0.4, 0.02);
            y[0] = low - 1;
            return false;
        });
    }

    /** Rod "nuke" and /orbitalnuke: announce, then drop the bomb from the sky. */
    static void nukeTnt(ServerWorld w, double x, double y, double z) {
        announce("ORBITAL NUKE incoming at " + (int) x + ", " + (int) y + ", " + (int) z + "!");
        dropNuke(w, x, y, z);
    }

    private static void announce(String msg) {
        if (OrbitalArsenal.server == null) return;
        OrbitalArsenal.server.getPlayerManager().broadcast(Text.literal(msg).formatted(Formatting.RED, Formatting.BOLD), false);
    }

    private static void dropNuke(ServerWorld w, double tx, double ty, double tz) {
        final double startY = Math.min(ty + 160.0, (w.getBottomY() + w.getHeight()) - 2);
        final TntEntity bomb = new TntEntity(w, tx, startY, tz, null);
        bomb.setFuse(100000);
        bomb.setNoGravity(true);
        w.spawnEntity(bomb);

        final double[] y = {startY};
        final int[] tick = {0};
        Scheduler.repeat(0, 1, () -> {
            tick[0]++;
            y[0] -= 3.0;
            if (bomb.isRemoved()) return true;
            if (y[0] <= ty) {
                bomb.discard();
                explodeNuke(w, tx, ty, tz);
                return true;
            }
            bomb.requestTeleport(tx, y[0], tz);
            Fx.particles(w, ParticleTypes.FLAME, tx, y[0], tz, 8, 0.3, 0.3, 0.3, 0.02);
            Fx.particles(w, ParticleTypes.CLOUD, tx, y[0], tz, 6, 0.4, 0.8, 0.4, 0.01);
            if (tick[0] % 4 == 0) Fx.sound(w, tx, y[0], tz, "entity.firework_rocket.launch", 6.0f, 0.5f);
            return false;
        });
    }

    private static void explodeNuke(ServerWorld w, double tx, double ty, double tz) {
        Fx.sound(w, tx, ty, tz, "entity.generic.explode", 16.0f, 0.4f);
        Fx.particles(w, ParticleTypes.EXPLOSION_EMITTER, tx, ty, tz, 10, 4, 2, 4, 0);
        // the big central blast (power 20)
        Fx.explode(w, tx, ty, tz, 20.0f);
        // 40 scattered TNT, power 5 each, random fuse 5..44
        for (int i = 0; i < 40; i++) {
            double x = tx + (RNG.nextDouble() - 0.5) * 24.0;
            double y = ty + RNG.nextDouble() * 12.0;
            double z = tz + (RNG.nextDouble() - 0.5) * 24.0;
            int fuse = 5 + RNG.nextInt(40);
            TntEntity t = new TntEntity(w, x, y, z, null);
            t.setFuse(fuse);
            w.spawnEntity(t);
            Scheduler.later(fuse - 1, () -> {
                if (t.isRemoved()) return;
                double ex = t.getX(), ey = t.getY(), ez = t.getZ();
                t.discard();
                Fx.explode(w, ex, ey, ez, 5.0f);
            });
        }
        // rising mushroom cloud for 40 ticks
        final int[] i = {0};
        Scheduler.repeat(0, 1, () -> {
            i[0]++;
            if (i[0] > 40) return true;
            double h = Math.min(i[0] * 1.5, 36.0);
            Fx.particles(w, ParticleTypes.CLOUD, tx, ty + h, tz, 60, 1.5, 0.6, 1.5, 0.02);
            if (h >= 30.0) {
                double r = Math.max(0.0, Math.min((i[0] - 20) * 0.4, 9.0));
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8.0;
                    Fx.particles(w, ParticleTypes.FLAME, tx + Math.cos(a) * r, ty + h, tz + Math.sin(a) * r,
                            6, 0.8, 0.5, 0.8, 0.01);
                }
            }
            return false;
        });
    }

    // ------------------------------------------------------------ effects

    /** Column of END_ROD particles from the sky down to the target. */
    static void beam(ServerWorld w, double x, double y, double z) {
        double top = w.getBottomY() + w.getHeight();
        for (double py = top; py >= y; py -= 1.0) {
            Fx.particles(w, ParticleTypes.END_ROD, x, py, z, 1, 0.05, 0.0, 0.05, 0.0);
        }
    }

    /** One lightning bolt now, two more 4 ticks apart. */
    static void tripleLightning(ServerWorld w, double x, double y, double z) {
        Fx.lightning(w, x, y, z);
        Fx.sound(w, x, y, z, "entity.lightning_bolt.thunder", 10.0f, 0.8f);
        final int[] n = {0};
        Scheduler.repeat(4, 4, () -> {
            n[0]++;
            Fx.lightning(w, x, y, z);
            return n[0] >= 2;
        });
    }
}
