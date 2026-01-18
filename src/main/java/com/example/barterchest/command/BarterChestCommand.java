package com.example.barterchest.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import java.util.concurrent.CompletableFuture;

/**
 * Main command for BarterChest plugin.
 * 
 * Usage: /barterchest <subcommand>
 * 
 * Subcommands:
 * - admin: Toggle admin mode for shop management
 * - cleanup [radius]: Remove orphaned display items nearby
 */
public class BarterChestCommand extends AbstractAsyncCommand {
    
    public BarterChestCommand() {
        super("barterchest", "BarterChest shop management commands");
        
        // Register subcommands
        addSubCommand(new AdminCommand());
        addSubCommand(new CleanupCommand());
    }
    
    @Override
    protected CompletableFuture<Void> executeAsync(com.hypixel.hytale.server.core.command.system.CommandContext context) {
        // Show help if no subcommand provided
        context.sendMessage(com.hypixel.hytale.server.core.Message.raw("BarterChest Commands:"));
        context.sendMessage(com.hypixel.hytale.server.core.Message.raw("  /barterchest admin - Toggle admin mode"));
        context.sendMessage(com.hypixel.hytale.server.core.Message.raw("  /barterchest cleanup - Refresh display for nearby shop"));
        return CompletableFuture.completedFuture(null);
    }
}
