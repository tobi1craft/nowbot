package de.tobi1craft.nowbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GameRoles {

    public static void updateMessageRoles(Guild guild) {
        if(true) return; //TODO
        if (!Settings.containsSetting(guild.getIdLong(), "rolesChannelId")) return;
        List<String> activeGames = new ArrayList<>();
        List<Role> roles = guild.getRoles();
        for (Role role : roles) {
            List<String> games = new ArrayList<>();
            addGames(games);
            if (games.contains(role.getName().toUpperCase())) activeGames.add(role.getName().toUpperCase());
        }

        long channelId = Settings.getSettingAsLong(guild.getIdLong(), "rolesChannelId");



        MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
        messageCreateBuilder.addActionRow();



        MessageEditBuilder messageEditBuilder = new MessageEditBuilder();
        messageEditBuilder.applyCreateData(messageCreateBuilder.build());

        if (Settings.containsSetting(guild.getIdLong(), "roleMessageId")) {
            if (guild.getNewsChannelById(channelId) != null) {
                Objects.requireNonNull(guild.getNewsChannelById(channelId)).editMessageById(Settings.getSettingAsLong(guild.getIdLong(), "roleMessageId"), messageEditBuilder.build()).queue();

            } else if (guild.getTextChannelById(channelId) != null) {
                Objects.requireNonNull(guild.getTextChannelById(channelId)).editMessageById(Settings.getSettingAsLong(guild.getIdLong(), "roleMessageId"), messageEditBuilder.build()).queue();
            }
        } else {
            if (guild.getNewsChannelById(channelId) != null) {
                Objects.requireNonNull(guild.getNewsChannelById(channelId)).sendMessage(messageCreateBuilder.build())
                        .queue(message1 -> Settings.updateSetting(guild.getIdLong(), "roleMessageId", message1.getIdLong()));

            } else if (guild.getTextChannelById(channelId) != null) {
                Objects.requireNonNull(guild.getTextChannelById(channelId)).sendMessage(messageCreateBuilder.build())
                        .queue(message1 -> Settings.updateSetting(guild.getIdLong(), "roleMessageId", message1.getIdLong()));
            }
        }
    }

    private static void addGames(List<String> list) {
        list.add("VALORANT");
        list.add("MINECRAFT");
        list.add("XDEFIANT");
        list.add("ENSHROUDED");
        list.add("VALHEIM");
        list.add("SPECTRE DIVIDE");
        list.add("TRACKMANIA");
        list.add("COUNTERSTRIKE");
        list.add("DEADLOCK");
    }
}
