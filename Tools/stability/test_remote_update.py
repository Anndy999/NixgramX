"""Run the production remote search state machine with queued fake Telegram RPCs.

Requires a JDK (javac/java); no Android runtime, network, or Telegram account.
"""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    'android/app/Activity.java': 'public class Activity { public static final int MODE_PRIVATE=0; }',
    'android/text/TextUtils.java': 'public class TextUtils { public static boolean isEmpty(String s) { return s==null || s.isEmpty(); } }',
    'android/content/SharedPreferences.java': '''public class SharedPreferences {
        public static java.util.HashMap<String,Object> map=new java.util.HashMap<>();
        public SharedPreferences edit(){return this;} public SharedPreferences remove(String s){map.remove(s);return this;}
        public SharedPreferences putLong(String s,long v){map.put(s,v);return this;}
        public SharedPreferences putString(String s,String v){map.put(s,v);return this;} public void apply(){}
        public String getString(String s,String d){Object v=map.get(s);return v==null?d:(String)v;}
        public long getLong(String s,long d){Object v=map.get(s);return v==null?d:((Long)v);} }''',
    'org/json/JSONException.java': 'public class JSONException extends Exception {}',
    'org/json/JSONObject.java': 'public class JSONObject { public JSONObject(String s) throws JSONException {} }',
    'org/telegram/messenger/ApplicationLoader.java': '''public class ApplicationLoader {
        public static ApplicationLoader applicationContext=new ApplicationLoader();
        public android.content.SharedPreferences getSharedPreferences(String s,int mode){return new android.content.SharedPreferences();} }''',
    'org/telegram/messenger/UserConfig.java': 'public class UserConfig { public static int selectedAccount; }',
    'org/telegram/messenger/FileLog.java': 'public class FileLog { public static void e(Exception e){} }',
    'org/telegram/messenger/ChatObject.java': 'public class ChatObject { public static boolean isChannel(org.telegram.tgnet.TLRPC.Chat c){return true;} }',
    'org/telegram/messenger/FileLoader.java': 'public class FileLoader { public static FileLoader getInstance(int a){return new FileLoader();} }',
    'org/telegram/messenger/MessagesStorage.java': '''public class MessagesStorage {
        public static int lastAccount=-1; public static MessagesStorage getInstance(int a){lastAccount=a;return new MessagesStorage();}
        public void putUsersAndChats(Object u,Object c,boolean x,boolean y){} }''',
    'org/telegram/messenger/MessagesController.java': '''public class MessagesController {
        public static int lastAccount=-1; public static MessagesController getInstance(int a){lastAccount=a;return new MessagesController();}
        public void putUsers(Object u,boolean b){} public void putChats(Object c,boolean b){}
        public void removeDeletedMessagesFromArray(long id,Object messages){}
        public static org.telegram.tgnet.TLRPC.InputChannel getInputChannel(org.telegram.tgnet.TLRPC.Chat c){
            var p=new org.telegram.tgnet.TLRPC.InputChannel();p.channel_id=c.id;p.access_hash=c.access_hash;return p;} }''',
    'org/telegram/tgnet/TLObject.java': 'public class TLObject {}',
    'org/telegram/tgnet/TLRPC.java': '''public class TLRPC {
        public static class Chat { public long id,access_hash; public boolean left; }
        public static class InputChannel { public long channel_id,access_hash; }
        public static class TL_inputPeerChannel extends InputChannel {}
        public static class TL_help_appUpdate extends TLObject {}
        public static class TL_contacts_resolveUsername extends TLObject { public String username; }
        public static class TL_contacts_resolvedPeer extends TLObject {
            public java.util.ArrayList<Chat> chats=new java.util.ArrayList<>();
            public java.util.ArrayList<Object> users=new java.util.ArrayList<>(); }
        public static class Message { public String message; }
        public static class messages_Messages extends TLObject { public java.util.ArrayList<Message> messages=new java.util.ArrayList<>(); }
        public static class TL_inputMessagesFilterEmpty {}
        public static class TL_messages_search extends TLObject { public int limit; public String q;
            public TL_inputMessagesFilterEmpty filter; public TL_inputPeerChannel peer; }
        public static class Error { public String text="CHANNEL_PRIVATE"; }
    }''',
    'org/telegram/tgnet/ConnectionsManager.java': '''public class ConnectionsManager {
        public interface Callback { void run(TLObject response, TLRPC.Error error); }
        public record Request(int account, TLObject body, Callback callback) {}
        public static java.util.ArrayDeque<Request> requests=new java.util.ArrayDeque<>();
        private int account; public static ConnectionsManager getInstance(int a){var c=new ConnectionsManager();c.account=a;return c;}
        public void sendRequest(TLObject r,Callback c){requests.add(new Request(account,r,c));}
    }''',
}
# Additional API stubs let the same harness execute UpdateHelper itself.
STUBS.update({
    'android/os/Build.java': 'public class Build { public static String[] SUPPORTED_ABIS={"arm64-v8a"}; }',
    'org/telegram/messenger/BuildConfig.java': 'public class BuildConfig { public static final boolean DEBUG=false; public static final String NIXGRAMX_CHANNEL="stable"; public static final int VERSION_CODE=10; public static final long BUILD_TIMESTAMP=10; }',
    'org/telegram/messenger/NotificationCenter.java': 'public class NotificationCenter { public static int appUpdateAvailable; public static NotificationCenter getGlobalInstance(){return new NotificationCenter();} public void postNotificationName(int n){} }',
    'org/telegram/messenger/SharedConfig.java': '''public class SharedConfig {
        public static org.telegram.tgnet.TLRPC.TL_help_appUpdate pendingAppUpdate;
        public static boolean setNewAppVersionAvailable(org.telegram.tgnet.TLRPC.TL_help_appUpdate u){pendingAppUpdate=u;return true;}
        public static void saveConfig(){} }''',
    'org/telegram/messenger/Utilities.java': 'public class Utilities { public static Utilities globalQueue=new Utilities(); public void postRunnable(Runnable r){r.run();} }',
    'org/telegram/messenger/diagnostics/Diagnostics.java': 'public class Diagnostics { public enum Event {UPDATE_FAILED,UPDATE_VERSION,UPDATE_PARSE_FAILED,UPDATE_CHECK} public static void event(Event e,int v){} }',
    'xyz/nextalone/nagram/NaConfig.java': 'public class NaConfig { public static NaConfig INSTANCE=new NaConfig(); public static int channel=1; public NaConfig getAutoUpdateChannel(){return this;} public int Int(){return channel;} }',
})
STUBS['org/telegram/messenger/FileLoader.java'] = STUBS['org/telegram/messenger/FileLoader.java'].replace(
    'public class FileLoader {', 'public class FileLoader { public java.io.File getPathToAttach(Object d,boolean b){return null;}')
