package com.enjin.enjincoin.spigot_framework;

import com.enjin.enjincoin.sdk.Callback;
import com.enjin.enjincoin.sdk.Response;
import com.enjin.enjincoin.sdk.model.body.GraphQLResponse;
import com.enjin.enjincoin.sdk.service.tokens.vo.Token;
import com.enjin.enjincoin.sdk.service.tokens.vo.data.TokensData;
import com.enjin.enjincoin.spigot_framework.listeners.ConnectionListener;
import com.enjin.enjincoin.spigot_framework.listeners.InventoryListener;
import com.enjin.enjincoin.spigot_framework.listeners.PlayerInteractionListener;
import com.enjin.enjincoin.spigot_framework.player.PlayerManager;
import com.enjin.enjincoin.spigot_framework.trade.TradeManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.enjin.enjincoin.sdk.service.notifications.NotificationsService;
import com.enjin.enjincoin.sdk.service.tokens.TokensService;
import com.enjin.enjincoin.spigot_framework.commands.RootCommand;
import com.enjin.enjincoin.spigot_framework.controllers.SdkClientController;
import com.enjin.enjincoin.spigot_framework.listeners.notifications.GenericNotificationListener;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * <p>Extended bootstrap for the Spigot Minecraft server platform.</p>
 *
 * @since 1.0
 */
public class SpigotBootstrap extends PluginBootstrap {

    /**
     * <p>The Spigot plugin.</p>
     */
    private final BasePlugin main;

    /**
     * <p>App ID.</p>
     */
    private Integer appId;
    private Integer devIdentityId;

    /**
     * <p>Debug mode flag.</p>
     */
    private boolean sdkDebug;
    private boolean pluginDebug;

    /**
     * <p>The Enjin Coin SDK controller.</p>
     */
    private SdkClientController sdkClientController;

    /**
     * <p>The Enjin Coin Player controller.</p>
     */
    private PlayerManager playerManager;

    private TradeManager tradeManager;

    /**
     * <p>The mapping of token IDs and associated data.</p>
     */
    private Map<String, Token> tokens;

    /**
     * <p>Bootstrap constructor that accepts a Spigot plugin instance.</p>
     *
     * @param main the Spigot plugin
     */
    public SpigotBootstrap(BasePlugin main) {
        this.main = main;
    }

    @Override
    public void setUp() {
        this.playerManager = new PlayerManager(this.main);
        this.tradeManager = new TradeManager(this.main);
        this.tokens = new ConcurrentHashMap<>();

        // Load the config to ensure that it is created or already exists.
        final JsonObject config = getConfig();

        // Validate that the config exists and has required fields.
        if (config == null || !config.has("platformBaseUrl")
                || !config.has("appId")
                || !config.has("secret")) {
            return;
        }

        this.appId = config.get("appId").getAsInt();
        this.devIdentityId = config.get("devIdentityId").getAsInt();

        // If the config has debug mode set the debug flag equal to the config value.
        if (config.has("debugging")) {
            JsonObject debugging = config.getAsJsonObject("debugging");
            if (debugging.has("sdk")) {
                sdkDebug = debugging.get("sdk").getAsBoolean();
            }

            if (debugging.has("plugin")) {
                pluginDebug = debugging.get("plugin").getAsBoolean();
            }
        }

        try {
            // Initialize the Enjin Coin SDK controller with the provided Spigot plugin and the config.
            this.sdkClientController = new SdkClientController(this.main, config);
            this.sdkClientController.setUp();

            // Start the notification service.
            final NotificationsService notificationsService = this.sdkClientController.getClient().getNotificationsService();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.whenComplete((result, throwable) -> {
                if (throwable != null || result == null || !result) {
                    this.main.getLogger().warning("An error occurred while starting the notifications service.");
                    if (throwable != null) {
                        this.main.getLogger().log(Level.SEVERE, throwable.getMessage(), throwable);
                    }
                } else {
                    this.main.getLogger().info("Registering pusher notification listener.");
                    notificationsService.addNotificationListener(new GenericNotificationListener(this.main));
                }
            });
            notificationsService.startAsync(future);

            // Fetch a list of all tokens registered to the configured app ID.
            final TokensService tokensService = this.sdkClientController.getClient().getTokensService();
            tokensService.getAllTokensAsync(new Callback<GraphQLResponse<TokensData>>() {
                @Override
                public void onComplete(Response<GraphQLResponse<TokensData>> response) {
                    if (response.body() != null) {
                        TokensData data = response.body().getData();
                        if (data != null && data.getTokens() != null) {
                            data.getTokens().forEach(token -> {
                                if (config.get("appId").getAsInt() == token.getAppId() && config.get("tokens").getAsJsonObject().has(token.getTokenId())) {
                                    tokens.put(token.getTokenId(), token);
                                }
                            });
                        }
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Register Listeners
        Bukkit.getPluginManager().registerEvents(this.playerManager, this.main);
        Bukkit.getPluginManager().registerEvents(this.tradeManager, this.main);
        // TODO: Refactor/migrate features from ConnectionListener/InventoryListener
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this.main), this.main);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this.main), this.main);
        Bukkit.getPluginManager().registerEvents(new PlayerInteractionListener(this.main), this.main);

        // Register Commands
        this.main.getCommand("enj").setExecutor(new RootCommand(this.main));
    }

    @Override
    public void tearDown() {
        this.sdkClientController.tearDown();
        this.sdkClientController = null;
    }

    @Override
    public SdkClientController getSdkController() {
        return this.sdkClientController;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return this.playerManager;
    }

    @Override
    public TradeManager getTradeManager() {
        return this.tradeManager;
    }

    @Override
    public Map<String, Token> getTokens() {
        return this.tokens;
    }

    @Override
    public JsonObject getConfig() {
        File file = new File(this.main.getDataFolder(), "config.json");
        JsonElement element = null;

        try {
            // Save the default config bundled with the plugin jar to the plugin data folder.
            if (!file.exists())
                this.main.saveResource(file.getName(), false);

            JsonParser parser = new JsonParser();
            element = parser.parse(new FileReader(file));

            if (!(element instanceof JsonObject)) {
                boolean deleted = file.delete();
                if (deleted)
                    element = getConfig();
                else
                    this.main.getLogger().warning("An error occurred while load the configuration file.");
            }
        } catch (IOException ex) {
            this.main.getLogger().warning("An error occurred while creating the configuration file.");
        }

        return element.getAsJsonObject();
    }

    @Override
    public Integer getAppId() {
        return this.appId;
    }

    public Integer getDevIdentityId() {
        return devIdentityId;
    }

    @Override
    public void debug(String log) {
        if (isPluginDebuggingEnabled())
            this.main.getLogger().info(log);
    }

    @Override
    public boolean isPluginDebuggingEnabled() {
        return this.pluginDebug;
    }

    @Override
    public boolean isSDKDebuggingEnabled() {
        return this.sdkDebug;
    }
}