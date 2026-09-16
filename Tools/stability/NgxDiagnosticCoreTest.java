import org.telegram.messenger.diagnostics.NgxDiagnosticCore;
import java.util.*;
public class NgxDiagnosticCoreTest {
 static void ok(boolean b,String n){if(!b)throw new AssertionError(n);}
 static String e(NgxDiagnosticCore c,int n){return c.event(NgxDiagnosticCore.Category.APP,"STEP",NgxDiagnosticCore.Value.integer(NgxDiagnosticCore.Field.INDEX,n));}
 public static void main(String[] x)throws Exception{
  NgxDiagnosticCore c=new NgxDiagnosticCore(); ok(c.event(NgxDiagnosticCore.Category.APP,"OFF")==null,"off"); c.setLevel(NgxDiagnosticCore.Level.DIAGNOSTIC);
  String a=c.begin(); ok(a!=null&&a.contains("sid="),"session"); ok(e(c,1)!=null&&e(c,1)==null&&e(c,2)!=null,"dedupe");
  for(int i=0;i<1000;i++)e(c,i); List<String>s=c.snapshot(); ok(s.size()==100&&s.get(99).contains("index=999"),"rollover");
  long d=c.dropped(); ok(c.event(null,"BAD")==null&&c.dropped()>d,"malformed safe");
  c.setLevel(NgxDiagnosticCore.Level.OFF); ok(c.capture(),"capture"); for(int i=0;i<50;i++)e(c,2000+i); ok(c.level()==NgxDiagnosticCore.Level.OFF,"restore");
  c.setLevel(NgxDiagnosticCore.Level.DIAGNOSTIC); ok(c.capture()&&!c.capture(),"double"); c.clear(); ok(c.level()==NgxDiagnosticCore.Level.DIAGNOSTIC&&c.snapshot().isEmpty(),"clear");
  Thread[] ts=new Thread[4]; for(int i=0;i<4;i++){ts[i]=new Thread(()->{for(int j=0;j<200;j++){e(c,j);c.snapshot();}});ts[i].start();} for(Thread t:ts)t.join(); ok(c.snapshot().size()<=100,"concurrent"); System.out.println("NGX core hardened PASS");
 }
}
