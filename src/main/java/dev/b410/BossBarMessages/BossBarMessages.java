package dev.b410.BossBarMessages;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BossBarMessages extends JavaPlugin {

    private BossBarConfig bossBarConfig;
    private Messages messages;
    private BossBarService service;
    private BossBarScheduler scheduler;

    @Override
    public void onEnable() {
        this.bossBarConfig = BossBarConfig.load(this);
        this.messages = new Messages(this, bossBarConfig.lang(), bossBarConfig.prefix());

        this.service = new BossBarService(this);
        getServer().getPluginManager().registerEvents(service, this);

        this.scheduler = new BossBarScheduler(this, service);
        getServer().getPluginManager().registerEvents(scheduler, this);

        PluginCommand command = Objects.requireNonNull(
                getCommand("bossbar"),
                "Command 'bossbar' is missing from plugin.yml"
        );
        BossBarCommand cmd = new BossBarCommand(this, service, scheduler, messages);
        command.setExecutor(cmd);
        command.setTabCompleter(cmd);

        startAutoBroadcast();
        getLogger().info("Boss Bar Messages v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.stop();
        if (service != null)   service.shutdown();
        HandlerList.unregisterAll(this);
        getLogger().info("Boss Bar Messages disabled.");
    }

    /** Stops rotations, clears bars, reloads config + language and restarts rotations. */
    public void reloadAll() {
        scheduler.stop();
        service.clearAll();
        this.bossBarConfig = BossBarConfig.load(this);
        this.messages = new Messages(this, bossBarConfig.lang(), bossBarConfig.prefix());

        // The command holds a Messages reference, so swap in a fresh executor
        PluginCommand command = getCommand("bossbar");
        if (command != null) {
            BossBarCommand cmd = new BossBarCommand(this, service, scheduler, messages);
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        }

        startAutoBroadcast();
    }

    private void startAutoBroadcast() {
        if (!bossBarConfig.isAutoEnabled()) {
            getLogger().info("Auto-broadcast is disabled (auto-broadcast.enabled: false).");
            return;
        }
        scheduler.start(bossBarConfig);
        getLogger().info("Auto-broadcast enabled. Active rotations: " + bossBarConfig.countsByRegion());
    }

    public BossBarConfig bossBarConfig() { return bossBarConfig; }
    public Messages messages()           { return messages; }
}
