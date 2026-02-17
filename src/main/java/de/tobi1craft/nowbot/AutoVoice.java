package de.tobi1craft.nowbot;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.tobi1craft.nowbot.util.Bucket;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.*;

/**
 * This class listens for events related to voice channels and user presence in a Discord server.
 * It creates and manages voice channels based on user activity and updates channel names and statuses.
 */
public class AutoVoice {

    private static final int RENAME_BUCKET_CAPACITY = 2;
    private static final long RENAME_BUCKET_WINDOW_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long SCHEDULER_JITTER_MS = 100;
    private static final ConcurrentMap<Long, String> latestRequestedChannelNames = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, Bucket> renameBuckets = new ConcurrentHashMap<>();
    private static final Set<Long> renamesInFlight = ConcurrentHashMap.newKeySet();
    private static final Set<Long> rateLimitedRenames = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService renameScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-voice-rename-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private static MongoCollection<Document> collection;

    public static void init() {
        MongoDatabase database = Database.get();
        collection = database.getCollection("autoChannel");
    }

    public static void genericUserPresenceEvent(GenericUserPresenceEvent e) {
        // Iterate through created channels and update their names and statuses based on user presence
        long guildId = e.getGuild().getIdLong();
        FindIterable<Document> channels = collection.find(new Document("guildId", guildId));
        for (Document channel : channels) {
            long channelId = channel.getLong("channelId");
            VoiceChannel voiceChannel = e.getGuild().getVoiceChannelById(channelId);
            if (voiceChannel == null) {
                collection.deleteOne(channel);
                cleanupChannelRenameState(channelId);
                continue;
            }
            if (voiceChannel.getMembers().contains(e.getMember())) updateTitleAndStatus(voiceChannel);
        }
    }


