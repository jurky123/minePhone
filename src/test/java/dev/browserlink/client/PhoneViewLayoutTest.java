package dev.browserlink.client;

import dev.browserlink.PhoneGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhoneViewLayoutTest {
    @Test void landscapeFitsBrowserWithoutStretchingOrCoveringControls() {
        var v=PhoneViewLayout.calculate(854,480,48,22,true,1280,720);
        assertTrue(v.right()-v.left()>v.bottom()-v.top());
        assertTrue(v.left()>=0&&v.right()<=854&&v.top()>=48&&v.bottom()<=458);
        assertEquals(1280.0/720,(double)v.imageWidth()/v.imageHeight(),.01);
        assertTrue(v.imageX()>=v.left()&&v.imageY()>=v.top());
        assertTrue(v.imageX()+v.imageWidth()<=v.right()&&v.imageY()+v.imageHeight()<=v.bottom());
    }
    @Test void rotationKeepsTheSamePageAspectAndCentersLetterboxing() {
        var v=PhoneViewLayout.calculate(854,480,48,22,false,1280,720);
        assertTrue(v.bottom()-v.top()>v.right()-v.left());
        assertEquals(1280.0/720,(double)v.imageWidth()/v.imageHeight(),.02);
        assertEquals((v.left()+v.right())/2.0,v.imageX()+v.imageWidth()/2.0,1);
        assertEquals((v.top()+v.bottom())/2.0,v.imageY()+v.imageHeight()/2.0,1);
    }
    @Test void displayAndPointerBoundsRemainValidAcrossGuiScalesAndWindowSizes() {
        for(int[] size:new int[][]{{320,240},{427,240},{640,360},{854,480},{1280,720},{2560,1440}})
            for(boolean rotated:new boolean[]{true,false}) {
                var v=PhoneViewLayout.calculate(size[0],size[1],36,17,rotated,1920,1080);
                assertTrue(v.scale()>0&&v.imageWidth()>0&&v.imageHeight()>0);
                assertTrue(v.imageX()>=0&&v.imageX()+v.imageWidth()<=size[0]);
                assertTrue(v.imageY()>=36&&v.imageY()+v.imageHeight()<=size[1]-17);
            }
    }
    @Test void zoomKeepsBodyAndPointerRegionCenteredAndWithinView() {
        for(boolean horizontal:new boolean[]{true,false})for(float zoom:new float[]{.7f,.9f,1f,5f,Float.NaN}){
            var v=PhoneViewLayout.calculate(854,480,48,22,horizontal,1920,1080,zoom);
            assertTrue(v.left()>=0&&v.right()<=854&&v.top()>=48&&v.bottom()<=458);
            assertTrue(v.imageX()>=v.left()&&v.imageX()+v.imageWidth()<=v.right());
            assertEquals(16.0/9,v.imageWidth()/(double)v.imageHeight(),.03);
        }
    }
    @Test void sourceScreenRemainsPortraitAndInsideAuthoredBody() {
        float[] s=PhoneGeometry.SCREEN,b=PhoneGeometry.BOUNDS;
        assertEquals(2,(s[3]-s[1])/(s[2]-s[0]),.00001);
        assertTrue(b[0]<s[0]&&b[1]<s[1]&&b[3]>s[2]&&b[4]>s[3]);
    }
}
