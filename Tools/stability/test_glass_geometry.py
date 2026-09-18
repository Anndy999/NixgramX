"""Execute production radius overloads, with only Android drawable state stubbed."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java'


class GlassGeometryTest(unittest.TestCase):
    def test_production_radius_overloads(self):
        source = DRAWABLE.read_text(encoding='utf-8')
        start = source.index('    public BlurredBackgroundDrawable setRadius(float radius)')
        end = source.index('    public BlurredBackgroundDrawable setThickness(', start)
        # Copy methods verbatim, not a Python reimplementation of the setter.
        methods = source[start:end]
        harness = '''import java.util.Arrays;
class BlurredBackgroundDrawable {
    static class Bounds {
        float[] radii=new float[8],shaderRadii=new float[8]; int builds;
        void build(){builds++;}
    }
    Bounds boundProps=new Bounds(); int notifications;
    void onBoundPropsChanged(){notifications++;}
    METHODS
}
public class GlassGeometryTest {
    static void check(boolean b){if(!b)throw new AssertionError("clip/shader radius mismatch");}
    public static void main(String[] args){
        for(float density:new float[]{1,1.5f,2,3,4}){
            var d=new BlurredBackgroundDrawable();
            // Private/channel and back/menu buttons use uniform radii.
            check(d.setRadius(23*density)==d);
            check(Arrays.equals(d.boundProps.radii,d.boundProps.shaderRadii));
            // Forum headers keep the same uniform 23dp shape during setup and search.
            int before=d.notifications,builds=d.boundProps.builds;
            check(d.setRadius(23*density)==d);
            check(Arrays.equals(d.boundProps.radii,d.boundProps.shaderRadii));
            check(d.boundProps.radii[0]==23*density && d.boundProps.radii[6]==23*density);
            check(d.notifications==before+1 && d.boundProps.builds==builds+1);
            // Deliberate five-argument clipped-bottom behavior must not change.
            d.setRadius(1,2,3,4,true);
            check(d.boundProps.radii[4]==0 && d.boundProps.radii[6]==0);
            check(d.boundProps.shaderRadii[4]==3 && d.boundProps.shaderRadii[6]==4);
            d.setRadius(5,6,7,8);
            check(Arrays.equals(d.boundProps.radii,d.boundProps.shaderRadii));
            check(d.boundProps.radii[4]==7 && d.boundProps.radii[6]==8);
        }
        System.out.println("PASS: uniform forum/search radii, 5 densities, clipped-bottom preserved");
    }
}
'''.replace('METHODS', methods)
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / 'GlassGeometryTest.java'
            path.write_text(harness, encoding='utf-8')
            subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root), str(path)], check=True)
            subprocess.run(['java', '-cp', str(root), 'GlassGeometryTest'], check=True)

    def test_forum_header_uses_uniform_radius(self):
        action = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
        chat = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
        renderer = (DRAWABLE.parent / 'BlurredBackgroundDrawableRenderNode.java').read_text(encoding='utf-8')
        self.assertIn('ChatObject.isForum(currentChat)', chat)
        self.assertIn('glassDrawable.setRadius(dp(23));', action)
        self.assertNotIn('glassDrawable.setRadius(dp(18.33f), dp(23), dp(23), dp(18.33f))', action)
        self.assertNotIn('glassDrawable.setRadius(r2, r1, r1, r2)', action)
        self.assertNotIn('lerp(dp(18.33f), dp(23), searchFieldVisibleAlpha)', action)
        self.assertIn('getOutline(outline, outlineRect, boundProps.radii)', renderer)
        self.assertIn('boundProps.shaderRadii[0]', renderer)
