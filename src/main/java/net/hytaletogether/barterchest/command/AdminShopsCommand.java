package net.hytaletogether.barterchest.command;

import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.ui.AdminShopListPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.CompletableFuture;

/**
 * /barterchest adminshops
 * Opens an admin UI listing all tracked shops.
 */
public class AdminShopsCommand extends AbstractAsyncCommand {

    public AdminShopsCommand() {
        super("adminshops", "Open the admin shop list UI");
        // Intentionally not using requirePermission() here.
        // Some server builds hide subcommands you lack permission for, which makes
        // "/barterchest adminshops" look like an invalid argument count.
        // We enforce the permission at runtime instead for a clearer error.
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        if (!player.hasPermission(BarterChestBlockState.ADMIN_PERMISSION)) {
            context.sendMessage(Message.raw("You do not have permission to use this command (" + BarterChestBlockState.ADMIN_PERMISSION + ")."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            context.sendMessage(Message.raw("You must be in a world to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        // UI + store access must happen on the world thread.
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                player.sendMessage(Message.raw("Could not resolve your player reference."));
                return;
            }

            player.getPageManager().openCustomPage(ref, store, new AdminShopListPage(playerRef));
        }, world);
    }
}
