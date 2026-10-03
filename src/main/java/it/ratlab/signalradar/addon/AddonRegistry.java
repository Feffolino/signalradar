// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition.Detector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * All addon definitions: the built-ins plus custom ones added through {@link #registerCustom}.
 * <p>
 * <b>Timing.</b> Addon items are registered in this mod's {@link RegisterEvent} listener for {@code ForgeRegistries.Keys.ITEMS}.
 * {@code registerCustom} works from any point after the class loads (mod construction, e.g. KubeJS startup scripts)
 * until that listener starts; from then on the registry is frozen and calls log an error and return false.
 * Definitions with a {@code requiredModId} whose mod is not loaded stay in {@link #all()} but get no item
 * ({@link #isActive} is false).
 */
public final class AddonRegistry {
    public static final ResourceLocation CONTAINER = SignalRadar.id("addon_container");
    public static final ResourceLocation ORE = SignalRadar.id("addon_ore");
    public static final ResourceLocation BIOSIGN = SignalRadar.id("addon_biosign");
    public static final ResourceLocation STRUCTURE = SignalRadar.id("addon_structure");
    public static final ResourceLocation MOTION = SignalRadar.id("addon_motion");
    public static final ResourceLocation MANHOLE = SignalRadar.id("addon_manhole");
    public static final ResourceLocation LOOTR = SignalRadar.id("addon_lootr");
    /** Id of the Lootr addon before it was renamed; mapped when a radar is decoded. */
    public static final ResourceLocation LEGACY_LOOT = SignalRadar.id("addon_loot");
    public static final ResourceLocation TEAM = SignalRadar.id("addon_team");
    public static final ResourceLocation BATTERY = SignalRadar.id("addon_battery");
    /** Max stack size of the battery addon item (and so of one battery slot). */
    public static final int BATTERY_STACK = 8;

    private static final Map<ResourceLocation, AddonDefinition> DEFS = new LinkedHashMap<>();
    private static final List<AddonDefinition> BUILTINS = new ArrayList<>();
    private static boolean frozen;
    private static final List<Runnable> PROVIDERS = new ArrayList<>();

    static {
        builtin(new AddonDefinition(CONTAINER, Detector.CONTAINER, 0, 24, 48, 10, 0xE0A040, 10, "container", null,
                SignalRadar.id("container_targets"), false));
        builtin(new AddonDefinition(ORE, Detector.BLOCK_TAG, 1, 16, 32, 5, 0xB0B0B0, 15, "ore", null,
                SignalRadar.id("ore_targets"), true));
        builtin(new AddonDefinition(BIOSIGN, Detector.BIOSIGN, 1, 32, 64, 1, 0x4CD964, 10, "biosign", null,
                SignalRadar.id("biosign"), false));
        // radius 0..0 = the radar's tier range; results come from the structure cache, refreshed every 5 s.
        builtin(new AddonDefinition(STRUCTURE, Detector.STRUCTURE_TAG, 1, 0, 0, 5, 0x40C0FF, 10, "structure", null,
                SignalRadar.id("scannable_structures"), false));
        builtin(new AddonDefinition(MOTION, Detector.MOTION, 2, 24, 48, 1, 0xFF3030, 20, "motion", null,
                SignalRadar.id("trackable"), false));
        // Compat addons: only get an item when their mod is loaded. Team radius = whole dimension (config maximum, 100000).
        builtin(new AddonDefinition(MANHOLE, Detector.MANHOLE, 0, 96, 160, 5, 0xC8A050, 5, "manhole", "manholes", null, false));
        builtin(new AddonDefinition(LOOTR, Detector.LOOTR, 2, 48, 96, 10, 0xB060FF, 10, "lootr", "lootr", null, false));
        // Battery: no detection, no energy cost, every tier; up to BATTERY_STACK in one slot, each adds capacity.
        builtin(new AddonDefinition(BATTERY, Detector.NONE, 0, 0, 0, 3600, 0xF0D040, 0, "battery", null, null, false, null, true));
        builtin(new AddonDefinition(TEAM, Detector.TEAM, 1, AddonMath.WHOLE_DIMENSION_RADIUS, AddonMath.WHOLE_DIMENSION_RADIUS, 1, 0x40E0D0, 5, "team", "ftbteams", null, false));
    }

    private AddonRegistry() {}

    private static void builtin(AddonDefinition def) {
        BUILTINS.add(def);
        DEFS.put(def.id(), def);
    }

    /**
     * Adds a custom addon. Public API for other mods / KubeJS startup scripts.
     *
     * @return true when added; false (and an error in the log, no exception) when the registry is already frozen or the
     *         id is taken
     */
    public static synchronized boolean registerCustom(AddonDefinition def) {
        if (frozen) {
            SignalRadar.LOGGER.error("Addon {} registered too late (item registration already ran); ignored. "
                    + "Register custom addons during mod construction / startup scripts.", def.id());
            return false;
        }
        if (DEFS.containsKey(def.id())) {
            SignalRadar.LOGGER.error("Addon {} is already registered; ignored", def.id());
            return false;
        }
        DEFS.put(def.id(), def);
        return true;
    }

    /**
     * Adds a provider that is run once, inside this mod's item {@link RegisterEvent}, right before the registry freezes:
     * the last point where {@link #registerCustom} works. Used by the KubeJS integration to post the startup event
     * {@code SignalRadarEvents.registerAddons}; other mods can use it when their definitions are only known late.
     * Exceptions of a provider are logged, never thrown.
     */
    public static synchronized void addProvider(Runnable provider) {
        if (frozen) {
            SignalRadar.LOGGER.error("Addon provider added after item registration; ignored");
            return;
        }
        PROVIDERS.add(provider);
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /** Every definition collected so far (built-ins first), including ones whose required mod is missing. */
    public static synchronized List<AddonDefinition> all() {
        return List.copyOf(DEFS.values());
    }

    /** The built-in definitions only (they have server config entries). */
    public static List<AddonDefinition> builtins() {
        return List.copyOf(BUILTINS);
    }

    public static synchronized Optional<AddonDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFS.get(id));
    }

    public static boolean isBuiltin(AddonDefinition def) {
        return BUILTINS.contains(def);
    }

    /** Required mod present (or none required). */
    public static boolean modPresent(AddonDefinition def) {
        String mod = def.requiredModId();
        if (mod == null) {
            return true;
        }
        ModList list = ModList.get();
        return list != null && list.isLoaded(mod);
    }

    /** The addon exists in this game (its required mod, if any, is loaded). */
    public static boolean isActive(AddonDefinition def) {
        return modPresent(def);
    }

    /** Active definitions only. */
    public static List<AddonDefinition> active() {
        return all().stream().filter(AddonRegistry::isActive).toList();
    }

    public static Optional<AddonDefinition> forStack(ItemStack stack) {
        return stack.getItem() instanceof AddonItem item ? get(item.defId()) : Optional.empty();
    }

    /** The registered item of an addon (empty before registration / when the required mod is missing). */
    public static Optional<Item> item(ResourceLocation id) {
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            return Optional.empty();
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item instanceof AddonItem ? Optional.of(item) : Optional.empty();
    }

    /** Mod bus listener: freezes the registry and registers one {@link AddonItem} per active definition. */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            return;
        }
        List<Runnable> providers;
        synchronized (AddonRegistry.class) {
            providers = List.copyOf(PROVIDERS);
        }
        for (Runnable provider : providers) {
            try {
                provider.run();
            } catch (RuntimeException e) {
                SignalRadar.LOGGER.error("Addon provider failed", e);
            }
        }
        List<AddonDefinition> defs;
        synchronized (AddonRegistry.class) {
            frozen = true;
            defs = new ArrayList<>(DEFS.values());
        }
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            for (AddonDefinition def : defs) {
                if (!modPresent(def)) {
                    SignalRadar.LOGGER.info("Addon {} skipped: mod '{}' is not loaded", def.id(), def.requiredModId());
                    continue;
                }
                try {
                    helper.register(def.id(), new AddonItem(def.id(), new Item.Properties().stacksTo(def.stackable() ? BATTERY_STACK : 16)));
                } catch (RuntimeException e) {
                    SignalRadar.LOGGER.error("Addon item {} could not be registered: {}", def.id(), e.toString());
                }
            }
        });
    }
}
