package pt.criativo.registo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import java.util.HashMap;
import java.util.Map;

public class FirebaseBridge {

    private static final String TAG   = "CriatvReg";
    private static final String PREFS = "CriativoRegisto";

    private final Activity activity;
    private final WebView  webView;
    private FirebaseFirestore db;

    public FirebaseBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView  = webView;
        try {
            FirebaseApp.initializeApp(activity);
            db = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build();
            db.setFirestoreSettings(settings);
            Log.d(TAG, "Firebase OK");
        } catch (Exception e) {
            Log.e(TAG, "Firebase init: " + e.getMessage());
        }
    }

    private void emitir(String evento, String detalhe) {
        String js = "window.dispatchEvent(new CustomEvent('" + evento + "',{detail:" +
            (detalhe.startsWith("{") || detalhe.startsWith("[") ? detalhe : "\"" + detalhe + "\"") + "}));";
        activity.runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    /** Grava preferencia local */
    @JavascriptInterface
    public void gravarPreferencia(String chave, String valor) {
        SharedPreferences.Editor ed = activity
            .getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit();
        if (valor == null || valor.isEmpty()) ed.remove(chave);
        else ed.putString(chave, valor);
        ed.apply();
    }

    /** Le preferencia local */
    @JavascriptInterface
    public String lerPreferencia(String chave) {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString(chave, "");
    }

    /** Incrementa contador de senhas (local) e devolve o proximo numero */
    @JavascriptInterface
    public int proximoNumero() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        int atual = prefs.getInt("contador_senhas", 0);
        int proximo = (atual % 999) + 1;
        prefs.edit().putInt("contador_senhas", proximo).apply();
        return proximo;
    }

    /** Reseta o contador de senhas */
    @JavascriptInterface
    public void resetarContador() {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit().putInt("contador_senhas", 0).apply();
    }

    /** Carrega menu do ficheiro assets/menu.json */
    @JavascriptInterface
    public void carregarMenu() {
        try {
            java.io.InputStream is = activity.getAssets().open("menu.json");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            emitir("fbMenuCarregado", sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "carregarMenu: " + e.getMessage());
            emitir("fbMenuCarregado", "[]");
        }
    }

    /** Modo A: Envia pedido directamente para KDS */
    @JavascriptInterface
    public void enviarPedido(String itemsJson, String totalStr, String numero, String destino) {
        try {
            double total = Double.parseDouble(totalStr);
            Map<String, Object> pedido = new HashMap<>();
            pedido.put("items",     itemsJson);
            pedido.put("total",     total);
            pedido.put("numero",    numero);
            pedido.put("destino",   destino);
            pedido.put("estado",    "pendente");
            pedido.put("tipo",      "balcao");
            pedido.put("criado_em", com.google.firebase.Timestamp.now());

            db.collection("pedidos").add(pedido)
                .addOnSuccessListener(ref -> {
                    emitir("fbPedidoEnviado", ref.getId());
                    Log.d(TAG, "Pedido enviado KDS: " + ref.getId());
                })
                .addOnFailureListener(e -> Log.e(TAG, "enviarPedido: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "enviarPedido: " + e.getMessage());
        }
    }

    /** Modo B: Grava pedido pendente no Firebase (aguarda activacao pelo Smartphone) */
    @JavascriptInterface
    public void gravarPedidoPendente(String itemsJson, String totalStr, String numero, String mesa) {
        try {
            double total = Double.parseDouble(totalStr);
            Map<String, Object> pedido = new HashMap<>();
            pedido.put("items",      itemsJson);
            pedido.put("total",      total);
            pedido.put("numero",     numero);
            pedido.put("mesa",       mesa != null ? mesa : "");
            pedido.put("estado",     "aguarda_activacao");
            pedido.put("tipo",       "balcao");
            pedido.put("criado_em",  com.google.firebase.Timestamp.now());

            db.collection("pedidos_pendentes").document("senha_" + numero).set(pedido)
                .addOnSuccessListener(v -> {
                    emitir("fbPedidoPendente", numero);
                    Log.d(TAG, "Pedido pendente: #" + numero);
                })
                .addOnFailureListener(e -> Log.e(TAG, "gravarPedidoPendente: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "gravarPedidoPendente: " + e.getMessage());
        }
    }

    /** Ler PIN de activacao do Firebase */
    @JavascriptInterface
    public void lerPinActivacao() {
        db.collection("config").document("activacao").get()
            .addOnSuccessListener(doc -> {
                String pin = doc.exists() ? String.valueOf(doc.getData().getOrDefault("pin", "")) : "";
                emitir("fbPinActivacao", pin);
            })
            .addOnFailureListener(e -> emitir("fbPinActivacao", ""));
    }

    public void destroy() {}
}