    public static void messageReceivedEvent(MessageReceivedEvent messageReceivedEvent) {
        if (!messageReceivedEvent.isFromGuild()) return;
        Document channelData = collection.find(new Document("guildId", messageReceivedEvent.getGuild().getIdLong()).append("channelId", messageReceivedEvent.getChannel().getIdLong())).first();
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
                    messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("❌")).queue();
                    return;
            }
            collection.updateOne(new Document("guildId", messageReceivedEvent.getGuild().getIdLong()).append("channelId", messageReceivedEvent.getChannel().getIdLong()), new Document("$set", new Document("isAuto", false)));
        } else {
            if (!Settings.containsSetting(messageReceivedEvent.getGuild().getIdLong(), "autoVoiceRoleToOverridePermissionsId")) {
                messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("❌")).queue();
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
                    collection.updateOne(new Document("guildId", messageReceivedEvent.getGuild().getIdLong()).append("channelId", messageReceivedEvent.getChannel().getIdLong()), new Document("$set", new Document("isAuto", true)));
                    break;
                default:
                    messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("❌")).queue();
                    return;
            }
        }
        messageReceivedEvent.getMessage().addReaction(Emoji.fromFormatted("✅")).queue();
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
            //Don't allow offline users to connect if offlineVoice is false
            if (!((boolean) Settings.getSetting(guild.getIdLong(), "offlineVoice")) && user.getOnlineStatus() == OnlineStatus.OFFLINE) {
                user.getUser().openPrivateChannel().flatMap(privateChannel -> privateChannel.sendMessage(
                        Language.get("autoVoice.offlineVoiceJoinDenied", guild.getName())
                )).queue();
                guild.kickVoiceMember(user).queue();
            }
            // If the member joined the auto voice creation channel, create a new voice channel
            else if (Settings.containsSetting(guild.getIdLong(), "autoVoiceCategoryId") && Settings.containsSetting(guild.getIdLong(), "autoVoiceChannelId")) {
                if (guildVoiceUpdateEvent.getChannelJoined().getIdLong() == Settings.getSettingAsLong(guild.getIdLong(), "autoVoiceChannelId")) {
                    String[] channelNameAndStatus = getChannelNameAndStatus(List.of(user), null);
                    guild.createVoiceChannel(channelNameAndStatus[0], guild.getCategoryById(Settings.getSettingAsLong(guild.getIdLong(), "autoVoiceCategoryId")))
                            .queue(voiceChannel -> {
                                guild.moveVoiceMember(user, voiceChannel).queue();
                                voiceChannel.getManager().setBitrate(guild.getMaxBitrate()).queue();

                                Document channelData = new Document("guildId", guild.getIdLong())
                                        .append("channelId", voiceChannel.getIdLong())
                                        .append("creatorId", user.getIdLong())
                                        .append("isAuto", true);
                                collection.insertOne(channelData);

                                updateStatus(voiceChannel, channelNameAndStatus[1]);
                            });
                } else {
                    // If the member joined a different auto voice channel, update the name and status of the channel
                    long joinedChannelId = guildVoiceUpdateEvent.getChannelJoined().getIdLong();
                    Document autoChannelData = collection.find(new Document("guildId", guild.getIdLong()).append("channelId", joinedChannelId)).first();
                    if (autoChannelData != null) {
                        updateTitleAndStatus((VoiceChannel) guildVoiceUpdateEvent.getChannelJoined());
                    }
                }
            }
        }

        if (guildVoiceUpdateEvent.getChannelLeft() != null) {
            // If the member left a voice channel, delete it if it is an auto channel and is empty
            Document channelData = collection.find(new Document("guildId", guild.getIdLong()).append("channelId", guildVoiceUpdateEvent.getChannelLeft().getIdLong())).first();
            if (channelData != null) {
                VoiceChannel channel = (VoiceChannel) guildVoiceUpdateEvent.getChannelLeft();
                if (channel.getMembers().isEmpty()) {
                    channel.delete().queue();
                    collection.deleteOne(channelData);
                    cleanupChannelRenameState(channel.getIdLong());
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
        Document channelData = collection.find(new Document("guildId", channel.getGuild().getIdLong()).append("channelId", channel.getIdLong())).first();
        if (channelData != null && !channelData.getBoolean("isAuto", true)) return;

        String[] result = getChannelNameAndStatus(channel.getMembers(), null);

        requestChannelRename(channel, result[0]);

        if (rateLimitedRenames.contains(channel.getIdLong()))
            result = getChannelNameAndStatus(channel.getMembers(), channel.getName());

        updateStatus(channel, result[1]);
    }

    private static void requestChannelRename(VoiceChannel channel, String desiredName) {
        long channelId = channel.getIdLong();
        latestRequestedChannelNames.put(channelId, desiredName);
        if (!renamesInFlight.add(channelId)) return;
        processLatestRename(channel.getGuild(), channelId);
    }

    private static void processLatestRename(Guild guild, long channelId) {
        VoiceChannel channel = guild.getVoiceChannelById(channelId);
        if (channel == null) {
            cleanupChannelRenameState(channelId);
            return;
        }

        String desiredName = latestRequestedChannelNames.get(channelId);
        if (desiredName == null || channel.getName().equals(desiredName)) {
            latestRequestedChannelNames.remove(channelId);
            rateLimitedRenames.remove(channelId);
            releaseRenameAndContinueIfPending(guild, channelId);
            return;
        }

        Bucket bucket = renameBuckets.computeIfAbsent(channelId, _ -> new Bucket(RENAME_BUCKET_CAPACITY, RENAME_BUCKET_WINDOW_MS));
        if (!bucket.tryConsume()) {
            rateLimitedRenames.add(channelId);
            long waitMs = bucket.getWaitMs() + SCHEDULER_JITTER_MS;
            renameScheduler.schedule(() -> processLatestRename(guild, channelId), waitMs, TimeUnit.MILLISECONDS);
            return;
        }
        rateLimitedRenames.remove(channelId);

        channel.getManager().setName(desiredName).queue(
                _ -> {
                    latestRequestedChannelNames.remove(channelId, desiredName);
                    VoiceChannel latestChannel = guild.getVoiceChannelById(channelId);
                    if (latestChannel != null) {
                        String[] latestNameAndStatus = getChannelNameAndStatus(latestChannel.getMembers(), latestChannel.getName());
                        updateStatus(latestChannel, latestNameAndStatus[1]);
                    }

                    releaseRenameAndContinueIfPending(guild, channelId);
                },
                failure -> {
                    NowBot.logger.warn(
                            "Failed to rename auto voice channel {} in guild {} to '{}'",
                            channelId,
                            guild.getIdLong(),
                            desiredName,
                            failure
                    );
                    rateLimitedRenames.remove(channelId);
                    renamesInFlight.remove(channelId);
                }
        );
    }

    private static void cleanupChannelRenameState(long channelId) {
        latestRequestedChannelNames.remove(channelId);
        renameBuckets.remove(channelId);
        renamesInFlight.remove(channelId);
        rateLimitedRenames.remove(channelId);
    }

    private static void releaseRenameAndContinueIfPending(Guild guild, long channelId) {
        renamesInFlight.remove(channelId);
        if (latestRequestedChannelNames.containsKey(channelId) && renamesInFlight.add(channelId)) {
            processLatestRename(guild, channelId);
        }
    }

    private static void updateStatus(VoiceChannel channel, String content) {
        if (content.isEmpty()) {
            if (!channel.getStatus().isEmpty()) channel.modifyStatus("").queue();
            return;
        }
        channel.modifyStatus(content).queue();
    }

    private static String[] getChannelNameAndStatus(List<Member> users, String currentChannelName) {
        Map<String, Integer> games = new HashMap<>();

        for (Member member : users) {
            for (Activity activity : member.getActivities()) {
                if (!isGameActivityType(activity.getType())) continue;

                if (activity.getName().equalsIgnoreCase("Hang Status")) continue;

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

        String channelName = Language.get("autoVoice.defaultChannelName");

        // If there are game activities, pick the most frequent activity for the channel title.
        if (!gamesList.isEmpty()) {
            channelName = gamesList.getFirst().getKey();
        }

        String[] result = new String[2];
        result[0] = channelName;

        // Build the status against the currently visible channel title to avoid duplicate entries
        // while a delayed rename is still pending.
        String displayedChannelName = currentChannelName == null ? channelName : currentChannelName;
        List<String> statusGames = new ArrayList<>();
        for (Map.Entry<String, Integer> game : gamesList) {
            if (game.getKey().equalsIgnoreCase(displayedChannelName)) continue;
            statusGames.add(game.getKey());
        }

        if (statusGames.isEmpty()) {
            result[1] = "";
        } else {
            StringBuilder description = new StringBuilder("+ ");
            for (int i = 0; i < statusGames.size() - 1; i++) {
                description.append(statusGames.get(i)).append(", ");
            }
            description.append(statusGames.getLast());
            result[1] = description.toString();
        }
        return result;
    }

    private static boolean isGameActivityType(Activity.ActivityType type) {
        return type == Activity.ActivityType.PLAYING
                || type == Activity.ActivityType.STREAMING
                || type == Activity.ActivityType.COMPETING;
    }
}
