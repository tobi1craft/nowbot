# NowBot

NowBot is a Discord bot written in Java using JDA.  
Its main feature is automatic voice channel management based on members' game presence.

## Features

- Auto-creates temporary voice channels when users join a configured "creation" voice channel.
- Auto-renames those channels based on the most common game activity of connected members.
- Auto-updates channel status with additional detected games.
- Removes temporary auto voice channels when they become empty.
- Supports per-guild settings via slash commands (`/setting`, `/settings`, `/help`).
- Uses MongoDB for persistent server configuration and auto-channel state.

## Current project status

- `AutoVoice` is implemented and active.
- `GameRoles` exists but is currently disabled in code (`if(true) return; //TODO`).
- `/help` and `/settings` responses are still marked as TODO in `Settings`.

## Tech stack

- Java 25
- JDA 6
- MongoDB Java Sync Driver

## Slash commands

### `/setting`

Updates guild-level configuration.

Available setting keys:

- `autoVoiceCategory` (requires `channel`)
- `autoVoiceChannel` (requires `channel`)
- `autoVoiceRoleToOverridePermissions` (requires `role`)
- `rolesChannel` (requires `channel`, currently unused because GameRoles is disabled)
- `commandPrefix` (requires `string`, no spaces)
- `offlineVoice` (requires `boolean`)

### `/settings`

Currently returns `TODO` (not yet implemented).

### `/help`

Currently returns `TODO` (not yet implemented).

## AutoVoice text commands

Inside an auto-generated voice channel's linked text context, users can use the configured command prefix (default `!`):

- Rename channel:
  - `!name <new name>`
  - aliases: `setname`, `rename`, `channelname`, `setchannelname`, `renamechannel`
- Update voice status/description:
  - `!description <text>`
  - aliases: `setdescription`, `status`, `setstatus`
- Visibility/connect control (requires `autoVoiceRoleToOverridePermissions` setting):
  - `!close` / `!private`
  - `!invisible`
  - `!open` / `!public`
  - `!visible`
- Re-enable automatic title/status updates:
  - `!auto` / `!automatic`

Default settings per guild:

- `commandPrefix = !`
- `offlineVoice = true`

## Localization

Resource bundle files:

- `src/main/resources/translation_en_US.properties`
- `src/main/resources/translation_de_DE.properties`

Current implementation initializes with `en_US` by default.

## Setup

### Prerequisites

- Java 25 installed and available in `PATH`
- A Discord bot token
- A MongoDB connection string

### Environment variables

Create a `.env` file in the project root:

```env
DISCORD_TOKEN=your_discord_bot_token
DATABASE_URI=your_mongodb_connection_string
```

Required variables:

- `DISCORD_TOKEN`: token for your Discord application bot
- `DATABASE_URI`: MongoDB URI used by the bot (database name is `nowbot`)

### Discord setup notes

The bot uses and expects these gateway intents:

- Guild Members
- Message Content
- Guild Presences

Make sure they are enabled in your Discord Developer Portal where required.

### Run locally

Windows (PowerShell or CMD):

```bash
.\gradlew.bat run
```

Linux/macOS:

```bash
./gradlew run
```

### Build

Windows:

```bash
.\gradlew.bat clean jar
```

Linux/macOS:

```bash
./gradlew clean jar
```

The built JAR is placed in `build/libs/`.

## License

MIT License. See `LICENSE`.
