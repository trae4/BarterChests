package net.hytaletogether.barterchest.command;

import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Subcommand: /barterchest audit
 *
 * Scans for and removes orphaned shop protections.
 */
public class AuditCommand extends AbstractAsyncCommand {

    public AuditCommand() {
        super("audit", "Scans for and removes orphaned shop protections.");
        requirePermission(BarterChestBlockState.ADMIN_PERMISSION);
        addSubCommand(new FixCommand());
        addSubCommand(new RemoveCommand());
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        World world = player.getWorld();
        if (world == null) {
            context.sendMessage(Message.raw("Could not access the world.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        player.sendMessage(Message.raw("Starting shop audit scan for this world...").color(Color.YELLOW));
        player.sendMessage(Message.raw("Note: Only checks loaded chunks.").color(Color.GRAY));

        return CompletableFuture.runAsync(() -> {
            Map<String, Set<Long>> snapshot = ShopLocationRegistry.getSnapshot();
            AtomicInteger orphanedCount = new AtomicInteger(0);
            List<String> orphanedShops = new ArrayList<>();

            // Only scan the current world
            String worldName = world.getName();
            Set<Long> worldShops = snapshot.get(worldName);
            
            if (worldShops != null) {
                for (long packedPos : worldShops) {
                    int x = ShopLocationRegistry.unpackX(packedPos);
                    int y = ShopLocationRegistry.unpackY(packedPos);
                    int z = ShopLocationRegistry.unpackZ(packedPos);

                    // Check if chunk is loaded
                    WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunk == null) {
                        continue; // Skip unloaded chunks
                    }

                    BlockState state = world.getState(x, y, z, false);

                    if (!(state instanceof BarterChestBlockState)) {
                        orphanedCount.incrementAndGet();
                        orphanedShops.add(x + ", " + y + ", " + z);
                    }
                }
            }

            if (orphanedCount.get() == 0) {
                player.sendMessage(Message.raw("Audit complete. No orphaned protections found in loaded chunks.").color(Color.GREEN));
            } else {
                player.sendMessage(Message.raw("Audit complete. Found " + orphanedCount.get() + " orphaned protections:").color(Color.RED));
                for (String loc : orphanedShops) {
                    player.sendMessage(Message.raw(" - " + loc).color(Color.RED));
                }
                player.sendMessage(Message.raw("Run '/barterchest audit fix' to remove them.").color(Color.YELLOW).bold(true));
            }
        }, world);
    }

    /**
     * Subcommand: /barterchest audit fix
     * Automatically removes all orphaned protections found in loaded chunks.
     */
    private static class FixCommand extends AbstractAsyncCommand {
        public FixCommand() {
            super("fix", "Automatically removes all orphaned protections found.");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            CommandSender sender = context.sender();
            if (!(sender instanceof Player player)) {
                context.sendMessage(Message.raw("This command can only be used by players."));
                return CompletableFuture.completedFuture(null);
            }

            World world = player.getWorld();
            if (world == null) {
                context.sendMessage(Message.raw("Could not access the world.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            player.sendMessage(Message.raw("Fixing orphaned protections in loaded chunks...").color(Color.YELLOW));

            return CompletableFuture.runAsync(() -> {
                Map<String, Set<Long>> snapshot = ShopLocationRegistry.getSnapshot();
                AtomicInteger fixedCount = new AtomicInteger(0);
                String worldName = world.getName();
                Set<Long> worldShops = snapshot.get(worldName);

                if (worldShops != null) {
                    for (long packedPos : worldShops) {
                        int x = ShopLocationRegistry.unpackX(packedPos);
                        int y = ShopLocationRegistry.unpackY(packedPos);
                        int z = ShopLocationRegistry.unpackZ(packedPos);

                        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                        if (chunk == null) continue;

                        BlockState state = world.getState(x, y, z, false);

                        if (!(state instanceof BarterChestBlockState)) {
                            // It's an orphan. Remove it.
                            ProtectedShopIndex.get().removeShop(worldName, x, y, z);
                            ShopLocationRegistry.unregister(worldName, x, y, z);
                            fixedCount.incrementAndGet();
                        }
                    }
                }

                if (fixedCount.get() == 0) {
                    player.sendMessage(Message.raw("No orphaned protections found to fix.").color(Color.GREEN));
                } else {
                    player.sendMessage(Message.raw("Successfully removed " + fixedCount.get() + " orphaned protections.").color(Color.GREEN));
                }
            }, world);
        }
    }

    /**
     * Subcommand: /barterchest audit remove
     * Removes protection for a shop near the player's location.
     */
    private static class RemoveCommand extends AbstractAsyncCommand {
        public RemoveCommand() {
            super("remove", "Removes protection for a shop near your location.");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            CommandSender sender = context.sender();
            if (!(sender instanceof Player player)) {
                context.sendMessage(Message.raw("This command can only be used by players."));
                return CompletableFuture.completedFuture(null);
            }

            World world = player.getWorld();
            if (world == null) {
                context.sendMessage(Message.raw("Could not access the world.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.runAsync(() -> {
                Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    player.sendMessage(Message.raw("Could not get a reference to your player entity.").color(Color.RED));
                    return;
                }
                Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = ref.getStore();
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

                if (transform == null) {
                    player.sendMessage(Message.raw("Could not get your position!").color(Color.RED));
                    return;
                }

                Vector3d pos = transform.getPosition();
                int px = (int) Math.floor(pos.getX());
                int py = (int) Math.floor(pos.getY());
                int pz = (int) Math.floor(pos.getZ());
                String worldName = world.getName();

                // Scan a 5x5x5 area around the player to find a registered shop center
                int radius = 2;
                boolean found = false;

                for (int x = px - radius; x <= px + radius; x++) {
                    for (int y = py - radius; y <= py + radius; y++) {
                        for (int z = pz - radius; z <= pz + radius; z++) {
                            if (ShopLocationRegistry.isRegistered(worldName, x, y, z)) {
                                // Found a registered shop center
                                ProtectedShopIndex.get().removeShop(worldName, x, y, z);
                                ShopLocationRegistry.unregister(worldName, x, y, z);
                                player.sendMessage(Message.raw("Removed shop protection at " + x + ", " + y + ", " + z).color(Color.GREEN));
                                found = true;
                                // We only remove one at a time to be safe
                                return; 
                            }
                        }
                    }
                }

                if (!found) {
                    player.sendMessage(Message.raw("No registered shop found within " + radius + " blocks of your location.").color(Color.RED));
                }
            }, world);
        }
    }
}