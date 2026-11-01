package dev.browserlink.client;

/** Tracks only the limit this feature owns; never overwrites an external change on exit. */
final class FrameLimitBudget {
    private int restore=-1,applied=-1;
    int update(int current,int budget,boolean visible){
        if(budget>0&&visible){
            if(restore<0||current!=applied)restore=current;
            applied=Math.min(restore,budget);return applied;
        }
        int result=restore>=0&&current==applied?restore:current;
        restore=-1;applied=-1;return result;
    }
}
