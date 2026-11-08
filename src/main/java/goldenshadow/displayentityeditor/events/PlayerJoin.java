package goldenshadow.displayentityeditor.events;

import goldenshadow.displayentityeditor.DisplayEntityEditor;
import goldenshadow.displayentityeditor.Utilities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerJoin implements Listener {

    /**
     * Used to listen for when a player joins
     * @param event The event
     */
    @EventHandler
    public void onJoin(PlayerLoginEvent event) {
        if (event.getPlayer().isOp()) {
            if (DisplayEntityEditor.sendUpdateMessageOnJoin) {


                Bukkit.getScheduler().scheduleSyncDelayedTask(DisplayEntityEditor.getPlugin(), () -> {
                    if (DisplayEntityEditor.useMessagesFile) {
                        if (!DisplayEntityEditor.getPlugin().getDescription().getVersion().equals(DisplayEntityEditor.messageManager.getString("file_version"))) {
                            event.getPlayer().sendMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("messages_file_outdated_version")));
                        }
                        if (!DisplayEntityEditor.messageManager.isMessageMapComplete()) {
                            event.getPlayer().sendMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("messages_file_incomplete")));
                        }
                    }
                }, 10L);
            }
        }
    }
}
