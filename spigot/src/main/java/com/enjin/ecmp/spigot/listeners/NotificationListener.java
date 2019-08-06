package com.enjin.ecmp.spigot.listeners;

import com.enjin.ecmp.spigot.Messages;
import com.enjin.ecmp.spigot.SpigotBootstrap;
import com.enjin.ecmp.spigot.player.ECPlayer;
import com.enjin.ecmp.spigot.trade.TradeManager;
import com.enjin.enjincoin.sdk.model.service.notifications.Event;
import com.enjin.enjincoin.sdk.model.service.notifications.EventData;
import com.enjin.enjincoin.sdk.model.service.notifications.NotificationEvent;
import com.enjin.enjincoin.sdk.model.service.notifications.NotificationType;
import com.enjin.java_commons.StringUtils;
import org.bukkit.Bukkit;

public class NotificationListener implements com.enjin.enjincoin.sdk.service.notifications.NotificationListener {

    private SpigotBootstrap bootstrap;

    public NotificationListener(SpigotBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void notificationReceived(NotificationEvent event) {
        try {
            NotificationType eventType = event.getType();
            bootstrap.debug(String.format("Received event type %s on channel %s.", eventType, event.getChannel()));

            if (eventType == null) return;

            switch (eventType) {
                case TX_EXECUTED:
                    onTxExecuted(event.getEvent());
                    break;
                case IDENTITY_LINKED:
                    onIdentityUpdated(event.getEvent());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            new Exception("An error occurred while processing an sdk event.", e).printStackTrace();
        }
    }

    private void onTxExecuted(Event event) {
        EventData data = event.getData();
        String type = event.getEventType();

        if (StringUtils.isEmpty(type)) return;


        switch (type) {
            case "CreateTrade":
                onCreateTrade(data);
                break;
            case "CompleteTrade":
                onCompleteTrade(data);
                break;
            case "Transfer":
                onTransfer(data);
                break;
            default:
                break;
        }
    }

    private void onIdentityUpdated(Event event) {
        EventData data = event.getData();

        if (data.getId() != null) {
            ECPlayer ecPlayer = bootstrap.getPlayerManager().getPlayer(data.getId());

            if (ecPlayer != null) {
                Bukkit.getScheduler().runTaskAsynchronously(bootstrap.plugin(), () -> ecPlayer.reloadIdentity());
            }
        }
    }

    private void onCreateTrade(EventData data) {
        String requestId = data.getTransactionId();
        String tradeId = data.getParam1();
        if (StringUtils.isEmpty(requestId) || StringUtils.isEmpty(tradeId)) return;
        TradeManager manager = bootstrap.getTradeManager();
        manager.submitCompleteTrade(requestId, tradeId);
    }

    private void onCompleteTrade(EventData data) {
        String requestId = data.getTransactionId();
        if (StringUtils.isEmpty(requestId)) return;
        TradeManager manager = bootstrap.getTradeManager();
        manager.completeTrade(requestId);
    }

    private void onTransfer(EventData data) {
        String fromEthAddr = data.getParam1();
        String toEthAddr = data.getParam2();

        if (StringUtils.isEmpty(fromEthAddr) || StringUtils.isEmpty(toEthAddr)) return;

        String amount = data.getParam4();

        ECPlayer fromEcPlayer = bootstrap.getPlayerManager().getPlayer(fromEthAddr);
        ECPlayer toEcPlayer = bootstrap.getPlayerManager().getPlayer(toEthAddr);

        if (fromEcPlayer != null) {
            Bukkit.getScheduler().runTaskAsynchronously(bootstrap.plugin(), () -> fromEcPlayer.reloadIdentity());
            Messages.tokenSent(fromEcPlayer.getBukkitPlayer(), amount, "?"); // TODO
        }

        if (toEcPlayer != null) {
            Bukkit.getScheduler().runTaskAsynchronously(bootstrap.plugin(), () -> toEcPlayer.reloadIdentity());
            Messages.tokenReceived(toEcPlayer.getBukkitPlayer(), amount, "?"); // TODO
        }
    }

}