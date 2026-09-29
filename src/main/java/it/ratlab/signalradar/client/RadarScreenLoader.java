// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.google.gson.JsonParser;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.display.ScreenLayout;
import java.io.Reader;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Reads {@code assets/signalradar/radar_screen.json} on every resource reload; defaults when missing or broken. */
public final class RadarScreenLoader implements ResourceManagerReloadListener {
    public static final ResourceLocation FILE = SignalRadar.id("radar_screen.json");
    private static volatile ScreenLayout layout = ScreenLayout.DEFAULT;

    public static ScreenLayout layout() {
        return layout;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Optional<Resource> res = manager.getResource(FILE);
        if (res.isEmpty()) {
            layout = ScreenLayout.DEFAULT;
            return;
        }
        try (Reader r = res.get().openAsReader()) {
            layout = ScreenLayout.parse(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            SignalRadar.LOGGER.warn("Bad {} ({}); using the default screen layout", FILE, e.toString());
            layout = ScreenLayout.DEFAULT;
        }
    }
}
