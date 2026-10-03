// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/** Listed in kubejs.plugins.txt; KubeJS only reads that file when KubeJS itself is installed. */
public class SignalRadarKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        SignalRadarEventsJS.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType().isServer()) {
            event.add("SignalRadar", new SignalRadarBindingJS());
        }
    }
}
