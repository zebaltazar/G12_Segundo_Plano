package pt.ubi.pdm.grupo12.background;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private EditText etTexto;
    private CheckBox cbCarregar;
    private ProgressBar progressBar;
    private TextView tvEstado;
    private TextView tvResultado;

    private final Set<UUID> seen = new HashSet<>();   // trabalhos terminados já tratados
    private boolean firstEmission = true;

    // Activity Result API (a mesma do Lab 5) para pedir a permissão de notificações
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    Toast.makeText(this,
                            granted ? R.string.toast_perm_ok : R.string.toast_perm_negada,
                            Toast.LENGTH_SHORT).show());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationHelper.createChannels(this);

        etTexto = findViewById(R.id.etTexto);
        cbCarregar = findViewById(R.id.cbCarregar);
        progressBar = findViewById(R.id.progressBar);
        tvEstado = findViewById(R.id.tvEstado);
        tvResultado = findViewById(R.id.tvResultado);

        Button btnAgora = findViewById(R.id.btnAgora);
        Button btnCondicionado = findViewById(R.id.btnCondicionado);
        Button btnPeriodico = findViewById(R.id.btnPeriodico);
        Button btnAlarme = findViewById(R.id.btnAlarme);
        Button btnCancelar = findViewById(R.id.btnCancelar);

        btnAgora.setOnClickListener(v -> {
            ensureNotificationPermission();
            TaskScheduler.enqueueNow(this, texto());
            toast(R.string.toast_agendado);
        });

        btnCondicionado.setOnClickListener(v -> {
            ensureNotificationPermission();
            TaskScheduler.enqueueConstrained(this, texto(), cbCarregar.isChecked());
            toast(R.string.toast_agendado);
        });

        btnPeriodico.setOnClickListener(v -> {
            ensureNotificationPermission();
            TaskScheduler.startPeriodic(this, texto());
            toast(R.string.toast_agendado);
        });

        btnAlarme.setOnClickListener(v -> {
            ensureNotificationPermission();
            TaskScheduler.scheduleAlarm(this, 30_000L);
            toast(R.string.toast_alarme);
        });

        btnCancelar.setOnClickListener(v -> {
            TaskScheduler.cancelAll(this);
            toast(R.string.toast_cancelado);
        });

        // Observar o estado de todos os trabalhos desta app (LiveData)
        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData(TaskScheduler.TAG_WORK)
                .observe(this, this::onWorkInfos);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Toque na notificação: mostra o resultado recebido via extra do Intent. */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String r = intent.getStringExtra(NotificationHelper.EXTRA_RESULT);
        if (r != null) tvResultado.setText(r);
    }

    private void onWorkInfos(List<WorkInfo> infos) {
        StringBuilder sb = new StringBuilder();
        int progress = 0;
        boolean running = false;

        for (WorkInfo wi : infos) {
            WorkInfo.State s = wi.getState();
            if (!s.isFinished()) {
                sb.append("• ").append(labelOf(wi)).append(": ").append(s.name());
                if (s == WorkInfo.State.RUNNING) {
                    running = true;
                    progress = wi.getProgress().getInt(TextProcessWorker.KEY_PROGRESS, 0);
                    sb.append(" (").append(progress).append("%)");
                }
                sb.append('\n');
            } else {
                boolean isNew = seen.add(wi.getId());
                if (isNew && !firstEmission && s == WorkInfo.State.SUCCEEDED) {
                    String r = wi.getOutputData().getString(TextProcessWorker.KEY_RESULT);
                    if (r != null) tvResultado.setText(r);
                }
            }
        }
        firstEmission = false;

        tvEstado.setText(sb.length() == 0 ? getString(R.string.estado_sem_tarefas) : sb.toString().trim());
        progressBar.setProgress(progress);
        progressBar.setVisibility(running ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
    }

    private String labelOf(WorkInfo wi) {
        for (String tag : wi.getTags()) {
            if (tag.startsWith(TaskScheduler.TAG_TYPE_PREFIX)) {
                return tag.substring(TaskScheduler.TAG_TYPE_PREFIX.length());
            }
        }
        return "tarefa";
    }

    /** Pede POST_NOTIFICATIONS (API 33+) no contexto da ação do utilizador. */
    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private String texto() {
        return etTexto.getText().toString();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
