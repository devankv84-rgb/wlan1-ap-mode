#!/system/bin/sh
# Wait until boot completes
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Enable IPv4 forwarding at system level
echo 1 > /proc/sys/net/ipv4/ip_forward

# Ensure hostapd and dnsmasq directories exist if needed
mkdir -p /data/local/tmp/wlan1_ap
