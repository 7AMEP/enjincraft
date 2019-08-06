package com.enjin.ecmp.spigot.commands.subcommands;

import com.enjin.ecmp.spigot.SpigotBootstrap;
import com.enjin.ecmp.spigot.configuration.TokenDefinition;
import com.enjin.ecmp.spigot.player.ECPlayer;
import com.enjin.ecmp.spigot.util.MessageUtils;
import com.enjin.ecmp.spigot.wallet.MutableBalance;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BalanceCommand {

    private SpigotBootstrap bootstrap;

    public BalanceCommand(SpigotBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            ECPlayer ecPlayer = bootstrap.getPlayerManager().getPlayer(player.getUniqueId());

            Bukkit.getScheduler().runTaskAsynchronously(bootstrap.plugin(), () -> {
                ecPlayer.reloadUser();

                if (!ecPlayer.isLinked()) {
                    TextComponent text = TextComponent.of("You have not linked a wallet to your account.").color(TextColor.RED);
                    MessageUtils.sendComponent(sender, text);
                    text = TextComponent.of("Please type '/enj link' to link your account to your Enjin Wallet.").color(TextColor.RED);
                    MessageUtils.sendComponent(sender, text);
                    return;
                }

                if (ecPlayer.isIdentityLoaded()) {
                    BigDecimal ethBalance = (ecPlayer.getEthBalance() == null)
                            ? BigDecimal.ZERO
                            : ecPlayer.getEthBalance();
                    BigDecimal enjBalance = (ecPlayer.getEnjBalance() == null)
                            ? BigDecimal.ZERO
                            : ecPlayer.getEnjBalance();

                    sendMsg(sender, "EthAdr: " + ChatColor.LIGHT_PURPLE + ecPlayer.getEthereumAddress());
                    sendMsg(sender, "ID: " + ecPlayer.getIdentityId() + "   ");

                    if (enjBalance != null)
                        sendMsg(sender, ChatColor.GREEN + "[ " + enjBalance + " ENJ ] ");
                    if (ethBalance != null)
                        sendMsg(sender, ChatColor.GREEN + "[ " + ethBalance + " ETH ]");

                    int itemCount = 0;
                    List<TextComponent> listing = new ArrayList<>();
                    if (ecPlayer.isLinked()) {
                        List<MutableBalance> balances = ecPlayer.getTokenWallet().getBalances();
                        for (MutableBalance balance : balances) {
                            TokenDefinition def = bootstrap.getConfig().getTokens().get(balance.id());
                            if (def != null && balance != null && balance.balance() > 0) {
                                itemCount++;
                                listing.add(TextComponent.of(itemCount + ". ").color(TextColor.GOLD)
                                        .append(TextComponent.of(def.getDisplayName()).color(TextColor.DARK_PURPLE))
                                        .append(TextComponent.of(" (qty. " + balance.balance() + ")").color(TextColor.GREEN)));
                            }
                        }
                    }

                    sendMsg(sender, "");
                    if (itemCount == 0)
                        sendMsg(sender, ChatColor.BOLD + "" + ChatColor.GOLD + "No CryptoItems found in your Enjin Wallet.");
                    else
                        sendMsg(sender, ChatColor.BOLD + "" + ChatColor.GOLD + "Found " + itemCount + " CryptoItems in your Wallet: ");

                    listing.forEach(l -> MessageUtils.sendComponent(sender, l));


                } else {
                    TextComponent text = TextComponent.of("You have not linked a wallet to your account.")
                            .color(TextColor.RED);
                    MessageUtils.sendComponent(sender, text);
                }
            });
        } else {
            TextComponent text = TextComponent.of("Only players can use this command.")
                    .color(TextColor.RED);
            MessageUtils.sendComponent(sender, text);
        }
    }

    private void sendMsg(CommandSender sender, String msg) {
        TextComponent text = TextComponent.of(msg)
                .color(TextColor.GOLD);
        MessageUtils.sendComponent(sender, text);
    }

}