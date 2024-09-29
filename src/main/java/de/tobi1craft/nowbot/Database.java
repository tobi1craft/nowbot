package de.tobi1craft.nowbot;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

public class Database {
    private static final Logger logger = NowBot.logger;
    private static MongoClient mongoClient;
    private static volatile MongoDatabase database;

    private Database() {
    }

    public static MongoDatabase get() {
        if (database == null) {
            synchronized (Database.class) {
                if (database == null) {
                    ServerApi serverApi = ServerApi.builder().version(ServerApiVersion.V1).build();

                    MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(NowBot.getEnv("DATABASE_URI", true))).serverApi(serverApi).build();

                    mongoClient = MongoClients.create(settings);
                    database = mongoClient.getDatabase("nowbot");


                }
            }
        }
        return database;
    }

    private static void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private static void ping() {
        try {
            get().runCommand(new Document("ping", 1));
            logger.info("Pinged deployment. Successfully connected to MongoDB!");
        } catch (MongoException e) {
            logger.error(e);
        }
    }

    private static void registerShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            close();
            System.out.println("MongoDB connection closed.");
        }));
    }

    public static void init() {
        registerShutdown();
        ping();
    }
}
