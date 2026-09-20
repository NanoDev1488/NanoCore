package com.nanocore.adminhub.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AdminHubCommand implements SimpleCommand {

    private final AdminHubVelocityPlugin plugin;

    public AdminHubCommand(AdminHubVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation inv) {
        if (!(inv.source() instanceof Player player)) {
            inv.source().sendMessage(Component.text("Только для игроков.", NamedTextColor.RED));
            return;
        }
        plugin.openHubFor(player);
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission(AdminHubVelocityPlugin.PERMISSION);
    }
}
