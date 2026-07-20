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
