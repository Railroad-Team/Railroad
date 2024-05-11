package io.github.railroad.discord.data;

import com.google.gson.annotations.SerializedName;

public class DiscordUser {
    @SerializedName("id")
    private final String userId;

    private final String username;
    private final String discriminator;
    private final String avatar;
    private final AvatarDecorationData avatar_decoration_data;

    private final Boolean bot;
    private final Integer flags;
    private final Integer premium_type;

    /**
     * <p>Create a new Discord user object, holding the data provided as arguments.</p>
     * <p>You probably do <b>not</b> want to construct the class, since it is mainly used for output.</p>
     *
     * @param userId               ID of the user, a Discord snowflake
     * @param username             Discord-Name
     * @param discriminator        Discord-Tag
     * @param avatar               Resource key of the user's avatar
     * @param avatarDecorationData Avatar decoration data
     * @param bot                  {@code true} if the user is a bot
     * @param premium_type         Type of Nitro subscription
     */
    public DiscordUser(long userId, String username, String discriminator, String avatar, AvatarDecorationData avatarDecorationData, Boolean bot, Integer premium_type) {
        this.userId = String.valueOf(userId);
        this.username = username;
        this.discriminator = discriminator;
        this.avatar = avatar;
        this.avatar_decoration_data = avatarDecorationData;
        this.bot = bot;
        this.premium_type = premium_type;
        this.flags = null;
    }

    /**
     * <p>Create a new Discord user object used to request a user by UID.</p>
     * <p>You probably do <b>not</b> want to construct the class, since it is mainly used for output.</p>
     *
     * @param userId ID of the user, a Discord snowflake
     */
    public DiscordUser(long userId) {
        this(userId, null, null, null, null, null, null);
    }

    public long getUserId() {
        return Long.parseLong(userId);
    }

    public String getUsername() {
        return username;
    }

    public String getDiscriminator() {
        return discriminator;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getAvatarUrl() {
        return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatar + ".png";
    }

    public AvatarDecorationData getAvatarDecorationData() {
        return avatar_decoration_data;
    }

    public Boolean isBot() {
        return bot;
    }

    public Integer getPremiumType() {
        return premium_type;
    }

    public Integer getFlags() {
        return flags;
    }

    @Override
    public String toString() {
        return "DiscordUser{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", discriminator='" + discriminator + '\'' +
                ", avatar='" + avatar + '\'' +
                ", avatar_decoration_data=" + avatar_decoration_data +
                ", bot=" + bot +
                ", premium_type=" + premium_type +
                ", flags=" + flags +
                '}';
    }

    public static class AvatarDecorationData {
        private String asset;
        private String sku_id;

        public String getAsset() {
            return asset;
        }

        public String getSkuId() {
            return sku_id;
        }

        @Override
        public String toString() {
            return "AvatarDecorationData{" +
                    "asset='" + asset + '\'' +
                    ", sku_id='" + sku_id + '\'' +
                    '}';
        }
    }
}
