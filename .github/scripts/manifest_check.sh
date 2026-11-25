#!/usr/bin/env bash
# Checks the merged release manifest: only allowed permissions, and only allowed exported components.
set -uo pipefail
M=$(find app/build/intermediates/merged_manifest/release -name AndroidManifest.xml | head -1)
[ -f "$M" ] || { echo "::error::merged release manifest not found"; exit 1; }
fail=0
ALLOWED_PERMS='android.permission.(INTERNET|ACCESS_NETWORK_STATE|POST_NOTIFICATIONS|REQUEST_INSTALL_PACKAGES|WAKE_LOCK|RECEIVE_BOOT_COMPLETED|FOREGROUND_SERVICE)|com.emotionalleaves.sprout(.debug)?.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
perms=$(grep -oE '<uses-permission[^>]*android:name="[^"]+"' "$M" | sed -E 's/.*android:name="([^"]+)"/\1/')
for p in $perms; do
  if ! echo "$p" | grep -qE "^($ALLOWED_PERMS)$"; then echo "::error::Unexpected permission $p"; fail=1; fi
done
# Exported components: the launcher activity, WorkManager's job service (system-bound, BIND_JOB_SERVICE)
# the home-screen widget receiver (the launcher delivers APPWIDGET_UPDATE to it) and Glance's
# RemoteViews service (system-bound, protected by BIND_REMOTEVIEWS).
exported=$(python3 - "$M" <<'PY'
import sys, xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
root=ET.parse(sys.argv[1]).getroot()
for el in root.find('application'):
    if el.get(A+'exported')=='true':
        print(el.get(A+'name'))
PY
)
for e in $exported; do
  case "$e" in
    com.emotionalleaves.sprout.MainActivity|androidx.work.impl.background.systemjob.SystemJobService|com.emotionalleaves.sprout.widget.SafeToSpendWidgetReceiver|androidx.glance.appwidget.GlanceRemoteViewsService) ;;
    *) echo "::error::Unexpected exported component $e"; fail=1 ;;
  esac
done
if grep -q 'GlanceRemoteViewsService' "$M" && ! grep -A3 'GlanceRemoteViewsService' "$M" | grep -q 'BIND_REMOTEVIEWS'; then
  echo "::error::GlanceRemoteViewsService must require BIND_REMOTEVIEWS"; fail=1
fi
[ "$fail" -eq 0 ] && echo "Manifest check passed: $(echo $perms | wc -w) permissions, exported: $(echo $exported)"
exit $fail
