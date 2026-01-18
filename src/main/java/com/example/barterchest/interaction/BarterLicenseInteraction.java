package com.example.barterchest.interaction;

import com.example.barterchest.integration.SimpleClaimsIntegration;
import com.example.barterchest.state.BarterChestBlockState;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Interaction that converts a regular chest into a player shop when using a Barter License item.
 */
public class BarterLicenseInteraction extends SimpleInstantInteraction {
    
    private static final HytaleLogger LOGGER = HytaleLogger.get("BarterChest/Interaction");
    
    public static final String SHOP_LICENSE_ITEM_ID = "Barter_License";
    
    // Messages
    private static final Message MSG_NOT_A_CONTAINER = Message.raw("This block is not a container!");
    private static final Message MSG_ALREADY_A_SHOP = Message.raw("This is already a shop!");
    private static final Message MSG_SHOP_CREATED = Message.raw("Barter shop created successfully! Right-click to manage.");
    private static final Message MSG_NO_TARGET = Message.raw("You must be looking at a chest!");
    private static final Message MSG_DOUBLE_CHEST = Message.raw("Cannot create a shop from a double chest! Use a single chest.");
    private static final Message MSG_NOT_ALLOWED_CLAIM = Message.raw("You cannot create a shop here - this land is claimed by another party!");
    
    @SuppressWarnings("unchecked")
    public static final BuilderCodec<BarterLicenseInteraction> CODEC = 
        ((BuilderCodec.Builder<BarterLicenseInteraction>)
            BuilderCodec.builder(BarterLicenseInteraction.class, BarterLicenseInteraction::new, SimpleInstantInteraction.CODEC)
        ).build();
    
    public BarterLicenseInteraction() {
        super();
    }
    
    public BarterLicenseInteraction(String id) {
        super(id);
    }
    
    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        // Get the player entity
        var ref = context.getEntity();
        var commandBuffer = context.getCommandBuffer();
        
