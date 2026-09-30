// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.api.RadarAddonChangedEvent;
import it.ratlab.signalradar.api.RadarScanEvent;
import it.ratlab.signalradar.api.RadarTargetFoundEvent;
import it.ratlab.signalradar.api.RadarUpgradedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Bridges Signal Radar's NeoForge events to {@code SignalRadarEvents} and posts the startup event
 * {@code registerAddons} from {@link AddonRegistry#addProvider} (inside our item RegisterEvent, before the addon registry
 * freezes; startup scripts are loaded earlier, in KubeJS' mod constructor). Only called when KubeJS is loaded.
 */
public final class KubeJSCompat {
    private KubeJSCompat() {}

    public static void init() {
        AddonRegistry.addProvider(KubeJSCompat::registerAddons);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::onScan);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::onFound);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::onUpgraded);
        NeoForge.EVENT_BUS.addListener(KubeJSCompat::onAddonChanged);
        SignalRadar.LOGGER.info("KubeJS found: SignalRadarEvents and the SignalRadar binding are available");
    }

    private static void registerAddons() {
        if (!SignalRadarEventsJS.REGISTER_ADDONS.hasListeners()) {
            return;
        }
        SignalRadarKubeEvents.RegisterAddons event = new SignalRadarKubeEvents.RegisterAddons();
        SignalRadarEventsJS.REGISTER_ADDONS.post(event);
        int added = 0;
        for (SignalRadarKubeEvents.AddonBuilderJS b : event.builders()) {
            AddonDefinition def;
            try {
                def = b.build();
            } catch (IllegalArgumentException e) {
                error("Custom addon " + b.id() + " is invalid: " + e.getMessage());
                continue;
            }
            try {
                if (AddonRegistry.registerCustom(def)) {
                    added++;
                } else {
                    error("Custom addon " + b.id() + " was not registered (id taken or too late), see the log");
                }
            } catch (RuntimeException e) {
                error("Custom addon " + b.id() + " failed to register: " + e);
            }
        }
        SignalRadar.LOGGER.info("KubeJS registerAddons: {} custom addon(s) registered", added);
    }

    static void error(String msg) {
        SignalRadar.LOGGER.error(msg);
        ScriptType.STARTUP.console.error(msg);
    }

    private static void onScan(RadarScanEvent e) {
        if (SignalRadarEventsJS.SCAN.hasListeners()
                && SignalRadarEventsJS.SCAN.post(new SignalRadarKubeEvents.Scan(e)).interruptFalse()) {
            e.setCanceled(true);
        }
    }

    private static void onFound(RadarTargetFoundEvent e) {
        if (SignalRadarEventsJS.TARGET_FOUND.hasListeners()) {
            SignalRadarEventsJS.TARGET_FOUND.post(new SignalRadarKubeEvents.TargetFound(e));
        }
    }

    private static void onUpgraded(RadarUpgradedEvent e) {
        if (SignalRadarEventsJS.UPGRADED.hasListeners()) {
            SignalRadarEventsJS.UPGRADED.post(new SignalRadarKubeEvents.Upgraded(e));
        }
    }

    private static void onAddonChanged(RadarAddonChangedEvent e) {
        if (SignalRadarEventsJS.ADDON_CHANGED.hasListeners()) {
            SignalRadarEventsJS.ADDON_CHANGED.post(new SignalRadarKubeEvents.AddonChanged(e));
        }
    }
}
