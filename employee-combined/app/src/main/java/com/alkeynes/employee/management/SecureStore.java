package com.alkeynes.employee.management;

import android.content.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String PREFS="employee_management_native_secure";
    private static final String ALIAS="employee_management_native_key_v1";
    private final SharedPreferences prefs;

    public SecureStore(Context c){ prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }

    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        KeyStore.Entry e=ks.getEntry(ALIAS,null);
        if(e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry)e).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }

    public void put(String name,String value){
        try{
            if(value==null){ remove(name); return; }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] iv=cipher.getIV(), enc=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            prefs.edit().putString(name,Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(enc,Base64.NO_WRAP)).apply();
        }catch(Exception e){ throw new IllegalStateException("Secure storage unavailable",e); }
    }

    public String get(String name){
        String raw=prefs.getString(name,null); if(raw==null)return null;
        try{
            String[] p=raw.split(":",2); if(p.length!=2)return null;
            byte[] iv=Base64.decode(p[0],Base64.NO_WRAP), enc=Base64.decode(p[1],Base64.NO_WRAP);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(enc),StandardCharsets.UTF_8);
        }catch(Exception e){ return null; }
    }

    public void remove(String name){ prefs.edit().remove(name).apply(); }
    public void clear(){ prefs.edit().clear().apply(); }
}
