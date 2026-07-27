package pt.criativo.registo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public class BridgeLocal {

    private static final String TAG   = "CriatvReg";
    private static final String PREFS = "CriativoRegisto";

    private final Activity activity;
    private final WebView  webView;

    public BridgeLocal(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView  = webView;
    }

    private void emitir(String evento, String detalhe) {
        String js = "window.dispatchEvent(new CustomEvent('" + evento + "',{detail:" +
            (detalhe.startsWith("{") || detalhe.startsWith("[") || detalhe.startsWith("\"")
                ? detalhe : "\"" + detalhe + "\"") + "}));";
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

    /** Incrementa contador de senhas e devolve o proximo numero */
    @JavascriptInterface
    public int proximoNumero() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        int atual = prefs.getInt("contador_senhas", 0);
        int proximo = (atual % 999) + 1;
        prefs.edit().putInt("contador_senhas", proximo).apply();
        return proximo;
    }

    /** Grava pedido pendente no Firebase (modo B - aguarda activacao) */
    @JavascriptInterface
    public void gravarPedidoPendente(String itemsJson, String totalStr, String numero, String mesa) {
        // Guardar localmente nas SharedPreferences para o Smartphone activar via QR
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        try {
            org.json.JSONObject pedido = new org.json.JSONObject();
            pedido.put("items",   itemsJson);
            pedido.put("total",   Double.parseDouble(totalStr));
            pedido.put("numero",  numero);
            pedido.put("mesa",    mesa != null ? mesa : "");
            pedido.put("estado",  "aguarda_activacao");
            pedido.put("criado",  System.currentTimeMillis());
            // Guardar indexed por numero
            prefs.edit().putString("pedido_" + numero, pedido.toString()).apply();
            // Lista de pendentes
            String lista = prefs.getString("pedidos_pendentes", "[]");
            org.json.JSONArray arr = new org.json.JSONArray(lista);
            arr.put(numero);
            prefs.edit().putString("pedidos_pendentes", arr.toString()).apply();
            emitir("fbPedidoPendente", numero);
            Log.d(TAG, "Pedido pendente guardado: #" + numero);
        } catch (Exception e) {
            Log.e(TAG, "gravarPedidoPendente: " + e.getMessage());
        }
    }

    /** Activa pedido pendente (chamado pelo Smartphone via QR) */
    @JavascriptInterface
    public String buscarPedidoPendente(String numero) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        return prefs.getString("pedido_" + numero, "");
    }

    /** Reseta o contador de senhas */
    @JavascriptInterface
    public void resetarContador() {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit().putInt("contador_senhas", 0).apply();
    }

    /** Imprime bytes ESC/POS via Bluetooth */
    @JavascriptInterface
    public void imprimirBytes(int[] bytes) {
        // Integrar com BluetoothBridge se necessario
        Log.d(TAG, "imprimirBytes: " + bytes.length + " bytes");
    }
}
