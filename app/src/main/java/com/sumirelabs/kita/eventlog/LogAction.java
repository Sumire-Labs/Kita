package com.sumirelabs.kita.eventlog;

public enum LogAction {
    MESSAGE_EDIT(Category.MESSAGES, "Message Edited"), MESSAGE_DELETE(Category.MESSAGES, "Message Deleted"),
    MESSAGE_BULK_DELETE(Category.MESSAGES, "Messages Bulk Deleted"),
    MEMBER_JOIN(Category.MEMBERS, "Member Joined"), MEMBER_LEAVE(Category.MEMBERS, "Member Left"),
    NICKNAME(Category.MEMBERS, "Nickname Changed"), MEMBER_ROLES(Category.MEMBERS, "Member Roles Changed"),
    KICK(Category.MODERATION, "Member Kicked"), BAN(Category.MODERATION, "Member Banned"),
    UNBAN(Category.MODERATION, "Member Unbanned"), TIMEOUT(Category.MODERATION, "Timeout Changed"),
    VOICE_JOIN(Category.VOICE, "Voice Joined"), VOICE_LEAVE(Category.VOICE, "Voice Left"),
    VOICE_MOVE(Category.VOICE, "Voice Moved"), VOICE_STATE(Category.VOICE, "Voice State Changed"),
    VOICE_MODERATION(Category.VOICE, "Voice Moderation"),
    ROLE_CREATE(Category.ROLES, "Role Created"), ROLE_UPDATE(Category.ROLES, "Role Updated"), ROLE_DELETE(Category.ROLES, "Role Deleted"),
    CHANNEL_CREATE(Category.CHANNELS, "Channel Created"), CHANNEL_UPDATE(Category.CHANNELS, "Channel Updated"),
    CHANNEL_DELETE(Category.CHANNELS, "Channel Deleted"), CHANNEL_PERMISSIONS(Category.CHANNELS, "Channel Permissions Changed"),
    SERVER_UPDATE(Category.SERVER, "Server Updated");
    public enum Category { MESSAGES, MEMBERS, MODERATION, VOICE, ROLES, CHANNELS, SERVER }
    private final Category category;
    private final String label;
    LogAction(Category category, String label) { this.category = category; this.label = label; }
    public Category category() { return category; }
    public String label() { return label; }
}