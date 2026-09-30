// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonConfig;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonMath;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.icon.IconSpec;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import it.ratlab.signalradar.scan.ScanHandler;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.Locator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

/** The server-side detectors of the addons. All read loaded chunks / loaded entities only and return raw hits. */
public final class Detectors {
    /** Hits per addon and refresh (nearest first). */
    public static final int MAX_HITS = 128;
    /** Raw block matches examined per block search. */
    public static final int MAX_RAW_BLOCKS = 32768;
    /** Merge same-block neighbours closer than this into one ore blip. */
    public static final double VEIN_MERGE_DISTANCE = 2.0;
    /** Blocks an entity must have moved since the previous motion sample to be shown. */
    public static final double MOTION_MIN_MOVE = 0.1;
    /** Blip category of still hostiles shown by the motion tracker from the configured radar tier. */
    public static final String MOTION_STILL_CATEGORY = "motion_still";
    /** Colour of still hostiles (dark red). */
    public static final int MOTION_STILL_COLOR = 0x8A2020;

    private static boolean structureCapWarned;

    private Detectors() {}

    /**
     * Runs the detector of {@code addon} for {@code player}.
     *
     * @param radius    detection radius in blocks
     * @param tierRange radar range of the player's tier (unused by radius-based detectors)
     * @param budget    block checks left for this scan (shared by the block detectors of one scan)
     */
    public static List<Hit> run(AddonSettings addon, ServerPlayer player, ServerLevel level, int radius, BlockLocatorScan.Budget budget,
                                long now) {
        AddonDefinition def = addon.def();
        try {
            return switch (def.detector()) {
                case CONTAINER -> containers(addon, player, level, radius, budget);
                case BLOCK_TAG -> blocks(addon, player, level, radius, budget);
                case ENTITY_TAG -> {
                    TagKey<EntityType<?>> tag = tagOf(Registries.ENTITY_TYPE, def);
                    yield entityHits(player, level, radius, e -> e.getType().is(tag));
                }
                case BIOSIGN -> {
                    TagKey<EntityType<?>> tag = tagOf(Registries.ENTITY_TYPE, def);
                    yield entityHits(player, level, radius, e -> e.getType().is(tag) || isBiosign(e));
                }
                case MOTION -> motion(addon, player, level, radius);
                case STRUCTURE_TAG -> structures(addon, player, level, radius, now);
                case MANHOLE -> AddonRegistry.modPresent(def)
                        ? it.ratlab.signalradar.compat.manholes.ManholeDetector.run(player, level, radius) : List.<Hit>of();
                case LOOT -> AddonRegistry.modPresent(def)
                        ? it.ratlab.signalradar.compat.lootr.LootrDetector.run(player, level, radius) : List.<Hit>of();
                case NONE -> List.<Hit>of();
                case TEAM -> AddonRegistry.modPresent(def)
                        ? it.ratlab.signalradar.compat.ftbteams.TeamDetector.run(player, level, radius) : List.<Hit>of();
            };
        } catch (RuntimeException | LinkageError e) {
            SignalRadar.LOGGER.warn("Addon {} detector failed: {}", def.id(), e.toString());
            return List.of();
        }
    }

    private static <T> TagKey<T> tagOf(net.minecraft.resources.ResourceKey<? extends Registry<T>> registry, AddonDefinition def) {
        return TagKey.create(registry, def.tag());
    }

    private static boolean tagNotEmpty(TagKey<Block> tag) {
        return BuiltInRegistries.BLOCK.getTag(tag).map(t -> t.size() > 0).orElse(false);
    }

    // ------------------------------------------------------------------ blocks