STUBS['org/json/JSONObject.java'] = '''public class JSONObject {
    String data; public JSONObject(String s) throws JSONException {data=s;}
    public int getInt(String key) throws JSONException {return switch(key){case "version_code" -> data.contains("old")?1:20; case "sticker","message" -> 0; default -> 42;};}
    public long optLong(String key,long fallback){return fallback;}
    public String getString(String key) throws JSONException {return key.equals("version")?"20":"https://example.org/update";}
    public boolean getBoolean(String key) throws JSONException {return false;}
    public JSONObject getJSONObject(String key) throws JSONException {return this;}
}'''
STUBS['org/telegram/tgnet/TLRPC.java'] = STUBS['org/telegram/tgnet/TLRPC.java'].replace(
    'public static class TL_help_appUpdate extends TLObject {}',
    'public static class TL_help_appUpdate extends TLObject { public String version,url,text; public boolean can_not_skip; public int flags; public Object sticker,document,entities; }').replace(
    'public static class Message { public String message; }',
    'public static class Media { public Object document; } public static class Message { public String message; public int id; public Media media; public Object entities; } public static class TL_channels_getMessages extends TLObject { public InputChannel channel; public java.util.ArrayList<Integer> id; }')
HARNESS = '''
import org.telegram.tgnet.*;
import org.telegram.messenger.*;
import tw.nekomimi.nekogram.helpers.remote.BaseRemoteHelper;
import tw.nekomimi.nekogram.helpers.remote.UpdateHelper;
import xyz.nextalone.nagram.NaConfig;
public class RemoteUpdateTest extends BaseRemoteHelper {
    String tag, error; int successes, account; TLRPC.InputChannel channel;
    java.util.ArrayList<org.json.JSONObject> lastResponses;
    RemoteUpdateTest(String tag){this.tag=tag;}
    protected String getTag(){return tag;}
    protected void onError(String e, Delegate d){error=e;}
    protected void onLoadSuccess(java.util.ArrayList<org.json.JSONObject> r, Delegate d, int a, TLRPC.InputChannel c){
        successes++;account=a;channel=c;lastResponses=r;
    }
    /** Mimics PagePreviewRulesHelper: empty success clears cache prefs, never errors. */
    static class PagePreviewStub extends BaseRemoteHelper {
        boolean cleared; String error;
        protected String getTag(){return "pagepreview";}
        protected void onError(String e, Delegate d){error=e;}
        protected void onLoadSuccess(java.util.ArrayList<org.json.JSONObject> r, Delegate d){
            if(r==null||r.isEmpty()){cleared=true;preferences.edit().remove(getTag()).apply();}
        }
    }
    static void check(boolean b){if(!b)throw new AssertionError();}
    static ConnectionsManager.Request next(int account, Class<?> type){
        var r=ConnectionsManager.requests.remove();check(r.account()==account && type.isInstance(r.body()));return r;
    }
    static void resolve(int account,long id,long hash,boolean left){
        var r=next(account,TLRPC.TL_contacts_resolveUsername.class);
        check(((TLRPC.TL_contacts_resolveUsername)r.body()).username.equals("NixgramXMetadata"));
        var result=new TLRPC.TL_contacts_resolvedPeer();var c=new TLRPC.Chat();
        c.id=id;c.access_hash=hash;c.left=left;result.chats.add(c);r.callback().run(result,null);
    }
    static void search(int account,String tag,long hash,boolean empty,boolean fail){
        var r=next(account,TLRPC.TL_messages_search.class);var q=(TLRPC.TL_messages_search)r.body();
        check(q.q.equals("#"+tag) && q.peer.channel_id==CHANNEL_METADATA_ID && q.peer.access_hash==hash);
        var result=new TLRPC.messages_Messages();
        if(!empty){var m=new TLRPC.Message();m.message="#"+tag+" {}";result.messages.add(m);}
        r.callback().run(result,fail?new TLRPC.Error():null);
    }
    static void drainEmptyThen(int account,String tag,long hash1,long hash2,boolean secondEmpty,boolean secondFail){
        resolve(account,CHANNEL_METADATA_ID,hash1,true);
        search(account,tag,hash1,true,false);
        resolve(account,CHANNEL_METADATA_ID,hash2,true);
        search(account,tag,hash2,secondEmpty,secondFail);
    }
    public static void main(String[] args){
        // updateRelease + updateBeta lanes, overlapping accounts, account/lane switch during callback.
        for(String tag:new String[]{"updateBeta","updateRelease"}){
            var a=new RemoteUpdateTest(tag);var b=new RemoteUpdateTest(tag);
            UserConfig.selectedAccount=0;a.load((r,e)->{});
            UserConfig.selectedAccount=1;b.load((r,e)->{});
            a.tag="changed";
            resolve(0,CHANNEL_METADATA_ID,100,true);resolve(1,CHANNEL_METADATA_ID,200,false);
            search(0,tag,100,false,false);search(1,tag,200,false,false);
            check(a.successes==1 && a.account==0 && a.channel.access_hash==100);
            check(b.successes==1 && b.account==1 && b.channel.access_hash==200);
            // Non-member metadata search + first empty then successful retry.
            var c=new RemoteUpdateTest(tag);UserConfig.selectedAccount=0;c.load((r,e)->{});
            UserConfig.selectedAccount=1;resolve(0,CHANNEL_METADATA_ID,300,true);
            search(0,tag,300,true,false);resolve(0,CHANNEL_METADATA_ID,301,true);
            search(0,tag,301,false,false);check(c.successes==1 && c.account==0);
            check(MessagesStorage.lastAccount==0 && MessagesController.lastAccount==0);
            // BaseRemoteHelper persistent empty → onLoadSuccess(empty), NOT UPDATE_METADATA_EMPTY.
            var emptyOk=new RemoteUpdateTest(tag);emptyOk.load((r,e)->{});
            drainEmptyThen(1,tag,400,401,true,false);
            check(emptyOk.successes==1 && emptyOk.error==null && emptyOk.lastResponses.isEmpty());
            check(ConnectionsManager.requests.isEmpty());
            // Persistent search errors still report the Telegram error.
            var fail=new RemoteUpdateTest(tag);fail.load((r,e)->{});
            drainEmptyThen(1,tag,402,403,true,true);
            check(fail.successes==0 && "CHANNEL_PRIVATE".equals(fail.error));
            check(ConnectionsManager.requests.isEmpty());
            // Public @NixgramX APK channel id must never be used as metadata.
            var e=new RemoteUpdateTest(tag);e.load((r,x)->{});resolve(1,3819693045L,500,true);
            check("CHANNEL_INVALID".equals(e.error) && ConnectionsManager.requests.isEmpty());
        }

        // UpdateHelper persistent empty → UPDATE_METADATA_EMPTY alone.
        final String[] updateErrors=new String[1];
        UserConfig.selectedAccount=0;NaConfig.channel=1;
        UpdateHelper.getInstance().checkNewVersionAvailable((r,err)->{updateErrors[0]=err;});
        drainEmptyThen(0,"updateRelease",610,611,true,false);
        check("UPDATE_METADATA_EMPTY".equals(updateErrors[0]));
        check(ConnectionsManager.requests.isEmpty());

        // PagePreviewRulesHelper-style persistent empty → empty success / cache clear.
        var preview=new PagePreviewStub();UserConfig.selectedAccount=0;preview.load((r,e)->{});
        drainEmptyThen(0,"pagepreview",620,621,true,false);
        check(preview.cleared && preview.error==null);
        check(ConnectionsManager.requests.isEmpty());

        // Start a manual OFF check and a release background check on different accounts.
        // Finish them after selecting a third account and disabling the global lane.
        final TLRPC.TL_help_appUpdate[] updates=new TLRPC.TL_help_appUpdate[2];
        UserConfig.selectedAccount=0;NaConfig.channel=0;
        UpdateHelper.getInstance().checkNewVersionAvailable((r,e)->{check(e==null);updates[0]=r;},true,true);
        UserConfig.selectedAccount=1;NaConfig.channel=1;
        UpdateHelper.getInstance().checkNewVersionAvailable((r,e)->{check(e==null);updates[1]=r;});
        UserConfig.selectedAccount=2;NaConfig.channel=0;
        resolve(0,CHANNEL_METADATA_ID,600,true);resolve(1,CHANNEL_METADATA_ID,700,true);
        var manualSearch=next(0,TLRPC.TL_messages_search.class);
        check(((TLRPC.TL_messages_search)manualSearch.body()).q.equals("#updateRelease"));
        var old=new TLRPC.messages_Messages();var oldMessage=new TLRPC.Message();
        oldMessage.message="#updateRelease old";old.messages.add(oldMessage);manualSearch.callback().run(old,null);
        search(1,"updateRelease",700,false,false);
        for(int i=0;i<2;i++){
            var request=next(i,TLRPC.TL_channels_getMessages.class);
            var get=(TLRPC.TL_channels_getMessages)request.body();
            check(get.channel.channel_id==CHANNEL_METADATA_ID && get.channel.access_hash==(i==0?600:700));
            check(get.id.equals(new java.util.ArrayList<>(java.util.List.of(42))));
            var messages=new TLRPC.messages_Messages();var apk=new TLRPC.Message();apk.id=42;
            apk.media=new TLRPC.Media();apk.media.document=new Object();messages.messages.add(apk);
            request.callback().run(messages,null);
            check(updates[i]!=null && updates[i].document==apk.media.document);
            check(MessagesController.lastAccount==i);
        }
        check(ConnectionsManager.requests.isEmpty());

        // pending+failed → preserved; pending+successful no-update → cleared.
        var pending=new TLRPC.TL_help_appUpdate();pending.version="19";
        SharedConfig.pendingAppUpdate=pending;
        check(!UpdateHelper.shouldClearPendingAppUpdate(null,"SOME_ERROR"));
        UpdateHelper.applyPendingUpdateCheckResult(null,"SOME_ERROR");
        check(SharedConfig.pendingAppUpdate==pending);
        check(UpdateHelper.shouldClearPendingAppUpdate(null,null));
        UpdateHelper.applyPendingUpdateCheckResult(null,null);
        check(SharedConfig.pendingAppUpdate==null);
        var newer=new TLRPC.TL_help_appUpdate();newer.version="20";
        UpdateHelper.applyPendingUpdateCheckResult(newer,null);
        check(SharedConfig.pendingAppUpdate==newer);

        System.out.println("Remote update RPC regression scenarios passed");
    }
}
'''


class RemoteUpdateTest(unittest.TestCase):
    def test_rpc_scenarios(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, body in STUBS.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('package ' + str(Path(name).parent).replace('/', '.') + ';\n' + body)
            for helper in ('BaseRemoteHelper.java', 'UpdateHelper.java'):
                shutil.copy(ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote' / helper, root)
            (root / 'RemoteUpdateTest.java').write_text(HARNESS)
            subprocess.run(['javac', '-d', str(root), *map(str, root.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(root), 'RemoteUpdateTest'], check=True)


if __name__ == '__main__':
    unittest.main()
