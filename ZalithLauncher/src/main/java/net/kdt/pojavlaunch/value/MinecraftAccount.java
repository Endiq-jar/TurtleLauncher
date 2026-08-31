package net.kdt.pojavlaunch.value;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.gson.JsonSyntaxException;
import com.movtery.zalithlauncher.feature.accounts.AccountsManager;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.skin.SkinFileDownloader;
import com.movtery.zalithlauncher.utils.stringutils.StringUtilsKt;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Keep
public class MinecraftAccount {
    public String accessToken = "0"; // access token
    public String clientToken = "0"; // clientID: refresh and invalidate
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public String msaRefreshToken = "0";
    public String xuid;
    public String otherBaseUrl;
    public String otherAccount;
    public String otherPassword;
    public String accountType;
    /**
     * Whether this account's skin uses the slim (Alex) arm model. Emitted by
     * TurtleSkinServer as textures metadata `"model": "slim"` so the in-game renderer
     * draws the arms at slim width instead of the default classic width - a mismatch here
     * is the usual cause of a skin looking "stretched"/wrong in-game even though the PNG
     * itself is fine. Persisted in the account file like every other field; old accounts
     * load as false (classic), which is the safe default.
     */
    public boolean slimModel = false;
    private final String uniqueUUID = UUID.randomUUID().toString().toLowerCase(Locale.ROOT);

    /**
     * The all-zero placeholder profile UUID that local accounts were saved with before
     * the offline-UUID fix below landed. Checked explicitly (not just null) because this
     * exact string is the field's default and was persisted to disk for every account.
     */
    public static final String NULL_PROFILE_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * TurtleLauncher CRASH/BUG FIX: LOCAL (offline) accounts were being created with
     * profileId left at its default "00000000-0000-0000-0000-000000000000" (see
     * MinecraftAccount field default above) — every single local account launched
     * Minecraft with the exact same all-zero --uuid, since getMinecraftClientArgs()
     * in LaunchArgs.kt sends `account.profileId` as auth_uuid. That collides player
     * save data across every local account, and many servers/anti-cheat systems
     * reject the null UUID outright. It also meant TurtleSkinServer had no stable
     * per-account identity to key local skin/cape textures against.
     * Fix: derive the same deterministic offline UUID vanilla Minecraft itself uses
     * for offline-mode play (UUID v3 from "OfflinePlayer:<username>", the exact
     * algorithm Mojang's own LoginManager uses), so each local account gets a
     * real, stable, unique, vanilla-consistent identity instead of all zeros.
     */
    public static java.util.UUID generateOfflineUUID(String username) {
        return java.util.UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /**
     * True for plain offline/local ("cracked") accounts. Other-Login (authlib-injector)
     * accounts store the server name as their accountType and carry a real profileId from
     * that server, so they must NOT be treated as local; neither must Microsoft accounts
     * ("Microsoft"). Only the exact LOCAL type string marks a cracked account.
     */
    public boolean isLocalAccount() {
        return com.movtery.zalithlauncher.feature.accounts.AccountType.LOCAL.getType().equals(accountType);
    }

    /**
     * The profileId that should actually be used as the game's auth_uuid (and as the key
     * for local skin/cape textures). This is the single choke point for the "cracked
     * account can't join servers" bug: accounts created BEFORE the offline-UUID fix still
     * sit on disk with {@link #NULL_PROFILE_ID}, and the launcher used to send that
     * all-zero UUID to the game for every one of them. Many cracked servers (and their
     * anti-cheat/auth plugins) reject the null UUID outright, and every such account
     * collided on the same identity for save data. Regenerating the deterministic vanilla
     * offline UUID here means both freshly created AND pre-existing local accounts always
     * launch with a real, stable, per-username identity.
     */
    public String getEffectiveProfileId() {
        if (isLocalAccount() && (profileId == null || NULL_PROFILE_ID.equals(profileId))) {
            return generateOfflineUUID(username == null ? "Steve" : username).toString();
        }
        return profileId == null ? NULL_PROFILE_ID : profileId;
    }

    /**
     * Fills in any null/legacy field values (and repairs a null/zero profileId on local
     * accounts) after the account is read back from disk. Kept idempotent so it's safe to
     * call on accounts that were already correct. Used by both load paths -
     * {@link #loadFromUniqueUUID} and AccountsManager#reloadInternal - so a fix applied
     * here covers every account object the launcher ever holds.
     */
    public void normalize() {
        if (accessToken == null) accessToken = "0";
        if (clientToken == null) clientToken = "0";
        if (username == null) username = "0";
        if (msaRefreshToken == null) msaRefreshToken = "0";
        profileId = getEffectiveProfileId();
    }

    public void updateMicrosoftSkin() {
        updateSkin("https://sessionserver.mojang.com");
    }

    public void updateOtherSkin() {
        updateSkin(StringUtilsKt.removeSuffix(otherBaseUrl, "/") + "/sessionserver/");
    }

    private void updateSkin(String url) {
        File skinFile = new File(PathManager.DIR_USER_SKIN, uniqueUUID + ".png");
        if (skinFile.exists()) FileUtils.deleteQuietly(skinFile); //清除一次皮肤文件
        try {
            new SkinFileDownloader().yggdrasil(url, skinFile, profileId);
            Logging.i("SkinLoader", "Update skin success");
        } catch (Exception e) {
            Logging.i("SkinLoader", "Could not update skin\n" + Tools.printToString(e));
        }
    }

    public void save() throws IOException {
        Tools.write(PathManager.DIR_ACCOUNT_NEW + "/" + uniqueUUID, Tools.GLOBAL_GSON.toJson(this));
    }
    
    public static MinecraftAccount parse(String content) throws JsonSyntaxException {
        return Tools.GLOBAL_GSON.fromJson(content, MinecraftAccount.class);
    }

    public static MinecraftAccount loadFromProfileID(String profileID) {
        for (MinecraftAccount account : AccountsManager.INSTANCE.getAllAccounts()) {
            if (Objects.equals(account.profileId, profileID)) return account;
        }
        return null;
    }

    public static MinecraftAccount loadFromUniqueUUID(String uniqueUUID) {
        if(!accountExists(uniqueUUID)) return null;
        try {
            MinecraftAccount acc = parse(Tools.read(PathManager.DIR_ACCOUNT_NEW + "/" + uniqueUUID));
            if (acc == null) return null;
            acc.normalize();
            return acc;
        } catch(IOException | JsonSyntaxException e) {
            Logging.e(MinecraftAccount.class.getName(), "Caught an exception while loading the profile",e);
            return null;
        }
    }

    private static boolean accountExists(String uniqueUUID) {
        return !uniqueUUID.isEmpty() && new File(PathManager.DIR_ACCOUNT_NEW + "/" + uniqueUUID).exists();
    }

    public String getUniqueUUID() {
        return this.uniqueUUID;
    }

    @NonNull
    @Override
    public String toString() {
        return "MinecraftAccount{" +
                "username='" + username + '\'' +
                ", accountType=" + accountType +
                '}';
    }
}
