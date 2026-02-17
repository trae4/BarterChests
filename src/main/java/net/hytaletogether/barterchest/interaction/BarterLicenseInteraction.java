package net.hytaletogether.barterchest.interaction;

import net.hytaletogether.barterchest.config.BarterConfig;
import net.hytaletogether.barterchest.integration.SimpleClaimsIntegration;
import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.ShopOwnershipRegistry;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry;
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
    private static final Message MSG_SHOP_CREATED = Message.raw("Barter shop created successfully! Interact to manage or Crouch Interact to stock items!");
    private static final Message MSG_NO_TARGET = Message.raw("You must be looking at a chest!");
    private static final Message MSG_DOUBLE_CHEST = Message.raw("Cannot create a shop from a double chest! Use a single chest.");
    private static final Message MSG_NOT_ALLOWED_CLAIM = Message.raw("You cannot create a shop here - this land is claimed by another party!");
    private static final Message MSG_SHOP_LIMIT_REACHED = Message.raw("You have reached the maximum number of shops you can own!");
    private static final Message MSG_TOO_CLOSE = Message.raw("Too close to another shop! Shops cannot be registered within another shop's protected area.");
    
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

        // Prevent registering a shop inside another shop's protected zone (including diagonals).
        // This avoids display entity cleanup collisions and blockstate instability when multiple shops are too close.
        if (ProtectedShopIndex.get().isProtected(world, targetBlock.x, targetBlock.y, targetBlock.z)) {
            playerRef.sendMessage(MSG_TOO_CLOSE);
            // Try to provide a helpful hint by searching for a nearby registered shop center (within 1 block).
            String worldName = world.getName();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int sx = targetBlock.x + dx;
                        int sy = targetBlock.y + dy;
                        int sz = targetBlock.z + dz;
                        if (ShopLocationRegistry.isRegistered(worldName, sx, sy, sz)) {
                            playerRef.sendMessage(Message.raw("Nearby shop at: " + sx + ", " + sy + ", " + sz));
                            dx = dy = dz = 2; // break all loops
                        }
                    }
                }
            }
            return;
        }
        
        // Check SimpleClaims permission (if SimpleClaims is installed)
        UUID playerUUID = playerRef.getUuid();
        String dimension = world.getName();

        // Enforce max shops per player (0 = unlimited). Admins are exempt.
        int maxShops = BarterConfig.getInstance().getMaxShopsPerPlayer();
        boolean isAdmin = player.hasPermission(BarterChestBlockState.ADMIN_PERMISSION);
        if (!isAdmin && maxShops > 0) {
            int owned = ShopOwnershipRegistry.getCount(playerUUID);
            if (owned >= maxShops) {
                playerRef.sendMessage(MSG_SHOP_LIMIT_REACHED);
                playerRef.sendMessage(Message.raw("Limit: " + maxShops + " (you own " + owned + ")"));
                return;
            }
        }

        if (!SimpleClaimsIntegration.canCreateShop(playerUUID, dimension, targetBlock.x, targetBlock.z)) {
            playerRef.sendMessage(MSG_NOT_ALLOWED_CLAIM);
            String ownerName = SimpleClaimsIntegration.getChunkOwnerName(dimension, targetBlock.x, targetBlock.z);
            if (ownerName != null) {
                playerRef.sendMessage(Message.raw("This area is claimed by: " + ownerName));
            }
            return;
        }
        
        ItemContainerState containerState = (ItemContainerState) existingState;
        String playerName = player.getDisplayName();

        // Get the existing container and its items
        ItemContainer sourceContainer = containerState.getItemContainer();
        java.util.List<ItemStack> sourceItems = new java.util.ArrayList<>();
        short sourceCapacity = 27; // Default chest size

        if (sourceContainer != null) {
            sourceCapacity = sourceContainer.getCapacity();
            for (short i = 0; i < sourceCapacity; i++) {
                ItemStack item = sourceContainer.getItemStack(i);
                if (item != null && !ItemStack.isEmpty(item)) {
                    sourceItems.add(item);
                }
            }
            // Crucially, replace the old state's container with a new empty one.
            // This prevents the old state's onDestroy() method from dropping the original items.
            try {
                java.lang.reflect.Field containerField = ItemContainerState.class.getDeclaredField("itemContainer");
                containerField.setAccessible(true);
                containerField.set(containerState, new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer(sourceCapacity));
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Error clearing old container via reflection: " + e.getMessage());
                // Proceed anyway. Duplication is better than a server crash.
            }
        }

        // Create the new shop state
        BarterChestBlockState shopState = BarterChestBlockState.create(playerUUID, playerName);
        
        // Get block type for initialization
        BlockType blockType = chunk.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);

        // Initialize the shop state, which creates a fresh container
        shopState.initialize(blockType);
        
        // Add the copied items to the new container
        ItemContainer newContainer = shopState.getItemContainer();
        if (newContainer != null) {
            for (ItemStack item : sourceItems) {
                newContainer.addItemStack(item);
            }
        }
        
        // Set the new state on the chunk
        chunk.setState(targetBlock.x, targetBlock.y, targetBlock.z, shopState);

        // Persist shop location so protection is ready after restart.
        ShopLocationRegistry.ensureRegistered(world.getName(), targetBlock.x, targetBlock.y, targetBlock.z);
        // Persist shop metadata for admin tooling.
        ShopMetadataRegistry.register(world.getName(), targetBlock.x, targetBlock.y, targetBlock.z, playerUUID, playerName);

        // Index the shop + its protected neighbors for fast protection checks.
        ProtectedShopIndex.get().addShop(world, targetBlock.x, targetBlock.y, targetBlock.z);

        // Consume one barter license from the held item
        consumeHeldItem(context);

        // Track ownership for max-shops-per-player enforcement
        ShopOwnershipRegistry.increment(playerUUID);
        
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
