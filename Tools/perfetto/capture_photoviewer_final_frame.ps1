param(
    [string]$Adb = "adb",
    [string]$PackageName = "app.nixgramx.android",
    [int]$DurationSeconds = 15,
    [string]$OutputPath = (Join-Path $PWD "ngx-photoviewer-final-frame.perfetto-trace")
)

$ErrorActionPreference = "Stop"
$remoteTrace = "/data/misc/perfetto-traces/ngx-photoviewer-final-frame.perfetto-trace"

& $Adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "No authorized Android device is available through adb."
}

# FrameTimeline is optional on older Android/Samsung Perfetto builds.
$dataSources = (& $Adb shell perfetto --query-raw 2>$null) -join "`n"
$frameTimeline = ""
if ($dataSources -match "android\.surfaceflinger\.frametimeline") {
    $frameTimeline = @'
data_sources {
  config { name: "android.surfaceflinger.frametimeline" }
}
'@
}

$config = @"
buffers {
  size_kb: 65536
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "binder/binder_transaction"
      ftrace_events: "binder/binder_transaction_received"
      atrace_categories: "am"
      atrace_categories: "binder_driver"
      atrace_categories: "freq"
      atrace_categories: "gfx"
      atrace_categories: "memory"
      atrace_categories: "sched"
      atrace_categories: "view"
      atrace_categories: "wm"
      atrace_apps: "$PackageName"
    }
  }
}
$frameTimeline
duration_ms: $($DurationSeconds * 1000)
"@

Write-Host "Perfetto will capture for $DurationSeconds seconds."
Write-Host "Immediately after pressing Enter: attachment -> image -> thumbnail -> PhotoViewer -> wait -> back, three times."
Read-Host "Press Enter to start"

# This blocks only the host terminal; tracing runs on the device during the
# requested interaction window. Search trace sections as NGX_PV_ / NGX_ATTACH_.
$config | & $Adb shell "perfetto --txt -c - -o $remoteTrace"
if ($LASTEXITCODE -ne 0) {
    throw "perfetto capture failed; verify that the device permits shell Perfetto tracing."
}

& $Adb pull $remoteTrace $OutputPath
if ($LASTEXITCODE -ne 0) {
    throw "Capture finished but the trace could not be pulled from the device."
}

Write-Host "Trace saved to: $OutputPath"
Write-Host "Open it in https://ui.perfetto.dev and search NGX_PV_ or NGX_ATTACH_."
