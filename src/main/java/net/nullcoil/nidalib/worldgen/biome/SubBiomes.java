package net.nullcoil.nidalib.worldgen.biome;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.nullcoil.nidalib.Nidalib;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Places biomes inside other biomes, independent of their climate parameters.
 * <pre>{@code
 * SubBiomes.add(MY_OUTER_BIOME, MY_INNER_BIOME, SubBiomeRule.core(0.5));
 * }</pre>
 * The parent must already generate somewhere (vanilla, datapack, or Fabric's
 * biome API). The sub-biome only needs to be registered as a biome; it does not
 * need climate parameters of its own.
 */
public final class SubBiomes {
    private record Entry(ResourceKey<Biome> parent, ResourceKey<Biome> child, SubBiomeRule rule) {}

    private record Resolved(Holder<Biome> child, SubBiomeRule rule) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    // Guards against accidental cycles (A inside B inside A)
    private static final int MAX_DEPTH = 8;

    // Parent key -> resolved children for the running server. Rebuilt on every server start,
    // since each world (singleplayer especially) has its own biome registry.
    private static volatile Map<ResourceKey<Biome>, List<Resolved>> resolved = Map.of();

    // Every biome that is some parent's sub-biome, for telling top-level parents apart
    private static volatile Set<ResourceKey<Biome>> children = Set.of();

    private SubBiomes() {}

    /** Call during mod initialization. Rules are checked in the order they were added. */
    public static synchronized void add(ResourceKey<Biome> parent, ResourceKey<Biome> child, SubBiomeRule rule) {
        ENTRIES.add(new Entry(parent, child, rule));
    }

    /** Called by Nidalib's initializer. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                resolve(server.registryAccess().lookupOrThrow(Registries.BIOME)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            resolved = Map.of();
            children = Set.of();
        });
    }

    private static synchronized void resolve(HolderLookup.RegistryLookup<Biome> biomes) {
        Map<ResourceKey<Biome>, List<Resolved>> map = new ConcurrentHashMap<>();
        Set<ResourceKey<Biome>> childKeys = new HashSet<>();
        for (Entry entry : ENTRIES) {
            var child = biomes.get(entry.child());
            if (child.isEmpty()) {
                Nidalib.LOGGER.warn("Sub-biome {} is not registered, skipping", entry.child());
                continue;
            }
            map.computeIfAbsent(entry.parent(), k -> new ArrayList<>()).add(new Resolved(child.get(), entry.rule()));
            childKeys.add(entry.child());
        }
        resolved = map;
        children = childKeys;
    }

    /** Whether any sub-biome is registered inside this biome (cheap, no climate sampling). */
    public static boolean hasSubBiomes(Holder<Biome> biome) {
        Map<ResourceKey<Biome>, List<Resolved>> map = resolved;
        if (map.isEmpty()) return false;
        return biome.unwrapKey().map(map::containsKey).orElse(false);
    }

    /**
     * Called from the biome source after vanilla picks a biome.
     * Returns the sub-biome to use instead, or null to keep vanilla's pick.
     */
    @Nullable
    public static Holder<Biome> pick(Holder<Biome> picked, Climate.TargetPoint point,
                                     Climate.ParameterList<Holder<Biome>> parameters) {
        Map<ResourceKey<Biome>, List<Resolved>> map = resolved;
        if (map.isEmpty()) return null;

        var key = picked.unwrapKey();
        if (key.isEmpty()) return null;
        List<Resolved> children = map.get(key.get());
        if (children == null) return null;

        // Closest climate point of the parent, and of everything else
        long parentDistance = Long.MAX_VALUE;
        long nextDistance = Long.MAX_VALUE;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : parameters.values()) {
            long distance = distance(pair.getFirst(), point);
            if (pair.getSecond().is(key.get())) {
                parentDistance = Math.min(parentDistance, distance);
            } else {
                nextDistance = Math.min(nextDistance, distance);
            }
        }

