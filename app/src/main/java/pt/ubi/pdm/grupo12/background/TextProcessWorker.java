package pt.ubi.pdm.grupo12.background;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Trabalho em segundo plano: analisa um texto (palavras, distintas, mais frequente).
 * O doWork() corre numa thread do WorkManager (nunca na thread principal).
 * A pausa de 500 ms por passo simula uma tarefa demorada (10 passos = ~5 s).
 */
public class TextProcessWorker extends Worker {

    public static final String KEY_TEXT = "texto";
    public static final String KEY_PROGRESS = "progresso";
    public static final String KEY_RESULT = "resultado";

    public static final String DEFAULT_TEXT =
            "Era uma vez uma história que ninguém sabia terminar. "
            + "Cada escritor acrescentava uma frase, e a história crescia; "
            + "a história mudava, e a história nunca terminava.";

    private static final String TAG = "G12Worker";
    private static final int STEPS = 10;
    private static final long STEP_MS = 500;

    public TextProcessWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String text = getInputData().getString(KEY_TEXT);
        if (text == null || text.trim().isEmpty()) {
            text = DEFAULT_TEXT;
        }
        Log.i(TAG, "Início (tentativa " + getRunAttemptCount()
                + ") na thread " + Thread.currentThread().getName());

        try {
            for (int i = 1; i <= STEPS; i++) {
                if (isStopped()) {                       // cancelamento / restrições deixaram de ser cumpridas
                    Log.w(TAG, "Trabalho parado pelo sistema no passo " + i);
                    return Result.retry();
                }
                Thread.sleep(STEP_MS);
                setProgressAsync(new Data.Builder()
                        .putInt(KEY_PROGRESS, i * 100 / STEPS)
                        .build());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        String summary = analyse(text);
        Log.i(TAG, "Fim: " + summary);

        NotificationHelper.showDone(getApplicationContext(), summary);

        return Result.success(new Data.Builder().putString(KEY_RESULT, summary).build());
    }

    /** Lógica de negócio pura (fácil de testar sem Android). */
    static String analyse(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .trim();
        Map<String, Integer> freq = new HashMap<>();
        int total = 0;
        for (String w : cleaned.split("\\s+")) {
            if (w.isEmpty()) continue;
            total++;
            freq.merge(w, 1, Integer::sum);
        }
        if (total == 0) {
            return "Sem palavras para analisar.";
        }
        String top = "";
        int topCount = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            int c = e.getValue();
            // desempate alfabético => resultado determinístico
            if (c > topCount || (c == topCount && e.getKey().compareTo(top) < 0)) {
                top = e.getKey();
                topCount = c;
            }
        }
        return String.format(Locale.getDefault(),
                "%d palavras, %d distintas, %d caracteres. Mais frequente: \"%s\" (%dx).",
                total, freq.size(), text.length(), top, topCount);
    }
}
