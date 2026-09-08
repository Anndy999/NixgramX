"""Run the production remote updater state machine with queued fake Telegram RPCs.

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
    'org/json/JSONException.java': 'public class JSONException extends Exception { public JSONException(){} public JSONException(String s){super(s);} }',
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
        public static int searches; public void sendRequest(TLObject r,Callback c){if(r instanceof TLRPC.TL_messages_search)searches++;requests.add(new Request(account,r,c));}
    }''',
}
# Additional API stubs let the same harness execute UpdateHelper itself.
STUBS.update({
    'android/os/Build.java': 'public class Build { public static String[] SUPPORTED_ABIS={"arm64-v8a"}; }',
    'org/telegram/messenger/BuildConfig.java': 'public class BuildConfig { public static final boolean DEBUG=false; public static final String NIXGRAMX_CHANNEL="stable"; public static final int VERSION_CODE=1284; public static final int UPDATE_BETA_POINTER_ID=101, UPDATE_RELEASE_POINTER_ID=102; public static final long BUILD_TIMESTAMP=10; }',
    'org/telegram/messenger/NotificationCenter.java': 'public class NotificationCenter { public static int appUpdateAvailable, notifications; public static NotificationCenter getGlobalInstance(){return new NotificationCenter();} public void postNotificationName(int n){notifications++;} }',
    'org/telegram/messenger/SharedConfig.java': '''public class SharedConfig {
        public static org.telegram.tgnet.TLRPC.TL_help_appUpdate pendingAppUpdate;
        public static boolean setNewAppVersionAvailable(org.telegram.tgnet.TLRPC.TL_help_appUpdate u){pendingAppUpdate=u;return true;}
        public static void saveConfig(){} }''',
    'org/telegram/messenger/DispatchQueue.java': '''public class DispatchQueue {
        public record Delayed(Runnable runnable,long delay) {}
        public java.util.ArrayDeque<Delayed> delayed=new java.util.ArrayDeque<>();
        public void postRunnable(Runnable r){r.run();}
        public void postRunnable(Runnable r,long delay){delayed.add(new Delayed(r,delay));} }''',
    'org/telegram/messenger/AndroidUtilities.java': '''public class AndroidUtilities {
        public static java.util.ArrayDeque<Runnable> queued=new java.util.ArrayDeque<>();
        public static void runOnUIThread(Runnable r){queued.add(r);}
        public static void drain(){while(!queued.isEmpty())queued.remove().run();} }''',
    'org/telegram/messenger/Utilities.java': '''public class Utilities { public static DispatchQueue globalQueue=new DispatchQueue(),stageQueue=new DispatchQueue(); }''',
    'org/telegram/messenger/diagnostics/Diagnostics.java': 'public class Diagnostics { public enum Event {UPDATE_FAILED,UPDATE_VERSION,UPDATE_PARSE_FAILED,UPDATE_CHECK} public static void event(Event e,int v){} }',
    'xyz/nextalone/nagram/NaConfig.java': 'public class NaConfig { public static NaConfig INSTANCE=new NaConfig(); public static int channel=1; public NaConfig getAutoUpdateChannel(){return this;} public int Int(){return channel;} }',
})
STUBS['org/telegram/messenger/FileLoader.java'] = STUBS['org/telegram/messenger/FileLoader.java'].replace(
    'public class FileLoader {', 'public class FileLoader { public java.io.File getPathToAttach(Object d,boolean b){return null;}')
# JSON is a deterministic boundary stub, not a replacement/parser test for Android org.json.
STUBS['org/json/JSONObject.java'] = '''public class JSONObject {
    String data; public JSONObject(String s) throws JSONException {if(s.equals("malformed"))throw new JSONException();data=s;}
    public boolean has(String key){return !data.contains("universal-only") || !key.equals("arm64-v8a");}
    public int getInt(String key) throws JSONException {
        if(data.contains("missing-code") && key.equals("version_code"))throw new JSONException();
        return switch(key){case "version_code" -> data.contains("old")?1283:data.contains("current")?1284:1285;
        case "sticker","message" -> 0; default -> data.contains("invalid-doc")?-1:42;};
    }
    public long optLong(String key,long fallback){return data.contains("rebuilt")?11:fallback;}
    public String getString(String key) throws JSONException {
        if(key.equals("version") && data.contains("missing-version"))throw new JSONException();
        return key.equals("version")?Integer.toString(getInt("version_code")):"https://example.org/update";
    }
    public boolean getBoolean(String key) throws JSONException {return false;}
    public JSONObject getJSONObject(String key) throws JSONException {return this;}
}'''
STUBS['org/telegram/tgnet/TLRPC.java'] = STUBS['org/telegram/tgnet/TLRPC.java'].replace(
    'public static class TL_help_appUpdate extends TLObject {}',
    'public static class TL_help_appUpdate extends TLObject { public String version,url,text; public boolean can_not_skip; public int flags; public Object sticker,document,entities; }').replace(
    'public static class Message { public String message; }',
    'public static class Media { public Object document; } public static class Message { public String message; public int id; public Media media; public Object entities; } public static class TL_channels_getMessages extends TLObject { public InputChannel channel; public java.util.ArrayList<Integer> id=new java.util.ArrayList<>(); }')
HARNESS = '''
import org.telegram.tgnet.*;
import org.telegram.messenger.*;
import tw.nekomimi.nekogram.helpers.remote.*;
import xyz.nextalone.nagram.NaConfig;
public class RemoteUpdateTest extends BaseRemoteHelper {
    String error; int successes;
    protected String getTag(){return "pagepreview";}
    protected void onError(String e,Delegate d){error=e;}
    protected void onLoadSuccess(java.util.ArrayList<org.json.JSONObject> r,Delegate d,int a,TLRPC.InputChannel c){successes++;}
    static void check(boolean b){if(!b)throw new AssertionError();}
    static ConnectionsManager.Request next(int account,Class<?> type){
        var r=ConnectionsManager.requests.remove();
        check(r.account()==account && type.isInstance(r.body()));return r;
    }
    static void resolve(int account){
        var req=next(account,TLRPC.TL_contacts_resolveUsername.class);
        check(((TLRPC.TL_contacts_resolveUsername)req.body()).username.equals("NixgramXMetadata"));
        var res=new TLRPC.TL_contacts_resolvedPeer();var chat=new TLRPC.Chat();
        chat.id=CHANNEL_METADATA_ID;chat.access_hash=700+account;chat.left=true;
        res.chats.add(chat);req.callback().run(res,null);
    }
    static TLRPC.messages_Messages payload(int id,String data){
        var res=new TLRPC.messages_Messages();var m=new TLRPC.Message();
        m.id=id;m.message=data;res.messages.add(m);return res;
    }
    static ConnectionsManager.Request pointer(int account,int id){
        resolve(account);var req=next(account,TLRPC.TL_channels_getMessages.class);
        var get=(TLRPC.TL_channels_getMessages)req.body();
        check(get.id.equals(new java.util.ArrayList<>(java.util.List.of(id))));
        check(get.channel.channel_id==CHANNEL_METADATA_ID && get.channel.access_hash==700+account);
        return req;
    }
    static void attachments(int account,boolean error){
        var req=next(account,TLRPC.TL_channels_getMessages.class);
        var get=(TLRPC.TL_channels_getMessages)req.body();
        check(get.id.equals(new java.util.ArrayList<>(java.util.List.of(42))));
        check(get.channel.access_hash==700+account);
        var res=payload(42,"");res.messages.get(0).media=new TLRPC.Media();
        res.messages.get(0).media.document=new Object();
        req.callback().run(res,error?new TLRPC.Error():null);
    }
    static class Result {
        int count;String error;TLRPC.TL_help_appUpdate update;
        void complete(TLRPC.TL_help_appUpdate u,String e){
            count++;error=e;update=u;
            // Production callers now write inside the guarded UI callback.
            UpdateHelper.applyPendingUpdateCheckResult(u,e);
        }
    }
    static Result start(boolean manual){
        var r=new Result();UpdateHelper.getInstance().checkNewVersionAvailable(r::complete,false,manual);return r;
    }
    static Result startFileRef(){
        var r=new Result();UpdateHelper.getInstance().checkNewVersionAvailableForFileReference(r::complete);return r;
    }
    static void idle(){
        check(ConnectionsManager.requests.isEmpty());
        check(Utilities.globalQueue.delayed.isEmpty());
        check(AndroidUtilities.queued.isEmpty());
    }
    public static void main(String[] args){
        String test=args[0];UserConfig.selectedAccount=0;NaConfig.channel=2;
        var pending=new TLRPC.TL_help_appUpdate();SharedConfig.pendingAppUpdate=pending;
        if(test.equals("shared-helper")){
            var h=new RemoteUpdateTest();h.load((r,e)->{});resolve(0);
            next(0,TLRPC.TL_messages_search.class).callback().run(new TLRPC.messages_Messages(),null);
            resolve(0);next(0,TLRPC.TL_messages_search.class).callback().run(new TLRPC.messages_Messages(),null);
            check(h.successes==1 && h.error==null && ConnectionsManager.searches==2);
            idle();return;
        }
        if(test.equals("overlap-fileref-then-manual") || test.equals("overlap-manual-then-fileref")){
            Result fileRef; Result manual;
            ConnectionsManager.Request fileReq; ConnectionsManager.Request manReq;
            if(test.equals("overlap-fileref-then-manual")){
                fileRef=startFileRef();fileReq=pointer(0,101);
                manual=start(true);manReq=pointer(0,101);
            }else{
                manual=start(true);manReq=pointer(0,101);
                fileRef=startFileRef();fileReq=pointer(0,101);
            }
            fileReq.callback().run(payload(101,"new"),null);attachments(0,false);
            check(fileRef.count==1 && fileRef.error==null && fileRef.update!=null);
            manReq.callback().run(payload(101,"new"),null);attachments(0,false);
            AndroidUtilities.drain();
            check(manual.count==1 && manual.error==null && manual.update!=null);
        }else if(test.startsWith("overlap")){
            var older=start(true);var oldReq=pointer(0,101);
            if(test.equals("overlap-queued")){
                oldReq.callback().run(payload(101,"current"),null);
            }
            var newer=start(true);var newReq=pointer(0,101);
            newReq.callback().run(payload(101,"new"),null);attachments(0,false);
            AndroidUtilities.drain();var accepted=SharedConfig.pendingAppUpdate;
            check(accepted!=null && accepted.version.equals("1285"));
            if(!test.equals("overlap-queued")){
                oldReq.callback().run(payload(101,"current"),null);AndroidUtilities.drain();
            }
            check(older.count==1 && older.error.equals("UPDATE_CHECK_SUPERSEDED"));
            check(newer.count==1 && SharedConfig.pendingAppUpdate==accepted);
        }else{
            if(test.equals("release"))NaConfig.channel=1;
            var result=start(!test.equals("automatic"));
            if(test.equals("account"))UserConfig.selectedAccount=2;
            if(test.equals("lane"))NaConfig.channel=1;
            if(test.equals("resolve-error")){
                next(0,TLRPC.TL_contacts_resolveUsername.class).callback().run(null,new TLRPC.Error());
            }else{
                int id=test.equals("release")?102:101;var req=pointer(0,id);
                String data=switch(test){
                    case "same" -> "current";
                    case "rebuilt" -> "current rebuilt";
                    case "malformed" -> "malformed";
                    case "missing-code" -> "current missing-code";
                    case "missing-version" -> "current missing-version";
                    case "invalid-doc" -> "current invalid-doc";
                    case "universal" -> "new universal-only";
                    default -> "new";
                };
                if(test.equals("pointer-error"))req.callback().run(null,new TLRPC.Error());
                else if(test.equals("missing-pointer"))req.callback().run(new TLRPC.messages_Messages(),null);
                else{
                    req.callback().run(payload(id,data),null);
                    if(!ConnectionsManager.requests.isEmpty())attachments(0,test.equals("attachment-error"));
                }
                if(test.equals("duplicate"))req.callback().run(payload(id,"current"),null);
            }
            AndroidUtilities.drain();check(result.count==1);
            if(test.contains("error") || test.startsWith("missing") || test.equals("malformed") || test.equals("invalid-doc")){
                check(result.error!=null && SharedConfig.pendingAppUpdate==pending);
            }else if(test.equals("same")){
                check(result.error==null && result.update==null && SharedConfig.pendingAppUpdate==null);
            }else{
                check(result.error==null && result.update!=null && result.update.document!=null);
                check(result.update.version.equals(test.equals("rebuilt")?"1284":"1285"));
            }
            if(test.equals("account"))check(MessagesController.lastAccount==0 && MessagesStorage.lastAccount==0);
        }
        // Mandatory test 13: every V2 scenario must execute ZERO search requests.
        check(ConnectionsManager.searches==0);idle();
        System.out.println("PASS "+test+"; V2 search requests=0; delayed tasks=0");
    }
}
'''


class RemoteUpdateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        for name, body in STUBS.items():
            path = cls.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('package ' + '/'.join(Path(name).parts[:-1]).replace('/', '.') + ';\\n' + body)
        for helper in ('BaseRemoteHelper.java', 'UpdateHelper.java'):
            shutil.copy(ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote' / helper, cls.root)
        (cls.root / 'RemoteUpdateTest.java').write_text(HARNESS)
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(cls.root), *map(str, cls.root.rglob('*.java'))], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()


def scenario(name):
    def test(self):
        subprocess.run(['java', '-cp', str(self.root), 'RemoteUpdateTest', name], check=True)
    return test


for case in ('same', 'new', 'rebuilt', 'release', 'automatic', 'account', 'lane',
             'malformed', 'missing-code', 'missing-version', 'invalid-doc',
             'resolve-error', 'pointer-error', 'missing-pointer', 'attachment-error',
             'overlap', 'overlap-queued', 'overlap-fileref-then-manual',
             'overlap-manual-then-fileref', 'duplicate', 'universal', 'shared-helper'):
    setattr(RemoteUpdateTest, 'test_' + case.replace('-', '_'), scenario(case))


if __name__ == '__main__':
    unittest.main()
