package pt.criativo.registo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class FirebaseBridge {

    private static final String TAG   = "CriatvReg";
    private static final String PREFS = "CriativoRegisto";

    private final Activity  activity;
    private final WebView   webView;
    private FirebaseFirestore db;
    private ListenerRegistration menuListener;

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
            (detalhe.startsWith("{") || detalhe.startsWith("[") || detalhe.startsWith("\"")
                ? detalhe : "\"" + detalhe + "\"") + "}));";
        activity.runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    /** Carrega menu do Firestore */
    @JavascriptInterface
    public void carregarMenu() {
        db.collection("menu").get()
            .addOnSuccessListener(snaps -> {
                try {
                    JSONArray cats = new JSONArray();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snaps.getDocuments()) {
                        JSONObject cat = new JSONObject();
                        cat.put("id",       doc.getId());
                        cat.put("nome",     doc.getString("nome") != null ? doc.getString("nome") : "");
                        cat.put("ordem",    doc.getLong("ordem") != null ? doc.getLong("ordem") : 0);
                        JSONArray items = new JSONArray();
                        Object raw = doc.get("items");
                        if (raw instanceof java.util.List) {
                            for (Object it : (java.util.List<?>) raw) {
                                if (it instanceof Map) {
                                    Map<?,?> m = (Map<?,?>) it;
                                    JSONObject item = new JSONObject();
                                    item.put("nome",    m.getOrDefault("nome",    "").toString());
                                    item.put("preco",   m.getOrDefault("preco",   0));
                                    item.put("destino", m.getOrDefault("destino", "cozinha").toString());
                                    items.put(item);
                                }
                            }
                        }
                        cat.put("items", items);
                        cats.put(cat);
                    }
                    emitir("fbMenuCarregado", cats.toString());
                } catch (Exception e) {
                    Log.e(TAG, "carregarMenu: " + e.getMessage());
                }
            })
            .addOnFailureListener(e -> Log.e(TAG, "carregarMenu: " + e.getMessage()));
    }

    /** Envia pedido para o KDS e grava na colecção pedidos */
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
                    Log.d(TAG, "Pedido enviado: " + ref.getId());
                })
                .addOnFailureListener(e -> Log.e(TAG, "enviarPedido: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "enviarPedido: " + e.getMessage());
        }
    }

    /** Incrementa e devolve o próximo número de senha */
    @JavascriptInterface
    public void proximoNumero() {
        com.google.firebase.firestore.DocumentReference ref =
            db.collection("contadores").document("senhas");
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(ref);
            long actual = snap.exists() && snap.contains("total") ?
                snap.getLong("total") : 0L;
            long proximo = actual + 1;
            Map<String, Object> dados = new HashMap<>();
            dados.put("total", proximo);
            transaction.set(ref, dados);
            return proximo;
        }).addOnSuccessListener(num -> emitir("fbNumeroGerado", String.valueOf(num)))
          .addOnFailureListener(e -> Log.e(TAG, "proximoNumero: " + e.getMessage()));
    }

    /** Reset do contador de senhas */
    @JavascriptInterface
    public void resetarContador() {
        Map<String, Object> dados = new HashMap<>();
        dados.put("total", 0L);
        db.collection("contadores").document("senhas").set(dados)
            .addOnSuccessListener(v -> emitir("fbContadorReset", "ok"))
            .addOnFailureListener(e -> Log.e(TAG, "resetarContador: " + e.getMessage()));
    }

    /** Grava/lê preferências locais */
    @JavascriptInterface
    public void gravarPreferencia(String chave, String valor) {
        SharedPreferences.Editor ed = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit();
        if (valor == null || valor.isEmpty()) ed.remove(chave);
        else ed.putString(chave, valor);
        ed.apply();
    }

    @JavascriptInterface
    public String lerPreferencia(String chave) {
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getString(chave, "");
    }

    public void destroy() {
        if (menuListener != null) { menuListener.remove(); menuListener = null; }
    }
}
