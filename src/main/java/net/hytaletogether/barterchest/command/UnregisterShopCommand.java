package net.hytaletogether.barterchest.command;

import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry;
import net.hytaletogether.barterchest.util.ShopPlateUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.util.ChunkUtil;
import net.hytaletogether.barterchest.display.BarterDisplayManager;
import net.hytaletogether.barterchest.system.ShopOwnershipRegistry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand: /barterchest unregister
 * 
 * Admin command to forcefully unregister a shop at a given location.
 * This is a cleanup tool for cases where a shop was not removed correctly.
 */
public class UnregisterShopCommand extends AbstractAsyncCommand {
    
    public UnregisterShopCommand() {
        super("unregister", "Forcefully unregister a shop at your location");
        requirePermission(BarterChestBlockState.ADMIN_PERMISSION);
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return CompletableFuture.completedFuture(null);
        }
        
        Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            context.sendMessage(Message.raw("You must be in a world!"));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        
        return CompletableFuture.runAsync(() -> {
            try {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) {
                    player.sendMessage(Message.raw("Could not get your position!").color(Color.RED));
                    return;
                }
                
                // Use player's current position, but target the block below their feet (Y-1)
                Vector3i pos = new Vector3i(
                    (int) Math.floor(transform.getPosition().getX()), 
                    (int) Math.floor(transform.getPosition().getY() - 1), // Corrected Y-coordinate
                    (int) Math.floor(transform.getPosition().getZ())
                );

                // Get the block state at the target position
                BlockState state = world.getState(pos.getX(), pos.getY(), pos.getZ(), false);
                if (!(state instanceof BarterChestBlockState shop)) {
                    player.sendMessage(Message.raw("No BarterChest block found at your feet (" + pos + ").").color(Color.RED));
                    player.sendMessage(Message.raw("Attempting to clean up any ghost data for that location...").color(Color.YELLOW));
                    // Even if no block is found, try to unregister ghost data from registries
                    ShopLocationRegistry.unregister(world.getName(), pos.getX(), pos.getY(), pos.getZ());
                    ShopMetadataRegistry.unregister(world.getName(), pos.getX(), pos.getY(), pos.getZ());
                    ProtectedShopIndex.get().removeShop(world, pos.getX(), pos.getY(), pos.getZ());
                    ShopPlateUtil.removePlateAboveShop(world, pos.getX(), pos.getY(), pos.getZ());
                    player.sendMessage(Message.raw("Cleanup attempt finished.").color(Color.YELLOW));
                    return;
                }

                // First, remove the floating display entity
                BarterDisplayManager.removeDisplay(shop, world);

                // Remove the plate (if present)
                ShopPlateUtil.removePlateAboveShop(world, pos.getX(), pos.getY(), pos.getZ());

                // Get chunk
                WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ()));
                if (chunk != null) {
                    // Get the block type to properly initialize new state
                    BlockType blockType = chunk.getBlockType(pos.getX(), pos.getY(), pos.getZ());

                    // Get the container from the shop and copy items
                    ItemContainer shopContainer = shop.getItemContainer();
                    java.util.List<com.hypixel.hytale.server.core.inventory.ItemStack> shopItems = new java.util.ArrayList<>();
                    int sourceCapacity = 0;
                    
                    if (shopContainer != null) {
                        sourceCapacity = shopContainer.getCapacity();
                        for (short i = 0; i < sourceCapacity; i++) {
                            com.hypixel.hytale.server.core.inventory.ItemStack item = shopContainer.getItemStack(i);
                            if (item != null && !com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(item)) {
                                shopItems.add(item);
                            }
                        }
                        
                        // Use reflection to replace the old container with an empty one.
                        // This prevents the engine from dropping items when the state is destroyed.
                        try {
                            java.lang.reflect.Field containerField = ItemContainerState.class.getDeclaredField("itemContainer");
                            containerField.setAccessible(true);
                            containerField.set(shop, new SimpleItemContainer((short) sourceCapacity));
                        } catch (Exception e) {
                            player.sendMessage(Message.raw("Critical error: Failed to swap inventory container. " + e.getMessage()).color(Color.RED));
                            return; // Abort on failure to prevent item loss/dupe
                        }
                    }

                    // Create a new regular ItemContainerState
                    ItemContainerState newState = new ItemContainerState();
                    
                    // Initialize the new state (creates fresh container)
                    newState.initialize(blockType);
                    
                    // Add items to the new container
                    ItemContainer newContainer = newState.getItemContainer();
                    if (newContainer != null) {
                        for (com.hypixel.hytale.server.core.inventory.ItemStack item : shopItems) {
                            newContainer.addItemStack(item);
                        }
                    }

                    // Replace shop state with regular container state
                    chunk.setState(pos.getX(), pos.getY(), pos.getZ(), newState);

                    // Remove shop + neighbor protection from the fast index
                    ProtectedShopIndex.get().removeShop(world, pos.getX(), pos.getY(), pos.getZ());
                    ShopPlateUtil.removePlateAboveShop(world, pos.getX(), pos.getY(), pos.getZ());

                    // Remove from persistent registry so it will not be re-indexed on restart.
                    ShopLocationRegistry.unregister(world.getName(), pos.getX(), pos.getY(), pos.getZ());

                    // Remove from metadata registry (admin tooling)
                    ShopMetadataRegistry.unregister(world.getName(), pos.getX(), pos.getY(), pos.getZ());

                    // Update per-player shop count (for max shop limits)
                    if (shop.getOwnerUUID() != null) {
                        ShopOwnershipRegistry.decrement(shop.getOwnerUUID());
                    }

                    player.sendMessage(Message.raw("Shop unregistered and replaced with a normal chest at " + pos).color(Color.GREEN));
                } else {
                    player.sendMessage(Message.raw("Could not find chunk for shop at " + pos).color(Color.RED));
                }

            } catch (Exception e) {
                player.sendMessage(Message.raw("An error occurred: " + e.getMessage()).color(Color.RED));
                e.printStackTrace();
            }
        }, world);
    }
}