    private static List<Hit> containers(AddonSettings a, ServerPlayer player, ServerLevel level, int radius, BlockLocatorScan.Budget budget) {
        Vec3 c = player.position();
        double r2 = (double) radius * radius;
        Set<BlockPos> found = new HashSet<>();
        // Lootr classes are only touched behind the mod check (compat isolation).
        boolean skipLootr = !it.ratlab.signalradar.addon.AddonConfig.includeLootrContainers()
                && net.neoforged.fml.ModList.get().isLoaded("lootr");
        int minCx = (int) Math.floor((c.x - radius) / 16.0);
        int maxCx = (int) Math.floor((c.x + radius) / 16.0);
        int minCz = (int) Math.floor((c.z - radius) / 16.0);
        int maxCz = (int) Math.floor((c.z + radius) / 16.0);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockPos p = e.getKey();
                    if (distSqCentre(p, c) <= r2 && exposesItems(level, p, e.getValue())
                            && !(skipLootr && it.ratlab.signalradar.compat.lootr.LootrDetector.isLootr(e.getValue()))) {
                        found.add(p);
                    }
                }
            }
        }
        if (a.def().tag() != null) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, a.def().tag());
            if (tagNotEmpty(tag)) {
                found.addAll(BlockLocatorScan.findAll(level, c, radius, s -> s.is(tag), budget, MAX_RAW_BLOCKS));
            }
        }
        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble((BlockPos p) -> distSqCentre(p, c)).thenComparing(BlockPos::asLong));
        List<Hit> hits = new ArrayList<>();
        for (BlockPos p : sorted) {
            if (hits.size() >= MAX_HITS) {
                break;
            }
            Block block = level.getBlockState(p).getBlock();
            hits.add(new Hit(p.getX() + "," + p.getY() + "," + p.getZ(), block.getName(),
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, blockItemIcon(block)));
        }
        return hits;
    }

    /** Icon of a found container-like block: its block item, the chest when it has none. */
    public static String blockItemIcon(Block block) {
        net.minecraft.world.item.Item item = block.asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return IconSpec.CHEST;
        }
        return IconSpec.item(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static String entityIcon(Entity e) {
        return IconSpec.entity(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    private static boolean exposesItems(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be instanceof Container) {
            return true;
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, d) != null) {
                return true;
            }
        }
        return false;
    }

    private static double distSqCentre(BlockPos p, Vec3 c) {
        double dx = p.getX() + 0.5 - c.x;
        double dy = p.getY() + 0.5 - c.y;
        double dz = p.getZ() + 0.5 - c.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Tag blocks (ore and custom {@code block_tag} addons): one blip per vein, coloured by ore material when asked. */
    private static List<Hit> blocks(AddonSettings a, ServerPlayer player, ServerLevel level, int radius, BlockLocatorScan.Budget budget) {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, a.def().tag());
        if (!tagNotEmpty(tag)) {
            return List.of();
        }
        Vec3 c = player.position();
        Predicate<BlockState> match = s -> s.is(tag);
        List<BlockPos> all = BlockLocatorScan.findAll(level, c, radius, match, budget, MAX_RAW_BLOCKS);
        List<int[]> points = new ArrayList<>(all.size());
        for (BlockPos p : all) {
            points.add(new int[] {p.getX(), p.getY(), p.getZ(), BuiltInRegistries.BLOCK.getId(level.getBlockState(p).getBlock())});
        }
        List<Hit> hits = new ArrayList<>();
        for (int i : AddonMath.mergeClose(points, VEIN_MERGE_DISTANCE)) {
            if (hits.size() >= MAX_HITS) {
                break;
            }
            BlockPos p = all.get(i);
            BlockState state = level.getBlockState(p);
            int color = 0;
            if (a.def().useMapColor()) {
                color = OreColorResolver.color(state.getBlock());
            }
            hits.add(new Hit(p.getX() + "," + p.getY() + "," + p.getZ(), state.getBlock().getName(),
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, color, IconSpec.block(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())));
        }
        return hits;
    }

    // ------------------------------------------------------------------ entities

    /** Passive mobs (creatures, ambient, water animals, axolotls) and villagers; monsters only through the tag. */
    private static boolean isBiosign(Entity e) {
        MobCategory cat = e.getType().getCategory();
        return switch (cat) {
            case CREATURE, AMBIENT, WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> true;
            case MONSTER -> false;
            case MISC -> e instanceof AbstractVillager;
        };
    }

    private static List<Hit> entityHits(ServerPlayer player, ServerLevel level, int radius, Predicate<Entity> accept) {
        Vec3 c = player.position();
        double r2 = (double) radius * radius;
        List<Entity> list = level.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator() && horizontalSq(e, c) <= r2 && accept.test(e));
        list.sort(Comparator.comparingDouble((Entity e) -> horizontalSq(e, c)).thenComparing(Entity::getUUID));
        List<Hit> hits = new ArrayList<>();
        for (Entity e : list) {
            if (hits.size() >= MAX_HITS) {
                break;
            }
            hits.add(new Hit(e.getUUID().toString(), e.getName(), e.getX(), e.getY(), e.getZ(), 0, entityIcon(e)));
        }
        return hits;
    }

    private static double horizontalSq(Entity e, Vec3 c) {
        double dx = e.getX() - c.x;
        double dz = e.getZ() - c.z;
        return dx * dx + dz * dz;
    }

    /**
     * Hostile mobs (and the trackable tag) that moved since this player's previous motion sample; with a held radar of
     * tier {@code stationaryFromTier} or more, still ones too (category {@code motion_still}, after the moving ones).
     */
    private static List<Hit> motion(AddonSettings a, ServerPlayer player, ServerLevel level, int radius) {
        TagKey<EntityType<?>> tag = tagOf(Registries.ENTITY_TYPE, a.def());
        Vec3 c = player.position();
        double r2 = (double) radius * radius;
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator() && horizontalSq(e, c) <= r2
                        && (e.getType().getCategory() == MobCategory.MONSTER || e.getType().is(tag)));
        List<MotionTracker.Sample> samples = new ArrayList<>(candidates.size());
        for (Entity e : candidates) {
            samples.add(new MotionTracker.Sample(e.getUUID(), e.getX(), e.getY(), e.getZ()));
        }
        Set<UUID> moving = MotionTracker.INSTANCE.update(player.getUUID(), samples, MOTION_MIN_MOVE);
        ItemStack held = ScanHandler.heldRadar(player);
        boolean withStill = !held.isEmpty()
                && AddonMath.showsStationary(RadarItem.tier(held), AddonConfig.stationaryFromTier());
        if (!withStill) {
            candidates.removeIf(e -> !moving.contains(e.getUUID()));
        }
        // moving first, then still ones; each nearest first
        candidates.sort(Comparator.comparingInt((Entity e) -> moving.contains(e.getUUID()) ? 0 : 1)
                .thenComparingDouble(e -> horizontalSq(e, c)).thenComparing(Entity::getUUID));
        List<Hit> hits = new ArrayList<>();
        for (Entity e : candidates) {
            if (hits.size() >= MAX_HITS) {
                break;
            }
            boolean isMoving = moving.contains(e.getUUID());
            hits.add(new Hit(e.getUUID().toString(), e.getName(), e.getX(), e.getY(), e.getZ(), isMoving ? 0 : MOTION_STILL_COLOR,
                    entityIcon(e), isMoving ? null : MOTION_STILL_CATEGORY));
        }
        return hits;
    }

    // ------------------------------------------------------------------ structures

    /** Nearest of every structure in the tag (capped), through the shared cache + lookup queue; range = {@code radius}. */
    private static List<Hit> structures(AddonSettings a, ServerPlayer player, ServerLevel level, int radius, long now) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<HolderSet.Named<Structure>> tag = registry.getTag(TagKey.create(Registries.STRUCTURE, a.def().tag()));
        if (tag.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (Holder<Structure> h : tag.get()) {
            h.unwrapKey().ifPresent(k -> ids.add(k.location()));
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        int cap = SignalRadarConfig.maxScannableStructures();
        if (ids.size() > cap) {
            if (!structureCapWarned) {
                structureCapWarned = true;
                SignalRadar.LOGGER.warn("Tag {} has {} structures, only the first {} are scanned (maxScannableStructures)", a.def().tag(),
                        ids.size(), cap);
            }
            ids.subList(cap, ids.size()).clear();
        }
        StructureCacheData data = StructureCacheData.get(level.getServer());
        int chunks = Math.max(1, radius / 16);
        Vec3 c = player.position();
        List<Hit> hits = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Optional<BlockPos> pos = StructureLookupService.INSTANCE.get(data, level.dimension(),
                    new Locator.Structure(id, false, chunks), player.blockPosition(), now);
            if (pos.isEmpty()) {
                continue;
            }
            double dx = pos.get().getX() + 0.5 - c.x;
            double dz = pos.get().getZ() + 0.5 - c.z;
            if (dx * dx + dz * dz > (double) radius * radius) {
                continue;
            }
            hits.add(new Hit(id.toString(), Component.literal(AddonMath.prettify(id.getPath())), pos.get().getX() + 0.5, c.y,
                    pos.get().getZ() + 0.5, 0, IconSpec.STRUCTURE));
        }
        return hits;
    }
}
