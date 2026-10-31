
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.topjohnwu.shell.Shell;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etSsid, etPass;
    private TextView tvLogs;
    private StringBuilder logBuffer = new StringBuilder();

    static {
        // Initialize libsu shell builder
        Shell.enableDebug = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main.xml.hashCode() == 0 ? R.layout.activity_main : R.layout.activity_main);

        etSsid = findViewById(R.id.etSsid);
        etPass = findViewById(R.id.etPass);
        tvLogs = findViewById(R.id.tvLogs);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        // Request root access immediately on launch
        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                appendLog("[+] Root access granted.");
            } else {
                appendLog("[-] Error: Root access denied!");
            }
        });

        btnStart.setOnClickListener(v -> {
            String ssid = etSsid.getText().toString().trim();
            String pass = etPass.getText().toString().trim();

            if (ssid.isEmpty()) {
                appendLog("[-] Please enter an SSID.");
                return;
            }

            new Thread(() -> startBridge(ssid, pass)).start();
        });

        btnStop.setOnClickListener(v -> new Thread(this::stopBridge).start());
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            logBuffer.append(message).append("\n");
            tvLogs.setText(logBuffer.toString());
        });
    }

    private void startBridge(String ssid, String pass) {
        appendLog("[+] Initializing wlan1 station mode...");

        List<String> commands = List.of(
            // 1. Bring up wlan1 in managed mode
            "ip link set wlan1 down",
            "iw dev wlan1 set type managed",
            "ip link set wlan1 up",

            // 2. Write wpa_supplicant config
            "mkdir -p /data/local/tmp/wpa_supplicant",
            "echo 'ctrl_interface=/data/local/tmp/wpa_supplicant' > /data/local/tmp/wlan1_station.conf",
            "echo 'update_config=1' >> /data/local/tmp/wlan1_station.conf",
            "echo 'network={' >> /data/local/tmp/wlan1_station.conf",
            "echo '    ssid=\"" + ssid + "\"' >> /data/local/tmp/wlan1_station.conf",
            "echo '    psk=\"" + pass + "\"' >> /data/local/tmp/wlan1_station.conf",
            "echo '}' >> /data/local/tmp/wlan1_station.conf",

            // 3. Start wpa_supplicant & grab IP via DHCP
            "pkill wpa_supplicant",
            "wpa_supplicant -B -i wlan1 -c /data/local/tmp/wlan1_station.conf",
            "udhcpc -i wlan1 -n",

            // 4. Enable IPv4 forwarding and NAT forwarding rules
            "echo 1 > /proc/sys/net/ipv4/ip_forward",
            "iptables -t nat -F",
            "iptables -t nat -A POSTROUTING -o wlan1 -j MASQUERADE",
            "iptables -A FORWARD -o wlan1 -j ACCEPT",
            "iptables -A FORWARD -i wlan1 -m state --state RELATED,ESTABLISHED -j ACCEPT"
        );

        Shell.Result result = Shell.cmd(commands.toArray(new String[0])).exec();
        
        if (result.isSuccess()) {
            appendLog("[+] wlan1 connected & NAT rules applied!");
            appendLog("[+] Now turn on Android's native Hotspot manually.");
        } else {
            appendLog("[-] Execution error: " + result.getErr());
        }
    }

    private void stopBridge() {
        appendLog("[-] Stopping bridge and clearing rules...");
        List.of(
            "pkill wpa_supplicant",
            "ip link set wlan1 down",
            "iptables -t nat -F",
            "iptables -F"
        ).forEach(cmd -> Shell.cmd(cmd).exec());
        appendLog("[-] Done.");
    }
}
