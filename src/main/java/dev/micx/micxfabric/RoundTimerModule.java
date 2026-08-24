package dev.micx.micxfabric;

public final class RoundTimerModule implements Module {
    private static final RoundTimerModule INSTANCE=new RoundTimerModule();
    private boolean enabled=true;
    private RoundTimerModule(){}
    public static RoundTimerModule instance(){return INSTANCE;}
    @Override public String id(){return "round_timer";}
    @Override public boolean defaultEnabled(){return true;}
    @Override public boolean enabled(){return enabled;}
    @Override public void setEnabled(boolean v){enabled=v; ModuleStateStore.put(id(),v);}
    static String renderText(int round,long startMs,long now){
        if(round<1||startMs<=0||now<startMs) return null;
        return "R"+round+" "+RoundTimeNotifier.formatDuration(now-startMs);
    }
}
