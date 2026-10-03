// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.target;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import it.ratlab.signalradar.SignalRadar;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Loads target definitions from {@code data/<ns>/signalradar/target/*.json} on server start and {@code /reload}.
 * Definitions are server-side only (snapshots carry everything the client needs).
 */
public final class TargetManager extends SimpleJsonResourceReloadListener {
    public static final String DIR = "signalradar/target";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static volatile Map<ResourceLocation, TargetDef> targets = Map.of();

    private TargetManager() {
        super(GSON, DIR);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TargetManager());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> raw, ResourceManager manager, ProfilerFiller profiler) {
        targets = parseAll(raw);
        SignalRadar.LOGGER.info("Loaded {} signalradar targets", targets.size());
    }

    /** Parses every entry; invalid ones are logged and skipped. Sorted by id. */
    public static Map<ResourceLocation, TargetDef> parseAll(Map<ResourceLocation, JsonElement> raw) {
        Map<ResourceLocation, TargetDef> out = new TreeMap<>();
        raw.forEach((id, json) -> TargetParser.parse(id, json).ifPresent(d -> out.put(id, d)));
        return Collections.unmodifiableMap(out);
    }

    public static Collection<TargetDef> all() {
        return targets.values();
    }

    @Nullable
    public static TargetDef get(ResourceLocation id) {
        return targets.get(id);
    }

    /** Test hook. */
    public static void setForTest(Map<ResourceLocation, TargetDef> defs) {
        targets = defs;
    }
}
