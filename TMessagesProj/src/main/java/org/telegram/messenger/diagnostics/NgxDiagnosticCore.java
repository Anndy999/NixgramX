package org.telegram.messenger.diagnostics;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Android-free, fail-safe, bounded diagnostic state. No public API throws. */
public final class NgxDiagnosticCore {
 public enum Level { OFF, DIAGNOSTIC, TRACE }
 public enum Category { APP, UI, NAVIGATION, GESTURE, CHAT, LAYOUT, MESSAGE, EMOJI, MEDIA, PLAYER, DOWNLOAD, UPLOAD, TRANSLATION, NETWORK, MTPROTO, STORAGE, DATABASE, NOTIFICATION, PUSH, BACKGROUND, GHOST, NIXGRAMX, PERFORMANCE, ERROR }
 public enum Field { RESULT, REASON, OLD_STATE, NEW_STATE, TRIGGER, DIRECTION, INDEX, TARGET_INDEX, COUNT, DX, DY, VELOCITY, WIDTH, HEIGHT, LINE_COUNT, MEDIA_TYPE, PROVIDER, SOURCE_LANGUAGE, TARGET_LANGUAGE, REQUEST_TYPE, REQUEST_TOKEN, DURATION_MS, ERROR_CATEGORY, RULE, THREAD }
 public static final int RING_LIMIT=100, CAPTURE_EVENTS=50;
 private static final AtomicInteger IDS=new AtomicInteger();
 private final ArrayDeque<String> ring=new ArrayDeque<>(RING_LIMIT); private final LinkedHashMap<String,Long> dup=new LinkedHashMap<>();
 private volatile Level level=Level.OFF; private String sid; private int remaining; private Level restore=Level.OFF; private long dropped;
 public Level level(){return level;} public boolean enabled(Level l){return level.ordinal()>=l.ordinal();} public long dropped(){return dropped;}
 public void setLevel(Level l){level=l==null?Level.OFF:l;}
 public static final class Value { final Field f; final String v; private Value(Field f,String v){this.f=f;this.v=v;} public static Value bool(Field f,boolean v){return new Value(f,""+v);} public static Value integer(Field f,int v){return new Value(f,""+v);} public static Value number(Field f,long v){return new Value(f,""+v);} public static Value enumValue(Field f,Enum<?> v){return v==null?null:new Value(f,v.name());} }
 public synchronized String begin(){ if(!enabled(Level.DIAGNOSTIC))return null; sid=id(); return emit(Category.APP,"SESSION_START"); }
 public synchronized String end(){ return emit(Category.APP,"SESSION_END"); }
 public synchronized boolean capture(){ if(remaining!=0)return false; restore=level; level=Level.DIAGNOSTIC; sid=id(); remaining=CAPTURE_EVENTS; emit(Category.APP,"CAPTURE_START",Value.integer(Field.COUNT,ring.size())); return true; }
 public synchronized String event(Category c,String name,Value... values){ try { if(!enabled(Level.DIAGNOSTIC)&&remaining==0)return null; if(c==null||!constant(name)||values==null){drop();return null;} StringBuilder b=new StringBuilder(96).append("[NGX]|").append(c).append("|sid=").append(sid==null?"NONE":sid).append("|event=").append(name); for(Value x:values){if(x==null||x.f==null||!constant(x.v)){drop();return null;} b.append('|').append(x.f.name().toLowerCase(Locale.US)).append('=').append(x.v);} return record(b.toString()); }catch(Throwable ignored){drop();return null;} }
 public synchronized List<String> snapshot(){return new ArrayList<>(ring);} public synchronized void clear(){ring.clear();dup.clear();sid=null;remaining=0;level=restore;restore=Level.OFF;}
 private String emit(Category c,String n,Value... v){return event(c,n,v);} private String record(String s){long now=System.nanoTime()/1000000L; Long old=dup.get(s);if(old!=null&&now-old<1000)return null;dup.put(s,now);if(dup.size()>128)dup.remove(dup.keySet().iterator().next());if(ring.size()==RING_LIMIT)ring.removeFirst();ring.addLast(s);if(remaining>0&&--remaining==0){level=restore;restore=Level.OFF;sid=null;}return s;}
 private void drop(){dropped++;} private static String id(){return String.format(Locale.US,"%04X",IDS.incrementAndGet()&0xffff);} private static boolean constant(String s){return s!=null&&s.length()<=80&&s.matches("[A-Z0-9_]+") && !s.contains("TOKEN")&&!s.contains("PASSWORD")&&!s.contains("USERNAME");}
}