        Player player = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.at(Level.FINE).log("No player component found");
            return;
        }
        
        PlayerRef playerRef = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            LOGGER.at(Level.FINE).log("No player ref found");
            return;
        }
        
        // Get the target block
        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            playerRef.sendMessage(MSG_NO_TARGET);
            return;
        }
        
        LOGGER.at(Level.INFO).log("Shop license used at %d, %d, %d by %s", 
            targetBlock.x, targetBlock.y, targetBlock.z, playerRef.getUuid());
        
        // Get the world and chunk
        var store = ref.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z));
        
        if (chunk == null) {
            LOGGER.at(Level.WARNING).log("Chunk not in memory");
            return;
        }
        
        // Check current block state
        BlockState existingState = world.getState(targetBlock.x, targetBlock.y, targetBlock.z, true);
        
        // Check if it's already a shop
        if (existingState instanceof BarterChestBlockState) {
            playerRef.sendMessage(MSG_ALREADY_A_SHOP);
            return;
        }
        
        // Check if it's a container (ItemContainerState)
        if (!(existingState instanceof ItemContainerState)) {
            playerRef.sendMessage(MSG_NOT_A_CONTAINER);
            return;
        }
        
        // Check if this is part of a double chest
        if (isDoubleChest(world, targetBlock.x, targetBlock.y, targetBlock.z)) {
            playerRef.sendMessage(MSG_DOUBLE_CHEST);
            return;
        }
        
        // Check SimpleClaims permission (if SimpleClaims is installed)
        UUID playerUUID = playerRef.getUuid();
        String dimension = world.getName();
        if (!SimpleClaimsIntegration.canCreateShop(playerUUID, dimension, targetBlock.x, targetBlock.z)) {
            playerRef.sendMessage(MSG_NOT_ALLOWED_CLAIM);
            String ownerName = SimpleClaimsIntegration.getChunkOwnerName(dimension, targetBlock.x, targetBlock.z);
            if (ownerName != null) {
                playerRef.sendMessage(Message.raw("This area is claimed by: " + ownerName));
            }
            return;
        }
        
        ItemContainerState containerState = (ItemContainerState) existingState;
        
        // Get player info (already have playerUUID from above)
        String playerName = player.getDisplayName();
        
        // Get the existing container BEFORE we do anything
        ItemContainer sourceContainer = containerState.getItemContainer();
        
        // Create the new shop state
        BarterChestBlockState shopState = BarterChestBlockState.create(playerUUID, playerName);
        
        // Get block type for initialization
        BlockType blockType = chunk.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
        
        // Use reflection to set custom=true and transfer the container BEFORE initialize
        // This prevents initialize() from creating a new container
        try {
            java.lang.reflect.Field customField = ItemContainerState.class.getDeclaredField("custom");
            customField.setAccessible(true);
            customField.set(shopState, true);
            
            // Transfer the container directly
            if (sourceContainer != null) {
                java.lang.reflect.Field containerField = ItemContainerState.class.getDeclaredField("itemContainer");
                containerField.setAccessible(true);
                containerField.set(shopState, sourceContainer);
                
                // Set the old container state's container to empty to prevent onDestroy from dropping items
                containerField.set(containerState, new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error transferring container: " + e.getMessage());
            playerRef.sendMessage(Message.raw("Error creating shop: " + e.getMessage()));
            return;
        }
        
        // Initialize the shop state (custom=true means it won't override our container)
        shopState.initialize(blockType);
        
        // Set the new state on the chunk
        chunk.setState(targetBlock.x, targetBlock.y, targetBlock.z, shopState);
        
        // Consume one barter license from the held item
        consumeHeldItem(context);
        
        // Notify the player
        playerRef.sendMessage(MSG_SHOP_CREATED);
        
        LOGGER.at(Level.INFO).log("Barter shop created at %d, %d, %d by %s (%s)", 
            targetBlock.x, targetBlock.y, targetBlock.z, playerName, playerUUID);
    }
    
    /**
     * Consume one item from the held item stack.
     */
    private void consumeHeldItem(InteractionContext context) {
        ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) return;
        
        int currentQuantity = heldItem.getQuantity();
        if (currentQuantity <= 1) {
            // Remove the item entirely
            context.setHeldItem(null);
            
            // Also update the container
            ItemContainer container = context.getHeldItemContainer();
            if (container != null) {
                byte slot = context.getHeldItemSlot();
                container.removeItemStackFromSlot(slot);
            }
        } else {
            // Reduce quantity by 1
            ItemStack newStack = new ItemStack(heldItem.getItemId(), currentQuantity - 1);
            context.setHeldItem(newStack);
            
            // Also update the container
            ItemContainer container = context.getHeldItemContainer();
            if (container != null) {
                byte slot = context.getHeldItemSlot();
                container.setItemStackForSlot(slot, newStack);
            }
        }
    }
    
    /**
     * Check if a chest at the given position is part of a double chest.
     * Double chests are formed by two adjacent single chests horizontally.
     */
    private boolean isDoubleChest(World world, int x, int y, int z) {
        // Check all 4 horizontal adjacent positions
        int[][] offsets = {
            {1, 0, 0},   // East
            {-1, 0, 0},  // West
            {0, 0, 1},   // South
            {0, 0, -1}   // North
        };
        
        for (int[] offset : offsets) {
            int adjX = x + offset[0];
            int adjY = y + offset[1];
            int adjZ = z + offset[2];
            
            // Check if adjacent block is also a chest
            BlockType adjacentBlockType = world.getBlockType(adjX, adjY, adjZ);
            if (adjacentBlockType != null) {
                String blockId = adjacentBlockType.getId();
                if (blockId != null && blockId.toLowerCase().contains("chest")) {
                    // Found an adjacent chest - this is a double chest
                    return true;
                }
            }
        }
        
        return false;
    }
}
