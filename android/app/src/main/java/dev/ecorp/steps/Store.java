package dev.ecorp.steps;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** All device state is encrypted; snapshots never escape into logs or backups. */
final class Store {
    private static final String KEY = "steps-device-state";

    private static SecretKey key() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (!keys.containsAlias(KEY)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) keys.getKey(KEY, null);
    }

    static synchronized JSONObject read(Context context) {
        String saved = context.getSharedPreferences("vault", Context.MODE_PRIVATE).getString("state", "");
        if (saved.isEmpty()) return new JSONObject();
        try {
            JSONObject envelope = new JSONObject(saved);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)));
            return new JSONObject(new String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8));
        } catch (Exception error) {
            // A missing/invalidated key must never expose another account's old data.
            context.getSharedPreferences("vault", Context.MODE_PRIVATE).edit().clear().commit();
            return new JSONObject();
        }
    }

    static synchronized void write(Context context, JSONObject state) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            JSONObject envelope = new JSONObject()
                .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("data", Base64.encodeToString(cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
            if (!context.getSharedPreferences("vault", Context.MODE_PRIVATE).edit().putString("state", envelope.toString()).commit()) {
                throw new IllegalStateException("Device storage is full.");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not securely save device settings.", error);
        }
    }

    static synchronized void put(Context context, String key, Object value) {
        JSONObject state = read(context);
        try { state.put(key, value); } catch (Exception error) { throw new IllegalArgumentException(error); }
        write(context, state);
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences("vault", Context.MODE_PRIVATE).edit().clear().commit();
    }

    static boolean connected(Context context) { return !read(context).optString("token").isEmpty(); }
}
