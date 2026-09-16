import org.telegram.messenger.diagnostics.NgxDiagnosticCore;
public class NgxDiagnosticCoreTest {
  public static void main(String[] a) {
    NgxDiagnosticCore c=new NgxDiagnosticCore();
    if(c.event(NgxDiagnosticCore.Category.APP,"OFF")!=null) throw new AssertionError("off");
    c.setLevel(NgxDiagnosticCore.Level.DIAGNOSTIC); c.begin("ACTION");
    String x=c.event(NgxDiagnosticCore.Category.GESTURE,"DECISION","RESULT","false");
    if(x==null||!x.contains("sid=")||c.event(NgxDiagnosticCore.Category.GESTURE,"DECISION","RESULT","false")!=null) throw new AssertionError("session/dedupe");
    for(int i=0;i<150;i++) c.event(NgxDiagnosticCore.Category.APP,"STEP","VALUE",Integer.toString(i));
    if(c.snapshot().size()>NgxDiagnosticCore.RING_LIMIT) throw new AssertionError("ring");
    try { c.event(NgxDiagnosticCore.Category.APP,"BAD","MESSAGE","secret"); throw new AssertionError("privacy"); } catch(IllegalArgumentException ok) {}
    c.clear(); if(!c.snapshot().isEmpty()) throw new AssertionError("clear");
    System.out.println("NGX core OFF/session/ring/privacy PASS");
  }
}
