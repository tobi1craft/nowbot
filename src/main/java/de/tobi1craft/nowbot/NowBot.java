package de.tobi1craft.nowbot;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class for the NowBot application.
 */
public class NowBot {

    /**
     * List of slash commands to be registered with the bot.
     */
    public static final List<SlashCommandData> commands = new ArrayList<>();
    private static final Logger logger = LogManager.getLogger(NowBot.class);
    private static Dotenv dotenv;
    private static MongoDatabase database;

    public static void main(String[] args) {
        logger.info("Hello world!");

        // Load environment variables from .env file
        dotenv = Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();

        // Configure MongoDB server API version
        ServerApi serverApi = ServerApi.builder().version(ServerApiVersion.V1).build();

        // Build MongoDB client settings with connection string and server API
        MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(getEnv("DATABASE_URI", true))).serverApi(serverApi).build();

        // Connect to MongoDB and send a ping to confirm connection
        try (MongoClient mongoClient = MongoClients.create(settings)) {
            try {
                database = mongoClient.getDatabase("nowbot");
                database.runCommand(new Document("ping", 1));
                logger.info("Pinged deployment. Successfully connected to MongoDB!");
            } catch (MongoException e) {
                logger.error(e);
            }
        }

        // Init Bot
        Language.initLanguages();
        Settings.init();
        AutoVoice.init();

        // Build JDA (Discord API) instance with environment token
        JDABuilder builder = JDABuilder.createDefault(getEnv("DISCORD_TOKEN", true));
        // Register event listeners for bot functionality
        builder.addEventListeners(new EventManager());

        // Enable the necessary intents and cache flags for bot functionality
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_PRESENCES);
        builder.enableCache(CacheFlag.ACTIVITY);
        JDA jda = builder.build();

        // Wait for JDA to be ready before continuing
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Log bot online status and invite-URL
        logger.info("Bot is online! - Invite: {}", jda.getInviteUrl());
    }

    /**
     * Retrieves an environment variable from either system environment or .env file.
     *
     * @param name The name of the environment variable.
     * @return The value of the environment variable, or null if not found.
     */
    public static String getEnv(String name) {
        String variable = System.getenv(name);
        if (variable == null || variable.isEmpty()) {
            variable = dotenv.get(name);
            if (variable == null || variable.isEmpty()) {
                logger.debug("{} environment variable is not set.", name);
            }
        }
        return variable;
    }

    /**
     * Retrieves an environment variable from either system environment or .env file
     * and exits the program if the variable is not found and required is true.
     *
     * @param name     The name of the environment variable.
     * @param required Whether the variable is required.
     * @return The value of the environment variable.
     */
    public static String getEnv(String name, boolean required) {
        if (!required) return getEnv(name);
        String variable = System.getenv(name);
        if (variable == null || variable.isEmpty()) {
            variable = dotenv.get(name);
            if (variable == null || variable.isEmpty()) {
                logger.fatal("Error: {} environment variable is not set.", name);
                System.exit(1);
            }
        }
        return variable;
    }

    public static MongoDatabase getDatabase() {
        return database;
    }
}