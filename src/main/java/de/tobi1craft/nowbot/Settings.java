package de.tobi1craft.nowbot;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Settings {
    //TODO: Language setting, translation
    private static MongoCollection<Document> collection;

    /**
     * Initializes the bot's settings (and help) command with various options for modifying settings.
     */
    public static void init() {
        MongoDatabase database = Database.get();
        collection = database.getCollection("settings");

        OptionData setting = new OptionData(OptionType.STRING, "setting", "The setting to change").setRequired(true)
                .addChoice("AutoVoice Category (requires channel)", "autoVoiceCategory")
                .addChoice("AutoVoice Channel (requires channel)", "autoVoiceChannel")
                .addChoice("AutoVoice Role To Override Permissions (requires role)", "autoVoiceRoleToOverridePermissions")
                .addChoice("Game Roles Channel (requires channel)", "rolesChannel")
                .addChoice("Command Prefix (requires string)", "commandPrefix")
                .addChoice("Offline Voice (requires boolean)", "offlineVoice");

        OptionData bool = new OptionData(OptionType.BOOLEAN, "boolean", "Sets a true/false value (if needed)").setRequired(false);

        OptionData string = new OptionData(OptionType.STRING, "string", "Sets a string value (if needed)").setRequired(false);

        OptionData channel = new OptionData(OptionType.CHANNEL, "channel", "Sets a channel value (if needed)").setRequired(false);

        OptionData role = new OptionData(OptionType.ROLE, "role", "Sets a role value (if needed)").setRequired(false);


        NowBot.commands.add(Commands.slash("setting", "Modify the settings of the bot").addOptions(setting, bool, string, channel, role).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)));

        NowBot.commands.add(Commands.slash("settings", "Get a list of your current settings").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)));

        NowBot.commands.add(Commands.slash("help", "Get a list of commands and settings to change").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS)));
    }

    public static boolean updateSetting(long guildId, String setting, Object value) {
        if (collection.find(Filters.eq("guildId", guildId)).first() == null)
            EventManager.initDatabaseForGuild(guildId);
        UpdateResult result = collection.updateOne(Filters.eq("guildId", guildId), Updates.set(setting, value));
        return result.wasAcknowledged();
    }

    public static Object getSetting(long guildId, String setting) {
        return Objects.requireNonNull(collection.find(Filters.eq("guildId", guildId)).first()).get(setting);
    }

    public static Long getSettingAsLong(long guildId, String setting) {
        return Objects.requireNonNull(collection.find(Filters.eq("guildId", guildId)).first()).getLong(setting);
    }

    public static String getSettingAsString(long guildId, String setting) {
        return Objects.requireNonNull(collection.find(Filters.eq("guildId", guildId)).first()).getString(setting);
    }

    public static boolean containsSetting(long guildId, String setting) {
        if (collection.find(Filters.eq("guildId", guildId)).first() == null)
            EventManager.initDatabaseForGuild(guildId);
        return Objects.requireNonNull(collection.find(Filters.eq("guildId", guildId)).first()).containsKey(setting);
    }

    public static boolean slashCommandInteraction(@NotNull SlashCommandInteractionEvent slashCommandInteractionEvent) {
        switch (slashCommandInteractionEvent.getName()) {
            case "setting" -> {
                long guildId = Objects.requireNonNull(slashCommandInteractionEvent.getGuild()).getIdLong();
                //TODO: no setting argument handling
                //TODO: return value for settings command when no option but setting
                String setting = Objects.requireNonNull(slashCommandInteractionEvent.getOption("setting")).getAsString();
                switch (setting) {
                    //TODO: maybe auslagern zu Methode
                    case "autoVoiceCategory":
                    case "autoVoiceChannel":
                    case "rolesChannel": {
                        OptionMapping option = slashCommandInteractionEvent.getOption("channel");
                        if (option == null) return false;
                        if (updateSetting(guildId, setting + "Id", option.getAsChannel().getIdLong())) {
                            slashCommandInteractionEvent.reply("✅ -> " + setting + " is now " + option.getAsChannel().getName()).setEphemeral(true).queue();
                            return true;
                        }
                        break;
                    }
                    case "autoVoiceRoleToOverridePermissions": {
                        OptionMapping option = slashCommandInteractionEvent.getOption("role");
                        if (option == null) return false;
                        if (updateSetting(guildId, setting + "Id", option.getAsRole().getIdLong())) {
                            slashCommandInteractionEvent.reply("✅ -> " + setting + " is now " + option.getAsRole().getName()).setEphemeral(true).queue();
                            return true;
                        }
                        break;
                    }
                    case "offlineVoice": {
                        OptionMapping option = slashCommandInteractionEvent.getOption("boolean");
                        if (option == null) return false;
                        if (updateSetting(guildId, setting, option.getAsBoolean())) {
                            slashCommandInteractionEvent.reply("✅ -> " + setting + " is now " + option.getAsString()).setEphemeral(true).queue();
                            return true;
                        }
                        break;
                    }
                    case "commandPrefix": {
                        OptionMapping option = slashCommandInteractionEvent.getOption("string");
                        if (option == null || option.getAsString().contains(" ")) return false;
                        if (updateSetting(guildId, setting, option.getAsString())) {
                            slashCommandInteractionEvent.reply("✅ -> " + setting + " is now " + option.getAsString()).setEphemeral(true).queue();
                            return true;
                        }
                        break;
                    }
                    default: {
                        return false;
                    }
                }
                slashCommandInteractionEvent.reply("❌").setEphemeral(true).queue();
                return true;
            }
            case "settings" -> {
                return printCurrentSetting(slashCommandInteractionEvent);
            }
            case "help" -> {
                slashCommandInteractionEvent.reply("TODO").setEphemeral(true).queue(); //TODO: Help command
                return true;
            }
        }
        return false;
    }

    private static boolean printCurrentSetting(SlashCommandInteractionEvent slashCommandInteractionEvent) {
        slashCommandInteractionEvent.reply("TODO").setEphemeral(true).queue(); //TODO: List current settings
        return true;
    }
}
