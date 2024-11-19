package de.tobi1craft.nowbot;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.user.UserActivityEndEvent;
import net.dv8tion.jda.api.events.user.UserActivityStartEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateActivitiesEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateActivityOrderEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class EventManager implements EventListener {

    private final static MongoCollection<Document> collection = Database.get().getCollection("settings");

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildReadyEvent guildReadyEvent) {
            initDatabaseForGuild(guildReadyEvent.getGuild().getIdLong());
            guildReadyEvent.getGuild().updateCommands().addCommands(NowBot.commands).queue();
            //GameRoles.updateMessageRoles(guildReadyEvent.getGuild()); not releasing

        } else if (event instanceof GuildJoinEvent guildJoinEvent) {
            initDatabaseForGuild(guildJoinEvent.getGuild().getIdLong());
            //GameRoles.updateMessageRoles(guildJoinEvent.getGuild()); not releasing

        } else if (event instanceof SlashCommandInteractionEvent slashCommandInteractionEvent) {
            if (!slashCommandInteractionEvent.isFromGuild()) {
                slashCommandInteractionEvent.reply("You need to execute on a server!").queue();
                return;
            }
            if (!Settings.slashCommandInteraction(slashCommandInteractionEvent))
                slashCommandInteractionEvent.reply("❌").setEphemeral(true).queue();

        } else if (event instanceof GuildVoiceUpdateEvent guildVoiceUpdateEvent) {
            AutoVoice.guildVoiceUpdateEvent(guildVoiceUpdateEvent);

        } else if (event instanceof UserUpdateOnlineStatusEvent || event instanceof UserUpdateActivityOrderEvent || event instanceof UserUpdateActivitiesEvent || event instanceof UserActivityStartEvent || event instanceof UserActivityEndEvent) {
            AutoVoice.genericUserPresenceEvent((GenericUserPresenceEvent) event);

        } else if (event instanceof MessageReceivedEvent messageReceivedEvent) {
            AutoVoice.messageReceivedEvent(messageReceivedEvent);

        } else if (event instanceof RoleUpdateNameEvent roleUpdateNameEvent) {
            //GameRoles.updateMessageRoles(roleUpdateNameEvent.getGuild()); not releasing

        }
    }

    public static void initDatabaseForGuild(long guildId) {
        if (collection.find(Filters.eq("guildId", guildId)).first() == null)
            collection.insertOne(new Document("guildId", guildId));

        HashMap<String, Object> defaultSettings = new HashMap<>();
        defaultSettings.put("commandPrefix", "!");
        defaultSettings.put("offlineVoice", true);

        for (String setting : defaultSettings.keySet()) {
            if (!Settings.containsSetting(guildId, setting))
                Settings.updateSetting(guildId, setting, defaultSettings.get(setting));
        }


    }
}
