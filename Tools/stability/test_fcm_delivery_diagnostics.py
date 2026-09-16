"""Static guards for privacy-safe FCM delivery timing and per-account fallback."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MESSENGER = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger"


class FcmDeliveryDiagnosticsTest(unittest.TestCase):
    def test_receiver_records_transport_and_priority_without_payload(self):
        receiver = (MESSENGER / "GcmPushListenerService.java").read_text(encoding="utf-8")
        timing = (MESSENGER / "diagnostics/FcmTiming.java").read_text(encoding="utf-8")
        self.assertIn("message.getSentTime(), message.getOriginalPriority(), message.getPriority()", receiver)
        self.assertIn("Diagnostics.fcmDelivery(transportDelay, clientProcessing", timing)
        self.assertIn("transport_delay_ms=", timing)
        self.assertNotIn('FCM received data:', receiver)

    def test_hybrid_fallback_reads_current_account_preferences(self):
        connections = (ROOT / "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java").read_text(encoding="utf-8")
        method_start = connections.index("    public boolean isPushConnectionEnabled()")
        method_end = connections.index("\n    }", method_start) + 6
        method = connections[method_start:method_end]
        self.assertIn("MessagesController.getNotificationsSettings(currentAccount)", method)
        self.assertIn("MessagesController.getMainSettings(currentAccount)", method)
        self.assertNotIn("getGlobalNotificationsSettings", method)
        self.assertNotIn("UserConfig.selectedAccount", method)

    def test_status_hides_token_value_and_exposes_timing(self):
        settings = (ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/NekoGeneralSettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("Diagnostics.lastFcmDelivery()", settings)
        self.assertIn("FcmPushHybridEnabled", settings)
        self.assertNotIn("token.substring(0, 8)", settings)


if __name__ == "__main__":
    unittest.main()