        // Sub-biomes can have sub-biomes of their own. Every level is judged against
        // the same climate context (measured from the biome vanilla placed), so nested
        // rules need decreasing thresholds: e.g. outer core(0.7), inner core(0.4).
        SubBiomeRule.Context context = new SubBiomeRule.Context(parentDistance, nextDistance);
        Holder<Biome> result = null;
        for (int depth = 0; depth < MAX_DEPTH && children != null; depth++) {
            Holder<Biome> next = null;
            for (Resolved child : children) {
                if (child.rule().test(context)) {
                    next = child.child();
                    break;
                }
            }
            if (next == null) break;
            result = next;
            children = next.unwrapKey().map(map::get).orElse(null);
        }
        return result;
    }

    /**
     * Applies sub-biomes to a chunk's stored biomes. For world generators that fill biomes
     * without asking the biome source (C2ME's GPU world generation copies the climate table
     * and picks biomes itself), so the {@code getNoiseBiome} hook never ran for this chunk.
     *
     * <p>Only touches cells holding a top-level parent (e.g. Soul Sand Valley), so cells
     * that already got their sub-biome are left alone and running this twice is harmless.
     */
    public static void applyToChunk(ChunkAccess chunk, BiomeSource source, Climate.Sampler sampler) {
        Map<ResourceKey<Biome>, List<Resolved>> map = resolved;
        if (map.isEmpty()) return;
        if (!(source instanceof ClimateParameterAccess access)) return;

        // Cheap check first: skip chunks with no top-level parent anywhere in them
        boolean any = false;
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getBiomes().maybeHas(SubBiomes::isTopLevelParent)) {
                any = true;
                break;
            }
        }
        if (!any) return;

        Climate.ParameterList<Holder<Biome>> parameters = access.nidalib$parameters();
        // While a section refills, getNoiseBiome still reads its old (stored) biomes
        chunk.fillBiomesFromNoise((x, y, z, s) -> {
            Holder<Biome> stored = chunk.getNoiseBiome(x, y, z);
            if (!isTopLevelParent(stored)) return stored;
            Holder<Biome> sub = pick(stored, s.sample(x, y, z), parameters);
            return sub != null ? sub : stored;
        }, sampler);
    }

    /** Has sub-biomes, and isn't a sub-biome itself (e.g. Soul Sand Valley, not the Crags). */
    private static boolean isTopLevelParent(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> resolved.containsKey(key) && !children.contains(key))
                .orElse(false);
    }

    /** Adds every sub-biome whose parent this biome source can produce. */
    public static Stream<Holder<Biome>> withSubBiomes(Stream<Holder<Biome>> possible) {
        Map<ResourceKey<Biome>, List<Resolved>> map = resolved;
        if (map.isEmpty()) return possible;

        List<Holder<Biome>> all = new ArrayList<>(possible.toList());
        // Walk down nested sub-biomes too (index loop, since the list grows)
        for (int i = 0; i < all.size(); i++) {
            all.get(i).unwrapKey().map(map::get).ifPresent(children ->
                    children.forEach(child -> {
                        if (!all.contains(child.child())) all.add(child.child());
                    }));
        }
        return all.stream();
    }

    /**
     * How deep a spot is inside its climate-placed biome: 0 at that biome's climate center,
     * approaching 1 at its border with another biome. This is the same value
     * {@link SubBiomeRule.Context#edgeRatio()} sees, so nested sub-biomes can tell how far
     * from their own edge a spot is: e.g. a sub-biome placed with {@code core(0.4)} spans
     * ratios 0 to 0.4, center to edge.
     *
     * <p>Takes quart (biome cell) coordinates, like {@code getNoiseBiome}.
     *
     * @return the ratio, or NaN if the biome source isn't climate-based
     */
    public static double edgeRatio(BiomeSource source, Climate.Sampler sampler, int qx, int qy, int qz) {
        if (!(source instanceof ClimateParameterAccess access)) return Double.NaN;
        Climate.ParameterList<Holder<Biome>> parameters = access.nidalib$parameters();
        Climate.TargetPoint point = sampler.sample(qx, qy, qz);

        // Nearest climate point overall: that's the climate-placed biome
        Holder<Biome> placed = null;
        long best = Long.MAX_VALUE;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : parameters.values()) {
            long d = distance(pair.getFirst(), point);
            if (d < best) {
                best = d;
                placed = pair.getSecond();
            }
        }
        if (placed == null) return Double.NaN;

        long parentDistance = Long.MAX_VALUE, nextDistance = Long.MAX_VALUE;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : parameters.values()) {
            long d = distance(pair.getFirst(), point);
            if (pair.getSecond().equals(placed)) parentDistance = Math.min(parentDistance, d);
            else nextDistance = Math.min(nextDistance, d);
        }
        return new SubBiomeRule.Context(parentDistance, nextDistance).edgeRatio();
    }

    // Same metric vanilla uses to pick biomes: squared distance on each axis, plus offset.
    private static long distance(Climate.ParameterPoint p, Climate.TargetPoint t) {
        return square(axis(p.temperature(), t.temperature()))
                + square(axis(p.humidity(), t.humidity()))
                + square(axis(p.continentalness(), t.continentalness()))
                + square(axis(p.erosion(), t.erosion()))
                + square(axis(p.depth(), t.depth()))
                + square(axis(p.weirdness(), t.weirdness()))
                + square(p.offset());
    }

    private static long axis(Climate.Parameter range, long value) {
        if (value > range.max()) return value - range.max();
        if (value < range.min()) return range.min() - value;
        return 0;
    }

    private static long square(long v) {
        return v * v;
    }
}