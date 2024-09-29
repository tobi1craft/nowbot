package de.tobi1craft.nowbot;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent;
import org.bson.Document;

import java.util.*;

/**
 * This class listens for events related to voice channels and user presence in a Discord server.
 * It creates and manages voice channels based on user activity and updates channel names and statuses.
 */
public class AutoVoice {

    private static MongoCollection<Document> collection;

    public static void init() {
        MongoDatabase database = NowBot.getDatabase();
        collection = database.getCollection("autoChannel");
    }

    public static void genericUserPresenceEvent(GenericUserPresenceEvent e) {
        // Iterate through created channels and update their names and statuses based on user presence
        FindIterable<Document> channels = collection.find();
        for (Document channel : channels) {
            long channelId = channel.getLong("channelId");
            VoiceChannel voiceChannel = Objects.requireNonNull(e.getGuild().getVoiceChannelById(channelId));
            if (voiceChannel.getMembers().contains(e.getMember())) updateTitleAndStatus(voiceChannel);
        }
    }


    public static void messageReceivedEvent(MessageReceivedEvent messageReceivedEvent) {
        Document channelData = collection.find(new Document("channelId", messageReceivedEvent.getChannel().getIdLong())).first();
        if (channelData == null) return;
        Message message = messageReceivedEvent.getMessage();
        String content = message.getContentRaw();
        String commandPrefix = Settings.getSettingAsString(messageReceivedEvent.getGuild().getIdLong(), "commandPrefix");
        if (!content.startsWith(commandPrefix)) return;
        content = content.replaceFirst(commandPrefix, "");
        if (content.startsWith(" ")) return;
        if (content.contains(" ")) {
            String[] split = content.split(" ", 2);
            String arg = split[1];
            switch (split[0].toLowerCase()) {
                case "name":
                case "setname":
                case "rename":
                case "channelname":
                case "setchannelname":
                case "renamechannel":
                    messageReceivedEvent.getChannel().asAudioChannel().getManager().setName(arg).queue();
                    break;

                case "description":
                case "setdescription":
                case "status":
                case "setstatus":
                    messageReceivedEvent.getChannel().asVoiceChannel().modifyStatus(arg).queue();
                    break;
                default:
                    messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("x")).queue();
                    return;
            }
            collection.updateOne(new Document("channelId", messageReceivedEvent.getChannel().getIdLong()), new Document("$set", new Document("isAuto", false)));
        } else {
            if (!Settings.containsSetting(messageReceivedEvent.getGuild().getIdLong(), "autoVoiceRoleToOverridePermissionsId")) {
                messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("x")).queue();
                return;
            }
            long roleId = Settings.getSettingAsLong(messageReceivedEvent.getGuild().getIdLong(), "autoVoiceRoleToOverridePermissionsId");
            switch (content) {
                case "close":
                case "setclosed":
                case "private":
                case "setprivate":
                    messageReceivedEvent.getChannel().asAudioChannel().getManager().putRolePermissionOverride(roleId, EnumSet.of(Permission.VIEW_CHANNEL), EnumSet.of(Permission.VOICE_CONNECT)).queue();
                    break;

                case "invisible":
                case "setinvisible":
                    messageReceivedEvent.getChannel().asAudioChannel().getManager().putRolePermissionOverride(roleId, null, EnumSet.of(Permission.VIEW_CHANNEL)).queue();
                    break;

                case "open":
                case "setopened":
                case "public":
                case "setpublic":
                    messageReceivedEvent.getChannel().asAudioChannel().getManager().putRolePermissionOverride(roleId, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null).queue();
                    break;

                case "visible":
                case "setvisible":
                    messageReceivedEvent.getChannel().asAudioChannel().getManager().putRolePermissionOverride(roleId, EnumSet.of(Permission.VIEW_CHANNEL), null).queue();
                    break;
                case "auto":
                case "automatic":
                    updateTitleAndStatus(messageReceivedEvent.getChannel().asVoiceChannel());
                    collection.updateOne(new Document("channelId", messageReceivedEvent.getChannel().getIdLong()), new Document("$set", new Document("isAuto", true)));
                    break;
                default:
                    messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("x")).queue();
                    return;
            }
        }
        messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("white_check_mark")).queue();
    }

    /**
     * Handles the GuildVoiceUpdateEvent.
     * This event is triggered when a member joins or leaves a voice channel in a guild.
     *
     * @param guildVoiceUpdateEvent The event that triggered this method.
     */
    public static void guildVoiceUpdateEvent(GuildVoiceUpdateEvent guildVoiceUpdateEvent) {
        Guild guild = guildVoiceUpdateEvent.getGuild();
        Member user = guildVoiceUpdateEvent.getEntity();

        if (guildVoiceUpdateEvent.getChannelJoined() != null) {
            // If the member joined the auto voice creation channel, create a new voice channel
            if (Settings.containsSetting(guild.getIdLong(), "autoVoiceCategoryId") && Settings.containsSetting(guild.getIdLong(), "autoVoiceChannelId")) {
                if (guildVoiceUpdateEvent.getChannelJoined().getIdLong() == Settings.getSettingAsLong(guild.getIdLong(), "autoVoiceChannelId")) {
                    String[] channelNameAndStatus = getChannelNameAndStatus(List.of(user));
                    guild.createVoiceChannel(channelNameAndStatus[0], guild.getCategoryById(Settings.getSettingAsLong(guild.getIdLong(), "autoVoiceCategoryId")))
                            .queue(voiceChannel -> {
                                guild.moveVoiceMember(user, voiceChannel).queue();
                                voiceChannel.getManager().setBitrate(guild.getMaxBitrate()).queue();

                                Document channelData = new Document("channelId", voiceChannel.getIdLong())
                                        .append("creatorId", user.getIdLong())
                                        .append("isAuto", true);
                                collection.insertOne(channelData);

                                updateStatus(voiceChannel, channelNameAndStatus[1]);
                            });
                } else {
                    // If the member joined a different auto voice channel, update the name and status of the channel
                    FindIterable<Document> channels = collection.find();
                    for (Document channel : channels) {
                        long channelId = channel.getLong("channelId");
                        if (guildVoiceUpdateEvent.getChannelJoined().getIdLong() != channelId) continue;
                        updateTitleAndStatus((VoiceChannel) guildVoiceUpdateEvent.getChannelJoined());
                        break;
                    }
                }
            }
        }

        if (guildVoiceUpdateEvent.getChannelLeft() != null) {
            // If the member left a voice channel, delete it if it is an auto channel and is empty
            Document channelData = collection.find(new Document("channelId", guildVoiceUpdateEvent.getChannelLeft().getIdLong())).first();
            if (channelData != null) {
                VoiceChannel channel = (VoiceChannel) guildVoiceUpdateEvent.getChannelLeft();
                if (channel.getMembers().isEmpty()) {
                    channel.delete().queue();
                    collection.deleteOne(channelData);
                }
            }
        }
    }

    /**
     * Updates the name and status of a voice channel based on the activities of its members.
     *
     * @param channel The voice channel to update.
     */
    private static void updateTitleAndStatus(VoiceChannel channel) {
        // Check if auto-update is enabled for the channel
        Document channelData = collection.find(new Document("channelId", channel.getIdLong())).first();
        if (channelData != null && !channelData.getBoolean("isAuto", true)) return;

        String[] result = getChannelNameAndStatus(channel.getMembers());

        channel.getManager().setName(result[0]).queue();
        updateStatus(channel, result[1]);
    }

    private static void updateStatus(VoiceChannel channel, String content) {
        if (content.isEmpty()) {
            if (!channel.getStatus().isEmpty()) channel.modifyStatus("").queue();
            return;
        }
        channel.modifyStatus(content).queue();
    }

    private static String[] getChannelNameAndStatus(List<Member> users) {
        Map<String, Integer> games = new HashMap<>();

        for (Member member : users) {
            for (Activity activity : member.getActivities()) {
                if (activity instanceof RichPresence richPresence) {
                    if (richPresence.getName().toLowerCase().contains("medal")) continue;
                    else if (richPresence.getName().toLowerCase().contains("badlion")) continue;
                }
                // Update the count of the game activity in the map
                games.put(activity.getName(), games.getOrDefault(activity.getName(), 0) + 1);
            }
        }

        // Convert the map entries to a list and sort them by count in descending order
        List<Map.Entry<String, Integer>> gamesList = new ArrayList<>(games.entrySet());
        gamesList.sort(Map.Entry.comparingByValue());
        Collections.reverse(gamesList);

        String channelName = "Spiel unbekannt";

        // If there are game activities, update the channel name to the most frequent activity
        if (!gamesList.isEmpty()) {
            channelName = gamesList.getFirst().getKey();
            gamesList.removeFirst();
        }

        String[] result = new String[2];
        result[0] = channelName;

        // Add remaining games to the channel description
        if (gamesList.isEmpty()) {
            result[1] = "";
        } else {
            StringBuilder description = new StringBuilder(Language.get("autoVoice.alsoPlaying") + " ");
            for (int i = 0; i < gamesList.size() - 1; i++) {
                description.append(gamesList.get(i).getKey()).append(", ");
            }
            description.append(gamesList.getLast().getKey());
            result[1] = description.toString();
        }
        return result;
    }
}
