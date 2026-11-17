package org.autoding.research;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local credential-encrypted storage. Secret values never enter reports or logs. */
public final class PinStore {
    private static final String ALIAS = "autoding_native_pin_v1";
    private static final byte[] AAD = new byte[]{65,117,116,111,68,105,110,103,80,73,78,1};
    private PinStore() {}

    private static Context ce(Context context) {
        Context app = context.getApplicationContext();
        if (app.isDeviceProtectedStorage()) throw new IllegalStateException("credential-storage-required");
        UserManager users = (UserManager)app.getSystemService(Context.USER_SERVICE);
        if (users == null || !users.isUserUnlocked()) throw new IllegalStateException("credential-storage-locked");
        return app;
    }
    private static SharedPreferences preferences(Context context) {
        return ce(context).getSharedPreferences("native_pin_ciphertext", Context.MODE_PRIVATE);
    }
    public static synchronized boolean isSaved(Context context) {
        try {
            SharedPreferences saved = preferences(context);
            return saved.getInt("version", 0) == 1 && saved.contains("iv") && saved.contains("ciphertext");
        } catch (RuntimeException ignored) { return false; }
    }
    static boolean valid(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 12) return false;
        for (char digit : pin) if (digit < '0' || digit > '9') return false;
        return true;
    }
    private static KeyStore keys() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = keys();
        if (store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("pin-key-unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(false).build());
        return generator.generateKey();
    }
    public static synchronized void save(Context context, char[] pin) throws Exception {
        if (!valid(pin)) throw new IllegalArgumentException("pin-format-invalid");
        byte[] bytes = new byte[pin.length];
        try {
            ce(context);
            for (int n = 0; n < pin.length; n++) bytes[n] = (byte)pin[n];
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(bytes);
            boolean written = preferences(context).edit().clear().putInt("version", 1)
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit();
            if (!written) throw new IllegalStateException("pin-save-failed");
            if (!resetAttempt(context)) throw new IllegalStateException("pin-save-failed");
        } catch (Exception ignored) { throw new IllegalStateException("pin-save-failed"); }
        finally { Arrays.fill(bytes, (byte)0); }
    }
    public static synchronized char[] read(Context context) throws Exception {
        byte[] bytes = null;
        char[] pin = null;
        try {
            SharedPreferences saved = preferences(context);
            if (!isSaved(context)) throw new IllegalStateException("pin-not-saved");
            byte[] iv = Base64.decode(saved.getString("iv", ""), Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(saved.getString("ciphertext", ""), Base64.NO_WRAP);
            if (iv.length != 12 || ciphertext.length < 20 || ciphertext.length > 28)
                throw new IllegalStateException("pin-storage-invalid");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            bytes = cipher.doFinal(ciphertext);
            pin = new char[bytes.length];
            for (int n = 0; n < bytes.length; n++) pin[n] = (char)(bytes[n] & 255);
            if (!valid(pin)) throw new IllegalStateException("pin-storage-invalid");
            return pin;
        } catch (Exception ignored) {
            if (pin != null) Arrays.fill(pin, '\0');
            throw new IllegalStateException("pin-read-failed");
        } finally { if (bytes != null) Arrays.fill(bytes, (byte)0); }
    }
    public static synchronized void clear(Context context) throws Exception {
        try {
            if (!preferences(context).edit().clear().commit()) throw new IllegalStateException();
            keys().deleteEntry(ALIAS);
            if (!resetAttempt(context)) throw new IllegalStateException("pin-clear-failed");
        } catch (Exception ignored) { throw new IllegalStateException("pin-clear-failed"); }
    }
    static synchronized boolean isAttemptBlocked(Context context) {
        try { return ce(context).getSharedPreferences("native_unlock_guard", 0).getBoolean("blocked", false); }
        catch (RuntimeException ignored) { return true; }
    }
    static synchronized boolean beginAttempt(Context context) {
        if (isAttemptBlocked(context)) return false;
        try {
            return ce(context).getSharedPreferences("native_unlock_guard", 0).edit()
                .putBoolean("blocked", true).commit();
        } catch (RuntimeException ignored) { return false; }
    }
    static synchronized boolean resetAttempt(Context context) {
        try { return ce(context).getSharedPreferences("native_unlock_guard", 0).edit().clear().commit(); }
        catch (RuntimeException ignored) { return false; }
    }
}
