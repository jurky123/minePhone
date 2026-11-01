package dev.browserlink.client;

import dev.browserlink.PhoneGeometry;

/** Shared close-up projection for the model, browser pixels and pointer coordinates. */
final class PhoneViewLayout {
    record View(float scale,float centerX,float centerY,int left,int top,int right,int bottom,
                int imageX,int imageY,int imageWidth,int imageHeight) {}
    static View calculate(int width,int height,int topMargin,int bottomMargin,boolean landscape,int imageWidth,int imageHeight) {
        return calculate(width,height,topMargin,bottomMargin,landscape,imageWidth,imageHeight,1);
    }
    static View calculate(int width,int height,int topMargin,int bottomMargin,boolean landscape,int imageWidth,int imageHeight,float zoom) {
        float[] b=PhoneGeometry.BOUNDS,s=PhoneGeometry.SCREEN;
        float bodyWidth=landscape?b[4]-b[1]:b[3]-b[0];
        float bodyHeight=landscape?b[3]-b[0]:b[4]-b[1];
        float scale=Math.min(Math.max(1,width-12)/bodyWidth,Math.max(1,height-topMargin-bottomMargin-12)/bodyHeight);
        scale*=Math.max(.7f,Math.min(1,Float.isFinite(zoom)?zoom:1));
        float cx=width/2f,cy=topMargin+(height-topMargin-bottomMargin)/2f;
        int left=Math.round(cx+scale*(landscape?.5f-s[3]:s[0]-.5f));
        int right=Math.round(cx+scale*(landscape?.5f-s[1]:s[2]-.5f));
        int top=Math.round(cy-scale*(landscape?s[2]-.5f:s[3]-.5f));
        int bottom=Math.round(cy-scale*(landscape?s[0]-.5f:s[1]-.5f));
        float fit=Math.min((right-left)/(float)Math.max(1,imageWidth),(bottom-top)/(float)Math.max(1,imageHeight));
        int iw=Math.max(1,Math.round(imageWidth*fit)),ih=Math.max(1,Math.round(imageHeight*fit));
        return new View(scale,cx,cy,left,top,right,bottom,left+(right-left-iw)/2,top+(bottom-top-ih)/2,iw,ih);
    }
    private PhoneViewLayout() {}
}
